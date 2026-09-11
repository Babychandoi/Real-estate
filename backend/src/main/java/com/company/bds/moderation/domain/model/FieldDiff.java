package com.company.bds.moderation.domain.model;

/**
 * Đại diện cho sự so sánh giữa 2 phiên bản của một trường dữ liệu.
 */
public record FieldDiff(
        String fieldName,
        String fieldLabel,
        String oldValue,
        String newValue,
        boolean isChanged
) {
    public static FieldDiff of(String fieldName, String fieldLabel, Object oldValue, Object newValue) {
        String oldStr = oldValue != null ? String.valueOf(oldValue) : "";
        String newStr = newValue != null ? String.valueOf(newValue) : "";
        boolean changed = !oldStr.equals(newStr);
        return new FieldDiff(fieldName, fieldLabel, oldStr, newStr, changed);
    }
}
