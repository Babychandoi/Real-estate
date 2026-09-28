package com.company.bds.admin;

import com.company.bds.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Admin listing management (ADMIN only, see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/listings")
public class AdminListingController {
    private final AdminListingService service;

    public AdminListingController(AdminListingService service) { this.service = service; }

    @GetMapping
    public AdminListingService.ListingPage search(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) UUID ownerId,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String district,
                                                  @RequestParam(required = false) String source,
                                                  @RequestParam(required = false) Boolean pendingEdit,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.search(new AdminListingService.Filters(status, ownerId, q, district, source, pendingEdit), page, size);
    }

    @GetMapping("/{id}/revisions")
    public List<AdminListingService.RevisionRow> revisions(@PathVariable UUID id) { return service.revisions(id); }

    @GetMapping("/{id}/history")
    public List<AdminListingService.StatusHistoryRow> history(@PathVariable UUID id) { return service.history(id); }

    @PostMapping("/{id}/status")
    public AdminListingService.StatusChange changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request,
                                                         Authentication authentication) {
        return service.changeStatus(id, request.action(), request.reason(), CurrentUser.id(authentication));
    }

    /** Private preview of a draft/submitted/any revision: never stored by a cache, never indexed (R-3). */
    @GetMapping("/{id}/preview")
    public ResponseEntity<AdminListingService.Preview> preview(@PathVariable UUID id, @RequestParam(required = false) UUID revisionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex, nofollow")
                .body(service.preview(id, revisionId));
    }

    public record StatusRequest(@NotBlank String action, @NotBlank(message = "Cần nhập lý do") String reason) {}
}
