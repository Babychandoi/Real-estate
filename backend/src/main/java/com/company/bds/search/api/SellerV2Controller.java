package com.company.bds.search.api;

import com.company.bds.search.api.response.ListingV2Responses.ResponseStats;
import com.company.bds.search.api.response.ListingV2Responses.SearchResponseV2;
import com.company.bds.search.api.response.ListingV2Responses.SellerProfileV2;
import com.company.bds.search.api.response.ListingV2Responses.TrustCheck;
import com.company.bds.search.application.ListingReadService;
import com.company.bds.search.application.SearchResults;
import com.company.bds.search.application.port.ListingReadModelPort.SellerProfile;
import com.company.bds.search.domain.InvalidFilterException;
import com.company.bds.search.domain.SearchFilterParser;
import com.company.bds.shared.security.ContactInfoGuard;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Public seller profile and paged inventory (audit F08.5, UI-04). The profile separates the seller's identity check from
 * per-listing ownership checks; response statistics are shown only with enough answered leads.
 */
@RestController
@RequestMapping("/api/v2/public/sellers")
public class SellerV2Controller {
    private final ListingReadService reads;
    private final Clock clock;

    public SellerV2Controller(ListingReadService reads, Clock clock) {
        this.reads = reads;
        this.clock = clock;
    }

    @GetMapping("/{sellerId}")
    public ResponseEntity<SellerProfileV2> profile(@PathVariable UUID sellerId) {
        SellerProfile seller = reads.seller(sellerId);
        ResponseStats stats = seller.responseSamples() >= ListingReadService.MIN_RESPONSE_SAMPLES
                && seller.medianFirstResponseMinutes() != null
                ? new ResponseStats(seller.responseSamples(), Math.round(seller.medianFirstResponseMinutes() * 10) / 10.0) : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(new SellerProfileV2(seller.id(),
                ContactInfoGuard.redact(seller.name()), seller.avatarUrl(), seller.role(), seller.memberSince(),
                new TrustCheck(seller.identityStatus(), seller.identityCheckedAt(), seller.identityExpiresAt()),
                seller.activeListings(), seller.ownershipVerifiedListings(), stats));
    }

    @GetMapping("/{sellerId}/listings")
    public ResponseEntity<SearchResponseV2> listings(@PathVariable UUID sellerId,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(defaultValue = "24") int size) {
        if (size < 1 || size > SearchFilterParser.MAX_SIZE) {
            throw new InvalidFilterException(InvalidFilterException.INVALID_FILTER, List.of(
                    new InvalidFilterException.FilterError("size", "Số tin mỗi trang phải từ 1 đến " + SearchFilterParser.MAX_SIZE + ".")));
        }
        SearchResults.Page page = reads.sellerListings(sellerId, size, cursor == null || cursor.isBlank() ? null : cursor);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .body(ListingV2Mapper.page(page, reads.thumbnails(page.items()), clock.instant()));
    }
}
