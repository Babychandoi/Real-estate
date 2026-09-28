package com.company.bds.moderation.api;

import com.company.bds.asset.PropertyAssetService;
import com.company.bds.moderation.api.dto.ApproveListingRequest;
import com.company.bds.moderation.api.dto.ListingDiffResponse;
import com.company.bds.moderation.api.dto.RejectListingRequest;
import com.company.bds.moderation.api.dto.StandardReasonResponse;
import com.company.bds.moderation.application.port.in.GetListingDiffUseCase;
import com.company.bds.moderation.application.service.ModerationMetrics;
import com.company.bds.moderation.application.service.ModerationQueueService;
import com.company.bds.moderation.application.service.ModerationWorkflowService;
import com.company.bds.moderation.application.service.RandomAuditService;
import com.company.bds.moderation.domain.model.ApprovalReason;
import com.company.bds.moderation.domain.model.ListingDiffResult;
import com.company.bds.moderation.domain.model.StandardModerationReason;
import com.company.bds.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Moderation v2 (staff only, see SecurityConfig): paged queue, claims, reasoned decisions by the signed-in moderator,
 * bulk actions under the actor's claims, duplicates, decision history and the weekly random audit.
 */
@RestController
@RequestMapping("/api/v1/moderation")
public class ModerationController {

    private final GetListingDiffUseCase diffUseCase;
    private final ModerationQueueService queue;
    private final ModerationWorkflowService workflow;
    private final RandomAuditService audit;
    private final PropertyAssetService assets;
    private final ModerationMetrics metrics;

    public ModerationController(GetListingDiffUseCase diffUseCase, ModerationQueueService queue, ModerationWorkflowService workflow,
                                RandomAuditService audit, PropertyAssetService assets, ModerationMetrics metrics) {
        this.diffUseCase = diffUseCase;
        this.queue = queue;
        this.workflow = workflow;
        this.audit = audit;
        this.assets = assets;
        this.metrics = metrics;
    }

    /** Paged queue, oldest submission first. {@code filter}: ALL, FIRST_SUBMISSION, EDIT, SLA_BREACH, DUPLICATES, MINE, UNCLAIMED. */
    @GetMapping("/queue")
    public ModerationQueueService.QueuePage getQueue(@RequestParam(defaultValue = "ALL") String filter,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     Authentication authentication) {
        return queue.page(filter, page, size, CurrentUser.id(authentication));
    }

    @PostMapping("/listings/{id}/claim")
    public ModerationQueueService.ClaimView claim(@PathVariable("id") UUID listingId, Authentication authentication) {
        return workflow.claim(listingId, CurrentUser.id(authentication));
    }

    @DeleteMapping("/listings/{id}/claim")
    public ResponseEntity<Void> release(@PathVariable("id") UUID listingId, Authentication authentication) {
        workflow.release(listingId, CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    /** Submitted revision compared with the revision the public currently sees. */
    @GetMapping("/listings/{id}/diff")
    public ResponseEntity<ListingDiffResponse> getDiff(@PathVariable("id") UUID listingId) {
        ListingDiffResult result = diffUseCase.getDiff(listingId);
        return ResponseEntity.ok(new ListingDiffResponse(result.listingId(), result.currentRevisionId(),
                result.currentRevisionNumber(), result.previousRevisionId(), result.previousRevisionNumber(),
                result.isFirstSubmission(), result.changedCount(), result.diffs()));
    }

    @GetMapping("/listings/{id}/decisions")
    public List<ModerationWorkflowService.DecisionView> decisions(@PathVariable("id") UUID listingId) {
        return workflow.history(listingId);
    }

    @GetMapping("/listings/{id}/duplicates")
    public List<PropertyAssetService.Candidate> duplicates(@PathVariable("id") UUID listingId) {
        return assets.candidatesOf(listingId);
    }

    @PostMapping("/duplicates/{candidateId}")
    public ResponseEntity<Void> decideDuplicate(@PathVariable UUID candidateId, @Valid @RequestBody DuplicateDecisionRequest request,
                                                Authentication authentication) {
        assets.decide(candidateId, request.status(), request.note(), CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/listings/{id}/approve")
    public ModerationWorkflowService.Decision approve(@PathVariable("id") UUID listingId,
                                                      @Valid @RequestBody ApproveListingRequest request,
                                                      Authentication authentication) {
        String reason = request.reasonCode() == null || request.reasonCode().isBlank()
                ? ApprovalReason.MEETS_STANDARDS.name() : request.reasonCode().trim();
        ModerationWorkflowService.Decision decision = workflow.approve(listingId, request.revisionId(),
                CurrentUser.id(authentication), reason, request.note());
        metrics.invalidate();
        return decision;
    }

    @PostMapping("/listings/{id}/reject")
    public ModerationWorkflowService.Decision reject(@PathVariable("id") UUID listingId,
                                                     @Valid @RequestBody RejectListingRequest request,
                                                     Authentication authentication) {
        ModerationWorkflowService.Decision decision = workflow.reject(listingId, request.revisionId(),
                CurrentUser.id(authentication), request.reasonCode().trim(), request.reasonDetail());
        metrics.invalidate();
        return decision;
    }

    /** At most 50 items, only those under the actor's live claims; the response reports every item. */
    @PostMapping("/bulk")
    public ModerationWorkflowService.BulkResult bulk(@Valid @RequestBody BulkRequest request, Authentication authentication) {
        ModerationWorkflowService.BulkResult result = workflow.bulk(request.action(), request.items(),
                CurrentUser.id(authentication), request.reasonCode(), request.note());
        metrics.invalidate();
        return result;
    }

    @GetMapping("/rejection-reasons")
    public List<StandardReasonResponse> getRejectionReasons() {
        return Arrays.stream(StandardModerationReason.values())
                .map(r -> new StandardReasonResponse(r.name(), r.getVietnameseLabel(), r.getCategory())).toList();
    }

    @GetMapping("/reasons")
    public Map<String, List<StandardReasonResponse>> reasons() {
        return Map.of(
                "approve", Arrays.stream(ApprovalReason.values())
                        .map(r -> new StandardReasonResponse(r.name(), r.getVietnameseLabel(), "Phê duyệt")).toList(),
                "reject", getRejectionReasons());
    }

    @GetMapping("/audit-samples")
    public RandomAuditService.SamplePage auditSamples(@RequestParam(defaultValue = "OPEN") String status,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return audit.page(status, page, size);
    }

    @PostMapping("/audit-samples/{id}/review")
    public ResponseEntity<Void> reviewSample(@PathVariable UUID id, @Valid @RequestBody AuditReviewRequest request,
                                             Authentication authentication) {
        audit.review(id, request.outcome(), request.reasonCode(), request.note(), CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    /** Draws last week's sample now if the weekly job has not (idempotent per week). */
    @PostMapping("/audit-samples/draw")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> drawSamples() {
        return Map.of("created", audit.drawPreviousWeek());
    }

    public record DuplicateDecisionRequest(@NotBlank String status, String note) {}

    public record BulkRequest(@NotBlank String action, @NotNull List<ModerationWorkflowService.BulkItem> items,
                              @NotBlank String reasonCode, String note) {}

    public record AuditReviewRequest(@NotBlank String outcome, @NotBlank String reasonCode, String note) {}
}
