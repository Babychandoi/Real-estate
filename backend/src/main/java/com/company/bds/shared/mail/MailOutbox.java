package com.company.bds.shared.mail;

import com.company.bds.shared.jobs.JobQueue;
import com.company.bds.shared.security.PiiProtectionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * it with retries. Recipient, subject and bodies are sealed (AES-GCM) because they contain personal data and one-time links.
 */
@Component
public class MailOutbox {
    public static final String QUEUE = "email";
    static final String SEAL_PURPOSE = "bds-mail-outbox-v1";
    private static final Pattern ADDRESS = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern CATEGORY = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
    private static final Pattern HEADER_NAME = Pattern.compile("X-[A-Za-z0-9-]{1,60}");
    private static final Set<String> RESERVED_HEADERS = Set.of("from", "to", "cc", "bcc", "subject", "reply-to", "sender");

    private final JobQueue jobs;
    private final PiiProtectionService pii;
    private final ObjectMapper json;

    public MailOutbox(JobQueue jobs, PiiProtectionService pii, ObjectMapper json) {
        this.jobs = jobs;
        this.pii = pii;
        this.json = json;
    }

    /**
     * Queues the message. With a dedupe key the message is sent at most once (while the completed job is retained,
     * {@code app.jobs.completed-retention}).
     *
     * @return the job id, or empty when a message with the same dedupe key was already queued or sent
     */
    public Optional<UUID> enqueue(MailMessage message) {
        validate(message);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("to", message.to().trim());
        envelope.put("subject", message.subject());
        envelope.put("text", message.textBody());
        if (message.htmlBody() != null) envelope.put("html", message.htmlBody());
        envelope.put("headers", message.headers());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("v", 1);
        payload.put("category", message.category());
        payload.put("recipient", maskAddress(message.to().trim()));
        payload.put("sealed", pii.seal(serialize(envelope), SEAL_PURPOSE));
        if (message.dedupeKey() != null) return jobs.enqueueOnce(QUEUE, message.dedupeKey(), payload, null);
        return Optional.of(jobs.enqueue(QUEUE, null, payload, null));
    }

    /** {@code nguyenvana@gmail.com} → {@code ng***@gmail.com}: enough for support, useless for harvesting. */
    static String maskAddress(String address) {
        int at = address.indexOf('@');
        if (at <= 0) return "***";
        return address.substring(0, Math.min(2, at)) + "***" + address.substring(at).toLowerCase(Locale.ROOT);
    }

    private static void validate(MailMessage message) {
        if (!ADDRESS.matcher(message.to().trim()).matches()) throw new IllegalArgumentException("Địa chỉ email người nhận không hợp lệ.");
        if (message.subject().isBlank() || message.subject().length() > 250 || message.subject().matches("(?s).*[\\r\\n].*")) {
            throw new IllegalArgumentException("Tiêu đề email không hợp lệ.");
        }
        if (message.textBody().isBlank()) throw new IllegalArgumentException("Email cần có nội dung văn bản.");
        if (!CATEGORY.matcher(message.category()).matches()) throw new IllegalArgumentException("Loại email không hợp lệ.");
        message.headers().forEach((name, value) -> {
            if (!HEADER_NAME.matcher(name).matches() || RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))
                    || value == null || value.matches("(?s).*[\\r\\n].*") || value.length() > 500) {
                throw new IllegalArgumentException("Header email không hợp lệ: " + name);
            }
        });
    }

    private String serialize(Map<String, Object> envelope) {
        try {
            return json.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể đóng gói email", ex);
        }
    }
}
