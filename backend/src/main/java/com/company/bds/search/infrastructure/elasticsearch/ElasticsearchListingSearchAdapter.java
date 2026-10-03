package com.company.bds.search.infrastructure.elasticsearch;

import com.company.bds.search.application.SearchIndexSettings;
import com.company.bds.search.application.port.ListingSearchEnginePort;
import com.company.bds.search.domain.SearchFilter;
import com.company.bds.search.domain.SearchSort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Elasticsearch implementation of the search port: bool filter mirroring the SQL predicates of
 * {@code JdbcListingReadModelAdapter.where}, {@code search_after} on the contract sort tuples, one time budget per call.
 */
@Component
public class ElasticsearchListingSearchAdapter implements ListingSearchEnginePort {
    private final ElasticsearchHttp http;
    private final ObjectMapper json;
    private final SearchIndexSettings settings;
    private final Duration budget;

    public ElasticsearchListingSearchAdapter(ElasticsearchHttp http, ObjectMapper json, SearchIndexSettings settings,
                                             @Value("${app.search.timeout:PT0.8S}") Duration budget) {
        this.http = http;
        this.json = json;
        this.settings = settings;
        this.budget = budget;
    }

    @Override
    public boolean ready() { return settings.ready(); }

    @Override
    public Hits search(SearchFilter filter, SearchSort sort, ArrayNode after, int size, boolean trackTotal, int totalCap) {
        ObjectNode body = query(filter, sort);
        body.put("size", size);
        body.put("_source", false);
        body.put("timeout", Math.max(100, budget.toMillis() - 100) + "ms");
        if (trackTotal) body.put("track_total_hits", totalCap);
        else body.put("track_total_hits", false);
        if (after != null) body.set("search_after", after);
        JsonNode root = post(body);
        List<Hit> hits = new ArrayList<>();
        for (JsonNode hit : root.path("hits").path("hits")) {
            hits.add(new Hit(UUID.fromString(hit.path("_id").asText()), (ArrayNode) hit.path("sort")));
        }
        Total total = null;
        JsonNode totalNode = root.path("hits").path("total");
        if (trackTotal && totalNode.isObject()) {
            total = new Total(totalNode.path("value").asLong(), "eq".equals(totalNode.path("relation").asText()) ? "eq" : "gte");
        }
        return new Hits(hits, total);
    }

    @Override
    public MapClusters mapClusters(SearchFilter filter, int precision, int limit, int totalCap) {
        ObjectNode body = query(filter, SearchSort.NEWEST);
        body.remove("sort");
        body.put("size", 0);
        body.put("timeout", Math.max(100, budget.toMillis() - 100) + "ms");
        body.put("track_total_hits", totalCap);
        ObjectNode cells = body.putObject("aggs").putObject("cells");
        cells.putObject("geotile_grid").put("field", "location").put("precision", precision).put("size", limit);
        ObjectNode sub = cells.putObject("aggs");
        sub.putObject("centroid").putObject("geo_centroid").put("field", "location");
        sub.putObject("bounds").putObject("geo_bounds").put("field", "location");
        JsonNode root = post(body);
        List<Cluster> clusters = new ArrayList<>();
        for (JsonNode bucket : root.path("aggregations").path("cells").path("buckets")) {
            JsonNode centroid = bucket.path("centroid").path("location");
            JsonNode bounds = bucket.path("bounds").path("bounds");
            clusters.add(new Cluster(centroid.path("lat").asDouble(), centroid.path("lon").asDouble(),
                    bucket.path("doc_count").asLong(),
                    bounds.path("top_left").path("lon").asDouble(), bounds.path("bottom_right").path("lat").asDouble(),
                    bounds.path("bottom_right").path("lon").asDouble(), bounds.path("top_left").path("lat").asDouble()));
        }
        JsonNode totalNode = root.path("hits").path("total");
        Total total = new Total(totalNode.path("value").asLong(), "eq".equals(totalNode.path("relation").asText()) ? "eq" : "gte");
        return new MapClusters(clusters, total);
    }

    private JsonNode post(ObjectNode body) {
        ElasticsearchHttp.Response response;
        try {
            response = http.send("POST", "/" + ElasticsearchIndexClient.encode(settings.alias()) + "/_search",
                    body.toString(), budget);
        } catch (ElasticsearchHttp.EngineUnavailableException ex) {
            throw new EngineUnavailable(ex.getMessage(), ex);
        }
        if (response.status() >= 500 || response.status() == 429) {
            throw new EngineUnavailable("Elasticsearch answered " + response.status(), null);
        }
        if (!response.ok()) throw new EngineRejected("Elasticsearch rejected the query: " + response.status());
        JsonNode root;
        try {
            root = json.readTree(response.body());
        } catch (Exception ex) {
            throw new EngineUnavailable("Unreadable Elasticsearch response", ex);
        }
        if (root.path("timed_out").asBoolean(false)) throw new EngineUnavailable("Elasticsearch search timed out", null);
        return root;
    }

    ObjectNode query(SearchFilter f, SearchSort sort) {
        ObjectNode body = json.createObjectNode();
        ObjectNode bool = body.putObject("query").putObject("bool");
        ArrayNode filters = bool.putArray("filter");
        term(filters, "purpose", f.purpose());
        // Version 2 documents always carry a deadline (infinity for NULL). Filter at query time: aggregate map
        // buckets cannot hydrate/recheck SQL rows and must not wait for the lifecycle sweep to drop expired docs.
        filters.addObject().putObject("range").putObject("expires_at").put("gt", "now");
        terms(filters, "property_type", f.types());
        range(filters, "price_vnd", f.priceMin(), f.priceMax());
        if (f.areaMin() != null || f.areaMax() != null) {
            ObjectNode r = filters.addObject().putObject("range").putObject("area_m2");
            if (f.areaMin() != null) r.put("gte", f.areaMin().doubleValue());
            if (f.areaMax() != null) r.put("lte", f.areaMax().doubleValue());
        }
        if (f.bedsMin() != null) filters.addObject().putObject("range").putObject("bedrooms").put("gte", f.bedsMin());
        terms(filters, "legal_status_code", f.legal());
        terms(filters, "furnishing", f.furnishing());
        if ("IDENTITY".equals(f.verified())) verified(filters, "identity_status", "identity_expires_at");
        if ("OWNERSHIP".equals(f.verified())) verified(filters, "ownership_status", "ownership_expires_at");
        terms(filters, "district_code", f.districts());
        if (f.project() != null) term(filters, "project_id", f.project().toString());
        if (f.bbox() != null) {
            ObjectNode box = filters.addObject().putObject("geo_bounding_box").putObject("location");
            box.putObject("top_left").put("lat", f.bbox().maxLat()).put("lon", f.bbox().minLng());
            box.putObject("bottom_right").put("lat", f.bbox().minLat()).put("lon", f.bbox().maxLng());
        }
        if (f.keyword() != null) {
            bool.putArray("must").addObject().putObject("match").putObject("search_text")
                    .put("query", f.keyword()).put("operator", "and");
            ArrayNode should = bool.putArray("should");
            should.addObject().putObject("match").putObject("title").put("query", f.keyword()).put("boost", 3);
            should.addObject().putObject("match").putObject("location_text").put("query", f.keyword()).put("boost", 2);
            should.addObject().putObject("match_phrase").putObject("search_text").put("query", f.keyword()).put("boost", 2);
        }
        ArrayNode sorts = body.putArray("sort");
        switch (sort) {
            case PRICE_ASC -> { sortBy(sorts, "price_vnd", "asc"); sortBy(sorts, "listing_id", "asc"); }
            case PRICE_DESC -> { sortBy(sorts, "price_vnd", "desc"); sortBy(sorts, "listing_id", "desc"); }
            case AREA_DESC -> { sortBy(sorts, "area_m2", "desc"); sortBy(sorts, "listing_id", "desc"); }
            case RELEVANCE -> {
                sortBy(sorts, "_score", "desc");
                sortBy(sorts, "published_at", "desc");
                sortBy(sorts, "listing_id", "desc");
            }
            default -> { sortBy(sorts, "published_at", "desc"); sortBy(sorts, "listing_id", "desc"); }
        }
        return body;
    }

    private static void term(ArrayNode filters, String field, String value) {
        filters.addObject().putObject("term").put(field, value);
    }

    private static void terms(ArrayNode filters, String field, Collection<String> values) {
        if (values.isEmpty()) return;
        ArrayNode array = filters.addObject().putObject("terms").putArray(field);
        values.forEach(array::add);
    }

    private static void range(ArrayNode filters, String field, Long min, Long max) {
        if (min == null && max == null) return;
        ObjectNode r = filters.addObject().putObject("range").putObject(field);
        if (min != null) r.put("gte", min);
        if (max != null) r.put("lte", max);
    }

    private static void verified(ArrayNode filters, String statusField, String expiryField) {
        term(filters, statusField, "VERIFIED");
        ObjectNode validity = filters.addObject().putObject("bool");
        validity.put("minimum_should_match", 1);
        ArrayNode should = validity.putArray("should");
        should.addObject().putObject("bool").putArray("must_not").addObject().putObject("exists").put("field", expiryField);
        should.addObject().putObject("range").putObject(expiryField).put("gt", "now");
    }

    private static void sortBy(ArrayNode sorts, String field, String order) {
        sorts.addObject().putObject(field).put("order", order);
    }
}
