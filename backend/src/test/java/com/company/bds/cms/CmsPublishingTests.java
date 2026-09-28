package com.company.bds.cms;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.QueryCount;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * CMS publishing (audit P-07) through the HTTP API: sanitised bodies, immutable revisions, edits as new revisions,
 * supersede on publish, scheduling, unpublish (410), preview links (no-store, noindex, expiry), staff-only writes,
 * paged admin list without N+1.
 */
@BdsIntegrationTest
class CmsPublishingTests {
    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private String staff;

    private String staffToken() {
        if (staff == null) staff = data.sessionFor(data.user().role("MODERATOR").create().id());
        return staff;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + staffToken())).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(expected);
        String body = result.getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    private JsonNode createArticle(String slug, String html) throws Exception {
        return call(post("/api/v1/cms/articles").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of(
                "slug", slug, "category", "KNOWLEDGE", "title", "Bài " + slug, "summary", "Tóm tắt " + slug,
                "contentHtml", html, "authorName", "Ban biên tập", "sourceName", "Bộ Xây dựng", "sourceUrl", "https://moc.gov.vn/"))), 201);
    }

    private static String slug() {
        return "cms-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode publicArticle(String slug, int expected) throws Exception {
        MvcResult result = mvc.perform(get("/api/v2/public/articles/" + slug)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        return expected == 200 ? json.readTree(result.getResponse().getContentAsString()) : null;
    }

    @Test
    void bodiesAreSanitisedAndAnEditOfAPublishedArticleIsANewRevisionThatSupersedesItOnApproval() throws Exception {
        String slug = slug();
        JsonNode created = createArticle(slug, "<h2>Mục</h2><p onclick=\"x()\">An toàn <a href=\"javascript:alert(1)\">link</a>"
                + " <a href=\"https://example.org\">nguồn</a></p><script>alert(1)</script><iframe src=\"https://evil\"></iframe>");
        String id = created.path("id").asText();
        String r1 = created.path("currentRevision").path("id").asText();
        String html = created.path("currentRevision").path("contentHtml").asText();
        assertThat(html).contains("<h2>Mục</h2>").doesNotContain("onclick").doesNotContain("javascript:")
                .doesNotContain("<script").doesNotContain("iframe").contains("rel=\"nofollow noopener noreferrer\"");

        call(post("/api/v1/cms/articles/" + id + "/revisions/" + r1 + "/submit"), 200);
        call(post("/api/v1/cms/articles/" + id + "/revisions/" + r1 + "/approve"), 200);
        JsonNode v1 = publicArticle(slug, 200);
        assertThat(v1.path("currentRevision").path("revisionNumber").asInt()).isEqualTo(1);
        assertThat(v1.path("currentRevision").path("sourceName").asText()).isEqualTo("Bộ Xây dựng");
        assertThat(v1.toString()).doesNotContain("reviewedBy");

        // content of a submitted/published revision cannot change, not even with direct SQL
        assertThatThrownBy(() -> jdbc.update("UPDATE cms_article_revisions SET title = 'sửa lén' WHERE id = ?::uuid", r1))
                .hasMessageContaining("immutable");
        call(put("/api/v1/cms/articles/" + id + "/revisions/" + r1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"contentHtml\":\"<p>x</p>\",\"authorName\":\"y\"}"), 409);

        JsonNode edited = call(post("/api/v1/cms/articles/" + id + "/revisions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Bài đã sửa\",\"contentHtml\":\"<p>Bản mới</p>\",\"authorName\":\"Ban biên tập\"}"), 201);
        String r2 = edited.path("currentRevision").path("id").asText();
        assertThat(edited.path("currentRevision").path("revisionNumber").asInt()).isEqualTo(2);
        assertThat(publicArticle(slug, 200).path("currentRevision").path("title").asText()).as("draft is not public").isEqualTo("Bài " + slug);

        call(post("/api/v1/cms/articles/" + id + "/revisions/" + r2 + "/submit"), 200);
        call(post("/api/v1/cms/articles/" + id + "/revisions/" + r2 + "/approve"), 200);
        assertThat(publicArticle(slug, 200).path("currentRevision").path("title").asText()).isEqualTo("Bài đã sửa");
        JsonNode detail = call(get("/api/v1/cms/articles/" + id), 200);
        assertThat(detail.path("revisions").get(1).path("status").asText()).isEqualTo("SUPERSEDED");
        assertThat(detail.path("publishedRevisionId").asText()).isEqualTo(r2);
    }

    @Test
    void scheduledPublishingCancellingRejectingAndUnpublishing() throws Exception {
        String slug = slug();
        JsonNode created = createArticle(slug, "<p>Hẹn giờ</p>");
        String id = created.path("id").asText();
        String r1 = created.path("currentRevision").path("id").asText();
        call(post("/api/v1/cms/articles/" + id + "/revisions/" + r1 + "/submit"), 200);
        String at = Instant.now().plus(3, ChronoUnit.HOURS).toString();
        JsonNode scheduled = call(post("/api/v1/cms/articles/" + id + "/revisions/" + r1 + "/approve")
                .contentType(MediaType.APPLICATION_JSON).content("{\"publishAt\":\"" + at + "\"}"), 200);
        assertThat(scheduled.path("status").asText()).isEqualTo("SCHEDULED");
        publicArticle(slug, 404);
        MvcResult list = mvc.perform(get("/api/v2/public/articles").param("size", "50")).andReturn();
        assertThat(list.getResponse().getContentAsString()).doesNotContain(slug);

        JsonNode cancelled = call(delete("/api/v1/cms/articles/" + id + "/schedule"), 200);
        assertThat(cancelled.path("scheduledRevisionId").isNull()).isTrue();
        assertThat(cancelled.path("currentRevision").path("status").asText()).isEqualTo("SUBMITTED");

        call(post("/api/v1/cms/articles/" + id + "/revisions/" + r1 + "/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"ngắn\"}"), 400);
        call(post("/api/v1/cms/articles/" + id + "/unpublish"), 409);

        call(post("/api/v1/cms/articles/" + id + "/revisions/" + r1 + "/approve"), 200);
        publicArticle(slug, 200);
        JsonNode unpublished = call(post("/api/v1/cms/articles/" + id + "/unpublish"), 200);
        assertThat(unpublished.path("status").asText()).isEqualTo("ARCHIVED");
        publicArticle(slug, 410);
    }

    @Test
    void previewLinksShowAnyRevisionPrivatelyAndExpire() throws Exception {
        String slug = slug();
        JsonNode created = createArticle(slug, "<p>Bản nháp cần xem trước</p>");
        String id = created.path("id").asText();
        String r1 = created.path("currentRevision").path("id").asText();
        JsonNode link = call(post("/api/v1/cms/articles/" + id + "/revisions/" + r1 + "/preview-link"), 201);
        String token = link.path("token").asText();
        assertThat(link.path("path").asText()).isEqualTo("/tin-tuc/xem-truoc/" + token);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cms_preview_tokens WHERE token_hash = ?", Integer.class, token)).isZero();

        MvcResult preview = mvc.perform(get("/api/v1/public/articles/preview/" + token)).andReturn();
        assertThat(preview.getResponse().getStatus()).isEqualTo(200);
        assertThat(preview.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(preview.getResponse().getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow");
        assertThat(preview.getResponse().getContentAsString()).contains("Bản nháp cần xem trước").contains("\"status\":\"DRAFT\"");
        publicArticle(slug, 404);

        assertThat(mvc.perform(get("/api/v1/public/articles/preview/" + token + "x")).andReturn().getResponse().getStatus()).isEqualTo(404);
        jdbc.update("UPDATE cms_preview_tokens SET expires_at = now() - interval '1 second' WHERE revision_id = ?::uuid", r1);
        assertThat(mvc.perform(get("/api/v1/public/articles/preview/" + token)).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void writesAreStaffOnlyAndTheAdminListIsPagedWithoutNPlusOne() throws Exception {
        String seeker = data.sessionFor(data.user().create().id());
        MvcResult forbidden = mvc.perform(post("/api/v1/cms/articles").header("Authorization", "Bearer " + seeker)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertThat(forbidden.getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/v1/cms/articles")).andReturn().getResponse().getStatus()).isEqualTo(401);

        for (int i = 0; i < 12; i++) createArticle(slug(), "<p>Bài " + i + "</p>");
        staffToken();
        MvcResult page = QueryCount.assertAtMost(6, () -> mvc.perform(get("/api/v1/cms/articles").param("size", "10")
                .header("Authorization", "Bearer " + staffToken())).andReturn());
        assertThat(page.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(page.getResponse().getContentAsString())).hasSize(10);
        assertThat(Long.parseLong(page.getResponse().getHeader("X-Total-Count"))).isGreaterThanOrEqualTo(12);
        assertThat(page.getResponse().getHeader("Cache-Control")).contains("no-store");

        call(post("/api/v1/cms/articles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"Có Dấu\",\"category\":\"KNOWLEDGE\",\"title\":\"t\",\"contentHtml\":\"<p>x</p>\",\"authorName\":\"a\"}"), 400);
        call(post("/api/v1/cms/articles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"rong\",\"category\":\"KNOWLEDGE\",\"title\":\"t\",\"contentHtml\":\"<script>x</script>\",\"authorName\":\"a\"}"), 400);
    }
}
