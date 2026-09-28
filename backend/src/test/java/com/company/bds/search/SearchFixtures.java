package com.company.bds.search;

import com.company.bds.testsupport.TestData;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * S2 test helpers on top of {@link TestData}: a random search token per test (keeps result sets independent of other
 * rows in the shared database), extra public-revision attributes and explicit read-model refreshes.
 */
final class SearchFixtures {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz";

    private final JdbcTemplate jdbc;
    private final TestData data;

    SearchFixtures(JdbcTemplate jdbc, TestData data) {
        this.jdbc = jdbc;
        this.data = data;
    }

    /** A letters-only token (a single search token after normalisation) unique to the calling test. */
    static String token() {
        StringBuilder out = new StringBuilder("zs");
        for (int i = 0; i < 8; i++) out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return out.toString();
    }

    TestData.TestUser seller(String role) {
        return data.user().role(role).name("Người bán " + token()).create();
    }

    /** Updates columns of the listing's public revision and refreshes its read-model row. */
    void revise(TestData.TestListing listing, String assignments, Object... args) {
        Object[] params = new Object[args.length + 1];
        System.arraycopy(args, 0, params, 0, args.length);
        params[args.length] = listing.publicRevisionId();
        jdbc.update("UPDATE listing_revisions SET " + assignments + " WHERE id = ?", params);
        refresh(listing.id());
    }

    void refresh(UUID listingId) {
        jdbc.queryForList("SELECT * FROM bds_refresh_listing_public_read(?)", listingId);
    }

    void kyc(UUID userId, String status, Instant verifiedAt, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO user_kyc_profiles(id,user_id,id_number_encrypted,id_number_lookup_hash,full_name,status,created_at,verified_at,expires_at)
                VALUES (?,?,?,?,?,?,now(),?,?)
                ON CONFLICT (user_id) DO UPDATE SET status = EXCLUDED.status, verified_at = EXCLUDED.verified_at, expires_at = EXCLUDED.expires_at
                """, UUID.randomUUID(), userId, "v1:test", "s2-kyc-" + userId, "Chủ tin kiểm thử", status,
                ts(verifiedAt), ts(expiresAt));
    }

    void ownership(UUID listingId, String status, Instant verifiedAt, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO listing_verifications(id,listing_id,verification_type,owner_name_on_doc,status,created_at,verified_at,expires_at)
                VALUES (?,?,'CERTIFICATE_OF_OWNERSHIP','Chủ sở hữu kiểm thử',?,now(),?,?)
                """, UUID.randomUUID(), listingId, status, ts(verifiedAt), ts(expiresAt));
        refresh(listingId);
    }

    /** Refreshes every listing of the owner (after identity/role/name changes; normally done by the fan-out job). */
    void refreshOwner(UUID ownerId) {
        jdbc.queryForList("SELECT f.* FROM listings l, LATERAL bds_refresh_listing_public_read(l.id) f WHERE l.owner_id = ?", ownerId);
    }

    static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
