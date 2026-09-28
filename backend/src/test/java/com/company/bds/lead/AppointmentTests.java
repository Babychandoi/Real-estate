package com.company.bds.lead;

import com.company.bds.lead.application.AppointmentReminderHandler;
import com.company.bds.lead.application.AppointmentService;
import com.company.bds.shared.error.ApiException;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** P-03 and D-12: slots, two-party confirmation, one effect under concurrency, overlap, reschedule, cancel, outcome, reminders. */
@BdsIntegrationTest
class AppointmentTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired ObjectMapper json;
    @Autowired AppointmentService appointments;
    @Autowired AppointmentReminderHandler reminders;

    record Fixture(TestData.TestUser owner, TestData.TestUser buyer, UUID leadId) {}

    @Test
    void ownerProposesRequesterConfirmsAndRemindersAreQueued() throws Exception {
        Fixture f = fixture();
        Instant day = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.HOURS);
        JsonNode proposal = read(post("/api/v1/leads/" + f.leadId() + "/appointments"), f.owner(), Map.of("slots", List.of(
                slot(day, 60), slot(day.plus(Duration.ofHours(2)), 45)), "note", "Gặp ở sảnh"), 201);
        assertThat(proposal.get("status").asText()).isEqualTo("PROPOSED");
        assertThat(proposal.get("slots")).hasSize(2);
        assertThat(proposal.get("awaitingMe").asBoolean()).isFalse();
        UUID appointmentId = UUID.fromString(proposal.get("id").asText());
        String slotId = proposal.get("slots").get(1).get("id").asText();

        MockHttpServletResponse self = perform(post("/api/v1/appointments/" + appointmentId + "/confirm"), f.owner(),
                Map.of("slotId", slotId, "expectedVersion", 0));
        assertThat(self.getContentAsString()).contains("CONFIRM_BY_OTHER_SIDE");

        JsonNode buyerView = read(get("/api/v1/me/inquiries/" + f.leadId() + "/appointments"), f.buyer(), null, 200);
        assertThat(buyerView.get(0).get("awaitingMe").asBoolean()).isTrue();
        JsonNode confirmed = read(post("/api/v1/appointments/" + appointmentId + "/confirm"), f.buyer(),
                Map.of("slotId", slotId, "expectedVersion", 0), 200);
        assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(Instant.parse(confirmed.get("startsAt").asText())).isEqualTo(day.plus(Duration.ofHours(2)));
        assertThat(jdbc.queryForObject("SELECT status FROM leads WHERE id = ?", String.class, f.leadId())).isEqualTo("APPOINTED");
        assertThat(jdbc.queryForObject("SELECT first_response_at IS NOT NULL FROM leads WHERE id = ?", Boolean.class, f.leadId()))
                .as("an owner-side proposal is a first response").isTrue();

        List<Map<String, Object>> jobs = jdbc.queryForList(
                "SELECT dedupe_key, run_at FROM background_jobs WHERE queue = 'appointment-reminder' AND payload ->> 'appointmentId' = ? ORDER BY run_at",
                appointmentId.toString());
        assertThat(jobs).extracting(row -> row.get("dedupe_key"))
                .containsExactly("appt:" + appointmentId + ":v1:H24", "appt:" + appointmentId + ":v1:H2");
        assertThat(((java.sql.Timestamp) jobs.get(1).get("run_at")).toInstant()).isEqualTo(day);
        for (String event : List.of("appointment_proposed", "appointment_confirmed")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_events WHERE name = ? AND properties ->> 'appointmentId' = ?",
                    Long.class, event, appointmentId.toString())).isEqualTo(1);
        }
    }

    @Test
    void slotRulesAreEnforced() {
        Fixture f = fixture();
        Instant now = Instant.now();
        Instant ok = now.plus(Duration.ofDays(1));
        assertCode("SLOT_TOO_SOON", () -> propose(f, f.owner(), List.of(new AppointmentService.SlotInput(now.plus(Duration.ofMinutes(10)), now.plus(Duration.ofMinutes(70))))));
        assertCode("SLOT_LENGTH_INVALID", () -> propose(f, f.owner(), List.of(new AppointmentService.SlotInput(ok, ok.plus(Duration.ofMinutes(5))))));
        assertCode("SLOT_LENGTH_INVALID", () -> propose(f, f.owner(), List.of(new AppointmentService.SlotInput(ok, ok.plus(Duration.ofHours(4))))));
        assertCode("SLOT_TOO_FAR", () -> propose(f, f.owner(), List.of(new AppointmentService.SlotInput(now.plus(Duration.ofDays(70)), now.plus(Duration.ofDays(70)).plus(Duration.ofHours(1))))));
        assertCode("SLOTS_OVERLAP", () -> propose(f, f.owner(), List.of(new AppointmentService.SlotInput(ok, ok.plus(Duration.ofHours(1))),
                new AppointmentService.SlotInput(ok.plus(Duration.ofMinutes(30)), ok.plus(Duration.ofMinutes(90))))));
        assertCode("SLOTS_INVALID", () -> propose(f, f.owner(), List.of()));
        List<AppointmentService.SlotInput> four = new ArrayList<>();
        for (int i = 0; i < 4; i++) four.add(new AppointmentService.SlotInput(ok.plus(Duration.ofHours(2L * i)), ok.plus(Duration.ofHours(2L * i + 1))));
        assertCode("SLOTS_INVALID", () -> propose(f, f.owner(), four));
        TestData.TestUser stranger = data.user().create();
        assertCode("LEAD_NOT_FOUND", () -> propose(f, stranger, List.of(new AppointmentService.SlotInput(ok, ok.plus(Duration.ofHours(1))))));
    }

    @Test
    void concurrentConfirmationsHaveExactlyOneEffect() throws Exception {
        Fixture f = fixture();
        Instant start = Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.MINUTES);
        AppointmentService.AppointmentView proposal = propose(f, f.buyer(), List.of(new AppointmentService.SlotInput(start, start.plus(Duration.ofHours(1)))));
        UUID slotId = proposal.slots().get(0).id();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        appointments.confirm(proposal.id(), f.owner().id(), slotId, 0L);
                        return "OK";
                    } catch (ApiException ex) {
                        return ex.code();
                    }
                }));
            }
            go.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) outcomes.add(future.get(30, TimeUnit.SECONDS));
            assertThat(outcomes).containsOnlyOnce("OK");
            assertThat(outcomes.stream().filter("APPOINTMENT_VERSION_CONFLICT"::equals).count()).isEqualTo(4);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT version FROM viewing_appointments WHERE id = ?", Long.class, proposal.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lead_events WHERE lead_id = ? AND type = 'APPOINTMENT_CONFIRMED'", Long.class, f.leadId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_jobs WHERE queue = 'appointment-reminder' AND payload ->> 'appointmentId' = ?",
                Long.class, proposal.id().toString())).isEqualTo(2);
    }

    @Test
    void anOwnerCannotConfirmTwoOverlappingVisits() {
        Fixture first = fixture();
        TestData.TestUser otherBuyer = data.user().create();
        TestData.TestListing secondListing = data.listing(first.owner().id()).create();
        Fixture second = new Fixture(first.owner(), otherBuyer, data.lead(secondListing.id()).requester(otherBuyer.id()).create());
        Instant start = Instant.now().plus(Duration.ofDays(4)).truncatedTo(ChronoUnit.HOURS);
        AppointmentService.AppointmentView a = propose(first, first.buyer(), List.of(new AppointmentService.SlotInput(start, start.plus(Duration.ofHours(1)))));
        appointments.confirm(a.id(), first.owner().id(), a.slots().get(0).id(), 0L);
        AppointmentService.AppointmentView b = propose(second, otherBuyer, List.of(
                new AppointmentService.SlotInput(start.plus(Duration.ofMinutes(30)), start.plus(Duration.ofMinutes(90)))));
        assertCode("APPOINTMENT_OVERLAP", () -> appointments.confirm(b.id(), first.owner().id(), b.slots().get(0).id(), 0L));
    }

    @Test
    void rescheduleCancelAndOutcomeFollowTheRules() throws Exception {
        Fixture f = fixture();
        Instant start = Instant.now().plus(Duration.ofDays(5)).truncatedTo(ChronoUnit.HOURS);
        AppointmentService.AppointmentView first = propose(f, f.owner(), List.of(new AppointmentService.SlotInput(start, start.plus(Duration.ofHours(1)))));
        assertCode("APPOINTMENT_VERSION_CONFLICT", () -> propose(f, f.buyer(), List.of(
                new AppointmentService.SlotInput(start.plus(Duration.ofDays(1)), start.plus(Duration.ofDays(1)).plus(Duration.ofHours(1))))));
        AppointmentService.AppointmentView counter = appointments.propose(f.leadId(), f.buyer().id(), false, List.of(
                new AppointmentService.SlotInput(start.plus(Duration.ofDays(1)), start.plus(Duration.ofDays(1)).plus(Duration.ofHours(1)))),
                "Tôi bận hôm đó", first.version());
        assertThat(jdbc.queryForObject("SELECT status FROM viewing_appointments WHERE id = ?", String.class, first.id())).isEqualTo("RESCHEDULED");
        assertThat(jdbc.queryForObject("SELECT replaced_by FROM viewing_appointments WHERE id = ?", UUID.class, first.id())).isEqualTo(counter.id());

        AppointmentService.AppointmentView confirmed = appointments.confirm(counter.id(), f.owner().id(), counter.slots().get(0).id(), 0L);
        assertCode("APPOINTMENT_NOT_STARTED", () -> appointments.recordOutcome(counter.id(), f.owner().id(), "COMPLETED", null, null, confirmed.version()));
        assertCode("CANCEL_REASON_REQUIRED", () -> appointments.cancel(counter.id(), f.buyer().id(), " ", confirmed.version()));

        // The visit has taken place: the requester cannot record the outcome, the owner side can.
        jdbc.update("UPDATE viewing_appointments SET starts_at = now() - interval '2 hours', ends_at = now() - interval '1 hour' WHERE id = ?", counter.id());
        assertCode("OWNER_SIDE_ONLY", () -> appointments.recordOutcome(counter.id(), f.buyer().id(), "COMPLETED", null, null, confirmed.version()));
        assertCode("NO_SHOW_PARTY_REQUIRED", () -> appointments.recordOutcome(counter.id(), f.owner().id(), "NO_SHOW", null, null, confirmed.version()));
        AppointmentService.AppointmentView noShow = appointments.recordOutcome(counter.id(), f.owner().id(), "NO_SHOW", "REQUESTER", "Khách không đến", confirmed.version());
        assertThat(noShow.status()).isEqualTo("NO_SHOW");
        assertThat(noShow.noShowParty()).isEqualTo("REQUESTER");

        // A new proposal after the outcome is allowed; the requester cancels it with a reason.
        Instant later = Instant.now().plus(Duration.ofDays(8)).truncatedTo(ChronoUnit.HOURS);
        AppointmentService.AppointmentView again = propose(f, f.owner(), List.of(new AppointmentService.SlotInput(later, later.plus(Duration.ofHours(1)))));
        AppointmentService.AppointmentView cancelled = appointments.cancel(again.id(), f.buyer().id(), "Đã chọn căn khác", again.version());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelReason()).isEqualTo("Đã chọn căn khác");
        JsonNode requesterHistory = read(get("/api/v1/me/inquiries/" + f.leadId() + "/history"), f.buyer(), null, 200);
        assertThat(requesterHistory.findValuesAsText("type")).contains("APPOINTMENT_CANCELLED", "APPOINTMENT_NO_SHOW", "APPOINTMENT_RESCHEDULED");
    }

    @Test
    void remindersAreSentOncePerKindAndStaleJobsAreNoOps() {
        Fixture f = fixture();
        Instant start = Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS);
        AppointmentService.AppointmentView proposal = propose(f, f.owner(), List.of(new AppointmentService.SlotInput(start, start.plus(Duration.ofHours(1)))));
        AppointmentService.AppointmentView confirmed = appointments.confirm(proposal.id(), f.buyer().id(), proposal.slots().get(0).id(), 0L);
        long before = notifications(f.buyer().id());

        assertThat(reminders.send(proposal.id(), confirmed.version(), "H24")).isTrue();
        assertThat(reminders.send(proposal.id(), confirmed.version(), "H24")).as("at-least-once delivery, exactly one reminder").isFalse();
        assertThat(notifications(f.buyer().id())).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'APPOINTMENT_REMINDER'",
                Long.class, f.owner().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT message FROM user_notifications WHERE user_id = ? AND type = 'APPOINTMENT_REMINDER'",
                String.class, f.buyer().id())).doesNotContain(f.owner().email()).doesNotContainPattern("0\\d{9}");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_jobs WHERE queue = 'email' AND dedupe_key LIKE ?",
                Long.class, "appt-reminder:" + proposal.id() + ":H24:%")).isEqualTo(2);

        assertThat(reminders.send(proposal.id(), confirmed.version() - 1, "H2")).as("stale version").isFalse();
        appointments.cancel(proposal.id(), f.owner().id(), "Chủ nhà bận việc đột xuất", confirmed.version());
        assertThat(reminders.send(proposal.id(), confirmed.version(), "H2")).as("cancelled").isFalse();
    }

    @Test
    void ownerSideTextSeenByTheRequesterCannotCarryDirectContactDetails() {
        Fixture f = fixture();
        Instant start = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.HOURS);
        List<AppointmentService.SlotInput> slots = List.of(new AppointmentService.SlotInput(start, start.plus(Duration.ofHours(1))));
        assertThatThrownBy(() -> appointments.propose(f.leadId(), f.owner().id(), false, slots, "Gọi tôi 0912 345 678", null))
                .isInstanceOf(IllegalArgumentException.class);
        AppointmentService.AppointmentView proposal = appointments.propose(f.leadId(), f.owner().id(), false, slots, "Gặp ở sảnh", null);
        assertThatThrownBy(() -> appointments.cancel(proposal.id(), f.owner().id(), "Liên hệ zalo 0912345678", proposal.version()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM viewing_appointments WHERE id = ?", String.class, proposal.id())).isEqualTo("PROPOSED");
    }

    private Fixture fixture() {
        TestData.TestUser owner = data.user().role("BROKER").create();
        TestData.TestUser buyer = data.user().create();
        UUID lead = data.lead(data.listing(owner.id()).create().id()).requester(buyer.id()).requestType("VIEWING").create();
        return new Fixture(owner, buyer, lead);
    }

    private AppointmentService.AppointmentView propose(Fixture f, TestData.TestUser actor, List<AppointmentService.SlotInput> slots) {
        return appointments.propose(f.leadId(), actor.id(), false, slots, null, null);
    }

    private long notifications(UUID userId) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'APPOINTMENT_REMINDER'", Long.class, userId);
        return value == null ? 0 : value;
    }

    private static Map<String, Object> slot(Instant start, int minutes) {
        return Map.of("startsAt", start.toString(), "endsAt", start.plus(Duration.ofMinutes(minutes)).toString());
    }

    private static void assertCode(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class).satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(code));
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, TestData.TestUser actor, Object body) throws Exception {
        request.header("Authorization", "Bearer " + data.sessionFor(actor.id()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request).andReturn().getResponse();
    }

    private JsonNode read(MockHttpServletRequestBuilder request, TestData.TestUser actor, Object body, int status) throws Exception {
        MockHttpServletResponse response = perform(request, actor, body);
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        return json.readTree(response.getContentAsString());
    }
}
