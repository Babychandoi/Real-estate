package com.company.bds.moderation.domain.model;

/**
 * Danh mục lý do từ chối kiểm duyệt chuẩn hóa theo FR10 & UC03.
 */
public enum StandardModerationReason {
    INCORRECT_PRICE("Mức giá không phản ánh thực tế hoặc nghi vấn giá ảo", "Tài chính"),
    PRICE_UNREALISTIC("Mức giá sai lệch bất thường so với phân khúc thị trường", "Tài chính"),
    INVALID_PAPERS("Hình ảnh giấy tờ pháp lý không rõ nét hoặc không khớp thông tin", "Pháp lý"),
    WATERMARK_VIOLATION("Hình ảnh chứa số điện thoại hoặc watermark của nền tảng khác", "Hình ảnh"),
    DUPLICATE_LISTING("Tin đăng bị trùng lặp với bất động sản đã tồn tại trên hệ thống", "Nội dung"),
    INCOMPLETE_INFO("Thông tin mô tả không đầy đủ hoặc địa chỉ hành chính không chính xác", "Nội dung");

    private final String vietnameseLabel;
    private final String category;

    StandardModerationReason(String vietnameseLabel, String category) {
        this.vietnameseLabel = vietnameseLabel;
        this.category = category;
    }

    public String getVietnameseLabel() {
        return vietnameseLabel;
    }

    public String getCategory() {
        return category;
    }

    public String getDefaultDescription() {
        return vietnameseLabel;
    }
}
