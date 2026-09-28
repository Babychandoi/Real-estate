package com.company.bds.broker;

import com.company.bds.lead.application.AppointmentService;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** UI-08 and P-08: measured SLA, today's tasks, intake history, qualified-lead/ROI report, KYC funnel. */
@BdsIntegrationTest
class BrokerWorkspaceTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired ObjectMapper json;
    @Autowired AppointmentService appointments;

    @Test
    void slaIsMeasuredFromRecordedFirstResponsesOnly() throws Exception {
        TestData.TestUser broker = data.user().role("BROKER").create();
        TestData.TestListing listing = data.listing(broker.id()).create();
        Instant now = Instant.now();
        for (int minutes : List.of(10, 20, 90)) {
            UUID lead = data.lead(listing.id()).status("CONTACTED").createdAt(now.minus(Duration.ofHours(5))).create();
            jdbc.update("UPDATE leads SET first_response_at = created_at + make_interval(mins => ?) WHERE id = ?", minutes, lead);
        }
        data.lead(listing.id()).status("CONTACTED").createdAt(now.minus(Duration.ofHours(4))).create(); // legacy: not measured
        UUID breached = data.lead(listing.id()).createdAt(now.minus(Duration.ofHours(2))).fullName("Khách chờ lâu").create();
        UUID fresh = data.lead(listing.id()).createdAt(now.minus(Duration.ofMinutes(5))).create();
        String bearer = bearer(broker);

        JsonNode workspace = read(get("/api/v1/broker/workspace"), bearer);
        JsonNode sla = workspace.get("slaMetrics");
        assertThat(sla.get("targetMinutes").asInt()).isEqualTo(30);
        assertThat(sla.get("leads").asLong()).isEqualTo(6);
        assertThat(sla.get("measuredResponses").asLong()).isEqualTo(3);
        assertThat(sla.get("medianFirstResponseMinutes").asLong()).isEqualTo(20);
        assertThat(sla.get("withinTargetPercent").asDouble()).isEqualTo(66.7);
        assertThat(sla.get("openBreaches").asLong()).isEqualTo(1);
        JsonNode respond = workspace.get("tasks").get("respond");
        assertThat(respond.findValuesAsText("leadId")).containsExactly(breached.toString(), fresh.toString());
        assertThat(respond.get(0).get("overdue").asBoolean()).isTrue();
        assertThat(respond.get(1).get("overdue").asBoolean()).isFalse();
        assertThat(workspace.toString()).doesNotContain("phone");

        // A stricter target changes the measurement, not the facts.
        read(put("/api/v1/broker/workspace/sla").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("firstResponseMinutes", 15, "reminderEnabled", true, "dailyDigestEnabled", false))), bearer);
        JsonNode stricter = read(get("/api/v1/broker/workspace"), bearer).get("slaMetrics");
        assertThat(stricter.get("withinTargetPercent").asDouble()).isEqualTo(33.3);
        assertThat(stricter.get("openBreaches").asLong()).isEqualTo(1);

        TestData.TestUser empty = data.user().role("BROKER").create();
        JsonNode none = read(get("/api/v1/broker/workspace"), bearer(empty)).get("slaMetrics");
        assertThat(none.get("medianFirstResponseMinutes").isNull()).as("not measured is null, not 0").isTrue();
        assertThat(none.get("withinTargetPercent").isNull()).isTrue();
    }

    @Test
    void todaysTasksListAppointmentsAndProposalsAndTheIntakeHistory() throws Exception {
        TestData.TestUser broker = data.user().role("BROKER").create();
        TestData.TestUser buyer = data.user().create();
        TestData.TestListing listing = data.listing(broker.id()).create();
        UUID proposedLead = data.lead(listing.id()).requester(buyer.id()).create();
        Instant later = Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS);
        appointments.propose(proposedLead, buyer.id(), false, List.of(new AppointmentService.SlotInput(later, later.plus(Duration.ofHours(1)))), null, null);
        UUID pastLead = data.lead(listing.id()).requester(buyer.id()).create();
        AppointmentService.AppointmentView past = appointments.propose(pastLead, buyer.id(), false,
                List.of(new AppointmentService.SlotInput(later, later.plus(Duration.ofMinutes(30)))), null, null);
        // Keep the two visits apart for the overlap check, then move the confirmed one into the past.
        jdbc.update("UPDATE appointment_slots SET starts_at = starts_at + interval '3 hours', ends_at = ends_at + interval '3 hours' WHERE appointment_id = ?", past.id());
        appointments.confirm(past.id(), broker.id(), past.slots().get(0).id(), 0L);
        jdbc.update("UPDATE viewing_appointments SET starts_at = now() - interval '1 hour', ends_at = now() - interval '30 minutes' WHERE id = ?", past.id());
        jdbc.update("INSERT INTO lead_events(id, lead_id, type, actor_id, actor_side, to_status, created_at) VALUES (?,?,?,?,?,?,now())",
                UUID.randomUUID(), proposedLead, "CREATED", buyer.id(), "REQUESTER", "NEW");

        JsonNode tasks = read(get("/api/v1/broker/workspace"), bearer(broker)).get("tasks");
        assertThat(tasks.get("proposalsAwaitingMe").findValuesAsText("leadId")).containsExactly(proposedLead.toString());
        assertThat(tasks.get("outcomesToRecord").findValuesAsText("appointmentId")).containsExactly(past.id().toString());
        JsonNode history = read(get("/api/v1/broker/workspace"), bearer(broker)).get("intakeHistory");
        assertThat(history.findValuesAsText("type")).contains("CREATED", "STATUS_CHANGED");

        assertThat(perform(get("/api/v1/broker/workspace"), bearer(data.user().role("OWNER").create())).getStatus()).isEqualTo(403);
    }

    @Test
    void qualifiedLeadReportComputesRoiOnlyFromRecordedSpend() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        TestData.TestListing listing = data.listing(owner.id()).create();
        for (int i = 0; i < 4; i++) {
            UUID lead = data.lead(listing.id()).create();
            if (i < 2) jdbc.update("UPDATE leads SET qualification = 'QUALIFIED', qualification_reason = 'READY_TO_VIEW' WHERE id = ?", lead);
            if (i == 2) jdbc.update("UPDATE leads SET qualification = 'UNQUALIFIED', qualification_reason = 'JUST_BROWSING' WHERE id = ?", lead);
        }
        String bearer = bearer(owner);
        JsonNode withoutSpend = read(get("/api/v1/leads/report"), bearer);
        assertThat(withoutSpend.get("leads").asLong()).isEqualTo(4);
        assertThat(withoutSpend.get("qualified").asLong()).isEqualTo(2);
        assertThat(withoutSpend.get("unassessed").asLong()).isEqualTo(1);
        assertThat(withoutSpend.get("qualifiedPercentOfAssessed").asDouble()).isEqualTo(66.7);
        assertThat(withoutSpend.get("spendVnd").isNull()).isTrue();
        assertThat(withoutSpend.get("costPerQualifiedLeadVnd").isNull()).as("no spend recorded = no ROI, never 0").isTrue();
        assertThat(withoutSpend.get("byListing").get(0).get("leads").asLong()).isEqualTo(4);

        jdbc.update("""
                INSERT INTO package_orders(id, user_id, plan_code, amount_vnd, transfer_reference, status, reviewed_at, created_at)
                VALUES (?, ?, 'PRO', 499000, ?, 'APPROVED', ?, now())
                """, UUID.randomUUID(), owner.id(), "IT" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
                Timestamp.from(Instant.now()));
        JsonNode withSpend = read(get("/api/v1/leads/report"), bearer);
        assertThat(withSpend.get("spendVnd").asLong()).isEqualTo(499000);
        assertThat(withSpend.get("costPerQualifiedLeadVnd").asLong()).isEqualTo(249500);
        assertThat(withSpend.get("costPerLeadVnd").asLong()).isEqualTo(124750);

        LocalDate today = LocalDate.now(BrokerWorkspaceService.VIETNAM);
        assertThat(read(get("/api/v1/leads/report").param("from", today.minusDays(60).toString())
                .param("to", today.minusDays(31).toString()), bearer).get("leads").asLong()).isZero();
        assertThat(perform(get("/api/v1/leads/report").param("from", today.toString()).param("to", today.minusDays(1).toString()), bearer)
                .getStatus()).isEqualTo(400);
        assertThat(perform(get("/api/v1/leads/report").param("from", today.minusDays(400).toString()), bearer).getStatus()).isEqualTo(400);
    }

    @Test
    void kycFunnelCountsRecordedEventsAndExcludesInternalTraffic() throws Exception {
        TestData.TestUser staff = data.user().role("ADMIN").create();
        JsonNode before = read(get("/api/v1/analytics/lead-funnel"), bearer(staff));
        for (boolean internal : List.of(false, false, true)) {
            jdbc.update("""
                    INSERT INTO analytics_events(event_id, name, schema_version, occurred_at, received_at, is_internal, is_bot, origin, properties)
                    VALUES (?, 'kyc_required_shown', 1, now(), now(), ?, FALSE, 'web', '{"context":"lead_form"}'::jsonb)
                    """, UUID.randomUUID(), internal);
        }
        JsonNode after = read(get("/api/v1/analytics/lead-funnel"), bearer(staff));
        assertThat(after.get("kycRequiredShown").asLong() - before.get("kycRequiredShown").asLong()).isEqualTo(2);
        assertThat(perform(get("/api/v1/analytics/lead-funnel"), bearer(data.user().role("BROKER").create())).getStatus()).isEqualTo(403);
    }

    private String bearer(TestData.TestUser user) {
        return "Bearer " + data.sessionFor(user.id());
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        return mvc.perform(request.header("Authorization", bearer)).andReturn().getResponse();
    }

    private JsonNode read(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        MockHttpServletResponse response = perform(request, bearer);
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString());
    }
}
