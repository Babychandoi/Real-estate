package com.company.bds.seo;

import com.company.bds.cms.application.CmsArticleApplicationService;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The prerender layer as a crawler sees it: plain HTTP, no JavaScript. Every page is fetched from {@code /render/...}
 * (what Nginx proxies page requests to) and parsed as HTML; the tests check status, title, canonical, robots, JSON-LD
 * and the main content. Audit F16.1, F16.3, F16.5, UI-16, P-06, P-07.
 */
// the search first-page cache (20 s) would hide listings created by an earlier test of this class
@BdsIntegrationTest(properties = "app.search.cache.first-page=false")
class SeoPrerenderTests {
    private static final String BASE = "http://localhost:3000";

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired CmsArticleApplicationService cms;

    record Page(int status, Document html, MvcResult result) {
        String header(String name) { return result.getResponse().getHeader(name); }
        String title() { return html.title(); }
        String canonical() { Element e = html.selectFirst("link[rel=canonical]"); return e == null ? null : e.attr("href"); }
        String robots() { Element e = html.selectFirst("meta[name=robots]"); return e == null ? null : e.attr("content"); }
        String meta(String property) { Element e = html.selectFirst("meta[property=" + property + "]"); return e == null ? null : e.attr("content"); }
        String main() { Element e = html.selectFirst("#root [data-prerender-content]"); return e == null ? "" : e.text(); }
    }

    private Page fetch(String path) throws Exception {
        MvcResult result = mvc.perform(get("/render" + path)).andReturn();
        return new Page(result.getResponse().getStatus(), Jsoup.parse(result.getResponse().getContentAsString()), result);
    }

    private List<JsonNode> jsonLd(Page page) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        for (Element script : page.html().select("script[type=application/ld+json]")) out.add(json.readTree(script.data()));
        return out;
    }

    private JsonNode jsonLdOfType(Page page, String type) throws Exception {
        return jsonLd(page).stream().filter(n -> type.equals(n.path("@type").asText())).findFirst()
                .orElseThrow(() -> new AssertionError("no JSON-LD " + type));
    }

    private TestData.TestUser seller() {
        return data.user().role("BROKER").name("Môi giới SEO " + UUID.randomUUID().toString().substring(0, 6)).create();
    }

    private String adminToken() {
        return data.sessionFor(data.user().role("ADMIN").create().id());
    }

    @Test
    void aListingPageHasItsTitleCanonicalJsonLdAndContentWithoutJavaScript() throws Exception {
        TestData.TestUser seller = seller();
        TestData.TestListing listing = data.listing(seller.id()).title("Căn hộ 2PN view hồ Cầu Giấy SEO").purpose("RENT")
                .price(15_000_000L).area("68.5").district("005", "Cầu Giấy, Hà Nội").media(2).create();

        Page page = fetch("/listings/" + listing.slug());

        assertThat(page.status()).isEqualTo(200);
        assertThat(page.title()).isEqualTo("Căn hộ 2PN view hồ Cầu Giấy SEO | Nhà Đất Chuẩn");
        assertThat(page.canonical()).isEqualTo(BASE + "/listings/" + listing.slug());
        assertThat(page.robots()).isEqualTo("index,follow,max-image-preview:large");
        assertThat(page.meta("og:type")).isEqualTo("product");
        assertThat(page.meta("og:url")).isEqualTo(BASE + "/listings/" + listing.slug());
        assertThat(page.html().selectFirst("meta[name=description]").attr("content"))
                .contains("Căn hộ cho thuê tại Cầu Giấy, Hà Nội").contains("/tháng");
        JsonNode product = jsonLdOfType(page, "Product");
        assertThat(product.path("name").asText()).isEqualTo("Căn hộ 2PN view hồ Cầu Giấy SEO");
        assertThat(product.path("offers").path("price").asLong()).isEqualTo(15_000_000L);
        assertThat(product.path("offers").path("priceSpecification").path("unitCode").asText()).isEqualTo("MON");
        assertThat(product.path("image").get(0).asText()).startsWith("http");
        assertThat(jsonLdOfType(page, "BreadcrumbList").path("itemListElement")).hasSize(3);
        assertThat(page.html().selectFirst("#root h1").text()).isEqualTo("Căn hộ 2PN view hồ Cầu Giấy SEO");
        assertThat(page.main()).contains("15 triệu/tháng").contains("68,5 m²").contains("Mô tả tin kiểm thử tích hợp.")
                .contains(seller.name());
        // still the SPA: the built asset tags are there, and every injected tag is marked for the client-side reset
        assertThat(page.html().select("script[type=module][src^=/assets/]")).hasSize(1);
        assertThat(page.html().selectFirst("title").hasAttr("data-prerender")).isTrue();
        assertThat(page.html().selectFirst("meta[name=description]").attr("data-default")).contains("Tìm mua, thuê");
        assertThat(page.html().select("link[rel=canonical]")).hasSize(1);
        assertThat(page.header("Content-Type")).startsWith("text/html");
    }

    @Test
    void anIdOrATrailingSlashRedirectsPermanentlyToTheCanonicalUrl() throws Exception {
        TestData.TestListing listing = data.listing(seller().id()).title("Nhà phố chuyển hướng").create();

        Page byId = fetch("/listings/" + listing.id());
        assertThat(byId.status()).isEqualTo(301);
        assertThat(byId.header("Location")).isEqualTo("/listings/" + listing.slug());

        Page slash = fetch("/listings/" + listing.slug() + "/");
        assertThat(slash.status()).isEqualTo(301);
        assertThat(slash.header("Location")).isEqualTo("/listings/" + listing.slug());

        MvcResult withQuery = mvc.perform(get("/render/tin-tuc/").param("page", "1")).andReturn();
        assertThat(withQuery.getResponse().getStatus()).isEqualTo(301);
        assertThat(withQuery.getResponse().getHeader("Location")).isEqualTo("/tin-tuc?page=1");
    }

    @Test
    void missingListingsAre404AndListingsThatWerePublicAre410() throws Exception {
        Page missing = fetch("/listings/khong-ton-tai-" + UUID.randomUUID());
        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.robots()).isEqualTo("noindex,follow");
        assertThat(missing.header("X-Robots-Tag")).isEqualTo("noindex");
        assertThat(missing.main()).contains("Không tìm thấy trang");

        TestData.TestListing paused = data.listing(seller().id()).title("Tin đã tạm ẩn").create();
        jdbc.update("UPDATE listings SET status = 'PAUSED' WHERE id = ?", paused.id());
        Page gone = fetch("/listings/" + paused.slug());
        assertThat(gone.status()).isEqualTo(410);
        assertThat(gone.robots()).isEqualTo("noindex,follow");
        assertThat(gone.main()).contains("không còn hiển thị");

        TestData.TestListing draft = data.listing(seller().id()).status("DRAFT").title("Bản nháp bí mật").create();
        Page draftPage = fetch("/listings/" + draft.slug());
        assertThat(draftPage.status()).isEqualTo(404);
        assertThat(draftPage.html().outerHtml()).doesNotContain("Bản nháp bí mật");
    }

    @Test
    void unknownRoutesAre404AndAppRoutesAreNoindexShells() throws Exception {
        Page unknown = fetch("/khong-co-trang-nay");
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.title()).startsWith("Không tìm thấy trang");
        assertThat(unknown.header("Cache-Control")).isEqualTo("no-store");

        for (String path : List.of("/my-listings", "/account", "/kyc", "/2026/nhadatchuan/admin/moderation", "/reset-password")) {
            Page app = fetch(path);
            assertThat(app.status()).as(path).isEqualTo(200);
            assertThat(app.robots()).as(path).isEqualTo("noindex,nofollow");
            assertThat(app.header("X-Robots-Tag")).as(path).isEqualTo("noindex, nofollow");
            assertThat(app.header("Cache-Control")).as(path).isEqualTo("no-store");
            assertThat(app.canonical()).as(path).isNull();
        }
    }

    @Test
    void onlyThePlainSearchPagesAreIndexableAndFilterCombinationsAreNoindex() throws Exception {
        data.listing(seller().id()).title("Căn hộ cho thuê tìm kiếm SEO").purpose("RENT").price(9_000_000L).create();

        MvcResult rentResult = mvc.perform(get("/render/search").param("purpose", "RENT")).andReturn();
        Document rent = Jsoup.parse(rentResult.getResponse().getContentAsString());
        assertThat(rentResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(rent.selectFirst("meta[name=robots]").attr("content")).startsWith("index");
        assertThat(rent.selectFirst("link[rel=canonical]").attr("href")).isEqualTo(BASE + "/search?purpose=RENT");
        assertThat(rent.selectFirst("#root").text()).contains("Nhà đất cho thuê");

        MvcResult filtered = mvc.perform(get("/render/search").param("purpose", "SALE").param("priceMin", "1000000000")
                .param("district", "005")).andReturn();
        Document filteredHtml = Jsoup.parse(filtered.getResponse().getContentAsString());
        assertThat(filtered.getResponse().getStatus()).isEqualTo(200);
        assertThat(filteredHtml.selectFirst("meta[name=robots]").attr("content")).isEqualTo("noindex,follow");
        assertThat(filteredHtml.selectFirst("link[rel=canonical]").attr("href")).isEqualTo(BASE + "/search?purpose=SALE");

        MvcResult invalid = mvc.perform(get("/render/search").param("priceMin", "abc")).andReturn();
        assertThat(invalid.getResponse().getStatus()).isEqualTo(200);
        assertThat(Jsoup.parse(invalid.getResponse().getContentAsString()).selectFirst("meta[name=robots]").attr("content"))
                .isEqualTo("noindex,follow");
    }

    @Test
    void theHomePageShowsRealListingsASearchFormAndWebsiteStructuredData() throws Exception {
        TestData.TestListing listing = data.listing(seller().id()).title("Biệt thự trang chủ SEO").purpose("SALE")
                .createdAt(Instant.now().plusSeconds(5)).create();

        Page home = fetch("/");

        assertThat(home.status()).isEqualTo(200);
        assertThat(home.canonical()).isEqualTo(BASE + "/");
        assertThat(jsonLdOfType(home, "WebSite").path("potentialAction").path("target").asText())
                .isEqualTo(BASE + "/search?q={search_term_string}");
        assertThat(home.html().selectFirst("#root form[action=/search][method=get] input[name=q]")).isNotNull();
        assertThat(home.html().select("#root a[href=/listings/" + listing.slug() + "]")).isNotEmpty();
        // no invented numbers: the only figures on the page come from data
        assertThat(home.main()).doesNotContainPattern("\\d+\\+ ");
    }

    @Test
    void escapingKeepsListingTextInsideItsElements() throws Exception {
        TestData.TestListing listing = data.listing(seller().id())
                .title("Nhà </title><script>alert(1)</script> \"x\" & y").create();

        Page page = fetch("/listings/" + listing.slug());

        String raw = page.result().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("<script>alert(1)</script>");
        assertThat(page.title()).isEqualTo("Nhà </title><script>alert(1)</script> \"x\" & y | Nhà Đất Chuẩn");
        assertThat(jsonLdOfType(page, "Product").path("name").asText()).isEqualTo("Nhà </title><script>alert(1)</script> \"x\" & y");
        assertThat(page.html().select("script:not([type]), script[type=module]")).hasSize(1);
    }

    @Test
    void projectPagesShowInventoryWithMethodAndAmenitiesWithSources() throws Exception {
        UUID projectId = UUID.randomUUID();
        String slug = "du-an-seo-" + projectId.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO projects(id, name, slug, developer_name, province_code, district_code, address, total_units,
                    legal_license_number, status, description)
                VALUES (?, 'Khu căn hộ Thử Nghiệm SEO', ?, 'Công ty CP Kiểm Thử', '01', '005', 'Đường Trần Duy Hưng', 500,
                    'GP-TEST-1', 'ACTIVE', 'Dự án kiểm thử cho trang công khai.')""", projectId, slug);
        TestData.TestUser seller = seller();
        for (int i = 0; i < 5; i++) {
            TestData.TestListing l = data.listing(seller.id()).title("Căn hộ dự án SEO " + i).purpose("SALE")
                    .price(3_000_000_000L + i * 100_000_000L).area("60").district("005", "Cầu Giấy").create();
            jdbc.update("UPDATE listing_revisions SET project_id = ? WHERE id = ?", projectId, l.publicRevisionId());
        }
        String admin = adminToken();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/catalog/projects/" + projectId + "/public-profile")
                        .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Dự án kiểm thử cho trang công khai.","infoSource":"Hồ sơ chủ đầu tư","infoCheckedAt":"2026-09-01",
                                 "amenities":[{"name":"Trường tiểu học Dịch Vọng","category":"EDUCATION","distanceM":400,
                                   "sourceName":"Bản đồ quy hoạch quận","sourceUrl":"https://example.org/qh","checkedAt":"2026-09-01"}]}"""))
                .andReturn();

        Page page = fetch("/du-an/" + slug);

        assertThat(page.status()).isEqualTo(200);
        assertThat(page.canonical()).isEqualTo(BASE + "/du-an/" + slug);
        assertThat(jsonLdOfType(page, "Place").path("name").asText()).isEqualTo("Khu căn hộ Thử Nghiệm SEO");
        assertThat(page.main()).contains("Tin bán: 5 tin, trung vị").contains("Phương pháp:")
                .contains("Trường tiểu học Dịch Vọng").contains("nguồn: Bản đồ quy hoạch quận")
                .contains("Căn hộ dự án SEO 0");

        // an amenity without a source is refused
        MvcResult refused = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/catalog/projects/" + projectId + "/public-profile")
                        .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amenities\":[{\"name\":\"Siêu thị\",\"category\":\"SHOPPING\",\"checkedAt\":\"2026-09-01\"}]}"))
                .andReturn();
        assertThat(refused.getResponse().getStatus()).isEqualTo(400);

        jdbc.update("UPDATE projects SET status = 'LOCKED' WHERE id = ?", projectId);
        assertThat(fetch("/du-an/" + slug).status()).isEqualTo(410);
        assertThat(fetch("/du-an/khong-co-du-an-nay").status()).isEqualTo(404);
    }

    @Test
    void areaStatisticsNeedFiveListingsBeforeAMedianIsShown() throws Exception {
        // Mê Linh (250): no other test puts listings there
        TestData.TestUser seller = seller();
        for (int i = 0; i < 4; i++) {
            data.listing(seller.id()).title("Nhà Mê Linh " + i).purpose("RENT").price(5_000_000L).district("250", "Mê Linh").create();
        }

        Page four = fetch("/khu-vuc/me-linh");
        assertThat(four.status()).isEqualTo(200);
        assertThat(four.canonical()).isEqualTo(BASE + "/khu-vuc/me-linh");
        assertThat(four.main()).contains("Tin cho thuê: 4 tin, chưa đủ 5 tin để tính trung vị");
        assertThat(jsonLdOfType(four, "Place").path("name").asText()).isEqualTo("Mê Linh, Hà Nội");

        assertThat(fetch("/khu-vuc/khong-co-khu-vuc").status()).isEqualTo(404);
        Page list = fetch("/khu-vuc");
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.html().select("#root a[href=/khu-vuc/me-linh]")).hasSize(1);
    }

    @Test
    void articlesArePublicOnlyWhileLiveScheduledOnesAppearAtTheirTimeAndUnpublishedOnesAre410() throws Exception {
        UUID admin = data.user().role("ADMIN").create().id();
        String slug = "bai-viet-seo-" + UUID.randomUUID().toString().substring(0, 8);
        var detail = cms.create(new CmsArticleApplicationService.Draft(slug, com.company.bds.cms.domain.model.ArticleCategory.KNOWLEDGE,
                new com.company.bds.cms.domain.model.RevisionContent("Kiểm tra pháp lý trước khi đặt cọc", "Tóm tắt ngắn.",
                        "<p>Nội dung <strong>chính</strong></p><script>alert(1)</script><img src=x onerror=alert(2)>",
                        null, "Ban biên tập", null, null, null, "Luật Đất đai 2024", "https://example.org/luat")), admin);
        UUID articleId = detail.article().id();
        UUID revisionId = detail.latest().id();

        assertThat(fetch("/tin-tuc/" + slug).status()).as("draft").isEqualTo(404);

        cms.submit(articleId, revisionId);
        Instant later = Instant.now().plus(2, ChronoUnit.HOURS);
        cms.approve(articleId, revisionId, admin, later);
        assertThat(fetch("/tin-tuc/" + slug).status()).as("scheduled, not yet due").isEqualTo(404);

        // time passes: the scheduled revision is public before the scheduler runs, then the scheduler makes it durable
        jdbc.update("UPDATE cms_articles SET scheduled_publish_at = now() - interval '1 minute' WHERE id = ?", articleId);
        Page live = fetch("/tin-tuc/" + slug);
        assertThat(live.status()).isEqualTo(200);
        assertThat(cms.publishDue(50)).isGreaterThanOrEqualTo(1);
        live = fetch("/tin-tuc/" + slug);
        assertThat(live.status()).isEqualTo(200);
        assertThat(live.title()).isEqualTo("Kiểm tra pháp lý trước khi đặt cọc | Nhà Đất Chuẩn");
        assertThat(live.canonical()).isEqualTo(BASE + "/tin-tuc/" + slug);
        JsonNode article = jsonLdOfType(live, "Article");
        assertThat(article.path("author").path("name").asText()).isEqualTo("Ban biên tập");
        assertThat(article.path("datePublished").asText()).isNotBlank();
        assertThat(live.main()).contains("Nội dung chính").contains("Luật Đất đai 2024").contains("Tác giả: Ban biên tập");
        String raw = live.result().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("alert(1)").doesNotContain("onerror");

        cms.unpublish(articleId);
        Page gone = fetch("/tin-tuc/" + slug);
        assertThat(gone.status()).isEqualTo(410);
        MvcResult api = mvc.perform(get("/api/v1/public/articles/" + slug)).andReturn();
        assertThat(api.getResponse().getStatus()).isEqualTo(410);
    }

    @Test
    void previewPagesAreNeverIndexedOrCached() throws Exception {
        Page preview = fetch("/tin-tuc/xem-truoc/abcdefghijklmnopqrstuvwxyz0123456789");
        assertThat(preview.status()).isEqualTo(200);
        assertThat(preview.robots()).isEqualTo("noindex,nofollow");
        assertThat(preview.header("X-Robots-Tag")).isEqualTo("noindex, nofollow");
        assertThat(preview.header("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void informationPagesShowTheConfiguredOperatorOrSayThatDataIsMissing() throws Exception {
        Page about = fetch("/about");
        assertThat(about.status()).isEqualTo(200);
        assertThat(about.title()).isEqualTo("Về Nhà Đất Chuẩn | Nhà Đất Chuẩn");
        assertThat(about.main()).contains("Đơn vị vận hành").contains("Tên pháp nhân: Chưa có dữ liệu");
        assertThat(about.canonical()).isEqualTo(BASE + "/about");
    }
}
