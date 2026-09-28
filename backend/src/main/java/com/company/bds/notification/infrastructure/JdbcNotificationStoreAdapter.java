package com.company.bds.notification.infrastructure;

import com.company.bds.notification.application.port.NotificationStorePort;
import com.company.bds.notification.domain.NotificationCategory;
import com.company.bds.notification.domain.NotificationEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcNotificationStoreAdapter implements NotificationStorePort {
    private static final String COLUMNS = "id, seq, user_id, type, category, title, message, link, created_at, read_at";
    private static final RowMapper<NotificationEvent> ROW = (rs, n) -> new NotificationEvent(
            rs.getObject("id", UUID.class), rs.getLong("seq"), rs.getObject("user_id", UUID.class), rs.getString("type"),
            rs.getString("category"), rs.getString("title"), rs.getString("message"), rs.getString("link"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcNotificationStoreAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<NotificationEvent> insert(UUID userId, String type, NotificationCategory category, String title,
                                              String message, @Nullable String link, @Nullable String dedupeKey) {
        List<NotificationEvent> rows = jdbc.query("""
                INSERT INTO user_notifications (id, user_id, type, category, title, message, link, dedupe_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL DO NOTHING
                RETURNING""" + " " + COLUMNS,
                ROW, UUID.randomUUID(), userId, type, category.name(), title, message, link, dedupeKey);
        return rows.stream().findFirst();
    }

    @Override
    public List<NotificationEvent> page(UUID userId, @Nullable Long beforeSeq, boolean unreadOnly, int limit) {
        String unread = unreadOnly ? " AND read_at IS NULL" : "";
        if (beforeSeq == null) {
            return jdbc.query("SELECT " + COLUMNS + " FROM user_notifications WHERE user_id = ?" + unread
                    + " ORDER BY seq DESC LIMIT ?", ROW, userId, limit);
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM user_notifications WHERE user_id = ? AND seq < ?" + unread
                + " ORDER BY seq DESC LIMIT ?", ROW, userId, beforeSeq, limit);
    }

    @Override
    public long unreadCount(UUID userId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM user_notifications WHERE user_id = ? AND read_at IS NULL",
                Long.class, userId);
        return count == null ? 0 : count;
    }

    @Override
    public boolean markRead(UUID userId, UUID id) {
        return jdbc.update("UPDATE user_notifications SET read_at = now() WHERE id = ? AND user_id = ? AND read_at IS NULL",
                id, userId) > 0;
    }

    @Override
    public int markAllRead(UUID userId, long upToSeq) {
        return jdbc.update("UPDATE user_notifications SET read_at = now() WHERE user_id = ? AND read_at IS NULL AND seq <= ?",
                userId, upToSeq);
    }

    @Override
    public boolean delete(UUID userId, UUID id) {
        return jdbc.update("DELETE FROM user_notifications WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    @Override
    public List<NotificationEvent> replay(UUID userId, long afterSeq, Duration lookBack, int limit) {
        return jdbc.query("SELECT " + COLUMNS + """
                 FROM user_notifications
                WHERE user_id = ? AND (seq > ? OR created_at > now() - make_interval(secs => ?))
                ORDER BY seq LIMIT ?""", ROW, userId, afterSeq, (double) lookBack.toSeconds(), limit);
    }

    @Override
    public Map<NotificationCategory, ChannelChoice> preferences(UUID userId) {
        Map<NotificationCategory, ChannelChoice> out = new EnumMap<>(NotificationCategory.class);
        jdbc.query("SELECT category, in_app, email FROM notification_preferences WHERE user_id = ?", rs -> {
            out.put(NotificationCategory.valueOf(rs.getString(1)), new ChannelChoice(rs.getBoolean(2), rs.getBoolean(3)));
        }, userId);
        return out;
    }

    @Override
    public void savePreference(UUID userId, NotificationCategory category, boolean inApp, boolean email) {
        jdbc.update("""
                INSERT INTO notification_preferences (user_id, category, in_app, email, updated_at) VALUES (?, ?, ?, ?, now())
                ON CONFLICT (user_id, category) DO UPDATE SET in_app = EXCLUDED.in_app, email = EXCLUDED.email,
                    updated_at = now()""", userId, category.name(), inApp, email);
    }

    @Override
    public Optional<Recipient> emailRecipient(UUID userId) {
        return jdbc.query("""
                SELECT email, full_name FROM users
                 WHERE id = ? AND status = 'ACTIVE' AND email IS NOT NULL AND email_verified_at IS NOT NULL""",
                (rs, n) -> new Recipient(rs.getString(1), rs.getString(2)), userId).stream().findFirst();
    }
}
