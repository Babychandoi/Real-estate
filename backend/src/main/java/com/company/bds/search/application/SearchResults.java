package com.company.bds.search.application;

import com.company.bds.search.domain.PublicListing;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Application-level results of the listing read side (mapped to API DTOs by the controllers). */
public final class SearchResults {
    private SearchResults() {}

    public static final String ENGINE_SEARCH = "search";
    public static final String ENGINE_DATABASE = "database";
    public static final String NOTICE_ENGINE_UNAVAILABLE = "SEARCH_ENGINE_UNAVAILABLE";
    public static final String NOTICE_RELEVANCE_APPROXIMATE = "RELEVANCE_APPROXIMATE";
    public static final int TOTAL_CAP = 10_000;

    public record Total(long value, String relation) {
        public static Total capped(long counted, int cap) {
            return counted > cap ? new Total(cap, "gte") : new Total(counted, "eq");
        }
    }

    /** A relaxation of a zero-result search: drop these parameters to get {@code total} results. */
    public record Suggestion(String type, List<String> drop, Total total) {}

    /** Cached zero-result suggestions of one filter (Redis value). */
    public record CachedSuggestions(List<Suggestion> items) {}

    public record Page(List<PublicListing> items, boolean hasNext, @Nullable String nextCursor, int size,
                       @Nullable Total total, String engine, boolean degraded, List<String> notices, Instant dataAsOf,
                       List<Suggestion> suggestions) {}

    /** What the first-page cache stores: ids in order plus paging; rows are re-read (and re-checked) on every hit. */
    public record CachedPage(List<UUID> ids, boolean hasNext, @Nullable String nextCursor, @Nullable Total total,
                             String engine, List<String> notices) {}
}
