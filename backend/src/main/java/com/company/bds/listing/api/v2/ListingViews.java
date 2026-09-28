package com.company.bds.listing.api.v2;

import com.company.bds.listing.application.service.ListingQualityService;
import com.company.bds.listing.domain.model.ListingAttributes;
import com.company.bds.listing.domain.model.ListingPurpose;
import com.company.bds.media.ImageDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Response shapes of the owner API v2 (contract §4 money/attributes). No JPA entity leaves the module. */
public final class ListingViews {
    private ListingViews() {}

    public record Money(long amount, String currency, String period) {
        public static Money of(long amount, ListingPurpose purpose) {
            return new Money(amount, "VND", purpose == ListingPurpose.RENT ? "MONTH" : null);
        }
    }

    public record UnitPrice(long amount, String per) {
        public static UnitPrice of(long amount, BigDecimal area, ListingPurpose purpose) {
            if (purpose != ListingPurpose.SALE || area == null || area.signum() <= 0 || amount <= 0) return null;
            return new UnitPrice(BigDecimal.valueOf(amount).divide(area, 0, RoundingMode.HALF_UP).longValueExact(), "M2");
        }
    }

    public record RentTerms(Long monthlyServiceFee, Long deposit) {
        public static RentTerms of(ListingAttributes a, ListingPurpose purpose) {
            return purpose == ListingPurpose.RENT ? new RentTerms(a.monthlyServiceFeeVnd(), a.depositVnd()) : null;
        }
    }

    public record Legal(String code, String label, String detail) {
        public static Legal of(ListingAttributes a, String detail) {
            if (a.legalStatusCode() == null && (detail == null || detail.isBlank())) return null;
            return new Legal(a.legalStatusCode() == null ? null : a.legalStatusCode().name(),
                    a.legalStatusCode() == null ? null : a.legalStatusCode().label(), detail);
        }
    }

    public record VersionSummary(UUID revisionId, int revisionNumber, String status, String title, String purpose,
                                 String propertyType, Money price, BigDecimal areaM2, Instant submittedAt,
                                 Instant moderatedAt, String rejectionReason) {}

    public record Freshness(Instant availabilityConfirmedAt, Instant expiresAt, Long daysUntilExpiry,
                            boolean expiringSoon, Instant soldCheckDueAt, boolean renewable) {}

    public record MyListingItem(UUID id, String slug, String status, String source, long version, Instant createdAt,
                                Instant updatedAt, String thumbnailUrl, long leadCount, VersionSummary publicVersion,
                                VersionSummary pendingEdit, Freshness freshness, ListingQualityService.Report quality) {}

    public record MyListingsPage(List<MyListingItem> items, int page, int size, long total, int totalPages,
                                 Map<String, Long> counts) {}

    /** Editable draft of the owner (latest revision) with the concurrency token. */
    public record DraftView(UUID listingId, String slug, String listingStatus, long version, UUID revisionId,
                            int revisionNumber, String revisionStatus, String rejectionReason, String title, String purpose,
                            String propertyType, long priceVnd, BigDecimal areaM2, Integer bedrooms, Integer bathrooms,
                            Integer floors, BigDecimal frontageM, BigDecimal roadWidthM, String direction,
                            String legalStatus, String legalStatusCode, String furnishing, Long monthlyServiceFeeVnd,
                            Long depositVnd, UUID projectId, String description, String provinceCode, String districtCode,
                            String wardCode, String addressSummary, Double publicLatitude, Double publicLongitude,
                            List<String> imageUrls, boolean hasPublicVersion, ListingQualityService.Report quality) {}

    public record Location(String provinceCode, String districtCode, String wardCode, String addressSummary, Double lat,
                           Double lng, String precision) {}

    /** Same field names as the public detail v2 (contract §8) so the preview renders like the published page. */
    public record PreviewView(UUID id, String slug, String previewOf, String title, String purpose, String propertyType,
                              Money price, UnitPrice unitPrice, BigDecimal areaM2, Integer bedrooms, Integer bathrooms,
                              Integer floors, BigDecimal frontageM, BigDecimal roadWidthM, String direction, Location location,
                              String description, List<ImageDto> images, RentTerms rentTerms, Legal legal,
                              String furnishing, int revisionNumber, String revisionStatus,
                              ListingQualityService.Report quality) {}
}
