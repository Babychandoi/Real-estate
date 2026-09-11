package com.company.bds.moderation.application.service;

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

    public ModerationApplicationService(ListingPersistencePort listingPersistencePort, Clock clock) {
        this.listingPersistencePort = listingPersistencePort;
        this.clock = clock;
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

        // Tìm revision được duyệt trước đó hoặc revision liền trước
        Optional<ListingRevision> previousOpt = revisions.stream()
                .filter(r -> r.getRevisionNumber() < current.getRevisionNumber())
                .max(Comparator.comparingInt(ListingRevision::getRevisionNumber));

        List<FieldDiff> diffs = new ArrayList<>();
        if (previousOpt.isPresent()) {
            ListingRevision prev = previousOpt.get();
            diffs.add(FieldDiff.of("title", "Tiêu đề tin đăng", prev.getTitle(), current.getTitle()));
            diffs.add(FieldDiff.of("priceVnd", "Giá niêm yết (VNĐ)", prev.getPriceVnd(), current.getPriceVnd()));
            diffs.add(FieldDiff.of("areaM2", "Diện tích (m²)", prev.getAreaM2(), current.getAreaM2()));
            diffs.add(FieldDiff.of("purpose", "Mục đích giao dịch", prev.getPurpose(), current.getPurpose()));
            diffs.add(FieldDiff.of("propertyType", "Loại hình bất động sản", prev.getPropertyType(), current.getPropertyType()));
            diffs.add(FieldDiff.of("addressSummary", "Địa chỉ hiển thị", prev.getAddressSummary(), current.getAddressSummary()));
            diffs.add(FieldDiff.of("description", "Nội dung mô tả chi tiết", prev.getDescription(), current.getDescription()));
            diffs.add(FieldDiff.of("mediaCount", "Số lượng hình ảnh/video", prev.getMediaList().size(), current.getMediaList().size()));

            return ListingDiffResult.of(
                    listing.getId(),
                    current.getId(),
                    current.getRevisionNumber(),
                    prev.getId(),
                    prev.getRevisionNumber(),
                    diffs
            );
        } else {
            // Lần nộp duyệt đầu tiên (Revision #1)
            diffs.add(new FieldDiff("title", "Tiêu đề tin đăng", "(Chưa có)", current.getTitle(), true));
            diffs.add(new FieldDiff("priceVnd", "Giá niêm yết (VNĐ)", "(Chưa có)", String.valueOf(current.getPriceVnd()), true));
            diffs.add(new FieldDiff("areaM2", "Diện tích (m²)", "(Chưa có)", String.valueOf(current.getAreaM2()), true));
            diffs.add(new FieldDiff("purpose", "Mục đích giao dịch", "(Chưa có)", String.valueOf(current.getPurpose()), true));
            diffs.add(new FieldDiff("propertyType", "Loại hình bất động sản", "(Chưa có)", String.valueOf(current.getPropertyType()), true));
            diffs.add(new FieldDiff("addressSummary", "Địa chỉ hiển thị", "(Chưa có)", current.getAddressSummary(), true));
            diffs.add(new FieldDiff("description", "Nội dung mô tả chi tiết", "(Chưa có)", current.getDescription(), true));
            diffs.add(new FieldDiff("mediaCount", "Số lượng hình ảnh/video", "0", String.valueOf(current.getMediaList().size()), current.getMediaList().size() > 0));

            return ListingDiffResult.of(
                    listing.getId(),
                    current.getId(),
                    current.getRevisionNumber(),
                    null,
                    null,
                    diffs
            );
        }
    }

    @Override
    public Listing approve(ApproveListingCommand command) {
        Listing listing = listingPersistencePort.findById(command.listingId())
                .orElseThrow(() -> new ListingDomainException("LISTING_NOT_FOUND", "Không tìm thấy tin đăng: " + command.listingId()));

        listing.approveRevision(command.revisionId(), clock.instant());
        return listingPersistencePort.save(listing);
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
