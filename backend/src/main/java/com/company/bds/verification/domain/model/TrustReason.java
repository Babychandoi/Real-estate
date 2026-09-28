package com.company.bds.verification.domain.model;

/** Reason codes of identity (KYC) and ownership decisions; each decision kind accepts only its own codes. */
public enum TrustReason {
    DOCUMENTS_MATCH(Kind.APPROVE, "Giấy tờ rõ ràng, thông tin trùng khớp"),
    NAME_MISMATCH(Kind.REJECT, "Họ tên trên giấy tờ không khớp với hồ sơ định danh"),
    DOCUMENT_UNREADABLE(Kind.REJECT, "Ảnh giấy tờ mờ, bị che hoặc thiếu mặt"),
    DOCUMENT_EXPIRED(Kind.REJECT, "Giấy tờ đã hết hiệu lực"),
    CERTIFICATE_MISMATCH(Kind.REJECT, "Số giấy chứng nhận không khớp hoặc đã dùng cho tin khác"),
    ADDRESS_MISMATCH(Kind.REJECT, "Địa chỉ trên giấy tờ không khớp với tin đăng"),
    SUSPECTED_FORGERY(Kind.REJECT, "Nghi vấn giấy tờ bị chỉnh sửa"),
    OTHER_REJECT(Kind.REJECT, "Lý do khác (xem ghi chú)"),
    DISPUTE(Kind.REVOKE, "Có tranh chấp hoặc khiếu nại về quyền sở hữu"),
    OWNERSHIP_CHANGED(Kind.REVOKE, "Quyền sở hữu đã thay đổi"),
    FRAUD_CONFIRMED(Kind.REVOKE, "Xác minh gian lận"),
    OWNER_REQUEST(Kind.REVOKE, "Người đăng yêu cầu thu hồi");

    public enum Kind { APPROVE, REJECT, REVOKE }

    private final Kind kind;
    private final String vietnameseLabel;

    TrustReason(Kind kind, String vietnameseLabel) {
        this.kind = kind;
        this.vietnameseLabel = vietnameseLabel;
    }

    public Kind getKind() { return kind; }

    public String getVietnameseLabel() { return vietnameseLabel; }

    /** Parses a code of the expected kind; {@code null}/blank falls back to the kind's default. */
    public static TrustReason parse(String code, Kind kind) {
        if (code == null || code.isBlank()) {
            return switch (kind) {
                case APPROVE -> DOCUMENTS_MATCH;
                case REJECT -> OTHER_REJECT;
                case REVOKE -> null;
            };
        }
        for (TrustReason reason : values()) {
            if (reason.name().equals(code.trim()) && reason.kind == kind) return reason;
        }
        return null;
    }
}
