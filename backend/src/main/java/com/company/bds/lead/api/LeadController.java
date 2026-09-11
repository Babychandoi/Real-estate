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
     * Khách hàng gửi liên hệ tư vấn BĐS (Public endpoint).
     */
    @PostMapping("/public/leads")
    public ResponseEntity<Map<String, Object>> submitLead(@Valid @RequestBody CreateLeadRequest request) {
        Lead lead = leadApplicationService.submitLead(
                request.listingId(),
                request.fullName(),
                request.phone(),
                request.note(),
                request.consentPolicy()
        );

        String msg = messageSource.getMessage("lead.received", null, "Đã tiếp nhận yêu cầu tư vấn thành công.", LocaleContextHolder.getLocale());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "leadId", lead.getId(),
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
            @RequestParam(name = "listingId", required = false) UUID listingId) {

        List<Lead> leads;
        if (listingId != null) {
            leads = leadApplicationService.getLeadsByListing(listingId);
        } else if (brokerId != null) {
            leads = leadApplicationService.getLeadsForBroker(brokerId);
        } else {
            leads = leadApplicationService.getAllLeads();
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
            @Valid @RequestBody UpdateLeadStatusRequest request) {

        Lead updated = leadApplicationService.updateLeadStatus(id, request.status());
        return ResponseEntity.ok(LeadResponse.fromDomain(updated));
    }
}
