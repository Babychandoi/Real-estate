package com.company.bds.transaction.domain.model;

/**
 * Trạng thái vòng đời của Hợp đồng Đặt cọc BĐS Trực tuyến (FR28, FR30, UC05)
 */
public enum DepositStatus {
    DRAFT,                // Bản nháp thỏa thuận cọc, chờ người mua xác nhận OTP
    AWAITING_SELLER_SIGN, // Người mua đã ký và nạp cọc, chờ người bán ký xác nhận
    ESCROW_LOCKED,        // Hai bên đã ký xong, tiền cọc đang được phong tỏa bảo đảm trong Escrow Vault
    COMPLETED,            // Hoàn tất công chứng chuyển nhượng, giải ngân tiền cọc cho người bán
    REFUNDED,             // Hủy giao dịch hợp lệ hoặc tin vi phạm, hoàn trả tiền cọc cho người mua
    DISPUTED              // Đang có tranh chấp, giữ phong tỏa chờ trọng tài / pháp lý xử lý
}
