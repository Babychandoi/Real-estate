package com.company.bds.notification.application.port;

import com.company.bds.notification.domain.NotificationCategory;
import com.company.bds.notification.domain.NotificationEvent;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence of the notification centre ({@code user_notifications}, {@code notification_preferences}). */
public interface NotificationStorePort {

    /** Inserts in the caller's transaction; empty when a notification with the same user + dedupe key exists. */
    Optional<NotificationEvent> insert(UUID userId, String type, NotificationCategory category, String title, String message,
                                       @Nullable String link, @Nullable String dedupeKey);

    /** Newest first, keyset on {@code seq}: rows with {@code seq < beforeSeq} (or the newest when null). */
    List<NotificationEvent> page(UUID userId, @Nullable Long beforeSeq, boolean unreadOnly, int limit);

    long unreadCount(UUID userId);

    boolean markRead(UUID userId, UUID id);

    /** Marks every unread notification with {@code seq <= upToSeq} read; returns how many changed. */
    int markAllRead(UUID userId, long upToSeq);

    boolean delete(UUID userId, UUID id);

    /**
     * Replay for a reconnecting SSE client, oldest first: rows after {@code afterSeq}, plus rows at or below it created
     * within {@code lookBack} (a transaction that took a lower sequence number can commit after a higher one was sent).
     */
    List<NotificationEvent> replay(UUID userId, long afterSeq, Duration lookBack, int limit);

    /** Explicit choices only; missing categories use the defaults. */
    Map<NotificationCategory, ChannelChoice> preferences(UUID userId);

    void savePreference(UUID userId, NotificationCategory category, boolean inApp, boolean email);

    /** Verified e-mail address and display name of an ACTIVE account, if any. */
    Optional<Recipient> emailRecipient(UUID userId);

    record ChannelChoice(boolean inApp, boolean email) {}

    record Recipient(String email, String name) {}
}
