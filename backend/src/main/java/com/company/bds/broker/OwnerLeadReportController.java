package com.company.bds.broker;

import com.company.bds.shared.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * The same qualified-lead / ROI report for every poster (OWNER included, who has no broker workspace — contract §2.5):
 * shown on the owner's "my leads" page.
 */
@RestController
public class OwnerLeadReportController {
    private final BrokerWorkspaceService workspace;

    public OwnerLeadReportController(BrokerWorkspaceService workspace) {
        this.workspace = workspace;
    }

    @GetMapping("/api/v1/leads/report")
    public Map<String, Object> report(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        return workspace.leadReport(CurrentUser.id(authentication), from, to);
    }
}
