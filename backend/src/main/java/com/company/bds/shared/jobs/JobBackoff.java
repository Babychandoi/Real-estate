package com.company.bds.shared.jobs;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/** Retry delay after a failed attempt: {@code min(cap, base · 2^attempts) ± 20 %} jitter (contract §3.1). */
public final class JobBackoff {
    private final Duration base;
    private final Duration cap;
    private final DoubleSupplier random;

    public JobBackoff(Duration base, Duration cap) {
        this(base, cap, () -> ThreadLocalRandom.current().nextDouble());
    }

    JobBackoff(Duration base, Duration cap, DoubleSupplier random) {
        if (base.isNegative() || base.isZero() || cap.compareTo(base) < 0) {
            throw new IllegalArgumentException("Retry base must be positive and not larger than the cap");
        }
        this.base = base;
        this.cap = cap;
        this.random = random;
    }

    /** @param previousAttempts failed attempts before the one that just failed (0 for the first failure) */
    public Duration delayAfter(int previousAttempts) {
        double exponential = base.toMillis() * Math.pow(2, Math.min(Math.max(previousAttempts, 0), 30));
        double capped = Math.min(cap.toMillis(), exponential);
        double jitter = 0.8 + 0.4 * random.getAsDouble();
        return Duration.ofMillis(Math.round(capped * jitter));
    }
}
