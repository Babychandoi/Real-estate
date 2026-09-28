package com.company.bds.search.api;

import com.company.bds.listing.api.response.ListingSummaryResponse;
import com.company.bds.search.application.ListingSearchService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deprecated v1 search (contract §8): a thin wrapper over v2 that returns the first page only, in the old array shape.
 * Answers carry {@code Deprecation: true} and a {@code Link} to the successor. Invalid values are no longer swallowed
 * (400 {@code INVALID_FILTER}); {@code purpose} defaults to SALE like v2; {@code page > 0} returns an empty array.
 */
@RestController
public class LegacySearchV1Controller {
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
        put(params, "size", Integer.toString(Math.max(1, Math.min(size, SearchFilterParser.MAX_SIZE))));
        if (page < 0) {
            throw new InvalidFilterException(InvalidFilterException.INVALID_FILTER,
                    List.of(new InvalidFilterException.FilterError("page", "Số trang không được âm.")));
        }
        List<ListingSummaryResponse> items = List.of();
        if (page == 0) {
            SearchResults.Page result = search.search(SearchFilterParser.parse(params));
            items = result.items().stream().map(LegacySearchV1Controller::legacy).toList();
        }
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Link", "</api/v2/listings/search>; rel=\"successor-version\"")
                .body(items);
    }

    private static ListingSummaryResponse legacy(PublicListing row) {
        return new ListingSummaryResponse(row.listingId(), row.slug(), ContactInfoGuard.redact(row.title()), row.purpose(),
                row.propertyType(), row.priceVnd(), row.areaM2(), ContactInfoGuard.redact(row.addressSummary()), row.lat(),
                row.lng(), "VERIFIED".equals(row.ownershipStatus()), false, row.thumbnailUrl() == null ? "" : row.thumbnailUrl(),
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
