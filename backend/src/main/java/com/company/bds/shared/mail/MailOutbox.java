package com.company.bds.shared.mail;

import com.company.bds.shared.jobs.JobQueue;
import com.company.bds.shared.security.PiiProtectionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Durable e-mail outbox: {@link #enqueue} writes a job on queue {@value #QUEUE} in the caller's transaction, so the message
 * exists exactly when the business change commits and SMTP never runs inside a transaction. {@link EmailJobHandler} sends
 * it with retries. Recipient, subject and bodies are sealed (AES-GCM) because they contain personal data and one-time
 * links; the sealed envelope is removed from the row as soon as the message is sent or given up.
 *
 * <p>Business flows that must not fail because of a mail problem use {@link #tryEnqueue}: an unusable message is logged
 * and counted ({@code bds.mail.rejected{category,reason}}) instead of throwing, so it never rolls back the caller.
 */
@Component
public class MailOutbox {
    public static final String QUEUE = "email";
    static final String SEAL_PURPOSE = "bds-mail-outbox-v1";
    private static final Logger log = LoggerFactory.getLogger(MailOutbox.class);
    private static final Pattern CATEGORY = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
    private static final Pattern HEADER_NAME = Pattern.compile("X-[A-Za-z0-9-]{1,60}");
    private static final Set<String> RESERVED_HEADERS = Set.of("from", "to", "cc", "bcc", "subject", "reply-to", "sender");

    public enum Outcome { QUEUED, DUPLICATE, REJECTED }

    /** Result of {@link #tryEnqueue}: the job id when queued, the reason when rejected. */
    public record Result(Outcome outcome, Optional<UUID> jobId, Optional<String> rejectionReason) {
        static Result queued(UUID id) { return new Result(Outcome.QUEUED, Optional.of(id), Optional.empty()); }
        static Result duplicate() { return new Result(Outcome.DUPLICATE, Optional.empty(), Optional.empty()); }
        static Result rejected(String reason) { return new Result(Outcome.REJECTED, Optional.empty(), Optional.of(reason)); }
    }

    private final JobQueue jobs;
    private final PiiProtectionService pii;
    private final ObjectMapper json;
    private final MailAddressValidator addresses;
    private final MeterRegistry meters;

    public MailOutbox(JobQueue jobs, PiiProtectionService pii, ObjectMapper json, MailAddressValidator addresses, MeterRegistry meters) {
        this.jobs = jobs;
        this.pii = pii;
        this.json = json;
        this.addresses = addresses;
        this.meters = meters;
    }

    /**
     * Queues the message. With a dedupe key the message is sent at most once (while the completed job is retained,
     * {@code app.jobs.completed-retention}).
     *
     * @return the job id, or empty when a message with the same dedupe key was already queued or sent
     * @throws IllegalArgumentException when the message is unusable (invalid address, subject, category or header)
     */
    public Optional<UUID> enqueue(MailMessage message) {
        String problem = problem(message);
        if (problem != null) throw new IllegalArgumentException(problem);
        return store(message);
    }

    /** Like {@link #enqueue} but never throws for an unusable message: it is logged (masked recipient) and counted. */
    public Result tryEnqueue(MailMessage message) {
        String problem = problem(message);
        if (problem != null) {
            meters.counter("bds.mail.rejected", "category", safeCategory(message.category()), "reason", problem).increment();
            log.warn("mail_rejected category={} recipient={} reason={}", safeCategory(message.category()),
                    maskAddress(message.to()), problem);
            return Result.rejected(problem);
        }
        return store(message).map(Result::queued).orElseGet(Result::duplicate);
    }

    private Optional<UUID> store(MailMessage message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("to", message.to());
        envelope.put("subject", message.subject());
        envelope.put("text", message.textBody());
        if (message.htmlBody() != null) envelope.put("html", message.htmlBody());
        envelope.put("headers", message.headers());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("v", 1);
        payload.put("category", message.category());
        payload.put("recipient", maskAddress(message.to()));
        if (message.notAfter() != null) payload.put("notAfter", message.notAfter().toString());
        payload.put(EmailJobHandler.SEALED, pii.seal(serialize(envelope), SEAL_PURPOSE));
        if (message.dedupeKey() != null) return jobs.enqueueOnce(QUEUE, message.dedupeKey(), payload, null);
        return Optional.of(jobs.enqueue(QUEUE, null, payload, null));
    }

    /** {@code nguyenvana@gmail.com} → {@code ng***@gmail.com}: enough for support, useless for harvesting. */
    static String maskAddress(String address) {
        if (address == null) return "***";
        int at = address.lastIndexOf('@');
        if (at <= 0) return "***";
        return address.substring(0, Math.min(2, at)) + "***" + address.substring(at).toLowerCase(Locale.ROOT);
    }

    /** Null when the message can be queued, otherwise a short machine-readable reason. */
    private String problem(MailMessage message) {
        if (!addresses.isValid(message.to())) return "invalid_address";
        if (message.subject().isBlank() || message.subject().length() > 250 || message.subject().matches("(?s).*[\\r\\n].*")) {
            return "invalid_subject";
        }
        if (message.textBody().isBlank()) return "empty_body";
        if (!CATEGORY.matcher(message.category()).matches()) return "invalid_category";
        for (Map.Entry<String, String> header : message.headers().entrySet()) {
            String name = header.getKey();
            String value = header.getValue();
            if (!HEADER_NAME.matcher(name).matches() || RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))
                    || value == null || value.matches("(?s).*[\\r\\n].*") || value.length() > 500) {
                return "invalid_header";
            }
        }
        return null;
    }

    private static String safeCategory(String category) {
        return category != null && CATEGORY.matcher(category).matches() ? category : "INVALID";
    }

    private String serialize(Map<String, Object> envelope) {
        try {
            return json.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể đóng gói email", ex);
        }
    }
}
