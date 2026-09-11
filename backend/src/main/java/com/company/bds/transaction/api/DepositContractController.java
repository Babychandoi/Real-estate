package com.company.bds.transaction.api;

import com.company.bds.transaction.api.request.CreateDepositRequest;
import com.company.bds.transaction.api.request.DepositActionRequest;
import com.company.bds.transaction.api.request.SignDepositRequest;
import com.company.bds.transaction.api.response.DepositContractResponse;
import com.company.bds.transaction.application.DepositTransactionApplicationService;
import com.company.bds.transaction.domain.model.DepositContract;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/transactions/deposits")
public class DepositContractController {

    private final DepositTransactionApplicationService depositService;

    public DepositContractController(DepositTransactionApplicationService depositService) {
        this.depositService = depositService;
    }

    @PostMapping
    public ResponseEntity<DepositContractResponse> createContract(
            @Valid @RequestBody CreateDepositRequest request
    ) {
        UUID buyerId = request.getBuyerId() != null ? request.getBuyerId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        DepositContract contract = depositService.createContract(
                request.getListingId(),
                buyerId,
                request.getBuyerName(),
                request.getBuyerPhone(),
                request.getBuyerIdNumber(),
                request.getDepositAmount(),
                request.getTermsConditions()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(DepositContractResponse.fromDomain(contract));
    }

    @PostMapping("/{id}/sign-buyer")
    public ResponseEntity<DepositContractResponse> signByBuyer(
            @PathVariable UUID id,
            @Valid @RequestBody SignDepositRequest request
    ) {
        DepositContract contract = depositService.signByBuyer(id, request.getOtpCode());
        return ResponseEntity.ok(DepositContractResponse.fromDomain(contract));
    }

    @PostMapping("/{id}/sign-seller")
    public ResponseEntity<DepositContractResponse> signBySeller(
            @PathVariable UUID id,
            @Valid @RequestBody SignDepositRequest request
    ) {
        DepositContract contract = depositService.signBySeller(id, request.getOtpCode());
        return ResponseEntity.ok(DepositContractResponse.fromDomain(contract));
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<DepositContractResponse> releaseEscrow(
            @PathVariable UUID id,
            @RequestBody(required = false) DepositActionRequest request
    ) {
        UUID operatorId = (request != null && request.getOperatorId() != null)
                ? request.getOperatorId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");
        DepositContract contract = depositService.releaseEscrow(id, operatorId);
        return ResponseEntity.ok(DepositContractResponse.fromDomain(contract));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<DepositContractResponse> refundEscrow(
            @PathVariable UUID id,
            @RequestBody(required = false) DepositActionRequest request
    ) {
        UUID operatorId = (request != null && request.getOperatorId() != null)
                ? request.getOperatorId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Hủy giao dịch hoàn tiền";
        DepositContract contract = depositService.refundEscrow(id, operatorId, reason);
        return ResponseEntity.ok(DepositContractResponse.fromDomain(contract));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepositContractResponse> getContract(@PathVariable UUID id) {
        DepositContract contract = depositService.getContract(id);
        return ResponseEntity.ok(DepositContractResponse.fromDomain(contract));
    }

    @GetMapping("/listing/{listingId}")
    public ResponseEntity<List<DepositContractResponse>> getContractsByListing(@PathVariable UUID listingId) {
        List<DepositContract> contracts = depositService.getContractsByListing(listingId);
        return ResponseEntity.ok(contracts.stream().map(DepositContractResponse::fromDomain).collect(Collectors.toList()));
    }
}
