package com.company.bds.verification.api;

import com.company.bds.verification.api.request.ApproveVerificationRequest;
import com.company.bds.verification.api.request.RejectVerificationRequest;
import com.company.bds.verification.api.request.SubmitVerificationRequest;
import com.company.bds.verification.api.response.ListingVerificationResponse;
import com.company.bds.verification.api.response.UserKycResponse;
import com.company.bds.verification.application.KycApplicationService;
import com.company.bds.verification.application.ListingVerificationApplicationService;
import com.company.bds.verification.domain.model.ListingVerification;
import com.company.bds.verification.domain.model.VerificationStatus;
import com.company.bds.verification.domain.port.UserKycPersistencePort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import com.company.bds.shared.security.CurrentUser;

@RestController
@RequestMapping("/api/v1")
public class ListingVerificationController {

    private final ListingVerificationApplicationService verificationApplicationService;
    private final UserKycPersistencePort userKycPersistencePort;

    public ListingVerificationController(
            ListingVerificationApplicationService verificationApplicationService,
            UserKycPersistencePort userKycPersistencePort) {
        this.verificationApplicationService = verificationApplicationService;
        this.userKycPersistencePort = userKycPersistencePort;
    }

    /**
     * Nộp hồ sơ pháp lý xin cấp nhãn Tin Chính Chủ (FR03).
     */
    @PostMapping("/listings/{listingId}/verifications")
    public ResponseEntity<ListingVerificationResponse> submitVerification(
            @PathVariable("listingId") UUID listingId,
            @Valid @RequestBody SubmitVerificationRequest request,
            Authentication authentication) {

        ListingVerification verification = verificationApplicationService.submitVerification(
                listingId,
                CurrentUser.id(authentication),
                request.verificationType(),
                request.certificateNumber(),
                request.documentUrls(),
                request.ownerNameOnDoc()
        );

        UserKycResponse kycResp = verification.getUserKycId() != null
                ? userKycPersistencePort.findById(verification.getUserKycId()).map(UserKycResponse::fromDomain).orElse(null)
                : null;

        return ResponseEntity.status(HttpStatus.CREATED).body(ListingVerificationResponse.fromDomain(verification, kycResp));
    }

    /**
     * Hàng đợi thẩm định hồ sơ tin chính chủ phục vụ Bàn Thẩm định (Desktop Studio).
     */
    @GetMapping("/verifications")
    public ResponseEntity<List<ListingVerificationResponse>> getQueue(
            @RequestParam(name = "status", required = false) VerificationStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {

        List<ListingVerification> queue = verificationApplicationService.getQueue(
                status, Math.max(0, page), Math.max(1, Math.min(100, size)));
        List<ListingVerificationResponse> list = queue.stream().map(v -> {
            UserKycResponse kyc = v.getUserKycId() != null
                    ? userKycPersistencePort.findById(v.getUserKycId()).map(UserKycResponse::fromDomain).orElse(null)
                    : null;
            return ListingVerificationResponse.fromDomain(v, kyc);
        }).collect(Collectors.toList());

        return ResponseEntity.ok(list);
    }

    /**
     * Chi tiết một hồ sơ thẩm định pháp lý kèm eKYC đối soát.
     */
    @GetMapping("/verifications/{id}")
    public ResponseEntity<ListingVerificationResponse> getVerificationDetail(@PathVariable("id") UUID id) {
        ListingVerification v = verificationApplicationService.getVerificationDetail(id);
        UserKycResponse kyc = v.getUserKycId() != null
                ? userKycPersistencePort.findById(v.getUserKycId()).map(UserKycResponse::fromDomain).orElse(null)
                : null;
        return ResponseEntity.ok(ListingVerificationResponse.fromDomain(v, kyc));
    }

    /**
     * Thẩm định viên Phê duyệt cấp nhãn Tin Chính Chủ (VERIFIED_OWNER).
     */
    @PostMapping("/verifications/{id}/approve")
    public ResponseEntity<ListingVerificationResponse> approveVerification(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ApproveVerificationRequest request) {

        String note = request != null ? request.verifierNote() : "Thông tin CCCD và Sổ đỏ trùng khớp 100%. Phê duyệt cấp nhãn Tin Chính Chủ.";
        ListingVerification v = verificationApplicationService.approveVerification(id, note);
        UserKycResponse kyc = v.getUserKycId() != null
                ? userKycPersistencePort.findById(v.getUserKycId()).map(UserKycResponse::fromDomain).orElse(null)
                : null;
        return ResponseEntity.ok(ListingVerificationResponse.fromDomain(v, kyc));
    }

    /**
     * Thẩm định viên Từ chối hồ sơ kèm lý do.
     */
    @PostMapping("/verifications/{id}/reject")
    public ResponseEntity<ListingVerificationResponse> rejectVerification(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RejectVerificationRequest request) {

        ListingVerification v = verificationApplicationService.rejectVerification(id, request.reason());
        UserKycResponse kyc = v.getUserKycId() != null
                ? userKycPersistencePort.findById(v.getUserKycId()).map(UserKycResponse::fromDomain).orElse(null)
                : null;
        return ResponseEntity.ok(ListingVerificationResponse.fromDomain(v, kyc));
    }

    /**
     * Thu hồi nhãn Tin Chính Chủ.
     */
    @PostMapping("/verifications/{id}/revoke")
    public ResponseEntity<ListingVerificationResponse> revokeVerification(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RejectVerificationRequest request) {

        ListingVerification v = verificationApplicationService.revokeVerification(id, request.reason());
        UserKycResponse kyc = v.getUserKycId() != null
                ? userKycPersistencePort.findById(v.getUserKycId()).map(UserKycResponse::fromDomain).orElse(null)
                : null;
        return ResponseEntity.ok(ListingVerificationResponse.fromDomain(v, kyc));
    }
}
