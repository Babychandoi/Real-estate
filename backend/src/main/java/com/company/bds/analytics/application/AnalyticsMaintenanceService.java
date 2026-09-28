package com.company.bds.analytics.application;

import com.company.bds.analytics.application.port.out.AnalyticsEventRepository;
import com.company.bds.analytics.application.port.out.AnalyticsMaintenanceRepository;
import com.company.bds.analytics.domain.DeviceFlag;
import com.company.bds.analytics.domain.RetentionPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregation, behavioural bot flagging and retention for analytics (run by the locked scheduled tasks in
 * {@code infrastructure.scheduling}). Days are Vietnam calendar days. Every step is idempotent, so an overlapping
 * run (lease expiry) or a retry does no harm.
 */
@Service
public class AnalyticsMaintenanceService {
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Web events are accepted up to 7 days late, so the nightly run re-aggregates the last 8 days. */
    public static final int LATE_DAYS = 8;
    private static final int MAX_BOT_FLAGS_PER_RUN = 500;

    private final AnalyticsMaintenanceRepository maintenance;
    private final AnalyticsEventRepository events;
    private final RetentionPolicy policy;
    private final Clock clock;

    public AnalyticsMaintenanceService(AnalyticsMaintenanceRepository maintenance, AnalyticsEventRepository events,
                                       RetentionPolicy policy, Clock clock) {
        this.maintenance = maintenance;
        this.events = events;
        this.policy = policy;
        this.clock = clock;
    }

    public RetentionPolicy policy() { return policy; }

    /** Re-aggregates the {@code days} most recent Vietnam days including today; returns the days recomputed. */
    public List<LocalDate> aggregateRecent(int days) {
        LocalDate today = LocalDate.now(clock.withZone(VIETNAM));
        List<LocalDate> done = new java.util.ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            aggregate(day);
            done.add(day);
        }
        return done;
    }

    @Transactional
    public void aggregate(LocalDate day) {
        maintenance.recomputeDay(day, day.atStartOfDay(VIETNAM).toInstant(), day.plusDays(1).atStartOfDay(VIETNAM).toInstant());
    }

    /** Flags devices that sent more than the policy's events per hour in the last hour; returns how many were new. */
    public int flagBusyDevices() {
        int flagged = 0;
        for (String device : maintenance.busyDevices(clock.instant().minus(Duration.ofHours(1)), policy.botEventsPerHour(),
                MAX_BOT_FLAGS_PER_RUN)) {
            if (events.flagDevice(device, DeviceFlag.BOT, "RATE_PER_HOUR")) flagged++;
        }
        return flagged;
    }

    /** Applies the retention policy; returns rows affected per step (for the log line). */
    public Map<String, Integer> applyRetention() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(VIETNAM));
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("identifiersStripped", maintenance.stripIdentifiers(now.minus(policy.identifiers())));
        result.put("eventsDeleted", maintenance.deleteEvents(now.minus(policy.rawEvents())));
        result.put("visitorDaysDeleted", maintenance.deleteVisitorDays(today.minusDays(policy.visitorDays().toDays())));
        result.put("dailyMetricsDeleted", maintenance.deleteDailyMetrics(today.minusDays(policy.dailyMetrics().toDays())));
        result.put("consentRecordsDeleted", maintenance.deleteConsentRecords(now.minus(policy.consentRecords())));
        result.put("deviceFlagsDeleted", maintenance.deleteDeviceFlags(now.minus(policy.deviceFlags())));
        return result;
    }
}
