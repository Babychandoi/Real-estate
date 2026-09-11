package com.company.bds.transaction.api.response;

import com.company.bds.transaction.domain.model.DepositContract;
import com.company.bds.transaction.domain.model.DepositStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DepositContractResponse {
    private UUID id;
    private UUID listingId;
    private UUID buyerId;
    private String buyerName;
    private String buyerPhone;
    private String buyerIdMasked;
    private UUID sellerId;
    private String sellerName;
    private String sellerPhone;
    private BigDecimal depositAmount;
    private BigDecimal listingPrice;
    private DepositStatus status;
    private String termsConditions;
    private Instant buyerSignedAt;
    private boolean buyerOtpVerified;
    private Instant sellerSignedAt;
    private boolean sellerOtpVerified;
    private Instant escrowLockedAt;
    private Instant completedAt;
    private String disputeReason;
    private Instant createdAt;
    private Instant updatedAt;
    private List<EscrowTransactionResponse> escrowTransactions;

    public DepositContractResponse() {}

    public static DepositContractResponse fromDomain(DepositContract domain) {
        DepositContractResponse res = new DepositContractResponse();
        res.id = domain.getId();
        res.listingId = domain.getListingId();
        res.buyerId = domain.getBuyerId();
        res.buyerName = domain.getBuyerName();
        res.buyerPhone = domain.getBuyerPhone();
        res.buyerIdMasked = domain.getBuyerIdMasked();
        res.sellerId = domain.getSellerId();
        res.sellerName = domain.getSellerName();
        res.sellerPhone = domain.getSellerPhone();
        res.depositAmount = domain.getDepositAmount();
        res.listingPrice = domain.getListingPrice();
        res.status = domain.getStatus();
        res.termsConditions = domain.getTermsConditions();
        res.buyerSignedAt = domain.getBuyerSignedAt();
        res.buyerOtpVerified = domain.isBuyerOtpVerified();
        res.sellerSignedAt = domain.getSellerSignedAt();
        res.sellerOtpVerified = domain.isSellerOtpVerified();
        res.escrowLockedAt = domain.getEscrowLockedAt();
        res.completedAt = domain.getCompletedAt();
        res.disputeReason = domain.getDisputeReason();
        res.createdAt = domain.getCreatedAt();
        res.updatedAt = domain.getUpdatedAt();

        if (domain.getEscrowTransactions() != null) {
            res.escrowTransactions = domain.getEscrowTransactions().stream()
                    .map(tx -> new EscrowTransactionResponse(
                            tx.getId(),
                            tx.getAction(),
                            tx.getAmount(),
                            tx.getPerformedBy(),
                            tx.getNote(),
                            tx.getCreatedAt()
                    ))
                    .collect(Collectors.toList());
        }
        return res;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public UUID getBuyerId() { return buyerId; }
    public String getBuyerName() { return buyerName; }
    public String getBuyerPhone() { return buyerPhone; }
    public String getBuyerIdMasked() { return buyerIdMasked; }
    public UUID getSellerId() { return sellerId; }
    public String getSellerName() { return sellerName; }
    public String getSellerPhone() { return sellerPhone; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public BigDecimal getListingPrice() { return listingPrice; }
    public DepositStatus getStatus() { return status; }
    public String getTermsConditions() { return termsConditions; }
    public Instant getBuyerSignedAt() { return buyerSignedAt; }
    public boolean isBuyerOtpVerified() { return buyerOtpVerified; }
    public Instant getSellerSignedAt() { return sellerSignedAt; }
    public boolean isSellerOtpVerified() { return sellerOtpVerified; }
    public Instant getEscrowLockedAt() { return escrowLockedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getDisputeReason() { return disputeReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<EscrowTransactionResponse> getEscrowTransactions() { return escrowTransactions; }
}
