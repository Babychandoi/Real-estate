package com.company.bds.shared.jobs;

import java.time.Duration;
import java.util.List;

/**
 * Processes the jobs of one queue; register one bean per queue and {@link JobWorker} polls it. Handlers must be
 * idempotent: delivery is at-least-once (a job whose lease expires before completion is claimed again).
 */
public interface JobHandler {

    String queue();

    default int batchSize() { return 20; }

    /** How long a claimed batch is reserved for this worker before another worker may claim it again. */
    default Duration lease() { return Duration.ofMinutes(2); }

    /** Attempts before a job is dead-lettered; applied to jobs enqueued through {@link JobQueue}. */
    default int maxAttempts() { return 10; }

    /** Return the ids that succeeded; throw or omit ids to fail them (the error message is recorded). */
    JobBatchResult handle(List<ClaimedJob> jobs);
}
