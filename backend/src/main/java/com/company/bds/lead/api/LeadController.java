package com.company.bds.lead.api;

import com.company.bds.lead.api.request.CreateLeadRequest;
import com.company.bds.lead.api.request.LeadCommandRequests;
import com.company.bds.lead.api.request.UpdateLeadStatusRequest;
import com.company.bds.lead.api.response.LeadListingPageResponse;
import com.company.bds.lead.application.AppointmentService;
import com.company.bds.lead.application.LeadAccessService;
import com.company.bds.lead.application.LeadApplicationService;
import com.company.bds.lead.application.LeadCommandService;
import com.company.bds.lead.application.LeadInboxQuery;
import com.company.bds.lead.domain.model.Lead;
import com.company.bds.lead.domain.model.LeadRequestType;
import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lead intake and the owner-side inbox (FR24, FR25, F08.3, F17, UI-09). Owner-side reads go through one JOIN on the
 * listing owner ({@link LeadInboxQuery}); writes are compare-and-set on the lead version ({@link LeadCommandService}).
 */
@RestController
@RequestMapping("/api/v1")
public class LeadController {

    private final LeadApplicationService leadApplicationService;
    private final LeadInboxQuery inbox;
    private final LeadCommandService commands;
    private final LeadAccessService access;
    private final AppointmentService appointments;
    private final MessageSource messageSource;
    private final JdbcTemplate jdbc;

    public LeadController(LeadApplicationService leadApplicationService, LeadInboxQuery inbox, LeadCommandService commands,
                          LeadAccessService access, AppointmentService appointments, MessageSource messageSource,
                          JdbcTemplate jdbc) {
        this.leadApplicationService = leadApplicationService;
        this.inbox = inbox;
        this.commands = commands;
        this.access = access;
        this.appointments = appointments;
        this.messageSource = messageSource;
        this.jdbc = jdbc;
    }

    /**
     * Thành viên đã eKYC gửi liên hệ tới người đăng đã eKYC. A replay of the same Idempotency-Key and payload answers with
     * the original lead, {@code replayed: true} and {@code Idempotent-Replayed: true}.
     */
    @PostMapping("/public/leads")
    public ResponseEntity<Map<String, Object>> submitLead(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateLeadRequest request,
            Authentication authentication) {
        LeadApplicationService.SubmitResult result = leadApplicationService.submit(
                CurrentUser.id(authentication), request.listingId(), request.fullName(), request.phone(),
                request.requestType(), request.note(), request.consentPolicy(), idempotencyKey);
        Lead lead = result.lead();
        String msg = messageSource.getMessage("lead.received", null, "Đã tiếp nhận yêu cầu tư vấn thành công.", LocaleContextHolder.getLocale());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("leadId", lead.getId());
        body.put("requestCode", "YC-" + lead.getId().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT));
        body.put("status", lead.getStatus().name());
        body.put("message", msg);
        body.put("createdAt", lead.getCreatedAt());
        body.put("replayed", result.replayed());
        // 201 for replays too (existing clients treat any other status as a failure); the header tells them apart.
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(body);
    }

    /** Legacy list (owner side: own + assigned leads; staff: all, or one owner's with brokerId). Newest first, size ≤ 50. */
    @GetMapping("/leads")
    public List<LeadInboxQuery.LeadItem> getLeads(
            @RequestParam(name = "brokerId", required = false) UUID brokerId,
            @RequestParam(name = "listingId", required = false) UUID listingId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            Authentication authentication) {
        boolean privileged = isPrivileged(authentication);
        UUID actor = CurrentUser.id(authentication);
        if (listingId != null && !privileged) inbox.requireListingAccess(listingId, actor);
        LeadInboxQuery.InboxFilter filter = new LeadInboxQuery.InboxFilter(privileged ? brokerId : null, listingId,
                null, null, null, false, null, null);
        return inbox.inbox(actor, privileged, filter, page, size).items();
    }

    /** Inbox page with server filters; {@code /leads/search} is the older name kept for the admin oversight page. */
    @GetMapping({"/leads/inbox", "/leads/search"})
    public LeadInboxQuery.PageResult<LeadInboxQuery.LeadItem> searchLeads(
            @RequestParam(name = "status", required = false) LeadStatus status,
            @RequestParam(name = "listingId", required = false) UUID listingId,
            @RequestParam(name = "requestType", required = false) LeadRequestType requestType,
            @RequestParam(name = "qualification", required = false) String qualification,
            @RequestParam(name = "overdue", defaultValue = "false") boolean overdue,
            @RequestParam(name = "assigneeId", required = false) UUID assigneeId,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {
        UUID actor = CurrentUser.id(authentication);
        boolean privileged = isPrivileged(authentication);
        if (listingId != null && !privileged) inbox.requireListingAccess(listingId, actor);
        return inbox.inbox(actor, privileged, new LeadInboxQuery.InboxFilter(
                null, listingId, status, requestType, qualification, overdue, assigneeId, keyword), page, size);
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
        String query = "%" + LeadInboxQuery.escapeLike(keyword.trim().toLowerCase(java.util.Locale.ROOT)) + "%";
        boolean privileged = isPrivileged(authentication);
        UUID ownerId = CurrentUser.id(authentication);
        String ownershipPredicate = privileged ? "" : " AND s.owner_id=? ";
        String match = "(LOWER(r.title) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(r.address_summary,'')) LIKE ? ESCAPE '\\' OR LOWER(s.slug) LIKE ? ESCAPE '\\')";
        Long total = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT l.listing_id)
                FROM leads l JOIN listings s ON s.id=l.listing_id
                JOIN listing_revisions r ON r.id=COALESCE(s.public_revision_id,
                    (SELECT r2.id FROM listing_revisions r2 WHERE r2.listing_id=s.id ORDER BY r2.revision_number DESC LIMIT 1))
                WHERE """ + match + ownershipPredicate, Long.class,
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
                WHERE """ + match + ownershipPredicate + """
                 GROUP BY s.id, r.id, r.title, s.slug, r.address_summary
                ORDER BY MAX(l.created_at) DESC, s.id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new LeadListingPageResponse.Item(
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

    /** The requester's own inquiries (older path; {@code /me/inquiries} is the same data). */
    @GetMapping("/leads/sent")
    public LeadInboxQuery.PageResult<LeadInboxQuery.InquiryItem> getSentLeads(
            @RequestParam(name = "status", required = false) LeadStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {
        return inbox.inquiries(CurrentUser.id(authentication), status, page, size);
    }

    @GetMapping("/leads/{id}")
    public LeadInboxQuery.LeadItem getLead(@PathVariable("id") UUID id, Authentication authentication) {
        access.requireOwnerSide(id, CurrentUser.id(authentication), isPrivileged(authentication));
        return inbox.item(id);
    }

    @GetMapping("/leads/{id}/history")
    public List<Map<String, Object>> history(@PathVariable("id") UUID id, Authentication authentication) {
        access.requireOwnerSide(id, CurrentUser.id(authentication), isPrivileged(authentication));
        return inbox.history(id, false);
    }

    /** Owner-side status change; {@code expectedVersion} required (stale → 409 LEAD_VERSION_CONFLICT). */
    @PatchMapping("/leads/{id}/status")
    public LeadInboxQuery.LeadItem updateLeadStatus(@PathVariable("id") UUID id,
                                                    @Valid @RequestBody UpdateLeadStatusRequest request,
                                                    Authentication authentication) {
        return commands.changeStatus(id, CurrentUser.id(authentication), isPrivileged(authentication), request.status(),
                request.expectedVersion(), request.note());
    }

    @PatchMapping("/leads/{id}/qualification")
    public LeadInboxQuery.LeadItem qualify(@PathVariable("id") UUID id, @RequestBody LeadCommandRequests.Qualify request,
                                           Authentication authentication) {
        return commands.qualify(id, CurrentUser.id(authentication), isPrivileged(authentication), request.qualification(),
                request.reason(), request.note(), request.expectedVersion());
    }

    @PatchMapping("/leads/{id}/assignee")
    public LeadInboxQuery.LeadItem assign(@PathVariable("id") UUID id, @RequestBody LeadCommandRequests.Assign request,
                                          Authentication authentication) {
        return commands.assign(id, CurrentUser.id(authentication), isPrivileged(authentication), request.assigneeId(),
                request.expectedVersion());
    }

    @GetMapping("/leads/{id}/appointments")
    public List<AppointmentService.AppointmentView> leadAppointments(@PathVariable("id") UUID id, Authentication authentication) {
        UUID actor = CurrentUser.id(authentication);
        boolean privileged = isPrivileged(authentication);
        access.requireOwnerSide(id, actor, privileged);
        return appointments.forLead(id, actor, privileged);
    }

    @PostMapping("/leads/{id}/appointments")
    public ResponseEntity<AppointmentService.AppointmentView> propose(@PathVariable("id") UUID id,
                                                                      @RequestBody LeadCommandRequests.Propose request,
                                                                      Authentication authentication) {
        UUID actor = CurrentUser.id(authentication);
        access.requireOwnerSide(id, actor, false);
        return ResponseEntity.status(HttpStatus.CREATED).body(appointments.propose(id, actor, false,
                AppointmentController.slots(request), request.note(), request.replacesVersion()));
    }

    @GetMapping("/leads/{id}/contact")
    public ResponseEntity<Map<String, String>> revealLeadContact(
            @PathVariable("id") UUID id, Authentication authentication) {
        String phone = leadApplicationService.revealPhone(
                id, CurrentUser.id(authentication), isPrivileged(authentication));
        return ResponseEntity.ok(Map.of("phone", phone));
    }

    static boolean isPrivileged(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MODERATOR"));
    }
}
