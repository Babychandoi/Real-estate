package com.company.bds.verification.api.response;

import com.company.bds.verification.application.TrustDecisionService;
import com.company.bds.verification.domain.model.ListingVerification;
import com.company.bds.verification.domain.model.VerificationStatus;
import com.company.bds.verification.domain.model.VerificationType;

import java.time.Instant;
import java.util.UUID;

/** Ownership check; {@code listingTitle}/{@code listingAddress} and validity/decider fields come from the trust columns. */
public record ListingVerificationResponse(
        UUID id,
        UUID listingId,
        String listingTitle,
        String listingAddress,
        UUID userKycId,
        VerificationType verificationType,
        String certificateNumber,
        String documentUrls,
        String ownerNameOnDoc,
        VerificationStatus status,
        String verifierNote,
        Instant createdAt,
        Instant verifiedAt,
        Instant expiresAt,
        Instant revokedAt,
        String decidedByName,
        String decisionReasonCode,
        UserKycResponse userKyc
) {
    public static ListingVerificationResponse fromDomain(ListingVerification domain, UserKycResponse userKyc) {
        return fromDomain(domain, userKyc, null);
    }

    public static ListingVerificationResponse fromDomain(ListingVerification domain, UserKycResponse userKyc,
                                                         TrustDecisionService.OwnershipExtras extras) {
        if (domain == null) return null;
        return new ListingVerificationResponse(
                domain.getId(),
                domain.getListingId(),
                extras == null ? null : extras.listingTitle(),
                extras == null ? null : extras.listingAddress(),
                domain.getUserKycId(),
                domain.getVerificationType(),
                domain.getCertificateNumber(),
                domain.getDocumentUrls(),
                domain.getOwnerNameOnDoc(),
                domain.getStatus(),
                domain.getVerifierNote(),
                domain.getCreatedAt(),
                domain.getVerifiedAt(),
                extras == null ? null : extras.expiresAt(),
                extras == null ? null : extras.revokedAt(),
                extras == null ? null : extras.decidedByName(),
                extras == null ? null : extras.decisionReasonCode(),
                userKyc
        );
    }
}
