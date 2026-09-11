package com.company.bds.listing.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ListingDetailResponse(
        UUID id,
        UUID ownerId,
        String status,
        int revisionNumber,
        String revisionStatus,
        String title,
        String purpose,
        String propertyType,
        long priceVnd,
        BigDecimal areaM2,
        String description,
        String provinceCode,
        String districtCode,
        String wardCode,
        String addressSummary,
        Double publicLatitude,
        Double publicLongitude,
        boolean isVerified,
        List<String> imageUrls,
        Instant createdAt,
        Instant updatedAt
) {}
