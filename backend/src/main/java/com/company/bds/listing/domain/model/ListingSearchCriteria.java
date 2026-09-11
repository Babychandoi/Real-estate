package com.company.bds.listing.domain.model;

import java.math.BigDecimal;

/**
 * Tiêu chí tìm kiếm tin đăng BĐS kết hợp lọc thuộc tính và Bounding Box GIS.
 */
public record ListingSearchCriteria(
        ListingPurpose purpose,
        PropertyType propertyType,
        Long minPriceVnd,
        Long maxPriceVnd,
        BigDecimal minAreaM2,
        BigDecimal maxAreaM2,
        String keyword,
        Double minLat,
        Double maxLat,
        Double minLng,
        Double maxLng,
        String sortBy
) {
    public static ListingSearchCriteria of(
            String purpose,
            String propertyType,
            Long minPrice,
            Long maxPrice,
            BigDecimal minArea,
            BigDecimal maxArea,
            String keyword,
            Double minLat,
            Double maxLat,
            Double minLng,
            Double maxLng,
            String sortBy
    ) {
        ListingPurpose p = null;
        if (purpose != null && !purpose.isBlank()) {
            try {
                p = ListingPurpose.valueOf(purpose.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        PropertyType pt = null;
        if (propertyType != null && !propertyType.isBlank()) {
            try {
                pt = PropertyType.valueOf(propertyType.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        return new ListingSearchCriteria(
                p,
                pt,
                minPrice,
                maxPrice,
                minArea,
                maxArea,
                keyword != null && !keyword.isBlank() ? keyword.trim() : null,
                minLat,
                maxLat,
                minLng,
                maxLng,
                sortBy != null ? sortBy : "LATEST"
        );
    }
}
