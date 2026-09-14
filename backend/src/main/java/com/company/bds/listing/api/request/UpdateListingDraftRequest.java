package com.company.bds.listing.api.request;

import com.company.bds.listing.domain.model.ListingPurpose;
import com.company.bds.listing.domain.model.PropertyType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO yêu cầu cập nhật bản nháp tin đăng.
 */
public record UpdateListingDraftRequest(
        ListingPurpose purpose,
        PropertyType propertyType,

        @Size(min = 10, max = 200, message = "{validation.listing.title.size}")
        String title,

        @Min(value = 0, message = "{validation.listing.price.min}")
        Long priceVnd,

        @DecimalMin(value = "1.0", message = "{validation.listing.area.min}")
        BigDecimal areaM2,

        @Min(0) Integer bedrooms,
        @Min(0) Integer bathrooms,
        @Min(0) Integer floors,
        @DecimalMin("0.0") BigDecimal frontageM,
        @DecimalMin("0.0") BigDecimal roadWidthM,
        @Size(max = 30) String direction,
        @Size(max = 100) String legalStatus,

        @Size(max = 5000, message = "{validation.listing.description.size}")
        String description,

        String provinceCode,
        String districtCode,
        String wardCode,
        String addressSummary,
        Double publicLatitude,
        Double publicLongitude,
        List<String> imageUrls
) {}
