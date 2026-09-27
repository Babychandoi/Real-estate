package com.company.bds.shared.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code app.security.rate-limit.*}. Policy defaults live in {@link RateLimitPolicies}; {@code policies} only
 * overrides them, e.g. {@code app.security.rate-limit.policies.auth-login.email.limit=5} or
 * {@code ...auth-login.email.window=30m}.
 */
@Component
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    /** Scales every limit. Only integration-test profiles raise it (all MockMvc requests share 127.0.0.1). */
    private int limitMultiplier = 1;
    /** Capacity of the in-process fallback table shared by the EVICT policies. */
    private int localMaxEntries = 100_000;
    /** Capacity of the in-process fallback table shared by the FAIL_CLOSED (credential) policies. */
    private int localStrictMaxEntries = 50_000;
    /** After a Redis failure, how long to count locally before trying Redis again. */
    private Duration redisRetryInterval = Duration.ofSeconds(5);
    /** Optional secret mixed into key hashes so Redis keys cannot be brute-forced back to IPs or e-mails. */
    private String keyPepper = "";
    private Map<String, Map<String, RuleOverride>> policies = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getLimitMultiplier() { return limitMultiplier; }
    public void setLimitMultiplier(int limitMultiplier) { this.limitMultiplier = limitMultiplier; }
    public int getLocalMaxEntries() { return localMaxEntries; }
    public void setLocalMaxEntries(int localMaxEntries) { this.localMaxEntries = localMaxEntries; }
    public int getLocalStrictMaxEntries() { return localStrictMaxEntries; }
    public void setLocalStrictMaxEntries(int localStrictMaxEntries) { this.localStrictMaxEntries = localStrictMaxEntries; }
    public Duration getRedisRetryInterval() { return redisRetryInterval; }
    public void setRedisRetryInterval(Duration redisRetryInterval) { this.redisRetryInterval = redisRetryInterval; }
    public String getKeyPepper() { return keyPepper; }
    public void setKeyPepper(String keyPepper) { this.keyPepper = keyPepper == null ? "" : keyPepper; }
    public Map<String, Map<String, RuleOverride>> getPolicies() { return policies; }
    public void setPolicies(Map<String, Map<String, RuleOverride>> policies) { this.policies = policies; }

    public static class RuleOverride {
        private Integer limit;
        private Duration window;

        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
    }
}
