package com.company.bds.analytics;

import com.company.bds.iam.application.AuthService;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * F17.4 (W6) with event ingestion enabled (test profile): the KYC wall of the lead API is recorded by the server
 * ({@code lead_kyc_blocked}, once per requester/listing/day, no consent needed), {@code lead_submitted} by the server on
 * success, and the dashboard computes the abandonment before KYC (form without the wall), after KYC (web sessions) and
 * after KYC on the server side — each with its definition.
 */
@BdsIntegrationTest
class KycAbandonmentFunnelTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;

    @Test
    void theLeadApiRecordsTheKycWallAndTheDashboardComputesAbandonmentBeforeAndAfterKyc() throws Exception {
        String source = "f174" + UUID.randomUUID().toString().substring(0, 8);
        TestData.TestUser owner = data.user().role("BROKER").verifiedKyc().create();
        TestData.TestListing listing = data.listing(owner.id()).create();
        JsonNode before = dashboard(null);

        // u1: hits the KYC wall (twice: a retry), never verifies. u2: hits the wall, verifies, then sends the lead.
        // u3: verified, sends without any wall. u4, u5: verified, open the form and leave.
        TestData.TestUser u1 = data.user().create();
        TestData.TestUser u2 = data.user().create();
        TestData.TestUser u3 = data.user().verifiedKyc().create();
        TestData.TestUser u4 = data.user().verifiedKyc().create();
        TestData.TestUser u5 = data.user().verifiedKyc().create();

        assertThat(lead(u1, listing, "0912000001")).isEqualTo(409);
        assertThat(lead(u1, listing, "0912000001")).isEqualTo(409);
        assertThat(lead(u2, listing, "0912000002")).isEqualTo(409);
        verifyKyc(u2);
        assertThat(lead(u2, listing, "0912000002")).isEqualTo(201);
        assertThat(lead(u3, listing, "0912000003")).isEqualTo(201);

        // Recorded server-side, without any consent or web collection, exactly once per requester/listing/day.
        assertThat(count("SELECT count(*) FROM analytics_events WHERE name = 'lead_kyc_blocked' AND user_id = ? AND listing_id = ? AND origin = 'server'",
                u1.id(), listing.id())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM analytics_events WHERE name = 'lead_kyc_blocked' AND user_id = ?", u2.id())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM analytics_events WHERE name = 'lead_kyc_blocked' AND user_id = ?", u3.id())).isZero();
        assertThat(count("SELECT count(*) FROM analytics_events WHERE name = 'lead_submitted' AND user_id = ?", u2.id())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM analytics_events WHERE name = 'lead_submitted' AND user_id = ?", u1.id())).isZero();

        // The web side of the same journeys, from consented sessions of each user.
        sessions(u1, listing, source, true);
        sessions(u2, listing, source, true);
        sessions(u3, listing, source, false);
        sessions(u4, listing, source, false);
        sessions(u5, listing, source, false);

        JsonNode web = dashboard(source);
        JsonNode funnel = funnel(web, "kyc");
        assertThat(funnel.get("definition").asText()).isNotBlank();
        assertThat(stepValue(funnel, 0)).as("sessions that opened the form").isEqualTo(5);
        assertThat(stepValue(funnel, 1)).as("... and were shown the KYC wall").isEqualTo(2);
        assertThat(stepValue(funnel, 2)).as("... and still sent the lead").isEqualTo(1);

        JsonNode after = metric(web, "kycAbandonment");
        assertThat(after.get("status").asText()).isEqualTo("MEASURED");
        assertThat(after.get("value").asDouble()).as("1 of 2 sessions that met the wall left").isEqualTo(50.0);
        JsonNode beforeKyc = metric(web, "leadAbandonmentWithoutKyc");
        assertThat(beforeKyc.get("status").asText()).isEqualTo("MEASURED");
        assertThat(beforeKyc.get("numerator").asLong()).isEqualTo(2);
        assertThat(beforeKyc.get("denominator").asLong()).isEqualTo(3);
        assertThat(beforeKyc.get("value").asDouble()).isEqualTo(66.7);
        assertThat(named(web, "leadAbandonmentWithoutKyc").get("definition").asText()).contains("không bị yêu cầu KYC");
        assertThat(named(web, "kycAbandonment").get("definition").asText()).isNotBlank();
        assertThat(metric(web, "kycAbandonmentServer").get("status").asText())
                .as("server data is not split by traffic source").isEqualTo("NOT_MEASURED");

        // Server side over the whole window: exactly our two blocked requesters and one later lead were added.
        JsonNode server = metric(dashboard(null), "kycAbandonmentServer");
        assertThat(server.get("status").asText()).isEqualTo("MEASURED");
        assertThat(named(dashboard(null), "kycAbandonmentServer").get("source").asText()).isEqualTo("server");
        JsonNode previous = metric(before, "kycAbandonmentServer");
        long previousBlocked = previous.path("denominator").isNull() || previous.path("denominator").isMissingNode() ? 0 : previous.get("denominator").asLong();
        long previousLeft = previous.path("numerator").isNull() || previous.path("numerator").isMissingNode() ? 0 : previous.get("numerator").asLong();
        assertThat(server.get("denominator").asLong() - previousBlocked).as("requesters blocked by KYC").isEqualTo(2);
        assertThat(server.get("numerator").asLong() - previousLeft).as("of whom never sent a lead afterwards").isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private int lead(TestData.TestUser user, TestData.TestListing listing, String phone) throws Exception {
        return mvc.perform(post("/api/v1/public/leads").header("Authorization", "Bearer " + data.sessionFor(user.id()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("listingId", listing.id(),
                        "fullName", "Khách kiểm thử", "phone", phone, "requestType", "VIEWING", "note", "Muốn xem nhà", "consentPolicy", true))))
                .andReturn().getResponse().getStatus();
    }

    private void verifyKyc(TestData.TestUser user) {
        jdbc.update("""
                INSERT INTO user_kyc_profiles(id,user_id,id_number_encrypted,id_number_lookup_hash,full_name,status,created_at,verified_at)
                VALUES (?,?,?,?,?,'VERIFIED',now(),now())
                """, UUID.randomUUID(), user.id(), "v1:test", AuthService.sha256("f174-kyc:" + user.id()), user.name());
    }

    /** One consented session: the form opened, and the KYC wall shown when {@code wall}. */
    private void sessions(TestData.TestUser user, TestData.TestListing listing, String source, boolean wall) throws Exception {
        String session = "f174-" + UUID.randomUUID();
        String device = "f174-dev-" + UUID.randomUUID();
        ObjectNode batch = json.createObjectNode().put("consent", "granted");
        ArrayNode events = batch.putArray("events");
        events.add(event("lead_form_opened", device, session, source, json.createObjectNode().put("requestType", "VIEWING"), listing.id()));
        if (wall) events.add(event("kyc_required_shown", device, session, source, json.createObjectNode().put("context", "lead_form"), null));
        assertThat(mvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(batch.toString())
                .header("User-Agent", EventIngestionTests.BROWSER).header("Authorization", "Bearer " + data.sessionFor(user.id())))
                .andReturn().getResponse().getStatus()).isEqualTo(202);
    }

    private ObjectNode event(String name, String device, String session, String source, ObjectNode properties, UUID listingId) {
        ObjectNode event = json.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("name", name);
        event.put("v", 1);
        event.put("occurredAt", Instant.now().minusSeconds(60).toString());
        event.put("anonymousId", device);
        event.put("sessionId", session);
        event.put("device", "desktop");
        if (listingId != null) event.put("listingId", listingId.toString());
        event.set("utm", json.createObjectNode().put("source", source));
        event.set("properties", properties);
        return event;
    }

    private JsonNode dashboard(String source) throws Exception {
        String admin = "Bearer " + data.sessionFor(data.user().role("ADMIN").create().id());
        var request = get("/api/v1/analytics/dashboard").header("Authorization", admin);
        if (source != null) request.param("source", source);
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString());
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static JsonNode funnel(JsonNode dashboard, String key) {
        for (JsonNode funnel : dashboard.get("funnels")) if (funnel.get("key").asText().equals(key)) return funnel;
        throw new AssertionError("no funnel " + key);
    }

    private static long stepValue(JsonNode funnel, int index) {
        JsonNode count = funnel.get("steps").get(index).get("count");
        assertThat(count.get("status").asText()).isEqualTo("MEASURED");
        return count.get("value").asLong();
    }

    private static JsonNode named(JsonNode dashboard, String key) {
        for (JsonNode metric : dashboard.get("metrics")) if (metric.get("key").asText().equals(key)) return metric;
        throw new AssertionError("no metric " + key);
    }

    private static JsonNode metric(JsonNode dashboard, String key) {
        return named(dashboard, key).get("metric");
    }
}
