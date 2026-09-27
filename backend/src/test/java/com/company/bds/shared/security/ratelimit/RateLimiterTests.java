package com.company.bds.shared.security.ratelimit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** F13.2/F13.3 decisions, with a real Redis client whose server is unreachable. */
class RateLimiterTests {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-28T00:00:00Z"));
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private LettuceConnectionFactory unreachable;

    @AfterEach
    void closeRedisClient() {
        if (unreachable != null) unreachable.destroy();
    }

    private static RateLimitPolicy loginPolicy() {
        return new RateLimitPolicy("auth-login", "POST", "/api/v1/auth/login", RateLimitFailureMode.FAIL_CLOSED, List.of(
                new RateLimitRule(RateLimitDimension.IP, 5, Duration.ofMinutes(1)),
                new RateLimitRule(RateLimitDimension.EMAIL, 2, Duration.ofMinutes(15))));
    }

    private static RateLimitPolicy browsePolicy() {
        return new RateLimitPolicy("api-default", null, "/api/**", RateLimitFailureMode.EVICT, List.of(
                new RateLimitRule(RateLimitDimension.IP, 2, Duration.ofMinutes(1))));
    }

    private RateLimiter limiter(StringRedisTemplate redis, int generalCapacity, RateLimitPolicy... policies) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setLocalMaxEntries(generalCapacity);
        properties.setLocalStrictMaxEntries(1_000);
        properties.setRedisRetryInterval(Duration.ofSeconds(5));
        return new RateLimiter(properties, new RateLimitPolicies(List.of(policies), properties), redis, meters, clock);
    }

    private StringRedisTemplate unreachableRedis() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        unreachable = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", closedPort),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
        unreachable.afterPropertiesSet();
        unreachable.start();
        return new StringRedisTemplate(unreachable);
    }

    @Test
    void redisOutageStillLimitsAndOnlyProbesRedisOncePerRetryInterval() throws IOException {
        RateLimitPolicy browse = browsePolicy();
        RateLimiter limiter = limiter(unreachableRedis(), 1_000, browse);
        Map<RateLimitDimension, String> client = Map.of(RateLimitDimension.IP, "203.0.113.9");

        assertThat(limiter.check(browse, client).allowed()).isTrue();
        assertThat(limiter.check(browse, client).allowed()).isTrue();
        RateLimitDecision third = limiter.check(browse, client);

        assertThat(third.allowed()).isFalse();
        assertThat(third.localFallback()).isTrue();
        assertThat(third.violated()).isEqualTo(RateLimitDimension.IP);
        assertThat(limiter.redisAvailable()).isFalse();
        assertThat(meters.get("bds.ratelimit.redis.available").gauge().value()).isZero();
        assertThat(meters.get("bds.ratelimit.redis.errors").counter().count()).isEqualTo(1.0);
        assertThat(meters.get("bds.ratelimit.fallback").tag("policy", "api-default").counter().count()).isEqualTo(3.0);
        assertThat(meters.get("bds.ratelimit.rejected").tags("policy", "api-default", "dimension", "ip").counter().count()).isEqualTo(1.0);

        clock.advance(Duration.ofSeconds(6));
        limiter.check(browse, client);
        assertThat(meters.get("bds.ratelimit.redis.errors").counter().count()).as("probed again after the interval").isEqualTo(2.0);
    }

    @Test
    void floodOfDistinctClientsDuringAnOutageKeepsMemoryBounded() throws IOException {
        RateLimitPolicy browse = browsePolicy();
        RateLimiter limiter = limiter(unreachableRedis(), 5_000, browse);

        for (int i = 0; i < 100_000; i++) {
            limiter.check(browse, Map.of(RateLimitDimension.IP, "flood-" + i));
        }

        assertThat(limiter.localEntries(RateLimitFailureMode.EVICT)).isLessThanOrEqualTo(limiter.localCapacity(RateLimitFailureMode.EVICT));
        assertThat(limiter.localCapacity(RateLimitFailureMode.EVICT)).isLessThanOrEqualTo(5_000);
        assertThat(meters.get("bds.ratelimit.local.entries").tag("table", "general").gauge().value()).isLessThanOrEqualTo(5_000);
    }

    @Test
    void retryAfterIsTheTimeUntilTheViolatedWindowResets() {
        RateLimitPolicy login = loginPolicy();
        RateLimiter limiter = limiter(null, 1_000, login);
        Map<RateLimitDimension, String> attempt = Map.of(RateLimitDimension.IP, "203.0.113.9", RateLimitDimension.EMAIL, "victim@example.test");

        limiter.check(login, attempt);
        limiter.check(login, attempt);
        RateLimitDecision blocked = limiter.check(login, attempt);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.violated()).isEqualTo(RateLimitDimension.EMAIL);
        assertThat(blocked.retryAfterSeconds()).isEqualTo(900);

        clock.advance(Duration.ofMillis(885_200));
        assertThat(limiter.check(login, attempt).retryAfterSeconds()).as("rounded up, never 0").isEqualTo(15);
        clock.advance(Duration.ofSeconds(15));
        assertThat(limiter.check(login, attempt).allowed()).isTrue();
    }

    @Test
    void emailQuotaFollowsTheTargetedAccountAcrossAddresses() {
        RateLimitPolicy login = loginPolicy();
        RateLimiter limiter = limiter(null, 1_000, login);

        assertThat(limiter.check(login, Map.of(RateLimitDimension.IP, "203.0.113.1", RateLimitDimension.EMAIL, "victim@example.test")).allowed()).isTrue();
        assertThat(limiter.check(login, Map.of(RateLimitDimension.IP, "203.0.113.2", RateLimitDimension.EMAIL, "victim@example.test")).allowed()).isTrue();
        RateLimitDecision third = limiter.check(login, Map.of(RateLimitDimension.IP, "203.0.113.3", RateLimitDimension.EMAIL, "victim@example.test"));
        assertThat(third.allowed()).isFalse();
        assertThat(third.violated()).isEqualTo(RateLimitDimension.EMAIL);
        assertThat(limiter.check(login, Map.of(RateLimitDimension.IP, "203.0.113.3", RateLimitDimension.EMAIL, "other@example.test")).allowed()).isTrue();
    }

    @Test
    void rulesWithoutASubjectAreSkippedAndKeysNeverContainTheSubject() {
        RateLimitPolicy login = loginPolicy();
        RateLimiter limiter = limiter(null, 1_000, login);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.check(login, Map.of(RateLimitDimension.IP, "203.0.113.4")).allowed()).isTrue();
        }
        assertThat(limiter.check(login, Map.of()).allowed()).isTrue();

        RateLimitKey key = RateLimitKey.of(new byte[0], login, login.rules().get(1), "victim@example.test");
        assertThat(key.redisKey()).startsWith("bds:rl:v2:auth-login:email:").doesNotContain("victim").hasSize("bds:rl:v2:auth-login:email:".length() + 32);
        assertThat(RateLimitKey.of("pepper".getBytes(), login, login.rules().get(1), "victim@example.test").redisKey())
                .as("the pepper changes every key").isNotEqualTo(key.redisKey());
    }

    @Test
    void policyOverridesAndTestMultiplierApplyAndTyposFailFast() {
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitProperties.RuleOverride tighter = new RateLimitProperties.RuleOverride();
        tighter.setLimit(4);
        properties.getPolicies().put("auth-login", Map.of("email", tighter));
        properties.setLimitMultiplier(10);

        RateLimitPolicy resolved = new RateLimitPolicies(properties).resolve("POST", "/API//v1/auth/login/");
        assertThat(resolved.name()).isEqualTo("auth-login");
        assertThat(resolved.rules()).extracting(RateLimitRule::limit).containsExactly(300, 40);
        assertThat(new RateLimitPolicies(new RateLimitProperties()).resolve("GET", "/api/v1/auth/me").name()).isEqualTo("api-default");
        assertThat(new RateLimitPolicies(new RateLimitProperties()).resolve("GET", "/actuator/health")).isNull();

        RateLimitProperties typo = new RateLimitProperties();
        typo.getPolicies().put("auth-logn", Map.of("email", tighter));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RateLimitPolicies(typo)).isInstanceOf(IllegalStateException.class);
    }
}
