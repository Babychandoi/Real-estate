package com.company.bds.notification.api;

import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.notification.application.NotificationCenterService;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/** Notification centre of the signed-in user (contract §11). Private: {@code no-store} via SensitiveResponseCacheFilter. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final RealtimeNotificationService notifications;
    private final NotificationCenterService center;

    public NotificationController(RealtimeNotificationService notifications, NotificationCenterService center) {
        this.notifications = notifications;
        this.center = center;
    }

    /**
     * SSE stream. {@code Last-Event-ID} (sent by the client after a reconnect; the {@code lastEventId} query parameter is
     * accepted for clients that cannot set headers) replays what was missed from the database.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication auth,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
                             @RequestParam(value = "lastEventId", required = false) String lastEventIdParam) {
        String raw = lastEventIdHeader != null ? lastEventIdHeader : lastEventIdParam;
        return notifications.connect(CurrentUser.id(auth), parseSeq(raw));
    }

    /** v1 list (newest 50), kept for compatibility; the centre uses {@code /feed}. */
    @GetMapping
    public List<RealtimeNotificationService.NotificationView> recent(Authentication auth) {
        return notifications.recent(CurrentUser.id(auth));
    }

    @GetMapping("/feed")
    public NotificationCenterService.Feed feed(Authentication auth,
                                               @RequestParam(value = "before", required = false) Long before,
                                               @RequestParam(value = "size", defaultValue = "20") int size,
                                               @RequestParam(value = "unread", defaultValue = "false") boolean unread) {
        if (size < 1 || size > 50) throw ApiException.badRequest("INVALID_SIZE", "Số thông báo mỗi trang phải từ 1 đến 50.");
        if (before != null && before < 1) throw ApiException.badRequest("INVALID_CURSOR", "Vị trí trang không hợp lệ.");
        return center.feed(CurrentUser.id(auth), before, size, unread);
    }

    @GetMapping("/unread-count")
    public NotificationCenterService.UnreadCount unreadCount(Authentication auth) {
        return center.unreadCount(CurrentUser.id(auth));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable UUID id, Authentication auth) {
        notifications.markRead(CurrentUser.id(auth), id);
        return ResponseEntity.noContent().build();
    }

    /** Marks everything up to {@code upToSeq} read (what the user has seen), so a notification arriving meanwhile stays unread. */
    @PostMapping("/read-all")
    public NotificationCenterService.UnreadCount readAll(Authentication auth, @RequestBody ReadAllRequest body) {
        if (body == null || body.upToSeq() == null || body.upToSeq() < 0) {
            throw ApiException.badRequest("INVALID_SEQ", "Thiếu vị trí thông báo mới nhất đã xem.");
        }
        return center.markAllRead(CurrentUser.id(auth), body.upToSeq());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        if (!center.delete(CurrentUser.id(auth), id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Không tìm thấy thông báo.");
        }
        return ResponseEntity.noContent().build();
    }

    static Long parseSeq(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (!value.matches("\\d{1,18}")) return null;
        return Long.parseLong(value);
    }

    public record ReadAllRequest(Long upToSeq) {}
}
