package com.company.bds.listing.domain.model;

import java.util.UUID;

/**
 * Structured attributes of a revision added by the 2026-09 audit (contract §2.1). Rent terms only exist for RENT:
 * {@link #forPurpose} drops them for SALE so a stored SALE revision never carries a deposit or service fee.
 */
public record ListingAttributes(Long monthlyServiceFeeVnd, Long depositVnd, Furnishing furnishing,
                                LegalStatusCode legalStatusCode, UUID projectId) {
    public static final ListingAttributes EMPTY = new ListingAttributes(null, null, null, null, null);

    public ListingAttributes forPurpose(ListingPurpose purpose) {
        if (purpose == ListingPurpose.RENT) return this;
        return new ListingAttributes(null, null, furnishing, legalStatusCode, projectId);
    }
}
