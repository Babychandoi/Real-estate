package com.company.bds.shared.uat;

import com.company.bds.shared.security.PiiProtectionService;
import com.company.bds.testsupport.BdsIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F01.2: a fixed clock gives identical fixtures; purge leaves no synthetic row behind. */
@BdsIntegrationTest
class UatDataSeederTests {
    private static final String CLOCK = "2026-09-01T03:00:00Z";
    private static final Instant NOW = Instant.parse(CLOCK);

    @Autowired JdbcTemplate jdbc;
    @Autowired PiiProtectionService pii;
    @Autowired TransactionTemplate transaction;
    @Autowired ApplicationContext context;

    @Test
    void fixedClockSeedIsDeterministicCoversNewColumnsAndPurgeRemovesEverySyntheticRow() {
        UatDataSeeder seeder = seeder("seed", CLOCK, "");
        try {
            seeder.seedNow();
            String first = fingerprint();
            assertSeededData();

            seeder.seedNow();
            assertThat(fingerprint()).as("same clock, same database: identical fixtures").isEqualTo(first);
        } finally {
            seeder.purgeNow();
        }
        assertThat(syntheticRowsLeft()).as("rows referencing ee5eed ids or UAT keys after purge").isEmpty();
    }

    @Test
    void passwordMakesSyntheticAccountsSignInOutsideProductionOnly() {
        UatDataSeeder seeder = seeder("seed", CLOCK, "Uat-Fixture-Password-2026");
        try {
            seeder.seedNow();
            String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, syntheticUser(15));
            assertThat(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("Uat-Fixture-Password-2026", hash)).isTrue();
        } finally {
            seeder.purgeNow();
        }
        assertThatThrownBy(() -> new UatDataSeeder(jdbc, pii, transaction, context, "seed", false, List.of(), "http://127.0.0.1:9",
                "s0be-unused", CLOCK, "production", "Uat-Fixture-Password-2026")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seeder("seed", CLOCK, "short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> seeder("seed", "1 September 2026", "")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO-8601");
    }

    private void assertSeededData() {
        // Timestamps are relative to the clock: fake user #1 was created 20 days before it.
        assertThat(jdbc.queryForObject("SELECT created_at FROM users WHERE id = ?", Timestamp.class, syntheticUser(1)).toInstant())
                .isEqualTo(NOW.minus(Duration.ofDays(20)));
        assertThat(jdbc.queryForObject("SELECT MAX(created_at) FROM listings WHERE id::text LIKE 'ee5eed%'", Timestamp.class).toInstant())
                .isBefore(NOW);

        // Two private owners (one verified) with their own listings.
        assertThat(jdbc.queryForList("""
                SELECT k.status FROM users u JOIN user_roles r ON r.user_id = u.id LEFT JOIN user_kyc_profiles k ON k.user_id = u.id
                WHERE u.id::text LIKE 'ee5eed%' AND r.role = 'OWNER' ORDER BY u.id
                """, String.class)).containsExactly("VERIFIED", "PENDING");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM listings l JOIN user_roles r ON r.user_id = l.owner_id WHERE l.id::text LIKE 'ee5eed%' AND r.role = 'OWNER'
                """, Integer.class)).isEqualTo(5);

        // New columns (contract §2.1–§2.3) are populated and satisfy their constraints.
        assertThat(jdbc.queryForList("SELECT DISTINCT source FROM listings WHERE id::text LIKE 'ee5eed%'", String.class)).containsExactly("SEED");
        Map<String, Object> rent = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE r.deposit_vnd IS NULL) AS rent_without_deposit,
                       COUNT(*) FILTER (WHERE r.property_type = 'APARTMENT' AND r.monthly_service_fee_vnd IS NULL) AS apartment_without_fee,
                       COUNT(*) FILTER (WHERE r.price_period IS DISTINCT FROM 'MONTH') AS without_period,
                       COUNT(*) AS total
                FROM listing_revisions r WHERE r.listing_id::text LIKE 'ee5eed%' AND r.purpose = 'RENT'
                """);
        assertThat(rent).containsEntry("rent_without_deposit", 0L).containsEntry("apartment_without_fee", 0L).containsEntry("without_period", 0L);
        assertThat((Long) rent.get("total")).isPositive();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM listing_revisions WHERE listing_id::text LIKE 'ee5eed%' AND purpose = 'SALE'
                  AND (deposit_vnd IS NOT NULL OR monthly_service_fee_vnd IS NOT NULL OR price_period IS NOT NULL)
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForList("""
                SELECT DISTINCT legal_status || '=' || legal_status_code FROM listing_revisions
                WHERE listing_id::text LIKE 'ee5eed%' AND legal_status IS NOT NULL ORDER BY 1
                """, String.class)).containsExactlyInAnyOrder("Hợp đồng mua bán=SALE_CONTRACT", "Sổ hồng lâu dài=PINK_BOOK", "Sổ đỏ chính chủ=RED_BOOK");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM listing_revisions WHERE listing_id::text LIKE 'ee5eed%' AND project_id::text LIKE 'ee5eed%'
                """, Integer.class)).as("apartments linked to seeded projects").isPositive();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM listings WHERE id::text LIKE 'ee5eed%' AND status = 'ACTIVE'
                  AND (availability_confirmed_at IS NULL OR availability_confirmed_at < created_at OR expires_at <= ?)
                """, Integer.class, Timestamp.from(NOW))).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM listings WHERE id::text LIKE 'ee5eed%' AND status = 'EXPIRED' AND (expires_at IS NULL OR expires_at > ?)
                """, Integer.class, Timestamp.from(NOW))).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_kyc_profiles WHERE user_id::text LIKE 'ee5eed%' AND status = 'VERIFIED'
                  AND expires_at IS DISTINCT FROM verified_at + interval '24 months'
                """, Integer.class)).isZero();
    }

    /** Every uuid column (except the append-only audit trail) plus the text keys the seeder uses. */
    private List<String> syntheticRowsLeft() {
        List<String> leftovers = new ArrayList<>();
        List<Map<String, Object>> columns = jdbc.queryForList("""
                SELECT table_name, column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND data_type = 'uuid' AND table_name <> 'audit_events'
                """);
        for (Map<String, Object> column : columns) {
            String sql = "SELECT COUNT(*) FROM " + column.get("table_name") + " WHERE " + column.get("column_name") + "::text LIKE 'ee5eed%'";
            Integer count = jdbc.queryForObject(sql, Integer.class);
            if (count != null && count > 0) leftovers.add(column.get("table_name") + "." + column.get("column_name") + "=" + count);
        }
        String[] textKeys = {
                "SELECT COUNT(*) FROM users WHERE email LIKE 'uat.%@example.invalid'",
                "SELECT COUNT(*) FROM projects WHERE slug LIKE 'uat-%'",
                "SELECT COUNT(*) FROM cms_articles WHERE slug LIKE 'uat-%'",
                "SELECT COUNT(*) FROM listing_reports WHERE case_number LIKE 'UAT-CASE-%'",
                "SELECT COUNT(*) FROM package_orders WHERE transfer_reference LIKE 'UAT%'",
                "SELECT COUNT(*) FROM invoices WHERE invoice_number LIKE 'UAT-INV-%'",
                "SELECT COUNT(*) FROM background_jobs WHERE dedupe_key LIKE 'ee5eed%'",
        };
        for (String sql : textKeys) {
            Integer count = jdbc.queryForObject(sql, Integer.class);
            if (count != null && count > 0) leftovers.add(sql + " -> " + count);
        }
        return leftovers;
    }

    /** Hash over the deterministic columns of every synthetic row (encrypted PII uses random nonces and is excluded). */
    private String fingerprint() {
        return jdbc.queryForObject("""
                SELECT md5(string_agg(line, '|' ORDER BY line)) FROM (
                  SELECT concat_ws('/', id, full_name, email, status, created_at, email_verified_at, plan_code, plan_expires_at, listing_quota_remaining) AS line
                    FROM users WHERE id::text LIKE 'ee5eed%'
                  UNION ALL SELECT concat_ws('/', id, owner_id, status, slug, created_at, updated_at, public_revision_id, is_verified_owner,
                                            availability_confirmed_at, expires_at, source) FROM listings WHERE id::text LIKE 'ee5eed%'
                  UNION ALL SELECT concat_ws('/', id, listing_id, revision_number, status, title, price_vnd, area_m2, public_latitude, public_longitude,
                                            created_at, submitted_at, moderated_at, legal_status_code, furnishing, monthly_service_fee_vnd, deposit_vnd, project_id)
                    FROM listing_revisions WHERE listing_id::text LIKE 'ee5eed%'
                  UNION ALL SELECT concat_ws('/', id, revision_id, media_url, sort_order, created_at) FROM listing_media WHERE id::text LIKE 'ee5eed%'
                  UNION ALL SELECT concat_ws('/', id, listing_id, requester_id, status, request_type, note, created_at) FROM leads WHERE id::text LIKE 'ee5eed%'
                  UNION ALL SELECT concat_ws('/', id, user_id, status, created_at, verified_at, expires_at) FROM user_kyc_profiles WHERE id::text LIKE 'ee5eed%'
                  UNION ALL SELECT concat_ws('/', id, listing_id, status, created_at, verified_at, expires_at, revoked_at) FROM listing_verifications WHERE id::text LIKE 'ee5eed%'
                  UNION ALL SELECT concat_ws('/', id, listing_id, status, severity, created_at, resolved_at) FROM listing_reports WHERE id::text LIKE 'ee5eed%'
                  UNION ALL SELECT concat_ws('/', id, slug, status, created_at, updated_at) FROM projects WHERE id::text LIKE 'ee5eed%'
                  UNION ALL SELECT concat_ws('/', id, slug, status, published_revision_id, created_at, updated_at) FROM cms_articles WHERE id::text LIKE 'ee5eed%'
                  UNION ALL SELECT concat_ws('/', id, user_id, status, amount_vnd, created_at, user_reported_at, reviewed_at) FROM package_orders WHERE id::text LIKE 'ee5eed%'
                ) rows
                """, String.class);
    }

    private UatDataSeeder seeder(String mode, String clock, String password) {
        return new UatDataSeeder(jdbc, pii, transaction, context, mode, false, List.of("nobody-" + UUID.randomUUID() + "@example.invalid"),
                "http://127.0.0.1:9", "s0be-unused", clock, "test", password);
    }

    private static UUID syntheticUser(int n) {
        return UUID.fromString(String.format("ee5eed01-0000-4000-8000-%012x", n));
    }
}
