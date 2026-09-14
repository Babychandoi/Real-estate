package com.company.bds.listing.api;

import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.listing.domain.model.ListingRevision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@RestController
public class ListingSeoController {
    private static final int PAGE_SIZE = 100;
    private final ListingPersistencePort listings;
    private final String publicBaseUrl;

    public ListingSeoController(ListingPersistencePort listings,
                                @Value("${app.public-base-url:https://nhadatchuan.online}") String publicBaseUrl) {
        this.listings = listings;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping(value = "/api/v1/public/seo/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")
                .append(url("/", null, "daily", "1.0"))
                .append(url("/search", null, "hourly", "0.9"));
        for (int page = 0; page < 100; page++) {
            List<Listing> batch = listings.findPublicActiveListings(null, page, PAGE_SIZE);
            for (Listing listing : batch) {
                ListingRevision revision = listing.getPublicRevision().orElse(null);
                if (revision == null) continue;
                String path = "/listings/" + slugify(revision.getTitle()) + "-" + listing.getId();
                xml.append(url(path, listing.getUpdatedAt().toString(), "daily", "0.8"));
            }
            if (batch.size() < PAGE_SIZE) break;
        }
        return xml.append("</urlset>").toString();
    }

    private String url(String path, String lastModified, String frequency, String priority) {
        StringBuilder value = new StringBuilder("<url><loc>").append(escape(publicBaseUrl + path)).append("</loc>");
        if (lastModified != null) value.append("<lastmod>").append(escape(lastModified)).append("</lastmod>");
        return value.append("<changefreq>").append(frequency).append("</changefreq><priority>")
                .append(priority).append("</priority></url>").toString();
    }

    static String slugify(String title) {
        String normalized = Normalizer.normalize(title.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) return "bat-dong-san";
        return normalized.substring(0, Math.min(90, normalized.length())).replaceAll("-+$", "");
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
