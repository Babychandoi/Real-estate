package com.company.bds.shared.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A job reserved by one worker. {@code attempts} counts earlier failed attempts of the current payload version's history;
 * {@code maxAttempts} is the effective limit (the job's override or the handler's {@link JobHandler#maxAttempts()}).
 * {@code expiredAttempt} is true when this claim recovered a lease that expired while the same version was being
 * processed (that expiry was counted as an attempt). {@code enqueueSeq} and {@code leaseToken} guard completion.
 */
public record ClaimedJob(UUID id, String queue, @Nullable String dedupeKey, JsonNode payload, int attempts, int maxAttempts,
                         long enqueueSeq, UUID leaseToken, Instant runAt, Instant createdAt, boolean expiredAttempt) {

    /** Text value of a top-level payload field, or {@code null} when absent. */
    @Nullable
    public String text(String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
