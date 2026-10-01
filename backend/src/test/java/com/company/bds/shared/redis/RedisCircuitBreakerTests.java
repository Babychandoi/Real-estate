package com.company.bds.shared.redis;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.bds.testsupport.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** States, single probe, metrics and log rate of the request-path Redis breaker. */
class RedisCircuitBreakerTests {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RedisCircuitBreaker breaker = new RedisCircuitBreaker(clock, meters, Duration.ofSeconds(5));
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RedisCircuitBreaker.class);

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    private double counted(String caller, String outcome) {
        var counter = meters.find("bds.redis.unavailable").tags("caller", caller, "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void aFailureOpensItThenOneProbePerPeriodDecides() {
        assertThat(breaker.tryAcquire("rate-limit")).isTrue();
        breaker.recordFailure("rate-limit", new RedisConnectionFailureException("refused"));

        assertThat(breaker.state()).isEqualTo(RedisCircuitBreaker.State.OPEN);
        assertThat(breaker.available()).isFalse();
        assertThat(breaker.tryAcquire("search-cache")).as("open: callers skip Redis").isFalse();
        assertThat(breaker.tryAcquire("rate-limit")).isFalse();
        assertThat(counted("rate-limit", "failed")).isEqualTo(1);
        assertThat(counted("rate-limit", "skipped")).isEqualTo(1);
        assertThat(counted("search-cache", "skipped")).isEqualTo(1);
        assertThat(meters.get("bds.redis.breaker.state").gauge().value()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(5));
        assertThat(breaker.state()).isEqualTo(RedisCircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.tryAcquire("search-cache")).as("the probe").isTrue();
        assertThat(breaker.tryAcquire("rate-limit")).as("only one probe at a time").isFalse();
        breaker.recordFailure("search-cache", new QueryTimeoutException("timeout"));
        assertThat(breaker.state()).as("failed probe: open for another period").isEqualTo(RedisCircuitBreaker.State.OPEN);
        clock.advance(Duration.ofSeconds(4));
        assertThat(breaker.tryAcquire("rate-limit")).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(breaker.tryAcquire("rate-limit")).isTrue();
        breaker.recordSuccess();
        assertThat(breaker.state()).isEqualTo(RedisCircuitBreaker.State.CLOSED);
        assertThat(breaker.tryAcquire("search-cache")).isTrue();
        assertThat(breaker.tryAcquire("rate-limit")).isTrue();
        assertThat(meters.get("bds.redis.breaker.state").gauge().value()).isZero();
    }

    @Test
    void aCallThatWasAlreadyRunningWhenRedisFailedDoesNotCloseIt() {
        assertThat(breaker.tryAcquire("rate-limit")).isTrue();
        assertThat(breaker.tryAcquire("search-cache")).isTrue();
        breaker.recordFailure("rate-limit", new QueryTimeoutException("timeout"));
        breaker.recordSuccess(); // the search-cache call answered late

        assertThat(breaker.state()).isEqualTo(RedisCircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire("rate-limit")).as("Redis stays skipped until the probe").isFalse();
    }

    @Test
    void aProbeThatNeverReportsIsReplacedAfterOnePeriod() {
        breaker.recordFailure("geocoding", new RedisConnectionFailureException("refused"));
        clock.advance(Duration.ofSeconds(5));
        assertThat(breaker.tryAcquire("geocoding")).isTrue();
        clock.advance(Duration.ofSeconds(4));
        assertThat(breaker.tryAcquire("geocoding")).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(breaker.tryAcquire("geocoding")).as("lost probe replaced").isTrue();
    }

    @Test
    void concurrentCallersGetExactlyOneProbe() throws Exception {
        breaker.recordFailure("rate-limit", new RedisConnectionFailureException("refused"));
        clock.advance(Duration.ofSeconds(6));
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return breaker.tryAcquire("rate-limit");
            }));
        }
        start.countDown();
        int granted = 0;
        for (Future<Boolean> result : results) if (result.get(5, TimeUnit.SECONDS)) granted++;
        pool.shutdownNow();
        assertThat(granted).isEqualTo(1);
    }

    @Test
    void oneWarningPerOutageAndAtMostOnePerMinuteWhenRedisFlaps() {
        for (int outage = 0; outage < 3; outage++) {
            for (int i = 0; i < 50; i++) breaker.recordFailure("rate-limit", new RedisConnectionFailureException("refused"));
            clock.advance(Duration.ofSeconds(6));
            assertThat(breaker.tryAcquire("rate-limit")).isTrue();
            breaker.recordSuccess();
            clock.advance(Duration.ofSeconds(10));
        }
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN).hasSize(1);
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN).first()
                .satisfies(e -> assertThat(e.getFormattedMessage()).startsWith("redis_unavailable caller=rate-limit error=RedisConnectionFailureException"));
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.INFO).hasSize(1)
                .first().satisfies(e -> assertThat(e.getFormattedMessage()).isEqualTo("redis_recovered downForMs=6000"));

        clock.advance(Duration.ofMinutes(1));
        breaker.recordFailure("search-cache", new QueryTimeoutException("timeout"));
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN).hasSize(2);
    }
}
