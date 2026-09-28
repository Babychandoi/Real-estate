package com.company.bds.notification.infrastructure;

import com.company.bds.notification.application.port.NotificationStorePort;
import com.company.bds.notification.domain.NotificationEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The SSE streams open on this instance (audit F12). Frames follow contract §11: {@code id: <seq>},
 * {@code event: notification}, {@code data: {id, seq, type, category, title, message, link, createdAt}}.
 *
 * <p>Opening a stream with a {@code Last-Event-ID} first registers the client (so nothing published meanwhile is missed),
 * then replays from the database; the client drops frames it has already seen by {@code id}. A replay larger than
 * {@code replay-limit} is not streamed: the client gets {@code event: resync} and reloads the list over REST. After the
 * replay the server sends {@code event: ready}. A comment heartbeat keeps proxies from closing idle streams and detects
 * dead clients; every failure removes the emitter. At most {@code max-streams-per-user} streams per user and instance:
 * opening one more closes the oldest.
 */
@Component
public class NotificationSseHub {
    private static final Logger log = LoggerFactory.getLogger(NotificationSseHub.class);

    private final NotificationStorePort store;
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Client>> clients = new ConcurrentHashMap<>();
    private final long timeoutMs;
    private final int maxStreamsPerUser;
    private final Duration replayLookBack;
    private final int replayLimit;
    private final long retryMs;
    private final Counter delivered;
    private final Counter dropped;
    private final AtomicLong sequence = new AtomicLong();

    public NotificationSseHub(NotificationStorePort store, MeterRegistry meters,
                              @Value("${app.notifications.stream-timeout:PT30M}") Duration timeout,
                              @Value("${app.notifications.max-streams-per-user:5}") int maxStreamsPerUser,
                              @Value("${app.notifications.replay-look-back:PT2M}") Duration replayLookBack,
                              @Value("${app.notifications.replay-limit:200}") int replayLimit,
                              @Value("${app.notifications.client-retry:PT3S}") Duration retry) {
        this.store = store;
        this.timeoutMs = timeout.toMillis();
        this.maxStreamsPerUser = Math.max(1, maxStreamsPerUser);
        this.replayLookBack = replayLookBack;
        this.replayLimit = Math.max(1, replayLimit);
        this.retryMs = retry.toMillis();
        Gauge.builder("bds.sse.connections", clients, map -> map.values().stream().mapToInt(List::size).sum())
                .description("Open notification SSE streams on this instance").register(meters);
        this.delivered = Counter.builder("bds.notifications.delivered").tag("channel", "sse").tag("outcome", "sent")
                .description("Notification frames written to SSE streams").register(meters);
        this.dropped = Counter.builder("bds.sse.dropped").description("SSE streams closed after a failed write")
                .register(meters);
    }

    /** Opens a stream for {@code userId}; {@code lastSeq} = the client's {@code Last-Event-ID}, or null for a fresh start. */
    public SseEmitter open(UUID userId, @Nullable Long lastSeq) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        Client client = new Client(userId, emitter, sequence.incrementAndGet());
        // compute (not computeIfAbsent + add): atomic with remove(), which drops the list once it is empty.
        CopyOnWriteArrayList<Client> list = clients.compute(userId, (id, current) -> {
            CopyOnWriteArrayList<Client> value = current == null ? new CopyOnWriteArrayList<>() : current;
            value.add(client);
            return value;
        });
        emitter.onCompletion(() -> remove(client));
        emitter.onTimeout(() -> { remove(client); emitter.complete(); });
        emitter.onError(ignored -> remove(client));
        enforceCap(list);

        if (!send(client, SseEmitter.event().reconnectTime(retryMs).comment("connected"))) return emitter;
        if (lastSeq != null) {
            List<NotificationEvent> rows = store.replay(userId, lastSeq, replayLookBack, replayLimit + 1);
            if (rows.size() > replayLimit) {
                if (!send(client, SseEmitter.event().name("resync").data(Map.of("reason", "TOO_MANY")))) return emitter;
            } else {
                for (NotificationEvent row : rows) {
                    if (!send(client, frame(row))) return emitter;
                }
            }
        }
        send(client, SseEmitter.event().name("ready").data(Map.of("replayed", lastSeq != null)));
        return emitter;
    }

    /** Pushes a committed notification to this instance's streams of its user. */
    public void deliverLocal(NotificationEvent event) {
        List<Client> list = clients.get(event.userId());
        if (list == null) return;
        for (Client client : list) {
            if (send(client, frame(event))) delivered.increment();
        }
    }

    @Scheduled(fixedDelayString = "${app.notifications.heartbeat-ms:25000}")
    public void heartbeat() {
        clients.values().forEach(list -> list.forEach(client -> send(client, SseEmitter.event().comment("hb"))));
    }

    /** Open streams on this instance (all users). */
    public int openStreams() {
        return clients.values().stream().mapToInt(List::size).sum();
    }

    public int openStreams(UUID userId) {
        List<Client> list = clients.get(userId);
        return list == null ? 0 : list.size();
    }

    static SseEmitter.SseEventBuilder frame(NotificationEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", event.id());
        data.put("seq", event.seq());
        data.put("type", event.type());
        data.put("category", event.category());
        data.put("title", event.title());
        data.put("message", event.message());
        data.put("link", event.link());
        data.put("createdAt", event.createdAt());
        return SseEmitter.event().id(Long.toString(event.seq())).name("notification").data(data);
    }

    private void enforceCap(CopyOnWriteArrayList<Client> list) {
        if (list.size() <= maxStreamsPerUser) return;
        List<Client> ordered = new ArrayList<>(list);
        ordered.sort((a, b) -> Long.compare(a.order, b.order));
        for (int i = 0; i < ordered.size() - maxStreamsPerUser; i++) {
            Client oldest = ordered.get(i);
            remove(oldest);
            try {
                oldest.emitter.complete();
            } catch (RuntimeException ignored) {
                // Already closed by the container.
            }
        }
    }

    private boolean send(Client client, SseEmitter.SseEventBuilder event) {
        try {
            client.emitter.send(event);
            return true;
        } catch (Exception ex) {
            // IOException (client gone) or IllegalStateException (emitter already completed): forget the stream.
            dropped.increment();
            remove(client);
            try {
                client.emitter.complete();
            } catch (RuntimeException ignored) {
                // Completing twice is harmless.
            }
            log.debug("sse_stream_dropped reason={}", ex.getClass().getSimpleName());
            return false;
        }
    }

    private void remove(Client client) {
        clients.computeIfPresent(client.userId, (id, list) -> {
            list.remove(client);
            return list.isEmpty() ? null : list;
        });
    }

    private record Client(UUID userId, SseEmitter emitter, long order) {}
}
