package com.company.bds.iam.api;

import com.company.bds.iam.application.AuthService;
import com.company.bds.iam.application.MfaService;
import com.company.bds.iam.application.SecurityEventLog;
import com.company.bds.iam.application.SessionService;
import com.company.bds.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The signed-in account's own security (F20.2, UI-17): open sessions and revocation, password change, second factor
 * status and recovery codes, recent security events. Every answer is private ({@code /api/v1/me/**} is no-store).
 */
@RestController
@RequestMapping("/api/v1/me")
public class AccountSecurityController {
    private final SessionService sessions;
    private final AuthService auth;
    private final MfaService mfa;
    private final SecurityEventLog events;
    private final ClientContexts clients;

    public AccountSecurityController(SessionService sessions, AuthService auth, MfaService mfa, SecurityEventLog events,
                                     ClientContexts clients) {
        this.sessions = sessions;
        this.auth = auth;
        this.mfa = mfa;
        this.events = events;
        this.clients = clients;
    }

    @GetMapping("/sessions")
    public List<SessionService.SessionView> sessions(Authentication authentication) {
        return sessions.list(CurrentUser.id(authentication), CurrentUser.sessionId(authentication));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID sessionId, Authentication authentication, HttpServletRequest http) {
        UUID userId = CurrentUser.id(authentication);
        sessions.revoke(userId, sessionId, sessionId.equals(CurrentUser.sessionId(authentication)) ? "LOGOUT" : "REVOKED_BY_USER");
        events.record(userId, SecurityEventLog.Type.SESSION_REVOKED, clients.of(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-others")
    public RevokedCount revokeOthers(Authentication authentication, HttpServletRequest http) {
        UUID userId = CurrentUser.id(authentication);
        int revoked = sessions.revokeAll(userId, CurrentUser.sessionId(authentication), "REVOKED_BY_USER");
        events.record(userId, SecurityEventLog.Type.OTHER_SESSIONS_REVOKED, clients.of(http));
        return new RevokedCount(revoked);
    }

    @PostMapping("/password")
    public RevokedCount changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication,
                                       HttpServletRequest http) {
        return new RevokedCount(auth.changePassword(CurrentUser.id(authentication), CurrentUser.sessionId(authentication),
                request.currentPassword(), request.newPassword(), clients.of(http)));
    }

    @GetMapping("/security-events")
    public List<SecurityEventLog.Event> securityEvents(Authentication authentication) {
        return events.recent(CurrentUser.id(authentication), 30);
    }

    @GetMapping("/mfa")
    public MfaService.Status mfaStatus(Authentication authentication) {
        return mfa.status(CurrentUser.id(authentication));
    }

    @PostMapping("/mfa/recovery-codes")
    public RecoveryCodes regenerateRecoveryCodes(@Valid @RequestBody CodeRequest request, Authentication authentication,
                                                 HttpServletRequest http) {
        return new RecoveryCodes(mfa.regenerateRecoveryCodes(CurrentUser.id(authentication), request.code(), clients.of(http)));
    }

    public record ChangePasswordRequest(@NotBlank @Size(max = 72) String currentPassword,
                                        @NotBlank @Size(min = 10, max = 72) String newPassword) {}

    public record CodeRequest(@NotBlank @Pattern(regexp = "^\\s*\\d{3}\\s?\\d{3}\\s*$") String code) {}

    public record RevokedCount(int revokedSessions) {}

    public record RecoveryCodes(List<String> recoveryCodes) {}
}
