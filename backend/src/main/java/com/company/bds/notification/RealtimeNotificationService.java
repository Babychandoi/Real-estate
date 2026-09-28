package com.company.bds.notification;

import com.company.bds.notification.application.NotificationPreferenceService;
import com.company.bds.notification.application.UnsubscribeService;
import com.company.bds.notification.application.port.NotificationFanoutPort;
import com.company.bds.notification.application.port.NotificationStorePort;
import com.company.bds.notification.application.port.NotificationStorePort.ChannelChoice;
import com.company.bds.notification.domain.NotificationCategory;
import com.company.bds.notification.domain.NotificationEvent;
import com.company.bds.notification.infrastructure.NotificationSseHub;
import com.company.bds.shared.mail.MailMessage;
import com.company.bds.shared.mail.MailOutbox;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point of the notification centre for every module (contract §11).
 *
 * <p>{@link #notify(NotificationRequest)} stores the notification in the caller's transaction (once per user and
 * {@code dedupeKey}), honours the user's channel preferences, queues the optional e-mail through {@link MailOutbox} in
 * the same transaction and, after commit, fans it out to every instance's SSE streams. Nothing is published for a
 * transaction that rolls back.
 */
@Service
public class RealtimeNotificationService {
    private final NotificationStorePort store;
    private final NotificationFanoutPort fanout;
    private final NotificationSseHub hub;
    private final NotificationPreferenceService preferences;
    private final UnsubscribeService unsubscribe;
    private final MailOutbox mail;
    private final MeterRegistry meters;
    private final String publicBaseUrl;

    public RealtimeNotificationService(NotificationStorePort store, NotificationFanoutPort fanout, NotificationSseHub hub,
                                       NotificationPreferenceService preferences, UnsubscribeService unsubscribe,
                                       MailOutbox mail, MeterRegistry meters,
                                       @Value("${app.public-base-url}") String publicBaseUrl) {
        this.store = store;
        this.fanout = fanout;
        this.hub = hub;
        this.preferences = preferences;
        this.unsubscribe = unsubscribe;
        this.mail = mail;
        this.meters = meters;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    /** The original signature (kept for every existing caller): in-app only, no link, no dedupe. */
    public void notify(UUID userId, String type, String title, String message) {
        notify(NotificationRequest.of(userId, type, title, message));
    }

    /**
     * @return the id of the stored in-app notification; empty when it was a duplicate or the user turned in-app delivery
     *         of the category off (an e-mail may still have been queued in that case)
     */
    public Optional<UUID> notify(NotificationRequest request) {
        NotificationCategory category = NotificationCategory.fromType(request.type());
        ChannelChoice choice = preferences.choice(request.userId(), category);
        Optional<NotificationEvent> stored = Optional.empty();
        if (choice.inApp()) {
            stored = store.insert(request.userId(), request.type(), category, request.title(), request.message(),
                    request.link(), request.dedupeKey());
            if (stored.isEmpty()) {
                meters.counter("bds.notifications.created", "category", category.name(), "outcome", "duplicate").increment();
                return Optional.empty();
            }
            NotificationEvent event = stored.get();
            meters.counter("bds.notifications.created", "category", category.name(), "outcome", "stored").increment();
            afterCommit(() -> fanout.publish(event));
        } else {
            meters.counter("bds.notifications.created", "category", category.name(), "outcome", "muted").increment();
        }
        if (request.email() && choice.email()) queueEmail(request, category, stored.map(NotificationEvent::id).orElse(null));
        return stored.map(NotificationEvent::id);
    }

    /** SSE stream of the user; {@code lastSeq} from {@code Last-Event-ID} replays what the client missed. */
    public SseEmitter connect(UUID userId, @Nullable Long lastSeq) {
        return hub.open(userId, lastSeq);
    }

    public SseEmitter connect(UUID userId) {
        return connect(userId, null);
    }

    /** Newest 50 (v1 list endpoint, kept for compatibility). */
    public List<NotificationView> recent(UUID userId) {
        return store.page(userId, null, false, 50).stream()
                .map(e -> new NotificationView(e.id(), e.type(), e.title(), e.message(), e.readAt(), e.createdAt()))
                .toList();
    }

    public void markRead(UUID userId, UUID id) {
        store.markRead(userId, id);
    }

    /**
     * Queues an e-mail for a category (caller transaction) when the user's preference allows it and the account has a
     * verified address. The footer carries a one-click unsubscribe link: for the saved search when
     * {@code savedSearchId} is given, otherwise for the category's e-mails.
     *
     * @return true when a message was queued (false: preference off, no verified address, or a duplicate)
     */
    public boolean email(EmailRequest request) {
        if (!preferences.choice(request.userId(), request.category()).email()) return false;
        Optional<NotificationStorePort.Recipient> recipient = store.emailRecipient(request.userId());
        if (recipient.isEmpty()) return false;
        String token = request.savedSearchId() != null
                ? unsubscribe.issueForSavedSearch(request.userId(), request.savedSearchId())
                : unsubscribe.issueForCategory(request.userId(), request.category());
        String unsubscribePage = publicBaseUrl + "/unsubscribe?token=" + token;
        String oneClick = publicBaseUrl + "/api/v1/public/unsubscribe?token=" + token;
        String body = "Chào " + recipient.get().name() + ",\n\n" + request.body().strip() + "\n"
                + "\n—\nBạn nhận email này vì đã bật thông báo qua email."
                + "\nQuản lý thông báo: " + publicBaseUrl + "/account#thong-bao"
                + "\n" + (request.savedSearchId() != null ? "Ngừng nhận cảnh báo của tìm kiếm này: " : "Ngừng nhận email loại này: ")
                + unsubscribePage + "\n";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("List-Unsubscribe", "<" + oneClick + ">");
        headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        MailOutbox.Result result = mail.tryEnqueue(new MailMessage(recipient.get().email(), request.subject(), body, null,
                "NOTIFICATION_" + request.category().name(), "notification:" + request.userId() + ":" + request.dedupeKey(),
                headers));
        meters.counter("bds.notifications.delivered", "channel", "email", "outcome", result.outcome().name().toLowerCase())
                .increment();
        return result.outcome() == MailOutbox.Outcome.QUEUED;
    }

    private void queueEmail(NotificationRequest request, NotificationCategory category, @Nullable UUID notificationId) {
        StringBuilder body = new StringBuilder(request.title()).append("\n").append(request.message()).append("\n");
        if (request.link() != null) body.append("\nXem chi tiết: ").append(publicBaseUrl).append(request.link()).append("\n");
        String dedupe = request.dedupeKey() != null ? request.dedupeKey()
                : String.valueOf(notificationId != null ? notificationId : UUID.randomUUID());
        email(new EmailRequest(request.userId(), category, request.title(), body.toString(), dedupe, null));
    }

    /** Base URL of the web app, for absolute links in e-mails. */
    public String publicBaseUrl() { return publicBaseUrl; }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    /** An e-mail for {@link #email}; {@code dedupeKey} makes it once-only per user. */
    public record EmailRequest(UUID userId, NotificationCategory category, String subject, String body, String dedupeKey,
                               @Nullable UUID savedSearchId) {}

    public record NotificationView(UUID id, String type, String title, String message, Instant readAt, Instant createdAt) {}
}
