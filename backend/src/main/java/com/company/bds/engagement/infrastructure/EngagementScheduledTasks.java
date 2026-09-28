package com.company.bds.engagement.infrastructure;

import com.company.bds.engagement.application.AlertDigestService;
import com.company.bds.shared.scheduling.ScheduledTaskLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Locked scheduled tasks of the engagement features (contract §3.3): the alert digest every minute and a daily
 * retention sweep (alert matches, expired unsubscribe tokens, notifications read more than 180 days ago).
 */
@Component
@ConditionalOnProperty(name = "app.engagement.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class EngagementScheduledTasks {
    private final ScheduledTaskLock lock;
    private final AlertDigestService digests;
    private final JdbcTemplate jdbc;

    public EngagementScheduledTasks(ScheduledTaskLock lock, AlertDigestService digests, JdbcTemplate jdbc) {
        this.lock = lock;
        this.digests = digests;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${app.engagement.digest-ms:60000}", initialDelayString = "${app.engagement.digest-initial-ms:30000}")
    public void deliverDigests() {
        lock.runExclusive("saved-search-digest", Duration.ofMinutes(5), () -> digests.deliverDue(2000));
    }

    @Scheduled(cron = "${app.engagement.retention-cron:0 23 4 * * *}")
    public void retention() {
        lock.runExclusive("engagement-retention", Duration.ofMinutes(15), Duration.ofHours(1), () -> {
            digests.purge();
            jdbc.update("DELETE FROM notification_unsubscribe_tokens WHERE expires_at < now()");
            jdbc.update("DELETE FROM user_notifications WHERE read_at IS NOT NULL AND read_at < now() - interval '180 days'");
        });
    }
}
