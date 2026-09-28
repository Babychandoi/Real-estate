package com.company.bds.billing;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F18.2–F18.4 on PostgreSQL: idempotent orders, bank compare-and-set, single-effect approval, exceptions, history. */
@BdsIntegrationTest
class BillingReconciliationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired ObjectMapper json;

    @BeforeEach
    void bankConfigured() {
        jdbc.update("""
                INSERT INTO bank_settings(singleton_id,bank_bin,bank_name,account_number,account_name) VALUES (1,'970436','Ngân hàng kiểm thử','0123456789','CONG TY KIEM THU')
                ON CONFLICT (singleton_id) DO NOTHING
                """);
    }

    @Test
    void twentyParallelOrderRequestsCreateOneOrder() throws Exception {
        String bearer = bearer(data.user().role("BROKER").create());
        List<Callable<String>> calls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            calls.add(() -> json.readTree(perform(bearer, post("/api/v1/billing/orders", Map.of("planCode", "STANDARD")))
                    .andReturn().getResponse().getContentAsString()).get("id").asText());
        }
        Set<String> ids = new HashSet<>(parallel(calls));
        assertThat(ids).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM package_orders WHERE id = ?::uuid", Integer.class, ids.iterator().next())).isEqualTo(1);
        UUID userId = jdbc.queryForObject("SELECT user_id FROM package_orders WHERE id = ?::uuid", UUID.class, ids.iterator().next());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM package_orders WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM package_order_events WHERE order_id = ?::uuid AND type = 'CREATED'", Integer.class,
                ids.iterator().next())).isEqualTo(1);
    }

    @Test
    void idempotencyKeyIsScopedToTheActorAndBoundToThePlan() throws Exception {
        String a = bearer(data.user().role("BROKER").create());
        String b = bearer(data.user().role("OWNER").create());
        String first = id(perform(a, post("/api/v1/billing/orders", Map.of("planCode", "STANDARD")).header("Idempotency-Key", "k-123"))
                .andExpect(status().isOk()).andExpect(r -> assertThat(r.getResponse().getHeader("X-Order-Reused")).isEqualTo("false")));
        String replay = id(perform(a, post("/api/v1/billing/orders", Map.of("planCode", "STANDARD")).header("Idempotency-Key", "k-123"))
                .andExpect(r -> assertThat(r.getResponse().getHeader("X-Order-Reused")).isEqualTo("true")));
        assertThat(replay).isEqualTo(first);
        perform(a, post("/api/v1/billing/orders", Map.of("planCode", "PRO")).header("Idempotency-Key", "k-123"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        String other = id(perform(b, post("/api/v1/billing/orders", Map.of("planCode", "STANDARD")).header("Idempotency-Key", "k-123")));
        assertThat(other).as("same key, other actor → own order").isNotEqualTo(first);
        // Another plan without a key is a separate open order.
        String pro = id(perform(a, post("/api/v1/billing/orders", Map.of("planCode", "PRO"))));
        assertThat(pro).isNotEqualTo(first);
        perform(a, post("/api/v1/billing/orders", Map.of("planCode", "STANDARD")).header("Idempotency-Key", "bad key!"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneKeyRacedAcrossPlansIsBoundToExactlyOneOrder() throws Exception {
        TestData.TestUser user = data.user().role("BROKER").create();
        String a = bearer(user);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String plan = i % 2 == 0 ? "STANDARD" : "PRO";
            calls.add(() -> perform(a, post("/api/v1/billing/orders", Map.of("planCode", plan)).header("Idempotency-Key", "race-key"))
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = parallel(calls);
        assertThat(statuses).containsOnly(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM package_orders WHERE user_id = ?", Integer.class, user.id()))
                .as("one key → one order, the other plan's requests are refused").isEqualTo(1);
        UUID bound = jdbc.queryForObject("SELECT resource_id FROM api_idempotency_keys WHERE scope = ? AND idempotency_key = 'race-key'",
                UUID.class, "billing-order:" + user.id());
        assertThat(jdbc.queryForObject("SELECT user_id FROM package_orders WHERE id = ?", UUID.class, bound)).isEqualTo(user.id());
    }

    @Test
    void legacyApproveNeedsANoteAndTheReportedStateAndOtherwiseConflicts() throws Exception {
        TestData.TestUser buyer = data.user().role("BROKER").plan("FREE", 2).create();
        String bearer = bearer(buyer);
        String admin = bearer(data.user().role("ADMIN").create());
        String order = id(perform(bearer, post("/api/v1/billing/orders", Map.of("planCode", "STANDARD"))));
        perform(admin, post("/api/v1/billing/admin/reconciliation/" + order + "/approve", Map.of("note", "Đã đối chiếu sao kê")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));
        perform(bearer, post("/api/v1/billing/orders/" + order + "/reported", null)).andExpect(status().isOk());
        perform(admin, post("/api/v1/billing/admin/reconciliation/" + order + "/approve", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTE_REQUIRED"));
        perform(admin, post("/api/v1/billing/admin/reconciliation/" + order + "/approve", Map.of("note", "Đã đối chiếu sao kê")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        perform(admin, post("/api/v1/billing/admin/reconciliation/" + order + "/approve", Map.of("note", "Đã đối chiếu sao kê")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_STATE_CHANGED"));
        perform(admin, post("/api/v1/billing/admin/reconciliation/" + order + "/reject", Map.of("reason", "Sai")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id = ?::uuid", Integer.class, order)).isEqualTo(1);
    }

    @Test
    void bankSettingsUseCompareAndSet() throws Exception {
        String admin1 = bearer(data.user().role("ADMIN").create());
        String admin2 = bearer(data.user().role("ADMIN").create());
        long loaded = json.readTree(perform(admin1, get("/api/v1/billing/admin/bank")).andReturn().getResponse().getContentAsString()).get("version").asLong();
        perform(admin1, put("/api/v1/billing/admin/bank", bank("0123456789", loaded))).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(loaded + 1));
        perform(admin2, put("/api/v1/billing/admin/bank", bank("0999999999", loaded))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANK_SETTINGS_CONFLICT"));
        perform(admin2, put("/api/v1/billing/admin/bank", bank("0999999999", null))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXPECTED_VERSION_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT account_number FROM bank_settings", String.class)).isEqualTo("0123456789");
    }

    @Test
    void concurrentApprovalsGrantTheQuotaOnce() throws Exception {
        TestData.TestUser buyer = data.user().role("BROKER").plan("FREE", 2).create();
        String bearer = bearer(buyer);
        String order = id(perform(bearer, post("/api/v1/billing/orders", Map.of("planCode", "STANDARD"))));
        perform(bearer, post("/api/v1/billing/orders/" + order + "/reported", null)).andExpect(status().isOk());
        String admin1 = bearer(data.user().role("ADMIN").create());
        String admin2 = bearer(data.user().role("ADMIN").create());
        List<Callable<String>> calls = List.of(
                () -> json.readTree(perform(admin1, post("/api/v1/billing/admin/reconciliation/" + order + "/approve", Map.of("note", "Đã đối chiếu sao kê")))
                        .andReturn().getResponse().getContentAsString()).path("status").asText("CONFLICT"),
                () -> json.readTree(perform(admin2, post("/api/v1/billing/admin/reconciliation/" + order + "/receipt",
                        Map.of("receivedAmountVnd", 199000, "receivedReference", "CK " + reference(order))))
                        .andReturn().getResponse().getContentAsString()).path("status").asText("CONFLICT"));
        parallel(calls);
        assertThat(jdbc.queryForObject("SELECT listing_quota_remaining FROM users WHERE id = ?", Integer.class, buyer.id())).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id = ?::uuid", Integer.class, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM package_order_events WHERE order_id = ?::uuid AND type = 'APPROVED'", Integer.class, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM package_orders WHERE id = ?::uuid", String.class, order)).isEqualTo("APPROVED");
    }

    @Test
    void mismatchedPaymentBecomesAnExceptionWithAnExplicitResolution() throws Exception {
        TestData.TestUser buyer = data.user().role("OWNER").plan("FREE", 2).create();
        String bearer = bearer(buyer);
        TestData.TestUser adminUser = data.user().role("ADMIN").name("Kế toán kiểm thử").create();
        String admin = bearer(adminUser);

        String order = id(perform(bearer, post("/api/v1/billing/orders", Map.of("planCode", "STANDARD"))));
        perform(bearer, post("/api/v1/billing/orders/" + order + "/reported", null)).andExpect(status().isOk());
        perform(admin, post("/api/v1/billing/admin/reconciliation/" + order + "/receipt",
                Map.of("receivedAmountVnd", 190000, "receivedReference", "CK " + reference(order), "note", "Thiếu 9.000đ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXCEPTION"))
                .andExpect(jsonPath("$.exceptionReason").value("AMOUNT_MISMATCH"));
        assertThat(jdbc.queryForObject("SELECT listing_quota_remaining FROM users WHERE id = ?", Integer.class, buyer.id())).isEqualTo(2);
        JsonNode queue = body(perform(admin, get("/api/v1/billing/admin/reconciliation?status=EXCEPTION&size=100")));
        assertThat(queue.toString()).contains(order).contains("\"receivedAmountVnd\":190000");
        assertThat(queue.get("counts").get("EXCEPTION").asLong()).isGreaterThanOrEqualTo(1);

        perform(admin, post("/api/v1/billing/admin/reconciliation/" + order + "/resolve", Map.of("resolution", "APPROVE_WITH_NOTE", "note", "")))
                .andExpect(status().isBadRequest());
        perform(admin, post("/api/v1/billing/admin/reconciliation/" + order + "/resolve",
                Map.of("resolution", "APPROVE_WITH_NOTE", "note", "Khách chuyển bổ sung 9.000đ lần hai")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.resolution").value("APPROVED_WITH_NOTE"));
        assertThat(jdbc.queryForObject("SELECT listing_quota_remaining FROM users WHERE id = ?", Integer.class, buyer.id())).isEqualTo(12);
        perform(admin, post("/api/v1/billing/admin/reconciliation/" + order + "/resolve", Map.of("resolution", "REJECT", "note", "Bấm lại lần nữa")))
                .andExpect(status().isConflict());

        JsonNode detail = body(perform(admin, get("/api/v1/billing/admin/orders/" + order)));
        List<String> types = new ArrayList<>();
        detail.get("events").forEach(e -> types.add(e.get("type").asText()));
        assertThat(types).containsExactly("CREATED", "TRANSFER_REPORTED", "EXCEPTION", "APPROVED");
        assertThat(detail.get("events").get(2).get("actorName").asText()).isEqualTo("Kế toán kiểm thử");
        JsonNode mine = body(perform(bearer, get("/api/v1/billing/orders/" + order)));
        assertThat(mine.get("events").get(2).get("actorName").isNull()).as("the customer does not see staff names").isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM background_jobs WHERE dedupe_key = ?", Integer.class, "billing-order:" + order + ":APPROVED")).isEqualTo(1);

        // Refunded offline: no quota, terminal state.
        String second = id(perform(bearer, post("/api/v1/billing/orders", Map.of("planCode", "PRO"))));
        perform(admin, post("/api/v1/billing/admin/reconciliation/" + second + "/receipt", Map.of("receivedAmountVnd", 499000, "receivedReference", "chuyen tien")))
                .andExpect(jsonPath("$.exceptionReason").value("REFERENCE_MISMATCH"));
        perform(admin, post("/api/v1/billing/admin/reconciliation/" + second + "/resolve", Map.of("resolution", "REFUNDED_OFFLINE", "note", "Đã hoàn qua ngân hàng")))
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        assertThat(jdbc.queryForObject("SELECT listing_quota_remaining FROM users WHERE id = ?", Integer.class, buyer.id())).isEqualTo(12);

        // Exact match approves directly.
        String third = id(perform(bearer, post("/api/v1/billing/orders", Map.of("planCode", "PRO"))));
        perform(admin, post("/api/v1/billing/admin/reconciliation/" + third + "/receipt",
                Map.of("receivedAmountVnd", 499000, "receivedReference", "NHAN TU KHACH " + reference(third).toLowerCase())))
                .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.resolution").value("MATCHED"));

        JsonNode history = body(perform(bearer, get("/api/v1/billing/orders?size=2")));
        assertThat(history.get("total").asInt()).isEqualTo(3);
        assertThat(history.get("items")).hasSize(2);
        assertThat(history.get("items").get(0).get("id").asText()).isEqualTo(third);
        assertThat(history.get("items").get(0).get("qrUrl").isNull()).as("no payment QR once decided").isTrue();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private String reference(String orderId) {
        return jdbc.queryForObject("SELECT transfer_reference FROM package_orders WHERE id = ?::uuid", String.class, orderId);
    }

    private Map<String, Object> bank(String account, Long version) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("bankBin", "970436", "bankName", "Ngân hàng kiểm thử",
                "accountNumber", account, "accountName", "CONG TY KIEM THU"));
        if (version != null) body.put("expectedVersion", version);
        return body;
    }

    private <T> List<T> parallel(List<Callable<T>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> call : calls) futures.add(pool.submit(() -> { start.await(); return call.call(); }));
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) results.add(f.get());
        pool.shutdown();
        return results;
    }

    private String bearer(TestData.TestUser user) { return "Bearer " + data.sessionFor(user.id()); }

    private String id(ResultActions r) throws Exception { return body(r).get("id").asText(); }

    private ResultActions perform(String bearer, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", bearer));
    }

    private static MockHttpServletRequestBuilder get(String path) { return MockMvcRequestBuilders.get(path); }

    private MockHttpServletRequestBuilder post(String path, Object body) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post(path);
        return body == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder put(String path, Object body) throws Exception {
        return MockMvcRequestBuilders.put(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private JsonNode body(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }
}
