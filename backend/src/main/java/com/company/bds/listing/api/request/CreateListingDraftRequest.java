package com.company.bds.listing.api.request;

import com.company.bds.listing.domain.model.ListingPurpose;
import com.company.bds.listing.domain.model.PropertyType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO yêu cầu tạo tin đăng nháp mới.
 */
public record CreateListingDraftRequest(
        @NotNull(message = "{validation.listing.purpose.required}")
        ListingPurpose purpose,

        @NotNull(message = "Loại hình bất động sản không được để trống")
        PropertyType propertyType,

        @NotBlank(message = "{validation.listing.title.required}")
        @Size(min = 10, max = 200, message = "{validation.listing.title.size}")
        String title,

        @NotNull(message = "{validation.listing.price.required}")
        @Min(value = 0, message = "{validation.listing.price.min}")
        Long priceVnd,

        @NotNull(message = "{validation.listing.area.required}")
        @DecimalMin(value = "1.0", message = "{validation.listing.area.min}")
        BigDecimal areaM2,

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
