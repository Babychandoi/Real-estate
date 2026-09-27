package com.company.bds.shared.jobs;

import com.company.bds.shared.scheduling.ScheduledTaskLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Purges completed jobs older than {@code app.jobs.completed-retention} (7 days); dead-lettered jobs are kept for operators. */
@Component
public class JobRetentionTask {
    private static final Logger log = LoggerFactory.getLogger(JobRetentionTask.class);
    static final String LOCK_NAME = "background-jobs-purge";

    private final JobStore store;
    private final ScheduledTaskLock lock;
    private final Clock clock;
    private final Duration retention;

    public JobRetentionTask(JobStore store, ScheduledTaskLock lock, Clock clock,
                            @Value("${app.jobs.completed-retention:P7D}") Duration retention) {
        this.store = store;
        this.lock = lock;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(cron = "${app.jobs.purge-cron:0 17 * * * *}")
    public void purgeCompletedJobs() {
        lock.runExclusive(LOCK_NAME, Duration.ofMinutes(10), Duration.ofMinutes(1), this::purgeNow);
    }

    /** Deletes completed jobs older than the retention now; returns the number of deleted rows. */
    public int purgeNow() {
        Instant cutoff = clock.instant().minus(retention);
        int deleted = store.purgeCompletedBefore(cutoff, 5_000);
        if (deleted > 0) log.info("background_jobs_purged deleted={} completed_before={}", deleted, cutoff);
        return deleted;
    }
}
