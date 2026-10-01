package com.company.bds.search;

import com.company.bds.search.api.GeocodingController;
import com.company.bds.shared.redis.RedisCircuitBreaker;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F10.3: geocode answers are cached under a normalised key, expire after 14 days, and empty answers are cached for one
 * hour only (negative cache). A local stub stands in for the provider and counts the calls it receives.
 */
@BdsIntegrationTest
class GeocodeCacheTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    HttpServer provider;
    final AtomicInteger calls = new AtomicInteger();
    final MutableClock clock = new MutableClock(Instant.now());
    GeocodingController controller;

    @BeforeEach
    void start() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/search", exchange -> {
            calls.incrementAndGet();
            String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            String body = query.contains("Không") ? "[]" : "[{\"display_name\":\"Cầu Giấy, Hà Nội\",\"lat\":\"21.03\",\"lon\":\"105.79\"}]";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        provider.start();
        String url = "http://127.0.0.1:" + provider.getAddress().getPort() + "/s2-" + UUID.randomUUID();
        controller = new GeocodingController(jdbc, new StaticListableBeanFactory().getBeanProvider(StringRedisTemplate.class),
                new RedisCircuitBreaker(clock, new SimpleMeterRegistry(), Duration.ofSeconds(5)), json, clock, url.substring(0, url.lastIndexOf('/')) , "", Duration.ofDays(14), Duration.ofHours(1));
    }

    @AfterEach
    void stop() {
        provider.stop(0);
    }

    /** The provider slot allows one call per second per process; wait it out between real provider calls. */
    private static void waitProviderSlot() throws InterruptedException {
        Thread.sleep(1_100);
    }

    @Test
    void positiveAnswersExpireAfterFourteenDaysUnderANormalisedKey() throws Exception {
        String place = "Cầu Giấy " + UUID.randomUUID().toString().substring(0, 6);
        assertThat(controller.search(place)).contains("21.03");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(controller.search("  " + place.toUpperCase().replace(" ", "   ") + ", ")).contains("21.03");
        assertThat(calls.get()).as("same normalised key").isEqualTo(1);
        clock.advance(Duration.ofDays(13));
        controller.search(place);
        assertThat(calls.get()).as("still fresh after 13 days").isEqualTo(1);
        clock.advance(Duration.ofDays(2));
        waitProviderSlot();
        controller.search(place);
        assertThat(calls.get()).as("expired after 14 days").isEqualTo(2);
    }

    @Test
    void emptyAnswersAreCachedForOneHourOnly() throws Exception {
        waitProviderSlot();
        String place = "Không có nơi này " + UUID.randomUUID().toString().substring(0, 6);
        assertThat(controller.search(place)).isEqualTo("[]");
        controller.search(place);
        assertThat(calls.get()).isEqualTo(1);
        Boolean negative = jdbc.queryForObject("SELECT negative FROM geocode_cache WHERE normalized_query = ?", Boolean.class,
                GeocodingController.normalizedKey(place));
        assertThat(negative).isTrue();
        clock.advance(Duration.ofMinutes(61));
        waitProviderSlot();
        controller.search(place);
        assertThat(calls.get()).isEqualTo(2);
    }
}
