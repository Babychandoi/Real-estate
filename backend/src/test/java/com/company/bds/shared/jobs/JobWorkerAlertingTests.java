package com.company.bds.shared.jobs;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.PrometheusScrape;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Spring-managed worker in production mode ({@code app.jobs.enabled=true}) with one handler that hangs on demand.
 * Proves on the scraped Prometheus series that the proposed alerts (docs/audit-2026-09-27/streams/s0-be/alerts.rules.yml)
 * fire when the worker hangs or dies and clear when it recovers, and that {@code /actuator/health} turns DOWN meanwhile.
 * The context is closed afterwards so its poller cannot race manually driven tests.
 */
@BdsIntegrationTest(properties = {"app.jobs.enabled=true", "app.jobs.poll-ms=100", "app.jobs.stall-after=PT30S",
        "app.jobs.metrics.refresh-ms=200"})
@AutoConfigureObservability(tracing = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JobWorkerAlertingTests {
    static final String HANG_QUEUE = "test-alert-hang-" + UUID.randomUUID().toString().substring(0, 8);
    static final String OK_QUEUE = "test-alert-ok-" + UUID.randomUUID().toString().substring(0, 8);
    private static final CountDownLatch RELEASE = new CountDownLatch(1);
    private static final AtomicInteger HANG_CALLS = new AtomicInteger();

    @TestConfiguration
    static class Handlers {
        @Bean
        JobHandler hangingHandler() {
            return new JobHandler() {
                @Override public String queue() { return HANG_QUEUE; }
                @Override public Duration lease() { return Duration.ofSeconds(2); }
                @Override public Duration timeout() { return Duration.ofMillis(1_500); }
                @Override public JobBatchResult handle(List<ClaimedJob> jobs) {
                    if (HANG_CALLS.incrementAndGet() == 1) {
                        while (true) { // a wedged dependency that ignores interrupts
                            try {
                                if (RELEASE.await(60, TimeUnit.SECONDS)) break;
                            } catch (InterruptedException ignored) {
                                // keep hanging
                            }
                        }
                    }
                    return JobBatchResult.allSucceeded(jobs);
                }
            };
        }

        @Bean
        JobHandler healthyHandler() {
            return new JobHandler() {
                @Override public String queue() { return OK_QUEUE; }
                @Override public JobBatchResult handle(List<ClaimedJob> jobs) { return JobBatchResult.allSucceeded(jobs); }
            };
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JobQueue queue;
    @Autowired JobWorker worker;
    @Autowired JdbcTemplate jdbc;

    @Test
    void hungHandlerFiresTheWorkerAndLagAlertsWhileOtherQueuesKeepRunningAndEverythingClearsOnRecovery() throws Exception {
        awaitAlerts(scrape -> assertThat(workerNotRunning(scrape)).isFalse());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        try {
            UUID hung = queue.enqueue(HANG_QUEUE, "hung", Map.of(), null);
            awaitAlerts(scrape -> {
                assertThat(workerNotRunning(scrape)).as("JobWorkerNotRunning fires").isTrue();
                assertThat(scrape.value("bds_jobs_worker_paused_queues").orElseThrow()).isEqualTo(1.0);
            });
            mockMvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value("DOWN"));
            assertThat(jdbc.queryForObject("SELECT last_error FROM background_jobs WHERE id = ?", String.class, hung))
                    .isEqualTo("Handler exceeded its time budget of 1 s");

            UUID waiting = queue.enqueue(HANG_QUEUE, "waiting", Map.of(), null);
            jdbc.update("UPDATE background_jobs SET run_at = now() - interval '10 minutes' WHERE id = ?", waiting);
            awaitAlerts(scrape -> assertThat(lagHigh(scrape, HANG_QUEUE)).as("JobQueueLagHigh fires for the paused queue").isTrue());

            UUID other = queue.enqueue(OK_QUEUE, "other", Map.of(), null);
            await().atMost(Duration.ofSeconds(10)).until(() -> completed(other));
        } finally {
            RELEASE.countDown();
        }

        awaitAlerts(scrape -> {
            assertThat(workerNotRunning(scrape)).as("JobWorkerNotRunning clears").isFalse();
            assertThat(lagHigh(scrape, HANG_QUEUE)).as("JobQueueLagHigh clears").isFalse();
        });
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void stoppedWorkerFiresTheWorkerAlertAndRecoversOnRestart() throws Exception {
        worker.stop();
        try {
            awaitAlerts(scrape -> {
                assertThat(scrape.value("bds_jobs_worker_enabled").orElseThrow()).isEqualTo(1.0);
                assertThat(workerNotRunning(scrape)).as("JobWorkerNotRunning fires for a dead poller").isTrue();
                assertThat(scrape.sum("bds_jobs_worker_running")).as("NoJobWorkerRunning fires (single instance)").isZero();
            });
            mockMvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable());
        } finally {
            worker.start();
        }
        awaitAlerts(scrape -> assertThat(workerNotRunning(scrape)).isFalse());
        UUID afterRestart = queue.enqueue(OK_QUEUE, "after-restart", Map.of(), null);
        await().atMost(Duration.ofSeconds(10)).until(() -> completed(afterRestart));
    }

    /** {@code bds_jobs_worker_enabled == 1 and bds_jobs_worker_running == 0} */
    static boolean workerNotRunning(PrometheusScrape scrape) {
        return scrape.value("bds_jobs_worker_enabled").orElse(0) == 1 && scrape.value("bds_jobs_worker_running").orElse(0) == 0;
    }

    /** {@code max by (queue) (bds_jobs_lag_seconds) > 300} */
    static boolean lagHigh(PrometheusScrape scrape, String queue) {
        return scrape.value("bds_jobs_lag_seconds", "queue", queue).orElse(0) > 300;
    }

    private boolean completed(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM background_jobs WHERE id = ?", Boolean.class, id));
    }

    private void awaitAlerts(ThrowingConsumer check) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> check.accept(PrometheusScrape.of(mockMvc)));
    }

    @FunctionalInterface
    interface ThrowingConsumer { void accept(PrometheusScrape scrape) throws Exception; }
}
