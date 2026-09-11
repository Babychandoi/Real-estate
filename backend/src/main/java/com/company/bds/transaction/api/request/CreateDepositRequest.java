package com.company.bds.transaction.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public class CreateDepositRequest {

    @NotNull(message = "Listing ID không được để trống")
    private UUID listingId;

    private UUID buyerId;

    @NotBlank(message = "Họ tên người mua không được để trống")
    private String buyerName;

    @NotBlank(message = "Số điện thoại người mua không được để trống")
    private String buyerPhone;

    @NotBlank(message = "Số CCCD người mua không được để trống")
    private String buyerIdNumber;

    @NotNull(message = "Số tiền cọc không được để trống")
    @DecimalMin(value = "1000000.00", message = "Tiền đặt cọc tối thiểu là 1,000,000 VNĐ")
    private BigDecimal depositAmount;

    private String termsConditions;

    public CreateDepositRequest() {}

    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }
    public UUID getBuyerId() { return buyerId; }
    public void setBuyerId(UUID buyerId) { this.buyerId = buyerId; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public String getBuyerPhone() { return buyerPhone; }
    public void setBuyerPhone(String buyerPhone) { this.buyerPhone = buyerPhone; }
    public String getBuyerIdNumber() { return buyerIdNumber; }
    public void setBuyerIdNumber(String buyerIdNumber) { this.buyerIdNumber = buyerIdNumber; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public String getTermsConditions() { return termsConditions; }
    public void setTermsConditions(String termsConditions) { this.termsConditions = termsConditions; }
}
