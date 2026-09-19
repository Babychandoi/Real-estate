package com.company.bds.lead.infrastructure.persistence.entity;

import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.lead.domain.model.LeadRequestType;
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

    @Column(name = "requester_id")
    private UUID requesterId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "phone_encrypted", nullable = false, columnDefinition = "TEXT")
    private String phoneEncrypted;

    @Column(name = "phone_lookup_hash", nullable = false, length = 64)
    private String phoneLookupHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    private LeadRequestType requestType = LeadRequestType.CONSULTATION;

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
            UUID requesterId,
            String fullName,
            String phoneEncrypted,
            String phoneLookupHash,
            LeadRequestType requestType,
            String note,
            boolean consentPolicy,
            LeadStatus status,
            Instant createdAt) {
        this.id = id;
        this.listingId = listingId;
        this.requesterId = requesterId;
        this.fullName = fullName;
        this.phoneEncrypted = phoneEncrypted;
        this.phoneLookupHash = phoneLookupHash;
        this.requestType = requestType != null ? requestType : LeadRequestType.CONSULTATION;
        this.note = note;
        this.consentPolicy = consentPolicy;
        this.status = status != null ? status : LeadStatus.NEW;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }
    public UUID getRequesterId() { return requesterId; }
    public void setRequesterId(UUID requesterId) { this.requesterId = requesterId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhoneEncrypted() { return phoneEncrypted; }
    public void setPhoneEncrypted(String phoneEncrypted) { this.phoneEncrypted = phoneEncrypted; }
    public String getPhoneLookupHash() { return phoneLookupHash; }
    public void setPhoneLookupHash(String phoneLookupHash) { this.phoneLookupHash = phoneLookupHash; }
    public LeadRequestType getRequestType() { return requestType; }
    public void setRequestType(LeadRequestType requestType) { this.requestType = requestType; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isConsentPolicy() { return consentPolicy; }
    public void setConsentPolicy(boolean consentPolicy) { this.consentPolicy = consentPolicy; }
    public LeadStatus getStatus() { return status; }
    public void setStatus(LeadStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
