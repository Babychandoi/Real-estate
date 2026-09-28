package com.company.bds.search.infrastructure.indexing;

import com.company.bds.search.application.SearchIndexSettings;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.infrastructure.JdbcListingReadModelAdapter;
import com.company.bds.search.infrastructure.ReadModelRefresher;
import com.company.bds.search.infrastructure.SearchIndexStateRepository;
import com.company.bds.search.infrastructure.elasticsearch.ElasticsearchHttp;
import com.company.bds.shared.jobs.ClaimedJob;
import com.company.bds.shared.jobs.JobBatchResult;
import com.company.bds.shared.jobs.JobHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Queue {@code search-index} (contract §9, audit F05): jobs carry {@code listingId}, {@code ownerId} or
 * {@code projectId}. Owner/project jobs fan out into listing jobs. Listing jobs of a batch are processed together:
 * <ol>
 *   <li>refresh the read-model rows (one statement; row versions increase);</li>
 *   <li>then read the write targets from {@code search_index_state} — after the refresh committed, so a rebuild that
 *       started later backfills the refreshed row, and a rebuild that started earlier is dual-written here;</li>
 *   <li>one {@code _bulk} with {@code version_type=external}: index visible rows, delete the others.</li>
 * </ol>
 * Replays are harmless (an older version is refused by Elasticsearch as a conflict and counted as done). Metric
 * {@code bds.search.index.lag}: time from the first coalesced change (job creation) to the index write.
 */
@Component
public class SearchIndexJobHandler implements JobHandler {
    public static final String QUEUE = "search-index";

    private final ReadModelRefresher refresher;
    private final JdbcListingReadModelAdapter readModel;
    private final SearchIndexStateRepository states;
    private final ListingIndexWriter writer;
    private final SearchIndexSettings settings;
    private final Clock clock;
    private final Timer lag;

    public SearchIndexJobHandler(ReadModelRefresher refresher, JdbcListingReadModelAdapter readModel,
                                 SearchIndexStateRepository states, ListingIndexWriter writer, SearchIndexSettings settings,
                                 Clock clock, ObjectProvider<MeterRegistry> meters) {
        this.refresher = refresher;
        this.readModel = readModel;
        this.states = states;
        this.writer = writer;
        this.settings = settings;
        this.clock = clock;
        this.lag = Timer.builder("bds.search.index.lag")
                .description("Time from a listing change (first coalesced enqueue) to its search index write")
                .publishPercentiles(0.5, 0.95, 0.99)
                .serviceLevelObjectives(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30))
                .register(meters.getIfAvailable(SimpleMeterRegistry::new));
    }

    @Override public String queue() { return QUEUE; }

    @Override public int batchSize() { return 100; }

    /** Long outages of Elasticsearch are retried for about two days (backoff capped at one hour) before dead-lettering. */
    @Override public int maxAttempts() { return 50; }

    @Override
    public JobBatchResult handle(List<ClaimedJob> jobs) {
        JobBatchResult.Builder result = JobBatchResult.builder();
        Map<UUID, List<ClaimedJob>> byListing = new LinkedHashMap<>();
        for (ClaimedJob job : jobs) {
            try {
                if (job.text("listingId") != null) {
                    byListing.computeIfAbsent(UUID.fromString(job.text("listingId")), id -> new ArrayList<>()).add(job);
                } else if (job.text("ownerId") != null) {
                    refresher.fanOutOwner(UUID.fromString(job.text("ownerId")));
                    result.succeed(job);
                } else if (job.text("projectId") != null) {
                    refresher.fanOutProject(UUID.fromString(job.text("projectId")));
                    result.succeed(job);
                } else {
                    result.failPermanently(job, "search-index job without listingId/ownerId/projectId");
                }
            } catch (IllegalArgumentException ex) {
                result.failPermanently(job, "malformed search-index payload");
            }
        }
        if (byListing.isEmpty()) return result.build();

        List<ReadModelRefresher.Refreshed> refreshed = refresher.refresh(byListing.keySet());
        Map<UUID, Long> deletes = new HashMap<>();
        List<UUID> visible = new ArrayList<>();
        for (ReadModelRefresher.Refreshed row : refreshed) {
            if (row.visible()) visible.add(row.listingId());
            else deletes.put(row.listingId(), row.rowVersion());
        }
        Map<UUID, String> failed = Map.of();
        if (settings.enabled()) {
            List<String> targets = states.writeTargets(settings.alias()).stream()
                    .map(SearchIndexStateRepository.IndexState::indexName).toList();
            if (!targets.isEmpty()) {
                List<PublicListing> rows = readModel.findByIds(visible);
                Map<UUID, PublicListing> found = new HashMap<>();
                rows.forEach(row -> found.put(row.listingId(), row));
                for (ReadModelRefresher.Refreshed row : refreshed) {
                    if (row.visible() && !found.containsKey(row.listingId())) deletes.put(row.listingId(), row.rowVersion());
                }
                try {
                    failed = writer.write(targets, rows, deletes);
                } catch (ElasticsearchHttp.EngineUnavailableException ex) {
                    byListing.values().forEach(list -> list.forEach(job -> result.fail(job, ex.getMessage())));
                    return result.build();
                }
            }
        }
        long now = clock.millis();
        for (Map.Entry<UUID, List<ClaimedJob>> entry : byListing.entrySet()) {
            String failure = failed.get(entry.getKey());
            for (ClaimedJob job : entry.getValue()) {
                if (failure != null) {
                    result.fail(job, failure);
                } else {
                    result.succeed(job);
                    lag.record(Duration.ofMillis(Math.max(0, now - job.createdAt().toEpochMilli())));
                }
            }
        }
        // No cache invalidation here: a cached first page holds only listing ids, which are re-read from PostgreSQL and
        // re-checked against the filter on every hit, so hidden or changed listings are never served from it. New or
        // re-sorted listings appear when the entry expires (20 s TTL); only an index generation swap bumps the key.
        return result.build();
    }
}
