package com.company.bds.admin;

import com.company.bds.shared.security.AuditTrail;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * W6: operator check of the request audit hash chain (ADMIN only, SecurityConfig). Recomputes every chained event page
 * by page; {@code intact} is true only when the chain verifies AND no stored event is left outside it. Runbook:
 * docs/operations/ALERT_RUNBOOK.md#bdsauditchainstalled.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-chain")
public class AdminAuditChainController {
    private final AuditTrail trail;

    public AdminAuditChainController(AuditTrail trail) {
        this.trail = trail;
    }

    public record VerificationView(boolean intact, long verifiedEvents, Long firstBrokenSeq, long unchainedEvents) {}

    @GetMapping("/verification")
    public ResponseEntity<VerificationView> verify() {
        AuditTrail.Verification v = trail.verify();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new VerificationView(v.intact(), v.verified(), v.firstBrokenSeq(), v.unchained()));
    }
}
