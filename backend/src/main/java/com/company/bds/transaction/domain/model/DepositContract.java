package com.company.bds.transaction.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Hợp đồng Đặt cọc BĐS Trực tuyến (Aggregate Root)
 */
public class DepositContract {
    private final UUID id;
    private final UUID listingId;
    private final UUID buyerId;
    private final String buyerName;
    private final String buyerPhone;
    private final String buyerIdMasked;
    private final UUID sellerId;
    private final String sellerName;
    private final String sellerPhone;
    private final BigDecimal depositAmount;
    private final BigDecimal listingPrice;
    private DepositStatus status;
    private final String termsConditions;
    private Instant buyerSignedAt;
    private boolean buyerOtpVerified;
    private Instant sellerSignedAt;
    private boolean sellerOtpVerified;
    private Instant escrowLockedAt;
    private Instant completedAt;
    private String disputeReason;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<EscrowTransaction> escrowTransactions;

    public DepositContract(
            UUID id,
            UUID listingId,
            UUID buyerId,
            String buyerName,
            String buyerPhone,
            String buyerIdMasked,
            UUID sellerId,
            String sellerName,
            String sellerPhone,
            BigDecimal depositAmount,
            BigDecimal listingPrice,
            DepositStatus status,
            String termsConditions,
            Instant buyerSignedAt,
            boolean buyerOtpVerified,
            Instant sellerSignedAt,
            boolean sellerOtpVerified,
            Instant escrowLockedAt,
            Instant completedAt,
            String disputeReason,
            Instant createdAt,
            Instant updatedAt,
            List<EscrowTransaction> escrowTransactions
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.listingId = listingId;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.buyerPhone = buyerPhone;
        this.buyerIdMasked = buyerIdMasked;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.sellerPhone = sellerPhone;
        this.depositAmount = depositAmount;
        this.listingPrice = listingPrice;
        this.status = status != null ? status : DepositStatus.DRAFT;
        this.termsConditions = termsConditions;
        this.buyerSignedAt = buyerSignedAt;
        this.buyerOtpVerified = buyerOtpVerified;
        this.sellerSignedAt = sellerSignedAt;
        this.sellerOtpVerified = sellerOtpVerified;
        this.escrowLockedAt = escrowLockedAt;
        this.completedAt = completedAt;
        this.disputeReason = disputeReason;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        this.escrowTransactions = escrowTransactions != null ? new ArrayList<>(escrowTransactions) : new ArrayList<>();
    }

    public static DepositContract create(
            UUID listingId,
            UUID buyerId,
            String buyerName,
            String buyerPhone,
            String buyerIdMasked,
            UUID sellerId,
            String sellerName,
            String sellerPhone,
            BigDecimal depositAmount,
            BigDecimal listingPrice,
            String termsConditions
    ) {
        Instant now = Instant.now();
        return new DepositContract(
                UUID.randomUUID(),
                listingId,
                buyerId,
                buyerName,
                buyerPhone,
                buyerIdMasked,
                sellerId,
                sellerName,
                sellerPhone,
                depositAmount,
                listingPrice,
                DepositStatus.DRAFT,
                termsConditions,
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                now,
                now,
                new ArrayList<>()
        );
    }

    /**
     * Người mua ký số bằng OTP
     */
    public void signByBuyer(boolean validOtp) {
        if (!validOtp) {
            throw new IllegalArgumentException("Mã xác thực OTP người mua không hợp lệ");
        }
        if (this.status != DepositStatus.DRAFT) {
            throw new IllegalStateException("Hợp đồng không ở trạng thái nháp để người mua ký");
        }
        this.buyerOtpVerified = true;
        this.buyerSignedAt = Instant.now();
        this.status = DepositStatus.AWAITING_SELLER_SIGN;
        this.updatedAt = Instant.now();
        addEscrowTransaction(EscrowAction.DEPOSIT, this.depositAmount, this.buyerId, "Người mua ký hợp đồng cọc & nạp tiền ký quỹ");
    }

    /**
     * Người bán ký số bằng OTP và chính thức phong tỏa tiền cọc trong Escrow Vault
     */
    public void signBySeller(boolean validOtp) {
        if (!validOtp) {
            throw new IllegalArgumentException("Mã xác thực OTP người bán không hợp lệ");
        }
        if (this.status != DepositStatus.AWAITING_SELLER_SIGN) {
            throw new IllegalStateException("Hợp đồng chưa sẵn sàng để người bán ký");
        }
        this.sellerOtpVerified = true;
        this.sellerSignedAt = Instant.now();
        this.escrowLockedAt = Instant.now();
        this.status = DepositStatus.ESCROW_LOCKED;
        this.updatedAt = Instant.now();
        addEscrowTransaction(EscrowAction.LOCK, this.depositAmount, this.sellerId, "Người bán xác nhận ký cọc - Kích hoạt phong tỏa Escrow bảo đảm");
    }

    /**
     * Giải ngân tiền cọc cho người bán khi hoàn tất công chứng chuyển nhượng
     */
    public void releaseEscrow(UUID adminOrBuyerId) {
        if (this.status != DepositStatus.ESCROW_LOCKED) {
            throw new IllegalStateException("Tiền cọc chưa được phong tỏa bảo đảm để giải ngân");
        }
        this.status = DepositStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
        addEscrowTransaction(EscrowAction.RELEASE, this.depositAmount, adminOrBuyerId, "Giải ngân tiền cọc thành công cho người bán sau khi công chứng");
    }

    /**
     * Hoàn tiền cọc cho người mua do vi phạm hoặc thỏa thuận chấm dứt
     */
    public void refundEscrow(UUID adminId, String reason) {
        if (this.status != DepositStatus.ESCROW_LOCKED && this.status != DepositStatus.AWAITING_SELLER_SIGN && this.status != DepositStatus.DISPUTED) {
            throw new IllegalStateException("Hợp đồng không đủ điều kiện để hoàn cọc");
        }
        this.status = DepositStatus.REFUNDED;
        this.disputeReason = reason;
        this.updatedAt = Instant.now();
        addEscrowTransaction(EscrowAction.REFUND, this.depositAmount, adminId, "Hoàn trả tiền cọc cho người mua: " + reason);
    }

    /**
     * Ghi nhận tranh chấp cọc
     */
    public void dispute(UUID reporterId, String reason) {
        if (this.status != DepositStatus.ESCROW_LOCKED) {
            throw new IllegalStateException("Chỉ hợp đồng đang phong tỏa mới có thể khiếu nại tranh chấp");
        }
        this.status = DepositStatus.DISPUTED;
        this.disputeReason = reason;
        this.updatedAt = Instant.now();
        addEscrowTransaction(EscrowAction.DISPUTE, this.depositAmount, reporterId, "Khiếu nại tranh chấp: " + reason);
    }

    private void addEscrowTransaction(EscrowAction action, BigDecimal amount, UUID performedBy, String note) {
        EscrowTransaction tx = new EscrowTransaction(
                UUID.randomUUID(),
                this.id,
                action,
                amount,
                performedBy,
                note,
                Instant.now()
        );
        this.escrowTransactions.add(tx);
    }

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
    public List<EscrowTransaction> getEscrowTransactions() { return Collections.unmodifiableList(escrowTransactions); }
}
