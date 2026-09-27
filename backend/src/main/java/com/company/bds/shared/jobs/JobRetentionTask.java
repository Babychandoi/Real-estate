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

/**
 * Purges completed jobs older than {@code app.jobs.completed-retention} (7 days) and dead-lettered jobs older than
 * {@code app.jobs.dead-retention} (30 days; their sensitive payload keys were already scrubbed when they died).
 */
@Component
public class JobRetentionTask {
    private static final Logger log = LoggerFactory.getLogger(JobRetentionTask.class);
    static final String LOCK_NAME = "background-jobs-purge";

    public record Purged(int completed, int dead) {}

    private final JobStore store;
    private final ScheduledTaskLock lock;
    private final Clock clock;
    private final Duration completedRetention;
    private final Duration deadRetention;

    public JobRetentionTask(JobStore store, ScheduledTaskLock lock, Clock clock,
                            @Value("${app.jobs.completed-retention:P7D}") Duration completedRetention,
                            @Value("${app.jobs.dead-retention:P30D}") Duration deadRetention) {
        this.store = store;
        this.lock = lock;
        this.clock = clock;
        this.completedRetention = completedRetention;
        this.deadRetention = deadRetention;
    }

    @Scheduled(cron = "${app.jobs.purge-cron:0 17 * * * *}")
    public void purgeOldJobs() {
        lock.runExclusive(LOCK_NAME, Duration.ofMinutes(10), Duration.ofMinutes(1), this::purgeNow);
    }

    /** Deletes completed and dead-lettered jobs past their retention now. */
    public Purged purgeNow() {
        Instant now = clock.instant();
        Purged purged = new Purged(store.purgeCompletedBefore(now.minus(completedRetention), 5_000),
                store.purgeDeadBefore(now.minus(deadRetention), 5_000));
        if (purged.completed() > 0 || purged.dead() > 0) {
            log.info("background_jobs_purged completed={} dead={}", purged.completed(), purged.dead());
        }
        return purged;
    }
}
