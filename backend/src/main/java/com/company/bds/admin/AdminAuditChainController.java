package com.company.bds.admin;

import com.company.bds.shared.security.AuditTrail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * W6: operator check of the request audit hash chain (ADMIN only, SecurityConfig; rate limited, policy
 * {@code admin-audit-chain}). Each run continues from the stored checkpoint and checks at most 50 000 positions, so a
 * request never rescans the whole chain; repeat until {@code complete}. {@code restart} forgets the checkpoint for a full
 * re-check. Runbook: docs/operations/ALERT_RUNBOOK.md#bdsauditchainstalled.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-chain/verification")
public class AdminAuditChainController {
    private final AuditTrail trail;

    public AdminAuditChainController(AuditTrail trail) {
        this.trail = trail;
    }

    /**
     * {@code intact}: no broken position found so far and no event stuck outside the chain for more than 5 minutes;
     * {@code unchainedEvents} counts events waiting for the next linking run (normally a few, never a problem by itself).
     */
    public record VerificationView(boolean intact, boolean complete, long fromSeq, long verifiedThrough, long headSeq,
                                   Long firstBrokenSeq, long unchainedEvents, long staleUnchainedEvents, Instant checkedAt) {}

    @PostMapping
    public ResponseEntity<VerificationView> run() {
        AuditTrail.IncrementalVerification v = trail.verifyIncrementally(AuditTrail.VERIFY_MAX_ROWS_PER_RUN);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new VerificationView(v.intact(), v.complete(), v.fromSeq(),
                v.verifiedThrough(), v.headSeq(), v.firstBrokenSeq(), v.unchained(), v.staleUnchained(), v.checkpointAt()));
    }

    @PostMapping("/restart")
    @ApiResponse(responseCode = "204", description = "Verification checkpoint reset")
    public ResponseEntity<Void> restart() {
        trail.resetCheckpoint();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
