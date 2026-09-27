package com.company.bds.shared.security;

import com.company.bds.shared.security.ratelimit.RateLimitDecision;
import com.company.bds.shared.security.ratelimit.RateLimitDimension;
import com.company.bds.shared.security.ratelimit.RateLimitPolicies;
import com.company.bds.shared.security.ratelimit.RateLimitPolicy;
import com.company.bds.shared.security.ratelimit.RateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Rate limiter v2 end to end through the real filter chain and a real Redis (audit F13.1–F13.3).
 * Needs the shared test Redis: {@code eval "$(scripts/test-infra.sh env)"}; this stream uses DB 7.
 */
@SpringBootTest(properties = {
        "app.security.rate-limit.limit-multiplier=1",
        "app.security.rate-limit.policies.api-default.account.limit=5",
        "app.security.rate-limit.policies.auth-login.email.limit=3",
        "spring.data.redis.database=7"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestRateLimitFilterTests {
    /** The Nginx container as seen from the backend inside the Compose network. */
    private static final String NGINX = "172.18.0.2";
    private static final String VERIFY_EMAIL = "/api/v1/auth/verify-email";
    private static final int VERIFY_EMAIL_IP_LIMIT = 20;

    @Autowired MockMvc mockMvc;
    @Autowired StringRedisTemplate redis;
    @Autowired RateLimiter limiter;
    @Autowired RateLimitPolicies policies;
    @Autowired MeterRegistry meters;
    @Autowired ObjectMapper mapper;

    @DynamicPropertySource
    static void sharedTestRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> requiredEnv("BDS_TEST_REDIS_HOST"));
        registry.add("spring.data.redis.port", () -> requiredEnv("BDS_TEST_REDIS_PORT"));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set: run eval \"$(scripts/test-infra.sh env)\" (shared test Redis) before the tests");
        }
        return value;
    }

    @BeforeEach
    void startFromEmptyCounters() {
        Set<String> keys = redis.keys("bds:rl:v2:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    private static MockHttpServletRequestBuilder verifyEmailFrom(String peer, String realIp) {
        MockHttpServletRequestBuilder request = get(VERIFY_EMAIL).param("token", "not-a-real-token-" + UUID.randomUUID())
                .with(servletRequest -> { servletRequest.setRemoteAddr(peer); return servletRequest; });
        return realIp == null ? request : request.header("X-Real-IP", realIp);
    }

    private int status(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void twoClientsBehindTheTrustedProxyHaveSeparateQuotas() throws Exception {
        for (int i = 0; i < VERIFY_EMAIL_IP_LIMIT; i++) {
            assertThat(status(verifyEmailFrom(NGINX, "203.0.113.10"))).isNotEqualTo(429);
        }
        assertThat(status(verifyEmailFrom(NGINX, "203.0.113.10"))).isEqualTo(429);

        for (int i = 0; i < VERIFY_EMAIL_IP_LIMIT; i++) {
            assertThat(status(verifyEmailFrom(NGINX, "203.0.113.11"))).as("second client, request %d", i + 1).isNotEqualTo(429);
        }
    }

    @Test
    void forgedRealIpFromAnUntrustedPeerIsIgnored() throws Exception {
        String directClient = "198.51.100.23";
        for (int i = 0; i < VERIFY_EMAIL_IP_LIMIT; i++) {
            assertThat(status(verifyEmailFrom(directClient, "203.0.113." + (100 + i)))).isNotEqualTo(429);
        }
        assertThat(status(verifyEmailFrom(directClient, "203.0.113.250")))
                .as("rotating X-Real-IP must not give an untrusted peer a fresh quota").isEqualTo(429);
    }

    @Test
    void accountQuotaFollowsTheUserAcrossAddresses() throws Exception {
        String account = UUID.randomUUID().toString();
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            statuses.add(status(get("/api/v1/leads/sent").with(user(account).roles("USER")).header("X-Real-IP", "203.0.113." + (20 + i))
                    .with(servletRequest -> { servletRequest.setRemoteAddr(NGINX); return servletRequest; })));
        }
        assertThat(statuses.subList(0, 5)).containsOnly(200);
        assertThat(statuses.get(5)).isEqualTo(429);

        assertThat(status(get("/api/v1/leads/sent").with(user(UUID.randomUUID().toString()).roles("USER")).header("X-Real-IP", "203.0.113.25")
                .with(servletRequest -> { servletRequest.setRemoteAddr(NGINX); return servletRequest; })))
                .as("another account on the same address keeps its own quota").isEqualTo(200);
    }

    @Test
    void loginAttemptsAreCountedPerTargetedEmailAcrossAddresses() throws Exception {
        String victim = "victim-" + UUID.randomUUID() + "@example.test";
        String[] spellings = {victim, " " + victim.toUpperCase() + " ", victim, victim};
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < spellings.length; i++) {
            statuses.add(status(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("email", spellings[i], "password", "Wrong-Password-2026!")))
                    .header("X-Real-IP", "198.51.100." + (40 + i))
                    .with(servletRequest -> { servletRequest.setRemoteAddr(NGINX); return servletRequest; })));
        }
        assertThat(statuses.subList(0, 3)).doesNotContain(429);
        assertThat(statuses.get(3)).as("4th attempt on one account from a 4th address").isEqualTo(429);

        MvcResult otherAccount = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", "someone-else@example.test", "password", "Wrong-Password-2026!")))
                .header("X-Real-IP", "198.51.100.43")
                .with(servletRequest -> { servletRequest.setRemoteAddr(NGINX); return servletRequest; })).andReturn();
        assertThat(otherAccount.getResponse().getStatus()).as("other accounts are unaffected").isEqualTo(400);
        // The controller parsed the replayed body and checked the credentials (not a body-parsing error).
        assertThat(mapper.readTree(otherAccount.getResponse().getContentAsByteArray()).get("detail").asText())
                .isEqualTo("Email hoặc mật khẩu không chính xác.");
    }

    @Test
    void rejectionIsProblemDetailsWithRetryAfterEqualToTheRedisTtl() throws Exception {
        for (int i = 0; i < VERIFY_EMAIL_IP_LIMIT; i++) status(verifyEmailFrom(NGINX, "203.0.113.60"));
        MvcResult rejected = mockMvc.perform(verifyEmailFrom(NGINX, "203.0.113.60")).andReturn();

        assertThat(rejected.getResponse().getStatus()).isEqualTo(429);
        assertThat(rejected.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(rejected.getResponse().getHeader("Cache-Control")).contains("no-store");
        JsonNode problem = mapper.readTree(rejected.getResponse().getContentAsByteArray());
        assertThat(problem.get("status").asInt()).isEqualTo(429);
        assertThat(problem.get("code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(problem.get("type").asText()).isEqualTo("https://api.bds.vn/problems/rate-limited");
        assertThat(problem.get("instance").asText()).isEqualTo(VERIFY_EMAIL);
        assertThat(problem.get("detail").asText()).contains("phút");

        Set<String> keys = redis.keys("bds:rl:v2:auth-verify-email:ip:*");
        assertThat(keys).hasSize(1);
        long ttlMillis = redis.getExpire(keys.iterator().next(), TimeUnit.MILLISECONDS);
        long retryAfter = Long.parseLong(rejected.getResponse().getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(Math.max(1, (ttlMillis + 999) / 1000 - 1), (ttlMillis + 999) / 1000 + 1);
        assertThat(retryAfter).isBetween(880L, 900L);
    }

    @Test
    void concurrentRequestsNeverExceedTheLimitAndEveryCounterExpires() throws Exception {
        RateLimitPolicy verify = policies.resolve("GET", VERIFY_EMAIL);
        Map<RateLimitDimension, String> client = Map.of(RateLimitDimension.IP, "203.0.113.77");
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 160; i++) attempts.add(() -> limiter.check(verify, client).allowed());
            long allowed = 0;
            for (Future<Boolean> attempt : pool.invokeAll(attempts)) if (attempt.get()) allowed++;
            assertThat(allowed).as("atomic INCR+PEXPIRE: exactly the limit passes").isEqualTo(VERIFY_EMAIL_IP_LIMIT);
        } finally {
            pool.shutdownNow();
        }
        RateLimitDecision decision = limiter.check(verify, client);
        assertThat(decision.localFallback()).as("decided by Redis, not the fallback").isFalse();

        status(verifyEmailFrom(NGINX, "203.0.113.78"));
        Set<String> keys = redis.keys("bds:rl:v2:*");
        assertThat(keys).isNotEmpty();
        for (String key : keys) {
            assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).as("TTL of %s", key).isPositive();
        }
    }

    @Test
    void rejectionsAreCountedPerPolicyAndDimension() throws Exception {
        double before = meters.get("bds.ratelimit.rejected").tags("policy", "auth-verify-email", "dimension", "ip").counter().count();
        for (int i = 0; i < VERIFY_EMAIL_IP_LIMIT + 3; i++) status(verifyEmailFrom(NGINX, "203.0.113.90"));

        assertThat(meters.get("bds.ratelimit.rejected").tags("policy", "auth-verify-email", "dimension", "ip").counter().count())
                .isEqualTo(before + 3);
        assertThat(meters.get("bds.ratelimit.redis.available").gauge().value()).isEqualTo(1.0);
    }
}
