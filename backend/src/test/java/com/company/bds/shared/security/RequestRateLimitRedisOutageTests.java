package com.company.bds.shared.security;

import com.company.bds.shared.security.ratelimit.RateLimitDimension;
import com.company.bds.shared.security.ratelimit.RateLimitPolicies;
import com.company.bds.shared.security.ratelimit.RateLimitPolicy;
import com.company.bds.shared.security.ratelimit.RateLimiter;
import com.company.bds.testsupport.BdsIntegrationTestInitializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Redis unreachable (port 1 refuses connections): the limiter keeps limiting from its bounded local tables
 * (audit F13.3). Credential endpoints fail closed once their table is full; the flood test therefore runs last.
 */
@SpringBootTest(properties = {
        "app.security.rate-limit.limit-multiplier=1",
        "app.security.rate-limit.local-max-entries=5000",
        "app.security.rate-limit.local-strict-max-entries=64",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.timeout=500ms"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = BdsIntegrationTestInitializer.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RequestRateLimitRedisOutageTests {
    private static final String NGINX = "172.18.0.2";

    @Autowired MockMvc mockMvc;
    @Autowired RateLimiter limiter;
    @Autowired RateLimitPolicies policies;
    @Autowired MeterRegistry meters;
    @Autowired ObjectMapper mapper;

    private MvcResult verifyEmail(String clientIp) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/verify-email").param("token", "not-a-real-token-" + UUID.randomUUID())
                .header("X-Real-IP", clientIp)
                .with(request -> { request.setRemoteAddr(NGINX); return request; })).andReturn();
    }

    @Test
    @Order(1)
    void limitsStillApplyWhileRedisIsDown() throws Exception {
        for (int i = 0; i < 20; i++) assertThat(verifyEmail("203.0.113.30").getResponse().getStatus()).isNotEqualTo(429);
        MvcResult rejected = verifyEmail("203.0.113.30");

        assertThat(rejected.getResponse().getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(rejected.getResponse().getHeader("Retry-After"))).isBetween(895L, 900L);
        assertThat(mapper.readTree(rejected.getResponse().getContentAsByteArray()).get("code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(meters.get("bds.ratelimit.redis.available").gauge().value()).isZero();
        assertThat(meters.get("bds.ratelimit.fallback").tag("policy", "auth-verify-email").counter().count()).isGreaterThanOrEqualTo(21);
    }

    @Test
    @Order(2)
    void floodOfDistinctClientsKeepsTheFallbackTableWithinItsCap() {
        RateLimitPolicy browse = policies.resolve("GET", "/api/v1/listings/search");
        for (int i = 0; i < 100_000; i++) {
            limiter.check(browse, Map.of(RateLimitDimension.IP, "flood-" + i));
        }
        assertThat(meters.get("bds.ratelimit.local.entries").tag("table", "general").gauge().value()).isLessThanOrEqualTo(5_000);
    }

    @Test
    @Order(3)
    void credentialEndpointsFailClosedOnceTheirTableIsFull() throws Exception {
        RateLimitPolicy verify = policies.resolve("GET", "/api/v1/auth/verify-email");
        for (int i = 0; i < 2_000; i++) {
            limiter.check(verify, Map.of(RateLimitDimension.IP, "attacker-" + i));
        }
        double before = meters.get("bds.ratelimit.rejected").tags("policy", "auth-verify-email", "dimension", "capacity").counter().count();

        MvcResult newcomer = verifyEmail("198.51.100.99");

        assertThat(newcomer.getResponse().getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(newcomer.getResponse().getHeader("Retry-After"))).isPositive();
        assertThat(meters.get("bds.ratelimit.rejected").tags("policy", "auth-verify-email", "dimension", "capacity").counter().count())
                .isEqualTo(before + 1);
        assertThat(meters.get("bds.ratelimit.local.entries").tag("table", "strict").gauge().value()).isLessThanOrEqualTo(64);
    }
}
