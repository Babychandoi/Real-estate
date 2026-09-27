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
import java.time.Duration;
import java.util.List;

/** Sends queued e-mails ({@link MailOutbox}) through SMTP; failures are retried by the job queue with backoff. */
@Component
public class EmailJobHandler implements JobHandler {
    private static final Logger log = LoggerFactory.getLogger(EmailJobHandler.class);

    private final JavaMailSender mailSender;
    private final PiiProtectionService pii;
    private final ObjectMapper json;
    private final String from;

    public EmailJobHandler(JavaMailSender mailSender, PiiProtectionService pii, ObjectMapper json,
                           @Value("${app.mail.from:no-reply@bds.local}") String from) {
        this.mailSender = mailSender;
        this.pii = pii;
        this.json = json;
        this.from = from;
    }

    @Override public String queue() { return MailOutbox.QUEUE; }

    @Override public int batchSize() { return 10; }

    /** Ten sends with 5 s SMTP timeouts fit well inside the lease. */
    @Override public Duration lease() { return Duration.ofMinutes(3); }

    @Override
    public JobBatchResult handle(List<ClaimedJob> jobs) {
        JobBatchResult.Builder result = JobBatchResult.builder();
        for (ClaimedJob job : jobs) {
            String category = job.text("category");
            try {
                JsonNode envelope = json.readTree(pii.unseal(job.text("sealed"), MailOutbox.SEAL_PURPOSE));
                mailSender.send(toMime(envelope));
                result.succeed(job);
                log.info("email_sent job={} category={} recipient={} attempts={}", job.id(), category, job.text("recipient"), job.attempts() + 1);
            } catch (Exception ex) {
                String reason = JobErrors.describe(ex);
                result.fail(job, reason);
                log.warn("email_send_failed job={} category={} recipient={} attempts={} error={}",
                        job.id(), category, job.text("recipient"), job.attempts() + 1, reason);
            }
        }
        return result.build();
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
