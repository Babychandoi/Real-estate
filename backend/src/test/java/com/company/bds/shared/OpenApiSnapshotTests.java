package com.company.bds.shared;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OpenAPI document is a versioned contract (audit F22.4): the committed snapshot
 * {@code src/test/resources/openapi/openapi.json} must equal what springdoc renders for {@code /v3/api-docs}.
 * The frontend generates its API types from the same file ({@code npm run gen:api}), so a backend change that alters
 * the contract fails here until the snapshot — and with it the generated types — are regenerated and reviewed:
 *
 * <pre>sh mvnw -Dtest=OpenApiSnapshotTests -Dopenapi.snapshot.update=true test &amp;&amp; (cd ../frontend &amp;&amp; npm run gen:api)</pre>
 */
// Media storage on (as in production), so the media endpoints are part of the contract. Same properties as
// MediaPipelineIntegrationTests, so both share one cached application context.
@BdsIntegrationTest(properties = {
        "app.media.storage-enabled=true",
        "app.media.endpoint=${BDS_TEST_MINIO_URL:http://127.0.0.1:59000}",
        "app.media.access-key=${BDS_TEST_MINIO_USER:bds-test-media}",
        "app.media.secret-key=${BDS_TEST_MINIO_PASSWORD:bds-test-media-only}",
        "app.media.bucket=s1-media-it",
        "app.media.signing-secret=s1-media-test-signing-secret-0123456789"})
class OpenApiSnapshotTests {
    static final Path SNAPSHOT = Path.of("src/test/resources/openapi/openapi.json");
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Autowired OpenApiWebMvcResource openApi;

    @Test
    void servedDocumentMatchesTheCommittedSnapshot() throws Exception {
        // /v3/api-docs is not reachable over HTTP (SecurityConfig denies unlisted paths); render it through springdoc directly
        byte[] served = openApi.openapiJson(new MockHttpServletRequest("GET", "/v3/api-docs"), "/v3/api-docs", Locale.ROOT);
        String actual = canonical(JSON.readTree(served));

        if (Boolean.getBoolean("openapi.snapshot.update")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, actual, StandardCharsets.UTF_8);
        }
        String expected = Files.readString(SNAPSHOT, StandardCharsets.UTF_8);
        if (!expected.equals(actual)) {
            throw new AssertionError("The API contract changed (" + summary(JSON.readTree(expected), JSON.readTree(actual))
                    + "). If intended, regenerate: sh mvnw -Dtest=OpenApiSnapshotTests -Dopenapi.snapshot.update=true test"
                    + " && (cd ../frontend && npm run gen:api), then review the diff.");
        }
    }

    @Test
    void everyOperationIsDocumentedAndErrorsUseProblemDetails() throws Exception {
        JsonNode document = JSON.readTree(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
        assertThat(document.path("paths").size()).isGreaterThan(100);
        assertThat(document.path("components").path("schemas").has("ProblemDetails")).isTrue();
        // operation ids are unique, so generated clients get stable names
        List<String> ids = new ArrayList<>();
        document.path("paths").forEach(path -> path.forEach(operation -> {
            if (operation.has("operationId")) ids.add(operation.path("operationId").asText());
        }));
        assertThat(ids).doesNotHaveDuplicates();
    }

    /** Sorted keys, no server list (environment-specific), trailing newline: byte-for-byte stable across runs. */
    static String canonical(JsonNode document) throws Exception {
        if (document instanceof ObjectNode object) object.remove("servers");
        Object sorted = JSON.treeToValue(document, Object.class);
        return JSON.writeValueAsString(sortDeep(sorted)).replace("\r\n", "\n") + "\n";
    }

    @SuppressWarnings("unchecked")
    private static Object sortDeep(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            ((Map<String, Object>) map).forEach((key, child) -> sorted.put(key, sortDeep(child)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(child -> copy.add(sortDeep(child)));
            return copy;
        }
        return value;
    }

    private static String summary(JsonNode expected, JsonNode actual) {
        TreeSet<String> before = operations(expected);
        TreeSet<String> after = operations(actual);
        TreeSet<String> added = new TreeSet<>(after);
        added.removeAll(before);
        TreeSet<String> removed = new TreeSet<>(before);
        removed.removeAll(after);
        TreeSet<String> changedSchemas = new TreeSet<>();
        JsonNode oldSchemas = expected.path("components").path("schemas");
        JsonNode newSchemas = actual.path("components").path("schemas");
        for (Iterator<String> names = newSchemas.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!newSchemas.path(name).equals(oldSchemas.path(name))) changedSchemas.add(name);
        }
        for (Iterator<String> names = oldSchemas.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!newSchemas.has(name)) changedSchemas.add(name + " (removed)");
        }
        TreeSet<String> changedOperations = new TreeSet<>();
        for (String operation : after) {
            if (!before.contains(operation)) continue;
            String[] parts = operation.split(" ", 2);
            JsonNode was = expected.path("paths").path(parts[1]).path(parts[0].toLowerCase());
            JsonNode now = actual.path("paths").path(parts[1]).path(parts[0].toLowerCase());
            if (!was.equals(now)) changedOperations.add(operation);
        }
        return "added operations " + added + ", removed operations " + removed + ", changed operations "
                + changedOperations + ", changed schemas " + changedSchemas;
    }

    private static TreeSet<String> operations(JsonNode document) {
        TreeSet<String> operations = new TreeSet<>();
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fieldNames()
                .forEachRemaining(method -> operations.add(method.toUpperCase() + " " + path.getKey())));
        return operations;
    }
}
