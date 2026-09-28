package com.company.bds.shared.mail;

import com.company.bds.shared.jobs.JobWorker;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.FakeSmtpServer;
import com.company.bds.testsupport.MailpitClient;
import com.company.bds.testsupport.TestData;
import io.micrometer.core.instrument.MeterRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The test profile never polls the queue, so each test drives delivery with {@link JobWorker#drain} and checks Mailpit. */
@BdsIntegrationTest
class MailOutboxTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired JobWorker worker;
    @Autowired MailOutbox outbox;
    @Autowired JavaMailSenderImpl smtp;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MeterRegistry meters;

    private final MailpitClient mailpit = new MailpitClient();

    @Test
    void smtpOutageDoesNotBlockTheTransferReportAndTheAdminGetsExactlyOneEmailAfterRecovery() throws Exception {
        String adminEmail = MailpitClient.uniqueAddress("billing");
        jdbc.update("""
                INSERT INTO bank_settings(singleton_id,bank_bin,bank_name,account_number,account_name,admin_notification_email)
                VALUES (1,'970436','Ngân hàng kiểm thử','0123456789','CONG TY KIEM THU',?)
                ON CONFLICT (singleton_id) DO UPDATE SET admin_notification_email = EXCLUDED.admin_notification_email
                """, adminEmail);
        String token = data.sessionFor(data.user().role("BROKER").create().id());
        JsonNode order = json.readTree(mockMvc.perform(post("/api/v1/billing/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planCode\":\"STANDARD\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String orderId = order.get("id").asText();
        String reference = order.get("reference").asText();
        String dedupeKey = "billing-transfer-reported:" + orderId;

        int workingPort = smtp.getPort();
        smtp.setPort(closedPort());
        UUID jobId;
        try {
            mockMvc.perform(post("/api/v1/billing/orders/" + orderId + "/reported").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("TRANSFER_REPORTED"));
            assertThat(jdbc.queryForObject("SELECT status FROM package_orders WHERE id = CAST(? AS uuid)", String.class, orderId))
                    .isEqualTo("TRANSFER_REPORTED");
            jobId = jdbc.queryForObject("SELECT id FROM background_jobs WHERE queue = 'email' AND dedupe_key = ?", UUID.class, dedupeKey);
            assertThat(jdbc.queryForObject("SELECT payload::text FROM background_jobs WHERE id = ?", String.class, jobId))
                    .as("recipient and content are sealed").doesNotContain(adminEmail).doesNotContain(reference);

            worker.drain(MailOutbox.QUEUE);

            Map<String, Object> failed = jdbc.queryForMap("SELECT attempts, completed_at, last_error FROM background_jobs WHERE id = ?", jobId);
            assertThat(failed.get("attempts")).isEqualTo(1);
            assertThat(failed.get("completed_at")).isNull();
            assertThat((String) failed.get("last_error")).contains("Mail").doesNotContain(adminEmail);
            assertThat(mailpit.subjectsTo(adminEmail)).isEmpty();
        } finally {
            smtp.setPort(workingPort);
        }

        jdbc.update("UPDATE background_jobs SET run_at = now() WHERE id = ?", jobId); // the retry backoff has elapsed
        worker.drain(MailOutbox.QUEUE);

        assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM background_jobs WHERE id = ?", Boolean.class, jobId)).isTrue();
        String subject = "[Nhà Đất Chuẩn] Có thanh toán chờ đối soát " + reference;
        await().atMost(Duration.ofSeconds(10)).until(() -> mailpit.subjectsTo(adminEmail).size() == 1);
        assertThat(mailpit.subjectsTo(adminEmail)).containsExactly(subject);

        mockMvc.perform(post("/api/v1/billing/orders/" + orderId + "/reported").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        worker.drain(MailOutbox.QUEUE);
        assertThat(mailpit.subjectsTo(adminEmail)).containsExactly(subject);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_jobs WHERE dedupe_key = ?", Integer.class, dedupeKey)).isEqualTo(1);
    }

    @Test
    void verificationAndPasswordResetEmailsCarryWorkingOneTimeLinks() throws Exception {
        String email = MailpitClient.uniqueAddress("signup");
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Strong-Test-Password-2026!","name":"Người dùng email","accountType":"USER"}
                        """.formatted(email)))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM background_jobs j
                JOIN email_verification_tokens t ON j.dedupe_key = 'email-verification:' || t.id
                JOIN users u ON u.id = t.user_id
                WHERE u.email = ? AND j.queue = 'email' AND j.completed_at IS NULL
                """, Integer.class, email)).as("queued in the registration transaction").isEqualTo(1);
        assertThat(mailpit.subjectsTo(email)).isEmpty();

        worker.drain(MailOutbox.QUEUE);
        await().atMost(Duration.ofSeconds(10)).until(() -> mailpit.subjectsTo(email).contains("Xác minh tài khoản Nhà Đất Chuẩn"));
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", linkToken(email, "verify-email")))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE email = ?", String.class, email)).isEqualTo("ACTIVE");

        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
        worker.drain(MailOutbox.QUEUE);
        await().atMost(Duration.ofSeconds(10)).until(() -> mailpit.subjectsTo(email).contains("Đặt lại mật khẩu Nhà Đất Chuẩn"));
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("""
                        {"token":"%s","password":"Another-Strong-Password-2026!"}
                        """.formatted(linkToken(email, "reset-password"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Another-Strong-Password-2026!\"}".formatted(email)))
                .andExpect(status().isOk());
        assertThat(mailpit.subjectsTo(email)).hasSize(2);
    }

    @Test
    void messageWithAnAlreadyUsedDedupeKeyIsSentOnlyOnce() {
        String to = MailpitClient.uniqueAddress("dedupe");
        MailMessage message = MailMessage.text(to, "Thông báo kiểm thử một lần", "Nội dung kiểm thử.", "TEST_NOTICE",
                "test-notice:" + UUID.randomUUID());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Optional<UUID> first = transaction.execute(status -> outbox.enqueue(message));
        Optional<UUID> duplicate = transaction.execute(status -> outbox.enqueue(message));
        assertThat(first).isPresent();
        assertThat(duplicate).isEmpty();

        worker.drain(MailOutbox.QUEUE);
        await().atMost(Duration.ofSeconds(10)).until(() -> mailpit.subjectsTo(to).size() == 1);
        assertThat(jdbc.queryForMap("SELECT payload->>'sealed' AS sealed, payload->>'recipient' AS recipient, payload->>'category' AS category"
                + " FROM background_jobs WHERE id = ?", first.get()))
                .as("the sealed envelope is dropped once sent; masked recipient and category stay for support")
                .containsEntry("sealed", null).containsEntry("recipient", MailOutbox.maskAddress(to)).containsEntry("category", "TEST_NOTICE");
        assertThat(outbox.enqueue(message)).as("already sent").isEmpty();
        worker.drain(MailOutbox.QUEUE);
        assertThat(mailpit.subjectsTo(to)).containsExactly("Thông báo kiểm thử một lần");
    }

    @Test
    void rejectsMalformedAddressesSubjectsAndHeaderInjection() {
        assertThatThrownBy(() -> outbox.enqueue(MailMessage.text("not-an-address", "Tiêu đề", "Nội dung", "TEST_NOTICE", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outbox.enqueue(MailMessage.text("a@example.test", "Tiêu đề\r\nBcc: x@example.test", "Nội dung", "TEST_NOTICE", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outbox.enqueue(new MailMessage("a@example.test", "Tiêu đề", "Nội dung", null, "TEST_NOTICE", null,
                Map.of("Bcc", "x@example.test")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outbox.enqueue(MailMessage.text("a@example.test", "Tiêu đề", "Nội dung", "lowercase", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(MailOutbox.maskAddress("Nguyen.Van.A@Gmail.com")).isEqualTo("Ng***@gmail.com");
    }

    @Test
    void acceptsEveryAddressTheApiAcceptsAndTryEnqueueNeverThrows() {
        List<UUID> queued = new ArrayList<>();
        for (String address : List.of("o'brien@example.test", "a&b@example.test", "x@b.xn--p1ai", "u@localhost", "ke.toan&co@x.vn")) {
            MailOutbox.Result result = outbox.tryEnqueue(MailMessage.text(address, "Địa chỉ hợp lệ", "Nội dung.", "TEST_NOTICE", null));
            assertThat(result.outcome()).as(address).isEqualTo(MailOutbox.Outcome.QUEUED);
            queued.add(result.jobId().orElseThrow());
        }
        double before = rejectedCount("TEST_NOTICE");
        for (String address : List.of("Kế toán <ketoan@congty.vn>", "a@x.vn, b@x.vn", " padded@x.vn", "")) {
            MailOutbox.Result result = outbox.tryEnqueue(MailMessage.text(address, "Địa chỉ sai", "Nội dung.", "TEST_NOTICE", null));
            assertThat(result.outcome()).as(address).isEqualTo(MailOutbox.Outcome.REJECTED);
            assertThat(result.rejectionReason()).contains("invalid_address");
        }
        assertThat(rejectedCount("TEST_NOTICE") - before).isEqualTo(4.0);
        queued.forEach(id -> jdbc.update("DELETE FROM background_jobs WHERE id = ?", id));
    }

    @Test
    void forgotPasswordAnswersIdenticallyForUnknownKnownAndUnsendableAccounts() throws Exception {
        TestData.TestUser known = data.user().email(MailpitClient.uniqueAddress("known")).create();
        TestData.TestUser unsendable = data.user().email(MailpitClient.uniqueAddress("reject")).create();
        String unknown = MailpitClient.uniqueAddress("unknown");
        worker.drain(MailOutbox.QUEUE); // deliver other tests' leftovers through the real test SMTP first

        List<String> answers = new ArrayList<>();
        for (String email : List.of(unknown, known.email(), unsendable.email())) {
            MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\"}".formatted(email))).andReturn().getResponse();
            answers.add(response.getStatus() + " " + response.getContentAsString());
        }
        assertThat(answers).as("no difference an attacker could use").containsOnly("202 ");

        int workingPort = smtp.getPort();
        try (FakeSmtpServer server = new FakeSmtpServer(rcpt -> rcpt.contains("reject") ? "550 5.1.1 Mailbox unavailable" : "250 OK")) {
            smtp.setPort(server.port());
            worker.drain(MailOutbox.QUEUE);
        } finally {
            smtp.setPort(workingPort);
        }
        assertThat(resetJob(known.id())).containsEntry("done", true).containsEntry("dead", false);
        assertThat(resetJob(unsendable.id())).as("a mailbox the server refuses is given up at once, not retried for a day")
                .containsEntry("dead", true).containsEntry("attempts", 1).containsEntry("sealed", false);
    }

    @Test
    void permanentSmtpRejectionIsDeadLetteredAtOnceWhileATemporaryOneIsRetried() throws Exception {
        worker.drain(MailOutbox.QUEUE);
        UUID permanent = outbox.enqueue(MailMessage.text(MailpitClient.uniqueAddress("reject"), "Hộp thư không tồn tại", "Nội dung.",
                "TEST_NOTICE", null)).orElseThrow();
        UUID temporary = outbox.enqueue(MailMessage.text(MailpitClient.uniqueAddress("later"), "Máy chủ bận", "Nội dung.",
                "TEST_NOTICE", null)).orElseThrow();
        int workingPort = smtp.getPort();
        try (FakeSmtpServer server = new FakeSmtpServer(rcpt -> rcpt.contains("reject") ? "550 5.1.1 Recipient address rejected"
                : "451 4.7.1 Try again later")) {
            smtp.setPort(server.port());
            worker.drain(MailOutbox.QUEUE);
        } finally {
            smtp.setPort(workingPort);
        }
        try {
            assertThat(job(permanent)).containsEntry("dead", true).containsEntry("attempts", 1).containsEntry("sealed", false);
            assertThat((String) job(permanent).get("last_error")).contains("550 5.1.1").doesNotContain("@example.test");
            Map<String, Object> retried = job(temporary);
            assertThat(retried).containsEntry("dead", false).containsEntry("attempts", 1).containsEntry("sealed", true)
                    .containsEntry("due", false);
            assertThat((String) retried.get("last_error")).contains("451 4.7.1");
        } finally {
            jdbc.update("DELETE FROM background_jobs WHERE id = ?", temporary);
        }
    }

    @Test
    void messagePastItsValidityIsDeadLetteredInsteadOfSentLate() {
        String to = MailpitClient.uniqueAddress("expired");
        UUID id = outbox.enqueue(MailMessage.text(to, "Liên kết đặt lại mật khẩu", "Liên kết: /reset-password?token=abc", "PASSWORD_RESET",
                "test-expired:" + UUID.randomUUID()).withNotAfter(Instant.now().minusSeconds(1))).orElseThrow();

        worker.drain(MailOutbox.QUEUE);

        assertThat(job(id)).containsEntry("dead", true).containsEntry("attempts", 1).containsEntry("sealed", false);
        assertThat((String) job(id).get("last_error")).startsWith("Expired before delivery");
        assertThat(mailpit.subjectsTo(to)).isEmpty();
    }

    private Map<String, Object> resetJob(UUID userId) {
        return jdbc.queryForMap("""
                SELECT j.completed_at IS NOT NULL AS done, j.dead_lettered_at IS NOT NULL AS dead, j.attempts, j.payload->>'sealed' IS NOT NULL AS sealed
                FROM background_jobs j JOIN password_reset_tokens t ON j.dedupe_key = 'password-reset:' || t.id
                WHERE t.user_id = ? AND j.queue = 'email'
                """, userId);
    }

    private Map<String, Object> job(UUID id) {
        return jdbc.queryForMap("""
                SELECT completed_at IS NOT NULL AS done, dead_lettered_at IS NOT NULL AS dead, attempts, last_error,
                       payload->>'sealed' IS NOT NULL AS sealed, run_at <= now() AS due
                FROM background_jobs WHERE id = ?
                """, id);
    }

    private double rejectedCount(String category) {
        var counter = meters.find("bds.mail.rejected").tags("category", category, "reason", "invalid_address").counter();
        return counter == null ? 0 : counter.count();
    }

    private String linkToken(String email, String path) {
        Matcher matcher = Pattern.compile("/" + path + "\\?token=([A-Za-z0-9_-]+)").matcher(mailpit.latestTextTo(email));
        assertThat(matcher.find()).as("link to /%s in the latest email", path).isTrue();
        return matcher.group(1);
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
