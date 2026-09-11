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

import java.math.BigDecimal;
import java.time.Instant;
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

    // Default mock user ID cho các thao tác khi chưa đăng nhập đầy đủ (giai đoạn dev)
    public static final UUID DEFAULT_DEMO_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CreateListingDraftUseCase createDraftUseCase;
    private final UpdateListingDraftUseCase updateDraftUseCase;
    private final SubmitListingRevisionUseCase submitRevisionUseCase;
    private final GetListingDetailUseCase getListingDetailUseCase;
    private final ListingPersistencePort persistencePort;
    private final com.company.bds.listing.application.service.ListingApplicationService listingAppService;

    public ListingController(
            CreateListingDraftUseCase createDraftUseCase,
            UpdateListingDraftUseCase updateDraftUseCase,
            SubmitListingRevisionUseCase submitRevisionUseCase,
            GetListingDetailUseCase getListingDetailUseCase,
            ListingPersistencePort persistencePort,
            com.company.bds.listing.application.service.ListingApplicationService listingAppService) {
        this.createDraftUseCase = createDraftUseCase;
        this.updateDraftUseCase = updateDraftUseCase;
        this.submitRevisionUseCase = submitRevisionUseCase;
        this.getListingDetailUseCase = getListingDetailUseCase;
        this.persistencePort = persistencePort;
        this.listingAppService = listingAppService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createDraft(@Valid @RequestBody CreateListingDraftRequest request) {
        CreateListingDraftCommand command = new CreateListingDraftCommand(
                DEFAULT_DEMO_USER_ID,
                request.title(),
                request.purpose(),
                request.propertyType(),
                request.priceVnd(),
                request.areaM2(),
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
            @Valid @RequestBody UpdateListingDraftRequest request) {

        UpdateListingDraftCommand command = new UpdateListingDraftCommand(
                id,
                DEFAULT_DEMO_USER_ID,
                request.title(),
                request.purpose(),
                request.propertyType(),
                request.priceVnd(),
                request.areaM2(),
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

    @PostMapping("/{id}/submit")
    public ResponseEntity<Map<String, Object>> submitRevision(@PathVariable UUID id) {
        SubmitListingRevisionCommand command = new SubmitListingRevisionCommand(id, DEFAULT_DEMO_USER_ID);
        submitRevisionUseCase.submitRevision(command);

        return ResponseEntity.ok(Map.of(
                "listingId", id,
                "status", "PENDING_REVIEW",
                "message", "Nộp duyệt tin đăng thành công. Hồ sơ đang được chuyển tới hội đồng thẩm định."
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListingDetailResponse> getListingById(@PathVariable UUID id) {
        return getListingDetailUseCase.getListingById(id)
                .map(this::mapToDetailResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    // Fallback mock nếu chưa có trong DB (cho trang chi tiết demo ban đầu)
                    return ResponseEntity.ok(new ListingDetailResponse(
                            id,
                            DEFAULT_DEMO_USER_ID,
                            "ACTIVE",
                            1,
                            "APPROVED",
                            "Căn hộ cao cấp Vinhomes Green Bay 2PN, view hồ thoáng đãng",
                            "SALE",
                            "APARTMENT",
                            5250000000L,
                            new BigDecimal("72.5"),
                            "Căn hộ tầng trung view trực diện công viên và hồ điều hòa, ban công Đông Nam mát mẻ quanh năm.\n- Nội thất bàn giao full cao cấp.\n- Pháp lý minh bạch: Sổ hồng lâu dài.",
                            "01",
                            "019",
                            "00600",
                            "Mễ Trì, Nam Từ Liêm, Hà Nội",
                            21.0062,
                            105.7892,
                            true,
                            List.of("https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80"),
                            Instant.now(),
                            Instant.now()
                    ));
                });
    }

    @GetMapping("/my-listings")
    public ResponseEntity<List<ListingDetailResponse>> getMyListings() {
        List<Listing> myListings = getListingDetailUseCase.getMyListings(DEFAULT_DEMO_USER_ID);
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

        List<Listing> activeListings = persistencePort.searchListings(criteria, page, size);

        if (!activeListings.isEmpty()) {
            List<ListingSummaryResponse> results = activeListings.stream()
                    .map(listing -> {
                        ListingRevision rev = listing.getPublicRevision().or(listing::getLatestRevision).orElse(null);
                        String imgUrl = "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80";
                        if (rev != null && !rev.getMediaList().isEmpty()) {
                            imgUrl = rev.getMediaList().get(0).mediaUrl();
                        }
                        return new ListingSummaryResponse(
                                listing.getId(),
                                rev != null ? rev.getTitle() : "Tin đăng BĐS",
                                rev != null ? rev.getPurpose().name() : (purpose != null ? purpose : "SALE"),
                                rev != null ? rev.getPropertyType().name() : "APARTMENT",
                                rev != null ? rev.getPriceVnd() : 0L,
                                rev != null && rev.getAreaM2() != null ? rev.getAreaM2() : BigDecimal.ZERO,
                                rev != null && rev.getAddressSummary() != null ? rev.getAddressSummary() : "",
                                (rev != null && rev.getPublicLatitude() != null) ? rev.getPublicLatitude() : 21.0,
                                (rev != null && rev.getPublicLongitude() != null) ? rev.getPublicLongitude() : 105.8,
                                true,
                                imgUrl,
                                listing.getCreatedAt()
                        );
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(results);
        }

        // Nếu người dùng có áp dụng bộ lọc cụ thể (giá, tọa độ GIS) mà DB chưa có dữ liệu khớp -> trả về rỗng
        if (minPrice != null || maxPrice != null || minLat != null || keyword != null) {
            return ResponseEntity.ok(List.of());
        }

        // Fallback demo items phong phú tại Hà Nội (Pilot) cho trải nghiệm bản đồ
        return ResponseEntity.ok(List.of(
                new ListingSummaryResponse(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "Căn hộ cao cấp Vinhomes Green Bay 2PN, view hồ thoáng đãng",
                        "SALE",
                        "APARTMENT",
                        4850000000L,
                        new BigDecimal("70.0"),
                        "Mễ Trì, Nam Từ Liêm, Hà Nội",
                        21.0062,
                        105.7892,
                        true,
                        "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80",
                        Instant.now()
                ),
                new ListingSummaryResponse(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "Căn hộ 2PN The Matrix One Mễ Trì view công viên hồ điều hòa",
                        "SALE",
                        "APARTMENT",
                        4200000000L,
                        new BigDecimal("72.0"),
                        "Mễ Trì, Nam Từ Liêm, Hà Nội",
                        21.0118,
                        105.7725,
                        true,
                        "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=800&q=80",
                        Instant.now()
                ),
                new ListingSummaryResponse(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        "Căn hộ Masteri West Heights Tây Mỗ 2PN resort",
                        "SALE",
                        "APARTMENT",
                        3200000000L,
                        new BigDecimal("62.0"),
                        "Tây Mỗ, Nam Từ Liêm, Hà Nội",
                        21.0025,
                        105.7423,
                        true,
                        "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=800&q=80",
                        Instant.now()
                ),
                new ListingSummaryResponse(
                        UUID.fromString("44444444-4444-4444-4444-444444444444"),
                        "Căn hộ Vinhomes Smart City Sapphire 2 full nội thất",
                        "SALE",
                        "APARTMENT",
                        2800000000L,
                        new BigDecimal("55.0"),
                        "Tây Mỗ, Nam Từ Liêm, Hà Nội",
                        20.9985,
                        105.7390,
                        true,
                        "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=800&q=80",
                        Instant.now()
                )
        ));
    }

    private ListingDetailResponse mapToDetailResponse(Listing listing) {
        ListingRevision rev = listing.getPublicRevision().or(listing::getLatestRevision).orElse(null);

        List<String> images = new ArrayList<>();
        if (rev != null) {
            for (ListingMedia m : rev.getMediaList()) {
                images.add(m.mediaUrl());
            }
        }
        if (images.isEmpty()) {
            images.add("https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80");
        }

        return new ListingDetailResponse(
                listing.getId(),
                listing.getOwnerId(),
                listing.getStatus().name(),
                rev != null ? rev.getRevisionNumber() : 1,
                rev != null ? rev.getStatus().name() : "DRAFT",
                rev != null ? rev.getTitle() : "",
                rev != null ? rev.getPurpose().name() : "SALE",
                rev != null ? rev.getPropertyType().name() : "APARTMENT",
                rev != null ? rev.getPriceVnd() : 0L,
                rev != null ? rev.getAreaM2() : BigDecimal.ZERO,
                rev != null ? rev.getDescription() : "",
                rev != null ? rev.getProvinceCode() : "",
                rev != null ? rev.getDistrictCode() : "",
                rev != null ? rev.getWardCode() : "",
                rev != null ? rev.getAddressSummary() : "",
                (rev != null && rev.getPublicLatitude() != null) ? rev.getPublicLatitude() : 21.0,
                (rev != null && rev.getPublicLongitude() != null) ? rev.getPublicLongitude() : 105.8,
                rev != null && rev.getStatus().name().equals("APPROVED"),
                images,
                listing.getCreatedAt(),
                listing.getUpdatedAt()
        );
    }

    @PostMapping("/estimate-price")
    public ResponseEntity<com.company.bds.listing.api.response.EstimatePriceResponse> estimatePrice(
            @Valid @RequestBody com.company.bds.listing.api.request.EstimatePriceRequest request) {
        return ResponseEntity.ok(listingAppService.estimatePrice(request));
    }

    @PostMapping("/quality-score")
    public ResponseEntity<com.company.bds.listing.api.response.QualityScoreResponse> calculateQualityScore(
            @RequestBody com.company.bds.listing.api.request.QualityScoreRequest request) {
        return ResponseEntity.ok(listingAppService.calculateQualityScore(request));
    }
}
