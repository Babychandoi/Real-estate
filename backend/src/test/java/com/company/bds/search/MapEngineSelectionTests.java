package com.company.bds.search;

import com.company.bds.media.PublicImageResolver;
import com.company.bds.search.application.ListingReadService;
import com.company.bds.search.application.SearchCircuitBreaker;
import com.company.bds.search.application.SearchCursorCodec;
import com.company.bds.search.application.SearchIndexSettings;
import com.company.bds.search.application.SearchResults;
import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.application.port.ListingReadModelPort.MapCluster;
import com.company.bds.search.application.port.ListingReadModelPort.MapPoint;
import com.company.bds.search.application.port.ListingSearchEnginePort;
import com.company.bds.search.application.port.ResponseCachePort;
import com.company.bds.search.domain.BoundingBox;
import com.company.bds.search.domain.SearchFilter;
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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W6-PERF map: no 10,001-row capped count per request, clusters from Elasticsearch, and a bounded database fallback
 * (identical exact viewports shared for a minute, with the original computation timestamp).
 */
class MapEngineSelectionTests {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-03T00:00:00Z"));
    private final ListingReadModelPort readModel = mock(ListingReadModelPort.class);
    private final ListingSearchEnginePort engine = mock(ListingSearchEnginePort.class);
    private final SearchCircuitBreaker breaker = new SearchCircuitBreaker(clock,
            new StaticListableBeanFactory(Map.of("meterRegistry", new SimpleMeterRegistry())).getBeanProvider(MeterRegistry.class),
            20, 1, 0.5, Duration.ofSeconds(30));
    private final SearchIndexSettings settings = new SearchIndexSettings(true, "w6-map");
    private final ListingReadService service = new ListingReadService(readModel, mock(PublicImageResolver.class), new MapCache(),
            new SearchCursorCodec(new ObjectMapper(), clock, "w6-perf-map-engine-selection-test-secret-012345"), clock,
            engine, breaker, settings);

    MapEngineSelectionTests() {
        settings.markReady(true);
        when(engine.ready()).thenReturn(true);
        when(readModel.mapClusters(any(), anyDouble(), anyInt())).thenReturn(List.of(
                new MapCluster(21.03, 105.80, 7, 105.79, 21.02, 105.81, 21.04),
                new MapCluster(21.00, 105.85, 5, 105.84, 20.99, 105.86, 21.01)));
    }

    private static SearchFilter filter(String bbox) {
        SearchFilter base = SearchFilterParser.parse(Map.of("purpose", new String[] {"SALE"})).filter();
        String[] p = bbox.split(",");
        return base.withBbox(new BoundingBox(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                Double.parseDouble(p[3])));
    }

    private static List<MapPoint> points(int n) {
        List<MapPoint> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new MapPoint(UUID.randomUUID(), "s" + i, 21.0, 105.8, 1, null, "HOUSE"));
        return out;
    }

    @Test
    void zoomedInWithFewMatchesAnswersPointsFromABoundedProbeWithoutACount() {
        when(readModel.mapPoints(any(), eq(401))).thenReturn(points(12));
        ListingReadService.MapResult result = service.map(filter("105.785,21.028,105.795,21.034"), 15);
        assertThat(result.mode()).isEqualTo("points");
        assertThat(result.points()).hasSize(12);
        assertThat(result.total()).isEqualTo(new SearchResults.Total(12, "eq"));
        verify(readModel, never()).countCapped(any(), anyInt());
        verify(engine, never()).mapClusters(any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void clustersComeFromTheEngineWithItsTotal() {
        when(engine.mapClusters(any(), eq(13), eq(2000), eq(10_000))).thenReturn(new ListingSearchEnginePort.MapClusters(
                List.of(new ListingSearchEnginePort.Cluster(21.03, 105.8, 9_000, 105.7, 21.0, 105.9, 21.1)),
                new ListingSearchEnginePort.Total(10_000, "gte")));
        ListingReadService.MapResult result = service.map(filter("105.70,20.95,105.90,21.10"), 11);
        assertThat(result.mode()).isEqualTo("clusters");
        assertThat(result.engine()).isEqualTo(SearchResults.ENGINE_SEARCH);
        assertThat(result.clusters()).singleElement().satisfies(c -> assertThat(c.count()).isEqualTo(9_000));
        assertThat(result.total()).isEqualTo(new SearchResults.Total(10_000, "gte"));
        verify(readModel, never()).countCapped(any(), anyInt());
        verify(readModel, never()).mapClusters(any(), anyDouble(), anyInt());
    }

    @Test
    void aDenseZoomedInViewportFallsThroughToClusters() {
        when(readModel.mapPoints(any(), eq(401))).thenReturn(points(401));
        when(engine.mapClusters(any(), eq(16), anyInt(), anyInt())).thenReturn(new ListingSearchEnginePort.MapClusters(
                List.of(), new ListingSearchEnginePort.Total(401, "eq")));
        assertThat(service.map(filter("105.785,21.028,105.795,21.034"), 14).mode()).isEqualTo("clusters");
    }

    @Test
    void anOutageSharesOnlyTheExactViewportAndPreservesItsSnapshotTime() {
        when(engine.mapClusters(any(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new ListingSearchEnginePort.EngineUnavailable("down", null));
        SearchFilter viewport = filter("105.701,20.951,105.899,21.099");
        Instant computedAt = clock.instant();
        ListingReadService.MapResult first = service.map(viewport, 11);
        clock.advance(Duration.ofSeconds(20));
        ListingReadService.MapResult second = service.map(viewport, 11);
        assertThat(breaker.state()).isEqualTo(SearchCircuitBreaker.State.OPEN);
        assertThat(first.engine()).isEqualTo(SearchResults.ENGINE_DATABASE);
        assertThat(first.total()).isEqualTo(new SearchResults.Total(12, "eq"));
        assertThat(second.clusters()).isEqualTo(first.clusters());
        assertThat(first.dataAsOf()).isEqualTo(computedAt);
        assertThat(second.dataAsOf()).isEqualTo(computedAt).isBefore(clock.instant());
        verify(readModel, times(1)).mapClusters(eq(viewport), anyDouble(), eq(2000));
        verify(readModel, never()).countCapped(any(), anyInt());
        verify(engine, times(1)).mapClusters(any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void aPannedViewportExcludesClustersOutsideItsEdgeInsteadOfReusingAnExpandedBox() {
        when(engine.mapClusters(any(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new ListingSearchEnginePort.EngineUnavailable("down", null));
        SearchFilter wide = filter("105.701,20.951,105.899,21.099");
        SearchFilter narrow = filter("105.705,20.955,105.895,21.095");
        // The edge point at 105.703 belongs to the wide box only, although both boxes occupy the same tiles.
        when(readModel.mapClusters(any(), anyDouble(), anyInt())).thenAnswer(call -> {
            SearchFilter requested = call.getArgument(0);
            if (requested.bbox().minLng() <= 105.703) {
                return List.of(new MapCluster(21.0, 105.703, 1, 105.703, 21.0, 105.703, 21.0));
            }
            return List.of();
        });
        assertThat(service.map(wide, 11).total()).isEqualTo(new SearchResults.Total(1, "eq"));
        ListingReadService.MapResult panned = service.map(narrow, 11);
        assertThat(panned.clusters()).isEmpty();
        assertThat(panned.total()).isEqualTo(new SearchResults.Total(0, "eq"));
        verify(readModel).mapClusters(eq(wide), anyDouble(), eq(2000));
        verify(readModel).mapClusters(eq(narrow), anyDouble(), eq(2000));
    }

    @Test
    void withoutTheEngineTheDatabaseAnswersTheExactViewport() {
        SearchIndexSettings disabled = new SearchIndexSettings(false, "w6-map");
        ListingReadService exact = new ListingReadService(readModel, mock(PublicImageResolver.class), new MapCache(),
                new SearchCursorCodec(new ObjectMapper(), clock, "w6-perf-map-engine-selection-test-secret-012345"), clock,
                engine, breaker, disabled);
        SearchFilter viewport = filter("105.701,20.951,105.899,21.099");
        ListingReadService.MapResult result = exact.map(viewport, 11);
        assertThat(result.total()).isEqualTo(new SearchResults.Total(12, "eq"));
        verify(readModel).mapClusters(eq(viewport), anyDouble(), eq(2000));
        verify(engine, never()).mapClusters(any(), anyInt(), anyInt(), anyInt());
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
