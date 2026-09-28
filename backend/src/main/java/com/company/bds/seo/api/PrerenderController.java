package com.company.bds.seo.api;

import com.company.bds.seo.application.PrerenderService;
import com.company.bds.seo.render.RenderedPage;
import com.company.bds.seo.render.SpaShell;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UrlPathHelper;

/**
 * {@code GET /render/<page path>?<page query>}: the page as HTML with its real status (Nginx sends every page request
 * that is not a static file here, see {@code frontend/nginx.conf} {@code @prerender}). Any failure answers 503 so
 * Nginx serves the static SPA shell instead: prerendering can degrade, the site cannot go down because of it.
 */
@RestController
public class PrerenderController {
    private static final Logger log = LoggerFactory.getLogger(PrerenderController.class);
    static final String PREFIX = "/render";
    private static final MediaType HTML = new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8);

    private final PrerenderService pages;
    private final SpaShell shell;

    public PrerenderController(PrerenderService pages, SpaShell shell) {
        this.pages = pages;
        this.shell = shell;
    }

    @GetMapping({PREFIX, PREFIX + "/**"})
    public ResponseEntity<String> render(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request).substring(PREFIX.length());
        try {
            RenderedPage page = pages.render(path.isEmpty() ? "/" : path, request.getParameterMap());
            if (page.isRedirect()) {
                return ResponseEntity.status(301).header(HttpHeaders.LOCATION, page.redirectTo())
                        .header(HttpHeaders.CACHE_CONTROL, page.cacheControl()).contentType(HTML).body("");
            }
            ResponseEntity.BodyBuilder response = ResponseEntity.status(page.status()).contentType(HTML)
                    .header(HttpHeaders.CACHE_CONTROL, page.cacheControl());
            if (page.xRobotsTag() != null) response.header("X-Robots-Tag", page.xRobotsTag());
            else if (page.status() >= 400) response.header("X-Robots-Tag", "noindex");
            return response.body(shell.html(page));
        } catch (RuntimeException ex) {
            log.warn("seo_prerender_failed reason={}", ex.getClass().getSimpleName());
            log.debug("seo_prerender_failed", ex);
            return ResponseEntity.status(503).header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.RETRY_AFTER, "30")
                    .contentType(MediaType.TEXT_PLAIN).body("prerender unavailable");
        }
    }
}
