package com.company.bds.shared.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Claim/complete/fail operations of the worker. Each call is one autocommitted statement (or two for a guarded release),
 * never part of a business transaction: a lease is visible to other workers as soon as it is taken.
 */
@Component
public class JobStore {
    private static final int MAX_ERROR_LENGTH = 2000;

    public enum CompletionOutcome { COMPLETED, RERUN, LEASE_LOST }

    public enum FailureOutcome { RETRY, DEAD, LEASE_LOST }

    public record QueueStats(String queue, long pending, long dead, double lagSeconds) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JobStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Reserves up to {@code limit} due jobs of the queue ({@code FOR UPDATE SKIP LOCKED}, so concurrent workers get
     * disjoint batches). A job whose lease expired is due again and that expiry counts as a failed attempt, so a job that
     * keeps killing its worker is eventually dead-lettered ({@link JobWorker} checks {@code attempts >= maxAttempts}).
     */
    public List<ClaimedJob> claim(String queue, int limit, Duration lease, String workerId) {
        UUID leaseToken = UUID.randomUUID();
        List<ClaimedJob> jobs = new ArrayList<>(jdbc.query("""
                WITH due AS (
                    SELECT id, locked_until IS NOT NULL AS lease_expired FROM background_jobs
                    WHERE queue = ? AND completed_at IS NULL AND dead_lettered_at IS NULL AND run_at <= now()
                      AND (locked_until IS NULL OR locked_until < now())
                    ORDER BY run_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE background_jobs j
                SET attempts = j.attempts + CASE WHEN due.lease_expired THEN 1 ELSE 0 END,
                    last_error = CASE WHEN due.lease_expired THEN 'Lease expired before the job completed' ELSE j.last_error END,
                    locked_by = ?, locked_until = now() + make_interval(secs => ?), lease_token = ?, updated_at = now()
                FROM due
                WHERE j.id = due.id
                RETURNING j.id, j.queue, j.dedupe_key, j.payload::text AS payload, j.attempts, j.max_attempts, j.enqueue_seq,
                          j.lease_token, j.run_at, j.created_at
                """, (rs, row) -> {
            try {
                return new ClaimedJob(rs.getObject("id", UUID.class), rs.getString("queue"), rs.getString("dedupe_key"),
                        json.readTree(rs.getString("payload")), rs.getInt("attempts"), rs.getInt("max_attempts"),
                        rs.getLong("enqueue_seq"), rs.getObject("lease_token", UUID.class),
                        rs.getTimestamp("run_at").toInstant(), rs.getTimestamp("created_at").toInstant());
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Stored job payload is not JSON", ex);
            }
        }, queue, Math.max(1, limit), workerId, lease.toMillis() / 1000.0, leaseToken));
        jobs.sort(Comparator.comparing(ClaimedJob::runAt).thenComparing(ClaimedJob::id));
        return jobs;
    }

    /**
     * Completes the job only while this worker still holds the lease and nobody coalesced a newer change into it. When the
     * sequence changed, the job is released for an immediate re-run (attempts unchanged) instead, so the change is processed.
     */
    public CompletionOutcome complete(ClaimedJob job) {
        int completed = jdbc.update("""
                UPDATE background_jobs
                SET completed_at = now(), locked_by = NULL, locked_until = NULL, lease_token = NULL, last_error = NULL, updated_at = now()
                WHERE id = ? AND lease_token = ? AND enqueue_seq = ? AND completed_at IS NULL AND dead_lettered_at IS NULL
                """, job.id(), job.leaseToken(), job.enqueueSeq());
        if (completed == 1) return CompletionOutcome.COMPLETED;
        int released = jdbc.update("""
                UPDATE background_jobs
                SET locked_by = NULL, locked_until = NULL, lease_token = NULL, run_at = LEAST(run_at, now()), updated_at = now()
                WHERE id = ? AND lease_token = ? AND completed_at IS NULL AND dead_lettered_at IS NULL
                """, job.id(), job.leaseToken());
        return released == 1 ? CompletionOutcome.RERUN : CompletionOutcome.LEASE_LOST;
    }

    /** Records a failed attempt: retry after {@code retryDelay}, or dead-letter once {@code max_attempts} is reached. */
    public FailureOutcome fail(ClaimedJob job, String error, Duration retryDelay) {
        List<Boolean> dead = jdbc.query("""
                UPDATE background_jobs
                SET attempts = attempts + 1,
                    run_at = now() + make_interval(secs => ?),
                    dead_lettered_at = CASE WHEN attempts + 1 >= max_attempts THEN now() END,
                    locked_by = NULL, locked_until = NULL, lease_token = NULL, last_error = ?, updated_at = now()
                WHERE id = ? AND lease_token = ? AND completed_at IS NULL AND dead_lettered_at IS NULL
                RETURNING dead_lettered_at IS NOT NULL
                """, (rs, row) -> rs.getBoolean(1), retryDelay.toMillis() / 1000.0, truncate(error), job.id(), job.leaseToken());
        if (dead.isEmpty()) return FailureOutcome.LEASE_LOST;
        return dead.get(0) ? FailureOutcome.DEAD : FailureOutcome.RETRY;
    }

    /** Dead-letters a claimed job without another attempt (used when its attempts are already exhausted). */
    public FailureOutcome deadLetter(ClaimedJob job, String reason) {
        int updated = jdbc.update("""
                UPDATE background_jobs
                SET dead_lettered_at = now(), locked_by = NULL, locked_until = NULL, lease_token = NULL, last_error = ?, updated_at = now()
                WHERE id = ? AND lease_token = ? AND completed_at IS NULL AND dead_lettered_at IS NULL
                """, truncate(reason), job.id(), job.leaseToken());
        return updated == 1 ? FailureOutcome.DEAD : FailureOutcome.LEASE_LOST;
    }

    /** Deletes completed jobs older than the cutoff in bounded batches; returns the number of deleted rows. */
    public int purgeCompletedBefore(Instant cutoff, int batchSize) {
        int total = 0;
        int deleted;
        do {
            deleted = jdbc.update("""
                    DELETE FROM background_jobs WHERE id IN (
                        SELECT id FROM background_jobs WHERE completed_at < ? ORDER BY completed_at LIMIT ?)
                    """, Timestamp.from(cutoff), batchSize);
            total += deleted;
        } while (deleted == batchSize);
        return total;
    }

    /** Pending count, dead-letter count and lag (age of the oldest due pending job) per queue. */
    public List<QueueStats> stats() {
        Map<String, long[]> counts = new HashMap<>();
        Map<String, Double> lags = new HashMap<>();
        jdbc.query("""
                SELECT queue, COUNT(*) AS pending,
                       COALESCE(EXTRACT(EPOCH FROM (now() - MIN(run_at) FILTER (WHERE run_at <= now()))), 0) AS lag_seconds
                FROM background_jobs WHERE completed_at IS NULL AND dead_lettered_at IS NULL GROUP BY queue
                """, rs -> {
            counts.computeIfAbsent(rs.getString("queue"), q -> new long[2])[0] = rs.getLong("pending");
            lags.put(rs.getString("queue"), rs.getDouble("lag_seconds"));
        });
        jdbc.query("SELECT queue, COUNT(*) FROM background_jobs WHERE dead_lettered_at IS NOT NULL GROUP BY queue",
                rs -> { counts.computeIfAbsent(rs.getString(1), q -> new long[2])[1] = rs.getLong(2); });
        List<QueueStats> stats = new ArrayList<>();
        counts.forEach((queue, values) -> stats.add(new QueueStats(queue, values[0], values[1], lags.getOrDefault(queue, 0.0))));
        return stats;
    }

    private static String truncate(String error) {
        String value = error == null || error.isBlank() ? "Job failed" : error;
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
