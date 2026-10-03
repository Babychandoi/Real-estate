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
 * and reports the unchained backlog; operators run {@link #verifyIncrementally} through the admin endpoint
 * {@code POST /api/v1/admin/audit-chain/verification} (bounded per call, continues from a stored checkpoint).
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

    /**
     * Unchained rows in arrival order (database clock). {@code stored_at} is NULL for every pre-V103 row and set by the
     * database for every later one, also for rows the previous image inserts during a rolling deploy (V103), so this is
     * exactly what must be chained — whatever any application clock says. Served by idx_audit_events_unchained (V106).
     */
    private static final String PENDING = """
            SELECT id, occurred_at, actor_id, action, resource, result_status, client_fingerprint FROM audit_events
            WHERE chain_seq IS NULL AND stored_at IS NOT NULL ORDER BY stored_at, id LIMIT ?
            """;
    /** An event waits up to a second for the linker; only one still unchained after this long is a problem. */
    static final java.time.Duration UNCHAINED_GRACE = java.time.Duration.ofMinutes(5);
    /** Rows one incremental verification run checks at most (bounded request-thread cost). */
    public static final int VERIFY_MAX_ROWS_PER_RUN = 50_000;

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
            LinkBatch result;
            do {
                result = linkBatch(LINK_BATCH);
                total += result.linked();
            } while (result.linked() == LINK_BATCH);
            // SKIP LOCKED is a no-op, not proof that a linker completed. Otherwise a stuck head lock plus a stable
            // backlog could keep refreshing this gauge on every instance and mask BdsAuditChainStalled forever.
            if (result.acquiredHead()) lastLinkSuccess.set(Instant.now().getEpochSecond());
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
        return linkBatch(limit).linked();
    }

    private record LinkBatch(int linked, boolean acquiredHead) {}

    private LinkBatch linkBatch(int limit) {
        LinkBatch result = independent.execute(status -> {
            List<Map<String, Object>> head = jdbc.queryForList(
                    "SELECT last_seq, last_hash FROM audit_chain_head WHERE singleton_id = 1 FOR UPDATE SKIP LOCKED");
            if (head.isEmpty()) {
                recreateHeadIfMissing();
                return new LinkBatch(0, false);
            }
            long sequence = ((Number) head.get(0).get("last_seq")).longValue();
            String previous = ((String) head.get(0).get("last_hash")).strip();
            List<Map<String, Object>> pending = jdbc.queryForList(PENDING, limit);
            if (pending.isEmpty()) return new LinkBatch(0, true);
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
            return new LinkBatch(pending.size(), true);
        });
        return result == null ? new LinkBatch(0, false) : result;
    }

    private void recreateHeadIfMissing() {
        Integer heads = jdbc.queryForObject("SELECT count(*) FROM audit_chain_head", Integer.class);
        if (heads != null && heads > 0) return; // locked by another linker
        List<Map<String, Object>> last = jdbc.queryForList(
                "SELECT chain_seq, event_hash FROM audit_events WHERE chain_seq IS NOT NULL ORDER BY chain_seq DESC LIMIT 1");
        List<String> first = jdbc.queryForList("SELECT previous_hash FROM audit_events WHERE chain_seq = 1", String.class);
        String legacyLatest = jdbc.query("SELECT TRIM(event_hash) FROM audit_events WHERE stored_at IS NULL AND event_hash IS NOT NULL "
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
        Long count = jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE chain_seq IS NULL AND stored_at IS NOT NULL", Long.class);
        return count == null ? 0 : count;
    }

    /** Unchained events older than {@link #UNCHAINED_GRACE}: the linker is behind or something keeps them out. */
    public long staleUnchained() {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM audit_events WHERE chain_seq IS NULL AND stored_at IS NOT NULL AND stored_at < now() - make_interval(secs => ?)
                """, Long.class, UNCHAINED_GRACE.toSeconds());
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
     * Result of a verification: how many chained events verified, the first position that does not (or {@code null}),
     * how many stored events are not chained yet ({@code unchained}, normally a few awaiting the next 1 s linking run)
     * and how many of those are older than {@link #UNCHAINED_GRACE} ({@code staleUnchained}). Intact means a valid chain
     * and nothing stuck outside it; events inside the normal linking delay do not make it non-intact.
     */
    public record Verification(long verified, Long firstBrokenSeq, long unchained, long staleUnchained) {
        public boolean intact() { return firstBrokenSeq == null && staleUnchained == 0; }
    }

    /**
     * Recomputes the whole chain from position 1, {@value #VERIFY_PAGE} rows at a time. The head is read FIRST and only
     * positions up to its {@code last_seq} are checked: rows the linker appends while this runs are outside the snapshot
     * and cannot make an intact chain look broken (W6 review round 2). Linked rows never change, so the prefix is stable.
     */
    public Verification verify() {
        Segment segment = verifySegment(0, null, Long.MAX_VALUE);
        return new Verification(segment.verifiedThrough(), segment.brokenSeq(), unchained(), staleUnchained());
    }

    /** Result of {@link #verifyIncrementally}: the range checked by this run and where the chain stands. */
    public record IncrementalVerification(long fromSeq, long verifiedThrough, long headSeq, boolean complete, Long firstBrokenSeq,
                                          long unchained, long staleUnchained, Instant checkpointAt) {
        public boolean intact() { return firstBrokenSeq == null && staleUnchained == 0; }
    }

    /**
     * Continues from the stored checkpoint (the last position a previous run proved intact) for at most
     * {@code maxRows} positions, and moves the checkpoint forward when they verify. Bounded cost per call; a full
     * re-check is {@link #resetCheckpoint()} followed by runs until {@code complete}.
     */
    public IncrementalVerification verifyIncrementally(int maxRows) {
        // Serialize operator runs with restart across instances: an in-flight run cannot undo a reset checkpoint.
        // This lock belongs only to verification metadata; request audit inserts and linking never wait for it.
        return independent.execute(status -> verifyFromCheckpoint(Math.min(VERIFY_MAX_ROWS_PER_RUN, Math.max(1, maxRows))));
    }

    private IncrementalVerification verifyFromCheckpoint(int maxRows) {
        Map<String, Object> checkpoint = jdbc.queryForMap("SELECT verified_seq, verified_hash FROM audit_chain_checkpoint WHERE singleton_id = 1 FOR UPDATE");
        long fromSeq = ((Number) checkpoint.get("verified_seq")).longValue();
        String fromHash = (String) checkpoint.get("verified_hash");
        Segment segment = verifySegment(fromSeq, fromHash == null ? null : fromHash.strip(), Math.max(1, maxRows));
        Instant now = Instant.now();
        if (segment.brokenSeq() == null) {
            jdbc.update("""
                    UPDATE audit_chain_checkpoint SET verified_seq = ?, verified_hash = ?, verified_at = ?, broken_seq = NULL
                    WHERE singleton_id = 1 AND verified_seq = ?
                    """, segment.verifiedThrough(), segment.lastHash(), Timestamp.from(now), fromSeq);
        } else {
            jdbc.update("UPDATE audit_chain_checkpoint SET broken_seq = ?, verified_at = ? WHERE singleton_id = 1",
                    segment.brokenSeq(), Timestamp.from(now));
        }
        return new IncrementalVerification(fromSeq, segment.verifiedThrough(), segment.headSeq(),
                segment.brokenSeq() == null && segment.verifiedThrough() >= segment.headSeq(), segment.brokenSeq(), unchained(),
                staleUnchained(), now);
    }

    /** Forgets the checkpoint: the next incremental runs re-verify the chain from position 1. */
    public void resetCheckpoint() {
        jdbc.update("UPDATE audit_chain_checkpoint SET verified_seq = 0, verified_hash = NULL, verified_at = NULL, broken_seq = NULL WHERE singleton_id = 1");
    }

    private record Segment(long verifiedThrough, String lastHash, Long brokenSeq, long headSeq) {}

    /** Verifies positions (fromSeq, min(head, fromSeq + maxRows)]; {@code fromHash} is the hash at fromSeq (genesis for 0). */
    private Segment verifySegment(long fromSeq, String fromHash, long maxRows) {
        // Head first: everything up to last_seq is committed and immutable when it is read (see verify()).
        Map<String, Object> head = jdbc.queryForMap("SELECT last_seq, last_hash, genesis_hash FROM audit_chain_head WHERE singleton_id = 1");
        long headSeq = ((Number) head.get("last_seq")).longValue();
        String headHash = ((String) head.get("last_hash")).strip();
        String expectedPrevious = fromSeq == 0 || fromHash == null ? ((String) head.get("genesis_hash")).strip() : fromHash;
        if (fromSeq > headSeq) return new Segment(fromSeq, expectedPrevious, headSeq + 1, headSeq);
        long until = Math.min(headSeq, fromSeq + maxRows);
        long expectedSeq = fromSeq + 1;
        long after = fromSeq;
        while (after < until) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT chain_seq, occurred_at, actor_id, action, resource, result_status, client_fingerprint, previous_hash, event_hash
                    FROM audit_events WHERE chain_seq > ? AND chain_seq <= ? ORDER BY chain_seq LIMIT ?
                    """, after, until, VERIFY_PAGE);
            if (rows.isEmpty()) break;
            for (Map<String, Object> row : rows) {
                long sequence = ((Number) row.get("chain_seq")).longValue();
                String previous = ((String) row.get("previous_hash")).strip();
                String hash = ((String) row.get("event_hash")).strip();
                boolean valid = sequence == expectedSeq && previous.equals(expectedPrevious)
                        && sha256(material(previous, sequence, ((Timestamp) row.get("occurred_at")).toInstant(), entry(row))).equals(hash);
                if (!valid) return new Segment(expectedSeq - 1, expectedPrevious, sequence, headSeq);
                expectedPrevious = hash;
                expectedSeq++;
                after = sequence;
            }
        }
        if (after < until) return new Segment(after, expectedPrevious, after + 1, headSeq); // a position is missing
        if (until == headSeq && !headHash.equals(expectedPrevious)) return new Segment(after, expectedPrevious, headSeq, headSeq);
        return new Segment(after, expectedPrevious, null, headSeq);
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
