package com.company.bds.engagement.infrastructure;

import com.company.bds.engagement.application.port.ShortlistStorePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcShortlistStoreAdapter implements ShortlistStorePort {
    private final JdbcTemplate jdbc;

    public JdbcShortlistStoreAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(UUID id, UUID ownerId, String name, Instant now) {
        jdbc.update("INSERT INTO shortlists (id, owner_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, ownerId, name, Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public int countOwned(UUID ownerId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM shortlists WHERE owner_id = ?", Integer.class, ownerId);
        return n == null ? 0 : n;
    }

    @Override
    public Optional<Access> access(UUID shortlistId, UUID userId) {
        return jdbc.query("""
                SELECT s.id, s.owner_id, s.name,
                       CASE WHEN s.owner_id = ? THEN 'OWNER' ELSE m.role END,
                       coalesce(m.muted, FALSE), s.version, s.share_token_hash IS NOT NULL, s.share_role
                  FROM shortlists s
                  LEFT JOIN shortlist_members m ON m.shortlist_id = s.id AND m.user_id = ?
                 WHERE s.id = ? AND (s.owner_id = ? OR m.user_id IS NOT NULL)""",
                (rs, n) -> new Access(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getBoolean(5), rs.getLong(6), rs.getBoolean(7), rs.getString(8)),
                userId, userId, shortlistId, userId).stream().findFirst();
    }

    @Override
    public List<Summary> listFor(UUID userId, int limit) {
        return jdbc.query("""
                SELECT s.id, s.name, CASE WHEN s.owner_id = ? THEN 'OWNER' ELSE m.role END,
                       (SELECT count(*) FROM shortlist_items i WHERE i.shortlist_id = s.id),
                       (SELECT count(*) FROM shortlist_members mm WHERE mm.shortlist_id = s.id),
                       s.share_token_hash IS NOT NULL, coalesce(m.muted, FALSE), s.version, s.updated_at
                  FROM shortlists s
                  LEFT JOIN shortlist_members m ON m.shortlist_id = s.id AND m.user_id = ?
                 WHERE s.owner_id = ? OR m.user_id IS NOT NULL
                 ORDER BY s.updated_at DESC, s.id DESC LIMIT ?""",
                (rs, n) -> new Summary(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getInt(5), rs.getBoolean(6), rs.getBoolean(7), rs.getLong(8), rs.getTimestamp(9).toInstant()),
                userId, userId, userId, limit);
    }

    @Override
    public boolean rename(UUID shortlistId, String name, long expectedVersion, Instant now) {
        return jdbc.update("UPDATE shortlists SET name = ?, version = version + 1, updated_at = ? WHERE id = ? AND version = ?",
                name, Timestamp.from(now), shortlistId, expectedVersion) > 0;
    }

    @Override
    public void delete(UUID shortlistId) {
        jdbc.update("DELETE FROM shortlists WHERE id = ?", shortlistId);
    }

    @Override
    public void setShare(UUID shortlistId, @Nullable String tokenHash, @Nullable String role, Instant now) {
        jdbc.update("""
                UPDATE shortlists SET share_token_hash = ?, share_role = ?, shared_at = ?,
                       version = version + 1, updated_at = ? WHERE id = ?""",
                tokenHash, role, tokenHash != null ? Timestamp.from(now) : null, Timestamp.from(now), shortlistId);
    }

    @Override
    public Optional<Shared> findByTokenHash(String tokenHash) {
        return jdbc.query("""
                SELECT s.id, s.owner_id, u.full_name, s.name, s.share_role FROM shortlists s
                  JOIN users u ON u.id = s.owner_id AND u.status = 'ACTIVE'
                 WHERE s.share_token_hash = ?""",
                (rs, n) -> new Shared(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getString(5)), tokenHash).stream().findFirst();
    }

    @Override
    public void upsertMember(UUID shortlistId, UUID userId, String role, Instant now) {
        jdbc.update("""
                INSERT INTO shortlist_members (shortlist_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (shortlist_id, user_id) DO UPDATE
                   SET role = CASE WHEN shortlist_members.role = 'EDITOR' OR EXCLUDED.role = 'EDITOR' THEN 'EDITOR' ELSE 'VIEWER' END""",
                shortlistId, userId, role, Timestamp.from(now));
    }

    @Override
    public boolean setMemberRole(UUID shortlistId, UUID userId, String role) {
        return jdbc.update("UPDATE shortlist_members SET role = ? WHERE shortlist_id = ? AND user_id = ?", role, shortlistId, userId) > 0;
    }

    @Override
    public boolean removeMember(UUID shortlistId, UUID userId) {
        return jdbc.update("DELETE FROM shortlist_members WHERE shortlist_id = ? AND user_id = ?", shortlistId, userId) > 0;
    }

    @Override
    public boolean setMuted(UUID shortlistId, UUID userId, boolean muted) {
        return jdbc.update("UPDATE shortlist_members SET muted = ? WHERE shortlist_id = ? AND user_id = ?", muted, shortlistId, userId) > 0;
    }

    @Override
    public List<Member> members(UUID shortlistId) {
        return jdbc.query("""
                SELECT m.user_id, u.full_name, m.role, m.muted, m.joined_at FROM shortlist_members m
                  JOIN users u ON u.id = m.user_id WHERE m.shortlist_id = ? ORDER BY m.joined_at, m.user_id LIMIT 200""",
                (rs, n) -> new Member(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                        rs.getTimestamp(5).toInstant()), shortlistId);
    }

    @Override
    public boolean addItem(UUID shortlistId, UUID listingId, UUID addedBy, Instant now) {
        return jdbc.update("""
                INSERT INTO shortlist_items (shortlist_id, listing_id, added_by, added_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (shortlist_id, listing_id) DO NOTHING""", shortlistId, listingId, addedBy, Timestamp.from(now)) > 0;
    }

    @Override
    public boolean removeItem(UUID shortlistId, UUID listingId) {
        return jdbc.update("DELETE FROM shortlist_items WHERE shortlist_id = ? AND listing_id = ?", shortlistId, listingId) > 0;
    }

    @Override
    public int countItems(UUID shortlistId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM shortlist_items WHERE shortlist_id = ?", Integer.class, shortlistId);
        return n == null ? 0 : n;
    }

    @Override
    public List<Item> items(UUID shortlistId, int limit) {
        return jdbc.query("""
                SELECT listing_id, added_by, added_at FROM shortlist_items WHERE shortlist_id = ?
                ORDER BY added_at DESC, listing_id DESC LIMIT ?""",
                (rs, n) -> new Item(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant()),
                shortlistId, limit);
    }

    @Override
    public void touch(UUID shortlistId, Instant now) {
        jdbc.update("UPDATE shortlists SET updated_at = ? WHERE id = ?", Timestamp.from(now), shortlistId);
    }

    @Override
    public List<UUID> audience(UUID shortlistId, UUID exceptUserId) {
        return jdbc.queryForList("""
                SELECT s.owner_id FROM shortlists s WHERE s.id = ? AND s.owner_id <> ?
                UNION
                SELECT m.user_id FROM shortlist_members m WHERE m.shortlist_id = ? AND m.user_id <> ? AND NOT m.muted""",
                UUID.class, shortlistId, exceptUserId, shortlistId, exceptUserId);
    }
}
