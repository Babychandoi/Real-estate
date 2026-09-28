package com.company.bds.media;

import com.company.bds.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaUrlSignerTests {
    private static final String SECRET = "s1-unit-signing-secret-0123456789abcdef";
    private static final String KEY = "8f0c4b8e-1111-4a5b-9c3d-000000000001.jpg";

    @Test
    void signedUrlVerifiesUntilItExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-28T10:00:00Z"));
        MediaUrlSigner signer = new MediaUrlSigner(SECRET, "", Duration.ofMinutes(15), clock);
        MediaUrlSigner.SignedUrl signed = signer.sign(KEY);
        assertThat(signed.expiresAt()).isEqualTo(Instant.parse("2026-09-28T10:15:00Z"));
        Parts parts = Parts.of(signed.url());
        assertThat(parts.key()).isEqualTo(KEY);
        assertThat(signer.verify(KEY, parts.exp(), parts.sig())).isTrue();

        clock.advance(Duration.ofMinutes(15));
        assertThat(signer.verify(KEY, parts.exp(), parts.sig())).as("expired at exp").isFalse();
    }

    @Test
    void signatureIsBoundToKeyExpiryAndSecret() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-28T10:00:00Z"));
        MediaUrlSigner signer = new MediaUrlSigner(SECRET, "", Duration.ofMinutes(15), clock);
        Parts parts = Parts.of(signer.sign(KEY).url());
        assertThat(signer.verify("8f0c4b8e-1111-4a5b-9c3d-000000000002.jpg", parts.exp(), parts.sig())).isFalse();
        assertThat(signer.verify("8f0c4b8e-1111-4a5b-9c3d-000000000001__w320.webp", parts.exp(), parts.sig())).isFalse();
        assertThat(signer.verify(KEY, parts.exp() + 1, parts.sig())).isFalse();
        assertThat(signer.verify(KEY, parts.exp(), "")).isFalse();
        assertThat(signer.verify("../etc/passwd", parts.exp(), parts.sig())).isFalse();
        MediaUrlSigner other = new MediaUrlSigner("another-signing-secret-0123456789abcdef", "", Duration.ofMinutes(15), clock);
        assertThat(other.verify(KEY, parts.exp(), parts.sig())).isFalse();
    }

    @Test
    void previousSecretStillVerifiesDuringRotation() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-28T10:00:00Z"));
        Parts old = Parts.of(new MediaUrlSigner(SECRET, "", Duration.ofMinutes(15), clock).sign(KEY).url());
        MediaUrlSigner rotated = new MediaUrlSigner("rotated-signing-secret-0123456789abcdef", SECRET, Duration.ofMinutes(15), clock);
        assertThat(rotated.verify(KEY, old.exp(), old.sig())).isTrue();
    }

    @Test
    void lifetimeIsCappedAtOneHourAndWeakConfigurationIsRefused() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-28T10:00:00Z"));
        MediaUrlSigner signer = new MediaUrlSigner(SECRET, "", Duration.ofMinutes(15), clock);
        assertThat(signer.sign(KEY, Duration.ofDays(30)).expiresAt()).isEqualTo(Instant.parse("2026-09-28T11:00:00Z"));
        // Even a correctly signed far-future expiry (e.g. from a leaked secret used by mistake elsewhere) is refused.
        long farFuture = Instant.parse("2026-09-29T10:00:00Z").getEpochSecond();
        MutableClock future = new MutableClock(Instant.parse("2026-09-29T09:00:00Z").minus(Duration.ofHours(23)));
        Parts longLived = Parts.of(new MediaUrlSigner(SECRET, "", Duration.ofHours(1),
                new MutableClock(Instant.ofEpochSecond(farFuture).minus(Duration.ofHours(1)))).sign(KEY).url());
        assertThat(new MediaUrlSigner(SECRET, "", Duration.ofHours(1), future).verify(KEY, longLived.exp(), longLived.sig()))
                .isFalse();
        assertThatThrownBy(() -> new MediaUrlSigner("short", "", Duration.ofMinutes(15), clock))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MediaUrlSigner(SECRET, "", Duration.ofHours(2), clock))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> signer.sign("not-a-key")).isInstanceOf(IllegalArgumentException.class);
    }

    private record Parts(String key, long exp, String sig) {
        static Parts of(String url) {
            String path = url.substring(MediaKeys.SIGNED_PREFIX.length());
            String key = path.substring(0, path.indexOf('?'));
            long exp = Long.parseLong(path.substring(path.indexOf("exp=") + 4, path.indexOf("&sig=")));
            return new Parts(key, exp, path.substring(path.indexOf("&sig=") + 5));
        }
    }
}
