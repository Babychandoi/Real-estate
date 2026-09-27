package com.company.bds.shared.jobs;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Queue metrics (contract §3.1): {@code bds.jobs.lag.seconds}, {@code bds.jobs.pending}, {@code bds.jobs.dead} gauges and the
 * {@code bds.jobs.processed{queue,outcome}} counter. Gauges read a snapshot refreshed by the worker at most every
 * {@link #REFRESH_INTERVAL} (two indexed aggregate queries), never on every scrape.
 */
@Component
public class JobMetrics {
    static final Duration REFRESH_INTERVAL = Duration.ofSeconds(15);

    private final MeterRegistry registry;
    private final JobStore store;
    private final Map<String, QueueGauges> gauges = new ConcurrentHashMap<>();
    private volatile long lastRefreshNanos;
    private volatile boolean refreshedOnce;

    public JobMetrics(MeterRegistry registry, JobStore store) {
        this.registry = registry;
        this.store = store;
    }

    /** Registers the gauges of a queue so it reports 0 instead of "no data" before its first job. */
    public void register(String queue) {
        gauges.computeIfAbsent(queue, name -> {
            QueueGauges values = new QueueGauges();
            Gauge.builder("bds.jobs.pending", values.pending, AtomicLong::get).tag("queue", name)
                    .description("Pending (not completed, not dead-lettered) jobs").register(registry);
            Gauge.builder("bds.jobs.dead", values.dead, AtomicLong::get).tag("queue", name)
                    .description("Dead-lettered jobs waiting for an operator").register(registry);
            Gauge.builder("bds.jobs.lag.seconds", values.lagMillis, millis -> millis.get() / 1000.0).tag("queue", name)
                    .description("Age of the oldest due pending job").register(registry);
            return values;
        });
    }

    public void processed(String queue, String outcome) {
        registry.counter("bds.jobs.processed", "queue", queue, "outcome", outcome).increment();
    }

    /** Re-reads pending/dead/lag for every queue now. */
    public void refresh() {
        Set<String> seen = new HashSet<>();
        for (JobStore.QueueStats stats : store.stats()) {
            register(stats.queue());
            QueueGauges values = gauges.get(stats.queue());
            values.pending.set(stats.pending());
            values.dead.set(stats.dead());
            values.lagMillis.set(Math.round(stats.lagSeconds() * 1000));
            seen.add(stats.queue());
        }
        gauges.forEach((queue, values) -> {
            if (!seen.contains(queue)) {
                values.pending.set(0);
                values.dead.set(0);
                values.lagMillis.set(0);
            }
        });
        lastRefreshNanos = System.nanoTime();
        refreshedOnce = true;
    }

    void refreshIfStale() {
        if (!refreshedOnce || System.nanoTime() - lastRefreshNanos >= REFRESH_INTERVAL.toNanos()) refresh();
    }

    private static final class QueueGauges {
        private final AtomicLong pending = new AtomicLong();
        private final AtomicLong dead = new AtomicLong();
        private final AtomicLong lagMillis = new AtomicLong();
    }
}
