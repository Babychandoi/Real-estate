package com.company.bds.search.domain;

/**
 * Result orders of contract §8. Every order ends with {@code listing_id} so the tuple is unique and keyset/search_after
 * paging never skips or repeats a row. RELEVANCE is served by Elasticsearch only; the database engine answers it with
 * NEWEST and the notice {@code RELEVANCE_APPROXIMATE}.
 */
public enum SearchSort {
    NEWEST("published_at", true),
    PRICE_ASC("price_vnd", false),
    PRICE_DESC("price_vnd", true),
    AREA_DESC("area_m2", true),
    RELEVANCE("_score", true);

    private final String column;
    private final boolean descending;

    SearchSort(String column, boolean descending) {
        this.column = column;
        this.descending = descending;
    }

    /** Leading read-model column of the tuple (the second one is always {@code listing_id} in the same direction). */
    public String column() { return column; }

    public boolean descending() { return descending; }
}
