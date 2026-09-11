package com.company.bds.verification.infrastructure.persistence.entity;

import com.company.bds.verification.domain.model.KycStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_kyc_profiles")
public class UserKycJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "id_number_encrypted", nullable = false, columnDefinition = "TEXT")
    private String idNumberEncrypted;

    @Column(name = "id_number_lookup_hash", nullable = false, length = 64)
    private String idNumberLookupHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "dob", length = 20)
    private String dob;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "id_card_front_url", columnDefinition = "TEXT")
    private String idCardFrontUrl;

    @Column(name = "id_card_back_url", columnDefinition = "TEXT")
    private String idCardBackUrl;

    @Column(name = "selfie_url", columnDefinition = "TEXT")
    private String selfieUrl;

    @Column(name = "face_match_score")
    private Double faceMatchScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private KycStatus status = KycStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public UserKycJpaEntity() {}

    public UserKycJpaEntity(
            UUID id,
            UUID userId,
            String idNumberEncrypted,
            String idNumberLookupHash,
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
            Instant verifiedAt) {
        this.id = id;
        this.userId = userId;
        this.idNumberEncrypted = idNumberEncrypted;
        this.idNumberLookupHash = idNumberLookupHash;
        this.fullName = fullName;
        this.dob = dob;
        this.address = address;
        this.idCardFrontUrl = idCardFrontUrl;
        this.idCardBackUrl = idCardBackUrl;
        this.selfieUrl = selfieUrl;
        this.faceMatchScore = faceMatchScore;
        this.status = status != null ? status : KycStatus.PENDING;
        this.rejectionReason = rejectionReason;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.verifiedAt = verifiedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getIdNumberEncrypted() { return idNumberEncrypted; }
    public void setIdNumberEncrypted(String idNumberEncrypted) { this.idNumberEncrypted = idNumberEncrypted; }
    public String getIdNumberLookupHash() { return idNumberLookupHash; }
    public void setIdNumberLookupHash(String idNumberLookupHash) { this.idNumberLookupHash = idNumberLookupHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getIdCardFrontUrl() { return idCardFrontUrl; }
    public void setIdCardFrontUrl(String idCardFrontUrl) { this.idCardFrontUrl = idCardFrontUrl; }
    public String getIdCardBackUrl() { return idCardBackUrl; }
    public void setIdCardBackUrl(String idCardBackUrl) { this.idCardBackUrl = idCardBackUrl; }
    public String getSelfieUrl() { return selfieUrl; }
    public void setSelfieUrl(String selfieUrl) { this.selfieUrl = selfieUrl; }
    public Double getFaceMatchScore() { return faceMatchScore; }
    public void setFaceMatchScore(Double faceMatchScore) { this.faceMatchScore = faceMatchScore; }
    public KycStatus getStatus() { return status; }
    public void setStatus(KycStatus status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
}
