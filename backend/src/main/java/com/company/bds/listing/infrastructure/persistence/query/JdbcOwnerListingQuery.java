package com.company.bds.listing.infrastructure.persistence.query;

import com.company.bds.listing.application.port.out.OwnerListingQueryPort;
import com.company.bds.listing.domain.model.LegalStatusCode;
import com.company.bds.listing.domain.model.ListingPurpose;
import com.company.bds.listing.domain.model.ListingStatus;
import com.company.bds.listing.domain.model.PropertyType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Three statements per page whatever its size: counts per status, the page itself (LATERAL latest revision,
 * scalar sub-selects bounded by the page), nothing per row. Sort {@code created_at DESC, id DESC} is stable
 * (index {@code idx_listings_owner_created} / {@code idx_listings_owner_status_created}).
 */
@Component
@Transactional(readOnly = true)
public class JdbcOwnerListingQuery implements OwnerListingQueryPort {
    private final JdbcTemplate jdbc;

    public JdbcOwnerListingQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Page findPage(UUID ownerId, ListingStatus status, int page, int size) {
        Map<ListingStatus, Long> counts = new EnumMap<>(ListingStatus.class);
        for (ListingStatus s : ListingStatus.values()) counts.put(s, 0L);
        jdbc.query("SELECT status, COUNT(*) FROM listings WHERE owner_id = ? GROUP BY status",
                rs -> { counts.put(ListingStatus.valueOf(rs.getString(1)), rs.getLong(2)); }, ownerId);
        long total = status == null ? counts.values().stream().mapToLong(Long::longValue).sum() : counts.get(status);

        List<Object> args = new ArrayList<>();
        args.add(ownerId);
        String filter = "";
        if (status != null) {
            filter = " AND l.status = ?";
            args.add(status.name());
        }
        args.add(size);
        args.add((long) page * size);
        List<Row> rows = jdbc.query("""
                SELECT l.id, l.slug, l.status, l.source, l.version, l.created_at, l.updated_at, l.availability_confirmed_at,
                       l.expires_at, l.sold_check_due_at,
                       p.id AS p_id, p.revision_number AS p_no, p.status AS p_status, p.title AS p_title, p.purpose AS p_purpose,
                       p.property_type AS p_type, p.price_vnd AS p_price, p.area_m2 AS p_area, p.created_at AS p_created,
                       p.submitted_at AS p_submitted, p.moderated_at AS p_moderated, p.moderation_note AS p_note,
                       w.id AS w_id, w.revision_number AS w_no, w.status AS w_status, w.title AS w_title, w.purpose AS w_purpose,
                       w.property_type AS w_type, w.price_vnd AS w_price, w.area_m2 AS w_area, w.created_at AS w_created,
                       w.submitted_at AS w_submitted, w.moderated_at AS w_moderated, w.moderation_note AS w_note,
                       w.description AS w_description, w.district_code AS w_district, w.public_latitude AS w_lat,
                       w.public_longitude AS w_lng, w.legal_status_code AS w_legal, w.deposit_vnd AS w_deposit,
                       w.monthly_service_fee_vnd AS w_fee,
                       (SELECT COUNT(*) FROM listing_media m WHERE m.revision_id = w.id) AS image_count,
                       (SELECT m.media_url FROM listing_media m WHERE m.revision_id = COALESCE(p.id, w.id)
                         ORDER BY m.is_primary DESC, m.sort_order, m.id LIMIT 1) AS thumbnail,
                       (SELECT COUNT(*) FROM leads le WHERE le.listing_id = l.id) AS lead_count
                FROM listings l
                LEFT JOIN listing_revisions p ON p.id = l.public_revision_id
                JOIN LATERAL (SELECT * FROM listing_revisions r WHERE r.listing_id = l.id
                              ORDER BY r.revision_number DESC LIMIT 1) w ON TRUE
                WHERE l.owner_id = ?""" + filter + """

                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, (rs, n) -> row(rs), args.toArray());
        return new Page(rows, total, counts);
    }

    private static Row row(ResultSet rs) throws SQLException {
        RevisionSummary published = rs.getObject("p_id") == null ? null : revision(rs, "p_");
        RevisionSummary latest = revision(rs, "w_");
        WorkingFacts facts = new WorkingFacts(rs.getInt("image_count"), rs.getString("w_description"), rs.getString("w_district"),
                (Double) rs.getObject("w_lat"), (Double) rs.getObject("w_lng"),
                rs.getString("w_legal") == null ? null : LegalStatusCode.valueOf(rs.getString("w_legal")),
                (Long) rs.getObject("w_deposit"), (Long) rs.getObject("w_fee"));
        return new Row(rs.getObject("id", UUID.class), rs.getString("slug"), ListingStatus.valueOf(rs.getString("status")),
                rs.getString("source"), rs.getLong("version"), instant(rs, "created_at"), instant(rs, "updated_at"),
                instant(rs, "availability_confirmed_at"), instant(rs, "expires_at"), instant(rs, "sold_check_due_at"),
                rs.getString("thumbnail"), rs.getLong("lead_count"), published, latest, facts);
    }

    private static RevisionSummary revision(ResultSet rs, String p) throws SQLException {
        return new RevisionSummary(rs.getObject(p + "id", UUID.class), rs.getInt(p + "no"), rs.getString(p + "status"),
                rs.getString(p + "title"), ListingPurpose.valueOf(rs.getString(p + "purpose")),
                PropertyType.valueOf(rs.getString(p + "type")), rs.getLong(p + "price"), rs.getBigDecimal(p + "area"),
                instant(rs, p + "created"), instant(rs, p + "submitted"), instant(rs, p + "moderated"), rs.getString(p + "note"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
