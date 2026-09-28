package com.company.bds.engagement.application;

import com.company.bds.analytics.application.AnalyticsRecorder;
import com.company.bds.engagement.application.port.SavedListingStorePort;
import com.company.bds.engagement.application.port.SavedListingStorePort.GoneListing;
import com.company.bds.engagement.application.port.SavedListingStorePort.SavedRow;
import com.company.bds.shared.error.ApiException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Account favourites ("tin đã lưu", audit P-02). Saving is idempotent and only possible for a publicly visible listing;
 * a saved listing that stops being public stays in the list as "không còn hiển thị" (title only when its owner withdrew
 * it). {@value #MAX_SAVED} per account.
 */
@Service
public class SavedListingService {
    public static final int MAX_SAVED = 500;

    private final SavedListingStorePort store;
    private final AnalyticsRecorder analytics;
    private final Clock clock;

    public SavedListingService(SavedListingStorePort store, AnalyticsRecorder analytics, Clock clock) {
        this.store = store;
        this.analytics = analytics;
        this.clock = clock;
    }

    /** @return when the listing was saved (the original time when it already was) */
    @Transactional
    public Instant save(UUID userId, UUID listingId) {
        Optional<Instant> existing = store.savedAt(userId, listingId);
        if (existing.isPresent()) return existing.get();
        if (!store.isPublic(listingId)) {
            throw ApiException.notFound("LISTING_NOT_FOUND", "Tin đăng không tồn tại hoặc không còn hiển thị.");
        }
        store.lockUser(userId);
        if (store.count(userId) >= MAX_SAVED) {
            throw ApiException.conflict("SAVED_LISTINGS_LIMIT",
                    "Bạn đã lưu tối đa " + MAX_SAVED + " tin. Hãy bỏ lưu bớt tin cũ để lưu thêm.");
        }
        Instant now = clock.instant();
        Optional<Instant> added = store.add(userId, listingId, now);
        if (added.isEmpty()) return store.savedAt(userId, listingId).orElse(now);
        analytics.recordServer("listing_favorited", 1, userId + ":" + listingId + ":" + added.get().toEpochMilli(),
                userId, listingId, Map.of());
        return added.get();
    }

    @Transactional
    public void unsave(UUID userId, UUID listingId) {
        Optional<Instant> savedAt = store.savedAt(userId, listingId);
        if (savedAt.isEmpty() || !store.remove(userId, listingId)) return;
        analytics.recordServer("listing_unfavorited", 1, userId + ":" + listingId + ":" + savedAt.get().toEpochMilli(),
                userId, listingId, Map.of());
    }

    @Transactional(readOnly = true)
    public Page page(UUID userId, @Nullable Instant beforeSavedAt, @Nullable UUID beforeListingId, int size) {
        List<SavedRow> rows = store.page(userId, beforeSavedAt, beforeListingId, size + 1);
        boolean hasNext = rows.size() > size;
        List<SavedRow> items = rows.subList(0, Math.min(size, rows.size()));
        return new Page(List.copyOf(items), hasNext, store.count(userId));
    }

    @Transactional(readOnly = true)
    public List<UUID> ids(UUID userId) {
        return store.ids(userId, MAX_SAVED);
    }

    public Map<UUID, GoneListing> gone(List<UUID> listingIds) {
        return store.gone(listingIds);
    }

    public record Page(List<SavedRow> rows, boolean hasNext, int total) {}
}
