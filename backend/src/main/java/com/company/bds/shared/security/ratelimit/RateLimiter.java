package com.company.bds.shared.security.ratelimit;

import com.company.bds.shared.redis.RedisCircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

/**
 * Counts a request against every applicable rule of its policy and decides (audit F13.2–F13.3).
 *
 * <p>Redis is the shared store. While Redis is unavailable (the shared {@link RedisCircuitBreaker} is open) the limiter
 * counts in the bounded {@link LocalRateLimitStore} without calling Redis, so an outage costs one failed call per
 * breaker period instead of one timeout per request; the breaker's probe brings it back to Redis. Credential policies
 * ({@code FAIL_CLOSED}) and the rest ({@code EVICT}) use separate local tables so a flood on public reads cannot evict
 * login counters.</p>
 *
 * <p>Metrics: {@code bds.ratelimit.rejected{policy,dimension}} (dimension {@code capacity} = fail-closed table full),
 * {@code bds.ratelimit.fallback{policy}} (decisions taken locally), {@code bds.ratelimit.redis.errors},
 * {@code bds.ratelimit.redis.available} (1/0) and {@code bds.ratelimit.local.entries{table}}.</p>
 */
@Component
public class RateLimiter {
    static final String CAPACITY_TAG = "capacity";
    static final String REDIS_CALLER = "rate-limit";

    private final RedisRateLimitStore redis;
    private final RedisCircuitBreaker breaker;
    private final LocalRateLimitStore strictTable;
    private final LocalRateLimitStore generalTable;
    private final byte[] pepper;
    private final MeterRegistry meters;
    private final Map<String, Counter> rejected = new ConcurrentHashMap<>();
    private final Map<String, Counter> fallback = new ConcurrentHashMap<>();
    private final Counter redisErrors;

    @Autowired
    public RateLimiter(RateLimitProperties properties, RateLimitPolicies policies, ObjectProvider<StringRedisTemplate> redis,
                       RedisCircuitBreaker breaker, ObjectProvider<MeterRegistry> meters, Clock clock) {
        this(properties, policies, redis.getIfAvailable(), breaker, meters.getIfAvailable(), clock);
    }

    RateLimiter(RateLimitProperties properties, RateLimitPolicies policies, StringRedisTemplate redis,
                RedisCircuitBreaker breaker, MeterRegistry meters, Clock clock) {
        this.redis = redis == null ? null : new RedisRateLimitStore(redis);
        this.breaker = breaker;
        this.strictTable = new LocalRateLimitStore(properties.getLocalStrictMaxEntries(), clock);
        this.generalTable = new LocalRateLimitStore(properties.getLocalMaxEntries(), clock);
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
            for (RateLimitKey key : keys) {
                RateLimitCounter counter = table.increment(key, policy.failureMode());
                counters.add(counter);
                // Same rule order and early stop as the Redis script: a rejected request allocates no further slots,
                // so one address rotating e-mails cannot fill the fail-closed table.
                if (counter.capacityExceeded() || counter.count() > key.limit()) break;
            }
        }
        return decide(policy, keys, counters, local);
    }

    public boolean redisAvailable() { return redis != null && breaker.available(); }

    int localEntries(RateLimitFailureMode mode) {
        return (mode == RateLimitFailureMode.FAIL_CLOSED ? strictTable : generalTable).size();
    }

    int localCapacity(RateLimitFailureMode mode) {
        return (mode == RateLimitFailureMode.FAIL_CLOSED ? strictTable : generalTable).capacity();
    }

    /** Counters from Redis, or null when the decision must be taken locally (no Redis, breaker open, call failed). */
    private List<RateLimitCounter> countInRedis(List<RateLimitKey> keys) {
        if (redis == null || !breaker.tryAcquire(REDIS_CALLER)) return null;
        try {
            List<RateLimitCounter> counters = redis.increment(keys);
            breaker.recordSuccess();
            return counters;
        } catch (RuntimeException ex) {
            redisErrors.increment();
            breaker.recordFailure(REDIS_CALLER, ex);
            return null;
        }
    }

    private RateLimitDecision decide(RateLimitPolicy policy, List<RateLimitKey> keys, List<RateLimitCounter> counters, boolean local) {
        RateLimitDimension violated = null;
        boolean capacity = false;
        long retryMillis = 0;
        for (int i = 0; i < counters.size(); i++) {
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
