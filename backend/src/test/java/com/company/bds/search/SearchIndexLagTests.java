package com.company.bds.search;

import com.company.bds.search.infrastructure.elasticsearch.ElasticsearchIndexClient;
import com.company.bds.search.infrastructure.indexing.SearchIndexLifecycle;
import com.company.bds.shared.jobs.JobWorker;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.BdsIntegrationTestInitializer;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F05.5: change → visible in the search index through the real background worker (no manual drain). Measures the
 * latency of updates and hides one at a time and asserts p95 ≤ 10 s; the numbers are printed for the stream report.
 * The context is closed afterwards so its worker never processes jobs of later test classes.
 */
@BdsIntegrationTest(properties = {BdsIntegrationTestInitializer.ELASTICSEARCH_OPT_IN + "=true", "app.search.bootstrap-on-startup=false",
        "app.jobs.enabled=true", "app.jobs.poll-ms=250"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SearchIndexLagTests {
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired SearchIndexLifecycle lifecycle;
    @Autowired ElasticsearchIndexClient client;
    @Autowired JobWorker worker;
    @Autowired MeterRegistry meters;
    @Value("${app.search.index-name}") String alias;

    @Test
    void updatesAndHidesBecomeVisibleWithinTenSecondsAtP95() throws Exception {
        try {
            assertThat(lifecycle.bootstrap()).isTrue();
            TestData.TestUser seller = data.user().role("BROKER").create();
            List<TestData.TestListing> listings = new ArrayList<>();
            for (int i = 0; i < 20; i++) listings.add(data.listing(seller.id()).title("Độ trễ " + i).create());
            for (TestData.TestListing listing : listings) awaitDocument(listing.id(), doc -> doc != null, 30_000);

            List<Long> updateMs = new ArrayList<>();
            List<Long> hideMs = new ArrayList<>();
            for (int i = 0; i < listings.size(); i++) {
                TestData.TestListing listing = listings.get(i);
                long price = 5_000_000_000L + i;
                long started = System.nanoTime();
                jdbc.update("UPDATE listing_revisions SET price_vnd = ? WHERE id = ?", price, listing.publicRevisionId());
                updateMs.add(awaitDocument(listing.id(), doc -> doc != null && doc.path("_source").path("price_vnd").asLong() == price, 30_000)
                        - started / 1_000_000);
                if (i % 2 == 0) {
                    long hideStarted = System.nanoTime();
                    jdbc.update("UPDATE listings SET status = 'PAUSED' WHERE id = ?", listing.id());
                    hideMs.add(awaitDocument(listing.id(), doc -> doc == null, 30_000) - hideStarted / 1_000_000);
                }
            }
            long p95Update = p95(updateMs);
            long p95Hide = p95(hideMs);
            Timer lag = meters.get("bds.search.index.lag").timer();
            System.out.printf("S2 LAG update n=%d p50=%dms p95=%dms max=%dms | hide n=%d p50=%dms p95=%dms max=%dms | "
                            + "bds.search.index.lag count=%d max=%.0fms%n",
                    updateMs.size(), p50(updateMs), p95Update, Collections.max(updateMs), hideMs.size(), p50(hideMs), p95Hide,
                    Collections.max(hideMs), lag.count(), lag.max(TimeUnit.MILLISECONDS));
            assertThat(p95Update).isLessThanOrEqualTo(10_000);
            assertThat(p95Hide).isLessThanOrEqualTo(10_000);
            assertThat(lag.count()).isGreaterThan(0);
        } finally {
            worker.stop();
        }
    }

    /** Polls the document through the alias (refresh-independent realtime GET); returns the time it matched (ms). */
    private long awaitDocument(UUID id, Predicate<JsonNode> condition, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() / 1_000_000 + timeoutMs;
        String index = client.aliasTargets(alias).get(0);
        while (true) {
            JsonNode doc = client.getDocument(index, id.toString()).orElse(null);
            long now = System.nanoTime() / 1_000_000;
            if (condition.test(doc)) return now;
            if (now > deadline) throw new AssertionError("index did not reflect the change of " + id + " within " + timeoutMs + " ms");
            Thread.sleep(25);
        }
    }

    private static long p95(List<Long> values) {
        return percentile(values, 0.95);
    }

    private static long p50(List<Long> values) {
        return percentile(values, 0.5);
    }

    private static long percentile(List<Long> values, double p) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get((int) Math.ceil(p * sorted.size()) - 1);
    }
}
