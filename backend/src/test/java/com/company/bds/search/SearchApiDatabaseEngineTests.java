package com.company.bds.search;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.QueryCount;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Search API v2 on the database engine (Elasticsearch disabled in this context): envelope, keyset paging over more than
 * 250 listings for every sort, validation, cursors, server-side trust filters, rent money, suggestions, query counts,
 * detail 404/410/ETag and public-only data. Audit F02, F03.2, F04.3, F06.1/F06.2, F07, F09.1, D-03, D-06, R-3.
 */
@BdsIntegrationTest
class SearchApiDatabaseEngineTests {
    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    SearchFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new SearchFixtures(jdbc, data);
    }

    private JsonNode getJson(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode search(Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v2/listings/search");
        params.forEach(request::param);
        return getJson(request, 200);
    }

    private List<String> ids(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.path("items").forEach(item -> out.add(item.path("id").asText()));
        return out;
    }

    @Test
    void pagesThroughMoreThan250ListingsInEverySortWithoutDuplicatesOrGaps() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        Instant base = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);
        int count = 260;
        for (int i = 0; i < count; i++) {
            data.listing(seller.id()).title("Căn hộ " + token + " số " + i)
                    .price(1_000_000_000L + (i % 37) * 50_000_000L)        // many price ties
                    .area(new BigDecimal(40 + (i % 23)).toPlainString())    // many area ties
                    .createdAt(base.plusSeconds(i / 4))                     // groups of 4 share published_at
                    .create();
        }
        for (String sort : List.of("NEWEST", "PRICE_ASC", "PRICE_DESC", "AREA_DESC")) {
            List<String> seen = new ArrayList<>();
            String cursor = null;
            int pages = 0;
            do {
                MockHttpServletRequestBuilder request = get("/api/v2/listings/search").param("q", token).param("sort", sort);
                if (cursor != null) request.param("cursor", cursor);
                JsonNode page = getJson(request, 200);
                if (pages == 0) {
                    assertThat(page.path("total").path("value").asLong()).isEqualTo(count);
                    assertThat(page.path("total").path("relation").asText()).isEqualTo("eq");
                } else {
                    assertThat(page.path("total").isNull()).as("total only on the first page").isTrue();
                }
                assertThat(page.path("items").size()).isLessThanOrEqualTo(24);
                assertThat(page.path("engine").asText()).isEqualTo("database");
                assertThat(page.path("queryVersion").asText()).isEqualTo("v2");
                seen.addAll(ids(page));
                cursor = page.path("pageInfo").path("hasNext").asBoolean() ? page.path("pageInfo").path("nextCursor").asText() : null;
                pages++;
            } while (cursor != null);
            assertThat(pages).isEqualTo(11);
            assertThat(new HashSet<>(seen)).as(sort + " has no duplicates").hasSize(seen.size());
            assertThat(seen).as(sort + " returns every listing").hasSize(count);
            String order = switch (sort) {
                case "PRICE_ASC" -> "price_vnd ASC, listing_id ASC";
                case "PRICE_DESC" -> "price_vnd DESC, listing_id DESC";
                case "AREA_DESC" -> "area_m2 DESC, listing_id DESC";
                default -> "published_at DESC, listing_id DESC";
            };
            List<String> expected = jdbc.queryForList("SELECT listing_id::text FROM listing_public_read WHERE owner_id = ? ORDER BY " + order,
                    String.class, seller.id());
            assertThat(seen).as(sort + " follows the sort tuple").containsExactlyElementsOf(expected);
        }
    }

    @Test
    void invalidParametersAnswer400WithEveryError() throws Exception {
        JsonNode problem = getJson(get("/api/v2/listings/search").param("purpose", "BUY").param("priceMin", "9")
                .param("priceMax", "1").param("bbox", "100,20,110,25").param("unknown", "1"), 400);
        assertThat(problem.path("code").asText()).isEqualTo("INVALID_FILTER");
        List<String> params = new ArrayList<>();
        problem.path("errors").forEach(error -> params.add(error.path("param").asText()));
        assertThat(params).contains("purpose", "priceMax", "bbox", "unknown");
    }

    @Test
    void cursorsAreSignedAndBoundToTheirFilter() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        for (int i = 0; i < 3; i++) data.listing(seller.id()).title("Nhà " + token + " " + i).create();
        JsonNode first = search(Map.of("q", token, "size", "1", "sort", "NEWEST"));
        String cursor = first.path("pageInfo").path("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        JsonNode forged = getJson(get("/api/v2/listings/search").param("q", token).param("size", "1").param("sort", "NEWEST")
                .param("cursor", cursor.substring(0, cursor.length() - 3) + "abc"), 400);
        assertThat(forged.path("code").asText()).isEqualTo("CURSOR_INVALID");
        JsonNode otherFilter = getJson(get("/api/v2/listings/search").param("q", token).param("size", "1").param("sort", "PRICE_ASC")
                .param("cursor", cursor), 400);
        assertThat(otherFilter.path("code").asText()).isEqualTo("CURSOR_INVALID");
        JsonNode second = search(Map.of("q", token, "size", "1", "sort", "NEWEST", "cursor", cursor));
        assertThat(ids(second)).hasSize(1).doesNotContainAnyElementsOf(ids(first));
    }

    @Test
    void verifiedFilterIsServerSideAndFindsListingsBeyondTheFirstPage() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser verified = fixtures.seller("OWNER");
        TestData.TestUser expired = fixtures.seller("OWNER");
        TestData.TestUser plain = fixtures.seller("BROKER");
        fixtures.kyc(verified.id(), "VERIFIED", Instant.now().minus(Duration.ofDays(10)), Instant.now().plus(Duration.ofDays(300)));
        fixtures.kyc(expired.id(), "VERIFIED", Instant.now().minus(Duration.ofDays(800)), Instant.now().minus(Duration.ofDays(5)));
        Instant old = Instant.now().minus(Duration.ofDays(30));
        List<UUID> verifiedIds = new ArrayList<>();
        for (int i = 0; i < 6; i++) verifiedIds.add(data.listing(verified.id()).title("Căn " + token + " xác minh " + i).createdAt(old).create().id());
        UUID expiredListing = data.listing(expired.id()).title("Căn " + token + " hết hạn").createdAt(old).create().id();
        for (int i = 0; i < 50; i++) data.listing(plain.id()).title("Căn " + token + " thường " + i).create();

        JsonNode unfiltered = search(Map.of("q", token, "sort", "NEWEST"));
        assertThat(ids(unfiltered)).doesNotContainAnyElementsOf(verifiedIds.stream().map(UUID::toString).toList());

        JsonNode identity = search(Map.of("q", token, "verified", "IDENTITY"));
        assertThat(ids(identity)).containsExactlyInAnyOrderElementsOf(verifiedIds.stream().map(UUID::toString).toList());
        assertThat(identity.path("total").path("value").asLong()).isEqualTo(6);
        identity.path("items").forEach(item -> {
            assertThat(item.path("trust").path("identity").path("status").asText()).isEqualTo("VERIFIED");
            assertThat(item.path("trust").path("ownership").path("status").asText()).isEqualTo("NOT_SUBMITTED");
            assertThat(item.path("trust").path("listing").path("status").asText()).isEqualTo("CHECKED");
        });

        JsonNode expiredDetail = getJson(get("/api/v2/listings/" + expiredListing), 200);
        assertThat(expiredDetail.path("trust").path("identity").path("status").asText()).isEqualTo("EXPIRED");

        fixtures.ownership(verifiedIds.get(0), "VERIFIED_OWNER", Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(179)));
        fixtures.ownership(verifiedIds.get(1), "VERIFIED_OWNER", Instant.now().minus(Duration.ofDays(200)), Instant.now().minus(Duration.ofDays(20)));
        fixtures.ownership(verifiedIds.get(2), "PENDING", null, null);
        JsonNode ownership = search(Map.of("q", token, "verified", "OWNERSHIP"));
        assertThat(ids(ownership)).containsExactly(verifiedIds.get(0).toString());
        assertThat(ownership.path("items").get(0).path("trust").path("ownership").path("documentType").asText())
                .isEqualTo("CERTIFICATE_OF_OWNERSHIP");
        JsonNode lapsed = getJson(get("/api/v2/listings/" + verifiedIds.get(1)), 200);
        assertThat(lapsed.path("trust").path("ownership").path("status").asText()).isEqualTo("EXPIRED");
    }

    @Test
    void rentListingsCarryAMonthlyPeriodAndRentTermsSaleCarriesAUnitPrice() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("OWNER");
        TestData.TestListing rent = data.listing(seller.id()).purpose("RENT").title("Cho thuê " + token).price(14_500_000L).area("62").create();
        fixtures.revise(rent, "monthly_service_fee_vnd = ?, deposit_vnd = ?, furnishing = 'FULL', legal_status_code = 'PINK_BOOK'",
                1_200_000L, 29_000_000L);
        TestData.TestListing sale = data.listing(seller.id()).title("Bán " + token).price(3_950_000_000L).area("82").create();

        JsonNode rentPage = search(Map.of("q", token, "purpose", "RENT", "priceMax", "15000000", "furnishing", "FULL", "legal", "PINK_BOOK"));
        assertThat(ids(rentPage)).containsExactly(rent.id().toString());
        JsonNode card = rentPage.path("items").get(0);
        assertThat(card.path("price").path("amount").asLong()).isEqualTo(14_500_000L);
        assertThat(card.path("price").path("currency").asText()).isEqualTo("VND");
        assertThat(card.path("price").path("period").asText()).isEqualTo("MONTH");
        assertThat(card.path("unitPrice").isNull()).isTrue();

        JsonNode detail = getJson(get("/api/v2/listings/" + rent.slug()), 200);
        assertThat(detail.path("rentTerms").path("monthlyServiceFee").asLong()).isEqualTo(1_200_000L);
        assertThat(detail.path("rentTerms").path("deposit").asLong()).isEqualTo(29_000_000L);
        assertThat(detail.path("legal").path("label").asText()).isEqualTo("Sổ hồng");
        assertThat(detail.path("furnishing").asText()).isEqualTo("FULL");

        JsonNode salePage = search(Map.of("q", token));
        assertThat(ids(salePage)).containsExactly(sale.id().toString());
        JsonNode saleCard = salePage.path("items").get(0);
        assertThat(saleCard.path("price").path("period").isNull()).isTrue();
        assertThat(saleCard.path("unitPrice").path("amount").asLong()).isEqualTo(Math.round(3_950_000_000d / 82));
        assertThat(saleCard.path("unitPrice").path("per").asText()).isEqualTo("M2");
        assertThat(detail.path("engine").isMissingNode()).isTrue();
    }

    @Test
    void zeroResultsComeWithCountedRelaxations() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        for (int i = 0; i < 3; i++) data.listing(seller.id()).title("Đất " + token + " " + i).price(5_000_000_000L).bedrooms(2).create();
        JsonNode empty = search(Map.of("q", token, "priceMax", "1000000000", "bedsMin", "4"));
        assertThat(empty.path("items")).isEmpty();
        assertThat(empty.path("total").path("value").asLong()).isZero();
        List<String> types = new ArrayList<>();
        empty.path("suggestions").forEach(s -> types.add(s.path("type").asText()));
        // dropping one constraint alone still matches nothing here, so neither is suggested
        assertThat(types).doesNotContain("REMOVE_PRICE", "REMOVE_BEDROOMS");
        JsonNode onlyPrice = search(Map.of("q", token, "priceMax", "1000000000"));
        JsonNode suggestion = onlyPrice.path("suggestions").get(0);
        assertThat(suggestion.path("type").asText()).isIn("REMOVE_PRICE", "REMOVE_KEYWORD");
        boolean hasPrice = false;
        for (JsonNode s : onlyPrice.path("suggestions")) {
            if (s.path("type").asText().equals("REMOVE_PRICE")) {
                hasPrice = true;
                assertThat(s.path("total").path("value").asLong()).isEqualTo(3);
                assertThat(s.path("drop").toString()).contains("priceMax");
            }
        }
        assertThat(hasPrice).isTrue();
    }

    @Test
    void aPageOf24CardsAndADetailStayWithinFourQueriesWhateverTheRevisionHistory() throws Exception {
        for (int revisions : List.of(1, 10)) {
            String token = SearchFixtures.token();
            TestData.TestUser seller = fixtures.seller("BROKER");
            UUID last = null;
            for (int i = 0; i < 25; i++) {
                last = data.listing(seller.id()).title("Căn " + token + " " + i).olderRevisions(revisions - 1).media(5).create().id();
            }
            UUID detailId = last;
            JsonNode page = json.readTree(QueryCount.assertAtMost(4, () -> mvc.perform(get("/api/v2/listings/search")
                    .param("q", token).param("sort", "NEWEST")).andReturn().getResponse().getContentAsString()));
            assertThat(page.path("items").size()).isEqualTo(24);
            assertThat(page.path("items").get(0).path("imageCount").asInt()).isEqualTo(5);
            QueryCount.assertAtMost(4, () -> mvc.perform(get("/api/v2/listings/" + detailId)).andReturn());
        }
    }

    @Test
    void detailIs404ForUnknownAndDraft410ForHiddenAndRevalidatesWithETag() throws Exception {
        TestData.TestUser seller = fixtures.seller("BROKER");
        assertThat(getJson(get("/api/v2/listings/" + UUID.randomUUID()), 404).path("code").asText()).isEqualTo("LISTING_NOT_FOUND");
        TestData.TestListing draft = data.listing(seller.id()).status("DRAFT").title("Bản nháp bí mật chưa công khai").create();
        JsonNode draftProblem = getJson(get("/api/v2/listings/" + draft.slug()), 404);
        assertThat(draftProblem.toString()).doesNotContain("Bản nháp bí mật");

        TestData.TestListing listing = data.listing(seller.id()).title("Nhà phố sẽ bị ẩn").create();
        MvcResult ok = mvc.perform(get("/api/v2/listings/" + listing.slug())).andReturn();
        String etag = ok.getResponse().getHeader("ETag");
        assertThat(etag).startsWith("\"l" + listing.id());
        assertThat(mvc.perform(get("/api/v2/listings/" + listing.slug()).header("If-None-Match", etag)).andReturn()
                .getResponse().getStatus()).isEqualTo(304);

        jdbc.update("UPDATE listings SET status = 'PAUSED' WHERE id = ?", listing.id());
        JsonNode gone = getJson(get("/api/v2/listings/" + listing.slug()).header("If-None-Match", etag), 410);
        assertThat(gone.path("code").asText()).isEqualTo("LISTING_GONE");
        assertThat(gone.path("slug").asText()).isEqualTo(listing.slug());
        assertThat(gone.path("listingTitle").asText()).isEqualTo("Nhà phố sẽ bị ẩn");
        assertThat(gone.path("title").asText()).isEqualTo("Tin không còn hiển thị");
        assertThat(gone.has("description")).isFalse();
    }

    @Test
    void publicResponsesNeverCarryDraftOrPrivateData() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = data.user().role("BROKER").name("Môi giới " + token + " 0912345678").create();
        TestData.TestListing listing = data.listing(seller.id()).title("Căn hộ " + token + " công khai")
                .mediaUrls(List.of("https://images.unsplash.com/photo-public-" + token)).create();
        // a newer draft revision with other text and private media must stay invisible
        jdbc.update("""
                INSERT INTO listing_revisions(id,listing_id,revision_number,status,title,purpose,property_type,price_vnd,area_m2,description,created_at)
                VALUES (?,?,99,'DRAFT','Bản nháp riêng tư','SALE','APARTMENT',1,10,'mô tả nháp',now())
                """, UUID.randomUUID(), listing.id());
        jdbc.update("UPDATE listings SET updated_at = now() WHERE id = ?", listing.id());
        String searchBody = mvc.perform(get("/api/v2/listings/search").param("q", token)).andReturn().getResponse().getContentAsString();
        String detailBody = mvc.perform(get("/api/v2/listings/" + listing.id())).andReturn().getResponse().getContentAsString();
        for (String body : List.of(searchBody, detailBody)) {
            assertThat(body).contains("công khai").doesNotContain("Bản nháp riêng tư", "mô tả nháp", "0912345678",
                    "phone", "email", seller.email());
        }
        JsonNode detail = json.readTree(detailBody);
        assertThat(detail.path("revisionNumber").asInt()).isEqualTo(1);
        assertThat(detail.path("images").get(0).path("url").asText()).isEqualTo("https://images.unsplash.com/photo-public-" + token);
        assertThat(detail.path("location").path("precision").asText()).isEqualTo("APPROXIMATE");
        assertThat(detail.path("seller").path("role").asText()).isEqualTo("BROKER");
    }

    @Test
    void hidingALockedOrPausedListingRemovesItFromSearchImmediately() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestListing paused = data.listing(seller.id()).title("Căn " + token + " a").create();
        TestData.TestListing locked = data.listing(seller.id()).title("Căn " + token + " b").create();
        TestData.TestListing kept = data.listing(seller.id()).title("Căn " + token + " c").create();
        assertThat(ids(search(Map.of("q", token)))).hasSize(3);
        jdbc.update("UPDATE listings SET status = 'PAUSED' WHERE id = ?", paused.id());
        jdbc.update("UPDATE listings SET status = 'LOCKED' WHERE id = ?", locked.id());
        // no worker drain: the deferred trigger refreshed the read model at commit
        assertThat(ids(search(Map.of("q", token)))).containsExactly(kept.id().toString());
        jdbc.update("UPDATE users SET status = 'LOCKED' WHERE id = ?", seller.id());
        fixtures.refreshOwner(seller.id());
        assertThat(ids(search(Map.of("q", token)))).isEmpty();
    }

    @Test
    void mapAnswersPointsWhenZoomedInAndClustersOtherwise() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        for (int i = 0; i < 5; i++) data.listing(seller.id()).title("Nhà " + token + " " + i).location(21.03 + i * 0.001, 105.80 + i * 0.001).create();
        data.listing(seller.id()).title("Nhà " + token + " xa").location(21.30, 105.60).create();
        JsonNode points = getJson(get("/api/v2/listings/map").param("q", token).param("bbox", "105.79,21.02,105.82,21.05").param("zoom", "15"), 200);
        assertThat(points.path("mode").asText()).isEqualTo("points");
        assertThat(points.path("points")).hasSize(5);
        assertThat(points.path("total").path("value").asLong()).isEqualTo(5);
        assertThat(points.path("points").get(0).path("price").path("currency").asText()).isEqualTo("VND");

        JsonNode clusters = getJson(get("/api/v2/listings/map").param("q", token).param("bbox", "105.0,20.5,106.5,21.9").param("zoom", "9"), 200);
        assertThat(clusters.path("mode").asText()).isEqualTo("clusters");
        long clustered = 0;
        for (JsonNode cluster : clusters.path("clusters")) {
            clustered += cluster.path("count").asLong();
            assertThat(cluster.path("bbox")).hasSize(4);
        }
        assertThat(clustered).isEqualTo(6);

        assertThat(getJson(get("/api/v2/listings/map").param("bbox", "100,10,106,21").param("zoom", "9"), 400).path("code").asText())
                .isEqualTo("BBOX_TOO_LARGE");
        assertThat(getJson(get("/api/v2/listings/map").param("bbox", "105.79,21.02,105.82,21.05").param("zoom", "25"), 400)
                .path("code").asText()).isEqualTo("INVALID_FILTER");
        assertThat(getJson(get("/api/v2/listings/map").param("zoom", "12"), 400).path("errors").toString()).contains("bbox");
    }

    @Test
    void sellerInventoryIsPagedBeyondSixtyAndIdentityIsSeparateFromListingChecks() throws Exception {
        TestData.TestUser seller = fixtures.seller("OWNER");
        fixtures.kyc(seller.id(), "VERIFIED", Instant.now().minus(Duration.ofDays(3)), Instant.now().plus(Duration.ofDays(700)));
        List<UUID> listings = new ArrayList<>();
        for (int i = 0; i < 70; i++) listings.add(data.listing(seller.id()).title("Kho tin " + i).create().id());
        fixtures.refreshOwner(seller.id());
        fixtures.ownership(listings.get(0), "VERIFIED_OWNER", Instant.now(), Instant.now().plus(Duration.ofDays(100)));

        Set<String> seen = new HashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            MockHttpServletRequestBuilder request = get("/api/v2/public/sellers/" + seller.id() + "/listings");
            if (cursor != null) request.param("cursor", cursor);
            JsonNode page = getJson(request, 200);
            if (pages == 0) assertThat(page.path("total").path("value").asLong()).isEqualTo(70);
            seen.addAll(ids(page));
            cursor = page.path("pageInfo").path("hasNext").asBoolean() ? page.path("pageInfo").path("nextCursor").asText() : null;
            pages++;
        } while (cursor != null);
        assertThat(pages).isEqualTo(3);
        assertThat(seen).hasSize(70);

        JsonNode profile = getJson(get("/api/v2/public/sellers/" + seller.id()), 200);
        assertThat(profile.path("role").asText()).isEqualTo("OWNER");
        assertThat(profile.path("identity").path("status").asText()).isEqualTo("VERIFIED");
        assertThat(profile.path("activeListingCount").asLong()).isEqualTo(70);
        assertThat(profile.path("ownershipVerifiedListingCount").asLong()).isEqualTo(1);
        assertThat(profile.path("responseStats").isNull()).as("fewer than 5 answered leads").isTrue();

        for (int i = 0; i < 5; i++) {
            UUID lead = data.lead(listings.get(i)).createdAt(Instant.now().minus(Duration.ofHours(3))).create();
            jdbc.update("UPDATE leads SET first_response_at = created_at + interval '30 minutes' WHERE id = ?", lead);
        }
        JsonNode withStats = getJson(get("/api/v2/public/sellers/" + seller.id()), 200);
        assertThat(withStats.path("responseStats").path("sampleSize").asLong()).isEqualTo(5);
        assertThat(withStats.path("responseStats").path("medianFirstResponseMinutes").asDouble()).isEqualTo(30.0);
        assertThat(getJson(get("/api/v2/public/sellers/" + UUID.randomUUID()), 404).path("code").asText()).isEqualTo("SELLER_NOT_FOUND");
    }

    @Test
    void priceChangeAndHistoryComeFromApprovedRevisions() throws Exception {
        TestData.TestUser seller = fixtures.seller("BROKER");
        // olderRevisions(2): revision 1 at price + 20M, revision 2 at price + 10M, public revision 3 at price
        TestData.TestListing listing = data.listing(seller.id()).title("Nhà giảm giá").price(4_000_000_000L).olderRevisions(2).create();
        JsonNode detail = getJson(get("/api/v2/listings/" + listing.id()), 200);
        assertThat(detail.path("priceChange").path("previousAmount").asLong()).isEqualTo(4_010_000_000L);
        assertThat(detail.path("priceChange").path("direction").asText()).isEqualTo("DOWN");
        JsonNode history = getJson(get("/api/v2/listings/" + listing.slug() + "/price-history"), 200);
        List<Long> amounts = new ArrayList<>();
        history.path("points").forEach(p -> amounts.add(p.path("price").path("amount").asLong()));
        assertThat(amounts).containsExactly(4_020_000_000L, 4_010_000_000L, 4_000_000_000L);
        assertThat(history.path("purpose").asText()).isEqualTo("SALE");
    }

    @Test
    void similarListingsShareTypeAndPurposeAndExcludeTheListing() throws Exception {
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestListing base = data.listing(seller.id()).propertyType("VILLA").price(20_000_000_000L).district("003", "Tây Hồ, Hà Nội").create();
        TestData.TestListing near = data.listing(seller.id()).propertyType("VILLA").price(21_000_000_000L).district("003", "Tây Hồ, Hà Nội").create();
        data.listing(seller.id()).propertyType("VILLA").price(60_000_000_000L).create();
        JsonNode similar = getJson(get("/api/v2/listings/" + base.id() + "/similar").param("size", "12"), 200);
        List<String> ids = new ArrayList<>();
        similar.forEach(item -> {
            ids.add(item.path("id").asText());
            assertThat(item.path("propertyType").asText()).isEqualTo("VILLA");
            assertThat(item.path("purpose").asText()).isEqualTo("SALE");
        });
        assertThat(ids).contains(near.id().toString()).doesNotContain(base.id().toString());
        assertThat(ids.get(0)).as("same district first").isEqualTo(near.id().toString());
    }

    @Test
    void v1SearchIsADeprecatedFirstPageWrapper() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestListing listing = data.listing(seller.id()).title("Nhà " + token).create();
        MvcResult result = mvc.perform(get("/api/v1/listings/search").param("keyword", token)).andReturn();
        assertThat(result.getResponse().getHeader("Deprecation")).isEqualTo("true");
        assertThat(result.getResponse().getHeader("Link")).contains("/api/v2/listings/search");
        JsonNode array = json.readTree(result.getResponse().getContentAsString());
        assertThat(array.isArray()).isTrue();
        assertThat(array.get(0).path("id").asText()).isEqualTo(listing.id().toString());
        assertThat(json.readTree(mvc.perform(get("/api/v1/listings/search").param("keyword", token).param("page", "1"))
                .andReturn().getResponse().getContentAsString())).isEmpty();
    }

    @Test
    void contactDetailsInTheDescriptionAreNotSearchable() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestListing listing = data.listing(seller.id()).title("Nhà " + token).create();
        fixtures.revise(listing, "description = ?",
                "Liên hệ 0912 345 678 hoặc chu.nha@example.com, zalo.me/0912345678. Sổ đỏ chính chủ " + token);
        assertThat(ids(search(Map.of("q", token)))).containsExactly(listing.id().toString());
        assertThat(ids(search(Map.of("q", token + " sổ đỏ")))).containsExactly(listing.id().toString());
        for (String probe : List.of("0912 345 678", "0912345678", "chu.nha@example.com", "example", "zalo")) {
            assertThat(ids(search(Map.of("q", token + " " + probe)))).as(probe).isEmpty();
        }
        String text = jdbc.queryForObject("SELECT search_text FROM listing_public_read WHERE listing_id = ?", String.class, listing.id());
        assertThat(text).doesNotContain("0912", "345", "example", "chu nha", "zalo");
    }

    @Test
    void aBannedSellersListingsDisappearAtOnceWithoutTheJobWorker() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestListing listing = data.listing(seller.id()).title("Căn " + token + " của người bị khoá").create();
        assertThat(ids(search(Map.of("q", token)))).hasSize(1);
        // the worker is not running in tests: nothing refreshes the read model after this update
        jdbc.update("UPDATE users SET status = 'LOCKED' WHERE id = ?", seller.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listing_public_read WHERE listing_id = ?", Long.class, listing.id()))
                .as("row still in the read model").isEqualTo(1);
        assertThat(ids(search(Map.of("q", token)))).isEmpty();
        JsonNode gone = getJson(get("/api/v2/listings/" + listing.slug()), 410);
        assertThat(gone.has("listingTitle")).as("no title for a banned seller").isFalse();
        assertThat(gone.toString()).doesNotContain(token);
    }

    @Test
    void aModerationLockedListingIs410WithoutItsTitle() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestListing listing = data.listing(seller.id()).title("Tin " + token + " vi phạm").create();
        jdbc.update("UPDATE listings SET status = 'LOCKED' WHERE id = ?", listing.id());
        JsonNode gone = getJson(get("/api/v2/listings/" + listing.slug()), 410);
        assertThat(gone.path("code").asText()).isEqualTo("LISTING_GONE");
        assertThat(gone.has("listingTitle")).isFalse();
        assertThat(gone.toString()).doesNotContain(token);
    }

    @Test
    void zeroResultSuggestionsCountAtMostThreeRelaxations() throws Exception {
        String token = SearchFixtures.token();
        TestData.TestUser seller = fixtures.seller("BROKER");
        data.listing(seller.id()).title("Đất " + token).price(5_000_000_000L).bedrooms(2).create();
        // five applicable relaxations; the page query plus at most three counts
        String body = QueryCount.assertAtMost(4, () -> mvc.perform(get("/api/v2/listings/search").param("q", token)
                .param("priceMax", "1000000000").param("areaMin", "500").param("bedsMin", "4").param("verified", "IDENTITY")
                .param("type", "HOUSE")).andReturn().getResponse().getContentAsString());
        assertThat(json.readTree(body).path("suggestions").size()).isLessThanOrEqualTo(3);
    }
}
