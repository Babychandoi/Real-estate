package com.company.bds.search.api;

import com.company.bds.media.ImageDto;
import com.company.bds.search.api.response.ListingV2Responses.Facts;
import com.company.bds.search.api.response.ListingV2Responses.Freshness;
import com.company.bds.search.api.response.ListingV2Responses.Legal;
import com.company.bds.search.api.response.ListingV2Responses.ListingCheck;
import com.company.bds.search.api.response.ListingV2Responses.ListingDetailV2;
import com.company.bds.search.api.response.ListingV2Responses.ListingSummaryV2;
import com.company.bds.search.api.response.ListingV2Responses.Location;
import com.company.bds.search.api.response.ListingV2Responses.Money;
import com.company.bds.search.api.response.ListingV2Responses.OwnershipCheck;
import com.company.bds.search.api.response.ListingV2Responses.PageInfo;
import com.company.bds.search.api.response.ListingV2Responses.PriceChange;
import com.company.bds.search.api.response.ListingV2Responses.Project;
import com.company.bds.search.api.response.ListingV2Responses.RentTerms;
import com.company.bds.search.api.response.ListingV2Responses.SearchResponseV2;
import com.company.bds.search.api.response.ListingV2Responses.Seller;
import com.company.bds.search.api.response.ListingV2Responses.SuggestionDto;
import com.company.bds.search.api.response.ListingV2Responses.TotalDto;
import com.company.bds.search.api.response.ListingV2Responses.Trust;
import com.company.bds.search.api.response.ListingV2Responses.TrustCheck;
import com.company.bds.search.api.response.ListingV2Responses.UnitPrice;
import com.company.bds.search.application.SearchResults;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.shared.security.ContactInfoGuard;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Maps read-model rows to the public v2 DTOs; free text is passed through {@link ContactInfoGuard#redact}. */
final class ListingV2Mapper {
    static final Map<String, String> LEGAL_LABELS = Map.of(
            "RED_BOOK", "Sổ đỏ", "PINK_BOOK", "Sổ hồng", "SALE_CONTRACT", "Hợp đồng mua bán",
            "PENDING_CERTIFICATE", "Đang chờ sổ", "OTHER", "Giấy tờ khác");

    private ListingV2Mapper() {}

    static Money money(long amount, String period) {
        return new Money(amount, "VND", period);
    }

    static ListingSummaryV2 summary(PublicListing row, ImageDto image, Instant now) {
        return new ListingSummaryV2(row.listingId(), row.slug(), ContactInfoGuard.redact(row.title()), row.purpose(),
                row.propertyType(), money(row.priceVnd(), row.pricePeriod()), unitPrice(row), row.areaM2(), row.bedrooms(),
                row.bathrooms(), location(row), image, row.imageCount(), trust(row, now), freshness(row), seller(row),
                project(row), priceChange(row));
    }

    static ListingDetailV2 detail(PublicListing row, List<ImageDto> images, Instant now) {
        ImageDto cover = images.isEmpty() ? null : images.get(0);
        RentTerms rentTerms = "RENT".equals(row.purpose())
                ? new RentTerms(row.monthlyServiceFeeVnd(), row.depositVnd()) : null;
        Legal legal = row.legalStatusCode() == null ? null
                : new Legal(row.legalStatusCode(), LEGAL_LABELS.getOrDefault(row.legalStatusCode(), row.legalStatusCode()));
        Facts facts = new Facts(row.bedrooms(), row.bathrooms(), row.floors(), row.frontageM(), row.roadWidthM(),
                ContactInfoGuard.redact(row.direction()), ContactInfoGuard.redact(row.legalStatusText()));
        return new ListingDetailV2(row.listingId(), row.slug(), ContactInfoGuard.redact(row.title()), row.purpose(),
                row.propertyType(), money(row.priceVnd(), row.pricePeriod()), unitPrice(row), row.areaM2(), row.bedrooms(),
                row.bathrooms(), location(row), cover, row.imageCount(), trust(row, now), freshness(row), seller(row),
                project(row), priceChange(row), ContactInfoGuard.redact(row.description()), images, facts, rentTerms, legal,
                row.furnishing(), row.revisionNumber());
    }

    static SearchResponseV2 page(SearchResults.Page page, Map<String, ImageDto> thumbnails, Instant now) {
        List<ListingSummaryV2> items = page.items().stream()
                .map(row -> summary(row, row.thumbnailUrl() == null ? null : thumbnails.get(row.thumbnailUrl()), now))
                .toList();
        return new SearchResponseV2(items, new PageInfo(page.hasNext(), page.nextCursor(), page.size()), total(page.total()),
                "v2", page.dataAsOf(), page.engine(), page.degraded(), page.notices(),
                page.suggestions().stream().map(s -> new SuggestionDto(s.type(), s.drop(), total(s.total()))).toList());
    }

    static TotalDto total(SearchResults.Total total) {
        return total == null ? null : new TotalDto(total.value(), total.relation());
    }

    private static UnitPrice unitPrice(PublicListing row) {
        return row.unitPriceVnd() == null ? null : new UnitPrice(row.unitPriceVnd(), "M2");
    }

    private static Location location(PublicListing row) {
        return new Location(row.districtCode(), row.districtName(), row.wardName(), ContactInfoGuard.redact(row.addressSummary()),
                row.lat(), row.lng(), "APPROXIMATE");
    }

    private static Trust trust(PublicListing row, Instant now) {
        String identity = row.identityStatusAt(now);
        String ownership = row.ownershipStatusAt(now);
        return new Trust(
                new TrustCheck(identity, row.identityCheckedAt(), row.identityExpiresAt()),
                new ListingCheck(row.listingCheckedAt() != null ? "CHECKED" : "NOT_CHECKED", row.listingCheckedAt()),
                new OwnershipCheck(ownership, row.ownershipCheckedAt(), row.ownershipExpiresAt(), row.ownershipDocumentType()));
    }

    private static Freshness freshness(PublicListing row) {
        return new Freshness(row.publishedAt(), row.updatedAt(), row.availabilityConfirmedAt());
    }

    private static Seller seller(PublicListing row) {
        return new Seller(row.ownerId(), ContactInfoGuard.redact(row.sellerName()), row.sellerAvatarUrl(), row.sellerRole());
    }

    private static Project project(PublicListing row) {
        return row.projectId() == null ? null : new Project(row.projectId(), row.projectSlug(), row.projectName());
    }

    private static PriceChange priceChange(PublicListing row) {
        if (row.previousPriceVnd() == null || row.priceChangedAt() == null || row.previousPriceVnd() == row.priceVnd()) return null;
        return new PriceChange(row.previousPriceVnd(), row.priceChangedAt(), row.priceVnd() < row.previousPriceVnd() ? "DOWN" : "UP");
    }
}
