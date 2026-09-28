package com.company.bds.seo.render;

import java.util.List;
import java.util.Map;

/**
 * What the prerenderer decided for one URL: HTTP status, metadata and the main content (HTML fragment, already
 * escaped), or a redirect. {@code cacheControl} is the response header; private routes are {@code no-store}.
 */
public record RenderedPage(int status, String redirectTo, String title, String description, String canonical,
                           String robots, Map<String, String> openGraph, List<Object> jsonLd, String body,
                           String cacheControl, String xRobotsTag) {

    public static final String INDEX = "index,follow,max-image-preview:large";
    public static final String NOINDEX_FOLLOW = "noindex,follow";
    public static final String NOINDEX_NOFOLLOW = "noindex,nofollow";
    /** HTML is never stored (same policy as the static shell); page data is cached in the application instead. */
    public static final String CACHE_PUBLIC = "no-store, no-cache, must-revalidate";
    public static final String CACHE_PRIVATE = "no-store";

    public static RenderedPage redirect(String location) {
        return new RenderedPage(301, location, null, null, null, null, Map.of(), List.of(), null, "public, max-age=3600", null);
    }

    public boolean isRedirect() { return redirectTo != null; }

    public static Builder page(int status) { return new Builder(status); }

    public static final class Builder {
        private final int status;
        private String title;
        private String description;
        private String canonical;
        private String robots = INDEX;
        private Map<String, String> openGraph = Map.of();
        private List<Object> jsonLd = List.of();
        private String body = "";
        private String cacheControl = CACHE_PUBLIC;
        private String xRobotsTag;

        private Builder(int status) { this.status = status; }

        public Builder title(String value) { title = value; return this; }
        public Builder description(String value) { description = value; return this; }
        public Builder canonical(String value) { canonical = value; return this; }
        public Builder robots(String value) { robots = value; return this; }
        public Builder openGraph(Map<String, String> value) { openGraph = value; return this; }
        public Builder jsonLd(List<Object> value) { jsonLd = value; return this; }
        public Builder body(String value) { body = value; return this; }
        public Builder cacheControl(String value) { cacheControl = value; return this; }
        public Builder xRobotsTag(String value) { xRobotsTag = value; return this; }

        public RenderedPage build() {
            return new RenderedPage(status, null, title, description, canonical, robots, openGraph, jsonLd, body,
                    cacheControl, xRobotsTag);
        }
    }
}
