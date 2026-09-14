package com.company.bds.listing.application.command;

import com.company.bds.listing.domain.model.ListingPurpose;
import com.company.bds.listing.domain.model.PropertyType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateListingDraftCommand(
        UUID ownerId,
        String title,
        ListingPurpose purpose,
        PropertyType propertyType,
        long priceVnd,
        BigDecimal areaM2,
        Integer bedrooms, Integer bathrooms, Integer floors,
        BigDecimal frontageM, BigDecimal roadWidthM, String direction, String legalStatus,
        String description,
        String provinceCode,
        String districtCode,
        String wardCode,
        String addressSummary,
        Double publicLatitude,
        Double publicLongitude,
        List<String> imageUrls
) {}
