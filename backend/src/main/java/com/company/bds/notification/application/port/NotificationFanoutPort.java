package com.company.bds.notification.application.port;

import com.company.bds.notification.domain.NotificationEvent;

/**
 * Delivers a committed notification to every application instance, each of which pushes it to its own SSE clients
 * (audit F12). Delivery is best effort: a client that misses a frame gets it from the database on reconnect.
 */
public interface NotificationFanoutPort {
    void publish(NotificationEvent event);
}
