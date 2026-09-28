package com.company.bds.listing.infrastructure.jobs;

import com.company.bds.listing.application.service.ListingFreshnessService;
import com.company.bds.shared.jobs.ClaimedJob;
import com.company.bds.shared.jobs.JobBatchResult;
import com.company.bds.shared.jobs.JobHandler;
import com.company.bds.shared.scheduling.ScheduledTaskLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Job handlers and the locked periodic sweeps of listing freshness (P-14). */
@Configuration
public class ListingFreshnessJobs {

    @Bean
    JobHandler listingExpiryReminderHandler(ListingFreshnessService freshness) {
        return new JobHandler() {
            @Override public String queue() { return ListingFreshnessService.REMINDER_QUEUE; }
            @Override public int maxAttempts() { return 5; }
            @Override public JobBatchResult handle(List<ClaimedJob> batch) {
                JobBatchResult.Builder result = JobBatchResult.builder();
                for (ClaimedJob job : batch) {
                    try {
                        freshness.sendReminder(UUID.fromString(job.text("listingId")), Instant.parse(job.text("cycle")), job.text("kind"));
                        result.succeed(job);
                    } catch (IllegalArgumentException | NullPointerException bad) {
                        result.failPermanently(job, "Invalid reminder payload");
                    } catch (RuntimeException ex) {
                        result.fail(job, ex.getClass().getSimpleName());
                    }
                }
                return result.build();
            }
        };
    }

    /** Runs at the sold-check deadline; the sweep pauses every listing whose check is still open. */
    @Bean
    JobHandler listingSoldCheckHandler(ListingFreshnessService freshness, Clock clock) {
        return new JobHandler() {
            @Override public String queue() { return ListingFreshnessService.SOLD_CHECK_QUEUE; }
            @Override public int maxAttempts() { return 5; }
            @Override public JobBatchResult handle(List<ClaimedJob> batch) {
                freshness.pauseUnansweredSoldChecks(clock.instant());
                return JobBatchResult.allSucceeded(batch);
            }
        };
    }

    /** Periodic, cluster-wide exclusive sweeps; disable with {@code app.listing.freshness.scheduler-enabled=false}. */
    @Component
    @ConditionalOnProperty(name = "app.listing.freshness.scheduler-enabled", havingValue = "true", matchIfMissing = true)
    static class Sweeps {
        private final ListingFreshnessService freshness;
        private final ScheduledTaskLock lock;
        private final Clock clock;

        Sweeps(ListingFreshnessService freshness, ScheduledTaskLock lock, Clock clock) {
            this.freshness = freshness;
            this.lock = lock;
            this.clock = clock;
        }

        @Scheduled(fixedDelayString = "${app.listing.freshness.sweep-ms:600000}", initialDelayString = "${app.listing.freshness.initial-delay-ms:60000}")
        void sweep() {
            lock.runExclusive("listing-freshness-sweep", Duration.ofMinutes(9), () -> {
                Instant now = clock.instant();
                freshness.expireDue(now);
                freshness.pauseUnansweredSoldChecks(now);
                freshness.scheduleReminders(now);
            });
        }
    }
}
