package com.company.bds.search.application;

import com.company.bds.search.application.SearchResults.CachedPage;
import com.company.bds.search.application.SearchResults.Page;
import com.company.bds.search.application.SearchResults.Suggestion;
import com.company.bds.search.application.SearchResults.Total;
import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.application.port.ListingSearchEnginePort;
import com.company.bds.search.application.port.ListingSearchEnginePort.Hit;
import com.company.bds.search.application.port.ListingSearchEnginePort.Hits;
import com.company.bds.search.application.port.ResponseCachePort;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.domain.SearchFilter;
import com.company.bds.search.domain.SearchFilterParser.SearchRequest;
import com.company.bds.search.domain.SearchSort;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Search API v2 (contract §8). Engine choice: Elasticsearch when configured, ready and the circuit breaker allows it;
 * otherwise the database engine on {@code listing_public_read}. A failed Elasticsearch call on a first page falls back
 * to the database in the same request ({@code degraded=true}, notice {@code SEARCH_ENGINE_UNAVAILABLE}); a cursor
 * issued by one engine is refused by the other ({@code 409 CURSOR_ENGINE_CHANGED}), so pages never mix orders.
 * Elasticsearch hits are re-read from PostgreSQL and re-checked against the filter; dropped hits are topped up once.
 */
@Service
public class ListingSearchService {
    private static final Logger log = LoggerFactory.getLogger(ListingSearchService.class);
    private static final Duration FIRST_PAGE_TTL = Duration.ofSeconds(20);

    private final ListingReadModelPort readModel;
    private final ListingSearchEnginePort engine;
    private final SearchCircuitBreaker breaker;
    private final SearchCursorCodec cursors;
    private final ResponseCachePort cache;
    private final SearchIndexSettings settings;
    private final Clock clock;
    private final MeterRegistry meters;
    private final boolean firstPageCache;

    public ListingSearchService(ListingReadModelPort readModel, ListingSearchEnginePort engine, SearchCircuitBreaker breaker,
                                SearchCursorCodec cursors, ResponseCachePort cache, SearchIndexSettings settings, Clock clock,
                                ObjectProvider<MeterRegistry> meters,
                                @Value("${app.search.cache.first-page:true}") boolean firstPageCache) {
        this.readModel = readModel;
        this.engine = engine;
        this.breaker = breaker;
        this.cursors = cursors;
        this.cache = cache;
        this.settings = settings;
        this.clock = clock;
        this.meters = meters.getIfAvailable(SimpleMeterRegistry::new);
        this.firstPageCache = firstPageCache;
    }

    public Page search(SearchRequest request) {
        SearchFilter filter = request.filter();
        String hash = filter.filterHash();
        SearchCursorCodec.Cursor cursor = request.cursor() == null ? null : cursors.decode(request.cursor(), hash);
        boolean engineUsable = settings.enabled() && engine.ready() && breaker.state() != SearchCircuitBreaker.State.OPEN;
        String current = engineUsable ? SearchResults.ENGINE_SEARCH : SearchResults.ENGINE_DATABASE;
        if (cursor != null && !cursor.engine().equals(current)) throw engineChanged();

        if (cursor == null && firstPageCache && filter.bbox() == null) {
            long generation = cache.generation();
            if (generation >= 0) {
                String key = "search:" + generation + ":" + current + ":" + hash + ":" + request.size();
                Page[] fresh = new Page[1];
                CachedPage cached = cache.getOrCompute("search-first-page", key, FIRST_PAGE_TTL, CachedPage.class, () -> {
                    Page page = compute(request, null, current);
                    fresh[0] = page;
                    // a fallback page carries a database cursor: never serve it under the search engine's key
                    return page.degraded() ? null : toCached(page);
                });
                if (fresh[0] != null) return fresh[0];
                if (cached != null) return fromCached(cached, request, filter);
                return compute(request, null, current);
            }
            // Cache unavailable (Redis down): identical concurrent first pages are still computed once, not once each.
            return cache.collapse("search:" + current + ":" + hash + ":" + request.size(), () -> compute(request, null, current));
        }
        return compute(request, cursor, current);
    }

    private Page compute(SearchRequest request, SearchCursorCodec.Cursor cursor, String current) {
        SearchFilter filter = request.filter();
        if (SearchResults.ENGINE_SEARCH.equals(current)) {
            if (breaker.tryAcquire()) {
                try {
                    Page page = searchEngine(request, cursor);
                    breaker.recordSuccess();
                    count(SearchResults.ENGINE_SEARCH, "ok");
                    return page;
                } catch (ListingSearchEnginePort.EngineUnavailable ex) {
                    breaker.recordFailure();
                    log.warn("Elasticsearch unavailable, answering from the database: {}", ex.getMessage());
                } catch (ListingSearchEnginePort.EngineRejected ex) {
                    breaker.recordIgnored();
                    log.error("Elasticsearch rejected a search query (bug, not an outage): {}", ex.getMessage());
                }
            }
            if (cursor != null) {
                count(SearchResults.ENGINE_SEARCH, "cursor_engine_changed");
                throw engineChanged();
            }
            count(SearchResults.ENGINE_DATABASE, "fallback");
            return database(request, null, true);
        }
        count(SearchResults.ENGINE_DATABASE, "ok");
        return database(request, cursor, settings.enabled());
    }

    private Page searchEngine(SearchRequest request, SearchCursorCodec.Cursor cursor) {
        SearchFilter filter = request.filter();
        SearchSort sort = filter.sort();
        int size = request.size();
        boolean first = cursor == null;
        Instant now = clock.instant();
        ArrayNode after = first ? null : cursor.keys();
        List<PublicListing> kept = new ArrayList<>();
        List<ArrayNode> keptKeys = new ArrayList<>();
        Total total = null;
        ArrayNode lastExamined = null;
        boolean exhausted = false;
        for (int call = 0; call < 2 && kept.size() <= size && !exhausted; call++) {
            int wanted = size + 1 - kept.size();
            Hits hits = engine.search(filter, sort, after, wanted, first && call == 0, SearchResults.TOTAL_CAP);
            if (call == 0 && hits.total() != null) total = new Total(Math.min(hits.total().value(), SearchResults.TOTAL_CAP), hits.total().relation());
            exhausted = hits.hits().size() < wanted;
            Map<UUID, PublicListing> rows = new HashMap<>();
            readModel.findByIds(hits.hits().stream().map(Hit::id).toList()).forEach(row -> rows.put(row.listingId(), row));
            for (Hit hit : hits.hits()) {
                lastExamined = hit.sortValues();
                PublicListing row = rows.get(hit.id());
                if (row != null && filter.matches(row, now)) {
                    kept.add(row);
                    keptKeys.add(hit.sortValues());
                }
                if (kept.size() > size) break;
            }
            after = lastExamined;
            if (hits.hits().isEmpty()) exhausted = true;
        }
        boolean hasNext;
        ArrayNode nextKeys;
        List<PublicListing> items;
        if (kept.size() > size) {
            items = kept.subList(0, size);
            hasNext = true;
            nextKeys = keptKeys.get(size - 1);
        } else {
            items = kept;
            hasNext = !exhausted && lastExamined != null;
            nextKeys = lastExamined;
        }
        String nextCursor = hasNext ? cursors.encode(SearchResults.ENGINE_SEARCH, sort.name(), nextKeys, filter.filterHash()) : null;
        List<Suggestion> suggestions = first && items.isEmpty() ? suggestions(filter) : List.of();
        return new Page(List.copyOf(items), hasNext, nextCursor, size, total, SearchResults.ENGINE_SEARCH, false, List.of(), now,
                suggestions);
    }

    private Page database(SearchRequest request, SearchCursorCodec.Cursor cursor, boolean degraded) {
        SearchFilter requested = request.filter();
        List<String> notices = new ArrayList<>();
        if (degraded) notices.add(SearchResults.NOTICE_ENGINE_UNAVAILABLE);
        SearchSort sort = requested.sort();
        if (sort == SearchSort.RELEVANCE) {
            sort = SearchSort.NEWEST;
            notices.add(SearchResults.NOTICE_RELEVANCE_APPROXIMATE);
        }
        SearchFilter filter = requested.withSort(sort);
        if (cursor != null && !sort.name().equals(cursor.sort())) {
            throw new SearchProblemException(400, "CURSOR_INVALID", "Con trỏ trang không hợp lệ", "Con trỏ trang thuộc cách sắp xếp khác.");
        }
        int size = request.size();
        List<PublicListing> rows = readModel.page(filter, sort, cursor == null ? null : cursor.keys(), size + 1);
        boolean hasNext = rows.size() > size;
        List<PublicListing> items = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext
                ? cursors.encode(SearchResults.ENGINE_DATABASE, sort.name(), readModel.keysOf(items.get(size - 1), sort), requested.filterHash())
                : null;
        Total total = null;
        List<Suggestion> suggestions = List.of();
        if (cursor == null) {
            total = hasNext ? Total.capped(readModel.countCapped(filter, SearchResults.TOTAL_CAP), SearchResults.TOTAL_CAP)
                    : new Total(items.size(), "eq");
            if (items.isEmpty()) suggestions = suggestions(filter);
        }
        return new Page(List.copyOf(items), hasNext, nextCursor, size, total, SearchResults.ENGINE_DATABASE, degraded,
                List.copyOf(notices), clock.instant(), suggestions);
    }

    /** At most this many relaxations are counted per zero-result filter (bounded extra database work). */
    static final int MAX_SUGGESTION_COUNTS = 3;
    private static final Duration SUGGESTIONS_TTL = Duration.ofSeconds(60);

    /**
     * Rule-based relaxations of a zero-result filter (audit D-11): each drops one group of constraints and is counted
     * on the database; only those with results are returned, most results first. Only the first
     * {@value #MAX_SUGGESTION_COUNTS} applicable relaxations (in priority order) are counted, each with a capped count,
     * and the answer is cached per filter hash for {@code SUGGESTIONS_TTL}, so an empty page costs at most three cheap
     * counts per filter and minute however often it is requested.
     */
    List<Suggestion> suggestions(SearchFilter filter) {
        long generation = cache.generation();
        if (generation < 0) return computeSuggestions(filter);
        String key = "suggest:" + generation + ":" + filter.filterHash();
        SearchResults.CachedSuggestions cached = cache.getOrCompute("search-suggestions", key, SUGGESTIONS_TTL,
                SearchResults.CachedSuggestions.class, () -> new SearchResults.CachedSuggestions(computeSuggestions(filter)));
        return cached == null ? List.of() : cached.items();
    }

    List<Suggestion> computeSuggestions(SearchFilter filter) {
        List<Relaxation> candidates = new ArrayList<>();
        candidate(candidates, "REMOVE_KEYWORD", filter.keyword() != null, List.of("q"));
        candidate(candidates, "REMOVE_PRICE", filter.priceMin() != null || filter.priceMax() != null, List.of("priceMin", "priceMax"));
        candidate(candidates, "REMOVE_AREA", filter.areaMin() != null || filter.areaMax() != null, List.of("areaMin", "areaMax"));
        candidate(candidates, "REMOVE_BEDROOMS", filter.bedsMin() != null, List.of("bedsMin"));
        candidate(candidates, "REMOVE_VERIFIED", filter.verified() != null, List.of("verified"));
        candidate(candidates, "REMOVE_ATTRIBUTES", !filter.legal().isEmpty() || !filter.furnishing().isEmpty(), List.of("legal", "furnishing"));
        candidate(candidates, "WIDEN_AREA", filter.bbox() != null || !filter.districts().isEmpty(), List.of("bbox", "district"));
        candidate(candidates, "REMOVE_TYPE", !filter.types().isEmpty(), List.of("type"));
        List<Suggestion> out = new ArrayList<>();
        for (Relaxation relaxation : candidates.subList(0, Math.min(MAX_SUGGESTION_COUNTS, candidates.size()))) {
            long count = readModel.countCapped(filter.without(Set.copyOf(relaxation.drop())).withSort(SearchSort.NEWEST), 1_000);
            if (count > 0) out.add(new Suggestion(relaxation.type(), relaxation.drop(), Total.capped(count, 1_000)));
        }
        out.sort(Comparator.comparingLong((Suggestion s) -> s.total().value()).reversed());
        return List.copyOf(out);
    }

    private record Relaxation(String type, List<String> drop) {}

    private static void candidate(List<Relaxation> out, String type, boolean applicable, List<String> drop) {
        if (applicable) out.add(new Relaxation(type, drop));
    }

    private CachedPage toCached(Page page) {
        return new CachedPage(page.items().stream().map(PublicListing::listingId).toList(), page.hasNext(), page.nextCursor(),
                page.total(), page.engine(), page.notices());
    }

    private Page fromCached(CachedPage cached, SearchRequest request, SearchFilter filter) {
        Instant now = clock.instant();
        Map<UUID, PublicListing> rows = new HashMap<>();
        readModel.findByIds(cached.ids()).forEach(row -> rows.put(row.listingId(), row));
        List<PublicListing> items = new ArrayList<>();
        SearchFilter effective = filter.sort() == SearchSort.RELEVANCE && SearchResults.ENGINE_DATABASE.equals(cached.engine())
                ? filter.withSort(SearchSort.NEWEST) : filter;
        for (UUID id : cached.ids()) {
            PublicListing row = rows.get(id);
            if (row != null && effective.matches(row, now)) items.add(row);
        }
        boolean degraded = cached.notices().contains(SearchResults.NOTICE_ENGINE_UNAVAILABLE);
        List<Suggestion> suggestions = items.isEmpty() && !cached.hasNext() ? suggestions(filter) : List.of();
        return new Page(List.copyOf(items), cached.hasNext(), cached.nextCursor(), request.size(), cached.total(), cached.engine(),
                degraded, cached.notices(), now, suggestions);
    }

    private void count(String engineName, String outcome) {
        meters.counter("bds.search.requests", "engine", engineName, "outcome", outcome).increment();
    }

    private static SearchProblemException engineChanged() {
        return new SearchProblemException(409, "CURSOR_ENGINE_CHANGED", "Công cụ tìm kiếm đã thay đổi",
                "Kết quả được tải lại từ trang đầu vì công cụ tìm kiếm vừa chuyển chế độ.");
    }
}
