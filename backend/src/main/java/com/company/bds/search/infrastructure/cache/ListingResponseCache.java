package com.company.bds.search.infrastructure.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
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
 * jitter. Any Redis error bypasses the cache for {@code app.search.cache.redis-retry} instead of failing the request.
 */
@Component
public class ListingResponseCache implements com.company.bds.search.application.port.ResponseCachePort {
    private static final Logger log = LoggerFactory.getLogger(ListingResponseCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean enabled;
    private final String prefix;
    private final Duration retryAfterFailure;
    private final MeterRegistry meters;
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();
    private volatile long redisDownUntilMillis;

    public ListingResponseCache(ObjectProvider<StringRedisTemplate> redis, ObjectMapper json, Clock clock,
                                ObjectProvider<MeterRegistry> meters,
                                @Value("${app.search.cache.enabled:true}") boolean enabled,
                                @Value("${app.search.cache.prefix:bds:search:v1:}") String prefix,
                                @Value("${app.search.cache.redis-retry:PT10S}") Duration retryAfterFailure) {
        this.redis = redis.getIfAvailable();
        this.json = json;
        this.clock = clock;
        this.enabled = enabled && this.redis != null;
        this.prefix = prefix;
        this.retryAfterFailure = retryAfterFailure;
        this.meters = meters.getIfAvailable(SimpleMeterRegistry::new);
    }

    /** Whether Redis is configured and not in its failure back-off window. */
    public boolean available() {
        return enabled && clock.millis() >= redisDownUntilMillis;
    }

    /**
     * Cached value of {@code key}, else computes it once (per key across instances while Redis works, per JVM always),
     * stores it with {@code ttl} ± 20 % and returns it. {@code compute} returning null is not cached.
     */
    public <T> T getOrCompute(String cacheName, String key, Duration ttl, JavaType type, Supplier<T> compute) {
        String fullKey = prefix + key;
        if (!available()) {
            count(cacheName, "bypass");
            return unwrapped(fullKey, compute);
        }
        try {
            Optional<T> cached = read(fullKey, type);
            if (cached.isPresent()) {
                count(cacheName, "hit");
                return cached.get();
            }
            String lockKey = fullKey + ":lock";
            boolean leader = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(3)));
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
            count(cacheName, "miss");
            T value = singleFlight(fullKey, compute);
            if (value != null) redis.opsForValue().set(fullKey, json.writeValueAsString(value), jitter(ttl));
            if (leader) redis.delete(lockKey);
            return value;
        } catch (ComputeFailure failure) {
            throw failure.unwrap();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return unwrapped(fullKey, compute);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            markDown(ex);
            count(cacheName, "bypass");
            return unwrapped(fullKey, compute);
        }
    }

    /** Current search generation (bumped after every index batch that changed rows); -1 when unknown (do not cache). */
    @Override
    public long generation() {
        if (!available()) return -1;
        try {
            String value = redis.opsForValue().get(prefix + "generation");
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException ex) {
            markDown(ex);
            return -1;
        }
    }

    public void bumpGeneration() {
        if (!available()) return;
        try {
            redis.opsForValue().increment(prefix + "generation");
        } catch (RuntimeException ex) {
            markDown(ex);
        }
    }

    @Override
    public <T> T getOrCompute(String cacheName, String key, Duration ttl, Class<T> type, Supplier<T> compute) {
        return getOrCompute(cacheName, key, ttl, json.getTypeFactory().constructType(type), compute);
    }

    static Duration jitter(Duration ttl) {
        double factor = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        return Duration.ofMillis(Math.max(1, (long) (ttl.toMillis() * factor)));
    }

    private <T> Optional<T> read(String key, JavaType type) throws com.fasterxml.jackson.core.JsonProcessingException {
        String value = redis.opsForValue().get(key);
        if (value == null) return Optional.empty();
        return Optional.of(json.readValue(value, type));
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

    private void markDown(Exception ex) {
        redisDownUntilMillis = clock.millis() + retryAfterFailure.toMillis();
        log.warn("Listing cache bypassed for {} after a Redis error: {}", retryAfterFailure, ex.getClass().getSimpleName());
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
