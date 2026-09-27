package com.company.bds.shared.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Polls every registered {@link JobHandler} queue on its own thread ({@code app.jobs.enabled}, {@code app.jobs.poll-ms}),
 * so a slow {@code @Scheduled} task never delays jobs. Safe with several instances: claims use {@code SKIP LOCKED}
 * leases and completion is guarded by the lease token and the enqueue sequence.
 */
@Component
public class JobWorker implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    private static final int MAX_BATCHES_PER_DRAIN = 1_000;

    private final JobStore store;
    private final JobMetrics metrics;
    private final JobBackoff backoff;
    private final Map<String, JobHandler> handlers;
    private final String workerId;
    private final boolean enabled;
    private final long pollMs;
    private volatile ScheduledExecutorService executor;

    @Autowired
    public JobWorker(JobStore store, JobMetrics metrics, ObjectProvider<JobHandler> handlers,
                     @Value("${app.jobs.enabled:true}") boolean enabled,
                     @Value("${app.jobs.poll-ms:1000}") long pollMs,
                     @Value("${app.jobs.retry-base:PT30S}") Duration retryBase,
                     @Value("${app.jobs.retry-cap:PT1H}") Duration retryCap) {
        this(store, metrics, handlers.orderedStream().toList(), enabled, pollMs, new JobBackoff(retryBase, retryCap), defaultWorkerId());
    }

    JobWorker(JobStore store, JobMetrics metrics, Collection<JobHandler> handlers, boolean enabled, long pollMs,
              JobBackoff backoff, String workerId) {
        this.store = store;
        this.metrics = metrics;
        this.backoff = backoff;
        this.enabled = enabled;
        this.pollMs = Math.max(50, pollMs);
        this.workerId = workerId;
        Map<String, JobHandler> byQueue = new LinkedHashMap<>();
        for (JobHandler handler : handlers) {
            if (byQueue.putIfAbsent(handler.queue(), handler) != null) {
                throw new IllegalStateException("Two JobHandler beans are registered for queue " + handler.queue());
            }
            metrics.register(handler.queue());
        }
        this.handlers = Map.copyOf(byQueue);
    }

    /** One batch for every registered queue; returns the number of jobs claimed. */
    public int runOnce() {
        int processed = 0;
        for (JobHandler handler : handlers.values()) {
            try {
                processed += processBatch(handler);
            } catch (RuntimeException ex) {
                log.warn("job_batch_failed queue={} worker={} error={}", handler.queue(), workerId, JobErrors.describe(ex));
            }
        }
        try {
            metrics.refreshIfStale();
        } catch (RuntimeException ex) {
            log.warn("job_metrics_refresh_failed error={}", JobErrors.describe(ex));
        }
        return processed;
    }

    /** Processes the registered queue until no due job is left (bounded); returns the number of jobs claimed. */
    public int drain(String queue) {
        JobHandler handler = handlers.get(queue);
        if (handler == null) throw new IllegalArgumentException("No JobHandler is registered for queue " + queue);
        return drain(handler);
    }

    /** Processes the handler's queue until no due job is left (bounded); the handler need not be a registered bean. */
    public int drain(JobHandler handler) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_DRAIN; batch++) {
            int claimed = processBatch(handler);
            if (claimed == 0) break;
            total += claimed;
        }
        return total;
    }

    /** Claims one batch of the handler's queue, runs the handler and records each job's outcome. */
    public int processBatch(JobHandler handler) {
        String queue = handler.queue();
        List<ClaimedJob> claimed = store.claim(queue, handler.batchSize(), handler.lease(), workerId);
        if (claimed.isEmpty()) return 0;
        List<ClaimedJob> runnable = new ArrayList<>();
        for (ClaimedJob job : claimed) {
            if (job.attempts() >= job.maxAttempts()) {
                // Only reachable when earlier leases expired without the job finishing (e.g. the worker was killed).
                record(queue, job, store.deadLetter(job, "Lease expired before completion on every attempt"), null);
            } else {
                runnable.add(job);
            }
        }
        if (runnable.isEmpty()) return claimed.size();
        JobBatchResult result;
        try {
            result = Objects.requireNonNull(handler.handle(List.copyOf(runnable)), "JobHandler returned null");
        } catch (RuntimeException ex) {
            String reason = JobErrors.describe(ex);
            JobBatchResult.Builder failed = JobBatchResult.builder();
            runnable.forEach(job -> failed.fail(job, reason));
            result = failed.build();
        }
        for (ClaimedJob job : runnable) {
            if (result.succeeded().contains(job.id())) {
                JobStore.CompletionOutcome outcome = store.complete(job);
                metrics.processed(queue, switch (outcome) {
                    case COMPLETED -> "success";
                    case RERUN -> "rerun";
                    case LEASE_LOST -> "lease_lost";
                });
            } else {
                String reason = JobErrors.sanitize(result.failures().getOrDefault(job.id(), "Handler did not report the job as succeeded"));
                record(queue, job, store.fail(job, reason, backoff.delayAfter(job.attempts())), reason);
            }
        }
        return claimed.size();
    }

    private void record(String queue, ClaimedJob job, JobStore.FailureOutcome outcome, String reason) {
        switch (outcome) {
            case DEAD -> {
                metrics.processed(queue, "dead");
                log.warn("job_dead_lettered queue={} id={} attempts={} error={}", queue, job.id(), job.attempts() + 1, reason);
            }
            case RETRY -> {
                metrics.processed(queue, "retry");
                log.info("job_retry_scheduled queue={} id={} attempts={} error={}", queue, job.id(), job.attempts() + 1, reason);
            }
            case LEASE_LOST -> metrics.processed(queue, "lease_lost");
        }
    }

    public String workerId() { return workerId; }

    @Override
    public synchronized void start() {
        if (!enabled || executor != null) return;
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bds-job-worker");
            thread.setDaemon(true);
            return thread;
        });
        service.scheduleWithFixedDelay(this::pollUntilIdle, pollMs, pollMs, TimeUnit.MILLISECONDS);
        executor = service;
        log.info("job_worker_started worker={} queues={} poll_ms={}", workerId, handlers.keySet(), pollMs);
    }

    @Override
    public synchronized void stop() {
        ScheduledExecutorService service = executor;
        if (service == null) return;
        service.shutdown();
        try {
            if (!service.awaitTermination(10, TimeUnit.SECONDS)) service.shutdownNow();
        } catch (InterruptedException ex) {
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    @Override
    public boolean isRunning() { return executor != null; }

    private void pollUntilIdle() {
        try {
            int processed;
            do {
                processed = runOnce();
            } while (processed > 0 && !Thread.currentThread().isInterrupted() && executor != null && !executor.isShutdown());
        } catch (RuntimeException ex) {
            log.warn("job_worker_poll_failed worker={} error={}", workerId, JobErrors.describe(ex));
        }
    }

    private static String defaultWorkerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            host = "unknown-host";
        }
        String id = host + "/" + ProcessHandle.current().pid() + "/" + UUID.randomUUID().toString().substring(0, 8);
        return id.length() <= 120 ? id : id.substring(id.length() - 120);
    }
}
