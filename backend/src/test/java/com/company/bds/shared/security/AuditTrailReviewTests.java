package com.company.bds.shared.security;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.BdsTestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adversarial review of W6 {@code AuditTrail} (PR #24). Tests named "...ShouldFail..." in the report reproduce defects
 * and are expected to fail on the reviewed commit; the others pin behaviour that was verified correct.
 */
@BdsIntegrationTest
class AuditTrailReviewTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditTrail trail;
    @Autowired PlatformTransactionManager transactions;

    /**
     * Rolling deploy (infra/k8s/app.yaml: 2 replicas, default RollingUpdate): after the new pod ran V103 the old pod keeps
     * auditing with the pre-W6 filter, which stores a hashed row with chain_seq NULL. The linker only picks rows whose
     * event_hash IS NULL and verify() only reads rows with a chain_seq, so such a row is outside the chain forever while
     * verify() still reports "intact" — the post-V103 trail is not fully tamper-evident.
     */
    @Test
    void anEventStoredByThePreviousImageAfterV103IsLinkedIntoTheChain() {
        String resource = "/api/v1/review-old-image-" + UUID.randomUUID();
        // The previous image's AuditTrailFilter, statement for statement (origin/main).
        String previous = jdbc.query("SELECT event_hash FROM audit_events ORDER BY occurred_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : "GENESIS");
        Instant now = Instant.now();
        String material = previous + '|' + now + '|' + null + '|' + "POST" + '|' + resource + '|' + 201;
        jdbc.update("INSERT INTO audit_events(id,occurred_at,actor_id,action,resource,result_status,client_fingerprint,previous_hash,event_hash) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), Timestamp.from(now), null, "POST", resource, 201, "f".repeat(64), previous, AuditTrail.sha256(material));

        trail.linkAll();
        assertThat(trail.verify().intact()).as("verify() reports the chain intact").isTrue();
        assertThat(jdbc.queryForObject("SELECT chain_seq FROM audit_events WHERE resource = ?", Long.class, resource))
                .as("an audit event stored after V103 must be covered by the hash chain (else it can be edited/deleted undetected)")
                .isNotNull();
    }

    /**
     * record() draws a new UUID per attempt. When the first INSERT committed on the server but the acknowledgement was
     * lost (connection reset after commit, statement timeout racing the commit), the retry stores the event a second time.
     */
    @Test
    void aRetryAfterACommittedButUnacknowledgedInsertStoresTheEventOnce() {
        AtomicBoolean first = new AtomicBoolean(true);
        JdbcTemplate lostAck = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public int update(String sql, Object... args) {
                int rows = super.update(sql, args);
                if (first.getAndSet(false)) throw new DataAccessResourceFailureException("I/O error: connection reset (after commit)");
                return rows;
            }
        };
        AuditTrail retrying = new AuditTrail(lostAck, transactions, new SimpleMeterRegistry(), false);
        String resource = "/api/v1/review-retry-" + UUID.randomUUID();
        assertThat(retrying.record(new AuditTrail.Entry(null, "POST", resource, 201, "f".repeat(64)))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE resource = ?", Integer.class, resource))
                .as("one request, one audit record").isEqualTo(1);
    }

    /**
     * With the pool exhausted every attempt waits a full connection-timeout (20 s in application.yml), so a write request
     * now holds its Tomcat thread (and its not-yet-flushed response) for 3 x 20 s after its business work, instead of
     * 1 x 20 s before W6. Measured here with a 1-connection pool and a 500 ms timeout.
     */
    @Test
    void underAnExhaustedPoolARequestWaitsForTheAuditInsertOnceNotThreeTimes() throws Exception {
        BdsTestDatabase.Database db = BdsTestDatabase.shared();
        try (HikariDataSource tiny = new HikariDataSource()) {
            tiny.setJdbcUrl(db.url());
            tiny.setUsername(db.username());
            tiny.setPassword(db.password());
            tiny.setMaximumPoolSize(1);
            tiny.setConnectionTimeout(500);
            try (Connection held = tiny.getConnection()) {
                assertThat(held.isValid(1)).isTrue();
                AuditTrail starved = new AuditTrail(new JdbcTemplate(tiny), new DataSourceTransactionManager(tiny), new SimpleMeterRegistry(), false);
                long start = System.nanoTime();
                boolean stored = starved.record(new AuditTrail.Entry(null, "POST", "/api/v1/review-starved", 201, "f".repeat(64)));
                Duration waited = Duration.ofNanos(System.nanoTime() - start);
                assertThat(stored).isFalse();
                assertThat(waited).as("time the request thread spent in the audit write with the pool exhausted (timeout 500 ms)")
                        .isLessThan(Duration.ofMillis(1_000));
            }
        }
    }

    /** Verified correct: a linker that dies after assigning positions but before moving the head leaves nothing half-linked. */
    @Test
    void aLinkerThatCrashesMidBatchLeavesNoPartialLinksAndTheNextRunLinksEverything() {
        String resource = "/api/v1/review-crash-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) trail.record(new AuditTrail.Entry(null, "POST", resource, 201, "f".repeat(64)));
        JdbcTemplate crashing = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public int update(String sql, Object... args) {
                if (sql.contains("audit_chain_head")) throw new DataAccessResourceFailureException("linker killed before the head update");
                return super.update(sql, args);
            }
        };
        AuditTrail crashingLinker = new AuditTrail(crashing, transactions, new SimpleMeterRegistry(), false);
        assertThatThrownBy(() -> crashingLinker.linkPending(AuditTrail.LINK_BATCH)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE resource = ? AND (chain_seq IS NOT NULL OR event_hash IS NOT NULL)",
                Integer.class, resource)).as("the crashed batch was rolled back").isZero();

        trail.linkAll();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE resource = ? AND chain_seq IS NOT NULL", Integer.class, resource))
                .isEqualTo(3);
        assertThat(trail.verify().intact()).isTrue();
    }

    /** Verified correct: deleting a linked event in the middle, or the newest one, is detected. */
    @Test
    void deletingALinkedEventIsDetected() {
        String resource = "/api/v1/review-delete-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) trail.record(new AuditTrail.Entry(null, "POST", resource, 201, "f".repeat(64)));
        trail.linkAll();
        Long middle = jdbc.queryForObject("SELECT MIN(chain_seq) + 1 FROM audit_events WHERE resource = ?", Long.class, resource);
        java.util.Map<String, Object> saved = jdbc.queryForMap("SELECT * FROM audit_events WHERE chain_seq = ?", middle);
        jdbc.update("DELETE FROM audit_events WHERE chain_seq = ?", middle);
        try {
            assertThat(trail.verify().firstBrokenSeq()).isEqualTo(middle + 1);
        } finally {
            jdbc.update("""
                    INSERT INTO audit_events(id,occurred_at,actor_id,action,resource,result_status,client_fingerprint,previous_hash,event_hash,chain_seq)
                    VALUES (?,?,?,?,?,?,?,?,?,?)""", saved.get("id"), saved.get("occurred_at"), saved.get("actor_id"), saved.get("action"),
                    saved.get("resource"), saved.get("result_status"), saved.get("client_fingerprint"), saved.get("previous_hash"),
                    saved.get("event_hash"), saved.get("chain_seq"));
        }
        assertThat(trail.verify().intact()).isTrue();
    }
}
