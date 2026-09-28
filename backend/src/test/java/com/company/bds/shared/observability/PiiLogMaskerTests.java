package com.company.bds.shared.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiLogMaskerTests {

    @ParameterizedTest
    @ValueSource(strings = {"nguyen.van.a@example.com", "A.B+tag@sub.mail.vn"})
    void masksEmailAddresses(String email) {
        assertThat(PiiLogMasker.mask("login failed for " + email + " twice"))
                .isEqualTo("login failed for [EMAIL] twice");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0912345678", "+84912345678", "84912345678", "0912 345 678", "091.234.5678", "0243-123-4567"})
    void masksVietnamesePhoneNumbers(String phone) {
        assertThat(PiiLogMasker.mask("callback " + phone + ".")).isEqualTo("callback [PHONE].");
    }

    @Test
    void masksBearerTokensAndSecretPairs() {
        assertThat(PiiLogMasker.mask("header Authorization: Bearer abc.DEF-123_xyz~ ok"))
                .isEqualTo("header Authorization: Bearer [REDACTED] ok");
        assertThat(PiiLogMasker.mask("password=hunter2 token: \"t0k3n\" otp=123456&x=1"))
                .isEqualTo("password=[REDACTED] token: \"[REDACTED]\" otp=[REDACTED]&x=1");
    }

    @Test
    void leavesIdsTimestampsAndAmountsAlone() {
        String line = "listing 3f2a9c1e-5b7d-4e0a-9c1e-5b7d4e0a9c1e at 2026-09-28T10:15:30Z price=3950000000 took 1727481600123 ms "
                + "status=409 page=2";
        assertThat(PiiLogMasker.mask(line)).isEqualTo(line);
        assertThat(PiiLogMasker.mask(null)).isNull();
        assertThat(PiiLogMasker.mask("")).isEmpty();
    }
}
