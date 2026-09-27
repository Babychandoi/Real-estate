package com.company.bds.shared.mail;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * An e-mail queued through {@link MailOutbox} (contract §3.4). {@code category} names the business reason (for metrics
 * and support, e.g. {@code PASSWORD_RESET}); {@code dedupeKey} makes the message once-only per business fact;
 * {@code notAfter} (optional) is when the message stops being useful, typically the expiry of the one-time link it
 * carries: the outbox never sends it later, it dead-letters it instead.
 */
public record MailMessage(String to, String subject, String textBody, @Nullable String htmlBody, String category,
                          @Nullable String dedupeKey, Map<String, String> headers, @Nullable Instant notAfter) {

    public MailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(textBody, "textBody");
        Objects.requireNonNull(category, "category");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /** The contract's constructor (no expiry). */
    public MailMessage(String to, String subject, String textBody, @Nullable String htmlBody, String category,
                       @Nullable String dedupeKey, Map<String, String> headers) {
        this(to, subject, textBody, htmlBody, category, dedupeKey, headers, null);
    }

    public static MailMessage text(String to, String subject, String textBody, String category, @Nullable String dedupeKey) {
        return new MailMessage(to, subject, textBody, null, category, dedupeKey, Map.of(), null);
    }

    public MailMessage withNotAfter(@Nullable Instant value) {
        return new MailMessage(to, subject, textBody, htmlBody, category, dedupeKey, headers, value);
    }
}
