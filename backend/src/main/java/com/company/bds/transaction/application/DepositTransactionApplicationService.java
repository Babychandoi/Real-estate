package com.company.bds.transaction.application;

import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.transaction.domain.model.DepositContract;
import com.company.bds.transaction.infrastructure.persistence.port.DepositContractPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DepositTransactionApplicationService {

    private final DepositContractPersistencePort depositContractPort;
    private final ListingPersistencePort listingPersistencePort;
    private final boolean realTransactionsEnabled;

    public DepositTransactionApplicationService(
            DepositContractPersistencePort depositContractPort,
            ListingPersistencePort listingPersistencePort,
            @Value("${app.features.real-transactions:false}") boolean realTransactionsEnabled
    ) {
        this.depositContractPort = depositContractPort;
        this.listingPersistencePort = listingPersistencePort;
        this.realTransactionsEnabled = realTransactionsEnabled;
    }

    public DepositContract createContract(
            UUID listingId,
            UUID buyerId,
            String buyerName,
            String buyerPhone,
            String buyerIdNumber,
            BigDecimal depositAmount,
            String termsConditions
    ) {
        requireProvider();
        Listing listing = listingPersistencePort.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tin đăng BĐS với mã: " + listingId));

        BigDecimal listingPrice = listing.getLatestRevision()
                .map(rev -> BigDecimal.valueOf(rev.getPriceVnd()))
                .orElse(BigDecimal.ZERO);

        // Mask PII số CCCD của người mua theo NFR12 (ví dụ 001****4567)
        String maskedBuyerId = maskIdNumber(buyerIdNumber);

        String defaultTerms = termsConditions != null && !termsConditions.isBlank()
                ? termsConditions
                : "Hai bên thống nhất đặt cọc số tiền qua tài khoản Ký quỹ Escrow bảo đảm của BDS WF 2026. "
                + "Tiền cọc sẽ được phong tỏa bảo đảm cho tới khi hai bên ký kết xong hợp đồng chuyển nhượng tại phòng công chứng. "
                + "Trường hợp bên bán không bán sẽ phạt cọc 100%, bên mua không mua sẽ mất tiền cọc theo quy định pháp luật.";

        DepositContract contract = DepositContract.create(
                listingId,
                buyerId,
                buyerName,
                buyerPhone,
                maskedBuyerId,
                listing.getOwnerId(),
                "Chủ sở hữu tin đăng BĐS",
                "0988889999",
                depositAmount,
                listingPrice,
                defaultTerms
        );

        return depositContractPort.save(contract);
    }

    public DepositContract signByBuyer(UUID contractId, String otpCode) {
        requireProvider();
        DepositContract contract = depositContractPort.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hợp đồng đặt cọc: " + contractId));

        // Kiểm tra mã OTP (cho phép 6 số bất kỳ hoặc mã mặc định 123456 trong sandbox)
        boolean isValidOtp = otpCode != null && otpCode.trim().length() == 6;
        contract.signByBuyer(isValidOtp);
        return depositContractPort.save(contract);
    }

    public DepositContract signBySeller(UUID contractId, String otpCode) {
        requireProvider();
        DepositContract contract = depositContractPort.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hợp đồng đặt cọc: " + contractId));

        boolean isValidOtp = otpCode != null && otpCode.trim().length() == 6;
        contract.signBySeller(isValidOtp);
        return depositContractPort.save(contract);
    }

    public DepositContract releaseEscrow(UUID contractId, UUID operatorId) {
        requireProvider();
        DepositContract contract = depositContractPort.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hợp đồng đặt cọc: " + contractId));

        contract.releaseEscrow(operatorId);
        return depositContractPort.save(contract);
    }

    public DepositContract refundEscrow(UUID contractId, UUID operatorId, String reason) {
        requireProvider();
        DepositContract contract = depositContractPort.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hợp đồng đặt cọc: " + contractId));

        contract.refundEscrow(operatorId, reason != null ? reason : "Bên bán vi phạm cam kết pháp lý hoặc thỏa thuận hủy cọc");
        return depositContractPort.save(contract);
    }

    public DepositContract disputeEscrow(UUID contractId, UUID operatorId, String reason) {
        requireProvider();
        DepositContract contract = depositContractPort.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hợp đồng đặt cọc: " + contractId));

        contract.dispute(operatorId, reason != null ? reason : "Phát hiện dấu hiệu gian lận hoặc sai lệch giấy tờ");
        return depositContractPort.save(contract);
    }

    @Transactional(readOnly = true)
    public DepositContract getContract(UUID contractId) {
        return depositContractPort.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hợp đồng đặt cọc: " + contractId));
    }

    @Transactional(readOnly = true)
    public List<DepositContract> getContractsByListing(UUID listingId, int page, int size) {
        return depositContractPort.findByListingId(listingId, page, size);
    }

    @Transactional(readOnly = true)
    public List<DepositContract> getContractsByBuyer(UUID buyerId) {
        return depositContractPort.findByBuyerId(buyerId);
    }

    private String maskIdNumber(String idNumber) {
        if (idNumber == null || idNumber.length() < 6) {
            return "***";
        }
        int len = idNumber.length();
        return idNumber.substring(0, 3) + "****" + idNumber.substring(len - 4);
    }

    private void requireProvider() {
        if (!realTransactionsEnabled) {
            throw new UnsupportedOperationException("Giao dịch tiền và ký số đang tắt: chưa cấu hình nhà cung cấp được cấp phép.");
        }
    }
}
