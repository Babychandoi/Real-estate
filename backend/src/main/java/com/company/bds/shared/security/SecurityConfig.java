package com.company.bds.shared.security;

import com.company.bds.shared.error.ProblemDetails;
import com.company.bds.shared.error.ProblemResponses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final BearerTokenFilter bearerTokenFilter;
    private final RequestRateLimitFilter rateLimitFilter;
    private final AccountRateLimitFilter accountRateLimitFilter;
    private final AuditTrailFilter auditTrailFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(BearerTokenFilter bearerTokenFilter,
                          RequestRateLimitFilter rateLimitFilter,
                          AccountRateLimitFilter accountRateLimitFilter,
                          AuditTrailFilter auditTrailFilter,
                          @Value("${app.security.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}") String origins) {
        this.bearerTokenFilter = bearerTokenFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.accountRateLimitFilter = accountRateLimitFilter;
        this.auditTrailFilter = auditTrailFilter;
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String[] posters = Roles.anyOf(Roles.POSTERS);
        String[] staff = Roles.anyOf(Roles.STAFF);
        String[] leadInbox = Roles.anyOf(Roles.STAFF, Roles.POSTERS);
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> ProblemResponses.write(response, null,
                                ProblemDetails.of(401, "UNAUTHORIZED", "Chưa xác thực danh tính", "Yêu cầu đăng nhập",
                                        request.getRequestURI())))
                        .accessDeniedHandler((request, response, ex) -> ProblemResponses.write(response, null,
                                ProblemDetails.of(403, "FORBIDDEN", "Từ chối quyền truy cập",
                                        "Bạn không có quyền thực hiện thao tác này", request.getRequestURI()))))
                .authorizeHttpRequests(auth -> auth
                        // SSE: completing a stream re-dispatches the request (ASYNC) after the response is committed; the
                        // original request was already authorised, so the async dispatch must not be denied (audit F12).
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                        // S9: the error dispatch of an already-authorised request renders its Problem Details
                        // (ProblemErrorController); it serves nothing else.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/admin/login", "/api/v1/auth/register", "/api/v1/auth/resend-verification", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/verify-email").permitAll()
                        // S5-B: token pages and the staff second factor (the challenge token is the credential there).
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-email", "/api/v1/auth/password-reset/status",
                                "/api/v1/auth/admin/mfa/verify", "/api/v1/auth/admin/mfa/enroll",
                                "/api/v1/auth/admin/mfa/enroll/confirm").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**", "/api/v1/listings/search", "/api/v1/listings/by-slug/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/reports").permitAll()
                        // S6: one-click unsubscribe from alert e-mails (RFC 8058) carries its own token, no session.
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/unsubscribe").permitAll()
                        // S2: public read API v2 (search, map, detail, price history, similar, sellers) and its admin side.
                        .requestMatchers(HttpMethod.GET, "/api/v2/listings/**", "/api/v2/public/**").permitAll()
                        // S7: prerendered pages (HTML shell + metadata) for every public route
                        .requestMatchers(HttpMethod.GET, "/render", "/render/**").permitAll()
                        .requestMatchers("/api/v2/admin/**").hasRole("ADMIN")
                        // Product analytics ingestion: anonymous allowed; a valid bearer token only adds the user id.
                        .requestMatchers(HttpMethod.POST, "/api/v1/events", "/api/v1/events/consent").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/leads").authenticated()
                        .requestMatchers("/api/v1/listings/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/listings", "/api/v1/listings/*/submit",
                                "/api/v1/listings/*/visibility", "/api/v1/listings/estimate-price",
                                "/api/v1/listings/quality-score").hasAnyRole(posters)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/listings/*/draft").hasAnyRole(posters)
                        .requestMatchers(HttpMethod.GET, "/api/v1/listings/my-listings").hasAnyRole(posters)
                        .requestMatchers(HttpMethod.GET, "/api/v1/listings/{id}").permitAll()
                        .requestMatchers("/api/v1/admin/users/**", "/api/v1/admin/listings/**", "/api/v1/admin/audit-chain/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/billing/plans").permitAll()
                        .requestMatchers("/api/v1/billing/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/billing/**").hasAnyRole(posters)
                        .requestMatchers("/api/v1/moderation/**", "/api/v1/analytics/**").hasAnyRole(staff)
                        .requestMatchers(HttpMethod.GET, "/api/v1/leads/sent").authenticated()
                        // S3b: requester side of inquiries and appointment actions; services check the actor is a party.
                        .requestMatchers("/api/v1/me/inquiries", "/api/v1/me/inquiries/**", "/api/v1/appointments/**").authenticated()
                        // Owner-side lead inbox; the services still restrict non-staff to leads of their own listings.
                        .requestMatchers("/api/v1/leads/**").hasAnyRole(leadInbox)
                        // The broker workspace stays broker-only; owners get the simplified dashboard in my-listings/my-leads.
                        .requestMatchers("/api/v1/broker/**").hasAnyRole(Roles.ADMIN, Roles.BROKER)
                        // Signed media URLs are capabilities (HMAC, expiry): no session, see MediaUrlSigner.
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/signed/**").permitAll()
                        .requestMatchers("/api/v1/media/**").authenticated()
                        .requestMatchers("/api/v1/reports/**").hasAnyRole(staff)
                        .requestMatchers("/api/v1/kyc/queue", "/api/v1/kyc/*/approve", "/api/v1/kyc/*/reject", "/api/v1/kyc/*/revoke",
                                "/api/v1/verifications/**").hasAnyRole(staff)
                        .requestMatchers("/api/v1/catalog/**", "/api/v1/cms/**").hasAnyRole(staff)
                        .requestMatchers(HttpMethod.POST, "/api/v1/transactions/deposits/*/release", "/api/v1/transactions/deposits/*/refund").hasRole("ADMIN")
                        .requestMatchers("/api/v1/transactions/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v2/me/listings/*/preview", "/api/v2/me/listings/*/draft")
                                .hasAnyRole(Roles.anyOf(Roles.POSTERS, Roles.STAFF))
                        .requestMatchers("/api/v2/me/listings", "/api/v2/me/listings/**").hasAnyRole(posters)
                        .requestMatchers(HttpMethod.POST, "/api/v1/me/become-owner").authenticated()
                        // S6: saved listings, shortlists, saved searches, notification preferences of the signed-in user.
                        .requestMatchers("/api/v1/me/**").authenticated()
                        .requestMatchers("/api/v1/kyc/**", "/api/v1/listings/**", "/api/v1/auth/**", "/api/v1/billing/**", "/api/v1/notifications/**").authenticated()
                        .anyRequest().denyAll())
                // IP/e-mail quotas before the bearer-token lookup (a token-spray flood never reaches the database),
                // account quotas right after it, and both before the audit trail (rejected floods write no rows).
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(bearerTokenFilter, RequestRateLimitFilter.class)
                .addFilterAfter(accountRateLimitFilter, BearerTokenFilter.class)
                .addFilterAfter(auditTrailFilter, AccountRateLimitFilter.class);
        return http.build();
    }

    @Bean public static PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key", "Last-Event-ID",
                "X-Request-Id", "traceparent"));
        // Headers the SPA reads: v1 profile total (X-Total-Count), idempotent replays (Idempotent-Replayed, X-Order-Reused).
        configuration.setExposedHeaders(List.of("X-Request-Id", "X-Total-Count", "Idempotent-Replayed", "X-Order-Reused"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
