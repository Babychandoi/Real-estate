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
import com.company.bds.search.application.port.ResponseCachePort;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.domain.SearchFilter;
import com.company.bds.search.domain.SearchSort;
import com.company.bds.shared.security.ContactInfoGuard;
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
    static final int MAP_CLUSTER_LIMIT = 2_000;
    /** Response statistics are published only with at least this many answered leads (audit §8.3 seller). */
    public static final int MIN_RESPONSE_SAMPLES = 5;

    /** Resolved public detail: the row plus its images; what the detail cache stores. */
    public record DetailSnapshot(PublicListing listing, List<ImageDto> images) {}

    /** Version handle of a public listing: ETag and cache key. */
    public record DetailRef(UUID listingId, long rowVersion, String etag) {}

    public record SellerPage(Page page, SellerProfile seller) {}

    public record MapResult(String mode, List<MapPoint> points, List<MapCluster> clusters, Total total, String engine,
                            Instant dataAsOf) {}

    private final ListingReadModelPort readModel;
    private final PublicImageResolver images;
    private final ResponseCachePort cache;
    private final SearchCursorCodec cursors;
    private final Clock clock;

    public ListingReadService(ListingReadModelPort readModel, PublicImageResolver images, ResponseCachePort cache,
                              SearchCursorCodec cursors, Clock clock) {
        this.readModel = readModel;
        this.images = images;
        this.cache = cache;
        this.cursors = cursors;
        this.clock = clock;
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
     * otherwise grid clusters (cell = a quarter of a 256 px tile at this zoom). Same filters as the list.
     */
    public MapResult map(SearchFilter filter, int zoom) {
        long counted = readModel.countCapped(filter, SearchResults.TOTAL_CAP);
        Total total = Total.capped(counted, SearchResults.TOTAL_CAP);
        Instant now = clock.instant();
        if (zoom >= MAP_POINTS_MIN_ZOOM && counted <= MAP_POINT_LIMIT) {
            return new MapResult("points", readModel.mapPoints(filter, MAP_POINT_LIMIT), List.of(), total,
                    SearchResults.ENGINE_DATABASE, now);
        }
        // R-2 (W6): never drop the smallest cells to stay under the limit — that made the clusters add up to less than the
        // list total. The cell size is chosen up front so that the grid over the bbox has at most MAP_CLUSTER_LIMIT cells:
        // one aggregation query, and every located match is in some cluster.
        double cell = clusterCell(filter.bbox(), zoom);
        return new MapResult("clusters", List.of(), readModel.mapClusters(filter, cell, MAP_CLUSTER_LIMIT), total,
                SearchResults.ENGINE_DATABASE, now);
    }

    /**
     * Grid cell (degrees) for the zoom — a quarter of a 256 px tile — doubled until the cells the bbox touches (aligned
     * like the SQL: {@code floor(coordinate / cell)}) are at most {@value #MAP_CLUSTER_LIMIT}.
     */
    static double clusterCell(com.company.bds.search.domain.BoundingBox bbox, int zoom) {
        double cell = 360.0 / Math.pow(2, zoom) / 4.0;
        com.company.bds.search.domain.BoundingBox box = bbox != null ? bbox
                : new com.company.bds.search.domain.BoundingBox(-180, -90, 180, 90);
        while (cells(box, cell) > MAP_CLUSTER_LIMIT && cell < 360.0) cell *= 2;
        return cell;
    }

    static long cells(com.company.bds.search.domain.BoundingBox box, double cell) {
        long columns = (long) (Math.floor(box.maxLng() / cell) - Math.floor(box.minLng() / cell)) + 1;
        long rows = (long) (Math.floor(box.maxLat() / cell) - Math.floor(box.minLat() / cell)) + 1;
        return columns * rows;
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
