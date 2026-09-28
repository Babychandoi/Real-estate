package com.company.bds.engagement.application;

import com.company.bds.analytics.application.AnalyticsRecorder;
import com.company.bds.engagement.application.port.SavedListingStorePort;
import com.company.bds.engagement.application.port.SavedSearchStorePort;
import com.company.bds.engagement.application.port.SavedSearchStorePort.SavedSearch;
import com.company.bds.engagement.domain.AlertFrequency;
import com.company.bds.notification.application.port.SavedSearchAlertsPort;
import com.company.bds.search.domain.SearchFilter;
import com.company.bds.search.domain.SearchFilterParser;
import com.company.bds.shared.error.ApiException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Saved searches (audit P-02). The filter is validated by the search API's own parser (contract §7) and stored in its
 * canonical form with the {@code filterHash}: the same search saved twice is one saved search (409 with its id).
 * API-only and view parameters ({@code cursor}, {@code size}, {@code view}) are not part of a saved search; the map
 * place label is kept for display only. {@value #MAX_SEARCHES} per account.
 */
@Service
public class SavedSearchService implements SavedSearchAlertsPort {
    public static final int MAX_SEARCHES = 20;
    public static final int MAX_NAME = 80;
    private static final Set<String> NOT_SAVED = Set.of("cursor", "size", "view");

    private final SavedSearchStorePort store;
    private final SavedListingStorePort users;
    private final AnalyticsRecorder analytics;
    private final Clock clock;

    public SavedSearchService(SavedSearchStorePort store, SavedListingStorePort users, AnalyticsRecorder analytics, Clock clock) {
        this.store = store;
        this.users = users;
        this.analytics = analytics;
        this.clock = clock;
    }

    @Transactional
    public SavedSearch create(UUID userId, Map<String, String[]> rawFilter, @Nullable String name, Settings settings) {
        Map<String, String[]> raw = new HashMap<>(rawFilter);
        NOT_SAVED.forEach(raw::remove);
        SearchFilter filter = SearchFilterParser.parse(raw).filter();
        String hash = filter.filterHash();
        users.lockUser(userId);
        Optional<UUID> existing = store.findIdByHash(userId, hash);
        if (existing.isPresent()) {
            throw ApiException.conflict("SAVED_SEARCH_EXISTS",
                    "Bạn đã lưu tìm kiếm này (" + existing.get() + ").");
        }
        if (store.count(userId) >= MAX_SEARCHES) {
            throw ApiException.conflict("SAVED_SEARCH_LIMIT",
                    "Bạn đã lưu tối đa " + MAX_SEARCHES + " tìm kiếm. Hãy xóa bớt tìm kiếm cũ.");
        }
        TreeMap<String, String> params = new TreeMap<>(filter.canonicalParams());
        String[] place = raw.get("place");
        if (place != null && place.length == 1 && filter.hasBbox() && !place[0].isBlank()) {
            params.put("place", place[0].strip().substring(0, Math.min(200, place[0].strip().length())));
        }
        settings.validate();
        Instant now = clock.instant();
        AlertFrequency frequency = settings.frequency();
        SavedSearch search = new SavedSearch(UUID.randomUUID(), userId, cleanName(name, filter), params, hash,
                filter.purpose(), List.copyOf(filter.types()), List.copyOf(filter.districts()), filter.priceMin(),
                filter.priceMax(), frequency.name(), settings.alertNew(), settings.alertPriceDrop(),
                settings.alertBackOnMarket(), false, frequency.firstDigestAt(now), null, 0, now, now);
        store.insert(search);
        analytics.recordServer("saved_search_created", 1, search.id().toString(), userId, null,
                Map.of("filterHash", hash, "frequency", frequency.name()));
        return search;
    }

    @Transactional(readOnly = true)
    public List<Listed> list(UUID userId) {
        Map<UUID, Integer> pending = store.pendingCounts(userId);
        List<Listed> out = new ArrayList<>();
        for (SavedSearch search : store.list(userId, MAX_SEARCHES)) {
            out.add(new Listed(search, pending.getOrDefault(search.id(), 0)));
        }
        return out;
    }

    /** Partial update: null fields keep their value. */
    @Transactional
    public SavedSearch update(UUID userId, UUID id, long expectedVersion, Change change) {
        SavedSearch current = store.find(userId, id).orElseThrow(SavedSearchService::notFound);
        if (current.version() != expectedVersion) throw conflict();
        Instant now = clock.instant();
        String nextName = change.name() == null ? current.name() : cleanName(change.name(), null);
        AlertFrequency frequency = change.frequency() == null ? AlertFrequency.valueOf(current.frequency()) : change.frequency();
        Settings settings = new Settings(frequency, pick(change.alertNew(), current.alertNew()),
                pick(change.alertPriceDrop(), current.alertPriceDrop()), pick(change.alertBackOnMarket(), current.alertBackOnMarket()));
        settings.validate();
        Instant nextDigest = frequency.name().equals(current.frequency()) ? current.nextDigestAt() : frequency.firstDigestAt(now);
        SavedSearch next = new SavedSearch(current.id(), userId, nextName, current.params(), current.filterHash(),
                current.purpose(), current.types(), current.districts(), current.priceMin(), current.priceMax(),
                frequency.name(), settings.alertNew(), settings.alertPriceDrop(), settings.alertBackOnMarket(),
                pick(change.paused(), current.paused()), nextDigest, current.lastDigestAt(), current.version() + 1,
                current.createdAt(), now);
        if (!store.update(next, expectedVersion)) throw conflict();
        return next;
    }

    private static boolean pick(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (!store.delete(userId, id)) throw notFound();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> name(UUID userId, UUID savedSearchId) {
        return store.find(userId, savedSearchId).map(SavedSearch::name);
    }

    @Override
    @Transactional
    public boolean stopAlerts(UUID userId, UUID savedSearchId) {
        return store.stopAlerts(userId, savedSearchId);
    }

    /** Rebuilds the validated filter of a stored search (the same parser as the API). */
    public static SearchFilter filterOf(Map<String, String> params) {
        Map<String, String[]> raw = new HashMap<>();
        params.forEach((key, value) -> raw.put(key, new String[]{value}));
        return SearchFilterParser.parse(raw).filter();
    }

    private static String cleanName(@Nullable String name, @Nullable SearchFilter filter) {
        String clean = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (clean.isEmpty() && filter != null) clean = defaultName(filter);
        if (clean.isEmpty() || clean.length() > MAX_NAME) {
            throw ApiException.badRequest("INVALID_NAME", "Tên tìm kiếm cần từ 1 đến " + MAX_NAME + " ký tự.");
        }
        return clean;
    }

    private static String defaultName(SearchFilter filter) {
        StringBuilder out = new StringBuilder("RENT".equals(filter.purpose()) ? "Thuê" : "Mua");
        if (!filter.types().isEmpty()) out.append(" · ").append(filter.types().size()).append(" loại hình");
        if (!filter.districts().isEmpty()) out.append(" · ").append(filter.districts().size()).append(" khu vực");
        if (filter.hasKeyword()) out.append(" · “").append(filter.keyword()).append("”");
        String value = out.toString();
        return value.length() > MAX_NAME ? value.substring(0, MAX_NAME) : value;
    }

    private static ApiException notFound() {
        return ApiException.notFound("SAVED_SEARCH_NOT_FOUND", "Không tìm thấy tìm kiếm đã lưu.");
    }

    private static ApiException conflict() {
        return ApiException.conflict("SAVED_SEARCH_VERSION_CONFLICT",
                "Tìm kiếm vừa được thay đổi ở nơi khác. Hãy tải lại rồi thử lại.");
    }

    /** Alert settings. */
    public record Settings(AlertFrequency frequency, boolean alertNew, boolean alertPriceDrop, boolean alertBackOnMarket) {
        public static Settings defaults() { return new Settings(AlertFrequency.DAILY, true, true, true); }

        /** An active search must watch at least one kind of change. */
        public void validate() {
            if (frequency != AlertFrequency.OFF && !alertNew && !alertPriceDrop && !alertBackOnMarket) {
                throw ApiException.badRequest("NO_ALERT_KIND", "Hãy chọn ít nhất một loại cảnh báo, hoặc tắt cảnh báo.");
            }
        }
    }

    /** Fields of a partial update; null = unchanged. */
    public record Change(String name, AlertFrequency frequency, Boolean alertNew, Boolean alertPriceDrop,
                         Boolean alertBackOnMarket, Boolean paused) {}

    public record Listed(SavedSearch search, int pendingMatches) {}
}
