package com.company.bds.search.api.response;

import com.company.bds.media.ImageDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public listing DTOs of API v2 (contracts §4, §6, §8). Built from read-model rows only; never from JPA entities. */
public final class ListingV2Responses {
    private ListingV2Responses() {}

    public record Money(long amount, String currency, String period) {}

    public record UnitPrice(long amount, String per) {}

    public record RentTerms(Long monthlyServiceFee, Long deposit) {}

    public record Legal(String code, String label) {}

    public record Location(String districtCode, String districtName, String wardName, String addressSummary, Double lat,
                           Double lng, String precision) {}

    public record TrustCheck(String status, Instant checkedAt, Instant expiresAt) {}

    public record ListingCheck(String status, Instant checkedAt) {}

    public record OwnershipCheck(String status, Instant checkedAt, Instant expiresAt, String documentType) {}

    public record Trust(TrustCheck identity, ListingCheck listing, OwnershipCheck ownership) {}

    public record Freshness(Instant publishedAt, Instant updatedAt, Instant availabilityConfirmedAt) {}

    public record Seller(UUID id, String name, String avatarUrl, String role) {}

    public record Project(UUID id, String slug, String name) {}

    public record PriceChange(long previousAmount, Instant changedAt, String direction) {}

    public record ListingSummaryV2(UUID id, String slug, String title, String purpose, String propertyType, Money price,
                                   UnitPrice unitPrice, BigDecimal areaM2, Integer bedrooms, Integer bathrooms,
                                   Location location, ImageDto image, int imageCount, Trust trust, Freshness freshness,
                                   Seller seller, Project project, PriceChange priceChange) {}

    public record Facts(Integer bedrooms, Integer bathrooms, Integer floors, BigDecimal frontageM, BigDecimal roadWidthM,
                        String direction, String legalStatusText) {}

    public record ListingDetailV2(UUID id, String slug, String title, String purpose, String propertyType, Money price,
                                  UnitPrice unitPrice, BigDecimal areaM2, Integer bedrooms, Integer bathrooms,
                                  Location location, ImageDto image, int imageCount, Trust trust, Freshness freshness,
                                  Seller seller, Project project, PriceChange priceChange, String description,
                                  List<ImageDto> images, Facts facts, RentTerms rentTerms, Legal legal, String furnishing,
                                  int revisionNumber) {}

    public record PageInfo(boolean hasNext, String nextCursor, int size) {}

    public record TotalDto(long value, String relation) {}

    public record SuggestionDto(String type, List<String> drop, TotalDto total) {}

    public record SearchResponseV2(List<ListingSummaryV2> items, PageInfo pageInfo, TotalDto total, String queryVersion,
                                   Instant dataAsOf, String engine, boolean degraded, List<String> notices,
                                   List<SuggestionDto> suggestions) {}

    public record MapPointDto(UUID id, String slug, double lat, double lng, Money price, String propertyType) {}

    public record MapClusterDto(double lat, double lng, long count, List<Double> bbox) {}

    public record MapResponseV2(String mode, List<MapPointDto> points, List<MapClusterDto> clusters, TotalDto total,
                                String engine, Instant dataAsOf) {}

    public record PricePointDto(Money price, Instant changedAt) {}

    public record PriceHistoryResponse(UUID listingId, String purpose, List<PricePointDto> points) {}

    public record ResponseStats(long sampleSize, double medianFirstResponseMinutes) {}

    public record SellerProfileV2(UUID id, String name, String avatarUrl, String role, Instant memberSince,
                                  TrustCheck identity, long activeListingCount, long ownershipVerifiedListingCount,
                                  ResponseStats responseStats) {}
}
