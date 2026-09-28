package com.company.bds.search.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Validated search filter (contract §7), shared by search, map, seller and saved-search code. Built by
 * {@link SearchFilterParser}; immutable. Set-valued fields are sorted so the canonical form is stable.
 *
 * @param keyword normalised keyword ({@link VietnameseNormalizer}), {@code null} without {@code q}
 */
public record SearchFilter(String purpose, SortedSet<String> types, Long priceMin, Long priceMax, BigDecimal areaMin,
                           BigDecimal areaMax, Integer bedsMin, SortedSet<String> legal, SortedSet<String> furnishing,
                           String verified, SortedSet<String> districts, UUID project, String keyword, BoundingBox bbox,
                           SearchSort sort) {

    public SearchFilter {
        types = SearchFilterParser.sorted(types);
        legal = SearchFilterParser.sorted(legal);
        furnishing = SearchFilterParser.sorted(furnishing);
        districts = SearchFilterParser.sorted(districts);
    }

    public boolean hasKeyword() { return keyword != null; }

    public boolean hasBbox() { return bbox != null; }

    /**
     * Canonical parameters: every set filter as its canonical string (CSV values sorted, decimals without trailing
     * zeros, bbox rounded to 5 decimals), plus the resolved {@code purpose} and {@code sort}. No cursor/size/view/place.
     */
    public TreeMap<String, String> canonicalParams() {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("purpose", purpose);
        params.put("sort", sort.name());
        putSet(params, "type", types);
        if (priceMin != null) params.put("priceMin", Long.toString(priceMin));
        if (priceMax != null) params.put("priceMax", Long.toString(priceMax));
        if (areaMin != null) params.put("areaMin", SearchFilterParser.plain(areaMin));
        if (areaMax != null) params.put("areaMax", SearchFilterParser.plain(areaMax));
        if (bedsMin != null) params.put("bedsMin", Integer.toString(bedsMin));
        putSet(params, "legal", legal);
        putSet(params, "furnishing", furnishing);
        if (verified != null) params.put("verified", verified);
        putSet(params, "district", districts);
        if (project != null) params.put("project", project.toString());
        if (keyword != null) params.put("q", keyword);
        if (bbox != null) params.put("bbox", bbox.canonical());
        return params;
    }

    /** First 32 hex characters of SHA-256 over the canonical JSON (sorted keys, string values). */
    public String filterHash() {
        StringBuilder json = new StringBuilder("{");
        canonicalParams().forEach((key, value) -> {
            if (json.length() > 1) json.append(',');
            json.append(jsonString(key)).append(':').append(jsonString(value));
        });
        json.append('}');
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** The same filter with another sort (the database engine answers RELEVANCE with NEWEST). */
    public SearchFilter withSort(SearchSort value) {
        return new SearchFilter(purpose, types, priceMin, priceMax, areaMin, areaMax, bedsMin, legal, furnishing, verified,
                districts, project, keyword, bbox, value);
    }

    /** The same filter restricted to {@code value} (map endpoint, where bbox is mandatory). */
    public SearchFilter withBbox(BoundingBox value) {
        return new SearchFilter(purpose, types, priceMin, priceMax, areaMin, areaMax, bedsMin, legal, furnishing, verified,
                districts, project, keyword, value, sort);
    }

    /** The filter without the given parameters (zero-result suggestions). */
    public SearchFilter without(Set<String> params) {
        boolean dropKeyword = params.contains("q");
        SearchSort nextSort = dropKeyword && sort == SearchSort.RELEVANCE ? SearchSort.NEWEST : sort;
        return new SearchFilter(purpose,
                params.contains("type") ? null : types,
                params.contains("priceMin") ? null : priceMin,
                params.contains("priceMax") ? null : priceMax,
                params.contains("areaMin") ? null : areaMin,
                params.contains("areaMax") ? null : areaMax,
                params.contains("bedsMin") ? null : bedsMin,
                params.contains("legal") ? null : legal,
                params.contains("furnishing") ? null : furnishing,
                params.contains("verified") ? null : verified,
                params.contains("district") ? null : districts,
                params.contains("project") ? null : project,
                dropKeyword ? null : keyword,
                params.contains("bbox") ? null : bbox,
                nextSort);
    }

    /**
     * Whether a read-model row satisfies this filter at {@code now}. Rows returned by Elasticsearch are re-checked with
     * it against PostgreSQL, so a stale index document never shows a listing that no longer matches.
     */
    public boolean matches(PublicListing row, Instant now) {
        if (!purpose.equals(row.purpose())) return false;
        if (!types.isEmpty() && !types.contains(row.propertyType())) return false;
        if (priceMin != null && row.priceVnd() < priceMin) return false;
        if (priceMax != null && row.priceVnd() > priceMax) return false;
        if (areaMin != null && row.areaM2().compareTo(areaMin) < 0) return false;
        if (areaMax != null && row.areaM2().compareTo(areaMax) > 0) return false;
        if (bedsMin != null && (row.bedrooms() == null || row.bedrooms() < bedsMin)) return false;
        if (!legal.isEmpty() && !legal.contains(row.legalStatusCode())) return false;
        if (!furnishing.isEmpty() && !furnishing.contains(row.furnishing())) return false;
        if ("IDENTITY".equals(verified) && !"VERIFIED".equals(row.identityStatusAt(now))) return false;
        if ("OWNERSHIP".equals(verified) && !"VERIFIED".equals(row.ownershipStatusAt(now))) return false;
        if (!districts.isEmpty() && !districts.contains(row.districtCode())) return false;
        if (project != null && !project.equals(row.projectId())) return false;
        if (bbox != null && !bbox.contains(row.lat(), row.lng())) return false;
        if (keyword != null) {
            if (row.searchText() == null) return false;
            Set<String> tokens = new HashSet<>(Arrays.asList(row.searchText().split(" ")));
            for (String token : keyword.split(" ")) {
                if (!tokens.contains(token)) return false;
            }
        }
        return true;
    }

    private static void putSet(Map<String, String> params, String key, SortedSet<String> values) {
        if (!values.isEmpty()) params.put(key, String.join(",", values));
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
