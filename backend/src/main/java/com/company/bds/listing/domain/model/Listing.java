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
    private ListingSource source = ListingSource.DIRECT;
    private Instant availabilityConfirmedAt;
    private Instant expiresAt;
    private Instant soldCheckDueAt;
    private Instant soldCheckClearedAt;

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
    /** Restores lifecycle columns (persistence mapping only). */
    public Listing withSoldCheckClearedAt(Instant value) {
        this.soldCheckClearedAt = value;
        return this;
    }

    public Listing withLifecycle(ListingSource source, Instant availabilityConfirmedAt, Instant expiresAt, Instant soldCheckDueAt) {
        this.source = source != null ? source : ListingSource.DIRECT;
        this.availabilityConfirmedAt = availabilityConfirmedAt;
        this.expiresAt = expiresAt;
        this.soldCheckDueAt = soldCheckDueAt;
        return this;
    }

    /** Marks a new listing as created by a CSV import. */
    public void markImported() {
        if (this.publicRevisionId != null) throw new ListingDomainException("INVALID_STATE", "Chỉ tin mới có thể đánh dấu nhập từ tệp.");
        this.source = ListingSource.IMPORT;
    }

    /** The owner confirms the property is still available: a new validity period starts (P-14). */
    public void confirmAvailability(Instant now) {
        if (this.status != ListingStatus.ACTIVE) {
            throw new ListingDomainException("LISTING_NOT_ACTIVE", "Chỉ xác nhận còn hàng cho tin đang hiển thị.");
        }
        startValidity(now);
        this.updatedAt = now;
    }

    /**
     * Renews an EXPIRED listing without new moderation when it expired at most {@link FreshnessPolicy#RENEWAL_WINDOW}
     * ago and no edit was made after the public revision. Otherwise the owner must edit and resubmit.
     */
    public void renew(Instant now) {
        if (this.status != ListingStatus.EXPIRED) {
            throw new ListingDomainException("LISTING_NOT_EXPIRED", "Chỉ gia hạn tin đã hết hạn hiển thị.");
        }
        ListingRevision published = getPublicRevision().orElseThrow(() ->
                new ListingDomainException("RENEWAL_REQUIRES_REVIEW", "Tin chưa từng được duyệt, vui lòng gửi duyệt."));
        ListingRevision latest = getLatestRevision().orElse(published);
        // Unchanged = no revision after the public one (the same rule the owner list shows as "renewable").
        boolean unchanged = latest.getId().equals(published.getId());
        boolean inWindow = expiresAt == null || !now.isAfter(expiresAt.plus(FreshnessPolicy.RENEWAL_WINDOW));
        if (!unchanged || !inWindow) {
            throw new ListingDomainException("RENEWAL_REQUIRES_REVIEW", unchanged
                    ? "Tin đã hết hạn quá 30 ngày, vui lòng kiểm tra lại nội dung và gửi duyệt."
                    : "Tin có nội dung sửa đổi chưa được duyệt, vui lòng gửi duyệt bản sửa.");
        }
        this.status = ListingStatus.ACTIVE;
        startValidity(now);
        this.updatedAt = now;
    }

    /** Somebody reported the property as sold/no longer available: the owner has 48 hours to confirm. */
    public boolean requestSoldCheck(Instant now) {
        if (this.status != ListingStatus.ACTIVE || this.soldCheckDueAt != null) return false;
        this.soldCheckDueAt = now.plus(FreshnessPolicy.SOLD_CHECK_DEADLINE);
        this.updatedAt = now;
        return true;
    }

    private void startValidity(Instant now) {
        if (this.soldCheckDueAt != null) this.soldCheckClearedAt = now;
        this.availabilityConfirmedAt = now;
        this.expiresAt = now.plus(FreshnessPolicy.VALIDITY);
        this.soldCheckDueAt = null;
    }

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
        // An edit of a published (or owner-hidden) listing keeps the public version as it is while the edit is reviewed.
        if (!(publicRevisionId != null && (status == ListingStatus.ACTIVE || status == ListingStatus.PAUSED))) {
            this.status = ListingStatus.PENDING_REVIEW;
        }
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
        if (this.status != ListingStatus.PAUSED) this.status = ListingStatus.ACTIVE;
        startValidity(now);
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
            // Đã có bản public trước đó: bản public giữ nguyên trạng thái (hết hạn thì vẫn hết hạn)
            if (this.status == ListingStatus.PENDING_REVIEW) {
                this.status = expiresAt != null && !now.isBefore(expiresAt) ? ListingStatus.EXPIRED : ListingStatus.ACTIVE;
            }
        }
        this.updatedAt = now;
    }

    /**
     * Tạm ẩn tin đăng (VD: khi bị báo xấu vi phạm P0 cần xác minh khẩn cấp).
     */
    public void pause(Instant now) {
        this.status = ListingStatus.PAUSED;
        this.soldCheckDueAt = null;
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
    public ListingSource getSource() { return source; }
    public Instant getAvailabilityConfirmedAt() { return availabilityConfirmedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getSoldCheckDueAt() { return soldCheckDueAt; }
    public Instant getSoldCheckClearedAt() { return soldCheckClearedAt; }
}
