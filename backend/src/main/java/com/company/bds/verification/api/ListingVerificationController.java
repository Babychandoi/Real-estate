package com.company.bds.verification.api;

import com.company.bds.shared.security.CurrentUser;
import com.company.bds.verification.api.request.ApproveVerificationRequest;
import com.company.bds.verification.api.request.RejectVerificationRequest;
import com.company.bds.verification.api.request.SubmitVerificationRequest;
import com.company.bds.verification.api.response.ListingVerificationResponse;
import com.company.bds.verification.api.response.UserKycResponse;
import com.company.bds.verification.application.KycDocumentAccessService;
import com.company.bds.verification.application.ListingVerificationApplicationService;
import com.company.bds.verification.application.TrustDecisionService;
import com.company.bds.verification.application.VerificationEvidenceService;
import com.company.bds.verification.domain.model.ListingVerification;
import com.company.bds.verification.domain.model.TrustReason;
import com.company.bds.verification.domain.model.VerificationStatus;
import com.company.bds.verification.domain.port.UserKycPersistencePort;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ownership checks: submission by the poster; queue, evidence comparison and reasoned decisions by staff. */
@RestController
@RequestMapping("/api/v1")
public class ListingVerificationController {

    private final ListingVerificationApplicationService verificationApplicationService;
    private final UserKycPersistencePort userKycPersistencePort;
    private final TrustDecisionService trust;
    private final VerificationEvidenceService evidence;
    private final KycDocumentAccessService kycAccess;

    public ListingVerificationController(ListingVerificationApplicationService verificationApplicationService,
                                         UserKycPersistencePort userKycPersistencePort, TrustDecisionService trust,
                                         VerificationEvidenceService evidence, KycDocumentAccessService kycAccess) {
        this.verificationApplicationService = verificationApplicationService;
        this.userKycPersistencePort = userKycPersistencePort;
        this.trust = trust;
        this.evidence = evidence;
        this.kycAccess = kycAccess;
    }

    @PostMapping("/listings/{listingId}/verifications")
    public ResponseEntity<ListingVerificationResponse> submitVerification(
            @PathVariable("listingId") UUID listingId,
            @Valid @RequestBody SubmitVerificationRequest request,
            Authentication authentication) {
        ListingVerification verification = verificationApplicationService.submitVerification(
                listingId, CurrentUser.id(authentication), request.verificationType(), request.certificateNumber(),
                request.documentUrls(), request.ownerNameOnDoc());
        UserKycResponse kycResp = verification.getUserKycId() != null
                ? userKycPersistencePort.findById(verification.getUserKycId()).map(UserKycResponse::fromDomain).orElse(null)
                : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(ListingVerificationResponse.fromDomain(verification, kycResp));
    }

    @GetMapping("/verifications")
    public ResponseEntity<List<ListingVerificationResponse>> getQueue(
            @RequestParam(name = "status", required = false) VerificationStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        List<ListingVerification> queue = verificationApplicationService.getQueue(status, Math.max(0, page), Math.max(1, Math.min(100, size)));
        Map<UUID, TrustDecisionService.OwnershipExtras> extras = trust.ownershipExtras(queue.stream().map(ListingVerification::getId).toList());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(queue.stream().map(v -> staffView(v, extras.get(v.getId()))).toList());
    }

    @GetMapping("/verifications/{id}")
    public ResponseEntity<ListingVerificationResponse> getVerificationDetail(@PathVariable("id") UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(staffView(id));
    }

    /** Evidence comparison: identity vs document name, certificate reuse, address, validity, private images, history. */
    @GetMapping("/verifications/{id}/evidence")
    public ResponseEntity<VerificationEvidenceService.Evidence> getEvidence(@PathVariable("id") UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(evidence.evidence(id));
    }

    /** Opening the private documents of the listing owner: password + reason, logged. */
    @PostMapping("/verifications/{id}/document-access")
    public ResponseEntity<KycDocumentAccessService.DocumentAccess> openDocuments(@PathVariable("id") UUID id,
                                                                               @RequestBody Map<String, String> body,
                                                                               Authentication authentication) {
        UUID owner = evidence.evidence(id).listingOwnerId();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(kycAccess.open(CurrentUser.id(authentication), owner,
                body == null ? null : body.get("password"), body == null ? null : body.get("reason")));
    }

    @GetMapping("/verifications/reasons")
    public Map<String, List<Map<String, String>>> reasons() {
        return Map.of(
                "approve", codes(TrustReason.Kind.APPROVE),
                "reject", codes(TrustReason.Kind.REJECT),
                "revoke", codes(TrustReason.Kind.REVOKE));
    }

    @PostMapping("/verifications/{id}/approve")
    public ResponseEntity<ListingVerificationResponse> approveVerification(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ApproveVerificationRequest request,
            Authentication authentication) {
        trust.approveOwnership(id, CurrentUser.id(authentication), request == null ? null : request.reasonCode(),
                request == null ? null : request.verifierNote());
        return ResponseEntity.ok(staffView(id));
    }

    @PostMapping("/verifications/{id}/reject")
    public ResponseEntity<ListingVerificationResponse> rejectVerification(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RejectVerificationRequest request,
            Authentication authentication) {
        trust.rejectOwnership(id, CurrentUser.id(authentication), request.reasonCode(), request.reason());
        return ResponseEntity.ok(staffView(id));
    }

    @PostMapping("/verifications/{id}/revoke")
    public ResponseEntity<ListingVerificationResponse> revokeVerification(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RejectVerificationRequest request,
            Authentication authentication) {
        trust.revokeOwnership(id, CurrentUser.id(authentication),
                request.reasonCode() == null || request.reasonCode().isBlank() ? TrustReason.DISPUTE.name() : request.reasonCode(),
                request.reason());
        return ResponseEntity.ok(staffView(id));
    }

    private ListingVerificationResponse staffView(UUID id) {
        ListingVerification v = verificationApplicationService.getVerificationDetail(id);
        return staffView(v, trust.ownershipExtras(List.of(id)).get(id));
    }

    private ListingVerificationResponse staffView(ListingVerification v, TrustDecisionService.OwnershipExtras extras) {
        UserKycResponse kyc = v.getUserKycId() != null
                ? userKycPersistencePort.findById(v.getUserKycId()).map(UserKycResponse::fromDomainForReviewer).orElse(null)
                : null;
        return ListingVerificationResponse.fromDomain(v, kyc, extras);
    }

    private static List<Map<String, String>> codes(TrustReason.Kind kind) {
        return Arrays.stream(TrustReason.values()).filter(r -> r.getKind() == kind)
                .map(r -> Map.of("code", r.name(), "label", r.getVietnameseLabel())).toList();
    }
}
