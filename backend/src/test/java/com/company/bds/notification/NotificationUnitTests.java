package com.company.bds.notification;

import com.company.bds.notification.domain.NotificationCategory;
import com.company.bds.notification.domain.NotificationEvent;
import com.company.bds.notification.infrastructure.NotificationSseHub;
import com.company.bds.notification.infrastructure.RedisNotificationFanout;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Pure rules of the notification module: request sanitising, category mapping, Redis failure fallback, wire format. */
class NotificationUnitTests {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    private NotificationEvent event() {
        return new NotificationEvent(UUID.randomUUID(), 42, UUID.randomUUID(), "LEAD_RECEIVED", "LEADS", "T", "M", "/my-leads",
                Instant.parse("2026-09-28T01:00:00Z"), null);
    }

    @Test
    void requestsKeepOnlySameSiteLinksAndClipLongText() {
        UUID user = UUID.randomUUID();
        assertThat(new NotificationRequest(user, "X", "t", "m", "/listings/abc?x=1", null, false).link()).isEqualTo("/listings/abc?x=1");
        for (String bad : new String[]{"https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)",
                "/a b", "/\"onmouseover", "listings/x"}) {
            assertThat(new NotificationRequest(user, "X", "t", "m", bad, null, false).link()).as(bad).isNull();
        }
        NotificationRequest long_ = new NotificationRequest(user, "X", "t".repeat(500), "m".repeat(900), null, null, false);
        assertThat(long_.title()).hasSize(NotificationRequest.MAX_TITLE);
        assertThat(long_.message()).hasSize(NotificationRequest.MAX_MESSAGE);
        assertThatThrownBy(() -> new NotificationRequest(user, "lower", "t", "m", null, null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationRequest(user, "X", "t", "m", null, " ", false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typesMapToCategories() {
        assertThat(NotificationCategory.fromType("SAVED_SEARCH_ALERT")).isEqualTo(NotificationCategory.ALERTS);
        assertThat(NotificationCategory.fromType("SAVED_LISTING_PRICE_DROP")).isEqualTo(NotificationCategory.SAVED_LISTINGS);
        assertThat(NotificationCategory.fromType("SHORTLIST_ITEM_ADDED")).isEqualTo(NotificationCategory.SHORTLIST);
        assertThat(NotificationCategory.fromType("APPOINTMENT_CONFIRMED")).isEqualTo(NotificationCategory.LEADS);
        assertThat(NotificationCategory.fromType("OWNERSHIP_VERIFIED")).isEqualTo(NotificationCategory.LISTINGS);
        assertThat(NotificationCategory.fromType("PLAN_UPGRADED")).isEqualTo(NotificationCategory.ACCOUNT);
        assertThat(NotificationCategory.ACCOUNT.mandatoryInApp()).isTrue();
        assertThat(NotificationCategory.ALERTS.mandatoryInApp()).isFalse();
    }

    @Test
    void whenRedisIsDownTheNotificationStillReachesThisInstancesStreams() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        NotificationSseHub hub = mock(NotificationSseHub.class);
        doThrow(new RedisConnectionFailureException("down")).when(redis).convertAndSend(anyString(), anyString());
        RedisNotificationFanout fanout = new RedisNotificationFanout(redis, hub, json, new SimpleMeterRegistry());
        NotificationEvent event = event();
        fanout.publish(event);
        verify(hub).deliverLocal(event);
    }

    @Test
    void publishedMessagesAreDeliveredLocallyByEverySubscriberAndGarbageIsIgnored() throws Exception {
        NotificationSseHub hub = mock(NotificationSseHub.class);
        RedisNotificationFanout fanout = new RedisNotificationFanout(mock(StringRedisTemplate.class), hub, json, new SimpleMeterRegistry());
        NotificationEvent event = event();
        String wire = json.writeValueAsString(new RedisNotificationFanout.Envelope(1, "other-node", event));
        fanout.onMessage(new DefaultMessage(RedisNotificationFanout.CHANNEL.getBytes(StandardCharsets.UTF_8),
                wire.getBytes(StandardCharsets.UTF_8)), null);
        verify(hub).deliverLocal(event);

        NotificationSseHub quiet = mock(NotificationSseHub.class);
        RedisNotificationFanout other = new RedisNotificationFanout(mock(StringRedisTemplate.class), quiet, json, new SimpleMeterRegistry());
        other.onMessage(new DefaultMessage(new byte[0], "{not json".getBytes(StandardCharsets.UTF_8)), null);
        other.onMessage(new DefaultMessage(new byte[0], "{\"v\":2,\"origin\":\"x\",\"event\":null}".getBytes(StandardCharsets.UTF_8)), null);
        verify(quiet, never()).deliverLocal(any());
    }
}
