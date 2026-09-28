package com.company.bds.search.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of {@code listing_public_read}: the public (APPROVED, ACTIVE, seller ACTIVE) state of a listing. Summary
 * queries leave {@code description} and {@code mediaUrls} empty; detail queries fill them.
 */
public record PublicListing(
        UUID listingId, String slug, UUID ownerId, UUID publicRevisionId, int revisionNumber,
        String title, String description, String descriptionExcerpt,
        String purpose, String propertyType, long priceVnd, String pricePeriod, Long unitPriceVnd, BigDecimal areaM2,
        Integer bedrooms, Integer bathrooms, Integer floors, BigDecimal frontageM, BigDecimal roadWidthM, String direction,
        String legalStatusCode, String legalStatusText, String furnishing, Long monthlyServiceFeeVnd, Long depositVnd,
        String provinceCode, String districtCode, String districtName, String wardCode, String wardName,
        String addressSummary, Double lat, Double lng,
        UUID projectId, String projectSlug, String projectName,
        String thumbnailUrl, int imageCount, List<String> mediaUrls,
        String sellerName, String sellerAvatarUrl, String sellerRole,
        String identityStatus, Instant identityCheckedAt, Instant identityExpiresAt,
        String ownershipStatus, Instant ownershipCheckedAt, Instant ownershipExpiresAt, String ownershipDocumentType,
        Instant listingCheckedAt, Instant publishedAt, Instant updatedAt, Instant availabilityConfirmedAt,
        Long previousPriceVnd, Instant priceChangedAt, String searchText, long rowVersion) {

    public PublicListing {
        mediaUrls = mediaUrls == null ? List.of() : List.copyOf(mediaUrls);
    }

    /** Identity status at {@code now}: a VERIFIED check whose validity passed reads EXPIRED even before the row refresh. */
    public String identityStatusAt(Instant now) {
        return expired(identityStatus, identityExpiresAt, now) ? "EXPIRED" : identityStatus;
    }

    public String ownershipStatusAt(Instant now) {
        return expired(ownershipStatus, ownershipExpiresAt, now) ? "EXPIRED" : ownershipStatus;
    }

    private static boolean expired(String status, Instant expiresAt, Instant now) {
        return "VERIFIED".equals(status) && expiresAt != null && !expiresAt.isAfter(now);
    }
}
