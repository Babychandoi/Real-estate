package com.company.bds.iam.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Component
public class TotpVerifier {
    private final boolean production;
    private final byte[] secret;

    public TotpVerifier(@Value("${app.mode:demo}") String mode,
                        @Value("${app.security.admin-mfa-secret-base64:}") String encodedSecret) {
        this.production = "production".equalsIgnoreCase(mode);
        try {
            this.secret = encodedSecret.isBlank() ? new byte[0] : Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("APP_ADMIN_MFA_SECRET_BASE64 must be valid Base64", ex);
        }
    }

    public void verifyForPrivilegedRole(String role, String code) {
        if (!production || !("ADMIN".equals(role) || "MODERATOR".equals(role))) return;
        if (secret.length < 20 || code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException("Mã MFA không hợp lệ.");
        }
        long step = Instant.now().getEpochSecond() / 30;
        for (long candidate = step - 1; candidate <= step + 1; candidate++) {
            if (MessageDigest.isEqual(totp(candidate).getBytes(), code.getBytes())) return;
        }
        throw new IllegalArgumentException("Mã MFA không hợp lệ.");
    }

    private String totp(long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể xác thực MFA", ex);
        }
    }
}

