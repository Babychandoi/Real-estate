package com.company.bds.moderation.application.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Queue gauges: {@code bds.moderation.queue.size}, {@code bds.moderation.queue.sla.breached} and
 * {@code bds.moderation.queue.oldest.age.seconds}. One aggregate query per 30 s at most, whatever the scrape rate.
 */
@Component
public class ModerationMetrics {
    private static final Duration CACHE = Duration.ofSeconds(30);
    private final ModerationQueueService queue;
    private final Clock clock;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    public ModerationMetrics(ModerationQueueService queue, Clock clock, MeterRegistry registry) {
        this.queue = queue;
        this.clock = clock;
        Gauge.builder("bds.moderation.queue.size", this, m -> m.current().stats().total())
                .description("Listings waiting for a moderation decision").register(registry);
        Gauge.builder("bds.moderation.queue.sla.breached", this, m -> m.current().stats().slaBreached())
                .description("Waiting submissions older than the 24 h SLA").register(registry);
        Gauge.builder("bds.moderation.queue.oldest.age.seconds", this, m -> {
                    Snapshot s = m.current();
                    return s.stats().oldestSubmittedAt() == null ? 0
                            : Math.max(0, Duration.between(s.stats().oldestSubmittedAt(), s.at()).toSeconds());
                })
                .description("Age of the oldest waiting submission").baseUnit("seconds").register(registry);
    }

    Snapshot current() {
        Instant now = clock.instant();
        Snapshot s = snapshot.get();
        if (s == null || s.at().plus(CACHE).isBefore(now)) {
            try {
                s = new Snapshot(queue.stats(now), now);
                snapshot.set(s);
            } catch (RuntimeException ex) {
                return s != null ? s : new Snapshot(new ModerationQueueService.QueueStats(0, 0, null), now);
            }
        }
        return s;
    }

    /** Drops the cached value (tests, or right after a bulk action). */
    public void invalidate() { snapshot.set(null); }

    record Snapshot(ModerationQueueService.QueueStats stats, Instant at) {}
}
