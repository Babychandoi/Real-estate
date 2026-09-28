package com.company.bds.notification.domain;

import java.time.Instant;
import java.util.UUID;

/** A stored notification as it travels to SSE clients and the notification centre (frame {@code id} = {@code seq}). */
public record NotificationEvent(UUID id, long seq, UUID userId, String type, String category, String title,
                                String message, String link, Instant createdAt, Instant readAt) {}
