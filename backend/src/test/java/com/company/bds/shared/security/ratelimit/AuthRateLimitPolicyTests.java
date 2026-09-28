package com.company.bds.shared.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** S5-B endpoints and S1 signed media get their own limits instead of the generous api-default budget. */
class AuthRateLimitPolicyTests {
    private final RateLimitPolicies policies = new RateLimitPolicies(new RateLimitProperties());

    private RateLimitPolicy resolve(String method, String path) { return policies.resolve(method, path); }

    @Test
    void newCredentialEndpointsFailClosedWithTightLimits() {
        assertThat(resolve("POST", "/api/v1/auth/admin/mfa/verify").name()).isEqualTo("auth-mfa-verify");
        assertThat(resolve("POST", "/api/v1/auth/admin/mfa/enroll").name()).isEqualTo("auth-mfa-enroll");
        assertThat(resolve("POST", "/api/v1/auth/admin/mfa/enroll/confirm").name()).isEqualTo("auth-mfa-enroll");
        assertThat(resolve("POST", "/API//v1/auth/admin/mfa/verify/").name()).as("normalized").isEqualTo("auth-mfa-verify");
        assertThat(resolve("POST", "/api/v1/auth/verify-email").name()).isEqualTo("auth-verify-email-post");
        assertThat(resolve("GET", "/api/v1/auth/verify-email").name()).isEqualTo("auth-verify-email");
        assertThat(resolve("POST", "/api/v1/auth/password-reset/status").name()).isEqualTo("auth-reset-status");
        for (String name : new String[]{"auth-mfa-verify", "auth-mfa-enroll", "auth-verify-email-post", "auth-reset-status",
                "account-password", "account-mfa-codes"}) {
            RateLimitPolicy policy = policies.all().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
            assertThat(policy.failureMode()).as(name).isEqualTo(RateLimitFailureMode.FAIL_CLOSED);
            assertThat(policy.rules()).as(name).allSatisfy(rule -> assertThat(rule.limit()).isLessThanOrEqualTo(30));
        }
    }

    @Test
    void accountSecretsAreLimitedPerAccountAsWell() {
        RateLimitPolicy password = resolve("POST", "/api/v1/me/password");
        assertThat(password.name()).isEqualTo("account-password");
        assertThat(password.uses(RateLimitDimension.ACCOUNT)).isTrue();
        assertThat(resolve("POST", "/api/v1/me/mfa/recovery-codes").uses(RateLimitDimension.ACCOUNT)).isTrue();
        // Reading sessions stays on the default budget.
        assertThat(resolve("GET", "/api/v1/me/sessions").name()).isEqualTo("api-default");
    }

    @Test
    void signedMediaHasItsOwnPoliciesAheadOfTheUploadPolicy() {
        assertThat(resolve("POST", "/api/v1/media/signed-urls").name()).isEqualTo("media-signed-urls");
        assertThat(resolve("GET", "/api/v1/media/signed/listing/abc.webp").name()).isEqualTo("media-signed-get");
        assertThat(resolve("POST", "/api/v1/media/images").name()).isEqualTo("media-upload");
    }
}
