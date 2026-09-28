package com.company.bds.iam.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Time-based one-time passwords (RFC 6238 over RFC 4226): HMAC-SHA1, 6 digits, 30 s steps — the parameters every
 * authenticator app supports. Pure functions; the caller stores the secret and the last accepted step.
 */
public final class Totp {
    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;
    /** Steps accepted either side of now (clock drift of the phone): ±30 s. */
    public static final int WINDOW = 1;
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    /** A new 160-bit secret (the RFC 4226 recommended length), Base32 without padding. */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    public static long step(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), STEP_SECONDS);
    }

    /** The code for one time step. */
    public static String code(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(base32Secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA1 không khả dụng", ex);
        }
    }

    /**
     * The step matched by {@code code} within the window around {@code now} and strictly after {@code lastUsedStep}
     * (a code, or an older one, is never accepted twice), or empty. Every candidate is compared in constant time.
     */
    public static OptionalLong verify(String base32Secret, String code, Instant now, long lastUsedStep) {
        String normalized = code == null ? "" : code.replaceAll("\\s", "");
        if (!normalized.matches("\\d{" + DIGITS + "}")) return OptionalLong.empty();
        byte[] given = normalized.getBytes(StandardCharsets.US_ASCII);
        long current = step(now);
        long matched = -1;
        for (long candidate = current - WINDOW; candidate <= current + WINDOW; candidate++) {
            boolean equal = MessageDigest.isEqual(code(base32Secret, candidate).getBytes(StandardCharsets.US_ASCII), given);
            if (equal && candidate > lastUsedStep && matched < 0) matched = candidate;
        }
        return matched < 0 ? OptionalLong.empty() : OptionalLong.of(matched);
    }

    /** {@code otpauth://} URI for authenticator apps (QR code or tap-to-add). */
    public static String otpauthUri(String issuer, String accountName, String base32Secret) {
        String label = enc(issuer) + ":" + enc(accountName);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >> (bits - 5)) & 0x1f]);
                bits -= 5;
            }
        }
        if (bits > 0) out.append(BASE32[(buffer << (5 - bits)) & 0x1f]);
        return out.toString();
    }

    static byte[] base32Decode(String value) {
        String clean = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int index = c >= 'A' && c <= 'Z' ? c - 'A' : c >= '2' && c <= '7' ? c - '2' + 26 : -1;
            if (index < 0) throw new IllegalArgumentException("Khóa Base32 không hợp lệ");
            buffer = (buffer << 5) | index;
            bits += 5;
            if (bits >= 8) {
                if (out.hasRemaining()) out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return out.array();
    }
}
