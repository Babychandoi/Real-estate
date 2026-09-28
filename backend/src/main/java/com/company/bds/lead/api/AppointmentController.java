package com.company.bds.lead.api;

import com.company.bds.lead.api.request.LeadCommandRequests;
import com.company.bds.lead.application.AppointmentService;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Actions on one appointment by either party (P-03); the service checks that the actor is a party of the lead. */
@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {
    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @PostMapping("/{id}/confirm")
    public AppointmentService.AppointmentView confirm(@PathVariable("id") UUID id, @RequestBody LeadCommandRequests.Confirm request,
                                                      Authentication authentication) {
        if (request.slotId() == null) throw ApiException.badRequest("SLOT_REQUIRED", "Chọn một khung giờ để xác nhận.");
        return appointments.confirm(id, CurrentUser.id(authentication), request.slotId(), request.expectedVersion());
    }

    @PostMapping("/{id}/cancel")
    public AppointmentService.AppointmentView cancel(@PathVariable("id") UUID id, @RequestBody LeadCommandRequests.Cancel request,
                                                     Authentication authentication) {
        return appointments.cancel(id, CurrentUser.id(authentication), request.reason(), request.expectedVersion());
    }

    @PostMapping("/{id}/outcome")
    public AppointmentService.AppointmentView outcome(@PathVariable("id") UUID id, @RequestBody LeadCommandRequests.Outcome request,
                                                      Authentication authentication) {
        return appointments.recordOutcome(id, CurrentUser.id(authentication), request.outcome(), request.noShowParty(),
                request.note(), request.expectedVersion());
    }

    static List<AppointmentService.SlotInput> slots(LeadCommandRequests.Propose request) {
        if (request == null || request.slots() == null) return List.of();
        return request.slots().stream()
                .map(slot -> slot == null ? null : new AppointmentService.SlotInput(slot.startsAt(), slot.endsAt()))
                .toList();
    }
}
