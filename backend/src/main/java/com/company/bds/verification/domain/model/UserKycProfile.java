package com.company.bds.verification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root quản lý Hồ sơ định danh eKYC người dùng / chủ sở hữu (FR01, NFR12).
 */
public class UserKycProfile {

    private final UUID id;
    private final UUID userId;
    private final String idNumberEncrypted;
    private final String idNumberLookupHash;
    private final String fullName;
    private final String dob;
    private final String address;
    private final String idCardFrontUrl;
    private final String idCardBackUrl;
    private final String selfieUrl;
    private final Double faceMatchScore;
    private KycStatus status;
    private String rejectionReason;
    private final Instant createdAt;
    private Instant verifiedAt;

    public UserKycProfile(
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
        this.id = id != null ? id : UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId không được để trống");
        this.idNumberEncrypted = Objects.requireNonNull(idNumberEncrypted, "idNumberEncrypted không được để trống");
        this.idNumberLookupHash = Objects.requireNonNull(idNumberLookupHash, "idNumberLookupHash không được để trống");
        this.fullName = Objects.requireNonNull(fullName, "fullName không được để trống");
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

    public static UserKycProfile create(
            UUID userId,
            String rawIdNumber,
            String fullName,
            String dob,
            String address,
            String idCardFrontUrl,
            String idCardBackUrl,
            String selfieUrl,
            Double faceMatchScore,
            Instant now) {

        String encrypted = "ENC_ID:" + rawIdNumber;
        String lookupHash = Integer.toHexString(rawIdNumber.hashCode());

        return new UserKycProfile(
                UUID.randomUUID(),
                userId,
                encrypted,
                lookupHash,
                fullName.trim(),
                dob,
                address,
                idCardFrontUrl,
                idCardBackUrl,
                selfieUrl,
                faceMatchScore,
                KycStatus.PENDING,
                null,
                now,
                null
        );
    }

    public void approve(Instant now) {
        this.status = KycStatus.VERIFIED;
        this.verifiedAt = now;
        this.rejectionReason = null;
    }

    public void reject(String reason, Instant now) {
        this.status = KycStatus.REJECTED;
        this.rejectionReason = reason;
        this.verifiedAt = now;
    }

    /**
     * Làm mờ an toàn số CCCD theo tiêu chuẩn bảo vệ dữ liệu cá nhân NFR12.
     * Ví dụ: 001201014567 -> 001****4567
     */
    public String getMaskedIdNumber() {
        String raw = idNumberEncrypted.startsWith("ENC_ID:") ? idNumberEncrypted.substring(7) : idNumberEncrypted;
        if (raw.length() >= 8) {
            return raw.substring(0, 3) + "****" + raw.substring(raw.length() - 4);
        }
        return raw;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getIdNumberEncrypted() { return idNumberEncrypted; }
    public String getIdNumberLookupHash() { return idNumberLookupHash; }
    public String getFullName() { return fullName; }
    public String getDob() { return dob; }
    public String getAddress() { return address; }
    public String getIdCardFrontUrl() { return idCardFrontUrl; }
    public String getIdCardBackUrl() { return idCardBackUrl; }
    public String getSelfieUrl() { return selfieUrl; }
    public Double getFaceMatchScore() { return faceMatchScore; }
    public KycStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
}
