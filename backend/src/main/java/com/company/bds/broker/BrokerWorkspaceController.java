package com.company.bds.broker;

import com.company.bds.lead.api.request.LeadCommandRequests;
import com.company.bds.shared.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Broker workspace (UI-08) and the qualified-lead / ROI report (P-08). BROKER/ADMIN only (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/broker")
public class BrokerWorkspaceController {
    private final BrokerWorkspaceService workspace;

    public BrokerWorkspaceController(BrokerWorkspaceService workspace) {
        this.workspace = workspace;
    }

    @GetMapping("/workspace")
    public Map<String, Object> get(Authentication authentication) {
        return workspace.workspace(CurrentUser.id(authentication));
    }

    @PutMapping("/workspace/sla")
    public Map<String, Object> sla(@RequestBody Sla request, Authentication authentication) {
        UUID actor = CurrentUser.id(authentication);
        workspace.updateSla(actor, request.firstResponseMinutes(), request.reminderEnabled(), request.dailyDigestEnabled());
        return workspace.workspace(actor);
    }

    @GetMapping("/team")
    public List<Map<String, Object>> team(Authentication authentication) {
        return workspace.team(CurrentUser.id(authentication));
    }

    @PostMapping("/team")
    public List<Map<String, Object>> addMember(@RequestBody LeadCommandRequests.TeamMember request, Authentication authentication) {
        return workspace.addMember(CurrentUser.id(authentication), request.email());
    }

    @DeleteMapping("/team/{memberId}")
    public List<Map<String, Object>> removeMember(@PathVariable("memberId") UUID memberId, Authentication authentication) {
        return workspace.removeMember(CurrentUser.id(authentication), memberId);
    }

    @GetMapping("/reports/leads")
    public Map<String, Object> leadReport(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        return workspace.leadReport(CurrentUser.id(authentication), from, to);
    }

    public record Sla(int firstResponseMinutes, boolean reminderEnabled, boolean dailyDigestEnabled) {}
}
