package com.company.bds.verification;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.company.bds.verification.application.TrustExpiryTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Identity and ownership decisions: reason codes, decider, validity, revocation, evidence, expiry reminders. */
@BdsIntegrationTest
class TrustDecisionTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired ObjectMapper json;
    @Autowired TrustExpiryTask expiry;

    @Test
    void kycApprovalRecordsDeciderReasonAndTwentyFourMonthValidity() throws Exception {
        TestData.TestUser moderator = data.user().role("MODERATOR").name("Thẩm định viên").create();
        String staff = bearer(moderator);
        TestData.TestUser owner = data.user().role("OWNER").kyc("PENDING").create();
        UUID kycId = kycId(owner.id());

        perform(staff, post("/api/v1/kyc/" + kycId + "/approve", Map.of("reason", "ok", "reasonCode", "NAME_MISMATCH")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REASON"));
        perform(staff, post("/api/v1/kyc/" + kycId + "/approve", Map.of("reason", "CCCD rõ, khớp ảnh chân dung", "reasonCode", "DOCUMENTS_MATCH")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFIED"));
        Map<String, Object> row = jdbc.queryForMap("SELECT verified_at, expires_at, decided_by, decision_reason_code FROM user_kyc_profiles WHERE id = ?", kycId);
        Instant verified = ((Timestamp) row.get("verified_at")).toInstant();
        Instant expires = ((Timestamp) row.get("expires_at")).toInstant();
        assertThat(expires).isEqualTo(verified.atOffset(ZoneOffset.UTC).plusMonths(24).toInstant());
        assertThat(row.get("decided_by")).isEqualTo(moderator.id());
        assertThat(row.get("decision_reason_code")).isEqualTo("DOCUMENTS_MATCH");
        perform(staff, post("/api/v1/kyc/" + kycId + "/approve", null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRUST_STATE"));

        JsonNode mine = body(perform(bearer(owner), get("/api/v1/kyc/me/status")).andExpect(status().isOk()));
        assertThat(mine.get("status").asText()).isEqualTo("VERIFIED");
        assertThat(mine.get("canSubmit").asBoolean()).isFalse();
        assertThat(Instant.parse(mine.get("expiresAt").asText())).isEqualTo(expires);
        assertThat(mine.get("timeline").get(0).get("decision").asText()).isEqualTo("APPROVED");
        assertThat(mine.get("timeline").get(0).get("reasonLabel").asText()).isEqualTo("Giấy tờ rõ ràng, thông tin trùng khớp");
        assertThat(mine.get("timeline").get(0).get("actorName").isNull()).as("the user does not see who decided").isTrue();

        perform(staff, post("/api/v1/kyc/" + kycId + "/revoke", Map.of("reason", "Khiếu nại giả mạo"))).andExpect(status().isBadRequest());
        perform(staff, post("/api/v1/kyc/" + kycId + "/revoke", Map.of("reason", "Khiếu nại giả mạo", "reasonCode", "FRAUD_CONFIRMED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM user_kyc_profiles WHERE id = ?", Timestamp.class, kycId)).isNotNull();
        assertThat(jdbc.queryForList("SELECT decision FROM trust_decisions WHERE subject_id = ? ORDER BY created_at", String.class, kycId))
                .containsExactly("APPROVED", "REVOKED");
        perform(bearer(data.user().create()), post("/api/v1/kyc/" + kycId + "/revoke", Map.of("reason", "x", "reasonCode", "DISPUTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void kycRejectionShowsTheReasonAndAllowsResubmission() throws Exception {
        String staff = bearer(data.user().role("ADMIN").create());
        TestData.TestUser user = data.user().kyc("PENDING").create();
        UUID kycId = kycId(user.id());
        perform(staff, post("/api/v1/kyc/" + kycId + "/reject", Map.of("reason", "Mặt sau bị lóa", "reasonCode", "DOCUMENT_UNREADABLE")))
                .andExpect(status().isOk());
        JsonNode mine = body(perform(bearer(user), get("/api/v1/kyc/me/status")));
        assertThat(mine.get("status").asText()).isEqualTo("REJECTED");
        assertThat(mine.get("rejectionReason").asText()).isEqualTo("Ảnh giấy tờ mờ, bị che hoặc thiếu mặt: Mặt sau bị lóa");
        assertThat(mine.get("canSubmit").asBoolean()).isTrue();
        JsonNode none = body(perform(bearer(data.user().create()), get("/api/v1/kyc/me/status")));
        assertThat(none.get("status").asText()).isEqualTo("NOT_SUBMITTED");

        TestData.TestUser moderator = data.user().role("MODERATOR").kyc("PENDING").create();
        perform(bearer(moderator), post("/api/v1/kyc/" + kycId(moderator.id()) + "/approve", null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWN_DECISION"));
    }

    @Test
    void ownershipDecisionsSetValidityDeciderAndListingBadge() throws Exception {
        TestData.TestUser moderator = data.user().role("MODERATOR").name("Thẩm định viên B").create();
        String staff = bearer(moderator);
        TestData.TestUser owner = data.user().role("OWNER").name("Nguyễn Văn Chủ").verifiedKyc().create();
        TestData.TestListing listing = data.listing(owner.id()).title("Nhà phố có sổ hồng chính chủ").create();
        UUID verification = verification(listing.id(), kycId(owner.id()), "nguyen van chu", "BH 123456");

        JsonNode queue = body(perform(staff, get("/api/v1/verifications?status=PENDING&size=100")));
        JsonNode item = null;
        for (JsonNode v : queue) if (v.get("id").asText().equals(verification.toString())) item = v;
        assertThat(item).isNotNull();
        assertThat(item.get("listingTitle").asText()).isEqualTo("Nhà phố có sổ hồng chính chủ");

        JsonNode evidence = body(perform(staff, get("/api/v1/verifications/" + verification + "/evidence")).andExpect(status().isOk()));
        assertThat(evidence.get("comparisons").get(0).get("result").asText()).as("accents and case ignored").isEqualTo("MATCH");
        assertThat(evidence.get("comparisons").get(1).get("result").asText()).isEqualTo("UNIQUE");
        assertThat(evidence.get("identity").get("status").asText()).isEqualTo("VERIFIED");

        perform(staff, post("/api/v1/verifications/" + verification + "/approve", Map.of("verifierNote", "Sổ hồng khớp", "reasonCode", "DOCUMENTS_MATCH")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFIED_OWNER"))
                .andExpect(jsonPath("$.decidedByName").value("Thẩm định viên B"));
        Map<String, Object> row = jdbc.queryForMap("SELECT verified_at, expires_at, decided_by FROM listing_verifications WHERE id = ?", verification);
        assertThat(Duration.between(((Timestamp) row.get("verified_at")).toInstant(), ((Timestamp) row.get("expires_at")).toInstant())).isEqualTo(Duration.ofDays(180));
        assertThat(row.get("decided_by")).isEqualTo(moderator.id());
        assertThat(jdbc.queryForObject("SELECT is_verified_owner FROM listings WHERE id = ?", Boolean.class, listing.id())).isTrue();

        perform(staff, post("/api/v1/verifications/" + verification + "/revoke", Map.of("reason", "Tranh chấp thừa kế", "reasonCode", "DISPUTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
        assertThat(jdbc.queryForObject("SELECT is_verified_owner FROM listings WHERE id = ?", Boolean.class, listing.id())).isFalse();
        JsonNode history = body(perform(staff, get("/api/v1/verifications/" + verification + "/evidence"))).get("history");
        assertThat(history.get(0).get("decision").asText()).isEqualTo("REVOKED");
        assertThat(history.get(0).get("actorName").asText()).isEqualTo("Thẩm định viên B");
    }

    @Test
    void evidenceFlagsNameMismatchAndCertificateUsedByAnotherOwner() throws Exception {
        String staff = bearer(data.user().role("MODERATOR").create());
        TestData.TestUser owner = data.user().role("OWNER").name("Trần Thị Bình").verifiedKyc().create();
        TestData.TestUser other = data.user().role("OWNER").verifiedKyc().create();
        TestData.TestListing mine = data.listing(owner.id()).create();
        TestData.TestListing theirs = data.listing(other.id()).create();
        verification(theirs.id(), kycId(other.id()), "Người khác", "CT-999");
        UUID suspicious = verification(mine.id(), kycId(owner.id()), "Lê Văn Cường", "ct 999");
        JsonNode evidence = body(perform(staff, get("/api/v1/verifications/" + suspicious + "/evidence")));
        assertThat(evidence.get("comparisons").get(0).get("result").asText()).isEqualTo("MISMATCH");
        assertThat(evidence.get("comparisons").get(1).get("result").asText()).isEqualTo("USED_ELSEWHERE");
        perform(staff, post("/api/v1/verifications/" + suspicious + "/reject", Map.of("reason", "Tên không khớp", "reasonCode", "NAME_MISMATCH")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        // Ownership cannot be approved while the poster's identity is not verified.
        TestData.TestUser unverified = data.user().role("OWNER").kyc("PENDING").create();
        UUID pendingKyc = verification(data.listing(unverified.id()).create().id(), kycId(unverified.id()), "A", "X-1");
        perform(staff, post("/api/v1/verifications/" + pendingKyc + "/approve", Map.of())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KYC_NOT_VERIFIED"));
    }

    @Test
    void expiryRemindersAreSentOnceAndExpiredOwnershipLosesTheBadge() {
        TestData.TestUser owner = data.user().role("OWNER").verifiedKyc().create();
        UUID kycId = kycId(owner.id());
        jdbc.update("UPDATE user_kyc_profiles SET expires_at = now() + interval '10 days' WHERE id = ?", kycId);
        TestData.TestListing listing = data.listing(owner.id()).create();
        UUID expired = verification(listing.id(), kycId, "Chủ", "E-1");
        jdbc.update("UPDATE listing_verifications SET status = 'VERIFIED_OWNER', verified_at = now() - interval '181 days', expires_at = now() - interval '1 day' WHERE id = ?", expired);
        jdbc.update("UPDATE listings SET is_verified_owner = TRUE WHERE id = ?", listing.id());

        expiry.runOnce();
        expiry.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notifications WHERE user_id = ? AND type = 'KYC_EXPIRING'", Integer.class, owner.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM background_jobs WHERE dedupe_key LIKE ?", Integer.class, "kyc-expiring:" + kycId + ":%")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_verified_owner FROM listings WHERE id = ?", Boolean.class, listing.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trust_decisions WHERE subject_id = ? AND decision = 'EXPIRED'", Integer.class, expired)).isEqualTo(1);
        Timestamp noticeExpiry = jdbc.queryForObject("SELECT expires_at FROM trust_expiry_notices WHERE subject_id = ?", Timestamp.class, kycId);
        assertThat(noticeExpiry.toInstant()).isCloseTo(Instant.now().plus(Duration.ofDays(10)), within(Duration.ofMinutes(5)));
    }

    @Test
    void ownershipIsNotApprovedOnAnExpiredOrRevokedIdentity() throws Exception {
        String staff = bearer(data.user().role("MODERATOR").create());
        TestData.TestUser owner = data.user().role("OWNER").verifiedKyc().create();
        UUID kycId = kycId(owner.id());
        jdbc.update("UPDATE user_kyc_profiles SET expires_at = now() - interval '1 day' WHERE id = ?", kycId);
        UUID onExpired = verification(data.listing(owner.id()).create().id(), kycId, "Chủ", "EXP-1");
        perform(staff, post("/api/v1/verifications/" + onExpired + "/approve", Map.of("reasonCode", "DOCUMENTS_MATCH")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("KYC_NOT_VERIFIED"));

        jdbc.update("UPDATE user_kyc_profiles SET expires_at = now() + interval '1 year', revoked_at = now() WHERE id = ?", kycId);
        perform(staff, post("/api/v1/verifications/" + onExpired + "/approve", Map.of("reasonCode", "DOCUMENTS_MATCH")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("KYC_NOT_VERIFIED"));
        assertThat(jdbc.queryForObject("SELECT status FROM listing_verifications WHERE id = ?", String.class, onExpired)).isEqualTo("PENDING");
    }

    @Test
    void revokingOneCheckKeepsTheBadgeWhileAnotherIsValidAndRevokingIdentityCascades() throws Exception {
        String staff = bearer(data.user().role("MODERATOR").create());
        TestData.TestUser owner = data.user().role("OWNER").verifiedKyc().create();
        UUID kycId = kycId(owner.id());
        TestData.TestListing listing = data.listing(owner.id()).create();
        UUID first = verification(listing.id(), kycId, "Chủ", "R-1");
        UUID second = verification(listing.id(), kycId, "Chủ", "R-2");
        TestData.TestListing otherListing = data.listing(owner.id()).create();
        UUID third = verification(otherListing.id(), kycId, "Chủ", "R-3");
        for (UUID v : new UUID[]{first, second, third}) {
            perform(staff, post("/api/v1/verifications/" + v + "/approve", Map.of("reasonCode", "DOCUMENTS_MATCH"))).andExpect(status().isOk());
        }
        perform(staff, post("/api/v1/verifications/" + first + "/revoke", Map.of("reason", "Sổ cũ", "reasonCode", "OWNERSHIP_CHANGED")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT is_verified_owner FROM listings WHERE id = ?", Boolean.class, listing.id()))
                .as("the second valid check still backs the badge").isTrue();

        perform(staff, post("/api/v1/kyc/" + kycId + "/revoke", Map.of("reason", "Giả mạo", "reasonCode", "FRAUD_CONFIRMED")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT status FROM listing_verifications WHERE id IN (?,?,?) ORDER BY certificate_number", String.class,
                first, second, third)).containsExactly("REVOKED", "REVOKED", "REVOKED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listings WHERE id IN (?,?) AND is_verified_owner", Integer.class,
                listing.id(), otherListing.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trust_decisions WHERE subject_id IN (?,?) AND decision = 'REVOKED' AND reason_code = 'FRAUD_CONFIRMED'",
                Integer.class, second, third)).isEqualTo(2);
    }

    @Test
    void expiryBacklogLargerThanOneBatchIsClearedInOneRun() {
        TestData.TestUser owner = data.user().role("OWNER").verifiedKyc().create();
        UUID kycId = kycId(owner.id());
        UUID listingId = data.listing(owner.id()).create().id();
        jdbc.update("""
                INSERT INTO listing_verifications(id, listing_id, user_kyc_id, verification_type, certificate_number, document_urls,
                    owner_name_on_doc, status, created_at, verified_at, expires_at)
                SELECT gen_random_uuid(), ?, ?, 'CERTIFICATE_OF_OWNERSHIP', 'B-' || g, '/x.jpg', 'Chủ', 'VERIFIED_OWNER', now(),
                       now() - interval '200 days', now() - interval '1 day' - g * interval '1 second'
                FROM generate_series(1, 450) g
                """, listingId, kycId);
        jdbc.update("UPDATE listings SET is_verified_owner = TRUE WHERE id = ?", listingId);
        expiry.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trust_decisions WHERE listing_id = ? AND decision = 'EXPIRED'", Integer.class, listingId))
                .isEqualTo(450);
        assertThat(jdbc.queryForObject("SELECT is_verified_owner FROM listings WHERE id = ?", Boolean.class, listingId)).isFalse();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private UUID kycId(UUID userId) { return jdbc.queryForObject("SELECT id FROM user_kyc_profiles WHERE user_id = ?", UUID.class, userId); }

    private UUID verification(UUID listingId, UUID kycId, String ownerName, String certificate) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO listing_verifications(id, listing_id, user_kyc_id, verification_type, certificate_number, document_urls, owner_name_on_doc, status, created_at)
                VALUES (?,?,?,'CERTIFICATE_OF_OWNERSHIP',?,?,?,'PENDING',now())
                """, id, listingId, kycId, certificate, "/api/v1/media/kyc/" + UUID.randomUUID() + ".jpg", ownerName);
        return id;
    }

    private String bearer(TestData.TestUser user) { return "Bearer " + data.sessionFor(user.id()); }

    private ResultActions perform(String bearer, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", bearer));
    }

    private static MockHttpServletRequestBuilder get(String path) { return MockMvcRequestBuilders.get(path); }

    private MockHttpServletRequestBuilder post(String path, Object body) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post(path);
        return body == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private JsonNode body(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }
}
