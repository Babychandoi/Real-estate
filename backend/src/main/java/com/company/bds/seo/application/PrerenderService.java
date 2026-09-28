package com.company.bds.seo.application;

import com.company.bds.catalog.application.PublicCatalogService;
import com.company.bds.catalog.application.PublicCatalogService.AreaPage;
import com.company.bds.catalog.application.PublicCatalogService.Inventory;
import com.company.bds.catalog.application.PublicCatalogService.ProjectPage;
import com.company.bds.catalog.application.PublicCatalogService.Statistic;
import com.company.bds.catalog.application.port.PublicCatalogStore.Amenity;
import com.company.bds.catalog.application.port.PublicCatalogStore.AreaCard;
import com.company.bds.catalog.application.port.PublicCatalogStore.ProjectCard;
import com.company.bds.catalog.application.port.PublicCatalogStore.ProjectRow;
import com.company.bds.cms.api.CmsDtos;
import com.company.bds.cms.application.CmsArticleApplicationService;
import com.company.bds.cms.application.CmsArticleApplicationService.PublicLookup;
import com.company.bds.cms.application.CmsHtmlSanitizer;
import com.company.bds.cms.application.port.ArticleStore.PublicArticle;
import com.company.bds.cms.application.port.ArticleStore.PublicPage;
import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleRevision;
import com.company.bds.media.ImageDto;
import com.company.bds.search.application.ListingReadService;
import com.company.bds.search.application.ListingReadService.DetailRef;
import com.company.bds.search.application.ListingReadService.DetailSnapshot;
import com.company.bds.search.application.ListingSearchService;
import com.company.bds.search.application.SearchProblemException;
import com.company.bds.search.application.SearchResults;
import com.company.bds.search.application.port.ListingReadModelPort.SellerProfile;
import com.company.bds.search.domain.InvalidFilterException;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.domain.SearchFilterParser;
import com.company.bds.seo.render.RenderedPage;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.ContactInfoGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.company.bds.seo.render.Html.esc;
import static com.company.bds.seo.render.Html.excerpt;
import static com.company.bds.seo.render.Html.money;

/**
 * Route table of the prerender layer (audit F16.1/F16.3/F16.5, UI-16): decides status, canonical URL, robots,
 * metadata, JSON-LD and the main content of every page URL, from the same read models as the public API. The SPA
 * mounts over the content; crawlers and link previews get complete HTML without running JavaScript.
 */
@Service
public class PrerenderService {
    private static final Logger log = LoggerFactory.getLogger(PrerenderService.class);
    static final String SITE = "Nhà Đất Chuẩn";
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    /** Filter parameters that may stay in an indexable search URL; anything else makes the page noindex (F16.5). */
    private static final Set<String> INDEXABLE_SEARCH_PARAMS = Set.of("purpose");
    /** SPA routes that exist but must never be indexed (accounts, tokens, tools, back office). */
    private static final Set<String> APP_ROUTES = Set.of("/listings/new", "/my-listings", "/broker/workspace", "/compare", "/billing",
            "/my-leads", "/become-owner", "/my-inquiries", "/kyc", "/account", "/saved", "/notifications", "/unsubscribe",
            "/verify-email", "/forgot-password", "/reset-password");
    private static final List<String> APP_PREFIXES = List.of("/shortlists/", "/2026/nhadatchuan/admin", "/admin/", "/2026/nhadatchua/admin/");
    private static final Map<String, String[]> INFO_PAGES = Map.of(
            "/about", new String[]{"Về Nhà Đất Chuẩn", "Nhà Đất Chuẩn là nơi người có nhu cầu mua, thuê và người đăng tin kết nối qua các tin bất động sản có thông tin rõ ràng."},
            "/terms", new String[]{"Điều khoản sử dụng", "Các điều khoản này quy định cách sử dụng nền tảng Nhà Đất Chuẩn và trách nhiệm của từng bên khi đăng hoặc tìm tin."},
            "/privacy", new String[]{"Chính sách quyền riêng tư", "Chúng tôi xử lý dữ liệu cần thiết để vận hành tài khoản, tin đăng và yêu cầu liên hệ, đồng thời giới hạn việc truy cập theo vai trò."},
            "/contact", new String[]{"Liên hệ hỗ trợ", "Gửi đúng thông tin và đúng kênh để yêu cầu của bạn được tiếp nhận rõ ràng hơn."});

    private final ListingReadService reads;
    private final ListingSearchService search;
    private final PublicCatalogService catalog;
    private final CmsArticleApplicationService cms;
    private final SiteOperator operator;
    private final String baseUrl;

    public PrerenderService(ListingReadService reads, ListingSearchService search, PublicCatalogService catalog,
                            CmsArticleApplicationService cms, SiteOperator operator,
                            @Value("${app.public-base-url:http://localhost:3000}") String baseUrl) {
        this.reads = reads;
        this.search = search;
        this.catalog = catalog;
        this.cms = cms;
        this.operator = operator;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** {@code path} is the decoded page path ("/listings/abc"), {@code query} the page's parameters. */
    public RenderedPage render(String path, Map<String, String[]> query) {
        String p = path == null || path.isEmpty() ? "/" : path;
        if (p.length() > 1 && p.endsWith("/")) return RenderedPage.redirect(stripSlashes(p) + queryString(query));
        if (p.length() > 512) return notFound();
        try {
            if (p.equals("/")) return home();
            if (p.equals("/search")) return searchPage(query);
            if (APP_ROUTES.contains(p) || APP_PREFIXES.stream().anyMatch(p::startsWith)) return appRoute();
            if (INFO_PAGES.containsKey(p)) return infoPage(p);
            String[] parts = p.substring(1).split("/", -1);
            if (parts.length == 2 && parts[0].equals("listings")) return listing(parts[1]);
            if (parts.length == 2 && parts[0].equals("nguoi-dang")) return seller(parts[1]);
            if (parts.length == 1 && parts[0].equals("du-an")) return projectList(query);
            if (parts.length == 2 && parts[0].equals("du-an")) return project(parts[1]);
            if (parts.length == 1 && parts[0].equals("khu-vuc")) return areaList();
            if (parts.length == 2 && parts[0].equals("khu-vuc")) return area(parts[1]);
            if (parts.length == 1 && parts[0].equals("tin-tuc")) return articleList(query);
            if (parts.length == 2 && parts[0].equals("tin-tuc")) return article(parts[1]);
            if (parts.length == 3 && parts[0].equals("tin-tuc") && parts[1].equals("xem-truoc")) return preview();
            return notFound();
        } catch (ApiException ex) {
            if (ex.status().value() == 410) return gone(ex.getMessage());
            if (ex.status().value() == 404) return notFound();
            throw ex;
        } catch (SearchProblemException ex) {
            if (ex.status() == 410) return gone("Tin đăng này đã được ẩn, hết hạn hoặc bị gỡ.");
            if (ex.status() == 404) return notFound();
            throw ex;
        }
    }

    // ---------------------------------------------------------------- pages

    private RenderedPage home() {
        String description = "Tìm mua, thuê và so sánh bất động sản tại Hà Nội với tin đăng đã kiểm duyệt, giá thuê theo tháng và thông tin người đăng rõ ràng.";
        StringBuilder body = new StringBuilder("<main><h1>Ngôi nhà phù hợp, từ thông tin rõ ràng.</h1>")
                .append("<p>").append(esc(description)).append("</p>")
                .append("<form action=\"/search\" method=\"get\" role=\"search\"><label>Từ khóa hoặc địa điểm <input name=\"q\" maxlength=\"100\"></label>")
                .append("<label>Nhu cầu <select name=\"purpose\"><option value=\"SALE\">Mua</option><option value=\"RENT\">Thuê</option></select></label>")
                .append("<button type=\"submit\">Tìm kiếm</button></form>");
        body.append(section("Bất động sản mới đăng bán", listingList(searchRows(Map.of("purpose", "SALE"), 12)), "/search?purpose=SALE", "Xem tất cả tin bán"));
        body.append(section("Bất động sản mới cho thuê", listingList(searchRows(Map.of("purpose", "RENT"), 6)), "/search?purpose=RENT", "Xem tất cả tin cho thuê"));
        PublicCatalogService.Home home = catalog.home();
        if (!home.areas().isEmpty()) body.append(section("Khu vực có nhiều tin", areaList(home.areas()), "/khu-vuc", "Tất cả khu vực"));
        if (!home.projects().isEmpty()) body.append(section("Dự án đang có tin", projectCards(home.projects()), "/du-an", "Tất cả dự án"));
        List<PublicArticle> articles = cms.publicPage(null, 0, 3).items();
        if (!articles.isEmpty()) body.append(section("Bài viết mới", articleCards(articles), "/tin-tuc", "Tất cả bài viết"));
        body.append("</main>");
        Map<String, Object> website = obj("@context", "https://schema.org", "@type", "WebSite", "name", SITE, "url", url("/"),
                "inLanguage", "vi-VN",
                "potentialAction", obj("@type", "SearchAction", "target", url("/search") + "?q={search_term_string}",
                        "query-input", "required name=search_term_string"));
        List<Object> jsonLd = new ArrayList<>(List.of(website));
        SiteOperator.Info info = operator.info();
        if (info.legalName() != null) {
            Map<String, Object> org = obj("@context", "https://schema.org", "@type", "Organization", "name", SITE,
                    "legalName", info.legalName(), "url", url("/"));
            if (info.email() != null) org.put("email", info.email());
            if (info.address() != null) org.put("address", info.address());
            jsonLd.add(org);
        }
        return RenderedPage.page(200).title(SITE + " — Mua bán, cho thuê nhà đất Hà Nội").description(description)
                .canonical(url("/")).openGraph(og(SITE + " — Mua bán, cho thuê nhà đất", description, "website", url("/"), null))
                .jsonLd(jsonLd).body(body.toString()).build();
    }

    private RenderedPage searchPage(Map<String, String[]> query) {
        String purpose = "RENT".equals(first(query, "purpose")) ? "RENT" : "SALE";
        boolean filtered = query.keySet().stream().anyMatch(k -> !INDEXABLE_SEARCH_PARAMS.contains(k))
                || (query.containsKey("purpose") && !List.of("SALE", "RENT").contains(first(query, "purpose")));
        String label = "RENT".equals(purpose) ? "Nhà đất cho thuê" : "Nhà đất bán";
        String title = label + " tại Hà Nội | " + SITE;
        String description = label + " tại Hà Nội: tin đã kiểm duyệt, lọc theo loại hình, giá, diện tích và khu vực.";
        List<PublicListing> rows = List.of();
        String note = "";
        try {
            Map<String, String[]> params = new HashMap<>(query);
            params.put("size", new String[]{"24"});
            params.remove("cursor");
            params.remove("view");
            rows = search.search(SearchFilterParser.parse(params)).items();
        } catch (InvalidFilterException | SearchProblemException ex) {
            note = "<p>Bộ lọc trong đường dẫn không hợp lệ.</p>";
            filtered = true;
        }
        String body = "<main><h1>" + esc(label) + "</h1>" + note
                + (rows.isEmpty() ? "<p>Chưa có tin phù hợp với bộ lọc này.</p>" : listingList(rows)) + "</main>";
        String canonical = url("/search") + "?purpose=" + purpose;
        return RenderedPage.page(200).title(title).description(description).canonical(canonical)
                .robots(filtered ? RenderedPage.NOINDEX_FOLLOW : RenderedPage.INDEX)
                .openGraph(og(label + " tại Hà Nội", description, "website", canonical, null))
                .jsonLd(List.of(breadcrumbs(List.of(new String[]{"Trang chủ", "/"}, new String[]{label, "/search?purpose=" + purpose}))))
                .body(body).build();
    }

    private RenderedPage listing(String slugOrId) {
        if (slugOrId.isEmpty() || slugOrId.length() > 200) return notFound();
        DetailRef ref = reads.ref(slugOrId);
        DetailSnapshot snapshot = reads.detail(ref);
        PublicListing row = snapshot.listing();
        // canonical URL is the slug: an id or an old spelling is a permanent redirect (F16.3)
        if (!slugOrId.equals(row.slug())) return RenderedPage.redirect("/listings/" + row.slug());
        String path = "/listings/" + row.slug();
        String title = ContactInfoGuard.redact(row.title());
        String place = row.addressSummary() != null ? row.addressSummary() : row.districtName() != null ? row.districtName() : "Hà Nội";
        boolean rent = "RENT".equals(row.purpose());
        String price = money(row.priceVnd(), row.pricePeriod());
        String description = com.company.bds.seo.render.Html.propertyType(row.propertyType()) + (rent ? " cho thuê" : " cần bán")
                + " tại " + place + ", diện tích " + area(row.areaM2()) + ", giá " + price + ".";
        List<String> images = snapshot.images().stream().map(ImageDto::url).map(this::absolute).toList();
        StringBuilder body = new StringBuilder("<main><article>")
                .append(breadcrumbNav(List.of(new String[]{"Trang chủ", "/"}, new String[]{rent ? "Cho thuê" : "Mua bán", "/search?purpose=" + row.purpose()})))
                .append("<h1>").append(esc(title)).append("</h1>")
                .append("<p><strong>").append(esc(price)).append("</strong> · ").append(esc(area(row.areaM2()))).append(" · ").append(esc(place)).append("</p>");
        if (!images.isEmpty()) {
            body.append("<img src=\"").append(esc(images.get(0))).append("\" alt=\"").append(esc(title)).append("\" width=\"960\" height=\"720\">");
        }
        body.append("<ul>")
                .append(fact("Loại hình", com.company.bds.seo.render.Html.propertyType(row.propertyType())))
                .append(fact("Nhu cầu", rent ? "Cho thuê" : "Bán"))
                .append(row.bedrooms() == null ? "" : fact("Phòng ngủ", row.bedrooms().toString()))
                .append(row.bathrooms() == null ? "" : fact("Phòng tắm", row.bathrooms().toString()))
                .append(row.legalStatusText() == null ? "" : fact("Pháp lý", row.legalStatusText()))
                .append(row.projectName() == null ? "" : "<li>Dự án: <a href=\"/du-an/" + esc(row.projectSlug()) + "\">" + esc(row.projectName()) + "</a></li>")
                .append(fact("Ngày đăng", com.company.bds.seo.render.Html.date(row.publishedAt())))
                .append("</ul>");
        if (row.description() != null) {
            body.append("<h2>Mô tả</h2><p>").append(esc(ContactInfoGuard.redact(row.description())).replace("\n", "<br>")).append("</p>");
        }
        if (row.sellerName() != null) {
            body.append("<p>Người đăng: <a href=\"/nguoi-dang/").append(row.ownerId()).append("\">").append(esc(row.sellerName())).append("</a></p>");
        }
        body.append("</article></main>");
        Map<String, Object> offer = obj("@type", "Offer", "price", row.priceVnd(), "priceCurrency", "VND",
                "availability", "https://schema.org/InStock", "url", url(path));
        if (rent) {
            offer.put("priceSpecification", obj("@type", "UnitPriceSpecification", "price", row.priceVnd(), "priceCurrency", "VND",
                    "unitCode", "MON", "referenceQuantity", obj("@type", "QuantitativeValue", "value", 1, "unitCode", "MON")));
        }
        Map<String, Object> product = obj("@context", "https://schema.org", "@type", "Product", "name", title,
                "category", "Bất động sản", "url", url(path), "image", images, "offers", offer,
                "address", obj("@type", "PostalAddress", "streetAddress", place, "addressLocality", "Hà Nội", "addressCountry", "VN"));
        if (row.description() != null) product.put("description", excerpt(ContactInfoGuard.redact(row.description()), 500));
        if (row.publishedAt() != null) product.put("datePosted", row.publishedAt().toString());
        return RenderedPage.page(200).title(title + " | " + SITE).description(description).canonical(url(path))
                .openGraph(og(title, description, "product", url(path), images.isEmpty() ? null : images.get(0)))
                .jsonLd(List.of(product, breadcrumbs(List.of(new String[]{"Trang chủ", "/"},
                        new String[]{rent ? "Cho thuê" : "Mua bán", "/search?purpose=" + row.purpose()}, new String[]{title, path}))))
                .body(body.toString()).build();
    }

    private RenderedPage seller(String idText) {
        if (!UUID_PATTERN.matcher(idText).matches()) return notFound();
        UUID id = UUID.fromString(idText);
        SellerProfile seller = reads.seller(id);
        List<PublicListing> rows = reads.sellerListings(id, 24, null).items();
        String path = "/nguoi-dang/" + id;
        String name = seller.name() == null ? "Người đăng" : seller.name();
        String description = name + " đang có " + seller.activeListings() + " tin đăng công khai trên " + SITE + ".";
        String body = "<main><h1>" + esc(name) + "</h1><p>" + esc(description) + "</p>"
                + (rows.isEmpty() ? "<p>Người đăng chưa có tin công khai.</p>" : listingList(rows)) + "</main>";
        return RenderedPage.page(200).title(name + " | " + SITE).description(description).canonical(url(path))
                .robots(seller.activeListings() > 0 ? RenderedPage.INDEX : RenderedPage.NOINDEX_FOLLOW)
                .openGraph(og(name, description, "profile", url(path), null)).body(body).build();
    }

    private RenderedPage projectList(Map<String, String[]> query) {
        int page = pageParam(query);
        PublicCatalogService.ProjectList list = catalog.projects(null, page, 24);
        if (page > 0 && list.items().isEmpty()) return notFound();
        String path = "/du-an" + (page > 0 ? "?page=" + page : "");
        String description = "Danh sách dự án nhà ở tại Hà Nội kèm số tin đang bán, cho thuê và thông tin có nguồn.";
        String body = "<main><h1>Dự án bất động sản</h1><p>" + esc(description) + "</p>"
                + (list.items().isEmpty() ? "<p>Chưa có dự án công khai.</p>" : projectCards(list.items()))
                + pager("/du-an", page, (long) (page + 1) * list.size() < list.total()) + "</main>";
        return RenderedPage.page(200).title("Dự án bất động sản tại Hà Nội | " + SITE).description(description)
                .canonical(url(path)).openGraph(og("Dự án bất động sản", description, "website", url(path), null))
                .jsonLd(List.of(breadcrumbs(List.of(new String[]{"Trang chủ", "/"}, new String[]{"Dự án", "/du-an"}))))
                .body(body).build();
    }

    private RenderedPage project(String slug) {
        if (!SLUG.matcher(slug).matches()) return notFound();
        ProjectPage page = catalog.project(slug);
        ProjectRow p = page.project();
        String path = "/du-an/" + p.slug();
        String place = p.districtName() == null ? p.address() : p.address() + " (" + p.districtName() + ")";
        String description = excerpt(p.description() != null ? p.description()
                : p.name() + " của " + p.developerName() + " tại " + place + ". Xem tin đang bán, cho thuê và thống kê giá chào.", 300);
        StringBuilder body = new StringBuilder("<main><article>")
                .append(breadcrumbNav(List.of(new String[]{"Trang chủ", "/"}, new String[]{"Dự án", "/du-an"})))
                .append("<h1>").append(esc(p.name())).append("</h1>")
                .append("<p>").append(esc(description)).append("</p><ul>")
                .append(fact("Chủ đầu tư", p.developerName()))
                .append(fact("Địa chỉ", p.address()))
                .append(p.districtName() == null ? "" : "<li>Khu vực: <a href=\"/khu-vuc/" + esc(p.areaSlug()) + "\">" + esc(p.districtName()) + "</a></li>")
                .append(p.handoverYear() == null ? "" : fact("Năm bàn giao dự kiến", p.handoverYear().toString()))
                .append(p.totalUnits() > 0 ? fact("Số căn", Integer.toString(p.totalUnits())) : "")
                .append(fact("Giấy phép", p.legalLicenseNumber()))
                .append("</ul>");
        if (p.infoSource() != null) {
            body.append("<p>Nguồn thông tin dự án: ").append(esc(p.infoSource()))
                    .append(p.infoCheckedAt() == null ? "" : ", kiểm tra ngày " + esc(p.infoCheckedAt().toString())).append("</p>");
        }
        body.append(statistics(page.inventory()));
        if (!page.amenities().isEmpty()) {
            body.append("<h2>Tiện ích xung quanh (có nguồn)</h2><ul>");
            for (Amenity a : page.amenities()) {
                body.append("<li>").append(esc(a.name())).append(a.distanceM() == null ? "" : " — khoảng " + a.distanceM() + " m")
                        .append(" (nguồn: ").append(esc(a.sourceName())).append(", kiểm tra ").append(esc(a.checkedAt().toString())).append(")</li>");
            }
            body.append("</ul>");
        }
        body.append(section("Tin đăng trong dự án", listingList(searchRows(Map.of("project", p.id().toString()), 12)),
                "/search?project=" + p.id(), "Xem tất cả tin trong dự án"));
        body.append("</article></main>");
        Map<String, Object> place2 = obj("@context", "https://schema.org", "@type", "Place", "name", p.name(), "url", url(path),
                "address", obj("@type", "PostalAddress", "streetAddress", p.address(), "addressLocality", "Hà Nội", "addressCountry", "VN"));
        if (p.description() != null) place2.put("description", excerpt(p.description(), 500));
        return RenderedPage.page(200).title(p.name() + " — dự án tại " + (p.districtName() == null ? "Hà Nội" : p.districtName()) + " | " + SITE)
                .description(description).canonical(url(path)).openGraph(og(p.name(), description, "website", url(path), null))
                .jsonLd(List.of(place2, breadcrumbs(List.of(new String[]{"Trang chủ", "/"}, new String[]{"Dự án", "/du-an"}, new String[]{p.name(), path}))))
                .body(body.toString()).build();
    }

    private RenderedPage areaList() {
        PublicCatalogService.AreaList areas = catalog.areas();
        String description = "Khu vực tìm nhà tại Hà Nội và số tin đang hiển thị ở mỗi khu vực.";
        String body = "<main><h1>Khu vực</h1><p>" + esc(description) + "</p>" + areaList(areas.items()) + "</main>";
        return RenderedPage.page(200).title("Nhà đất theo khu vực tại Hà Nội | " + SITE).description(description)
                .canonical(url("/khu-vuc")).openGraph(og("Nhà đất theo khu vực", description, "website", url("/khu-vuc"), null))
                .jsonLd(List.of(breadcrumbs(List.of(new String[]{"Trang chủ", "/"}, new String[]{"Khu vực", "/khu-vuc"}))))
                .body(body).build();
    }

    private RenderedPage area(String slug) {
        if (!SLUG.matcher(slug).matches()) return notFound();
        AreaPage page = catalog.area(slug);
        String path = "/khu-vuc/" + page.area().slug();
        String name = page.area().name();
        String description = "Nhà đất bán và cho thuê tại " + name + ", " + page.area().provinceName() + ": "
                + page.inventory().total() + " tin đang hiển thị, thống kê giá chào có phương pháp và dự án trong khu vực.";
        StringBuilder body = new StringBuilder("<main>")
                .append(breadcrumbNav(List.of(new String[]{"Trang chủ", "/"}, new String[]{"Khu vực", "/khu-vuc"})))
                .append("<h1>Nhà đất ").append(esc(name)).append("</h1><p>").append(esc(description)).append("</p>")
                .append("<p>Khu vực tìm kiếm theo địa giới quận/huyện trước ngày 01/07/2025.</p>")
                .append(statistics(page.inventory()));
        body.append(section("Tin bán mới tại " + name, listingList(searchRows(Map.of("purpose", "SALE", "district", page.area().districtCode()), 12)),
                "/search?purpose=SALE&district=" + page.area().districtCode(), "Xem tất cả tin bán"));
        body.append(section("Tin cho thuê mới tại " + name, listingList(searchRows(Map.of("purpose", "RENT", "district", page.area().districtCode()), 6)),
                "/search?purpose=RENT&district=" + page.area().districtCode(), "Xem tất cả tin cho thuê"));
        if (!page.projects().isEmpty()) body.append("<h2>Dự án tại ").append(esc(name)).append("</h2>").append(projectCards(page.projects()));
        body.append("</main>");
        Map<String, Object> placeLd = obj("@context", "https://schema.org", "@type", "Place", "name", name + ", " + page.area().provinceName(),
                "url", url(path), "containedInPlace", obj("@type", "AdministrativeArea", "name", page.area().provinceName()));
        return RenderedPage.page(200).title("Nhà đất " + name + " — bán, cho thuê | " + SITE).description(description)
                .canonical(url(path)).openGraph(og("Nhà đất " + name, description, "website", url(path), null))
                .jsonLd(List.of(placeLd, breadcrumbs(List.of(new String[]{"Trang chủ", "/"}, new String[]{"Khu vực", "/khu-vuc"}, new String[]{name, path}))))
                .body(body.toString()).build();
    }

    private RenderedPage articleList(Map<String, String[]> query) {
        int page = pageParam(query);
        ArticleCategory category = null;
        String categoryText = first(query, "category");
        boolean extraParams = query.keySet().stream().anyMatch(k -> !Set.of("page", "category").contains(k));
        if (categoryText != null) {
            try { category = ArticleCategory.valueOf(categoryText); } catch (IllegalArgumentException ex) { return notFound(); }
        }
        PublicPage result = cms.publicPage(category, page, 12);
        if (page > 0 && result.items().isEmpty()) return notFound();
        String heading = category == null ? "Tin tức và cẩm nang" : CmsDtos.categoryLabel(category);
        String base = "/tin-tuc" + (category == null ? "" : "?category=" + category.name());
        String path = page == 0 ? base : base + (category == null ? "?" : "&") + "page=" + page;
        String description = "Bài viết đã qua biên tập về pháp lý, kiến thức và thị trường nhà đất, ghi rõ tác giả và nguồn.";
        String body = "<main><h1>" + esc(heading) + "</h1><p>" + esc(description) + "</p>"
                + (result.items().isEmpty() ? "<p>Chưa có bài viết.</p>" : articleCards(result.items()))
                + pager(base, page, (long) (page + 1) * 12 < result.total()) + "</main>";
        return RenderedPage.page(200).title(heading + " | " + SITE).description(description).canonical(url(path))
                .robots(extraParams ? RenderedPage.NOINDEX_FOLLOW : RenderedPage.INDEX)
                .openGraph(og(heading, description, "website", url(path), null))
                .jsonLd(List.of(breadcrumbs(List.of(new String[]{"Trang chủ", "/"}, new String[]{"Tin tức", "/tin-tuc"}))))
                .body(body).build();
    }

    private RenderedPage article(String slug) {
        if (slug.length() > 255) return notFound();
        PublicLookup lookup = cms.publicBySlug(slug);
        if (lookup.article() == null) return lookup.gone() ? gone("Bài viết đã được gỡ khỏi trang công khai.") : notFound();
        PublicArticle article = lookup.article();
        ArticleRevision r = article.revision();
        String path = "/tin-tuc/" + article.article().slug();
        String description = r.metaDescription() != null ? r.metaDescription()
                : r.summary() != null ? excerpt(r.summary(), 300) : excerpt(CmsHtmlSanitizer.text(r.contentHtml()), 300);
        String cover = r.coverImageUrl() == null ? null : absolute(r.coverImageUrl());
        StringBuilder body = new StringBuilder("<main><article>")
                .append(breadcrumbNav(List.of(new String[]{"Trang chủ", "/"}, new String[]{"Tin tức", "/tin-tuc"})))
                .append("<p>").append(esc(CmsDtos.categoryLabel(article.article().category()))).append("</p>")
                .append("<h1>").append(esc(r.title())).append("</h1>")
                .append("<p>Tác giả: ").append(esc(r.authorName())).append(" · Xuất bản ").append(esc(com.company.bds.seo.render.Html.date(article.publishedAt())))
                .append("</p>");
        if (r.summary() != null) body.append("<p><strong>").append(esc(r.summary())).append("</strong></p>");
        // stored bodies are sanitised on write; sanitise again so rows written before V090 are safe too
        body.append("<div>").append(CmsHtmlSanitizer.sanitize(r.contentHtml())).append("</div>");
        if (r.sourceName() != null || r.legalReference() != null) {
            body.append("<h2>Nguồn tham khảo</h2><ul>");
            if (r.sourceName() != null) {
                body.append("<li>").append(r.sourceUrl() == null ? esc(r.sourceName())
                        : "<a href=\"" + esc(r.sourceUrl()) + "\" rel=\"nofollow noopener noreferrer\">" + esc(r.sourceName()) + "</a>").append("</li>");
            }
            if (r.legalReference() != null) body.append("<li>").append(esc(r.legalReference())).append("</li>");
            body.append("</ul>");
        }
        body.append("</article></main>");
        Map<String, Object> ld = obj("@context", "https://schema.org", "@type", "Article", "headline", excerpt(r.title(), 110),
                "description", description, "url", url(path), "mainEntityOfPage", url(path), "inLanguage", "vi-VN",
                "author", obj("@type", "Person", "name", r.authorName()),
                "publisher", obj("@type", "Organization", "name", SITE, "url", url("/")));
        if (article.publishedAt() != null) ld.put("datePublished", article.publishedAt().toString());
        if (article.article().updatedAt() != null) ld.put("dateModified", article.article().updatedAt().toString());
        if (cover != null) ld.put("image", List.of(cover));
        return RenderedPage.page(200).title(r.title() + " | " + SITE).description(description).canonical(url(path))
                .openGraph(og(r.title(), description, "article", url(path), cover))
                .jsonLd(List.of(ld, breadcrumbs(List.of(new String[]{"Trang chủ", "/"}, new String[]{"Tin tức", "/tin-tuc"}, new String[]{r.title(), path}))))
                .body(body.toString()).build();
    }

    /** Preview links carry a secret: never indexed, cached or rendered server-side (the SPA fetches with the token). */
    private RenderedPage preview() {
        return RenderedPage.page(200).title("Xem trước bài viết | " + SITE).robots(RenderedPage.NOINDEX_NOFOLLOW)
                .cacheControl(RenderedPage.CACHE_PRIVATE).xRobotsTag("noindex, nofollow").body("").build();
    }

    private RenderedPage infoPage(String path) {
        String[] page = INFO_PAGES.get(path);
        SiteOperator.Info info = operator.info();
        StringBuilder body = new StringBuilder("<main><article><h1>").append(esc(page[0])).append("</h1><p>").append(esc(page[1])).append("</p>")
                .append("<h2>Đơn vị vận hành</h2><ul>")
                .append(fact("Tên pháp nhân", orMissing(info.legalName())))
                .append(fact("Đăng ký kinh doanh", orMissing(info.businessRegistration())))
                .append(fact("Mã số thuế", orMissing(info.taxCode())))
                .append(fact("Địa chỉ", orMissing(info.address())))
                .append(fact("Email", orMissing(info.email())))
                .append("</ul>");
        List<PublicArticle> policies = cms.publicPage(ArticleCategory.LEGAL_POLICY, 0, 20).items();
        if (!policies.isEmpty()) body.append("<h2>Văn bản chính sách đã duyệt</h2>").append(articleCards(policies));
        body.append("</article></main>");
        return RenderedPage.page(200).title(page[0] + " | " + SITE).description(page[1]).canonical(url(path))
                .openGraph(og(page[0], page[1], "website", url(path), null)).body(body.toString()).build();
    }

    private RenderedPage appRoute() {
        return RenderedPage.page(200).title(SITE).robots(RenderedPage.NOINDEX_NOFOLLOW).cacheControl(RenderedPage.CACHE_PRIVATE)
                .xRobotsTag("noindex, nofollow").body("").build();
    }

    public RenderedPage notFound() {
        return RenderedPage.page(404).title("Không tìm thấy trang | " + SITE).description("Trang bạn tìm không tồn tại.")
                .robots(RenderedPage.NOINDEX_FOLLOW).cacheControl(RenderedPage.CACHE_PRIVATE)
                .body("<main><p>404</p><h1>Không tìm thấy trang</h1><p><a href=\"/\">Về trang chủ</a> · <a href=\"/search\">Tìm nhà</a></p></main>")
                .build();
    }

    private RenderedPage gone(String message) {
        return RenderedPage.page(410).title("Nội dung không còn hiển thị | " + SITE).description(message)
                .robots(RenderedPage.NOINDEX_FOLLOW).cacheControl(RenderedPage.CACHE_PRIVATE)
                .body("<main><p>410</p><h1>Nội dung không còn hiển thị</h1><p>" + esc(message)
                        + "</p><p><a href=\"/search\">Tìm tin khác</a></p></main>")
                .build();
    }

    // ---------------------------------------------------------------- fragments

    private List<PublicListing> searchRows(Map<String, String> params, int size) {
        Map<String, String[]> raw = new HashMap<>();
        params.forEach((k, v) -> raw.put(k, new String[]{v}));
        raw.put("size", new String[]{Integer.toString(size)});
        try {
            SearchResults.Page page = search.search(SearchFilterParser.parse(raw));
            return page.items();
        } catch (RuntimeException ex) {
            // a search outage must not turn a landing page into an error: the SPA loads the list itself
            log.warn("seo_prerender_search_failed reason={}", ex.getClass().getSimpleName());
            return List.of();
        }
    }

    private static String listingList(List<PublicListing> rows) {
        if (rows.isEmpty()) return "<p>Chưa có tin trong mục này.</p>";
        StringBuilder out = new StringBuilder("<ul>");
        for (PublicListing row : rows) {
            out.append("<li><a href=\"/listings/").append(esc(row.slug())).append("\">").append(esc(ContactInfoGuard.redact(row.title())))
                    .append("</a> — ").append(esc(money(row.priceVnd(), row.pricePeriod()))).append(" · ").append(esc(area(row.areaM2())));
            if (row.districtName() != null) out.append(" · ").append(esc(row.districtName()));
            out.append("</li>");
        }
        return out.append("</ul>").toString();
    }

    private static String areaList(List<AreaCard> areas) {
        StringBuilder out = new StringBuilder("<ul>");
        for (AreaCard a : areas) {
            out.append("<li><a href=\"/khu-vuc/").append(esc(a.slug())).append("\">").append(esc(a.name())).append("</a> — ")
                    .append(a.activeListings()).append(" tin</li>");
        }
        return out.append("</ul>").toString();
    }

    private static String projectCards(List<ProjectCard> projects) {
        StringBuilder out = new StringBuilder("<ul>");
        for (ProjectCard p : projects) {
            out.append("<li><a href=\"/du-an/").append(esc(p.slug())).append("\">").append(esc(p.name())).append("</a>");
            if (p.districtName() != null) out.append(" — ").append(esc(p.districtName()));
            out.append(" · ").append(p.activeListings()).append(" tin</li>");
        }
        return out.append("</ul>").toString();
    }

    private static String articleCards(List<PublicArticle> articles) {
        StringBuilder out = new StringBuilder("<ul>");
        for (PublicArticle a : articles) {
            out.append("<li><a href=\"/tin-tuc/").append(esc(a.article().slug())).append("\">").append(esc(a.revision().title())).append("</a>");
            if (a.revision().summary() != null) out.append(" — ").append(esc(excerpt(a.revision().summary(), 160)));
            out.append("</li>");
        }
        return out.append("</ul>").toString();
    }

    private static String statistics(Inventory inventory) {
        StringBuilder out = new StringBuilder("<h2>Thống kê tin đang hiển thị</h2><ul>");
        for (Statistic s : inventory.statistics()) {
            boolean sale = "SALE".equals(s.purpose());
            out.append("<li>").append(sale ? "Tin bán" : "Tin cho thuê").append(": ").append(s.count()).append(" tin");
            if (s.median() != null) {
                out.append(", trung vị ").append(sale ? esc(money(Math.round(s.median()), null)) + "/m²" : esc(money(Math.round(s.median()), "MONTH")));
            } else {
                out.append(", chưa đủ ").append(s.minSamples()).append(" tin để tính trung vị");
            }
            out.append("</li>");
        }
        if (inventory.statistics().isEmpty()) out.append("<li>Chưa có tin đang hiển thị.</li>");
        out.append("</ul><p>Phương pháp: ").append(esc(PublicCatalogService.METHOD_SALE)).append(" ")
                .append(esc(PublicCatalogService.METHOD_RENT)).append("</p>");
        return out.toString();
    }

    private static String section(String heading, String content, String moreHref, String moreLabel) {
        return "<section><h2>" + esc(heading) + "</h2>" + content + "<p><a href=\"" + esc(moreHref) + "\">" + esc(moreLabel) + "</a></p></section>";
    }

    private static String fact(String label, String value) {
        return value == null ? "" : "<li>" + esc(label) + ": " + esc(value) + "</li>";
    }

    private static String pager(String base, int page, boolean hasNext) {
        String join = base.contains("?") ? "&" : "?";
        StringBuilder out = new StringBuilder("<nav aria-label=\"Phân trang\">");
        if (page > 0) out.append("<a href=\"").append(esc(page == 1 ? base : base + join + "page=" + (page - 1))).append("\" rel=\"prev\">Trang trước</a> ");
        if (hasNext) out.append("<a href=\"").append(esc(base + join + "page=" + (page + 1))).append("\" rel=\"next\">Trang sau</a>");
        return out.append("</nav>").toString();
    }

    private static String breadcrumbNav(List<String[]> items) {
        StringBuilder out = new StringBuilder("<nav aria-label=\"Đường dẫn\"><ol>");
        for (String[] item : items) out.append("<li><a href=\"").append(esc(item[1])).append("\">").append(esc(item[0])).append("</a></li>");
        return out.append("</ol></nav>").toString();
    }

    private Map<String, Object> breadcrumbs(List<String[]> items) {
        List<Object> elements = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            elements.add(obj("@type", "ListItem", "position", i + 1, "name", items.get(i)[0], "item", url(items.get(i)[1])));
        }
        return obj("@context", "https://schema.org", "@type", "BreadcrumbList", "itemListElement", elements);
    }

    private Map<String, String> og(String title, String description, String type, String url, String image) {
        Map<String, String> og = new LinkedHashMap<>();
        og.put("title", title);
        og.put("description", description);
        og.put("type", type);
        og.put("url", url);
        if (image != null) og.put("image", image);
        return og;
    }

    private static Map<String, Object> obj(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    private String url(String path) {
        return baseUrl + path;
    }

    private String absolute(String value) {
        return value.startsWith("http://") || value.startsWith("https://") ? value : baseUrl + (value.startsWith("/") ? "" : "/") + value;
    }

    private static String area(BigDecimal value) {
        return value == null ? "" : com.company.bds.seo.render.Html.number(value.doubleValue(), 1) + " m²";
    }

    private static String orMissing(String value) {
        return value == null ? "Chưa có dữ liệu" : value;
    }

    private static String first(Map<String, String[]> query, String name) {
        String[] values = query.get(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    private static int pageParam(Map<String, String[]> query) {
        String value = first(query, "page");
        if (value == null) return 0;
        try {
            return Math.max(0, Math.min(Integer.parseInt(value), 500));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String stripSlashes(String path) {
        String stripped = path.replaceAll("/+$", "");
        return stripped.isEmpty() ? "/" : stripped;
    }

    static String queryString(Map<String, String[]> query) {
        if (query == null || query.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String[]> entry : query.entrySet()) {
            for (String value : entry.getValue()) {
                out.append(out.length() == 0 ? "?" : "&").append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        return out.toString();
    }
}
