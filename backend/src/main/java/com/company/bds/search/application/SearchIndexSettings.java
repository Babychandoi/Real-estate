package com.company.bds.search.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Search engine settings plus the runtime "alias ready" flag. {@code app.search.index-name} is the alias the
 * application reads and writes through (default {@code bds-listings}); concrete indices are named
 * {@code <alias>-v2-<yyyyMMddHHmmssSSS>}.
 */
@Component
public class SearchIndexSettings {
    private final boolean enabled;
    private final String alias;
    private volatile boolean ready;

    public SearchIndexSettings(@Value("${app.search.elasticsearch.enabled:true}") boolean enabled,
                               @Value("${app.search.index-name:bds-listings}") String alias) {
        this.enabled = enabled;
        this.alias = alias;
    }

    public boolean enabled() { return enabled; }

    public String alias() { return alias; }

    /** Set once the alias points at a backfilled index (after bootstrap/migration on this instance). */
    public boolean ready() { return enabled && ready; }

    public void markReady(boolean value) { ready = value; }
}
