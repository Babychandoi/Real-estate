package com.company.bds.notification.domain;

import java.util.Locale;

/**
 * What a notification is about; preferences are chosen per category. {@code mandatoryInApp} categories (account,
 * billing, trust decisions and the owner's own listings) always reach the notification centre: they carry decisions the
 * user has to know about. E-mail is only ever sent for a notification whose producer asked for it
 * ({@code NotificationRequest.email}) and whose category allows it for this user.
 */
public enum NotificationCategory {
    ACCOUNT(true, true, true),
    LISTINGS(true, true, true),
    LEADS(false, true, true),
    ALERTS(false, true, true),
    SAVED_LISTINGS(false, true, false),
    SHORTLIST(false, true, false);

    private final boolean mandatoryInApp;
    private final boolean defaultInApp;
    private final boolean defaultEmail;

    NotificationCategory(boolean mandatoryInApp, boolean defaultInApp, boolean defaultEmail) {
        this.mandatoryInApp = mandatoryInApp;
        this.defaultInApp = defaultInApp;
        this.defaultEmail = defaultEmail;
    }

    public boolean mandatoryInApp() { return mandatoryInApp; }

    public boolean defaultInApp() { return defaultInApp; }

    public boolean defaultEmail() { return defaultEmail; }

    /** Category of a notification type code; the same mapping as the V068 backfill. */
    public static NotificationCategory fromType(String type) {
        String t = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (t.startsWith("SAVED_SEARCH")) return ALERTS;
        if (t.startsWith("SAVED_LISTING")) return SAVED_LISTINGS;
        if (t.startsWith("SHORTLIST")) return SHORTLIST;
        if (t.startsWith("LEAD") || t.startsWith("APPOINTMENT")) return LEADS;
        if (t.startsWith("LISTING") || t.startsWith("OWNERSHIP")) return LISTINGS;
        return ACCOUNT;
    }

    /** @throws IllegalArgumentException for an unknown value */
    public static NotificationCategory parse(String value) {
        if (value == null) throw new IllegalArgumentException("Unknown notification category");
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown notification category");
        }
    }
}
