package com.company.bds.search.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Circuit breaker in front of Elasticsearch (audit F06.3). Counts engine failures (timeouts, connection errors, 5xx)
 * over the last {@code window} calls; when at least {@code minCalls} were made and the failure rate reaches
 * {@code failureRate}, it opens for {@code openFor}. Then one half-open probe at a time decides: success closes, failure
 * opens again. Query-validation errors are not engine failures and never trip it (see {@link #recordIgnored()}).
 * Gauge {@code bds.search.breaker.state}: 0 closed, 1 open, 2 half-open.
 */
@Component
public class SearchCircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final Clock clock;
    private final int window;
    private final int minCalls;
    private final double failureRate;
    private final Duration openFor;
    private final Deque<Boolean> outcomes = new ArrayDeque<>();
    private State state = State.CLOSED;
    private Instant openedAt;
    private boolean probeInFlight;

    public SearchCircuitBreaker(Clock clock, ObjectProvider<MeterRegistry> meters,
                                @Value("${app.search.breaker.window:20}") int window,
                                @Value("${app.search.breaker.min-calls:5}") int minCalls,
                                @Value("${app.search.breaker.failure-rate:0.5}") double failureRate,
                                @Value("${app.search.breaker.open-for:PT30S}") Duration openFor) {
        this.clock = clock;
        this.window = window;
        this.minCalls = minCalls;
        this.failureRate = failureRate;
        this.openFor = openFor;
        MeterRegistry registry = meters.getIfAvailable();
        if (registry != null) {
            Gauge.builder("bds.search.breaker.state", this, breaker -> breaker.state().ordinal())
                    .description("Elasticsearch circuit breaker: 0 closed, 1 open, 2 half-open").register(registry);
        }
    }

    /** Whether a call may go to Elasticsearch now; in half-open state only one probe at a time is allowed. */
    public synchronized boolean tryAcquire() {
        if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openFor))) {
            state = State.HALF_OPEN;
            probeInFlight = false;
        }
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> false;
            case HALF_OPEN -> {
                if (probeInFlight) yield false;
                probeInFlight = true;
                yield true;
            }
        };
    }

    public synchronized void recordSuccess() {
        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            outcomes.clear();
            probeInFlight = false;
        }
        push(true);
    }

    public synchronized void recordFailure() {
        if (state == State.HALF_OPEN) {
            open();
            return;
        }
        push(false);
        long failures = outcomes.stream().filter(ok -> !ok).count();
        if (outcomes.size() >= minCalls && failures >= Math.ceil(failureRate * outcomes.size())) open();
    }

    /** A call that ended without saying anything about engine health (e.g. our query was rejected with 400). */
    public synchronized void recordIgnored() {
        if (state == State.HALF_OPEN) probeInFlight = false;
    }

    /**
     * Whether a call arriving now could go to the engine: closed, or half-open with the probe slot still free. While
     * another request holds the half-open probe the answer is false, so callers pick the database engine (and its cache
     * key) instead of missing the engine's key and falling back uncached.
     */
    public synchronized boolean callable() {
        State current = state();
        return current == State.CLOSED || (current == State.HALF_OPEN && !(state == State.HALF_OPEN && probeInFlight));
    }

    public synchronized State state() {
        if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openFor))) return State.HALF_OPEN;
        return state;
    }

    /** Test/ops hook: forget history and close. */
    public synchronized void reset() {
        state = State.CLOSED;
        outcomes.clear();
        probeInFlight = false;
        openedAt = null;
    }

    private void open() {
        state = State.OPEN;
        openedAt = clock.instant();
        probeInFlight = false;
        outcomes.clear();
    }

    private void push(boolean ok) {
        outcomes.addLast(ok);
        while (outcomes.size() > window) outcomes.removeFirst();
    }
}
