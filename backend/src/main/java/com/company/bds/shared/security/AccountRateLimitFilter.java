package com.company.bds.shared.security;

import com.company.bds.shared.security.ratelimit.RateLimitDecision;
import com.company.bds.shared.security.ratelimit.RateLimitDimension;
import com.company.bds.shared.security.ratelimit.RateLimitPolicies;
import com.company.bds.shared.security.ratelimit.RateLimitPolicy;
import com.company.bds.shared.security.ratelimit.RateLimitProperties;
import com.company.bds.shared.security.ratelimit.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Rate limiting phase 2 of 2: the per-account rules of a policy, counted once the bearer token has identified the
 * user (phase 1, {@link RequestRateLimitFilter}, has already applied the IP and e-mail rules). Anonymous requests have
 * no account and pass. Runs before the audit filter, so rejected requests write no audit rows.
 */
@Component
public class AccountRateLimitFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final RateLimitPolicies policies;
    private final RateLimiter limiter;
    private final ObjectMapper mapper;

    public AccountRateLimitFilter(RateLimitProperties properties, RateLimitPolicies policies, RateLimiter limiter, ObjectMapper mapper) {
        this.enabled = properties.isEnabled();
        this.policies = policies;
        this.limiter = limiter;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        RateLimitPolicy policy = RateLimitResponses.policyFor(policies, request);
        return policy == null || !policy.uses(RateLimitDimension.ACCOUNT);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitPolicy policy = RateLimitResponses.policyFor(policies, request);
        String account = currentAccount();
        if (policy != null && account != null) {
            RateLimitDecision decision = limiter.check(policy, Map.of(RateLimitDimension.ACCOUNT, account));
            if (!decision.allowed()) {
                RateLimitResponses.tooManyRequests(request, response, decision, mapper);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String currentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return CurrentUser.id(authentication).toString();
        } catch (IllegalStateException ex) {
            return authentication.getName();
        }
    }
}
