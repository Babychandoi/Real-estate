package com.company.bds.analytics.infrastructure.scheduling;

import com.company.bds.analytics.application.AnalyticsMaintenanceService;
import com.company.bds.analytics.domain.RetentionPolicy;
import com.company.bds.shared.scheduling.ScheduledTaskLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Locked analytics maintenance: hourly (bot rule + today/yesterday aggregates) and nightly (last 8 days + retention).
 * Disable with {@code app.analytics.maintenance.enabled=false} (e.g. an instance that must not write).
 */
@Configuration
public class AnalyticsMaintenanceTasks {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsMaintenanceTasks.class);

    private final AnalyticsMaintenanceService maintenance;
    private final ScheduledTaskLock lock;
    private final boolean enabled;

    public AnalyticsMaintenanceTasks(AnalyticsMaintenanceService maintenance, ScheduledTaskLock lock,
                                     @Value("${app.analytics.maintenance.enabled:true}") boolean enabled) {
        this.maintenance = maintenance;
        this.lock = lock;
        this.enabled = enabled;
    }

    @Bean
    static RetentionPolicy analyticsRetentionPolicy(
            @Value("${app.analytics.retention.identifiers:P90D}") Duration identifiers,
            @Value("${app.analytics.retention.raw-events:P180D}") Duration rawEvents,
            @Value("${app.analytics.retention.visitor-days:P90D}") Duration visitorDays,
            @Value("${app.analytics.retention.daily-metrics:P760D}") Duration dailyMetrics,
            @Value("${app.analytics.retention.consent-records:P1095D}") Duration consentRecords,
            @Value("${app.analytics.retention.device-flags:P180D}") Duration deviceFlags,
            @Value("${app.analytics.bot.max-events-per-hour:600}") int botEventsPerHour) {
        return new RetentionPolicy(identifiers, rawEvents, visitorDays, dailyMetrics, consentRecords, deviceFlags, botEventsPerHour);
    }

    @Scheduled(cron = "${app.analytics.maintenance.hourly-cron:0 7 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void hourly() {
        if (!enabled) return;
        lock.runExclusive("analytics-hourly", Duration.ofMinutes(20), () -> {
            int bots = maintenance.flagBusyDevices();
            List<?> days = maintenance.aggregateRecent(2);
            log.info("analytics_hourly bot_devices_flagged={} days_aggregated={}", bots, days.size());
        });
    }

    @Scheduled(cron = "${app.analytics.maintenance.nightly-cron:0 20 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void nightly() {
        if (!enabled) return;
        lock.runExclusive("analytics-nightly", Duration.ofHours(1), Duration.ofMinutes(30), () -> {
            List<?> days = maintenance.aggregateRecent(AnalyticsMaintenanceService.LATE_DAYS);
            Map<String, Integer> retention = maintenance.applyRetention();
            log.info("analytics_nightly days_aggregated={} retention={}", days.size(), retention);
        });
    }
}
