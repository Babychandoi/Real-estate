package com.company.bds.billing;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-4 (package approval part) on PostgreSQL: concurrent admin reviews, a user cancel racing an admin receipt, payment
 * exception resolutions racing each other, and retries of the approval endpoints. Every case counts the effects of an
 * approval (quota, invoice, APPROVED event, in-app notification, mail job) so a double effect cannot hide.
 */
@BdsIntegrationTest
class BillingApprovalConcurrencyTests {
    private static final int STANDARD_PRICE = 199_000;
    private static final int STANDARD_QUOTA = 10;

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
    void eightAdminsRecordingTheSameMatchingReceiptAtOnceApproveOnceAndTheOthersGet409() throws Exception {
        TestData.TestUser buyer = data.user().role("BROKER").plan("FREE", 2).create();
        String order = reportedOrder(buyer);
        List<String> admins = admins(8);
        List<MockHttpServletResponse> responses = parallel(admins.size(), i -> send(admins.get(i),
                post(review(order, "receipt"), Map.of("receivedAmountVnd", STANDARD_PRICE, "receivedReference", "CK " + reference(order)))));

        assertThat(statuses(responses)).containsEntry(200, 1L).containsEntry(409, 7L).hasSize(2);
        responses.stream().filter(r -> r.getStatus() == 409).forEach(r -> assertThat(content(r)).contains("ORDER_STATE_CHANGED"));
        assertSingleApproval(buyer, order);
    }

    @Test
    void approveWithNoteAgainstReceiptAgainstRejectHasExactlyOneOutcome() throws Exception {
        for (int round = 0; round < 3; round++) {
            TestData.TestUser buyer = data.user().role("BROKER").plan("FREE", 2).create();
            String order = reportedOrder(buyer);
            List<String> admins = admins(3);
            List<MockHttpServletRequestBuilder> actions = List.of(
                    post(review(order, "approve"), Map.of("note", "Đã đối chiếu sao kê ngân hàng")),
                    post(review(order, "receipt"), Map.of("receivedAmountVnd", STANDARD_PRICE, "receivedReference", "CK " + reference(order))),
                    post(review(order, "reject"), Map.of("reason", "Không thấy khoản chuyển")));
            List<MockHttpServletResponse> responses = parallel(3, i -> send(admins.get(i), actions.get(i)));

            assertThat(statuses(responses)).containsEntry(200, 1L).containsEntry(409, 2L);
            String status = status(order);
            if ("APPROVED".equals(status)) {
                assertSingleApproval(buyer, order);
            } else {
                assertThat(status).isEqualTo("REJECTED");
                assertNoApproval(buyer, order);
                assertThat(count("SELECT count(*) FROM package_order_events WHERE order_id = ?::uuid AND type = 'REJECTED'", order)).isEqualTo(1);
            }
        }
    }

    @Test
    void aUserCancelRacingAnAdminReceiptEndsInOneStateAndTheLoserIsTold() throws Exception {
        int cancelled = 0;
        int approved = 0;
        for (int round = 0; round < 6; round++) {
            TestData.TestUser buyer = data.user().role("OWNER").plan("FREE", 2).create();
            String bearer = bearer(buyer);
            String order = createdOrder(bearer); // CREATED: the admin may record money that arrived before the report
            String admin = admins(1).get(0);
            List<MockHttpServletResponse> responses = parallel(2, i -> i == 0
                    ? send(bearer, post("/api/v1/billing/orders/" + order + "/cancel", null))
                    : send(admin, post(review(order, "receipt"), Map.of("receivedAmountVnd", STANDARD_PRICE, "receivedReference", reference(order)))));
            MockHttpServletResponse cancel = responses.get(0);
            MockHttpServletResponse receipt = responses.get(1);
            String status = status(order);
            if ("CANCELLED".equals(status)) {
                cancelled++;
                assertThat(cancel.getStatus()).isEqualTo(200);
                assertThat(receipt.getStatus()).as(content(receipt)).isEqualTo(409);
                assertNoApproval(buyer, order);
            } else {
                approved++;
                assertThat(status).isEqualTo("APPROVED");
                assertThat(receipt.getStatus()).isEqualTo(200);
                // Before W6 the losing cancel answered 200 with an APPROVED order the client showed as "cancelled".
                assertThat(cancel.getStatus()).as(content(cancel)).isEqualTo(409);
                assertThat(content(cancel)).contains("ORDER_STATE_CHANGED");
                assertSingleApproval(buyer, order);
            }
            assertThat(count("SELECT count(*) FROM package_order_events WHERE order_id = ?::uuid AND type IN ('CANCELLED','APPROVED')", order))
                    .isEqualTo(1);
        }
        assertThat(cancelled + approved).isEqualTo(6);
    }

    @Test
    void cancelAfterApprovalIs409AndARepeatedCancelOrReportIsAnsweredWithTheOrder() throws Exception {
        TestData.TestUser buyer = data.user().role("OWNER").plan("FREE", 2).create();
        String bearer = bearer(buyer);
        String first = createdOrder(bearer);
        assertThat(send(bearer, post("/api/v1/billing/orders/" + first + "/cancel", null)).getStatus()).isEqualTo(200);
        MockHttpServletResponse again = send(bearer, post("/api/v1/billing/orders/" + first + "/cancel", null));
        assertThat(again.getStatus()).isEqualTo(200);
        assertThat(json.readTree(content(again)).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(send(bearer, post("/api/v1/billing/orders/" + first + "/reported", null)).getStatus()).as("report of a cancelled order").isEqualTo(409);
        assertThat(count("SELECT count(*) FROM package_order_events WHERE order_id = ?::uuid AND type = 'CANCELLED'", first)).isEqualTo(1);

        String second = reportedOrder(buyer);
        assertThat(send(bearer, post("/api/v1/billing/orders/" + second + "/reported", null)).getStatus()).as("repeated report").isEqualTo(200);
        assertThat(count("SELECT count(*) FROM package_order_events WHERE order_id = ?::uuid AND type = 'TRANSFER_REPORTED'", second)).isEqualTo(1);
        assertThat(send(admins(1).get(0), post(review(second, "receipt"), Map.of("receivedAmountVnd", STANDARD_PRICE,
                "receivedReference", reference(second)))).getStatus()).isEqualTo(200);
        MockHttpServletResponse late = send(bearer, post("/api/v1/billing/orders/" + second + "/cancel", null));
        assertThat(late.getStatus()).isEqualTo(409);
        assertSingleApproval(buyer, second);
    }

    @Test
    void exceptionResolutionsRacingEachOtherHaveOneOutcome() throws Exception {
        for (int round = 0; round < 3; round++) {
            TestData.TestUser buyer = data.user().role("OWNER").plan("FREE", 2).create();
            String order = reportedOrder(buyer);
            List<String> admins = admins(3);
            assertThat(send(admins.get(0), post(review(order, "receipt"), Map.of("receivedAmountVnd", 190_000,
                    "receivedReference", reference(order)))).getStatus()).isEqualTo(200);
            assertThat(status(order)).isEqualTo("EXCEPTION");

            List<String> resolutions = List.of("APPROVE_WITH_NOTE", "REFUNDED_OFFLINE", "REJECT");
            List<MockHttpServletResponse> responses = parallel(3, i -> send(admins.get(i), post(review(order, "resolve"),
                    Map.of("resolution", resolutions.get(i), "note", "Xử lý ngoại lệ lần " + i))));
            assertThat(statuses(responses)).containsEntry(200, 1L).containsEntry(409, 2L);
            String status = status(order);
            assertThat(status).isIn("APPROVED", "REFUNDED", "REJECTED");
            if ("APPROVED".equals(status)) assertSingleApproval(buyer, order);
            else assertNoApproval(buyer, order);
            assertThat(count("SELECT count(*) FROM package_order_events WHERE order_id = ?::uuid AND type IN ('APPROVED','REFUNDED','REJECTED')", order))
                    .isEqualTo(1);
            assertThat(count("""
                    SELECT count(*) FROM user_notifications WHERE user_id = ? AND type IN ('PLAN_UPGRADED','PAYMENT_REFUNDED','PAYMENT_REJECTED')
                    """, buyer.id())).isEqualTo(1);
        }
    }

    @Test
    void retriesOfAnApprovalWithOneIdempotencyKeyReplayTheResultWithoutASecondEffect() throws Exception {
        TestData.TestUser buyer = data.user().role("BROKER").plan("FREE", 2).create();
        String order = reportedOrder(buyer);
        String admin = admins(1).get(0);
        String key = "review-" + UUID.randomUUID();
        Map<String, Object> receipt = Map.of("receivedAmountVnd", STANDARD_PRICE, "receivedReference", "CK " + reference(order));

        // A double click and three network retries of the same request, all at once.
        List<MockHttpServletResponse> responses = parallel(5, i -> send(admin, post(review(order, "receipt"), receipt).header("Idempotency-Key", key)));
        assertThat(responses).allSatisfy(r -> assertThat(r.getStatus()).as(content(r)).isEqualTo(200));
        assertThat(responses.stream().filter(r -> "true".equals(r.getHeader("Idempotent-Replayed"))).count()).isEqualTo(4);
        for (MockHttpServletResponse response : responses) {
            assertThat(json.readTree(content(response)).get("status").asText()).isEqualTo("APPROVED");
        }
        assertSingleApproval(buyer, order);

        // A retry after the response was lost: the committed result again, still one effect.
        MockHttpServletResponse late = send(admin, post(review(order, "receipt"), receipt).header("Idempotency-Key", key));
        assertThat(late.getStatus()).isEqualTo(200);
        assertThat(late.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertSingleApproval(buyer, order);

        // Without the key, or with the key but another payload, or another admin's retry: refused, nothing changes.
        assertThat(content(send(admin, post(review(order, "receipt"), receipt)))).contains("ORDER_STATE_CHANGED");
        assertThat(content(send(admin, post(review(order, "receipt"), Map.of("receivedAmountVnd", 1, "receivedReference", "x"))
                .header("Idempotency-Key", key)))).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(send(admins(1).get(0), post(review(order, "receipt"), receipt).header("Idempotency-Key", key)).getStatus()).isEqualTo(409);
        assertThat(send(admin, post(review(order, "receipt"), receipt).header("Idempotency-Key", "bad key!")).getStatus()).isEqualTo(400);
        assertSingleApproval(buyer, order);
    }

    @Test
    void oneIdempotencyKeyRacedOverTwoOrdersIsBoundToExactlyOne() throws Exception {
        TestData.TestUser first = data.user().role("BROKER").plan("FREE", 2).create();
        TestData.TestUser second = data.user().role("BROKER").plan("FREE", 2).create();
        List<String> orders = List.of(reportedOrder(first), reportedOrder(second));
        String admin = admins(1).get(0);
        String key = "shared-" + UUID.randomUUID();
        List<MockHttpServletResponse> responses = parallel(2, i -> send(admin, post(review(orders.get(i), "approve"),
                Map.of("note", "Đã đối chiếu sao kê ngân hàng")).header("Idempotency-Key", key)));
        assertThat(statuses(responses)).containsEntry(200, 1L).containsEntry(409, 1L);
        long approved = orders.stream().filter(o -> "APPROVED".equals(status(o))).count();
        assertThat(approved).as("the losing request rolled back its approval").isEqualTo(1);
        assertThat(count("SELECT count(*) FROM api_idempotency_keys WHERE idempotency_key = ?", key)).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------------ effects

    private void assertSingleApproval(TestData.TestUser buyer, String order) {
        assertThat(status(order)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT listing_quota_remaining FROM users WHERE id = ?", Integer.class, buyer.id()))
                .as("quota granted once").isEqualTo(2 + STANDARD_QUOTA);
        assertThat(count("SELECT count(*) FROM invoices WHERE order_id = ?::uuid", order)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM package_order_events WHERE order_id = ?::uuid AND type = 'APPROVED'", order)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM user_notifications WHERE user_id = ? AND type = 'PLAN_UPGRADED'", buyer.id())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM background_jobs WHERE dedupe_key = ?", "billing-order:" + order + ":APPROVED")).isLessThanOrEqualTo(1);
    }

    private void assertNoApproval(TestData.TestUser buyer, String order) {
        assertThat(jdbc.queryForObject("SELECT listing_quota_remaining FROM users WHERE id = ?", Integer.class, buyer.id())).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM invoices WHERE order_id = ?::uuid", order)).isZero();
        assertThat(count("SELECT count(*) FROM package_order_events WHERE order_id = ?::uuid AND type = 'APPROVED'", order)).isZero();
        assertThat(count("SELECT count(*) FROM user_notifications WHERE user_id = ? AND type = 'PLAN_UPGRADED'", buyer.id())).isZero();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private String createdOrder(String bearer) throws Exception {
        MockHttpServletResponse created = send(bearer, post("/api/v1/billing/orders", Map.of("planCode", "STANDARD")));
        assertThat(created.getStatus()).as(content(created)).isEqualTo(200);
        return json.readTree(content(created)).get("id").asText();
    }

    private String reportedOrder(TestData.TestUser buyer) throws Exception {
        String bearer = bearer(buyer);
        String order = createdOrder(bearer);
        assertThat(send(bearer, post("/api/v1/billing/orders/" + order + "/reported", null)).getStatus()).isEqualTo(200);
        return order;
    }

    private List<String> admins(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(bearer(data.user().role("ADMIN").create()));
        return out;
    }

    private static String review(String order, String action) { return "/api/v1/billing/admin/reconciliation/" + order + "/" + action; }

    private String reference(String order) {
        return jdbc.queryForObject("SELECT transfer_reference FROM package_orders WHERE id = ?::uuid", String.class, order);
    }

    private String status(String order) {
        return jdbc.queryForObject("SELECT status FROM package_orders WHERE id = ?::uuid", String.class, order);
    }

    private long count(String sql, Object arg) { return jdbc.queryForObject(sql, Long.class, arg); }

    private static Map<Integer, Long> statuses(List<MockHttpServletResponse> responses) {
        Map<Integer, Long> out = new HashMap<>();
        responses.forEach(r -> out.merge(r.getStatus(), 1L, Long::sum));
        return out;
    }

    private static String content(MockHttpServletResponse response) {
        try {
            return response.getContentAsString();
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String bearer(TestData.TestUser user) { return "Bearer " + data.sessionFor(user.id()); }

    private MockHttpServletResponse send(String bearer, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", bearer)).andReturn().getResponse();
    }

    private MockHttpServletRequestBuilder post(String path, Object body) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post(path);
        return body == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    interface IndexedCall<T> { T call(int index) throws Exception; }

    private static <T> List<T> parallel(int n, IndexedCall<T> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                Callable<T> task = () -> { start.await(); return call.call(index); };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(180, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
