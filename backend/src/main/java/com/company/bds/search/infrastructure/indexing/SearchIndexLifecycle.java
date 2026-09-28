package com.company.bds.search.infrastructure.indexing;

import com.company.bds.search.application.SearchIndexAdministration;
import com.company.bds.search.application.SearchIndexSettings;
import com.company.bds.search.application.SearchProblemException;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.infrastructure.JdbcListingReadModelAdapter;
import com.company.bds.search.infrastructure.SearchIndexStateRepository;
import com.company.bds.search.infrastructure.SearchIndexStateRepository.IndexState;
import com.company.bds.search.infrastructure.SearchIndexStateRepository.Role;
import com.company.bds.search.infrastructure.elasticsearch.ElasticsearchIndexClient;
import com.company.bds.search.infrastructure.elasticsearch.ListingIndexMapping;
import com.company.bds.shared.jobs.JobQueue;
import com.company.bds.shared.scheduling.ScheduledTaskLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Index generations behind the alias (contract §9):
 * <ul>
 *   <li><b>bootstrap</b> (first start, locked across instances): an existing alias is adopted; otherwise a new
 *       {@code <alias>-v2-<timestamp>} index is created, backfilled from {@code listing_public_read} in keyset batches
 *       and the alias is added — atomically removing an old concrete index that carried the alias name (the pre-S2
 *       {@code bds-listings});</li>
 *   <li><b>rebuild</b>: new BUILDING index (dual-written by the search-index job from now on), background backfill via
 *       queue {@code search-rebuild}, atomic alias swap; the old index becomes PREVIOUS and keeps being written, so
 *       <b>rollback</b> is a lossless swap back; <b>cleanup</b> deletes PREVIOUS/RETIRED indices on request.</li>
 * </ul>
 */
@Component
public class SearchIndexLifecycle implements SearchIndexAdministration {
    private static final Logger log = LoggerFactory.getLogger(SearchIndexLifecycle.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);
    static final int BATCH = 500;
    static final Duration RETIRE_GRACE = Duration.ofMinutes(2);

    private final SearchIndexSettings settings;
    private final ElasticsearchIndexClient client;
    private final SearchIndexStateRepository states;
    private final JdbcListingReadModelAdapter readModel;
    private final ListingIndexWriter writer;
    private final ScheduledTaskLock taskLock;
    private final JobQueue jobs;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final boolean bootstrapOnStartup;

    public SearchIndexLifecycle(SearchIndexSettings settings, ElasticsearchIndexClient client, SearchIndexStateRepository states,
                                JdbcListingReadModelAdapter readModel, ListingIndexWriter writer, ScheduledTaskLock taskLock,
                                JobQueue jobs, JdbcTemplate jdbc, TransactionTemplate tx, Clock clock,
                                @Value("${app.search.bootstrap-on-startup:true}") boolean bootstrapOnStartup) {
        this.settings = settings;
        this.client = client;
        this.states = states;
        this.readModel = readModel;
        this.writer = writer;
        this.taskLock = taskLock;
        this.jobs = jobs;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.bootstrapOnStartup = bootstrapOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!settings.enabled() || !bootstrapOnStartup) return;
        Thread thread = new Thread(this::bootstrapQuietly, "search-index-bootstrap");
        thread.setDaemon(true);
        thread.start();
    }

    /** Retries the bootstrap while the index is not ready (e.g. Elasticsearch was down at startup). */
    @Scheduled(fixedDelayString = "${app.search.readiness-check-ms:30000}", initialDelayString = "${app.search.readiness-check-ms:30000}")
    public void ensureReady() {
        if (settings.enabled() && bootstrapOnStartup && !settings.ready()) bootstrapQuietly();
    }

    private void bootstrapQuietly() {
        try {
            bootstrap();
        } catch (RuntimeException ex) {
            log.warn("Search index bootstrap failed, search keeps using the database engine: {}", ex.getMessage());
        }
    }

    /** Makes the alias usable on this instance; idempotent. Returns whether the index is ready. */
    public boolean bootstrap() {
        if (!settings.enabled()) return false;
        String alias = settings.alias();
        boolean ran = taskLock.runExclusive("search-index-bootstrap:" + alias, Duration.ofMinutes(30), () -> {
            List<String> targets = client.aliasTargets(alias);
            if (!targets.isEmpty()) {
                for (String target : targets) {
                    if (states.find(target).isEmpty()) states.insert(target, alias, Role.ACTIVE, ListingIndexMapping.VERSION);
                }
                settings.markReady(true);
                return;
            }
            IndexState building = states.withRole(alias, Role.BUILDING).orElse(null);
            String index = building != null ? building.indexName() : createIndex(alias);
            while (!backfillStep(index, Integer.MAX_VALUE)) {
                // backfillStep returns when the whole read model is indexed
            }
            activate(index);
            settings.markReady(true);
            log.info("Search index alias {} now points at {}", alias, index);
        });
        if (!ran) settings.markReady(!client.aliasTargets(alias).isEmpty());
        return settings.ready();
    }

    @Override
    public Status startRebuild() {
        requireEnabled();
        String alias = settings.alias();
        String index;
        try {
            index = createIndex(alias);
        } catch (DuplicateKeyException ex) {
            throw new SearchProblemException(409, "REBUILD_IN_PROGRESS", "Đang dựng lại chỉ mục",
                    "Một lần dựng lại chỉ mục đang chạy; chờ nó hoàn tất hoặc kiểm tra trạng thái.");
        }
        jobs.enqueue(SearchIndexRebuildJobHandler.QUEUE, index, Map.of("index", index), null);
        return status();
    }

    /**
     * Backfills up to {@code maxBatches} batches; returns true when the index holds the whole read model. Rows are written
     * with their own row version, so concurrent dual-writes with newer versions always win.
     */
    public boolean backfillStep(String index, int maxBatches) {
        IndexState state = states.find(index).orElseThrow(() -> new IllegalStateException("Unknown index " + index));
        UUID cursor = state.backfillCursor();
        for (int batch = 0; batch < maxBatches; batch++) {
            List<PublicListing> rows = readModel.batchAfter(cursor, BATCH);
            if (rows.isEmpty()) {
                states.recordBackfill(index, cursor, 0, true);
                return true;
            }
            Map<UUID, String> failed = writer.write(List.of(index), rows, Map.of());
            if (!failed.isEmpty()) throw new IllegalStateException("Backfill of " + index + " failed for " + failed.size() + " rows");
            cursor = rows.get(rows.size() - 1).listingId();
            states.recordBackfill(index, cursor, rows.size(), false);
        }
        return false;
    }

    /** Atomic alias swap to {@code index}; ACTIVE → PREVIOUS, PREVIOUS → RETIRED. */
    public void activate(String index) {
        String alias = settings.alias();
        List<String> from = client.aliasTargets(alias);
        boolean legacy = from.isEmpty() && client.concreteIndexExists(alias);
        client.refresh(index);
        client.swapAlias(alias, from, index, legacy ? alias : null);
        tx.executeWithoutResult(status -> {
            states.withRole(alias, Role.PREVIOUS).ifPresent(previous -> states.setRole(previous.indexName(), Role.RETIRED));
            states.withRole(alias, Role.ACTIVE).ifPresent(active -> states.setRole(active.indexName(), Role.PREVIOUS));
            states.setRole(index, Role.ACTIVE);
        });
    }

    @Override
    public Status rollback() {
        requireEnabled();
        String alias = settings.alias();
        IndexState previous = states.withRole(alias, Role.PREVIOUS).orElseThrow(() -> new SearchProblemException(409,
                "NO_PREVIOUS_INDEX", "Không có chỉ mục trước đó", "Không còn chỉ mục thế hệ trước để quay lại."));
        IndexState active = states.withRole(alias, Role.ACTIVE).orElseThrow();
        client.refresh(previous.indexName());
        client.swapAlias(alias, client.aliasTargets(alias), previous.indexName(), null);
        tx.executeWithoutResult(status -> {
            // the generation rolled back from stays PREVIOUS (still written), so rolling forward again is lossless too
            states.setRole(active.indexName(), Role.PREVIOUS);
            states.setRole(previous.indexName(), Role.ACTIVE);
        });
        return status();
    }

    /**
     * Two-phase so an in-flight index batch never recreates a deleted index: PREVIOUS becomes RETIRED (no longer
     * written), and RETIRED indices older than {@link #RETIRE_GRACE} are deleted. Call again after the grace period.
     */
    @Override
    public Status cleanup() {
        requireEnabled();
        for (IndexState state : states.all(settings.alias())) {
            if (state.role() == Role.PREVIOUS) {
                states.setRole(state.indexName(), Role.RETIRED);
            } else if (state.role() == Role.RETIRED
                    && (state.retiredAt() == null || state.retiredAt().isBefore(clock.instant().minus(RETIRE_GRACE)))) {
                states.delete(state.indexName());
                client.deleteIndex(state.indexName());
            }
        }
        return status();
    }

    @Override
    public Status status() {
        String alias = settings.alias();
        List<String> targets = List.of();
        List<IndexInfo> indices = new ArrayList<>();
        if (settings.enabled()) {
            try {
                targets = client.aliasTargets(alias);
            } catch (RuntimeException ex) {
                targets = List.of();
            }
        }
        for (IndexState state : states.all(alias)) {
            long docs;
            try {
                docs = settings.enabled() ? client.count(state.indexName()) : -1;
            } catch (RuntimeException ex) {
                docs = -1;
            }
            indices.add(new IndexInfo(state.indexName(), state.role().name(), state.mappingVersion(), state.backfilledRows(),
                    state.backfillCompletedAt(), state.activatedAt(), state.retiredAt(), state.createdAt(), docs));
        }
        Long rows = jdbc.queryForObject("SELECT count(*) FROM listing_public_read", Long.class);
        Map<String, Object> queue = jdbc.queryForMap("""
                SELECT count(*) AS pending, EXTRACT(EPOCH FROM (now() - min(created_at))) AS oldest
                FROM background_jobs WHERE queue = 'search-index' AND completed_at IS NULL AND dead_lettered_at IS NULL
                """);
        Number oldest = (Number) queue.get("oldest");
        return new Status(settings.enabled(), settings.ready(), alias, targets, indices, rows == null ? 0 : rows,
                ((Number) queue.get("pending")).longValue(), oldest == null ? null : oldest.doubleValue());
    }

    /**
     * Creates the concrete index first and registers it as BUILDING afterwards, so the dual-writing job never writes
     * to an index that does not exist yet (which Elasticsearch would auto-create with a dynamic mapping).
     */
    private String createIndex(String alias) {
        if (states.withRole(alias, Role.BUILDING).isPresent()) throw new DuplicateKeyException("rebuild in progress");
        String index = alias + "-v2-" + STAMP.format(clock.instant());
        client.createIndex(index);
        try {
            states.insert(index, alias, Role.BUILDING, ListingIndexMapping.VERSION);
        } catch (RuntimeException ex) {
            client.deleteIndex(index);
            throw ex;
        }
        return index;
    }

    private void requireEnabled() {
        if (!settings.enabled()) {
            throw new SearchProblemException(409, "SEARCH_ENGINE_DISABLED", "Chưa bật công cụ tìm kiếm",
                    "Elasticsearch đang tắt trong cấu hình (app.search.elasticsearch.enabled=false).");
        }
    }
}
