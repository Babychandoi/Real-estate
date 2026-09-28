package com.company.bds.catalog.application;

import com.company.bds.catalog.application.port.PublicCatalogStore;
import com.company.bds.catalog.application.port.PublicCatalogStore.Amenity;
import com.company.bds.catalog.application.port.PublicCatalogStore.AreaCard;
import com.company.bds.catalog.application.port.PublicCatalogStore.AreaRow;
import com.company.bds.catalog.application.port.PublicCatalogStore.InventoryRow;
import com.company.bds.catalog.application.port.PublicCatalogStore.ProjectCard;
import com.company.bds.catalog.application.port.PublicCatalogStore.ProjectRow;
import com.company.bds.search.application.port.ResponseCachePort;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.ContactInfoGuard;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public project and area pages (audit P-06) and the home page aggregates (UI-01). Statistics are medians over the
 * listings public right now, shown only with at least {@value #MIN_SAMPLES} listings, with their method, sample size
 * and time; they are asking prices, never transaction prices. Payloads are cached 10 minutes (D-09, §7.3 "Dự án/khu
 * vực/CMS public 5–15 phút") under the public content generation, which a project/CMS change bumps.
 */
@Service
@Transactional(readOnly = true)
public class PublicCatalogService {
    public static final int MIN_SAMPLES = 5;
    public static final String METHOD_SALE = "Trung vị giá chào bán trên mỗi m² của các tin bán đang hiển thị (đã duyệt, còn hiệu lực, người đăng đang hoạt động) tại thời điểm tính. Không phải giá giao dịch thành công.";
    public static final String METHOD_RENT = "Trung vị giá chào thuê theo tháng của các tin cho thuê đang hiển thị tại thời điểm tính. Không phải giá hợp đồng thuê.";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Set<String> AMENITY_CATEGORIES = Set.of("EDUCATION", "HEALTH", "TRANSPORT", "SHOPPING", "PARK", "SPORT", "OTHER");
    private static final Set<String> PROJECT_STATUSES = Set.of("ACTIVE", "PLANNING", "UNDER_CONSTRUCTION", "COMPLETED", "LOCKED");

    public record Statistic(String purpose, long count, Double median, String unit, int minSamples, String method) {}

    public record TypeCount(String purpose, String propertyType, long count) {}

    public record Inventory(long total, List<Statistic> statistics, List<TypeCount> types, Instant lastListingUpdate,
                            Instant dataAsOf) {}

    public record ProjectPage(ProjectRow project, List<Amenity> amenities, Inventory inventory) {}

    public record AreaPage(AreaRow area, Inventory inventory, List<ProjectCard> projects) {}

    public record ProjectList(List<ProjectCard> items, long total, int page, int size) {}

    public record AreaList(List<AreaCard> items, Instant dataAsOf) {}

    public record Home(List<AreaCard> areas, List<ProjectCard> projects, Instant dataAsOf) {}

    public record AmenityInput(String name, String category, Integer distanceM, String sourceName, String sourceUrl,
                               LocalDate checkedAt) {}

    public record ProfileInput(String description, String websiteUrl, String infoSource, LocalDate infoCheckedAt,
                               String status, List<AmenityInput> amenities) {}

    private final PublicCatalogStore store;
    private final ResponseCachePort cache;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PublicCatalogService(PublicCatalogStore store, ResponseCachePort cache, JdbcTemplate jdbc, Clock clock) {
        this.store = store;
        this.cache = cache;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Public content generation (V090 {@code seo_content_state}); part of every cache key. */
    public long contentGeneration() {
        Long value = jdbc.queryForObject("SELECT generation FROM seo_content_state WHERE singleton_id = 1", Long.class);
        return value == null ? 0 : value;
    }

    /** 404 PROJECT_NOT_FOUND, 410 PROJECT_GONE (locked by staff). */
    public ProjectPage project(String slug) {
        if (slug == null || slug.length() > 200) throw projectNotFound();
        ProjectPage page = cache.getOrCompute("seo-project", "project:" + contentGeneration() + ":" + slug, TTL, ProjectPage.class,
                () -> store.projectBySlug(slug).map(p -> p.locked() ? new ProjectPage(p, List.of(), null)
                        : new ProjectPage(publicProject(p), store.amenities(p.id()), inventory(store.projectInventory(p.id())))).orElse(null));
        if (page == null) throw projectNotFound();
        if (page.project().locked()) throw new ApiException(HttpStatus.GONE, "PROJECT_GONE", "Dự án không còn hiển thị công khai.");
        return page;
    }

    public AreaPage area(String slug) {
        if (slug == null || slug.length() > 120) throw areaNotFound();
        AreaPage page = cache.getOrCompute("seo-area", "area:" + contentGeneration() + ":" + slug, TTL, AreaPage.class,
                () -> store.areaBySlug(slug).map(a -> new AreaPage(a, inventory(store.areaInventory(a.provinceCode(), a.districtCode())),
                        store.projectPage(a.districtCode(), 0, 12))).orElse(null));
        if (page == null) throw areaNotFound();
        return page;
    }

    public ProjectList projects(String districtCode, int page, int size) {
        int safePage = Math.max(0, Math.min(page, 200));
        int safeSize = Math.max(1, Math.min(size, 48));
        String district = districtCode == null || !districtCode.matches("\\d{3}") ? null : districtCode;
        return cache.getOrCompute("seo-projects", "projects:" + contentGeneration() + ":" + district + ":" + safePage + ":" + safeSize,
                TTL, ProjectList.class, () -> new ProjectList(store.projectPage(district, safePage, safeSize),
                        store.projectCount(district), safePage, safeSize));
    }

    public AreaList areas() {
        return cache.getOrCompute("seo-areas", "areas:" + contentGeneration(), TTL, AreaList.class,
                () -> new AreaList(store.areas(), clock.instant()));
    }

    /** Areas and projects that have public listings now (no invented numbers: zero-listing entries are left out). */
    public Home home() {
        return cache.getOrCompute("seo-home", "home:" + contentGeneration(), TTL, Home.class, () -> new Home(
                store.areas().stream().filter(a -> a.activeListings() > 0).limit(8).toList(),
                store.topProjects(6).stream().filter(p -> p.activeListings() > 0).toList(),
                clock.instant()));
    }

    public List<PublicCatalogStore.SitemapEntry> projectSitemap(int limit) { return store.projectSitemap(limit); }

    public List<PublicCatalogStore.SitemapEntry> areaSitemap() { return store.areaSitemap(); }

    /** Staff: public description, sources and amenities (each with a source and a check date); optional status. */
    @Transactional
    public ProjectRow updatePublicProfile(UUID projectId, ProfileInput input) {
        ProjectRow project = store.projectById(projectId)
                .orElseThrow(() -> ApiException.notFound("PROJECT_NOT_FOUND", "Không tìm thấy dự án."));
        String status = input.status() == null || input.status().isBlank() ? null : input.status().trim();
        if (status != null && !PROJECT_STATUSES.contains(status)) throw ApiException.badRequest("PROJECT_STATUS_INVALID", "Trạng thái dự án không hợp lệ.");
        String website = url(input.websiteUrl(), "PROJECT_WEBSITE_INVALID");
        List<AmenityInput> raw = input.amenities() == null ? List.of() : input.amenities();
        if (raw.size() > 50) throw ApiException.badRequest("PROJECT_AMENITIES_TOO_MANY", "Tối đa 50 tiện ích.");
        LocalDate today = LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.ofHours(7));
        List<Amenity> amenities = new ArrayList<>();
        for (AmenityInput a : raw) {
            String name = text(a.name(), 150);
            String source = text(a.sourceName(), 255);
            if (name == null || source == null || a.checkedAt() == null) {
                throw ApiException.badRequest("PROJECT_AMENITY_SOURCE_REQUIRED", "Mỗi tiện ích cần tên, nguồn và ngày kiểm tra.");
            }
            if (a.checkedAt().isAfter(today)) throw ApiException.badRequest("PROJECT_AMENITY_DATE_INVALID", "Ngày kiểm tra không được ở tương lai.");
            if (a.category() == null || !AMENITY_CATEGORIES.contains(a.category())) {
                throw ApiException.badRequest("PROJECT_AMENITY_CATEGORY_INVALID", "Loại tiện ích không hợp lệ.");
            }
            if (a.distanceM() != null && (a.distanceM() < 0 || a.distanceM() > 100_000)) {
                throw ApiException.badRequest("PROJECT_AMENITY_DISTANCE_INVALID", "Khoảng cách phải từ 0 đến 100 000 m.");
            }
            amenities.add(new Amenity(null, name, a.category(), a.distanceM(), source, url(a.sourceUrl(), "PROJECT_AMENITY_SOURCE_URL_INVALID"), a.checkedAt()));
        }
        if (input.infoCheckedAt() != null && input.infoCheckedAt().isAfter(today)) {
            throw ApiException.badRequest("PROJECT_INFO_DATE_INVALID", "Ngày kiểm tra thông tin không được ở tương lai.");
        }
        String description = text(input.description(), 5000);
        store.replacePublicProfile(project.id(), description == null ? null : ContactInfoGuard.redact(description), website,
                text(input.infoSource(), 255), input.infoCheckedAt(), status, amenities);
        return store.projectById(projectId).orElseThrow();
    }

    public List<Amenity> amenities(UUID projectId) { return store.amenities(projectId); }

    /** Staff view of a project's public profile (any status). */
    public ProjectRow updatePublicProfileView(UUID projectId) {
        return store.projectById(projectId).orElseThrow(() -> ApiException.notFound("PROJECT_NOT_FOUND", "Không tìm thấy dự án."));
    }

    Inventory inventory(List<InventoryRow> rows) {
        List<Statistic> statistics = new ArrayList<>();
        List<TypeCount> types = new ArrayList<>();
        long total = 0;
        Instant last = null;
        for (InventoryRow row : rows) {
            if (row.propertyType() == null) {
                total += row.count();
                boolean enough = row.count() >= MIN_SAMPLES;
                boolean sale = "SALE".equals(row.purpose());
                statistics.add(new Statistic(row.purpose(), row.count(),
                        enough && row.median() != null ? (double) Math.round(row.median()) : null,
                        sale ? "VND_PER_M2" : "VND_PER_MONTH", MIN_SAMPLES, sale ? METHOD_SALE : METHOD_RENT));
                if (row.lastUpdated() != null && (last == null || row.lastUpdated().isAfter(last))) last = row.lastUpdated();
            } else {
                types.add(new TypeCount(row.purpose(), row.propertyType(), row.count()));
            }
        }
        return new Inventory(total, statistics, types, last, clock.instant());
    }

    /** Free text a seller or staff typed can carry contact details; the public page never shows them. */
    private static ProjectRow publicProject(ProjectRow p) {
        return new ProjectRow(p.id(), p.slug(), p.name(), p.developerName(), p.provinceCode(), p.districtCode(), p.districtName(),
                p.areaSlug(), p.address(), p.totalAreaM2(), p.totalBlocks(), p.totalUnits(), p.handoverYear(), p.legalLicenseNumber(),
                p.status(), p.description() == null ? null : ContactInfoGuard.redact(p.description()), p.websiteUrl(), p.infoSource(),
                p.infoCheckedAt(), p.updatedAt());
    }

    private static String text(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static String url(String value, String code) {
        String url = text(value, 1000);
        if (url == null) return null;
        if (!(url.startsWith("https://") || url.startsWith("http://")) || url.chars().anyMatch(c -> c < 0x20 || c == '"' || c == '<' || c == '>')) {
            throw ApiException.badRequest(code, "Đường dẫn phải bắt đầu bằng https:// hoặc http://.");
        }
        return url;
    }

    private static ApiException projectNotFound() { return ApiException.notFound("PROJECT_NOT_FOUND", "Không tìm thấy dự án."); }

    private static ApiException areaNotFound() { return ApiException.notFound("AREA_NOT_FOUND", "Không tìm thấy khu vực."); }
}
