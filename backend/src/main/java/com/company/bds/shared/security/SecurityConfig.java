package com.company.bds.shared.security;

import jakarta.servlet.http.HttpServletResponse;
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
    private final AuditTrailFilter auditTrailFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(BearerTokenFilter bearerTokenFilter,
                          RequestRateLimitFilter rateLimitFilter,
                          AuditTrailFilter auditTrailFilter,
                          @Value("${app.security.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}") String origins) {
        this.bearerTokenFilter = bearerTokenFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.auditTrailFilter = auditTrailFilter;
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> problem(response, 401, "Yêu cầu đăng nhập"))
                        .accessDeniedHandler((request, response, ex) -> problem(response, 403, "Bạn không có quyền thực hiện thao tác này")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/admin/login", "/api/v1/auth/register", "/api/v1/auth/resend-verification", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/verify-email").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**", "/api/v1/listings/search", "/api/v1/listings/by-slug/**", "/api/v1/listings/{id}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/reports").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/leads").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/billing/plans").permitAll()
                        .requestMatchers("/api/v1/billing/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/moderation/**", "/api/v1/analytics/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers("/api/v1/leads/**").hasAnyRole("ADMIN", "MODERATOR", "BROKER", "USER")
                        .requestMatchers("/api/v1/broker/**").hasAnyRole("ADMIN", "BROKER")
                        .requestMatchers("/api/v1/media/**").authenticated()
                        .requestMatchers("/api/v1/reports/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers("/api/v1/kyc/queue", "/api/v1/kyc/*/approve", "/api/v1/kyc/*/reject",
                                "/api/v1/verifications/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers("/api/v1/catalog/**", "/api/v1/cms/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/transactions/deposits/*/release", "/api/v1/transactions/deposits/*/refund").hasRole("ADMIN")
                        .requestMatchers("/api/v1/transactions/**").authenticated()
                        .requestMatchers("/api/v1/kyc/**", "/api/v1/listings/**", "/api/v1/auth/**", "/api/v1/billing/**", "/api/v1/notifications/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(bearerTokenFilter, RequestRateLimitFilter.class)
                .addFilterAfter(auditTrailFilter, BearerTokenFilter.class);
        return http.build();
    }

    @Bean public static PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static void problem(HttpServletResponse response, int status, String detail) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"title\":\"Truy cập bị từ chối\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
