package com.company.bds.lead.infrastructure.persistence.entity;

import com.company.bds.lead.domain.model.LeadStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity biểu diễn bảng leads (Hộp tiếp nhận Lead CRM).
 */
@Entity
@Table(name = "leads")
public class LeadJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "phone_encrypted", nullable = false, columnDefinition = "TEXT")
    private String phoneEncrypted;

    @Column(name = "phone_lookup_hash", nullable = false, length = 64)
    private String phoneLookupHash;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "consent_policy", nullable = false)
    private boolean consentPolicy = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LeadStatus status = LeadStatus.NEW;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LeadJpaEntity() {}

    public LeadJpaEntity(
            UUID id,
            UUID listingId,
            String fullName,
            String phoneEncrypted,
            String phoneLookupHash,
            String note,
            boolean consentPolicy,
            LeadStatus status,
            Instant createdAt) {
        this.id = id;
        this.listingId = listingId;
        this.fullName = fullName;
        this.phoneEncrypted = phoneEncrypted;
        this.phoneLookupHash = phoneLookupHash;
        this.note = note;
        this.consentPolicy = consentPolicy;
        this.status = status != null ? status : LeadStatus.NEW;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhoneEncrypted() { return phoneEncrypted; }
    public void setPhoneEncrypted(String phoneEncrypted) { this.phoneEncrypted = phoneEncrypted; }
    public String getPhoneLookupHash() { return phoneLookupHash; }
    public void setPhoneLookupHash(String phoneLookupHash) { this.phoneLookupHash = phoneLookupHash; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isConsentPolicy() { return consentPolicy; }
    public void setConsentPolicy(boolean consentPolicy) { this.consentPolicy = consentPolicy; }
    public LeadStatus getStatus() { return status; }
    public void setStatus(LeadStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
