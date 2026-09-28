package com.company.bds.listing.api.v2;

import com.company.bds.listing.api.ListingController;
import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.application.port.out.OwnerListingQueryPort;
import com.company.bds.listing.application.service.ListingFreshnessService;
import com.company.bds.listing.application.service.ListingImportService;
import com.company.bds.listing.application.service.ListingQualityService;
import com.company.bds.listing.domain.model.FreshnessPolicy;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.listing.domain.model.ListingAttributes;
import com.company.bds.listing.domain.model.ListingMedia;
import com.company.bds.listing.domain.model.ListingRevision;
import com.company.bds.listing.domain.model.ListingStatus;
import com.company.bds.listing.domain.model.RevisionStatus;
import com.company.bds.media.ImageDto;
import com.company.bds.shared.security.ContactInfoGuard;
import com.company.bds.shared.security.CurrentUser;
import com.company.bds.shared.security.Roles;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owner side of listings, API v2: my-listings paging, draft, preview, freshness actions, CSV import. */
@RestController
@RequestMapping("/api/v2/me/listings")
public class OwnerListingController {
    private static final int MAX_PAGE_SIZE = 50;

    private final OwnerListingQueryPort query;
    private final ListingPersistencePort listings;
    private final ListingQualityService quality;
    private final ListingFreshnessService freshness;
    private final ListingImportService importer;
    private final Clock clock;

    public OwnerListingController(OwnerListingQueryPort query, ListingPersistencePort listings, ListingQualityService quality,
                                  ListingFreshnessService freshness, ListingImportService importer, Clock clock) {
        this.query = query;
        this.listings = listings;
        this.quality = quality;
        this.freshness = freshness;
        this.importer = importer;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<ListingViews.MyListingsPage> myListings(@RequestParam(required = false) String status,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size,
                                                                  Authentication authentication) {
        ListingStatus filter = null;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            try {
                filter = ListingStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Trạng thái không hợp lệ: " + status);
            }
        }
        if (page < 0 || page > 10_000) throw new IllegalArgumentException("page phải trong khoảng 0..10000");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new IllegalArgumentException("size phải trong khoảng 1.." + MAX_PAGE_SIZE);
        OwnerListingQueryPort.Page result = query.findPage(CurrentUser.id(authentication), filter, page, size);

        List<ListingQualityService.Input> inputs = new ArrayList<>();
        for (OwnerListingQueryPort.Row row : result.rows()) {
            OwnerListingQueryPort.RevisionSummary w = row.latestRevision();
            OwnerListingQueryPort.WorkingFacts f = row.working();
            inputs.add(new ListingQualityService.Input(row.id(), w.purpose(), w.propertyType(), w.priceVnd(), w.areaM2(),
                    f.imageCount(), f.description(), f.districtCode(), f.latitude(), f.longitude(), f.legalStatusCode(),
                    f.depositVnd(), f.monthlyServiceFeeVnd()));
        }
        Map<UUID, ListingQualityService.Report> reports = quality.evaluateAll(inputs);
        Instant now = clock.instant();
        List<ListingViews.MyListingItem> items = result.rows().stream().map(row -> {
            OwnerListingQueryPort.RevisionSummary pub = row.publicRevision();
            OwnerListingQueryPort.RevisionSummary latest = row.latestRevision();
            boolean hasEdit = pub == null || !pub.id().equals(latest.id());
            return new ListingViews.MyListingItem(row.id(), row.slug(), row.status().name(), row.source(), row.version(),
                    row.createdAt(), row.updatedAt(), row.thumbnailUrl(), row.leadCount(),
                    pub == null ? null : summary(pub), hasEdit ? summary(latest) : null,
                    freshness(row.status(), row.availabilityConfirmedAt(), row.expiresAt(), row.soldCheckDueAt(),
                            pub != null && !hasEdit, now),
                    reports.get(row.id()));
        }).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ALL", result.countsByStatus().values().stream().mapToLong(Long::longValue).sum());
        result.countsByStatus().forEach((key, value) -> counts.put(key.name(), value));
        int totalPages = (int) Math.max(1, (result.total() + size - 1) / size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ListingViews.MyListingsPage(items, page, size, result.total(), totalPages, counts));
    }

    @GetMapping("/{id}/draft")
    public ResponseEntity<ListingViews.DraftView> draft(@PathVariable UUID id, Authentication authentication) {
        Listing listing = ownedOrStaff(id, authentication);
        ListingRevision r = listing.getLatestRevision().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ListingAttributes a = r.getAttributes();
        String rejection = r.getStatus() == RevisionStatus.REJECTED ? r.getModerationNote() : null;
        ListingViews.DraftView view = new ListingViews.DraftView(listing.getId(), listing.getSlug(), listing.getStatus().name(),
                listing.getVersion(), r.getId(), r.getRevisionNumber(), r.getStatus().name(), rejection, r.getTitle(),
                r.getPurpose().name(), r.getPropertyType().name(), r.getPriceVnd(), r.getAreaM2(), r.getBedrooms(),
                r.getBathrooms(), r.getFloors(), r.getFrontageM(), r.getRoadWidthM(), r.getDirection(), r.getLegalStatus(),
                a.legalStatusCode() == null ? null : a.legalStatusCode().name(), a.furnishing() == null ? null : a.furnishing().name(),
                a.monthlyServiceFeeVnd(), a.depositVnd(), a.projectId(), r.getDescription(), r.getProvinceCode(),
                r.getDistrictCode(), r.getWardCode(), r.getAddressSummary(), r.getPublicLatitude(), r.getPublicLongitude(),
                r.getMediaList().stream().map(ListingMedia::mediaUrl).toList(), listing.getPublicRevisionId() != null,
                quality.evaluate(ListingQualityService.Input.of(listing.getId(), r)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(ListingController.etag(listing.getVersion())).body(view);
    }

    /**
     * What the public page would show: the latest revision by default, {@code ?version=public} for the published one.
     * Contact details are redacted exactly like on the public page.
     */
    @GetMapping("/{id}/preview")
    public ResponseEntity<ListingViews.PreviewView> preview(@PathVariable UUID id,
                                                            @RequestParam(defaultValue = "latest") String version,
                                                            Authentication authentication) {
        Listing listing = ownedOrStaff(id, authentication);
        boolean published = version.equalsIgnoreCase("public");
        ListingRevision r = (published ? listing.getPublicRevision() : listing.getLatestRevision())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin chưa có phiên bản này."));
        ListingAttributes a = r.getAttributes();
        List<ImageDto> images = r.getMediaList().stream().map(m -> ImageDto.urlOnly(m.mediaUrl())).toList();
        ListingViews.PreviewView view = new ListingViews.PreviewView(listing.getId(), listing.getSlug(),
                published ? "PUBLIC" : "LATEST", ContactInfoGuard.redact(r.getTitle()), r.getPurpose().name(),
                r.getPropertyType().name(), ListingViews.Money.of(r.getPriceVnd(), r.getPurpose()),
                ListingViews.UnitPrice.of(r.getPriceVnd(), r.getAreaM2(), r.getPurpose()), r.getAreaM2(), r.getBedrooms(),
                r.getBathrooms(), r.getFloors(), r.getFrontageM(), r.getRoadWidthM(), ContactInfoGuard.redact(r.getDirection()),
                new ListingViews.Location(r.getProvinceCode(), r.getDistrictCode(), r.getWardCode(),
                        ContactInfoGuard.redact(r.getAddressSummary()), r.getPublicLatitude(), r.getPublicLongitude(), "APPROXIMATE"),
                ContactInfoGuard.redact(r.getDescription()), images, ListingViews.RentTerms.of(a, r.getPurpose()),
                ListingViews.Legal.of(a, ContactInfoGuard.redact(r.getLegalStatus())),
                a.furnishing() == null ? null : a.furnishing().name(), r.getRevisionNumber(), r.getStatus().name(),
                quality.evaluate(ListingQualityService.Input.of(listing.getId(), r)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view);
    }

    @PostMapping("/{id}/confirm-availability")
    public ResponseEntity<Map<String, Object>> confirmAvailability(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(lifecycle(freshness.confirmAvailability(id, CurrentUser.id(authentication))));
    }

    @PostMapping("/{id}/renew")
    public ResponseEntity<Map<String, Object>> renew(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(lifecycle(freshness.renew(id, CurrentUser.id(authentication))));
    }

    @GetMapping(value = "/import/template", produces = "text/csv")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=\"mau-nhap-tin-dang.csv\"")
                .cacheControl(CacheControl.noStore())
                .body(ListingImportService.template().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ListingImportService.ImportReport> importCsv(@RequestPart("file") MultipartFile file,
                                                                       @RequestParam(defaultValue = "true") boolean dryRun,
                                                                       Authentication authentication) throws IOException {
        if (file.getSize() > ListingImportService.MAX_BYTES) {
            throw new IllegalArgumentException("Tệp vượt quá 1 MB.");
        }
        ListingImportService.ImportReport report = importer.importCsv(CurrentUser.id(authentication), file.getBytes(), dryRun);
        HttpStatus status = !dryRun && report.committed() && !report.duplicate() ? HttpStatus.CREATED
                : (!dryRun && !report.committed() ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.OK);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(report);
    }

    private Map<String, Object> lifecycle(Listing listing) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("listingId", listing.getId());
        body.put("status", listing.getStatus().name());
        body.put("version", listing.getVersion());
        body.put("freshness", freshness(listing.getStatus(), listing.getAvailabilityConfirmedAt(), listing.getExpiresAt(),
                listing.getSoldCheckDueAt(), true, clock.instant()));
        return body;
    }

    static ListingViews.Freshness freshness(ListingStatus status, Instant confirmed, Instant expires, Instant soldCheck,
                                            boolean unchanged, Instant now) {
        Long days = expires == null ? null : Math.max(0, Duration.between(now, expires).toDays());
        boolean soon = status == ListingStatus.ACTIVE && expires != null
                && !expires.isAfter(now.plus(FreshnessPolicy.FIRST_REMINDER));
        boolean renewable = status == ListingStatus.EXPIRED && unchanged
                && (expires == null || !now.isAfter(expires.plus(FreshnessPolicy.RENEWAL_WINDOW)));
        return new ListingViews.Freshness(confirmed, expires, days, soon, soldCheck, renewable);
    }

    private static ListingViews.VersionSummary summary(OwnerListingQueryPort.RevisionSummary r) {
        return new ListingViews.VersionSummary(r.id(), r.revisionNumber(), r.status(), ContactInfoGuard.redact(r.title()),
                r.purpose().name(), r.propertyType().name(), ListingViews.Money.of(r.priceVnd(), r.purpose()), r.areaM2(),
                r.submittedAt(), r.moderatedAt(), "REJECTED".equals(r.status()) ? r.moderationNote() : null);
    }

    private Listing ownedOrStaff(UUID id, Authentication authentication) {
        Listing listing = listings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean staff = authentication.getAuthorities().stream()
                .anyMatch(a -> Roles.STAFF.stream().anyMatch(role -> a.getAuthority().equals("ROLE_" + role)));
        if (!staff && !listing.getOwnerId().equals(CurrentUser.id(authentication))) {
            // Same answer as a missing listing: do not reveal that someone else's draft exists.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return listing;
    }
}
