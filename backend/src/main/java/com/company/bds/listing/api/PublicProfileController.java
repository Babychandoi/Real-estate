package com.company.bds.listing.api;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
                       CASE WHEN EXISTS (SELECT 1 FROM user_kyc_profiles k
                                         WHERE k.user_id = u.id AND k.status = 'VERIFIED') THEN TRUE ELSE FALSE END AS identity_verified,
                       COUNT(l.id) AS active_listing_count
                FROM users u
                LEFT JOIN listings l ON l.owner_id = u.id AND l.status = 'ACTIVE'
                WHERE u.id = ? AND u.status = 'ACTIVE'
                GROUP BY u.id, u.full_name, u.avatar_media_url, u.created_at
                """, (rs, row) -> new PublicProfileResponse(
                rs.getString("full_name"), rs.getString("avatar_media_url"), rs.getBoolean("identity_verified"),
                rs.getLong("active_listing_count"), rs.getTimestamp("created_at").toInstant()
        ), ownerId).stream().findFirst();
        return profile.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Approved, currently visible listings of one owner for their public profile page. */
    @GetMapping("/{ownerId}/listings")
    public java.util.List<PublicListingCard> listings(@PathVariable UUID ownerId) {
        return jdbc.query("""
                SELECT l.id, l.slug, r.title, r.purpose, r.property_type, r.price_vnd, r.area_m2, r.address_summary,
                       l.is_verified_owner, l.created_at,
                       (SELECT m.media_url FROM listing_media m WHERE m.revision_id = r.id ORDER BY m.sort_order LIMIT 1) AS image_url
                FROM listings l
                JOIN listing_revisions r ON r.id = l.public_revision_id
                JOIN users u ON u.id = l.owner_id AND u.status = 'ACTIVE'
                WHERE l.owner_id = ? AND l.status = 'ACTIVE'
                ORDER BY l.created_at DESC
                LIMIT 60
                """, (rs, row) -> new PublicListingCard(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"), rs.getString("purpose"),
                rs.getString("property_type"), rs.getLong("price_vnd"), rs.getBigDecimal("area_m2"), rs.getString("address_summary"),
                rs.getBoolean("is_verified_owner"), rs.getString("image_url") == null ? "" : rs.getString("image_url"),
                rs.getTimestamp("created_at").toInstant()), ownerId);
    }

    public record PublicListingCard(UUID id, String slug, String title, String purpose, String propertyType, long priceVnd,
                                    java.math.BigDecimal areaM2, String addressSummary, boolean isVerified,
                                    String primaryImageUrl, Instant publishedAt) { }

    public record PublicProfileResponse(String displayName, String avatarMediaUrl, boolean identityVerified,
                                        long activeListingCount, Instant memberSince) { }
}
