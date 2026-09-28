package com.company.bds.engagement.infrastructure;

import com.company.bds.engagement.application.port.SavedListingStorePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSavedListingStoreAdapter implements SavedListingStorePort {
    static final String PUBLIC_LISTING = """
            EXISTS (SELECT 1 FROM listing_public_read p JOIN users ou ON ou.id = p.owner_id AND ou.status = 'ACTIVE'
                     WHERE p.listing_id = %s)""";

    private final JdbcTemplate jdbc;

    public JdbcSavedListingStoreAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockUser(UUID userId) {
        jdbc.query("SELECT id FROM users WHERE id = ? FOR UPDATE", rs -> { }, userId);
    }

    @Override
    public Optional<Instant> add(UUID userId, UUID listingId, Instant now) {
        return jdbc.query("""
                INSERT INTO saved_listings (user_id, listing_id, created_at) VALUES (?, ?, ?)
                ON CONFLICT (user_id, listing_id) DO NOTHING RETURNING created_at""",
                (rs, n) -> rs.getTimestamp(1).toInstant(), userId, listingId, Timestamp.from(now)).stream().findFirst();
    }

    @Override
    public Optional<Instant> savedAt(UUID userId, UUID listingId) {
        return jdbc.query("SELECT created_at FROM saved_listings WHERE user_id = ? AND listing_id = ?",
                (rs, n) -> rs.getTimestamp(1).toInstant(), userId, listingId).stream().findFirst();
    }

    @Override
    public boolean remove(UUID userId, UUID listingId) {
        return jdbc.update("DELETE FROM saved_listings WHERE user_id = ? AND listing_id = ?", userId, listingId) > 0;
    }

    @Override
    public int count(UUID userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM saved_listings WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    @Override
    public List<SavedRow> page(UUID userId, @Nullable Instant beforeSavedAt, @Nullable UUID beforeListingId, int limit) {
        if (beforeSavedAt == null || beforeListingId == null) {
            return jdbc.query("""
                    SELECT listing_id, created_at FROM saved_listings WHERE user_id = ?
                    ORDER BY created_at DESC, listing_id DESC LIMIT ?""",
                    (rs, n) -> new SavedRow(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()), userId, limit);
        }
        return jdbc.query("""
                SELECT listing_id, created_at FROM saved_listings
                 WHERE user_id = ? AND (created_at, listing_id) < (?, ?)
                ORDER BY created_at DESC, listing_id DESC LIMIT ?""",
                (rs, n) -> new SavedRow(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()),
                userId, Timestamp.from(beforeSavedAt), beforeListingId, limit);
    }

    @Override
    public List<UUID> ids(UUID userId, int limit) {
        return jdbc.queryForList("SELECT listing_id FROM saved_listings WHERE user_id = ? ORDER BY created_at DESC, listing_id DESC LIMIT ?",
                UUID.class, userId, limit);
    }

    @Override
    public List<UUID> savers(UUID listingId, @Nullable UUID afterUserId, int limit) {
        return jdbc.queryForList("""
                SELECT s.user_id FROM saved_listings s JOIN users u ON u.id = s.user_id AND u.status = 'ACTIVE'
                 WHERE s.listing_id = ? AND s.user_id > ? ORDER BY s.user_id LIMIT ?""",
                UUID.class, listingId, afterUserId == null ? new UUID(0L, 0L) : afterUserId, limit);
    }

    @Override
    public boolean isPublic(UUID listingId) {
        Boolean value = jdbc.queryForObject("SELECT " + PUBLIC_LISTING.formatted("?"), Boolean.class, listingId);
        return Boolean.TRUE.equals(value);
    }

    @Override
    public Map<UUID, GoneListing> gone(Collection<UUID> listingIds) {
        Map<UUID, GoneListing> out = new HashMap<>();
        if (listingIds.isEmpty()) return out;
        jdbc.query("""
                SELECT l.id, l.slug, CASE WHEN l.status <> 'LOCKED' AND u.status = 'ACTIVE' THEN r.title END
                  FROM listings l
                  LEFT JOIN listing_revisions r ON r.id = l.public_revision_id AND r.status = 'APPROVED'
                  LEFT JOIN users u ON u.id = l.owner_id
                 WHERE l.id = ANY(?)""", rs -> {
            UUID id = rs.getObject(1, UUID.class);
            out.put(id, new GoneListing(id, rs.getString(2), rs.getString(3)));
        }, SqlArrays.uuids(listingIds));
        return out;
    }
}
