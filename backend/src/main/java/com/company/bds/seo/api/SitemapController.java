package com.company.bds.seo.api;

import com.company.bds.seo.application.SitemapService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Sitemap index, parts and robots.txt. Nginx maps {@code /sitemap.xml}, {@code /sitemaps/<part>.xml} and
 * {@code /robots.txt} here, so URLs in the files are the public ones.
 */
@RestController
public class SitemapController {
    private static final MediaType XML = new MediaType("application", "xml", java.nio.charset.StandardCharsets.UTF_8);
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic();

    private final SitemapService sitemaps;

    public SitemapController(SitemapService sitemaps) {
        this.sitemaps = sitemaps;
    }

    @GetMapping("/api/v1/public/seo/sitemap.xml")
    public ResponseEntity<String> index() {
        return ResponseEntity.ok().contentType(XML).cacheControl(CACHE).body(sitemaps.index());
    }

    @GetMapping("/api/v1/public/seo/sitemaps/{name}.xml")
    public ResponseEntity<String> part(@PathVariable String name) {
        return sitemaps.part(name)
                .map(xml -> ResponseEntity.ok().contentType(XML).cacheControl(CACHE).body(xml))
                .orElseGet(() -> ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN).body("not found"));
    }

    @GetMapping(value = "/api/v1/public/seo/robots.txt")
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok().contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(sitemaps.robots());
    }
}
