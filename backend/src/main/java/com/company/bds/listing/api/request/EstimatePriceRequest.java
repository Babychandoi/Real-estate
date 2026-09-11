package com.company.bds.listing.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class EstimatePriceRequest {

    @NotBlank(message = "Loại BĐS không được để trống")
    private String propertyType;

    private String districtCode;
    private String provinceCode;

    @NotNull(message = "Diện tích không được để trống")
    @DecimalMin(value = "5.0", message = "Diện tích tối thiểu phải từ 5m2")
    private BigDecimal areaM2;

    public EstimatePriceRequest() {}

    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
    public String getDistrictCode() { return districtCode; }
    public void setDistrictCode(String districtCode) { this.districtCode = districtCode; }
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }
    public BigDecimal getAreaM2() { return areaM2; }
    public void setAreaM2(BigDecimal areaM2) { this.areaM2 = areaM2; }
}
