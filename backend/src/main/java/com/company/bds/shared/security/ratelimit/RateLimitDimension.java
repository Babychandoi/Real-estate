package com.company.bds.shared.security.ratelimit;

import java.util.Locale;

/** What a quota is counted against. */
public enum RateLimitDimension {
    /** Client address resolved through the trusted proxy chain (IPv6 grouped by /64). */
    IP,
    /** Authenticated user id. */
    ACCOUNT,
    /** Normalised e-mail from the request body (login, password reset, verification resend). */
    EMAIL;

    public String tag() { return name().toLowerCase(Locale.ROOT); }

    public static RateLimitDimension fromTag(String tag) {
        return valueOf(tag.trim().toUpperCase(Locale.ROOT));
    }
}
