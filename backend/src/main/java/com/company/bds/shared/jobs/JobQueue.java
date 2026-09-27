package com.company.bds.shared.jobs;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable queue backed by {@code background_jobs}. Enqueueing joins the caller's transaction (it is a plain insert on
 * the Spring-managed DataSource), so a job exists exactly when the business change that caused it commits.
 */
public interface JobQueue {

    /**
     * Enqueues a job. With a dedupe key, a pending job with the same key is coalesced: its payload is replaced,
     * {@code run_at} becomes the earliest of both and {@code enqueue_seq} is incremented. A job that is being processed
     * while it is coalesced runs again right after the current attempt, so the newer change is never lost.
     *
     * @param runAt {@code null} means now
     * @return the id of the new or coalesced job
     */
    UUID enqueue(String queue, @Nullable String dedupeKey, Map<String, ?> payload, @Nullable Instant runAt);

    /**
     * Enqueues at most once per {@code (queue, dedupeKey)}: nothing happens while a pending job or a retained completed
     * job (see {@code app.jobs.completed-retention}) with the key exists. For side effects that must not repeat, such as
     * e-mail. A dead-lettered job does not block a new one.
     *
     * @return the new job id, or empty when the key was already used
     */
    Optional<UUID> enqueueOnce(String queue, String dedupeKey, Map<String, ?> payload, @Nullable Instant runAt);
}
