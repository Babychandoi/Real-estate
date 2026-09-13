package com.company.bds.verification.api;

import com.company.bds.verification.api.request.RejectKycRequest;
import com.company.bds.verification.api.request.SubmitKycRequest;
import com.company.bds.verification.api.response.UserKycResponse;
import com.company.bds.verification.application.KycApplicationService;
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

@RestController
@RequestMapping("/api/v1/kyc")
public class KycController {

    private final KycApplicationService kycApplicationService;

    public KycController(KycApplicationService kycApplicationService) {
        this.kycApplicationService = kycApplicationService;
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
                .map(p -> ResponseEntity.ok(UserKycResponse.fromDomain(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
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
        return ResponseEntity.ok(list.stream().map(UserKycResponse::fromDomain).collect(Collectors.toList()));
    }

    /**
     * Phê duyệt định danh eKYC.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<UserKycResponse> approveKyc(@PathVariable("id") UUID id) {
        UserKycProfile profile = kycApplicationService.approveKyc(id);
        return ResponseEntity.ok(UserKycResponse.fromDomain(profile));
    }

    /**
     * Từ chối hồ sơ eKYC.
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<UserKycResponse> rejectKyc(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RejectKycRequest request) {
        UserKycProfile profile = kycApplicationService.rejectKyc(id, request.reason());
        return ResponseEntity.ok(UserKycResponse.fromDomain(profile));
    }
}
