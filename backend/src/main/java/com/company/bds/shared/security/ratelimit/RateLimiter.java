package com.company.bds.shared.security.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Counts a request against every applicable rule of its policy and decides (audit F13.2–F13.3).
 *
 * <p>Redis is the shared store. After a Redis failure the limiter counts in the bounded {@link LocalRateLimitStore}
 * for {@code redis-retry-interval} before probing Redis again, so an outage costs one timeout per interval instead of
 * one per request. Credential policies ({@code FAIL_CLOSED}) and the rest ({@code EVICT}) use separate local tables so
 * a flood on public reads cannot evict login counters.</p>
 *
 * <p>Metrics: {@code bds.ratelimit.rejected{policy,dimension}} (dimension {@code capacity} = fail-closed table full),
 * {@code bds.ratelimit.fallback{policy}} (decisions taken locally), {@code bds.ratelimit.redis.errors},
 * {@code bds.ratelimit.redis.available} (1/0) and {@code bds.ratelimit.local.entries{table}}.</p>
 */
@Component
public class RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    static final String CAPACITY_TAG = "capacity";

    private final RedisRateLimitStore redis;
    private final LocalRateLimitStore strictTable;
    private final LocalRateLimitStore generalTable;
    private final Clock clock;
    private final long redisRetryMillis;
    private final byte[] pepper;
    private final MeterRegistry meters;
    private final Map<String, Counter> rejected = new ConcurrentHashMap<>();
    private final Map<String, Counter> fallback = new ConcurrentHashMap<>();
    private final Counter redisErrors;
    private final AtomicBoolean redisDown = new AtomicBoolean(false);
    private volatile long redisRetryAt;

    @Autowired
    public RateLimiter(RateLimitProperties properties, RateLimitPolicies policies, ObjectProvider<StringRedisTemplate> redis,
                       ObjectProvider<MeterRegistry> meters, Clock clock) {
        this(properties, policies, redis.getIfAvailable(), meters.getIfAvailable(), clock);
    }

    RateLimiter(RateLimitProperties properties, RateLimitPolicies policies, StringRedisTemplate redis,
                MeterRegistry meters, Clock clock) {
        this.redis = redis == null ? null : new RedisRateLimitStore(redis);
        this.strictTable = new LocalRateLimitStore(properties.getLocalStrictMaxEntries(), clock);
        this.generalTable = new LocalRateLimitStore(properties.getLocalMaxEntries(), clock);
        this.clock = clock;
        this.redisRetryMillis = Math.max(100, properties.getRedisRetryInterval().toMillis());
        this.pepper = properties.getKeyPepper().getBytes(StandardCharsets.UTF_8);
        this.meters = meters != null ? meters : new SimpleMeterRegistry();
        this.redisErrors = Counter.builder("bds.ratelimit.redis.errors")
                .description("Redis calls of the rate limiter that failed (the request was counted locally)").register(this.meters);
        Gauge.builder("bds.ratelimit.redis.available", this, limiter -> limiter.redisAvailable() ? 1 : 0)
                .description("1 while the rate limiter uses Redis, 0 while it counts in the local fallback table").register(this.meters);
        Gauge.builder("bds.ratelimit.local.entries", strictTable, LocalRateLimitStore::size).tag("table", "strict")
                .description("Live windows in the bounded local fallback table").register(this.meters);
        Gauge.builder("bds.ratelimit.local.entries", generalTable, LocalRateLimitStore::size).tag("table", "general")
                .description("Live windows in the bounded local fallback table").register(this.meters);
        // Register every series up front so dashboards show 0 instead of "no data" before the first rejection.
        for (RateLimitPolicy policy : policies.all()) {
            fallbackCounter(policy.name());
            policy.rules().forEach(rule -> rejectedCounter(policy.name(), rule.dimension().tag()));
            if (policy.failureMode() == RateLimitFailureMode.FAIL_CLOSED) rejectedCounter(policy.name(), CAPACITY_TAG);
        }
    }

    /** Counts the request for every rule whose subject is present; rules without a subject (anonymous user) are skipped. */
    public RateLimitDecision check(RateLimitPolicy policy, Map<RateLimitDimension, String> subjects) {
        List<RateLimitKey> keys = new ArrayList<>(policy.rules().size());
        for (RateLimitRule rule : policy.rules()) {
            String subject = subjects.get(rule.dimension());
            if (subject != null && !subject.isBlank()) keys.add(RateLimitKey.of(pepper, policy, rule, subject));
        }
        if (keys.isEmpty()) return RateLimitDecision.allow(policy.name(), false);

        List<RateLimitCounter> counters = countInRedis(keys);
        boolean local = counters == null;
        if (local) {
            fallbackCounter(policy.name()).increment();
            LocalRateLimitStore table = policy.failureMode() == RateLimitFailureMode.FAIL_CLOSED ? strictTable : generalTable;
            counters = new ArrayList<>(keys.size());
            for (RateLimitKey key : keys) counters.add(table.increment(key, policy.failureMode()));
        }
        return decide(policy, keys, counters, local);
    }

    public boolean redisAvailable() { return redis != null && !redisDown.get(); }

    int localEntries(RateLimitFailureMode mode) {
        return (mode == RateLimitFailureMode.FAIL_CLOSED ? strictTable : generalTable).size();
    }

    int localCapacity(RateLimitFailureMode mode) {
        return (mode == RateLimitFailureMode.FAIL_CLOSED ? strictTable : generalTable).capacity();
    }

    private List<RateLimitCounter> countInRedis(List<RateLimitKey> keys) {
        if (redis == null) return null;
        long now = clock.millis();
        if (now < redisRetryAt) return null;
        try {
            List<RateLimitCounter> counters = redis.increment(keys);
            redisRetryAt = 0;
            if (redisDown.compareAndSet(true, false)) log.info("rate_limit_redis_recovered store=redis");
            return counters;
        } catch (RuntimeException ex) {
            redisErrors.increment();
            redisRetryAt = now + redisRetryMillis;
            if (redisDown.compareAndSet(false, true)) {
                log.warn("rate_limit_redis_unavailable fallback=local retryInMs={} error={}", redisRetryMillis, ex.getClass().getSimpleName());
            }
            return null;
        }
    }

    private RateLimitDecision decide(RateLimitPolicy policy, List<RateLimitKey> keys, List<RateLimitCounter> counters, boolean local) {
        RateLimitDimension violated = null;
        boolean capacity = false;
        long retryMillis = 0;
        for (int i = 0; i < keys.size(); i++) {
            RateLimitCounter counter = counters.get(i);
            RateLimitKey key = keys.get(i);
            if (counter.capacityExceeded()) {
                capacity = true;
                retryMillis = Math.max(retryMillis, counter.ttlMillis());
            } else if (counter.count() > key.limit()) {
                if (violated == null) violated = key.dimension();
                retryMillis = Math.max(retryMillis, counter.ttlMillis());
            }
        }
        if (violated == null && !capacity) return RateLimitDecision.allow(policy.name(), local);
        rejectedCounter(policy.name(), violated != null ? violated.tag() : CAPACITY_TAG).increment();
        long seconds = Math.max(1, (retryMillis + 999) / 1000);
        return new RateLimitDecision(false, policy.name(), violated, capacity, seconds, local);
    }

    private Counter rejectedCounter(String policy, String dimension) {
        return rejected.computeIfAbsent(policy + '|' + dimension, ignored -> Counter.builder("bds.ratelimit.rejected")
                .tag("policy", policy).tag("dimension", dimension)
                .description("Requests rejected with 429 by the rate limiter").register(meters));
    }

    private Counter fallbackCounter(String policy) {
        return fallback.computeIfAbsent(policy, ignored -> Counter.builder("bds.ratelimit.fallback")
                .tag("policy", policy)
                .description("Rate-limit decisions taken by the local fallback table because Redis was unavailable").register(meters));
    }
}
