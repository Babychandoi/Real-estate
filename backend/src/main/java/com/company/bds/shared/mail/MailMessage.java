package com.company.bds.shared.mail;

import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * An e-mail queued through {@link MailOutbox} (contract §3.4). {@code category} names the business reason (for metrics
 * and support, e.g. {@code PASSWORD_RESET}); {@code dedupeKey} makes the message once-only per business fact.
 */
public record MailMessage(String to, String subject, String textBody, @Nullable String htmlBody, String category,
                          @Nullable String dedupeKey, Map<String, String> headers) {

    public MailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(textBody, "textBody");
        Objects.requireNonNull(category, "category");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static MailMessage text(String to, String subject, String textBody, String category, @Nullable String dedupeKey) {
        return new MailMessage(to, subject, textBody, null, category, dedupeKey, Map.of());
    }
}
