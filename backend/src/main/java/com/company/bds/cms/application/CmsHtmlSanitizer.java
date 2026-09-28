package com.company.bds.cms.application;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Allow-list sanitising of article bodies (stored XSS would run on the public site and inside the admin preview).
 * Keeps structure (headings, lists, tables, quotes, emphasis), links (http/https/mailto, relative) and images
 * (http/https or relative, e.g. {@code /api/v1/public/media/...}); drops scripts, styles, event handlers, iframes and
 * {@code javascript:} URLs. External links get {@code rel="nofollow noopener noreferrer"}.
 */
public final class CmsHtmlSanitizer {
    private static final Safelist SAFELIST = Safelist.relaxed()
            .removeTags("div", "span")
            .addTags("figure", "figcaption", "hr")
            .addAttributes("a", "rel", "target")
            .addAttributes("img", "loading")
            .preserveRelativeLinks(true)
            .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");

    private CmsHtmlSanitizer() {}

    public static String sanitize(String html) {
        if (html == null) return null;
        Document.OutputSettings output = new Document.OutputSettings().prettyPrint(false);
        // base URI makes relative links survive the protocol check (preserveRelativeLinks)
        return Jsoup.clean(html, "https://nhadatchuan.invalid/", SAFELIST, output).trim();
    }

    /** Plain text of an HTML fragment (descriptions, reading time). */
    public static String text(String html) {
        return html == null ? "" : Jsoup.parse(html).text();
    }
}
