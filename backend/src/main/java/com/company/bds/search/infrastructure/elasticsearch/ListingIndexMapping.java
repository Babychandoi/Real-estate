package com.company.bds.search.infrastructure.elasticsearch;

import com.company.bds.search.domain.PublicListing;
import com.company.bds.shared.security.ContactInfoGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * Explicit index mapping (version {@value #VERSION}, audit D-08) and the document built from a read-model row. The index
 * holds only what filtering, sorting and matching need; responses are always built from PostgreSQL rows.
 * {@code vi_fold} = standard tokenizer + lowercase + asciifolding, which folds Vietnamese diacritics and {@code đ} the
 * same way {@code bds_search_normalize} does. {@code published_at} is {@code date_nanos} so the sort keeps the
 * microsecond precision of PostgreSQL (identical order on both engines). {@code gc_deletes} is raised so a delete
 * tombstone outlives any delayed index call with an older external version.
 */
public final class ListingIndexMapping {
    public static final int VERSION = 2;

    public static final String BODY = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": %d,
                "index.gc_deletes": "10m",
                "analysis": {
                  "analyzer": {
                    "vi_fold": { "type": "custom", "tokenizer": "standard", "filter": ["lowercase", "asciifolding"] }
                  }
                }
              },
              "mappings": {
                "_meta": { "bds_listing_version": 2 },
                "dynamic": "strict",
                "properties": {
                  "listing_id":          { "type": "keyword" },
                  "owner_id":            { "type": "keyword" },
                  "purpose":             { "type": "keyword" },
                  "property_type":       { "type": "keyword" },
                  "price_vnd":           { "type": "long" },
                  "area_m2":             { "type": "double" },
                  "bedrooms":            { "type": "integer" },
                  "legal_status_code":   { "type": "keyword" },
                  "furnishing":          { "type": "keyword" },
                  "district_code":       { "type": "keyword" },
                  "project_id":          { "type": "keyword" },
                  "identity_status":     { "type": "keyword" },
                  "identity_expires_at": { "type": "date" },
                  "ownership_status":    { "type": "keyword" },
                  "ownership_expires_at":{ "type": "date" },
                  "location":            { "type": "geo_point" },
                  "published_at":        { "type": "date_nanos" },
                  "expires_at":          { "type": "date" },
                  "title":               { "type": "text", "analyzer": "vi_fold" },
                  "location_text":       { "type": "text", "analyzer": "vi_fold" },
                  "search_text":         { "type": "text", "analyzer": "vi_fold" },
                  "row_version":         { "type": "long" }
                }
              }
            }
            """;

    private ListingIndexMapping() {}

    public static String body(int replicas) {
        return BODY.formatted(replicas);
    }

    public static ObjectNode document(ObjectMapper json, PublicListing row) {
        ObjectNode doc = json.createObjectNode();
        doc.put("listing_id", row.listingId().toString());
        doc.put("owner_id", row.ownerId().toString());
        doc.put("purpose", row.purpose());
        doc.put("property_type", row.propertyType());
        doc.put("price_vnd", row.priceVnd());
        doc.put("area_m2", row.areaM2().doubleValue());
        if (row.bedrooms() != null) doc.put("bedrooms", row.bedrooms());
        if (row.legalStatusCode() != null) doc.put("legal_status_code", row.legalStatusCode());
        if (row.furnishing() != null) doc.put("furnishing", row.furnishing());
        if (row.districtCode() != null) doc.put("district_code", row.districtCode());
        if (row.projectId() != null) doc.put("project_id", row.projectId().toString());
        doc.put("identity_status", row.identityStatus());
        putInstant(doc, "identity_expires_at", row.identityExpiresAt());
        doc.put("ownership_status", row.ownershipStatus());
        putInstant(doc, "ownership_expires_at", row.ownershipExpiresAt());
        if (row.lat() != null && row.lng() != null) doc.putObject("location").put("lat", row.lat()).put("lon", row.lng());
        doc.put("published_at", row.publishedAt().toString());
        // No-expiry fixtures use the same explicit infinity sentinel as indexing. Real expiry comes from one batched
        // listings lookup in ListingIndexWriter, without adding a field to public DTOs or a large read-model backfill.
        doc.put("expires_at", "9999-12-31T23:59:59Z");
        // indexed free text is redacted like the display (search_text already is, in SQL: V036 bds_redact_contact)
        doc.put("title", nonNull(ContactInfoGuard.redact(row.title(), " ")));
        doc.put("location_text", String.join(" ", nonNull(ContactInfoGuard.redact(row.addressSummary(), " ")), nonNull(row.districtName()),
                nonNull(row.projectName())).trim());
        doc.put("search_text", row.searchText() == null ? "" : row.searchText());
        doc.put("row_version", row.rowVersion());
        return doc;
    }

    private static void putInstant(ObjectNode doc, String field, Instant value) {
        if (value != null) doc.put(field, value.toString());
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
