package com.company.bds.shared.security.ratelimit;

import java.util.List;

/**
 * A named route policy. {@code method} {@code null} matches any method; {@code path} is either an exact path or a
 * prefix ending in {@code /**}. Paths are compared after {@link RateLimitPolicies#normalizePath(String)}.
 */
public record RateLimitPolicy(String name, String method, String path, RateLimitFailureMode failureMode,
                              List<RateLimitRule> rules) {
    public RateLimitPolicy {
        if (name == null || !name.matches("[a-z0-9-]{1,40}")) throw new IllegalArgumentException("Tên policy không hợp lệ: " + name);
        if (rules == null || rules.isEmpty()) throw new IllegalArgumentException("Policy " + name + " cần ít nhất một quy tắc");
        long distinct = rules.stream().map(RateLimitRule::dimension).distinct().count();
        if (distinct != rules.size()) throw new IllegalArgumentException("Policy " + name + " có hai quy tắc cùng chiều");
        rules = List.copyOf(rules);
    }

    public boolean matches(String requestMethod, String normalizedPath) {
        if (method != null && !method.equalsIgnoreCase(requestMethod)) return false;
        if (path.endsWith("/**")) {
            String prefix = path.substring(0, path.length() - 3);
            return normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/");
        }
        return normalizedPath.equals(path);
    }

    public boolean uses(RateLimitDimension dimension) {
        return rules.stream().anyMatch(rule -> rule.dimension() == dimension);
    }
}
