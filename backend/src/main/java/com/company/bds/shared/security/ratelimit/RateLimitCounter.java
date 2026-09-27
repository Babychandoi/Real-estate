package com.company.bds.shared.security.ratelimit;

/**
 * Result of counting one request against one key: the count in the current window (including this request) and the
 * milliseconds until the window resets. {@code capacityExceeded} means a FAIL_CLOSED table had no slot for the key.
 */
record RateLimitCounter(long count, long ttlMillis, boolean capacityExceeded) {
    static RateLimitCounter counted(long count, long ttlMillis) { return new RateLimitCounter(count, ttlMillis, false); }
    static RateLimitCounter noCapacity(long retryAfterMillis) { return new RateLimitCounter(0, retryAfterMillis, true); }
}
