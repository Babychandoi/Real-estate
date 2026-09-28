package com.company.bds.shared.jobs;

import java.util.regex.Pattern;

/** Makes handler errors safe to store in {@code background_jobs.last_error} and to log: no e-mail or phone numbers. */
public final class JobErrors {
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    /** Vietnamese mobile/landline numbers with optional separators; SMTP status codes such as "550 5.1.1" are kept. */
    private static final Pattern PHONE = Pattern.compile("(?<![\\d+])(?:\\+?84|0)(?:[ .-]?\\d){8,10}(?!\\d)");
    private static final int MAX_LENGTH = 500;

    private JobErrors() {}

    public static String describe(Throwable error) {
        String message = error.getMessage();
        String text = error.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        return sanitize(text);
    }

    public static String sanitize(String text) {
        if (text == null || text.isBlank()) return "Job failed";
        String masked = PHONE.matcher(EMAIL.matcher(text).replaceAll("<email>")).replaceAll("<phone>");
        String singleLine = masked.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= MAX_LENGTH ? singleLine : singleLine.substring(0, MAX_LENGTH);
    }
}
