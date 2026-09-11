package com.company.bds.moderation.api;

import com.company.bds.listing.domain.model.Listing;
import com.company.bds.listing.domain.model.ListingRevision;
import com.company.bds.listing.domain.model.RevisionStatus;
import com.company.bds.moderation.api.dto.*;
import com.company.bds.moderation.application.command.ApproveListingCommand;
import com.company.bds.moderation.application.command.RejectListingCommand;
import com.company.bds.moderation.application.port.in.GetListingDiffUseCase;
import com.company.bds.moderation.application.port.in.GetModerationQueueUseCase;
import com.company.bds.moderation.application.port.in.ModerateListingUseCase;
import com.company.bds.moderation.domain.model.ListingDiffResult;
import com.company.bds.moderation.domain.model.StandardModerationReason;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/moderation")
public class ModerationController {

    private final GetModerationQueueUseCase queueUseCase;
    private final GetListingDiffUseCase diffUseCase;
    private final ModerateListingUseCase moderateUseCase;

    public ModerationController(
            GetModerationQueueUseCase queueUseCase,
            GetListingDiffUseCase diffUseCase,
            ModerateListingUseCase moderateUseCase) {
        this.queueUseCase = queueUseCase;
        this.diffUseCase = diffUseCase;
        this.moderateUseCase = moderateUseCase;
    }

    @GetMapping("/queue")
    public ResponseEntity<List<ModerationQueueItemResponse>> getQueue() {
        List<Listing> listings = queueUseCase.getPendingQueue();
        List<ModerationQueueItemResponse> response = listings.stream()
                .map(l -> {
                    ListingRevision rev = l.getRevisions().stream()
                            .filter(r -> r.getStatus() == RevisionStatus.SUBMITTED)
                            .max(Comparator.comparingInt(ListingRevision::getRevisionNumber))
                            .orElseGet(() -> l.getLatestRevision().orElse(null));

                    if (rev == null) return null;

                    return new ModerationQueueItemResponse(
                            l.getId(),
                            rev.getId(),
                            rev.getRevisionNumber(),
                            rev.getTitle(),
                            l.getOwnerId(),
                            rev.getPriceVnd(),
                            rev.getAreaM2(),
                            rev.getPurpose().name(),
                            rev.getPropertyType().name(),
                            rev.getAddressSummary(),
                            rev.getSubmittedAt(),
                            rev.getMediaList().size(),
                            rev.getRevisionNumber() == 1
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/listings/{id}/diff")
    public ResponseEntity<ListingDiffResponse> getDiff(@PathVariable("id") UUID listingId) {
        ListingDiffResult result = diffUseCase.getDiff(listingId);
        return ResponseEntity.ok(new ListingDiffResponse(
                result.listingId(),
                result.currentRevisionId(),
                result.currentRevisionNumber(),
                result.previousRevisionId(),
                result.previousRevisionNumber(),
                result.isFirstSubmission(),
                result.changedCount(),
                result.diffs()
        ));
    }

    @PostMapping("/listings/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable("id") UUID listingId,
            @Valid @RequestBody ApproveListingRequest request) {

        UUID moderatorId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        Listing approved = moderateUseCase.approve(new ApproveListingCommand(
                listingId,
                request.revisionId(),
                moderatorId,
                request.note()
        ));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "listingId", approved.getId(),
                "status", approved.getStatus().name(),
                "publicRevisionId", approved.getPublicRevisionId() != null ? approved.getPublicRevisionId() : ""
        ));
    }

    @PostMapping("/listings/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable("id") UUID listingId,
            @Valid @RequestBody RejectListingRequest request) {

        UUID moderatorId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        Listing rejected = moderateUseCase.reject(new RejectListingCommand(
                listingId,
                request.revisionId(),
                moderatorId,
                request.reasonCode(),
                request.reasonDetail()
        ));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "listingId", rejected.getId(),
                "status", rejected.getStatus().name()
        ));
    }

    @GetMapping("/rejection-reasons")
    public ResponseEntity<List<StandardReasonResponse>> getRejectionReasons() {
        List<StandardReasonResponse> reasons = Arrays.stream(StandardModerationReason.values())
                .map(r -> new StandardReasonResponse(r.name(), r.getVietnameseLabel(), r.getCategory()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(reasons);
    }
}
