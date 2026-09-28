package com.company.bds.iam.application;

import com.company.bds.iam.domain.ClientContext;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.Roles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Opaque bearer sessions (ADR 0001): issue, look up, list and revoke. Only the SHA-256 of the token is stored. Staff
 * sessions carry an idle timeout; activity is recorded at most once a minute so a busy tab does not write on every
 * request.
 */
@Service
public class SessionService {
    /** {@code last_seen_at} is refreshed only when older than this. */
    static final Duration TOUCH_INTERVAL = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int LIST_LIMIT = 50;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuthPolicy policy;

    public SessionService(JdbcTemplate jdbc, Clock clock, AuthPolicy policy) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.policy = policy;
    }

    public Issued issue(UUID userId, String role, ClientContext client, boolean mfaVerified) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(policy.ttlFor(role));
        Duration idle = policy.idleTimeoutFor(role);
        ClientContext context = client == null ? ClientContext.UNKNOWN : client;
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO auth_sessions(id, user_id, token_hash, expires_at, created_at, last_seen_at, idle_timeout_seconds,
                                          device_label, ip_hint, mfa_verified_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, id, userId, AuthService.sha256(token), Timestamp.from(expiresAt), Timestamp.from(now), Timestamp.from(now),
                idle == null ? null : (int) idle.toSeconds(), context.deviceLabel(), context.ipHint(),
                mfaVerified ? Timestamp.from(now) : null);
        return new Issued(id, token, expiresAt, idle == null ? null : now.plus(idle));
    }

    /**
     * The active session of a raw token: not revoked, before its absolute expiry, within its idle timeout, account
     * ACTIVE. Refreshes {@code last_seen_at} when it is older than a minute.
     */
    public ActiveSession find(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) return null;
        Timestamp now = Timestamp.from(clock.instant());
        List<ActiveSession> sessions = jdbc.query("""
                SELECT s.id, s.last_seen_at, u.id, u.full_name, u.email, %s AS role
                FROM auth_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > ? AND u.status = 'ACTIVE'
                  AND (s.idle_timeout_seconds IS NULL OR s.last_seen_at + make_interval(secs => s.idle_timeout_seconds) > ?)
                """.formatted(Roles.effectiveRoleSql("u.id")), (rs, n) -> new ActiveSession(rs.getObject(1, UUID.class),
                rs.getTimestamp(2).toInstant(), new AuthService.UserAccount(rs.getObject(3, UUID.class), rs.getString(4),
                rs.getString(5), null, rs.getString(6))), AuthService.sha256(rawToken), now, now);
        if (sessions.isEmpty()) return null;
        ActiveSession session = sessions.get(0);
        if (session.lastSeenAt().isBefore(now.toInstant().minus(TOUCH_INTERVAL))) {
            jdbc.update("UPDATE auth_sessions SET last_seen_at = ? WHERE id = ? AND last_seen_at < ?",
                    now, session.id(), Timestamp.from(now.toInstant().minus(TOUCH_INTERVAL)));
        }
        return session;
    }

    @Transactional(readOnly = true)
    public List<SessionView> list(UUID userId, UUID currentSessionId) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.query("""
                SELECT id, created_at, last_seen_at, expires_at, idle_timeout_seconds, device_label, ip_hint,
                       mfa_verified_at IS NOT NULL
                FROM auth_sessions
                WHERE user_id = ? AND revoked_at IS NULL AND expires_at > ?
                  AND (idle_timeout_seconds IS NULL OR last_seen_at + make_interval(secs => idle_timeout_seconds) > ?)
                ORDER BY last_seen_at DESC, id DESC LIMIT ?
                """, (rs, n) -> {
            UUID id = rs.getObject(1, UUID.class);
            Instant lastSeen = rs.getTimestamp(3).toInstant();
            int idle = rs.getInt(5);
            Instant idleExpiry = rs.wasNull() ? null : lastSeen.plusSeconds(idle);
            return new SessionView(id, id.equals(currentSessionId), rs.getTimestamp(2).toInstant(), lastSeen,
                    rs.getTimestamp(4).toInstant(), idleExpiry, rs.getString(6), rs.getString(7), rs.getBoolean(8));
        }, userId, now, now, LIST_LIMIT);
    }

    /** Revokes one of the user's own sessions; 404 for a session of someone else (no existence oracle). */
    @Transactional
    public void revoke(UUID userId, UUID sessionId, String reason) {
        int updated = jdbc.update("""
                UPDATE auth_sessions SET revoked_at = ?, revoked_reason = ?
                WHERE id = ? AND user_id = ? AND revoked_at IS NULL
                """, Timestamp.from(clock.instant()), reason, sessionId, userId);
        if (updated == 0) throw ApiException.notFound("SESSION_NOT_FOUND", "Không tìm thấy phiên đăng nhập đang hoạt động.");
    }

    /** Revokes every open session of the user except {@code keep} (may be null); returns how many. */
    @Transactional
    public int revokeAll(UUID userId, UUID keep, String reason) {
        return jdbc.update("""
                UPDATE auth_sessions SET revoked_at = ?, revoked_reason = ?
                WHERE user_id = ? AND revoked_at IS NULL AND (CAST(? AS uuid) IS NULL OR id <> CAST(? AS uuid))
                """, Timestamp.from(clock.instant()), reason, userId, keep, keep);
    }

    public void revokeToken(String rawToken, String reason) {
        if (rawToken == null || rawToken.isBlank()) return;
        jdbc.update("UPDATE auth_sessions SET revoked_at = ?, revoked_reason = ? WHERE token_hash = ? AND revoked_at IS NULL",
                Timestamp.from(clock.instant()), reason, AuthService.sha256(rawToken));
    }

    /** Housekeeping at login: rows past their lifetime or revoked more than a day ago (the list only shows open ones). */
    public void purgeStale() {
        Instant now = clock.instant();
        jdbc.update("DELETE FROM auth_sessions WHERE expires_at < ? OR revoked_at < ?",
                Timestamp.from(now), Timestamp.from(now.minus(Duration.ofDays(1))));
    }

    public record Issued(UUID sessionId, String token, Instant expiresAt, Instant idleExpiresAt) {}

    public record ActiveSession(UUID id, Instant lastSeenAt, AuthService.UserAccount user) {}

    public record SessionView(UUID id, boolean current, Instant createdAt, Instant lastSeenAt, Instant expiresAt,
                              Instant idleExpiresAt, String device, String ipHint, boolean mfaVerified) {}
}
