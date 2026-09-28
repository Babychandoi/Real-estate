package com.company.bds.asset;

import com.company.bds.shared.error.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Real property assets behind listings and duplicate detection (P-05, D-10).
 *
 * <ol>
 *   <li>Fingerprint = md5(district | type | rounded area | normalized address) of the listing's current revision.</li>
 *   <li>On approval an exact fingerprint links the listing to a {@code property_assets} row (created on first sight),
 *       so several listings of one real property share one asset.</li>
 *   <li>Candidates come from blocking keys only (same district, type, purpose, price bucket ±1, area ±5%) through
 *       {@code idx_listing_fingerprints_block}; pg_trgm similarity is computed only for the rows of that block, so the
 *       work per listing grows with its block, never with the total number of listings (no n² comparison).</li>
 * </ol>
 */
@Service
public class PropertyAssetService {
    /** A block is small by construction; the cap keeps a pathological block (bulk import of one building) bounded. */
    static final int MAX_BLOCK = 200;
    static final double MIN_SIMILARITY = 0.35;
    private static final Set<String> CANDIDATE_STATUSES = Set.of("ACTIVE", "PENDING_REVIEW", "PAUSED");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper json;

    public PropertyAssetService(JdbcTemplate jdbc, Clock clock, ObjectMapper json) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.json = json;
    }

    /** Recomputes the comparable attributes of one revision (idempotent). */
    @Transactional
    public void refreshFingerprint(UUID listingId, UUID revisionId) {
        jdbc.update("""
                INSERT INTO listing_fingerprints(listing_id, revision_id, fingerprint, district_code, property_type, purpose,
                                                 area_m2, price_vnd, price_bucket, match_text, computed_at)
                SELECT r.listing_id, r.id,
                       md5(concat_ws('|', COALESCE(r.district_code, ''), r.property_type, round(r.area_m2)::int,
                                     bds_normalize_text(r.address_summary))),
                       COALESCE(r.district_code, ''), r.property_type, r.purpose, r.area_m2, r.price_vnd,
                       CASE WHEN r.price_vnd <= 0 THEN 0 ELSE floor(ln(r.price_vnd::numeric) / ln(1.15))::int END,
                       bds_normalize_text(concat_ws(' ', r.address_summary, r.title)), ?
                FROM listing_revisions r WHERE r.id = ? AND r.listing_id = ?
                ON CONFLICT (listing_id) DO UPDATE SET revision_id = EXCLUDED.revision_id, fingerprint = EXCLUDED.fingerprint,
                    district_code = EXCLUDED.district_code, property_type = EXCLUDED.property_type, purpose = EXCLUDED.purpose,
                    area_m2 = EXCLUDED.area_m2, price_vnd = EXCLUDED.price_vnd, price_bucket = EXCLUDED.price_bucket,
                    match_text = EXCLUDED.match_text, computed_at = EXCLUDED.computed_at
                """, Timestamp.from(clock.instant()), revisionId, listingId);
    }

    /** Called when a revision becomes public: exact fingerprint → shared asset, then fresh candidates. */
    @Transactional
    public UUID linkAssetOnApproval(UUID listingId, UUID revisionId) {
        refreshFingerprint(listingId, revisionId);
        jdbc.update("""
                INSERT INTO property_assets(id, fingerprint, district_code, property_type, area_m2_rounded, normalized_address, created_at)
                SELECT ?, f.fingerprint, f.district_code, f.property_type, round(f.area_m2)::int,
                       left(bds_normalize_text(r.address_summary), 400), ?
                FROM listing_fingerprints f JOIN listing_revisions r ON r.id = f.revision_id
                WHERE f.listing_id = ?
                ON CONFLICT (fingerprint) DO NOTHING
                """, UUID.randomUUID(), Timestamp.from(clock.instant()), listingId);
        UUID assetId = jdbc.queryForObject("""
                SELECT a.id FROM property_assets a JOIN listing_fingerprints f ON f.fingerprint = a.fingerprint
                WHERE f.listing_id = ?
                """, UUID.class, listingId);
        jdbc.update("UPDATE listings SET property_asset_id = ? WHERE id = ? AND property_asset_id IS DISTINCT FROM ?",
                assetId, listingId, assetId);
        detectCandidates(listingId);
        return assetId;
    }

    /**
     * Finds duplicate candidates of one listing inside its block and upserts them (status of decided pairs is kept).
     * Requires a fingerprint row; returns how many rows were compared so callers/tests can see the cost.
     */
    @Transactional
    public DetectionResult detectCandidates(UUID listingId) {
        List<Compared> block = jdbc.query("""
                SELECT f.listing_id, f.fingerprint = me.fingerprint AS exact, similarity(f.match_text, me.match_text) AS sim,
                       f.area_m2, me.area_m2 AS my_area, f.price_vnd, me.price_vnd AS my_price, l.owner_id = ml.owner_id AS same_owner,
                       l.status
                FROM listing_fingerprints me
                JOIN listing_fingerprints f
                  ON f.district_code = me.district_code AND f.property_type = me.property_type AND f.purpose = me.purpose
                 AND f.price_bucket BETWEEN me.price_bucket - 1 AND me.price_bucket + 1
                 AND f.area_m2 BETWEEN me.area_m2 * 0.95 AND me.area_m2 * 1.05
                 AND f.listing_id <> me.listing_id
                JOIN listings l ON l.id = f.listing_id
                JOIN listings ml ON ml.id = me.listing_id
                WHERE me.listing_id = ?
                ORDER BY f.listing_id
                LIMIT ?
                """, (rs, n) -> new Compared(rs.getObject("listing_id", UUID.class), rs.getBoolean("exact"), rs.getDouble("sim"),
                rs.getBigDecimal("area_m2"), rs.getBigDecimal("my_area"), rs.getLong("price_vnd"), rs.getLong("my_price"),
                rs.getBoolean("same_owner"), rs.getString("status")), listingId, MAX_BLOCK);
        int created = 0;
        Instant now = clock.instant();
        for (Compared c : block) {
            if (!CANDIDATE_STATUSES.contains(c.status())) continue;
            if (!c.exact() && c.similarity() < MIN_SIMILARITY) continue;
            double areaCloseness = 1 - Math.min(1, c.area().subtract(c.myArea()).abs()
                    .divide(c.myArea().multiply(new BigDecimal("0.05")), 6, RoundingMode.HALF_UP).doubleValue());
            double priceCloseness = c.myPrice() <= 0 ? 0
                    : 1 - Math.min(1, Math.abs(c.price() - c.myPrice()) / (double) Math.max(c.price(), c.myPrice()));
            double score = c.exact() ? 1.0 : Math.min(0.999, 0.7 * c.similarity() + 0.15 * areaCloseness + 0.15 * priceCloseness);
            List<String> reasons = new ArrayList<>(List.of("SAME_DISTRICT_TYPE", "AREA_WITHIN_5_PERCENT", "PRICE_BUCKET"));
            if (c.exact()) reasons.add(0, "EXACT_FINGERPRINT");
            reasons.add("TEXT_SIMILARITY:" + BigDecimal.valueOf(c.similarity()).setScale(2, RoundingMode.HALF_UP));
            if (c.sameOwner()) reasons.add("SAME_OWNER");
            String reasonsJson = toJson(reasons);
            BigDecimal rounded = BigDecimal.valueOf(score).setScale(3, RoundingMode.HALF_UP);
            // A pair is stored once, in whichever direction it was first found.
            int updated = jdbc.update("""
                    UPDATE listing_duplicate_candidates SET score = ?, reasons = CAST(? AS jsonb), updated_at = ?
                    WHERE (listing_id = ? AND candidate_listing_id = ?) OR (listing_id = ? AND candidate_listing_id = ?)
                    """, rounded, reasonsJson, Timestamp.from(now), listingId, c.listingId(), c.listingId(), listingId);
            if (updated == 0) {
                created += jdbc.update("""
                        INSERT INTO listing_duplicate_candidates(id, listing_id, candidate_listing_id, score, reasons, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, CAST(? AS jsonb), 'OPEN', ?, ?)
                        ON CONFLICT (listing_id, candidate_listing_id) DO NOTHING
                        """, UUID.randomUUID(), listingId, c.listingId(), rounded, reasonsJson, Timestamp.from(now), Timestamp.from(now));
            }
        }
        return new DetectionResult(block.size(), created);
    }

    /** Duplicate candidates of a listing in both directions, best score first. */
    @Transactional(readOnly = true)
    public List<Candidate> candidatesOf(UUID listingId) {
        return jdbc.query("""
                SELECT d.id, CASE WHEN d.listing_id = ? THEN d.candidate_listing_id ELSE d.listing_id END AS other_id,
                       d.score, d.reasons::text, d.status, d.note, d.decided_at, l.status AS other_status, l.slug,
                       r.title, r.price_vnd, r.area_m2, r.address_summary, l.owner_id, l.property_asset_id
                FROM listing_duplicate_candidates d
                JOIN listings l ON l.id = CASE WHEN d.listing_id = ? THEN d.candidate_listing_id ELSE d.listing_id END
                LEFT JOIN listing_revisions r ON r.id = COALESCE(
                    l.public_revision_id,
                    (SELECT r2.id FROM listing_revisions r2 WHERE r2.listing_id = l.id ORDER BY r2.revision_number DESC LIMIT 1))
                WHERE d.listing_id = ? OR d.candidate_listing_id = ?
                ORDER BY d.score DESC, d.id
                LIMIT 50
                """, (rs, n) -> new Candidate(rs.getObject("id", UUID.class), rs.getObject("other_id", UUID.class),
                rs.getBigDecimal("score"), parseReasons(rs.getString(4)), rs.getString("status"), rs.getString("note"),
                rs.getString("other_status"), rs.getString("slug"), rs.getString("title"),
                rs.getObject("price_vnd") == null ? null : rs.getLong("price_vnd"), rs.getBigDecimal("area_m2"),
                rs.getString("address_summary"), rs.getObject("owner_id", UUID.class),
                rs.getObject("property_asset_id", UUID.class)), listingId, listingId, listingId, listingId);
    }

    /** Moderator decision on a candidate pair: DISMISSED (different properties) or CONFIRMED (same property). */
    @Transactional
    public void decide(UUID candidateId, String status, String note, UUID actorId) {
        if (!Set.of("DISMISSED", "CONFIRMED").contains(status)) {
            throw ApiException.badRequest("INVALID_DUPLICATE_DECISION", "Chỉ có thể xác nhận trùng hoặc bỏ qua cặp tin.");
        }
        int changed = jdbc.update("""
                UPDATE listing_duplicate_candidates SET status = ?, note = ?, decided_by = ?, decided_at = ?, updated_at = ?
                WHERE id = ?
                """, status, note == null || note.isBlank() ? null : note.trim(), actorId, Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant()), candidateId);
        if (changed == 0) throw ApiException.notFound("DUPLICATE_CANDIDATE_NOT_FOUND", "Không tìm thấy cặp tin nghi trùng.");
    }

    /** Listings with a submitted revision whose fingerprint is missing or stale (for the periodic sweep). */
    @Transactional(readOnly = true)
    public List<UUID[]> staleSubmissions(int limit) {
        return jdbc.query("""
                SELECT r.listing_id, r.id FROM listing_revisions r
                LEFT JOIN listing_fingerprints f ON f.listing_id = r.listing_id
                WHERE r.status = 'SUBMITTED' AND (f.listing_id IS NULL OR f.revision_id <> r.id)
                ORDER BY r.submitted_at NULLS LAST, r.id
                LIMIT ?
                """, (rs, n) -> new UUID[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}, limit);
    }

    private String toJson(List<String> reasons) {
        try { return json.writeValueAsString(reasons); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private List<String> parseReasons(String raw) {
        try { return raw == null ? List.of() : List.of(json.readValue(raw, String[].class)); }
        catch (JsonProcessingException e) { return List.of(); }
    }

    private record Compared(UUID listingId, boolean exact, double similarity, BigDecimal area, BigDecimal myArea, long price,
                            long myPrice, boolean sameOwner, String status) {}

    public record DetectionResult(int compared, int created) {}

    public record Candidate(UUID id, UUID otherListingId, BigDecimal score, List<String> reasons, String status, String note,
                            String otherStatus, String otherSlug, String otherTitle, Long otherPriceVnd, BigDecimal otherAreaM2,
                            String otherAddress, UUID otherOwnerId, UUID otherAssetId) {}
}
