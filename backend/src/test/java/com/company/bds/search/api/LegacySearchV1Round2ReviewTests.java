package com.company.bds.search.api;

import com.company.bds.listing.api.response.ListingSummaryResponse;
import com.company.bds.search.application.ListingSearchService;
import com.company.bds.search.application.SearchProblemException;
import com.company.bds.search.application.SearchResults;
import com.company.bds.search.domain.SearchFilterParser;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Round-2 adversarial review of the bounded v1 search (PR #24, bafe510). Expected to fail on bafe510. */
class LegacySearchV1Round2ReviewTests {

    /**
     * MAX_SEARCHES_PER_REQUEST (50) is per walk, and the walk is restarted once on CURSOR_ENGINE_CHANGED: a switch at the
     * last cursor of the deepest page costs 49 + 50 = 99 searches for one request.
     */
    @Test
    void anEngineSwitchAtTheEndOfADeepWalkStillCostsAtMost50Searches() {
        ListingSearchService search = mock(ListingSearchService.class);
        AtomicInteger calls = new AtomicInteger();
        when(search.search(any())).thenAnswer(call -> {
            int n = calls.incrementAndGet();
            SearchFilterParser.SearchRequest request = call.getArgument(0);
            if (n == 50 && request.cursor() != null) {
                throw new SearchProblemException(409, "CURSOR_ENGINE_CHANGED", "Công cụ tìm kiếm đã thay đổi", "engine switched");
            }
            return new SearchResults.Page(List.of(), true, "c" + n, 48, null, SearchResults.ENGINE_SEARCH, false, List.of(),
                    Instant.now(), List.of());
        });
        LegacySearchV1Controller controller = new LegacySearchV1Controller(search);
        controller.search(null, null, null, null, null, null, null, null, null, null, null, "LATEST", 49, 48);
        assertThat(calls.get()).as("v2 searches run by one v1 request").isLessThanOrEqualTo(LegacySearchV1Controller.MAX_SEARCHES_PER_REQUEST);
    }

    /**
     * The contract of this endpoint (class Javadoc) is "a page past the last one is an empty array". With 10 results in
     * total, page 120 of size 20 is past the last page, but it is refused with 400 before anything is searched.
     */
    @Test
    void aPagePastTheLastOneIsAnEmptyArrayEvenBeyondTheDepthLimit() {
        ListingSearchService search = mock(ListingSearchService.class);
        when(search.search(any())).thenReturn(new SearchResults.Page(List.of(), false, null, 48, new SearchResults.Total(0, "eq"),
                SearchResults.ENGINE_SEARCH, false, List.of(), Instant.now(), List.of()));
        LegacySearchV1Controller controller = new LegacySearchV1Controller(search);
        Throwable refused = catchThrowable(() -> {
            ResponseEntity<List<ListingSummaryResponse>> past = controller.search(null, null, null, null, null, null, null, null, null,
                    null, null, "LATEST", 120, 20);
            assertThat(past.getBody()).isEmpty();
        });
        assertThat(refused).as("page past the last one of a 0-result search").isNull();
    }
}
