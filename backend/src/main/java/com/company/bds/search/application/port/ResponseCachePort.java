package com.company.bds.search.application.port;

import java.time.Duration;
import java.util.function.Supplier;

/** Versioned-key response cache with stampede protection; failures of the cache never fail the request. */
public interface ResponseCachePort {

    <T> T getOrCompute(String cacheName, String key, Duration ttl, Class<T> type, Supplier<T> compute);

    /** Search generation for cache keys, or -1 when the cache is unavailable (then do not cache). */
    long generation();

    /**
     * Runs {@code compute} once for concurrent callers with the same key in this JVM and gives them all its result;
     * nothing is stored. For reads that cannot be cached right now (e.g. no generation while Redis is down), so a burst
     * of identical requests still costs the database one computation at a time.
     */
    <T> T collapse(String key, Supplier<T> compute);
}
