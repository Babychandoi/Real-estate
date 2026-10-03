package com.company.bds.listing.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ListingDetailResponse(
        UUID id,
        String slug,
        UUID ownerId,
        String status,
        int revisionNumber,
        String revisionStatus,
        String title,
        String purpose,
        String propertyType,
        long priceVnd,
        /** {@code MONTH} for a rent listing (the price is per month), {@code null} for a sale (contract §2.2). */
        String pricePeriod,
        BigDecimal areaM2,
        Integer bedrooms,
        Integer bathrooms,
        Integer floors,
        BigDecimal frontageM,
        BigDecimal roadWidthM,
        String direction,
        String legalStatus,
        String description,
        String provinceCode,
        String districtCode,
        String wardCode,
        String addressSummary,
        Double publicLatitude,
        Double publicLongitude,
        boolean isVerified,
        boolean isShowcase,
        List<String> imageUrls,
        Instant createdAt,
        Instant updatedAt
) {}
