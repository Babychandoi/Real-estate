package com.company.bds.shared.jobs;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.PrometheusScrape;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An instance with {@code app.jobs.enabled=false} (the test profile default) still exports truthful queue gauges from its
 * own refresher, so the lag and dead-letter alerts fire and "no worker anywhere" is visible.
 */
@BdsIntegrationTest(properties = "app.jobs.metrics.refresh-ms=200")
@AutoConfigureObservability(tracing = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JobAlertingWithoutWorkerTests {
    @Autowired MockMvc mockMvc;
    @Autowired JobQueue queue;
    @Autowired JdbcTemplate jdbc;

    @Test
    void queueAlertsFireWithoutAnyRunningWorker() throws Exception {
        String queueName = "test-noworker-" + UUID.randomUUID().toString().substring(0, 8);
        UUID old = queue.enqueue(queueName, "old", Map.of(), null);
        UUID dead = queue.enqueue(queueName, "dead", Map.of(), null);
        jdbc.update("UPDATE background_jobs SET run_at = now() - interval '10 minutes' WHERE id = ?", old);
        jdbc.update("UPDATE background_jobs SET dead_lettered_at = now() WHERE id = ?", dead);

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            PrometheusScrape scrape = PrometheusScrape.of(mockMvc);
            assertThat(JobWorkerAlertingTests.lagHigh(scrape, queueName)).as("JobQueueLagHigh").isTrue();
            assertThat(scrape.value("bds_jobs_dead", "queue", queueName).orElse(0)).as("JobQueueDeadLetters").isPositive();
            assertThat(scrape.value("bds_jobs_pending", "queue", queueName).orElse(0)).isEqualTo(1.0);
            assertThat(scrape.sum("bds_jobs_worker_running")).as("NoJobWorkerRunning").isZero();
            assertThat(scrape.value("bds_jobs_worker_enabled").orElseThrow()).isZero();
            assertThat(scrape.value("bds_jobs_metrics_age_seconds").orElseThrow()).as("JobMetricsStale stays quiet").isLessThan(120);
        });
        // Disabled by configuration is not a failure of this instance: the cluster-level alert covers it.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
