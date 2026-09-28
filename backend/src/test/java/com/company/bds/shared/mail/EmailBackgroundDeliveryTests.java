package com.company.bds.shared.mail;

import com.company.bds.shared.jobs.JobWorker;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.MailpitClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Production mode of the worker ({@code app.jobs.enabled=true}): its own thread picks queued e-mails up without any
 * manual drain. The context is closed afterwards so the poller cannot race the manually driven tests.
 */
@BdsIntegrationTest(properties = {"app.jobs.enabled=true", "app.jobs.poll-ms=200"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailBackgroundDeliveryTests {
    @Autowired MailOutbox outbox;
    @Autowired JobWorker worker;

    private final MailpitClient mailpit = new MailpitClient();

    @Test
    void workerThreadDeliversQueuedEmails() {
        String to = MailpitClient.uniqueAddress("poller");

        outbox.enqueue(MailMessage.text(to, "Thư gửi bởi tiến trình nền", "Nội dung kiểm thử.", "TEST_NOTICE", null));

        assertThat(worker.isRunning()).isTrue();
        await().atMost(Duration.ofSeconds(20)).until(() -> mailpit.subjectsTo(to).size() == 1);
        assertThat(mailpit.subjectsTo(to)).containsExactly("Thư gửi bởi tiến trình nền");
    }
}
