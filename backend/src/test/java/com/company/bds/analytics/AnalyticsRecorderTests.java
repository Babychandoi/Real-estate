package com.company.bds.analytics;

import com.company.bds.analytics.application.AnalyticsRecorder;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Server events are exactly-once per business fact and part of the business transaction. */
@BdsIntegrationTest
class AnalyticsRecorderTests {
    @Autowired AnalyticsRecorder recorder;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void recordsOneEventPerBusinessFactWithADeterministicId() {
        UUID leadId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        TestData.TestUser owner = data.user().role("OWNER").create();
        Map<String, Object> properties = Map.of("leadId", leadId.toString(), "minutes", 12);

        assertThat(recorder.recordServer("lead_first_response", 1, leadId.toString(), owner.id(), listingId, properties)).isTrue();
        assertThat(recorder.recordServer("lead_first_response", 1, leadId.toString(), owner.id(), listingId, properties)).isFalse();

        UUID expectedId = UUID.nameUUIDFromBytes(("server:lead_first_response:" + leadId).getBytes(StandardCharsets.UTF_8));
        assertThat(AnalyticsRecorder.serverEventId("lead_first_response", leadId.toString())).isEqualTo(expectedId);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT origin, user_id, listing_id, is_internal, is_bot, anonymous_id, properties->>'minutes' AS minutes
                FROM analytics_events WHERE event_id = ?
                """, expectedId);
        assertThat(row).containsEntry("origin", "server").containsEntry("user_id", owner.id()).containsEntry("listing_id", listingId)
                .containsEntry("is_internal", false).containsEntry("is_bot", false).containsEntry("minutes", "12");
        assertThat(row.get("anonymous_id")).isNull();

        TestData.TestUser moderator = data.user().role("MODERATOR").create();
        UUID otherLead = UUID.randomUUID();
        recorder.recordServer("lead_qualified", 1, otherLead.toString(), moderator.id(), listingId,
                Map.of("leadId", otherLead.toString(), "qualification", "QUALIFIED"));
        assertThat(jdbc.queryForObject("SELECT is_internal FROM analytics_events WHERE event_id = ?", Boolean.class,
                AnalyticsRecorder.serverEventId("lead_qualified", otherLead.toString()))).as("staff activity is internal").isTrue();
    }

    @Test
    void rolledBackBusinessTransactionRecordsNothing() {
        UUID leadId = UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            recorder.recordServer("lead_submitted", 1, leadId.toString(), null, UUID.randomUUID(),
                    Map.of("leadId", leadId.toString(), "requestType", "VIEWING"));
            throw new IllegalStateException("lead rejected");
        })).hasMessage("lead rejected");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_events WHERE event_id = ?", Integer.class,
                AnalyticsRecorder.serverEventId("lead_submitted", leadId.toString()))).isZero();
    }

    @Test
    void rejectsWebOnlyUnknownAndInvalidServerEvents() {
        UUID listing = UUID.randomUUID();
        assertThatThrownBy(() -> recorder.recordServer("search_performed", 1, "k", null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recorder.recordServer("listing_archived", 1, "k", null, listing, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recorder.recordServer("listing_published", 2, "k", null, listing, Map.of("revisionNumber", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recorder.recordServer("listing_published", 1, "k", null, null, Map.of("revisionNumber", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recorder.recordServer("listing_published", 1, "k", null, listing, Map.of("revisionNumber", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recorder.recordServer("lead_submitted", 1, "k", null, listing, Map.of("leadId", "not-a-uuid", "requestType", "VIEWING")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submittingALeadRecordsExactlyOneLeadSubmittedEventEvenWhenRetried() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").verifiedKyc().create();
        TestData.TestUser requester = data.user().verifiedKyc().create();
        TestData.TestListing listing = data.listing(owner.id()).create();
        String body = """
                {"listingId":"%s","fullName":"Khách phân tích","phone":"0987000111","requestType":"VIEWING","consentPolicy":true}
                """.formatted(listing.id());
        String bearer = "Bearer " + data.sessionFor(requester.id());
        String idempotencyKey = "analytics-" + UUID.randomUUID();

        JsonNode first = json.readTree(mockMvc.perform(post("/api/v1/public/leads").header("Authorization", bearer)
                        .header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/public/leads").header("Authorization", bearer).header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());

        String leadId = first.get("leadId").asText();
        Map<String, Object> event = jdbc.queryForMap("""
                SELECT COUNT(*) AS events, MAX(user_id::text) AS user_id, MAX(listing_id::text) AS listing_id,
                       MAX(properties->>'requestType') AS request_type
                FROM analytics_events WHERE name = 'lead_submitted' AND properties->>'leadId' = ?
                """, leadId);
        assertThat(event).containsEntry("events", 1L).containsEntry("user_id", requester.id().toString())
                .containsEntry("listing_id", listing.id().toString()).containsEntry("request_type", "VIEWING");
    }

    @Test
    void approvingARevisionRecordsListingPublishedForTheOwner() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        TestData.TestListing listing = data.listing(owner.id()).status("PENDING_REVIEW").create();

        mockMvc.perform(post("/api/v1/moderation/listings/" + listing.id() + "/approve")
                        .with(user(UUID.randomUUID().toString()).roles("MODERATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revisionId\":\"%s\"}".formatted(listing.latestRevisionId())))
                .andExpect(status().isOk());

        Map<String, Object> event = jdbc.queryForMap("""
                SELECT user_id, is_internal, properties->>'revisionNumber' AS revision FROM analytics_events WHERE event_id = ?
                """, AnalyticsRecorder.serverEventId("listing_published", listing.id() + ":1"));
        assertThat(event).containsEntry("user_id", owner.id()).containsEntry("is_internal", false).containsEntry("revision", "1");
    }
}
