package com.company.bds.engagement;

import com.company.bds.engagement.domain.AlertFrequency;
import com.company.bds.engagement.domain.ListingChange;
import com.company.bds.engagement.domain.ListingChange.Current;
import com.company.bds.engagement.domain.ListingChange.Kind;
import com.company.bds.engagement.domain.ListingChange.Seen;
import com.company.bds.engagement.domain.VndFormat;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure rules: change classification, digest schedule (Vietnam time), compact prices in alert texts. */
class EngagementDomainTests {
    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");     // 17:00 in Vietnam, a Monday

    @Test
    void classifiesNewPriceDropAndReturn() {
        Current current = new Current("SALE", 3_000_000_000L, NOW.minus(Duration.ofHours(1)));
        assertThat(ListingChange.classify(null, current, NOW).orElseThrow().kind()).isEqualTo(Kind.NEW);
        // First seen, but published long ago (hidden when tracking began): a return, not a new listing.
        Current old = new Current("SALE", 3_000_000_000L, NOW.minus(Duration.ofDays(30)));
        assertThat(ListingChange.classify(null, old, NOW).orElseThrow().kind()).isEqualTo(Kind.BACK_ON_MARKET);

        Seen visible = new Seen(true, "SALE", 3_500_000_000L, null);
        var drop = ListingChange.classify(visible, current, NOW).orElseThrow();
        assertThat(drop.kind()).isEqualTo(Kind.PRICE_DROP);
        assertThat(drop.factKey()).isEqualTo("price:3000000000");
        assertThat(drop.previousPriceVnd()).isEqualTo(3_500_000_000L);
        assertThat(ListingChange.classify(new Seen(true, "SALE", 2_000_000_000L, null), current, NOW)).as("increase").isEmpty();
        assertThat(ListingChange.classify(new Seen(true, "RENT", 9_000_000_000L, null), current, NOW)).as("purpose change").isEmpty();
        assertThat(ListingChange.classify(new Seen(true, "SALE", 3_000_000_000L, null), current, NOW)).as("same price").isEmpty();

        Instant hidden = NOW.minus(Duration.ofDays(2));
        var back = ListingChange.classify(new Seen(false, "SALE", 3_000_000_000L, hidden), current, NOW).orElseThrow();
        assertThat(back.kind()).isEqualTo(Kind.BACK_ON_MARKET);
        assertThat(back.factKey()).isEqualTo("hidden:" + hidden.getEpochSecond());
        assertThat(ListingChange.classify(visible, null, NOW)).isEmpty();
    }

    @Test
    void digestsFollowVietnamTime() {
        assertThat(AlertFrequency.INSTANT.firstDigestAt(NOW)).isNull();
        assertThat(AlertFrequency.INSTANT.nextDigestAfter(NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(AlertFrequency.DAILY.nextDigestAfter(NOW)).isEqualTo(Instant.parse("2026-09-29T00:00:00Z"));
        assertThat(AlertFrequency.DAILY.nextDigestAfter(Instant.parse("2026-09-28T23:59:00Z"))).isEqualTo(Instant.parse("2026-09-29T00:00:00Z"));
        assertThat(AlertFrequency.DAILY.nextDigestAfter(Instant.parse("2026-09-29T00:00:00Z"))).isEqualTo(Instant.parse("2026-09-30T00:00:00Z"));
        assertThat(AlertFrequency.WEEKLY.nextDigestAfter(NOW)).isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
        assertThat(AlertFrequency.WEEKLY.nextDigestAfter(Instant.parse("2026-09-27T23:00:00Z"))).as("Monday 06:00 VN")
                .isEqualTo(Instant.parse("2026-09-28T00:00:00Z"));
        assertThat(AlertFrequency.OFF.nextDigestAfter(NOW)).isNull();
    }

    @Test
    void pricesUseTheWebWording() {
        assertThat(VndFormat.compact(3_950_000_000L, "SALE")).isEqualTo("3,95 tỷ");
        assertThat(VndFormat.compact(850_000_000L, "SALE")).isEqualTo("850 triệu");
        assertThat(VndFormat.compact(14_500_000L, "RENT")).isEqualTo("14,5 triệu/tháng");
        assertThat(VndFormat.compact(2_000_000_000L, "SALE")).isEqualTo("2 tỷ");
        assertThat(VndFormat.compact(900_000L, "RENT")).isEqualTo("900.000 đ/tháng");
    }
}
