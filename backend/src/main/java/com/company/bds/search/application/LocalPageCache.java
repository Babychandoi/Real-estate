package com.company.bds.search.application;

import com.company.bds.search.application.SearchResults.CachedPage;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small bounded per-instance cache of first pages with a short TTL. Used only when neither Elasticsearch nor the shared
 * Redis cache is available (W6-PERF): without it every repeated degraded first page and its capped count would go to the
 * database. Entries hold ids only; rows are re-read and re-checked on every hit, as with the Redis cache.
 */
final class LocalPageCache {
    private record Entry(CachedPage page, long expiresAt) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final long ttlMillis;

    LocalPageCache(int maxEntries, Duration ttl) {
        this.maxEntries = maxEntries;
        this.ttlMillis = ttl.toMillis();
    }

    CachedPage get(String key, long nowMillis) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expiresAt() <= nowMillis) {
            entries.remove(key, entry);
            return null;
        }
        return entry.page();
    }

    // Serialize admission so concurrent outage requests cannot all observe free capacity and exceed the memory bound.
    synchronized void put(String key, CachedPage page, long nowMillis) {
        if (page == null) return;
        if (entries.size() >= maxEntries) evict(nowMillis);
        if (entries.size() >= maxEntries) return; // full of live entries: do not grow beyond the bound
        entries.put(key, new Entry(page, nowMillis + ttlMillis));
    }

    int size() {
        return entries.size();
    }

    private void evict(long nowMillis) {
        for (Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().expiresAt() <= nowMillis) it.remove();
        }
    }
}
