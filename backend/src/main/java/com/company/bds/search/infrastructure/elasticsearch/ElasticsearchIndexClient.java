package com.company.bds.search.infrastructure.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/** Index administration and bulk writes on Elasticsearch (aliases, versioned indices, external versioning). */
@Component
public class ElasticsearchIndexClient {
    private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration BULK_TIMEOUT = Duration.ofSeconds(20);

    /** One bulk action: index {@code document} (or delete when null) with external version {@code version}. */
    public record BulkOp(String index, String id, long version, ObjectNode document) {
        public static BulkOp index(String index, String id, long version, ObjectNode document) {
            return new BulkOp(index, id, version, document);
        }

        public static BulkOp delete(String index, String id, long version) {
            return new BulkOp(index, id, version, null);
        }
    }

    /**
     * Result of one bulk item. A version conflict means a newer (or equal) version is already indexed: the index is
     * already at least as fresh as this call, so it counts as success. A 404 on delete means nothing to delete.
     */
    public record BulkItemResult(BulkOp op, int status, String error) {
        public boolean succeeded() {
            return (status >= 200 && status < 300) || status == 409 || (op.document() == null && status == 404);
        }
    }

    private final ElasticsearchHttp http;
    private final ObjectMapper json;
    private final int replicas;

    public ElasticsearchIndexClient(ElasticsearchHttp http, ObjectMapper json,
                                    @Value("${app.search.index-replicas:0}") int replicas) {
        this.http = http;
        this.json = json;
        this.replicas = replicas;
    }

    public void createIndex(String name) {
        ElasticsearchHttp.Response response = http.send("PUT", "/" + encode(name), ListingIndexMapping.body(replicas), ADMIN_TIMEOUT);
        if (!response.ok()) throw new IllegalStateException("Cannot create index " + name + ": " + response.status() + " " + abbreviate(response.body()));
    }

    public void deleteIndex(String name) {
        ElasticsearchHttp.Response response = http.send("DELETE", "/" + encode(name), null, ADMIN_TIMEOUT);
        if (!response.ok() && response.status() != 404) {
            throw new IllegalStateException("Cannot delete index " + name + ": " + response.status());
        }
    }

    /** Concrete indices behind {@code alias}; empty when no such alias exists. */
    public List<String> aliasTargets(String alias) {
        ElasticsearchHttp.Response response = http.send("GET", "/_alias/" + encode(alias), null, ADMIN_TIMEOUT);
        if (response.status() == 404) return List.of();
        if (!response.ok()) throw new IllegalStateException("Cannot read alias " + alias + ": " + response.status());
        List<String> names = new ArrayList<>();
        readTree(response.body()).fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** Mapping version from index metadata; pre-expiry mappings had no metadata and are version 1. */
    public int mappingVersion(String index) {
        ElasticsearchHttp.Response response = http.send("GET", "/" + encode(index) + "/_mapping", null, ADMIN_TIMEOUT);
        if (!response.ok()) throw new IllegalStateException("Cannot inspect mapping " + index + ": " + response.status());
        return readTree(response.body()).path(index).path("mappings").path("_meta").path("bds_listing_version").asInt(1);
    }

    /** Whether a concrete index (not an alias) with this exact name exists. */
    public boolean concreteIndexExists(String name) {
        ElasticsearchHttp.Response response = http.send("GET", "/" + encode(name) + "/_settings", null, ADMIN_TIMEOUT);
        if (response.status() == 404) return false;
        if (!response.ok()) throw new IllegalStateException("Cannot inspect index " + name + ": " + response.status());
        JsonNode root = readTree(response.body());
        return root.has(name);
    }

    /**
     * Atomically points {@code alias} at {@code target}: removes it from {@code from} indices and, when
     * {@code legacyIndexToRemove} is set, deletes that concrete index in the same request (migration of the old concrete
     * {@code bds-listings} index into an alias of the same name).
     */
    public void swapAlias(String alias, List<String> from, String target, String legacyIndexToRemove) {
        ObjectNode body = json.createObjectNode();
        ArrayNode actions = body.putArray("actions");
        for (String index : from) {
            if (!index.equals(target)) actions.addObject().putObject("remove").put("index", index).put("alias", alias);
        }
        if (legacyIndexToRemove != null) actions.addObject().putObject("remove_index").put("index", legacyIndexToRemove);
        actions.addObject().putObject("add").put("index", target).put("alias", alias);
        ElasticsearchHttp.Response response = http.send("POST", "/_aliases", body.toString(), ADMIN_TIMEOUT);
        if (!response.ok()) throw new IllegalStateException("Alias swap failed: " + response.status() + " " + abbreviate(response.body()));
    }

    public void refresh(String index) {
        http.send("POST", "/" + encode(index) + "/_refresh", null, ADMIN_TIMEOUT);
    }

    public long count(String index) {
        ElasticsearchHttp.Response response = http.send("GET", "/" + encode(index) + "/_count", null, ADMIN_TIMEOUT);
        if (!response.ok()) return -1;
        return readTree(response.body()).path("count").asLong(-1);
    }

    public Optional<JsonNode> getDocument(String index, String id) {
        ElasticsearchHttp.Response response = http.send("GET", "/" + encode(index) + "/_doc/" + encode(id), null, ADMIN_TIMEOUT);
        if (response.status() == 404) return Optional.empty();
        if (!response.ok()) throw new IllegalStateException("Cannot read document: " + response.status());
        return Optional.of(readTree(response.body()));
    }

    /** One {@code _bulk} call; every item gets its own result (never all-or-nothing). */
    public List<BulkItemResult> bulk(List<BulkOp> ops) {
        if (ops.isEmpty()) return List.of();
        StringBuilder body = new StringBuilder();
        for (BulkOp op : ops) {
            ObjectNode meta = json.createObjectNode();
            ObjectNode action = meta.putObject(op.document() == null ? "delete" : "index");
            action.put("_index", op.index()).put("_id", op.id()).put("version", op.version()).put("version_type", "external");
            body.append(meta).append('\n');
            if (op.document() != null) body.append(op.document()).append('\n');
        }
        ElasticsearchHttp.Response response = http.send("POST", "/_bulk", body.toString(), "application/x-ndjson", BULK_TIMEOUT);
        List<BulkItemResult> results = new ArrayList<>(ops.size());
        if (!response.ok()) {
            for (BulkOp op : ops) results.add(new BulkItemResult(op, response.status(), "bulk request failed: " + response.status()));
            return results;
        }
        Iterator<JsonNode> items = readTree(response.body()).path("items").elements();
        for (BulkOp op : ops) {
            JsonNode item = items.hasNext() ? items.next() : null;
            JsonNode result = item == null ? null : item.elements().next();
            int status = result == null ? 500 : result.path("status").asInt(500);
            String error = result == null ? "missing bulk item" : result.path("error").path("type").asText(null);
            results.add(new BulkItemResult(op, status, error));
        }
        return results;
    }

    private JsonNode readTree(String body) {
        try {
            return json.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Unreadable Elasticsearch response", ex);
        }
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String abbreviate(String body) {
        return body == null ? "" : body.substring(0, Math.min(300, body.length()));
    }
}
