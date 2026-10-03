package com.company.bds.search.application.port;

import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.domain.SearchFilter;
import com.company.bds.search.domain.SearchSort;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Queries on {@code listing_public_read} (PostgreSQL is the source of truth for everything public). */
public interface ListingReadModelPort {

    /** One keyset page (summary columns) ordered by the sort tuple; {@code after} = last sort values or null. */
    List<PublicListing> page(SearchFilter filter, SearchSort sort, @Nullable ArrayNode after, int limit);

    /** Keyset sort values of a row for {@code sort} (the database cursor). */
    ArrayNode keysOf(PublicListing row, SearchSort sort);

    /** Matching rows, counted up to {@code cap + 1} (so the caller can tell "more than cap"). */
    long countCapped(SearchFilter filter, int cap);

    /** Summary rows for these ids (any order; missing ids are simply absent). */
    List<PublicListing> findByIds(Collection<UUID> ids);

    /** Listing id and current row version for a slug or id, when publicly visible. */
    Optional<VersionRef> findVersion(String slugOrId);

    /** Full row (description, all media URLs). */
    Optional<PublicListing> findDetail(UUID listingId);

    /** A listing that exists but is not public now and was public before (for 410); never a never-published draft. */
    Optional<GoneListing> findGone(String slugOrId);

    List<PublicListing> sellerPage(UUID ownerId, @Nullable ArrayNode after, int limit);

    long sellerCountCapped(UUID ownerId, int cap);

    /** At most {@code limit} matches newest first; when there are more, which {@code limit} rows is unspecified (a probe). */
    List<MapPoint> mapPoints(SearchFilter filter, int limit);

    List<MapCluster> mapClusters(SearchFilter filter, double cellDegrees, int limit);

    List<PublicListing> similar(PublicListing base, int limit);

    List<PricePoint> priceHistory(UUID listingId);

    Optional<SellerProfile> sellerProfile(UUID sellerId, Instant now);

    /** {@code trustStamp} changes when a shown trust check expires before the row is refreshed. */
    record VersionRef(UUID listingId, long rowVersion, String trustStamp) {}

    record GoneListing(UUID listingId, String slug, String title) {}

    record MapPoint(UUID id, String slug, double lat, double lng, long priceVnd, String pricePeriod, String propertyType) {}

    record MapCluster(double lat, double lng, long count, double minLng, double minLat, double maxLng, double maxLat) {}

    record PricePoint(int revisionNumber, long priceVnd, String pricePeriod, Instant changedAt) {}

    record SellerProfile(UUID id, String name, String avatarUrl, String role, Instant memberSince,
                         String identityStatus, Instant identityCheckedAt, Instant identityExpiresAt,
                         long activeListings, long ownershipVerifiedListings,
                         long responseSamples, @Nullable Double medianFirstResponseMinutes) {}
}
