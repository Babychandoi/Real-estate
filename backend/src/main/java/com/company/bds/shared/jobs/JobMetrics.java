package com.company.bds.shared.jobs;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Queue and worker metrics (contract §3.1), independent of the worker thread so they stay truthful when the worker is
 * disabled, dead or hung on this instance:
 * <ul>
 *   <li>{@code bds.jobs.pending{queue}}, {@code bds.jobs.dead{queue}} and {@code bds.jobs.lag.seconds{queue}} come from a
 *       snapshot that a dedicated thread refreshes every {@code app.jobs.metrics.refresh-ms} (two indexed aggregate
 *       queries, whatever {@code app.jobs.enabled} says). The lag is computed at scrape time from the snapshot's oldest due
 *       job, so it keeps growing while that job is not processed, even if the refresh itself stalls.</li>
 *   <li>{@code bds.jobs.metrics.age.seconds}: time since the last successful snapshot.</li>
 *   <li>{@code bds.jobs.worker.enabled}, {@code bds.jobs.worker.running} (1 only when polling with a fresh heartbeat and no
 *       paused queue), {@code bds.jobs.worker.last_poll_age.seconds}, {@code bds.jobs.worker.paused_queues}.</li>
 *   <li>Counters {@code bds.jobs.processed{queue,outcome}} and {@code bds.jobs.handler.timeouts{queue}}.</li>
 * </ul>
 */
@Component
public class JobMetrics implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(JobMetrics.class);

    private record Snapshot(long pending, long dead, @Nullable Instant oldestDueRunAt) {
        static final Snapshot EMPTY = new Snapshot(0, 0, null);
    }

    private final MeterRegistry registry;
    private final JobStore store;
    private final Clock clock;
    private final boolean refresherEnabled;
    private final long refreshMs;
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Set<String> registered = ConcurrentHashMap.newKeySet();
    private volatile Instant lastRefresh;
    private volatile ScheduledExecutorService refresher;

    public JobMetrics(MeterRegistry registry, JobStore store, Clock clock,
                      @Value("${app.jobs.metrics.enabled:true}") boolean refresherEnabled,
                      @Value("${app.jobs.metrics.refresh-ms:15000}") long refreshMs) {
        this.registry = registry;
        this.store = store;
        this.clock = clock;
        this.refresherEnabled = refresherEnabled;
        this.refreshMs = Math.max(100, refreshMs);
        Gauge.builder("bds.jobs.metrics.age.seconds", this, JobMetrics::snapshotAgeSeconds)
                .description("Seconds since the job queue metrics were last read from the database").register(registry);
    }

    /** Registers the gauges of a queue so it reports 0 instead of "no data" before its first job. */
    public void register(String queue) {
        if (!registered.add(queue)) return;
        Gauge.builder("bds.jobs.pending", this, metrics -> metrics.snapshot(queue).pending()).tag("queue", queue)
                .description("Pending (not completed, not dead-lettered) jobs").register(registry);
        Gauge.builder("bds.jobs.dead", this, metrics -> metrics.snapshot(queue).dead()).tag("queue", queue)
                .description("Dead-lettered jobs waiting for an operator").register(registry);
        Gauge.builder("bds.jobs.lag.seconds", this, metrics -> metrics.lagSeconds(queue)).tag("queue", queue)
                .description("Age of the oldest due pending job").register(registry);
    }

    /** Worker-state gauges of this instance's worker (registered once, by the Spring-managed worker). */
    void registerWorker(JobWorker worker) {
        Gauge.builder("bds.jobs.worker.enabled", worker, w -> w.state().enabled() ? 1 : 0)
                .description("1 when app.jobs.enabled is true on this instance").register(registry);
        Gauge.builder("bds.jobs.worker.running", worker, w -> w.state().healthy() ? 1 : 0)
                .description("1 when this instance's worker polls with a fresh heartbeat and no paused queue").register(registry);
        Gauge.builder("bds.jobs.worker.last_poll_age.seconds", worker, w -> {
            Duration age = w.state().heartbeatAge();
            return age == null ? Double.NaN : age.toMillis() / 1000.0;
        }).description("Seconds since the worker's last heartbeat").register(registry);
        Gauge.builder("bds.jobs.worker.paused_queues", worker, w -> w.state().pausedQueues().size())
                .description("Queues paused on this instance because a handler exceeded its time budget").register(registry);
    }

    public void processed(String queue, String outcome) {
        registry.counter("bds.jobs.processed", "queue", queue, "outcome", outcome).increment();
    }

    void handlerTimedOut(String queue) {
        registry.counter("bds.jobs.handler.timeouts", "queue", queue).increment();
    }

    /** Re-reads pending/dead/oldest-due for every queue now. */
    public void refresh() {
        Map<String, Snapshot> fresh = new ConcurrentHashMap<>();
        for (JobStore.QueueStats stats : store.stats()) {
            fresh.put(stats.queue(), new Snapshot(stats.pending(), stats.dead(), stats.oldestDueRunAt()));
        }
        fresh.keySet().forEach(this::register);
        registered.forEach(queue -> snapshots.put(queue, fresh.getOrDefault(queue, Snapshot.EMPTY)));
        lastRefresh = clock.instant();
    }

    private Snapshot snapshot(String queue) {
        return snapshots.getOrDefault(queue, Snapshot.EMPTY);
    }

    private double lagSeconds(String queue) {
        Instant oldest = snapshot(queue).oldestDueRunAt();
        if (oldest == null) return 0;
        return Math.max(0, Duration.between(oldest, clock.instant()).toMillis() / 1000.0);
    }

    private double snapshotAgeSeconds() {
        Instant last = lastRefresh;
        return last == null ? Double.NaN : Duration.between(last, clock.instant()).toMillis() / 1000.0;
    }

    private void refreshSafely() {
        try {
            refresh();
        } catch (Throwable error) { // keep the schedule alive; bds.jobs.metrics.age.seconds shows the gap
            log.warn("job_metrics_refresh_failed error={}", JobErrors.describe(error));
        }
    }

    @Override
    public synchronized void start() {
        if (!refresherEnabled || refresher != null) return;
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bds-job-metrics");
            thread.setDaemon(true);
            return thread;
        });
        service.scheduleWithFixedDelay(this::refreshSafely, 0, refreshMs, TimeUnit.MILLISECONDS);
        refresher = service;
    }

    @Override
    public synchronized void stop() {
        ScheduledExecutorService service = refresher;
        if (service == null) return;
        refresher = null;
        service.shutdownNow();
    }

    @Override
    public boolean isRunning() { return refresher != null; }
}
