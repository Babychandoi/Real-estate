package com.company.bds.search.infrastructure.cache;

import com.company.bds.shared.redis.RedisCircuitBreaker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Redis cache for public listing reads (audit §7.3, F10.1/F10.4). Keys are versioned by the caller (listing row version,
 * search generation), so invalidation never needs a scan. Protection against stampedes: a short Redis lock lets one
 * request compute while the others wait briefly for its value; identical concurrent computations inside one JVM are
 * collapsed (single flight) — also while Redis is down, which bounds the database load in that case. TTLs get ±20 %
 * jitter. A Redis error never fails the request: it opens the shared {@link RedisCircuitBreaker} and the cache is
 * bypassed (without calling Redis) until the breaker's probe succeeds. An unreadable cached value is a miss and is
 * overwritten; it says nothing about Redis' health.
 */
@Component
public class ListingResponseCache implements com.company.bds.search.application.port.ResponseCachePort {
    static final String REDIS_CALLER = "search-cache";
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);

    private final StringRedisTemplate redis;
    private final RedisCircuitBreaker breaker;
    private final ObjectMapper json;
    private final boolean enabled;
    private final String prefix;
    private final MeterRegistry meters;
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    public ListingResponseCache(ObjectProvider<StringRedisTemplate> redis, RedisCircuitBreaker breaker, ObjectMapper json,
                                ObjectProvider<MeterRegistry> meters,
                                @Value("${app.search.cache.enabled:true}") boolean enabled,
                                @Value("${app.search.cache.prefix:bds:search:v1:}") String prefix) {
        this.redis = redis.getIfAvailable();
        this.breaker = breaker;
        this.json = json;
        this.enabled = enabled && this.redis != null;
        this.prefix = prefix;
        this.meters = meters.getIfAvailable(SimpleMeterRegistry::new);
    }

    /** Whether Redis is configured and the shared breaker is closed. */
    public boolean available() {
        return enabled && breaker.available();
    }

    /**
     * Cached value of {@code key}, else computes it once (per key across instances while Redis works, per JVM always),
     * stores it with {@code ttl} ± 20 % and returns it. {@code compute} returning null is not cached.
     */
    public <T> T getOrCompute(String cacheName, String key, Duration ttl, JavaType type, Supplier<T> compute) {
        String fullKey = prefix + key;
        if (!enabled || !breaker.tryAcquire(REDIS_CALLER)) {
            count(cacheName, "bypass");
            return unwrapped(fullKey, compute);
        }
        String lockKey = fullKey + ":lock";
        boolean leader;
        try {
            Optional<T> cached = read(fullKey, type);
            breaker.recordSuccess();
            if (cached.isPresent()) {
                count(cacheName, "hit");
                return cached.get();
            }
            leader = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL));
            if (!leader) {
                for (int i = 0; i < 6; i++) {
                    Thread.sleep(50);
                    cached = read(fullKey, type);
                    if (cached.isPresent()) {
                        count(cacheName, "hit");
                        return cached.get();
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            count(cacheName, "bypass");
            return unwrapped(fullKey, compute);
        } catch (RuntimeException ex) {
            breaker.recordFailure(REDIS_CALLER, ex);
            count(cacheName, "bypass");
            return unwrapped(fullKey, compute);
        }
        count(cacheName, "miss");
        T value = unwrapped(fullKey, compute);
        store(fullKey, value, ttl, leader ? lockKey : null);
        return value;
    }

    /** Current search generation (bumped after every index batch that changed rows); -1 when unknown (do not cache). */
    @Override
    public long generation() {
        if (!enabled || !breaker.tryAcquire(REDIS_CALLER)) return -1;
        String value;
        try {
            value = redis.opsForValue().get(prefix + "generation");
            breaker.recordSuccess();
        } catch (RuntimeException ex) {
            breaker.recordFailure(REDIS_CALLER, ex);
            return -1;
        }
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    public void bumpGeneration() {
        if (!enabled || !breaker.tryAcquire(REDIS_CALLER)) return;
        try {
            redis.opsForValue().increment(prefix + "generation");
            breaker.recordSuccess();
        } catch (RuntimeException ex) {
            breaker.recordFailure(REDIS_CALLER, ex);
        }
    }

    @Override
    public <T> T getOrCompute(String cacheName, String key, Duration ttl, Class<T> type, Supplier<T> compute) {
        return getOrCompute(cacheName, key, ttl, json.getTypeFactory().constructType(type), compute);
    }

    @Override
    public <T> T collapse(String key, Supplier<T> compute) {
        return unwrapped("collapse:" + key, compute);
    }

    static Duration jitter(Duration ttl) {
        double factor = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        return Duration.ofMillis(Math.max(1, (long) (ttl.toMillis() * factor)));
    }

    /** Redis errors propagate; a value that no longer deserialises (e.g. written by an older release) is a miss. */
    private <T> Optional<T> read(String key, JavaType type) {
        String value = redis.opsForValue().get(key);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(json.readValue(value, type));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private void store(String key, Object value, Duration ttl, String lockKey) {
        String payload = null;
        if (value != null) {
            try {
                payload = json.writeValueAsString(value);
            } catch (JsonProcessingException ex) {
                // not cacheable; the value is still returned
            }
        }
        try {
            if (payload != null) redis.opsForValue().set(key, payload, jitter(ttl));
            if (lockKey != null) redis.delete(lockKey);
        } catch (RuntimeException ex) {
            breaker.recordFailure(REDIS_CALLER, ex);
        }
    }

    private <T> T unwrapped(String key, Supplier<T> compute) {
        try {
            return singleFlight(key, compute);
        } catch (ComputeFailure failure) {
            throw failure.unwrap();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T singleFlight(String key, Supplier<T> compute) {
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            try {
                return (T) existing.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return compute.get();
            } catch (ExecutionException ex) {
                return compute.get();
            }
        }
        try {
            T value = compute.get();
            mine.complete(value);
            return value;
        } catch (RuntimeException ex) {
            mine.completeExceptionally(ex);
            throw new ComputeFailure(ex);
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private void count(String cacheName, String result) {
        meters.counter("bds.search.cache", "cache", cacheName, "result", result).increment();
    }

    /** Wraps an exception thrown by the computation so it is rethrown unchanged instead of disabling the cache. */
    private static final class ComputeFailure extends RuntimeException {
        ComputeFailure(RuntimeException cause) { super(cause); }

        RuntimeException unwrap() { return (RuntimeException) getCause(); }
    }
}
