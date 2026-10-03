package com.company.bds.shared.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
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
 *   positions and hashes to unchained events in arrival order, in batches, every second. Unchained means: stored without
 *   a hash by this class, or stored after the V103 cut-over by an older image of the application (rolling deploy) that
 *   still hashed on its own — those are re-hashed into the chain.</li>
 * </ol>
 * The unique {@code chain_seq} makes a fork impossible whoever writes. {@link #verify} recomputes the chain page by page
 * and reports the unchained backlog (admin endpoint {@code GET /api/v1/admin/audit-chain/verification}).
 *
 * <p>The audit insert runs after the business transaction committed, so it can never undo it. A failed insert is
 * retried with the same event id (a lost acknowledgement never stores it twice), except when no pooled connection could
 * be obtained: the request already waited one connection timeout and must not wait more. A record that is still not
 * stored is logged with its cause and counted in {@code bds.audit.write.failures} (alert BdsAuditWriteFailures).
 */
@Component
public class AuditTrail {
    static final String GENESIS = "GENESIS";
    static final int ATTEMPTS = 3;
    static final int LINK_BATCH = 500;
    static final int VERIFY_PAGE = 5_000;
    private static final Logger log = LoggerFactory.getLogger(AuditTrail.class);

    /** Unchained rows, oldest first; each branch is served by its own partial index (V103). */
    private static final String PENDING = """
            SELECT id, occurred_at, actor_id, action, resource, result_status, client_fingerprint FROM (
                (SELECT id, occurred_at, actor_id, action, resource, result_status, client_fingerprint FROM audit_events
                 WHERE event_hash IS NULL ORDER BY occurred_at, id LIMIT ?)
                UNION ALL
                (SELECT id, occurred_at, actor_id, action, resource, result_status, client_fingerprint FROM audit_events
                 WHERE chain_seq IS NULL AND event_hash IS NOT NULL AND occurred_at >= ? ORDER BY occurred_at, id LIMIT ?)
            ) pending ORDER BY occurred_at, id LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate independent;
    private final boolean schedulerEnabled;
    private final Counter failures;
    private final Counter retries;
    private final Counter linkFailures;
    private final AtomicLong backlog = new AtomicLong();
    private final AtomicLong lastLinkSuccess = new AtomicLong();

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
                .description("Stored audit events not yet linked into the hash chain (updated by every linking run)").register(meters);
        Gauge.builder("bds.audit.chain.last.link.success", lastLinkSuccess, AtomicLong::get)
                .description("Epoch seconds of the last linking run that completed").baseUnit("seconds").register(meters);
    }

    public record Entry(UUID actorId, String method, String resource, int status, String clientFingerprint) {}

    /** Stores the event (unchained); false when it could not be stored. */
    public boolean record(Entry entry) {
        RuntimeException last = null;
        // One id for every attempt: a retry after a commit whose acknowledgement was lost is a no-op, not a second record.
        UUID id = UUID.randomUUID();
        // PostgreSQL keeps microseconds: hash exactly what is stored so the chain can be recomputed from its rows.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        int attempts = 0;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            attempts = attempt;
            try {
                jdbc.update("""
                        INSERT INTO audit_events(id, occurred_at, actor_id, action, resource, result_status, client_fingerprint)
                        VALUES (?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING
                        """, id, Timestamp.from(now), entry.actorId(), entry.method(), entry.resource(), entry.status(),
                        entry.clientFingerprint());
                return true;
            } catch (CannotGetJdbcConnectionException ex) {
                // The pool is exhausted: this thread already waited a full connection timeout; retrying would triple it.
                last = ex;
                break;
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
                entry.status(), attempts, last.getClass().getSimpleName(), last.getMessage());
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
        try {
            int linked;
            do {
                linked = linkPending(LINK_BATCH);
                total += linked;
            } while (linked == LINK_BATCH);
            lastLinkSuccess.set(Instant.now().getEpochSecond());
            return total;
        } finally {
            // Also after a failure, so a stalled linker shows as a growing backlog (alert BdsAuditChainStalled).
            try {
                backlog.set(unchained());
            } catch (RuntimeException ex) {
                log.warn("audit_chain_backlog_unknown cause={}", ex.getClass().getSimpleName());
            }
        }
    }

    /**
     * Links up to {@code limit} pending events in arrival order. Returns 0 without waiting when another instance is
     * linking (the head row is locked): it will link these events too. A missing head row is recreated from the chain
     * (and counted as a failure: someone deleted it).
     */
    public int linkPending(int limit) {
        Integer linked = independent.execute(status -> {
            List<Map<String, Object>> head = jdbc.queryForList(
                    "SELECT last_seq, last_hash, cutover_at FROM audit_chain_head WHERE singleton_id = 1 FOR UPDATE SKIP LOCKED");
            if (head.isEmpty()) {
                recreateHeadIfMissing();
                return 0;
            }
            long sequence = ((Number) head.get(0).get("last_seq")).longValue();
            String previous = ((String) head.get(0).get("last_hash")).strip();
            Timestamp cutover = (Timestamp) head.get(0).get("cutover_at");
            List<Map<String, Object>> pending = jdbc.queryForList(PENDING, limit, cutover, limit, limit);
            if (pending.isEmpty()) return 0;
            List<Object[]> updates = new ArrayList<>(pending.size());
            for (Map<String, Object> row : pending) {
                sequence++;
                String hash = sha256(material(previous, sequence, ((Timestamp) row.get("occurred_at")).toInstant(), entry(row)));
                updates.add(new Object[]{sequence, previous, hash, row.get("id")});
                previous = hash;
            }
            jdbc.batchUpdate("UPDATE audit_events SET chain_seq = ?, previous_hash = ?, event_hash = ? WHERE id = ? AND chain_seq IS NULL",
                    updates);
            jdbc.update("UPDATE audit_chain_head SET last_seq = ?, last_hash = ? WHERE singleton_id = 1", sequence, previous);
            return pending.size();
        });
        return linked == null ? 0 : linked;
    }

    private void recreateHeadIfMissing() {
        Integer heads = jdbc.queryForObject("SELECT count(*) FROM audit_chain_head", Integer.class);
        if (heads != null && heads > 0) return; // locked by another linker
        List<Map<String, Object>> last = jdbc.queryForList(
                "SELECT chain_seq, event_hash FROM audit_events WHERE chain_seq IS NOT NULL ORDER BY chain_seq DESC LIMIT 1");
        List<String> first = jdbc.queryForList("SELECT previous_hash FROM audit_events WHERE chain_seq = 1", String.class);
        String legacyLatest = jdbc.query("SELECT TRIM(event_hash) FROM audit_events WHERE chain_seq IS NULL AND event_hash IS NOT NULL "
                + "ORDER BY occurred_at DESC, id DESC LIMIT 1", rs -> rs.next() ? rs.getString(1) : GENESIS);
        long lastSeq = last.isEmpty() ? 0 : ((Number) last.get(0).get("chain_seq")).longValue();
        String lastHash = last.isEmpty() ? legacyLatest : ((String) last.get(0).get("event_hash")).strip();
        String genesis = first.isEmpty() ? lastHash : first.get(0).strip();
        jdbc.update("""
                INSERT INTO audit_chain_head(singleton_id, last_seq, last_hash, genesis_hash, cutover_at) VALUES (1, ?, ?, ?, now())
                ON CONFLICT (singleton_id) DO NOTHING
                """, lastSeq, lastHash, genesis);
        linkFailures.increment();
        log.error("audit_chain_head_missing recreated last_seq={}", lastSeq);
    }

    /** Stored events that are not (yet) in the chain. */
    public long unchained() {
        Timestamp cutover = jdbc.query("SELECT cutover_at FROM audit_chain_head WHERE singleton_id = 1",
                rs -> rs.next() ? rs.getTimestamp(1) : new Timestamp(0));
        Long count = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM audit_events WHERE event_hash IS NULL)
                     + (SELECT count(*) FROM audit_events WHERE chain_seq IS NULL AND event_hash IS NOT NULL AND occurred_at >= ?)
                """, Long.class, cutover);
        return count == null ? 0 : count;
    }

    static String material(String previous, long sequence, Instant occurredAt, Entry entry) {
        return previous + '|' + sequence + '|' + occurredAt + '|' + entry.actorId() + '|' + entry.method() + '|'
                + entry.resource() + '|' + entry.status() + '|' + entry.clientFingerprint();
    }

    private static Entry entry(Map<String, Object> row) {
        return new Entry((UUID) row.get("actor_id"), (String) row.get("action"), (String) row.get("resource"),
                ((Number) row.get("result_status")).intValue(), (String) row.get("client_fingerprint"));
    }

    /**
     * Result of {@link #verify()}: how many chained events verified, the first position that does not (or {@code null})
     * and how many stored events are not in the chain yet. Intact means a valid chain AND nothing left outside it.
     */
    public record Verification(long verified, Long firstBrokenSeq, long unchained) {
        public boolean intact() { return firstBrokenSeq == null && unchained == 0; }
    }

    /**
     * Recomputes every chained event (those stored since V103) in order, {@value #VERIFY_PAGE} rows at a time: each must
     * take the next position, name its predecessor's hash and hash to its stored value; the head must point at the last
     * one. Also counts the events still outside the chain.
     */
    public Verification verify() {
        String expectedPrevious = jdbc.queryForObject("SELECT genesis_hash FROM audit_chain_head WHERE singleton_id = 1", String.class).strip();
        long expectedSeq = 1;
        long after = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT chain_seq, occurred_at, actor_id, action, resource, result_status, client_fingerprint, previous_hash, event_hash
                    FROM audit_events WHERE chain_seq > ? ORDER BY chain_seq LIMIT ?
                    """, after, VERIFY_PAGE);
            for (Map<String, Object> row : rows) {
                long sequence = ((Number) row.get("chain_seq")).longValue();
                String previous = ((String) row.get("previous_hash")).strip();
                String hash = ((String) row.get("event_hash")).strip();
                boolean valid = sequence == expectedSeq && previous.equals(expectedPrevious)
                        && sha256(material(previous, sequence, ((Timestamp) row.get("occurred_at")).toInstant(), entry(row))).equals(hash);
                if (!valid) return new Verification(expectedSeq - 1, sequence, unchained());
                expectedPrevious = hash;
                expectedSeq++;
                after = sequence;
            }
            if (rows.size() < VERIFY_PAGE) break;
        }
        Map<String, Object> head = jdbc.queryForMap("SELECT last_seq, last_hash FROM audit_chain_head WHERE singleton_id = 1");
        if (((Number) head.get("last_seq")).longValue() != expectedSeq - 1 || !((String) head.get("last_hash")).strip().equals(expectedPrevious)) {
            return new Verification(expectedSeq - 1, expectedSeq, unchained());
        }
        return new Verification(expectedSeq - 1, null, unchained());
    }

    /** Lower-case hex SHA-256 of the UTF-8 bytes (the format of the pre-V103 hashes). */
    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(25L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
