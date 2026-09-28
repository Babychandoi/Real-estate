package com.company.bds.seo;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.QueryCount;
import com.company.bds.testsupport.TestData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sitemap index over more than 10 000 public listings (audit F16.4): chunked parts with lastmod, every public listing
 * exactly once, a bounded number of statements per file whatever the listing count (no entity hydration), and
 * robots.txt pointing at the index.
 */
@BdsIntegrationTest
class SitemapTests {
    private static final int LISTINGS = 10_050;

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;

    private Document xml(String path) throws Exception {
        MvcResult result = mvc.perform(get(path)).andReturn();
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("application/xml");
        return Jsoup.parse(result.getResponse().getContentAsString(), "", Parser.xmlParser());
    }

    /** Inserts {@code count} approved, public listings with set-based SQL and refreshes their read-model rows. */
    private void bulkListings(UUID owner, String token, int count) {
        jdbc.update("""
                INSERT INTO listings(id, owner_id, status, is_verified_owner, version, slug, created_at, updated_at)
                SELECT gen_random_uuid(), ?, 'ACTIVE', FALSE, 0, 'sm-' || ? || '-' || g, now(), now() FROM generate_series(1, ?) g""",
                owner, token, count);
        jdbc.update("""
                INSERT INTO listing_revisions(id, listing_id, revision_number, status, title, purpose, property_type, price_vnd, area_m2,
                    description, province_code, district_code, address_summary, created_at, submitted_at, moderated_at)
                SELECT gen_random_uuid(), l.id, 1, 'APPROVED', 'Tin sitemap ' || l.slug, 'SALE', 'APARTMENT', 2000000000, 55,
                    'Mô tả', '01', '006', 'Đống Đa', now(), now(), now()
                FROM listings l WHERE l.owner_id = ?""", owner);
        jdbc.update("UPDATE listings l SET public_revision_id = r.id FROM listing_revisions r WHERE r.listing_id = l.id AND l.owner_id = ?", owner);
        jdbc.queryForList("SELECT f.visible FROM listings l, LATERAL bds_refresh_listing_public_read(l.id) f WHERE l.owner_id = ?", owner);
    }

    @Test
    void theIndexSplitsMoreThanTenThousandListingsIntoPartsWithLastmodAndBoundedQueries() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        String token = UUID.randomUUID().toString().substring(0, 8);
        try {
            bulkListings(owner.id(), token, LISTINGS);
            Integer publicCount = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM listing_public_read p WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = p.owner_id AND u.status = 'ACTIVE')""",
                    Integer.class);
            assertThat(publicCount).isGreaterThan(10_000);

            // the index is built from one aggregate over the read model plus one query per content kind
            Document index = QueryCount.assertAtMost(10, () -> xml("/api/v1/public/seo/sitemap.xml"));
            List<String> locs = index.select("sitemap > loc").eachText();
            assertThat(locs).contains("http://localhost:3000/sitemaps/static.xml", "http://localhost:3000/sitemaps/areas.xml",
                    "http://localhost:3000/sitemaps/projects.xml", "http://localhost:3000/sitemaps/articles.xml",
                    "http://localhost:3000/sitemaps/listings-0.xml", "http://localhost:3000/sitemaps/listings-1.xml");
            long listingParts = locs.stream().filter(l -> l.contains("/sitemaps/listings-")).count();
            assertThat(listingParts).isEqualTo((publicCount + 9_999) / 10_000);
            for (Element sitemap : index.select("sitemap")) {
                if (sitemap.selectFirst("loc").text().contains("listings-")) assertThat(sitemap.selectFirst("lastmod")).isNotNull();
            }

            Set<String> seen = new HashSet<>();
            List<Long> statementsPerPart = new ArrayList<>();
            for (int part = 0; part < listingParts; part++) {
                String path = "/api/v1/public/seo/sitemaps/listings-" + part + ".xml";
                long[] statements = new long[1];
                Document[] doc = new Document[1];
                statements[0] = QueryCount.count(() -> doc[0] = xml(path));
                statementsPerPart.add(statements[0]);
                List<String> urls = doc[0].select("url > loc").eachText();
                assertThat(urls.size()).isLessThanOrEqualTo(10_000);
                assertThat(doc[0].select("url > lastmod")).hasSameSizeAs(urls);
                for (String url : urls) assertThat(seen.add(url)).as("duplicate " + url).isTrue();
            }
            assertThat(statementsPerPart).allSatisfy(n -> assertThat(n).isLessThanOrEqualTo(4));
            assertThat(seen).hasSize(publicCount);
            assertThat(mvc.perform(get("/api/v1/public/seo/sitemaps/listings-" + listingParts + ".xml")).andReturn()
                    .getResponse().getStatus()).isEqualTo(404);
            assertThat(seen.stream().filter(u -> u.contains("/listings/sm-" + token + "-")).count()).isEqualTo(LISTINGS);
        } finally {
            jdbc.update("DELETE FROM listing_public_read WHERE owner_id = ?", owner.id());
        }
    }

    /** Must not touch the listing snapshot: it is cached for the TTL and the other test builds it after its inserts. */
    @Test
    void unknownPartsAre404AndRobotsPointsAtTheIndex() throws Exception {
        assertThat(mvc.perform(get("/api/v1/public/seo/sitemaps/nope.xml")).andReturn().getResponse().getStatus()).isEqualTo(404);

        Document staticPart = xml("/api/v1/public/seo/sitemaps/static.xml");
        assertThat(staticPart.select("url > loc").eachText()).contains("http://localhost:3000/", "http://localhost:3000/tin-tuc");

        String robots = mvc.perform(get("/api/v1/public/seo/robots.txt")).andReturn().getResponse().getContentAsString();
        assertThat(robots).contains("Sitemap: http://localhost:3000/sitemap.xml").contains("Disallow: /api/")
                .contains("Allow: /api/v1/public/media/").contains("Disallow: /tin-tuc/xem-truoc/")
                .doesNotContain("nhadatchuan/admin");
    }
}
