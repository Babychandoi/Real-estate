package com.company.bds.search;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.BdsIntegrationTestInitializer;
import com.company.bds.testsupport.BdsTestEnvironment;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in Elasticsearch test: a stream-prefixed random index on the shared test cluster, deleted when the JVM exits. The
 * scheduled sync is pushed a day out (its first run is one interval after startup), so only the calls in the test sync.
 */
@BdsIntegrationTest(properties = {BdsIntegrationTestInitializer.ELASTICSEARCH_OPT_IN + "=true", "app.search.sync-ms=86400000"})
class ElasticsearchSyncTests {
    @Autowired ElasticsearchListingIndex index;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Value("${app.search.index-name}") String indexName;
    @Value("${spring.elasticsearch.uris}") String elasticsearchUrl;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void fullSyncUsesThePrefixedIndexAndRunsOnlyOnTheInstanceHoldingTheTaskLock() throws Exception {
        assertThat(indexName).startsWith(BdsTestEnvironment.optional("BDS_TEST_ES_PREFIX", "s0be") + "-listings-");
        TestData.TestUser owner = data.user().role("BROKER").create();
        TestData.TestListing listing = data.listing(owner.id()).title("Căn hộ kiểm thử đồng bộ chỉ mục").create();

        jdbc.update("""
                INSERT INTO scheduled_task_locks(name, locked_until, locked_by) VALUES ('search-index-sync', now() + interval '1 minute', 'other#1')
                ON CONFLICT (name) DO UPDATE SET locked_until = EXCLUDED.locked_until, locked_by = EXCLUDED.locked_by
                """);
        try {
            index.sync();
            assertThat(document(listing.id().toString()).statusCode()).as("another instance holds the sync lock").isEqualTo(404);
        } finally {
            jdbc.update("DELETE FROM scheduled_task_locks WHERE name = 'search-index-sync'");
        }

        index.sync();
        HttpResponse<String> indexed = document(listing.id().toString());
        assertThat(indexed.statusCode()).isEqualTo(200);
        assertThat(json.readTree(indexed.body()).path("_source").path("title").asText()).isEqualTo("Căn hộ kiểm thử đồng bộ chỉ mục");
    }

    private HttpResponse<String> document(String id) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(elasticsearchUrl + "/" + indexName + "/_doc/" + id))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
