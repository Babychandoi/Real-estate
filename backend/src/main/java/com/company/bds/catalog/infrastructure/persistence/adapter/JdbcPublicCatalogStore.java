package com.company.bds.catalog.infrastructure.persistence.adapter;

import com.company.bds.catalog.application.port.PublicCatalogStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC read side of the public project/area pages; every statement is bounded and index-backed. */
@Repository
public class JdbcPublicCatalogStore implements PublicCatalogStore {
    /** Same visibility rule as the public search: the seller must be ACTIVE. */
    private static final String SELLER_ACTIVE =
            " AND EXISTS (SELECT 1 FROM users ou WHERE ou.id = p.owner_id AND ou.status = 'ACTIVE')";
    private static final String PROJECT_COLUMNS = """
            pr.id, pr.slug, pr.name, pr.developer_name, pr.province_code, pr.district_code, loc.name AS district_name,
            loc.slug AS area_slug, pr.address, pr.total_area_m2, pr.total_blocks, pr.total_units, pr.handover_year,
            pr.legal_license_number, pr.status, pr.description, pr.website_url, pr.info_source, pr.info_checked_at,
            pr.updated_at""";
    private static final String PROJECT_FROM =
            " FROM projects pr LEFT JOIN search_locations loc ON loc.province_code = pr.province_code AND loc.district_code = pr.district_code";
    /** Statistic per bucket: SALE = price per m², RENT = monthly rent. */
    private static final String INVENTORY_SELECT = """
            SELECT p.purpose, CASE WHEN GROUPING(p.property_type) = 1 THEN NULL ELSE p.property_type END AS property_type,
                   COUNT(*) AS n,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY CASE WHEN p.purpose = 'SALE' AND p.area_m2 > 0
                       THEN p.price_vnd / p.area_m2 ELSE p.price_vnd END) AS median,
                   MAX(p.updated_at) AS last_updated
            FROM listing_public_read p WHERE\s""";
    private static final String INVENTORY_GROUP =
            " GROUP BY GROUPING SETS ((p.purpose), (p.purpose, p.property_type)) ORDER BY 1, 2 NULLS FIRST";

    private final JdbcTemplate jdbc;

    public JdbcPublicCatalogStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ProjectRow> projectBySlug(String slug) {
        return jdbc.query("SELECT " + PROJECT_COLUMNS + PROJECT_FROM + " WHERE pr.slug = ?", PROJECT, slug).stream().findFirst();
    }

    @Override
    public Optional<ProjectRow> projectById(UUID id) {
        return jdbc.query("SELECT " + PROJECT_COLUMNS + PROJECT_FROM + " WHERE pr.id = ?", PROJECT, id).stream().findFirst();
    }

    @Override
    public List<Amenity> amenities(UUID projectId) {
        return jdbc.query("""
                SELECT id, name, category, distance_m, source_name, source_url, checked_at FROM project_amenities
                WHERE project_id = ? ORDER BY sort_order, name LIMIT 100""",
                (rs, n) -> new Amenity(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        (Integer) rs.getObject(4), rs.getString(5), rs.getString(6), date(rs, 7)), projectId);
    }

    @Override
    public List<InventoryRow> projectInventory(UUID projectId) {
        return jdbc.query(INVENTORY_SELECT + "p.project_id = ?" + SELLER_ACTIVE + INVENTORY_GROUP, INVENTORY, projectId);
    }

    @Override
    public List<InventoryRow> areaInventory(String provinceCode, String districtCode) {
        return jdbc.query(INVENTORY_SELECT + "p.province_code = ? AND p.district_code = ?" + SELLER_ACTIVE + INVENTORY_GROUP,
                INVENTORY, provinceCode, districtCode);
    }

    @Override
    public List<ProjectCard> projectPage(String districtCode, int page, int size) {
        String where = " WHERE pr.status <> 'LOCKED'" + (districtCode == null ? "" : " AND pr.district_code = ?");
        Object[] params = districtCode == null ? new Object[]{size, (long) page * size}
                : new Object[]{districtCode, size, (long) page * size};
        return jdbc.query("SELECT pr.slug, pr.name, loc.name, loc.slug, pr.status, COALESCE(c.n, 0)" + PROJECT_FROM
                        + " LEFT JOIN LATERAL (SELECT COUNT(*) AS n FROM listing_public_read p WHERE p.project_id = pr.id" + SELLER_ACTIVE + ") c ON TRUE"
                        + where + " ORDER BY COALESCE(c.n, 0) DESC, pr.name, pr.id LIMIT ? OFFSET ?",
                PROJECT_CARD, params);
    }

    @Override
    public long projectCount(String districtCode) {
        Long n = districtCode == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM projects WHERE status <> 'LOCKED'", Long.class)
                : jdbc.queryForObject("SELECT COUNT(*) FROM projects WHERE status <> 'LOCKED' AND district_code = ?", Long.class, districtCode);
        return n == null ? 0 : n;
    }

    @Override
    public List<ProjectCard> topProjects(int limit) {
        return projectPage(null, 0, limit);
    }

    @Override
    public Optional<AreaRow> areaBySlug(String slug) {
        return jdbc.query("SELECT province_code, district_code, province_name, name, slug FROM search_locations WHERE slug = ?",
                (rs, n) -> new AreaRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)), slug)
                .stream().findFirst();
    }

    @Override
    public List<AreaCard> areas() {
        return jdbc.query("""
                SELECT loc.slug, loc.name, loc.province_name, loc.district_code, COALESCE(c.n, 0)
                FROM search_locations loc
                LEFT JOIN (SELECT p.province_code, p.district_code, COUNT(*) AS n FROM listing_public_read p
                           WHERE TRUE""" + SELLER_ACTIVE + """
                            GROUP BY p.province_code, p.district_code) c
                       ON c.province_code = loc.province_code AND c.district_code = loc.district_code
                ORDER BY COALESCE(c.n, 0) DESC, loc.name LIMIT 500""",
                (rs, n) -> new AreaCard(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5)));
    }

    @Override
    public List<SitemapEntry> projectSitemap(int limit) {
        return jdbc.query("""
                SELECT pr.slug, GREATEST(pr.updated_at, (SELECT MAX(p.updated_at) FROM listing_public_read p WHERE p.project_id = pr.id))
                FROM projects pr WHERE pr.status <> 'LOCKED' ORDER BY pr.id LIMIT ?""",
                (rs, n) -> new SitemapEntry(rs.getString(1), instant(rs, 2)), limit);
    }

    @Override
    public List<SitemapEntry> areaSitemap() {
        return jdbc.query("""
                SELECT loc.slug, c.last_updated FROM search_locations loc
                JOIN (SELECT p.province_code, p.district_code, MAX(p.updated_at) AS last_updated FROM listing_public_read p
                      WHERE TRUE""" + SELLER_ACTIVE + """
                       GROUP BY p.province_code, p.district_code) c
                  ON c.province_code = loc.province_code AND c.district_code = loc.district_code
                ORDER BY loc.slug LIMIT 1000""",
                (rs, n) -> new SitemapEntry(rs.getString(1), instant(rs, 2)));
    }

    @Override
    public void replacePublicProfile(UUID projectId, String description, String websiteUrl, String infoSource,
                                     LocalDate infoCheckedAt, String status, List<Amenity> amenities) {
        jdbc.update("""
                UPDATE projects SET description = ?, website_url = ?, info_source = ?, info_checked_at = ?,
                    status = COALESCE(?, status), updated_at = now() WHERE id = ?""",
                description, websiteUrl, infoSource, infoCheckedAt == null ? null : Date.valueOf(infoCheckedAt), status, projectId);
        jdbc.update("DELETE FROM project_amenities WHERE project_id = ?", projectId);
        int order = 0;
        for (Amenity amenity : amenities) {
            jdbc.update("""
                    INSERT INTO project_amenities(id, project_id, name, category, distance_m, source_name, source_url, checked_at, sort_order)
                    VALUES (?,?,?,?,?,?,?,?,?)""",
                    UUID.randomUUID(), projectId, amenity.name(), amenity.category(), amenity.distanceM(), amenity.sourceName(),
                    amenity.sourceUrl(), Date.valueOf(amenity.checkedAt()), order++);
        }
        // the read model carries the project name/slug: refresh the project's listings (LOCKED hides the link)
        jdbc.query("SELECT bds_refresh_listing_public_read(l.id) FROM listings l JOIN listing_revisions r ON r.id = l.public_revision_id WHERE r.project_id = ?",
                rs -> { }, projectId);
    }

    private static final RowMapper<ProjectRow> PROJECT = (rs, n) -> new ProjectRow(
            rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"), rs.getString("developer_name"),
            rs.getString("province_code"), rs.getString("district_code"), rs.getString("district_name"),
            rs.getString("area_slug"), rs.getString("address"), rs.getBigDecimal("total_area_m2"), rs.getInt("total_blocks"),
            rs.getInt("total_units"), (Integer) rs.getObject("handover_year"), rs.getString("legal_license_number"),
            rs.getString("status"), rs.getString("description"), rs.getString("website_url"), rs.getString("info_source"),
            date(rs, "info_checked_at"), instant(rs, "updated_at"));

    private static final RowMapper<ProjectCard> PROJECT_CARD = (rs, n) -> new ProjectCard(
            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6));

    private static final RowMapper<InventoryRow> INVENTORY = (rs, n) -> new InventoryRow(
            rs.getString("purpose"), rs.getString("property_type"), rs.getLong("n"),
            rs.getObject("median") == null ? null : rs.getDouble("median"), instant(rs, "last_updated"));

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static LocalDate date(ResultSet rs, int column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
