package com.company.bds.search.api;

import com.company.bds.search.application.ListingSearchService;
import com.company.bds.search.application.SearchResults;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Adversarial review of the W6 v1 search paging (PR #24). {@code GET /api/v1/listings/search?page=N} now walks the v2
 * cursors: one public, anonymous request runs N+1 full searches (Elasticsearch query + PostgreSQL re-read each), with no
 * bound on N and under the generic api-default rate limit (1 200/min per IP). Expected to fail on the reviewed commit.
 */
class LegacySearchV1PagingReviewTests {
    @Test
    void oneV1RequestRunsABoundedNumberOfSearches() {
        ListingSearchService search = mock(ListingSearchService.class);
        AtomicInteger calls = new AtomicInteger();
        when(search.search(any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            // A large result set: every page has a next one (e.g. size=1 over 10 000 matches).
            return new SearchResults.Page(List.of(), true, "c" + calls.get(), 1, null, SearchResults.ENGINE_SEARCH, false, List.of(),
                    Instant.now(), List.of());
        });
        LegacySearchV1Controller controller = new LegacySearchV1Controller(search);

        Throwable refused = catchThrowable(() -> controller.search(null, null, null, null, null, null, null, null, null, null, null,
                "LATEST", 9_999, 1));
        assertThat(calls.get()).as("searches run by ONE v1 request for page=9999 (refused=%s)", refused).isLessThanOrEqualTo(50);
    }
}
