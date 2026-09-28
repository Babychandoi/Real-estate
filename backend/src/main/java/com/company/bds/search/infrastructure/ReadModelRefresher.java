package com.company.bds.search.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Calls {@code bds_refresh_listing_public_read} and the fan-out helpers of V033/V034. */
@Component
public class ReadModelRefresher {
    public record Refreshed(UUID listingId, boolean visible, long rowVersion) {}

    private final JdbcTemplate jdbc;

    public ReadModelRefresher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Refreshes every listing in one statement (one transaction); each row is serialised per listing by the function. */
    public List<Refreshed> refresh(Collection<UUID> listingIds) {
        if (listingIds.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT ids.id, f.visible, f.row_version
                FROM unnest(CAST(? AS uuid[])) AS ids(id)
                CROSS JOIN LATERAL bds_refresh_listing_public_read(ids.id) f
                """, (rs, n) -> new Refreshed(rs.getObject(1, UUID.class), rs.getBoolean(2), rs.getLong(3)),
                JdbcListingReadModelAdapter.uuidArray(listingIds));
    }

    /** Enqueues one listing job per listing of the owner that is ACTIVE or still in the read model. */
    public int fanOutOwner(UUID ownerId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT bds_enqueue_listing_index(x.id) FROM (
                        SELECT id FROM listings WHERE owner_id = ? AND status = 'ACTIVE'
                        UNION SELECT listing_id FROM listing_public_read WHERE owner_id = ?) x) q
                """, Integer.class, ownerId, ownerId);
        return count == null ? 0 : count;
    }

    /** Enqueues one listing job per listing whose public revision belongs to the project. */
    public int fanOutProject(UUID projectId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT bds_enqueue_listing_index(l.id) FROM listings l
                    JOIN listing_revisions r ON r.id = l.public_revision_id
                    WHERE r.project_id = ?) q
                """, Integer.class, projectId);
        return count == null ? 0 : count;
    }

    /** Enqueues listings whose shown trust facts (identity/ownership validity) have expired since the last refresh. */
    public int enqueueExpiredTrust(int limit) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT bds_enqueue_listing_index(listing_id) FROM listing_public_read
                    WHERE trust_expires_at <= now() ORDER BY trust_expires_at LIMIT ?) q
                """, Integer.class, limit);
        return count == null ? 0 : count;
    }
}
