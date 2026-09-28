package com.company.bds.moderation.application.service;

import com.company.bds.analytics.application.AnalyticsRecorder;
import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.exception.ListingDomainException;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.listing.domain.model.ListingRevision;
import com.company.bds.listing.domain.model.RevisionStatus;
import com.company.bds.moderation.application.command.ApproveListingCommand;
import com.company.bds.moderation.application.command.RejectListingCommand;
import com.company.bds.moderation.application.port.in.GetListingDiffUseCase;
import com.company.bds.moderation.application.port.in.GetModerationQueueUseCase;
import com.company.bds.moderation.application.port.in.ModerateListingUseCase;
import com.company.bds.moderation.domain.model.FieldDiff;
import com.company.bds.moderation.domain.model.ListingDiffResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

@Service
@Transactional
public class ModerationApplicationService implements GetModerationQueueUseCase, GetListingDiffUseCase, ModerateListingUseCase {

    private final ListingPersistencePort listingPersistencePort;
    private final Clock clock;
    private final AnalyticsRecorder analytics;

    public ModerationApplicationService(ListingPersistencePort listingPersistencePort, Clock clock, AnalyticsRecorder analytics) {
        this.listingPersistencePort = listingPersistencePort;
        this.clock = clock;
        this.analytics = analytics;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Listing> getPendingQueue() {
        return listingPersistencePort.findPendingReviewListings();
    }

    @Override
    @Transactional(readOnly = true)
    public ListingDiffResult getDiff(UUID listingId) {
        Listing listing = listingPersistencePort.findById(listingId)
                .orElseThrow(() -> new ListingDomainException("LISTING_NOT_FOUND", "Không tìm thấy tin đăng: " + listingId));

        List<ListingRevision> revisions = listing.getRevisions();
        if (revisions.isEmpty()) {
            throw new ListingDomainException("NO_REVISION", "Tin đăng chưa có phiên bản nào.");
        }

        // Tìm revision đang cần duyệt (SUBMITTED) hoặc revision mới nhất
        ListingRevision current = revisions.stream()
                .filter(r -> r.getStatus() == RevisionStatus.SUBMITTED)
                .max(Comparator.comparingInt(ListingRevision::getRevisionNumber))
                .orElseGet(() -> listing.getLatestRevision().orElseThrow());

        // Compare with what the public currently sees (the public revision); a first submission compares with nothing.
        Optional<ListingRevision> previousOpt = listing.getPublicRevision()
                .filter(publicRevision -> !publicRevision.getId().equals(current.getId()));

        ListingRevision prev = previousOpt.orElse(null);
        List<FieldDiff> diffs = new ArrayList<>();
        diffs.add(diff("title", "Tiêu đề tin đăng", prev == null ? null : prev.getTitle(), current.getTitle()));
        diffs.add(diff("priceVnd", "Giá niêm yết (VNĐ)", prev == null ? null : prev.getPriceVnd(), current.getPriceVnd()));
        diffs.add(diff("areaM2", "Diện tích (m²)", prev == null ? null : prev.getAreaM2(), current.getAreaM2()));
        diffs.add(diff("purpose", "Mục đích giao dịch", prev == null ? null : prev.getPurpose(), current.getPurpose()));
        diffs.add(diff("propertyType", "Loại hình bất động sản", prev == null ? null : prev.getPropertyType(), current.getPropertyType()));
        diffs.add(diff("addressSummary", "Địa chỉ hiển thị", prev == null ? null : prev.getAddressSummary(), current.getAddressSummary()));
        diffs.add(diff("districtCode", "Mã quận/huyện", prev == null ? null : prev.getDistrictCode(), current.getDistrictCode()));
        diffs.add(diff("bedrooms", "Số phòng ngủ", prev == null ? null : prev.getBedrooms(), current.getBedrooms()));
        diffs.add(diff("bathrooms", "Số phòng tắm", prev == null ? null : prev.getBathrooms(), current.getBathrooms()));
        diffs.add(diff("legalStatus", "Pháp lý", prev == null ? null : prev.getLegalStatus(), current.getLegalStatus()));
        diffs.add(diff("description", "Nội dung mô tả chi tiết", prev == null ? null : prev.getDescription(), current.getDescription()));
        diffs.add(diff("mediaCount", "Số lượng hình ảnh/video", prev == null ? null : prev.getMediaList().size(), current.getMediaList().size()));
        return ListingDiffResult.of(listing.getId(), current.getId(), current.getRevisionNumber(),
                prev == null ? null : prev.getId(), prev == null ? null : prev.getRevisionNumber(), diffs);
    }

    /** First submission: every provided field counts as new; "(Chưa có)" marks the empty public side. */
    private static FieldDiff diff(String field, String label, Object publicValue, Object submittedValue) {
        if (publicValue == null) {
            String value = submittedValue == null ? "" : String.valueOf(submittedValue);
            return new FieldDiff(field, label, "(Chưa có)", value, !value.isEmpty() && !"0".equals(value));
        }
        return FieldDiff.of(field, label, publicValue, submittedValue);
    }

    @Override
    public Listing approve(ApproveListingCommand command) {
        Listing listing = listingPersistencePort.findById(command.listingId())
                .orElseThrow(() -> new ListingDomainException("LISTING_NOT_FOUND", "Không tìm thấy tin đăng: " + command.listingId()));

        listing.approveRevision(command.revisionId(), clock.instant());
        Listing saved = listingPersistencePort.save(listing);
        // The listing's owner is the subject (a moderator approving must not make the fact "internal").
        saved.getPublicRevision().ifPresent(revision -> analytics.recordServer("listing_published", 1,
                saved.getId() + ":" + revision.getRevisionNumber(), saved.getOwnerId(), saved.getId(),
                Map.of("revisionNumber", revision.getRevisionNumber())));
        return saved;
    }

    @Override
    public Listing reject(RejectListingCommand command) {
        Listing listing = listingPersistencePort.findById(command.listingId())
                .orElseThrow(() -> new ListingDomainException("LISTING_NOT_FOUND", "Không tìm thấy tin đăng: " + command.listingId()));

        String fullReason = command.reasonCode();
        if (command.reasonDetail() != null && !command.reasonDetail().isBlank()) {
            fullReason += ": " + command.reasonDetail().trim();
        }

        listing.rejectRevision(command.revisionId(), fullReason, clock.instant());
        return listingPersistencePort.save(listing);
    }
}
