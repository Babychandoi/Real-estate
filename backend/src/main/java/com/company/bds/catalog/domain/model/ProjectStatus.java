package com.company.bds.catalog.domain.model;

/**
 * Trạng thái của Dự án BĐS (FR25)
 */
public enum ProjectStatus {
    ACTIVE,              // Đang mở bán / thứ cấp giao dịch
    PLANNING,            // Đang quy hoạch 1/500
    UNDER_CONSTRUCTION,  // Đang thi công xây dựng
    COMPLETED,           // Đã bàn giao
    LOCKED               // Khóa do vi phạm pháp lý
}
