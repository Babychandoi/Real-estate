package com.company.bds.listing.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity biểu diễn bảng listings trong CSDL.
 * Tuân thủ quy ước: Không dùng Lombok @Data, kiểm soát equals/hashCode tránh lazy loading.
 */
@Entity
@Table(name = "listings")
public class ListingJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "public_revision_id")
    private UUID publicRevisionId;

    @Column(name = "slug", nullable = false, length = 180, unique = true)
    private String slug;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "is_verified_owner", nullable = false)
    private boolean isVerifiedOwner = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "listing", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("revisionNumber ASC")
    private List<ListingRevisionJpaEntity> revisions = new ArrayList<>();

    public ListingJpaEntity() {}

    public ListingJpaEntity(UUID id, UUID ownerId, UUID publicRevisionId, String slug, String status, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.publicRevisionId = publicRevisionId;
        this.slug = slug;
        this.status = status;
        this.version = version != null ? version : 0L;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void addRevision(ListingRevisionJpaEntity revision) {
        revisions.add(revision);
        revision.setListing(this);
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public UUID getPublicRevisionId() { return publicRevisionId; }
    public void setPublicRevisionId(UUID publicRevisionId) { this.publicRevisionId = publicRevisionId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isVerifiedOwner() { return isVerifiedOwner; }
    public void setVerifiedOwner(boolean verifiedOwner) { isVerifiedOwner = verifiedOwner; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<ListingRevisionJpaEntity> getRevisions() { return revisions; }
    public void setRevisions(List<ListingRevisionJpaEntity> revisions) { this.revisions = revisions; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ListingJpaEntity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
