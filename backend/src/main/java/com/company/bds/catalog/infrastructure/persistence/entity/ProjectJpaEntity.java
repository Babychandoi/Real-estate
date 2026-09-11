package com.company.bds.catalog.infrastructure.persistence.entity;

import com.company.bds.catalog.domain.model.ProjectStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectJpaEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, length = 200, unique = true)
    private String slug;

    @Column(name = "developer_name", nullable = false, length = 150)
    private String developerName;

    @Column(name = "province_code", nullable = false, length = 50)
    private String provinceCode;

    @Column(name = "district_code", nullable = false, length = 50)
    private String districtCode;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "total_area_m2", precision = 12, scale = 2)
    private BigDecimal totalAreaM2;

    @Column(name = "total_blocks", nullable = false)
    private int totalBlocks;

    @Column(name = "total_units", nullable = false)
    private int totalUnits;

    @Column(name = "handover_year")
    private Integer handoverYear;

    @Column(name = "legal_license_number", nullable = false, length = 100)
    private String legalLicenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ProjectStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectJpaEntity() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDeveloperName() { return developerName; }
    public void setDeveloperName(String developerName) { this.developerName = developerName; }
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }
    public String getDistrictCode() { return districtCode; }
    public void setDistrictCode(String districtCode) { this.districtCode = districtCode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public BigDecimal getTotalAreaM2() { return totalAreaM2; }
    public void setTotalAreaM2(BigDecimal totalAreaM2) { this.totalAreaM2 = totalAreaM2; }
    public int getTotalBlocks() { return totalBlocks; }
    public void setTotalBlocks(int totalBlocks) { this.totalBlocks = totalBlocks; }
    public int getTotalUnits() { return totalUnits; }
    public void setTotalUnits(int totalUnits) { this.totalUnits = totalUnits; }
    public Integer getHandoverYear() { return handoverYear; }
    public void setHandoverYear(Integer handoverYear) { this.handoverYear = handoverYear; }
    public String getLegalLicenseNumber() { return legalLicenseNumber; }
    public void setLegalLicenseNumber(String legalLicenseNumber) { this.legalLicenseNumber = legalLicenseNumber; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
