package com.company.bds.iam.application;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import com.company.bds.shared.security.PiiProtectionService;

@Service
public class AuthService {
    private static final Duration SESSION_TTL = Duration.ofHours(12);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TotpVerifier totpVerifier;
    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String publicBaseUrl;
    private final PiiProtectionService piiProtection;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, TotpVerifier totpVerifier,
                       JavaMailSender mailSender,
                       @Value("${app.mail.from}") String mailFrom,
                       @Value("${app.public-base-url}") String publicBaseUrl,
                       PiiProtectionService piiProtection) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.totpVerifier = totpVerifier;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.piiProtection = piiProtection;
    }

    @Transactional
    public RegistrationResult register(String email, String password, String fullName, String accountType) {
        String normalizedEmail = normalizeEmail(email);
        String role = "BROKER".equals(accountType) ? "BROKER" : "USER";
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

    @Transactional
    public AuthResult login(String email, String password, String mfaCode) {
        List<UserAccount> users = jdbc.query("""
                SELECT u.id, u.full_name, u.email, u.password_hash,
                       COALESCE((SELECT ur.role FROM user_roles ur WHERE ur.user_id=u.id
                                 ORDER BY CASE ur.role WHEN 'ADMIN' THEN 1 WHEN 'MODERATOR' THEN 2 WHEN 'BROKER' THEN 3 ELSE 4 END
                                 LIMIT 1), 'USER') role
                FROM users u WHERE LOWER(u.email)=?
                """, (rs, row) -> new UserAccount(
                rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("email"),
                rs.getString("password_hash"), rs.getString("role")), normalizeEmail(email));
        if (users.isEmpty() || users.get(0).passwordHash() == null ||
                !passwordEncoder.matches(password, users.get(0).passwordHash())) {
            throw new IllegalArgumentException("Email hoặc mật khẩu không chính xác.");
        }
        String status = jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, users.get(0).id());
        if ("PENDING_EMAIL_VERIFICATION".equals(status)) {
            throw new IllegalStateException("Vui lòng xác minh email trước khi đăng nhập.");
        }
        if (!"ACTIVE".equals(status)) throw new IllegalArgumentException("Tài khoản hiện không hoạt động.");
        totpVerifier.verifyForPrivilegedRole(users.get(0).role(), mfaCode);
        jdbc.update("DELETE FROM auth_sessions WHERE expires_at < CURRENT_TIMESTAMP OR revoked_at IS NOT NULL");
        return issueSession(users.get(0));
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

    private void sendNewVerificationToken(UUID userId, String email) {
        jdbc.update("UPDATE email_verification_tokens SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL", userId);
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO email_verification_tokens(id,user_id,token_hash,expires_at) VALUES (?,?,?,?)",
                UUID.randomUUID(), userId, sha256(token), Timestamp.from(Instant.now().plus(Duration.ofHours(24))));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom); message.setTo(email);
                message.setSubject("Xác minh tài khoản Nhà Đất Chuẩn");
                message.setText("Chào bạn,\n\nXác minh email để kích hoạt tài khoản tại:\n" + publicBaseUrl
                        + "/verify-email?token=" + token + "\n\nLiên kết có hiệu lực trong 24 giờ. Nếu bạn không đăng ký, hãy bỏ qua email này.");
                mailSender.send(message);
            }
        });
    }

    public UserAccount findByToken(String rawToken) {
        List<UserAccount> users = jdbc.query("""
                SELECT u.id, u.full_name, u.email, u.password_hash,
                       COALESCE((SELECT ur.role FROM user_roles ur WHERE ur.user_id=u.id
                                 ORDER BY CASE ur.role WHEN 'ADMIN' THEN 1 WHEN 'MODERATOR' THEN 2 WHEN 'BROKER' THEN 3 ELSE 4 END
                                 LIMIT 1), 'USER') role
                FROM auth_sessions s JOIN users u ON u.id=s.user_id
                WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>CURRENT_TIMESTAMP AND u.status='ACTIVE'
                """, (rs, row) -> new UserAccount(
                rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("email"),
                null, rs.getString("role")), sha256(rawToken));
        return users.isEmpty() ? null : users.get(0);
    }

    private UserAccount loadUser(UUID id) {
        return jdbc.queryForObject("""
                SELECT u.id,u.full_name,u.email,u.password_hash,ur.role FROM users u
                JOIN user_roles ur ON ur.user_id=u.id WHERE u.id=?
                """, (rs, row) -> new UserAccount(rs.getObject("id", UUID.class), rs.getString("full_name"),
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
        String normalizedAvatar = avatarMediaUrl == null || avatarMediaUrl.isBlank() ? null : avatarMediaUrl.trim();
        if (normalizedAvatar != null) {
            Integer owned = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM media_objects
                    WHERE owner_id=? AND visibility='PUBLIC' AND CONCAT('/api/v1/public/media/', object_key)=?
                    """, Integer.class, userId, normalizedAvatar);
            if (owned == null || owned == 0) throw new IllegalArgumentException("Ảnh đại diện phải là ảnh công khai thuộc tài khoản hiện tại.");
        }
        PiiProtectionService.ProtectedValue protectedPhone = piiProtection.protect(phone);
        jdbc.update("UPDATE users SET full_name=?,phone_encrypted=?,phone_lookup_hash=?,avatar_media_url=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                name.trim(), protectedPhone.encrypted(), protectedPhone.blindIndex(), normalizedAvatar, userId);
        UserAccount current = loadUser(userId);
        return view(current);
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
}
