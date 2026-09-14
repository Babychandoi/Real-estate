package com.company.bds.listing.domain.model;

import com.company.bds.listing.domain.exception.ListingDomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Aggregate Root quản lý Tin đăng BĐS và vòng đời các phiên bản revision bất biến.
 * Tham khảo: PROJECT_CODE_RULES_BDS.md mục 4.3 & 10.3
 */
public class Listing {

    private final UUID id;
    private final UUID ownerId;
    private UUID publicRevisionId;
    private final String slug;
    private ListingStatus status;
    private boolean isVerifiedOwner = false;
    private final List<ListingRevision> revisions;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public Listing(
            UUID id,
            UUID ownerId,
            UUID publicRevisionId,
            ListingStatus status,
            List<ListingRevision> revisions,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this(id, ownerId, publicRevisionId, null, status, false, revisions, version, createdAt, updatedAt);
    }

    public Listing(
            UUID id,
            UUID ownerId,
            UUID publicRevisionId,
            String slug,
            ListingStatus status,
            boolean isVerifiedOwner,
            List<ListingRevision> revisions,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId không được để trống");
        this.publicRevisionId = publicRevisionId;
        this.slug = slug;
        this.status = status != null ? status : ListingStatus.DRAFT;
        this.isVerifiedOwner = isVerifiedOwner;
        this.revisions = revisions != null ? new ArrayList<>(revisions) : new ArrayList<>();
        this.version = version;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    /**
     * Khởi tạo một tin đăng mới ở trạng thái DRAFT cùng với Revision #1 nháp.
     */
    public static Listing createNewDraft(
            UUID ownerId,
            String slug,
            String title,
            ListingPurpose purpose,
            PropertyType propertyType,
            long priceVnd,
            BigDecimal areaM2,
            Integer bedrooms, Integer bathrooms, Integer floors,
            BigDecimal frontageM, BigDecimal roadWidthM, String direction, String legalStatus,
            String description,
            String provinceCode,
            String districtCode,
            String wardCode,
            String addressSummary,
            Double publicLatitude,
            Double publicLongitude,
            List<ListingMedia> mediaList,
            Instant now) {

        UUID listingId = UUID.randomUUID();
        ListingRevision initialRevision = ListingRevision.createInitialDraft(
                listingId,
                title,
                purpose,
                propertyType,
                priceVnd,
                areaM2,
                bedrooms, bathrooms, floors, frontageM, roadWidthM, direction, legalStatus,
                description,
                provinceCode,
                districtCode,
                wardCode,
                addressSummary,
                publicLatitude,
                publicLongitude,
                mediaList,
                now
        );

        return new Listing(
                listingId,
                ownerId,
                null,
                Objects.requireNonNull(slug, "slug không được để trống"),
                ListingStatus.DRAFT,
                false,
                List.of(initialRevision),
                0L,
                now,
                now
        );
    }

    /**
     * Cập nhật bản nháp tin đăng:
     * - Nếu revision gần nhất là DRAFT -> Cập nhật trực tiếp lên revision đó.
     * - Nếu revision gần nhất đã SUBMITTED hoặc APPROVED -> Tự động sinh revision DRAFT mới (bất biến revision cũ).
     */
    public ListingRevision updateDraft(
            String title,
            ListingPurpose purpose,
            PropertyType propertyType,
            long priceVnd,
            BigDecimal areaM2,
            Integer bedrooms, Integer bathrooms, Integer floors,
            BigDecimal frontageM, BigDecimal roadWidthM, String direction, String legalStatus,
            String description,
            String provinceCode,
            String districtCode,
            String wardCode,
            String addressSummary,
            Double publicLatitude,
            Double publicLongitude,
            List<ListingMedia> mediaList,
            Instant now) {

        ListingRevision latest = getLatestRevision()
                .orElseThrow(() -> new ListingDomainException("NO_REVISION", "Tin đăng không có revision nào tồn tại."));

        this.updatedAt = now;

        if (latest.getStatus() == RevisionStatus.DRAFT) {
            latest.updateDraft(
                    title,
                    purpose,
                    propertyType,
                    priceVnd,
                    areaM2,
                    bedrooms, bathrooms, floors, frontageM, roadWidthM, direction, legalStatus,
                    description,
                    provinceCode,
                    districtCode,
                    wardCode,
                    addressSummary,
                    publicLatitude,
                    publicLongitude,
                    mediaList
            );
            return latest;
        } else {
            // Revision gần nhất đã nộp duyệt hoặc đã duyệt -> Tạo revision nháp mới
            int nextNumber = latest.getRevisionNumber() + 1;
            ListingRevision nextDraft = latest.createNextDraft(nextNumber, now);
            nextDraft.updateDraft(
                    title,
                    purpose,
                    propertyType,
                    priceVnd,
                    areaM2,
                    bedrooms, bathrooms, floors, frontageM, roadWidthM, direction, legalStatus,
                    description,
                    provinceCode,
                    districtCode,
                    wardCode,
                    addressSummary,
                    publicLatitude,
                    publicLongitude,
                    mediaList
            );
            this.revisions.add(nextDraft);
            return nextDraft;
        }
    }

    /**
     * Nộp duyệt bản nháp gần nhất lên hội đồng kiểm duyệt.
     */
    public void submitLatestDraft(Instant now) {
        ListingRevision latest = getLatestRevision()
                .orElseThrow(() -> new ListingDomainException("NO_REVISION", "Không tìm thấy revision để nộp duyệt."));

        latest.submit(now);
        this.status = ListingStatus.PENDING_REVIEW;
        this.updatedAt = now;
    }

    /**
     * Phê duyệt một phiên bản revision -> Kích hoạt tin đăng ACTIVE và cập nhật publicRevisionId.
     */
    public void approveRevision(UUID revisionId, Instant now) {
        ListingRevision target = revisions.stream()
                .filter(r -> r.getId().equals(revisionId))
                .findFirst()
                .orElseThrow(() -> new ListingDomainException("REVISION_NOT_FOUND", "Không tìm thấy revision ID: " + revisionId));

        target.approve(now);
        this.publicRevisionId = target.getId();
        this.status = ListingStatus.ACTIVE;
        this.updatedAt = now;
    }

    /**
     * Từ chối một phiên bản revision đang nộp duyệt kèm lý do chuẩn hóa.
     */
    public void rejectRevision(UUID revisionId, String reason, Instant now) {
        ListingRevision target = revisions.stream()
                .filter(r -> r.getId().equals(revisionId))
                .findFirst()
                .orElseThrow(() -> new ListingDomainException("REVISION_NOT_FOUND", "Không tìm thấy revision ID: " + revisionId));

        target.reject(reason, now);
        if (this.publicRevisionId == null) {
            this.status = ListingStatus.REJECTED;
        } else {
            // Đã có bản public trước đó, giữ nguyên trạng thái ACTIVE cho bản public
            this.status = ListingStatus.ACTIVE;
        }
        this.updatedAt = now;
    }

    /**
     * Tạm ẩn tin đăng (VD: khi bị báo xấu vi phạm P0 cần xác minh khẩn cấp).
     */
    public void pause(Instant now) {
        this.status = ListingStatus.PAUSED;
        this.updatedAt = now;
    }

    /**
     * Khóa tin đăng vĩnh viễn hoặc do vi phạm lừa đảo (FR27).
     */
    public void lock(Instant now) {
        this.status = ListingStatus.LOCKED;
        this.updatedAt = now;
    }

    /**
     * Phục hồi tin đăng sau khi giải trình/xác minh thành công (FR27).
     */
    public void resume(Instant now) {
        if (this.publicRevisionId != null) {
            this.status = ListingStatus.ACTIVE;
        } else {
            this.status = ListingStatus.DRAFT;
        }
        this.updatedAt = now;
    }

    public Optional<ListingRevision> getLatestRevision() {
        return revisions.stream()
                .max(Comparator.comparingInt(ListingRevision::getRevisionNumber));
    }

    public Optional<ListingRevision> getPublicRevision() {
        if (publicRevisionId == null) return Optional.empty();
        return revisions.stream()
                .filter(r -> r.getId().equals(publicRevisionId))
                .findFirst();
    }

    public boolean isVerifiedOwner() { return isVerifiedOwner; }

    /**
     * Cấp hoặc thu hồi nhãn Tin Chính Chủ sau khi đối chiếu thẩm định eKYC & Sổ đỏ (FR03).
     */
    public void markVerifiedOwner(boolean verified, Instant now) {
        this.isVerifiedOwner = verified;
        this.updatedAt = now;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getPublicRevisionId() { return publicRevisionId; }
    public String getSlug() { return slug; }
    public ListingStatus getStatus() { return status; }
    public List<ListingRevision> getRevisions() { return Collections.unmodifiableList(revisions); }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
