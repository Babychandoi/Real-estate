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
                SELECT u.full_name, u.created_at,
                       CASE WHEN EXISTS (SELECT 1 FROM user_kyc_profiles k
                                         WHERE k.user_id = u.id AND k.status = 'VERIFIED') THEN TRUE ELSE FALSE END AS identity_verified,
                       COUNT(l.id) AS active_listing_count
                FROM users u
                LEFT JOIN listings l ON l.owner_id = u.id AND l.status = 'ACTIVE'
                WHERE u.id = ? AND u.status = 'ACTIVE'
                GROUP BY u.id, u.full_name, u.created_at
                """, (rs, row) -> new PublicProfileResponse(
                rs.getString("full_name"), rs.getBoolean("identity_verified"),
                rs.getLong("active_listing_count"), rs.getObject("created_at", Instant.class)
        ), ownerId).stream().findFirst();
        return profile.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record PublicProfileResponse(String displayName, boolean identityVerified,
                                        long activeListingCount, Instant memberSince) { }
}
