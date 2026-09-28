package com.company.bds.iam;

import com.company.bds.iam.application.AuthPolicy;
import com.company.bds.iam.application.AuthService;
import com.company.bds.iam.domain.ClientContext;
import com.company.bds.iam.domain.Totp;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TOTP against the RFC 6238 appendix B vectors, replay and window rules; client metadata stays coarse. */
class TotpAndClientContextTests {
    /** Base32 of the ASCII RFC 6238 SHA-1 seed "12345678901234567890". */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void codesMatchTheRfc6238Sha1VectorsTruncatedToSixDigits() {
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1234567890)))).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    void verifyAcceptsOneStepOfDriftNeverAReplayAndNeverGarbage() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = Totp.step(now);
        String current = Totp.code(RFC_SECRET, step);
        assertThat(Totp.verify(RFC_SECRET, current, now, 0)).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, "  " + current.substring(0, 3) + " " + current.substring(3), now, 0)).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, 0)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, 0)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, 0)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now, 0)).isEmpty();
        // Replay: the step already used (or an older one) is refused even though the code is right.
        assertThat(Totp.verify(RFC_SECRET, current, now, step)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, step - 1)).isEmpty();
        for (String bad : new String[]{null, "", "12345", "1234567", "abcdef", "12-456"}) {
            assertThat(Totp.verify(RFC_SECRET, bad, now, 0)).as(String.valueOf(bad)).isEmpty();
        }
    }

    @Test
    void newSecretsAre160BitBase32AndTheOtpauthUriCarriesTheParameters() {
        String secret = Totp.newSecret();
        assertThat(secret).matches("[A-Z2-7]{32}").isNotEqualTo(Totp.newSecret());
        String uri = Totp.otpauthUri("Nhà Đất Chuẩn", "admin@example.test", secret);
        assertThat(uri).startsWith("otpauth://totp/Nh%C3%A0%20%C4%90%E1%BA%A5t%20Chu%E1%BA%A9n:admin%40example.test?secret=" + secret)
                .contains("digits=6").contains("period=30").contains("algorithm=SHA1");
    }

    @Test
    void clientContextKeepsANetworkPrefixAndABrowserLabelOnly() {
        ClientContext chrome = ClientContext.of("203.0.113.57",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36");
        assertThat(chrome.ipHint()).isEqualTo("203.0.113.x");
        assertThat(chrome.deviceLabel()).isEqualTo("Chrome trên Windows");
        assertThat(ClientContext.of("2001:db8:85a3:8d3:1319:8a2e:370:7348", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1"))
                .isEqualTo(new ClientContext("2001:db8:85a3::/48", "Safari trên iOS"));
        assertThat(ClientContext.of("::ffff:198.51.100.9", null)).isEqualTo(new ClientContext("198.51.100.x", null));
        assertThat(ClientContext.of("unknown", "curl/8.0").ipHint()).isNull();
        assertThat(ClientContext.of("10.0.0.1", "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Gecko/20100101 Firefox/130.0").deviceLabel())
                .isEqualTo("Firefox trên macOS");
    }

    @Test
    void returnPathsAreSameSiteRelativeOnly() {
        assertThat(AuthService.safeReturnPath("/search?purpose=RENT&district=005")).isEqualTo("/search?purpose=RENT&district=005");
        assertThat(AuthService.safeReturnPath("/listings/abc#contact")).isEqualTo("/listings/abc#contact");
        for (String bad : new String[]{null, "", "search", "https://evil.example", "//evil.example/x", "/\\evil.example",
                "/ok\nSet-Cookie:x", "/2026/nhadatchuan/admin/users", "/reset-password?token=x", "/verify-email",
                "/" + "a".repeat(600)}) {
            assertThat(AuthService.safeReturnPath(bad)).as(String.valueOf(bad)).isNull();
        }
    }

    @Test
    void staffSessionsAreShorterWithAnIdleTimeoutAndMfaCannotBeDisabledInProduction() {
        AuthPolicy policy = new AuthPolicy(Duration.ofHours(12), Duration.ofHours(8), Duration.ofMinutes(30), true,
                "Nhà Đất Chuẩn", Duration.ofMinutes(5), 5, "production");
        assertThat(policy.ttlFor("ADMIN")).isEqualTo(Duration.ofHours(8));
        assertThat(policy.ttlFor("MODERATOR")).isEqualTo(Duration.ofHours(8));
        assertThat(policy.ttlFor("BROKER")).isEqualTo(Duration.ofHours(12));
        assertThat(policy.idleTimeoutFor("MODERATOR")).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.idleTimeoutFor("USER")).isNull();
        assertThatThrownBy(() -> new AuthPolicy(Duration.ofHours(12), Duration.ofHours(8), Duration.ofMinutes(30), false,
                "x", Duration.ofMinutes(5), 5, "production")).isInstanceOf(IllegalStateException.class);
        assertThat(new AuthPolicy(Duration.ofHours(12), Duration.ofHours(8), Duration.ofMinutes(30), false,
                "x", Duration.ofMinutes(5), 5, "demo").mfaRequired()).isFalse();
    }
}
