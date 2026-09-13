package com.company.bds.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RealtimeNotificationService {
    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> clients = new ConcurrentHashMap<>();

    public RealtimeNotificationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public SseEmitter connect(UUID userId) {
        SseEmitter emitter = new SseEmitter(30 * 60_000L);
        clients.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ignored -> remove(userId, emitter));
        send(emitter, "connected", java.util.Map.of("ok", true));
        return emitter;
    }

    public void notify(UUID userId, String type, String title, String message) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO user_notifications(id,user_id,type,title,message) VALUES (?,?,?,?,?)",
                id, userId, type, title, message);
        var payload = java.util.Map.of("id", id, "type", type, "title", title, "message", message);
        clients.getOrDefault(userId, new CopyOnWriteArrayList<>()).forEach(e -> send(e, "notification", payload));
    }

    public List<NotificationView> recent(UUID userId) {
        return jdbc.query("SELECT id,type,title,message,read_at,created_at FROM user_notifications WHERE user_id=? ORDER BY created_at DESC LIMIT 50",
                (rs, n) -> new NotificationView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant()), userId);
    }

    public void markRead(UUID userId, UUID id) {
        jdbc.update("UPDATE user_notifications SET read_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND read_at IS NULL", id, userId);
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try { emitter.send(SseEmitter.event().name(event).data(data)); }
        catch (IOException ex) { emitter.complete(); }
    }
    private void remove(UUID userId, SseEmitter emitter) {
        var list = clients.get(userId); if (list != null) list.remove(emitter);
    }
    public record NotificationView(UUID id, String type, String title, String message, java.time.Instant readAt, java.time.Instant createdAt) {}
}
