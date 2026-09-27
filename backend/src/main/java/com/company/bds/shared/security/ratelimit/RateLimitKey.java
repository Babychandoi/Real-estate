package com.company.bds.shared.security.ratelimit;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Counter identity for one (policy, dimension, subject). The subject (IP, user id, e-mail) is never stored: the key
 * is the first 128 bits of SHA-256 over {@code pepper || policy || dimension || subject}, NUL-separated. 128 bits make
 * collisions between clients practically impossible, unlike the 32-bit {@code String.hashCode} used before.
 */
record RateLimitKey(String policy, RateLimitDimension dimension, int limit, long windowMillis, long hi, long lo) {
    static final String REDIS_PREFIX = "bds:rl:v2:";

    static RateLimitKey of(byte[] pepper, RateLimitPolicy policy, RateLimitRule rule, String subject) {
        MessageDigest sha256 = sha256();
        sha256.update(pepper);
        sha256.update((byte) 0);
        sha256.update(policy.name().getBytes(StandardCharsets.UTF_8));
        sha256.update((byte) 0);
        sha256.update(rule.dimension().tag().getBytes(StandardCharsets.UTF_8));
        sha256.update((byte) 0);
        sha256.update(subject.getBytes(StandardCharsets.UTF_8));
        ByteBuffer digest = ByteBuffer.wrap(sha256.digest());
        return new RateLimitKey(policy.name(), rule.dimension(), rule.limit(), rule.windowMillis(), digest.getLong(), digest.getLong());
    }

    /** {@code bds:rl:v2:<policy>:<dimension>:<32 hex chars>}; the policy and dimension stay readable for operators. */
    String redisKey() {
        byte[] bytes = ByteBuffer.allocate(16).putLong(hi).putLong(lo).array();
        return REDIS_PREFIX + policy + ':' + dimension.tag() + ':' + HexFormat.of().formatHex(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM thiếu SHA-256", ex);
        }
    }
}
