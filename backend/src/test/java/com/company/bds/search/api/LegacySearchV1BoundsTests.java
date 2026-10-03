package com.company.bds.search.api;

import com.company.bds.listing.api.response.ListingSummaryResponse;
import com.company.bds.search.application.ListingSearchService;
import com.company.bds.search.application.SearchProblemException;
import com.company.bds.search.application.SearchResults;
import com.company.bds.search.domain.InvalidFilterException;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.domain.SearchFilterParser;
import com.company.bds.shared.security.ratelimit.RateLimitPolicies;
import com.company.bds.shared.security.ratelimit.RateLimitPolicy;
import com.company.bds.shared.security.ratelimit.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** W6 review MAJOR 1: the deprecated v1 search is bounded, rate limited like v2 and hides engine switches. */
class LegacySearchV1BoundsTests {

    @Test
    void aPageBeyondTheDepthLimitIsA400WithoutAnySearch() {
        ListingSearchService search = mock(ListingSearchService.class);
        AtomicInteger calls = new AtomicInteger();
        when(search.search(any())).thenAnswer(call -> { calls.incrementAndGet(); return page(48, true); });
        LegacySearchV1Controller controller = new LegacySearchV1Controller(search);
        assertThatThrownBy(() -> controller.search(null, null, null, null, null, null, null, null, null, null, null, "LATEST", 50, 48))
                .isInstanceOf(InvalidFilterException.class);
        assertThat(calls.get()).isZero();
        // The deepest allowed page (results 2 352..2 399) costs exactly 50 searches.
        ResponseEntity<List<ListingSummaryResponse>> deepest = controller.search(null, null, null, null, null, null, null, null, null, null,
                null, "LATEST", 49, 48);
        assertThat(deepest.getBody()).hasSize(48);
        assertThat(calls.get()).isEqualTo(LegacySearchV1Controller.MAX_SEARCHES_PER_REQUEST);
    }

    @Test
    void anEngineSwitchMidWalkRestartsOnTheNewEngineInsteadOfAnswering409() {
        ListingSearchService search = mock(ListingSearchService.class);
        AtomicInteger calls = new AtomicInteger();
        when(search.search(any())).thenAnswer(call -> {
            int n = calls.incrementAndGet();
            SearchFilterParser.SearchRequest request = call.getArgument(0);
            if (n == 2 && request.cursor() != null) {
                throw new SearchProblemException(409, "CURSOR_ENGINE_CHANGED", "Công cụ tìm kiếm đã thay đổi", "engine switched");
            }
            return page(48, true);
        });
        LegacySearchV1Controller controller = new LegacySearchV1Controller(search);
        ResponseEntity<List<ListingSummaryResponse>> second = controller.search(null, null, null, null, null, null, null, null, null, null,
                null, "LATEST", 1, 48);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getBody()).hasSize(48);
        assertThat(calls.get()).as("first walk: 1 page + refused cursor; restart: 2 pages").isEqualTo(4);
    }

    @Test
    void v1SearchHasTheSameRateLimitAsV2Search() {
        RateLimitPolicies policies = new RateLimitPolicies(new RateLimitProperties());
        RateLimitPolicy v1 = policies.resolve("GET", "/api/v1/listings/search");
        RateLimitPolicy v2 = policies.resolve("GET", "/api/v2/listings/search");
        assertThat(v1.name()).isEqualTo("search-v1");
        assertThat(v1.rules()).isEqualTo(v2.rules());
    }

    private static SearchResults.Page page(int size, boolean hasNext) {
        List<PublicListing> rows = new ArrayList<>();
        for (int i = 0; i < size; i++) rows.add(row());
        return new SearchResults.Page(rows, hasNext, hasNext ? "c" + UUID.randomUUID() : null, size, null, SearchResults.ENGINE_SEARCH,
                false, List.of(), Instant.now(), List.of());
    }

    private static PublicListing row() {
        return new PublicListing(UUID.randomUUID(), "slug", UUID.randomUUID(), UUID.randomUUID(), 1, "Nhà", null, null, "SALE",
                "HOUSE", 1_000_000_000L, null, null, new BigDecimal("50"), null, null, null, null, null, null, null, null, null, null,
                null, "01", "005", null, null, null, "Cầu Giấy", null, null, null, null, null, null, 1, List.of(), "Người bán", null,
                "OWNER", "NOT_SUBMITTED", null, null, "NOT_SUBMITTED", null, null, null, null, Instant.now(), Instant.now(), null, null,
                null, "", 1L);
    }
}
