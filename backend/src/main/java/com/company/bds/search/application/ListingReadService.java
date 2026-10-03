package com.company.bds.search.application;

import com.company.bds.media.ImageDto;
import com.company.bds.media.PublicImageResolver;
import com.company.bds.search.application.SearchResults.Page;
import com.company.bds.search.application.SearchResults.Total;
import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.application.port.ListingReadModelPort.GoneListing;
import com.company.bds.search.application.port.ListingReadModelPort.MapCluster;
import com.company.bds.search.application.port.ListingReadModelPort.MapPoint;
import com.company.bds.search.application.port.ListingReadModelPort.PricePoint;
import com.company.bds.search.application.port.ListingReadModelPort.SellerProfile;
import com.company.bds.search.application.port.ListingReadModelPort.VersionRef;
import com.company.bds.search.application.port.ListingSearchEnginePort;
import com.company.bds.search.application.port.ResponseCachePort;
import com.company.bds.search.domain.BoundingBox;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.domain.SearchFilter;
import com.company.bds.search.domain.SearchSort;
import com.company.bds.shared.security.ContactInfoGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Public detail, price history, similar listings, seller pages and the map (contract §8), all on the read model. */
@Service
public class ListingReadService {
    private static final Duration DETAIL_TTL = Duration.ofSeconds(60);
    public static final int MAP_POINT_LIMIT = 400;
    public static final int MAP_POINTS_MIN_ZOOM = 12;
    private static final int MAP_CLUSTER_LIMIT = 2_000;
    /** Response statistics are published only with at least this many answered leads (audit §8.3 seller). */
    public static final int MIN_RESPONSE_SAMPLES = 5;

    /** Resolved public detail: the row plus its images; what the detail cache stores. */
    public record DetailSnapshot(PublicListing listing, List<ImageDto> images) {}

    /** Version handle of a public listing: ETag and cache key. */
    public record DetailRef(UUID listingId, long rowVersion, String etag) {}

    public record SellerPage(Page page, SellerProfile seller) {}

    public record MapResult(String mode, List<MapPoint> points, List<MapCluster> clusters, Total total, String engine,
                            Instant dataAsOf) {}

    /** What the map fallback cache stores (database clusters of a snapped viewport). */
    public record CachedClusters(List<MapCluster> clusters, Total total) {}

    /** Database clusters computed during an Elasticsearch outage are shared for this long (snapped viewport key). */
    static final Duration FALLBACK_CLUSTERS_TTL = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(ListingReadService.class);

    private final ListingReadModelPort readModel;
    private final PublicImageResolver images;
    private final ResponseCachePort cache;
    private final SearchCursorCodec cursors;
    private final Clock clock;
    private final ListingSearchEnginePort engine;
    private final SearchCircuitBreaker breaker;
    private final SearchIndexSettings settings;

    public ListingReadService(ListingReadModelPort readModel, PublicImageResolver images, ResponseCachePort cache,
                              SearchCursorCodec cursors, Clock clock, ListingSearchEnginePort engine,
                              SearchCircuitBreaker breaker, SearchIndexSettings settings) {
        this.readModel = readModel;
        this.images = images;
        this.cache = cache;
        this.cursors = cursors;
        this.clock = clock;
        this.engine = engine;
        this.breaker = breaker;
        this.settings = settings;
    }

    /**
     * Version of a public listing, or {@code 410 LISTING_GONE} (was public, is not any more; body carries slug and the
     * last public title only) / {@code 404 LISTING_NOT_FOUND} (never public, including drafts that exist).
     */
    public DetailRef ref(String slugOrId) {
        VersionRef ref = readModel.findVersion(slugOrId).orElseThrow(() -> goneOrNotFound(slugOrId));
        return new DetailRef(ref.listingId(), ref.rowVersion(),
                "\"l" + ref.listingId() + "-" + ref.rowVersion() + ref.trustStamp() + "\"");
    }

    public DetailSnapshot detail(DetailRef ref) {
        DetailSnapshot snapshot = cache.getOrCompute("listing-detail", "detail:" + ref.etag(), DETAIL_TTL, DetailSnapshot.class,
                () -> readModel.findDetail(ref.listingId())
                        .map(row -> new DetailSnapshot(row, images.resolve(row.mediaUrls())))
                        .orElse(null));
        if (snapshot == null) throw goneOrNotFound(ref.listingId().toString());
        return snapshot;
    }

    public List<PricePoint> priceHistory(String slugOrId) {
        DetailRef ref = ref(slugOrId);
        List<PricePoint> points = new ArrayList<>();
        for (PricePoint point : readModel.priceHistory(ref.listingId())) {
            if (points.isEmpty() || points.get(points.size() - 1).priceVnd() != point.priceVnd()) points.add(point);
        }
        return points;
    }

    public List<PublicListing> similar(String slugOrId, int size) {
        DetailRef ref = ref(slugOrId);
        PublicListing base = readModel.findByIds(List.of(ref.listingId())).stream().findFirst()
                .orElseThrow(() -> goneOrNotFound(slugOrId));
        return readModel.similar(base, size);
    }

    public SellerProfile seller(UUID sellerId) {
        return readModel.sellerProfile(sellerId, clock.instant()).orElseThrow(() -> new SearchProblemException(404,
                "SELLER_NOT_FOUND", "Không tìm thấy người đăng", "Người đăng không tồn tại hoặc không còn hoạt động."));
    }

    /** Public listings of a seller, newest first, keyset paged (no 60-row cap). */
    public Page sellerListings(UUID sellerId, int size, String cursorText) {
        seller(sellerId);
        String hash = "seller:" + sellerId;
        SearchCursorCodec.Cursor cursor = cursorText == null ? null : cursors.decode(cursorText, hash);
        List<PublicListing> rows = readModel.sellerPage(sellerId, cursor == null ? null : cursor.keys(), size + 1);
        boolean hasNext = rows.size() > size;
        List<PublicListing> items = hasNext ? rows.subList(0, size) : rows;
        String next = hasNext ? cursors.encode(SearchResults.ENGINE_DATABASE, SearchSort.NEWEST.name(),
                readModel.keysOf(items.get(size - 1), SearchSort.NEWEST), hash) : null;
        Total total = null;
        if (cursor == null) {
            total = hasNext ? Total.capped(readModel.sellerCountCapped(sellerId, SearchResults.TOTAL_CAP), SearchResults.TOTAL_CAP)
                    : new Total(items.size(), "eq");
        }
        return new Page(List.copyOf(items), hasNext, next, size, total, SearchResults.ENGINE_DATABASE, false, List.of(),
                clock.instant(), List.of());
    }

    /**
     * Points when zoomed in to at least {@value #MAP_POINTS_MIN_ZOOM} and at most {@value #MAP_POINT_LIMIT} matches;
     * otherwise grid clusters (cell = a quarter of a 256 px tile at this zoom, at most 2,000 cells). Same filters as the
     * list. W6-PERF: no separate capped count any more. The points probe reads at most {@value #MAP_POINT_LIMIT} + 1 rows
     * and the cluster total is the engine's hit count or the sum of the cells. Clusters come from Elasticsearch
     * ({@code geotile_grid}, so they follow the ~1 s index lag) and from PostgreSQL only when the engine is disabled or
     * unavailable. During an outage the database clusters are computed for the viewport snapped outward to whole tiles
     * and shared for {@code FALLBACK_CLUSTERS_TTL}, so map traffic cannot overload the database fallback.
     */
    public MapResult map(SearchFilter filter, int zoom) {
        Instant now = clock.instant();
        if (zoom >= MAP_POINTS_MIN_ZOOM) {
            List<MapPoint> probe = readModel.mapPoints(filter, MAP_POINT_LIMIT + 1);
            if (probe.size() <= MAP_POINT_LIMIT) {
                return new MapResult("points", probe, List.of(), new Total(probe.size(), "eq"),
                        SearchResults.ENGINE_DATABASE, now);
            }
        }
        double cell = 360.0 / Math.pow(2, zoom) / 4.0;
        if (settings.enabled() && engine.ready() && breaker.callable() && breaker.tryAcquire()) {
            try {
                ListingSearchEnginePort.MapClusters result = engine.mapClusters(filter, Math.min(29, zoom + 2),
                        MAP_CLUSTER_LIMIT, SearchResults.TOTAL_CAP);
                breaker.recordSuccess();
                List<MapCluster> clusters = result.clusters().stream().map(c -> new MapCluster(c.lat(), c.lng(), c.count(),
                        c.minLng(), c.minLat(), c.maxLng(), c.maxLat())).toList();
                Total total = new Total(Math.min(result.total().value(), SearchResults.TOTAL_CAP), result.total().relation());
                return new MapResult("clusters", List.of(), clusters, total, SearchResults.ENGINE_SEARCH, now);
            } catch (ListingSearchEnginePort.EngineUnavailable ex) {
                breaker.recordFailure();
                log.warn("Elasticsearch unavailable for map clusters, answering from the database: {}", ex.getMessage());
            } catch (ListingSearchEnginePort.EngineRejected ex) {
                breaker.recordIgnored();
                log.error("Elasticsearch rejected a map query (bug, not an outage): {}", ex.getMessage());
            }
        }
        if (!settings.enabled()) {
            CachedClusters exact = databaseClusters(filter, cell);
            return new MapResult("clusters", List.of(), exact.clusters(), exact.total(), SearchResults.ENGINE_DATABASE, now);
        }
        // Outage policy: whole tiles around the viewport, shared across requests (and instances) for a minute.
        SearchFilter snapped = filter.withBbox(snap(filter.bbox(), cell * 4));
        String hash = snapped.filterHash() + ":" + zoom;
        long generation = cache.generation();
        CachedClusters shared = generation >= 0
                ? cache.getOrCompute("map-clusters", "map:" + generation + ":" + hash, FALLBACK_CLUSTERS_TTL,
                        CachedClusters.class, () -> databaseClusters(snapped, cell))
                : cache.collapse("map:" + hash, () -> databaseClusters(snapped, cell));
        return new MapResult("clusters", List.of(), shared.clusters(), shared.total(), SearchResults.ENGINE_DATABASE, now);
    }

    private CachedClusters databaseClusters(SearchFilter filter, double cell) {
        List<MapCluster> clusters = readModel.mapClusters(filter, cell, MAP_CLUSTER_LIMIT);
        long sum = clusters.stream().mapToLong(MapCluster::count).sum();
        // more cells than the bound: the sum is a lower bound
        Total total = clusters.size() >= MAP_CLUSTER_LIMIT ? new Total(Math.min(sum, SearchResults.TOTAL_CAP), "gte")
                : Total.capped(sum, SearchResults.TOTAL_CAP);
        return new CachedClusters(clusters, total);
    }

    static BoundingBox snap(BoundingBox box, double step) {
        return new BoundingBox(Math.max(-180, Math.floor(box.minLng() / step) * step),
                Math.max(-90, Math.floor(box.minLat() / step) * step),
                Math.min(180, Math.ceil(box.maxLng() / step) * step),
                Math.min(90, Math.ceil(box.maxLat() / step) * step));
    }

    /** Thumbnails of a page resolved with one resolver call (at most one query). */
    public Map<String, ImageDto> thumbnails(List<PublicListing> rows) {
        List<String> urls = rows.stream().map(PublicListing::thumbnailUrl).filter(url -> url != null && !url.isBlank()).distinct().toList();
        List<ImageDto> resolved = images.resolve(urls);
        Map<String, ImageDto> byUrl = new java.util.HashMap<>();
        for (int i = 0; i < Math.min(urls.size(), resolved.size()); i++) byUrl.put(urls.get(i), resolved.get(i));
        return byUrl;
    }

    private SearchProblemException goneOrNotFound(String slugOrId) {
        GoneListing gone = readModel.findGone(slugOrId).orElse(null);
        if (gone != null) {
            return new SearchProblemException(410, "LISTING_GONE", "Tin không còn hiển thị",
                    "Tin đăng này đã được ẩn, hết hạn hoặc bị gỡ.",
                    // listingTitle, not the RFC 9457 "title" (which stays the generic problem title); absent for
                    // moderation-locked listings and banned sellers (the read model returns null then)
                    gone.title() == null ? Map.of("slug", gone.slug())
                            : Map.of("slug", gone.slug(), "listingTitle", ContactInfoGuard.redact(gone.title())));
        }
        return new SearchProblemException(404, "LISTING_NOT_FOUND", "Không tìm thấy tin đăng", "Tin đăng không tồn tại.");
    }
}
