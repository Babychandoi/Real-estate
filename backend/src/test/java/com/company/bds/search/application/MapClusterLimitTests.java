package com.company.bds.search.application;

import com.company.bds.media.PublicImageResolver;
import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.application.port.ListingReadModelPort.MapCluster;
import com.company.bds.search.application.port.ResponseCachePort;
import com.company.bds.search.domain.SearchFilter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * R-2 (W6): when a map view has more grid cells than the cluster limit, the clusters must still add up to every located
 * listing — coarser cells instead of dropping the smallest ones (which made the map count less than the list total).
 */
class MapClusterLimitTests {
    @Test
    void moreCellsThanTheLimitAreMergedIntoCoarserCellsInsteadOfDropped() {
        ListingReadModelPort readModel = mock(ListingReadModelPort.class);
        int zoom = 11;
        double fine = 360.0 / Math.pow(2, zoom) / 4.0;
        when(readModel.countCapped(any(), anyInt())).thenReturn(6_000L);
        // At the requested cell size 6000 listings fall into 3000 cells (more than the limit) ...
        when(readModel.mapClusters(any(), eq(fine), anyInt())).thenAnswer(call -> clusters(Math.min(call.getArgument(2), 3_000), 2));
        // ... at twice the size into 1500 cells of 4.
        when(readModel.mapClusters(any(), eq(fine * 2), anyInt())).thenAnswer(call -> clusters(1_500, 4));
        when(readModel.mapClusters(any(), anyDouble(), eq(0))).thenReturn(List.of());

        ListingReadService service = new ListingReadService(readModel, mock(PublicImageResolver.class), mock(ResponseCachePort.class),
                mock(SearchCursorCodec.class), Clock.systemUTC());
        ListingReadService.MapResult result = service.map(mock(SearchFilter.class), zoom);

        assertThat(result.mode()).isEqualTo("clusters");
        assertThat(result.clusters()).hasSize(1_500);
        assertThat(result.clusters().stream().mapToLong(MapCluster::count).sum()).isEqualTo(result.total().value());
    }

    @Test
    void cellsWithinTheLimitAreReturnedAsTheyAre() {
        ListingReadModelPort readModel = mock(ListingReadModelPort.class);
        when(readModel.countCapped(any(), anyInt())).thenReturn(60L);
        when(readModel.mapClusters(any(), anyDouble(), anyInt())).thenAnswer(call -> clusters(30, 2));
        ListingReadService service = new ListingReadService(readModel, mock(PublicImageResolver.class), mock(ResponseCachePort.class),
                mock(SearchCursorCodec.class), Clock.systemUTC());
        ListingReadService.MapResult result = service.map(mock(SearchFilter.class), 9);
        assertThat(result.clusters()).hasSize(30);
        assertThat(result.clusters().stream().mapToLong(MapCluster::count).sum()).isEqualTo(60);
    }

    private static List<MapCluster> clusters(int cells, long each) {
        List<MapCluster> out = new ArrayList<>();
        for (int i = 0; i < cells; i++) out.add(new MapCluster(21, 105, each, 105, 21, 105.01, 21.01));
        return out;
    }
}
