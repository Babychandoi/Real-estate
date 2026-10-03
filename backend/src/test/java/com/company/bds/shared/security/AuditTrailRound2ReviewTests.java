package com.company.bds.shared.security;

import com.company.bds.testsupport.BdsIntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-2 adversarial review of AuditTrail.verify() and the cut-over (PR #24, bafe510). Expected to fail on bafe510.
 */
@BdsIntegrationTest
class AuditTrailRound2ReviewTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditTrail trail;
    @Autowired PlatformTransactionManager transactions;

    /**
     * verify() reads the chain page by page and the head afterwards, each in its own READ COMMITTED statement, while the
     * linker keeps appending every second. When a linking run commits between the last page and the head read, the head
     * is "ahead" of the verified chain and verify() reports tampering (firstBrokenSeq) on an intact chain.
     */
    @Test
    void verifyDoesNotReportTamperingWhenTheLinkerAppendsWhileItRuns() {
        trail.linkAll();
        AtomicBoolean once = new AtomicBoolean(true);
        JdbcTemplate racing = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public Map<String, Object> queryForMap(String sql) {
                if (once.getAndSet(false) && sql.contains("FROM audit_chain_head")) {
                    // A request is audited and the scheduled linker (another thread/instance) chains it right now.
                    trail.record(new AuditTrail.Entry(null, "POST", "/api/v1/review2-race-" + UUID.randomUUID(), 201, "f".repeat(64)));
                    trail.linkAll();
                }
                return super.queryForMap(sql);
            }
        };
        AuditTrail.Verification v = new AuditTrail(racing, transactions, new SimpleMeterRegistry(), false).verify();
        assertThat(trail.verify().intact()).as("the chain itself is intact").isTrue();
        assertThat(v.firstBrokenSeq()).as("verify() during normal linking must not report a broken chain").isNull();
    }

    /**
     * intact = valid chain AND unchained == 0, but every event waits up to app.audit.chain.delay-ms (1 s) for the next
     * linking run. On a live system the admin endpoint therefore answers intact=false whenever a write happened in the
     * last second: a routine state is reported like tampering.
     */
    @Test
    void anEventAwaitingTheNextScheduledLinkDoesNotMakeTheChainNonIntact() {
        trail.linkAll();
        trail.record(new AuditTrail.Entry(null, "POST", "/api/v1/review2-pending-" + UUID.randomUUID(), 201, "f".repeat(64)));
        try {
            AuditTrail.Verification v = trail.verify();
            assertThat(v.firstBrokenSeq()).isNull();
            assertThat(v.intact()).as("a fresh event inside the normal linking delay (unchained=%s)", v.unchained()).isTrue();
        } finally {
            trail.linkAll();
        }
    }

    /**
     * The cut-over is decided by occurred_at, which the previous image takes from its own JVM clock. An old pod whose
     * clock lags the database by 2 s writes, right after V103, rows dated before cutover_at: they are neither chained nor
     * counted as unchained, so verify() says intact while they can be edited or deleted undetected.
     */
    @Test
    void aRowWrittenAfterTheCutoverByAnOldPodWithALaggingClockIsNotSilentlyIgnored() {
        Instant cutover = jdbc.queryForObject("SELECT cutover_at FROM audit_chain_head WHERE singleton_id = 1", Timestamp.class).toInstant();
        String resource = "/api/v1/review2-skew-" + UUID.randomUUID();
        Instant lagging = cutover.minusSeconds(2);
        String previous = jdbc.query("SELECT event_hash FROM audit_events ORDER BY occurred_at DESC LIMIT 1", rs -> rs.next() ? rs.getString(1) : "GENESIS");
        jdbc.update("INSERT INTO audit_events(id,occurred_at,actor_id,action,resource,result_status,client_fingerprint,previous_hash,event_hash) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), Timestamp.from(lagging), null, "POST", resource, 201, "f".repeat(64), previous,
                AuditTrail.sha256(previous + '|' + lagging + "|null|POST|" + resource + "|201"));
        trail.linkAll();
        Long position = jdbc.queryForObject("SELECT chain_seq FROM audit_events WHERE resource = ?", Long.class, resource);
        AuditTrail.Verification v = trail.verify();
        assertThat(position != null || v.unchained() > 0)
                .as("an audit row stored after V103 is either chained or reported as unchained (chain_seq=%s, unchained=%s)", position, v.unchained())
                .isTrue();
    }
}
