package com.company.bds.engagement.application.port;

import com.company.bds.engagement.domain.ListingChange;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@code saved_searches}, {@code saved_search_matches} and the matcher's {@code engage_listing_state}. */
public interface SavedSearchStorePort {

    void insert(SavedSearch search);

    Optional<UUID> findIdByHash(UUID userId, String filterHash);

    int count(UUID userId);

    List<SavedSearch> list(UUID userId, int limit);

    Optional<SavedSearch> find(UUID userId, UUID id);

    /** Undelivered matches per search of the user. */
    Map<UUID, Integer> pendingCounts(UUID userId);

    /** Compare-and-set update of the editable fields; false when {@code expectedVersion} is stale. */
    boolean update(SavedSearch search, long expectedVersion);

    boolean delete(UUID userId, UUID id);

    /** Frequency OFF (unsubscribe link); true when the search exists. */
    boolean stopAlerts(UUID userId, UUID id);

    /**
     * Active searches (frequency not OFF, not paused, account ACTIVE, not the listing owner's) created before
     * {@code createdBefore} that watch {@code kind}, whose coarse columns admit the listing; keyset by id.
     */
    List<Candidate> candidates(ListingChange.Kind kind, String purpose, String propertyType, String districtCode,
                               long priceVnd, UUID listingOwnerId, Instant createdBefore, @Nullable UUID afterId, int limit);

    /** @return false when the same (search, listing, kind, fact) was already recorded */
    boolean insertMatch(UUID searchId, UUID listingId, ListingChange.Kind kind, String factKey, long priceVnd,
                        @Nullable Long previousPriceVnd, Instant now);

    /** Locks and returns the matcher's last seen state; empty when never seen. */
    Optional<ListingChange.Seen> lockState(UUID listingId);

    boolean listingExists(UUID listingId);

    void saveState(UUID listingId, boolean visible, @Nullable String purpose, @Nullable Long priceVnd,
                   @Nullable Instant hiddenAt, Instant now);

    /** Searches whose digest is due and that have undelivered matches. */
    List<UUID> dueSearches(Instant now, int limit);

    /** Locks a due search (SKIP LOCKED: another instance may be delivering it); empty when not due any more. */
    Optional<SavedSearch> lockDue(UUID id, Instant now);

    /** Marks every undelivered match of the search delivered and returns them (oldest first). */
    List<Match> takePending(UUID searchId, Instant now);

    void markDigested(UUID searchId, Instant now, @Nullable Instant nextDigestAt);

    /** Retention: delivered matches older than {@code deliveredBefore}, undelivered ones older than {@code pendingBefore}. */
    int purgeMatches(Instant deliveredBefore, Instant pendingBefore);

    record SavedSearch(UUID id, UUID userId, String name, Map<String, String> params, String filterHash, String purpose,
                       List<String> types, List<String> districts, Long priceMin, Long priceMax, String frequency,
                       boolean alertNew, boolean alertPriceDrop, boolean alertBackOnMarket, boolean paused,
                       Instant nextDigestAt, Instant lastDigestAt, long version, Instant createdAt, Instant updatedAt) {}

    record Candidate(UUID id, UUID userId, Map<String, String> params) {}

    record Match(long id, UUID listingId, ListingChange.Kind kind, long priceVnd, Long previousPriceVnd, Instant matchedAt) {}
}
