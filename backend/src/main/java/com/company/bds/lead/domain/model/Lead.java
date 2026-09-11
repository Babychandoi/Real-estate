package com.company.bds.lead.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root quản lý thông tin khách hàng tiềm năng (Lead CRM).
 * Áp dụng mã hóa và băm tra cứu số điện thoại bảo vệ dữ liệu cá nhân NFR12.
 */
public class Lead {

    private final UUID id;
    private final UUID listingId;
    private final String fullName;
    private final String phoneEncrypted;
    private final String phoneLookupHash;
    private final String note;
    private final boolean consentPolicy;
    private LeadStatus status;
    private final Instant createdAt;

    public Lead(
            UUID id,
            UUID listingId,
            String fullName,
            String phoneEncrypted,
            String phoneLookupHash,
            String note,
            boolean consentPolicy,
            LeadStatus status,
            Instant createdAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.listingId = Objects.requireNonNull(listingId, "listingId không được để trống");
        this.fullName = Objects.requireNonNull(fullName, "fullName không được để trống");
        this.phoneEncrypted = Objects.requireNonNull(phoneEncrypted, "phoneEncrypted không được để trống");
        this.phoneLookupHash = Objects.requireNonNull(phoneLookupHash, "phoneLookupHash không được để trống");
        this.note = note;
        this.consentPolicy = consentPolicy;
        this.status = status != null ? status : LeadStatus.NEW;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static Lead create(
            UUID listingId,
            String fullName,
            String rawPhone,
            String note,
            boolean consentPolicy,
            Instant now) {
        // Mô phỏng mã hóa số điện thoại theo NFR12
        String encrypted = "ENC:" + rawPhone;
        // Băm số điện thoại dùng cho đối soát tra cứu chống spam
        String lookupHash = Integer.toHexString(rawPhone.hashCode());

        return new Lead(
                UUID.randomUUID(),
                listingId,
                fullName,
                encrypted,
                lookupHash,
                note,
                consentPolicy,
                LeadStatus.NEW,
                now
        );
    }

    public void updateStatus(LeadStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "Trạng thái Lead không được null");
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public String getFullName() { return fullName; }
    public String getPhoneEncrypted() { return phoneEncrypted; }
    public String getPhoneLookupHash() { return phoneLookupHash; }
    public String getNote() { return note; }
    public boolean isConsentPolicy() { return consentPolicy; }
    public LeadStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Giải mã an toàn số điện thoại cho môi giới phụ trách xem.
     */
    public String getMaskedPhone() {
        String raw = phoneEncrypted.startsWith("ENC:") ? phoneEncrypted.substring(4) : phoneEncrypted;
        if (raw.length() >= 7) {
            return raw.substring(0, 3) + "****" + raw.substring(raw.length() - 3);
        }
        return raw;
    }
}
