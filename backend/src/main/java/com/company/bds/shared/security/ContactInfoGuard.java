package com.company.bds.shared.security;

import java.util.regex.Pattern;

/**
 * Keeps direct contact details (phone numbers, emails, messenger links) out of public user content, so every
 * contact between people goes through the platform's lead flow.
 */
public final class ContactInfoGuard {
    public static final String REDACTED = "[đã ẩn liên hệ]";
    public static final String REJECTION = "Không ghi số điện thoại, email hoặc liên kết liên hệ (Zalo, Facebook…) trong nội dung. "
            + "Người quan tâm sẽ liên hệ qua nút “Hẹn xem & nhận tư vấn” của hệ thống.";

    private static final String SEP = "[\\s.\\-]?";
    /** Vietnamese mobile (0/84/+84 then 3,5,7,8,9 + 8 digits) and landline (02x + 8 digits), separators allowed. */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\d.,])(?:\\+?84" + SEP + "|0)(?:[35789](?:" + SEP + "\\d){8}|2(?:" + SEP + "\\d){9})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+\\s*(?:@|\\(at\\)|\\[at\\])\\s*[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK = Pattern.compile(
            "(?:https?://\\S+|www\\.\\S+|\\b(?:zalo\\.me|facebook\\.com|fb\\.com|fb\\.me|m\\.me|t\\.me|wa\\.me|messenger\\.com)/?\\S*)",
            Pattern.CASE_INSENSITIVE);

    private ContactInfoGuard() {}

    public static boolean containsContact(String text) {
        return text != null && (PHONE.matcher(text).find() || EMAIL.matcher(text).find() || LINK.matcher(text).find());
    }

    /** Throws the user-facing rejection when any of the given texts carries contact details. */
    public static void requireNoContact(String... texts) {
        for (String text : texts) {
            if (containsContact(text)) throw new IllegalArgumentException(REJECTION);
        }
    }

    /** Masks contact details in already stored content before it is shown publicly. */
    public static String redact(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = LINK.matcher(text).replaceAll(REDACTED);
        result = EMAIL.matcher(result).replaceAll(REDACTED);
        return PHONE.matcher(result).replaceAll(REDACTED);
    }
}
