package com.company.bds.shared.security;

import com.company.bds.iam.application.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class AuditTrailFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuditTrailFilter.class);
    private final AuditTrail trail;
    private final ClientIpResolver clientIp;
    public AuditTrailFilter(AuditTrail trail, ClientIpResolver clientIp) { this.trail = trail; this.clientIp = clientIp; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        boolean sensitiveRead = request.getMethod().equals("GET")
                && request.getRequestURI().matches("/api/v1/leads/[^/]+/contact");
        // Anonymous analytics beacons are telemetry, not actions: auditing them would let anyone flood the audit chain.
        boolean analyticsIngestion = (request.getRequestURI().equals("/api/v1/events") || request.getRequestURI().equals("/api/v1/events/consent"));
        return (!sensitiveRead && List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()))
                || !request.getRequestURI().startsWith("/api/") || analyticsIngestion;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        AuditTrail.Entry entry;
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            UUID actor = authentication != null && authentication.getPrincipal() instanceof AuthService.UserAccount user ? user.id() : null;
            // Behind Nginx the socket address is the proxy for everyone; fingerprint the resolved client instead.
            String fingerprint = AuthService.sha256(clientIp.resolve(request) + ':' +
                    String.valueOf(request.getHeader("User-Agent")));
            String resource = request.getRequestURI();
            entry = new AuditTrail.Entry(actor, request.getMethod(), resource.length() > 500 ? resource.substring(0, 500) : resource,
                    response.getStatus(), fingerprint);
        } catch (RuntimeException ex) {
            log.error("audit_write_failed method={} path={} status={} cause={}: {}", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), ex.getClass().getSimpleName(), ex.getMessage());
            return;
        }
        // A plain insert (linked into the hash chain shortly after); retried, counted and logged with its cause when it fails.
        trail.record(entry);
    }
}
