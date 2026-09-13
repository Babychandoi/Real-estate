package com.company.bds.notification;

import com.company.bds.shared.security.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final RealtimeNotificationService service;
    public NotificationController(RealtimeNotificationService service) { this.service = service; }
    @GetMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication auth) { return service.connect(CurrentUser.id(auth)); }
    @GetMapping public List<RealtimeNotificationService.NotificationView> recent(Authentication auth) { return service.recent(CurrentUser.id(auth)); }
    @PostMapping("/{id}/read") public void read(@PathVariable UUID id, Authentication auth) { service.markRead(CurrentUser.id(auth), id); }
}
