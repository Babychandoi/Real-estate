package com.company.bds.notification;

import com.company.bds.notification.domain.NotificationCategory;
import com.company.bds.shared.jobs.JobWorker;
import com.company.bds.shared.mail.MailOutbox;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.MailpitClient;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Notification centre (contract §11, audit P-02/UI-13): dedupe, links, feed paging, read state, deletion, per-category
 * preferences (mandatory categories), e-mail copies with one-click unsubscribe, and isolation between accounts.
 */
@BdsIntegrationTest
class NotificationCenterTests {
    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired RealtimeNotificationService notifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JobWorker worker;

    private JsonNode call(MockHttpServletRequestBuilder request, String token, int status) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? null : json.readTree(body);
    }

    @Test
    void dedupeLinksFeedPagingReadStateAndDeletionAreScopedToTheUser() throws Exception {
        TestData.TestUser user = data.user().create();
        TestData.TestUser other = data.user().create();
        String token = data.sessionFor(user.id());
        String otherToken = data.sessionFor(other.id());

        assertThat(notifications.notify(new NotificationRequest(user.id(), "LISTING_EXPIRED", "A", "a", "/my-listings", "fact-1", false))).isPresent();
        assertThat(notifications.notify(new NotificationRequest(user.id(), "LISTING_EXPIRED", "A again", "a", null, "fact-1", false)))
                .as("same dedupe key = one notification").isEmpty();
        // Links must stay on this site.
        UUID external = notifications.notify(new NotificationRequest(user.id(), "PAYMENT_REPORTED", "B", "b",
                "https://evil.example/phish", null, false)).orElseThrow();
        UUID protocolRelative = notifications.notify(new NotificationRequest(user.id(), "PAYMENT_REPORTED", "C", "c",
                "//evil.example", null, false)).orElseThrow();
        for (int i = 0; i < 3; i++) notifications.notify(NotificationRequest.of(user.id(), "PLAN_UPGRADED", "P" + i, "p"));

        JsonNode count = call(get("/api/v1/notifications/unread-count"), token, 200);
        assertThat(count.path("count").asLong()).isEqualTo(6);
        long latest = count.path("latestSeq").asLong();

        JsonNode page1 = call(get("/api/v1/notifications/feed").param("size", "4"), token, 200);
        assertThat(page1.path("items")).hasSize(4);
        assertThat(page1.path("unreadCount").asLong()).isEqualTo(6);
        JsonNode page2 = call(get("/api/v1/notifications/feed").param("size", "4")
                .param("before", page1.path("nextBefore").asText()), token, 200);
        assertThat(page2.path("items")).hasSize(2);
        assertThat(page2.path("nextBefore").isNull()).isTrue();
        List<String> titles = new ArrayList<>();
        page1.path("items").forEach(i -> titles.add(i.path("title").asText()));
        page2.path("items").forEach(i -> titles.add(i.path("title").asText()));
        assertThat(titles).containsExactly("P2", "P1", "P0", "C", "B", "A");
        page2.path("items").forEach(item -> {
            if (item.path("id").asText().equals(external.toString()) || item.path("id").asText().equals(protocolRelative.toString())) {
                assertThat(item.path("link").isNull()).as("unsafe link dropped").isTrue();
            }
        });
        assertThat(page2.path("items").get(0).path("category").asText()).isEqualTo("ACCOUNT");
        assertThat(page2.path("items").get(1).path("category").asText()).isEqualTo("LISTINGS");

        // Another account can neither read, mark nor delete these.
        assertThat(call(get("/api/v1/notifications/unread-count"), otherToken, 200).path("count").asLong()).isZero();
        call(post("/api/v1/notifications/" + external + "/read"), otherToken, 204);
        call(delete("/api/v1/notifications/" + external), otherToken, 404);
        assertThat(call(get("/api/v1/notifications/unread-count"), token, 200).path("count").asLong()).isEqualTo(6);

        call(post("/api/v1/notifications/" + external + "/read"), token, 204);
        assertThat(call(get("/api/v1/notifications/feed").param("unread", "true").param("size", "50"), token, 200)
                .path("items")).hasSize(5);
        // read-all only up to what the user has seen: a notification arriving meanwhile stays unread.
        notifications.notify(NotificationRequest.of(user.id(), "PLAN_UPGRADED", "Mới hơn", "n"));
        JsonNode after = call(post("/api/v1/notifications/read-all").contentType(MediaType.APPLICATION_JSON)
                .content("{\"upToSeq\":" + latest + "}"), token, 200);
        assertThat(after.path("count").asLong()).isEqualTo(1);
        call(delete("/api/v1/notifications/" + external), token, 204);
        call(delete("/api/v1/notifications/" + external), token, 404);

        call(get("/api/v1/notifications/feed").param("size", "0"), token, 400);
        call(get("/api/v1/notifications/feed").param("before", "abc"), token, 400);
        call(get("/api/v1/notifications/feed").param("size", "x"), token, 400);
        call(get("/api/v1/notifications/feed"), null, 401);
    }

    @Test
    void preferencesMuteOptionalCategoriesButNeverMandatoryOnes() throws Exception {
        TestData.TestUser user = data.user().create();
        String token = data.sessionFor(user.id());
        JsonNode defaults = call(get("/api/v1/me/notification-preferences"), token, 200);
        assertThat(defaults).hasSize(NotificationCategory.values().length);
        defaults.forEach(p -> {
            if (p.path("category").asText().equals("ACCOUNT")) assertThat(p.path("mandatoryInApp").asBoolean()).isTrue();
            if (p.path("category").asText().equals("SAVED_LISTINGS")) assertThat(p.path("email").asBoolean()).isFalse();
        });

        call(put("/api/v1/me/notification-preferences").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"category\":\"ACCOUNT\",\"inApp\":false,\"email\":false}]"), token, 400);
        call(put("/api/v1/me/notification-preferences").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"category\":\"NOPE\",\"inApp\":true,\"email\":false}]"), token, 400);
        JsonNode saved = call(put("/api/v1/me/notification-preferences").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"category\":\"SHORTLIST\",\"inApp\":false,\"email\":false},{\"category\":\"ACCOUNT\",\"inApp\":true,\"email\":false}]"),
                token, 200);
        saved.forEach(p -> {
            if (p.path("category").asText().equals("SHORTLIST")) assertThat(p.path("inApp").asBoolean()).isFalse();
        });

        assertThat(notifications.notify(NotificationRequest.of(user.id(), "SHORTLIST_ITEM_ADDED", "muted", "m"))).isEmpty();
        assertThat(notifications.notify(NotificationRequest.of(user.id(), "PAYMENT_REPORTED", "always", "a"))).isPresent();
        assertThat(call(get("/api/v1/notifications/unread-count"), token, 200).path("count").asLong()).isEqualTo(1);
    }

    @Test
    void anEmailCopyRespectsThePreferenceAndCarriesAWorkingOneClickUnsubscribe() throws Exception {
        String address = MailpitClient.uniqueAddress("s6-notify");
        TestData.TestUser user = data.user().email(address).create();
        String token = data.sessionFor(user.id());
        notifications.notify(new NotificationRequest(user.id(), "LEAD_RECEIVED", "Khách mới quan tâm", "Có khách hỏi tin của bạn.",
                "/my-leads", "lead-1", true));
        worker.drain(MailOutbox.QUEUE);
        MailpitClient mailpit = new MailpitClient();
        assertThat(mailpit.subjectsTo(address)).containsExactly("Khách mới quan tâm");
        String text = mailpit.latestTextTo(address);
        assertThat(text).contains("/my-leads").contains("/unsubscribe?token=");
        assertThat(mailpit.latestHeadersTo(address).path("List-Unsubscribe-Post").get(0).asText()).isEqualTo("List-Unsubscribe=One-Click");
        Matcher m = Pattern.compile("/unsubscribe\\?token=([A-Za-z0-9_-]{43})").matcher(text);
        assertThat(m.find()).isTrue();
        String unsubscribe = m.group(1);

        JsonNode described = call(get("/api/v1/public/unsubscribe").param("token", unsubscribe), null, 200);
        assertThat(described.path("scope").asText()).isEqualTo("CATEGORY");
        assertThat(described.path("category").asText()).isEqualTo("LEADS");
        assertThat(described.path("applied").asBoolean()).isFalse();
        assertThat(call(post("/api/v1/public/unsubscribe").param("token", unsubscribe), null, 200).path("applied").asBoolean()).isTrue();
        call(post("/api/v1/public/unsubscribe").param("token", unsubscribe), null, 200);      // idempotent
        call(post("/api/v1/public/unsubscribe").param("token", unsubscribe.substring(1) + "x"), null, 404);
        call(get("/api/v1/public/unsubscribe").param("token", "short"), null, 404);

        JsonNode prefs = call(get("/api/v1/me/notification-preferences"), token, 200);
        prefs.forEach(p -> {
            if (p.path("category").asText().equals("LEADS")) {
                assertThat(p.path("email").asBoolean()).isFalse();
                assertThat(p.path("inApp").asBoolean()).as("in-app unchanged").isTrue();
            }
        });
        notifications.notify(new NotificationRequest(user.id(), "LEAD_RECEIVED", "Khách thứ hai", "…", "/my-leads", "lead-2", true));
        worker.drain(MailOutbox.QUEUE);
        assertThat(mailpit.subjectsTo(address)).as("no e-mail after unsubscribing").containsExactly("Khách mới quan tâm");
        Integer inApp = jdbc.queryForObject("SELECT count(*) FROM user_notifications WHERE user_id = ?", Integer.class, user.id());
        assertThat(inApp).isEqualTo(2);
    }
}
