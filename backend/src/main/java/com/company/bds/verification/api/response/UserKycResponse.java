package com.company.bds.verification.api.response;

import com.company.bds.verification.domain.model.KycStatus;
import com.company.bds.verification.domain.model.UserKycProfile;

import java.time.Instant;
import java.util.UUID;

public record UserKycResponse(
        UUID id,
        UUID userId,
        String maskedIdNumber,
        String fullName,
        String dob,
        String address,
        String idCardFrontUrl,
        String idCardBackUrl,
        String selfieUrl,
        Double faceMatchScore,
        KycStatus status,
        String rejectionReason,
        Instant createdAt,
        Instant verifiedAt
) {
    public static UserKycResponse fromDomain(UserKycProfile domain) {
        if (domain == null) return null;
        return new UserKycResponse(
                domain.getId(),
                domain.getUserId(),
                domain.getMaskedIdNumber(),
                domain.getFullName(),
                domain.getDob(),
                domain.getAddress(),
                null,
                null,
                null,
                domain.getFaceMatchScore(),
                domain.getStatus(),
                domain.getRejectionReason(),
                domain.getCreatedAt(),
                domain.getVerifiedAt()
        );
    }

    public static UserKycResponse fromDomainForReviewer(UserKycProfile domain) {
        if (domain == null) return null;
        return new UserKycResponse(
                domain.getId(), domain.getUserId(), domain.getMaskedIdNumber(), domain.getFullName(),
                domain.getDob(), domain.getAddress(), domain.getIdCardFrontUrl(), domain.getIdCardBackUrl(),
                domain.getSelfieUrl(), domain.getFaceMatchScore(), domain.getStatus(),
                domain.getRejectionReason(), domain.getCreatedAt(), domain.getVerifiedAt());
    }
}
