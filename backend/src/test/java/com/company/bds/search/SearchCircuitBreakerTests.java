package com.company.bds.search;

import com.company.bds.search.application.SearchCircuitBreaker;
import com.company.bds.search.application.SearchCircuitBreaker.State;
import com.company.bds.testsupport.MutableClock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** F06.3: failure-rate breaker with half-open probes; query-validation errors never trip it. */
class SearchCircuitBreakerTests {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-28T00:00:00Z"));
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SearchCircuitBreaker breaker = new SearchCircuitBreaker(clock,
            new StaticListableBeanFactory(Map.of("meterRegistry", registry)).getBeanProvider(MeterRegistry.class),
            10, 4, 0.5, Duration.ofSeconds(30));

    @Test
    void opensOnFailureRateThenProbesOnceAndCloses() {
        breaker.recordSuccess();
        breaker.recordSuccess();
        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(State.CLOSED);
        breaker.recordFailure();
        assertThat(breaker.state()).as("2 of 4 calls failed").isEqualTo(State.OPEN);
        assertThat(breaker.tryAcquire()).isFalse();
        assertThat(registry.get("bds.search.breaker.state").gauge().value()).isEqualTo(1.0);

        clock.advance(Duration.ofSeconds(31));
        assertThat(breaker.tryAcquire()).as("one half-open probe").isTrue();
        assertThat(breaker.tryAcquire()).as("no second concurrent probe").isFalse();
        breaker.recordSuccess();
        assertThat(breaker.state()).isEqualTo(State.CLOSED);
        assertThat(breaker.tryAcquire()).isTrue();
    }

    @Test
    void failedProbeReopens() {
        for (int i = 0; i < 4; i++) breaker.recordFailure();
        clock.advance(Duration.ofSeconds(31));
        assertThat(breaker.tryAcquire()).isTrue();
        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(State.OPEN);
        assertThat(breaker.tryAcquire()).isFalse();
    }

    @Test
    void callableIsFalseWhileOpenAndWhileTheHalfOpenProbeIsTaken() {
        assertThat(breaker.callable()).isTrue();
        for (int i = 0; i < 4; i++) breaker.recordFailure();
        assertThat(breaker.callable()).isFalse();
        clock.advance(Duration.ofSeconds(31));
        assertThat(breaker.callable()).as("half-open, probe free").isTrue();
        assertThat(breaker.tryAcquire()).isTrue();
        assertThat(breaker.callable()).as("probe in flight").isFalse();
        breaker.recordSuccess();
        assertThat(breaker.callable()).isTrue();
    }

    @Test
    void rejectedQueriesDoNotCount() {
        for (int i = 0; i < 20; i++) breaker.recordIgnored();
        breaker.recordSuccess();
        assertThat(breaker.state()).isEqualTo(State.CLOSED);
    }
}
