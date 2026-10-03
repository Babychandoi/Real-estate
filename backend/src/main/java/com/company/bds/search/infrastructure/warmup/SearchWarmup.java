package com.company.bds.search.infrastructure.warmup;

import com.company.bds.search.application.SearchIndexSettings;
import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.application.port.ListingSearchEnginePort;
import com.company.bds.search.domain.BoundingBox;
import com.company.bds.search.domain.SearchFilter;
import com.company.bds.search.domain.SearchFilterParser;
import com.company.bds.search.domain.SearchSort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * W6-PERF (cold restart, O5): before an instance takes traffic, warm what a cold start otherwise pays for under load.
 * <ol>
 *   <li>PostgreSQL: {@code pg_prewarm} of the read model's indexes into shared buffers and of its heap into the OS page
 *       cache (only when the extension exists, V101);</li>
 *   <li>Elasticsearch: representative searches and map aggregations sent straight to the engine port (not through the
 *       circuit breaker, so slow first answers cannot open it before traffic arrives), with the hits hydrated from
 *       PostgreSQL, repeated until they answer within the normal budget — this also warms the JVM's hot paths.</li>
 * </ol>
 * Until it finishes (or {@code app.warmup.max-duration} passes) readiness is REFUSING_TRAFFIC and the health contributor
 * {@code searchWarmup} is OUT_OF_SERVICE, so {@code docker compose up --wait}, the frontend's {@code service_healthy}
 * dependency and orchestrators hold traffic back. Off by default (tests); enabled in docker-compose.yml.
 */
@Component("searchWarmup")
public class SearchWarmup implements HealthIndicator {
    private static final Logger log = LoggerFactory.getLogger(SearchWarmup.class);
    private static final BoundingBox CITY = new BoundingBox(105.70, 20.95, 105.90, 21.10);

    private final boolean enabled;
    private final Duration maxDuration;
    private final int rounds;
    private final JdbcTemplate jdbc;
    private final ListingSearchEnginePort engine;
    private final ListingReadModelPort readModel;
    private final SearchIndexSettings settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private volatile boolean done;
    private volatile String summary = "pending";

    public SearchWarmup(@Value("${app.warmup.enabled:false}") boolean enabled,
                        @Value("${app.warmup.max-duration:PT60S}") Duration maxDuration,
                        @Value("${app.warmup.rounds:30}") int rounds,
                        JdbcTemplate jdbc, ListingSearchEnginePort engine, ListingReadModelPort readModel,
                        SearchIndexSettings settings, ApplicationEventPublisher events, Clock clock) {
        this.enabled = enabled;
        this.maxDuration = maxDuration;
        this.rounds = rounds;
        this.jdbc = jdbc;
        this.engine = engine;
        this.readModel = readModel;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
        this.done = !enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) return;
        AvailabilityChangeEvent.publish(events, this, ReadinessState.REFUSING_TRAFFIC);
        Thread thread = new Thread(this::run, "search-warmup");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public Health health() {
        Health.Builder builder = done ? Health.up() : Health.outOfService();
        return builder.withDetail("enabled", enabled).withDetail("result", summary).build();
    }

    public boolean done() {
        return done;
    }

    void run() {
        Instant start = clock.instant();
        Instant deadline = start.plus(maxDuration);
        String database = "skipped";
        String search = "skipped";
        try {
            database = prewarmDatabase(deadline);
            search = warmEngine(deadline);
        } catch (RuntimeException ex) {
            log.warn("search_warmup_failed error={}", ex.toString());
        } finally {
            summary = "database=" + database + ", search=" + search + ", seconds="
                    + Duration.between(start, clock.instant()).toMillis() / 1000.0;
            done = true;
            AvailabilityChangeEvent.publish(events, this, ReadinessState.ACCEPTING_TRAFFIC);
            log.info("search_warmup_done {}", summary);
        }
    }

    private String prewarmDatabase(Instant deadline) {
        Integer installed = jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'pg_prewarm'", Integer.class);
        if (installed == null || installed == 0) return "no pg_prewarm";
        long blocks = 0;
        List<Map<String, Object>> relations = jdbc.queryForList("""
                SELECT c.oid::regclass::text AS name, CASE WHEN c.relkind = 'i' THEN 'buffer' ELSE 'read' END AS mode
                FROM pg_class c
                WHERE c.oid = 'listing_public_read'::regclass
                   OR c.oid IN (SELECT indexrelid FROM pg_index WHERE indrelid IN ('listing_public_read'::regclass, 'users'::regclass))
                ORDER BY c.relkind DESC""");
        for (Map<String, Object> relation : relations) {
            long left = Duration.between(clock.instant(), deadline).toMillis();
            if (left <= 1000) return "partial, " + blocks + " blocks";
            Long read = jdbc.execute((java.sql.Connection connection) -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET statement_timeout = " + (left - 500));
                }
                try (var statement = connection.prepareStatement("SELECT pg_prewarm(?::regclass, ?)")) {
                    statement.setString(1, (String) relation.get("name"));
                    statement.setString(2, (String) relation.get("mode"));
                    try (var rs = statement.executeQuery()) {
                        rs.next();
                        return rs.getLong(1);
                    }
                } finally {
                    try (var statement = connection.createStatement()) {
                        statement.execute("RESET statement_timeout");
                    }
                }
            });
            blocks += read == null ? 0 : read;
        }
        return blocks + " blocks";
    }

    private String warmEngine(Instant deadline) {
        if (!settings.enabled()) return "engine disabled";
        while (!engine.ready()) {
            if (clock.instant().isAfter(deadline)) return "index not ready";
            sleep(500);
        }
        SearchFilter sale = SearchFilterParser.parse(Map.of("purpose", new String[] {"SALE"})).filter();
        SearchFilter rent = SearchFilterParser.parse(Map.of("purpose", new String[] {"RENT"})).filter();
        SearchFilter keyword = SearchFilterParser.parse(Map.of("q", new String[] {"can ho"})).filter();
        int ok = 0;
        int failed = 0;
        int fastRounds = 0;
        for (int round = 0; round < rounds && clock.instant().isBefore(deadline); round++) {
            Instant begin = clock.instant();
            try {
                hydrate(engine.search(sale, SearchSort.NEWEST, null, 25, true, 10_000));
                hydrate(engine.search(rent, SearchSort.NEWEST, null, 25, true, 10_000));
                hydrate(engine.search(sale, SearchSort.PRICE_ASC, null, 25, true, 10_000));
                hydrate(engine.search(keyword, SearchSort.RELEVANCE, null, 25, true, 10_000));
                engine.mapClusters(sale.withBbox(CITY), 13, 2_000, 10_000);
                ok++;
                if (Duration.between(begin, clock.instant()).toMillis() < 400) fastRounds++;
            } catch (RuntimeException ex) {
                failed++;
                sleep(200);
            }
            if (fastRounds >= 5 && round >= 10) break;
        }
        return ok + " rounds ok, " + failed + " failed, " + fastRounds + " fast";
    }

    private void hydrate(ListingSearchEnginePort.Hits hits) {
        List<UUID> ids = hits.hits().stream().map(ListingSearchEnginePort.Hit::id).toList();
        readModel.findByIds(ids);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
