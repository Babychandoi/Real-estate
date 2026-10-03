package com.company.bds.search;

import com.company.bds.search.application.ListingSearchService;
import com.company.bds.search.application.SearchCircuitBreaker;
import com.company.bds.search.application.SearchCursorCodec;
import com.company.bds.search.application.SearchIndexSettings;
import com.company.bds.search.application.SearchResults;
import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.application.port.ListingSearchEnginePort;
import com.company.bds.search.application.port.ResponseCachePort;
import com.company.bds.search.domain.SearchFilterParser;
import com.company.bds.testsupport.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W6-PERF (R-5 controlled fallback): during an Elasticsearch outage the database answers every search. Measured at 100k
 * listings, 100 reads/s then saturated PostgreSQL because no degraded first page was cached, so each repeated first page
 * paid its page query and capped count again. With the breaker open the cache key is the database engine's own key, so
 * the degraded page is cached there; a fallback computed under the search engine's key still never is (its cursor
 * belongs to the database engine).
 */
class SearchDegradedFirstPageCacheTests {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-03T00:00:00Z"));
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ListingReadModelPort readModel = mock(ListingReadModelPort.class);
    private final ListingSearchEnginePort engine = mock(ListingSearchEnginePort.class);
    private final SearchCircuitBreaker breaker = new SearchCircuitBreaker(clock, meters(), 20, 1, 0.5, Duration.ofSeconds(30));
    private final ListingSearchService service;

    SearchDegradedFirstPageCacheTests() {
        SearchIndexSettings settings = new SearchIndexSettings(true, "w6-degraded-cache");
        settings.markReady(true);
        when(engine.ready()).thenReturn(true);
        when(readModel.page(any(), any(), any(), anyInt())).thenReturn(List.of());
        service = new ListingSearchService(readModel, engine, breaker,
                new SearchCursorCodec(new ObjectMapper(), clock, "w6-perf-degraded-cache-test-secret-0123456789"),
                new MapCache(), settings, clock, meters(), true);
    }

    private org.springframework.beans.factory.ObjectProvider<MeterRegistry> meters() {
        return new StaticListableBeanFactory(Map.of("meterRegistry", registry)).getBeanProvider(MeterRegistry.class);
    }

    private SearchResults.Page firstSalePage() {
        return service.search(SearchFilterParser.parse(Map.of("purpose", new String[] {"SALE"})));
    }

    @Test
    void withTheBreakerOpenARepeatedDegradedFirstPageIsServedFromTheCache() {
        breaker.recordFailure(); // min-calls 1: open
        assertThat(breaker.state()).isEqualTo(SearchCircuitBreaker.State.OPEN);

        SearchResults.Page first = firstSalePage();
        SearchResults.Page second = firstSalePage();

        assertThat(first.degraded()).isTrue();
        assertThat(second.degraded()).as("a cached degraded page still says so").isTrue();
        assertThat(second.engine()).isEqualTo(SearchResults.ENGINE_DATABASE);
        verify(readModel, times(1)).page(any(), any(), any(), anyInt());
    }

    @Test
    void aFallbackUnderTheSearchEngineKeyIsNeverCached() {
        when(engine.search(any(), any(), any(), anyInt(), anyBoolean(), anyInt()))
                .thenThrow(new ListingSearchEnginePort.EngineUnavailable("down", null));
        assertThat(breaker.state()).isEqualTo(SearchCircuitBreaker.State.CLOSED);

        SearchResults.Page first = firstSalePage(); // engine key, falls back, opens the breaker (min-calls 1)
        assertThat(first.degraded()).isTrue();
        assertThat(breaker.state()).isEqualTo(SearchCircuitBreaker.State.OPEN);
        firstSalePage(); // database key now: computed (not the engine key's entry), then cached
        firstSalePage(); // served from the database key's entry

        verify(readModel, times(2)).page(any(), any(), any(), anyInt());
    }

    /** In-memory {@link ResponseCachePort}: stores non-null values, generation 0. */
    private static final class MapCache implements ResponseCachePort {
        private final Map<String, Object> values = new HashMap<>();

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
            return 0;
        }

        @Override
        public <T> T collapse(String key, Supplier<T> compute) {
            return compute.get();
        }
    }
}
