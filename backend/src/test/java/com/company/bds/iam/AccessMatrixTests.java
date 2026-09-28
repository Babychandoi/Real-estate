package com.company.bds.iam;

import com.company.bds.shared.security.Roles;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Least privilege (F20.4): every API route of the application, not a hand-picked sample. Anonymous callers are refused
 * everywhere except an explicit public allowlist; members are refused on every staff and admin route; moderators on
 * every admin-only route. A new controller that forgets its rule fails here. The matrix is documented in
 * {@code docs/security/ACCESS_MATRIX.md}.
 */
@BdsIntegrationTest
class AccessMatrixTests {
    /** "METHOD pattern" of every route anonymous callers may reach (patterns exactly as declared in the controllers). */
    static final Set<String> PUBLIC = Set.of(
            "POST /api/v1/auth/login", "POST /api/v1/auth/admin/login", "POST /api/v1/auth/register",
            "POST /api/v1/auth/resend-verification", "POST /api/v1/auth/forgot-password", "POST /api/v1/auth/reset-password",
            "GET /api/v1/auth/verify-email", "POST /api/v1/auth/verify-email", "POST /api/v1/auth/password-reset/status",
            "POST /api/v1/auth/admin/mfa/verify", "POST /api/v1/auth/admin/mfa/enroll", "POST /api/v1/auth/admin/mfa/enroll/confirm",
            "POST /api/v1/public/reports", "POST /api/v1/public/unsubscribe", "POST /api/v1/events",
            "GET /api/v1/listings/search", "GET /api/v1/listings/{id}", "GET /api/v1/billing/plans");
    /** Public GET prefixes (read-only public API; handlers only return published data). */
    static final List<String> PUBLIC_GET_PREFIXES = List.of("/api/v1/public/", "/api/v1/listings/by-slug/", "/api/v2/listings/",
            "/api/v2/public/", "/api/v1/media/signed/");
    /** Routes only ADMIN may use. */
    static final List<Pattern> ADMIN_ONLY = List.of(
            Pattern.compile("/api/v1/admin/.*"), Pattern.compile("/api/v2/admin/.*"), Pattern.compile("/api/v1/listings/admin/.*"),
            Pattern.compile("/api/v1/billing/admin/.*"), Pattern.compile("/api/v1/transactions/deposits/[^/]+/(release|refund)"),
            Pattern.compile("/api/v1/moderation/audit-samples/draw"));
    /** Routes for staff (MODERATOR and ADMIN) only. */
    static final List<Pattern> STAFF = List.of(
            Pattern.compile("/api/v1/moderation/.*"), Pattern.compile("/api/v1/analytics/.*"), Pattern.compile("/api/v1/reports(/.*)?"),
            Pattern.compile("/api/v1/verifications(/.*)?"), Pattern.compile("/api/v1/kyc/queue"),
            Pattern.compile("/api/v1/kyc/[^/]+/(approve|reject|revoke)"), Pattern.compile("/api/v1/catalog(/.*)?"),
            Pattern.compile("/api/v1/cms(/.*)?"));

    @Autowired MockMvc mockMvc;
    @Autowired TestData data;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    String member;
    String moderator;

    @BeforeEach
    void setUp() {
        member = "Bearer " + data.sessionFor(data.user().role(Roles.USER).create().id());
        moderator = "Bearer " + data.sessionFor(data.user().role(Roles.MODERATOR).create().id());
    }

    record Route(String method, String pattern, String path) {
        String key() { return method + " " + pattern; }
    }

    private List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/")) continue;
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    routes.add(new Route(method.name(), pattern, concrete(pattern)));
                }
            }
        }
        return routes;
    }

    /** {@code /x/{id}/y/{key:.+}} → {@code /x/<uuid>/y/<uuid>}. */
    private static String concrete(String pattern) {
        return pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString()).replace("/**", "/" + UUID.randomUUID());
    }

    private int status(Route route, String bearer) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(route.method()), route.path())
                .contentType(MediaType.APPLICATION_JSON).content("{}");
        if (bearer != null) builder.header("Authorization", bearer);
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }

    private static boolean isPublic(Route route) {
        return PUBLIC.contains(route.key())
                || ("GET".equals(route.method()) && PUBLIC_GET_PREFIXES.stream().anyMatch(route.pattern()::startsWith));
    }

    private static boolean matches(List<Pattern> patterns, Route route) {
        return patterns.stream().anyMatch(p -> p.matcher(route.path()).matches());
    }

    @Test
    void theApiHasManyRoutesAndEveryPublicAllowlistEntryStillExists() {
        List<Route> routes = routes();
        assertThat(routes).hasSizeGreaterThan(150);
        Set<String> keys = new TreeSet<>();
        routes.forEach(route -> keys.add(route.key()));
        assertThat(keys).as("stale allowlist entries widen access silently").containsAll(PUBLIC);
    }

    @Test
    void anonymousCallersAreRefusedEverywhereExceptThePublicAllowlist() throws Exception {
        List<String> exposed = new ArrayList<>();
        for (Route route : routes()) {
            if (isPublic(route)) continue;
            int status = status(route, null);
            if (status != 401) exposed.add(route.key() + " → " + status);
        }
        assertThat(exposed).as("routes reachable without a session").isEmpty();
    }

    @Test
    void membersAreRefusedOnEveryStaffAndAdminRoute() throws Exception {
        List<String> leaks = new ArrayList<>();
        for (Route route : routes()) {
            if (!matches(STAFF, route) && !matches(ADMIN_ONLY, route)) continue;
            int status = status(route, member);
            if (status != 403) leaks.add(route.key() + " → " + status);
        }
        assertThat(leaks).as("staff/admin routes a USER can reach").isEmpty();
    }

    @Test
    void moderatorsAreRefusedOnEveryAdminOnlyRoute() throws Exception {
        List<String> leaks = new ArrayList<>();
        int checked = 0;
        for (Route route : routes()) {
            if (!matches(ADMIN_ONLY, route)) continue;
            checked++;
            int status = status(route, moderator);
            if (status != 403) leaks.add(route.key() + " → " + status);
        }
        assertThat(checked).isGreaterThan(20);
        assertThat(leaks).as("admin-only routes a MODERATOR can reach").isEmpty();
    }
}
