package com.company.bds.analytics.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * How long analytics data is kept (defaults in {@code application.yml}, section {@code app.analytics.retention}).
 *
 * @param identifiers     after this age a raw event loses anonymous id, session id, user id and UTM (pseudonymisation)
 * @param rawEvents       after this age raw events are deleted (daily aggregates keep the counts)
 * @param visitorDays     device/day rows used for cohorts and return rate (pseudonymous, so ≤ identifiers)
 * @param dailyMetrics    aggregated counts without identifiers
 * @param consentRecords  proof of consent decisions
 * @param deviceFlags     internal/bot device flags
 * @param botEventsPerHour behavioural bot rule: more events than this from one device within an hour
 */
public record RetentionPolicy(Duration identifiers, Duration rawEvents, Duration visitorDays, Duration dailyMetrics,
                              Duration consentRecords, Duration deviceFlags, int botEventsPerHour) {
    public RetentionPolicy {
        Objects.requireNonNull(identifiers);
        Objects.requireNonNull(rawEvents);
        Objects.requireNonNull(visitorDays);
        Objects.requireNonNull(dailyMetrics);
        Objects.requireNonNull(consentRecords);
        Objects.requireNonNull(deviceFlags);
        if (identifiers.compareTo(rawEvents) > 0) throw new IllegalArgumentException("identifiers must not outlive raw events");
        if (visitorDays.compareTo(identifiers) > 0) throw new IllegalArgumentException("visitor days are pseudonymous: keep them no longer than identifiers");
        if (rawEvents.compareTo(Duration.ofDays(8)) < 0) throw new IllegalArgumentException("raw events are re-aggregated for 8 days");
        if (botEventsPerHour < 10) throw new IllegalArgumentException("botEventsPerHour must be >= 10");
    }
}
