package com.company.bds.moderation;

import com.company.bds.asset.DuplicateDetectionSweep;
import com.company.bds.moderation.application.service.RandomAuditService;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Moderation v2 on PostgreSQL: claims, real-actor decisions, bulk scope, concurrency, queue filters, random audit. */
@BdsIntegrationTest
class ModerationV2Tests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired ObjectMapper json;
    @Autowired RandomAuditService audit;
    @Autowired DuplicateDetectionSweep sweep;
    @Autowired MeterRegistry meters;

    @Test
    void claimProtectsAnItemAndTheDecisionRecordsTheRealModerator() throws Exception {
        Staff a = staff("MODERATOR", "Kiểm duyệt viên A");
        Staff b = staff("MODERATOR", "Kiểm duyệt viên B");
        TestData.TestListing listing = pending();

        postAs(a, "/api/v1/moderation/listings/" + listing.id() + "/claim", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value(true));
        postAs(b, "/api/v1/moderation/listings/" + listing.id() + "/claim", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_CONFLICT"));
        postAs(b, "/api/v1/moderation/listings/" + listing.id() + "/approve",
                Map.of("revisionId", listing.latestRevisionId(), "reasonCode", "MEETS_STANDARDS"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_CONFLICT"));
        postAs(a, "/api/v1/moderation/listings/" + listing.id() + "/approve",
                Map.of("revisionId", listing.latestRevisionId(), "reasonCode", "INVALID_CODE"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REASON"));

        postAs(a, "/api/v1/moderation/listings/" + listing.id() + "/approve",
                Map.of("revisionId", listing.latestRevisionId(), "reasonCode", "MEETS_STANDARDS", "note", "Ảnh rõ, giá hợp lý"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.listingStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.moderatorId").value(a.id().toString()));

        Map<String, Object> decision = jdbc.queryForMap("SELECT moderator_id, decision, reason_code, note FROM moderation_decisions WHERE listing_id = ?", listing.id());
        assertThat(decision.get("moderator_id")).isEqualTo(a.id());
        assertThat(decision).containsEntry("decision", "APPROVED").containsEntry("reason_code", "MEETS_STANDARDS")
                .containsEntry("note", "Ảnh rõ, giá hợp lý");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM moderation_decisions WHERE moderator_id = '00000000-0000-0000-0000-000000000099'::uuid", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM moderation_claims WHERE listing_id = ?", Integer.class, listing.id())).isZero();
        getAs(a, "/api/v1/moderation/listings/" + listing.id() + "/decisions").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].moderatorName").value("Kiểm duyệt viên A"))
                .andExpect(jsonPath("$[0].reasonCode").value("MEETS_STANDARDS"));
    }

    @Test
    void expiredClaimCanBeTakenOverAndReleaseFreesTheItem() throws Exception {
        Staff a = staff("MODERATOR", "A");
        Staff b = staff("ADMIN", "B");
        TestData.TestListing listing = pending();
        postAs(a, "/api/v1/moderation/listings/" + listing.id() + "/claim", null).andExpect(status().isOk());
        Timestamp expires = jdbc.queryForObject("SELECT expires_at FROM moderation_claims WHERE listing_id = ?", Timestamp.class, listing.id());
        assertThat(Duration.between(Instant.now(), expires.toInstant())).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(31));

        jdbc.update("UPDATE moderation_claims SET expires_at = now() - interval '1 second' WHERE listing_id = ?", listing.id());
        postAs(b, "/api/v1/moderation/listings/" + listing.id() + "/claim", null).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/moderation/listings/" + listing.id() + "/claim").header("Authorization", b.bearer()))
                .andExpect(status().isNoContent());
        postAs(a, "/api/v1/moderation/listings/" + listing.id() + "/claim", null).andExpect(status().isOk());
    }

    @Test
    void bulkActsOnlyOnTheActorsClaimsAndReportsEveryItem() throws Exception {
        Staff a = staff("MODERATOR", "A");
        Staff b = staff("MODERATOR", "B");
        TestData.TestListing mine1 = pending();
        TestData.TestListing mine2 = pending();
        TestData.TestListing unclaimed = pending();
        TestData.TestListing othersClaim = pending();
        postAs(a, "/api/v1/moderation/listings/" + mine1.id() + "/claim", null).andExpect(status().isOk());
        postAs(a, "/api/v1/moderation/listings/" + mine2.id() + "/claim", null).andExpect(status().isOk());
        postAs(b, "/api/v1/moderation/listings/" + othersClaim.id() + "/claim", null).andExpect(status().isOk());

        List<Map<String, Object>> items = new ArrayList<>();
        for (TestData.TestListing l : List.of(mine1, mine2, unclaimed, othersClaim)) {
            items.add(Map.of("listingId", l.id(), "revisionId", l.latestRevisionId()));
        }
        JsonNode result = body(postAs(a, "/api/v1/moderation/bulk",
                Map.of("action", "REJECT", "reasonCode", "INCOMPLETE_INFO", "note", "Thiếu địa chỉ", "items", items))
                .andExpect(status().isOk()));
        Map<String, String> outcomes = new java.util.HashMap<>();
        result.get("results").forEach(r -> outcomes.put(r.get("listingId").asText(), r.get("outcome").asText()));
        assertThat(outcomes).containsEntry(mine1.id().toString(), "REJECTED").containsEntry(mine2.id().toString(), "REJECTED")
                .containsEntry(unclaimed.id().toString(), "NOT_CLAIMED").containsEntry(othersClaim.id().toString(), "CLAIM_CONFLICT");
        assertThat(listingStatus(unclaimed)).isEqualTo("PENDING_REVIEW");
        assertThat(listingStatus(othersClaim)).isEqualTo("PENDING_REVIEW");
        assertThat(listingStatus(mine1)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT bulk_batch_id) FROM moderation_decisions WHERE listing_id IN (?,?)",
                Integer.class, mine1.id(), mine2.id())).isEqualTo(1);

        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i < 51; i++) tooMany.add(Map.of("listingId", UUID.randomUUID(), "revisionId", UUID.randomUUID()));
        postAs(a, "/api/v1/moderation/bulk", Map.of("action", "APPROVE", "reasonCode", "MEETS_STANDARDS", "items", tooMany))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BULK_TOO_LARGE"));
    }

    @Test
    void concurrentApprovalsOfOneRevisionHaveExactlyOneEffect() throws Exception {
        Staff a = staff("MODERATOR", "A");
        Staff b = staff("ADMIN", "B");
        TestData.TestListing listing = pending();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (Staff s : List.of(a, b)) {
            results.add(pool.submit(() -> {
                start.await();
                return postAs(s, "/api/v1/moderation/listings/" + listing.id() + "/approve",
                        Map.of("revisionId", listing.latestRevisionId(), "reasonCode", "MEETS_STANDARDS"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get());
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM moderation_decisions WHERE revision_id = ?", Integer.class,
                listing.latestRevisionId())).isEqualTo(1);
        assertThat(listingStatus(listing)).isEqualTo("ACTIVE");
    }

    @Test
    void queueIsPagedStableAndFilteredBySubmissionKindAndSla() throws Exception {
        Staff a = staff("MODERATOR", "A");
        TestData.TestUser owner = data.user().role("BROKER").create();
        TestData.TestListing old = data.listing(owner.id()).status("PENDING_REVIEW").title("Tin chờ duyệt quá hạn SLA")
                .createdAt(Instant.now().minus(Duration.ofHours(30))).create();
        TestData.TestListing fresh = data.listing(owner.id()).status("PENDING_REVIEW").title("Tin chờ duyệt mới gửi").create();
        TestData.TestListing edited = data.listing(owner.id()).status("ACTIVE").title("Tin đang hiển thị có bản sửa").create();
        UUID editRevision = submitEdit(edited, 2_950_000_000L);

        JsonNode breached = body(getAs(a, "/api/v1/moderation/queue?filter=SLA_BREACH&size=100").andExpect(status().isOk()));
        assertThat(ids(breached)).contains(old.id().toString()).doesNotContain(fresh.id().toString());
        breached.get("items").forEach(i -> assertThat(i.get("slaBreached").asBoolean()).isTrue());

        JsonNode edits = body(getAs(a, "/api/v1/moderation/queue?filter=EDIT&size=100"));
        assertThat(ids(edits)).contains(edited.id().toString()).doesNotContain(fresh.id().toString());
        JsonNode firsts = body(getAs(a, "/api/v1/moderation/queue?filter=FIRST_SUBMISSION&size=100"));
        assertThat(ids(firsts)).contains(fresh.id().toString(), old.id().toString()).doesNotContain(edited.id().toString());

        JsonNode diff = body(getAs(a, "/api/v1/moderation/listings/" + edited.id() + "/diff"));
        assertThat(diff.get("currentRevisionId").asText()).isEqualTo(editRevision.toString());
        assertThat(diff.get("previousRevisionId").asText()).isEqualTo(edited.publicRevisionId().toString());
        assertThat(diff.get("changedCount").asInt()).isEqualTo(1);

        // Stable order across pages: oldest submission first, no duplicates between pages.
        JsonNode p0 = body(getAs(a, "/api/v1/moderation/queue?size=1&page=0"));
        JsonNode p1 = body(getAs(a, "/api/v1/moderation/queue?size=1&page=1"));
        assertThat(p0.get("total").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(p0.get("items").get(0).get("listingId").asText()).isNotEqualTo(p1.get("items").get(0).get("listingId").asText());
        getAs(a, "/api/v1/moderation/queue?size=1000").andExpect(jsonPath("$.size").value(100));
        getAs(a, "/api/v1/moderation/queue?filter=NOPE").andExpect(status().isBadRequest());
        assertThat(meters.find("bds.moderation.queue.size").gauge()).isNotNull();

        TestData.TestUser buyer = data.user().create();
        mvc.perform(get("/api/v1/moderation/queue").header("Authorization", "Bearer " + data.sessionFor(buyer.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void weeklyRandomAuditDrawsOnceAndRecordsTheSecondLook() throws Exception {
        Staff a = staff("MODERATOR", "A");
        TestData.TestListing listing = pending();
        postAs(a, "/api/v1/moderation/listings/" + listing.id() + "/approve",
                Map.of("revisionId", listing.latestRevisionId(), "reasonCode", "MEETS_STANDARDS")).andExpect(status().isOk());
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate week = LocalDate.of(2020, 1, 6).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        jdbc.update("UPDATE moderation_decisions SET created_at = ? WHERE listing_id = ?",
                Timestamp.from(week.plusDays(2).atStartOfDay(zone).toInstant()), listing.id());

        assertThat(audit.draw(week)).isEqualTo(1);
        assertThat(audit.draw(week)).as("a week is drawn once").isZero();
        UUID sample = jdbc.queryForObject("SELECT id FROM moderation_audit_samples WHERE week_start = ?", UUID.class, java.sql.Date.valueOf(week));
        postAs(a, "/api/v1/moderation/audit-samples/" + sample + "/review", Map.of("outcome", "FAILED", "reasonCode", "INCORRECT_PRICE", "note", "Giá thấp bất thường"))
                .andExpect(status().isNoContent());
        postAs(a, "/api/v1/moderation/audit-samples/" + sample + "/review", Map.of("outcome", "PASSED", "reasonCode", "MEETS_STANDARDS"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM moderation_decisions WHERE listing_id = ? AND decision = 'AUDIT_FAILED' AND moderator_id = ?",
                Integer.class, listing.id(), a.id())).isEqualTo(1);
    }

    @Test
    void duplicateCandidatesComeFromTheBlockOnlyAndShowInTheQueue() throws Exception {
        Staff a = staff("MODERATOR", "A");
        TestData.TestUser owner = data.user().role("BROKER").create();
        String district = "9" + (100 + Math.floorMod(UUID.randomUUID().hashCode(), 800));
        TestData.TestListing existing = data.listing(owner.id()).status("ACTIVE").district(district, "12 Ngõ Trạm, Hoàn Kiếm, Hà Nội")
                .title("Căn hộ 70m2 ngõ Trạm view phố").area("70.00").price(3_000_000_000L).create();
                // Existing public listings get their fingerprint from the approval path; emulate it for the seeded one.
        com.company.bds.asset.PropertyAssetService assets = assetsBean();
        assets.linkAssetOnApproval(existing.id(), existing.publicRevisionId());

        // Noise: many fingerprints in other blocks (other districts) that must never be compared.
        for (int i = 0; i < 300; i++) {
            TestData.TestListing other = data.listing(owner.id()).status("ACTIVE").district("8" + String.format("%02d", i % 90), "Địa chỉ khác " + i)
                    .area("70.00").price(3_000_000_000L).media(0).create();
            assets.refreshFingerprint(other.id(), other.publicRevisionId());
        }
        TestData.TestListing duplicate = data.listing(owner.id()).status("PENDING_REVIEW").district(district, "12 ngõ Trạm, Hoàn Kiếm, Hà Nội")
                .title("Căn hộ 70m2 ngõ Trạm view phố đẹp").area("71.00").price(3_050_000_000L).create();

        assertThat(sweep.runOnce()).isGreaterThanOrEqualTo(1);
        assets.refreshFingerprint(duplicate.id(), duplicate.latestRevisionId());
        com.company.bds.asset.PropertyAssetService.DetectionResult result = assets.detectCandidates(duplicate.id());
        assertThat(result.compared()).as("only the block is compared, not the 300 other listings").isEqualTo(1);

        JsonNode candidates = body(getAs(a, "/api/v1/moderation/listings/" + duplicate.id() + "/duplicates"));
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).get("otherListingId").asText()).isEqualTo(existing.id().toString());
        assertThat(candidates.get(0).get("reasons").toString()).contains("SAME_OWNER").contains("TEXT_SIMILARITY");
        assertThat(ids(body(getAs(a, "/api/v1/moderation/queue?filter=DUPLICATES&size=100")))).contains(duplicate.id().toString());

        postAs(a, "/api/v1/moderation/duplicates/" + candidates.get(0).get("id").asText(), Map.of("status", "DISMISSED", "note", "Khác tầng"))
                .andExpect(status().isNoContent());
        assets.detectCandidates(duplicate.id());
        assertThat(jdbc.queryForObject("SELECT status FROM listing_duplicate_candidates WHERE id = ?::uuid", String.class,
                candidates.get(0).get("id").asText())).as("a decided pair is not reopened").isEqualTo("DISMISSED");

        // Approval with the exact same fingerprint links the same property asset.
        TestData.TestListing sameAsset = data.listing(owner.id()).status("PENDING_REVIEW").district(district, "12 Ngõ Trạm, Hoàn Kiếm, Hà Nội")
                .area("70.20").price(2_900_000_000L).create();
        postAs(a, "/api/v1/moderation/listings/" + sameAsset.id() + "/approve",
                Map.of("revisionId", sameAsset.latestRevisionId(), "reasonCode", "MEETS_STANDARDS")).andExpect(status().isOk());
        UUID assetOfExisting = jdbc.queryForObject("SELECT property_asset_id FROM listings WHERE id = ?", UUID.class, existing.id());
        assertThat(assetOfExisting).isNotNull();
        assertThat(jdbc.queryForObject("SELECT property_asset_id FROM listings WHERE id = ?", UUID.class, sameAsset.id())).isEqualTo(assetOfExisting);
    }

    @Autowired com.company.bds.asset.PropertyAssetService propertyAssetService;

    private com.company.bds.asset.PropertyAssetService assetsBean() { return propertyAssetService; }

    // ------------------------------------------------------------------------------------------------ helpers

    record Staff(UUID id, String bearer) {}

    private Staff staff(String role, String name) {
        TestData.TestUser user = data.user().role(role).name(name).create();
        return new Staff(user.id(), "Bearer " + data.sessionFor(user.id()));
    }

    private TestData.TestListing pending() {
        TestData.TestUser owner = data.user().role("BROKER").create();
        return data.listing(owner.id()).status("PENDING_REVIEW").title("Căn hộ chờ kiểm duyệt tích hợp").create();
    }

    private UUID submitEdit(TestData.TestListing listing, long newPrice) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO listing_revisions(id,listing_id,revision_number,status,title,purpose,property_type,price_vnd,area_m2,description,
                    province_code,district_code,address_summary,created_at,submitted_at,bedrooms)
                SELECT ?, listing_id, revision_number + 1, 'SUBMITTED', title, purpose, property_type, ?, area_m2, description,
                    province_code, district_code, address_summary, now(), now(), bedrooms
                FROM listing_revisions WHERE id = ?
                """, id, newPrice, listing.publicRevisionId());
        jdbc.update("INSERT INTO listing_media(id,revision_id,media_url,is_primary,sort_order,created_at) SELECT gen_random_uuid(), ?, media_url, is_primary, sort_order, now() FROM listing_media WHERE revision_id = ?",
                id, listing.publicRevisionId());
        return id;
    }

    private String listingStatus(TestData.TestListing listing) {
        return jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, listing.id());
    }

    private ResultActions postAs(Staff s, String path, Object body) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization", s.bearer());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }

    private ResultActions getAs(Staff s, String path) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Authorization", s.bearer()));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(i -> ids.add(i.get("listingId").asText()));
        return ids;
    }
}
