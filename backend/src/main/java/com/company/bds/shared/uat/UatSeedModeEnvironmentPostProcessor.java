package com.company.bds.shared.uat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * A seeder run ({@code --app.uat-seed.mode=seed|purge}) is a one-off tool JVM that may point at a shared database: it must
 * not claim background jobs (e-mail, indexing) with its own configuration, and must not run business schedulers (full
 * search sync, orphan cleanup, job purge, notification heartbeat, webhook dispatch). This post-processor switches them off
 * with the highest precedence whenever the seed mode is set, whatever else was configured.
 */
public class UatSeedModeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String SOURCE_NAME = "uatSeedModeOverrides";
    static final Map<String, Object> OVERRIDES = Map.of(
            "app.jobs.enabled", "false",
            "app.jobs.metrics.enabled", "false",
            "app.scheduling.enabled", "false",
            "app.search.sync-on-startup", "false",
            // S2: no search index bootstrap/backfill from a seeder JVM
            "app.search.bootstrap-on-startup", "false");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String mode = environment.getProperty("app.uat-seed.mode");
        if (mode == null || mode.isBlank()) return;
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, OVERRIDES));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // after the config data has been loaded, so app.uat-seed.mode from any source counts
    }
}
