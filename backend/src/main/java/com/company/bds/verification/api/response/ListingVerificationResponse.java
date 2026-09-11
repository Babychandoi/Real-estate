package com.company.bds.verification.api.response;

import com.company.bds.verification.domain.model.ListingVerification;
import com.company.bds.verification.domain.model.VerificationStatus;
import com.company.bds.verification.domain.model.VerificationType;

import java.time.Instant;
import java.util.UUID;

public record ListingVerificationResponse(
        UUID id,
        UUID listingId,
        UUID userKycId,
        VerificationType verificationType,
        String certificateNumber,
        String documentUrls,
        String ownerNameOnDoc,
        VerificationStatus status,
        String verifierNote,
        Instant createdAt,
        Instant verifiedAt,
        UserKycResponse userKyc
) {
    public static ListingVerificationResponse fromDomain(ListingVerification domain, UserKycResponse userKyc) {
        if (domain == null) return null;
        return new ListingVerificationResponse(
                domain.getId(),
                domain.getListingId(),
                domain.getUserKycId(),
                domain.getVerificationType(),
                domain.getCertificateNumber(),
                domain.getDocumentUrls(),
                domain.getOwnerNameOnDoc(),
                domain.getStatus(),
                domain.getVerifierNote(),
                domain.getCreatedAt(),
                domain.getVerifiedAt(),
                userKyc
        );
    }
}
