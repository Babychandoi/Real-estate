package com.company.bds.engagement.api;

import com.company.bds.engagement.application.SavedListingService;
import com.company.bds.engagement.application.ShortlistService;
import com.company.bds.engagement.application.port.SavedListingStorePort.GoneListing;
import com.company.bds.engagement.application.port.ShortlistStorePort;
import com.company.bds.search.api.ListingSummaries;
import com.company.bds.search.api.response.ListingV2Responses.ListingSummaryV2;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.ContactInfoGuard;
import com.company.bds.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shortlists of the signed-in user, plus the read-only public view behind a share link. */
@RestController
public class ShortlistController {
    private final ShortlistService shortlists;
    private final SavedListingService saved;
    private final ListingSummaries summaries;

    public ShortlistController(ShortlistService shortlists, SavedListingService saved, ListingSummaries summaries) {
        this.shortlists = shortlists;
        this.saved = saved;
        this.summaries = summaries;
    }

    @GetMapping("/api/v1/me/shortlists")
    public List<ShortlistStorePort.Summary> mine(Authentication auth) {
        return shortlists.mine(CurrentUser.id(auth));
    }

    @PostMapping("/api/v1/me/shortlists")
    public ResponseEntity<ShortlistDetail> create(Authentication auth, @RequestBody NameRequest body) {
        UUID userId = CurrentUser.id(auth);
        UUID id = shortlists.create(userId, body == null ? null : body.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(detail(id, userId));
    }

    @GetMapping("/api/v1/me/shortlists/{id}")
    public ShortlistDetail get(Authentication auth, @PathVariable UUID id) {
        return detail(id, CurrentUser.id(auth));
    }

    @PatchMapping("/api/v1/me/shortlists/{id}")
    public ShortlistDetail rename(Authentication auth, @PathVariable UUID id, @RequestBody RenameRequest body) {
        UUID userId = CurrentUser.id(auth);
        if (body == null || body.expectedVersion() == null) {
            throw ApiException.badRequest("VERSION_REQUIRED", "Thiếu phiên bản danh sách (expectedVersion).");
        }
        shortlists.rename(id, userId, body.name(), body.expectedVersion());
        return detail(id, userId);
    }

    @DeleteMapping("/api/v1/me/shortlists/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        shortlists.delete(id, CurrentUser.id(auth));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/v1/me/shortlists/{id}/items/{listingId}")
    public ShortlistDetail addItem(Authentication auth, @PathVariable UUID id, @PathVariable UUID listingId) {
        UUID userId = CurrentUser.id(auth);
        shortlists.addItem(id, userId, listingId);
        return detail(id, userId);
    }

    @DeleteMapping("/api/v1/me/shortlists/{id}/items/{listingId}")
    public ShortlistDetail removeItem(Authentication auth, @PathVariable UUID id, @PathVariable UUID listingId) {
        UUID userId = CurrentUser.id(auth);
        shortlists.removeItem(id, userId, listingId);
        return detail(id, userId);
    }

    /** Creates or rotates the share link. The token is returned only here; store it in the link you send. */
    @PostMapping("/api/v1/me/shortlists/{id}/share")
    public ShareLink share(Authentication auth, @PathVariable UUID id, @RequestBody ShareRequest body) {
        String role = body == null ? null : body.role();
        String token = shortlists.share(id, CurrentUser.id(auth), role);
        return new ShareLink(token, "/shortlists/" + token, role);
    }

    @DeleteMapping("/api/v1/me/shortlists/{id}/share")
    public ResponseEntity<Void> revoke(Authentication auth, @PathVariable UUID id) {
        shortlists.revokeShare(id, CurrentUser.id(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/me/shortlists/join")
    public ShortlistDetail join(Authentication auth, @RequestBody TokenRequest body) {
        UUID userId = CurrentUser.id(auth);
        UUID id = shortlists.join(body == null ? null : body.token(), userId);
        return detail(id, userId);
    }

    @PatchMapping("/api/v1/me/shortlists/{id}/members/{memberId}")
    public ShortlistDetail setRole(Authentication auth, @PathVariable UUID id, @PathVariable UUID memberId,
                                   @RequestBody ShareRequest body) {
        UUID userId = CurrentUser.id(auth);
        shortlists.setMemberRole(id, userId, memberId, body == null ? null : body.role());
        return detail(id, userId);
    }

    @DeleteMapping("/api/v1/me/shortlists/{id}/members/{memberId}")
    public ShortlistDetail removeMember(Authentication auth, @PathVariable UUID id, @PathVariable UUID memberId) {
        UUID userId = CurrentUser.id(auth);
        shortlists.removeMember(id, userId, memberId);
        return detail(id, userId);
    }

    @DeleteMapping("/api/v1/me/shortlists/{id}/membership")
    public ResponseEntity<Void> leave(Authentication auth, @PathVariable UUID id) {
        shortlists.leave(id, CurrentUser.id(auth));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/v1/me/shortlists/{id}/mute")
    public ShortlistDetail mute(Authentication auth, @PathVariable UUID id, @RequestBody MuteRequest body) {
        UUID userId = CurrentUser.id(auth);
        shortlists.mute(id, userId, body != null && body.muted());
        return detail(id, userId);
    }

    /** Anyone holding the link: the list name, the owner's given name and the listings that are public now. */
    @GetMapping("/api/v1/public/shortlists/{token}")
    public PublicShortlist publicView(@PathVariable String token) {
        ShortlistService.PublicView view = shortlists.publicView(token);
        List<ListingSummaryV2> items = List.copyOf(summaries.byIds(view.listingIds()).values());
        return new PublicShortlist(view.name(), view.ownerGivenName(), view.shareRole(), items);
    }

    private ShortlistDetail detail(UUID id, UUID userId) {
        ShortlistService.Detail detail = shortlists.detail(id, userId);
        List<UUID> ids = detail.items().stream().map(ShortlistStorePort.Item::listingId).toList();
        Map<UUID, ListingSummaryV2> visible = summaries.byIds(ids);
        List<UUID> missing = ids.stream().filter(listingId -> !visible.containsKey(listingId)).toList();
        Map<UUID, GoneListing> gone = missing.isEmpty() ? Map.of() : saved.gone(missing);
        boolean owner = "OWNER".equals(detail.access().role());
        List<Item> items = detail.items().stream().map(item -> {
            ListingSummaryV2 summary = visible.get(item.listingId());
            GoneListing stub = gone.get(item.listingId());
            return new Item(item.listingId(), item.addedAt(), item.addedBy() != null && item.addedBy().equals(userId), summary,
                    summary != null || stub == null ? null
                            : new SavedListingController.Unavailable(stub.slug(), ContactInfoGuard.redact(stub.title())));
        }).toList();
        // Members see each other by given name only; the owner, who shared the list, sees full names.
        List<Member> members = detail.members().stream().map(m -> new Member(
                owner || m.userId().equals(userId) ? m.userId() : null,
                owner || m.userId().equals(userId) ? m.name() : ShortlistService.givenName(m.name()), m.role(),
                owner || m.userId().equals(userId) ? m.muted() : null, m.joinedAt())).toList();
        var access = detail.access();
        return new ShortlistDetail(access.shortlistId(), access.name(), access.role(), access.muted(), access.version(),
                owner ? new ShareState(access.shared(), access.shareRole()) : null, items, members, detail.itemCount(),
                ShortlistService.MAX_ITEMS);
    }

    public record NameRequest(String name) {}

    public record RenameRequest(String name, Long expectedVersion) {}

    public record ShareRequest(String role) {}

    public record TokenRequest(String token) {}

    public record MuteRequest(boolean muted) {}

    public record ShareLink(String token, String path, String role) {}

    public record ShareState(boolean shared, String role) {}

    public record Item(UUID listingId, Instant addedAt, boolean addedByMe, ListingSummaryV2 listing,
                       SavedListingController.Unavailable unavailable) {}

    public record Member(UUID userId, String name, String role, Boolean muted, Instant joinedAt) {}

    /** {@code share} only for the owner. */
    public record ShortlistDetail(UUID id, String name, String role, boolean muted, long version, ShareState share,
                                  List<Item> items, List<Member> members, int itemCount, int itemLimit) {}

    public record PublicShortlist(String name, String ownerGivenName, String role, List<ListingSummaryV2> items) {}
}
