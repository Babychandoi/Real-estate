package com.company.bds.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Short-lived capability URLs for media that is not publicly served (drafts, pending edits, hidden listings; contract
 * §10): {@code /api/v1/media/signed/<key>?exp=<epoch seconds>&sig=<base64url HMAC-SHA256("v1|key|exp")>}.
 * <ul>
 *   <li>The signature covers the exact object key (a variant is its own key) and the expiry, so neither can be changed.</li>
 *   <li>Verification is constant time, accepts the current secret and an optional previous one (rotation), and refuses
 *   any expiry further away than {@link #MAX_LIFETIME} even when the signature is valid.</li>
 *   <li>Without a configured secret (dev/test only — production refuses to start) a random per-process secret is used,
 *   so URLs are valid only on the instance that issued them.</li>
 * </ul>
 */
@Component
public class MediaUrlSigner {
    public static final Duration MAX_LIFETIME = Duration.ofHours(1);
    private static final Logger log = LoggerFactory.getLogger(MediaUrlSigner.class);
    private final List<byte[]> secrets;
    private final Duration ttl;
    private final Clock clock;

    public MediaUrlSigner(@Value("${app.media.signing-secret:}") String secret,
                          @Value("${app.media.previous-signing-secret:}") String previousSecret,
                          @Value("${app.media.signed-url-ttl:PT15M}") Duration ttl,
                          Clock clock) {
        List<byte[]> keys = new ArrayList<>();
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            keys.add(random);
            log.warn("app.media.signing-secret is not set: signed media URLs use a random per-process key (dev/test only).");
        } else {
            if (secret.length() < 32) throw new IllegalStateException("app.media.signing-secret phải có ít nhất 32 ký tự.");
            keys.add(secret.getBytes(StandardCharsets.UTF_8));
        }
        if (previousSecret != null && !previousSecret.isBlank()) keys.add(previousSecret.getBytes(StandardCharsets.UTF_8));
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(MAX_LIFETIME) > 0) {
            throw new IllegalStateException("app.media.signed-url-ttl phải trong khoảng (0, 1 giờ].");
        }
        this.secrets = List.copyOf(keys);
        this.ttl = ttl;
        this.clock = clock;
    }

    public SignedUrl sign(String objectKey) { return sign(objectKey, ttl); }

    public SignedUrl sign(String objectKey, Duration lifetime) {
        if (!MediaKeys.isValid(objectKey)) throw new IllegalArgumentException("invalid media key");
        if (lifetime.compareTo(MAX_LIFETIME) > 0) lifetime = MAX_LIFETIME;
        long exp = clock.instant().plus(lifetime).getEpochSecond();
        String sig = signature(secrets.get(0), objectKey, exp);
        return new SignedUrl(MediaKeys.SIGNED_PREFIX + objectKey + "?exp=" + exp + "&sig=" + sig, Instant.ofEpochSecond(exp));
    }

    /** True only for an unexpired, not-too-far-in-the-future expiry with a matching signature for this exact key. */
    public boolean verify(String objectKey, long exp, String sig) {
        if (sig == null || sig.isEmpty() || sig.length() > 64 || !MediaKeys.isValid(objectKey)) return false;
        long now = clock.instant().getEpochSecond();
        if (exp <= now || exp > now + MAX_LIFETIME.getSeconds() + 60) return false;
        byte[] presented = sig.getBytes(StandardCharsets.US_ASCII);
        boolean ok = false;
        for (byte[] secret : secrets) {
            ok |= MessageDigest.isEqual(signature(secret, objectKey, exp).getBytes(StandardCharsets.US_ASCII), presented);
        }
        return ok;
    }

    private static String signature(byte[] secret, String objectKey, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(("v1|" + objectKey + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }

    public record SignedUrl(String url, Instant expiresAt) {}
}
