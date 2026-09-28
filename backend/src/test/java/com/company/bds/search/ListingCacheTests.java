package com.company.bds.search;

import com.company.bds.search.infrastructure.cache.ListingResponseCache;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.MutableClock;
import com.company.bds.testsupport.QueryCount;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Listing caches (audit §7.3, F10.1, F10.4): version-keyed detail cache invalidated by approve/hide/price change,
 * first-page cache that never serves a hidden listing, single flight under concurrency, Redis failure → bypass.
 */
@BdsIntegrationTest
class ListingCacheTests {
    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ListingResponseCache cache;
    SearchFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new SearchFixtures(jdbc, data);
    }

    private JsonNode detail(String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/v2/listings/" + id)).andReturn().getResponse().getContentAsString());
    }

    @Test
    void detailCacheFollowsApprovalsPriceChangesAndHiding() throws Exception {
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestListing listing = data.listing(seller.id()).title("Căn hộ có bộ nhớ đệm").price(3_000_000_000L).create();
        assertThat(detail(listing.id().toString()).path("price").path("amount").asLong()).isEqualTo(3_000_000_000L);
        long cachedQueries = QueryCount.count(() -> mvc.perform(get("/api/v2/listings/" + listing.id())).andReturn());
        assertThat(cachedQueries).as("a cache hit only reads the row version").isEqualTo(1);

        // a new approved revision with another price becomes public (what moderation does)
        UUID revision = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO listing_revisions(id,listing_id,revision_number,status,title,purpose,property_type,price_vnd,area_m2,
                    description,province_code,district_code,address_summary,created_at,submitted_at,moderated_at)
                VALUES (?,?,2,'APPROVED','Căn hộ có bộ nhớ đệm','SALE','APARTMENT',2800000000,70,'Mô tả',
                    '01','005','Cầu Giấy, Hà Nội',now(),now(),now())
                """, revision, listing.id());
        jdbc.update("UPDATE listings SET public_revision_id = ?, updated_at = now() WHERE id = ?", revision, listing.id());
        JsonNode approved = detail(listing.id().toString());
        assertThat(approved.path("price").path("amount").asLong()).isEqualTo(2_800_000_000L);
        assertThat(approved.path("priceChange").path("direction").asText()).isEqualTo("DOWN");
        assertThat(approved.path("revisionNumber").asInt()).isEqualTo(2);

        jdbc.update("UPDATE listings SET status = 'PAUSED' WHERE id = ?", listing.id());
        assertThat(mvc.perform(get("/api/v2/listings/" + listing.id())).andReturn().getResponse().getStatus()).isEqualTo(410);
    }

    @Test
    void firstPageCacheServesFreshRowsAndNeverAHiddenListing() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        List<TestData.TestListing> listings = new ArrayList<>();
        for (int i = 0; i < 30; i++) listings.add(data.listing(seller.id()).title("Nhà " + token + " " + i).create());
        long miss = QueryCount.count(() -> mvc.perform(get("/api/v2/listings/search").param("q", token)).andReturn());
        long hit = QueryCount.count(() -> mvc.perform(get("/api/v2/listings/search").param("q", token)).andReturn());
        assertThat(miss).as("page + total").isEqualTo(2);
        assertThat(hit).as("cached ids re-read from PostgreSQL in one query").isEqualTo(1);

        TestData.TestListing newest = listings.get(listings.size() - 1);
        fixtures.revise(newest, "price_vnd = ?", 1_234_000_000L);
        jdbc.update("UPDATE listings SET status = 'LOCKED' WHERE id = ?", listings.get(listings.size() - 2).id());
        JsonNode page = json.readTree(mvc.perform(get("/api/v2/listings/search").param("q", token)).andReturn()
                .getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        page.path("items").forEach(item -> {
            ids.add(item.path("id").asText());
            if (item.path("id").asText().equals(newest.id().toString())) {
                assertThat(item.path("price").path("amount").asLong()).isEqualTo(1_234_000_000L);
            }
        });
        assertThat(ids).contains(newest.id().toString()).doesNotContain(listings.get(listings.size() - 2).id().toString());
    }

    @Test
    void concurrentMissesComputeOnce() throws Exception {
        AtomicInteger computations = new AtomicInteger();
        String key = "s2-test:" + UUID.randomUUID();
        assertThat(runConcurrently(cache, key, computations)).allMatch("value"::equals);
        assertThat(computations.get()).isEqualTo(1);
    }

    @Test
    void redisOutageBypassesTheCacheAndStillCollapsesConcurrentWork() throws Exception {
        LettuceConnectionFactory broken = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300)).build());
        broken.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(broken);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MutableClock clock = new MutableClock(Instant.now());
        ListingResponseCache down = new ListingResponseCache(
                new StaticListableBeanFactory(Map.of("redis", template)).getBeanProvider(StringRedisTemplate.class), json, clock,
                new StaticListableBeanFactory(Map.of("meters", registry)).getBeanProvider(MeterRegistry.class),
                true, "s2-test:", Duration.ofSeconds(10));
        try {
            assertThat(down.getOrCompute("probe", "first", Duration.ofSeconds(5), String.class, () -> "computed")).isEqualTo("computed");
            assertThat(down.available()).as("Redis error opens a bypass window").isFalse();
            assertThat(down.generation()).isEqualTo(-1);
            AtomicInteger computations = new AtomicInteger();
            long started = System.nanoTime();
            assertThat(runConcurrently(down, "k", computations)).allMatch("value"::equals);
            assertThat((System.nanoTime() - started) / 1_000_000).as("no Redis timeouts while bypassed").isLessThan(1_500);
            assertThat(computations.get()).as("single flight bounds the database load").isEqualTo(1);
            assertThat(registry.get("bds.search.cache").tag("result", "bypass").counter().count()).isGreaterThanOrEqualTo(2);
            clock.advance(Duration.ofSeconds(11));
            assertThat(down.available()).as("retried after the window").isTrue();
        } finally {
            broken.destroy();
        }
    }

    private static List<String> runConcurrently(ListingResponseCache target, String key, AtomicInteger computations) throws Exception {
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return target.getOrCompute("probe", key, Duration.ofSeconds(30), String.class, () -> {
                    computations.incrementAndGet();
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    return "value";
                });
            }));
        }
        start.countDown();
        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) results.add(future.get(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        return results;
    }
}
