package com.company.bds.shared.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Claim/complete/fail operations of the worker. Each call is one autocommitted statement (or two for a guarded release),
 * never part of a business transaction: a lease is visible to other workers as soon as it is taken. Every statement has a
 * {@value #QUERY_TIMEOUT_SECONDS} s query timeout so a stuck database cannot freeze the worker thread indefinitely.
 *
 * <p>Version rule: a failure (or an expired lease) counts as an attempt only while the job's {@code enqueue_seq} still
 * equals the version that was claimed. If a newer change was coalesced meanwhile, the job is released for an immediate
 * run of the new version instead, so a change that was never attempted is never backed off or dead-lettered.
 */
@Component
public class JobStore {
    static final int QUERY_TIMEOUT_SECONDS = 30;
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final Pattern PAYLOAD_KEY = Pattern.compile("[A-Za-z0-9_]{1,60}");

    public enum CompletionOutcome { COMPLETED, RERUN, LEASE_LOST }

    public enum FailureOutcome { RETRY, DEAD, RERUN, LEASE_LOST }

    /** Pending/dead counts and the run_at of the oldest due pending job ({@code null} when none is due). */
    public record QueueStats(String queue, long pending, long dead, @Nullable Instant oldestDueRunAt) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public JobStore(DataSource dataSource, ObjectMapper json) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        this.jdbc = new NamedParameterJdbcTemplate(template);
        this.json = json;
    }

    /**
     * Reserves up to {@code limit} due jobs of the queue ({@code FOR UPDATE SKIP LOCKED}, so concurrent workers get
     * disjoint batches). A job whose lease expired is due again; that expiry counts as a failed attempt when the same
     * version was being processed, so a job that keeps killing its worker is eventually dead-lettered.
     *
     * @param defaultMaxAttempts the handler's limit, used for jobs without their own {@code max_attempts} override
     */
    public List<ClaimedJob> claim(String queue, int limit, Duration lease, String workerId, int defaultMaxAttempts) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queue", queue).addValue("limit", Math.max(1, limit)).addValue("worker", workerId)
                .addValue("lease", seconds(lease)).addValue("token", UUID.randomUUID()).addValue("max", defaultMaxAttempts);
        List<ClaimedJob> jobs = new ArrayList<>(jdbc.query("""
                WITH due AS (
                    SELECT id,
                           locked_until IS NOT NULL AS lease_expired,
                           claimed_seq IS NOT DISTINCT FROM enqueue_seq AS same_version
                    FROM background_jobs
                    WHERE queue = :queue AND completed_at IS NULL AND dead_lettered_at IS NULL AND run_at <= now()
                      AND (locked_until IS NULL OR locked_until < now())
                    ORDER BY run_at, id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE background_jobs j
                SET attempts = j.attempts + CASE WHEN due.lease_expired AND due.same_version THEN 1 ELSE 0 END,
                    last_error = CASE WHEN due.lease_expired THEN 'Lease expired before the job completed' ELSE j.last_error END,
                    claimed_seq = j.enqueue_seq,
                    locked_by = :worker, locked_until = now() + make_interval(secs => :lease), lease_token = :token,
                    updated_at = now()
                FROM due
                WHERE j.id = due.id
                RETURNING j.id, j.queue, j.dedupe_key, j.payload::text AS payload, j.attempts,
                          COALESCE(j.max_attempts, :max) AS max_attempts, j.enqueue_seq, j.lease_token, j.run_at,
                          j.created_at, due.lease_expired AND due.same_version AS expired_attempt
                """, params, (rs, row) -> {
            try {
                return new ClaimedJob(rs.getObject("id", UUID.class), rs.getString("queue"), rs.getString("dedupe_key"),
                        json.readTree(rs.getString("payload")), rs.getInt("attempts"), rs.getInt("max_attempts"),
                        rs.getLong("enqueue_seq"), rs.getObject("lease_token", UUID.class),
                        rs.getTimestamp("run_at").toInstant(), rs.getTimestamp("created_at").toInstant(),
                        rs.getBoolean("expired_attempt"));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Stored job payload is not JSON", ex);
            }
        }));
        jobs.sort(Comparator.comparing(ClaimedJob::runAt).thenComparing(ClaimedJob::id));
        return jobs;
    }

    /**
     * Completes the job only while this worker still holds the lease and nobody coalesced a newer change into it; the
     * {@code scrubKeys} are removed from the stored payload. When the sequence changed, the job is released for an
     * immediate re-run (attempts unchanged) instead, so the change is processed.
     */
    public CompletionOutcome complete(ClaimedJob job, Collection<String> scrubKeys) {
        MapSqlParameterSource params = guard(job).addValue("scrub", keys(scrubKeys));
        int completed = jdbc.update("""
                UPDATE background_jobs
                SET completed_at = now(), payload = payload - CAST(:scrub AS text[]), locked_by = NULL, locked_until = NULL,
                    lease_token = NULL, last_error = NULL, updated_at = now()
                WHERE id = :id AND lease_token = :token AND enqueue_seq = :seq AND completed_at IS NULL AND dead_lettered_at IS NULL
                """, params);
        if (completed == 1) return CompletionOutcome.COMPLETED;
        return release(job) ? CompletionOutcome.RERUN : CompletionOutcome.LEASE_LOST;
    }

    /**
     * Records a failed attempt: retry after {@code retryDelay}, or dead-letter (and scrub) once the effective limit is
     * reached. When a newer change was coalesced during the attempt the job is released for an immediate run of that
     * change without counting the attempt ({@link FailureOutcome#RERUN}).
     */
    public FailureOutcome fail(ClaimedJob job, String error, Duration retryDelay, Collection<String> scrubKeys) {
        MapSqlParameterSource params = guard(job).addValue("delay", seconds(retryDelay)).addValue("max", job.maxAttempts())
                .addValue("scrub", keys(scrubKeys)).addValue("error", truncate(error));
        List<FailureOutcome> outcome = jdbc.query("""
                UPDATE background_jobs
                SET attempts = CASE WHEN enqueue_seq = :seq THEN attempts + 1 ELSE attempts END,
                    run_at = CASE WHEN enqueue_seq = :seq THEN now() + make_interval(secs => :delay) ELSE LEAST(run_at, now()) END,
                    dead_lettered_at = CASE WHEN enqueue_seq = :seq AND attempts + 1 >= :max THEN now() END,
                    payload = CASE WHEN enqueue_seq = :seq AND attempts + 1 >= :max THEN payload - CAST(:scrub AS text[])
                                   ELSE payload END,
                    locked_by = NULL, locked_until = NULL, lease_token = NULL, last_error = :error, updated_at = now()
                WHERE id = :id AND lease_token = :token AND completed_at IS NULL AND dead_lettered_at IS NULL
                RETURNING enqueue_seq = :seq AS counted, dead_lettered_at IS NOT NULL AS dead
                """, params, (rs, row) -> !rs.getBoolean("counted") ? FailureOutcome.RERUN
                : rs.getBoolean("dead") ? FailureOutcome.DEAD : FailureOutcome.RETRY);
        return outcome.isEmpty() ? FailureOutcome.LEASE_LOST : outcome.get(0);
    }

    /**
     * Dead-letters a claimed job now (and scrubs it): a permanent failure ({@code countAttempt = true}) or a job whose
     * attempts were exhausted by expired leases ({@code false}, already counted at claim). If a newer change was coalesced
     * since the claim, that change is released for a fresh run instead ({@link FailureOutcome#RERUN}).
     */
    public FailureOutcome deadLetter(ClaimedJob job, String reason, boolean countAttempt, Collection<String> scrubKeys) {
        MapSqlParameterSource params = guard(job).addValue("count", countAttempt).addValue("scrub", keys(scrubKeys))
                .addValue("error", truncate(reason));
        List<FailureOutcome> outcome = jdbc.query("""
                UPDATE background_jobs
                SET attempts = CASE WHEN enqueue_seq = :seq AND CAST(:count AS boolean) THEN attempts + 1 ELSE attempts END,
                    dead_lettered_at = CASE WHEN enqueue_seq = :seq THEN now() END,
                    run_at = CASE WHEN enqueue_seq = :seq THEN run_at ELSE LEAST(run_at, now()) END,
                    payload = CASE WHEN enqueue_seq = :seq THEN payload - CAST(:scrub AS text[]) ELSE payload END,
                    locked_by = NULL, locked_until = NULL, lease_token = NULL, last_error = :error, updated_at = now()
                WHERE id = :id AND lease_token = :token AND completed_at IS NULL AND dead_lettered_at IS NULL
                RETURNING dead_lettered_at IS NOT NULL AS dead
                """, params, (rs, row) -> rs.getBoolean("dead") ? FailureOutcome.DEAD : FailureOutcome.RERUN);
        return outcome.isEmpty() ? FailureOutcome.LEASE_LOST : outcome.get(0);
    }

    /** Gives a claimed job back without counting an attempt (the worker is stopping); returns whether the lease was held. */
    public boolean release(ClaimedJob job) {
        return jdbc.update("""
                UPDATE background_jobs
                SET locked_by = NULL, locked_until = NULL, lease_token = NULL, run_at = LEAST(run_at, now()), updated_at = now()
                WHERE id = :id AND lease_token = :token AND completed_at IS NULL AND dead_lettered_at IS NULL
                """, guard(job)) == 1;
    }

    /** Deletes completed jobs older than the cutoff in bounded batches; returns the number of deleted rows. */
    public int purgeCompletedBefore(Instant cutoff, int batchSize) {
        return purge("completed_at", cutoff, batchSize);
    }

    /** Deletes dead-lettered jobs older than the cutoff (operators had that long to inspect them). */
    public int purgeDeadBefore(Instant cutoff, int batchSize) {
        return purge("dead_lettered_at", cutoff, batchSize);
    }

    private int purge(String column, Instant cutoff, int batchSize) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("cutoff", Timestamp.from(cutoff)).addValue("batch", batchSize);
        String sql = "DELETE FROM background_jobs WHERE id IN (SELECT id FROM background_jobs WHERE " + column
                + " < :cutoff ORDER BY " + column + " LIMIT :batch)";
        int total = 0;
        int deleted;
        do {
            deleted = jdbc.update(sql, params);
            total += deleted;
        } while (deleted == batchSize);
        return total;
    }

    /** Pending count, dead-letter count and oldest due pending job per queue (two indexed aggregate queries). */
    public List<QueueStats> stats() {
        Map<String, long[]> counts = new HashMap<>();
        Map<String, Instant> oldestDue = new HashMap<>();
        MapSqlParameterSource none = new MapSqlParameterSource();
        jdbc.query("""
                SELECT queue, COUNT(*) AS pending, MIN(run_at) FILTER (WHERE run_at <= now()) AS oldest_due
                FROM background_jobs WHERE completed_at IS NULL AND dead_lettered_at IS NULL GROUP BY queue
                """, none, rs -> {
            counts.computeIfAbsent(rs.getString("queue"), q -> new long[2])[0] = rs.getLong("pending");
            Timestamp due = rs.getTimestamp("oldest_due");
            if (due != null) oldestDue.put(rs.getString("queue"), due.toInstant());
        });
        jdbc.query("SELECT queue, COUNT(*) AS dead FROM background_jobs WHERE dead_lettered_at IS NOT NULL GROUP BY queue", none,
                rs -> { counts.computeIfAbsent(rs.getString("queue"), q -> new long[2])[1] = rs.getLong("dead"); });
        List<QueueStats> stats = new ArrayList<>();
        counts.forEach((queue, values) -> stats.add(new QueueStats(queue, values[0], values[1], oldestDue.get(queue))));
        return stats;
    }

    private static MapSqlParameterSource guard(ClaimedJob job) {
        return new MapSqlParameterSource().addValue("id", job.id()).addValue("token", job.leaseToken()).addValue("seq", job.enqueueSeq());
    }

    /** PostgreSQL array literal of payload keys ({@code {a,b}}); keys are plain identifiers. */
    private static String keys(Collection<String> scrubKeys) {
        for (String key : scrubKeys) {
            if (!PAYLOAD_KEY.matcher(key).matches()) throw new IllegalArgumentException("Invalid payload key " + key);
        }
        return "{" + String.join(",", scrubKeys) + "}";
    }

    private static double seconds(Duration duration) {
        return duration.toMillis() / 1000.0;
    }

    private static String truncate(String error) {
        String value = error == null || error.isBlank() ? "Job failed" : error;
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
