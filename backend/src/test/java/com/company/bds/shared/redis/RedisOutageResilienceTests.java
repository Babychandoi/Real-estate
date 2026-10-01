package com.company.bds.shared.redis;

import com.company.bds.testsupport.BdsIntegrationTestInitializer;
import com.company.bds.testsupport.BdsTestConfiguration;
import com.company.bds.testsupport.MutableClock;
import com.company.bds.testsupport.RedisFaultProxy;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Redis outage on the request path, with the production client settings (command timeout, commands rejected while
 * disconnected) against a Redis the test can take away ({@link RedisFaultProxy} in front of the shared test Redis):
 * public search reads and draft writes stay fast while Redis is stopped, hung or coming back; only a breaker probe may
 * pay one short timeout; the breaker closes again on its own; the rate limiter keeps each policy's documented outage
 * behaviour (FAIL_CLOSED credential endpoints still limited, EVICT endpoints counted locally) and returns to Redis.
 * Time of the breaker is a {@link MutableClock}, so probes happen exactly when the test advances it.
 */
@SpringBootTest(properties = {
        "app.security.rate-limit.limit-multiplier=1",
        "app.redis.breaker.open-for=PT5S",
        "app.redis.reconnect-max-delay=PT0.2S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = BdsIntegrationTestInitializer.class)
@Import({BdsTestConfiguration.class, RedisOutageResilienceTests.Clocks.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RedisOutageResilienceTests {
    /** Generous for a loaded CI machine, far below the 2 s that every Redis touch cost before. */
    private static final long REQUEST_BOUND_MS = 1_000;
    private static final String NGINX = "172.18.0.2";
    private static final RedisFaultProxy PROXY = RedisFaultProxy.toSharedTestRedis();

    @TestConfiguration(proxyBeanMethods = false)
    static class Clocks {
        @Bean
        @Primary
        MutableClock testClock() { return MutableClock.startingNow(); }
    }

    @DynamicPropertySource
    static void redisBehindTheProxy(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", PROXY::port);
    }

    @AfterAll
    static void closeProxy() {
        PROXY.close();
    }

    @Autowired MockMvc mvc;
    @Autowired MeterRegistry meters;
    @Autowired MutableClock clock;
    @Autowired RedisCircuitBreaker breaker;
    @Autowired TestData data;
    @Autowired ObjectMapper json;

    private static String token;

    private MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String clientIp) {
        return request.header("X-Real-IP", clientIp).with(r -> { r.setRemoteAddr(NGINX); return r; });
    }

    private long search(String clientIp) throws Exception {
        long started = System.nanoTime();
        MvcResult result = mvc.perform(from(get("/api/v2/listings/search").param("purpose", "SALE").param("size", "24"), clientIp)).andReturn();
        long ms = (System.nanoTime() - started) / 1_000_000;
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return ms;
    }

    private long createDraft(String clientIp) throws Exception {
        if (token == null) token = data.sessionFor(data.user().role("BROKER").verifiedKyc().plan("PRO", 1_000).create().id());
        String body = json.writeValueAsString(java.util.Map.of("title", "Tin nháp kiểm thử Redis " + UUID.randomUUID(),
                "purpose", "SALE", "propertyType", "APARTMENT", "priceVnd", 2_500_000_000L, "areaM2", 60,
                "description", "Kiểm thử Redis mất kết nối", "imageUrls", List.of()));
        long started = System.nanoTime();
        MvcResult result = mvc.perform(from(post("/api/v1/listings"), clientIp).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        long ms = (System.nanoTime() - started) / 1_000_000;
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return ms;
    }

    /** Latencies of a mixed burst: {@code reads} searches and one draft per ten reads. */
    private List<Long> burst(int reads, String clientIp) throws Exception {
        List<Long> latencies = new ArrayList<>();
        for (int i = 0; i < reads; i++) {
            latencies.add(search(clientIp));
            if (i % 10 == 9) latencies.add(createDraft(clientIp));
        }
        return latencies;
    }

    private double count(String name, String... tags) {
        var counter = meters.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private double failedRedisCalls() {
        return meters.find("bds.redis.unavailable").tag("outcome", "failed").counters().stream().mapToDouble(c -> c.count()).sum();
    }

    private double gauge(String name) {
        return meters.get(name).gauge().value();
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    @Test
    @Order(1)
    void withRedisUpTheLimiterAndTheCacheUseRedis() throws Exception {
        burst(10, "203.0.113.10");
        assertThat(breaker.state()).isEqualTo(RedisCircuitBreaker.State.CLOSED);
        assertThat(gauge("bds.ratelimit.redis.available")).isEqualTo(1);
        assertThat(count("bds.ratelimit.fallback", "policy", "search-v2")).isZero();
        assertThat(count("bds.search.cache", "cache", "search-first-page", "result", "hit")).isPositive();
        assertThat(failedRedisCalls()).isZero();
    }

    @Test
    @Order(2)
    void redisStoppedRequestsStayFastAndRedisIsNotCalledAgainUntilTheProbe() throws Exception {
        PROXY.stop();
        double searchFallbackBefore = count("bds.ratelimit.fallback", "policy", "search-v2");

        List<Long> latencies = burst(40, "203.0.113.11");

        assertThat(latencies).as("no request waits for Redis").allMatch(ms -> ms < REQUEST_BOUND_MS);
        assertThat(median(latencies)).isLessThan(250);
        assertThat(failedRedisCalls()).as("the first failed call opens the breaker; the rest skip Redis").isBetween(1.0, 3.0);
        assertThat(count("bds.redis.unavailable", "caller", "rate-limit", "outcome", "skipped")).isPositive();
        assertThat(count("bds.redis.unavailable", "caller", "search-cache", "outcome", "skipped")).isPositive();
        assertThat(breaker.state()).isEqualTo(RedisCircuitBreaker.State.OPEN);
        assertThat(gauge("bds.redis.breaker.state")).isEqualTo(1);
        assertThat(gauge("bds.ratelimit.redis.available")).isZero();
        assertThat(count("bds.ratelimit.fallback", "policy", "search-v2") - searchFallbackBefore)
                .as("EVICT policy: every read counted in the local table and served").isEqualTo(40);
    }

    @Test
    @Order(3)
    void redisStoppedCredentialEndpointsAreStillLimitedLocally() throws Exception {
        String client = "203.0.113.12";
        for (int i = 0; i < 20; i++) {
            int status = mvc.perform(from(get("/api/v1/auth/verify-email").param("token", "not-a-token-" + UUID.randomUUID()), client))
                    .andReturn().getResponse().getStatus();
            assertThat(status).isNotEqualTo(429);
        }
        MvcResult rejected = mvc.perform(from(get("/api/v1/auth/verify-email").param("token", "x-" + UUID.randomUUID()), client)).andReturn();

        assertThat(rejected.getResponse().getStatus()).as("FAIL_CLOSED policy keeps its limit while Redis is down").isEqualTo(429);
        assertThat(rejected.getResponse().getHeader("Retry-After")).isEqualTo("900");
        assertThat(count("bds.ratelimit.fallback", "policy", "auth-verify-email")).isEqualTo(21);
    }

    @Test
    @Order(4)
    void probesWhileRedisIsStillDownCostOneFastFailureAndDoNotStallOthers() throws Exception {
        double failedBefore = failedRedisCalls();
        clock.advance(Duration.ofSeconds(6));
        assertThat(breaker.state()).isEqualTo(RedisCircuitBreaker.State.HALF_OPEN);

        List<Long> latencies = burst(10, "203.0.113.13");

        assertThat(latencies).allMatch(ms -> ms < REQUEST_BOUND_MS);
        assertThat(failedRedisCalls() - failedBefore).as("one probe per period").isEqualTo(1);
        assertThat(breaker.state()).isEqualTo(RedisCircuitBreaker.State.OPEN);
    }

    @Test
    @Order(5)
    void whenRedisIsBackTheBreakerClosesWithoutARestartAndWithoutStallingRequests() throws Exception {
        PROXY.forward();
        List<Long> latencies = new ArrayList<>();
        // Lettuce reconnects in the background (back-off capped at 0.2 s here); each period one request probes.
        for (int attempt = 0; attempt < 50 && breaker.state() != RedisCircuitBreaker.State.CLOSED; attempt++) {
            clock.advance(Duration.ofSeconds(6));
            latencies.add(search("203.0.113.14"));
            if (breaker.state() != RedisCircuitBreaker.State.CLOSED) Thread.sleep(100);
        }
        assertThat(breaker.state()).isEqualTo(RedisCircuitBreaker.State.CLOSED);
        assertThat(latencies).allMatch(ms -> ms < REQUEST_BOUND_MS);
        assertThat(gauge("bds.ratelimit.redis.available")).isEqualTo(1);

        double localBefore = count("bds.ratelimit.fallback", "policy", "search-v2");
        List<Long> after = burst(10, "203.0.113.15");
        assertThat(after).allMatch(ms -> ms < REQUEST_BOUND_MS);
        assertThat(count("bds.ratelimit.fallback", "policy", "search-v2")).as("counted in Redis again").isEqualTo(localBefore);
    }

    @Test
    @Order(6)
    void redisHangingCostsOneCommandTimeoutThenRequestsSkipIt() throws Exception {
        double failedBefore = failedRedisCalls();
        PROXY.hang();

        long first = search("203.0.113.16");
        List<Long> rest = burst(20, "203.0.113.16");

        assertThat(first).as("one command timeout (250 ms), not one per Redis touch").isLessThan(REQUEST_BOUND_MS);
        assertThat(rest).allMatch(ms -> ms < REQUEST_BOUND_MS);
        assertThat(median(rest)).isLessThan(250);
        assertThat(failedRedisCalls() - failedBefore).isEqualTo(1);

        clock.advance(Duration.ofSeconds(6));
        long probe = search("203.0.113.17");
        long next = search("203.0.113.17");
        assertThat(probe).as("the probe pays at most one command timeout").isLessThan(REQUEST_BOUND_MS);
        assertThat(next).isLessThan(250);
        assertThat(failedRedisCalls() - failedBefore).isEqualTo(2);

        PROXY.forward();
        for (int attempt = 0; attempt < 50 && breaker.state() != RedisCircuitBreaker.State.CLOSED; attempt++) {
            clock.advance(Duration.ofSeconds(6));
            assertThat(search("203.0.113.18")).isLessThan(REQUEST_BOUND_MS);
            if (breaker.state() != RedisCircuitBreaker.State.CLOSED) Thread.sleep(100);
        }
        assertThat(breaker.state()).isEqualTo(RedisCircuitBreaker.State.CLOSED);
        assertThat(burst(10, "203.0.113.19")).allMatch(ms -> ms < REQUEST_BOUND_MS);
    }
}
