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
    public ResponseEntity<UserKycResponse> submitKyc(@Valid @RequestBody SubmitKycRequest request) {
        UserKycProfile profile = kycApplicationService.submitKyc(
                request.userId(),
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
    public ResponseEntity<UserKycResponse> getKycByUserId(@PathVariable("userId") UUID userId) {
        return kycApplicationService.getKycByUserId(userId)
                .map(p -> ResponseEntity.ok(UserKycResponse.fromDomain(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Hàng đợi eKYC chờ duyệt.
     */
    @GetMapping("/queue")
    public ResponseEntity<List<UserKycResponse>> getQueue(
            @RequestParam(name = "status", required = false) KycStatus status) {
        List<UserKycProfile> list = kycApplicationService.getQueue(status);
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
