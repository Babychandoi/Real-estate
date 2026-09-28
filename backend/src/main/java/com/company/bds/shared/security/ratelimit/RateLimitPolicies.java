package com.company.bds.shared.security.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static com.company.bds.shared.security.ratelimit.RateLimitDimension.ACCOUNT;
import static com.company.bds.shared.security.ratelimit.RateLimitDimension.EMAIL;
import static com.company.bds.shared.security.ratelimit.RateLimitDimension.IP;
import static com.company.bds.shared.security.ratelimit.RateLimitFailureMode.EVICT;
import static com.company.bds.shared.security.ratelimit.RateLimitFailureMode.FAIL_CLOSED;

/**
 * Route policies (audit F13.2), first match wins. Credential endpoints are counted per IP and per targeted e-mail so
 * a password-spraying botnet is limited per account even when every request comes from a new address; write
 * endpoints for signed-in users are also counted per account. Every other {@code /api/**} request gets a generous
 * per-IP and per-account budget that only stops runaway clients (mobile carriers put many users behind one IP).
 */
@Component
public class RateLimitPolicies {
    private static final Pattern REPEATED_SLASHES = Pattern.compile("/{2,}");
    private final List<RateLimitPolicy> policies;

    @Autowired
    public RateLimitPolicies(RateLimitProperties properties) {
        this(defaults(), properties);
    }

    RateLimitPolicies(List<RateLimitPolicy> base, RateLimitProperties properties) {
        int multiplier = properties.getLimitMultiplier();
        if (multiplier < 1) throw new IllegalStateException("app.security.rate-limit.limit-multiplier phải >= 1");
        Map<String, Map<String, RateLimitProperties.RuleOverride>> overrides = properties.getPolicies();
        for (String name : overrides.keySet()) {
            if (base.stream().noneMatch(policy -> policy.name().equals(name))) {
                throw new IllegalStateException("Không có policy giới hạn tần suất tên '" + name + "'");
            }
        }
        List<RateLimitPolicy> resolved = new ArrayList<>(base.size());
        for (RateLimitPolicy policy : base) {
            resolved.add(apply(policy, overrides.getOrDefault(policy.name(), Map.of()), multiplier));
        }
        this.policies = List.copyOf(resolved);
    }

    public static List<RateLimitPolicy> defaults() {
        return List.of(
                policy("auth-login", "POST", "/api/v1/auth/login", FAIL_CLOSED,
                        rule(IP, 30, Duration.ofMinutes(1)), rule(EMAIL, 10, Duration.ofMinutes(15))),
                policy("auth-admin-login", "POST", "/api/v1/auth/admin/login", FAIL_CLOSED,
                        rule(IP, 10, Duration.ofMinutes(1)), rule(EMAIL, 5, Duration.ofMinutes(15))),
                policy("auth-register", "POST", "/api/v1/auth/register", FAIL_CLOSED,
                        rule(IP, 20, Duration.ofHours(1))),
                policy("auth-forgot-password", "POST", "/api/v1/auth/forgot-password", FAIL_CLOSED,
                        rule(IP, 10, Duration.ofMinutes(15)), rule(EMAIL, 3, Duration.ofHours(1))),
                policy("auth-resend-verification", "POST", "/api/v1/auth/resend-verification", FAIL_CLOSED,
                        rule(IP, 10, Duration.ofMinutes(15)), rule(EMAIL, 3, Duration.ofHours(1))),
                policy("auth-reset-password", "POST", "/api/v1/auth/reset-password", FAIL_CLOSED,
                        rule(IP, 10, Duration.ofMinutes(15))),
                policy("auth-verify-email", "GET", "/api/v1/auth/verify-email", FAIL_CLOSED,
                        rule(IP, 20, Duration.ofMinutes(15))),
                // S5-B token pages: the page posts the token in the body; the reset page asks the link state first.
                policy("auth-verify-email-post", "POST", "/api/v1/auth/verify-email", FAIL_CLOSED,
                        rule(IP, 20, Duration.ofMinutes(15))),
                policy("auth-reset-status", "POST", "/api/v1/auth/password-reset/status", FAIL_CLOSED,
                        rule(IP, 30, Duration.ofMinutes(15))),
                // S5-B staff second factor: each challenge also burns itself after 5 wrong codes (MfaService).
                policy("auth-mfa-verify", "POST", "/api/v1/auth/admin/mfa/verify", FAIL_CLOSED,
                        rule(IP, 20, Duration.ofMinutes(15))),
                policy("auth-mfa-enroll", "POST", "/api/v1/auth/admin/mfa/**", FAIL_CLOSED,
                        rule(IP, 20, Duration.ofMinutes(15))),
                // Re-enters the current password / a TOTP code of the signed-in account.
                policy("account-password", "POST", "/api/v1/me/password", FAIL_CLOSED,
                        rule(IP, 20, Duration.ofMinutes(15)), rule(ACCOUNT, 5, Duration.ofMinutes(15))),
                policy("account-mfa-codes", "POST", "/api/v1/me/mfa/recovery-codes", FAIL_CLOSED,
                        rule(IP, 20, Duration.ofMinutes(15)), rule(ACCOUNT, 5, Duration.ofMinutes(15))),
                // Re-enters the account password before private KYC images are shown.
                policy("kyc-document-access", "POST", "/api/v1/kyc/documents/access", FAIL_CLOSED,
                        rule(IP, 20, Duration.ofMinutes(15)), rule(ACCOUNT, 5, Duration.ofMinutes(15))),
                policy("public-leads", "POST", "/api/v1/public/leads", EVICT,
                        rule(IP, 60, Duration.ofHours(1)), rule(ACCOUNT, 20, Duration.ofHours(1))),
                policy("public-reports", "POST", "/api/v1/public/reports", EVICT,
                        rule(IP, 20, Duration.ofHours(1)), rule(ACCOUNT, 10, Duration.ofHours(1))),
                policy("geocoding", "GET", "/api/v1/public/geocoding", EVICT,
                        rule(IP, 60, Duration.ofMinutes(1))),
                // S2 search/map v2: dearer than a plain read (engine call + count); generous for people paging and panning.
                policy("search-v2", "GET", "/api/v2/listings/search", EVICT,
                        rule(IP, 300, Duration.ofMinutes(1))),
                policy("search-map-v2", "GET", "/api/v2/listings/map", EVICT,
                        rule(IP, 300, Duration.ofMinutes(1))),
                // Rebuild/rollback/cleanup of the search index: a handful per hour is plenty.
                policy("admin-search-index", "POST", "/api/v2/admin/search/**", FAIL_CLOSED,
                        rule(ACCOUNT, 20, Duration.ofHours(1))),
                // S6: token-bearing public endpoints (unsubscribe links, shared shortlist links): bounded guessing.
                policy("public-unsubscribe", "GET", "/api/v1/public/unsubscribe", EVICT,
                        rule(IP, 30, Duration.ofMinutes(15))),
                policy("public-unsubscribe-apply", "POST", "/api/v1/public/unsubscribe", EVICT,
                        rule(IP, 30, Duration.ofMinutes(15))),
                policy("public-shortlist", "GET", "/api/v1/public/shortlists/**", EVICT,
                        rule(IP, 120, Duration.ofMinutes(1))),
                // S8: consent decisions (banner/preferences); a visitor decides a handful of times at most.
                policy("analytics-consent", "POST", "/api/v1/events/consent", EVICT,
                        rule(IP, 30, Duration.ofMinutes(15))),
                // S7: prerendered HTML for crawlers and first page loads (a page render is a few indexed reads).
                policy("prerender", "GET", "/render/**", EVICT,
                        rule(IP, 600, Duration.ofMinutes(1))),
                // S7: CMS preview links are bearer secrets: bounded guessing.
                policy("cms-preview", "GET", "/api/v1/public/articles/preview/**", EVICT,
                        rule(IP, 60, Duration.ofMinutes(15))),
                policy("analytics-events", "POST", "/api/v1/events", EVICT,
                        rule(IP, 120, Duration.ofMinutes(1))),
                // S1 signed media (follow-up): signing is a cheap HMAC, but each signed GET streams an object.
                policy("media-signed-urls", "POST", "/api/v1/media/signed-urls", EVICT,
                        rule(IP, 240, Duration.ofMinutes(1)), rule(ACCOUNT, 120, Duration.ofMinutes(1))),
                policy("media-signed-get", "GET", "/api/v1/media/signed/**", EVICT,
                        rule(IP, 600, Duration.ofMinutes(1))),
                // Uploads are scanned by ClamAV and written to object storage: far more expensive than a read.
                policy("media-upload", "POST", "/api/v1/media/**", EVICT,
                        rule(IP, 120, Duration.ofHours(1)), rule(ACCOUNT, 120, Duration.ofHours(1))),
                policy("api-default", null, "/api/**", EVICT,
                        rule(IP, 1200, Duration.ofMinutes(1)), rule(ACCOUNT, 600, Duration.ofMinutes(1))));
    }

    /** Policy for the request, or {@code null} when the path is not rate limited. */
    public RateLimitPolicy resolve(String method, String path) {
        String normalized = normalizePath(path);
        for (RateLimitPolicy policy : policies) {
            if (policy.matches(method, normalized)) return policy;
        }
        return null;
    }

    public List<RateLimitPolicy> all() { return policies; }

    /** Lower-case, duplicate slashes collapsed, no trailing slash, so {@code /API//v1/auth/login/} counts as login. */
    public static String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        String normalized = REPEATED_SLASHES.matcher(path.toLowerCase(Locale.ROOT)).replaceAll("/");
        return normalized.length() > 1 && normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static RateLimitPolicy apply(RateLimitPolicy policy, Map<String, RateLimitProperties.RuleOverride> overrides,
                                         int multiplier) {
        Map<RateLimitDimension, RateLimitRule> rules = new LinkedHashMap<>();
        policy.rules().forEach(rule -> rules.put(rule.dimension(), rule));
        overrides.forEach((dimensionTag, override) -> {
            RateLimitDimension dimension;
            try {
                dimension = RateLimitDimension.fromTag(dimensionTag);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Chiều giới hạn không hợp lệ '" + dimensionTag + "' trong policy " + policy.name(), ex);
            }
            RateLimitRule current = rules.get(dimension);
            Integer limit = override.getLimit() != null ? override.getLimit() : current == null ? null : current.limit();
            Duration window = override.getWindow() != null ? override.getWindow() : current == null ? null : current.window();
            if (limit == null || window == null) {
                throw new IllegalStateException("Policy " + policy.name() + " thêm chiều " + dimensionTag + " cần cả limit và window");
            }
            rules.put(dimension, new RateLimitRule(dimension, limit, window));
        });
        List<RateLimitRule> scaled = rules.values().stream()
                .map(rule -> new RateLimitRule(rule.dimension(), (int) Math.min(Integer.MAX_VALUE, (long) rule.limit() * multiplier), rule.window()))
                .toList();
        return new RateLimitPolicy(policy.name(), policy.method(), policy.path(), policy.failureMode(), scaled);
    }

    private static RateLimitPolicy policy(String name, String method, String path, RateLimitFailureMode mode, RateLimitRule... rules) {
        return new RateLimitPolicy(name, method, path, mode, List.of(rules));
    }

    private static RateLimitRule rule(RateLimitDimension dimension, int limit, Duration window) {
        return new RateLimitRule(dimension, limit, window);
    }
}
