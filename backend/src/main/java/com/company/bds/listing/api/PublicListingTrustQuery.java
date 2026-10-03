package com.company.bds.listing.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * R-3 checks of the v1 listing reads, evaluated at request time with the definitions of contract §6 (the same as the v2
 * read model): an owner who is not ACTIVE hides their listings from the public, and the ownership badge counts only a
 * VERIFIED_OWNER check that is neither revoked nor expired now — never the {@code listings.is_verified_owner} flag,
 * which the daily expiry task clears up to a day late.
 */
@Component
public class PublicListingTrustQuery {
    private final JdbcTemplate jdbc;

    public PublicListingTrustQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean publiclyVisible(UUID listingId) {
        Boolean active = jdbc.query("""
                SELECT l.status = 'ACTIVE' AND (l.expires_at IS NULL OR l.expires_at > now()) AND u.status = 'ACTIVE'
                FROM listings l JOIN users u ON u.id = l.owner_id WHERE l.id = ?
                """, rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, listingId);
        return Boolean.TRUE.equals(active);
    }

    /** The listings among {@code listingIds} with a valid ownership check now. */
    public Set<UUID> ownershipVerified(Collection<UUID> listingIds) {
        Set<UUID> verified = new HashSet<>();
        if (listingIds.isEmpty()) return verified;
        jdbc.query("""
                SELECT DISTINCT listing_id FROM listing_verifications
                WHERE listing_id = ANY(?::uuid[]) AND status = 'VERIFIED_OWNER' AND revoked_at IS NULL
                  AND (expires_at IS NULL OR expires_at > now())
                """, rs -> { verified.add(rs.getObject(1, UUID.class)); }, (Object) listingIds.stream().distinct().map(UUID::toString).toArray(String[]::new));
        return verified;
    }
}
