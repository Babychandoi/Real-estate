package com.company.bds.shared.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Polls every registered {@link JobHandler} queue ({@code app.jobs.enabled}, {@code app.jobs.poll-ms}). Safe with several
 * instances: claims use {@code SKIP LOCKED} leases and completion is guarded by the lease token and the enqueue sequence.
 *
 * <p>Robustness: the poll loop catches every {@link Throwable}, so no handler error (not even an {@link Error}) stops it;
 * a throwing handler fails its batch. Handlers run on separate threads with a time budget ({@link JobHandler#timeout()}):
 * a batch that exceeds it is interrupted and failed, and its queue is paused on this instance until the handler thread
 * returns, so one hung dependency cannot block the other queues or pile up threads. The loop records a heartbeat (also
 * while waiting for a handler); {@link #state()} reports the worker as stalled when the heartbeat is older than
 * {@code app.jobs.stall-after}, the poll thread is gone, or a queue is paused (see {@code JobWorkerHealthIndicator}).
 */
@Component
public class JobWorker implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    private static final int MAX_BATCHES_PER_DRAIN = 1_000;
    private static final long WAIT_SLICE_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final Duration MIN_TIMEOUT = Duration.ofSeconds(1);

    /** Snapshot for health and metrics. {@code heartbeatAge} is null before the worker ever ran. */
    public record State(boolean enabled, boolean running, @Nullable Instant lastHeartbeat, @Nullable Duration heartbeatAge,
                        Duration stallAfter, Map<String, Instant> pausedQueues) {
        /** Enabled, polling, heartbeat fresh and no queue paused by a hung handler. */
        public boolean healthy() {
            return enabled && running && heartbeatAge != null && heartbeatAge.compareTo(stallAfter) <= 0 && pausedQueues.isEmpty();
        }
    }

    /** A batch still running after its timeout; identity matters (see {@link #runHandler}). */
    private record HungBatch(Instant since, int jobs) {}

    private final JobStore store;
    private final JobMetrics metrics;
    private final JobBackoff backoff;
    private final Map<String, JobHandler> handlers;
    private final String workerId;
    private final boolean enabled;
    private final long pollMs;
    private final Duration stallAfter;
    private final Clock clock;
    private final Map<String, HungBatch> pausedQueues = new ConcurrentHashMap<>();
    private volatile ExecutorService handlerThreads;
    private volatile ScheduledExecutorService poller;
    private volatile Instant lastHeartbeat;

    @Autowired
    public JobWorker(JobStore store, JobMetrics metrics, ObjectProvider<JobHandler> handlers, Clock clock,
                     @Value("${app.jobs.enabled:true}") boolean enabled,
                     @Value("${app.jobs.poll-ms:1000}") long pollMs,
                     @Value("${app.jobs.retry-base:PT30S}") Duration retryBase,
                     @Value("${app.jobs.retry-cap:PT1H}") Duration retryCap,
                     @Value("${app.jobs.stall-after:PT2M}") Duration stallAfter) {
        this(store, metrics, handlers.orderedStream().toList(), enabled, pollMs, new JobBackoff(retryBase, retryCap),
                defaultWorkerId(), clock, stallAfter);
        metrics.registerWorker(this);
    }

    JobWorker(JobStore store, JobMetrics metrics, Collection<JobHandler> handlers, boolean enabled, long pollMs,
              JobBackoff backoff, String workerId, Clock clock, Duration stallAfter) {
        this.store = store;
        this.metrics = metrics;
        this.backoff = backoff;
        this.enabled = enabled;
        this.pollMs = Math.max(50, pollMs);
        this.workerId = workerId;
        this.clock = clock;
        this.stallAfter = stallAfter;
        Map<String, JobHandler> byQueue = new LinkedHashMap<>();
        for (JobHandler handler : handlers) {
            if (byQueue.putIfAbsent(handler.queue(), handler) != null) {
                throw new IllegalStateException("Two JobHandler beans are registered for queue " + handler.queue());
            }
            metrics.register(handler.queue());
        }
        this.handlers = Map.copyOf(byQueue);
        this.handlerThreads = newHandlerThreads();
    }

    /** One batch for every registered queue; returns the number of jobs claimed. Never throws. */
    public int runOnce() {
        heartbeat();
        int processed = 0;
        for (JobHandler handler : handlers.values()) {
            if (Thread.currentThread().isInterrupted()) break;
            try {
                processed += processBatch(handler);
            } catch (Throwable error) { // Errors included: one bad batch must never stop the poll loop
                metrics.processed(handler.queue(), "batch_error");
                log.error("job_batch_failed queue={} worker={} error={}", handler.queue(), workerId, describe(error));
            }
            heartbeat();
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

    /**
     * Claims one batch of the handler's queue, runs the handler within its time budget and records each job's outcome.
     * Returns 0 without claiming while the queue is paused by a hung batch.
     */
    public int processBatch(JobHandler handler) {
        String queue = handler.queue();
        if (pausedQueues.containsKey(queue)) return 0;
        heartbeat();
        List<ClaimedJob> claimed = store.claim(queue, handler.batchSize(), handler.lease(), workerId, handler.maxAttempts());
        if (claimed.isEmpty()) return 0;
        Set<String> scrub = handler.sensitivePayloadKeys();
        List<ClaimedJob> runnable = new ArrayList<>();
        for (ClaimedJob job : claimed) {
            if (job.expiredAttempt() && job.attempts() >= job.maxAttempts()) {
                // Every attempt of this version ended with an expired lease (e.g. the job keeps killing its worker).
                String reason = "Lease expired before completion on all " + job.attempts() + " attempts";
                recordFailure(queue, job, store.deadLetter(job, reason, false, scrub), reason, job.attempts());
            } else {
                runnable.add(job);
            }
        }
        if (runnable.isEmpty()) return claimed.size();
        Optional<JobBatchResult> outcome = runHandler(handler, runnable);
        if (outcome.isEmpty()) { // the worker is stopping: hand the jobs back without counting an attempt
            runnable.forEach(store::release);
            metrics.processed(queue, "released");
            return claimed.size();
        }
        JobBatchResult result = outcome.get();
        for (ClaimedJob job : runnable) {
            heartbeat();
            String permanent = result.permanentFailures().get(job.id());
            if (permanent != null) {
                String reason = JobErrors.sanitize(permanent);
                recordFailure(queue, job, store.deadLetter(job, reason, true, scrub), reason, job.attempts() + 1);
            } else if (result.succeeded().contains(job.id())) {
                JobStore.CompletionOutcome completion = store.complete(job, scrub);
                metrics.processed(queue, switch (completion) {
                    case COMPLETED -> "success";
                    case RERUN -> "rerun";
                    case LEASE_LOST -> "lease_lost";
                });
            } else {
                String reason = JobErrors.sanitize(result.failures().getOrDefault(job.id(), "Handler did not report the job as succeeded"));
                recordFailure(queue, job, store.fail(job, reason, backoff.delayAfter(job.attempts()), scrub), reason, job.attempts() + 1);
            }
        }
        return claimed.size();
    }

    /**
     * Runs {@code handle} on a handler thread and waits at most the handler's time budget, refreshing the heartbeat while
     * waiting. A throwing handler (any {@link Throwable}) fails the batch; a hung one is interrupted, fails the batch and
     * pauses its queue until its thread returns. Empty when this worker thread was interrupted (shutdown).
     */
    private Optional<JobBatchResult> runHandler(JobHandler handler, List<ClaimedJob> jobs) {
        String queue = handler.queue();
        Duration timeout = effectiveTimeout(handler);
        HungBatch marker = new HungBatch(clock.instant(), jobs.size());
        AtomicBoolean finished = new AtomicBoolean();
        AtomicBoolean abandoned = new AtomicBoolean();
        Future<JobBatchResult> future;
        try {
            future = handlerThreads.submit(() -> {
                try {
                    return Objects.requireNonNull(handler.handle(List.copyOf(jobs)), "JobHandler returned null");
                } finally {
                    finished.set(true);
                    if (abandoned.get() && pausedQueues.remove(queue, marker)) {
                        log.warn("job_handler_returned_after_timeout queue={} worker={} jobs={}", queue, workerId, marker.jobs());
                    }
                }
            });
        } catch (RejectedExecutionException stopping) {
            return Optional.empty();
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            heartbeat();
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            try {
                return Optional.of(future.get(Math.min(remaining, WAIT_SLICE_NANOS), TimeUnit.NANOSECONDS));
            } catch (TimeoutException slice) {
                // still running; the heartbeat above keeps showing that this worker is alive
            } catch (ExecutionException failed) {
                Throwable cause = failed.getCause() == null ? failed : failed.getCause();
                return Optional.of(JobBatchResult.allFailed(jobs, describe(cause)));
            } catch (CancellationException cancelled) {
                return Optional.of(JobBatchResult.allFailed(jobs, "Handler was cancelled"));
            } catch (InterruptedException interrupted) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        // Order matters: publish the pause before flagging the batch abandoned, then undo it if the handler just returned.
        pausedQueues.put(queue, marker);
        abandoned.set(true);
        if (finished.get()) pausedQueues.remove(queue, marker);
        future.cancel(true);
        metrics.handlerTimedOut(queue);
        log.error("job_handler_timed_out queue={} worker={} jobs={} timeout_s={}", queue, workerId, jobs.size(), timeout.toSeconds());
        return Optional.of(JobBatchResult.allFailed(jobs, "Handler exceeded its time budget of " + timeout.toSeconds() + " s"));
    }

    private void recordFailure(String queue, ClaimedJob job, JobStore.FailureOutcome outcome, String reason, int attempts) {
        switch (outcome) {
            case DEAD -> {
                metrics.processed(queue, "dead");
                log.warn("job_dead_lettered queue={} id={} attempts={} max_attempts={} error={}",
                        queue, job.id(), attempts, job.maxAttempts(), reason);
            }
            case RETRY -> {
                metrics.processed(queue, "retry");
                log.info("job_retry_scheduled queue={} id={} attempts={} max_attempts={} error={}",
                        queue, job.id(), attempts, job.maxAttempts(), reason);
            }
            case RERUN -> {
                metrics.processed(queue, "rerun");
                log.info("job_rerun_newer_change queue={} id={} error={}", queue, job.id(), reason);
            }
            case LEASE_LOST -> metrics.processed(queue, "lease_lost");
        }
    }

    public State state() {
        Instant now = clock.instant();
        Instant beat = lastHeartbeat;
        Map<String, Instant> paused = new LinkedHashMap<>();
        pausedQueues.forEach((queue, batch) -> paused.put(queue, batch.since()));
        ScheduledExecutorService current = poller;
        return new State(enabled, current != null && !current.isShutdown(), beat, beat == null ? null : Duration.between(beat, now),
                stallAfter, Map.copyOf(paused));
    }

    public String workerId() { return workerId; }

    Set<String> queues() { return handlers.keySet(); }

    @Override
    public synchronized void start() {
        if (!enabled || poller != null) return;
        if (handlerThreads.isShutdown()) handlerThreads = newHandlerThreads();
        heartbeat(); // the reference for stall detection until the first loop completes
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(daemonThreads("bds-job-worker"));
        service.scheduleWithFixedDelay(this::pollUntilIdle, pollMs, pollMs, TimeUnit.MILLISECONDS);
        poller = service;
        log.info("job_worker_started worker={} queues={} poll_ms={}", workerId, handlers.keySet(), pollMs);
    }

    @Override
    public synchronized void stop() {
        ScheduledExecutorService service = poller;
        if (service == null) return;
        poller = null;
        service.shutdown();
        try {
            if (!service.awaitTermination(10, TimeUnit.SECONDS)) service.shutdownNow();
        } catch (InterruptedException ex) {
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
        handlerThreads.shutdownNow();
        log.info("job_worker_stopped worker={}", workerId);
    }

    @Override
    public boolean isRunning() { return poller != null; }

    private void pollUntilIdle() {
        try {
            int processed;
            do {
                processed = runOnce();
            } while (processed > 0 && poller != null && !Thread.currentThread().isInterrupted());
        } catch (Throwable error) { // never let the scheduled task die (a thrown Throwable cancels scheduleWithFixedDelay)
            log.error("job_worker_poll_failed worker={} error={}", workerId, describe(error));
        }
    }

    private void heartbeat() {
        lastHeartbeat = clock.instant();
    }

    private static Duration effectiveTimeout(JobHandler handler) {
        Duration max = handler.lease().multipliedBy(9).dividedBy(10);
        Duration timeout = handler.timeout().compareTo(max) > 0 ? max : handler.timeout();
        return timeout.compareTo(MIN_TIMEOUT) < 0 ? MIN_TIMEOUT : timeout;
    }

    private static String describe(Throwable error) {
        try {
            return JobErrors.describe(error);
        } catch (Throwable ignored) { // e.g. no memory left to build the message
            return error.getClass().getName();
        }
    }

    private static ExecutorService newHandlerThreads() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                daemonThreads("bds-job-handler"));
        return pool;
    }

    private static ThreadFactory daemonThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
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
