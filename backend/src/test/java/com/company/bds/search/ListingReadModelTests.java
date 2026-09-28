package com.company.bds.search;

import com.company.bds.search.domain.VietnameseNormalizer;
import com.company.bds.search.infrastructure.indexing.TrustExpiryReindexTask;
import com.company.bds.shared.jobs.JobWorker;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * listing_public_read and its triggers (contract §9, D-01/D-02): what enqueues which job, what a refresh stores, the
 * trust-expiry task, and the Java/SQL normalisation parity (D-08).
 */
@BdsIntegrationTest
class ListingReadModelTests {
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired JobWorker worker;
    @Autowired TrustExpiryReindexTask trustTask;
    SearchFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new SearchFixtures(jdbc, data);
        worker.drain("search-index");
    }

    private Map<String, Object> row(UUID listingId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM listing_public_read WHERE listing_id = ?", listingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<String> pendingKeys() {
        return jdbc.queryForList("""
                SELECT dedupe_key FROM background_jobs WHERE queue = 'search-index' AND completed_at IS NULL AND dead_lettered_at IS NULL
                """, String.class);
    }

    @Test
    void onlyActiveListingsWithAnApprovedPublicRevisionAndAnActiveSellerArePublic() {
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestListing active = data.listing(seller.id()).create();
        TestData.TestListing draft = data.listing(seller.id()).status("DRAFT").create();
        TestData.TestListing pending = data.listing(seller.id()).status("PENDING_REVIEW").create();
        TestData.TestListing expired = data.listing(seller.id()).status("EXPIRED").create();
        assertThat(row(active.id())).isNotNull();
        assertThat(row(draft.id())).isNull();
        assertThat(row(pending.id())).isNull();
        assertThat(row(expired.id())).isNull();
        Map<String, Object> stored = row(active.id());
        assertThat(stored.get("seller_role")).isEqualTo("BROKER");
        assertThat(stored.get("district_name")).isEqualTo("Cầu Giấy");
        assertThat((String) stored.get("search_text")).contains("cau giay").contains("ha noi");
        assertThat(stored.get("image_count")).isEqualTo(1);
    }

    @Test
    void everyRefreshGetsAStrictlyHigherVersion() {
        TestData.TestUser seller = fixtures.seller("BROKER");
        TestData.TestListing listing = data.listing(seller.id()).create();
        long v1 = (Long) row(listing.id()).get("row_version");
        fixtures.refresh(listing.id());
        long v2 = (Long) row(listing.id()).get("row_version");
        jdbc.update("UPDATE listings SET updated_at = now() WHERE id = ?", listing.id());
        long v3 = (Long) row(listing.id()).get("row_version");
        assertThat(v2).isGreaterThan(v1);
        assertThat(v3).isGreaterThan(v2);
    }

    @Test
    void sourceTablesEnqueueTheRightJobsAndTheWorkerAppliesThem() {
        TestData.TestUser seller = fixtures.seller("OWNER");
        TestData.TestListing listing = data.listing(seller.id()).create();
        worker.drain("search-index");

        jdbc.update("UPDATE users SET full_name = 'Tên mới người bán' WHERE id = ?", seller.id());
        assertThat(pendingKeys()).contains("owner:" + seller.id());
        worker.drain("search-index"); // owner fan-out, then the listing job it enqueued
        assertThat(row(listing.id()).get("seller_name")).isEqualTo("Tên mới người bán");

        fixtures.kyc(seller.id(), "VERIFIED", Instant.now(), Instant.now().plus(Duration.ofDays(730)));
        assertThat(pendingKeys()).contains("owner:" + seller.id());
        worker.drain("search-index");
        assertThat(row(listing.id()).get("identity_status")).isEqualTo("VERIFIED");

        jdbc.update("INSERT INTO listing_media(id,revision_id,media_url,is_primary,sort_order,created_at) VALUES (?,?,?,FALSE,5,now())",
                UUID.randomUUID(), listing.publicRevisionId(), "https://images.unsplash.com/photo-extra");
        assertThat(pendingKeys()).contains(listing.id().toString());
        worker.drain("search-index");
        assertThat(row(listing.id()).get("image_count")).isEqualTo(2);

        jdbc.update("""
                INSERT INTO listing_verifications(id,listing_id,verification_type,owner_name_on_doc,status,created_at,verified_at,expires_at)
                VALUES (?,?,'CERTIFICATE_OF_OWNERSHIP','Chủ',  'VERIFIED_OWNER', now(), now(), now() + interval '180 days')
                """, UUID.randomUUID(), listing.id());
        worker.drain("search-index");
        assertThat(row(listing.id()).get("ownership_status")).isEqualTo("VERIFIED");

        UUID project = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO projects(id,name,slug,developer_name,province_code,district_code,address,legal_license_number)
                VALUES (?,?,?,?,'01','005','Cầu Giấy','GP-TEST')
                """, project, "Dự án thử", "du-an-thu-" + project, "Chủ đầu tư thử");
        fixtures.revise(listing, "project_id = ?", project);
        worker.drain("search-index");
        jdbc.update("UPDATE projects SET name = 'Dự án đổi tên' WHERE id = ?", project);
        assertThat(pendingKeys()).contains("project:" + project);
        worker.drain("search-index");
        assertThat(row(listing.id()).get("project_name")).isEqualTo("Dự án đổi tên");
        assertThat((String) row(listing.id()).get("search_text")).contains("du an doi ten");

        jdbc.update("UPDATE user_roles SET role = 'BROKER' WHERE user_id = ?", seller.id());
        worker.drain("search-index");
        assertThat(row(listing.id()).get("seller_role")).isEqualTo("BROKER");

        jdbc.update("UPDATE listing_revisions SET status = 'DRAFT' WHERE listing_id = ? AND status <> 'APPROVED'", listing.id());
        assertThat(pendingKeys()).as("draft edits do not reindex").doesNotContain(listing.id().toString());
    }

    @Test
    void trustExpiryTaskReindexesListingsWhoseCheckExpired() {
        TestData.TestUser seller = fixtures.seller("OWNER");
        TestData.TestListing listing = data.listing(seller.id()).create();
        fixtures.ownership(listing.id(), "VERIFIED_OWNER", Instant.now(), Instant.now().plus(Duration.ofDays(1)));
        worker.drain("search-index");
        assertThat(row(listing.id()).get("trust_expires_at")).isNotNull();
        // the day passes: the check is now expired in the source table, the stored status still says VERIFIED
        jdbc.update("ALTER TABLE listing_verifications DISABLE TRIGGER trg_lpr_verification");
        try {
            jdbc.update("UPDATE listing_verifications SET expires_at = now() - interval '1 minute' WHERE listing_id = ?", listing.id());
        } finally {
            jdbc.update("ALTER TABLE listing_verifications ENABLE TRIGGER trg_lpr_verification");
        }
        jdbc.update("UPDATE listing_public_read SET trust_expires_at = now() - interval '1 minute' WHERE listing_id = ?", listing.id());
        assertThat(pendingKeys()).doesNotContain(listing.id().toString());
        assertThat(trustTask.runOnce()).isGreaterThanOrEqualTo(1);
        assertThat(pendingKeys()).contains(listing.id().toString());
        worker.drain("search-index");
        Map<String, Object> refreshed = row(listing.id());
        assertThat(refreshed.get("ownership_status")).isEqualTo("EXPIRED");
        assertThat(refreshed.get("trust_expires_at")).isNull();
    }

    @Test
    void javaAndSqlNormaliseVietnameseIdentically() {
        List<String> samples = List.of("Căn hộ Cầu Giấy", "ĐỐNG ĐA", "Hoàng Mai – Tam Trinh", "Nhà 3PN, 2WC; 45m²",
                "Tây Hồ   view hồ", Normalizer.normalize("Hoà Bình Tuyết Ứng Hòa", Normalizer.Form.NFD),
                "Mỹ Đức, Chương Mỹ", "ỹ ữ ự ử ừ ứ ợ ỡ ở ờ ớ ộ ỗ ổ ồ ố ọ ỏ ị ỉ ệ ễ ể ề ế ẹ ẽ ẻ ặ ẵ ẳ ằ ắ ậ ẫ ẩ ầ ấ ạ ả",
                "giá 2.5 tỷ (thương lượng)", "  ", "Q.Cầu Giấy");
        for (String sample : samples) {
            String sql = jdbc.queryForObject("SELECT bds_search_normalize(?)", String.class, sample);
            assertThat(sql).as("normalise(" + sample + ")").isEqualTo(VietnameseNormalizer.normalize(sample));
        }
    }
}
