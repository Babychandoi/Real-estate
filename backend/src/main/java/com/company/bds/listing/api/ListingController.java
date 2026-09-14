package com.company.bds.listing.api;

import com.company.bds.listing.api.request.CreateListingDraftRequest;
import com.company.bds.listing.api.request.UpdateListingDraftRequest;
import com.company.bds.listing.api.response.ListingDetailResponse;
import com.company.bds.listing.api.response.ListingSummaryResponse;
import com.company.bds.listing.application.command.CreateListingDraftCommand;
import com.company.bds.listing.application.command.SubmitListingRevisionCommand;
import com.company.bds.listing.application.command.UpdateListingDraftCommand;
import com.company.bds.listing.application.port.in.CreateListingDraftUseCase;
import com.company.bds.listing.application.port.in.GetListingDetailUseCase;
import com.company.bds.listing.application.port.in.SubmitListingRevisionUseCase;
import com.company.bds.listing.application.port.in.UpdateListingDraftUseCase;
import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.listing.domain.model.ListingMedia;
import com.company.bds.listing.domain.model.ListingRevision;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import com.company.bds.shared.security.CurrentUser;
import com.company.bds.shared.security.MediaUrlPolicy;
import com.company.bds.media.MediaStorageService;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller quản lý Tin đăng BĐS và vòng đời Revision.
 */
@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {

    private final CreateListingDraftUseCase createDraftUseCase;
    private final UpdateListingDraftUseCase updateDraftUseCase;
    private final SubmitListingRevisionUseCase submitRevisionUseCase;
    private final GetListingDetailUseCase getListingDetailUseCase;
    private final ListingPersistencePort persistencePort;
    private final MediaUrlPolicy mediaUrlPolicy;
    private final ObjectProvider<MediaStorageService> mediaStorageProvider;

    public ListingController(
            CreateListingDraftUseCase createDraftUseCase,
            UpdateListingDraftUseCase updateDraftUseCase,
            SubmitListingRevisionUseCase submitRevisionUseCase,
            GetListingDetailUseCase getListingDetailUseCase,
            ListingPersistencePort persistencePort,
            MediaUrlPolicy mediaUrlPolicy,
            ObjectProvider<MediaStorageService> mediaStorageProvider) {
        this.createDraftUseCase = createDraftUseCase;
        this.updateDraftUseCase = updateDraftUseCase;
        this.submitRevisionUseCase = submitRevisionUseCase;
        this.getListingDetailUseCase = getListingDetailUseCase;
        this.persistencePort = persistencePort;
        this.mediaUrlPolicy = mediaUrlPolicy;
        this.mediaStorageProvider = mediaStorageProvider;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createDraft(@Valid @RequestBody CreateListingDraftRequest request,
                                                            Authentication authentication) {
        UUID ownerId = CurrentUser.id(authentication);
        validateMedia(ownerId, request.imageUrls());
        CreateListingDraftCommand command = new CreateListingDraftCommand(
                ownerId,
                request.title(),
                request.purpose(),
                request.propertyType(),
                request.priceVnd(),
                request.areaM2(),
                request.bedrooms(), request.bathrooms(), request.floors(),
                request.frontageM(), request.roadWidthM(), request.direction(), request.legalStatus(),
                request.description(),
                request.provinceCode(),
                request.districtCode(),
                request.wardCode(),
                request.addressSummary(),
                request.publicLatitude(),
                request.publicLongitude(),
                request.imageUrls()
        );

        UUID listingId = createDraftUseCase.createDraft(command);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "listingId", listingId,
                "status", "DRAFT",
                "message", "Khởi tạo tin đăng nháp thành công."
        ));
    }

    @PutMapping("/{id}/draft")
    public ResponseEntity<Map<String, Object>> updateDraft(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateListingDraftRequest request,
            Authentication authentication) {

        UUID ownerId = CurrentUser.id(authentication);
        validateMedia(ownerId, request.imageUrls());

        UpdateListingDraftCommand command = new UpdateListingDraftCommand(
                id,
                ownerId,
                request.title(),
                request.purpose(),
                request.propertyType(),
                request.priceVnd(),
                request.areaM2(),
                request.bedrooms(), request.bathrooms(), request.floors(),
                request.frontageM(), request.roadWidthM(), request.direction(), request.legalStatus(),
                request.description(),
                request.provinceCode(),
                request.districtCode(),
                request.wardCode(),
                request.addressSummary(),
                request.publicLatitude(),
                request.publicLongitude(),
                request.imageUrls()
        );

        UUID revisionId = updateDraftUseCase.updateDraft(command);

        return ResponseEntity.ok(Map.of(
                "listingId", id,
                "revisionId", revisionId,
                "message", "Cập nhật bản nháp thành công."
        ));
    }

    private void validateMedia(UUID ownerId, List<String> imageUrls) {
        mediaUrlPolicy.validate(imageUrls);
        MediaStorageService storage = mediaStorageProvider.getIfAvailable();
        if (storage != null) storage.validateOwnership(ownerId, imageUrls);
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<Map<String, Object>> submitRevision(@PathVariable UUID id, Authentication authentication) {
        SubmitListingRevisionCommand command = new SubmitListingRevisionCommand(id, CurrentUser.id(authentication));
        submitRevisionUseCase.submitRevision(command);

        return ResponseEntity.ok(Map.of(
                "listingId", id,
                "status", "PENDING_REVIEW",
                "message", "Nộp duyệt tin đăng thành công. Hồ sơ đang được chuyển tới hội đồng thẩm định."
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListingDetailResponse> getListingById(@PathVariable UUID id, Authentication authentication) {
        return getListingDetailUseCase.getListingById(id)
                .filter(listing -> canView(listing, authentication))
                .map(this::mapToDetailResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<ListingDetailResponse> getListingBySlug(@PathVariable String slug, Authentication authentication) {
        return persistencePort.findBySlug(slug)
                .filter(listing -> canView(listing, authentication))
                .map(this::mapToDetailResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/my-listings")
    public ResponseEntity<List<ListingDetailResponse>> getMyListings(Authentication authentication) {
        List<Listing> myListings = getListingDetailUseCase.getMyListings(CurrentUser.id(authentication));
        List<ListingDetailResponse> responses = myListings.stream()
                .map(this::mapToDetailResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ListingSummaryResponse>> searchListings(
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) String propertyType,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) BigDecimal minArea,
            @RequestParam(required = false) BigDecimal maxArea,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double minLng,
            @RequestParam(required = false) Double maxLng,
            @RequestParam(required = false, defaultValue = "LATEST") String sortBy,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        com.company.bds.listing.domain.model.ListingSearchCriteria criteria =
                com.company.bds.listing.domain.model.ListingSearchCriteria.of(
                        purpose, propertyType, minPrice, maxPrice, minArea, maxArea,
                        keyword, minLat, maxLat, minLng, maxLng, sortBy
                );

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<Listing> activeListings = persistencePort.searchListings(criteria, safePage, safeSize);

        if (!activeListings.isEmpty()) {
            List<ListingSummaryResponse> results = activeListings.stream()
                    .map(listing -> {
                        ListingRevision rev = listing.getPublicRevision().orElseThrow(() ->
                                new IllegalStateException("ACTIVE listing has no approved public revision: " + listing.getId()));
                        String imgUrl = "";
                        if (rev != null && !rev.getMediaList().isEmpty()) {
                            imgUrl = rev.getMediaList().get(0).mediaUrl();
                        }
                            return new ListingSummaryResponse(
                                listing.getId(),
                                listing.getSlug(),
                                rev.getTitle(),
                                rev.getPurpose().name(),
                                rev.getPropertyType().name(),
                                rev.getPriceVnd(),
                                rev.getAreaM2(),
                                rev.getAddressSummary(),
                                rev.getPublicLatitude(),
                                rev.getPublicLongitude(),
                                listing.isVerifiedOwner(),
                                false,
                                imgUrl,
                                listing.getCreatedAt()
                        );
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(results);
        }

        return ResponseEntity.ok(List.of());
    }

    private ListingDetailResponse mapToDetailResponse(Listing listing) {
        ListingRevision rev = listing.getPublicRevision().or(listing::getLatestRevision).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Tin đăng chưa có phiên bản dữ liệu."));

        List<String> images = new ArrayList<>();
        for (ListingMedia m : rev.getMediaList()) {
            images.add(m.mediaUrl());
        }

        return new ListingDetailResponse(
                listing.getId(),
                listing.getSlug(),
                listing.getOwnerId(),
                listing.getStatus().name(),
                rev.getRevisionNumber(),
                rev.getStatus().name(),
                rev.getTitle(),
                rev.getPurpose().name(),
                rev.getPropertyType().name(),
                rev.getPriceVnd(),
                rev.getAreaM2(),
                rev.getBedrooms(), rev.getBathrooms(), rev.getFloors(),
                rev.getFrontageM(), rev.getRoadWidthM(), rev.getDirection(), rev.getLegalStatus(),
                rev.getDescription(),
                rev.getProvinceCode(),
                rev.getDistrictCode(),
                rev.getWardCode(),
                rev.getAddressSummary(),
                rev.getPublicLatitude(),
                rev.getPublicLongitude(),
                listing.isVerifiedOwner(),
                false,
                images,
                listing.getCreatedAt(),
                listing.getUpdatedAt()
        );
    }

    private boolean canView(Listing listing, Authentication authentication) {
        if (listing.getStatus().name().equals("ACTIVE")) return true;
        if (authentication == null || !authentication.isAuthenticated()) return false;
        boolean privileged = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MODERATOR"));
        if (privileged) return true;
        try { return listing.getOwnerId().equals(CurrentUser.id(authentication)); }
        catch (IllegalStateException ex) { return false; }
    }

    @PostMapping("/estimate-price")
    public ResponseEntity<com.company.bds.listing.api.response.EstimatePriceResponse> estimatePrice(
            @Valid @RequestBody com.company.bds.listing.api.request.EstimatePriceRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "Chưa có nguồn dữ liệu định giá được kiểm chứng.");
    }

    @PostMapping("/quality-score")
    public ResponseEntity<com.company.bds.listing.api.response.QualityScoreResponse> calculateQualityScore(
            @RequestBody com.company.bds.listing.api.request.QualityScoreRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "Chưa có tiêu chí chấm điểm phía máy chủ được phê duyệt.");
    }
}
