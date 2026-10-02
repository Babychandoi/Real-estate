package com.company.bds.shared.security;

import com.company.bds.iam.application.AuthService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Append-only request audit trail kept as one hash chain: event {@code n} ({@code chain_seq}) stores the hash of event
 * {@code n-1} ({@code previous_hash}) and its own hash over both and its fields ({@code event_hash}).
 *
 * <p><b>Why two steps.</b> Before V103 every request read "the latest hash" and inserted its event without a lock.
 * Concurrent requests read the same predecessor, so the chain forked; when two of them also had the same actor, path,
 * status and timestamp their hashes were equal, the second insert hit {@code event_hash UNIQUE} and the record was lost
 * ({@code audit_write_failed}). Linking a chain needs one writer at a time, but making every request wait for that
 * writer while it holds a pooled connection starves the pool under load. So:
 * <ol>
 *   <li>{@link #record} — on the request thread, one plain {@code INSERT} of the unlinked event (no lock, no read, no
 *   possible collision). This is the durable record.</li>
 *   <li>{@link #linkPending} — one linker at a time (the {@code audit_chain_head} row lock, cluster-wide) assigns the next
 *   positions and hashes to unlinked events in arrival order, in batches, every second.</li>
 * </ol>
 * The unique {@code chain_seq} makes a fork impossible whoever writes. {@link #verify} recomputes the chain.
 *
 * <p>The audit insert runs after the business transaction committed, so it can never undo it. A failed insert is
 * retried; one that still fails is logged with its cause and counted in {@code bds.audit.write.failures} (alert
 * BdsAuditWriteFailures) — never dropped silently.
 */
@Component
public class AuditTrail {
    static final String GENESIS = "GENESIS";
    static final int ATTEMPTS = 3;
    static final int LINK_BATCH = 500;
    private static final Logger log = LoggerFactory.getLogger(AuditTrail.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate independent;
    private final boolean schedulerEnabled;
    private final Counter failures;
    private final Counter retries;
    private final Counter linkFailures;
    private final AtomicLong backlog = new AtomicLong();

    public AuditTrail(JdbcTemplate jdbc, PlatformTransactionManager transactions, MeterRegistry meters,
                      @Value("${app.audit.chain.scheduler-enabled:true}") boolean schedulerEnabled) {
        this.jdbc = jdbc;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.schedulerEnabled = schedulerEnabled;
        this.failures = Counter.builder("bds.audit.write.failures")
                .description("Request audit records that could not be stored after every retry").register(meters);
        this.retries = Counter.builder("bds.audit.write.retries")
                .description("Request audit inserts that had to be retried").register(meters);
        this.linkFailures = Counter.builder("bds.audit.chain.link.failures")
                .description("Audit chain linking runs that failed (the events stay stored and are linked by the next run)").register(meters);
        Gauge.builder("bds.audit.chain.backlog", backlog, AtomicLong::get)
                .description("Stored audit events not yet linked into the hash chain after the last linking run").register(meters);
    }

    public record Entry(UUID actorId, String method, String resource, int status, String clientFingerprint) {}

    /** Stores the event (unlinked); false when it could not be stored after {@value #ATTEMPTS} attempts. */
    public boolean record(Entry entry) {
        RuntimeException last = null;
        // PostgreSQL keeps microseconds: hash exactly what is stored so the chain can be recomputed from its rows.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                jdbc.update("""
                        INSERT INTO audit_events(id, occurred_at, actor_id, action, resource, result_status, client_fingerprint)
                        VALUES (?,?,?,?,?,?,?)
                        """, UUID.randomUUID(), Timestamp.from(now), entry.actorId(), entry.method(), entry.resource(), entry.status(),
                        entry.clientFingerprint());
                return true;
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt < ATTEMPTS) {
                    retries.increment();
                    pause(attempt);
                }
            }
        }
        failures.increment();
        log.error("audit_write_failed method={} path={} status={} attempts={} cause={}: {}", entry.method(), entry.resource(),
                entry.status(), ATTEMPTS, last.getClass().getSimpleName(), last.getMessage());
        return false;
    }

    @Scheduled(initialDelayString = "${app.audit.chain.initial-delay-ms:5000}", fixedDelayString = "${app.audit.chain.delay-ms:1000}")
    void scheduledLink() {
        if (!schedulerEnabled) return;
        try {
            linkAll();
        } catch (RuntimeException ex) {
            linkFailures.increment();
            log.error("audit_chain_link_failed cause={}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

    /** Links every pending event (batch after batch); returns how many were linked by this call. */
    public long linkAll() {
        long total = 0;
        int linked;
        do {
            linked = linkPending(LINK_BATCH);
            total += linked;
        } while (linked == LINK_BATCH);
        Long remaining = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE event_hash IS NULL", Long.class);
        backlog.set(remaining == null ? 0 : remaining);
        return total;
    }

    /**
     * Links up to {@code limit} pending events in arrival order. Returns 0 without waiting when another instance is
     * linking (the head row is locked): it will link these events too.
     */
    public int linkPending(int limit) {
        Integer linked = independent.execute(status -> {
            List<Map<String, Object>> head = jdbc.queryForList(
                    "SELECT last_seq, last_hash FROM audit_chain_head WHERE singleton_id = 1 FOR UPDATE SKIP LOCKED");
            if (head.isEmpty()) return 0;
            long sequence = ((Number) head.get(0).get("last_seq")).longValue();
            String previous = ((String) head.get(0).get("last_hash")).strip();
            List<Map<String, Object>> pending = jdbc.queryForList("""
                    SELECT id, occurred_at, actor_id, action, resource, result_status, client_fingerprint
                    FROM audit_events WHERE event_hash IS NULL ORDER BY occurred_at, id LIMIT ?
                    """, limit);
            if (pending.isEmpty()) return 0;
            List<Object[]> updates = new java.util.ArrayList<>(pending.size());
            for (Map<String, Object> row : pending) {
                sequence++;
                String hash = AuthService.sha256(material(previous, sequence, ((Timestamp) row.get("occurred_at")).toInstant(), entry(row)));
                updates.add(new Object[]{sequence, previous, hash, row.get("id")});
                previous = hash;
            }
            jdbc.batchUpdate("UPDATE audit_events SET chain_seq = ?, previous_hash = ?, event_hash = ? WHERE id = ? AND event_hash IS NULL",
                    updates);
            jdbc.update("UPDATE audit_chain_head SET last_seq = ?, last_hash = ? WHERE singleton_id = 1", sequence, previous);
            return pending.size();
        });
        return linked == null ? 0 : linked;
    }

    static String material(String previous, long sequence, Instant occurredAt, Entry entry) {
        return previous + '|' + sequence + '|' + occurredAt + '|' + entry.actorId() + '|' + entry.method() + '|'
                + entry.resource() + '|' + entry.status() + '|' + entry.clientFingerprint();
    }

    private static Entry entry(Map<String, Object> row) {
        return new Entry((UUID) row.get("actor_id"), (String) row.get("action"), (String) row.get("resource"),
                ((Number) row.get("result_status")).intValue(), (String) row.get("client_fingerprint"));
    }

    /** Result of {@link #verify()}: how many linked events verified, and the first position that does not. */
    public record Verification(long verified, Long firstBrokenSeq) {
        public boolean intact() { return firstBrokenSeq == null; }
    }

    /**
     * Recomputes every linked event (those stored since V103) in order: each must take the next position, name its
     * predecessor's hash and hash to its stored value; the head must point at the last one.
     */
    public Verification verify() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT chain_seq, occurred_at, actor_id, action, resource, result_status, client_fingerprint, previous_hash, event_hash
                FROM audit_events WHERE chain_seq IS NOT NULL ORDER BY chain_seq
                """);
        String expectedPrevious = jdbc.queryForObject("SELECT genesis_hash FROM audit_chain_head WHERE singleton_id = 1", String.class).strip();
        long expectedSeq = 1;
        for (Map<String, Object> row : rows) {
            long sequence = ((Number) row.get("chain_seq")).longValue();
            String previous = ((String) row.get("previous_hash")).strip();
            String hash = ((String) row.get("event_hash")).strip();
            boolean valid = sequence == expectedSeq && previous.equals(expectedPrevious)
                    && AuthService.sha256(material(previous, sequence, ((Timestamp) row.get("occurred_at")).toInstant(), entry(row))).equals(hash);
            if (!valid) return new Verification(expectedSeq - 1, sequence);
            expectedPrevious = hash;
            expectedSeq++;
        }
        Map<String, Object> head = jdbc.queryForMap("SELECT last_seq, last_hash FROM audit_chain_head WHERE singleton_id = 1");
        if (((Number) head.get("last_seq")).longValue() != expectedSeq - 1 || !((String) head.get("last_hash")).strip().equals(expectedPrevious)) {
            return new Verification(expectedSeq - 1, expectedSeq);
        }
        return new Verification(expectedSeq - 1, null);
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(25L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
