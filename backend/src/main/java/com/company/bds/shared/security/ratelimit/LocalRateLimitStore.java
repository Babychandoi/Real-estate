package com.company.bds.shared.security.ratelimit;

import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded in-process fixed windows used while Redis is unavailable (audit F13.3).
 *
 * <p>The table is split into 16 lock-striped segments, each an access-ordered {@link LinkedHashMap} with a hard
 * capacity, so memory never grows with the number of distinct clients. Expired windows are swept lazily (at most
 * once per second per segment, only when the segment is full). When a segment is full of live windows the policy's
 * {@link RateLimitFailureMode} decides: {@code EVICT} drops the least recently used client, {@code FAIL_CLOSED}
 * rejects the new client. Counts are per instance, so N instances allow up to N times the limit during an outage.</p>
 */
final class LocalRateLimitStore {
    static final int SEGMENTS = 16;
    private static final long SWEEP_INTERVAL_MILLIS = 1_000;
    private static final long MIN_RETRY_MILLIS = 1_000;

    private final Segment[] segments = new Segment[SEGMENTS];
    private final int capacity;
    private final Clock clock;

    LocalRateLimitStore(int capacity, Clock clock) {
        if (capacity < SEGMENTS) throw new IllegalArgumentException("Bảng giới hạn cục bộ cần ít nhất " + SEGMENTS + " ô");
        int perSegment = capacity / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) segments[i] = new Segment(perSegment);
        this.capacity = perSegment * SEGMENTS;
        this.clock = clock;
    }

    RateLimitCounter increment(RateLimitKey key, RateLimitFailureMode mode) {
        Segment segment = segments[(int) (key.lo() & (SEGMENTS - 1))];
        return segment.increment(new Slot(key.hi(), key.lo()), key.windowMillis(), mode, clock.millis());
    }

    /** Effective capacity (configured value rounded down to a multiple of the segment count). */
    int capacity() { return capacity; }

    int size() {
        int total = 0;
        for (Segment segment : segments) total += segment.size();
        return total;
    }

    private record Slot(long hi, long lo) {}

    private static final class Window {
        private final long expiresAt;
        private long count;
        private Window(long expiresAt) { this.expiresAt = expiresAt; }
    }

    private static final class Segment {
        private final int capacity;
        private final LinkedHashMap<Slot, Window> windows;
        private long nextSweepAt;
        private long earliestExpiry = Long.MAX_VALUE;

        private Segment(int capacity) {
            this.capacity = capacity;
            this.windows = new LinkedHashMap<>(Math.min(capacity, 1024), 0.75f, true);
        }

        private synchronized RateLimitCounter increment(Slot slot, long windowMillis, RateLimitFailureMode mode, long now) {
            Window window = windows.get(slot);
            if (window != null && window.expiresAt <= now) {
                windows.remove(slot);
                window = null;
            }
            if (window == null) {
                if (windows.size() >= capacity) {
                    if (now >= nextSweepAt) sweep(now);
                    if (windows.size() >= capacity) {
                        if (mode == RateLimitFailureMode.FAIL_CLOSED) {
                            return RateLimitCounter.noCapacity(Math.max(MIN_RETRY_MILLIS, earliestExpiry - now));
                        }
                        Iterator<Map.Entry<Slot, Window>> eldest = windows.entrySet().iterator();
                        eldest.next();
                        eldest.remove();
                    }
                }
                window = new Window(now + windowMillis);
                windows.put(slot, window);
            }
            window.count++;
            return RateLimitCounter.counted(window.count, window.expiresAt - now);
        }

        private void sweep(long now) {
            long earliest = Long.MAX_VALUE;
            for (Iterator<Window> it = windows.values().iterator(); it.hasNext(); ) {
                Window window = it.next();
                if (window.expiresAt <= now) it.remove();
                else earliest = Math.min(earliest, window.expiresAt);
            }
            earliestExpiry = earliest;
            nextSweepAt = now + SWEEP_INTERVAL_MILLIS;
        }

        private synchronized int size() { return windows.size(); }
    }
}
