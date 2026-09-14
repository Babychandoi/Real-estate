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
    public UUID createDraft(CreateListingDraftCommand command) {
        requireVerifiedKyc(command.ownerId());
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

        Listing saved = persistencePort.save(listing);
        return saved.getId();
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
    public UUID updateDraft(UpdateListingDraftCommand command) {
        requireVerifiedKyc(command.requesterId());
        Instant now = Instant.now(clock);

        Listing listing = persistencePort.findById(command.listingId())
                .orElseThrow(() -> new ListingDomainException("LISTING_NOT_FOUND", "Không tìm thấy tin đăng với ID: " + command.listingId()));

        // Kiểm tra quyền sở hữu
        if (command.requesterId() != null && !listing.getOwnerId().equals(command.requesterId())) {
            throw new ListingDomainException("FORBIDDEN", "Bạn không có quyền chỉnh sửa tin đăng này.");
        }

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

        persistencePort.save(listing);
        return updatedRevision.getId();
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

    private List<ListingMedia> buildMediaList(List<String> imageUrls) {
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

    private void requireVerifiedKyc(UUID userId) {
        if (!verifiedKycRequired) return;
        if (userId != null && userKycPersistencePort.findByUserId(userId)
                .map(profile -> profile.getStatus() == KycStatus.VERIFIED)
                .orElse(false)) return;
        throw new ListingDomainException("KYC_REQUIRED",
                "Bạn cần hoàn tất xác minh danh tính eKYC trước khi đăng tin.");
    }

}
