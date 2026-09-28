package com.company.bds.shared.jobs;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobBackoffTests {
    private static final Duration BASE = Duration.ofSeconds(30);
    private static final Duration CAP = Duration.ofHours(1);

    @Test
    void doublesFromThirtySecondsAndCapsAtOneHour() {
        JobBackoff noJitter = new JobBackoff(BASE, CAP, () -> 0.5);

        assertThat(noJitter.delayAfter(0)).isEqualTo(Duration.ofSeconds(30));
        assertThat(noJitter.delayAfter(1)).isEqualTo(Duration.ofSeconds(60));
        assertThat(noJitter.delayAfter(4)).isEqualTo(Duration.ofMinutes(8));
        assertThat(noJitter.delayAfter(7)).isEqualTo(Duration.ofHours(1));
        assertThat(noJitter.delayAfter(500)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void jitterStaysWithinTwentyPercent() {
        assertThat(new JobBackoff(BASE, CAP, () -> 0.0).delayAfter(0)).isEqualTo(Duration.ofSeconds(24));
        assertThat(new JobBackoff(BASE, CAP, () -> 0.999_999).delayAfter(0)).isBetween(Duration.ofMillis(35_999), Duration.ofSeconds(36));
        JobBackoff random = new JobBackoff(BASE, CAP);
        for (int i = 0; i < 1_000; i++) {
            assertThat(random.delayAfter(2)).isBetween(Duration.ofSeconds(96), Duration.ofSeconds(144));
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new JobBackoff(Duration.ZERO, CAP)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobBackoff(CAP, BASE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storedErrorsCarryNoEmailOrPhoneButKeepSmtpCodes() {
        String sanitized = JobErrors.describe(new IllegalStateException(
                "550 5.1.1 <nguoi.nhan@gmail.com> rejected; gọi +84 912 345 678 hoặc 0243.826.1234\nline two"));

        assertThat(sanitized).isEqualTo("IllegalStateException: 550 5.1.1 <<email>> rejected; gọi <phone> hoặc <phone> line two");
        assertThat(JobErrors.sanitize(null)).isEqualTo("Job failed");
        assertThat(JobErrors.sanitize("x".repeat(900))).hasSize(500);
    }
}
