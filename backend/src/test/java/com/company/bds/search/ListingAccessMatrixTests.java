package com.company.bds.search;

import com.company.bds.media.MediaKeys;
import com.company.bds.media.MediaTestImages;
import com.company.bds.shared.jobs.JobWorker;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * R-3 permission matrix (W6): anonymous / another user / owner / moderator / admin × draft, pending, active, active
 * with an unapproved edit, hidden, expired, rejected, locked and banned-owner listings, over the public v1 and v2 reads,
 * search, seller pages, the owner and admin views, public and signed media and KYC documents. Nothing private may be
 * reachable where it must not be, and trust badges must keep their scope (identity ≠ listing ownership) and validity
 * (an expired check is never shown as verified, whatever the stored flag says).
 */
@BdsIntegrationTest(properties = {
        "app.media.storage-enabled=true",
        "app.media.endpoint=${BDS_TEST_MINIO_URL:http://127.0.0.1:59000}",
        "app.media.access-key=${BDS_TEST_MINIO_USER:bds-test-media}",
        "app.media.secret-key=${BDS_TEST_MINIO_PASSWORD:bds-test-media-only}",
        "app.media.bucket=s1-media-it",
        "app.media.signing-secret=s1-media-test-signing-secret-0123456789"})
class ListingAccessMatrixTests {
    enum Actor { ANONYMOUS, OTHER_USER, OWNER, MODERATOR, ADMIN }

    /** One listing of the matrix: what it is, whether the public may see it, and the private text it must not leak. */
    /**
     * {@code privateText}: text of this listing a non-insider must never receive (never-approved title, the description of
     * a listing that is not public, the moderator's internal note); {@code privateImage}: an image only insiders may get.
     */
    record Case(String name, TestData.TestListing listing, UUID ownerId, boolean publicVisible, List<String> privateText, String imageUrl,
                String privateImage) {}

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JobWorker worker;
    SearchFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new SearchFixtures(jdbc, data);
    }

    @Test
    void everyActorSeesExactlyWhatTheyMayAcrossListingMediaSellerAndStaffEndpoints() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser owner = data.user().role("BROKER").name("Chủ tin " + token).create();
        fixtures.kyc(owner.id(), "VERIFIED", Instant.now().minus(Duration.ofDays(5)), Instant.now().plus(Duration.ofDays(500)));
        Map<Actor, String> bearer = new EnumMap<>(Actor.class);
        bearer.put(Actor.OWNER, "Bearer " + data.sessionFor(owner.id()));
        bearer.put(Actor.OTHER_USER, "Bearer " + data.sessionFor(data.user().role("BROKER").verifiedKyc().create().id()));
        bearer.put(Actor.MODERATOR, "Bearer " + data.sessionFor(data.user().role("MODERATOR").create().id()));
        bearer.put(Actor.ADMIN, "Bearer " + data.sessionFor(data.user().role("ADMIN").create().id()));

        UUID moderatorId = data.user().role("MODERATOR").create().id();
        List<Case> cases = new ArrayList<>();
        for (String status : List.of("DRAFT", "PENDING_REVIEW", "ACTIVE", "PAUSED", "EXPIRED", "REJECTED", "LOCKED")) {
            String code = token + status.toLowerCase().replace("_", "");
            String titleSecret = "Bimat" + code;
            String descriptionSecret = "Motarieng" + code;
            String noteSecret = "Ghichunoibo" + code;
            String image = image(bearer.get(Actor.OWNER));
            TestData.TestListing listing = data.listing(owner.id()).status(status).title("Nhà " + token + " " + titleSecret)
                    .mediaUrls(List.of(image)).create();
            jdbc.update("UPDATE listing_revisions SET description = ? WHERE listing_id = ?", "Mô tả " + descriptionSecret, listing.id());
            jdbc.update("""
                    INSERT INTO moderation_decisions(id, listing_id, revision_id, moderator_id, decision, reason_code, note, created_at)
                    VALUES (?, ?, ?, ?, 'AUDIT_PASSED', 'OTHER', ?, now())
                    """, UUID.randomUUID(), listing.id(), listing.latestRevisionId(), moderatorId, "Ghi chú " + noteSecret);
            boolean isPublic = status.equals("ACTIVE");
            List<String> privateText = new ArrayList<>(List.of(noteSecret, owner.email()));
            if (!isPublic) privateText.add(descriptionSecret);
            // Hidden, expired and locked listings were public once: their 410 may name the last public title (contract),
            // so only a never-approved title counts as private.
            if (List.of("DRAFT", "PENDING_REVIEW", "REJECTED").contains(status)) privateText.add(titleSecret);
            cases.add(new Case(status, listing, owner.id(), isPublic, privateText, image, isPublic ? null : image));
        }
        // ACTIVE with an edit waiting for moderation: the public sees the approved revision only, never the edit or its new image.
        String publicImage = image(bearer.get(Actor.OWNER));
        TestData.TestListing edited = data.listing(owner.id()).title("Nhà " + token + " congkhai").mediaUrls(List.of(publicImage)).create();
        String editSecret = "Suachuaduyet" + token;
        String editImage = image(bearer.get(Actor.OWNER));
        String editDescriptionSecret = "Motasua" + token;
        pendingEdit(edited, "Nhà " + token + " " + editSecret, "Mô tả " + editDescriptionSecret, editImage);
        cases.add(new Case("ACTIVE+PENDING_EDIT", edited, owner.id(), true, List.of(editSecret, editDescriptionSecret, owner.email()),
                publicImage, editImage));
        // ACTIVE listing of a seller who has since been suspended (a ban): hidden from the public at once.
        TestData.TestUser banned = data.user().role("BROKER").name("Bị khóa " + token).create();
        String bannedImage = image("Bearer " + data.sessionFor(banned.id()));
        String bannedSecret = "Bikhoa" + token;
        TestData.TestListing bannedListing = data.listing(banned.id()).title("Nhà " + token + " " + bannedSecret)
                .mediaUrls(List.of(bannedImage)).create();
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", banned.id());
        cases.add(new Case("ACTIVE/BANNED_OWNER", bannedListing, banned.id(), false, List.of(bannedSecret, banned.email()), bannedImage,
                bannedImage));

        Set<String> publicIds = new HashSet<>();
        for (Case c : cases) if (c.publicVisible()) publicIds.add(c.listing().id().toString());

        for (Case c : cases) {
            for (Actor actor : Actor.values()) {
                String who = c.name() + " as " + actor;
                boolean ownCase = actor == Actor.OWNER && c.ownerId().equals(owner.id());
                boolean staff = actor == Actor.MODERATOR || actor == Actor.ADMIN;

                // v1 detail by id and by slug: staff and the owner see every state; everyone else the public ones only.
                boolean v1Visible = staff || ownCase || c.publicVisible();
                for (String path : List.of("/api/v1/listings/" + c.listing().id(), "/api/v1/listings/by-slug/" + c.listing().slug())) {
                    MockHttpServletResponse v1 = send(actor, bearer, get(path));
                    assertThat(v1.getStatus()).as("v1 " + who + " " + path).isEqualTo(v1Visible ? 200 : 404);
                    assertThat(v1.getHeader("Cache-Control")).as("v1 detail is never stored by a shared cache: " + who).contains("no-store");
                    if (!staff && !ownCase) assertNothingPrivate(body(v1), c, "v1 " + who + " " + path);
                }

                // v2 public detail: public data only, for every caller (logged in or not).
                MockHttpServletResponse v2 = send(actor, bearer, get("/api/v2/listings/" + c.listing().id()));
                if (c.publicVisible()) assertThat(v2.getStatus()).as("v2 " + who).isEqualTo(200);
                else assertThat(v2.getStatus()).as("v2 " + who).isIn(404, 410);
                assertNothingPrivate(body(v2), c, "v2 " + who);

                // Owner workspace: the owner and staff only; a stranger cannot tell the listing exists.
                MockHttpServletResponse draft = send(actor, bearer, get("/api/v2/me/listings/" + c.listing().id() + "/draft"));
                int expectedDraft = actor == Actor.ANONYMOUS ? 401 : ownCase || staff ? 200 : 404;
                assertThat(draft.getStatus()).as("owner draft " + who).isEqualTo(expectedDraft);

                // Admin preview: admins only.
                MockHttpServletResponse preview = send(actor, bearer, get("/api/v1/admin/listings/" + c.listing().id() + "/preview"));
                assertThat(preview.getStatus()).as("admin preview " + who)
                        .isEqualTo(actor == Actor.ANONYMOUS ? 401 : actor == Actor.ADMIN ? 200 : 403);
                if (actor == Actor.ADMIN) assertThat(preview.getHeader("Cache-Control")).contains("no-store");
            }

            // Public media: served only while the listing is public; the image of an unapproved edit never is.
            assertThat(send(Actor.ANONYMOUS, bearer, get(c.imageUrl())).getStatus()).as("public media of " + c.name())
                    .isEqualTo(c.publicVisible() ? 200 : 404);
        }
        assertThat(send(Actor.ANONYMOUS, bearer, get(editImage)).getStatus()).as("image of the pending edit").isEqualTo(404);

        // Lists: search (v2, v1), seller pages (v2, v1) show exactly the public listings, to every caller.
        for (Actor actor : Actor.values()) {
            JsonNode v2 = tree(send(actor, bearer, get("/api/v2/listings/search").param("q", token).param("size", "48")));
            assertThat(ids(v2.path("items"))).as("v2 search as " + actor).containsExactlyInAnyOrderElementsOf(publicIds);
            for (Case c : cases) assertNothingPrivate(v2.toString(), c, "v2 search as " + actor);
            JsonNode v1 = tree(send(actor, bearer, get("/api/v1/listings/search").param("keyword", token).param("size", "48")));
            for (Case c : cases) assertNothingPrivate(v1.toString(), c, "v1 search as " + actor);
            assertThat(ids(v1)).as("v1 search as " + actor).containsExactlyInAnyOrderElementsOf(publicIds);
            JsonNode seller = tree(send(actor, bearer, get("/api/v2/public/sellers/" + owner.id() + "/listings").param("size", "48")));
            assertThat(ids(seller.path("items"))).as("v2 seller page as " + actor).containsExactlyInAnyOrderElementsOf(publicIds);
            JsonNode profile = tree(send(actor, bearer, get("/api/v1/public/profiles/" + owner.id() + "/listings")));
            assertThat(ids(profile)).as("v1 profile listings as " + actor).containsExactlyInAnyOrderElementsOf(publicIds);
            for (Case c : cases) assertNothingPrivate(profile.toString(), c, "v1 profile listings as " + actor);
            for (Case c : cases) assertNothingPrivate(seller.toString(), c, "v2 seller page as " + actor);
        }
        assertThat(send(Actor.ANONYMOUS, bearer, get("/api/v1/public/profiles/" + banned.id())).getStatus()).isEqualTo(404);
        assertThat(tree(send(Actor.ANONYMOUS, bearer, get("/api/v1/public/profiles/" + banned.id() + "/listings")))).isEmpty();
        assertThat(send(Actor.ANONYMOUS, bearer, get("/api/v2/public/sellers/" + banned.id())).getStatus()).isEqualTo(404);

        // Signed URLs (capability links to private images): the owner for their own, staff for any, nobody else.
        List<String> ownImages = cases.stream().filter(c -> c.ownerId().equals(owner.id())).map(Case::imageUrl).toList();
        List<String> all = new ArrayList<>(ownImages);
        all.add(editImage);
        assertThat(signed(bearer.get(Actor.OWNER), all)).containsExactlyInAnyOrderElementsOf(all);
        assertThat(signed(bearer.get(Actor.OTHER_USER), all)).isEmpty();
        assertThat(signed(bearer.get(Actor.MODERATOR), all)).containsExactlyInAnyOrderElementsOf(all);
        assertThat(mvc.perform(post("/api/v1/media/signed-urls").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("urls", all)))).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void kycDocumentsAreNeverPublicNeverSignedAndNeedARecentPasswordOrAReasonedStaffAccess() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        String ownerBearer = "Bearer " + data.sessionFor(owner.id());
        MockHttpServletResponse uploaded = mvc.perform(multipart("/api/v1/media/kyc")
                .file(new MockMultipartFile("file", "cccd", "image/jpeg", MediaTestImages.jpeg(800, 500)))
                .header("Authorization", ownerBearer)).andReturn().getResponse();
        assertThat(uploaded.getStatus()).as(body(uploaded)).isEqualTo(201);
        String key = json.readTree(body(uploaded)).get("objectKey").asText();
        String kycUrl = json.readTree(body(uploaded)).get("url").asText();

        assertThat(mvc.perform(get(MediaKeys.publicUrl(key))).andReturn().getResponse().getStatus()).isEqualTo(404);
        String admin = "Bearer " + data.sessionFor(data.user().role("ADMIN").create().id());
        for (String viewer : List.of(ownerBearer, admin)) {
            assertThat(signed(viewer, List.of(kycUrl, MediaKeys.publicUrl(key)))).as("a KYC document is never a capability URL").isEmpty();
        }
        assertThat(mvc.perform(get("/api/v1/media/kyc/" + key)).andReturn().getResponse().getStatus()).isEqualTo(401);
        String stranger = "Bearer " + data.sessionFor(data.user().role("BROKER").create().id());
        assertThat(mvc.perform(get("/api/v1/media/kyc/" + key).header("Authorization", stranger)).andReturn().getResponse().getStatus())
                .isIn(403, 404);
        assertThat(mvc.perform(get("/api/v1/media/kyc/" + key).header("Authorization", ownerBearer)).andReturn().getResponse().getStatus())
                .as("owner without re-confirming the password").isEqualTo(403);
        assertThat(mvc.perform(get("/api/v1/media/kyc/" + key).header("Authorization", admin)).andReturn().getResponse().getStatus())
                .as("staff without a reasoned, logged access").isEqualTo(403);
    }

    @Test
    void trustBadgesKeepTheirScopeAndAreNeverShownPastTheirValidity() throws Exception {
        String token = SearchFixtures.token();
        Instant now = Instant.now();
        // Seller A: identity valid. Listing 1: valid ownership check. Listing 2: the ownership check expired yesterday but
        // the daily expiry task has not run yet (the stored flag still says verified). Listing 3: no ownership check.
        TestData.TestUser a = data.user().role("OWNER").name("Người bán " + token).create();
        fixtures.kyc(a.id(), "VERIFIED", now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(300)));
        TestData.TestListing valid = data.listing(a.id()).title("Nhà " + token + " sohong").create();
        TestData.TestListing expired = data.listing(a.id()).title("Nhà " + token + " hethan").create();
        TestData.TestListing none = data.listing(a.id()).title("Nhà " + token + " chuaxacminh").create();
        fixtures.refreshOwner(a.id());
        fixtures.ownership(valid.id(), "VERIFIED_OWNER", now.minus(Duration.ofDays(10)), now.plus(Duration.ofDays(100)));
        fixtures.ownership(expired.id(), "VERIFIED_OWNER", now.minus(Duration.ofDays(200)), now.minus(Duration.ofDays(1)));
        jdbc.update("UPDATE listings SET is_verified_owner = TRUE WHERE id IN (?, ?)", valid.id(), expired.id());
        // Seller B: identity expired (status still VERIFIED with a past expiry); no ownership checks.
        TestData.TestUser b = data.user().role("OWNER").name("Người bán hết hạn " + token).create();
        fixtures.kyc(b.id(), "VERIFIED", now.minus(Duration.ofDays(800)), now.minus(Duration.ofDays(2)));
        TestData.TestListing bListing = data.listing(b.id()).title("Nhà " + token + " nguoibanb").create();
        fixtures.refreshOwner(b.id());

        Map<UUID, Boolean> ownershipBadge = new LinkedHashMap<>();
        ownershipBadge.put(valid.id(), true);
        ownershipBadge.put(expired.id(), false);
        ownershipBadge.put(none.id(), false);
        ownershipBadge.put(bListing.id(), false);
        for (Map.Entry<UUID, Boolean> entry : ownershipBadge.entrySet()) {
            String id = entry.getKey().toString();
            boolean verified = entry.getValue();
            JsonNode v1 = tree(mvc.perform(get("/api/v1/listings/" + id)).andReturn().getResponse());
            assertThat(v1.path("isVerified").asBoolean()).as("v1 ownership badge " + id).isEqualTo(verified);
            JsonNode v2 = tree(mvc.perform(get("/api/v2/listings/" + id)).andReturn().getResponse());
            assertThat(v2.path("trust").path("ownership").path("status").asText().equals("VERIFIED")).as("v2 ownership " + id).isEqualTo(verified);
        }
        assertThat(tree(mvc.perform(get("/api/v2/listings/" + expired.id())).andReturn().getResponse())
                .path("trust").path("ownership").path("status").asText()).isEqualTo("EXPIRED");
        // Identity is about the seller, never about the listing: a verified seller does not make a listing "verified".
        JsonNode noneV2 = tree(mvc.perform(get("/api/v2/listings/" + none.id())).andReturn().getResponse());
        assertThat(noneV2.path("trust").path("identity").path("status").asText()).isEqualTo("VERIFIED");
        assertThat(noneV2.path("trust").path("ownership").path("status").asText()).isNotEqualTo("VERIFIED");
        assertThat(tree(mvc.perform(get("/api/v2/listings/" + bListing.id())).andReturn().getResponse())
                .path("trust").path("identity").path("status").asText()).isEqualTo("EXPIRED");

        // Lists carry the same badges: v1 search, v1 profile listings, v2 search.
        JsonNode v1Search = tree(mvc.perform(get("/api/v1/listings/search").param("keyword", token).param("size", "48")).andReturn().getResponse());
        JsonNode v1Profile = tree(mvc.perform(get("/api/v1/public/profiles/" + a.id() + "/listings")).andReturn().getResponse());
        JsonNode v2Search = tree(mvc.perform(get("/api/v2/listings/search").param("q", token).param("size", "48")).andReturn().getResponse());
        for (Map.Entry<UUID, Boolean> entry : ownershipBadge.entrySet()) {
            String id = entry.getKey().toString();
            assertThat(find(v1Search, id).path("isVerified").asBoolean()).as("v1 search badge " + id).isEqualTo(entry.getValue());
            if (!entry.getKey().equals(bListing.id())) {
                assertThat(find(v1Profile, id).path("isVerified").asBoolean()).as("v1 profile badge " + id).isEqualTo(entry.getValue());
            }
            assertThat(find(v2Search.path("items"), id).path("trust").path("ownership").path("status").asText().equals("VERIFIED"))
                    .as("v2 search badge " + id).isEqualTo(entry.getValue());
        }

        // Seller identity: valid for A, expired for B — on both profile versions.
        assertThat(tree(mvc.perform(get("/api/v1/public/profiles/" + a.id())).andReturn().getResponse()).path("identityVerified").asBoolean()).isTrue();
        assertThat(tree(mvc.perform(get("/api/v1/public/profiles/" + b.id())).andReturn().getResponse()).path("identityVerified").asBoolean())
                .as("an expired identity check is not a verified identity").isFalse();
        assertThat(tree(mvc.perform(get("/api/v2/public/sellers/" + a.id())).andReturn().getResponse()).path("identity").path("status").asText())
                .isEqualTo("VERIFIED");
        assertThat(tree(mvc.perform(get("/api/v2/public/sellers/" + b.id())).andReturn().getResponse()).path("identity").path("status").asText())
                .isEqualTo("EXPIRED");
        // A revoked identity (stored as REJECTED + revoked_at) is not verified either.
        jdbc.update("UPDATE user_kyc_profiles SET status = 'REJECTED', revoked_at = now() WHERE user_id = ?", a.id());
        assertThat(tree(mvc.perform(get("/api/v1/public/profiles/" + a.id())).andReturn().getResponse()).path("identityVerified").asBoolean()).isFalse();
    }

    @Test
    void hidingAListingRemovesItFromEveryPublicReadAndTheServerCacheAtOnce() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser owner = data.user().role("BROKER").create();
        String ownerBearer = "Bearer " + data.sessionFor(owner.id());
        String image = image(ownerBearer);
        TestData.TestListing listing = data.listing(owner.id()).title("Nhà " + token + " ancache").mediaUrls(List.of(image)).create();
        // Warm every cache: detail (Redis), first search page (Redis ids), public image.
        assertThat(mvc.perform(get("/api/v2/listings/" + listing.slug())).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/api/v2/listings/" + listing.slug())).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(ids(tree(mvc.perform(get("/api/v2/listings/search").param("q", token)).andReturn().getResponse()).path("items")))
                .containsExactly(listing.id().toString());
        assertThat(mvc.perform(get(image)).andReturn().getResponse().getStatus()).isEqualTo(200);

        MockHttpServletResponse hidden = mvc.perform(post("/api/v1/listings/" + listing.id() + "/visibility").header("Authorization", ownerBearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}")).andReturn().getResponse();
        assertThat(hidden.getStatus()).as(body(hidden)).isEqualTo(200);

        assertThat(mvc.perform(get("/api/v2/listings/" + listing.slug())).andReturn().getResponse().getStatus()).isEqualTo(410);
        assertThat(mvc.perform(get("/api/v1/listings/" + listing.id())).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(ids(tree(mvc.perform(get("/api/v2/listings/search").param("q", token)).andReturn().getResponse()).path("items"))).isEmpty();
        assertThat(mvc.perform(get(image)).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get("/api/v1/listings/" + listing.id()).header("Authorization", ownerBearer)).andReturn().getResponse().getStatus())
                .as("the owner still sees the hidden listing").isEqualTo(200);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static void assertNothingPrivate(String body, Case c, String where) {
        for (String text : c.privateText()) assertThat(body).as(where + " leaks private text of " + c.name()).doesNotContain(text);
        if (c.privateImage() != null) {
            String key = c.privateImage().substring(c.privateImage().lastIndexOf('/') + 1);
            assertThat(body).as(where + " leaks a private image of " + c.name()).doesNotContain(key);
        }
    }

    private void pendingEdit(TestData.TestListing listing, String title, String description, String imageUrl) {
        UUID revision = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO listing_revisions(id,listing_id,revision_number,status,title,purpose,property_type,price_vnd,area_m2,
                    description,province_code,district_code,address_summary,created_at,submitted_at)
                SELECT ?, listing_id, revision_number + 1, 'SUBMITTED', ?, purpose, property_type, price_vnd + 1, area_m2,
                       ?, province_code, district_code, address_summary, ?, ?
                FROM listing_revisions WHERE id = ?
                """, revision, title, description, now, now, listing.publicRevisionId());
        jdbc.update("INSERT INTO listing_media(id,revision_id,media_url,is_primary,sort_order,created_at) VALUES (?,?,?,TRUE,0,?)",
                UUID.randomUUID(), revision, imageUrl, now);
    }

    private String image(String bearer) throws Exception {
        MockHttpServletResponse uploaded = mvc.perform(multipart("/api/v1/media/images")
                .file(new MockMultipartFile("file", "photo", "image/jpeg", MediaTestImages.jpeg(640, 480)))
                .header("Authorization", bearer)).andReturn().getResponse();
        assertThat(uploaded.getStatus()).as(body(uploaded)).isEqualTo(201);
        worker.drain("media-variants");
        return json.readTree(body(uploaded)).get("url").asText();
    }

    private Set<String> signed(String bearer, List<String> urls) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/v1/media/signed-urls").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("urls", urls)))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        Set<String> out = new HashSet<>();
        json.readTree(body(response)).path("urls").fieldNames().forEachRemaining(out::add);
        return out;
    }

    private MockHttpServletResponse send(Actor actor, Map<Actor, String> bearer, MockHttpServletRequestBuilder request) throws Exception {
        if (actor != Actor.ANONYMOUS) request.header("Authorization", bearer.get(actor));
        return mvc.perform(request).andReturn().getResponse();
    }

    private static List<String> ids(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(item -> out.add(item.path("id").asText()));
        return out;
    }

    private static JsonNode find(JsonNode array, String id) {
        for (JsonNode item : array) if (id.equals(item.path("id").asText())) return item;
        throw new AssertionError("listing " + id + " missing from " + array);
    }

    private JsonNode tree(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).as(body(response)).isEqualTo(200);
        return json.readTree(body(response));
    }

    private static String body(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
