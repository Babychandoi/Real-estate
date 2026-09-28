package com.company.bds.engagement;

import com.company.bds.engagement.application.AlertDigestService;
import com.company.bds.shared.jobs.JobWorker;
import com.company.bds.shared.mail.MailOutbox;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.MailpitClient;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Saved searches and alerts (audit P-02): the filter schema of the search API (contract §7), dedupe by filterHash,
 * matching of new listings / price drops / returns against the read model, once-only facts, alerts to favourites,
 * digests sent once even when two instances race, listings hidden before the digest left out, the unsubscribe link.
 */
@BdsIntegrationTest
class SavedSearchAlertTests {
    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JobWorker worker;
    @Autowired AlertDigestService digests;
    EngagementFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new EngagementFixtures(jdbc, worker);
        fixtures.drainChanges();   // start from a quiet queue (other suites' listings)
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, int status) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? null : json.readTree(body);
    }

    private JsonNode save(String token, String filterJson, String frequency, int status) throws Exception {
        return call(post("/api/v1/me/saved-searches").contentType(MediaType.APPLICATION_JSON)
                .content("{\"filter\":" + filterJson + ",\"frequency\":\"" + frequency + "\"}"), token, status);
    }

    private void enqueueChange(UUID listingId) {
        jdbc.queryForObject("SELECT bds_enqueue_job('engage-listing-change', ?, jsonb_build_object('listingId', ?::text))::text",
                String.class, listingId.toString(), listingId.toString());
    }

    private void makeDue(UUID searchId) {
        jdbc.update("UPDATE saved_searches SET next_digest_at = now() - interval '1 minute' WHERE id = ?", searchId);
    }

    @Test
    void theFilterIsValidatedByTheSearchSchemaAndDeduplicatedByItsHash() throws Exception {
        TestData.TestUser user = data.user().create();
        String token = data.sessionFor(user.id());
        JsonNode invalid = save(token, "{\"purpose\":\"BUY\",\"bedsMin\":\"99\",\"foo\":\"1\"}", "DAILY", 400);
        assertThat(invalid.path("code").asText()).isEqualTo("INVALID_FILTER");
        assertThat(invalid.path("errors")).hasSize(3);

        JsonNode created = save(token, "{\"purpose\":\"RENT\",\"type\":\"HOUSE,APARTMENT\",\"district\":\"005\",\"priceMax\":\"20000000\","
                + "\"cursor\":\"ignored\",\"view\":\"map\"}", "DAILY", 201);
        assertThat(created.path("filter").path("type").asText()).isEqualTo("APARTMENT,HOUSE");
        assertThat(created.path("filter").has("cursor")).isFalse();
        assertThat(created.path("query").asText()).contains("purpose=RENT").contains("type=APARTMENT%2CHOUSE");
        assertThat(created.path("filterHash").asText()).hasSize(32);
        assertThat(created.path("nextDigestAt").asText()).as("daily digest at 07:00 Vietnam time = 00:00Z").endsWith("T00:00:00Z");
        // The same search in another order is the same saved search.
        JsonNode duplicate = save(token, "{\"type\":\"APARTMENT,HOUSE\",\"purpose\":\"RENT\",\"priceMax\":\"20000000\",\"district\":\"005\"}",
                "INSTANT", 409);
        assertThat(duplicate.path("code").asText()).isEqualTo("SAVED_SEARCH_EXISTS");
        Integer events = jdbc.queryForObject("SELECT count(*) FROM analytics_events WHERE name = 'saved_search_created' AND user_id = ?",
                Integer.class, user.id());
        assertThat(events).isEqualTo(1);

        String id = created.path("id").asText();
        long version = created.path("version").asLong();
        call(patch("/api/v1/me/saved-searches/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + version + ",\"alertNew\":false,\"alertPriceDrop\":false,\"alertBackOnMarket\":false}"), token, 400);
        JsonNode updated = call(patch("/api/v1/me/saved-searches/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + version + ",\"frequency\":\"WEEKLY\",\"name\":\"Nhà thuê Cầu Giấy\"}"), token, 200);
        assertThat(updated.path("frequency").asText()).isEqualTo("WEEKLY");
        assertThat(updated.path("name").asText()).isEqualTo("Nhà thuê Cầu Giấy");
        call(patch("/api/v1/me/saved-searches/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + version + ",\"paused\":true}"), token, 409);
        TestData.TestUser other = data.user().create();
        call(delete("/api/v1/me/saved-searches/" + id), data.sessionFor(other.id()), 404);
        assertThat(call(get("/api/v1/me/saved-searches"), token, 200).path("items")).hasSize(1);
        call(delete("/api/v1/me/saved-searches/" + id), token, 204);
    }

    @Test
    void newListingsPriceDropsAndReturnsMatchOnceAndReachFavourites() throws Exception {
        TestData.TestUser seller = data.user().role("BROKER").create();
        TestData.TestUser buyer = data.user().create();
        String token = data.sessionFor(buyer.id());
        String district = "0" + (10 + (int) (Math.random() * 80));
        JsonNode search = save(token, "{\"purpose\":\"SALE\",\"type\":\"APARTMENT\",\"district\":\"" + district
                + "\",\"priceMax\":\"5000000000\"}", "INSTANT", 201);
        UUID searchId = UUID.fromString(search.path("id").asText());
        // The seller's own search on the same filter never alerts about the seller's listings.
        JsonNode sellerSearch = save(data.sessionFor(seller.id()), "{\"purpose\":\"SALE\",\"district\":\"" + district + "\"}", "INSTANT", 201);

        TestData.TestListing matching = data.listing(seller.id()).district(district, "Khu thử").price(4_000_000_000L).create();
        data.listing(seller.id()).district(district, "Khu thử").price(9_000_000_000L).create();          // too expensive
        data.listing(seller.id()).district(district, "Khu thử").propertyType("HOUSE").create();           // other type
        data.listing(seller.id()).district(district, "Khu thử").purpose("RENT").price(10_000_000L).create(); // other purpose
        fixtures.drainChanges();
        assertThat(fixtures.pendingMatches(searchId)).isEqualTo(1);
        assertThat(fixtures.pendingMatches(UUID.fromString(sellerSearch.path("id").asText()))).isZero();

        // Re-processing the same listing (another change event) records nothing new.
        enqueueChange(matching.id());
        fixtures.drainChanges();
        assertThat(fixtures.pendingMatches(searchId)).isEqualTo(1);

        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/me/saved-listings/" + matching.id()), token, 200);

        // Price drop: one more fact for the search, and a favourite notification.
        fixtures.changePrice(matching.id(), 3_600_000_000L);
        fixtures.drainChanges();
        assertThat(fixtures.pendingMatches(searchId)).isEqualTo(2);
        assertThat(fixtures.notifications(buyer.id(), "SAVED_LISTING_PRICE_DROP")).isEqualTo(1);
        // A price increase is not an alert.
        fixtures.changePrice(matching.id(), 3_900_000_000L);
        fixtures.drainChanges();
        assertThat(fixtures.pendingMatches(searchId)).isEqualTo(2);

        // Hidden, then public again: BACK_ON_MARKET once.
        fixtures.setStatus(matching.id(), "PAUSED");
        fixtures.drainChanges();
        fixtures.setStatus(matching.id(), "ACTIVE");
        fixtures.drainChanges();
        assertThat(fixtures.pendingMatches(searchId)).isEqualTo(3);
        assertThat(fixtures.notifications(buyer.id(), "SAVED_LISTING_BACK_ON_MARKET")).isEqualTo(1);
        List<String> kinds = jdbc.queryForList("SELECT kind FROM saved_search_matches WHERE saved_search_id = ? ORDER BY id",
                String.class, searchId);
        assertThat(kinds).containsExactly("NEW", "PRICE_DROP", "BACK_ON_MARKET");

        // A search created after a listing was published does not announce it as new.
        JsonNode late = save(token, "{\"purpose\":\"SALE\",\"district\":\"" + district + "\",\"bedsMin\":\"1\"}", "INSTANT", 201);
        enqueueChange(matching.id());
        fixtures.drainChanges();
        assertThat(fixtures.pendingMatches(UUID.fromString(late.path("id").asText()))).isZero();
    }

    @Test
    void aDigestIsSentOnceEvenWhenTwoInstancesRaceAndSkipsListingsHiddenMeanwhile() throws Exception {
        String address = MailpitClient.uniqueAddress("s6-digest");
        TestData.TestUser seller = data.user().role("BROKER").create();
        TestData.TestUser buyer = data.user().email(address).create();
        String token = data.sessionFor(buyer.id());
        String district = "0" + (10 + (int) (Math.random() * 80));
        JsonNode search = save(token, "{\"purpose\":\"SALE\",\"district\":\"" + district + "\"}", "INSTANT", 201);
        UUID searchId = UUID.fromString(search.path("id").asText());
        List<TestData.TestListing> listings = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            listings.add(data.listing(seller.id()).title("Căn hộ cảnh báo " + i).district(district, "Khu thử").create());
        }
        fixtures.drainChanges();
        assertThat(fixtures.pendingMatches(searchId)).isEqualTo(3);
        fixtures.setStatus(listings.get(2).id(), "PAUSED");       // hidden before the digest goes out

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> runs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            runs.add(pool.submit(() -> { start.await(); return digests.deliverDue(500); }));
        }
        start.countDown();
        int sent = 0;
        for (Future<Integer> run : runs) sent += run.get();
        pool.shutdown();
        assertThat(sent).as("one digest across both racing runs (other suites' searches may also be due)").isGreaterThanOrEqualTo(1);
        assertThat(fixtures.notifications(buyer.id(), "SAVED_SEARCH_ALERT")).isEqualTo(1);
        assertThat(fixtures.pendingMatches(searchId)).isZero();
        String message = jdbc.queryForObject("SELECT title || ' | ' || message FROM user_notifications WHERE user_id = ? AND type = 'SAVED_SEARCH_ALERT'",
                String.class, buyer.id());
        assertThat(message).startsWith("2 tin khớp").contains("2 tin mới");
        String link = jdbc.queryForObject("SELECT link FROM user_notifications WHERE user_id = ? AND type = 'SAVED_SEARCH_ALERT'",
                String.class, buyer.id());
        assertThat(link).startsWith("/search?").contains("district=" + district);

        // INSTANT is throttled: the next digest waits 15 minutes even with a new match.
        data.listing(seller.id()).district(district, "Khu thử").create();
        fixtures.drainChanges();
        digests.deliverDue(500);
        assertThat(fixtures.notifications(buyer.id(), "SAVED_SEARCH_ALERT")).isEqualTo(1);

        worker.drain(MailOutbox.QUEUE);
        MailpitClient mailpit = new MailpitClient();
        assertThat(mailpit.subjectsTo(address)).hasSize(1);
        String text = mailpit.latestTextTo(address);
        assertThat(text).contains("Căn hộ cảnh báo 0").contains("Căn hộ cảnh báo 1").doesNotContain("Căn hộ cảnh báo 2")
                .contains("/listings/").contains("Ngừng nhận cảnh báo của tìm kiếm này");
        Matcher m = Pattern.compile("/unsubscribe\\?token=([A-Za-z0-9_-]{43})").matcher(text);
        assertThat(m.find()).isTrue();
        JsonNode applied = call(post("/api/v1/public/unsubscribe").param("token", m.group(1)), null, 200);
        assertThat(applied.path("scope").asText()).isEqualTo("SAVED_SEARCH");
        assertThat(applied.path("savedSearchName").asText()).isEqualTo(search.path("name").asText());
        assertThat(jdbc.queryForObject("SELECT frequency FROM saved_searches WHERE id = ?", String.class, searchId)).isEqualTo("OFF");

        // An OFF search collects nothing more and never becomes due.
        makeDue(searchId);
        data.listing(seller.id()).district(district, "Khu thử").create();
        fixtures.drainChanges();
        digests.deliverDue(500);
        assertThat(fixtures.notifications(buyer.id(), "SAVED_SEARCH_ALERT")).isEqualTo(1);
    }
}
