package com.company.bds.shared.security;

import com.company.bds.iam.application.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Component
public class AuditTrailFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuditTrailFilter.class);
    private final JdbcTemplate jdbc;
    public AuditTrailFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            UUID actor = authentication != null && authentication.getPrincipal() instanceof AuthService.UserAccount user ? user.id() : null;
            String previous = jdbc.query("SELECT event_hash FROM audit_events ORDER BY occurred_at DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : "GENESIS");
            String fingerprint = AuthService.sha256(String.valueOf(request.getRemoteAddr()) + ':' +
                    String.valueOf(request.getHeader("User-Agent")));
            Instant now = Instant.now();
            String material = previous + '|' + now + '|' + actor + '|' + request.getMethod() + '|' + request.getRequestURI() + '|' + response.getStatus();
            jdbc.update("INSERT INTO audit_events(id,occurred_at,actor_id,action,resource,result_status,client_fingerprint,previous_hash,event_hash) VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), Timestamp.from(now), actor, request.getMethod(), request.getRequestURI(), response.getStatus(), fingerprint, previous,
                    AuthService.sha256(material));
        } catch (Exception ex) {
            log.error("audit_write_failed method={} path={} status={}", request.getMethod(), request.getRequestURI(), response.getStatus());
        }
    }
}
