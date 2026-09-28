package com.company.bds.search.api;

import com.company.bds.search.api.response.ListingV2Responses.ListingDetailV2;
import com.company.bds.search.api.response.ListingV2Responses.ListingSummaryV2;
import com.company.bds.search.api.response.ListingV2Responses.MapClusterDto;
import com.company.bds.search.api.response.ListingV2Responses.MapPointDto;
import com.company.bds.search.api.response.ListingV2Responses.MapResponseV2;
import com.company.bds.search.api.response.ListingV2Responses.PriceHistoryResponse;
import com.company.bds.search.api.response.ListingV2Responses.PricePointDto;
import com.company.bds.search.api.response.ListingV2Responses.SearchResponseV2;
import com.company.bds.search.application.ListingReadService;
import com.company.bds.search.application.ListingSearchService;
import com.company.bds.search.application.SearchResults;
import com.company.bds.search.domain.BoundingBox;
import com.company.bds.search.domain.InvalidFilterException;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.domain.SearchFilterParser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Public search/map/detail API v2 (contract §8). */
@RestController
@RequestMapping("/api/v2/listings")
public class ListingSearchV2Controller {
    private final ListingSearchService search;
    private final ListingReadService reads;
    private final Clock clock;

    public ListingSearchV2Controller(ListingSearchService search, ListingReadService reads, Clock clock) {
        this.search = search;
        this.reads = reads;
        this.clock = clock;
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponseV2> search(HttpServletRequest request) {
        SearchResults.Page page = search.search(SearchFilterParser.parse(request.getParameterMap()));
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .body(ListingV2Mapper.page(page, reads.thumbnails(page.items()), clock.instant()));
    }

    @GetMapping("/map")
    public ResponseEntity<MapResponseV2> map(HttpServletRequest request) {
        Map<String, String[]> params = new HashMap<>(request.getParameterMap());
        String[] zoomValues = params.remove("zoom");
        String[] bboxValues = params.remove("bbox");
        params.remove("cursor");
        params.remove("size");
        SearchFilterParser.Errors errors = new SearchFilterParser.Errors();
        Integer zoom = null;
        if (zoomValues == null || zoomValues.length != 1 || !zoomValues[0].matches("\\d{1,2}")
                || Integer.parseInt(zoomValues[0]) < 3 || Integer.parseInt(zoomValues[0]) > 20) {
            errors.add("zoom", "Mức phóng to phải là số nguyên từ 3 đến 20.");
        } else {
            zoom = Integer.parseInt(zoomValues[0]);
        }
        BoundingBox bbox = null;
        if (bboxValues == null || bboxValues.length != 1) errors.add("bbox", "Bản đồ cần tham số bbox=minLng,minLat,maxLng,maxLat.");
        else bbox = SearchFilterParser.bbox(bboxValues[0], errors, InvalidFilterException.BBOX_TOO_LARGE);
        SearchFilterParser.SearchRequest parsed;
        try {
            parsed = SearchFilterParser.parse(params);
        } catch (InvalidFilterException ex) {
            if (errors.isEmpty()) throw ex;
            List<InvalidFilterException.FilterError> all = new java.util.ArrayList<>(ex.errors());
            all.addAll(errors.list());
            throw new InvalidFilterException(InvalidFilterException.INVALID_FILTER, all);
        }
        errors.throwIfAny(InvalidFilterException.INVALID_FILTER);
        ListingReadService.MapResult result = reads.map(parsed.filter().withBbox(bbox), zoom);
        List<MapPointDto> points = result.points().stream().map(p -> new MapPointDto(p.id(), p.slug(), p.lat(), p.lng(),
                ListingV2Mapper.money(p.priceVnd(), p.pricePeriod()), p.propertyType())).toList();
        List<MapClusterDto> clusters = result.clusters().stream().map(c -> new MapClusterDto(c.lat(), c.lng(), c.count(),
                List.of(c.minLng(), c.minLat(), c.maxLng(), c.maxLat()))).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(new MapResponseV2(result.mode(), points, clusters,
                ListingV2Mapper.total(result.total()), result.engine(), result.dataAsOf()));
    }

    /** Public detail: {@code 404 LISTING_NOT_FOUND} / {@code 410 LISTING_GONE}; ETag from the read-model version. */
    @GetMapping("/{slugOrId}")
    public ResponseEntity<ListingDetailV2> detail(@PathVariable String slugOrId,
                                                  @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        ListingReadService.DetailRef ref = reads.ref(slugOrId);
        if (matches(ifNoneMatch, ref.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(ref.etag()).cacheControl(CacheControl.noCache()).build();
        }
        ListingReadService.DetailSnapshot snapshot = reads.detail(ref);
        return ResponseEntity.ok().eTag(ref.etag()).cacheControl(CacheControl.noCache())
                .body(ListingV2Mapper.detail(snapshot.listing(), snapshot.images(), clock.instant()));
    }

    @GetMapping("/{slugOrId}/price-history")
    public ResponseEntity<PriceHistoryResponse> priceHistory(@PathVariable String slugOrId) {
        ListingReadService.DetailRef ref = reads.ref(slugOrId);
        var points = reads.priceHistory(ref.listingId().toString());
        String purpose = points.isEmpty() ? null : points.get(0).pricePeriod() == null ? "SALE" : "RENT";
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(new PriceHistoryResponse(ref.listingId(), purpose,
                points.stream().map(p -> new PricePointDto(ListingV2Mapper.money(p.priceVnd(), p.pricePeriod()), p.changedAt())).toList()));
    }

    /** Rule-based similar listings: same purpose and type, price within ±30 %, same district first (audit D-11). */
    @GetMapping("/{slugOrId}/similar")
    public ResponseEntity<List<ListingSummaryV2>> similar(@PathVariable String slugOrId,
                                                          @RequestParam(defaultValue = "6") int size) {
        if (size < 1 || size > 12) {
            throw new InvalidFilterException(InvalidFilterException.INVALID_FILTER,
                    List.of(new InvalidFilterException.FilterError("size", "Số tin gợi ý phải từ 1 đến 12.")));
        }
        List<PublicListing> rows = reads.similar(slugOrId, size);
        var thumbnails = reads.thumbnails(rows);
        Instant now = clock.instant();
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(rows.stream()
                .map(row -> ListingV2Mapper.summary(row, row.thumbnailUrl() == null ? null : thumbnails.get(row.thumbnailUrl()), now))
                .toList());
    }

    static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) return false;
        Set<String> tags = Set.copyOf(Arrays.stream(ifNoneMatch.split(",")).map(String::trim)
                .map(tag -> tag.startsWith("W/") ? tag.substring(2) : tag).toList());
        return tags.contains("*") || tags.contains(etag);
    }
}
