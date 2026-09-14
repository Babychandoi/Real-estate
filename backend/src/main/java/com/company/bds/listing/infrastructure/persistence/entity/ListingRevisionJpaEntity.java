package com.company.bds.listing.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity biểu diễn bảng listing_revisions.
 */
@Entity
@Table(name = "listing_revisions")
public class ListingRevisionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private ListingJpaEntity listing;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "purpose", nullable = false, length = 20)
    private String purpose;

    @Column(name = "property_type", nullable = false, length = 30)
    private String propertyType;

    @Column(name = "price_vnd", nullable = false)
    private long priceVnd;

    @Column(name = "area_m2", nullable = false, precision = 10, scale = 2)
    private BigDecimal areaM2;

    @Column(name = "bedrooms")
    private Integer bedrooms;

    @Column(name = "bathrooms")
    private Integer bathrooms;

    @Column(name = "floors")
    private Integer floors;

    @Column(name = "frontage_m", precision = 10, scale = 2)
    private BigDecimal frontageM;

    @Column(name = "road_width_m", precision = 10, scale = 2)
    private BigDecimal roadWidthM;

    @Column(name = "direction", length = 30)
    private String direction;

    @Column(name = "legal_status", length = 100)
    private String legalStatus;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "province_code", length = 50)
    private String provinceCode;

    @Column(name = "district_code", length = 50)
    private String districtCode;

    @Column(name = "ward_code", length = 50)
    private String wardCode;

    @Column(name = "address_summary", length = 255)
    private String addressSummary;

    @Column(name = "public_latitude")
    private Double publicLatitude;

    @Column(name = "public_longitude")
    private Double publicLongitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "moderation_note", columnDefinition = "TEXT")
    private String moderationNote;

    @OneToMany(mappedBy = "revision", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<ListingMediaJpaEntity> mediaList = new ArrayList<>();

    public ListingRevisionJpaEntity() {}

    public void addMedia(ListingMediaJpaEntity media) {
        mediaList.add(media);
        media.setRevision(this);
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ListingJpaEntity getListing() { return listing; }
    public void setListing(ListingJpaEntity listing) { this.listing = listing; }
    public int getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(int revisionNumber) { this.revisionNumber = revisionNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
    public long getPriceVnd() { return priceVnd; }
    public void setPriceVnd(long priceVnd) { this.priceVnd = priceVnd; }
    public BigDecimal getAreaM2() { return areaM2; }
    public void setAreaM2(BigDecimal areaM2) { this.areaM2 = areaM2; }
    public Integer getBedrooms() { return bedrooms; }
    public void setBedrooms(Integer bedrooms) { this.bedrooms = bedrooms; }
    public Integer getBathrooms() { return bathrooms; }
    public void setBathrooms(Integer bathrooms) { this.bathrooms = bathrooms; }
    public Integer getFloors() { return floors; }
    public void setFloors(Integer floors) { this.floors = floors; }
    public BigDecimal getFrontageM() { return frontageM; }
    public void setFrontageM(BigDecimal frontageM) { this.frontageM = frontageM; }
    public BigDecimal getRoadWidthM() { return roadWidthM; }
    public void setRoadWidthM(BigDecimal roadWidthM) { this.roadWidthM = roadWidthM; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getLegalStatus() { return legalStatus; }
    public void setLegalStatus(String legalStatus) { this.legalStatus = legalStatus; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }
    public String getDistrictCode() { return districtCode; }
    public void setDistrictCode(String districtCode) { this.districtCode = districtCode; }
    public String getWardCode() { return wardCode; }
    public void setWardCode(String wardCode) { this.wardCode = wardCode; }
    public String getAddressSummary() { return addressSummary; }
    public void setAddressSummary(String addressSummary) { this.addressSummary = addressSummary; }
    public Double getPublicLatitude() { return publicLatitude; }
    public void setPublicLatitude(Double publicLatitude) { this.publicLatitude = publicLatitude; }
    public Double getPublicLongitude() { return publicLongitude; }
    public void setPublicLongitude(Double publicLongitude) { this.publicLongitude = publicLongitude; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getModeratedAt() { return moderatedAt; }
    public void setModeratedAt(Instant moderatedAt) { this.moderatedAt = moderatedAt; }
    public String getModerationNote() { return moderationNote; }
    public void setModerationNote(String moderationNote) { this.moderationNote = moderationNote; }
    public List<ListingMediaJpaEntity> getMediaList() { return mediaList; }
    public void setMediaList(List<ListingMediaJpaEntity> mediaList) { this.mediaList = mediaList; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ListingRevisionJpaEntity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
