package com.company.bds.catalog.api.response;

import com.company.bds.catalog.domain.model.Project;
import com.company.bds.catalog.domain.model.ProjectStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ProjectResponse {
    private UUID id;
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
    private Instant createdAt;
    private Instant updatedAt;

    public ProjectResponse() {}

    public static ProjectResponse fromDomain(Project domain) {
        ProjectResponse res = new ProjectResponse();
        res.id = domain.getId();
        res.name = domain.getName();
        res.slug = domain.getSlug();
        res.developerName = domain.getDeveloperName();
        res.provinceCode = domain.getProvinceCode();
        res.districtCode = domain.getDistrictCode();
        res.address = domain.getAddress();
        res.totalAreaM2 = domain.getTotalAreaM2();
        res.totalBlocks = domain.getTotalBlocks();
        res.totalUnits = domain.getTotalUnits();
        res.handoverYear = domain.getHandoverYear();
        res.legalLicenseNumber = domain.getLegalLicenseNumber();
        res.status = domain.getStatus();
        res.createdAt = domain.getCreatedAt();
        res.updatedAt = domain.getUpdatedAt();
        return res;
    }

    // Getters
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
