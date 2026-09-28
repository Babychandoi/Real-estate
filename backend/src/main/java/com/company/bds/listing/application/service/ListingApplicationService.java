package com.company.bds.listing.application.service;

import com.company.bds.listing.application.command.CreateListingDraftCommand;
import com.company.bds.listing.application.command.SubmitListingRevisionCommand;
import com.company.bds.listing.application.command.UpdateListingDraftCommand;
import com.company.bds.listing.application.port.in.CreateListingDraftUseCase;
import com.company.bds.listing.application.port.in.GetListingDetailUseCase;
import com.company.bds.listing.application.port.in.SubmitListingRevisionUseCase;
import com.company.bds.listing.application.port.in.UpdateListingDraftUseCase;
import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.exception.ListingDomainException;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.listing.domain.model.ListingMedia;
import com.company.bds.listing.domain.model.ListingRevision;
import com.company.bds.listing.domain.model.ListingAttributes;
import com.company.bds.listing.domain.model.ListingPurpose;
import com.company.bds.listing.domain.model.LegalStatusCode;
import com.company.bds.listing.domain.exception.ListingValidationException;
import com.company.bds.listing.domain.exception.ListingVersionConflictException;
import com.company.bds.listing.application.port.in.DraftSaved;
import com.company.bds.verification.domain.model.KycStatus;
import com.company.bds.verification.domain.port.UserKycPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Application Service điều phối use case tin đăng và ranh giới transaction.
 * Tham khảo: PROJECT_CODE_RULES_BDS.md mục 4.4
 */
@Service
public class ListingApplicationService implements
        CreateListingDraftUseCase,
        UpdateListingDraftUseCase,
        SubmitListingRevisionUseCase,
        GetListingDetailUseCase {

    private final ListingPersistencePort persistencePort;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final boolean quotaEnforced;
    private final boolean verifiedKycRequired;
    private final UserKycPersistencePort userKycPersistencePort;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ListingApplicationService.class);
    private io.micrometer.core.instrument.Counter unversionedUpdates =
            io.micrometer.core.instrument.Counter.builder("bds.listing.draft.unversioned_updates")
                    .register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void meters(io.micrometer.core.instrument.MeterRegistry registry) {
        unversionedUpdates = io.micrometer.core.instrument.Counter.builder("bds.listing.draft.unversioned_updates")
                .description("Draft updates without If-Match/expectedVersion (v1 clients)").register(registry);
    }

    public ListingApplicationService(ListingPersistencePort persistencePort, Clock clock, JdbcTemplate jdbc,
                                     @Value("${app.billing.quota-enforced:true}") boolean quotaEnforced,
                                     @Value("${app.listing.require-verified-kyc:true}") boolean verifiedKycRequired,
                                     UserKycPersistencePort userKycPersistencePort) {
        this.persistencePort = persistencePort;
        this.clock = clock;
        this.jdbc = jdbc;
        this.quotaEnforced = quotaEnforced;
        this.verifiedKycRequired = verifiedKycRequired;
        this.userKycPersistencePort = userKycPersistencePort;
    }

    @Override
    @Transactional
    public DraftSaved createDraft(CreateListingDraftCommand command) {
        requireVerifiedKyc(command.ownerId());
        validateAttributes(command.purpose(), command.legalStatus(), command.attributes());
        Instant now = Instant.now(clock);

        List<ListingMedia> mediaList = buildMediaList(command.imageUrls());

        Listing listing = Listing.createNewDraft(
                command.ownerId(),
                allocateSlug(command.title()),
                command.title(),
                command.purpose(),
                command.propertyType(),
                command.priceVnd(),
                command.areaM2(),
                command.bedrooms(), command.bathrooms(), command.floors(),
                command.frontageM(), command.roadWidthM(), command.direction(), command.legalStatus(),
                command.description(),
                command.provinceCode(),
                command.districtCode(),
                command.wardCode(),
                command.addressSummary(),
                command.publicLatitude(),
                command.publicLongitude(),
                mediaList,
                now
        );
        listing.getLatestRevision().orElseThrow().updateAttributes(command.attributes());

        Listing saved = persistencePort.save(listing);
        return new DraftSaved(saved.getId(), saved.getLatestRevision().orElseThrow().getId(), saved.getVersion());
    }

    /**
     * Creates a draft marked {@code source=IMPORT} inside the caller's transaction (CSV import, P-08).
     */
    @Transactional
    public DraftSaved createImportedDraft(CreateListingDraftCommand command, UUID importBatchId) {
        requireVerifiedKyc(command.ownerId());
        validateAttributes(command.purpose(), command.legalStatus(), command.attributes());
        Instant now = Instant.now(clock);
        Listing listing = Listing.createNewDraft(command.ownerId(), allocateSlug(command.title()), command.title(),
                command.purpose(), command.propertyType(), command.priceVnd(), command.areaM2(),
                command.bedrooms(), command.bathrooms(), command.floors(), command.frontageM(), command.roadWidthM(),
                command.direction(), command.legalStatus(), command.description(), command.provinceCode(),
                command.districtCode(), command.wardCode(), command.addressSummary(), command.publicLatitude(),
                command.publicLongitude(), buildMediaList(command.imageUrls()), now);
        listing.getLatestRevision().orElseThrow().updateAttributes(command.attributes());
        listing.markImported();
        Listing saved = persistencePort.save(listing);
        jdbc.update("UPDATE listings SET import_batch_id=? WHERE id=?", importBatchId, saved.getId());
        return new DraftSaved(saved.getId(), saved.getLatestRevision().orElseThrow().getId(), saved.getVersion());
    }

    /** Field checks of the structured attributes (contract §2.1): rent terms for RENT only, legal detail, project. */
    public void validateAttributes(ListingPurpose purpose, String legalStatus, ListingAttributes attributes) {
        List<ListingValidationException.FieldIssue> issues = new ArrayList<>();
        ListingAttributes a = attributes == null ? ListingAttributes.EMPTY : attributes;
        if (purpose != ListingPurpose.RENT) {
            if (a.monthlyServiceFeeVnd() != null) issues.add(new ListingValidationException.FieldIssue(
                    "monthlyServiceFeeVnd", "Phí dịch vụ hằng tháng chỉ áp dụng cho tin cho thuê."));
            if (a.depositVnd() != null) issues.add(new ListingValidationException.FieldIssue(
                    "depositVnd", "Tiền đặt cọc chỉ áp dụng cho tin cho thuê."));
        }
        if (a.legalStatusCode() == LegalStatusCode.OTHER && (legalStatus == null || legalStatus.isBlank())) {
            issues.add(new ListingValidationException.FieldIssue("legalStatus",
                    "Mô tả loại giấy tờ pháp lý khi chọn \"Khác\"."));
        }
        if (a.projectId() != null) {
            Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM projects WHERE id=?", Integer.class, a.projectId());
            if (found == null || found == 0) issues.add(new ListingValidationException.FieldIssue(
                    "projectId", "Dự án không tồn tại."));
        }
        if (!issues.isEmpty()) throw new ListingValidationException(issues);
    }

    private String allocateSlug(String title) {
        String base = Normalizer.normalize(title.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isBlank()) base = "bat-dong-san";
        base = base.substring(0, Math.min(160, base.length())).replaceAll("-+$", "");
        String candidate = base;
        for (int suffix = 2; persistencePort.existsBySlug(candidate); suffix++) candidate = base + "-" + suffix;
        return candidate;
    }

    @Override
    @Transactional
    public DraftSaved updateDraft(UpdateListingDraftCommand command) {
        requireVerifiedKyc(command.requesterId());
        validateAttributes(command.purpose(), command.legalStatus(), command.attributes());
        Instant now = Instant.now(clock);

        // Serialize concurrent saves of one listing (two tabs, autosave + manual save): the row lock makes the second
        // writer wait, then its expected version no longer matches and it gets 409 instead of overwriting (R-4).
        // Ownership first, with the same 404 as a missing listing: no version or existence leaks to other accounts.
        List<Map<String, Object>> locked = jdbc.queryForList(
                "SELECT version, owner_id FROM listings WHERE id=? FOR UPDATE", command.listingId());
        if (locked.isEmpty() || (command.requesterId() != null && !command.requesterId().equals(locked.get(0).get("owner_id")))) {
            throw new ListingDomainException("LISTING_NOT_FOUND", "Không tìm thấy tin đăng.");
        }
        Long current = ((Number) locked.get(0).get("version")).longValue();
        if (command.expectedVersion() == null) {
            // v1 clients without If-Match (old UI); the wizard always sends it. Planned: require it once the old UI is gone.
            unversionedUpdates.increment();
            log.info("listing_draft_update_without_version listing={}", command.listingId());
        } else if (!command.expectedVersion().equals(current)) {
            throw new ListingVersionConflictException(current);
        }

        Listing listing = persistencePort.findById(command.listingId())
                .orElseThrow(() -> new ListingDomainException("LISTING_NOT_FOUND", "Không tìm thấy tin đăng."));

        List<ListingMedia> mediaList = buildMediaList(command.imageUrls());

        ListingRevision updatedRevision = listing.updateDraft(
                command.title(),
                command.purpose(),
                command.propertyType(),
                command.priceVnd(),
                command.areaM2(),
                command.bedrooms(), command.bathrooms(), command.floors(),
                command.frontageM(), command.roadWidthM(), command.direction(), command.legalStatus(),
                command.description(),
                command.provinceCode(),
                command.districtCode(),
                command.wardCode(),
                command.addressSummary(),
                command.publicLatitude(),
                command.publicLongitude(),
                mediaList,
                now
        );
        updatedRevision.updateAttributes(command.attributes());

        Listing saved = persistencePort.save(listing);
        return new DraftSaved(saved.getId(), updatedRevision.getId(), saved.getVersion());
    }

    @Override
    @Transactional
    public void submitRevision(SubmitListingRevisionCommand command) {
        requireVerifiedKyc(command.requesterId());
        Instant now = Instant.now(clock);

        Listing listing = persistencePort.findById(command.listingId())
                .orElseThrow(() -> new ListingDomainException("LISTING_NOT_FOUND", "Không tìm thấy tin đăng với ID: " + command.listingId()));

        if (command.requesterId() != null && !listing.getOwnerId().equals(command.requesterId())) {
            throw new ListingDomainException("FORBIDDEN", "Bạn không có quyền nộp duyệt tin đăng này.");
        }

        if (quotaEnforced) {
            int quota = jdbc.update("UPDATE users SET listing_quota_remaining=listing_quota_remaining-1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND listing_quota_remaining>0 AND (plan_expires_at IS NULL OR plan_expires_at>CURRENT_TIMESTAMP)", listing.getOwnerId());
            if (quota == 0) throw new ListingDomainException("LISTING_QUOTA_EXHAUSTED", "Bạn đã hết lượt đăng tin. Vui lòng mua thêm gói dịch vụ.");
        }

        listing.submitLatestDraft(now);
        persistencePort.save(listing);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Listing> getListingById(UUID id) {
        return persistencePort.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Listing> getMyListings(UUID ownerId) {
        return persistencePort.findByOwnerId(ownerId);
    }

    /** {@code null} keeps the current images of a draft; an empty list removes them. */
    private List<ListingMedia> buildMediaList(List<String> imageUrls) {
        if (imageUrls == null) return null;
        List<ListingMedia> list = new ArrayList<>();
        if (imageUrls != null) {
            for (int i = 0; i < imageUrls.size(); i++) {
                list.add(new ListingMedia(
                        UUID.randomUUID(),
                        imageUrls.get(i),
                        i == 0, // Ảnh đầu tiên làm ảnh đại diện primary
                        i
                ));
            }
        }
        return list;
    }

    public void requireVerifiedKyc(UUID userId) {
        if (!verifiedKycRequired) return;
        if (userId != null && userKycPersistencePort.findByUserId(userId)
                .map(profile -> profile.getStatus() == KycStatus.VERIFIED)
                .orElse(false)) return;
        throw new ListingDomainException("KYC_REQUIRED",
                "Bạn cần hoàn tất xác minh danh tính eKYC trước khi đăng tin.");
    }

}
