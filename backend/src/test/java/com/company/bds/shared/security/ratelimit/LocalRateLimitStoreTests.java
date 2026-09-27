package com.company.bds.shared.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** F13.3: the fallback used while Redis is down is exact inside a window and bounded in memory. */
class LocalRateLimitStoreTests {
    private static final byte[] NO_PEPPER = new byte[0];
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-28T00:00:00Z"));
    private final RateLimitRule perMinute = new RateLimitRule(RateLimitDimension.IP, 3, Duration.ofMinutes(1));
    private final RateLimitPolicy policy = new RateLimitPolicy("test-policy", "POST", "/api/test",
            RateLimitFailureMode.EVICT, List.of(perMinute));

    private RateLimitKey key(String subject) {
        return RateLimitKey.of(NO_PEPPER, policy, perMinute, subject);
    }

    @Test
    void countsInsideTheWindowAndStartsOverWhenItEnds() {
        LocalRateLimitStore store = new LocalRateLimitStore(1_000, clock);
        RateLimitKey client = key("203.0.113.9");

        assertThat(store.increment(client, RateLimitFailureMode.EVICT).count()).isEqualTo(1);
        assertThat(store.increment(client, RateLimitFailureMode.EVICT).count()).isEqualTo(2);
        clock.advance(Duration.ofSeconds(59));
        assertThat(store.increment(client, RateLimitFailureMode.EVICT).count()).isEqualTo(3);
        clock.advance(Duration.ofSeconds(1));
        assertThat(store.increment(client, RateLimitFailureMode.EVICT).count()).isEqualTo(1);
    }

    @Test
    void ttlIsTheExactTimeLeftInTheWindow() {
        LocalRateLimitStore store = new LocalRateLimitStore(1_000, clock);
        RateLimitKey client = key("203.0.113.9");

        assertThat(store.increment(client, RateLimitFailureMode.EVICT).ttlMillis()).isEqualTo(60_000);
        clock.advance(Duration.ofMillis(15_500));
        assertThat(store.increment(client, RateLimitFailureMode.EVICT).ttlMillis()).isEqualTo(44_500);
    }

    @Test
    void hundredThousandDistinctClientsNeverGrowTheTableBeyondItsCapacity() {
        LocalRateLimitStore store = new LocalRateLimitStore(10_000, clock);
        RateLimitKey hot = key("198.51.100.1");
        long hotCount = 0;

        for (int i = 0; i < 100_000; i++) {
            store.increment(key("flood-" + i), RateLimitFailureMode.EVICT);
            if (i % 100 == 0) hotCount = store.increment(hot, RateLimitFailureMode.EVICT).count();
            if (i % 10_000 == 0) assertThat(store.size()).isLessThanOrEqualTo(store.capacity());
        }

        assertThat(store.capacity()).isLessThanOrEqualTo(10_000);
        assertThat(store.size()).isLessThanOrEqualTo(store.capacity());
        // A client that keeps sending stays in the LRU table, so the flood cannot reset its counter.
        assertThat(hotCount).isEqualTo(1_000);
    }

    @Test
    void failClosedTableRejectsNewClientsWhileEverySlotHoldsALiveWindow() {
        LocalRateLimitStore store = new LocalRateLimitStore(32, clock); // 16 segments x 2 slots
        RateLimitKey admitted = key("admitted-client");
        assertThat(store.increment(admitted, RateLimitFailureMode.FAIL_CLOSED).capacityExceeded()).isFalse();

        RateLimitKey rejected = null;
        for (int i = 0; i < 10_000 && rejected == null; i++) {
            RateLimitKey candidate = key("attacker-" + i);
            if (store.increment(candidate, RateLimitFailureMode.FAIL_CLOSED).capacityExceeded()) rejected = candidate;
        }

        assertThat(rejected).as("a full table must reject unknown clients").isNotNull();
        assertThat(store.size()).isLessThanOrEqualTo(32);
        RateLimitCounter stillCounted = store.increment(admitted, RateLimitFailureMode.FAIL_CLOSED);
        assertThat(stillCounted.capacityExceeded()).isFalse();
        assertThat(stillCounted.count()).isEqualTo(2);
        RateLimitCounter retry = store.increment(rejected, RateLimitFailureMode.FAIL_CLOSED);
        assertThat(retry.capacityExceeded()).isTrue();
        assertThat(retry.ttlMillis()).isBetween(1_000L, 60_000L);

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        assertThat(store.increment(rejected, RateLimitFailureMode.FAIL_CLOSED).capacityExceeded())
                .as("expired windows are swept and free their slots").isFalse();

        // Refill right after a sweep emptied the table: Retry-After must stay within the window, never "forever".
        RateLimitCounter refilled = null;
        for (int i = 0; i < 10_000 && (refilled == null || !refilled.capacityExceeded()); i++) {
            refilled = store.increment(key("second-wave-" + i), RateLimitFailureMode.FAIL_CLOSED);
        }
        assertThat(refilled.capacityExceeded()).isTrue();
        assertThat(refilled.ttlMillis()).isBetween(1_000L, 60_000L);
    }

    @Test
    void evictingTableForgetsTheLeastRecentlyUsedClient() {
        LocalRateLimitStore store = new LocalRateLimitStore(16, clock); // one slot per segment
        for (int i = 0; i < 1_000; i++) {
            assertThat(store.increment(key("client-" + i), RateLimitFailureMode.EVICT).capacityExceeded()).isFalse();
        }
        assertThat(store.size()).isEqualTo(16);
    }
}
