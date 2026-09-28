package com.company.bds.listing.domain.model;

import java.time.Duration;

/** Freshness policy of published listings (P-14), documented in {@code streams/s3a-supply.md}. */
public final class FreshnessPolicy {
    /** A confirmation (publication, "còn hàng", renewal) keeps a listing public this long. */
    public static final Duration VALIDITY = Duration.ofDays(45);
    /** Reminder offsets before expiry. */
    public static final Duration FIRST_REMINDER = Duration.ofDays(7);
    public static final Duration SECOND_REMINDER = Duration.ofDays(2);
    /** An expired listing can be renewed without new moderation during this window when its content is unchanged. */
    public static final Duration RENEWAL_WINDOW = Duration.ofDays(30);
    /** Time the owner has to answer a "đã bán/không còn" report before the listing is paused. */
    public static final Duration SOLD_CHECK_DEADLINE = Duration.ofHours(48);

    private FreshnessPolicy() {}
}
