package com.company.bds.admin;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.company.bds.verification.application.KycDocumentAccessService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin listings (filters, reasoned status changes, private preview) and admin users (roles, lock, history, KYC access). */
@BdsIntegrationTest
class AdminListingsAndUsersTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired ObjectMapper json;
    @Autowired KycDocumentAccessService kycAccess;

    @Test
    void adminListingSearchIsPagedAndFiltered() throws Exception {
        String admin = bearer(data.user().role("ADMIN").create());
        TestData.TestUser owner = data.user().role("BROKER").name("Chủ tin lọc quản trị").create();
        TestData.TestListing active = data.listing(owner.id()).status("ACTIVE").title("Nhà phố lọc quản trị Ba Đình").district("001", "Ba Đình, Hà Nội").create();
        TestData.TestListing locked = data.listing(owner.id()).status("LOCKED").create();
        TestData.TestListing withEdit = data.listing(owner.id()).status("ACTIVE").create();
        jdbc.update("""
                INSERT INTO listing_revisions(id,listing_id,revision_number,status,title,purpose,property_type,price_vnd,area_m2,created_at,submitted_at)
                VALUES (gen_random_uuid(), ?, 2, 'SUBMITTED', 'Bản sửa chờ duyệt kiểm thử', 'SALE', 'APARTMENT', 1, 50, now(), now())
                """, withEdit.id());

        JsonNode byOwner = body(perform(admin, get("/api/v1/admin/listings").param("ownerId", owner.id().toString())).andExpect(status().isOk()));
        assertThat(byOwner.get("total").asInt()).isEqualTo(3);
        JsonNode lockedOnly = body(perform(admin, get("/api/v1/admin/listings").param("ownerId", owner.id().toString()).param("status", "LOCKED")));
        assertThat(ids(lockedOnly)).containsExactly(locked.id().toString());
        JsonNode edits = body(perform(admin, get("/api/v1/admin/listings").param("ownerId", owner.id().toString()).param("pendingEdit", "true")));
        assertThat(ids(edits)).containsExactly(withEdit.id().toString());
        assertThat(edits.get("items").get(0).get("hasPendingEdit").asBoolean()).isTrue();
        JsonNode keyword = body(perform(admin, get("/api/v1/admin/listings").param("q", "lọc quản trị Ba Đình").param("district", "001")));
        assertThat(ids(keyword)).contains(active.id().toString());
        perform(admin, get("/api/v1/admin/listings").param("size", "5000")).andExpect(jsonPath("$.size").value(100));
        perform(admin, get("/api/v1/admin/listings").param("status", "BOGUS")).andExpect(status().isBadRequest());

        String moderator = bearer(data.user().role("MODERATOR").create());
        perform(moderator, get("/api/v1/admin/listings")).andExpect(status().isForbidden());
    }

    @Test
    void statusActionsNeedAReasonAndAreRecordedWithTheActor() throws Exception {
        TestData.TestUser adminUser = data.user().role("ADMIN").name("Quản trị viên kiểm thử").create();
        String admin = bearer(adminUser);
        TestData.TestListing listing = data.listing(data.user().role("BROKER").create().id()).status("ACTIVE").create();
        String path = "/api/v1/admin/listings/" + listing.id() + "/status";

        perform(admin, post(path, Map.of("action", "HIDE", "reason", " "))).andExpect(status().isBadRequest());
        perform(admin, post(path, Map.of("action", "HIDE", "reason", "abc"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        perform(admin, post(path, Map.of("action", "HIDE", "reason", "Ảnh chứa số điện thoại"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.toStatus").value("PAUSED"));
        perform(admin, post(path, Map.of("action", "HIDE", "reason", "Ẩn lần nữa"))).andExpect(status().isConflict());
        perform(admin, post(path, Map.of("action", "LOCK", "reason", "Lừa đảo đã xác minh"))).andExpect(jsonPath("$.toStatus").value("LOCKED"));
        perform(admin, post(path, Map.of("action", "UNLOCK", "reason", "Khiếu nại được chấp nhận"))).andExpect(jsonPath("$.toStatus").value("ACTIVE"));

        JsonNode history = body(perform(admin, get("/api/v1/admin/listings/" + listing.id() + "/history")));
        assertThat(history).hasSize(3);
        assertThat(history.get(2).get("action").asText()).isEqualTo("HIDE");
        assertThat(history.get(2).get("reason").asText()).isEqualTo("Ảnh chứa số điện thoại");
        assertThat(history.get(0).get("actorName").asText()).isEqualTo("Quản trị viên kiểm thử");
        JsonNode revisions = body(perform(admin, get("/api/v1/admin/listings/" + listing.id() + "/revisions")));
        assertThat(revisions.get(0).get("isPublic").asBoolean()).isTrue();
    }

    @Test
    void privatePreviewIsAdminOnlyAndNeverCached() throws Exception {
        String admin = bearer(data.user().role("ADMIN").create());
        TestData.TestUser owner = data.user().role("BROKER").create();
        TestData.TestListing draft = data.listing(owner.id()).status("DRAFT").title("Bản nháp riêng tư không công khai").create();
        String path = "/api/v1/admin/listings/" + draft.id() + "/preview";

        perform(admin, get(path)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(jsonPath("$.revisionStatus").value("DRAFT"))
                .andExpect(jsonPath("$.isPublic").value(false))
                .andExpect(jsonPath("$.mediaUrls.length()").value(1));
        perform(bearer(owner), get(path)).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        // The public detail endpoints do not expose the draft.
        mvc.perform(get("/api/v1/listings/by-slug/" + draft.slug())).andExpect(r -> assertThat(r.getResponse().getStatus()).isIn(404, 410));
    }

    @Test
    void roleChangeRulesAndHistory() throws Exception {
        TestData.TestUser adminUser = data.user().role("ADMIN").name("Quản trị A").create();
        String admin = bearer(adminUser);
        TestData.TestUser target = data.user().role("USER").create();
        jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, 'OWNER')", target.id()); // legacy second row
        String path = "/api/v1/admin/users/" + target.id() + "/role";

        perform(admin, patch(path, Map.of("role", "BROKER"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        perform(admin, patch(path, Map.of("role", "SUPERUSER", "reason", "Thử vai trò lạ"))).andExpect(status().isBadRequest());
        perform(admin, patch(path, Map.of("role", "BROKER", "reason", "Đã xác minh chứng chỉ môi giới"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.fromRole").value("OWNER")).andExpect(jsonPath("$.toRole").value("BROKER"));
        assertThat(jdbc.queryForList("SELECT role FROM user_roles WHERE user_id = ?", String.class, target.id())).containsExactly("BROKER");
        perform(admin, patch("/api/v1/admin/users/" + adminUser.id() + "/role", Map.of("role", "USER", "reason", "Tự hạ quyền mình")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OWN_ROLE"));

        perform(admin, patch("/api/v1/admin/users/" + target.id() + "/status", Map.of("status", "SUSPENDED"))).andExpect(status().isBadRequest());
        String targetSession = data.sessionFor(target.id());
        perform(admin, patch("/api/v1/admin/users/" + target.id() + "/status", Map.of("status", "SUSPENDED", "reason", "Đăng tin lừa đảo")))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id = ? AND revoked_at IS NULL", Integer.class, target.id())).isZero();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + targetSession)).andExpect(status().isUnauthorized());
        perform(admin, patch("/api/v1/admin/users/" + target.id() + "/status", Map.of("status", "ACTIVE", "reason", "Đã giải trình hợp lệ")))
                .andExpect(status().isNoContent());

        JsonNode history = body(perform(admin, get("/api/v1/admin/users/" + target.id() + "/history")));
        assertThat(history).hasSize(3);
        assertThat(history.get(0).get("action").asText()).isEqualTo("UNLOCK");
        assertThat(history.get(1).get("reason").asText()).isEqualTo("Đăng tin lừa đảo");
        assertThat(history.get(2).get("action").asText()).isEqualTo("ROLE_CHANGE");
        assertThat(history.get(2).get("actorName").asText()).isEqualTo("Quản trị A");

        String list = perform(admin, get("/api/v1/admin/users").param("size", "100")).andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContainIgnoringCase("phone");
        perform(bearer(data.user().role("MODERATOR").create()), patch(path, Map.of("role", "ADMIN", "reason", "Tự nâng quyền"))).andExpect(status().isForbidden());
    }

    @Test
    void theLastActiveAdminCannotBeDemotedEvenUnderConcurrency() throws Exception {
        TestData.TestUser a = data.user().role("ADMIN").create();
        TestData.TestUser b = data.user().role("ADMIN").create();
        List<UUID> others = jdbc.queryForList("""
                SELECT DISTINCT u.id FROM users u JOIN user_roles r ON r.user_id = u.id
                WHERE r.role = 'ADMIN' AND u.status = 'ACTIVE' AND u.id NOT IN (?, ?)
                """, UUID.class, a.id(), b.id());
        String bearerA = bearer(a);
        String bearerB = bearer(b);
        try {
            for (UUID other : others) jdbc.update("UPDATE users SET status = 'PENDING_EMAIL_VERIFICATION' WHERE id = ?", other);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            Future<Integer> aDemotesB = pool.submit(() -> { start.await(); return perform(bearerA, patch("/api/v1/admin/users/" + b.id() + "/role",
                    Map.of("role", "USER", "reason", "Hạ quyền đồng thời"))).andReturn().getResponse().getStatus(); });
            Future<Integer> bDemotesA = pool.submit(() -> { start.await(); return perform(bearerB, patch("/api/v1/admin/users/" + a.id() + "/role",
                    Map.of("role", "USER", "reason", "Hạ quyền đồng thời"))).andReturn().getResponse().getStatus(); });
            start.countDown();
            List<Integer> statuses = List.of(aDemotesB.get(), bDemotesA.get());
            pool.shutdown();
            assertThat(statuses).contains(200);
            Integer admins = jdbc.queryForObject("""
                    SELECT count(DISTINCT u.id) FROM users u JOIN user_roles r ON r.user_id = u.id
                    WHERE r.role = 'ADMIN' AND u.status = 'ACTIVE'
                    """, Integer.class);
            assertThat(admins).as("one active admin always remains").isEqualTo(1);
            UUID remaining = jdbc.queryForObject("SELECT u.id FROM users u JOIN user_roles r ON r.user_id = u.id WHERE r.role = 'ADMIN' AND u.status = 'ACTIVE'", UUID.class);
            UUID demoted = remaining.equals(a.id()) ? b.id() : a.id();
            // The survivor may not demote itself (own role) and nobody else is left who could.
            perform(remaining.equals(a.id()) ? bearerA : bearerB, patch("/api/v1/admin/users/" + remaining + "/role",
                    Map.of("role", "USER", "reason", "Tự hạ quyền"))).andExpect(status().isConflict());
            assertThat(jdbc.queryForList("SELECT role FROM user_roles WHERE user_id = ?", String.class, demoted)).containsExactly("USER");
        } finally {
            for (UUID other : others) jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", other);
        }
    }

    @Test
    void theOnlyActiveAdminCannotBeDemoted() throws Exception {
        TestData.TestUser only = data.user().role("ADMIN").create();
        List<UUID> others = jdbc.queryForList("""
                SELECT DISTINCT u.id FROM users u JOIN user_roles r ON r.user_id = u.id
                WHERE r.role = 'ADMIN' AND u.status = 'ACTIVE' AND u.id <> ?
                """, UUID.class, only.id());
        try {
            for (UUID other : others) jdbc.update("UPDATE users SET status = 'PENDING_EMAIL_VERIFICATION' WHERE id = ?", other);
            // A caller authenticated as ADMIN without an active admin account of its own (e.g. a break-glass session).
            mvc.perform(MockMvcRequestBuilders.patch("/api/v1/admin/users/" + only.id() + "/role")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                    .user(UUID.randomUUID().toString()).roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("role", "MODERATOR", "reason", "Hạ quyền quản trị cuối"))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
            assertThat(jdbc.queryForList("SELECT role FROM user_roles WHERE user_id = ?", String.class, only.id())).containsExactly("ADMIN");
        } finally {
            for (UUID other : others) jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", other);
        }
    }

    @Autowired com.company.bds.iam.application.AuthService authService;

    @Test
    void kycDocumentsNeedPasswordAndReasonAndEveryOpeningIsLogged() throws Exception {
        TestData.TestUser adminUser = data.user().role("ADMIN").create();
        String admin = bearer(adminUser);
        TestData.TestUser subject = data.user().role("OWNER").verifiedKyc().create();
        String objectKey = UUID.randomUUID() + ".jpg";
        jdbc.update("UPDATE user_kyc_profiles SET id_card_front_url = ? WHERE user_id = ?", "/api/v1/media/kyc/" + objectKey, subject.id());
        jdbc.update("INSERT INTO media_objects(object_key, owner_id, content_type, size_bytes, visibility) VALUES (?,?,?,?,'KYC_PRIVATE')",
                objectKey, subject.id(), "image/jpeg", 10);
        String path = "/api/v1/admin/users/" + subject.id() + "/kyc-documents";

        perform(admin, post(path, Map.of("password", TestData.DEFAULT_PASSWORD, "reason", ""))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        perform(admin, post(path, Map.of("password", "wrong-password-123", "reason", "Đối chiếu hồ sơ khiếu nại"))).andExpect(status().isForbidden());
        assertThat(kycAccess.log(subject.id())).isEmpty();

        JsonNode access = body(perform(admin, post(path, Map.of("password", TestData.DEFAULT_PASSWORD, "reason", "Đối chiếu hồ sơ khiếu nại")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))));
        assertThat(access.get("identity").get("idCardFrontUrl").asText()).endsWith(objectKey);
        String token = access.get("token").asText();
        assertThat(kycAccess.staffMayRead(adminUser.id(), token, objectKey)).isTrue();
        assertThat(kycAccess.staffMayRead(adminUser.id(), "forged", objectKey)).isFalse();
        TestData.TestUser otherSubject = data.user().create();
        String otherKey = UUID.randomUUID() + ".jpg";
        jdbc.update("INSERT INTO media_objects(object_key, owner_id, content_type, size_bytes, visibility) VALUES (?,?,?,?,'KYC_PRIVATE')",
                otherKey, otherSubject.id(), "image/jpeg", 10);
        assertThat(kycAccess.staffMayRead(adminUser.id(), token, otherKey)).as("the grant covers only the logged subject").isFalse();
        // A grant obtained without a reason (plain password re-check) does not borrow the reasoned log row.
        String unreasoned = authService.grantKycDocumentAccess(adminUser.id(), TestData.DEFAULT_PASSWORD).token();
        assertThat(kycAccess.staffMayRead(adminUser.id(), unreasoned, objectKey)).isFalse();

        JsonNode log = body(perform(admin, get("/api/v1/admin/users/" + subject.id() + "/kyc-access-log")));
        assertThat(log).hasSize(1);
        assertThat(log.get(0).get("reason").asText()).isEqualTo("Đối chiếu hồ sơ khiếu nại");
        assertThat(log.get(0).get("actorId").asText()).isEqualTo(adminUser.id().toString());
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private String bearer(TestData.TestUser user) { return "Bearer " + data.sessionFor(user.id()); }

    private ResultActions perform(String bearer, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", bearer));
    }

    private static MockHttpServletRequestBuilder get(String path) { return MockMvcRequestBuilders.get(path); }

    private MockHttpServletRequestBuilder post(String path, Object body) throws Exception {
        return MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder patch(String path, Object body) throws Exception {
        return MockMvcRequestBuilders.patch(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private JsonNode body(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(i -> ids.add(i.get("id").asText()));
        return ids;
    }
}
