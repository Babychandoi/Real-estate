package com.company.bds.shared.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A job reserved by one worker. {@code attempts} counts earlier failed attempts; {@code enqueueSeq} and
 * {@code leaseToken} guard completion (see {@link JobStore#complete}).
 */
public record ClaimedJob(UUID id, String queue, @Nullable String dedupeKey, JsonNode payload, int attempts, int maxAttempts,
                         long enqueueSeq, UUID leaseToken, Instant runAt, Instant createdAt) {

    /** Text value of a top-level payload field, or {@code null} when absent. */
    @Nullable
    public String text(String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
