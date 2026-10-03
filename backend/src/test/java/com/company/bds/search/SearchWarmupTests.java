package com.company.bds.search;

import com.company.bds.search.application.SearchIndexSettings;
import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.application.port.ListingSearchEnginePort;
import com.company.bds.search.infrastructure.warmup.SearchWarmup;
import com.company.bds.testsupport.BdsIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W6-PERF O5: the start-up warm-up holds readiness and health until PostgreSQL (pg_prewarm, V101) and the engine are
 * warm, never touches the circuit breaker, and is a no-op that reports UP when disabled.
 */
@BdsIntegrationTest
class SearchWarmupTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired SearchWarmup configured;

    private final List<Object> events = new ArrayList<>();
    private final ApplicationEventPublisher publisher = events::add;

    @Test
    void disabledByDefaultAndThenAlwaysUp() {
        assertThat(configured.health().getStatus()).isEqualTo(Status.UP);
        assertThat(configured.done()).isTrue();
    }

    @Test
    void prewarmsTheReadModelAndWarmsTheEngineBeforeReportingReady() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'pg_prewarm'", Integer.class))
                .as("V101 created the extension").isEqualTo(1);
        ListingSearchEnginePort engine = mock(ListingSearchEnginePort.class);
        when(engine.ready()).thenReturn(true);
        when(engine.search(any(), any(), any(), anyInt(), anyBoolean(), anyInt()))
                .thenThrow(new ListingSearchEnginePort.EngineUnavailable("cold", null)) // first answer too slow
                .thenReturn(new ListingSearchEnginePort.Hits(List.of(), null));
        when(engine.mapClusters(any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new ListingSearchEnginePort.MapClusters(List.of(), new ListingSearchEnginePort.Total(0, "eq")));
        SearchIndexSettings settings = new SearchIndexSettings(true, "w6-warmup");
        SearchWarmup warmup = new SearchWarmup(true, Duration.ofSeconds(20), 12, jdbc, engine, mock(ListingReadModelPort.class),
                settings, publisher, Clock.systemUTC());

        assertThat(warmup.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        warmup.onReady();
        for (int i = 0; i < 200 && !warmup.done(); i++) Thread.sleep(50);

        assertThat(warmup.done()).isTrue();
        assertThat(warmup.health().getStatus()).isEqualTo(Status.UP);
        String result = (String) warmup.health().getDetails().get("result");
        assertThat(result).contains("blocks").contains("rounds ok").contains("1 failed");
        List<Object> states = events.stream().filter(AvailabilityChangeEvent.class::isInstance)
                .map(e -> (Object) ((AvailabilityChangeEvent<?>) e).getState()).toList();
        assertThat(states).containsExactly(ReadinessState.REFUSING_TRAFFIC, ReadinessState.ACCEPTING_TRAFFIC);
        verify(engine, atLeast(10)).mapClusters(any(), anyInt(), anyInt(), anyInt());
    }
}
