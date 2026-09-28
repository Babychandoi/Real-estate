package com.company.bds.verification.api;

import com.company.bds.shared.error.ApiException;
import com.company.bds.verification.api.request.RejectKycRequest;
import com.company.bds.verification.api.request.SubmitKycRequest;
import com.company.bds.verification.api.response.UserKycResponse;
import com.company.bds.verification.application.KycApplicationService;
import com.company.bds.verification.application.TrustDecisionService;
import com.company.bds.iam.application.AuthService;
import com.company.bds.verification.domain.model.KycStatus;
import com.company.bds.verification.domain.model.UserKycProfile;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import com.company.bds.shared.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycController {

    private final KycApplicationService kycApplicationService;
    private final AuthService authService;
    private final TrustDecisionService trust;
    private final com.company.bds.verification.domain.port.UserKycPersistencePort kycPersistence;

    public KycController(KycApplicationService kycApplicationService, AuthService authService, TrustDecisionService trust,
                         com.company.bds.verification.domain.port.UserKycPersistencePort kycPersistence) {
        this.kycApplicationService = kycApplicationService;
        this.authService = authService;
        this.trust = trust;
        this.kycPersistence = kycPersistence;
    }

    /**
     * Nộp hồ sơ định danh cá nhân eKYC công dân mức 2 (FR01, NFR12).
     */
    @PostMapping("/submit")
    public ResponseEntity<UserKycResponse> submitKyc(@Valid @RequestBody SubmitKycRequest request,
                                                      Authentication authentication) {
        UserKycProfile profile = kycApplicationService.submitKyc(
                CurrentUser.id(authentication),
                request.idNumber(),
                request.fullName(),
                request.dob(),
                request.address(),
                request.idCardFrontUrl(),
                request.idCardBackUrl(),
                request.selfieUrl()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(UserKycResponse.fromDomain(profile));
    }

    /**
     * Tra cứu hồ sơ eKYC của người dùng theo userId.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<UserKycResponse> getKycByUserId(@PathVariable("userId") UUID userId, Authentication authentication) {
        boolean privileged = authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MODERATOR"));
        if (!privileged && !CurrentUser.id(authentication).equals(userId)) {
            throw new AccessDeniedException("Không có quyền xem hồ sơ định danh này.");
        }
        return kycApplicationService.getKycByUserId(userId)
                .map(p -> ResponseEntity.ok(privileged
                        ? UserKycResponse.fromDomainForReviewer(p)
                        : UserKycResponse.fromDomain(p)))
                .orElseThrow(() -> ApiException.notFound("KYC_NOT_FOUND", "Chưa có hồ sơ định danh."));
    }

    @PostMapping("/documents/access")
    public AuthService.KycDocumentAccess grantDocumentAccess(
            @Valid @RequestBody DocumentAccessRequest request, Authentication authentication) {
        return authService.grantKycDocumentAccess(CurrentUser.id(authentication), request.password());
    }

    @GetMapping("/user/{userId}/documents")
    public KycDocumentsResponse getOwnDocuments(
            @PathVariable("userId") UUID userId,
            @RequestHeader("X-Kyc-Document-Access") String accessToken,
            Authentication authentication) {
        if (!CurrentUser.id(authentication).equals(userId)
                || !authService.hasKycDocumentAccess(userId, accessToken)) {
            throw new AccessDeniedException("Cần xác nhận lại mật khẩu để xem ảnh định danh.");
        }
        UserKycProfile profile = kycApplicationService.getKycByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ eKYC."));
        return new KycDocumentsResponse(profile.getIdCardFrontUrl(), profile.getIdCardBackUrl(), profile.getSelfieUrl());
    }

    /**
     * Hàng đợi eKYC chờ duyệt.
     */
    @GetMapping("/queue")
    public ResponseEntity<List<UserKycResponse>> getQueue(
            @RequestParam(name = "status", required = false) KycStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        List<UserKycProfile> list = kycApplicationService.getQueue(status, Math.max(0, page), Math.max(1, Math.min(100, size)));
        return ResponseEntity.ok(list.stream().map(UserKycResponse::fromDomainForReviewer).collect(Collectors.toList()));
    }

    /** Identity approval: validity 24 months, decider and reason code recorded (TrustDecisionService). */
    @PostMapping("/{id}/approve")
    public ResponseEntity<UserKycResponse> approveKyc(@PathVariable("id") UUID id,
                                                      @RequestBody(required = false) RejectKycRequest request,
                                                      Authentication authentication) {
        trust.approveKyc(id, CurrentUser.id(authentication), request == null ? null : request.reasonCode(),
                request == null ? null : request.reason());
        return ResponseEntity.ok(reviewerView(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<UserKycResponse> rejectKyc(@PathVariable("id") UUID id, @Valid @RequestBody RejectKycRequest request,
                                                     Authentication authentication) {
        trust.rejectKyc(id, CurrentUser.id(authentication), request.reasonCode(), request.reason());
        return ResponseEntity.ok(reviewerView(id));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<UserKycResponse> revokeKyc(@PathVariable("id") UUID id, @Valid @RequestBody RejectKycRequest request,
                                                     Authentication authentication) {
        trust.revokeKyc(id, CurrentUser.id(authentication), request.reasonCode(), request.reason());
        return ResponseEntity.ok(reviewerView(id));
    }

    /** The signed-in user's identity check for the /kyc page: status, validity, rejection reason, decision timeline. */
    @GetMapping("/me/status")
    public ResponseEntity<MyKycStatus> myStatus(Authentication authentication) {
        UUID userId = CurrentUser.id(authentication);
        var profile = kycApplicationService.getKycByUserId(userId);
        var validity = kycApplicationService.validity(userId);
        String status = profile.map(p -> p.getStatus().name()).orElse("NOT_SUBMITTED");
        if ("VERIFIED".equals(status) && validity.expiresAt() != null && validity.expiresAt().isBefore(java.time.Instant.now())) status = "EXPIRED";
        List<TrustDecisionService.Decision> timeline = profile.map(p -> trust.history("KYC", p.getId(), false)).orElse(List.of());
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(new MyKycStatus(status,
                profile.map(UserKycProfile::getCreatedAt).orElse(null), profile.map(UserKycProfile::getVerifiedAt).orElse(null),
                validity.expiresAt(), validity.revokedAt(), profile.map(UserKycProfile::getRejectionReason).orElse(null),
                profile.map(p -> p.getStatus() != KycStatus.PENDING && (p.getStatus() != KycStatus.VERIFIED
                        || (validity.expiresAt() != null && validity.expiresAt().isBefore(java.time.Instant.now())))).orElse(true),
                timeline));
    }

    private UserKycResponse reviewerView(UUID kycId) {
        return kycPersistence.findById(kycId).map(UserKycResponse::fromDomainForReviewer)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ eKYC."));
    }

    public record MyKycStatus(String status, java.time.Instant submittedAt, java.time.Instant decidedAt, java.time.Instant expiresAt,
                              java.time.Instant revokedAt, String rejectionReason, boolean canSubmit,
                              List<TrustDecisionService.Decision> timeline) {}

    public record DocumentAccessRequest(@NotBlank String password) {}
    public record KycDocumentsResponse(String idCardFrontUrl, String idCardBackUrl, String selfieUrl) {}
}
