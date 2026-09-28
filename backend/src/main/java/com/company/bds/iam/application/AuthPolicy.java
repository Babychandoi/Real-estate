package com.company.bds.iam.application;

import com.company.bds.shared.security.Roles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Session lifetimes and the staff second factor (ADR 0001, phase B). Staff sessions are shorter and expire after
 * 30 minutes without activity; the second factor for ADMIN/MODERATOR can only be switched off outside production.
 */
@Component
public class AuthPolicy {
    private final Duration userTtl;
    private final Duration staffTtl;
    private final Duration staffIdleTimeout;
    private final boolean mfaRequired;
    private final String mfaIssuer;
    private final Duration challengeTtl;
    private final int challengeMaxAttempts;

    public AuthPolicy(@Value("${app.security.session.user-ttl:PT12H}") Duration userTtl,
                      @Value("${app.security.session.staff-ttl:PT8H}") Duration staffTtl,
                      @Value("${app.security.session.staff-idle-timeout:PT30M}") Duration staffIdleTimeout,
                      @Value("${app.security.mfa.required:true}") boolean mfaRequired,
                      @Value("${app.security.mfa.issuer:Nhà Đất Chuẩn}") String mfaIssuer,
                      @Value("${app.security.mfa.challenge-ttl:PT5M}") Duration challengeTtl,
                      @Value("${app.security.mfa.challenge-max-attempts:5}") int challengeMaxAttempts,
                      @Value("${app.mode:demo}") String mode) {
        if (userTtl.isNegative() || userTtl.isZero() || staffTtl.isNegative() || staffTtl.isZero()
                || staffIdleTimeout.isNegative() || staffIdleTimeout.isZero()) {
            throw new IllegalStateException("Thời hạn phiên phải dương");
        }
        if (!mfaRequired && "production".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("app.security.mfa.required=false không được phép khi app.mode=production");
        }
        if (challengeMaxAttempts < 1) throw new IllegalStateException("app.security.mfa.challenge-max-attempts phải >= 1");
        this.userTtl = userTtl;
        this.staffTtl = staffTtl;
        this.staffIdleTimeout = staffIdleTimeout;
        this.mfaRequired = mfaRequired;
        this.mfaIssuer = mfaIssuer;
        this.challengeTtl = challengeTtl;
        this.challengeMaxAttempts = challengeMaxAttempts;
    }

    public Duration ttlFor(String role) { return Roles.isStaff(role) ? staffTtl : userTtl; }

    /** Idle timeout for the role's sessions, or {@code null} when only the absolute lifetime applies. */
    public Duration idleTimeoutFor(String role) { return Roles.isStaff(role) ? staffIdleTimeout : null; }

    /** Whether a staff account without a confirmed authenticator must enrol before it gets a session. */
    public boolean mfaRequired() { return mfaRequired; }

    public String mfaIssuer() { return mfaIssuer; }

    public Duration challengeTtl() { return challengeTtl; }

    public int challengeMaxAttempts() { return challengeMaxAttempts; }
}
