package com.company.bds.shared.mail;

import com.company.bds.shared.jobs.ClaimedJob;
import com.company.bds.shared.jobs.JobBatchResult;
import com.company.bds.shared.jobs.JobErrors;
import com.company.bds.shared.jobs.JobHandler;
import com.company.bds.shared.security.PiiProtectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * Sends queued e-mails ({@link MailOutbox}) through SMTP. Retryable failures (connection problems, SMTP 4xx) are retried
 * with the queue's backoff for about a day ({@link #maxAttempts()}); permanent ones (SMTP 5xx, an address JavaMail cannot
 * use, an envelope that cannot be decrypted) and messages past their {@code notAfter} are dead-lettered at once. The sealed
 * envelope is removed from the row when the job completes or is dead-lettered.
 */
@Component
public class EmailJobHandler implements JobHandler {
    static final String SEALED = "sealed";
    private static final Logger log = LoggerFactory.getLogger(EmailJobHandler.class);

    private final JavaMailSender mailSender;
    private final PiiProtectionService pii;
    private final ObjectMapper json;
    private final Clock clock;
    private final String from;

    public EmailJobHandler(JavaMailSender mailSender, PiiProtectionService pii, ObjectMapper json, Clock clock,
                           @Value("${app.mail.from:no-reply@bds.local}") String from) {
        this.mailSender = mailSender;
        this.pii = pii;
        this.json = json;
        this.clock = clock;
        this.from = from;
    }

    @Override public String queue() { return MailOutbox.QUEUE; }

    @Override public int batchSize() { return 10; }

    /** Ten sends with 5 s SMTP connect/read/write timeouts fit well inside the 4 minute budget (80 % of the lease). */
    @Override public Duration lease() { return Duration.ofMinutes(5); }

    /** 30 attempts with 30 s·2ⁿ backoff capped at 1 h ≈ 23 hours: a day-long SMTP outage still delivers. */
    @Override public int maxAttempts() { return 30; }

    @Override public Set<String> sensitivePayloadKeys() { return Set.of(SEALED); }

    @Override
    public JobBatchResult handle(List<ClaimedJob> jobs) {
        JobBatchResult.Builder result = JobBatchResult.builder();
        for (ClaimedJob job : jobs) {
            String category = job.text("category");
            Instant notAfter = notAfter(job);
            if (notAfter != null && clock.instant().isAfter(notAfter)) {
                result.failPermanently(job, "Expired before delivery (valid until " + notAfter + ")");
                log.warn("email_expired job={} category={} recipient={} not_after={}", job.id(), category, job.text("recipient"), notAfter);
                continue;
            }
            JsonNode envelope;
            try {
                envelope = json.readTree(pii.unseal(job.text(SEALED), MailOutbox.SEAL_PURPOSE));
            } catch (Exception unreadable) { // wrong key, tampered or scrubbed payload: retrying cannot help
                result.failPermanently(job, "Envelope cannot be decrypted: " + JobErrors.describe(unreadable));
                log.error("email_envelope_unreadable job={} category={}", job.id(), category);
                continue;
            }
            try {
                mailSender.send(toMime(envelope));
                result.succeed(job);
                log.info("email_sent job={} category={} recipient={} attempts={}", job.id(), category, job.text("recipient"), job.attempts() + 1);
            } catch (Exception ex) {
                String reason = JobErrors.describe(ex);
                boolean permanent = SmtpFailures.isPermanent(ex);
                if (permanent) result.failPermanently(job, reason);
                else result.fail(job, reason);
                log.warn("email_send_failed job={} category={} recipient={} attempts={} permanent={} error={}",
                        job.id(), category, job.text("recipient"), job.attempts() + 1, permanent, reason);
            }
        }
        return result.build();
    }

    private static Instant notAfter(ClaimedJob job) {
        String value = job.text("notAfter");
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private MimeMessage toMime(JsonNode envelope) throws Exception {
        MimeMessage mime = mailSender.createMimeMessage();
        boolean html = envelope.hasNonNull("html");
        MimeMessageHelper helper = new MimeMessageHelper(mime, html, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(envelope.path("to").asText());
        helper.setSubject(envelope.path("subject").asText());
        if (html) helper.setText(envelope.path("text").asText(), envelope.path("html").asText());
        else helper.setText(envelope.path("text").asText());
        envelope.path("headers").fields().forEachRemaining(header -> {
            try {
                mime.addHeader(header.getKey(), header.getValue().asText());
            } catch (jakarta.mail.MessagingException ex) {
                throw new IllegalStateException("Invalid e-mail header " + header.getKey(), ex);
            }
        });
        return mime;
    }
}
