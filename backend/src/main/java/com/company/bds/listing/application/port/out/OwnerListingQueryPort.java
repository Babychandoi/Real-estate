package com.company.bds.listing.application.port.out;

import com.company.bds.listing.domain.model.LegalStatusCode;
import com.company.bds.listing.domain.model.ListingPurpose;
import com.company.bds.listing.domain.model.ListingStatus;
import com.company.bds.listing.domain.model.PropertyType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read side of "Tin đăng của tôi": one bounded page query with only the columns the page needs (F08.1, F08.6). */
public interface OwnerListingQueryPort {

    record RevisionSummary(UUID id, int revisionNumber, String status, String title, ListingPurpose purpose,
                           PropertyType propertyType, long priceVnd, BigDecimal areaM2, Instant createdAt,
                           Instant submittedAt, Instant moderatedAt, String moderationNote) {}

    /** Inputs of the quality checklist taken from the working (latest) revision. */
    record WorkingFacts(int imageCount, String description, String districtCode, Double latitude, Double longitude,
                        LegalStatusCode legalStatusCode, Long depositVnd, Long monthlyServiceFeeVnd) {}

    record Row(UUID id, String slug, ListingStatus status, String source, long version, Instant createdAt, Instant updatedAt,
               Instant availabilityConfirmedAt, Instant expiresAt, Instant soldCheckDueAt, String thumbnailUrl,
               long leadCount, RevisionSummary publicRevision, RevisionSummary latestRevision, WorkingFacts working) {}

    record Page(List<Row> rows, long total, Map<ListingStatus, Long> countsByStatus) {}

    Page findPage(UUID ownerId, ListingStatus status, int page, int size);
}
