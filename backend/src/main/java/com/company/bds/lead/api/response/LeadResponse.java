package com.company.bds.lead.api.response;

import com.company.bds.lead.domain.model.Lead;
import com.company.bds.lead.domain.model.LeadStatus;

import java.time.Instant;
import java.util.UUID;

public record LeadResponse(
        UUID id,
        UUID listingId,
        String fullName,
        String maskedPhone,
        String note,
        boolean consentPolicy,
        LeadStatus status,
        Instant createdAt
) {
    public static LeadResponse fromDomain(Lead lead) {
        return new LeadResponse(
                lead.getId(),
                lead.getListingId(),
                lead.getFullName(),
                lead.getMaskedPhone(),
                lead.getNote(),
                lead.isConsentPolicy(),
                lead.getStatus(),
                lead.getCreatedAt()
        );
    }
}
