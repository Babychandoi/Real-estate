package com.company.bds.catalog.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Thực thể Dự án Bất động sản (Aggregate Root) theo chuẩn FR25
 */
public class Project {
    private final UUID id;
    private String name;
    private String slug;
    private String developerName;
    private String provinceCode;
    private String districtCode;
    private String address;
    private BigDecimal totalAreaM2;
    private int totalBlocks;
    private int totalUnits;
    private Integer handoverYear;
    private String legalLicenseNumber;
    private ProjectStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Project(
            UUID id,
            String name,
            String slug,
            String developerName,
            String provinceCode,
            String districtCode,
            String address,
            BigDecimal totalAreaM2,
            int totalBlocks,
            int totalUnits,
            Integer handoverYear,
            String legalLicenseNumber,
            ProjectStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.name = name;
        this.slug = slug;
        this.developerName = developerName;
        this.provinceCode = provinceCode;
        this.districtCode = districtCode;
        this.address = address;
        this.totalAreaM2 = totalAreaM2;
        this.totalBlocks = totalBlocks;
        this.totalUnits = totalUnits;
        this.handoverYear = handoverYear;
        this.legalLicenseNumber = legalLicenseNumber;
        this.status = status != null ? status : ProjectStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Project create(
            String name,
            String slug,
            String developerName,
            String provinceCode,
            String districtCode,
            String address,
            BigDecimal totalAreaM2,
            int totalBlocks,
            int totalUnits,
            Integer handoverYear,
            String legalLicenseNumber
    ) {
        Instant now = Instant.now();
        return new Project(
                UUID.randomUUID(),
                name,
                slug,
                developerName,
                provinceCode,
                districtCode,
                address,
                totalAreaM2,
                totalBlocks,
                totalUnits,
                handoverYear,
                legalLicenseNumber,
                ProjectStatus.ACTIVE,
                now,
                now
        );
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDeveloperName() { return developerName; }
    public String getProvinceCode() { return provinceCode; }
    public String getDistrictCode() { return districtCode; }
    public String getAddress() { return address; }
    public BigDecimal getTotalAreaM2() { return totalAreaM2; }
    public int getTotalBlocks() { return totalBlocks; }
    public int getTotalUnits() { return totalUnits; }
    public Integer getHandoverYear() { return handoverYear; }
    public String getLegalLicenseNumber() { return legalLicenseNumber; }
    public ProjectStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
