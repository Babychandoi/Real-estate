package com.company.bds.search;

import com.company.bds.search.application.ListingSearchService;
import com.company.bds.search.application.SearchCircuitBreaker;
import com.company.bds.search.application.SearchCursorCodec;
import com.company.bds.search.application.SearchIndexSettings;
import com.company.bds.search.application.SearchResults;
import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.application.port.ListingSearchEnginePort;
import com.company.bds.search.application.port.ResponseCachePort;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.domain.SearchFilterParser;
import com.company.bds.testsupport.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Review checks for W6-PERF (degraded first pages cached under the database key). The two gaps the review recorded
 * (half-open probe in flight; Redis down as well) are closed: those tests now assert the fixed behaviour.
 */
class SearchDegradedCacheReviewTests {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-03T00:00:00Z"));
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ListingReadModelPort readModel = mock(ListingReadModelPort.class);
    private final ListingSearchEnginePort engine = mock(ListingSearchEnginePort.class);
    private final SearchCircuitBreaker breaker = new SearchCircuitBreaker(clock, meters(), 20, 1, 0.5, Duration.ofSeconds(30));
    private final MapCache cache = new MapCache();
    private final ListingSearchService service;

    SearchDegradedCacheReviewTests() {
        SearchIndexSettings settings = new SearchIndexSettings(true, "w6-review");
        settings.markReady(true);
        when(engine.ready()).thenReturn(true);
        // a full first page (25 rows for size 24): hasNext, so the database engine also runs countCapped
        List<PublicListing> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            PublicListing row = mock(PublicListing.class);
            when(row.listingId()).thenReturn(UUID.randomUUID());
            rows.add(row);
        }
        when(readModel.page(any(), any(), any(), anyInt())).thenReturn(rows);
        when(readModel.keysOf(any(), any())).thenReturn(new ObjectMapper().createArrayNode().add(1));
        when(readModel.countCapped(any(), anyInt())).thenReturn(10_001L);
        when(readModel.findByIds(any())).thenReturn(List.of());
        when(engine.search(any(), any(), any(), anyInt(), anyBoolean(), anyInt()))
                .thenReturn(new ListingSearchEnginePort.Hits(List.of(), null));
        service = new ListingSearchService(readModel, engine, breaker,
                new SearchCursorCodec(new ObjectMapper(), clock, "w6-review-degraded-cache-test-secret-0123456789"),
                cache, settings, clock, meters(), true);
    }

    private org.springframework.beans.factory.ObjectProvider<MeterRegistry> meters() {
        return new StaticListableBeanFactory(Map.of("meterRegistry", registry)).getBeanProvider(MeterRegistry.class);
    }

    private SearchResults.Page firstSalePage() {
        return service.search(SearchFilterParser.parse(Map.of("purpose", new String[] {"SALE"}, "size", new String[] {"24"})));
    }

    @Test
    void theCappedCountIsAlsoServedFromTheCacheWhileTheBreakerIsOpen() {
        breaker.recordFailure();
        SearchResults.Page first = firstSalePage();
        SearchResults.Page second = firstSalePage();
        assertThat(first.degraded()).isTrue();
        assertThat(second.degraded()).isTrue();
        assertThat(second.notices()).contains(SearchResults.NOTICE_ENGINE_UNAVAILABLE);
        assertThat(second.total()).isEqualTo(first.total());
        verify(readModel, times(1)).page(any(), any(), any(), anyInt());
        verify(readModel, times(1)).countCapped(any(), anyInt());
    }

    @Test
    void afterTheBreakerClosesTheCachedDegradedPageIsNoLongerServed() {
        breaker.recordFailure();
        assertThat(firstSalePage().degraded()).isTrue(); // cached under the database key
        clock.advance(Duration.ofSeconds(31)); // half-open; the probe (engine.search) succeeds
        SearchResults.Page recovered = firstSalePage();
        assertThat(breaker.state()).isEqualTo(SearchCircuitBreaker.State.CLOSED);
        assertThat(recovered.degraded()).isFalse();
        assertThat(recovered.engine()).isEqualTo(SearchResults.ENGINE_SEARCH);
        // and the database-key entry is not read again once the breaker is closed
        assertThat(firstSalePage().degraded()).isFalse();
        verify(readModel, times(1)).page(any(), any(), any(), anyInt());
    }

    @Test
    void whileTheHalfOpenProbeIsInFlightRepeatedFirstPagesAreServedFromTheDatabaseKey() {
        breaker.recordFailure();
        firstSalePage(); // cached under the database key
        clock.advance(Duration.ofSeconds(31));
        assertThat(breaker.tryAcquire()).isTrue(); // another request holds the half-open probe (ES hanging up to 800 ms)
        // callable() is false while the probe is in flight -> the database key is used -> cache hit
        for (int i = 0; i < 5; i++) assertThat(firstSalePage().degraded()).isTrue();
        verify(readModel, times(1)).page(any(), any(), any(), anyInt());
        verify(readModel, times(1)).countCapped(any(), anyInt());
        verify(engine, never()).search(any(), any(), any(), anyInt(), anyBoolean(), anyInt());
    }

    @Test
    void withRedisAlsoDownDegradedFirstPagesAreKeptBrieflyInMemory() {
        breaker.recordFailure();
        cache.generation = -1; // Redis breaker open (ListingResponseCache.generation() == -1)
        for (int i = 0; i < 5; i++) assertThat(firstSalePage().degraded()).isTrue();
        verify(readModel, times(1)).page(any(), any(), any(), anyInt());
        verify(readModel, times(1)).countCapped(any(), anyInt());
        clock.advance(Duration.ofSeconds(11)); // the in-memory entry expires after 10 s
        firstSalePage();
        verify(readModel, times(2)).page(any(), any(), any(), anyInt());
        verify(engine, never()).search(any(), any(), any(), anyInt(), anyBoolean(), anyInt());
    }

    @Test
    void withRedisDownButTheEngineHealthyNothingIsKeptInMemory() {
        cache.generation = -1;
        for (int i = 0; i < 3; i++) assertThat(firstSalePage().degraded()).isFalse();
        verify(engine, times(3)).search(any(), any(), any(), anyInt(), anyBoolean(), anyInt());
    }

    /** In-memory {@link ResponseCachePort}: stores non-null values. */
    private static final class MapCache implements ResponseCachePort {
        private final Map<String, Object> values = new HashMap<>();
        long generation = 0;

        @Override
        public <T> T getOrCompute(String cacheName, String key, Duration ttl, Class<T> type, Supplier<T> compute) {
            Object cached = values.get(key);
            if (cached != null) return type.cast(cached);
            T value = compute.get();
            if (value != null) values.put(key, value);
            return value;
        }

        @Override
        public long generation() {
            return generation;
        }

        @Override
        public <T> T collapse(String key, Supplier<T> compute) {
            return compute.get();
        }
    }
}
