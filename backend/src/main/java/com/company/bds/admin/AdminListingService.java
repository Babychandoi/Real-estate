package com.company.bds.admin;

import com.company.bds.listing.application.port.out.ListingPersistencePort;
import com.company.bds.listing.domain.model.Listing;
import com.company.bds.listing.domain.model.ListingStatus;
import com.company.bds.shared.error.ApiException;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Admin listing management: paged search with filters and a stable order, revision history, reasoned status actions
 * recorded in {@code listing_status_history}, and a private preview of any revision (never cached, never public).
 */
@Service
public class AdminListingService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "PENDING_REVIEW", "ACTIVE", "PAUSED", "EXPIRED", "REJECTED", "LOCKED");
    private static final Set<String> SOURCES = Set.of("DIRECT", "IMPORT", "SEED");
    private static final int MAX_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final ListingPersistencePort listings;
    private final EntityManager entityManager;
    private final Clock clock;

    public AdminListingService(JdbcTemplate jdbc, ListingPersistencePort listings, EntityManager entityManager, Clock clock) {
        this.jdbc = jdbc;
        this.listings = listings;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListingPage search(Filters filters, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_SIZE, Math.max(1, size));
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (filters.status() != null && !filters.status().isBlank()) {
            String status = filters.status().trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) throw ApiException.badRequest("INVALID_FILTER", "Trạng thái tin không hợp lệ.");
            where.append(" AND l.status = ?");
            args.add(status);
        }
        if (filters.source() != null && !filters.source().isBlank()) {
            String source = filters.source().trim().toUpperCase(Locale.ROOT);
            if (!SOURCES.contains(source)) throw ApiException.badRequest("INVALID_FILTER", "Nguồn tin không hợp lệ.");
            where.append(" AND l.source = ?");
            args.add(source);
        }
        if (filters.ownerId() != null) {
            where.append(" AND l.owner_id = ?");
            args.add(filters.ownerId());
        }
        if (filters.district() != null && !filters.district().isBlank()) {
            where.append(" AND r.district_code = ?");
            args.add(filters.district().trim());
        }
        if (Boolean.TRUE.equals(filters.pendingEdit())) {
            where.append(" AND l.public_revision_id IS NOT NULL AND EXISTS (SELECT 1 FROM listing_revisions p WHERE p.listing_id = l.id AND p.status = 'SUBMITTED')");
        }
        if (filters.keyword() != null && !filters.keyword().isBlank()) {
            String keyword = filters.keyword().trim();
            if (keyword.length() > 100) throw ApiException.badRequest("INVALID_FILTER", "Từ khóa tối đa 100 ký tự.");
            where.append(" AND (r.title ILIKE ? OR r.address_summary ILIKE ? OR l.slug ILIKE ? OR u.full_name ILIKE ? OR l.id::text = ?)");
            String like = "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.addAll(List.of(like, like, like, like, keyword.toLowerCase(Locale.ROOT)));
        }
        // The current revision: public one when there is one, otherwise the newest.
        String from = """
                FROM listings l
                JOIN users u ON u.id = l.owner_id
                JOIN LATERAL (SELECT * FROM listing_revisions x WHERE x.listing_id = l.id
                              ORDER BY (x.id = l.public_revision_id) DESC, x.revision_number DESC LIMIT 1) r ON TRUE
                """;
        Long total = jdbc.queryForObject("SELECT count(*) " + from + where, Long.class, args.toArray());
        List<Object> dataArgs = new ArrayList<>(args);
        dataArgs.add(safeSize);
        dataArgs.add(safePage * safeSize);
        List<ListingRow> items = jdbc.query("""
                SELECT l.id, l.slug, l.status, l.source, l.owner_id, u.full_name AS owner_name, r.title, r.purpose, r.property_type,
                       r.price_vnd, r.area_m2, r.district_code, r.address_summary, l.public_revision_id, l.property_asset_id,
                       (SELECT max(x.revision_number) FROM listing_revisions x WHERE x.listing_id = l.id) AS latest_revision,
                       EXISTS (SELECT 1 FROM listing_revisions p WHERE p.listing_id = l.id AND p.status = 'SUBMITTED') AS has_submitted,
                       l.created_at, l.updated_at, l.expires_at
                """ + from + where + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?", this::row, dataArgs.toArray());
        return new ListingPage(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public List<RevisionRow> revisions(UUID listingId) {
        requireListing(listingId);
        return jdbc.query("""
                SELECT r.id, r.revision_number, r.status, r.title, r.price_vnd, r.area_m2, r.created_at, r.submitted_at, r.moderated_at,
                       r.moderation_note, r.id = l.public_revision_id AS is_public,
                       (SELECT count(*) FROM listing_media m WHERE m.revision_id = r.id) AS media_count
                FROM listing_revisions r JOIN listings l ON l.id = r.listing_id
                WHERE r.listing_id = ? ORDER BY r.revision_number DESC LIMIT 100
                """, (rs, n) -> new RevisionRow(rs.getObject("id", UUID.class), rs.getInt("revision_number"), rs.getString("status"),
                rs.getString("title"), rs.getLong("price_vnd"), rs.getBigDecimal("area_m2"), instant(rs, "created_at"),
                instant(rs, "submitted_at"), instant(rs, "moderated_at"), rs.getString("moderation_note"), rs.getBoolean("is_public"),
                rs.getInt("media_count")), listingId);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryRow> history(UUID listingId) {
        requireListing(listingId);
        return jdbc.query("""
                SELECT h.id, h.from_status, h.to_status, h.action, h.reason, h.actor_id, u.full_name, h.created_at
                FROM listing_status_history h LEFT JOIN users u ON u.id = h.actor_id
                WHERE h.listing_id = ? ORDER BY h.created_at DESC, h.id DESC LIMIT 100
                """, (rs, n) -> new StatusHistoryRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getObject(6, UUID.class), rs.getString(7), rs.getTimestamp(8).toInstant()), listingId);
    }

    /** LOCK, UNLOCK, HIDE or UNHIDE with a mandatory reason; every change is recorded with the actor. */
    @Transactional
    public StatusChange changeStatus(UUID listingId, String action, String reason, UUID actorId) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < 5) throw ApiException.badRequest("REASON_REQUIRED", "Cần nhập lý do (ít nhất 5 ký tự).");
        if (trimmed.length() > 1000) throw ApiException.badRequest("REASON_TOO_LONG", "Lý do tối đa 1000 ký tự.");
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Không tìm thấy tin đăng."));
        ListingStatus from = listing.getStatus();
        Instant now = clock.instant();
        switch (normalized) {
            case "LOCK" -> {
                if (from == ListingStatus.LOCKED) throw ApiException.conflict("INVALID_TRANSITION", "Tin đã bị khóa.");
                listing.lock(now);
            }
            case "UNLOCK" -> {
                if (from != ListingStatus.LOCKED) throw ApiException.conflict("INVALID_TRANSITION", "Chỉ tin đang khóa mới mở khóa được.");
                listing.resume(now);
            }
            case "HIDE" -> {
                if (from != ListingStatus.ACTIVE) throw ApiException.conflict("INVALID_TRANSITION", "Chỉ tin đang hiển thị mới ẩn được.");
                listing.pause(now);
            }
            case "UNHIDE" -> {
                if (from != ListingStatus.PAUSED || listing.getPublicRevisionId() == null) {
                    throw ApiException.conflict("INVALID_TRANSITION", "Chỉ tin đang tạm ẩn và đã có bản công khai mới hiển thị lại được.");
                }
                listing.resume(now);
            }
            default -> throw ApiException.badRequest("INVALID_ACTION", "Thao tác không hợp lệ.");
        }
        Listing saved = listings.save(listing);
        entityManager.flush();
        recordStatusChange(listingId, from.name(), saved.getStatus().name(), normalized, trimmed, actorId, now);
        return new StatusChange(listingId, from.name(), saved.getStatus().name(), normalized);
    }

    /** Also used by the report desk (emergency hide, lock/resume from a case). */
    public void recordStatusChange(UUID listingId, String from, String to, String action, String reason, UUID actorId, Instant at) {
        jdbc.update("""
                INSERT INTO listing_status_history(id, listing_id, from_status, to_status, action, reason, actor_id, created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), listingId, from, to, action, reason, actorId, Timestamp.from(at));
    }

    /** Full content of one revision (default: newest) incl. private/draft media; the controller sends it no-store. */
    @Transactional(readOnly = true)
    public Preview preview(UUID listingId, UUID revisionId) {
        List<Preview> rows = jdbc.query("""
                SELECT l.id, l.slug, l.status, r.id AS revision_id, r.revision_number, r.status AS revision_status, r.title,
                       r.description, r.purpose, r.property_type, r.price_vnd, r.area_m2, r.bedrooms, r.bathrooms, r.floors,
                       r.legal_status, r.district_code, r.address_summary, r.submitted_at, r.moderated_at, r.moderation_note,
                       r.id = l.public_revision_id AS is_public
                FROM listings l JOIN listing_revisions r ON r.listing_id = l.id
                WHERE l.id = ? AND (CAST(? AS uuid) IS NULL OR r.id = ?)
                ORDER BY r.revision_number DESC LIMIT 1
                """, (rs, n) -> new Preview(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("status"),
                rs.getObject("revision_id", UUID.class), rs.getInt("revision_number"), rs.getString("revision_status"),
                rs.getString("title"), rs.getString("description"), rs.getString("purpose"), rs.getString("property_type"),
                rs.getLong("price_vnd"), rs.getBigDecimal("area_m2"), (Integer) rs.getObject("bedrooms"),
                (Integer) rs.getObject("bathrooms"), (Integer) rs.getObject("floors"), rs.getString("legal_status"),
                rs.getString("district_code"), rs.getString("address_summary"), instant(rs, "submitted_at"),
                instant(rs, "moderated_at"), rs.getString("moderation_note"), rs.getBoolean("is_public"), List.of()),
                listingId, revisionId, revisionId);
        if (rows.isEmpty()) throw ApiException.notFound("LISTING_NOT_FOUND", "Không tìm thấy tin đăng hoặc phiên bản.");
        Preview p = rows.get(0);
        List<String> media = jdbc.queryForList("SELECT media_url FROM listing_media WHERE revision_id = ? ORDER BY sort_order, id LIMIT 50",
                String.class, p.revisionId());
        return new Preview(p.listingId(), p.slug(), p.listingStatus(), p.revisionId(), p.revisionNumber(), p.revisionStatus(), p.title(),
                p.description(), p.purpose(), p.propertyType(), p.priceVnd(), p.areaM2(), p.bedrooms(), p.bathrooms(), p.floors(),
                p.legalStatus(), p.districtCode(), p.addressSummary(), p.submittedAt(), p.moderatedAt(), p.moderationNote(),
                p.isPublic(), media);
    }

    private void requireListing(UUID listingId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM listings WHERE id = ?", Integer.class, listingId);
        if (count == null || count == 0) throw ApiException.notFound("LISTING_NOT_FOUND", "Không tìm thấy tin đăng.");
    }

    private ListingRow row(ResultSet rs, int n) throws SQLException {
        return new ListingRow(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("status"), rs.getString("source"),
                rs.getObject("owner_id", UUID.class), rs.getString("owner_name"), rs.getString("title"), rs.getString("purpose"),
                rs.getString("property_type"), rs.getLong("price_vnd"), rs.getBigDecimal("area_m2"), rs.getString("district_code"),
                rs.getString("address_summary"), rs.getObject("public_revision_id") != null, rs.getInt("latest_revision"),
                rs.getBoolean("has_submitted") && rs.getObject("public_revision_id") != null,
                rs.getObject("property_asset_id", UUID.class), instant(rs, "created_at"), instant(rs, "updated_at"),
                instant(rs, "expires_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    public record Filters(String status, UUID ownerId, String keyword, String district, String source, Boolean pendingEdit) {}

    public record ListingRow(UUID id, String slug, String status, String source, UUID ownerId, String ownerName, String title,
                             String purpose, String propertyType, long priceVnd, BigDecimal areaM2, String districtCode,
                             String addressSummary, boolean hasPublicRevision, int latestRevisionNumber, boolean hasPendingEdit,
                             UUID propertyAssetId, Instant createdAt, Instant updatedAt, Instant expiresAt) {}

    public record ListingPage(List<ListingRow> items, int page, int size, long total) {}

    public record RevisionRow(UUID id, int revisionNumber, String status, String title, long priceVnd, BigDecimal areaM2,
                              Instant createdAt, Instant submittedAt, Instant moderatedAt, String moderationNote, boolean isPublic,
                              int mediaCount) {}

    public record StatusHistoryRow(UUID id, String fromStatus, String toStatus, String action, String reason, UUID actorId,
                                   String actorName, Instant createdAt) {}

    public record StatusChange(UUID listingId, String fromStatus, String toStatus, String action) {}

    public record Preview(UUID listingId, String slug, String listingStatus, UUID revisionId, int revisionNumber,
                          String revisionStatus, String title, String description, String purpose, String propertyType,
                          long priceVnd, BigDecimal areaM2, Integer bedrooms, Integer bathrooms, Integer floors, String legalStatus,
                          String districtCode, String addressSummary, Instant submittedAt, Instant moderatedAt,
                          String moderationNote, boolean isPublic, List<String> mediaUrls) {}
}
