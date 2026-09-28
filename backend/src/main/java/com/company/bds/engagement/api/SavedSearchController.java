package com.company.bds.engagement.api;

import com.company.bds.engagement.application.SavedSearchService;
import com.company.bds.engagement.application.SavedSearchService.Settings;
import com.company.bds.engagement.application.port.SavedSearchStorePort.SavedSearch;
import com.company.bds.engagement.domain.AlertFrequency;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Saved searches of the signed-in user. The filter uses the search API's parameters (contract §7). */
@RestController
@RequestMapping("/api/v1/me/saved-searches")
public class SavedSearchController {
    private static final int MAX_FILTER_PARAMS = 30;

    private final SavedSearchService searches;

    public SavedSearchController(SavedSearchService searches) {
        this.searches = searches;
    }

    @GetMapping
    public SavedSearchList list(Authentication auth) {
        List<SavedSearchDto> items = searches.list(CurrentUser.id(auth)).stream()
                .map(listed -> SavedSearchDto.of(listed.search(), listed.pendingMatches())).toList();
        return new SavedSearchList(items, SavedSearchService.MAX_SEARCHES);
    }

    @PostMapping
    public ResponseEntity<SavedSearchDto> create(Authentication auth, @RequestBody CreateRequest body) {
        if (body == null || body.filter() == null || body.filter().size() > MAX_FILTER_PARAMS) {
            throw ApiException.badRequest("INVALID_FILTER", "Thiếu bộ lọc tìm kiếm.");
        }
        Map<String, String[]> raw = new HashMap<>();
        body.filter().forEach((key, value) -> { if (value != null) raw.put(key, new String[]{value}); });
        SavedSearch created = searches.create(CurrentUser.id(auth), raw, body.name(), settings(body.frequency(), body.alertNew(),
                body.alertPriceDrop(), body.alertBackOnMarket(), Settings.defaults()));
        return ResponseEntity.status(HttpStatus.CREATED).body(SavedSearchDto.of(created, 0));
    }

    @PatchMapping("/{id}")
    public SavedSearchDto update(Authentication auth, @PathVariable UUID id, @RequestBody UpdateRequest body) {
        if (body == null || body.expectedVersion() == null) {
            throw ApiException.badRequest("VERSION_REQUIRED", "Thiếu phiên bản (expectedVersion).");
        }
        SavedSearch updated = searches.update(CurrentUser.id(auth), id, body.expectedVersion(),
                new SavedSearchService.Change(body.name(), frequency(body.frequency()), body.alertNew(), body.alertPriceDrop(),
                        body.alertBackOnMarket(), body.paused()));
        return SavedSearchDto.of(updated, 0);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        searches.delete(CurrentUser.id(auth), id);
        return ResponseEntity.noContent().build();
    }

    private static Settings settings(String frequency, Boolean alertNew, Boolean alertPriceDrop, Boolean alertBackOnMarket,
                                     Settings base) {
        AlertFrequency parsed = frequency(frequency);
        return new Settings(parsed == null ? base.frequency() : parsed, alertNew == null ? base.alertNew() : alertNew,
                alertPriceDrop == null ? base.alertPriceDrop() : alertPriceDrop,
                alertBackOnMarket == null ? base.alertBackOnMarket() : alertBackOnMarket);
    }

    private static AlertFrequency frequency(String value) {
        if (value == null) return null;
        try {
            return AlertFrequency.parse(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_FREQUENCY", "Tần suất phải là INSTANT, DAILY, WEEKLY hoặc OFF.");
        }
    }

    /** Search page query string of the stored filter ({@code /search?<query>}). */
    static String query(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public record CreateRequest(Map<String, String> filter, String name, String frequency, Boolean alertNew,
                                Boolean alertPriceDrop, Boolean alertBackOnMarket) {}

    public record UpdateRequest(Long expectedVersion, String name, String frequency, Boolean alertNew, Boolean alertPriceDrop,
                                Boolean alertBackOnMarket, Boolean paused) {}

    public record SavedSearchDto(UUID id, String name, Map<String, String> filter, String query, String filterHash,
                                 String frequency, boolean alertNew, boolean alertPriceDrop, boolean alertBackOnMarket,
                                 boolean paused, int pendingMatches, Instant nextDigestAt, Instant lastDigestAt, long version,
                                 Instant createdAt) {
        static SavedSearchDto of(SavedSearch s, int pending) {
            return new SavedSearchDto(s.id(), s.name(), s.params(), SavedSearchController.query(s.params()), s.filterHash(), s.frequency(),
                    s.alertNew(), s.alertPriceDrop(), s.alertBackOnMarket(), s.paused(), pending, s.nextDigestAt(),
                    s.lastDigestAt(), s.version(), s.createdAt());
        }
    }

    public record SavedSearchList(List<SavedSearchDto> items, int limit) {}
}
