package com.company.bds.search.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPageCacheTests {
    private static final SearchResults.CachedPage PAGE = new SearchResults.CachedPage(List.of(), false, null,
            new SearchResults.Total(0, "eq"), SearchResults.ENGINE_DATABASE, List.of());

    @Test
    void concurrentFallbackPagesRespectMemoryCapacity() throws Exception {
        var workers = Executors.newFixedThreadPool(16);
        try {
            for (int round = 0; round < 20; round++) {
                LocalPageCache cache = new LocalPageCache(8, Duration.ofSeconds(10));
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> requests = new ArrayList<>();
                for (int request = 0; request < 128; request++) {
                    String key = "request-" + request;
                    requests.add(workers.submit(() -> {
                        try {
                            if (!start.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("Requests did not start");
                            cache.put(key, PAGE, 1_000);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                    }));
                }
                start.countDown();
                for (Future<?> request : requests) request.get(2, TimeUnit.SECONDS);
                assertThat(cache.size()).isLessThanOrEqualTo(8);
            }
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void expiredFallbackPagesGiveCapacityBackWithoutReturningOldResults() {
        LocalPageCache cache = new LocalPageCache(1, Duration.ofSeconds(10));
        cache.put("old", PAGE, 1_000);
        cache.put("full", PAGE, 1_000);
        assertThat(cache.get("old", 10_999)).isSameAs(PAGE);
        assertThat(cache.get("full", 10_999)).isNull();
        cache.put("new", PAGE, 11_000);
        assertThat(cache.get("old", 11_000)).isNull();
        assertThat(cache.get("new", 11_000)).isSameAs(PAGE);
        assertThat(cache.size()).isEqualTo(1);
    }
}
