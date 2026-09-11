package com.company.bds.lead.domain.model;

public enum ReportCategory {
    SCAM_DEPOSIT,    // Lừa cọc, chuyển khoản giữ chỗ
    FAKE_SOLD,       // Tin ảo, căn đã bán nhưng vẫn treo câu view
    INCORRECT_PRICE, // Sai giá, giá chênh lệch ảo
    OTHER            // Vi phạm khác
}
