package com.company.bds.catalog.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read side of the public project and area pages (audit P-06). Inventory and statistics are computed on the public
 * listing read model ({@code listing_public_read}, seller ACTIVE), never on write-side entities.
 */
public interface PublicCatalogStore {

    record ProjectRow(UUID id, String slug, String name, String developerName, String provinceCode, String districtCode,
                      String districtName, String areaSlug, String address, BigDecimal totalAreaM2, int totalBlocks,
                      int totalUnits, Integer handoverYear, String legalLicenseNumber, String status, String description,
                      String websiteUrl, String infoSource, LocalDate infoCheckedAt, Instant updatedAt) {
        public boolean locked() { return "LOCKED".equals(status); }
    }

    record Amenity(UUID id, String name, String category, Integer distanceM, String sourceName, String sourceUrl,
                   LocalDate checkedAt) {}

    record AreaRow(String provinceCode, String districtCode, String provinceName, String name, String slug) {}

    /** One statistics bucket: {@code propertyType} null = every type of the purpose. {@code median} null below the sample minimum is decided by the service. */
    record InventoryRow(String purpose, String propertyType, long count, Double median, Instant lastUpdated) {}

    record ProjectCard(String slug, String name, String districtName, String areaSlug, String status, long activeListings) {}

    record AreaCard(String slug, String name, String provinceName, String districtCode, long activeListings) {}

    record SitemapEntry(String slug, Instant lastModified) {}

    Optional<ProjectRow> projectBySlug(String slug);

    List<Amenity> amenities(UUID projectId);

    List<InventoryRow> projectInventory(UUID projectId);

    List<ProjectCard> projectPage(String districtCode, int page, int size);

    long projectCount(String districtCode);

    List<ProjectCard> topProjects(int limit);

    Optional<AreaRow> areaBySlug(String slug);

    List<InventoryRow> areaInventory(String provinceCode, String districtCode);

    /** Every area with its active listing count (areas without listings included), by count then name. */
    List<AreaCard> areas();

    List<SitemapEntry> projectSitemap(int limit);

    /** Areas with at least one public listing, lastmod = newest listing change. */
    List<SitemapEntry> areaSitemap();

    void replacePublicProfile(UUID projectId, String description, String websiteUrl, String infoSource,
                              LocalDate infoCheckedAt, String status, List<Amenity> amenities);

    Optional<ProjectRow> projectById(UUID id);
}
