package com.company.bds.seo.application;

import com.company.bds.catalog.application.PublicCatalogService;
import com.company.bds.catalog.application.port.PublicCatalogStore;
import com.company.bds.cms.application.CmsArticleApplicationService;
import com.company.bds.cms.application.port.ArticleStore;
import com.company.bds.search.application.port.ResponseCachePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sitemap index + parts (audit F16.4): {@code static}, {@code areas}, {@code projects}, {@code articles} and
 * {@code listings-<n>} of at most {@code app.seo.sitemap.chunk-size} URLs. Listing parts are keyset ranges of
 * {@code listing_public_read.listing_id}: one scan computes every range with its {@code lastmod} and URL count (the
 * snapshot), each part is then one bounded index range read of two columns. Nothing hydrates listing entities or
 * revisions. Snapshot and parts are cached ({@code app.seo.sitemap.ttl}, default 10 min) under the public content
 * generation, so a CMS/project change shows up at once and listing changes within the TTL.
 */
@Service
public class SitemapService {
    private static final Pattern LISTING_PART = Pattern.compile("listings-(\\d{1,5})");
    /** Visible to the public search: seller ACTIVE (same rule as the read API). */
    private static final String SELLER_ACTIVE =
            " EXISTS (SELECT 1 FROM users ou WHERE ou.id = p.owner_id AND ou.status = 'ACTIVE')";
    static final List<String> STATIC_PATHS = List.of("/", "/search?purpose=SALE", "/search?purpose=RENT", "/du-an", "/khu-vuc",
            "/tin-tuc", "/about", "/terms", "/privacy", "/contact");

    public record ListingRange(String firstId, Instant lastModified, long count) {}

    public record Snapshot(List<ListingRange> listingRanges, Instant builtAt) {}

    public record Xml(String value) {}

    private final JdbcTemplate jdbc;
    private final ResponseCachePort cache;
    private final PublicCatalogService catalog;
    private final CmsArticleApplicationService cms;
    private final Clock clock;
    private final String baseUrl;
    private final int chunkSize;
    private final Duration ttl;

    public SitemapService(JdbcTemplate jdbc, ResponseCachePort cache, PublicCatalogService catalog, CmsArticleApplicationService cms,
                          Clock clock, @Value("${app.public-base-url:http://localhost:3000}") String baseUrl,
                          @Value("${app.seo.sitemap.chunk-size:10000}") int chunkSize,
                          @Value("${app.seo.sitemap.ttl:PT10M}") Duration ttl) {
        if (chunkSize < 1 || chunkSize > 50_000) throw new IllegalStateException("app.seo.sitemap.chunk-size must be 1..50000");
        this.jdbc = jdbc;
        this.cache = cache;
        this.catalog = catalog;
        this.cms = cms;
        this.clock = clock;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.chunkSize = chunkSize;
        this.ttl = ttl;
    }

    public Snapshot snapshot() {
        Snapshot snapshot = cache.getOrCompute("seo-sitemap", "snapshot:" + catalog.contentGeneration() + ":" + chunkSize, ttl,
                Snapshot.class, this::buildSnapshot);
        return snapshot != null ? snapshot : buildSnapshot();
    }

    Snapshot buildSnapshot() {
        List<ListingRange> ranges = jdbc.query("""
                SELECT MIN(listing_id::text), MAX(updated_at), COUNT(*) FROM (
                    SELECT p.listing_id, p.updated_at, (ROW_NUMBER() OVER (ORDER BY p.listing_id) - 1) / ? AS chunk
                    FROM listing_public_read p WHERE""" + SELLER_ACTIVE + """
                ) t GROUP BY chunk ORDER BY chunk""",
                (rs, n) -> new ListingRange(rs.getString(1), instant(rs.getTimestamp(2)), rs.getLong(3)), chunkSize);
        return new Snapshot(ranges, clock.instant());
    }

    public String index() {
        return cached("index", () -> {
            Snapshot snapshot = snapshot();
            StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    .append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
            entry(xml, "sitemap", "/sitemaps/static.xml", null);
            entry(xml, "sitemap", "/sitemaps/areas.xml", latest(catalog.areaSitemap().stream().map(PublicCatalogStore.SitemapEntry::lastModified).toList()));
            entry(xml, "sitemap", "/sitemaps/projects.xml", latest(catalog.projectSitemap(50_000).stream().map(PublicCatalogStore.SitemapEntry::lastModified).toList()));
            entry(xml, "sitemap", "/sitemaps/articles.xml", latest(cms.sitemapEntries(50_000).stream().map(ArticleStore.SitemapEntry::lastModified).toList()));
            for (int i = 0; i < snapshot.listingRanges().size(); i++) {
                entry(xml, "sitemap", "/sitemaps/listings-" + i + ".xml", snapshot.listingRanges().get(i).lastModified());
            }
            return xml.append("</sitemapindex>\n").toString();
        });
    }

    /** A part by name ({@code static}, {@code areas}, {@code projects}, {@code articles}, {@code listings-<n>}), or empty. */
    public Optional<String> part(String name) {
        switch (name) {
            case "static" -> {
                return Optional.of(cached("static", () -> urlset(STATIC_PATHS.stream().map(path -> new Url(path, null)).toList())));
            }
            case "areas" -> {
                return Optional.of(cached("areas", () -> urlset(catalog.areaSitemap().stream()
                        .map(e -> new Url("/khu-vuc/" + e.slug(), e.lastModified())).toList())));
            }
            case "projects" -> {
                return Optional.of(cached("projects", () -> urlset(catalog.projectSitemap(50_000).stream()
                        .map(e -> new Url("/du-an/" + e.slug(), e.lastModified())).toList())));
            }
            case "articles" -> {
                return Optional.of(cached("articles", () -> urlset(cms.sitemapEntries(50_000).stream()
                        .map(e -> new Url("/tin-tuc/" + e.slug(), e.lastModified())).toList())));
            }
            default -> {
                Matcher matcher = LISTING_PART.matcher(name);
                if (!matcher.matches()) return Optional.empty();
                int index = Integer.parseInt(matcher.group(1));
                List<ListingRange> ranges = snapshot().listingRanges();
                if (index >= ranges.size()) return Optional.empty();
                String from = ranges.get(index).firstId();
                String to = index + 1 < ranges.size() ? ranges.get(index + 1).firstId() : null;
                return Optional.of(cached(name + ":" + from, () -> urlset(listingUrls(from, to))));
            }
        }
    }

    public String robots() {
        StringBuilder out = new StringBuilder("User-agent: *\nAllow: /\n")
                .append("Disallow: /api/\nAllow: /api/v1/public/media/\nDisallow: /render/\n")
                .append("Disallow: /tin-tuc/xem-truoc/\nDisallow: /shortlists/\n");
        // account and tool pages (the back office path is deliberately not listed: its pages send noindex instead)
        for (String path : List.of("/listings/new", "/my-listings", "/my-leads", "/my-inquiries", "/broker/", "/billing", "/kyc",
                "/account", "/saved", "/notifications", "/become-owner", "/compare", "/verify-email", "/forgot-password",
                "/reset-password", "/unsubscribe", "/admin/")) {
            out.append("Disallow: ").append(path).append('\n');
        }
        return out.append("\nSitemap: ").append(baseUrl).append("/sitemap.xml\n").toString();
    }

    private List<Url> listingUrls(String from, String to) {
        String sql = "SELECT p.slug, p.updated_at FROM listing_public_read p WHERE p.listing_id >= ?::uuid"
                + (to == null ? "" : " AND p.listing_id < ?::uuid") + " AND" + SELLER_ACTIVE
                + " ORDER BY p.listing_id LIMIT ?";
        Object[] params = to == null ? new Object[]{from, chunkSize} : new Object[]{from, to, chunkSize};
        return jdbc.query(sql, (rs, n) -> new Url("/listings/" + rs.getString(1), instant(rs.getTimestamp(2))), params);
    }

    private record Url(String path, Instant lastModified) {}

    private String urlset(List<Url> urls) {
        StringBuilder xml = new StringBuilder(64 + urls.size() * 120).append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (Url url : urls) entry(xml, "url", url.path(), url.lastModified());
        return xml.append("</urlset>\n").toString();
    }

    private void entry(StringBuilder xml, String element, String path, Instant lastModified) {
        xml.append("<").append(element).append("><loc>").append(xmlEscape(baseUrl + path)).append("</loc>");
        if (lastModified != null) xml.append("<lastmod>").append(lastModified.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)).append("</lastmod>");
        xml.append("</").append(element).append(">\n");
    }

    private String cached(String key, java.util.function.Supplier<String> build) {
        Xml xml = cache.getOrCompute("seo-sitemap", "part:" + catalog.contentGeneration() + ":" + chunkSize + ":" + key, ttl, Xml.class,
                () -> new Xml(build.get()));
        return xml != null ? xml.value() : build.get();
    }

    private static Instant latest(List<Instant> values) {
        Instant latest = null;
        for (Instant value : values) if (value != null && (latest == null || value.isAfter(latest))) latest = value;
        return latest;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
