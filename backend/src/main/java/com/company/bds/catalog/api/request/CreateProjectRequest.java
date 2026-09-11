package com.company.bds.catalog.api.request;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public class CreateProjectRequest {

    @NotBlank(message = "Tên dự án không được để trống")
    private String name;

    @NotBlank(message = "Tên chủ đầu tư không được để trống")
    private String developerName;

    private String provinceCode = "HN";

    @NotBlank(message = "Mã quận/huyện không được để trống")
    private String districtCode;

    @NotBlank(message = "Địa chỉ chi tiết không được để trống")
    private String address;

    private BigDecimal totalAreaM2;
    private int totalBlocks = 1;
    private int totalUnits = 0;
    private Integer handoverYear;

    @NotBlank(message = "Số Giấy phép xây dựng/Quy hoạch 1/500 không được để trống (FR25)")
    private String legalLicenseNumber;

    public CreateProjectRequest() {}

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
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
}
