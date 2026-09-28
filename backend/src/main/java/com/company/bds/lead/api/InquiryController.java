package com.company.bds.lead.api;

import com.company.bds.lead.api.request.LeadCommandRequests;
import com.company.bds.lead.application.AppointmentService;
import com.company.bds.lead.application.LeadAccessService;
import com.company.bds.lead.application.LeadApplicationService;
import com.company.bds.lead.application.LeadCommandService;
import com.company.bds.lead.application.LeadInboxQuery;
import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The requester's side of their inquiries (UI-10): real response state, appointments (confirm, counter-propose, cancel)
 * and withdrawal. Only the requester of a lead gets it here (anyone else: 404). Never exposes the seller's phone/e-mail
 * or owner-internal data (qualification, assignment, internal notes).
 */
@RestController
@RequestMapping("/api/v1/me/inquiries")
public class InquiryController {
    private final LeadInboxQuery inbox;
    private final LeadCommandService commands;
    private final LeadAccessService access;
    private final AppointmentService appointments;
    private final LeadApplicationService leads;

    public InquiryController(LeadInboxQuery inbox, LeadCommandService commands, LeadAccessService access,
                             AppointmentService appointments, LeadApplicationService leads) {
        this.inbox = inbox;
        this.commands = commands;
        this.access = access;
        this.appointments = appointments;
        this.leads = leads;
    }

    @GetMapping
    public LeadInboxQuery.PageResult<LeadInboxQuery.InquiryItem> list(
            @RequestParam(name = "status", required = false) LeadStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size,
            Authentication authentication) {
        return inbox.inquiries(CurrentUser.id(authentication), status, page, size);
    }

    /** Whether the contact form can be sent (KYC of both sides, listing state, an already open request). */
    @GetMapping("/eligibility")
    public Map<String, Object> eligibility(@RequestParam("listingId") UUID listingId, Authentication authentication) {
        return leads.eligibility(CurrentUser.id(authentication), listingId);
    }

    @GetMapping("/{id}/history")
    public List<Map<String, Object>> history(@PathVariable("id") UUID id, Authentication authentication) {
        requireRequester(id, CurrentUser.id(authentication));
        return inbox.history(id, true);
    }

    @PostMapping("/{id}/withdraw")
    public LeadInboxQuery.InquiryItem withdraw(@PathVariable("id") UUID id, @RequestBody LeadCommandRequests.Withdraw request,
                                               Authentication authentication) {
        return commands.withdraw(id, CurrentUser.id(authentication), request.reason(), request.expectedVersion());
    }

    @GetMapping("/{id}/appointments")
    public List<AppointmentService.AppointmentView> appointments(@PathVariable("id") UUID id, Authentication authentication) {
        UUID actor = CurrentUser.id(authentication);
        requireRequester(id, actor);
        return appointments.forLead(id, actor, false);
    }

    @PostMapping("/{id}/appointments")
    public ResponseEntity<AppointmentService.AppointmentView> propose(@PathVariable("id") UUID id,
                                                                      @RequestBody LeadCommandRequests.Propose request,
                                                                      Authentication authentication) {
        UUID actor = CurrentUser.id(authentication);
        requireRequester(id, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(appointments.propose(id, actor, false,
                AppointmentController.slots(request), request.note(), request.replacesVersion()));
    }

    private void requireRequester(UUID leadId, UUID actor) {
        LeadAccessService.LeadAccess lead = access.load(leadId);
        if (!actor.equals(lead.requesterId())) throw ApiException.notFound("LEAD_NOT_FOUND", "Không tìm thấy yêu cầu liên hệ.");
    }
}
