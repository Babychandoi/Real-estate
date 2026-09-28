package com.company.bds.notification.application;

import com.company.bds.notification.application.port.NotificationStorePort;
import com.company.bds.notification.domain.NotificationEvent;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reads and state changes of the notification centre (always scoped to the signed-in user). */
@Service
public class NotificationCenterService {
    private final NotificationStorePort store;

    public NotificationCenterService(NotificationStorePort store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public Feed feed(UUID userId, @Nullable Long beforeSeq, int size, boolean unreadOnly) {
        List<NotificationEvent> rows = store.page(userId, beforeSeq, unreadOnly, size + 1);
        boolean hasNext = rows.size() > size;
        List<Item> items = rows.stream().limit(size).map(Item::of).toList();
        Long next = hasNext ? items.get(items.size() - 1).seq() : null;
        return new Feed(items, next, store.unreadCount(userId));
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount(UUID userId) {
        List<NotificationEvent> newest = store.page(userId, null, false, 1);
        return new UnreadCount(store.unreadCount(userId), newest.isEmpty() ? 0 : newest.get(0).seq());
    }

    @Transactional
    public UnreadCount markAllRead(UUID userId, long upToSeq) {
        store.markAllRead(userId, upToSeq);
        return unreadCount(userId);
    }

    @Transactional
    public boolean delete(UUID userId, UUID id) {
        return store.delete(userId, id);
    }

    public record Item(UUID id, long seq, String type, String category, String title, String message, String link,
                       Instant createdAt, Instant readAt) {
        static Item of(NotificationEvent e) {
            return new Item(e.id(), e.seq(), e.type(), e.category(), e.title(), e.message(), e.link(), e.createdAt(), e.readAt());
        }
    }

    /** {@code nextBefore}: pass as {@code before} for the next page; null on the last page. */
    public record Feed(List<Item> items, Long nextBefore, long unreadCount) {}

    public record UnreadCount(long count, long latestSeq) {}
}
