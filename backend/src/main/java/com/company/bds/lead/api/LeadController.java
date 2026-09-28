package com.company.bds.lead.api;

import com.company.bds.lead.api.request.CreateLeadRequest;
import com.company.bds.lead.api.request.UpdateLeadStatusRequest;
import com.company.bds.lead.api.response.LeadResponse;
import com.company.bds.lead.api.response.LeadPageResponse;
import com.company.bds.lead.api.response.LeadListingPageResponse;
import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.lead.application.LeadApplicationService;
import com.company.bds.lead.domain.model.Lead;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;

    public LeadController(LeadApplicationService leadApplicationService, MessageSource messageSource, JdbcTemplate jdbc) {
        this.leadApplicationService = leadApplicationService;
        this.messageSource = messageSource;
        this.jdbc = jdbc;
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
                request.requestType(),
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

        var listingContexts = leadApplicationService.getListingContexts(leads);
        List<LeadResponse> response = leads.stream()
                .map(lead -> LeadResponse.fromDomain(lead, listingContexts.get(lead.getListingId())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/leads/search")
    public ResponseEntity<LeadPageResponse> searchLeads(
            @RequestParam(name = "status", required = false) LeadStatus status,
            @RequestParam(name = "listingId", required = false) UUID listingId,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {
        page = Math.max(0, page);
        size = Math.max(1, Math.min(100, size));
        boolean privileged = isPrivileged(authentication);
        UUID actorId = CurrentUser.id(authentication);
        var leadPage = listingId != null
                ? leadApplicationService.searchLeadsByListing(listingId, actorId, privileged, status, keyword, page, size)
                : privileged
                    ? leadApplicationService.searchAllLeads(status, keyword, page, size)
                    : leadApplicationService.searchLeadsForBroker(actorId, status, keyword, page, size);
        return ResponseEntity.ok(LeadPageResponse.fromDomain(leadPage, leadApplicationService.getListingContexts(leadPage.items())));
    }

    @GetMapping("/leads/listings")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','BROKER','OWNER')")
    public LeadListingPageResponse getLeadListings(
            @RequestParam(name = "q", defaultValue = "") String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size,
            Authentication authentication) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(50, size));
        String query = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        boolean privileged = isPrivileged(authentication);
        UUID ownerId = CurrentUser.id(authentication);
        String ownershipPredicate = privileged ? "" : " AND s.owner_id=? ";
        Long total = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT l.listing_id)
                FROM leads l JOIN listings s ON s.id=l.listing_id
                JOIN listing_revisions r ON r.id=COALESCE(s.public_revision_id,
                    (SELECT r2.id FROM listing_revisions r2 WHERE r2.listing_id=s.id ORDER BY r2.revision_number DESC LIMIT 1))
                WHERE (LOWER(r.title) LIKE ? OR LOWER(COALESCE(r.address_summary,'')) LIKE ? OR LOWER(s.slug) LIKE ?)
                """ + ownershipPredicate, Long.class,
                privileged ? new Object[] { query, query, query } : new Object[] { query, query, query, ownerId });
        List<LeadListingPageResponse.Item> items = jdbc.query("""
                SELECT s.id, r.title, s.slug, r.address_summary,
                       (SELECT lm.media_url FROM listing_media lm WHERE lm.revision_id=r.id
                        ORDER BY lm.is_primary DESC, lm.sort_order ASC LIMIT 1) AS image_url,
                       COUNT(l.id) AS total_leads,
                       COUNT(l.id) FILTER (WHERE l.status='NEW') AS new_leads,
                       COUNT(l.id) FILTER (WHERE l.status IN ('CONTACTED','APPOINTED')) AS active_leads,
                       COUNT(l.id) FILTER (WHERE l.status='CLOSED') AS closed_leads,
                       MAX(l.created_at) AS last_lead_at
                FROM leads l JOIN listings s ON s.id=l.listing_id
                JOIN listing_revisions r ON r.id=COALESCE(s.public_revision_id,
                    (SELECT r2.id FROM listing_revisions r2 WHERE r2.listing_id=s.id ORDER BY r2.revision_number DESC LIMIT 1))
                WHERE (LOWER(r.title) LIKE ? OR LOWER(COALESCE(r.address_summary,'')) LIKE ? OR LOWER(s.slug) LIKE ?)
                GROUP BY s.id, r.id, r.title, s.slug, r.address_summary
                ORDER BY MAX(l.created_at) DESC LIMIT ? OFFSET ?
                """.replace("GROUP BY", ownershipPredicate + " GROUP BY"), (rs, row) -> new LeadListingPageResponse.Item(
                        rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("slug"),
                        rs.getString("address_summary"), rs.getString("image_url"), rs.getLong("total_leads"),
                        rs.getLong("new_leads"), rs.getLong("active_leads"), rs.getLong("closed_leads"),
                        rs.getTimestamp("last_lead_at").toInstant()),
                privileged ? new Object[] { query, query, query, safeSize, safePage * safeSize }
                        : new Object[] { query, query, query, ownerId, safeSize, safePage * safeSize });
        long count = total == null ? 0 : total;
        int totalPages = count == 0 ? 0 : (int) Math.ceil((double) count / safeSize);
        return new LeadListingPageResponse(items, count, safePage, safeSize, totalPages);
    }

    @GetMapping("/leads/sent")
    public ResponseEntity<LeadPageResponse> getSentLeads(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {
        page = Math.max(0, page);
        size = Math.max(1, Math.min(100, size));
        var leadPage = leadApplicationService.getSentLeads(CurrentUser.id(authentication), page, size);
        return ResponseEntity.ok(LeadPageResponse.fromDomain(
                leadPage, leadApplicationService.getListingContexts(leadPage.items())));
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
        return ResponseEntity.ok(LeadResponse.fromDomain(updated,
                leadApplicationService.getListingContexts(List.of(updated)).get(updated.getListingId())));
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
