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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.company.bds.iam.domain.ClientContext;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.mail.MailMessage;
import com.company.bds.shared.mail.MailOutbox;
import com.company.bds.shared.security.ContactInfoGuard;
import com.company.bds.shared.security.PiiProtectionService;
import com.company.bds.shared.security.Roles;

@Service
public class AuthService {
    private static final Duration KYC_DOCUMENT_ACCESS_TTL = Duration.ofMinutes(10);
    private static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final int RETURN_PATH_MAX = 512;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final MailOutbox mailOutbox;
    private final String publicBaseUrl;
    private final PiiProtectionService piiProtection;
    private final Clock clock;
    private final AuthPolicy policy;
    private final SessionService sessions;
    private final MfaService mfa;
    private final SecurityEventLog events;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                       MailOutbox mailOutbox,
                       @Value("${app.public-base-url}") String publicBaseUrl,
                       PiiProtectionService piiProtection, Clock clock, AuthPolicy policy, SessionService sessions,
                       MfaService mfa, SecurityEventLog events) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.mailOutbox = mailOutbox;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.piiProtection = piiProtection;
        this.clock = clock;
        this.policy = policy;
        this.sessions = sessions;
        this.mfa = mfa;
        this.events = events;
    }

    public RegistrationResult register(String email, String password, String fullName, String accountType) {
        return register(email, password, fullName, accountType, null);
    }

    /** {@code returnTo}: page to come back to after verification (DS-11); anything but a same-site relative path is dropped. */
    @Transactional
    public RegistrationResult register(String email, String password, String fullName, String accountType, String returnTo) {
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
        sendNewVerificationToken(userId, normalizedEmail, safeReturnPath(returnTo));
        return new RegistrationResult(normalizedEmail, true);
    }

    // Portal mismatches reuse the wrong-credentials message so neither portal reveals which accounts are staff.
    private static final String INVALID_CREDENTIALS = "Email hoặc mật khẩu không chính xác.";

    public AuthResult login(String email, String password) { return login(email, password, ClientContext.UNKNOWN); }

    @Transactional
    public AuthResult login(String email, String password, ClientContext client) {
        UserAccount user = authenticate(email, password, client);
        if (isPrivileged(user.role())) throw new IllegalArgumentException(INVALID_CREDENTIALS);
        events.record(user.id(), SecurityEventLog.Type.LOGIN_SUCCEEDED, client);
        return issueSession(user, client, false);
    }

    /**
     * Staff portal. With a confirmed authenticator — or whenever {@code app.security.mfa.required} — the answer is a
     * challenge, not a session; the session comes from {@link MfaService#verify} or {@link MfaService#confirmEnrollment}.
     */
    @Transactional
    public AdminLoginResult adminLogin(String email, String password, ClientContext client) {
        UserAccount user = authenticate(email, password, client);
        if (!isPrivileged(user.role())) throw new IllegalArgumentException(INVALID_CREDENTIALS);
        if (policy.mfaRequired() || mfa.isEnrolled(user.id())) {
            MfaService.Challenge challenge = mfa.startChallenge(user.id());
            return new AdminLoginResult(null, null, null, null, true, challenge.state(), challenge.token(), challenge.expiresAt());
        }
        events.record(user.id(), SecurityEventLog.Type.LOGIN_SUCCEEDED, client);
        AuthResult session = issueSession(user, client, false);
        return new AdminLoginResult(session.accessToken(), session.expiresAt(), session.idleExpiresAt(), session.user(),
                false, null, null, null);
    }

    public AdminLoginResult adminLogin(String email, String password) { return adminLogin(email, password, ClientContext.UNKNOWN); }

    /** Session result of a completed MFA step. */
    public AuthResult signedIn(MfaService.SignedIn signedIn) {
        SessionService.Issued issued = signedIn.issued();
        return new AuthResult(issued.token(), issued.expiresAt(), view(loadUser(signedIn.userId())), issued.idleExpiresAt());
    }

    private UserAccount authenticate(String email, String password, ClientContext client) {
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
            // Kept although the request fails: the account owner sees failed attempts in their security history.
            if (!users.isEmpty()) events.recordIndependently(users.get(0).id(), SecurityEventLog.Type.LOGIN_FAILED, client);
            throw new IllegalArgumentException(INVALID_CREDENTIALS);
        }
        String status = jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, users.get(0).id());
        if ("PENDING_EMAIL_VERIFICATION".equals(status)) {
            throw new IllegalStateException("Vui lòng xác minh email trước khi đăng nhập.");
        }
        if (!"ACTIVE".equals(status)) throw new IllegalArgumentException("Tài khoản hiện không hoạt động.");
        sessions.purgeStale();
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
        sessions.revokeToken(rawToken, "LOGOUT");
    }

    /**
     * Single-use e-mail verification (UI-14). A link already used for an account that is now active answers
     * ALREADY_VERIFIED so the person can simply sign in; other failures carry TOKEN_INVALID / TOKEN_EXPIRED /
     * TOKEN_USED / TOKEN_SUPERSEDED.
     */
    @Transactional
    public VerificationResult verifyEmail(String rawToken) {
        TokenRow token = lockToken("email_verification_tokens", rawToken);
        TokenStatus status = token == null ? TokenStatus.INVALID : token.status(clock.instant());
        if (status == TokenStatus.USED) {
            String userStatus = jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, token.userId());
            if ("ACTIVE".equals(userStatus)) return new VerificationResult("ALREADY_VERIFIED", token.returnPath());
        }
        if (status != TokenStatus.VALID) throw tokenProblem(status, "xác minh email");
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("UPDATE email_verification_tokens SET used_at=? WHERE id=?", now, token.id());
        jdbc.update("UPDATE users SET status='ACTIVE',email_verified_at=?,updated_at=? WHERE id=? AND status='PENDING_EMAIL_VERIFICATION'",
                now, now, token.userId());
        sessions.revokeAll(token.userId(), null, "EMAIL_VERIFIED");
        return new VerificationResult("VERIFIED", token.returnPath());
    }

    /** State of a reset link before the person types a new password; never consumes it. */
    @Transactional(readOnly = true)
    public TokenStatus passwordResetTokenStatus(String rawToken) {
        TokenRow token = findToken("password_reset_tokens", rawToken, false);
        return token == null ? TokenStatus.INVALID : token.status(clock.instant());
    }

    @Transactional
    public void resendVerification(String email) {
        List<UUID> users = jdbc.query("SELECT id FROM users WHERE LOWER(email)=? AND status='PENDING_EMAIL_VERIFICATION'",
                (rs, row) -> rs.getObject(1, UUID.class), normalizeEmail(email));
        if (users.isEmpty()) return;
        // The new link keeps the page the person registered from.
        List<String> previous = jdbc.query("""
                SELECT return_path FROM email_verification_tokens WHERE user_id=? ORDER BY created_at DESC LIMIT 1
                """, (rs, row) -> rs.getString(1), users.get(0));
        sendNewVerificationToken(users.get(0), normalizeEmail(email), previous.isEmpty() ? null : previous.get(0));
    }

    @Transactional
    public void requestPasswordReset(String email) {
        List<UUID> users = jdbc.query("""
                SELECT id FROM users WHERE LOWER(email)=? AND status='ACTIVE' AND password_hash IS NOT NULL
                """, (rs, row) -> rs.getObject(1, UUID.class), normalizeEmail(email));
        if (users.isEmpty()) return;
        UUID userId = users.get(0);
        Instant now = clock.instant();
        jdbc.update("UPDATE password_reset_tokens SET superseded_at=? WHERE user_id=? AND used_at IS NULL AND superseded_at IS NULL",
                Timestamp.from(now), userId);
        String token = randomToken();
        UUID tokenId = UUID.randomUUID();
        Instant expiresAt = now.plus(RESET_TTL);
        jdbc.update("INSERT INTO password_reset_tokens(id,user_id,token_hash,expires_at,created_at) VALUES (?,?,?,?,?)",
                tokenId, userId, sha256(token), Timestamp.from(expiresAt), Timestamp.from(now));
        // Queued in this transaction and sent by the job worker: SMTP never runs inside the request transaction. tryEnqueue
        // never throws, so the answer stays 202 whatever the stored address is (no account enumeration).
        mailOutbox.tryEnqueue(MailMessage.text(normalizeEmail(email), "Đặt lại mật khẩu Nhà Đất Chuẩn",
                "Chào bạn,\n\nNhấn vào liên kết sau để đặt lại mật khẩu: " + publicBaseUrl
                        + "/reset-password?token=" + token + "\n\nLiên kết có hiệu lực trong 30 phút và chỉ dùng một lần. Nếu bạn không yêu cầu, hãy bỏ qua email này.",
                "PASSWORD_RESET", "password-reset:" + tokenId).withNotAfter(expiresAt));
    }

    public void resetPassword(String rawToken, String password) { resetPassword(rawToken, password, ClientContext.UNKNOWN); }

    /** Consumes the reset link, sets the password and revokes every session of the account (F20.2). */
    @Transactional
    public void resetPassword(String rawToken, String password, ClientContext client) {
        TokenRow token = lockToken("password_reset_tokens", rawToken);
        TokenStatus status = token == null ? TokenStatus.INVALID : token.status(clock.instant());
        if (status != TokenStatus.VALID) throw tokenProblem(status, "đặt lại mật khẩu");
        UUID userId = token.userId();
        Timestamp now = Timestamp.from(clock.instant());
        int updated = jdbc.update("UPDATE users SET password_hash=?,password_changed_at=?,updated_at=? WHERE id=? AND status='ACTIVE'",
                passwordEncoder.encode(password), now, now, userId);
        if (updated == 0) throw tokenProblem(TokenStatus.INVALID, "đặt lại mật khẩu");
        jdbc.update("UPDATE password_reset_tokens SET used_at=? WHERE id=?", now, token.id());
        jdbc.update("UPDATE password_reset_tokens SET superseded_at=? WHERE user_id=? AND used_at IS NULL AND superseded_at IS NULL", now, userId);
        sessions.revokeAll(userId, null, "PASSWORD_RESET");
        events.record(userId, SecurityEventLog.Type.PASSWORD_RESET, client);
        notifyPasswordChanged(userId, "password-reset-notice:" + token.id());
    }

    /**
     * Password change by the signed-in account: the current password is required, every other session is revoked
     * (the current one stays), and the address on file is told (F20.2, ADR 0001 §4).
     */
    @Transactional
    public int changePassword(UUID userId, UUID currentSessionId, String currentPassword, String newPassword, ClientContext client) {
        // NO KEY UPDATE, not UPDATE: the failure event is written by an independent transaction whose foreign-key check
        // takes KEY SHARE on this row; FOR UPDATE would make the request wait on itself.
        String stored = jdbc.query("SELECT password_hash FROM users WHERE id=? AND status='ACTIVE' FOR NO KEY UPDATE",
                (rs, n) -> rs.getString(1), userId).stream().findFirst().orElse(null);
        if (stored == null || !passwordEncoder.matches(currentPassword, stored)) {
            events.recordIndependently(userId, SecurityEventLog.Type.LOGIN_FAILED, client);
            throw ApiException.badRequest("CURRENT_PASSWORD_INVALID", "Mật khẩu hiện tại không đúng.");
        }
        if (passwordEncoder.matches(newPassword, stored)) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("UPDATE users SET password_hash=?,password_changed_at=?,updated_at=? WHERE id=?",
                passwordEncoder.encode(newPassword), now, now, userId);
        jdbc.update("UPDATE password_reset_tokens SET superseded_at=? WHERE user_id=? AND used_at IS NULL AND superseded_at IS NULL", now, userId);
        int revoked = sessions.revokeAll(userId, currentSessionId, "PASSWORD_CHANGED");
        events.record(userId, SecurityEventLog.Type.PASSWORD_CHANGED, client);
        notifyPasswordChanged(userId, "password-changed:" + userId + ":" + now.getTime());
        return revoked;
    }

    private void notifyPasswordChanged(UUID userId, String dedupeKey) {
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, userId);
        if (email == null) return;
        mailOutbox.tryEnqueue(MailMessage.text(email, "Mật khẩu Nhà Đất Chuẩn vừa được thay đổi",
                "Chào bạn,\n\nMật khẩu tài khoản của bạn vừa được thay đổi và các phiên đăng nhập khác đã được đăng xuất."
                        + "\n\nNếu không phải bạn thực hiện, hãy đặt lại mật khẩu ngay tại: " + publicBaseUrl + "/forgot-password",
                "PASSWORD_CHANGED", dedupeKey));
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

    private void sendNewVerificationToken(UUID userId, String email, String returnPath) {
        Instant now = clock.instant();
        jdbc.update("UPDATE email_verification_tokens SET superseded_at=? WHERE user_id=? AND used_at IS NULL AND superseded_at IS NULL",
                Timestamp.from(now), userId);
        String token = randomToken();
        UUID tokenId = UUID.randomUUID();
        Instant expiresAt = now.plus(VERIFICATION_TTL);
        jdbc.update("INSERT INTO email_verification_tokens(id,user_id,token_hash,expires_at,created_at,return_path) VALUES (?,?,?,?,?,?)",
                tokenId, userId, sha256(token), Timestamp.from(expiresAt), Timestamp.from(now), returnPath);
        mailOutbox.tryEnqueue(MailMessage.text(email, "Xác minh tài khoản Nhà Đất Chuẩn",
                "Chào bạn,\n\nXác minh email để kích hoạt tài khoản tại:\n" + publicBaseUrl
                        + "/verify-email?token=" + token + "\n\nLiên kết có hiệu lực trong 24 giờ. Nếu bạn không đăng ký, hãy bỏ qua email này.",
                "EMAIL_VERIFICATION", "email-verification:" + tokenId).withNotAfter(expiresAt));
    }

    /**
     * A same-site relative path ({@code /search?x=1}), or {@code null}: no scheme, no protocol-relative {@code //} or
     * backslash, no control characters, bounded length, never the staff portal or a token page. Keeps the verification
     * flow from becoming an open redirect.
     */
    public static String safeReturnPath(String returnTo) {
        if (returnTo == null) return null;
        String value = returnTo.trim();
        if (value.isEmpty() || value.length() > RETURN_PATH_MAX || !value.startsWith("/") || value.startsWith("//")) return null;
        for (char c : value.toCharArray()) {
            if (c < 0x20 || c == 0x7f || c == '\\') return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/2026/nhadatchuan/admin") || lower.startsWith("/verify-email")
                || lower.startsWith("/reset-password") || lower.startsWith("/forgot-password")) return null;
        return value;
    }

    private TokenRow lockToken(String table, String rawToken) { return findToken(table, rawToken, true); }

    private TokenRow findToken(String table, String rawToken, boolean lock) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) return null;
        String returnPath = "email_verification_tokens".equals(table) ? "return_path" : "NULL";
        List<TokenRow> rows = jdbc.query("SELECT id, user_id, expires_at, used_at, superseded_at, " + returnPath
                        + " FROM " + table + " WHERE token_hash=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new TokenRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4) != null, rs.getTimestamp(5) != null, rs.getString(6)), sha256(rawToken));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static ApiException tokenProblem(TokenStatus status, String purpose) {
        return switch (status) {
            case EXPIRED -> ApiException.gone("TOKEN_EXPIRED",
                    "Liên kết " + purpose + " đã hết hạn. Hãy yêu cầu liên kết mới.");
            case USED -> ApiException.conflict("TOKEN_USED", "Liên kết " + purpose + " đã được sử dụng.");
            case SUPERSEDED -> ApiException.conflict("TOKEN_SUPERSEDED",
                    "Liên kết " + purpose + " đã được thay bằng một liên kết mới hơn. Hãy dùng email gần nhất.");
            default -> ApiException.badRequest("TOKEN_INVALID", "Liên kết " + purpose + " không hợp lệ.");
        };
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public UserAccount findByToken(String rawToken) {
        SessionService.ActiveSession session = sessions.find(rawToken);
        return session == null ? null : session.user();
    }

    /** The session of a bearer token with its id (session list "current" flag, keep-current on password change). */
    public SessionService.ActiveSession findSession(String rawToken) {
        return sessions.find(rawToken);
    }

    /** Same lookup; {@code recordActivity=false} leaves the idle timer alone (background traffic). */
    public SessionService.ActiveSession findSession(String rawToken, boolean recordActivity) {
        return sessions.find(rawToken, recordActivity);
    }

    private UserAccount loadUser(UUID id) {
        return jdbc.queryForObject("""
                SELECT u.id,u.full_name,u.email,u.password_hash,%s AS role FROM users u WHERE u.id=?
                """.formatted(Roles.effectiveRoleSql("u.id")), (rs, row) -> new UserAccount(rs.getObject("id", UUID.class), rs.getString("full_name"),
                rs.getString("email"), rs.getString("password_hash"), rs.getString("role")), id);
    }

    private AuthResult issueSession(UserAccount user, ClientContext client, boolean mfaVerified) {
        SessionService.Issued issued = sessions.issue(user.id(), user.role(), client, mfaVerified);
        return new AuthResult(issued.token(), issued.expiresAt(), view(user), issued.idleExpiresAt());
    }

    public UserView view(UserAccount user) {
        return jdbc.queryForObject("SELECT plan_code,plan_expires_at,listing_quota_remaining,avatar_media_url,phone_encrypted FROM users WHERE id=?",
                (rs, row) -> new UserView(user.id(), user.fullName(), user.email(), user.role(), rs.getString(1),
                        rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant(), rs.getInt(3), rs.getString(4),
                        revealPhoneIfAvailable(rs.getString(5)), Roles.label(user.role())), user.id());
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
    public record UserView(UUID id, String name, String email, String role, String planCode, Instant planExpiresAt, int listingQuotaRemaining, String avatarMediaUrl, String phone, String roleLabel) {}

    public enum BecomeOwnerOutcome { UPGRADED, ALREADY_OWNER }

    public record BecomeOwnerResult(BecomeOwnerOutcome outcome, UserView user) {}

    /**
     * Self-service USER → OWNER (P-09) after the user explicitly confirmed they post their own property. Audited in
     * {@code user_role_changes}; idempotent for an OWNER; refused for brokers and staff (their role is managed elsewhere).
     */
    @Transactional
    public BecomeOwnerResult becomeOwner(UUID userId) {
        // Lock the user row (exists even when the user has no role row yet).
        jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", userId);
        List<String> roles = jdbc.queryForList("SELECT role FROM user_roles WHERE user_id=?", String.class, userId);
        String current = roles.isEmpty() ? Roles.USER : Roles.highest(roles);
        if (current.equals(Roles.OWNER)) return new BecomeOwnerResult(BecomeOwnerOutcome.ALREADY_OWNER, view(loadUser(userId)));
        if (!current.equals(Roles.USER)) {
            throw new IllegalStateException("Tài khoản " + Roles.label(current) + " không cần chuyển sang vai trò chủ nhà.");
        }
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", userId);
        jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, ?)", userId, Roles.OWNER);
        jdbc.update("INSERT INTO user_role_changes(id, user_id, from_role, to_role, reason) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), userId, current, Roles.OWNER, "SELF_DECLARED_OWNER");
        return new BecomeOwnerResult(BecomeOwnerOutcome.UPGRADED, view(loadUser(userId)));
    }
    public record AuthResult(String accessToken, Instant expiresAt, UserView user, Instant idleExpiresAt) {}

    /** Staff login answer: a session, or (mfaRequired) a challenge to complete with a code or an enrolment. */
    public record AdminLoginResult(String accessToken, Instant expiresAt, Instant idleExpiresAt, UserView user,
                                   boolean mfaRequired, String mfaState, String challengeToken, Instant challengeExpiresAt) {}

    public enum TokenStatus { VALID, INVALID, EXPIRED, USED, SUPERSEDED }

    public record VerificationResult(String status, String returnTo) {}

    private record TokenRow(UUID id, UUID userId, Instant expiresAt, boolean used, boolean superseded, String returnPath) {
        TokenStatus status(Instant now) {
            if (used) return TokenStatus.USED;
            if (superseded) return TokenStatus.SUPERSEDED;
            if (!expiresAt.isAfter(now)) return TokenStatus.EXPIRED;
            return TokenStatus.VALID;
        }
    }
    public record RegistrationResult(String email, boolean requiresEmailVerification) {}
    public record KycDocumentAccess(String token, Instant expiresAt) {}
}
