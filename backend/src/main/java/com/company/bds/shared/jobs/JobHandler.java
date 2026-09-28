package com.company.bds.shared.jobs;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Processes the jobs of one queue; register one bean per queue and {@link JobWorker} polls it. Handlers must be
 * idempotent: delivery is at-least-once (a job whose lease expires, or whose batch exceeds {@link #timeout()}, runs again).
 */
public interface JobHandler {

    String queue();

    default int batchSize() { return 20; }

    /** How long a claimed batch is reserved for this worker before another worker may claim it again. */
    default Duration lease() { return Duration.ofMinutes(2); }

    /**
     * Time budget of one {@link #handle} call. When it is exceeded the worker interrupts the handler, fails the batch
     * (the jobs are retried) and pauses this queue on this instance until the handler thread returns. The worker caps it
     * at 90 % of {@link #lease()} so the batch never outlives its lease.
     */
    default Duration timeout() { return lease().multipliedBy(4).dividedBy(5); }

    /**
     * Attempts before a job is dead-lettered, unless the job carries its own {@code max_attempts} override. Read when a job
     * fails, so it applies to every queued job, whether it was enqueued from Java or with {@code bds_enqueue_job}.
     */
    default int maxAttempts() { return 10; }

    /** Top-level payload keys removed from the stored row once the job completes or is dead-lettered (secrets, links). */
    default Set<String> sensitivePayloadKeys() { return Set.of(); }

    /**
     * Return the ids that succeeded; throw or omit ids to fail them (the error message is recorded and the job retried
     * with backoff), or report {@link JobBatchResult.Builder#failPermanently permanent failures} to dead-letter them now.
     */
    JobBatchResult handle(List<ClaimedJob> jobs);
}
