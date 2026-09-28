package com.company.bds.shared.observability;

import java.util.regex.Pattern;

/**
 * Last line of defence against PII and secrets in logs (audit F22.2, PROJECT_CODE_RULES §12–13). Code must not log
 * PII in the first place; this masks what slips through anyway — e-mail addresses, Vietnamese phone numbers,
 * bearer tokens and {@code password=/token=/secret=} pairs — in every string a log line carries (message, MDC values,
 * exception messages and stack traces).
 */
public final class PiiLogMasker {
    private static final Pattern EMAIL = Pattern.compile(
            "(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");
    /** +84/84/0 followed by nine digits (mobile) or ten (landline), optionally grouped by spaces, dots or dashes. */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\w+.-])(?:\\+84|84|0)(?:[ .-]?\\d){9,10}(?![\\w-])");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern SECRET_PAIR = Pattern.compile(
            "(?i)\\b(password|passwd|secret|token|otp|api[_-]?key)(\\s*[=:]\\s*)(\"?)[^\\s\",&;]+");

    private PiiLogMasker() {}

    public static String mask(String value) {
        if (value == null || value.isEmpty()) return value;
        String masked = BEARER.matcher(value).replaceAll("Bearer [REDACTED]");
        masked = SECRET_PAIR.matcher(masked).replaceAll("$1$2$3[REDACTED]");
        masked = EMAIL.matcher(masked).replaceAll("[EMAIL]");
        masked = PHONE.matcher(masked).replaceAll("[PHONE]");
        return masked;
    }
}
