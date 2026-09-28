package com.company.bds.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Private API areas are never stored by a browser, CDN or proxy (audit F10.2, cache policy §7.3: KYC, leads,
 * billing, admin → {@code private, no-store}).
 *
 * <p>Spring Security already writes {@code no-store} when a controller sets no cache header; this filter makes the
 * policy independent of controllers: for the paths below it sets the no-store headers first and ignores any later
 * attempt to change {@code Cache-Control}, {@code Expires} or {@code Pragma}. New private endpoints outside these
 * prefixes still get the Spring Security default; add their prefix here when they are introduced.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class SensitiveResponseCacheFilter extends OncePerRequestFilter {
    static final String NO_STORE = "no-cache, no-store, max-age=0, must-revalidate";
    private static final Pattern REPEATED_SLASHES = Pattern.compile("/{2,}");
    private static final List<String> SENSITIVE = List.of(
            "/api/v1/auth/**", "/api/v1/kyc/**", "/api/v1/media/**",
            "/api/v1/leads/**", "/api/v1/public/leads", "/api/v1/appointments/**",
            "/api/v1/billing/**", "/api/v1/admin/**", "/api/v1/moderation/**",
            "/api/v1/verifications/**", "/api/v1/listings/*/verifications",
            "/api/v1/reports/**", "/api/v1/public/reports",
            "/api/v1/public/unsubscribe", "/api/v1/public/shortlists/**",
            "/api/v1/transactions/**", "/api/v1/broker/**", "/api/v1/notifications/**", "/api/v1/analytics/**",
            "/api/v1/listings/my-listings", "/api/v1/listings/admin/**", "/api/v1/listings/*/draft",
            "/api/v1/cms/**", "/api/v1/catalog/**", "/api/v1/events", "/api/v1/events/consent",
            "/api/v2/admin/**", "/api/v1/me/**", "/api/v2/me/**");
    /** Public catalogue inside a private prefix. */
    private static final List<String> PUBLIC_EXCEPTIONS = List.of("/api/v1/billing/plans");

    private final AntPathMatcher matcher = new AntPathMatcher();

    public SensitiveResponseCacheFilter() {
        matcher.setCaseSensitive(false);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isSensitive(pathOf(request));
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        NoStoreResponse wrapped = new NoStoreResponse(response);
        wrapped.applyNoStore();
        chain.doFilter(request, wrapped);
    }

    boolean isSensitive(String path) {
        String normalized = REPEATED_SLASHES.matcher(path).replaceAll("/");
        if (PUBLIC_EXCEPTIONS.stream().anyMatch(pattern -> matcher.match(pattern, normalized))) return false;
        return SENSITIVE.stream().anyMatch(pattern -> matcher.match(pattern, normalized));
    }

    /** Decoded like Spring MVC routing, so {@code /api/v1/%6Byc/queue} is recognised as the KYC area. */
    private static String pathOf(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }

    private static final class NoStoreResponse extends HttpServletResponseWrapper {
        private NoStoreResponse(HttpServletResponse response) { super(response); }

        private void applyNoStore() {
            super.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
            super.setHeader(HttpHeaders.PRAGMA, "no-cache");
            super.setHeader(HttpHeaders.EXPIRES, "0");
        }

        private static boolean isCacheHeader(String name) {
            return HttpHeaders.CACHE_CONTROL.equalsIgnoreCase(name) || HttpHeaders.EXPIRES.equalsIgnoreCase(name)
                    || HttpHeaders.PRAGMA.equalsIgnoreCase(name);
        }

        @Override public void setHeader(String name, String value) { if (!isCacheHeader(name)) super.setHeader(name, value); }
        @Override public void addHeader(String name, String value) { if (!isCacheHeader(name)) super.addHeader(name, value); }
        @Override public void setDateHeader(String name, long date) { if (!isCacheHeader(name)) super.setDateHeader(name, date); }
        @Override public void addDateHeader(String name, long date) { if (!isCacheHeader(name)) super.addDateHeader(name, date); }

        @Override
        public void reset() {
            super.reset();
            applyNoStore();
        }
    }
}
