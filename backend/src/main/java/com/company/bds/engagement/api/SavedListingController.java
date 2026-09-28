package com.company.bds.engagement.api;

import com.company.bds.engagement.application.SavedListingService;
import com.company.bds.engagement.application.port.SavedListingStorePort.GoneListing;
import com.company.bds.engagement.application.port.SavedListingStorePort.SavedRow;
import com.company.bds.search.api.ListingSummaries;
import com.company.bds.search.api.response.ListingV2Responses.ListingSummaryV2;
import com.company.bds.shared.security.ContactInfoGuard;
import com.company.bds.shared.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Favourites of the signed-in user. */
@RestController
@RequestMapping("/api/v1/me/saved-listings")
public class SavedListingController {
    private final SavedListingService saved;
    private final ListingSummaries summaries;

    public SavedListingController(SavedListingService saved, ListingSummaries summaries) {
        this.saved = saved;
        this.summaries = summaries;
    }

    @GetMapping
    public SavedListingPage page(Authentication auth, @RequestParam(value = "cursor", required = false) String cursor,
                                 @RequestParam(value = "size", defaultValue = "24") int size) {
        PageCursor after = PageCursor.decode(cursor);
        SavedListingService.Page page = saved.page(CurrentUser.id(auth), after == null ? null : after.at(),
                after == null ? null : after.id(), PageCursor.size(size, 48));
        List<UUID> ids = page.rows().stream().map(SavedRow::listingId).toList();
        Map<UUID, ListingSummaryV2> visible = summaries.byIds(ids);
        List<UUID> missing = ids.stream().filter(id -> !visible.containsKey(id)).toList();
        Map<UUID, GoneListing> gone = missing.isEmpty() ? Map.of() : saved.gone(missing);
        List<SavedListingItem> items = new ArrayList<>();
        for (SavedRow row : page.rows()) {
            ListingSummaryV2 summary = visible.get(row.listingId());
            GoneListing stub = gone.get(row.listingId());
            items.add(new SavedListingItem(row.listingId(), row.savedAt(), summary,
                    summary != null || stub == null ? null : new Unavailable(stub.slug(), ContactInfoGuard.redact(stub.title()))));
        }
        String next = page.hasNext() && !page.rows().isEmpty()
                ? new PageCursor(page.rows().get(page.rows().size() - 1).savedAt(), page.rows().get(page.rows().size() - 1).listingId()).encode()
                : null;
        return new SavedListingPage(items, next, page.total(), SavedListingService.MAX_SAVED);
    }

    @GetMapping("/ids")
    public SavedIds ids(Authentication auth) {
        return new SavedIds(saved.ids(CurrentUser.id(auth)), SavedListingService.MAX_SAVED);
    }

    @PutMapping("/{listingId}")
    public SavedState save(Authentication auth, @PathVariable UUID listingId) {
        return new SavedState(listingId, true, saved.save(CurrentUser.id(auth), listingId));
    }

    @DeleteMapping("/{listingId}")
    public ResponseEntity<Void> unsave(Authentication auth, @PathVariable UUID listingId) {
        saved.unsave(CurrentUser.id(auth), listingId);
        return ResponseEntity.noContent().build();
    }

    /** {@code listing} for a public listing; otherwise {@code unavailable} (title null when it must not be shown). */
    public record SavedListingItem(UUID listingId, Instant savedAt, ListingSummaryV2 listing, Unavailable unavailable) {}

    public record Unavailable(String slug, String title) {}

    public record SavedListingPage(List<SavedListingItem> items, String nextCursor, int total, int limit) {}

    public record SavedIds(List<UUID> ids, int limit) {}

    public record SavedState(UUID listingId, boolean saved, Instant savedAt) {}
}
