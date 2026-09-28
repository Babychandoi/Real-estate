package com.company.bds.iam.application;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.company.bds.shared.mail.MailMessage;
import com.company.bds.shared.mail.MailOutbox;
import com.company.bds.shared.security.ContactInfoGuard;
import com.company.bds.shared.security.PiiProtectionService;
import com.company.bds.shared.security.Roles;

@Service
public class AuthService {
    private static final Duration SESSION_TTL = Duration.ofHours(12);
    private static final Duration KYC_DOCUMENT_ACCESS_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final MailOutbox mailOutbox;
    private final String publicBaseUrl;
    private final PiiProtectionService piiProtection;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                       MailOutbox mailOutbox,
                       @Value("${app.public-base-url}") String publicBaseUrl,
                       PiiProtectionService piiProtection) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.mailOutbox = mailOutbox;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.piiProtection = piiProtection;
    }

    @Transactional
    public RegistrationResult register(String email, String password, String fullName, String accountType) {
        ContactInfoGuard.requireNoContact(fullName);
        String normalizedEmail = normalizeEmail(email);
        String role = accountType != null && Roles.SELF_REGISTRATION.contains(accountType) ? accountType : Roles.USER;
        UUID userId = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO users(id, phone_lookup_hash, phone_encrypted, full_name, email, password_hash, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING_EMAIL_VERIFICATION')
                    """, userId, sha256("unset:" + userId), "NOT_PROVIDED", fullName.trim(), normalizedEmail,
                    passwordEncoder.encode(password));
            jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, ?)", userId, role);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("Email đã được sử dụng bởi tài khoản khác.");
        }
        sendNewVerificationToken(userId, normalizedEmail);
        return new RegistrationResult(normalizedEmail, true);
    }

    // Portal mismatches reuse the wrong-credentials message so neither portal reveals which accounts are staff.
    private static final String INVALID_CREDENTIALS = "Email hoặc mật khẩu không chính xác.";

    @Transactional
    public AuthResult login(String email, String password) {
        UserAccount user = authenticate(email, password);
        if (isPrivileged(user.role())) throw new IllegalArgumentException(INVALID_CREDENTIALS);
        return issueSession(user);
    }

    @Transactional
    public AuthResult adminLogin(String email, String password) {
        UserAccount user = authenticate(email, password);
        if (!isPrivileged(user.role())) throw new IllegalArgumentException(INVALID_CREDENTIALS);
        return issueSession(user);
    }

    private UserAccount authenticate(String email, String password) {
        List<UserAccount> users = jdbc.query("""
                SELECT u.id, u.full_name, u.email, u.password_hash, %s AS role
                FROM users u WHERE LOWER(u.email)=?
                """.formatted(Roles.effectiveRoleSql("u.id")), (rs, row) -> new UserAccount(
                rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("email"),
                rs.getString("password_hash"), rs.getString("role")), normalizeEmail(email));
        String storedHash = users.isEmpty() ? null : users.get(0).passwordHash();
        // Always run one BCrypt comparison so response time does not reveal whether the email exists.
        boolean matches = passwordEncoder.matches(password, storedHash != null ? storedHash : timingDummyHash());
        if (storedHash == null || !matches) {
            throw new IllegalArgumentException(INVALID_CREDENTIALS);
        }
        String status = jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, users.get(0).id());
        if ("PENDING_EMAIL_VERIFICATION".equals(status)) {
            throw new IllegalStateException("Vui lòng xác minh email trước khi đăng nhập.");
        }
        if (!"ACTIVE".equals(status)) throw new IllegalArgumentException("Tài khoản hiện không hoạt động.");
        jdbc.update("DELETE FROM auth_sessions WHERE expires_at < CURRENT_TIMESTAMP OR revoked_at IS NOT NULL");
        return users.get(0);
    }

    private volatile String timingDummyHash;

    private String timingDummyHash() {
        if (timingDummyHash == null) timingDummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
        return timingDummyHash;
    }

    private static boolean isPrivileged(String role) {
        return Roles.isStaff(role);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            jdbc.update("UPDATE auth_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE token_hash=? AND revoked_at IS NULL", sha256(rawToken));
        }
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        List<UUID> users = jdbc.query("""
                SELECT user_id FROM email_verification_tokens
                WHERE token_hash=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP
                FOR UPDATE
                """, (rs, row) -> rs.getObject(1, UUID.class), sha256(rawToken));
        if (users.isEmpty()) throw new IllegalArgumentException("Liên kết xác minh không hợp lệ hoặc đã hết hạn.");
        UUID userId = users.get(0);
        jdbc.update("UPDATE email_verification_tokens SET used_at=CURRENT_TIMESTAMP WHERE token_hash=?", sha256(rawToken));
        jdbc.update("UPDATE users SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?", userId);
        jdbc.update("DELETE FROM auth_sessions WHERE user_id=?", userId);
    }

    @Transactional
    public void resendVerification(String email) {
        List<UUID> users = jdbc.query("SELECT id FROM users WHERE LOWER(email)=? AND status='PENDING_EMAIL_VERIFICATION'",
                (rs, row) -> rs.getObject(1, UUID.class), normalizeEmail(email));
        if (!users.isEmpty()) sendNewVerificationToken(users.get(0), normalizeEmail(email));
    }

    @Transactional
    public void requestPasswordReset(String email) {
        List<UUID> users = jdbc.query("""
                SELECT id FROM users WHERE LOWER(email)=? AND status='ACTIVE' AND password_hash IS NOT NULL
                """, (rs, row) -> rs.getObject(1, UUID.class), normalizeEmail(email));
        if (users.isEmpty()) return;
        UUID userId = users.get(0);
        jdbc.update("UPDATE password_reset_tokens SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL", userId);
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID tokenId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(30));
        jdbc.update("INSERT INTO password_reset_tokens(id,user_id,token_hash,expires_at) VALUES (?,?,?,?)",
                tokenId, userId, sha256(token), Timestamp.from(expiresAt));
        // Queued in this transaction and sent by the job worker: SMTP never runs inside the request transaction. tryEnqueue
        // never throws, so the answer stays 202 whatever the stored address is (no account enumeration).
        mailOutbox.tryEnqueue(MailMessage.text(normalizeEmail(email), "Đặt lại mật khẩu Nhà Đất Chuẩn",
                "Chào bạn,\n\nNhấn vào liên kết sau để đặt lại mật khẩu: " + publicBaseUrl
                        + "/reset-password?token=" + token + "\n\nLiên kết có hiệu lực trong 30 phút và chỉ dùng một lần. Nếu bạn không yêu cầu, hãy bỏ qua email này.",
                "PASSWORD_RESET", "password-reset:" + tokenId).withNotAfter(expiresAt));
    }

    @Transactional
    public void resetPassword(String rawToken, String password) {
        List<UUID> users = jdbc.query("""
                SELECT user_id FROM password_reset_tokens
                WHERE token_hash=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP
                FOR UPDATE
                """, (rs, row) -> rs.getObject(1, UUID.class), sha256(rawToken));
        if (users.isEmpty()) throw new IllegalArgumentException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
        UUID userId = users.get(0);
        jdbc.update("UPDATE users SET password_hash=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='ACTIVE'",
                passwordEncoder.encode(password), userId);
        jdbc.update("UPDATE password_reset_tokens SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL", userId);
        jdbc.update("UPDATE auth_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL", userId);
    }

    @Transactional
    public KycDocumentAccess grantKycDocumentAccess(UUID userId, String password) {
        String passwordHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id=? AND status='ACTIVE'",
                String.class, userId);
        if (passwordHash == null || !passwordEncoder.matches(password, passwordHash)) {
            throw new org.springframework.security.access.AccessDeniedException("Mật khẩu xác nhận không chính xác.");
        }
        jdbc.update("DELETE FROM kyc_document_access_grants WHERE user_id=? OR expires_at<CURRENT_TIMESTAMP", userId);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(KYC_DOCUMENT_ACCESS_TTL);
        jdbc.update("INSERT INTO kyc_document_access_grants(id,user_id,token_hash,expires_at) VALUES (?,?,?,?)",
                UUID.randomUUID(), userId, sha256(token), Timestamp.from(expiresAt));
        return new KycDocumentAccess(token, expiresAt);
    }

    @Transactional(readOnly = true)
    public boolean hasKycDocumentAccess(UUID userId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return false;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM kyc_document_access_grants
                WHERE user_id=? AND token_hash=? AND expires_at>CURRENT_TIMESTAMP
                """, Integer.class, userId, sha256(rawToken));
        return count != null && count > 0;
    }

    private void sendNewVerificationToken(UUID userId, String email) {
        jdbc.update("UPDATE email_verification_tokens SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL", userId);
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID tokenId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(24));
        jdbc.update("INSERT INTO email_verification_tokens(id,user_id,token_hash,expires_at) VALUES (?,?,?,?)",
                tokenId, userId, sha256(token), Timestamp.from(expiresAt));
        mailOutbox.tryEnqueue(MailMessage.text(email, "Xác minh tài khoản Nhà Đất Chuẩn",
                "Chào bạn,\n\nXác minh email để kích hoạt tài khoản tại:\n" + publicBaseUrl
                        + "/verify-email?token=" + token + "\n\nLiên kết có hiệu lực trong 24 giờ. Nếu bạn không đăng ký, hãy bỏ qua email này.",
                "EMAIL_VERIFICATION", "email-verification:" + tokenId).withNotAfter(expiresAt));
    }

    public UserAccount findByToken(String rawToken) {
        List<UserAccount> users = jdbc.query("""
                SELECT u.id, u.full_name, u.email, u.password_hash, %s AS role
                FROM auth_sessions s JOIN users u ON u.id=s.user_id
                WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>CURRENT_TIMESTAMP AND u.status='ACTIVE'
                """.formatted(Roles.effectiveRoleSql("u.id")), (rs, row) -> new UserAccount(
                rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("email"),
                null, rs.getString("role")), sha256(rawToken));
        return users.isEmpty() ? null : users.get(0);
    }

    private UserAccount loadUser(UUID id) {
        return jdbc.queryForObject("""
                SELECT u.id,u.full_name,u.email,u.password_hash,%s AS role FROM users u WHERE u.id=?
                """.formatted(Roles.effectiveRoleSql("u.id")), (rs, row) -> new UserAccount(rs.getObject("id", UUID.class), rs.getString("full_name"),
                rs.getString("email"), rs.getString("password_hash"), rs.getString("role")), id);
    }

    private AuthResult issueSession(UserAccount user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        jdbc.update("INSERT INTO auth_sessions(id,user_id,token_hash,expires_at) VALUES (?,?,?,?)",
                UUID.randomUUID(), user.id(), sha256(token), Timestamp.from(expiresAt));
        return new AuthResult(token, expiresAt, view(user));
    }

    public UserView view(UserAccount user) {
        return jdbc.queryForObject("SELECT plan_code,plan_expires_at,listing_quota_remaining,avatar_media_url,phone_encrypted FROM users WHERE id=?",
                (rs, row) -> new UserView(user.id(), user.fullName(), user.email(), user.role(), rs.getString(1),
                        rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant(), rs.getInt(3), rs.getString(4),
                        revealPhoneIfAvailable(rs.getString(5))), user.id());
    }

    @Transactional
    public UserView updateProfile(UUID userId, String name, String phone, String avatarMediaUrl) {
        ContactInfoGuard.requireNoContact(name);
        String normalizedAvatar = ownedAvatar(userId, avatarMediaUrl);
        PiiProtectionService.ProtectedValue protectedPhone = piiProtection.protect(phone);
        jdbc.update("UPDATE users SET full_name=?,phone_encrypted=?,phone_lookup_hash=?,avatar_media_url=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                name.trim(), protectedPhone.encrypted(), protectedPhone.blindIndex(), normalizedAvatar, userId);
        UserAccount current = loadUser(userId);
        return view(current);
    }

    /** Changes only the avatar, so accounts without a phone number can still set a profile photo. */
    @Transactional
    public UserView updateAvatar(UUID userId, String avatarMediaUrl) {
        jdbc.update("UPDATE users SET avatar_media_url=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", ownedAvatar(userId, avatarMediaUrl), userId);
        return view(loadUser(userId));
    }

    private String ownedAvatar(UUID userId, String avatarMediaUrl) {
        String normalizedAvatar = avatarMediaUrl == null || avatarMediaUrl.isBlank() ? null : avatarMediaUrl.trim();
        if (normalizedAvatar != null) {
            Integer owned = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM media_objects
                    WHERE owner_id=? AND visibility='PUBLIC' AND CONCAT('/api/v1/public/media/', object_key)=?
                    """, Integer.class, userId, normalizedAvatar);
            if (owned == null || owned == 0) throw new IllegalArgumentException("Ảnh đại diện phải là ảnh công khai thuộc tài khoản hiện tại.");
        }
        return normalizedAvatar;
    }

    private String revealPhoneIfAvailable(String protectedPhone) {
        return protectedPhone != null && protectedPhone.startsWith("v1:") ? piiProtection.reveal(protectedPhone) : null;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể băm dữ liệu", ex);
        }
    }

    public record UserAccount(UUID id, String fullName, String email, String passwordHash, String role) {}
    public record UserView(UUID id, String name, String email, String role, String planCode, Instant planExpiresAt, int listingQuotaRemaining, String avatarMediaUrl, String phone) {}
    public record AuthResult(String accessToken, Instant expiresAt, UserView user) {}
    public record RegistrationResult(String email, boolean requiresEmailVerification) {}
    public record KycDocumentAccess(String token, Instant expiresAt) {}
}
