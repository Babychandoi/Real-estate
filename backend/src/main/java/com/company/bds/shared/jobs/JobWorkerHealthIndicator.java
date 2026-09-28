package com.company.bds.shared.jobs;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health contributor {@code jobWorker}, part of the aggregate {@code /actuator/health} (the container healthcheck) but not
 * of the liveness/readiness probe groups: a stalled worker must be visible and alerted on, not take the API out of
 * rotation. DOWN when the worker is enabled on this instance but stalled (poll thread gone, heartbeat older than
 * {@code app.jobs.stall-after}, or a queue paused by a hung handler); UP with {@code enabled=false} when disabled by
 * configuration (then {@code bds.jobs.worker.running} is 0 and cluster-level alerting decides).
 */
@Component
public class JobWorkerHealthIndicator implements HealthIndicator {
    private final JobWorker worker;

    public JobWorkerHealthIndicator(JobWorker worker) {
        this.worker = worker;
    }

    @Override
    public Health health() {
        JobWorker.State state = worker.state();
        Health.Builder builder = !state.enabled() || state.healthy() ? Health.up() : Health.down();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("enabled", state.enabled());
        details.put("running", state.running());
        details.put("queues", worker.queues());
        if (state.heartbeatAge() != null) details.put("lastPollAgeSeconds", state.heartbeatAge().toSeconds());
        details.put("stallAfterSeconds", state.stallAfter().toSeconds());
        if (!state.pausedQueues().isEmpty()) details.put("pausedQueues", state.pausedQueues());
        return builder.withDetails(details).build();
    }
}
