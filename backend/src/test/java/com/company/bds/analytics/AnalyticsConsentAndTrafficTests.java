package com.company.bds.analytics;

import com.company.bds.analytics.application.AnalyticsMaintenanceService;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F19.2 (S8): consent records, internal devices and the behavioural bot rule. */
@BdsIntegrationTest
class AnalyticsConsentAndTrafficTests {
    static final String BROWSER = EventIngestionTests.BROWSER;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired AnalyticsMaintenanceService maintenance;

    @Test
    void consentDecisionsAreRecordedAsProofWithTheUserFromTheTokenOnly() throws Exception {
        TestData.TestUser member = data.user().create();
        String consentId = "c-" + UUID.randomUUID();
        long auditBefore = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long.class);

        mockMvc.perform(consent("{\"consentId\":\"" + consentId + "\",\"choice\":\"granted\",\"policyVersion\":\"2026-09-28\","
                        + "\"source\":\"banner\",\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.recordId").exists())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(consent("{\"consentId\":\"" + consentId + "\",\"purpose\":\"analytics\",\"choice\":\"withdrawn\","
                        + "\"policyVersion\":\"2026-09-28\",\"source\":\"preferences\"}")
                        .header("Authorization", "Bearer " + data.sessionFor(member.id())))
                .andExpect(status().isCreated());

        var rows = jdbc.queryForList("""
                SELECT choice, source, policy_version, user_id FROM analytics_consent_records
                WHERE consent_id = ? ORDER BY recorded_at, choice DESC
                """, consentId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("choice", "granted").containsEntry("source", "banner");
        assertThat(rows.get(0).get("user_id")).as("a userId in the body is ignored").isNull();
        assertThat(rows.get(1)).containsEntry("choice", "withdrawn").containsEntry("user_id", member.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long.class)).isEqualTo(auditBefore);

        mockMvc.perform(consent("{\"consentId\":\"x\",\"choice\":\"yes\",\"policyVersion\":\"v1\",\"source\":\"popup\",\"purpose\":\"ads\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(5));
        mockMvc.perform(post("/api/v1/events/consent").contentType(MediaType.TEXT_PLAIN).content("{}")
                .header("User-Agent", BROWSER)).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void aDeviceUsedByStaffBecomesInternalIncludingItsEarlierAnonymousVisit() throws Exception {
        String device = "dev-" + UUID.randomUUID();
        ObjectNode before = event("compare_opened", device);
        send(before, null).andExpect(status().isAccepted());
        assertThat(flags(before)).containsEntry("is_internal", false);

        ObjectNode asStaff = event("compare_opened", device);
        mockMvc.perform(withBody(post("/api/v1/events").with(user(UUID.randomUUID().toString()).roles("ADMIN")), batch(asStaff)))
                .andExpect(status().isAccepted());
        assertThat(flags(asStaff)).containsEntry("is_internal", true);
        assertThat(flags(before)).as("re-marked when the staff session was seen").containsEntry("is_internal", true);

        ObjectNode after = event("compare_opened", device);
        send(after, null).andExpect(status().isAccepted());
        assertThat(flags(after)).as("signed out on the same device").containsEntry("is_internal", true);

        ObjectNode other = event("compare_opened", "dev-" + UUID.randomUUID());
        send(other, null).andExpect(status().isAccepted());
        assertThat(flags(other)).containsEntry("is_internal", false);
    }

    @Test
    void aDeviceSendingTooManyEventsPerHourIsFlaggedAsABot() throws Exception {
        String busy = "busy-" + UUID.randomUUID();
        String calm = "calm-" + UUID.randomUUID();
        insertEvents(busy, 601);
        insertEvents(calm, 30);

        assertThat(maintenance.flagBusyDevices()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_events WHERE anonymous_id = ? AND is_bot = FALSE", Long.class, busy)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_events WHERE anonymous_id = ? AND is_bot = TRUE", Long.class, calm)).isZero();
        assertThat(jdbc.queryForObject("SELECT reason FROM analytics_client_flags WHERE anonymous_id = ? AND kind = 'BOT'", String.class, busy))
                .isEqualTo("RATE_PER_HOUR");
        assertThat(maintenance.flagBusyDevices()).as("idempotent").isZero();

        ObjectNode later = event("compare_opened", busy);
        send(later, null).andExpect(status().isAccepted());
        assertThat(flags(later)).as("a flagged device stays a bot even with a browser UA").containsEntry("is_bot", true);
    }

    // ------------------------------------------------------------------ helpers

    private void insertEvents(String device, int count) {
        jdbc.update("""
                INSERT INTO analytics_events (event_id, name, schema_version, occurred_at, anonymous_id, session_id, origin, properties)
                SELECT gen_random_uuid(), 'search_results_viewed', 1, now() - (g * interval '1 second'), ?, 'sess-' || ?, 'web', '{}'::jsonb
                FROM generate_series(1, ?) g
                """, device, device, count);
    }

    private ObjectNode event(String name, String device) {
        ObjectNode event = json.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("name", name);
        event.put("v", 1);
        event.put("occurredAt", Instant.now().minusSeconds(2).toString());
        event.put("anonymousId", device);
        event.put("sessionId", "s-" + device.substring(0, 20));
        event.set("properties", json.createObjectNode().set("listingIds", json.createArrayNode().add(UUID.randomUUID().toString())));
        return event;
    }

    private String batch(ObjectNode event) {
        ObjectNode body = json.createObjectNode().put("consent", "granted");
        body.putArray("events").add(event);
        return body.toString();
    }

    private org.springframework.test.web.servlet.ResultActions send(ObjectNode event, String bearer) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/events");
        if (bearer != null) request.header("Authorization", bearer);
        return mockMvc.perform(withBody(request, batch(event)));
    }

    private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body).header("User-Agent", BROWSER);
    }

    private static MockHttpServletRequestBuilder consent(String body) {
        return post("/api/v1/events/consent").contentType(MediaType.APPLICATION_JSON).content(body).header("User-Agent", BROWSER);
    }

    private Map<String, Object> flags(ObjectNode event) {
        return jdbc.queryForMap("SELECT is_internal, is_bot FROM analytics_events WHERE event_id = CAST(? AS uuid)",
                event.get("eventId").asText());
    }
}
