package com.company.bds.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Service
public class PiiProtectionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] encryptionKey;
    private final byte[] indexKey;

    public PiiProtectionService(@Value("${app.security.pii-encryption-key:}") String encryptionKey,
                                @Value("${app.security.pii-index-key:}") String indexKey,
                                @Value("${app.mode:demo}") String mode) {
        this.encryptionKey = decodeOrDemo(encryptionKey, mode, "demo-encryption-key");
        this.indexKey = decodeOrDemo(indexKey, mode, "demo-blind-index-key");
    }

    public ProtectedValue protect(String raw) {
        String normalized = normalize(raw);
        try {
            byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return new ProtectedValue("v1:" + mask(normalized) + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + ":" +
                    Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted), hmac(normalized));
        } catch (Exception ex) { throw new IllegalStateException("Không thể bảo vệ dữ liệu cá nhân", ex); }
    }

    public String blindIndex(String raw) { return hmac(normalize(raw)); }

    public String reveal(String protectedValue) {
        if (protectedValue == null || !protectedValue.startsWith("v1:")) {
            throw new IllegalArgumentException("Dữ liệu bảo vệ có định dạng không được hỗ trợ");
        }
        String[] parts = protectedValue.split(":", 4);
        if (parts.length != 4) throw new IllegalArgumentException("Dữ liệu bảo vệ không hợp lệ");
        try {
            byte[] nonce = Base64.getUrlDecoder().decode(parts[2]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[3]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể giải mã dữ liệu cá nhân", ex);
        }
    }

    private String hmac(String normalized) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(indexKey, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Không thể tạo blind index", ex); }
    }

    private static String normalize(String raw) { return raw == null ? "" : raw.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
    private static String mask(String value) {
        if (value.length() < 7) return "***";
        int suffix = value.length() >= 12 ? 4 : 3;
        return value.substring(0, 3) + "****" + value.substring(value.length() - suffix);
    }
    private static byte[] decodeOrDemo(String configured, String mode, String seed) {
        if (configured != null && !configured.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(configured);
            if (decoded.length != 32) throw new IllegalStateException("Khóa PII phải là Base64 của đúng 32 byte");
            return decoded;
        }
        if ("production".equalsIgnoreCase(mode)) throw new IllegalStateException("Production yêu cầu khóa PII từ secret manager");
        try { return MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public record ProtectedValue(String encrypted, String blindIndex) {}
}
