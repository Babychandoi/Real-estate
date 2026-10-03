package com.company.bds.search.api;

import com.company.bds.listing.api.response.ListingSummaryResponse;
import com.company.bds.search.application.ListingSearchService;
import com.company.bds.search.application.SearchProblemException;
import com.company.bds.search.application.SearchResults;
import com.company.bds.search.domain.InvalidFilterException;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.domain.SearchFilterParser;
import com.company.bds.shared.security.ContactInfoGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deprecated v1 search (contract §8): a thin wrapper over v2 in the old array shape; page {@code n} is reached by walking
 * the v2 cursors (W6, R-2: before, every page after the first was empty), up to {@value #MAX_DEPTH} results deep (400
 * beyond) and at most {@value #MAX_SEARCHES_PER_REQUEST} v2 searches per request; rate limited like v2 search.
 * Answers carry {@code Deprecation: true} and a {@code Link} to the successor. Invalid values are no longer swallowed
 * (400 {@code INVALID_FILTER}); {@code purpose} defaults to SALE like v2; a page past the last one is an empty array.
 */
@RestController
public class LegacySearchV1Controller {
    /** Deepest result a v1 page may reach: 50 v2 pages of 48 (v2 itself pages without limit by cursor). */
    static final int MAX_DEPTH = 2_400;
    /** Hard bound on the v2 searches one v1 request runs (Elasticsearch pages can come back short after the re-check). */
    static final int MAX_SEARCHES_PER_REQUEST = 50;

    private final ListingSearchService search;

    public LegacySearchV1Controller(ListingSearchService search) {
        this.search = search;
    }

    @GetMapping("/api/v1/listings/search")
    public ResponseEntity<List<ListingSummaryResponse>> search(
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) String propertyType,
            @RequestParam(required = false) String minPrice,
            @RequestParam(required = false) String maxPrice,
            @RequestParam(required = false) String minArea,
            @RequestParam(required = false) String maxArea,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String minLat,
            @RequestParam(required = false) String maxLat,
            @RequestParam(required = false) String minLng,
            @RequestParam(required = false) String maxLng,
            @RequestParam(required = false, defaultValue = "LATEST") String sortBy,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        Map<String, String[]> params = new HashMap<>();
        put(params, "purpose", purpose);
        put(params, "type", propertyType);
        put(params, "priceMin", minPrice);
        put(params, "priceMax", maxPrice);
        put(params, "areaMin", plainDecimal(minArea));
        put(params, "areaMax", plainDecimal(maxArea));
        put(params, "q", keyword);
        if (minLat != null || maxLat != null || minLng != null || maxLng != null) {
            put(params, "bbox", String.join(",", String.valueOf(minLng), String.valueOf(minLat), String.valueOf(maxLng),
                    String.valueOf(maxLat)));
        }
        put(params, "sort", "LATEST".equals(sortBy) ? "NEWEST" : sortBy);
        int pageSize = Math.max(1, Math.min(size, SearchFilterParser.MAX_SIZE));
        if (page < 0) {
            throw new InvalidFilterException(InvalidFilterException.INVALID_FILTER,
                    List.of(new InvalidFilterException.FilterError("page", "Số trang không được âm.")));
        }
        // R-2: v1 pages are numbers, v2 pages are cursors. Page n is reached by walking the cursors with the largest v2
        // page, so one v1 request costs at most MAX_SEARCHES_PER_REQUEST searches: results beyond MAX_DEPTH are refused
        // (400) and the client is pointed at the v2 cursor API, which has no such limit.
        long offset = (long) page * pageSize;
        if (offset + pageSize > MAX_DEPTH) {
            throw new InvalidFilterException(InvalidFilterException.INVALID_FILTER, List.of(new InvalidFilterException.FilterError("page",
                    "API v1 chỉ trả tối đa " + MAX_DEPTH + " kết quả đầu (page × size); dùng /api/v2/listings/search với con trỏ để xem tiếp.")));
        }
        put(params, "size", Integer.toString(SearchFilterParser.MAX_SIZE));
        List<PublicListing> window = walk(params, (int) (offset + pageSize));
        List<ListingSummaryResponse> items = window.size() <= offset ? List.of()
                : window.subList((int) offset, (int) Math.min(window.size(), offset + pageSize)).stream()
                        .map(LegacySearchV1Controller::legacy).toList();
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Link", "</api/v2/listings/search>; rel=\"successor-version\"")
                .body(items);
    }

    /**
     * The first {@code wanted} results in v2 order. A cursor refused because the engine switched mid-walk (Elasticsearch
     * to PostgreSQL fallback or back, 409 CURSOR_ENGINE_CHANGED) restarts the walk once on the new engine: v1 clients
     * cannot handle cursors, so they never see that 409.
     */
    private List<PublicListing> walk(Map<String, String[]> params, int wanted) {
        for (int attempt = 0; ; attempt++) {
            params.remove("cursor");
            List<PublicListing> rows = new ArrayList<>();
            try {
                SearchResults.Page result = search.search(SearchFilterParser.parse(params));
                rows.addAll(result.items());
                int searches = 1;
                while (rows.size() < wanted && result.hasNext() && result.nextCursor() != null && searches < MAX_SEARCHES_PER_REQUEST) {
                    put(params, "cursor", result.nextCursor());
                    result = search.search(SearchFilterParser.parse(params));
                    rows.addAll(result.items());
                    searches++;
                }
                return rows;
            } catch (SearchProblemException ex) {
                if (!"CURSOR_ENGINE_CHANGED".equals(ex.code()) || attempt >= 1) throw ex;
            }
        }
    }

    private static ListingSummaryResponse legacy(PublicListing row) {
        return new ListingSummaryResponse(row.listingId(), row.slug(), ContactInfoGuard.redact(row.title()), row.purpose(),
                row.propertyType(), row.priceVnd(), row.pricePeriod(), row.areaM2(), ContactInfoGuard.redact(row.addressSummary()), row.lat(),
                // Validity now (contract §6), not the stored status: an expired check is not a badge even before the row refresh.
                row.lng(), "VERIFIED".equals(row.ownershipStatusAt(Instant.now())), false, row.thumbnailUrl() == null ? "" : row.thumbnailUrl(),
                row.publishedAt(), row.ownerId(), ContactInfoGuard.redact(row.sellerName()), row.sellerAvatarUrl());
    }

    private static void put(Map<String, String[]> params, String name, String value) {
        if (value != null && !value.isBlank()) params.put(name, new String[]{value});
    }

    /** v1 accepted any decimal (e.g. 65.0000); v2 wants at most two decimals. */
    private static String plainDecimal(String value) {
        if (value == null || value.isBlank()) return value;
        try {
            BigDecimal decimal = new BigDecimal(value.trim()).stripTrailingZeros();
            return decimal.scale() < 0 ? decimal.setScale(0).toPlainString() : decimal.toPlainString();
        } catch (NumberFormatException ex) {
            return value;
        }
    }
}
