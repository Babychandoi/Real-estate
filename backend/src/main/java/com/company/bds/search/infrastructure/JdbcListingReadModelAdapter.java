package com.company.bds.search.infrastructure;

import com.company.bds.search.application.port.ListingReadModelPort;
import com.company.bds.search.domain.PublicListing;
import com.company.bds.search.domain.SearchFilter;
import com.company.bds.search.domain.SearchSort;
import com.company.bds.shared.security.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ListingReadModelPort} on PostgreSQL. Predicates are added only for the filters that are set (no
 * {@code OR :param IS NULL}), each sort has one fixed ORDER BY matching an index of V033, and paging is keyset only.
 */
@Repository
public class JdbcListingReadModelAdapter implements ListingReadModelPort {
    static final String SUMMARY_COLUMNS = """
            listing_id, slug, owner_id, public_revision_id, revision_number, title, NULL::text AS description,
            description_excerpt, purpose, property_type, price_vnd, price_period, unit_price_vnd, area_m2, bedrooms,
            bathrooms, floors, frontage_m, road_width_m, direction, legal_status_code, legal_status_text, furnishing,
            monthly_service_fee_vnd, deposit_vnd, province_code, district_code, district_name, ward_code, ward_name,
            address_summary, lat, lng, project_id, project_slug, project_name, thumbnail_url, image_count,
            NULL::text[] AS media_urls, seller_name, seller_avatar_url, seller_role, identity_status, identity_checked_at,
            identity_expires_at, ownership_status, ownership_checked_at, ownership_expires_at, ownership_document_type,
            listing_checked_at, published_at, updated_at, availability_confirmed_at, previous_price_vnd, price_changed_at,
            search_text, row_version""";
    static final String DETAIL_COLUMNS = SUMMARY_COLUMNS
            .replace("NULL::text AS description", "description")
            .replace("NULL::text[] AS media_urls", "media_urls");

    /**
     * The seller account must still be ACTIVE: a ban hides every listing of the seller on the database path at once,
     * without waiting for the owner fan-out job (which removes the rows and the index documents later).
     */
    static final String OWNER_ACTIVE =
            " AND EXISTS (SELECT 1 FROM users ou WHERE ou.id = listing_public_read.owner_id AND ou.status = 'ACTIVE')";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcListingReadModelAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<PublicListing> page(SearchFilter filter, SearchSort sort, ArrayNode after, int limit) {
        Sql sql = new Sql("SELECT " + SUMMARY_COLUMNS + " FROM listing_public_read WHERE ");
        where(sql, filter);
        if (after != null) keyset(sql, sort, after);
        sql.append(" ORDER BY ").append(orderBy(sort)).append(" LIMIT ?").param(limit);
        return jdbc.query(sql.text(), ROW, sql.params());
    }

    @Override
    public ArrayNode keysOf(PublicListing row, SearchSort sort) {
        ArrayNode keys = json.createArrayNode();
        switch (sort) {
            case PRICE_ASC, PRICE_DESC -> keys.add(row.priceVnd());
            case AREA_DESC -> keys.add(row.areaM2().toPlainString());
            default -> keys.add(epochMicros(row.publishedAt()));
        }
        return keys.add(row.listingId().toString());
    }

    @Override
    public long countCapped(SearchFilter filter, int cap) {
        Sql sql = new Sql("SELECT count(*) FROM (SELECT 1 FROM listing_public_read WHERE ");
        where(sql, filter);
        sql.append(" LIMIT ?) capped").param(cap + 1);
        Long count = jdbc.queryForObject(sql.text(), Long.class, sql.params());
        return count == null ? 0 : count;
    }

    @Override
    public List<PublicListing> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.query("SELECT " + SUMMARY_COLUMNS + " FROM listing_public_read WHERE listing_id = ANY(?)" + OWNER_ACTIVE,
                ROW, uuidArray(ids));
    }

    /** Keyset batch in listing id order (index backfill). */
    public List<PublicListing> batchAfter(UUID after, int limit) {
        if (after == null) {
            return jdbc.query("SELECT " + SUMMARY_COLUMNS + " FROM listing_public_read ORDER BY listing_id LIMIT ?", ROW, limit);
        }
        return jdbc.query("SELECT " + SUMMARY_COLUMNS + " FROM listing_public_read WHERE listing_id > ? ORDER BY listing_id LIMIT ?",
                ROW, after, limit);
    }

    @Override
    public Optional<VersionRef> findVersion(String slugOrId) {
        UUID id = parseUuid(slugOrId);
        String sql = """
                SELECT listing_id, row_version,
                       (CASE WHEN identity_status = 'VERIFIED' AND identity_expires_at <= now() THEN 'i' ELSE '' END)
                    || (CASE WHEN ownership_status = 'VERIFIED' AND ownership_expires_at <= now() THEN 'o' ELSE '' END)
                FROM listing_public_read WHERE """ + (id != null ? " listing_id = ?" : " slug = ?") + OWNER_ACTIVE;
        return jdbc.query(sql, (rs, n) -> new VersionRef(rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3)),
                id != null ? id : slugOrId).stream().findFirst();
    }

    @Override
    public Optional<PublicListing> findDetail(UUID listingId) {
        return jdbc.query("SELECT " + DETAIL_COLUMNS + " FROM listing_public_read WHERE listing_id = ?" + OWNER_ACTIVE, ROW, listingId)
                .stream().findFirst();
    }

    @Override
    public Optional<GoneListing> findGone(String slugOrId) {
        UUID id = parseUuid(slugOrId);
        // the last public title is shown only for listings their owner withdrew (paused, sold, expired...), never for a
        // listing locked by moderation or one whose seller account is no longer ACTIVE (banned, locked, deleted)
        String sql = """
                SELECT l.id, l.slug,
                       CASE WHEN l.status <> 'LOCKED' AND u.status = 'ACTIVE' THEN r.title END AS title
                FROM listings l
                JOIN listing_revisions r ON r.id = l.public_revision_id AND r.status = 'APPROVED'
                LEFT JOIN users u ON u.id = l.owner_id
                WHERE %s AND NOT EXISTS (SELECT 1 FROM listing_public_read p WHERE p.listing_id = l.id AND u.status = 'ACTIVE')
                """.formatted(id != null ? "l.id = ?" : "l.slug = ?");
        return jdbc.query(sql, (rs, n) -> new GoneListing(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                id != null ? id : slugOrId).stream().findFirst();
    }

    @Override
    public List<PublicListing> sellerPage(UUID ownerId, ArrayNode after, int limit) {
        Sql sql = new Sql("SELECT " + SUMMARY_COLUMNS + " FROM listing_public_read WHERE owner_id = ?" + OWNER_ACTIVE).param(ownerId);
        if (after != null) keyset(sql, SearchSort.NEWEST, after);
        sql.append(" ORDER BY published_at DESC, listing_id DESC LIMIT ?").param(limit);
        return jdbc.query(sql.text(), ROW, sql.params());
    }

    @Override
    public long sellerCountCapped(UUID ownerId, int cap) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM (SELECT 1 FROM listing_public_read WHERE owner_id = ?" + OWNER_ACTIVE + " LIMIT ?) capped",
                Long.class, ownerId, cap + 1);
        return count == null ? 0 : count;
    }

    @Override
    public List<MapPoint> mapPoints(SearchFilter filter, int limit) {
        // The inner LIMIT without ORDER BY keeps this a bounded probe on the GiST index (W6-PERF): the caller only shows
        // points when there are fewer than `limit` matches, so the newest-first order is applied to that small set and a
        // dense viewport never walks the newest index looking for matches inside the box.
        Sql sql = new Sql("SELECT listing_id, slug, lat, lng, price_vnd, price_period, property_type FROM (SELECT listing_id, "
                + "slug, lat, lng, price_vnd, price_period, property_type, published_at FROM listing_public_read WHERE ");
        where(sql, filter);
        sql.append(" AND public_location IS NOT NULL LIMIT ?) probe ORDER BY published_at DESC, listing_id DESC").param(limit);
        return jdbc.query(sql.text(), (rs, n) -> new MapPoint(rs.getObject(1, UUID.class), rs.getString(2), rs.getDouble(3),
                rs.getDouble(4), rs.getLong(5), rs.getString(6), rs.getString(7)), sql.params());
    }

    @Override
    public List<MapCluster> mapClusters(SearchFilter filter, double cellDegrees, int limit) {
        Sql sql = new Sql("""
                SELECT avg(lat), avg(lng), count(*), min(lng), min(lat), max(lng), max(lat)
                FROM listing_public_read WHERE """);
        where(sql, filter);
        sql.append(" AND public_location IS NOT NULL GROUP BY floor(lng / ?), floor(lat / ?) ORDER BY count(*) DESC LIMIT ?")
                .param(cellDegrees).param(cellDegrees).param(limit);
        return jdbc.query(sql.text(), (rs, n) -> new MapCluster(rs.getDouble(1), rs.getDouble(2), rs.getLong(3),
                rs.getDouble(4), rs.getDouble(5), rs.getDouble(6), rs.getDouble(7)), sql.params());
    }

    @Override
    public List<PublicListing> similar(PublicListing base, int limit) {
        long low = Math.round(base.priceVnd() * 0.7);
        long high = Math.round(base.priceVnd() * 1.3);
        return jdbc.query("SELECT " + SUMMARY_COLUMNS + """
                 FROM listing_public_read
                WHERE purpose = ? AND property_type = ? AND listing_id <> ? AND price_vnd BETWEEN ? AND ?""" + OWNER_ACTIVE + """

                ORDER BY (district_code IS NOT DISTINCT FROM ?) DESC, abs(price_vnd - ?) ASC, listing_id ASC
                LIMIT ?""", ROW, base.purpose(), base.propertyType(), base.listingId(), low, high,
                base.districtCode(), base.priceVnd(), limit);
    }

    @Override
    public List<PricePoint> priceHistory(UUID listingId) {
        return jdbc.query("""
                SELECT r.revision_number, r.price_vnd, r.price_period, r.moderated_at
                FROM listing_public_read p
                JOIN listing_revisions r ON r.listing_id = p.listing_id AND r.status = 'APPROVED'
                     AND r.revision_number <= p.revision_number AND r.purpose = p.purpose
                WHERE p.listing_id = ?
                ORDER BY r.revision_number
                LIMIT 200""", (rs, n) -> new PricePoint(rs.getInt(1), rs.getLong(2), rs.getString(3), instant(rs, 4)), listingId);
    }

    @Override
    public Optional<SellerProfile> sellerProfile(UUID sellerId, Instant now) {
        return jdbc.query("""
                SELECT u.id, u.full_name, u.avatar_media_url, %s AS role, u.created_at,
                       k.status AS kyc_status, k.verified_at, k.expires_at,
                       (SELECT count(*) FROM listing_public_read p WHERE p.owner_id = u.id) AS active_listings,
                       (SELECT count(*) FROM listing_public_read p WHERE p.owner_id = u.id AND p.ownership_status = 'VERIFIED'
                           AND (p.ownership_expires_at IS NULL OR p.ownership_expires_at > ?)) AS ownership_verified,
                       stats.samples, stats.median_minutes
                FROM users u
                LEFT JOIN user_kyc_profiles k ON k.user_id = u.id
                LEFT JOIN LATERAL (
                    SELECT count(*) AS samples,
                           percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (ld.first_response_at - ld.created_at)) / 60.0)
                               AS median_minutes
                    FROM leads ld JOIN listings l ON l.id = ld.listing_id
                    WHERE l.owner_id = u.id AND ld.first_response_at IS NOT NULL AND ld.first_response_at >= ld.created_at
                ) stats ON TRUE
                WHERE u.id = ? AND u.status = 'ACTIVE'
                """.formatted(Roles.effectiveRoleSql("u.id")), (rs, n) -> {
            String kyc = rs.getString("kyc_status");
            Instant expires = instant(rs, "expires_at");
            String identity = kyc == null ? "NOT_SUBMITTED"
                    : "VERIFIED".equals(kyc) ? (expires == null || expires.isAfter(now) ? "VERIFIED" : "EXPIRED")
                    : "REJECTED".equals(kyc) ? "REJECTED" : "PENDING";
            double median = rs.getDouble("median_minutes");
            Double medianMinutes = rs.wasNull() ? null : median;
            return new SellerProfile(rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("avatar_media_url"),
                    rs.getString("role"), instant(rs, "created_at"), identity,
                    "VERIFIED".equals(kyc) || "REJECTED".equals(kyc) ? instant(rs, "verified_at") : null,
                    "VERIFIED".equals(kyc) ? expires : null,
                    rs.getLong("active_listings"), rs.getLong("ownership_verified"), rs.getLong("samples"), medianMinutes);
        }, now == null ? null : Timestamp.from(now), sellerId).stream().findFirst();
    }

    // --- SQL building --------------------------------------------------------------------------------------------------

    static void where(Sql sql, SearchFilter f) {
        sql.append(" purpose = ?").param(f.purpose());
        sql.append(OWNER_ACTIVE);
        if (!f.types().isEmpty()) sql.append(" AND property_type = ANY(?)").param(textArray(f.types()));
        if (f.priceMin() != null) sql.append(" AND price_vnd >= ?").param(f.priceMin());
        if (f.priceMax() != null) sql.append(" AND price_vnd <= ?").param(f.priceMax());
        if (f.areaMin() != null) sql.append(" AND area_m2 >= ?").param(f.areaMin());
        if (f.areaMax() != null) sql.append(" AND area_m2 <= ?").param(f.areaMax());
        if (f.bedsMin() != null) sql.append(" AND bedrooms >= ?").param(f.bedsMin());
        if (!f.legal().isEmpty()) sql.append(" AND legal_status_code = ANY(?)").param(textArray(f.legal()));
        if (!f.furnishing().isEmpty()) sql.append(" AND furnishing = ANY(?)").param(textArray(f.furnishing()));
        if ("IDENTITY".equals(f.verified())) {
            sql.append(" AND identity_status = 'VERIFIED' AND (identity_expires_at IS NULL OR identity_expires_at > now())");
        }
        if ("OWNERSHIP".equals(f.verified())) {
            sql.append(" AND ownership_status = 'VERIFIED' AND (ownership_expires_at IS NULL OR ownership_expires_at > now())");
        }
        if (!f.districts().isEmpty()) sql.append(" AND district_code = ANY(?)").param(textArray(f.districts()));
        if (f.project() != null) sql.append(" AND project_id = ?").param(f.project());
        if (f.keyword() != null) sql.append(" AND search_tsv @@ plainto_tsquery('simple', ?)").param(f.keyword());
        if (f.bbox() != null) {
            sql.append(" AND public_location && ST_MakeEnvelope(?, ?, ?, ?, 4326)")
                    .param(f.bbox().minLng()).param(f.bbox().minLat()).param(f.bbox().maxLng()).param(f.bbox().maxLat());
        }
    }

    private static void keyset(Sql sql, SearchSort sort, ArrayNode after) {
        UUID id = UUID.fromString(after.get(1).asText());
        switch (sort) {
            case PRICE_ASC -> sql.append(" AND (price_vnd, listing_id) > (?, ?)").param(after.get(0).asLong()).param(id);
            case PRICE_DESC -> sql.append(" AND (price_vnd, listing_id) < (?, ?)").param(after.get(0).asLong()).param(id);
            case AREA_DESC -> sql.append(" AND (area_m2, listing_id) < (?, ?)").param(new BigDecimal(after.get(0).asText())).param(id);
            default -> sql.append(" AND (published_at, listing_id) < (?, ?)")
                    .param(Timestamp.from(fromEpochMicros(after.get(0).asLong()))).param(id);
        }
    }

    private static String orderBy(SearchSort sort) {
        return switch (sort) {
            case PRICE_ASC -> "price_vnd ASC, listing_id ASC";
            case PRICE_DESC -> "price_vnd DESC, listing_id DESC";
            case AREA_DESC -> "area_m2 DESC, listing_id DESC";
            default -> "published_at DESC, listing_id DESC";
        };
    }

    public static long epochMicros(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000L), instant.getNano() / 1_000);
    }

    public static Instant fromEpochMicros(long micros) {
        return Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L);
    }

    /** A bound SQL array built with {@link java.sql.Connection#createArrayOf} (no textual array literal). */
    static SqlTypeValue textArray(Collection<String> values) {
        return array("text", values.toArray());
    }

    public static SqlTypeValue uuidArray(Collection<UUID> values) {
        return array("uuid", values.toArray());
    }

    private static SqlTypeValue array(String elementType, Object[] elements) {
        return (ps, index, sqlType, typeName) -> ps.setArray(index, ps.getConnection().createArrayOf(elementType, elements));
    }

    private static UUID parseUuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            return id.toString().equalsIgnoreCase(value) ? id : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static Instant instant(ResultSet rs, int column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static final RowMapper<PublicListing> ROW = (rs, n) -> new PublicListing(
            rs.getObject("listing_id", UUID.class), rs.getString("slug"), rs.getObject("owner_id", UUID.class),
            rs.getObject("public_revision_id", UUID.class), rs.getInt("revision_number"),
            rs.getString("title"), rs.getString("description"), rs.getString("description_excerpt"),
            rs.getString("purpose"), rs.getString("property_type"), rs.getLong("price_vnd"), rs.getString("price_period"),
            (Long) rs.getObject("unit_price_vnd"), rs.getBigDecimal("area_m2"),
            (Integer) rs.getObject("bedrooms"), (Integer) rs.getObject("bathrooms"), (Integer) rs.getObject("floors"),
            rs.getBigDecimal("frontage_m"), rs.getBigDecimal("road_width_m"), rs.getString("direction"),
            rs.getString("legal_status_code"), rs.getString("legal_status_text"), rs.getString("furnishing"),
            (Long) rs.getObject("monthly_service_fee_vnd"), (Long) rs.getObject("deposit_vnd"),
            rs.getString("province_code"), rs.getString("district_code"), rs.getString("district_name"),
            rs.getString("ward_code"), rs.getString("ward_name"), rs.getString("address_summary"),
            (Double) rs.getObject("lat"), (Double) rs.getObject("lng"),
            rs.getObject("project_id", UUID.class), rs.getString("project_slug"), rs.getString("project_name"),
            rs.getString("thumbnail_url"), rs.getInt("image_count"), textList(rs.getArray("media_urls")),
            rs.getString("seller_name"), rs.getString("seller_avatar_url"), rs.getString("seller_role"),
            rs.getString("identity_status"), instant(rs, "identity_checked_at"), instant(rs, "identity_expires_at"),
            rs.getString("ownership_status"), instant(rs, "ownership_checked_at"), instant(rs, "ownership_expires_at"),
            rs.getString("ownership_document_type"), instant(rs, "listing_checked_at"),
            instant(rs, "published_at"), instant(rs, "updated_at"), instant(rs, "availability_confirmed_at"),
            (Long) rs.getObject("previous_price_vnd"), instant(rs, "price_changed_at"), rs.getString("search_text"),
            rs.getLong("row_version"));

    private static List<String> textList(Array array) throws SQLException {
        if (array == null) return List.of();
        return Arrays.asList((String[]) array.getArray());
    }

    /** Parameterised SQL under construction. */
    static final class Sql {
        private final StringBuilder text;
        private final List<Object> params = new ArrayList<>();

        Sql(String start) { text = new StringBuilder(start); }

        Sql append(String part) { text.append(part); return this; }

        Sql param(Object value) { params.add(value); return this; }

        String text() { return text.toString(); }

        Object[] params() { return params.toArray(); }
    }
}
