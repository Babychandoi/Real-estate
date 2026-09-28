package com.company.bds.engagement.application.port;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@code saved_listings} plus the few listing facts the engagement features need. */
public interface SavedListingStorePort {

    /** Serialises writers of one user's lists (cap checks) until the transaction ends. */
    void lockUser(UUID userId);

    /** @return the saved time, or empty when it was already saved */
    Optional<Instant> add(UUID userId, UUID listingId, Instant now);

    Optional<Instant> savedAt(UUID userId, UUID listingId);

    boolean remove(UUID userId, UUID listingId);

    int count(UUID userId);

    /** Newest first; keyset {@code (savedAt, listingId)} strictly after the cursor. */
    List<SavedRow> page(UUID userId, @Nullable Instant beforeSavedAt, @Nullable UUID beforeListingId, int limit);

    List<UUID> ids(UUID userId, int limit);

    /** Users who saved the listing, keyset by user id. */
    List<UUID> savers(UUID listingId, @Nullable UUID afterUserId, int limit);

    /** Whether the listing is publicly visible now (read model row, seller ACTIVE). */
    boolean isPublic(UUID listingId);

    /**
     * For listings that are not public any more: slug and the last public title, the title only when the owner withdrew
     * it (never for a moderation lock or a seller account that is not ACTIVE) — the same rule as the public 410 page.
     */
    Map<UUID, GoneListing> gone(Collection<UUID> listingIds);

    record SavedRow(UUID listingId, Instant savedAt) {}

    record GoneListing(UUID listingId, String slug, String title) {}
}
