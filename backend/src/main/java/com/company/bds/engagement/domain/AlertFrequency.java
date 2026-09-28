package com.company.bds.engagement.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * How often a saved search sends its alerts. INSTANT batches what matched within {@link #INSTANT_THROTTLE} (at most one
 * alert per search per 15 minutes); DAILY at 07:00 and WEEKLY on Monday 07:00, Vietnam time; OFF sends nothing (the
 * search stays saved).
 */
public enum AlertFrequency {
    INSTANT, DAILY, WEEKLY, OFF;

    public static final Duration INSTANT_THROTTLE = Duration.ofMinutes(15);
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final LocalTime DIGEST_TIME = LocalTime.of(7, 0);

    /** When the first digest may go out for a search created or switched to this frequency at {@code now}; null = asap. */
    public Instant firstDigestAt(Instant now) {
        return switch (this) {
            case INSTANT -> null;
            case DAILY, WEEKLY -> nextDigestAfter(now);
            case OFF -> null;
        };
    }

    /** When the next digest may go out after one was sent at {@code now}. */
    public Instant nextDigestAfter(Instant now) {
        ZonedDateTime local = now.atZone(ZONE);
        return switch (this) {
            case INSTANT -> now.plus(INSTANT_THROTTLE);
            case DAILY -> {
                ZonedDateTime today = local.with(DIGEST_TIME);
                yield (today.isAfter(local) ? today : today.plusDays(1)).toInstant();
            }
            case WEEKLY -> {
                ZonedDateTime monday = local.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).with(DIGEST_TIME);
                yield (monday.isAfter(local) ? monday : monday.plusWeeks(1)).toInstant();
            }
            case OFF -> null;
        };
    }

    public static AlertFrequency parse(String value) {
        if (value == null) throw new IllegalArgumentException("frequency");
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
