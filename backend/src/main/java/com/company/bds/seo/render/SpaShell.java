package com.company.bds.seo.render;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The built SPA shell ({@code index.html} with the hashed asset URLs of the current deploy) and the injection of a
 * {@link RenderedPage} into it. The shell comes from {@code app.seo.shell-location} (in Compose the Nginx container:
 * {@code http://frontend:3000/index.html}) and is re-read every {@code app.seo.shell-ttl}; the last good copy is kept
 * when a refresh fails. Without any copy {@link #html} throws and the controller answers 503, which Nginx turns into
 * the static shell (the SPA keeps working, only prerendering is lost).
 *
 * <p>Tags the page changes are marked {@code data-prerender}; a replaced default keeps its original value in
 * {@code data-default}. The SPA ({@code useDocumentMeta}/{@code prerenderHead.ts}) restores those defaults on the first
 * client-side navigation, so prerendered metadata never leaks into another route.
 */
@Component
public class SpaShell {
    private static final Logger log = LoggerFactory.getLogger(SpaShell.class);
    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern ROOT = Pattern.compile("<div id=\"root\">\\s*</div>");
    private static final int MAX_SHELL_BYTES = 512 * 1024;

    private final ResourceLoader resources;
    private final ObjectMapper json;
    private final Clock clock;
    private final String location;
    private final Duration ttl;
    private volatile String cached;
    private volatile Instant loadedAt = Instant.EPOCH;

    public SpaShell(ResourceLoader resources, ObjectMapper json, Clock clock,
                    @Value("${app.seo.shell-location:http://frontend:3000/index.html}") String location,
                    @Value("${app.seo.shell-ttl:PT1M}") Duration ttl) {
        this.resources = resources;
        this.json = json;
        this.clock = clock;
        this.location = location;
        this.ttl = ttl;
    }

    public static class ShellUnavailableException extends RuntimeException {
        ShellUnavailableException(String message, Throwable cause) { super(message, cause); }
    }

    /** Current shell text (cached). */
    public String template() {
        String current = cached;
        if (current != null && loadedAt.plus(ttl).isAfter(clock.instant())) return current;
        synchronized (this) {
            if (cached != null && loadedAt.plus(ttl).isAfter(clock.instant())) return cached;
            try {
                String loaded = load();
                if (!loaded.contains("<head") || !loaded.contains("id=\"root\"")) throw new IOException("not an SPA shell");
                cached = loaded;
            } catch (IOException | RuntimeException ex) {
                if (cached == null) throw new ShellUnavailableException("SPA shell unavailable at " + location, ex);
                log.warn("seo_shell_refresh_failed location={} reason={}", location, ex.getClass().getSimpleName());
            }
            loadedAt = clock.instant();
            return cached;
        }
    }

    private String load() throws IOException {
        Resource resource = resources.getResource(location);
        URLConnection connection = resource.isFile() || location.startsWith("classpath:") ? null : resource.getURL().openConnection();
        if (connection != null) {
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(3_000);
        }
        try (InputStream in = connection == null ? resource.getInputStream() : connection.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_SHELL_BYTES + 1);
            if (bytes.length > MAX_SHELL_BYTES) throw new IOException("shell too large");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** The full document for {@code page}. */
    public String html(RenderedPage page) {
        String html = template();
        Matcher title = TITLE.matcher(html);
        if (page.title() != null) {
            if (title.find()) {
                html = html.substring(0, title.start()) + "<title data-prerender data-default=\"" + title.group(1).replace("\"", "&quot;")
                        + "\">" + Html.esc(page.title()) + "</title>" + html.substring(title.end());
            } else {
                html = beforeHeadEnd(html, "<title data-prerender>" + Html.esc(page.title()) + "</title>");
            }
        }
        StringBuilder extra = new StringBuilder();
        html = meta(html, extra, "name", "description", page.description());
        html = meta(html, extra, "name", "robots", page.robots());
        for (Map.Entry<String, String> og : page.openGraph().entrySet()) {
            html = meta(html, extra, "property", "og:" + og.getKey(), og.getValue());
        }
        if (page.canonical() != null) {
            extra.append("<link rel=\"canonical\" href=\"").append(Html.esc(page.canonical())).append("\" data-prerender />");
        }
        for (Object item : page.jsonLd()) {
            extra.append("<script type=\"application/ld+json\" data-prerender>").append(jsonLd(item)).append("</script>");
        }
        html = beforeHeadEnd(html, extra.toString());
        String body = "<div id=\"root\"><div data-prerender-content>" + (page.body() == null ? "" : page.body()) + "</div></div>";
        Matcher root = ROOT.matcher(html);
        return root.find() ? html.substring(0, root.start()) + body + html.substring(root.end()) : html.replace("</body>", body + "</body>");
    }

    private String jsonLd(Object value) {
        try {
            // "<" can never close the script element; U+2028/9 are valid JSON but not JavaScript in old parsers
            return json.writeValueAsString(value).replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Replaces the value of an existing meta tag (keeping its default in data-default) or queues a new one. */
    private static String meta(String html, StringBuilder extra, String key, String name, String value) {
        if (value == null) return html;
        Pattern pattern = Pattern.compile("<meta\\s+" + key + "=\"" + Pattern.quote(name) + "\"\\s+content=\"([^\"]*)\"\\s*/?>");
        Matcher matcher = pattern.matcher(html);
        String tag = "<meta " + key + "=\"" + name + "\" content=\"" + Html.esc(value) + "\" data-prerender";
        if (matcher.find()) {
            return html.substring(0, matcher.start()) + tag + " data-default=\"" + matcher.group(1) + "\" />" + html.substring(matcher.end());
        }
        extra.append(tag).append(" />");
        return html;
    }

    private static String beforeHeadEnd(String html, String insert) {
        int end = html.indexOf("</head>");
        return end < 0 ? insert + html : html.substring(0, end) + insert + html.substring(end);
    }
}
