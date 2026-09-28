package com.company.bds.moderation.domain.model;

/** Reason codes a moderator records when approving (or passing a random audit): every decision says why. */
public enum ApprovalReason {
    MEETS_STANDARDS("Nội dung, hình ảnh và giá đạt tiêu chuẩn đăng tin"),
    MINOR_EDIT("Chỉnh sửa nhỏ, không thay đổi thông tin quan trọng của bất động sản"),
    CONFIRMED_WITH_POSTER("Đã xác nhận lại thông tin với người đăng");

    private final String vietnameseLabel;

    ApprovalReason(String vietnameseLabel) { this.vietnameseLabel = vietnameseLabel; }

    public String getVietnameseLabel() { return vietnameseLabel; }

    public static boolean isValid(String code) {
        for (ApprovalReason reason : values()) if (reason.name().equals(code)) return true;
        return false;
    }
}
