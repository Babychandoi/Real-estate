package com.company.bds.billing;

import com.company.bds.lead.application.IdempotencyKeyPurgeTask;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial review of the W6 admin-review Idempotency-Key (PR #24). Expected to fail on the reviewed commit: each test
 * reproduces a defect described in the review report.
 */
@BdsIntegrationTest
class BillingReviewIdempotencyReviewTests {
    private static final int STANDARD_PRICE = 199_000;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired ObjectMapper json;
    @Autowired IdempotencyKeyPurgeTask purge;

    @BeforeEach
    void bankConfigured() {
        jdbc.update("""
                INSERT INTO bank_settings(singleton_id,bank_bin,bank_name,account_number,account_name) VALUES (1,'970436','Ngân hàng kiểm thử','0123456789','CONG TY KIEM THU')
                ON CONFLICT (singleton_id) DO NOTHING
                """);
    }

    /**
     * The controller comment and the stream report promise that a retry "gets the committed result back". The replay
     * returns the order as it is NOW: admin A records a short payment (result EXCEPTION), admin B approves the exception,
     * A's client retries the lost response with the same key and is told, with Idempotent-Replayed: true, that its
     * receipt APPROVED the order.
     */
    @Test
    void aReplayReturnsTheResultTheKeyedRequestProducedNotWhateverHappenedLater() throws Exception {
        String order = reportedOrder(data.user().role("BROKER").plan("FREE", 2).create());
        String adminA = admin();
        String key = "rv-" + UUID.randomUUID();
        Map<String, Object> shortPayment = Map.of("receivedAmountVnd", 190_000, "receivedReference", reference(order));
        MockHttpServletResponse first = send(adminA, post(review(order, "receipt"), shortPayment).header("Idempotency-Key", key));
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(json.readTree(content(first)).get("status").asText()).isEqualTo("EXCEPTION");

        assertThat(send(admin(), post(review(order, "resolve"), Map.of("resolution", "APPROVE_WITH_NOTE", "note", "Khách chuyển bù đủ")))
                .getStatus()).isEqualTo(200);

        MockHttpServletResponse retry = send(adminA, post(review(order, "receipt"), shortPayment).header("Idempotency-Key", key));
        assertThat(retry.getStatus()).isEqualTo(200);
        assertThat(retry.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(json.readTree(content(retry)).get("status").asText())
                .as("a replay must answer what the keyed request did (EXCEPTION), not the order's current state")
                .isEqualTo("EXCEPTION");
    }

    /** "Kept for 24 h": IdempotencyKeyPurgeTask only deletes lead scopes, so billing-review keys are never removed. */
    @Test
    void anExpiredAdminReviewKeyIsPurged() throws Exception {
        String order = reportedOrder(data.user().role("BROKER").plan("FREE", 2).create());
        String key = "rv-" + UUID.randomUUID();
        assertThat(send(admin(), post(review(order, "approve"), Map.of("note", "Đã đối chiếu sao kê")).header("Idempotency-Key", key))
                .getStatus()).isEqualTo(200);
        jdbc.update("UPDATE api_idempotency_keys SET created_at = TIMESTAMPTZ '2000-01-01', expires_at = TIMESTAMPTZ '2000-01-02' WHERE idempotency_key = ?", key);
        purge.purge();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM api_idempotency_keys WHERE idempotency_key = ?", Integer.class, key))
                .as("an expired billing-review key is removed by the purge job").isZero();
    }

    /**
     * An expired key is ignored by the replay check but still blocks bind() (INSERT ... ON CONFLICT DO NOTHING), so a
     * valid new action with a key that expired is rolled back with 409 IDEMPOTENCY_KEY_REUSED — the key value is poisoned
     * forever because nothing purges it.
     */
    @Test
    void aKeyThatExpiredCanBeUsedForANewAction() throws Exception {
        String admin = admin();
        String key = "rv-" + UUID.randomUUID();
        String first = reportedOrder(data.user().role("BROKER").plan("FREE", 2).create());
        assertThat(send(admin, post(review(first, "approve"), Map.of("note", "Đã đối chiếu sao kê")).header("Idempotency-Key", key))
                .getStatus()).isEqualTo(200);
        jdbc.update("UPDATE api_idempotency_keys SET created_at = TIMESTAMPTZ '2000-01-01', expires_at = TIMESTAMPTZ '2000-01-02' WHERE idempotency_key = ?", key);

        String second = reportedOrder(data.user().role("BROKER").plan("FREE", 2).create());
        MockHttpServletResponse later = send(admin, post(review(second, "approve"), Map.of("note", "Đã đối chiếu sao kê")).header("Idempotency-Key", key));
        assertThat(later.getStatus()).as(content(later)).isEqualTo(200);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private String reportedOrder(TestData.TestUser buyer) throws Exception {
        String bearer = "Bearer " + data.sessionFor(buyer.id());
        MockHttpServletResponse created = send(bearer, post("/api/v1/billing/orders", Map.of("planCode", "STANDARD")));
        assertThat(created.getStatus()).as(content(created)).isEqualTo(200);
        String order = json.readTree(content(created)).get("id").asText();
        assertThat(send(bearer, post("/api/v1/billing/orders/" + order + "/reported", null)).getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT amount_vnd FROM package_orders WHERE id = ?::uuid", Long.class, order)).isEqualTo(STANDARD_PRICE);
        return order;
    }

    private String admin() { return "Bearer " + data.sessionFor(data.user().role("ADMIN").create().id()); }

    private static String review(String order, String action) { return "/api/v1/billing/admin/reconciliation/" + order + "/" + action; }

    private String reference(String order) {
        return jdbc.queryForObject("SELECT transfer_reference FROM package_orders WHERE id = ?::uuid", String.class, order);
    }

    private static String content(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private MockHttpServletResponse send(String bearer, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", bearer)).andReturn().getResponse();
    }

    private MockHttpServletRequestBuilder post(String path, Object body) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post(path);
        return body == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }
}
