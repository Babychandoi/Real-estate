package com.company.bds.listing.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Hợp đồng DTO trả về tóm tắt thông tin tin đăng BĐS cho danh sách / tìm kiếm.
 * Dùng record bất biến, không lộ JPA entity.
 * Tham khảo: PROJECT_CODE_RULES_BDS.md mục 4.4
 */
public record ListingSummaryResponse(
        UUID id,
        String title,
        String purpose,
        String propertyType,
        long priceVnd,
        BigDecimal areaM2,
        String addressSummary,
        Double publicLatitude,
        Double publicLongitude,
        boolean isVerified,
        boolean isShowcase,
        String primaryImageUrl,
        Instant publishedAt
) {}
