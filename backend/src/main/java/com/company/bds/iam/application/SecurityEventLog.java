package com.company.bds.iam.application;

import com.company.bds.iam.domain.ClientContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Account security history (UI-17): what happened to the account's credentials and sessions, shown to the account
 * owner. Stores only the event type, a network prefix and a device label — never an address, code or token.
 * Every event also increments {@code bds.auth.events{type}} for the MFA-failure alert.
 */
@Component
public class SecurityEventLog {
    public enum Type {
        LOGIN_SUCCEEDED, LOGIN_FAILED, MFA_SUCCEEDED, MFA_FAILED, MFA_RECOVERY_CODE_USED, MFA_ENROLLED,
        MFA_RECOVERY_CODES_REGENERATED, MFA_RESET_BY_ADMIN, MFA_CHALLENGE_LOCKED, PASSWORD_CHANGED, PASSWORD_RESET,
        SESSION_REVOKED, OTHER_SESSIONS_REVOKED, SESSIONS_REVOKED_BY_ADMIN
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final MeterRegistry meters;
    private final TransactionTemplate independent;

    public SecurityEventLog(JdbcTemplate jdbc, Clock clock, MeterRegistry meters, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.meters = meters;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Recorded in the caller's transaction (rolled back with it). */
    public void record(UUID userId, Type type, ClientContext client, UUID actorId) {
        insert(userId, type, client, actorId);
    }

    public void record(UUID userId, Type type, ClientContext client) { record(userId, type, client, null); }

    /** Recorded in its own transaction: failures are kept even though the request that caused them is refused. */
    public void recordIndependently(UUID userId, Type type, ClientContext client) {
        independent.executeWithoutResult(status -> insert(userId, type, client, null));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Event> recent(UUID userId, int limit) {
        return jdbc.query("""
                SELECT id, event_type, ip_hint, device_label, actor_id IS NOT NULL AND actor_id <> user_id, created_at
                FROM auth_security_events WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?
                """, (rs, n) -> new Event(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getBoolean(5), rs.getTimestamp(6).toInstant()), userId, Math.max(1, Math.min(limit, 100)));
    }

    private void insert(UUID userId, Type type, ClientContext client, UUID actorId) {
        ClientContext context = client == null ? ClientContext.UNKNOWN : client;
        jdbc.update("""
                INSERT INTO auth_security_events(id, user_id, event_type, ip_hint, device_label, actor_id, created_at)
                VALUES (?,?,?,?,?,?,?)
                """, UUID.randomUUID(), userId, type.name(), context.ipHint(), context.deviceLabel(), actorId,
                Timestamp.from(clock.instant()));
        Counter.builder("bds.auth.events").description("Account security events by type")
                .tag("type", type.name()).register(meters).increment();
    }

    public record Event(UUID id, String type, String ipHint, String device, boolean byAdmin, Instant occurredAt) {}
}
