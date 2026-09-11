package com.company.bds.transaction.infrastructure.persistence.entity;

import com.company.bds.transaction.domain.model.DepositStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "deposit_contracts")
public class DepositContractJpaEntity {

    @Id
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "buyer_name", nullable = false, length = 150)
    private String buyerName;

    @Column(name = "buyer_phone", nullable = false, length = 20)
    private String buyerPhone;

    @Column(name = "buyer_id_masked", nullable = false, length = 20)
    private String buyerIdMasked;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "seller_name", nullable = false, length = 150)
    private String sellerName;

    @Column(name = "seller_phone", nullable = false, length = 20)
    private String sellerPhone;

    @Column(name = "deposit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "listing_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal listingPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DepositStatus status;

    @Column(name = "terms_conditions", nullable = false, columnDefinition = "TEXT")
    private String termsConditions;

    @Column(name = "buyer_signed_at")
    private Instant buyerSignedAt;

    @Column(name = "buyer_otp_verified", nullable = false)
    private boolean buyerOtpVerified;

    @Column(name = "seller_signed_at")
    private Instant sellerSignedAt;

    @Column(name = "seller_otp_verified", nullable = false)
    private boolean sellerOtpVerified;

    @Column(name = "escrow_locked_at")
    private Instant escrowLockedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "dispute_reason", columnDefinition = "TEXT")
    private String disputeReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EscrowTransactionJpaEntity> escrowTransactions = new ArrayList<>();

    public DepositContractJpaEntity() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }
    public UUID getBuyerId() { return buyerId; }
    public void setBuyerId(UUID buyerId) { this.buyerId = buyerId; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public String getBuyerPhone() { return buyerPhone; }
    public void setBuyerPhone(String buyerPhone) { this.buyerPhone = buyerPhone; }
    public String getBuyerIdMasked() { return buyerIdMasked; }
    public void setBuyerIdMasked(String buyerIdMasked) { this.buyerIdMasked = buyerIdMasked; }
    public UUID getSellerId() { return sellerId; }
    public void setSellerId(UUID sellerId) { this.sellerId = sellerId; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public String getSellerPhone() { return sellerPhone; }
    public void setSellerPhone(String sellerPhone) { this.sellerPhone = sellerPhone; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public BigDecimal getListingPrice() { return listingPrice; }
    public void setListingPrice(BigDecimal listingPrice) { this.listingPrice = listingPrice; }
    public DepositStatus getStatus() { return status; }
    public void setStatus(DepositStatus status) { this.status = status; }
    public String getTermsConditions() { return termsConditions; }
    public void setTermsConditions(String termsConditions) { this.termsConditions = termsConditions; }
    public Instant getBuyerSignedAt() { return buyerSignedAt; }
    public void setBuyerSignedAt(Instant buyerSignedAt) { this.buyerSignedAt = buyerSignedAt; }
    public boolean isBuyerOtpVerified() { return buyerOtpVerified; }
    public void setBuyerOtpVerified(boolean buyerOtpVerified) { this.buyerOtpVerified = buyerOtpVerified; }
    public Instant getSellerSignedAt() { return sellerSignedAt; }
    public void setSellerSignedAt(Instant sellerSignedAt) { this.sellerSignedAt = sellerSignedAt; }
    public boolean isSellerOtpVerified() { return sellerOtpVerified; }
    public void setSellerOtpVerified(boolean sellerOtpVerified) { this.sellerOtpVerified = sellerOtpVerified; }
    public Instant getEscrowLockedAt() { return escrowLockedAt; }
    public void setEscrowLockedAt(Instant escrowLockedAt) { this.escrowLockedAt = escrowLockedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getDisputeReason() { return disputeReason; }
    public void setDisputeReason(String disputeReason) { this.disputeReason = disputeReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<EscrowTransactionJpaEntity> getEscrowTransactions() { return escrowTransactions; }
    public void setEscrowTransactions(List<EscrowTransactionJpaEntity> escrowTransactions) { this.escrowTransactions = escrowTransactions; }
}
