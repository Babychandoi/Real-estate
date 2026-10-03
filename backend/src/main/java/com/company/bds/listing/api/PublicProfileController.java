package com.company.bds.listing.api;

import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.ContactInfoGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Public, non-sensitive reputation information for a listing owner. */
@RestController
@RequestMapping("/api/v1/public/profiles")
public class PublicProfileController {
    private final JdbcTemplate jdbc;

    public PublicProfileController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/{ownerId}")
    public ResponseEntity<PublicProfileResponse> getProfile(@PathVariable UUID ownerId) {
        Optional<PublicProfileResponse> profile = jdbc.query("""
                SELECT u.full_name, u.avatar_media_url, u.created_at,
                       -- Contract §6: identity counts only while VERIFIED, not revoked and not expired (R-3, W6).
                       EXISTS (SELECT 1 FROM user_kyc_profiles k
                               WHERE k.user_id = u.id AND k.status = 'VERIFIED' AND k.revoked_at IS NULL
                                 AND (k.expires_at IS NULL OR k.expires_at > now())) AS identity_verified,
                       (SELECT COUNT(*) FROM listings l
                        WHERE l.owner_id = u.id AND l.status = 'ACTIVE' AND l.public_revision_id IS NOT NULL
                          AND (l.expires_at IS NULL OR l.expires_at > now())) AS active_listing_count
                FROM users u
                WHERE u.id = ? AND u.status = 'ACTIVE'
                """, (rs, row) -> new PublicProfileResponse(
                ContactInfoGuard.redact(rs.getString("full_name")), rs.getString("avatar_media_url"), rs.getBoolean("identity_verified"),
                rs.getLong("active_listing_count"), rs.getTimestamp("created_at").toInstant()
        ), ownerId).stream().findFirst();
        return profile.map(ResponseEntity::ok)
                .orElseThrow(() -> ApiException.notFound("PROFILE_NOT_FOUND", "Không tìm thấy người đăng."));
    }

    /**
     * Approved, currently visible listings of one owner, newest first, paged ({@code page} from 0, {@code size} clamped to 1..100,
     * default 60). R-2 (W6): this used to stop at 60 rows with no way to reach the rest; the total is in
     * {@code X-Total-Count} and the successor is {@code /api/v2/public/sellers/{id}/listings}.
     */
    @Deprecated
    @GetMapping("/{ownerId}/listings")
    public ResponseEntity<java.util.List<PublicListingCard>> listings(@PathVariable UUID ownerId,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "60") int size) {
        // Clamped, not refused: before W6 these parameters did not exist and any value was ignored (backward compatible).
        page = Math.max(0, page);
        size = Math.max(1, Math.min(size, 100));
        String from = """
                FROM listings l
                JOIN listing_revisions r ON r.id = l.public_revision_id
                JOIN users u ON u.id = l.owner_id AND u.status = 'ACTIVE'
                WHERE l.owner_id = ? AND l.status = 'ACTIVE' AND (l.expires_at IS NULL OR l.expires_at > now())
                """;
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, ownerId);
        java.util.List<PublicListingCard> items = jdbc.query("""
                SELECT l.id, l.slug, r.title, r.purpose, r.property_type, r.price_vnd, r.price_period, r.area_m2, r.address_summary,
                       l.created_at,
                       -- Contract §6: a valid (not revoked, not expired) ownership check now, not the daily-refreshed flag.
                       EXISTS (SELECT 1 FROM listing_verifications v WHERE v.listing_id = l.id AND v.status = 'VERIFIED_OWNER'
                               AND v.revoked_at IS NULL AND (v.expires_at IS NULL OR v.expires_at > now())) AS ownership_verified,
                       (SELECT m.media_url FROM listing_media m WHERE m.revision_id = r.id ORDER BY m.sort_order LIMIT 1) AS image_url
                """ + from + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?", (rs, row) -> new PublicListingCard(
                rs.getObject("id", UUID.class), rs.getString("slug"), ContactInfoGuard.redact(rs.getString("title")), rs.getString("purpose"),
                rs.getString("property_type"), rs.getLong("price_vnd"), rs.getString("price_period"), rs.getBigDecimal("area_m2"),
                ContactInfoGuard.redact(rs.getString("address_summary")),
                rs.getBoolean("ownership_verified"), rs.getString("image_url") == null ? "" : rs.getString("image_url"),
                rs.getTimestamp("created_at").toInstant()), ownerId, size, (long) page * size);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(total == null ? 0 : total))
                .header("Deprecation", "true")
                .header("Link", "</api/v2/public/sellers/" + ownerId + "/listings>; rel=\"successor-version\"")
                .body(items);
    }

    /** {@code pricePeriod}: {@code MONTH} for a rent listing, {@code null} for a sale. */
    public record PublicListingCard(UUID id, String slug, String title, String purpose, String propertyType, long priceVnd,
                                    String pricePeriod, java.math.BigDecimal areaM2, String addressSummary, boolean isVerified,
                                    String primaryImageUrl, Instant publishedAt) { }

    public record PublicProfileResponse(String displayName, String avatarMediaUrl, boolean identityVerified,
                                        long activeListingCount, Instant memberSince) { }
}
