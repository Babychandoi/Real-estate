package com.company.bds.shared.uat;

import com.company.bds.shared.security.PiiProtectionService;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F01.2: a fixed clock gives identical fixtures; purge leaves no synthetic row behind and never touches real rows. */
@BdsIntegrationTest
class UatDataSeederTests {
    private static final String CLOCK = "2026-09-01T03:00:00Z";
    private static final Instant NOW = Instant.parse(CLOCK);

    @Autowired JdbcTemplate jdbc;
    @Autowired PiiProtectionService pii;
    @Autowired TransactionTemplate transaction;
    @Autowired ApplicationContext context;
    @Autowired TestData data;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

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
    void purgeRemovesWhatSyntheticAccountsCreatedThroughTheApiAndKeepsRealRowsThatMerelyLookSimilar() throws Exception {
        UUID lookalike = UUID.fromString("ee5eed01-9c4b-4d2e-8f00-" + UUID.randomUUID().toString().substring(24));
        jdbc.update("INSERT INTO users(id,phone_lookup_hash,phone_encrypted,full_name,email,status) VALUES (?,?,'NOT_PROVIDED','Người thật','"
                + "real-" + lookalike + "@example.test','ACTIVE')", lookalike, "lookalike-" + lookalike);
        jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, 'USER')", lookalike);
        TestData.TestUser realOwner = data.user().role("BROKER").verifiedKyc().create();
        TestData.TestListing realListing = data.listing(realOwner.id()).create();
        jdbc.update("""
                INSERT INTO bank_settings(singleton_id,bank_bin,bank_name,account_number,account_name)
                VALUES (1,'970436','Ngân hàng kiểm thử','0123456789','CONG TY KIEM THU') ON CONFLICT (singleton_id) DO NOTHING
                """);
        UatDataSeeder seeder = seeder("seed", CLOCK, "");
        String ownerBearer;
        String createdListing;
        String createdOrder;
        String createdLead;
        seeder.seedNow();
        try {
            ownerBearer = "Bearer " + data.sessionFor(syntheticUser(15)); // synthetic OWNER, KYC verified
            String buyerBearer = "Bearer " + data.sessionFor(syntheticUser(6)); // synthetic USER, KYC verified
            createdListing = json.readTree(mockMvc.perform(post("/api/v1/listings").header("Authorization", ownerBearer)
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"purpose":"SALE","propertyType":"HOUSE","title":"Nhà chủ nhà tự đăng khi kiểm thử",
                                     "priceVnd":2500000000,"areaM2":60,"description":"Tạo qua API bởi tài khoản mẫu.","addressSummary":"Hà Nội"}
                                    """))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("listingId").asText();
            createdOrder = json.readTree(mockMvc.perform(post("/api/v1/billing/orders").header("Authorization", ownerBearer)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"planCode\":\"STANDARD\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();
            mockMvc.perform(post("/api/v1/billing/orders/" + createdOrder + "/reported").header("Authorization", ownerBearer))
                    .andExpect(status().isOk());
            createdLead = json.readTree(mockMvc.perform(post("/api/v1/public/leads").header("Authorization", buyerBearer)
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"listingId":"%s","fullName":"Người mua mẫu","phone":"0987111222","consentPolicy":true}
                                    """.formatted(realListing.id())))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("leadId").asText();
            mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"uat.phan.thanh.hai@example.invalid\"}")).andExpect(status().isAccepted());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE user_id = ?", Integer.class, syntheticUser(15)))
                    .isPositive();
        } finally {
            seeder.purgeNow();
        }

        assertThat(syntheticRowsLeft()).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM listings WHERE id = CAST(? AS uuid)", createdListing)).isZero();
        assertThat(count("SELECT COUNT(*) FROM package_orders WHERE id = CAST(? AS uuid)", createdOrder)).isZero();
        assertThat(count("SELECT COUNT(*) FROM leads WHERE id = CAST(? AS uuid)", createdLead)).as("lead of a synthetic buyer on a real listing").isZero();
        assertThat(count("SELECT COUNT(*) FROM auth_sessions WHERE token_hash = ?", com.company.bds.iam.application.AuthService.sha256(ownerBearer.substring(7))))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM users WHERE id = CAST(? AS uuid)", lookalike.toString())).as("a real id with the same prefix").isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM listings WHERE id = CAST(? AS uuid) AND status = 'ACTIVE'", realListing.id().toString())).isEqualTo(1);

        seeder.seedNow(); // re-seeding after API activity must not trip over a foreign key either
        seeder.purgeNow();
        assertThat(syntheticRowsLeft()).isEmpty();
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

    @Test
    void listedRealAccountsWithoutKycGetASyntheticVerifiedProfileThatPurgeRemovesAndExistingProfilesAreKept() {
        TestData.TestUser broker = data.user().role("BROKER").create();
        TestData.TestUser pending = data.user().role("BROKER").kyc("PENDING").create();
        String unlisted = "unlisted-" + UUID.randomUUID() + "@example.invalid";
        UatDataSeeder seeder = new UatDataSeeder(jdbc, pii, transaction, context, "seed", false,
                List.of(broker.email(), pending.email()), "http://127.0.0.1:9", "s0be-unused", CLOCK, "test", "");
        seeder.setKycVerifiedAccounts(List.of(broker.email().toUpperCase(), pending.email(), unlisted));
        try {
            seeder.seedNow();
            assertThat(jdbc.queryForObject("SELECT status FROM user_kyc_profiles WHERE user_id = ?", String.class, broker.id()))
                    .isEqualTo("VERIFIED");
            assertThat(jdbc.queryForObject("SELECT id::text FROM user_kyc_profiles WHERE user_id = ?", String.class, broker.id()))
                    .startsWith("ee5eed07");
            assertThat(jdbc.queryForObject("SELECT status FROM user_kyc_profiles WHERE user_id = ?", String.class, pending.id()))
                    .as("an existing profile is never changed").isEqualTo("PENDING");
            // The verified real broker gets the full listing lifecycle like any real seller.
            assertThat(count("SELECT COUNT(*) FROM listings WHERE owner_id = ?", broker.id())).isGreaterThanOrEqualTo(12);
        } finally {
            seeder.purgeNow();
        }
        assertThat(count("SELECT COUNT(*) FROM user_kyc_profiles WHERE user_id = ?", broker.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM user_kyc_profiles WHERE user_id = ?", pending.id())).isOne();
    }

    private void assertSeededData() {
        assertThat(count("SELECT COUNT(*) FROM listing_reports WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-' AND reporter_phone IS NOT NULL AND reporter_phone NOT LIKE 'v1:%'"))
                .as("seeded reporter phones are stored encrypted").isZero();
        assertThat(count("SELECT COUNT(*) FROM listing_reports WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-' AND reporter_phone LIKE 'v1:%'"))
                .isPositive();
        // Timestamps are relative to the clock: fake user #1 was created 20 days before it.
        assertThat(jdbc.queryForObject("SELECT created_at FROM users WHERE id = ?", Timestamp.class, syntheticUser(1)).toInstant())
                .isEqualTo(NOW.minus(Duration.ofDays(20)));
        assertThat(jdbc.queryForObject("SELECT MAX(created_at) FROM listings WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'", Timestamp.class).toInstant())
                .isBefore(NOW);

        // Two private owners (one verified) with their own listings.
        assertThat(jdbc.queryForList("""
                SELECT k.status FROM users u JOIN user_roles r ON r.user_id = u.id LEFT JOIN user_kyc_profiles k ON k.user_id = u.id
                WHERE u.id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-' AND r.role = 'OWNER' ORDER BY u.id
                """, String.class)).containsExactly("VERIFIED", "PENDING");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM listings l JOIN user_roles r ON r.user_id = l.owner_id WHERE l.id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-' AND r.role = 'OWNER'
                """, Integer.class)).isEqualTo(5);

        // New columns (contract §2.1–§2.3) are populated and satisfy their constraints.
        assertThat(jdbc.queryForList("SELECT DISTINCT source FROM listings WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'", String.class)).containsExactly("SEED");
        Map<String, Object> rent = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE r.deposit_vnd IS NULL) AS rent_without_deposit,
                       COUNT(*) FILTER (WHERE r.property_type = 'APARTMENT' AND r.monthly_service_fee_vnd IS NULL) AS apartment_without_fee,
                       COUNT(*) FILTER (WHERE r.price_period IS DISTINCT FROM 'MONTH') AS without_period,
                       COUNT(*) AS total
                FROM listing_revisions r WHERE r.listing_id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-' AND r.purpose = 'RENT'
                """);
        assertThat(rent).containsEntry("rent_without_deposit", 0L).containsEntry("apartment_without_fee", 0L).containsEntry("without_period", 0L);
        assertThat((Long) rent.get("total")).isPositive();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM listing_revisions WHERE listing_id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-' AND purpose = 'SALE'
                  AND (deposit_vnd IS NOT NULL OR monthly_service_fee_vnd IS NOT NULL OR price_period IS NOT NULL)
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForList("""
                SELECT DISTINCT legal_status || '=' || legal_status_code FROM listing_revisions
                WHERE listing_id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-' AND legal_status IS NOT NULL ORDER BY 1
                """, String.class)).containsExactlyInAnyOrder("Hợp đồng mua bán=SALE_CONTRACT", "Sổ hồng lâu dài=PINK_BOOK", "Sổ đỏ chính chủ=RED_BOOK");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM listing_revisions WHERE listing_id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-' AND project_id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                """, Integer.class)).as("apartments linked to seeded projects").isPositive();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM listings WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-' AND status = 'ACTIVE'
                  AND (availability_confirmed_at IS NULL OR availability_confirmed_at < created_at OR expires_at <= ?)
                """, Integer.class, Timestamp.from(NOW))).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM listings WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-' AND status = 'EXPIRED' AND (expires_at IS NULL OR expires_at > ?)
                """, Integer.class, Timestamp.from(NOW))).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM listings WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-' AND status = 'ACTIVE'
                  AND expires_at <= ?
                """, Integer.class, Timestamp.from(NOW.plus(Duration.ofDays(365))))).as("fixed-clock E2E listings stay visible for at least a year").isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_kyc_profiles WHERE user_id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-' AND status = 'VERIFIED'
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
            String sql = "SELECT COUNT(*) FROM " + column.get("table_name") + " WHERE " + column.get("column_name")
                    + "::text ~ '" + UatDataSeeder.SYNTHETIC_ID_PATTERN + "'";
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
                "SELECT COUNT(*) FROM background_jobs WHERE dedupe_key ~ 'ee5eed[0-9a-f]{2}-0000-4000-8000-'",
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
                    FROM users WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                  UNION ALL SELECT concat_ws('/', id, owner_id, status, slug, created_at, updated_at, public_revision_id, is_verified_owner,
                                            availability_confirmed_at, expires_at, source) FROM listings WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                  UNION ALL SELECT concat_ws('/', id, listing_id, revision_number, status, title, price_vnd, area_m2, public_latitude, public_longitude,
                                            created_at, submitted_at, moderated_at, legal_status_code, furnishing, monthly_service_fee_vnd, deposit_vnd, project_id)
                    FROM listing_revisions WHERE listing_id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                  UNION ALL SELECT concat_ws('/', id, revision_id, media_url, sort_order, created_at) FROM listing_media WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                  UNION ALL SELECT concat_ws('/', id, listing_id, requester_id, status, request_type, note, created_at, updated_at, first_response_at)
                    FROM leads WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                  UNION ALL SELECT concat_ws('/', id, user_id, status, created_at, verified_at, expires_at) FROM user_kyc_profiles WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                  UNION ALL SELECT concat_ws('/', id, listing_id, status, created_at, verified_at, expires_at, revoked_at) FROM listing_verifications WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                  UNION ALL SELECT concat_ws('/', id, listing_id, status, severity, created_at, resolved_at) FROM listing_reports WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                  UNION ALL SELECT concat_ws('/', id, slug, status, created_at, updated_at) FROM projects WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                  UNION ALL SELECT concat_ws('/', id, slug, status, published_revision_id, created_at, updated_at) FROM cms_articles WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                  UNION ALL SELECT concat_ws('/', id, user_id, status, amount_vnd, created_at, user_reported_at, reviewed_at) FROM package_orders WHERE id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                  UNION ALL SELECT concat_ws('/', user_id, first_response_minutes, reminder_enabled, daily_digest_enabled, updated_at)
                    FROM broker_sla_settings WHERE user_id::text ~ '^ee5eed[0-9a-f]{2}-0000-4000-8000-'
                ) rows
                """, String.class);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private UatDataSeeder seeder(String mode, String clock, String password) {
        return new UatDataSeeder(jdbc, pii, transaction, context, mode, false, List.of("nobody-" + UUID.randomUUID() + "@example.invalid"),
                "http://127.0.0.1:9", "s0be-unused", clock, "test", password);
    }

    private static UUID syntheticUser(int n) {
        return UUID.fromString(String.format("ee5eed01-0000-4000-8000-%012x", n));
    }
}
