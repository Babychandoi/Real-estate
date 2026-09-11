package com.company.bds.listing.application.command;

import com.company.bds.listing.domain.model.ListingPurpose;
import com.company.bds.listing.domain.model.PropertyType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateListingDraftCommand(
        UUID listingId,
        UUID requesterId,
        String title,
        ListingPurpose purpose,
        PropertyType propertyType,
        long priceVnd,
        BigDecimal areaM2,
        String description,
        String provinceCode,
        String districtCode,
        String wardCode,
        String addressSummary,
        Double publicLatitude,
        Double publicLongitude,
        List<String> imageUrls
) {}
