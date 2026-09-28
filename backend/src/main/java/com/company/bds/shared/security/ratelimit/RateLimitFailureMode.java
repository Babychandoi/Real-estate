package com.company.bds.shared.security.ratelimit;

/**
 * Behaviour of a policy while Redis is unavailable. In both modes requests keep being counted in a bounded in-process
 * table (per instance); the modes differ only when that table is full.
 */
public enum RateLimitFailureMode {
    /**
     * Credential and token endpoints. A full table (every slot holds a live window) means the instance is under a
     * distributed attack while Redis is down: requests from clients without a slot are rejected rather than letting
     * an attacker reset counters by flooding new keys.
     */
    FAIL_CLOSED,
    /** Everything else. A full table evicts the least recently used client, so memory stays bounded and service continues. */
    EVICT
}
