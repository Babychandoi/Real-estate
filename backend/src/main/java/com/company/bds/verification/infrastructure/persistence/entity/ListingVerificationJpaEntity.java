package com.company.bds.verification.infrastructure.persistence.entity;

import com.company.bds.verification.domain.model.VerificationStatus;
import com.company.bds.verification.domain.model.VerificationType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listing_verifications")
public class ListingVerificationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "user_kyc_id")
    private UUID userKycId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false, length = 50)
    private VerificationType verificationType;

    @Column(name = "certificate_number", length = 100)
    private String certificateNumber;

    @Column(name = "document_urls", columnDefinition = "TEXT")
    private String documentUrls;

    @Column(name = "owner_name_on_doc", nullable = false, length = 150)
    private String ownerNameOnDoc;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(name = "verifier_note", columnDefinition = "TEXT")
    private String verifierNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public ListingVerificationJpaEntity() {}

    public ListingVerificationJpaEntity(
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
        this.id = id;
        this.listingId = listingId;
        this.userKycId = userKycId;
        this.verificationType = verificationType;
        this.certificateNumber = certificateNumber;
        this.documentUrls = documentUrls;
        this.ownerNameOnDoc = ownerNameOnDoc;
        this.status = status != null ? status : VerificationStatus.PENDING;
        this.verifierNote = verifierNote;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.verifiedAt = verifiedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }
    public UUID getUserKycId() { return userKycId; }
    public void setUserKycId(UUID userKycId) { this.userKycId = userKycId; }
    public VerificationType getVerificationType() { return verificationType; }
    public void setVerificationType(VerificationType verificationType) { this.verificationType = verificationType; }
    public String getCertificateNumber() { return certificateNumber; }
    public void setCertificateNumber(String certificateNumber) { this.certificateNumber = certificateNumber; }
    public String getDocumentUrls() { return documentUrls; }
    public void setDocumentUrls(String documentUrls) { this.documentUrls = documentUrls; }
    public String getOwnerNameOnDoc() { return ownerNameOnDoc; }
    public void setOwnerNameOnDoc(String ownerNameOnDoc) { this.ownerNameOnDoc = ownerNameOnDoc; }
    public VerificationStatus getStatus() { return status; }
    public void setStatus(VerificationStatus status) { this.status = status; }
    public String getVerifierNote() { return verifierNote; }
    public void setVerifierNote(String verifierNote) { this.verifierNote = verifierNote; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
}
