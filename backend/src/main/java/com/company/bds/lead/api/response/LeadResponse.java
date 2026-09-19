package com.company.bds.lead.api.response;

import com.company.bds.lead.domain.model.Lead;
import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.lead.domain.model.LeadRequestType;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.listing.domain.model.ListingRevision;

import java.time.Instant;
import java.util.UUID;

public record LeadResponse(
        UUID id,
        UUID listingId,
        String fullName,
        String maskedPhone,
        LeadRequestType requestType,
        String note,
        boolean consentPolicy,
        LeadStatus status,
        Instant createdAt,
        String listingTitle,
        String listingSlug,
        String listingAddress,
        String listingImageUrl
) {
    public static LeadResponse fromDomain(Lead lead, Listing listing) {
        ListingRevision revision = listing == null ? null : listing.getPublicRevision().orElseGet(() -> listing.getLatestRevision().orElse(null));
        String imageUrl = revision == null ? null : revision.getMediaList().stream()
                .sorted(java.util.Comparator.comparingInt(media -> media.isPrimary() ? -1 : media.sortOrder()))
                .map(media -> media.mediaUrl()).findFirst().orElse(null);
        return new LeadResponse(
                lead.getId(),
                lead.getListingId(),
                lead.getFullName(),
                lead.getMaskedPhone(),
                lead.getRequestType(),
                lead.getNote(),
                lead.isConsentPolicy(),
                lead.getStatus(),
                lead.getCreatedAt(),
                revision == null ? "Tin đăng" : revision.getTitle(),
                listing == null ? null : listing.getSlug(),
                revision == null ? null : revision.getAddressSummary(),
                imageUrl
        );
    }
}
