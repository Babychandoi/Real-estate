package com.company.bds.shared.security.ratelimit;

/**
 * Outcome of {@link RateLimiter#check}. {@code violated} is the dimension that ran out ({@code null} when allowed or
 * when a FAIL_CLOSED fallback table had no room, see {@code capacityExceeded}). {@code retryAfterSeconds} is the time
 * until every violated window has reset, rounded up, at least 1.
 */
public record RateLimitDecision(boolean allowed, String policy, RateLimitDimension violated, boolean capacityExceeded,
                                long retryAfterSeconds, boolean localFallback) {
    static RateLimitDecision allow(String policy, boolean localFallback) {
        return new RateLimitDecision(true, policy, null, false, 0, localFallback);
    }
}
