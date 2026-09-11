package com.company.bds.verification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root quản lý Hồ sơ Thẩm định Pháp lý & Gắn Nhãn Tin Chính Chủ (FR03, UC03).
 */
public class ListingVerification {

    private final UUID id;
    private final UUID listingId;
    private final UUID userKycId;
    private final VerificationType verificationType;
    private final String certificateNumber;
    private final String documentUrls;
    private final String ownerNameOnDoc;
    private VerificationStatus status;
    private String verifierNote;
    private final Instant createdAt;
    private Instant verifiedAt;

    public ListingVerification(
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
            Instant verifiedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.listingId = Objects.requireNonNull(listingId, "listingId không được để trống");
        this.userKycId = userKycId;
        this.verificationType = verificationType != null ? verificationType : VerificationType.CERTIFICATE_OF_OWNERSHIP;
        this.certificateNumber = certificateNumber;
        this.documentUrls = documentUrls;
        this.ownerNameOnDoc = Objects.requireNonNull(ownerNameOnDoc, "ownerNameOnDoc không được để trống");
        this.status = status != null ? status : VerificationStatus.PENDING;
        this.verifierNote = verifierNote;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.verifiedAt = verifiedAt;
    }

    public static ListingVerification create(
            UUID listingId,
            UUID userKycId,
            VerificationType verificationType,
            String certificateNumber,
            String documentUrls,
            String ownerNameOnDoc,
            Instant now) {
        return new ListingVerification(
                UUID.randomUUID(),
                listingId,
                userKycId,
                verificationType,
                certificateNumber,
                documentUrls,
                ownerNameOnDoc.trim(),
                VerificationStatus.PENDING,
                null,
                now,
                null
        );
    }

    public void approve(String note, Instant now) {
        this.status = VerificationStatus.VERIFIED_OWNER;
        this.verifierNote = note;
        this.verifiedAt = now;
    }

    public void reject(String reason, Instant now) {
        this.status = VerificationStatus.REJECTED;
        this.verifierNote = reason;
        this.verifiedAt = now;
    }

    public void revoke(String reason, Instant now) {
        this.status = VerificationStatus.REVOKED;
        this.verifierNote = reason;
        this.verifiedAt = now;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public UUID getUserKycId() { return userKycId; }
    public VerificationType getVerificationType() { return verificationType; }
    public String getCertificateNumber() { return certificateNumber; }
    public String getDocumentUrls() { return documentUrls; }
    public String getOwnerNameOnDoc() { return ownerNameOnDoc; }
    public VerificationStatus getStatus() { return status; }
    public String getVerifierNote() { return verifierNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
}
