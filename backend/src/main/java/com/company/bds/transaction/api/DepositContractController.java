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
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import com.company.bds.shared.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/transactions/deposits")
public class DepositContractController {

    private final DepositTransactionApplicationService depositService;

    public DepositContractController(DepositTransactionApplicationService depositService) {
        this.depositService = depositService;
    }

    @PostMapping
    public ResponseEntity<DepositContractResponse> createContract(
            @Valid @RequestBody CreateDepositRequest request, Authentication authentication
    ) {
        UUID buyerId = CurrentUser.id(authentication);
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
            @Valid @RequestBody SignDepositRequest request, Authentication authentication
    ) {
        requireParticipant(id, authentication, true);
        DepositContract contract = depositService.signByBuyer(id, request.getOtpCode());
        return ResponseEntity.ok(DepositContractResponse.fromDomain(contract));
    }

    @PostMapping("/{id}/sign-seller")
    public ResponseEntity<DepositContractResponse> signBySeller(
            @PathVariable UUID id,
            @Valid @RequestBody SignDepositRequest request, Authentication authentication
    ) {
        requireParticipant(id, authentication, false);
        DepositContract contract = depositService.signBySeller(id, request.getOtpCode());
        return ResponseEntity.ok(DepositContractResponse.fromDomain(contract));
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<DepositContractResponse> releaseEscrow(
            @PathVariable UUID id,
            @RequestBody(required = false) DepositActionRequest request, Authentication authentication
    ) {
        UUID operatorId = CurrentUser.id(authentication);
        DepositContract contract = depositService.releaseEscrow(id, operatorId);
        return ResponseEntity.ok(DepositContractResponse.fromDomain(contract));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<DepositContractResponse> refundEscrow(
            @PathVariable UUID id,
            @RequestBody(required = false) DepositActionRequest request, Authentication authentication
    ) {
        UUID operatorId = CurrentUser.id(authentication);
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Hủy giao dịch hoàn tiền";
        DepositContract contract = depositService.refundEscrow(id, operatorId, reason);
        return ResponseEntity.ok(DepositContractResponse.fromDomain(contract));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepositContractResponse> getContract(@PathVariable UUID id, Authentication authentication) {
        DepositContract contract = depositService.getContract(id);
        requireAccess(contract, authentication);
        return ResponseEntity.ok(DepositContractResponse.fromDomain(contract));
    }

    @GetMapping("/listing/{listingId}")
    public ResponseEntity<List<DepositContractResponse>> getContractsByListing(
            @PathVariable UUID listingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        List<DepositContract> contracts = depositService.getContractsByListing(
                listingId, Math.max(0, page), Math.max(1, Math.min(100, size)));
        contracts.forEach(contract -> requireAccess(contract, authentication));
        return ResponseEntity.ok(contracts.stream().map(DepositContractResponse::fromDomain).collect(Collectors.toList()));
    }

    private void requireParticipant(UUID contractId, Authentication authentication, boolean buyer) {
        DepositContract contract = depositService.getContract(contractId);
        UUID expected = buyer ? contract.getBuyerId() : contract.getSellerId();
        if (!expected.equals(CurrentUser.id(authentication))) throw new AccessDeniedException("Không phải bên ký hợp đồng");
    }

    private void requireAccess(DepositContract contract, Authentication authentication) {
        boolean privileged = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MODERATOR"));
        UUID userId = CurrentUser.id(authentication);
        if (!privileged && !contract.getBuyerId().equals(userId) && !contract.getSellerId().equals(userId)) {
            throw new AccessDeniedException("Không có quyền xem hợp đồng này");
        }
    }
}
