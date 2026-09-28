package com.company.bds.iam.application;

import com.company.bds.iam.domain.ClientContext;
import com.company.bds.iam.domain.Totp;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.PiiProtectionService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Second factor for staff (F20.3, UI-17): TOTP with single-use recovery codes. The staff login checks the password
 * and answers a short-lived challenge instead of a session; the challenge is exchanged for a session with a code
 * (VERIFY) or by confirming a new authenticator (ENROLL). Wrong codes are counted on the challenge and burn it after
 * {@link AuthPolicy#challengeMaxAttempts()}; the IP is limited separately by the rate limiter.
 */
@Service
public class MfaService {
    static final String SECRET_PURPOSE = "mfa-totp-secret";
    static final int RECOVERY_CODE_COUNT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Crockford-like alphabet without 0/O/1/I/L so codes can be read back from paper. */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final String CHALLENGE_INVALID = "Phiên xác thực hai lớp không hợp lệ hoặc đã hết hạn. Hãy đăng nhập lại.";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuthPolicy policy;
    private final PiiProtectionService crypto;
    private final SessionService sessions;
    private final SecurityEventLog events;

    public MfaService(JdbcTemplate jdbc, Clock clock, AuthPolicy policy, PiiProtectionService crypto,
                      SessionService sessions, SecurityEventLog events) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.policy = policy;
        this.crypto = crypto;
        this.sessions = sessions;
        this.events = events;
    }

    public boolean isEnrolled(UUID userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM user_mfa WHERE user_id = ?", Integer.class, userId);
        return count != null && count > 0;
    }

    /** Challenge for a staff account whose password was just verified; ENROLL when no authenticator is confirmed yet. */
    public Challenge startChallenge(UUID userId) {
        Instant now = clock.instant();
        String purpose = isEnrolled(userId) ? "VERIFY" : "ENROLL";
        // One open challenge per account: a new login replaces the previous one.
        jdbc.update("UPDATE mfa_challenges SET consumed_at = ? WHERE user_id = ? AND consumed_at IS NULL", Timestamp.from(now), userId);
        jdbc.update("DELETE FROM mfa_challenges WHERE expires_at < ?", Timestamp.from(now.minus(java.time.Duration.ofDays(1))));
        String token = randomToken();
        Instant expiresAt = now.plus(policy.challengeTtl());
        jdbc.update("""
                INSERT INTO mfa_challenges(id, user_id, token_hash, purpose, expires_at, created_at) VALUES (?,?,?,?,?,?)
                """, UUID.randomUUID(), userId, AuthService.sha256(token), purpose, Timestamp.from(expiresAt), Timestamp.from(now));
        return new Challenge(purpose, token, expiresAt);
    }

    /** New secret for an ENROLL challenge (replaces one generated earlier for the same challenge). */
    @Transactional
    public Enrollment beginEnrollment(String challengeToken) {
        OpenChallenge challenge = lockChallenge(challengeToken, "ENROLL");
        String secret = Totp.newSecret();
        jdbc.update("UPDATE mfa_challenges SET pending_secret_sealed = ? WHERE id = ?",
                crypto.seal(secret, SECRET_PURPOSE), challenge.id());
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, challenge.userId());
        return new Enrollment(secret, Totp.otpauthUri(policy.mfaIssuer(), email == null ? "admin" : email, secret),
                challenge.expiresAt());
    }

    /** Confirms the authenticator with its first code: stores the secret, issues recovery codes and a session. */
    @Transactional(noRollbackFor = ApiException.class)
    public Completed confirmEnrollment(String challengeToken, String code, ClientContext client) {
        OpenChallenge challenge = lockChallenge(challengeToken, "ENROLL");
        if (challenge.pendingSecretSealed() == null) {
            throw ApiException.conflict("MFA_ENROLLMENT_NOT_STARTED", "Hãy tạo khóa cho ứng dụng xác thực trước khi nhập mã.");
        }
        if (isEnrolled(challenge.userId())) {
            consume(challenge.id());
            throw ApiException.conflict("MFA_ALREADY_ENROLLED", "Tài khoản đã bật xác thực hai lớp. Hãy đăng nhập lại.");
        }
        String secret = crypto.unseal(challenge.pendingSecretSealed(), SECRET_PURPOSE);
        OptionalLong step = Totp.verify(secret, code, clock.instant(), 0);
        if (step.isEmpty()) throw wrongCode(challenge, client);
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO user_mfa(user_id, secret_sealed, confirmed_at, last_used_step, created_at, updated_at)
                VALUES (?,?,?,?,?,?)
                """, challenge.userId(), crypto.seal(secret, SECRET_PURPOSE), Timestamp.from(now), step.getAsLong(),
                Timestamp.from(now), Timestamp.from(now));
        List<String> codes = replaceRecoveryCodes(challenge.userId());
        consume(challenge.id());
        events.record(challenge.userId(), SecurityEventLog.Type.MFA_ENROLLED, client);
        return new Completed(issueSession(challenge.userId(), client), codes);
    }

    /** Exchanges a VERIFY challenge for a session with a TOTP code or one unused recovery code. */
    @Transactional(noRollbackFor = ApiException.class)
    public SignedIn verify(String challengeToken, String code, String recoveryCode, ClientContext client) {
        OpenChallenge challenge = lockChallenge(challengeToken, "VERIFY");
        UUID userId = challenge.userId();
        if (recoveryCode != null && !recoveryCode.isBlank()) {
            int used = jdbc.update("""
                    UPDATE user_mfa_recovery_codes SET used_at = ? WHERE user_id = ? AND code_hash = ? AND used_at IS NULL
                    """, Timestamp.from(clock.instant()), userId, AuthService.sha256(normalizeRecoveryCode(recoveryCode)));
            if (used == 0) throw wrongCode(challenge, client);
            consume(challenge.id());
            events.record(userId, SecurityEventLog.Type.MFA_RECOVERY_CODE_USED, client);
            return issueSession(userId, client);
        }
        List<Object[]> rows = jdbc.query("SELECT secret_sealed, last_used_step FROM user_mfa WHERE user_id = ? FOR UPDATE",
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)}, userId);
        if (rows.isEmpty()) {
            consume(challenge.id());
            throw ApiException.conflict("MFA_NOT_ENROLLED", "Xác thực hai lớp đã bị đặt lại. Hãy đăng nhập lại để thiết lập.");
        }
        String secret = crypto.unseal((String) rows.get(0)[0], SECRET_PURPOSE);
        OptionalLong step = Totp.verify(secret, code, clock.instant(), (Long) rows.get(0)[1]);
        if (step.isEmpty()) throw wrongCode(challenge, client);
        jdbc.update("UPDATE user_mfa SET last_used_step = ?, updated_at = ? WHERE user_id = ?",
                step.getAsLong(), Timestamp.from(clock.instant()), userId);
        consume(challenge.id());
        events.record(userId, SecurityEventLog.Type.MFA_SUCCEEDED, client);
        return issueSession(userId, client);
    }

    @Transactional(readOnly = true)
    public Status status(UUID userId) {
        List<Instant> confirmed = jdbc.query("SELECT confirmed_at FROM user_mfa WHERE user_id = ?",
                (rs, n) -> rs.getTimestamp(1).toInstant(), userId);
        Integer remaining = jdbc.queryForObject(
                "SELECT count(*) FROM user_mfa_recovery_codes WHERE user_id = ? AND used_at IS NULL", Integer.class, userId);
        return new Status(!confirmed.isEmpty(), confirmed.isEmpty() ? null : confirmed.get(0), remaining == null ? 0 : remaining,
                policy.mfaRequired());
    }

    /** New recovery codes (old ones stop working) after a fresh TOTP code from the signed-in account. */
    @Transactional
    public List<String> regenerateRecoveryCodes(UUID userId, String code, ClientContext client) {
        List<Object[]> rows = jdbc.query("SELECT secret_sealed, last_used_step FROM user_mfa WHERE user_id = ? FOR UPDATE",
                (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)}, userId);
        if (rows.isEmpty()) throw ApiException.conflict("MFA_NOT_ENROLLED", "Tài khoản chưa bật xác thực hai lớp.");
        OptionalLong step = Totp.verify(crypto.unseal((String) rows.get(0)[0], SECRET_PURPOSE), code, clock.instant(),
                (Long) rows.get(0)[1]);
        if (step.isEmpty()) throw ApiException.badRequest("MFA_CODE_INVALID", "Mã xác thực không đúng hoặc đã được dùng.");
        jdbc.update("UPDATE user_mfa SET last_used_step = ?, updated_at = ? WHERE user_id = ?",
                step.getAsLong(), Timestamp.from(clock.instant()), userId);
        List<String> codes = replaceRecoveryCodes(userId);
        events.record(userId, SecurityEventLog.Type.MFA_RECOVERY_CODES_REGENERATED, client);
        return codes;
    }

    /** Admin reset (lost phone): removes the authenticator and codes; the next staff login must enrol again. */
    public boolean reset(UUID userId) {
        jdbc.update("DELETE FROM user_mfa_recovery_codes WHERE user_id = ?", userId);
        jdbc.update("UPDATE mfa_challenges SET consumed_at = ? WHERE user_id = ? AND consumed_at IS NULL",
                Timestamp.from(clock.instant()), userId);
        return jdbc.update("DELETE FROM user_mfa WHERE user_id = ?", userId) > 0;
    }

    private SignedIn issueSession(UUID userId, ClientContext client) {
        String role = jdbc.queryForObject("SELECT " + com.company.bds.shared.security.Roles.effectiveRoleSql("u.id")
                + " FROM users u WHERE u.id = ? AND u.status = 'ACTIVE'", String.class, userId);
        return new SignedIn(userId, sessions.issue(userId, role, client, true));
    }

    private ApiException wrongCode(OpenChallenge challenge, ClientContext client) {
        int attempts = challenge.failedAttempts() + 1;
        boolean locked = attempts >= policy.challengeMaxAttempts();
        jdbc.update("UPDATE mfa_challenges SET failed_attempts = ?, consumed_at = CASE WHEN ? THEN ? ELSE consumed_at END WHERE id = ?",
                attempts, locked, Timestamp.from(clock.instant()), challenge.id());
        events.record(challenge.userId(), locked ? SecurityEventLog.Type.MFA_CHALLENGE_LOCKED : SecurityEventLog.Type.MFA_FAILED, client);
        if (locked) {
            return new ApiException(HttpStatus.UNAUTHORIZED, "MFA_CHALLENGE_LOCKED",
                    "Nhập sai mã quá nhiều lần. Hãy đăng nhập lại từ đầu.");
        }
        int left = policy.challengeMaxAttempts() - attempts;
        return ApiException.badRequest("MFA_CODE_INVALID", "Mã xác thực không đúng hoặc đã được dùng. Còn " + left + " lần thử.");
    }

    private OpenChallenge lockChallenge(String token, String purpose) {
        if (token == null || token.isBlank() || token.length() > 128) throw challengeInvalid();
        List<OpenChallenge> rows = jdbc.query("""
                SELECT c.id, c.user_id, c.pending_secret_sealed, c.failed_attempts, c.expires_at
                FROM mfa_challenges c JOIN users u ON u.id = c.user_id
                WHERE c.token_hash = ? AND c.purpose = ? AND c.consumed_at IS NULL AND c.expires_at > ? AND u.status = 'ACTIVE'
                FOR UPDATE OF c
                """, (rs, n) -> new OpenChallenge(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getInt(4), rs.getTimestamp(5).toInstant()), AuthService.sha256(token), purpose, Timestamp.from(clock.instant()));
        if (rows.isEmpty()) throw challengeInvalid();
        return rows.get(0);
    }

    private static ApiException challengeInvalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "MFA_CHALLENGE_INVALID", CHALLENGE_INVALID);
    }

    private void consume(UUID challengeId) {
        jdbc.update("UPDATE mfa_challenges SET consumed_at = ? WHERE id = ?", Timestamp.from(clock.instant()), challengeId);
    }

    private List<String> replaceRecoveryCodes(UUID userId) {
        jdbc.update("DELETE FROM user_mfa_recovery_codes WHERE user_id = ?", userId);
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = randomRecoveryCode();
            codes.add(code);
            jdbc.update("INSERT INTO user_mfa_recovery_codes(id, user_id, code_hash, created_at) VALUES (?,?,?,?)",
                    UUID.randomUUID(), userId, AuthService.sha256(normalizeRecoveryCode(code)), Timestamp.from(clock.instant()));
        }
        return codes;
    }

    /** 10 characters from a 31-symbol alphabet (~49 bits), shown as XXXXX-XXXXX. */
    static String randomRecoveryCode() {
        StringBuilder out = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) out.append('-');
            out.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return out.toString();
    }

    static String normalizeRecoveryCode(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record OpenChallenge(UUID id, UUID userId, String pendingSecretSealed, int failedAttempts, Instant expiresAt) {}

    public record Challenge(String state, String token, Instant expiresAt) {}

    public record Enrollment(String secret, String otpauthUri, Instant challengeExpiresAt) {}

    public record Completed(SignedIn session, List<String> recoveryCodes) {}

    public record SignedIn(UUID userId, SessionService.Issued issued) {}

    public record Status(boolean enrolled, Instant enrolledAt, int recoveryCodesRemaining, boolean required) {}
}
