package com.company.bds.search.application.port;

import com.company.bds.search.domain.SearchFilter;
import com.company.bds.search.domain.SearchSort;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.UUID;

/** Full-text search engine (Elasticsearch). Returns ids and sort values only; rows always come from PostgreSQL. */
public interface ListingSearchEnginePort {

    /** Whether the engine is configured and its alias is ready to serve. */
    boolean ready();

    /**
     * @param trackTotal count matches up to {@code totalCap} (first page only)
     * @throws EngineUnavailable timeout, connection failure or 5xx (counts against the circuit breaker)
     * @throws EngineRejected the engine refused the query (4xx): a bug on our side, not an engine outage
     */
    Hits search(SearchFilter filter, SearchSort sort, @Nullable ArrayNode after, int size, boolean trackTotal, int totalCap);

    /**
     * Map clusters of the matches of {@code filter} (whose bbox is set): Web-Mercator tiles of zoom {@code precision},
     * at most {@code limit} cells, most populated first, each with its centroid and bounds; total counted up to
     * {@code totalCap}. Same failure contract as {@link #search}.
     */
    MapClusters mapClusters(SearchFilter filter, int precision, int limit, int totalCap);

    record Cluster(double lat, double lng, long count, double minLng, double minLat, double maxLng, double maxLat) {}

    record MapClusters(List<Cluster> clusters, Total total) {}

    record Hit(UUID id, ArrayNode sortValues) {}

    record Total(long value, String relation) {}

    record Hits(List<Hit> hits, @Nullable Total total) {}

    class EngineUnavailable extends RuntimeException {
        public EngineUnavailable(String message, Throwable cause) { super(message, cause); }
    }

    class EngineRejected extends RuntimeException {
        public EngineRejected(String message) { super(message); }
    }
}
