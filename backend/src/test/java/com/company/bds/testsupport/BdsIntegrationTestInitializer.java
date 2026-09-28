package com.company.bds.testsupport;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Points every {@link BdsIntegrationTest} context at the per-JVM PostgreSQL database and, when a test opts in with
 * {@code bds.test.elasticsearch=true}, at the shared test Elasticsearch with an index named
 * {@code <BDS_TEST_ES_PREFIX|s0be>-listings-<random>} that is deleted when the JVM exits.
 */
public class BdsIntegrationTestInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static final String ELASTICSEARCH_OPT_IN = "bds.test.elasticsearch";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        BdsTestDatabase.Database database = BdsTestDatabase.shared();
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", database.url());
        properties.put("spring.datasource.username", database.username());
        properties.put("spring.datasource.password", database.password());
        properties.put("spring.data.redis.database", BdsTestRedis.database());
        if (Boolean.parseBoolean(environment.getProperty(ELASTICSEARCH_OPT_IN, "false"))) {
            String baseUrl = BdsTestEnvironment.require("BDS_TEST_ES_URL").replaceAll("/+$", "");
            String prefix = BdsTestEnvironment.optional("BDS_TEST_ES_PREFIX", "s0be").toLowerCase(Locale.ROOT);
            String index = prefix + "-listings-" + UUID.randomUUID().toString().substring(0, 8);
            properties.put("app.search.elasticsearch.enabled", "true");
            properties.put("spring.elasticsearch.uris", baseUrl);
            properties.put("app.search.index-name", index);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteIndex(baseUrl, index), "bds-test-es-cleanup"));
        }
        environment.getPropertySources().addFirst(new MapPropertySource("bdsIntegrationTest", properties));
    }

    /**
     * Since S2 the configured name is an alias over versioned indices ({@code <name>-v2-<timestamp>}); deleting an alias
     * by name is refused, so every concrete index starting with the name is listed and deleted by its own name.
     */
    private static void deleteIndex(String baseUrl, String index) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> listed = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/_cat/indices/" + index + "*?h=index"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            names.add(index);
            if (listed.statusCode() == 200) {
                for (String line : listed.body().split("\n")) {
                    if (!line.isBlank() && line.trim().startsWith(index)) names.add(line.trim());
                }
            }
            for (String name : names) {
                client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/" + name))
                        .timeout(Duration.ofSeconds(5)).DELETE().build(), HttpResponse.BodyHandlers.discarding());
            }
        } catch (Exception ignored) {
            // The shared test Elasticsearch is disposable; a leftover prefixed index does not affect other streams.
        }
    }
}
