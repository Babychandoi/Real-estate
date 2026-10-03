package com.company.bds.search;

import com.company.bds.search.application.SearchCircuitBreaker;
import com.company.bds.search.application.SearchIndexSettings;
import com.company.bds.search.infrastructure.elasticsearch.ElasticsearchIndexClient;
import com.company.bds.search.infrastructure.indexing.SearchIndexLifecycle;
import com.company.bds.shared.jobs.JobWorker;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.BdsIntegrationTestInitializer;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * R-2 acceptance at the API (W6): 300 public listings (sale and rent, four districts, some without a location) paged
 * end to end on both engines — Elasticsearch and PostgreSQL — with filters; map totals and clusters agree with the list;
 * seller pages (v2 and v1) and the v1 search reach every listing (no 60/100 cap); every rent price carries its period.
 */
@BdsIntegrationTest(properties = {BdsIntegrationTestInitializer.ELASTICSEARCH_OPT_IN + "=true", "app.search.bootstrap-on-startup=false",
        "app.search.cache.first-page=false",
        // parity is about results, not latency: a loaded CI machine must not trip the 800 ms budget mid-walk
        "app.search.timeout=PT10S"})
class SearchConsistencyAcceptanceTests {
    private static final int LISTINGS = 300;
    private static final String[][] DISTRICTS = {{"005", "Dịch Vọng, Cầu Giấy, Hà Nội"}, {"007", "Bạch Mai, Hai Bà Trưng, Hà Nội"},
            {"019", "Mễ Trì, Nam Từ Liêm, Hà Nội"}, {"006", "Láng Hạ, Đống Đa, Hà Nội"}};
    // Far from the Hà Nội / HCMC boxes other tests assert on (the database is shared by the whole run).
    private static final String BBOX = "109.05,12.15,109.35,12.45";

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JobWorker worker;
    @Autowired SearchIndexLifecycle lifecycle;
    @Autowired SearchIndexSettings settings;
    @Autowired SearchCircuitBreaker breaker;
    @Autowired ElasticsearchIndexClient client;
    SearchFixtures fixtures;

    /** What the test created, to compute every expected answer independently of the API. */
    record Seeded(String id, String purpose, String district, long price, Double lat, Double lng) {}

    @BeforeEach
    void setUp() {
        fixtures = new SearchFixtures(jdbc, data);
        breaker.reset();
        assertThat(lifecycle.bootstrap()).isTrue();
        settings.markReady(true);
    }

    @AfterEach
    void tearDown() {
        settings.markReady(true);
        breaker.reset();
    }

    @Test
    void moreThan250ListingsPageConsistentlyOnBothEnginesWithFiltersMapAndRentUnits() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("OWNER");
        Instant base = Instant.now().minus(Duration.ofDays(4));
        List<Seeded> seeded = new ArrayList<>();
        for (int i = 0; i < LISTINGS; i++) {
            String[] place = DISTRICTS[i % DISTRICTS.length];
            boolean rent = i % 3 == 0;
            boolean located = i % 10 != 7;
            Double lat = located ? 12.20 + (i % 17) * 0.012 : null;
            Double lng = located ? 109.10 + (i % 19) * 0.011 : null;
            long price = rent ? 5_000_000L + (i % 11) * 1_000_000L : 1_500_000_000L + (i % 13) * 100_000_000L;
            TestData.ListingBuilder builder = data.listing(seller.id()).purpose(rent ? "RENT" : "SALE")
                    .title((rent ? "Cho thuê căn hộ " : "Bán nhà ") + token + " số " + i).district(place[0], place[1])
                    .price(price).area(Integer.toString(40 + i % 30)).createdAt(base.plusSeconds(i / 3));
            if (located) builder.location(lat, lng);
            seeded.add(new Seeded(builder.create().id().toString(), rent ? "RENT" : "SALE", place[0], price, lat, lng));
        }
        fixtures.refreshOwner(seller.id());
        worker.drain("search-index");
        client.refresh(settings.alias());

        List<Map<String, String>> filters = List.of(
                query("q", token, "purpose", "SALE"),
                query("q", token, "purpose", "RENT"),
                query("q", token, "purpose", "SALE", "district", "005,019", "priceMax", "2300000000"),
                query("q", token, "purpose", "RENT", "priceMin", "8000000", "sort", "PRICE_ASC"),
                query("q", token, "purpose", "SALE", "bbox", BBOX, "sort", "PRICE_DESC"));
        for (Map<String, String> filter : filters) {
            Set<String> expected = expected(seeded, filter);
            Map<String, List<String>> byEngine = new LinkedHashMap<>();
            for (String engine : List.of("search", "database")) {
                settings.markReady(engine.equals("search"));
                try {
                    byEngine.put(engine, walk(filter, engine, expected.size()));
                } finally {
                    settings.markReady(true);
                }
            }
            for (Map.Entry<String, List<String>> result : byEngine.entrySet()) {
                assertThat(new HashSet<>(result.getValue())).as(result.getKey() + " has no duplicates for " + filter).hasSameSizeAs(result.getValue());
                assertThat(result.getValue()).as(result.getKey() + " returns exactly the matches of " + filter)
                        .containsExactlyInAnyOrderElementsOf(expected);
            }
            if (filter.containsKey("sort")) {
                assertThat(byEngine.get("search")).as("same order on both engines for " + filter).containsExactlyElementsOf(byEngine.get("database"));
            }
        }
        assertThat(expected(seeded, filters.get(0))).hasSizeGreaterThan(150);
        assertThat(expected(seeded, filters.get(0)).size() + expected(seeded, filters.get(1)).size()).isEqualTo(LISTINGS);

        // Map: the total equals the list total for the same filters, clusters add up to the located matches, and
        // zoomed-in points equal the list too.
        for (String purpose : List.of("SALE", "RENT")) {
            Map<String, String> filter = query("q", token, "purpose", purpose, "bbox", BBOX);
            long located = expected(seeded, filter).size();
            JsonNode list = page(filter, null, 48);
            JsonNode clusters = json(get("/api/v2/listings/map").param("q", token).param("purpose", purpose).param("bbox", BBOX).param("zoom", "11"));
            assertThat(clusters.path("mode").asText()).isEqualTo("clusters");
            assertThat(clusters.path("total").path("value").asLong()).as("map total = list total, " + purpose)
                    .isEqualTo(list.path("total").path("value").asLong()).isEqualTo(located);
            long clustered = 0;
            for (JsonNode cluster : clusters.path("clusters")) clustered += cluster.path("count").asLong();
            assertThat(clustered).as("clusters add up to the located matches, " + purpose).isEqualTo(located);
        }
        String smallBox = "109.09,12.19,109.15,12.25";
        Map<String, String> zoomed = query("q", token, "purpose", "RENT", "bbox", smallBox);
        JsonNode points = json(get("/api/v2/listings/map").param("q", token).param("purpose", "RENT").param("bbox", smallBox).param("zoom", "15"));
        assertThat(points.path("mode").asText()).isEqualTo("points");
        assertThat(points.path("points")).hasSize(expected(seeded, zoomed).size()).isNotEmpty();
        points.path("points").forEach(point -> assertThat(point.path("price").path("period").asText()).isEqualTo("MONTH"));

        // Seller pages: every public listing of the seller, on v2 (cursor) and v1 (page/size, no 60 cap).
        List<String> sellerV2 = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder request = get("/api/v2/public/sellers/" + seller.id() + "/listings").param("size", "48");
            if (cursor != null) request.param("cursor", cursor);
            JsonNode page = json(request);
            page.path("items").forEach(item -> {
                sellerV2.add(item.path("id").asText());
                assertRentUnit(item.path("purpose").asText(), item.path("price").path("period"));
            });
            cursor = page.path("pageInfo").path("hasNext").asBoolean() ? page.path("pageInfo").path("nextCursor").asText() : null;
        } while (cursor != null);
        assertThat(sellerV2).hasSize(LISTINGS).doesNotHaveDuplicates();
        List<String> sellerV1 = new ArrayList<>();
        for (int pageNumber = 0; ; pageNumber++) {
            MockHttpServletResponse response = mvc.perform(get("/api/v1/public/profiles/" + seller.id() + "/listings")
                    .param("page", Integer.toString(pageNumber)).param("size", "100")).andReturn().getResponse();
            assertThat(response.getHeader("X-Total-Count")).isEqualTo(Integer.toString(LISTINGS));
            JsonNode page = json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
            if (page.isEmpty()) break;
            page.forEach(card -> {
                sellerV1.add(card.path("id").asText());
                assertRentUnit(card.path("purpose").asText(), card.path("pricePeriod"));
            });
        }
        assertThat(sellerV1).containsExactlyInAnyOrderElementsOf(sellerV2);
        // Backward compatible: an oversized page is clamped to 100, a negative page to the first one (W6 review).
        JsonNode clamped = json(get("/api/v1/public/profiles/" + seller.id() + "/listings").param("size", "500").param("page", "-1"));
        assertThat(clamped).hasSize(100);

        // Deprecated v1 search reaches every page (it used to answer [] after the first) and carries the rent unit.
        List<String> v1Rent = new ArrayList<>();
        for (int pageNumber = 0; ; pageNumber++) {
            JsonNode page = json(get("/api/v1/listings/search").param("keyword", token).param("purpose", "RENT")
                    .param("size", "48").param("page", Integer.toString(pageNumber)));
            if (page.isEmpty()) break;
            page.forEach(card -> {
                v1Rent.add(card.path("id").asText());
                assertThat(card.path("pricePeriod").asText()).isEqualTo("MONTH");
            });
        }
        assertThat(v1Rent).containsExactlyInAnyOrderElementsOf(expected(seeded, filters.get(1)));

        // Detail on v1 and v2: the rent unit is part of the price.
        String rentId = seeded.stream().filter(s -> s.purpose().equals("RENT")).findFirst().orElseThrow().id();
        String saleId = seeded.stream().filter(s -> s.purpose().equals("SALE")).findFirst().orElseThrow().id();
        assertThat(json(get("/api/v1/listings/" + rentId)).path("pricePeriod").asText()).isEqualTo("MONTH");
        assertThat(json(get("/api/v1/listings/" + saleId)).path("pricePeriod").isNull()).isTrue();
        assertThat(json(get("/api/v2/listings/" + rentId)).path("price").path("period").asText()).isEqualTo("MONTH");
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static Set<String> expected(List<Seeded> seeded, Map<String, String> filter) {
        Set<String> out = new HashSet<>();
        for (Seeded s : seeded) {
            if (filter.containsKey("purpose") && !filter.get("purpose").equals(s.purpose())) continue;
            if (filter.containsKey("district") && !List.of(filter.get("district").split(",")).contains(s.district())) continue;
            if (filter.containsKey("priceMax") && s.price() > Long.parseLong(filter.get("priceMax"))) continue;
            if (filter.containsKey("priceMin") && s.price() < Long.parseLong(filter.get("priceMin"))) continue;
            if (filter.containsKey("bbox") && !inside(s, filter.get("bbox"))) continue;
            out.add(s.id());
        }
        return out;
    }

    /** {@code bbox = minLng,minLat,maxLng,maxLat}; a listing without a location is never inside. */
    private static boolean inside(Seeded s, String bbox) {
        if (s.lat() == null) return false;
        String[] b = bbox.split(",");
        return s.lng() >= Double.parseDouble(b[0]) && s.lat() >= Double.parseDouble(b[1])
                && s.lng() <= Double.parseDouble(b[2]) && s.lat() <= Double.parseDouble(b[3]);
    }

    private List<String> walk(Map<String, String> filter, String engine, int expectedTotal) throws Exception {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            JsonNode page = page(filter, cursor, 24);
            assertThat(page.path("engine").asText()).as("engine for " + filter).isEqualTo(engine);
            if (pages == 0) {
                assertThat(page.path("total").path("value").asLong()).as(engine + " total for " + filter).isEqualTo(expectedTotal);
                assertThat(page.path("total").path("relation").asText()).isEqualTo("eq");
            }
            page.path("items").forEach(item -> {
                ids.add(item.path("id").asText());
                assertRentUnit(item.path("purpose").asText(), item.path("price").path("period"));
            });
            cursor = page.path("pageInfo").path("hasNext").asBoolean() ? page.path("pageInfo").path("nextCursor").asText() : null;
            pages++;
        } while (cursor != null && pages < 100);
        return ids;
    }

    private JsonNode page(Map<String, String> filter, String cursor, int size) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v2/listings/search").param("size", Integer.toString(size));
        filter.forEach(request::param);
        if (cursor != null) request.param("cursor", cursor);
        return json(request);
    }

    private static void assertRentUnit(String purpose, JsonNode period) {
        if (purpose.equals("RENT")) assertThat(period.asText()).as("rent price period").isEqualTo("MONTH");
        else assertThat(period.isNull() || period.isMissingNode()).as("sale price has no period").isTrue();
    }

    private JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);
        return json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    private static Map<String, String> query(String... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put(pairs[i], pairs[i + 1]);
        return out;
    }
}
