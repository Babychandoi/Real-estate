package com.company.bds.shared.redis;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One circuit breaker for the Redis calls made while serving requests: rate limiter, response caches, geocoding
 * throttle and notification fan-out. Redis is optional on the request path, so a Redis failure must never make every
 * request slow. The first failed call opens the breaker; while it is open every caller skips Redis immediately (the
 * rate limiter counts in its bounded local tables, caches are bypassed, notifications are delivered locally) for
 * {@code app.redis.breaker.open-for}. After that, one call probes Redis while the others keep skipping it; a successful
 * probe closes the breaker, a failed one opens it for another period. An outage therefore costs at most one failed call
 * per period instead of one timeout per request, and the service returns to Redis on its own.
 *
 * <p>The Lettuce options in {@link RedisClientConfig} keep that single call short: commands are rejected at once while
 * the connection is down and otherwise time out after {@code spring.data.redis.timeout}.</p>
 *
 * <p>Metrics: {@code bds.redis.breaker.state} (0 closed, 1 open, 2 half-open: a probe is due or running) and
 * {@code bds.redis.unavailable{caller,outcome}} with outcome {@code failed} (Redis was called and failed) or
 * {@code skipped} (Redis was not called because the breaker was open). Logs: one WARN {@code redis_unavailable} when it
 * opens and one INFO {@code redis_recovered} when it closes, at most one pair per {@value #LOG_INTERVAL_MILLIS} ms.</p>
 */
@Component
public class RedisCircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    static final long LOG_INTERVAL_MILLIS = 60_000;
    private static final Logger log = LoggerFactory.getLogger(RedisCircuitBreaker.class);

    private final Clock clock;
    private final long openForMillis;
    private final MeterRegistry meters;
    private final Map<String, Counter[]> counters = new ConcurrentHashMap<>();
    /** Read without locking on every call; changed only under the monitor. */
    private volatile boolean open;
    private volatile long probeAt;
    /** Start of the probe in flight, or -1. A probe that never reports back is replaced after one period. */
    private long probeStartedAt = -1;
    private long openedAt;
    private boolean everLogged;
    private long lastLoggedAt;
    private boolean outageLogged;

    @Autowired
    public RedisCircuitBreaker(Clock clock, ObjectProvider<MeterRegistry> meters,
                               @Value("${app.redis.breaker.open-for:PT5S}") Duration openFor) {
        this(clock, meters.getIfAvailable(SimpleMeterRegistry::new), openFor);
    }

    public RedisCircuitBreaker(Clock clock, MeterRegistry meters, Duration openFor) {
        if (openFor.isNegative() || openFor.isZero()) throw new IllegalArgumentException("app.redis.breaker.open-for must be positive");
        this.clock = clock;
        this.openForMillis = openFor.toMillis();
        this.meters = meters;
        Gauge.builder("bds.redis.breaker.state", this, breaker -> breaker.state().ordinal())
                .description("Redis circuit breaker of the request path: 0 closed, 1 open (Redis skipped), 2 half-open (probing)")
                .register(meters);
    }

    /**
     * Whether {@code caller} may call Redis now: always while closed; while open only the single probe once the period
     * is over. A caller that gets {@code true} must report the outcome with {@link #recordSuccess()} or
     * {@link #recordFailure(String, Throwable)}; one that gets {@code false} must not touch Redis.
     */
    public boolean tryAcquire(String caller) {
        if (!open) return true;
        if (clock.millis() >= probeAt && tryProbe()) return true;
        counter(caller)[1].increment();
        return false;
    }

    /**
     * A Redis call succeeded. While open, only a success after the probe was granted closes the breaker: a call that
     * was already running when another one failed proves little, and closing on it would let a flapping Redis make
     * requests wait for timeouts again right away.
     */
    public void recordSuccess() {
        if (!open) return;
        synchronized (this) {
            if (!open || probeStartedAt < 0) return;
            open = false;
            probeStartedAt = -1;
            if (outageLogged) log.info("redis_recovered downForMs={}", clock.millis() - openedAt);
        }
    }

    /** A Redis call failed (connection refused or lost, rejected while reconnecting, timeout, error reply). */
    public void recordFailure(String caller, Throwable error) {
        counter(caller)[0].increment();
        synchronized (this) {
            long now = clock.millis();
            if (!open) {
                openedAt = now;
                outageLogged = !everLogged || now - lastLoggedAt >= LOG_INTERVAL_MILLIS;
                if (outageLogged) {
                    everLogged = true;
                    lastLoggedAt = now;
                    log.warn("redis_unavailable caller={} error={} retryInMs={} effect=\"rate limits counted per instance, caches bypassed, "
                            + "notifications delivered locally\"", caller, describe(error), openForMillis);
                }
            }
            probeAt = now + openForMillis;
            probeStartedAt = -1;
            open = true;
        }
    }

    /** True while closed, i.e. Redis is being used. */
    public boolean available() { return !open; }

    public State state() {
        if (!open) return State.CLOSED;
        return clock.millis() >= probeAt ? State.HALF_OPEN : State.OPEN;
    }

    private synchronized boolean tryProbe() {
        if (!open) return true;
        long now = clock.millis();
        if (now < probeAt) return false;
        if (probeStartedAt >= 0 && now - probeStartedAt < openForMillis) return false;
        probeStartedAt = now;
        return true;
    }

    private Counter[] counter(String caller) {
        return counters.computeIfAbsent(caller, name -> new Counter[] {
                Counter.builder("bds.redis.unavailable").tag("caller", name).tag("outcome", "failed")
                        .description("Redis calls of the request path that failed, or were skipped because the breaker was open")
                        .register(meters),
                Counter.builder("bds.redis.unavailable").tag("caller", name).tag("outcome", "skipped")
                        .description("Redis calls of the request path that failed, or were skipped because the breaker was open")
                        .register(meters)});
    }

    /** Exception class and its root cause, without messages (no host names or keys in the log line). */
    private static String describe(Throwable error) {
        if (error == null) return "unknown";
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root == error ? error.getClass().getSimpleName() : error.getClass().getSimpleName() + "/" + root.getClass().getSimpleName();
    }
}
