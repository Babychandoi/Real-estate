package com.company.bds.search;

import com.company.bds.search.application.SearchCircuitBreaker;
import com.company.bds.search.application.SearchIndexSettings;
import com.company.bds.search.application.SearchProblemException;
import com.company.bds.search.infrastructure.JdbcListingReadModelAdapter;
import com.company.bds.search.infrastructure.SearchIndexStateRepository;
import com.company.bds.search.infrastructure.cache.ListingResponseCache;
import com.company.bds.search.infrastructure.elasticsearch.ElasticsearchIndexClient;
import com.company.bds.search.infrastructure.elasticsearch.ListingIndexMapping;
import com.company.bds.search.infrastructure.indexing.ListingIndexWriter;
import com.company.bds.search.infrastructure.indexing.SearchIndexLifecycle;
import com.company.bds.shared.jobs.JobQueue;
import com.company.bds.shared.jobs.JobWorker;
import com.company.bds.shared.scheduling.ScheduledTaskLock;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.BdsIntegrationTestInitializer;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Elasticsearch engine on the shared test cluster (index prefix {@code s2}): index pipeline with external versions
 * (F05.2), legacy index migration and rebuild with dual-write, alias swap and rollback (F05.3), ES–DB parity on the same
 * data (F06.4, D-08), cursor bound to its engine (F06.2) and visibility decided by PostgreSQL (D-01).
 */
@BdsIntegrationTest(properties = {BdsIntegrationTestInitializer.ELASTICSEARCH_OPT_IN + "=true", "app.search.bootstrap-on-startup=false",
        "app.search.cache.first-page=false",
        // parity is about results, not latency: a loaded CI machine must not trip the 800 ms budget mid-walk
        "app.search.timeout=PT10S"})
class SearchElasticsearchEngineTests {
    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JobWorker worker;
    @Autowired SearchIndexLifecycle lifecycle;
    @Autowired SearchIndexSettings settings;
    @Autowired SearchCircuitBreaker breaker;
    @Autowired ElasticsearchIndexClient client;
    @Autowired SearchIndexStateRepository states;
    @Autowired JdbcListingReadModelAdapter readModel;
    @Autowired ListingIndexWriter writer;
    @Autowired ScheduledTaskLock taskLock;
    @Autowired JobQueue jobs;
    @Autowired TransactionTemplate tx;
    @Autowired Clock clock;
    @Autowired ListingResponseCache responseCache;
    @Value("${spring.elasticsearch.uris}") String esUrl;
    SearchFixtures fixtures;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        fixtures = new SearchFixtures(jdbc, data);
        breaker.reset();
        assertThat(lifecycle.bootstrap()).isTrue();
        settings.markReady(true);
        drain();
    }

    @AfterEach
    void tearDown() {
        settings.markReady(true);
        breaker.reset();
    }

    private void drain() {
        worker.drain("search-index");
        client.refresh(settings.alias());
    }

    private String activeIndex() {
        return client.aliasTargets(settings.alias()).get(0);
    }

    private JsonNode doc(String index, UUID id) {
        return client.getDocument(index, id.toString()).orElse(null);
    }

    private JsonNode getJson(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private long rowVersion(UUID id) {
        return jdbc.queryForObject("SELECT row_version FROM listing_public_read WHERE listing_id = ?", Long.class, id);
    }

    @Test
    void pipelineIndexesWithExternalVersionsNeverLowersThemAndDeletesHiddenListings() throws Exception {
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestListing listing = data.listing(seller.id()).title("Căn hộ pipeline " + SearchFixtures.token()).create();
        drain();
        JsonNode indexed = doc(activeIndex(), listing.id());
        assertThat(indexed).isNotNull();
        assertThat(indexed.path("_version").asLong()).isEqualTo(rowVersion(listing.id()));

        fixtures.revise(listing, "price_vnd = ?", 2_500_000_000L);
        jdbc.update("UPDATE listings SET updated_at = now() WHERE id = ?", listing.id());
        drain();
        JsonNode updated = doc(activeIndex(), listing.id());
        assertThat(updated.path("_source").path("price_vnd").asLong()).isEqualTo(2_500_000_000L);
        long current = updated.path("_version").asLong();
        assertThat(current).isGreaterThan(indexed.path("_version").asLong()).isEqualTo(rowVersion(listing.id()));

        // a delayed replay of the first version is refused as a conflict (counted as done) and changes nothing
        var row = readModel.findByIds(List.of(listing.id())).get(0);
        var staleDoc = ListingIndexMapping.document(json, row).put("price_vnd", 9_999L);
        var replay = client.bulk(List.of(ElasticsearchIndexClient.BulkOp.index(activeIndex(), listing.id().toString(),
                indexed.path("_version").asLong(), staleDoc)));
        assertThat(replay.get(0).status()).isEqualTo(409);
        assertThat(replay.get(0).succeeded()).as("a version conflict means the index is already newer").isTrue();
        JsonNode afterReplay = doc(activeIndex(), listing.id());
        assertThat(afterReplay.path("_version").asLong()).isEqualTo(current);
        assertThat(afterReplay.path("_source").path("price_vnd").asLong()).isEqualTo(2_500_000_000L);

        jdbc.update("UPDATE listings SET status = 'PAUSED' WHERE id = ?", listing.id());
        drain();
        assertThat(doc(activeIndex(), listing.id())).as("hidden listing deleted from the index").isNull();
        // replaying the job of a hidden listing keeps it deleted
        jobs.enqueue("search-index", listing.id().toString(), Map.of("listingId", listing.id().toString()), null);
        drain();
        assertThat(doc(activeIndex(), listing.id())).isNull();
    }

    @Test
    void contactDetailsAreNotIndexedOrFindableThroughElasticsearch() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestListing listing = data.listing(seller.id()).title("Nhà " + token + " gọi 0912345678").create();
        fixtures.revise(listing, "description = ?", "Liên hệ chu.nha@example.com hoặc 0987 654 321 " + token);
        jdbc.update("UPDATE listings SET updated_at = now() WHERE id = ?", listing.id());
        drain();
        JsonNode source = doc(activeIndex(), listing.id()).path("_source");
        assertThat(source.toString()).doesNotContain("0912345678", "0987", "example");
        JsonNode hit = getJson(get("/api/v2/listings/search").param("q", token), 200);
        assertThat(hit.path("engine").asText()).isEqualTo("search");
        assertThat(hit.path("items").size()).isEqualTo(1);
        for (String probe : List.of("0912345678", "0987 654 321", "chu.nha@example.com")) {
            JsonNode page = getJson(get("/api/v2/listings/search").param("q", token + " " + probe), 200);
            assertThat(page.path("items").size()).as(probe).isZero();
        }
    }

    @Test
    void theOldFullScanSyncCannotWriteIntoTheNewAlias() throws Exception {
        lifecycle.bootstrap();
        // exactly the document shape and call of the pre-S2 ElasticsearchListingIndex.syncAll (internal versioning)
        String legacyDoc = "{\"listing_id\":\"x\",\"created_at\":\"2026-01-01T00:00:00Z\",\"title\":\"t\","
                + "\"description\":\"d\",\"purpose\":\"SALE\",\"property_type\":\"HOUSE\",\"price_vnd\":1,"
                + "\"area_m2\":1,\"address_summary\":\"a\",\"is_verified_owner\":false}";
        HttpResponse<String> put = http.send(HttpRequest.newBuilder(URI.create(esUrl + "/" + settings.alias() + "/_doc/"
                        + UUID.randomUUID())).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(legacyDoc)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(put.statusCode()).as(put.body()).isEqualTo(400);
        assertThat(put.body()).contains("strict_dynamic_mapping_exception");
    }

    @Test
    void firstStartMigratesALegacyConcreteIndexIntoAnAliasOverAVersionedIndex() throws Exception {
        String alias = "s2-legacy-" + UUID.randomUUID().toString().substring(0, 8);
        HttpResponse<String> created = http.send(HttpRequest.newBuilder(URI.create(esUrl + "/" + alias + "/_doc/old?refresh=true"))
                .header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString("{\"title\":\"legacy\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isIn(200, 201);
        SearchIndexSettings legacySettings = new SearchIndexSettings(true, alias);
        SearchIndexLifecycle legacyLifecycle = new SearchIndexLifecycle(legacySettings, client, states, readModel, writer, taskLock,
                jobs, jdbc, tx, clock, false, responseCache);
        try {
            assertThat(legacyLifecycle.bootstrap()).isTrue();
            List<String> targets = client.aliasTargets(alias);
            assertThat(targets).hasSize(1);
            assertThat(targets.get(0)).startsWith(alias + "-v2-");
            assertThat(client.concreteIndexExists(alias)).as("old concrete index removed").isFalse();
            client.refresh(alias);
            Long rows = jdbc.queryForObject("SELECT count(*) FROM listing_public_read", Long.class);
            assertThat(client.count(alias)).isEqualTo(rows);
            assertThat(states.withRole(alias, SearchIndexStateRepository.Role.ACTIVE)).isPresent();
            // idempotent second start
            assertThat(legacyLifecycle.bootstrap()).isTrue();
            assertThat(client.aliasTargets(alias)).isEqualTo(targets);
        } finally {
            for (var state : states.all(alias)) {
                client.deleteIndex(state.indexName());
                states.delete(state.indexName());
            }
        }
    }

    @Test
    void rebuildDuringChangesLosesNothingSwapsAtomicallyAndRollsBack() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestUser admin = data.user().role("ADMIN").create();
        String bearer = "Bearer " + data.sessionFor(admin.id());
        List<TestData.TestListing> listings = new ArrayList<>();
        for (int i = 0; i < 30; i++) listings.add(data.listing(seller.id()).title("Rebuild " + token + " " + i).create());
        drain();
        String oldIndex = activeIndex();

        JsonNode started = getJson(post("/api/v2/admin/search/index/rebuild").header("Authorization", bearer), 202);
        String building = null;
        for (JsonNode index : started.path("indices")) {
            if (index.path("role").asText().equals("BUILDING")) building = index.path("name").asText();
        }
        assertThat(building).isNotNull();
        assertThat(getJson(post("/api/v2/admin/search/index/rebuild").header("Authorization", bearer), 409).path("code").asText())
                .isEqualTo("REBUILD_IN_PROGRESS");

        // changes while the backfill has not started, between its batches and before the swap
        fixtures.revise(listings.get(0), "price_vnd = ?", 1_111_000_000L);
        jdbc.update("UPDATE listings SET status = 'PAUSED' WHERE id = ?", listings.get(1).id());
        drain();
        lifecycle.backfillStep(building, 1);
        TestData.TestListing added = data.listing(seller.id()).title("Rebuild " + token + " mới").create();
        jdbc.update("UPDATE listings SET status = 'LOCKED' WHERE id = ?", listings.get(2).id());
        fixtures.revise(listings.get(3), "price_vnd = ?", 3_333_000_000L);
        drain();
        while (!lifecycle.backfillStep(building, 1)) {
            // keep backfilling in single batches
        }
        fixtures.revise(listings.get(4), "price_vnd = ?", 4_444_000_000L);
        drain();
        worker.drain("search-rebuild");
        client.refresh(settings.alias());

        assertThat(activeIndex()).isEqualTo(building);
        assertIndexMatchesReadModel(building, seller.id());
        assertThat(doc(building, added.id())).isNotNull();
        assertThat(doc(building, listings.get(1).id())).isNull();
        assertThat(doc(building, listings.get(2).id())).isNull();
        assertThat(doc(building, listings.get(4).id()).path("_source").path("price_vnd").asLong()).isEqualTo(4_444_000_000L);

        // the previous generation is still written after the swap, so rolling back loses nothing either
        fixtures.revise(listings.get(5), "price_vnd = ?", 5_555_000_000L);
        drain();
        JsonNode rolledBack = getJson(post("/api/v2/admin/search/index/rollback").header("Authorization", bearer), 200);
        assertThat(rolledBack.path("aliasTargets").get(0).asText()).isEqualTo(oldIndex);
        client.refresh(oldIndex);
        assertIndexMatchesReadModel(oldIndex, seller.id());
        assertThat(doc(oldIndex, listings.get(5).id()).path("_source").path("price_vnd").asLong()).isEqualTo(5_555_000_000L);

        // cleanup retires the rolled-back generation first (no longer written), deletes it after the grace period
        JsonNode cleaned = getJson(post("/api/v2/admin/search/index/cleanup").header("Authorization", bearer), 200);
        for (JsonNode index : cleaned.path("indices")) {
            if (index.path("name").asText().equals(building)) assertThat(index.path("role").asText()).isEqualTo("RETIRED");
        }
        assertThat(getJson(get("/api/v2/admin/search/index").header("Authorization", bearer), 200).path("ready").asBoolean()).isTrue();
        TestData.TestUser plain = data.user().role("BROKER").create();
        assertThat(mvc.perform(post("/api/v2/admin/search/index/rebuild").header("Authorization", "Bearer " + data.sessionFor(plain.id())))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        client.deleteIndex(building);
        states.delete(building);
    }

    private void assertIndexMatchesReadModel(String index, UUID ownerId) throws Exception {
        Map<String, Long> expected = new LinkedHashMap<>();
        jdbc.query("SELECT listing_id::text, row_version FROM listing_public_read WHERE owner_id = ?", rs -> {
            expected.put(rs.getString(1), rs.getLong(2));
        }, ownerId);
        Map<String, Long> actual = new LinkedHashMap<>();
        String body = "{\"size\":10000,\"_source\":false,\"version\":true,\"query\":{\"term\":{\"owner_id\":\"" + ownerId + "\"}}}";
        JsonNode hits = json.readTree(http.send(HttpRequest.newBuilder(URI.create(esUrl + "/" + index + "/_search"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString()).body()).path("hits").path("hits");
        hits.forEach(hit -> actual.put(hit.path("_id").asText(), hit.path("_version").asLong()));
        assertThat(actual).as("every public row indexed with its current version, nothing else").isEqualTo(expected);
    }

    @Test
    void mapClustersComeFromElasticsearchAndPointsFromABoundedProbe() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        for (int i = 0; i < 5; i++) data.listing(seller.id()).title("Nhà " + token + " " + i).location(21.03 + i * 0.001, 105.80 + i * 0.001).create();
        data.listing(seller.id()).title("Nhà " + token + " xa").location(21.30, 105.60).create();
        drain();

        JsonNode clusters = getJson(get("/api/v2/listings/map").param("q", token).param("bbox", "105.0,20.5,106.5,21.9")
                .param("zoom", "9"), 200);
        assertThat(clusters.path("mode").asText()).isEqualTo("clusters");
        assertThat(clusters.path("engine").asText()).isEqualTo("search");
        long clustered = 0;
        for (JsonNode cluster : clusters.path("clusters")) {
            clustered += cluster.path("count").asLong();
            assertThat(cluster.path("bbox")).hasSize(4);
            assertThat(cluster.path("lat").asDouble()).isBetween(21.0, 21.31);
        }
        assertThat(clustered).isEqualTo(6);
        assertThat(clusters.path("total").path("value").asLong()).isEqualTo(6);

        JsonNode points = getJson(get("/api/v2/listings/map").param("q", token).param("bbox", "105.79,21.02,105.82,21.05")
                .param("zoom", "15"), 200);
        assertThat(points.path("mode").asText()).isEqualTo("points");
        assertThat(points.path("points")).hasSize(5);
        assertThat(points.path("total").path("value").asLong()).isEqualTo(5);
    }

    @Test
    void expiredActiveDocumentsLeaveSearchAndMapWithoutWaitingForTheExpiryScheduler() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestListing expired = data.listing(seller.id()).title("Nhà " + token + " hết hạn").location(21.03, 105.80).create();
        TestData.TestListing noExpiry = data.listing(seller.id()).title("Nhà " + token + " không thời hạn").location(21.04, 105.81).create();
        Instant future = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("UPDATE listings SET expires_at = ?, updated_at = now() WHERE id = ?", java.sql.Timestamp.from(future), expired.id());
        jdbc.update("UPDATE listings SET expires_at = NULL, updated_at = now() WHERE id = ?", noExpiry.id());
        drain();
        assertThat(Instant.parse(doc(activeIndex(), expired.id()).path("_source").path("expires_at").asText())).isEqualTo(future);
        var row = readModel.findByIds(List.of(expired.id())).get(0);
        Instant deadline = Instant.now().minusSeconds(1);
        jdbc.update("UPDATE listings SET expires_at = ? WHERE id = ?", java.sql.Timestamp.from(deadline), expired.id());
        // Keep an indexed ACTIVE document, just as it remains when time passes before the lifecycle job runs.
        var elapsed = ListingIndexMapping.document(json, row).put("expires_at", deadline.toString());
        assertThat(client.bulk(List.of(ElasticsearchIndexClient.BulkOp.index(activeIndex(), expired.id().toString(),
                row.rowVersion() + 100, elapsed))).get(0).succeeded()).isTrue();
        client.refresh(settings.alias());
        assertThat(doc(activeIndex(), expired.id())).as("still stored, not scheduler-deleted").isNotNull();
        assertThat(doc(activeIndex(), noExpiry.id()).path("_source").path("expires_at").asText()).startsWith("9999-");
        JsonNode page = getJson(get("/api/v2/listings/search").param("q", token), 200);
        assertThat(page.path("engine").asText()).isEqualTo("search");
        assertThat(page.path("items")).hasSize(1);
        assertThat(page.path("items").get(0).path("id").asText()).isEqualTo(noExpiry.id().toString());
        JsonNode clusters = getJson(get("/api/v2/listings/map").param("q", token).param("bbox", "105.0,20.5,106.5,21.9")
                .param("zoom", "9"), 200);
        assertThat(clusters.path("engine").asText()).isEqualTo("search");
        assertThat(clusters.path("total").path("value").asLong()).isEqualTo(1);
        long count = 0;
        for (JsonNode cluster : clusters.path("clusters")) count += cluster.path("count").asLong();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void anExistingAliasWithoutExpiryFallsBackUntilSchemaRebuildAndCannotBeRolledBack() throws Exception {
        String alias = "s2-expiry-" + UUID.randomUUID().toString().substring(0, 8);
        String old = alias + "-old";
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(ListingIndexMapping.body(0));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("mappings")).remove("_meta");
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("mappings").path("properties")).remove("expires_at");
        HttpResponse<String> created = http.send(HttpRequest.newBuilder(URI.create(esUrl + "/" + old))
                .header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        states.insert(old, alias, SearchIndexStateRepository.Role.ACTIVE, 1);
        client.swapAlias(alias, List.of(), old, null);
        SearchIndexSettings legacySettings = new SearchIndexSettings(true, alias);
        SearchIndexLifecycle legacyLifecycle = new SearchIndexLifecycle(legacySettings, client, states, readModel, writer, taskLock,
                jobs, jdbc, tx, clock, false, responseCache);
        try {
            assertThat(client.mappingVersion(old)).isEqualTo(1);
            assertThatThrownBy(() -> legacyLifecycle.activate(old)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("incompatible mapping");
            assertThat(client.aliasTargets(alias)).containsExactly(old);
            assertThat(legacyLifecycle.bootstrap()).isFalse();
            assertThat(legacySettings.ready()).isFalse();
            var building = states.withRole(alias, SearchIndexStateRepository.Role.BUILDING).orElseThrow();
            assertThat(building.mappingVersion()).isEqualTo(2);
            assertThat(legacyLifecycle.bootstrap()).as("second start reuses the build").isFalse();
            assertThat(states.withRole(alias, SearchIndexStateRepository.Role.BUILDING).orElseThrow().indexName()).isEqualTo(building.indexName());
            var rows = readModel.batchAfter(null, 2);
            assertThat(writer.write(List.of(old, building.indexName()), rows, Map.of())).as("dual-write old strict schema").isEmpty();
            assertThat(legacyLifecycle.backfillStep(building.indexName(), Integer.MAX_VALUE)).isTrue();
            legacyLifecycle.activate(building.indexName());
            assertThat(legacyLifecycle.bootstrap()).isTrue();
            assertThat(client.mappingVersion(building.indexName())).isEqualTo(2);
            assertThatThrownBy(legacyLifecycle::rollback).isInstanceOfSatisfying(SearchProblemException.class,
                    error -> assertThat(error.code()).isEqualTo("MAPPING_VERSION_MISMATCH"));
        } finally {
            for (var state : states.all(alias)) {
                client.deleteIndex(state.indexName());
                states.delete(state.indexName());
            }
        }
    }

    @Test
    void bothEnginesReturnTheSamePagesForTheSameData() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestUser verifiedSeller = fixtures.seller("OWNER");
        fixtures.kyc(verifiedSeller.id(), "VERIFIED", Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(300)));
        Instant base = Instant.now().minus(Duration.ofDays(2));
        String[][] places = {{"005", "Dịch Vọng, Cầu Giấy, Hà Nội"}, {"007", "Bạch Mai, Hai Bà Trưng, Hà Nội"},
                {"019", "Mễ Trì, Nam Từ Liêm, Hà Nội"}, {"006", "Láng Hạ, Đống Đa, Hà Nội"}};
        String[] titles = {"Căn hộ cao cấp view hồ", "Nhà phố mặt tiền kinh doanh", "Chung cư mini giá rẻ", "Biệt thự sân vườn yên tĩnh"};
        for (int i = 0; i < 64; i++) {
            String[] place = places[i % places.length];
            UUID owner = i % 5 == 0 ? verifiedSeller.id() : seller.id();
            data.listing(owner).title(titles[i % titles.length] + " " + token + " số " + (i % 9))
                    .district(place[0], place[1]).price(2_000_000_000L + (i % 7) * 100_000_000L).area(Integer.toString(50 + (i % 5) * 10))
                    .bedrooms(1 + i % 4).createdAt(base.plusSeconds(i / 3)).location(21.0 + (i % 8) * 0.01, 105.80 + (i % 6) * 0.01).create();
        }
        // a listing that is still in the index but hidden in PostgreSQL (stale index document)
        TestData.TestListing stale = data.listing(seller.id()).title("Căn hộ cao cấp view hồ " + token).create();
        drain();
        jdbc.update("UPDATE listings SET status = 'PAUSED' WHERE id = ?", stale.id());
        assertThat(doc(activeIndex(), stale.id())).as("index not yet updated").isNotNull();

        List<Map<String, String>> queries = List.of(
                Map.of("q", token, "sort", "NEWEST"),
                Map.of("q", "Cầu Giấy " + token, "sort", "NEWEST"),
                Map.of("q", "cau giay " + token, "sort", "PRICE_ASC"),
                Map.of("q", "HBT " + token, "sort", "PRICE_DESC"),
                Map.of("q", "hai ba trung " + token, "sort", "AREA_DESC"),
                Map.of("q", "căn hộ " + token, "sort", "NEWEST", "bedsMin", "2", "priceMax", "2400000000"),
                Map.of("q", "ntl " + token, "sort", "NEWEST", "verified", "IDENTITY"),
                Map.of("q", token, "sort", "PRICE_ASC", "district", "005,006", "areaMin", "60"),
                Map.of("q", token, "sort", "AREA_DESC", "bbox", "105.795,20.995,105.825,21.045"),
                Map.of("q", "biet thu " + token, "sort", "NEWEST", "type", "APARTMENT"));
        for (Map<String, String> query : queries) {
            List<String> onSearch = walk(query, "search");
            settings.markReady(false);
            List<String> onDatabase;
            try {
                onDatabase = walk(query, "database");
            } finally {
                settings.markReady(true);
            }
            assertThat(onSearch).as("same pages for " + query).containsExactlyElementsOf(onDatabase);
            assertThat(onSearch).doesNotContain(stale.id().toString());
            assertThat(new HashSet<>(onSearch)).hasSameSizeAs(onSearch);
        }
        List<String> all = walk(Map.of("q", token, "sort", "NEWEST"), "search");
        assertThat(all).hasSize(64);
        // relevance is Elasticsearch-only; the set of matches is the same as the database's
        List<String> relevance = walk(Map.of("q", "cau giay " + token), "search");
        settings.markReady(false);
        try {
            JsonNode approximate = getJson(get("/api/v2/listings/search").param("q", "cau giay " + token), 200);
            assertThat(approximate.path("notices").toString()).contains("RELEVANCE_APPROXIMATE");
            assertThat(walk(Map.of("q", "cau giay " + token), "database")).containsExactlyInAnyOrderElementsOf(relevance);
        } finally {
            settings.markReady(true);
        }
        // invalid queries fail identically on both engines (validation happens before engine choice)
        assertThat(getJson(get("/api/v2/listings/search").param("sort", "RELEVANCE"), 400).path("code").asText()).isEqualTo("INVALID_FILTER");
    }

    private List<String> walk(Map<String, String> query, String engine) throws Exception {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        int guard = 0;
        do {
            MockHttpServletRequestBuilder request = get("/api/v2/listings/search").param("size", "5");
            query.forEach(request::param);
            if (cursor != null) request.param("cursor", cursor);
            JsonNode page = getJson(request, 200);
            assertThat(page.path("engine").asText()).isEqualTo(engine);
            page.path("items").forEach(item -> ids.add(item.path("id").asText()));
            cursor = page.path("pageInfo").path("hasNext").asBoolean() ? page.path("pageInfo").path("nextCursor").asText() : null;
        } while (cursor != null && ++guard < 100);
        return ids;
    }

    @Test
    void aCursorIsRefusedByTheOtherEngine() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        for (int i = 0; i < 4; i++) data.listing(seller.id()).title("Căn " + token + " " + i).create();
        drain();
        JsonNode onSearch = getJson(get("/api/v2/listings/search").param("q", token).param("size", "2").param("sort", "NEWEST"), 200);
        assertThat(onSearch.path("engine").asText()).isEqualTo("search");
        String searchCursor = onSearch.path("pageInfo").path("nextCursor").asText();
        settings.markReady(false);
        JsonNode changed = getJson(get("/api/v2/listings/search").param("q", token).param("size", "2").param("sort", "NEWEST")
                .param("cursor", searchCursor), 409);
        assertThat(changed.path("code").asText()).isEqualTo("CURSOR_ENGINE_CHANGED");
        JsonNode onDatabase = getJson(get("/api/v2/listings/search").param("q", token).param("size", "2").param("sort", "NEWEST"), 200);
        assertThat(onDatabase.path("degraded").asBoolean()).isTrue();
        String databaseCursor = onDatabase.path("pageInfo").path("nextCursor").asText();
        settings.markReady(true);
        assertThat(getJson(get("/api/v2/listings/search").param("q", token).param("size", "2").param("sort", "NEWEST")
                .param("cursor", databaseCursor), 409).path("code").asText()).isEqualTo("CURSOR_ENGINE_CHANGED");
        JsonNode next = getJson(get("/api/v2/listings/search").param("q", token).param("size", "2").param("sort", "NEWEST")
                .param("cursor", searchCursor), 200);
        assertThat(next.path("items")).hasSize(2);
    }

    @Test
    void mappingIsExplicitWithTheFoldingAnalyzer() throws Exception {
        JsonNode mapping = json.readTree(http.send(HttpRequest.newBuilder(URI.create(esUrl + "/" + activeIndex() + "/_mapping")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body()).path(activeIndex()).path("mappings");
        assertThat(mapping.path("dynamic").asText()).isEqualTo("strict");
        assertThat(mapping.path("properties").path("published_at").path("type").asText()).isEqualTo("date_nanos");
        assertThat(mapping.path("properties").path("expires_at").path("type").asText()).isEqualTo("date");
        assertThat(mapping.path("_meta").path("bds_listing_version").asInt()).isEqualTo(2);
        assertThat(mapping.path("properties").path("search_text").path("analyzer").asText()).isEqualTo("vi_fold");
        JsonNode analyzed = json.readTree(http.send(HttpRequest.newBuilder(URI.create(esUrl + "/" + activeIndex() + "/_analyze"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"analyzer\":\"vi_fold\",\"text\":\"Đống Đa Hoàng Mai\"}")).build(),
                HttpResponse.BodyHandlers.ofString()).body());
        List<String> tokens = new ArrayList<>();
        analyzed.path("tokens").forEach(t -> tokens.add(t.path("token").asText()));
        assertThat(tokens).containsExactly("dong", "da", "hoang", "mai");
        assertThat(ListingIndexMapping.VERSION).isEqualTo(2);
    }
}
