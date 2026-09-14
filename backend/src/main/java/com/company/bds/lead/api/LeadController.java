package com.company.bds.lead.api;

import com.company.bds.lead.api.request.CreateLeadRequest;
import com.company.bds.lead.api.request.UpdateLeadStatusRequest;
import com.company.bds.lead.api.response.LeadResponse;
import com.company.bds.lead.application.LeadApplicationService;
import com.company.bds.lead.domain.model.Lead;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.company.bds.shared.security.CurrentUser;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller tiếp nhận và quản lý yêu cầu tư vấn / lead từ khách hàng (FR24, FR25).
 */
@RestController
@RequestMapping("/api/v1")
public class LeadController {

    private final LeadApplicationService leadApplicationService;
    private final MessageSource messageSource;

    public LeadController(LeadApplicationService leadApplicationService, MessageSource messageSource) {
        this.leadApplicationService = leadApplicationService;
        this.messageSource = messageSource;
    }

    /**
     * Thành viên đã eKYC gửi liên hệ tới người đăng đã eKYC.
     */
    @PostMapping("/public/leads")
    public ResponseEntity<Map<String, Object>> submitLead(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateLeadRequest request,
            Authentication authentication) {
        Lead lead = leadApplicationService.submitLead(
                CurrentUser.id(authentication),
                request.listingId(),
                request.fullName(),
                request.phone(),
                request.note(),
                request.consentPolicy(),
                idempotencyKey
        );

        String msg = messageSource.getMessage("lead.received", null, "Đã tiếp nhận yêu cầu tư vấn thành công.", LocaleContextHolder.getLocale());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "leadId", lead.getId(),
                "requestCode", "YC-" + lead.getId().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT),
                "status", lead.getStatus().name(),
                "message", msg,
                "createdAt", lead.getCreatedAt()
        ));
    }

    /**
     * Lấy danh sách Lead cho Môi giới hoặc Admin bàn điều phối.
     */
    @GetMapping("/leads")
    public ResponseEntity<List<LeadResponse>> getLeads(
            @RequestParam(name = "brokerId", required = false) UUID brokerId,
            @RequestParam(name = "listingId", required = false) UUID listingId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            Authentication authentication) {

        page = Math.max(0, page);
        size = Math.max(1, Math.min(100, size));

        List<Lead> leads;
        boolean privileged = isPrivileged(authentication);
        UUID actorId = CurrentUser.id(authentication);
        if (listingId != null) {
            leads = leadApplicationService.getLeadsByListing(listingId, actorId, privileged, page, size);
        } else if (brokerId != null && privileged) {
            leads = leadApplicationService.getLeadsForBroker(brokerId, page, size);
        } else if (!privileged) {
            leads = leadApplicationService.getLeadsForBroker(actorId, page, size);
        } else {
            leads = leadApplicationService.getAllLeads(page, size);
        }

        List<LeadResponse> response = leads.stream()
                .map(LeadResponse::fromDomain)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Cập nhật trạng thái xử lý Lead (NEW -> CONTACTED -> APPOINTED -> CLOSED -> SPAM).
     */
    @PatchMapping("/leads/{id}/status")
    public ResponseEntity<LeadResponse> updateLeadStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateLeadStatusRequest request,
            Authentication authentication) {

        Lead updated = leadApplicationService.updateLeadStatus(
                id, request.status(), CurrentUser.id(authentication), isPrivileged(authentication));
        return ResponseEntity.ok(LeadResponse.fromDomain(updated));
    }

    @GetMapping("/leads/{id}/contact")
    public ResponseEntity<Map<String, String>> revealLeadContact(
            @PathVariable("id") UUID id, Authentication authentication) {
        String phone = leadApplicationService.revealPhone(
                id, CurrentUser.id(authentication), isPrivileged(authentication));
        return ResponseEntity.ok(Map.of("phone", phone));
    }

    private boolean isPrivileged(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MODERATOR"));
    }
}
