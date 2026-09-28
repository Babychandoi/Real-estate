package com.company.bds.notification;

import org.springframework.lang.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A notification to deliver (contract §11). {@code link} is an in-app path ({@code /listings/…}); anything that is not a
 * same-site relative path is dropped. {@code dedupeKey} makes the notification once-only per user and business fact.
 * {@code email} asks for an e-mail copy as well; it is sent only when the user's preference for the category allows it
 * and the account has a verified address.
 */
public record NotificationRequest(UUID userId, String type, String title, String message, @Nullable String link,
                                  @Nullable String dedupeKey, boolean email) {
    public static final int MAX_TITLE = 180;
    public static final int MAX_MESSAGE = 600;
    public static final int MAX_LINK = 300;
    public static final int MAX_DEDUPE_KEY = 160;
    private static final Pattern TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,49}");
    /** A path on this site: one leading slash (never {@code //host} or {@code /\host}), no scheme, no spaces or quotes. */
    private static final Pattern SAFE_LINK = Pattern.compile("/(?![/\\\\])[A-Za-z0-9\\-._~/?=&%+]*");

    public NotificationRequest {
        Objects.requireNonNull(userId, "userId");
        if (type == null || !TYPE.matcher(type).matches()) throw new IllegalArgumentException("Invalid notification type");
        title = clip(Objects.requireNonNull(title, "title"), MAX_TITLE);
        message = clip(Objects.requireNonNull(message, "message"), MAX_MESSAGE);
        link = link != null && link.length() <= MAX_LINK && SAFE_LINK.matcher(link).matches() ? link : null;
        if (dedupeKey != null && (dedupeKey.isBlank() || dedupeKey.length() > MAX_DEDUPE_KEY)) {
            throw new IllegalArgumentException("Invalid dedupe key");
        }
    }

    public static NotificationRequest of(UUID userId, String type, String title, String message) {
        return new NotificationRequest(userId, type, title, message, null, null, false);
    }

    public NotificationRequest withLink(@Nullable String value) {
        return new NotificationRequest(userId, type, title, message, value, dedupeKey, email);
    }

    public NotificationRequest withDedupeKey(@Nullable String value) {
        return new NotificationRequest(userId, type, title, message, link, value, email);
    }

    public NotificationRequest withEmail(boolean value) {
        return new NotificationRequest(userId, type, title, message, link, dedupeKey, value);
    }

    private static String clip(String value, int max) {
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + "…";
    }
}
