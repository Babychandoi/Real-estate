package com.company.bds.listing;

import com.company.bds.listing.application.service.ListingFreshnessService;
import com.company.bds.shared.jobs.JobWorker;
import com.company.bds.shared.mail.MailOutbox;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P-14: confirmation, expiry, reminders with the job worker, renewal and the sold-out check. */
@BdsIntegrationTest
class ListingFreshnessTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired ListingFreshnessService freshness;
    @Autowired JobWorker worker;

    String bearer(UUID userId) { return "Bearer " + data.sessionFor(userId); }

    void setExpiry(UUID id, Instant expires) {
        jdbc.update("UPDATE listings SET expires_at=?, availability_confirmed_at=? WHERE id=?",
                Timestamp.from(expires), Timestamp.from(expires.minus(Duration.ofDays(45))), id);
    }

    String listingStatus(UUID id) { return jdbc.queryForObject("SELECT status FROM listings WHERE id=?", String.class, id); }

    int notifications(UUID user, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE user_id=? AND type=?", Integer.class, user, type);
    }

    @Test
    void confirmingAvailabilityStartsA45DayPeriodAndOnlyForActiveOwnListings() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        TestData.TestListing listing = data.listing(owner.id()).create();
        setExpiry(listing.id(), Instant.now().plus(Duration.ofDays(1)));
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(post("/api/v2/me/listings/" + listing.id() + "/confirm-availability").header("Authorization", bearer(owner.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.freshness.daysUntilExpiry").value(44));
        Instant expires = jdbc.queryForObject("SELECT expires_at FROM listings WHERE id=?", Timestamp.class, listing.id()).toInstant();
        assertThat(expires).isBetween(before.plus(Duration.ofDays(45)), Instant.now().plus(Duration.ofDays(45)));

        TestData.TestListing draft = data.listing(owner.id()).status("DRAFT").create();
        mockMvc.perform(post("/api/v2/me/listings/" + draft.id() + "/confirm-availability").header("Authorization", bearer(owner.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_ACTIVE"));
    }

    @Test
    void remindersAreQueuedOncePerCycleAndSentByTheWorkerThenTheListingExpires() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        TestData.TestListing listing = data.listing(owner.id()).create();
        Instant now = Instant.now();
        Instant expires = now.plus(Duration.ofDays(6)).truncatedTo(ChronoUnit.MILLIS);
        setExpiry(listing.id(), expires);

        assertThat(freshness.scheduleReminders(now)).isGreaterThanOrEqualTo(2); // D7 now, D2 in four days
        assertThat(freshness.scheduleReminders(now)).isZero();                   // same cycle: nothing new
        worker.drain(ListingFreshnessService.REMINDER_QUEUE);
        assertThat(notifications(owner.id(), "LISTING_EXPIRY_REMINDER")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT kind FROM listing_expiry_reminders WHERE listing_id=?", String.class, listing.id()))
                .containsExactly("D7");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_jobs WHERE queue=? AND dedupe_key LIKE ?",
                Integer.class, MailOutbox.QUEUE, "%expiry:" + listing.id() + "%D7")).isEqualTo(1);
        // The 2-day reminder is due later; it is sent when due (simulate by making it due now).
        jdbc.update("UPDATE background_jobs SET run_at=now() WHERE queue=? AND payload->>'listingId'=?",
                ListingFreshnessService.REMINDER_QUEUE, listing.id().toString());
        worker.drain(ListingFreshnessService.REMINDER_QUEUE);
        assertThat(notifications(owner.id(), "LISTING_EXPIRY_REMINDER")).isEqualTo(2);
        // Sending the same reminder again (at-least-once delivery) does nothing.
        assertThat(freshness.sendReminder(listing.id(), expires, "D2")).isFalse();

        assertThat(freshness.expireDue(now)).doesNotContain(listing.id());
        assertThat(freshness.expireDue(expires.plusSeconds(1))).contains(listing.id());
        assertThat(listingStatus(listing.id())).isEqualTo("EXPIRED");
        assertThat(notifications(owner.id(), "LISTING_EXPIRED")).isEqualTo(1);
        // A reminder of the old cycle arriving late is ignored because the listing is no longer ACTIVE.
        assertThat(freshness.sendReminder(listing.id(), expires, "D7")).isFalse();
    }

    @Test
    void reconfirmingStartsANewCycleSoQueuedRemindersOfTheOldCycleAreSkipped() {
        TestData.TestUser owner = data.user().role("OWNER").create();
        TestData.TestListing listing = data.listing(owner.id()).create();
        Instant old = Instant.now().plus(Duration.ofDays(5)).truncatedTo(ChronoUnit.MILLIS);
        setExpiry(listing.id(), old);
        freshness.scheduleReminders(Instant.now());
        freshness.confirmAvailability(listing.id(), owner.id());
        worker.drain(ListingFreshnessService.REMINDER_QUEUE);
        assertThat(notifications(owner.id(), "LISTING_EXPIRY_REMINDER")).isZero();
    }

    @Test
    void expiredListingRenewsWithoutModerationOnlyWhenUnchangedAndWithin30Days() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        String auth = bearer(owner.id());
        TestData.TestListing fresh = data.listing(owner.id()).status("EXPIRED").create();
        setExpiry(fresh.id(), Instant.now().minus(Duration.ofDays(3)));
        mockMvc.perform(post("/api/v2/me/listings/" + fresh.id() + "/renew").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        TestData.TestListing old = data.listing(owner.id()).status("EXPIRED").create();
        setExpiry(old.id(), Instant.now().minus(Duration.ofDays(31)));
        mockMvc.perform(post("/api/v2/me/listings/" + old.id() + "/renew").header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RENEWAL_REQUIRES_REVIEW"));

        TestData.TestListing edited = data.listing(owner.id()).status("EXPIRED").create();
        setExpiry(edited.id(), Instant.now().minus(Duration.ofDays(2)));
        mockMvc.perform(put("/api/v1/listings/" + edited.id() + "/draft").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"purpose":"SALE","propertyType":"APARTMENT","title":"Căn hộ đã đổi giá bán mới",
                                 "priceVnd":2900000000,"areaM2":70,"addressSummary":"Cầu Giấy, Hà Nội"}"""))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/me/listings/" + edited.id() + "/renew").header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RENEWAL_REQUIRES_REVIEW"));
        // Resubmitting the edit goes back through moderation.
        mockMvc.perform(post("/api/v1/listings/" + edited.id() + "/submit").header("Authorization", auth))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    void soldReportAsksTheOwnerAndPausesAfter48HoursUnlessConfirmed() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        TestData.TestListing ignored = data.listing(owner.id()).create();
        TestData.TestListing answered = data.listing(owner.id()).create();
        for (TestData.TestListing l : new TestData.TestListing[]{ignored, answered}) {
            for (int i = 0; i < 2; i++) { // a second report while the check is open changes nothing
                mockMvc.perform(post("/api/v1/public/reports").contentType(MediaType.APPLICATION_JSON).content("""
                                {"listingId":"%s","category":"FAKE_SOLD","description":"Căn này đã bán từ tháng trước"}
                                """.formatted(l.id())))
                        .andExpect(status().isCreated());
            }
        }
        Instant due = jdbc.queryForObject("SELECT sold_check_due_at FROM listings WHERE id=?", Timestamp.class, ignored.id()).toInstant();
        assertThat(Duration.between(Instant.now(), due)).isBetween(Duration.ofHours(47), Duration.ofHours(48));
        assertThat(notifications(owner.id(), "LISTING_SOLD_CHECK")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_jobs WHERE queue=? AND payload->>'listingId'=?",
                Integer.class, ListingFreshnessService.SOLD_CHECK_QUEUE, ignored.id().toString())).isEqualTo(1);

        mockMvc.perform(post("/api/v2/me/listings/" + answered.id() + "/confirm-availability").header("Authorization", bearer(owner.id())))
                .andExpect(status().isOk());

        assertThat(freshness.pauseUnansweredSoldChecks(Instant.now())).doesNotContain(ignored.id());
        assertThat(freshness.pauseUnansweredSoldChecks(due.plusSeconds(1))).contains(ignored.id()).doesNotContain(answered.id());
        assertThat(listingStatus(ignored.id())).isEqualTo("PAUSED");
        assertThat(listingStatus(answered.id())).isEqualTo("ACTIVE");
        assertThat(notifications(owner.id(), "LISTING_AUTO_PAUSED")).isEqualTo(1);
    }
}
