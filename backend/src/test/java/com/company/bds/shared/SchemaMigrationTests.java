package com.company.bds.shared;

import com.company.bds.testsupport.BdsTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upgrades a database that is at the production version (V026) and holds representative legacy rows to the latest
 * schema, then checks every backfill of the shared schema (V027–V029) and that re-running is a no-op.
 */
class SchemaMigrationTests {
    private static final UUID OWNER = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID MULTI_ROLE = UUID.fromString("c0000000-0000-4000-8000-000000000002");
    private static final UUID ACTIVE = UUID.fromString("c1000000-0000-4000-8000-000000000001");
    private static final UUID PAUSED = UUID.fromString("c1000000-0000-4000-8000-000000000002");
    private static final UUID DRAFT = UUID.fromString("c1000000-0000-4000-8000-000000000003");
    private static final Instant ACTIVE_UPDATED = Instant.parse("2026-05-01T10:00:00Z");

    private BdsTestDatabase.Database database;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createLegacyDatabase() {
        database = BdsTestDatabase.createScratch("mig");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(database.url(), database.username(), database.password());
        jdbc = new JdbcTemplate(dataSource);
        flyway("26").migrate();
    }

    @AfterEach
    void dropDatabase() {
        BdsTestDatabase.drop(database);
    }

    @Test
    void backfillsSharedSchemaFromLegacyRowsAndIsRepeatable() {
        seedLegacyRows();

        // Up to the shared-schema migrations of S0-BE (later streams add their own versions after V029).
        var result = flyway("29").migrate();
        assertThat(result.migrationsExecuted).isEqualTo(3);
        assertThat(result.targetSchemaVersion).endsWith("29");

        // §2.1 legal code from free text; generated price period.
        Map<Integer, String> expectedCodes = Map.of(1, "RED_BOOK", 2, "PINK_BOOK", 3, "SALE_CONTRACT", 4, "SALE_CONTRACT",
                5, "PENDING_CERTIFICATE", 6, "OTHER", 9, "PENDING_CERTIFICATE", 10, "RED_BOOK");
        jdbc.query("SELECT revision_number, legal_status_code, purpose, price_period FROM listing_revisions WHERE listing_id=? ORDER BY 1",
                rs -> {
                    int number = rs.getInt(1);
                    assertThat(rs.getString(2)).as("legal code of revision %d", number).isEqualTo(expectedCodes.get(number));
                    assertThat(rs.getString(4)).as("price period of revision %d", number)
                            .isEqualTo("RENT".equals(rs.getString(3)) ? "MONTH" : null);
                }, ACTIVE);
        jdbc.update("""
                INSERT INTO listing_revisions(id,listing_id,revision_number,status,title,purpose,property_type,price_vnd,area_m2,
                                              monthly_service_fee_vnd,deposit_vnd,furnishing)
                VALUES (?,?,11,'DRAFT','Cho thuê sau nâng cấp','RENT','APARTMENT',14500000,62,1200000,29000000,'FULL')
                """, UUID.randomUUID(), ACTIVE);
        assertThat(jdbc.queryForObject("SELECT price_period FROM listing_revisions WHERE listing_id=? AND revision_number=11", String.class, ACTIVE))
                .isEqualTo("MONTH");
        assertThatThrownBy(() -> jdbc.update("UPDATE listing_revisions SET furnishing='LUXURY' WHERE listing_id=?", ACTIVE))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE listing_revisions SET deposit_vnd=-1 WHERE listing_id=?", ACTIVE))
                .isInstanceOf(DataIntegrityViolationException.class);

        // §2.2 lifecycle columns.
        assertThat(jdbc.queryForObject("SELECT availability_confirmed_at FROM listings WHERE id=?", Timestamp.class, ACTIVE).toInstant())
                .isEqualTo(ACTIVE_UPDATED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE id IN (?,?) AND availability_confirmed_at IS NULL",
                Integer.class, PAUSED, DRAFT)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT DISTINCT source FROM listings", String.class)).containsExactly("DIRECT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE expires_at IS NOT NULL OR property_asset_id IS NOT NULL", Integer.class))
                .isZero();
        assertThatThrownBy(() -> jdbc.update("UPDATE listings SET source='SCRAPED' WHERE id=?", DRAFT))
                .isInstanceOf(DataIntegrityViolationException.class);

        // §2.3 trust validity backfills.
        assertThat(instant("SELECT expires_at FROM user_kyc_profiles WHERE full_name='KYC đã duyệt'"))
                .isEqualTo(Instant.parse("2028-01-15T08:00:00Z"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_kyc_profiles WHERE full_name<>'KYC đã duyệt' AND expires_at IS NOT NULL",
                Integer.class)).isZero();
        assertThat(instant("SELECT expires_at FROM listing_verifications WHERE status='VERIFIED_OWNER'"))
                .isEqualTo(Instant.parse("2026-03-01T00:00:00Z").plusSeconds(180L * 24 * 3600));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing_verifications WHERE status<>'VERIFIED_OWNER' AND expires_at IS NOT NULL",
                Integer.class)).isZero();

        // §2.4 leads: new status, optimistic-lock version, last change = creation for legacy rows.
        jdbc.query("SELECT version, updated_at, created_at, first_response_at FROM leads", rs -> {
            assertThat(rs.getLong(1)).isZero();
            assertThat(rs.getTimestamp(2)).isEqualTo(rs.getTimestamp(3));
            assertThat(rs.getTimestamp(4)).isNull();
        });
        jdbc.update("UPDATE leads SET status='WITHDRAWN', qualification='QUALIFIED' WHERE status='NEW'");
        assertThatThrownBy(() -> jdbc.update("UPDATE leads SET status='ARCHIVED'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE leads SET qualification='MAYBE'"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // §2.5 role values are validated because every legacy role is known; OWNER is accepted.
        assertThat(jdbc.queryForObject("SELECT convalidated FROM pg_constraint WHERE conname='chk_user_roles_role'", Boolean.class)).isTrue();
        jdbc.update("UPDATE user_roles SET role='OWNER' WHERE user_id=? AND role='USER'", MULTI_ROLE);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, 'SUPERUSER')", OWNER))
                .isInstanceOf(DataIntegrityViolationException.class);

        // §2.6 infrastructure objects exist.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name IN ('background_jobs','scheduled_task_locks','analytics_events')
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_proc WHERE proname='bds_enqueue_job'", Integer.class)).isEqualTo(1);

        assertThat(flyway("29").migrate().migrationsExecuted).isZero();
        // The later migrations (S2 read model, S4 admin, …) also apply on top of these legacy rows; a second run changes nothing.
        assertThat(flyway(null).migrate().success).isTrue();
        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
    }

    @Test
    void unknownLegacyRoleDoesNotBlockTheDeploy() {
        insertUser(OWNER, "SUPPORT");

        flyway(null).migrate();

        assertThat(jdbc.queryForObject("SELECT convalidated FROM pg_constraint WHERE conname='chk_user_roles_role'", Boolean.class)).isFalse();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, 'SUPERUSER')", OWNER))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void seedLegacyRows() {
        insertUser(OWNER, "BROKER");
        insertUser(MULTI_ROLE, "USER");
        jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, 'BROKER')", MULTI_ROLE);
        insertListing(ACTIVE, "ACTIVE", ACTIVE_UPDATED);
        insertListing(PAUSED, "PAUSED", Instant.parse("2026-04-01T00:00:00Z"));
        insertListing(DRAFT, "DRAFT", Instant.parse("2026-04-02T00:00:00Z"));
        String[] legal = {"Sổ đỏ chính chủ", "SỔ HỒNG LÂU DÀI", "Hợp đồng mua bán", "HĐMB công chứng", "Đang chờ sổ hồng",
                "Giấy tay", "   ", null, "chờ cấp sổ", "so do"};
        for (int i = 0; i < legal.length; i++) {
            jdbc.update("""
                    INSERT INTO listing_revisions(id,listing_id,revision_number,status,title,purpose,property_type,price_vnd,area_m2,legal_status)
                    VALUES (?,?,?,'APPROVED','Tin cũ',?,'APARTMENT',?,70,?)
                    """, UUID.randomUUID(), ACTIVE, i + 1, i % 2 == 0 ? "SALE" : "RENT", i % 2 == 0 ? 3_950_000_000L : 14_500_000L, legal[i]);
        }
        jdbc.update("""
                INSERT INTO user_kyc_profiles(id,user_id,id_number_encrypted,id_number_lookup_hash,full_name,status,verified_at) VALUES
                 (?,?,'v1:x','h1','KYC đã duyệt','VERIFIED','2026-01-15T08:00:00Z'),
                 (?,?,'v1:x','h2','KYC chờ duyệt','PENDING',NULL),
                 (?,?,'v1:x','h3','KYC thiếu ngày duyệt','VERIFIED',NULL)
                """, UUID.randomUUID(), OWNER, UUID.randomUUID(), MULTI_ROLE, UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("""
                INSERT INTO listing_verifications(id,listing_id,verification_type,owner_name_on_doc,status,verified_at) VALUES
                 (?,?,'CERTIFICATE_OF_OWNERSHIP','Chủ nhà','VERIFIED_OWNER','2026-03-01T00:00:00Z'),
                 (?,?,'CERTIFICATE_OF_OWNERSHIP','Chủ nhà','PENDING',NULL),
                 (?,?,'POWER_OF_ATTORNEY','Chủ nhà','REJECTED','2026-03-02T00:00:00Z')
                """, UUID.randomUUID(), ACTIVE, UUID.randomUUID(), PAUSED, UUID.randomUUID(), DRAFT);
        jdbc.update("""
                INSERT INTO leads(id,listing_id,full_name,phone_encrypted,phone_lookup_hash,status,created_at) VALUES
                 (?,?,'Khách 1','v1:x','p1','NEW','2026-04-01T09:00:00Z'),
                 (?,?,'Khách 2','v1:x','p2','CONTACTED','2026-04-02T09:00:00Z')
                """, UUID.randomUUID(), ACTIVE, UUID.randomUUID(), ACTIVE);
    }

    private void insertUser(UUID id, String role) {
        jdbc.update("INSERT INTO users(id,phone_lookup_hash,phone_encrypted,full_name,status) VALUES (?,?,'x','Người dùng cũ','ACTIVE')",
                id, "legacy-" + id);
        jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, ?)", id, role);
    }

    private void insertListing(UUID id, String status, Instant updatedAt) {
        jdbc.update("INSERT INTO listings(id,owner_id,status,slug,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                id, OWNER, status, "legacy-" + id, Timestamp.from(updatedAt.minusSeconds(86_400)), Timestamp.from(updatedAt));
    }

    private Instant instant(String sql) {
        return jdbc.queryForObject(sql, Timestamp.class).toInstant();
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(database.url(), database.username(), database.password())
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
