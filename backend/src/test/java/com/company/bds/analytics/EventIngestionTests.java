package com.company.bds.analytics;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/v1/events (contract §5): catalog validation, consent, identity, internal/bot flags and dedupe. */
@BdsIntegrationTest
class EventIngestionTests {
    static final String BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;

    @Test
    void storesAValidBatchAndIgnoresReplays() throws Exception {
        UUID listing = UUID.randomUUID();
        ObjectNode search = event("search_performed", props -> props.put("filterHash", "0123456789abcdef0123456789abcdef")
                .put("purpose", "RENT").putNull("resultCount").put("zeroResult", true).put("engine", "database")
                .put("hasBbox", false).put("hasKeyword", true));
        search.set("page", json.createObjectNode().put("path", "/search?purpose=RENT&q=cau+giay#top").put("referrer", "https://www.google.com/"));
        search.set("utm", json.createObjectNode().put("source", "google").put("campaign", "thue-can-ho"));
        search.put("device", "mobile");
        ObjectNode detail = event("listing_detail_viewed", props -> props.put("purpose", "SALE").put("propertyType", "HOUSE").put("district", "019"));
        detail.put("listingId", listing.toString());
        detail.put("page", "/listings/nha-rieng-cau-giay");
        String body = batch("granted", search, detail);

        send(body, BROWSER).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(2)).andExpect(jsonPath("$.duplicates").value(0));
        send(body, BROWSER).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(0)).andExpect(jsonPath("$.duplicates").value(2));

        Map<String, Object> stored = row(search.get("eventId").asText());
        assertThat(stored).containsEntry("name", "search_performed").containsEntry("schema_version", 1)
                .containsEntry("origin", "web").containsEntry("device", "mobile").containsEntry("page_path", "/search")
                .containsEntry("anonymous_id", search.get("anonymousId").asText()).containsEntry("session_id", "session-0001")
                .containsEntry("is_bot", false).containsEntry("is_internal", false);
        assertThat(stored.get("user_id")).isNull();
        assertThat(json.readTree((String) stored.get("utm"))).isEqualTo(json.readTree("{\"source\":\"google\",\"campaign\":\"thue-can-ho\"}"));
        assertThat(json.readTree((String) stored.get("properties")).get("resultCount").isNull()).isTrue();
        Map<String, Object> viewed = row(detail.get("eventId").asText());
        assertThat(viewed).containsEntry("listing_id", listing).containsEntry("area_code", "019")
                .containsEntry("page_path", "/listings/nha-rieng-cau-giay");
    }

    @Test
    void withoutConsentTheServerDropsIdentifiersAndCampaignData() throws Exception {
        String bearer = "Bearer " + data.sessionFor(data.user().create().id());
        for (String consent : new String[] {"denied", null}) {
            ObjectNode event = event("compare_opened", props -> props.set("listingIds",
                    json.createArrayNode().add(UUID.randomUUID().toString()).add(UUID.randomUUID().toString())));
            event.set("utm", json.createObjectNode().put("source", "facebook"));
            event.put("page", "/compare");

            mockMvc.perform(withBody(post("/api/v1/events").header("Authorization", bearer), batch(consent, event), BROWSER))
                    .andExpect(status().isAccepted());

            Map<String, Object> stored = row(event.get("eventId").asText());
            assertThat(stored.get("anonymous_id")).as("consent=%s", consent).isNull();
            assertThat(stored.get("session_id")).isNull();
            assertThat(stored.get("user_id")).isNull();
            assertThat(stored.get("utm")).isNull();
            assertThat(stored).containsEntry("page_path", "/compare");
        }
    }

    @Test
    void userComesFromTheBearerTokenAndStaffTrafficIsInternal() throws Exception {
        TestData.TestUser member = data.user().create();
        ObjectNode fromMember = event("lead_form_opened", props -> props.put("requestType", "VIEWING"));
        fromMember.put("listingId", UUID.randomUUID().toString());
        mockMvc.perform(withBody(post("/api/v1/events").header("Authorization", "Bearer " + data.sessionFor(member.id())),
                batch("granted", fromMember), BROWSER)).andExpect(status().isAccepted());
        assertThat(row(fromMember.get("eventId").asText())).containsEntry("user_id", member.id()).containsEntry("is_internal", false);

        ObjectNode spoofed = event("kyc_required_shown", props -> props.put("context", "lead"));
        spoofed.put("userId", member.id().toString());
        send(batch("granted", spoofed), BROWSER).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("events[0].userId"))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"));

        ObjectNode fromModerator = event("kyc_required_shown", props -> props.put("context", "lead"));
        mockMvc.perform(withBody(post("/api/v1/events").with(user(UUID.randomUUID().toString()).roles("MODERATOR")),
                batch("denied", fromModerator), BROWSER)).andExpect(status().isAccepted());
        assertThat(row(fromModerator.get("eventId").asText())).containsEntry("is_internal", true);
    }

    @Test
    void automatedUserAgentsAreFlaggedAsBots() throws Exception {
        Map<String, Boolean> expectations = new java.util.LinkedHashMap<>();
        expectations.put(BROWSER, false);
        expectations.put("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1", false);
        expectations.put("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", true);
        expectations.put("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/129.0 Safari/537.36", true);
        expectations.put("curl/8.7.1", true);
        expectations.put(null, true);
        for (Map.Entry<String, Boolean> expectation : expectations.entrySet()) {
            ObjectNode vital = event("web_vital", props -> props.put("metric", "LCP").put("value", 1840.5).put("rating", "good")
                    .put("route", "/listings/:slug"));
            send(batch("granted", vital), expectation.getKey()).andExpect(status().isAccepted());
            assertThat(row(vital.get("eventId").asText())).as("User-Agent %s", expectation.getKey())
                    .containsEntry("is_bot", expectation.getValue());
        }
    }

    @Test
    void rejectsEventsOutsideTheCatalogAndStoresNothingOfTheBatch() throws Exception {
        expectRejected(event("page_scrolled", props -> { }), "events[0].name", "UNKNOWN_EVENT");
        expectRejected(event("lead_submitted", props -> props.put("leadId", UUID.randomUUID().toString()).put("requestType", "VIEWING")),
                "events[0].name", "SERVER_ONLY_EVENT");
        expectRejected(with(event("kyc_required_shown", props -> props.put("context", "lead")), e -> e.put("v", 2)),
                "events[0].v", "UNSUPPORTED_VERSION");
        expectRejected(event("kyc_required_shown", props -> props.put("context", "lead").put("phone", "0912345678")),
                "events[0].properties", "INVALID_PROPERTY");
        expectRejected(event("kyc_required_shown", props -> props.put("context", 42)), "events[0].properties", "INVALID_PROPERTY");
        expectRejected(event("kyc_required_shown", props -> { }), "events[0].properties", "INVALID_PROPERTY");
        expectRejected(event("listing_detail_viewed", props -> props.put("district", "Cầu Giấy")), "events[0].listingId", "LISTING_REQUIRED");
        expectRejected(with(event("kyc_required_shown", props -> props.put("context", "lead")),
                e -> e.put("eventId", UUID.nameUUIDFromBytes("server:x".getBytes(StandardCharsets.UTF_8)).toString())),
                "events[0].eventId", "INVALID_EVENT_ID");
        expectRejected(with(event("kyc_required_shown", props -> props.put("context", "lead")),
                e -> e.put("occurredAt", Instant.now().minus(Duration.ofDays(8)).toString())), "events[0].occurredAt", "INVALID_OCCURRED_AT");
        expectRejected(with(event("kyc_required_shown", props -> props.put("context", "lead")), e -> e.put("page", "https://evil.example/x")),
                "events[0].page", "INVALID_PAGE");
        expectRejected(with(event("kyc_required_shown", props -> props.put("context", "lead")),
                e -> e.set("utm", json.createObjectNode().put("gclid", "abc"))), "events[0].utm.gclid", "INVALID_UTM");

        ObjectNode valid = event("kyc_required_shown", props -> props.put("context", "lead"));
        ObjectNode invalid = event("kyc_required_shown", props -> props.put("context", "Lead With Spaces"));
        send(batch("granted", valid, invalid), BROWSER).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_EVENTS"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.errors[0].field").value("events[1].properties"));
        assertThat(count(valid.get("eventId").asText())).as("a rejected batch stores nothing").isZero();

        send("{\"consent\":\"maybe\",\"events\":[" + valid + "]}", BROWSER).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_CONSENT"));
        send("{\"consent\":\"granted\",\"events\":[]}", BROWSER).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_BATCH_SIZE"));
        ArrayNode tooMany = json.createArrayNode();
        for (int i = 0; i < 51; i++) tooMany.add(event("kyc_required_shown", props -> props.put("context", "lead")));
        send("{\"consent\":\"granted\",\"events\":" + tooMany + "}", BROWSER).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_BATCH_SIZE"));
        send("{\"consent\":\"granted\",\"events\":[", BROWSER).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MALFORMED_JSON"));
    }

    @Test
    void acceptsSendBeaconTextAndRejectsOtherMediaTypesAndOversizedBodies() throws Exception {
        ObjectNode beacon = event("kyc_required_shown", props -> props.put("context", "lead"));
        mockMvc.perform(post("/api/v1/events").contentType("text/plain;charset=UTF-8").header("User-Agent", BROWSER)
                        .content(batch("granted", beacon)))
                .andExpect(status().isAccepted());
        assertThat(count(beacon.get("eventId").asText())).isEqualTo(1);

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_XML).content("<events/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        String padding = "x".repeat(70 * 1024);
        send("{\"consent\":\"granted\",\"events\":[],\"pad\":\"" + padding + "\"}", BROWSER)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void ingestionIsPublicAndDoesNotWriteTheAuditChain() throws Exception {
        long auditBefore = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long.class);

        // Cache-Control's exact value is the shared policy for every private/never-cache path (see
        // SensitiveResponseCacheFilter, audit F10.2); this only checks that the no-store directive is present.
        send(batch("granted", event("kyc_required_shown", props -> props.put("context", "lead"))), BROWSER)
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().stringValues("Cache-Control",
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.containsString("no-store"))));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long.class)).isEqualTo(auditBefore);
    }

    // ------------------------------------------------------------------ helpers

    private ObjectNode event(String name, Consumer<ObjectNode> properties) {
        ObjectNode event = json.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("name", name);
        event.put("v", 1);
        event.put("occurredAt", Instant.now().minusSeconds(3).toString());
        event.put("anonymousId", "anon-" + UUID.randomUUID());
        event.put("sessionId", "session-0001");
        ObjectNode props = json.createObjectNode();
        properties.accept(props);
        event.set("properties", props);
        return event;
    }

    private static ObjectNode with(ObjectNode event, Consumer<ObjectNode> change) {
        change.accept(event);
        return event;
    }

    private String batch(String consent, ObjectNode... events) {
        ObjectNode body = json.createObjectNode();
        if (consent != null) body.put("consent", consent);
        ArrayNode list = body.putArray("events");
        for (ObjectNode event : events) list.add(event);
        return body.toString();
    }

    private ResultActions send(String body, String userAgent) throws Exception {
        return mockMvc.perform(withBody(post("/api/v1/events"), body, userAgent));
    }

    private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, String body, String userAgent) {
        request.contentType(MediaType.APPLICATION_JSON).content(body);
        if (userAgent != null) request.header("User-Agent", userAgent);
        return request;
    }

    private void expectRejected(ObjectNode event, String field, String code) throws Exception {
        String response = send(batch("granted", event), BROWSER).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENTS"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode errors = json.readTree(response).get("errors");
        boolean found = false;
        for (JsonNode error : errors) found |= field.equals(error.get("field").asText()) && code.equals(error.get("code").asText());
        assertThat(found).as("expected %s on %s in %s", code, field, errors).isTrue();
        assertThat(count(event.get("eventId").asText())).isZero();
    }

    private Map<String, Object> row(String eventId) {
        return jdbc.queryForMap("""
                SELECT name, schema_version, origin, device, page_path, anonymous_id, session_id, user_id, listing_id, area_code,
                       is_bot, is_internal, properties::text AS properties, utm::text AS utm
                FROM analytics_events WHERE event_id = CAST(? AS uuid)
                """, eventId);
    }

    private int count(String eventId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM analytics_events WHERE event_id = CAST(? AS uuid)", Integer.class, eventId);
    }
}
