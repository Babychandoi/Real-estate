package com.company.bds.billing;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Review 2 M3: the reconciliation notice can never block a buyer's transfer report (F18.1). */
@BdsIntegrationTest
class BillingNotificationTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired ObjectMapper json;
    @Autowired MeterRegistry meters;

    @Test
    void bankSettingsRefuseAddressesMailCannotUseAndKeepUnusualValidOnes() throws Exception {
        for (String invalid : List.of("Kế toán <ketoan@congty.vn>", "a@x.vn, b@x.vn", "ketoan@", "ke toan@congty.vn")) {
            saveBank(invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        for (String valid : List.of("admin@localhost", "ke.toan&co@x.vn", "  ketoan@congty.vn  ")) {
            saveBank(valid).andExpect(status().isOk()).andExpect(jsonPath("$.adminEmail").value(valid.trim()));
        }
        saveBank("").andExpect(status().isOk()).andExpect(jsonPath("$.adminEmail").doesNotExist());
    }

    @Test
    void unusableStoredAdminAddressNeverBlocksTheTransferReport() throws Exception {
        saveBank("ketoan@congty.vn").andExpect(status().isOk());
        // A value stored before the address was validated (older release or direct SQL).
        jdbc.update("UPDATE bank_settings SET admin_notification_email = 'Kế toán <ketoan@congty.vn>' WHERE singleton_id = 1");
        TestData.TestUser broker = data.user().role("BROKER").create();
        String bearer = "Bearer " + data.sessionFor(broker.id());
        String orderId = json.readTree(mockMvc.perform(post("/api/v1/billing/orders").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planCode\":\"STANDARD\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();
        double rejectedBefore = rejected();

        mockMvc.perform(post("/api/v1/billing/orders/" + orderId + "/reported").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRANSFER_REPORTED"));

        assertThat(jdbc.queryForObject("SELECT status FROM package_orders WHERE id = CAST(? AS uuid)", String.class, orderId))
                .isEqualTo("TRANSFER_REPORTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_jobs WHERE dedupe_key = ?", Integer.class,
                "billing-transfer-reported:" + orderId)).as("no mail queued to an unusable address").isZero();
        assertThat(rejected() - rejectedBefore).isEqualTo(1.0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'PAYMENT_REPORTED'",
                Integer.class, broker.id())).as("the buyer's in-app confirmation still happens").isEqualTo(1);
        saveBank("ketoan@congty.vn").andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions saveBank(String adminEmail) throws Exception {
        // F18.3: saves are compare-and-set on the version the admin loaded.
        Long version = jdbc.queryForList("SELECT version FROM bank_settings WHERE singleton_id = 1", Long.class).stream().findFirst().orElse(null);
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>(java.util.Map.of(
                "bankBin", "970436", "bankName", "Ngân hàng kiểm thử", "accountNumber", "0123456789",
                "accountName", "CONG TY KIEM THU", "adminEmail", adminEmail));
        if (version != null) body.put("expectedVersion", version);
        return mockMvc.perform(put("/api/v1/billing/admin/bank").with(user(UUID.randomUUID().toString()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private double rejected() {
        var counter = meters.find("bds.mail.rejected").tags("category", "BILLING_TRANSFER_REPORTED", "reason", "invalid_address").counter();
        return counter == null ? 0 : counter.count();
    }
}
