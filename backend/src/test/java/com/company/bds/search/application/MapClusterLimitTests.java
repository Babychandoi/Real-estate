package com.company.bds.search.application;

import com.company.bds.media.PublicImageResolver;
import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.application.port.ResponseCachePort;
import com.company.bds.search.domain.BoundingBox;
import com.company.bds.search.domain.SearchFilter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R-2 (W6): the map never drops cells to stay under the cluster limit (that made the clusters add up to less than the
 * list total). The cell size is computed up front from the bbox and zoom so the grid has at most 2 000 cells, and the
 * clusters are aggregated with ONE query (W6 review: no re-aggregation per doubling).
 */
class MapClusterLimitTests {
    @Test
    void theCellIsChosenSoThatTheGridOverTheBboxHasAtMostTheLimit() {
        BoundingBox wide = new BoundingBox(105.0, 20.0, 108.0, 23.0); // 3° × 3°, the largest allowed
        for (int zoom = 3; zoom <= 20; zoom++) {
            double cell = ListingReadService.clusterCell(wide, zoom);
            assertThat(ListingReadService.cells(wide, cell)).as("zoom " + zoom).isLessThanOrEqualTo(ListingReadService.MAP_CLUSTER_LIMIT);
            double natural = 360.0 / Math.pow(2, zoom) / 4.0;
            if (ListingReadService.cells(wide, natural) <= ListingReadService.MAP_CLUSTER_LIMIT) {
                assertThat(cell).as("no coarsening needed at zoom " + zoom).isEqualTo(natural);
            } else {
                assertThat(ListingReadService.cells(wide, cell / 2)).as("the finest grid that fits, zoom " + zoom)
                        .isGreaterThan(ListingReadService.MAP_CLUSTER_LIMIT);
            }
        }
        // 3° at zoom 11 (cell ≈ 0.044°) would be ~4 700 cells: coarsened once.
        assertThat(ListingReadService.clusterCell(wide, 11)).isEqualTo(360.0 / Math.pow(2, 11) / 4.0 * 2);
    }

    @Test
    void clustersAreAggregatedWithOneQueryAtTheComputedCell() {
        ListingReadModelPort readModel = mock(ListingReadModelPort.class);
        when(readModel.countCapped(any(), anyInt())).thenReturn(6_000L);
        when(readModel.mapClusters(any(), anyDouble(), anyInt())).thenReturn(List.of());
        BoundingBox wide = new BoundingBox(105.0, 20.0, 108.0, 23.0);
        SearchFilter filter = mock(SearchFilter.class);
        when(filter.bbox()).thenReturn(wide);
        ListingReadService service = new ListingReadService(readModel, mock(PublicImageResolver.class), mock(ResponseCachePort.class),
                mock(SearchCursorCodec.class), Clock.systemUTC(), mock(com.company.bds.search.application.port.ListingSearchEnginePort.class),
                mock(SearchCircuitBreaker.class), new SearchIndexSettings(false, "w6-map-limit-test"));

        assertThat(service.map(filter, 11).mode()).isEqualTo("clusters");
        verify(readModel, times(1)).mapClusters(any(), anyDouble(), anyInt());
        verify(readModel).mapClusters(eq(filter), eq(ListingReadService.clusterCell(wide, 11)), eq(ListingReadService.MAP_CLUSTER_LIMIT));
    }
}
