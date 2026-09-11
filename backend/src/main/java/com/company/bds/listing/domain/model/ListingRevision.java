package com.company.bds.listing.domain.model;

import com.company.bds.listing.domain.exception.ListingDomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Thực thể Revision của tin đăng BĐS.
 * Tuân thủ quy tắc bất biến: Khi status != DRAFT, không được phép sửa đổi trực tiếp.
 */
public class ListingRevision {

    private final UUID id;
    private final UUID listingId;
    private final int revisionNumber;
    private RevisionStatus status;
    private String title;
    private ListingPurpose purpose;
    private PropertyType propertyType;
    private long priceVnd;
    private BigDecimal areaM2;
    private String description;
    private String provinceCode;
    private String districtCode;
    private String wardCode;
    private String addressSummary;
    private Double publicLatitude;
    private Double publicLongitude;
    private Double privateLatitude;
    private Double privateLongitude;
    private final List<ListingMedia> mediaList;
    private final Instant createdAt;
    private Instant submittedAt;
    private Instant moderatedAt;
    private String moderationNote;

    public ListingRevision(
            UUID id,
            UUID listingId,
            int revisionNumber,
            RevisionStatus status,
            String title,
            ListingPurpose purpose,
            PropertyType propertyType,
            long priceVnd,
            BigDecimal areaM2,
            String description,
            String provinceCode,
            String districtCode,
            String wardCode,
            String addressSummary,
            Double publicLatitude,
            Double publicLongitude,
            Double privateLatitude,
            Double privateLongitude,
            List<ListingMedia> mediaList,
            Instant createdAt,
            Instant submittedAt,
            Instant moderatedAt,
            String moderationNote) {
        this.id = id != null ? id : UUID.randomUUID();
        this.listingId = listingId;
        this.revisionNumber = revisionNumber;
        this.status = status != null ? status : RevisionStatus.DRAFT;
        this.title = title;
        this.purpose = purpose;
        this.propertyType = propertyType;
        this.priceVnd = priceVnd;
        this.areaM2 = areaM2;
        this.description = description;
        this.provinceCode = provinceCode;
        this.districtCode = districtCode;
        this.wardCode = wardCode;
        this.addressSummary = addressSummary;
        this.publicLatitude = publicLatitude;
        this.publicLongitude = publicLongitude;
        this.privateLatitude = privateLatitude;
        this.privateLongitude = privateLongitude;
        this.mediaList = mediaList != null ? new ArrayList<>(mediaList) : new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.submittedAt = submittedAt;
        this.moderatedAt = moderatedAt;
        this.moderationNote = moderationNote;
    }

    public static ListingRevision createInitialDraft(
            UUID listingId,
            String title,
            ListingPurpose purpose,
            PropertyType propertyType,
            long priceVnd,
            BigDecimal areaM2,
            String description,
            String provinceCode,
            String districtCode,
            String wardCode,
            String addressSummary,
            Double publicLatitude,
            Double publicLongitude,
            List<ListingMedia> mediaList,
            Instant now) {
        return new ListingRevision(
                UUID.randomUUID(),
                listingId,
                1,
                RevisionStatus.DRAFT,
                title,
                purpose,
                propertyType,
                priceVnd,
                areaM2,
                description,
                provinceCode,
                districtCode,
                wardCode,
                addressSummary,
                publicLatitude,
                publicLongitude,
                publicLatitude,
                publicLongitude,
                mediaList,
                now,
                null,
                null,
                null
        );
    }

    public void updateDraft(
            String title,
            ListingPurpose purpose,
            PropertyType propertyType,
            long priceVnd,
            BigDecimal areaM2,
            String description,
            String provinceCode,
            String districtCode,
            String wardCode,
            String addressSummary,
            Double publicLatitude,
            Double publicLongitude,
            List<ListingMedia> mediaList) {
        if (this.status != RevisionStatus.DRAFT) {
            throw new ListingDomainException(
                    "REVISION_IMMUTABLE",
                    "Không thể sửa đổi trực tiếp revision đã ở trạng thái " + this.status + ". Cần tạo revision nháp mới."
            );
        }
        this.title = title;
        this.purpose = purpose;
        this.propertyType = propertyType;
        this.priceVnd = priceVnd;
        this.areaM2 = areaM2;
        this.description = description;
        this.provinceCode = provinceCode;
        this.districtCode = districtCode;
        this.wardCode = wardCode;
        this.addressSummary = addressSummary;
        if (publicLatitude != null) {
            this.publicLatitude = publicLatitude;
            this.publicLongitude = publicLongitude;
        }
        if (mediaList != null && !mediaList.isEmpty()) {
            this.mediaList.clear();
            this.mediaList.addAll(mediaList);
        }
    }

    public void submit(Instant now) {
        if (this.status != RevisionStatus.DRAFT) {
            throw new ListingDomainException(
                    "INVALID_REVISION_STATE",
                    "Chỉ có thể nộp duyệt revision đang ở trạng thái DRAFT. Trạng thái hiện tại: " + this.status
            );
        }
        if (this.title == null || this.title.trim().length() < 10) {
            throw new ListingDomainException("TITLE_TOO_SHORT", "Tiêu đề tin đăng phải từ 10 ký tự trở lên để nộp duyệt.");
        }
        if (this.priceVnd < 0) {
            throw new ListingDomainException("INVALID_PRICE", "Mức giá không thể là số âm.");
        }
        if (this.areaM2 == null || this.areaM2.compareTo(BigDecimal.valueOf(1.0)) < 0) {
            throw new ListingDomainException("INVALID_AREA", "Diện tích phải từ 1.0 m² trở lên.");
        }

        this.status = RevisionStatus.SUBMITTED;
        this.submittedAt = now;
    }

    public void approve(Instant now) {
        if (this.status != RevisionStatus.SUBMITTED) {
            throw new ListingDomainException(
                    "INVALID_APPROVAL_STATE",
                    "Chỉ có thể phê duyệt revision ở trạng thái SUBMITTED. Trạng thái hiện tại: " + this.status
            );
        }
        this.status = RevisionStatus.APPROVED;
        this.moderatedAt = now;
        this.moderationNote = null;
    }

    public void reject(String reason, Instant now) {
        if (this.status != RevisionStatus.SUBMITTED) {
            throw new ListingDomainException(
                    "INVALID_REJECTION_STATE",
                    "Chỉ có thể từ chối revision ở trạng thái SUBMITTED. Trạng thái hiện tại: " + this.status
            );
        }
        this.status = RevisionStatus.REJECTED;
        this.moderatedAt = now;
        this.moderationNote = reason;
    }

    public ListingRevision createNextDraft(int nextRevisionNumber, Instant now) {
        List<ListingMedia> copiedMedia = new ArrayList<>();
        for (ListingMedia m : this.mediaList) {
            copiedMedia.add(new ListingMedia(UUID.randomUUID(), m.mediaUrl(), m.isPrimary(), m.sortOrder()));
        }
        return new ListingRevision(
                UUID.randomUUID(),
                this.listingId,
                nextRevisionNumber,
                RevisionStatus.DRAFT,
                this.title,
                this.purpose,
                this.propertyType,
                this.priceVnd,
                this.areaM2,
                this.description,
                this.provinceCode,
                this.districtCode,
                this.wardCode,
                this.addressSummary,
                this.publicLatitude,
                this.publicLongitude,
                this.privateLatitude,
                this.privateLongitude,
                copiedMedia,
                now,
                null,
                null,
                null
        );
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public int getRevisionNumber() { return revisionNumber; }
    public RevisionStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public ListingPurpose getPurpose() { return purpose; }
    public PropertyType getPropertyType() { return propertyType; }
    public long getPriceVnd() { return priceVnd; }
    public BigDecimal getAreaM2() { return areaM2; }
    public String getDescription() { return description; }
    public String getProvinceCode() { return provinceCode; }
    public String getDistrictCode() { return districtCode; }
    public String getWardCode() { return wardCode; }
    public String getAddressSummary() { return addressSummary; }
    public Double getPublicLatitude() { return publicLatitude; }
    public Double getPublicLongitude() { return publicLongitude; }
    public Double getPrivateLatitude() { return privateLatitude; }
    public Double getPrivateLongitude() { return privateLongitude; }
    public List<ListingMedia> getMediaList() { return Collections.unmodifiableList(mediaList); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getModeratedAt() { return moderatedAt; }
    public String getModerationNote() { return moderationNote; }
}
