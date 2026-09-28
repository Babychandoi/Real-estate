package com.company.bds.engagement;

import com.company.bds.shared.jobs.JobWorker;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.QueryCount;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Saved listings and shared shortlists (audit P-02): idempotent favourites with analytics, public listings only,
 * unavailable stubs without leaking locked titles, the cap, paging; shortlist roles, share links (rotate/revoke), public
 * view without personal data, joining, muting, optimistic rename and existence hiding.
 */
@BdsIntegrationTest
class SavedListingAndShortlistTests {
    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JobWorker worker;
    EngagementFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new EngagementFixtures(jdbc, worker);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, int status) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(request.toString() + " " + result.getResponse().getContentAsString())
                .isEqualTo(status);
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? null : json.readTree(body);
    }

    private JsonNode body(MockHttpServletRequestBuilder request, String token, int status, String content) throws Exception {
        return call(request.contentType(MediaType.APPLICATION_JSON).content(content), token, status);
    }

    private int events(String name, UUID userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM analytics_events WHERE name = ? AND user_id = ?", Integer.class, name, userId);
        return n == null ? 0 : n;
    }

    @Test
    void favouritesAreIdempotentPublicOnlyPagedAndKeepUnavailableStubs() throws Exception {
        TestData.TestUser seller = data.user().role("BROKER").name("Trần Thị Bán").create();
        TestData.TestUser buyer = data.user().create();
        String token = data.sessionFor(buyer.id());
        List<TestData.TestListing> listings = new ArrayList<>();
        for (int i = 0; i < 5; i++) listings.add(data.listing(seller.id()).title("Căn hộ yêu thích " + i).create());
        TestData.TestListing draft = data.listing(seller.id()).status("DRAFT").create();

        JsonNode first = call(put("/api/v1/me/saved-listings/" + listings.get(0).id()), token, 200);
        JsonNode again = call(put("/api/v1/me/saved-listings/" + listings.get(0).id()), token, 200);
        assertThat(again.path("savedAt").asText()).isEqualTo(first.path("savedAt").asText());
        assertThat(events("listing_favorited", buyer.id())).as("one server event per real save").isEqualTo(1);
        call(put("/api/v1/me/saved-listings/" + draft.id()), token, 404);
        call(put("/api/v1/me/saved-listings/" + UUID.randomUUID()), token, 404);
        call(put("/api/v1/me/saved-listings/" + listings.get(1).id()), null, 401);
        for (int i = 1; i < 5; i++) call(put("/api/v1/me/saved-listings/" + listings.get(i).id()), token, 200);

        // Concurrent saves of the same listing by the same user: one row, one event.
        TestData.TestListing contested = data.listing(seller.id()).create();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Callable<Integer> save = () -> mvc.perform(put("/api/v1/me/saved-listings/" + contested.id())
                    .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
            futures.add(pool.submit(save));
        }
        for (Future<Integer> f : futures) assertThat(f.get()).isEqualTo(200);
        pool.shutdown();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM saved_listings WHERE user_id = ? AND listing_id = ?", Integer.class,
                buyer.id(), contested.id())).isEqualTo(1);
        assertThat(events("listing_favorited", buyer.id())).isEqualTo(6);

        // One listing withdrawn by its owner (title kept), one locked by moderation (title withheld).
        fixtures.setStatus(listings.get(3).id(), "PAUSED");
        fixtures.setStatus(listings.get(4).id(), "LOCKED");

        JsonNode page1 = QueryCount.assertAtMost(12, () -> call(get("/api/v1/me/saved-listings").param("size", "4"), token, 200));
        assertThat(page1.path("total").asInt()).isEqualTo(6);
        assertThat(page1.path("items")).hasSize(4);
        JsonNode page2 = call(get("/api/v1/me/saved-listings").param("size", "4").param("cursor", page1.path("nextCursor").asText()), token, 200);
        assertThat(page2.path("items")).hasSize(2);
        assertThat(page2.path("nextCursor").isNull()).isTrue();
        List<JsonNode> all = new ArrayList<>();
        page1.path("items").forEach(all::add);
        page2.path("items").forEach(all::add);
        assertThat(all.get(0).path("listingId").asText()).as("newest first").isEqualTo(contested.id().toString());
        for (JsonNode item : all) {
            String id = item.path("listingId").asText();
            if (id.equals(listings.get(3).id().toString())) {
                assertThat(item.path("listing").isNull()).isTrue();
                assertThat(item.path("unavailable").path("title").asText()).isEqualTo("Căn hộ yêu thích 3");
            } else if (id.equals(listings.get(4).id().toString())) {
                assertThat(item.path("listing").isNull()).isTrue();
                assertThat(item.path("unavailable").path("title").isNull()).as("locked title withheld").isTrue();
            } else {
                assertThat(item.path("listing").path("title").asText()).startsWith("Căn hộ");
                assertThat(item.path("listing").toString()).doesNotContain("phone").doesNotContain("email");
            }
        }
        call(get("/api/v1/me/saved-listings").param("cursor", "garbage!"), token, 400);

        JsonNode ids = call(get("/api/v1/me/saved-listings/ids"), token, 200);
        assertThat(ids.path("ids")).hasSize(6);
        call(delete("/api/v1/me/saved-listings/" + listings.get(0).id()), token, 204);
        call(delete("/api/v1/me/saved-listings/" + listings.get(0).id()), token, 204);
        assertThat(events("listing_unfavorited", buyer.id())).isEqualTo(1);
        assertThat(call(get("/api/v1/me/saved-listings/ids"), token, 200).path("ids")).hasSize(5);
    }

    @Test
    void theFavouriteCapIsEnforced() throws Exception {
        TestData.TestUser seller = data.user().role("BROKER").create();
        TestData.TestUser buyer = data.user().create();
        String token = data.sessionFor(buyer.id());
        TestData.TestListing one = data.listing(seller.id()).create();
        TestData.TestListing extra = data.listing(seller.id()).create();
        // 500 saved rows (listing ids reused through a series of real listings would be slow; any listing id works for the cap).
        jdbc.update("""
                INSERT INTO saved_listings (user_id, listing_id, created_at)
                SELECT ?, l.id, now() FROM listings l WHERE l.id <> ? AND l.id <> ? LIMIT 500""", buyer.id(), one.id(), extra.id());
        int present = jdbc.queryForObject("SELECT count(*) FROM saved_listings WHERE user_id = ?", Integer.class, buyer.id());
        if (present < 500) {
            for (int i = present; i < 500; i++) {
                jdbc.update("INSERT INTO saved_listings (user_id, listing_id) VALUES (?, ?)", buyer.id(), data.listing(seller.id()).create().id());
            }
        }
        JsonNode problem = call(put("/api/v1/me/saved-listings/" + one.id()), token, 409);
        assertThat(problem.path("code").asText()).isEqualTo("SAVED_LISTINGS_LIMIT");
    }

    @Test
    void shortlistRolesShareLinksPublicViewMutingAndVersions() throws Exception {
        TestData.TestUser seller = data.user().role("BROKER").create();
        TestData.TestUser owner = data.user().name("Nguyễn Văn Chủ").create();
        TestData.TestUser editor = data.user().name("Lê Thị Sửa").create();
        TestData.TestUser viewer = data.user().name("Phạm Văn Xem").create();
        TestData.TestUser stranger = data.user().create();
        String ownerToken = data.sessionFor(owner.id());
        String editorToken = data.sessionFor(editor.id());
        String viewerToken = data.sessionFor(viewer.id());
        String strangerToken = data.sessionFor(stranger.id());
        TestData.TestListing a = data.listing(seller.id()).title("Nhà phố danh sách A").create();
        TestData.TestListing b = data.listing(seller.id()).title("Nhà phố danh sách B").create();
        TestData.TestListing hidden = data.listing(seller.id()).title("Tin sẽ ẩn").create();

        JsonNode created = body(post("/api/v1/me/shortlists"), ownerToken, 201, "{\"name\":\"  Nhà cho bố mẹ  \"}");
        String id = created.path("id").asText();
        assertThat(created.path("name").asText()).isEqualTo("Nhà cho bố mẹ");
        assertThat(created.path("role").asText()).isEqualTo("OWNER");
        body(post("/api/v1/me/shortlists"), ownerToken, 400, "{\"name\":\"\"}");
        call(put("/api/v1/me/shortlists/" + id + "/items/" + a.id()), ownerToken, 200);
        call(put("/api/v1/me/shortlists/" + id + "/items/" + hidden.id()), ownerToken, 200);

        // Strangers cannot tell the list exists.
        call(get("/api/v1/me/shortlists/" + id), strangerToken, 404);
        call(put("/api/v1/me/shortlists/" + id + "/items/" + b.id()), strangerToken, 404);

        String viewerLink = body(post("/api/v1/me/shortlists/" + id + "/share"), ownerToken, 200, "{\"role\":\"VIEWER\"}").path("token").asText();
        body(post("/api/v1/me/shortlists/" + id + "/share"), editorToken, 404, "{\"role\":\"EDITOR\"}");
        body(post("/api/v1/me/shortlists/join"), viewerToken, 200, "{\"token\":\"" + viewerLink + "\"}");
        call(put("/api/v1/me/shortlists/" + id + "/items/" + b.id()), viewerToken, 403);

        // Rotating the link invalidates the old one; the viewer stays a member.
        String editorLink = body(post("/api/v1/me/shortlists/" + id + "/share"), ownerToken, 200, "{\"role\":\"EDITOR\"}").path("token").asText();
        call(get("/api/v1/public/shortlists/" + viewerLink), null, 404);
        body(post("/api/v1/me/shortlists/join"), editorToken, 200, "{\"token\":\"" + editorLink + "\"}");
        assertThat(fixtures.notifications(owner.id(), "SHORTLIST_MEMBER_JOINED")).isEqualTo(2);

        // The viewer mutes the list; an editor's change then notifies the owner only.
        call(put("/api/v1/me/shortlists/" + id + "/mute").contentType(MediaType.APPLICATION_JSON).content("{\"muted\":true}"), viewerToken, 200);
        call(put("/api/v1/me/shortlists/" + id + "/items/" + b.id()), editorToken, 200);
        assertThat(fixtures.notifications(owner.id(), "SHORTLIST_ITEM_ADDED")).isEqualTo(1);
        assertThat(fixtures.notifications(viewer.id(), "SHORTLIST_ITEM_ADDED")).isZero();
        assertThat(fixtures.notifications(editor.id(), "SHORTLIST_ITEM_ADDED")).as("the actor is not notified").isZero();

        // Anonymous link holder: public listings only, owner's given name only, no member data.
        fixtures.setStatus(hidden.id(), "PAUSED");
        JsonNode view = call(get("/api/v1/public/shortlists/" + editorLink), null, 200);
        assertThat(view.path("name").asText()).isEqualTo("Nhà cho bố mẹ");
        assertThat(view.path("ownerGivenName").asText()).isEqualTo("Chủ");
        assertThat(view.path("items")).hasSize(2);
        assertThat(view.toString()).doesNotContain("Nguyễn").doesNotContain(owner.email()).doesNotContain("Lê Thị")
                .doesNotContain("Tin sẽ ẩn").doesNotContain(owner.id().toString());

        // Members see each other by given name only; the owner sees full names and the share state.
        JsonNode asViewer = call(get("/api/v1/me/shortlists/" + id), viewerToken, 200);
        assertThat(asViewer.path("share").isNull()).isTrue();
        assertThat(asViewer.toString()).doesNotContain("Lê Thị Sửa").contains("\"Sửa\"");
        JsonNode asOwner = call(get("/api/v1/me/shortlists/" + id), ownerToken, 200);
        assertThat(asOwner.toString()).contains("Lê Thị Sửa");
        assertThat(asOwner.path("share").path("role").asText()).isEqualTo("EDITOR");
        assertThat(asOwner.path("items")).hasSize(3);

        // Optimistic rename.
        long version = asOwner.path("version").asLong();
        body(patch("/api/v1/me/shortlists/" + id), ownerToken, 200, "{\"name\":\"Nhà cho bố mẹ 2\",\"expectedVersion\":" + version + "}");
        JsonNode conflict = body(patch("/api/v1/me/shortlists/" + id), ownerToken, 409, "{\"name\":\"X\",\"expectedVersion\":" + version + "}");
        assertThat(conflict.path("code").asText()).isEqualTo("SHORTLIST_VERSION_CONFLICT");
        body(patch("/api/v1/me/shortlists/" + id), editorToken, 403, "{\"name\":\"X\",\"expectedVersion\":" + (version + 1) + "}");

        // Revoking stops the link; leaving and removal.
        call(delete("/api/v1/me/shortlists/" + id + "/share"), ownerToken, 204);
        call(get("/api/v1/public/shortlists/" + editorLink), null, 404);
        call(get("/api/v1/public/shortlists/not-a-token"), null, 404);
        call(delete("/api/v1/me/shortlists/" + id + "/membership"), viewerToken, 204);
        call(get("/api/v1/me/shortlists/" + id), viewerToken, 404);
        call(delete("/api/v1/me/shortlists/" + id + "/members/" + editor.id()), ownerToken, 200);
        call(put("/api/v1/me/shortlists/" + id + "/items/" + a.id()), editorToken, 404);
        call(delete("/api/v1/me/shortlists/" + id + "/membership"), ownerToken, 400);
        call(delete("/api/v1/me/shortlists/" + id), ownerToken, 204);
        assertThat(call(get("/api/v1/me/shortlists"), ownerToken, 200)).isEmpty();
    }
}
