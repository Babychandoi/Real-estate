package com.company.bds.shared.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * {@link JobQueue} on {@code background_jobs}; coalescing is implemented once, in SQL ({@code bds_enqueue_job}). Jobs are
 * stored without a {@code max_attempts} override, so the handler's {@link JobHandler#maxAttempts()} applies when they fail.
 */
@Component
class JdbcJobQueue implements JobQueue {
    private static final Pattern QUEUE_NAME = Pattern.compile("[a-z0-9][a-z0-9._-]{0,59}");
    private static final int MAX_DEDUPE_KEY = 200;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcJobQueue(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public UUID enqueue(String queue, @Nullable String dedupeKey, Map<String, ?> payload, @Nullable Instant runAt) {
        validate(queue, dedupeKey);
        return jdbc.queryForObject("SELECT bds_enqueue_job(?, CAST(? AS text), CAST(? AS jsonb), CAST(? AS timestamptz))",
                UUID.class, queue, dedupeKey, serialize(payload), timestamp(runAt));
    }

    @Override
    public Optional<UUID> enqueueOnce(String queue, String dedupeKey, Map<String, ?> payload, @Nullable Instant runAt) {
        validate(queue, Objects.requireNonNull(dedupeKey, "dedupeKey is required for enqueueOnce"));
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO background_jobs (id, queue, dedupe_key, payload, run_at)
                SELECT ?, ?, ?, CAST(? AS jsonb), COALESCE(CAST(? AS timestamptz), now())
                WHERE NOT EXISTS (SELECT 1 FROM background_jobs
                                  WHERE queue = ? AND dedupe_key = ? AND dead_lettered_at IS NULL)
                ON CONFLICT (queue, dedupe_key) WHERE completed_at IS NULL AND dead_lettered_at IS NULL AND dedupe_key IS NOT NULL
                DO NOTHING
                """, id, queue, dedupeKey, serialize(payload), timestamp(runAt), queue, dedupeKey);
        return inserted == 1 ? Optional.of(id) : Optional.empty();
    }

    private static void validate(String queue, @Nullable String dedupeKey) {
        if (queue == null || !QUEUE_NAME.matcher(queue).matches()) {
            throw new IllegalArgumentException("Queue name must match " + QUEUE_NAME.pattern());
        }
        if (dedupeKey != null && (dedupeKey.isBlank() || dedupeKey.length() > MAX_DEDUPE_KEY)) {
            throw new IllegalArgumentException("Dedupe key must be 1-" + MAX_DEDUPE_KEY + " characters and not blank");
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
