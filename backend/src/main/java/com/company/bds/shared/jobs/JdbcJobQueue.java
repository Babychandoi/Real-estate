package com.company.bds.shared.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** {@link JobQueue} on {@code background_jobs}; coalescing is implemented once, in SQL ({@code bds_enqueue_job}). */
@Component
class JdbcJobQueue implements JobQueue {
    private static final Pattern QUEUE_NAME = Pattern.compile("[a-z0-9][a-z0-9._-]{0,59}");
    private static final int MAX_DEDUPE_KEY = 200;
    private static final int DEFAULT_MAX_ATTEMPTS = 10;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ObjectProvider<JobHandler> handlers;
    private volatile Map<String, Integer> maxAttemptsByQueue;

    JdbcJobQueue(JdbcTemplate jdbc, ObjectMapper json, ObjectProvider<JobHandler> handlers) {
        this.jdbc = jdbc;
        this.json = json;
        this.handlers = handlers;
    }

    @Override
    public UUID enqueue(String queue, @Nullable String dedupeKey, Map<String, ?> payload, @Nullable Instant runAt) {
        validate(queue, dedupeKey);
        return jdbc.queryForObject("SELECT bds_enqueue_job(?, CAST(? AS text), CAST(? AS jsonb), CAST(? AS timestamptz), ?)",
                UUID.class, queue, dedupeKey, serialize(payload), timestamp(runAt), maxAttempts(queue));
    }

    @Override
    public Optional<UUID> enqueueOnce(String queue, String dedupeKey, Map<String, ?> payload, @Nullable Instant runAt) {
        validate(queue, Objects.requireNonNull(dedupeKey, "dedupeKey is required for enqueueOnce"));
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO background_jobs (id, queue, dedupe_key, payload, run_at, max_attempts)
                SELECT ?, ?, ?, CAST(? AS jsonb), COALESCE(CAST(? AS timestamptz), now()), ?
                WHERE NOT EXISTS (SELECT 1 FROM background_jobs
                                  WHERE queue = ? AND dedupe_key = ? AND dead_lettered_at IS NULL)
                ON CONFLICT (queue, dedupe_key) WHERE completed_at IS NULL AND dead_lettered_at IS NULL AND dedupe_key IS NOT NULL
                DO NOTHING
                """, id, queue, dedupeKey, serialize(payload), timestamp(runAt), maxAttempts(queue), queue, dedupeKey);
        return inserted == 1 ? Optional.of(id) : Optional.empty();
    }

    private int maxAttempts(String queue) {
        Map<String, Integer> byQueue = maxAttemptsByQueue;
        if (byQueue == null) {
            // JobWorker rejects two handlers for one queue at startup; keep the first here so enqueue never fails on it.
            byQueue = handlers.orderedStream()
                    .collect(Collectors.toUnmodifiableMap(JobHandler::queue, JobHandler::maxAttempts, (first, second) -> first));
            maxAttemptsByQueue = byQueue;
        }
        return byQueue.getOrDefault(queue, DEFAULT_MAX_ATTEMPTS);
    }

    private static void validate(String queue, @Nullable String dedupeKey) {
        if (queue == null || !QUEUE_NAME.matcher(queue).matches()) {
            throw new IllegalArgumentException("Queue name must match " + QUEUE_NAME.pattern());
        }
        if (dedupeKey != null && (dedupeKey.isBlank() || dedupeKey.length() > MAX_DEDUPE_KEY)) {
            throw new IllegalArgumentException("Dedupe key must be 1-" + MAX_DEDUPE_KEY + " characters");
        }
    }

    private String serialize(Map<String, ?> payload) {
        try {
            return json.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Job payload is not serializable to JSON", ex);
        }
    }

    @Nullable
    private static Timestamp timestamp(@Nullable Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
