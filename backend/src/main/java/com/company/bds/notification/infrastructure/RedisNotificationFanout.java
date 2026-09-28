package com.company.bds.notification.infrastructure;

import com.company.bds.notification.application.port.NotificationFanoutPort;
import com.company.bds.notification.domain.NotificationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Cross-instance fan-out over Redis Pub/Sub, channel {@value #CHANNEL} (contract §11). Every instance subscribes, the
 * publisher included, and delivers the message to its local streams; so each stream receives a notification once no
 * matter which instance wrote it. Pub/Sub is fire-and-forget: history and replay stay in PostgreSQL. When publishing
 * fails (Redis down) the notification is delivered to this instance's streams directly and the other instances' clients
 * get it from the database when they reconnect.
 */
public class RedisNotificationFanout implements NotificationFanoutPort, MessageListener {
    public static final String CHANNEL = "bds:notifications:v1";
    private static final Logger log = LoggerFactory.getLogger(RedisNotificationFanout.class);

    private final StringRedisTemplate redis;
    private final NotificationSseHub hub;
    private final ObjectMapper json;
    private final MeterRegistry meters;
    private final String instanceId = UUID.randomUUID().toString();

    public RedisNotificationFanout(StringRedisTemplate redis, NotificationSseHub hub, ObjectMapper json, MeterRegistry meters) {
        this.redis = redis;
        this.hub = hub;
        this.json = json;
        this.meters = meters;
    }

    @Override
    public void publish(NotificationEvent event) {
        try {
            redis.convertAndSend(CHANNEL, json.writeValueAsString(new Envelope(1, instanceId, event)));
            meters.counter("bds.notifications.fanout", "outcome", "published").increment();
        } catch (Exception ex) {
            meters.counter("bds.notifications.fanout", "outcome", "local_fallback").increment();
            log.warn("notification_fanout_failed fallback=local reason={}", ex.getClass().getSimpleName());
            hub.deliverLocal(event);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Envelope envelope = json.readValue(new String(message.getBody(), StandardCharsets.UTF_8), Envelope.class);
            if (envelope.v() != 1 || envelope.event() == null) return;
            meters.counter("bds.notifications.fanout", "outcome",
                    instanceId.equals(envelope.origin()) ? "received_own" : "received_remote").increment();
            hub.deliverLocal(envelope.event());
        } catch (Exception ex) {
            meters.counter("bds.notifications.fanout", "outcome", "invalid").increment();
            log.warn("notification_fanout_invalid_message reason={}", ex.getClass().getSimpleName());
        }
    }

    public String instanceId() { return instanceId; }

    /** Wire format; {@code origin} is the publishing instance (metrics only). */
    public record Envelope(int v, String origin, NotificationEvent event) {}
}
