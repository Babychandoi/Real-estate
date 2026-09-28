package com.company.bds.lead;

import com.company.bds.lead.application.ReportDeskService;
import com.company.bds.lead.application.ReporterPhoneEncryptionMigrator;
import com.company.bds.shared.security.PiiProtectionService;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Report queue: encrypted reporter phone, SLA by severity, claims, event history, owner outcome, listing history. */
@BdsIntegrationTest
class ReportDeskTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired ObjectMapper json;
    @Autowired ReporterPhoneEncryptionMigrator migrator;
    @Autowired PiiProtectionService pii;
    @Autowired ReportDeskService desk;

    @Test
    void reporterPhoneIsStoredEncryptedAndOnlyShownMasked() throws Exception {
        TestData.TestListing listing = data.listing(data.user().role("BROKER").create().id()).create();
        JsonNode created = body(mvc.perform(MockMvcRequestBuilders.post("/api/v1/public/reports").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("listingId", listing.id(), "category", "FAKE_SOLD",
                        "description", "Căn này đã bán từ tháng trước", "reporterPhone", "0912345678"))))
                .andExpect(status().isCreated()));
        assertThat(created.get("reporterPhone").asText()).isEqualTo("091****678");
        assertThat(created.toString()).doesNotContain("0912345678");
        String stored = jdbc.queryForObject("SELECT reporter_phone FROM listing_reports WHERE id = ?::uuid", String.class, created.get("id").asText());
        assertThat(stored).startsWith("v1:").doesNotContain("0912345678");
        assertThat(pii.reveal(stored)).isEqualTo("0912345678");
        assertThat(created.get("severity").asText()).isEqualTo("MEDIUM");

        String staff = bearer("MODERATOR");
        String queue = perform(staff, get("/api/v1/reports/queue?size=100")).andReturn().getResponse().getContentAsString();
        assertThat(queue).contains("091****678").doesNotContain("0912345678");
        JsonNode events = body(perform(staff, get("/api/v1/reports/" + created.get("id").asText() + "/events")));
        assertThat(events.get(0).get("type").asText()).isEqualTo("SUBMITTED");
    }

    @Test
    void legacyPlaintextPhonesAreEncryptedInPlaceAndTheMigrationIsResumable() {
        TestData.TestListing listing = data.listing(data.user().role("BROKER").create().id()).create();
        UUID legacy = insertReport(listing.id(), "MEDIUM", Instant.now(), "0987 654 321");
        UUID blank = insertReport(listing.id(), "LOW", Instant.now(), "  ");
        assertThat(ReportDeskService.maskedPhone("0987 654 321")).isEqualTo("*******321");

        assertThat(migrator.migrateAll()).isGreaterThanOrEqualTo(2);
        String stored = jdbc.queryForObject("SELECT reporter_phone FROM listing_reports WHERE id = ?", String.class, legacy);
        assertThat(stored).startsWith("v1:");
        assertThat(pii.reveal(stored)).isEqualTo("0987654321");
        assertThat(jdbc.queryForObject("SELECT reporter_phone FROM listing_reports WHERE id = ?", String.class, blank)).isNull();
        assertThat(migrator.migrateAll()).as("second run changes nothing").isZero();
        assertThat(jdbc.queryForObject("SELECT reporter_phone FROM listing_reports WHERE id = ?", String.class, legacy)).isEqualTo(stored);
    }

    @Test
    void slaFollowsSeverityAndTheQueueIsOrderedByDueTime() throws Exception {
        TestData.TestListing listing = data.listing(data.user().role("BROKER").create().id()).create();
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        UUID p0 = insertReport(listing.id(), "P0_EMERGENCY", twoHoursAgo, null);   // due 1 h after → breached
        UUID high = insertReport(listing.id(), "HIGH", twoHoursAgo, null);          // due in ~2 h
        UUID low = insertReport(listing.id(), "LOW", twoHoursAgo, null);            // due in ~70 h

        String staff = bearer("MODERATOR");
        JsonNode page = body(perform(staff, get("/api/v1/reports/queue?size=100")));
        List<String> order = new ArrayList<>();
        page.get("items").forEach(i -> order.add(i.get("id").asText()));
        assertThat(order.indexOf(p0.toString())).isLessThan(order.indexOf(high.toString()));
        assertThat(order.indexOf(high.toString())).isLessThan(order.indexOf(low.toString()));
        JsonNode p0Item = item(page, p0);
        assertThat(p0Item.get("slaBreached").asBoolean()).isTrue();
        assertSlaOffset(twoHoursAgo, p0Item, Duration.ofHours(1));
        assertSlaOffset(twoHoursAgo, item(page, high), Duration.ofHours(4));
        assertSlaOffset(twoHoursAgo, item(page, low), Duration.ofHours(72));
        assertThat(item(page, high).get("slaBreached").asBoolean()).isFalse();

        JsonNode breached = body(perform(staff, get("/api/v1/reports/queue?breached=true&size=100")));
        List<String> breachedIds = new ArrayList<>();
        breached.get("items").forEach(i -> breachedIds.add(i.get("id").asText()));
        assertThat(breachedIds).contains(p0.toString()).doesNotContain(high.toString(), low.toString());
    }

    @Test
    void claimsHistoryAndListingStatusAuditOfAnEmergencyHide() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        TestData.TestListing listing = data.listing(owner.id()).status("ACTIVE").create();
        UUID report = insertReport(listing.id(), "HIGH", Instant.now(), null);
        TestData.TestUser a = data.user().role("MODERATOR").name("Điều phối viên A").create();
        String bearerA = "Bearer " + data.sessionFor(a.id());
        String bearerB = bearer("ADMIN");

        perform(bearerA, post("/api/v1/reports/" + report + "/claim", null)).andExpect(status().isOk());
        perform(bearerB, post("/api/v1/reports/" + report + "/claim", null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_CONFLICT"));
        perform(bearerB, post("/api/v1/reports/" + report + "/emergency-hide", Map.of("reason", "Nghi lừa cọc")))
                .andExpect(status().isConflict());
        perform(bearerA, post("/api/v1/reports/" + report + "/emergency-hide", Map.of("reason", "Nghi lừa cọc, tạm ẩn xác minh")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING_REPLY"));
        assertThat(jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, listing.id())).isEqualTo("PAUSED");
        Map<String, Object> history = jdbc.queryForMap("SELECT action, from_status, to_status, actor_id, reason FROM listing_status_history WHERE listing_id = ?", listing.id());
        assertThat(history).containsEntry("action", "EMERGENCY_HIDE").containsEntry("from_status", "ACTIVE").containsEntry("to_status", "PAUSED")
                .containsEntry("actor_id", a.id());
        perform(bearerA, post("/api/v1/reports/" + report + "/notes", Map.of("note", "Đã gọi người đăng, chờ giấy tờ"))).andExpect(status().isNoContent());
        perform(bearerA, post("/api/v1/reports/" + report + "/resolve", Map.of("resolutionNote", "Xác minh lừa đảo", "permanentlyLockListing", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));

        JsonNode events = body(perform(bearerA, get("/api/v1/reports/" + report + "/events")));
        List<String> types = new ArrayList<>();
        events.forEach(e -> types.add(e.get("type").asText()));
        assertThat(types).containsExactly("SUBMITTED", "CLAIMED", "EMERGENCY_HIDDEN", "NOTE", "RESOLVED");
        assertThat(events.get(2).get("actorName").asText()).isEqualTo("Điều phối viên A");
        assertThat(jdbc.queryForObject("SELECT resolved_by FROM listing_reports WHERE id = ?", UUID.class, report)).isEqualTo(a.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listing_status_history WHERE listing_id = ? AND action = 'REPORT_LOCK'", Integer.class, listing.id())).isEqualTo(1);
        perform(bearerB, post("/api/v1/reports/" + report + "/claim", null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_CLOSED"));
    }

    @Test
    void fakeSoldOwnerOutcomeIsShownInTheQueue() throws Exception {
        TestData.TestListing listing = data.listing(data.user().role("OWNER").create().id()).create();
        UUID report = insertReport(listing.id(), "MEDIUM", Instant.now(), null);
        jdbc.update("UPDATE listing_reports SET category = 'FAKE_SOLD' WHERE id = ?", report);
        desk.recordEvent(report, "AUTO_PAUSED", null, "Chủ tin không xác nhận còn hàng trong hạn", Map.of("hours", 48));
        JsonNode page = body(perform(bearer("MODERATOR"), get("/api/v1/reports/queue?size=100")));
        assertThat(item(page, report).get("ownerOutcome").asText()).isEqualTo("AUTO_PAUSED");

        String staff = bearer("MODERATOR");
        perform(staff, post("/api/v1/reports/" + report + "/severity", Map.of("severity", "P0_EMERGENCY"))).andExpect(status().isBadRequest());
        perform(staff, post("/api/v1/reports/" + report + "/severity", Map.of("severity", "P0_EMERGENCY", "reason", "Nhiều báo cáo lừa cọc")))
                .andExpect(status().isNoContent());
        JsonNode escalated = item(body(perform(staff, get("/api/v1/reports/queue?size=100"))), report);
        assertThat(escalated.get("severity").asText()).isEqualTo("P0_EMERGENCY");
        assertThat(body(perform(staff, get("/api/v1/reports/" + report + "/events"))).toString()).contains("severityTo").contains("P0_EMERGENCY")
                .contains("ESCALATED");
        perform(bearer("USER"), get("/api/v1/reports/queue")).andExpect(status().isForbidden());
    }

    @Test
    void closedCasesCannotBeActedOnAgainAndConcurrentClosingHasOneEffect() throws Exception {
        TestData.TestListing listing = data.listing(data.user().role("BROKER").create().id()).status("ACTIVE").create();
        UUID report = insertReport(listing.id(), "HIGH", Instant.now(), null);
        String a = bearer("MODERATOR");
        String b = bearer("ADMIN");
        List<java.util.concurrent.Callable<Integer>> calls = List.of(
                () -> perform(a, post("/api/v1/reports/" + report + "/resolve", Map.of("resolutionNote", "Vi phạm rõ", "permanentlyLockListing", true)))
                        .andReturn().getResponse().getStatus(),
                () -> perform(b, post("/api/v1/reports/" + report + "/dismiss", Map.of("dismissNote", "Không vi phạm", "resumeListing", true)))
                        .andReturn().getResponse().getStatus());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> futures = new ArrayList<>();
        for (var call : calls) futures.add(pool.submit(() -> { start.await(); return call.call(); }));
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (var f : futures) statuses.add(f.get());
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_events WHERE report_id = ? AND type IN ('RESOLVED','DISMISSED')",
                Integer.class, report)).isEqualTo(1);

        perform(a, post("/api/v1/reports/" + report + "/resolve", Map.of("resolutionNote", "Lần nữa", "permanentlyLockListing", false)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_CLOSED"));
        perform(b, post("/api/v1/reports/" + report + "/dismiss", Map.of("dismissNote", "Lần nữa", "resumeListing", true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_CLOSED"));
        perform(a, post("/api/v1/reports/" + report + "/severity", Map.of("severity", "LOW", "reason", "Hạ mức độ sau khi đóng")))
                .andExpect(status().isConflict());
    }

    private static void assertSlaOffset(Instant createdAt, JsonNode item, Duration expected) {
        Duration actual = Duration.between(createdAt, Instant.parse(item.get("slaDueAt").asText()));
        assertThat(Math.abs(actual.minus(expected).toMillis())).isLessThan(1000L);
    }

    private UUID insertReport(UUID listingId, String severity, Instant createdAt, String phone) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO listing_reports(id, listing_id, case_number, reporter_type, reporter_phone, category, severity, status, description, created_at)
                VALUES (?,?,?,?,?,'OTHER',?,'PENDING','Mô tả vụ việc kiểm thử',?)
                """, id, listingId, "CASE-" + id.toString().substring(0, 8), "ANONYMOUS", phone, severity, Timestamp.from(createdAt));
        return id;
    }

    private static JsonNode item(JsonNode page, UUID id) {
        for (JsonNode i : page.get("items")) if (i.get("id").asText().equals(id.toString())) return i;
        throw new AssertionError("missing " + id);
    }

    private String bearer(String role) { return "Bearer " + data.sessionFor(data.user().role(role).create().id()); }

    private ResultActions perform(String bearer, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", bearer));
    }

    private static MockHttpServletRequestBuilder get(String path) { return MockMvcRequestBuilders.get(path); }

    private MockHttpServletRequestBuilder post(String path, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path);
        return body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private JsonNode body(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
}
