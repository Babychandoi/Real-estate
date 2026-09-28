package com.company.bds.analytics;

import com.company.bds.analytics.application.AnalyticsMaintenanceService;
import com.company.bds.analytics.application.AnalyticsRecorder;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F19.3 / P-13 / F17.4 / UI-24 (S8): one fixture journey is counted exactly once in every funnel; its bot and internal
 * copies are not; "chưa đo" is never reported as 0. Unique utm source / district codes isolate this test's rows from
 * the other tests sharing the database.
 */
@BdsIntegrationTest
class AnalyticsDashboardTests {
    static final String BROWSER = EventIngestionTests.BROWSER;
    static final String GOOGLEBOT = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired AnalyticsRecorder recorder;
    @Autowired AnalyticsMaintenanceService maintenance;
    @Autowired TransactionTemplate transactions;

    @Test
    void oneJourneyIsCountedOnceInEveryFunnelAndBotOrStaffCopiesAreNot() throws Exception {
        String source = "s8j" + UUID.randomUUID().toString().substring(0, 8);
        String district = String.valueOf(10000 + (int) (Math.random() * 89999));
        TestData.TestUser seeker = data.user().create();
        TestData.TestUser owner = data.user().role("BROKER").create();
        TestData.TestListing listing = data.listing(owner.id()).district(district, "Quận kiểm thử").create();
        String seekerBearer = "Bearer " + data.sessionFor(seeker.id());

        // Web journey of one consented session, sent twice (a retried beacon) → stored once.
        String body = journey(listing.id(), district, source, "dev-" + UUID.randomUUID(), "journey-" + UUID.randomUUID());
        send(body, seekerBearer, BROWSER);
        send(body, seekerBearer, BROWSER);
        // A crawler replaying the same journey and a moderator testing it: excluded.
        send(journey(listing.id(), district, source, "bot-" + UUID.randomUUID(), "bot-sess-" + UUID.randomUUID()), null, GOOGLEBOT);
        TestData.TestUser moderator = data.user().role("MODERATOR").create();
        send(journey(listing.id(), district, source, "staff-" + UUID.randomUUID(), "staff-sess-" + UUID.randomUUID()),
                "Bearer " + data.sessionFor(moderator.id()), BROWSER);

        // Server/database side: lead → first response in 10 min → qualified → appointment confirmed → completed.
        Instant created = Instant.now().minus(Duration.ofHours(2));
        UUID lead = data.lead(listing.id()).requester(seeker.id()).createdAt(created).create();
        jdbc.update("UPDATE leads SET first_response_at = ?, qualification = 'QUALIFIED' WHERE id = ?",
                Timestamp.from(created.plus(Duration.ofMinutes(10))), lead);
        jdbc.update("""
                INSERT INTO viewing_appointments (id, lead_id, listing_id, owner_id, requester_id, status, proposed_by_side, starts_at, ends_at,
                                                  confirmed_at, outcome_at)
                VALUES (?, ?, ?, ?, ?, 'COMPLETED', 'OWNER_SIDE', ?, ?, ?, now())
                """, UUID.randomUUID(), lead, listing.id(), owner.id(), seeker.id(), Timestamp.from(created.plus(Duration.ofMinutes(30))),
                Timestamp.from(created.plus(Duration.ofMinutes(60))), Timestamp.from(created.plus(Duration.ofMinutes(20))));
        for (int attempt = 0; attempt < 2; attempt++) {
            transactions.executeWithoutResult(status -> recorder.recordServer("lead_submitted", 1, lead.toString(), seeker.id(), listing.id(),
                    Map.of("leadId", lead.toString(), "requestType", "VIEWING")));
        }
        // A staff member's own test lead on the same listing is not counted.
        data.lead(listing.id()).requester(moderator.id()).createdAt(created).create();

        JsonNode web = dashboard("source", source);
        assertThat(counts(funnel(web, "search"))).containsExactly(1L, 1L, 1L, 1L);
        assertThat(counts(funnel(web, "kyc"))).containsExactly(1L, 1L, 1L);
        JsonNode zero = metric(web, "zeroResultRate");
        assertThat(zero.get("status").asText()).isEqualTo("MEASURED");
        assertThat(zero.get("value").asDouble()).as("a measured 0 %, not 'not measured'").isZero();
        assertThat(metric(web, "kycAbandonment").get("value").asDouble()).isZero();
        JsonNode leadStep = funnel(web, "lead").get("steps").get(0).get("count");
        assertThat(leadStep.get("status").asText()).as("server data is not split by traffic source").isEqualTo("NOT_MEASURED");
        assertThat(leadStep.get("value").isNull()).isTrue();
        assertThat(leadStep.get("reason").asText()).contains("nguồn truy cập");

        JsonNode byArea = dashboard("area", district);
        assertThat(counts(funnel(byArea, "lead"))).containsExactly(1L, 1L, 1L, 1L, 1L);
        assertThat(metric(byArea, "responseWithinSla").get("value").asDouble()).isEqualTo(100.0);
        assertThat(metric(byArea, "medianFirstResponseMinutes").get("value").asDouble()).isEqualTo(10.0);
        assertThat(metric(byArea, "qualifiedShare").get("value").asDouble()).isEqualTo(100.0);
        assertThat(metric(byArea, "appointmentHeld").get("value").asDouble()).isEqualTo(100.0);
        assertThat(metric(byArea, "confirmedAppointments").get("value").asLong()).isEqualTo(1);
        assertThat(funnel(byArea, "search").get("steps").get(0).get("count").get("status").asText()).isEqualTo("NOT_MEASURED");

        // Aggregates: breakdown by source, trend, return visits.
        maintenance.aggregateRecent(1);
        JsonNode aggregated = dashboard("source", source);
        JsonNode sourceRow = aggregated.get("breakdowns").get("source").get(0);
        assertThat(sourceRow.get("key").asText()).isEqualTo(source);
        assertThat(sourceRow.get("searchSessions").asLong()).isEqualTo(1);
        assertThat(sourceRow.get("detailViews").asLong()).isEqualTo(1);
        assertThat(sourceRow.get("leadForms").asLong()).isEqualTo(1);
        JsonNode today = aggregated.get("trend").get(aggregated.get("trend").size() - 1);
        assertThat(today.get("day").asText()).isEqualTo(LocalDate.now(AnalyticsMaintenanceService.VIETNAM).toString());
        assertThat(today.get("searches").asLong()).isEqualTo(1);
        assertThat(metric(aggregated, "returnRate").get("denominator").asLong()).isEqualTo(1);
        assertThat(aggregated.get("cohorts").get("rows").get(0).get("devices").asLong()).isEqualTo(1);
        JsonNode vital = aggregated.get("webVitals").get(0);
        assertThat(vital.get("metric").asText()).isEqualTo("LCP");
        assertThat(vital.get("samples").asLong()).isEqualTo(1);
        assertThat(vital.get("rating").asText()).isEqualTo("good");
        assertThat(aggregated.get("freshness").get("aggregatesComputedAt").isNull()).isFalse();
    }

    @Test
    void anUnusedFilterIsAMeasuredZeroWhileRatesWithoutASampleAreNotMeasured() throws Exception {
        JsonNode empty = dashboard("source", "nobody" + UUID.randomUUID().toString().substring(0, 6));
        // The pipeline is live (other tests stored consented web events), so counts are real zeros ...
        JsonNode first = funnel(empty, "search").get("steps").get(0);
        assertThat(first.get("count").get("status").asText()).isEqualTo("MEASURED");
        assertThat(first.get("count").get("value").asLong()).isZero();
        // ... but a rate over zero sessions is undefined, not 0 %.
        JsonNode rate = metric(empty, "searchToDetail");
        assertThat(rate.get("status").asText()).isEqualTo("NOT_MEASURED");
        assertThat(rate.get("value").isNull()).isTrue();
        assertThat(rate.get("reason").asText()).isNotBlank();
        assertThat(named(empty, "searchToDetail").get("definition").asText()).isNotBlank();
        assertThat(empty.get("collection").get("ingestionEnabled").asBoolean()).isTrue();
    }

    @Test
    void theWindowAndFiltersAreValidatedAndOnlyStaffMayRead() throws Exception {
        String admin = "Bearer " + data.sessionFor(data.user().role("ADMIN").create().id());
        LocalDate today = LocalDate.now(AnalyticsMaintenanceService.VIETNAM);
        for (MockHttpServletRequestBuilder bad : new MockHttpServletRequestBuilder[] {
                get("/api/v1/analytics/dashboard").param("from", today.toString()).param("to", today.minusDays(1).toString()),
                get("/api/v1/analytics/dashboard").param("from", today.minusDays(92).toString()),
                get("/api/v1/analytics/dashboard").param("to", today.plusDays(1).toString()),
                get("/api/v1/analytics/dashboard").param("device", "phone"),
                get("/api/v1/analytics/dashboard").param("area", "Cầu Giấy"),
                get("/api/v1/analytics/dashboard").param("source", "Google Ads")}) {
            mockMvc.perform(bad.header("Authorization", admin)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/analytics/dashboard").param("from", today.minusDays(91).toString()).header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/analytics/dashboard")
                .header("Authorization", "Bearer " + data.sessionFor(data.user().role("BROKER").create().id())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/analytics/dashboard")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private String journey(UUID listingId, String district, String source, String device, String session) {
        ObjectNode batch = json.createObjectNode().put("consent", "granted");
        ArrayNode events = batch.putArray("events");
        events.add(event("search_performed", device, session, source, null, p -> p.put("filterHash", "0123456789abcdef0123456789abcdef")
                .put("purpose", "SALE").put("resultCount", 3).put("zeroResult", false).put("engine", "database")));
        events.add(event("listing_detail_viewed", device, session, source, listingId, p -> p.put("purpose", "SALE").put("district", district)));
        events.add(event("lead_form_opened", device, session, source, listingId, p -> p.put("requestType", "VIEWING")));
        events.add(event("kyc_required_shown", device, session, source, null, p -> p.put("context", "lead_form")));
        events.add(event("web_vital", device, session, source, null, p -> p.put("metric", "LCP").put("value", 1800.0)
                .put("rating", "good").put("route", "/listings/:slug")));
        return batch.toString();
    }

    private ObjectNode event(String name, String device, String session, String source, UUID listingId, Consumer<ObjectNode> props) {
        ObjectNode event = json.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("name", name);
        event.put("v", 1);
        event.put("occurredAt", Instant.now().minusSeconds(30).toString());
        event.put("anonymousId", device);
        event.put("sessionId", session);
        event.put("device", "mobile");
        if (listingId != null) event.put("listingId", listingId.toString());
        event.set("utm", json.createObjectNode().put("source", source));
        ObjectNode properties = json.createObjectNode();
        props.accept(properties);
        event.set("properties", properties);
        return event;
    }

    private void send(String body, String bearer, String userAgent) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("User-Agent", userAgent);
        if (bearer != null) request.header("Authorization", bearer);
        mockMvc.perform(request).andExpect(status().isAccepted());
    }

    private JsonNode dashboard(String param, String value) throws Exception {
        String admin = "Bearer " + data.sessionFor(data.user().role("ADMIN").create().id());
        String response = mockMvc.perform(get("/api/v1/analytics/dashboard").param(param, value).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private static JsonNode funnel(JsonNode dashboard, String key) {
        for (JsonNode funnel : dashboard.get("funnels")) if (funnel.get("key").asText().equals(key)) return funnel;
        throw new AssertionError("no funnel " + key);
    }

    private static long[] counts(JsonNode funnel) {
        JsonNode steps = funnel.get("steps");
        long[] counts = new long[steps.size()];
        for (int i = 0; i < steps.size(); i++) {
            JsonNode count = steps.get(i).get("count");
            assertThat(count.get("status").asText()).as("step %s", steps.get(i).get("key")).isEqualTo("MEASURED");
            counts[i] = count.get("value").asLong();
        }
        return counts;
    }

    private static JsonNode named(JsonNode dashboard, String key) {
        for (JsonNode metric : dashboard.get("metrics")) if (metric.get("key").asText().equals(key)) return metric;
        throw new AssertionError("no metric " + key);
    }

    private static JsonNode metric(JsonNode dashboard, String key) {
        return named(dashboard, key).get("metric");
    }
}
