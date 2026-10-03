package com.company.bds.search.infrastructure.indexing;

import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.infrastructure.elasticsearch.ElasticsearchIndexClient;
import com.company.bds.search.infrastructure.elasticsearch.ElasticsearchIndexClient.BulkItemResult;
import com.company.bds.search.infrastructure.elasticsearch.ElasticsearchIndexClient.BulkOp;
import com.company.bds.search.infrastructure.elasticsearch.ListingIndexMapping;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes read-model rows into one or more concrete indices with one {@code _bulk} call, external version =
 * {@code row_version}. Returns the listings whose write failed on any target (the caller retries them).
 */
@Component
public class ListingIndexWriter {
    private final ElasticsearchIndexClient client;
    private final ObjectMapper json;
    private final Counter indexed;
    private final Counter deleted;
    private final Counter failures;
    private final JdbcTemplate jdbc;

    public ListingIndexWriter(ElasticsearchIndexClient client, ObjectMapper json, ObjectProvider<MeterRegistry> meters, JdbcTemplate jdbc) {
        this.client = client;
        this.json = json;
        this.jdbc = jdbc;
        MeterRegistry registry = meters.getIfAvailable(SimpleMeterRegistry::new);
        this.indexed = Counter.builder("bds.search.index.documents").tag("op", "index").register(registry);
        this.deleted = Counter.builder("bds.search.index.documents").tag("op", "delete").register(registry);
        this.failures = Counter.builder("bds.search.index.bulk.failures")
                .description("Bulk items that failed (not counting version conflicts)").register(registry);
    }

    /**
     * @param rows    current rows to index (visible listings)
     * @param deletes listing id → version for listings that are not public (delete the document)
     * @return failed listing id → reason
     */
    public Map<UUID, String> write(Collection<String> indices, Collection<PublicListing> rows, Map<UUID, Long> deletes) {
        Map<UUID, Instant> expiries = new HashMap<>();
        if (!rows.isEmpty()) {
            jdbc.query("SELECT id, expires_at FROM listings WHERE id = ANY(?::uuid[])", rs -> {
                Timestamp expiry = rs.getTimestamp(2);
                expiries.put(rs.getObject(1, UUID.class), expiry == null ? null : expiry.toInstant());
            }, (Object) rows.stream().map(row -> row.listingId().toString()).toArray(String[]::new));
        }
        Map<String, Integer> schemas = new HashMap<>();
        if (!indices.isEmpty()) {
            jdbc.query("SELECT index_name, mapping_version FROM search_index_state WHERE index_name = ANY(?::text[])",
                    rs -> { schemas.put(rs.getString(1), rs.getInt(2)); }, (Object) indices.toArray(String[]::new));
        }
        List<BulkOp> ops = new ArrayList<>();
        for (String index : indices) {
            for (PublicListing row : rows) {
                var doc = ListingIndexMapping.document(json, row);
                if (schemas.getOrDefault(index, ListingIndexMapping.VERSION) >= 2) {
                    Instant expiry = expiries.get(row.listingId());
                    // A listing deleted between reading the model and indexing is immediately excluded, too.
                    if (!expiries.containsKey(row.listingId())) doc.put("expires_at", Instant.EPOCH.toString());
                    else if (expiry != null) doc.put("expires_at", expiry.toString());
                } else {
                    doc.remove("expires_at"); // dual-write the previous strict mapping during a schema rebuild
                }
                ops.add(BulkOp.index(index, row.listingId().toString(), row.rowVersion(), doc));
            }
            deletes.forEach((id, version) -> ops.add(BulkOp.delete(index, id.toString(), version)));
        }
        Map<UUID, String> failed = new HashMap<>();
        for (BulkItemResult result : client.bulk(ops)) {
            if (result.succeeded()) {
                if (result.op().document() == null) deleted.increment();
                else indexed.increment();
            } else {
                failures.increment();
                failed.put(UUID.fromString(result.op().id()), "index " + result.op().index() + ": " + result.status()
                        + (result.error() == null ? "" : " " + result.error()));
            }
        }
        return failed;
    }
}
