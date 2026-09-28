package com.company.bds.engagement.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Classifies a change of a listing's public state against the last state the alert matcher saw (audit P-02: new
 * listing, price drop, back on the market). Pure: the caller loads the previous state and the current public row.
 */
public final class ListingChange {
    /** A listing first seen public but published longer ago than this was public before tracking began: "back". */
    public static final Duration NEW_LISTING_MAX_AGE = Duration.ofDays(3);

    public enum Kind { NEW, PRICE_DROP, BACK_ON_MARKET }

    /** The last seen state; {@code visible=false} with {@code hiddenAt} after the listing left the public read model. */
    public record Seen(boolean visible, String purpose, Long priceVnd, Instant hiddenAt) {}

    /** The current public row (absent = not publicly visible). */
    public record Current(String purpose, long priceVnd, Instant publishedAt) {}

    /**
     * An alert-worthy fact; {@code factKey} makes it once-only: the listing for NEW, the new price for PRICE_DROP, the
     * moment it disappeared for BACK_ON_MARKET (one alert per return).
     */
    public record Fact(Kind kind, String factKey, Long previousPriceVnd) {}

    private ListingChange() {}

    public static Optional<Fact> classify(Seen seen, Current current, Instant now) {
        if (current == null) return Optional.empty();
        if (seen == null) {
            boolean recent = current.publishedAt() == null || !current.publishedAt().isBefore(now.minus(NEW_LISTING_MAX_AGE));
            return Optional.of(recent ? new Fact(Kind.NEW, "listing", null)
                    : new Fact(Kind.BACK_ON_MARKET, "untracked:" + current.publishedAt().getEpochSecond(), null));
        }
        if (!seen.visible()) {
            long since = seen.hiddenAt() == null ? 0 : seen.hiddenAt().getEpochSecond();
            return Optional.of(new Fact(Kind.BACK_ON_MARKET, "hidden:" + since, null));
        }
        if (seen.priceVnd() != null && current.purpose().equals(seen.purpose()) && current.priceVnd() < seen.priceVnd()) {
            return Optional.of(new Fact(Kind.PRICE_DROP, "price:" + current.priceVnd(), seen.priceVnd()));
        }
        return Optional.empty();
    }
}
