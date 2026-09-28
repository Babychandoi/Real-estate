package com.company.bds.lead;

import com.company.bds.lead.application.LeadInboxQuery;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.QueryCount;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** F08.3, F08.6, UI-09, UI-10, R-4: owner JOIN inbox, server filters, compare-and-set, history, withdrawal, team access. */
@BdsIntegrationTest
class LeadInboxAndCommandTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired ObjectMapper json;
    @Autowired LeadInboxQuery inbox;

    @Test
    void ownerWithTenThousandListingsOpensATwentyLeadPageInTwoStatements() {
        TestData.TestUser owner = data.user().role("BROKER").create();
        List<UUID> bulk = jdbc.queryForList("""
                INSERT INTO listings(id, owner_id, status, is_verified_owner, version, slug, created_at, updated_at)
                SELECT gen_random_uuid(), ?, 'DRAFT', FALSE, 0, 'it-bulk-' || gen_random_uuid(), now(), now()
                FROM generate_series(1, 10000) RETURNING id
                """, UUID.class, owner.id());
        // Keep the shared queue clean for other suites (their workers drain search-index jobs).
        jdbc.update("DELETE FROM background_jobs WHERE queue = 'search-index' AND dedupe_key IN (SELECT id::text FROM listings WHERE owner_id = ?)", owner.id());
        TestData.TestListing withLeads = data.listing(owner.id()).create();
        Instant base = Instant.now().minus(Duration.ofHours(2));
        for (int i = 0; i < 25; i++) data.lead(withLeads.id()).createdAt(base.plusSeconds(i)).create();
        data.lead(data.listing(data.user().role("BROKER").create().id()).create().id()).create();

        LeadInboxQuery.PageResult<LeadInboxQuery.LeadItem> page = QueryCount.assertAtMost(2, () ->
                inbox.inbox(owner.id(), false, LeadInboxQuery.InboxFilter.none(), 0, 20));
        assertThat(bulk).hasSize(10_000);
        assertThat(page.items()).hasSize(20);
        assertThat(page.totalElements()).isEqualTo(25);
        assertThat(page.items()).allSatisfy(item -> assertThat(item.listingId()).isEqualTo(withLeads.id()));
        assertThat(page.items().get(0).createdAt()).isAfter(page.items().get(19).createdAt());
        assertThat(page.items().get(0).listingTitle()).isEqualTo("Căn hộ kiểm thử tích hợp");
        LeadInboxQuery.PageResult<LeadInboxQuery.LeadItem> second = inbox.inbox(owner.id(), false, LeadInboxQuery.InboxFilter.none(), 1, 20);
        assertThat(second.items()).hasSize(5);
        assertThat(second.items()).extracting(LeadInboxQuery.LeadItem::id).doesNotContainAnyElementsOf(
                page.items().stream().map(LeadInboxQuery.LeadItem::id).toList());
        assertThat(inbox.inbox(owner.id(), false, LeadInboxQuery.InboxFilter.none(), 0, 500).size())
                .as("size capped").isEqualTo(LeadInboxQuery.MAX_SIZE);
    }

    @Test
    void serverFiltersNarrowTheInboxAndCountsIgnoreTheStatusFilter() throws Exception {
        TestData.TestUser owner = data.user().role("OWNER").create();
        TestData.TestListing a = data.listing(owner.id()).create();
        TestData.TestListing b = data.listing(owner.id()).create();
        UUID viewing = data.lead(a.id()).requestType("VIEWING").fullName("Nguyễn Hẹn Xem").create();
        UUID overdue = data.lead(a.id()).createdAt(Instant.now().minus(Duration.ofHours(3))).fullName("Trần Quá Hạn").create();
        data.lead(b.id()).status("CONTACTED").fullName("Lê Đã Gọi").create();
        jdbc.update("UPDATE leads SET qualification = 'QUALIFIED', qualification_reason = 'BUDGET_MATCH' WHERE id = ?", viewing);
        String bearer = bearer(owner.id());

        JsonNode byListing = read(get("/api/v1/leads/inbox").param("listingId", a.id().toString()), bearer);
        assertThat(byListing.get("totalElements").asLong()).isEqualTo(2);
        assertThat(read(get("/api/v1/leads/inbox").param("requestType", "VIEWING"), bearer).get("items").get(0).get("id").asText())
                .isEqualTo(viewing.toString());
        JsonNode overdueOnly = read(get("/api/v1/leads/inbox").param("overdue", "true"), bearer);
        assertThat(overdueOnly.get("items")).hasSize(1);
        assertThat(overdueOnly.get("items").get(0).get("id").asText()).isEqualTo(overdue.toString());
        assertThat(overdueOnly.get("items").get(0).get("overdue").asBoolean()).isTrue();
        assertThat(read(get("/api/v1/leads/inbox").param("qualification", "QUALIFIED"), bearer).get("totalElements").asLong()).isEqualTo(1);
        JsonNode contacted = read(get("/api/v1/leads/inbox").param("status", "CONTACTED"), bearer);
        assertThat(contacted.get("totalElements").asLong()).isEqualTo(1);
        assertThat(contacted.get("statusCounts").get("NEW").asLong()).isEqualTo(2);
        assertThat(read(get("/api/v1/leads/inbox").param("q", "quá hạn"), bearer).get("totalElements").asLong()).isEqualTo(1);
        assertThat(read(get("/api/v1/leads/inbox").param("q", "%"), bearer).get("totalElements").asLong())
                .as("LIKE wildcards are escaped").isZero();
        assertThat(perform(get("/api/v1/leads/inbox").param("qualification", "MAYBE"), bearer).getStatus()).isEqualTo(400);
        assertThat(contacted.toString()).doesNotContain("0998").as("phones are masked");
        TestData.TestListing foreign = data.listing(data.user().role("BROKER").create().id()).create();
        assertThat(perform(get("/api/v1/leads/inbox").param("listingId", foreign.id().toString()), bearer).getStatus()).isEqualTo(403);
    }

    @Test
    void concurrentStatusUpdatesWithOneVersionLetExactlyOneWin() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        UUID leadId = data.lead(data.listing(owner.id()).create().id()).create();
        String bearer = bearer(owner.id());
        long version = read(get("/api/v1/leads/" + leadId), bearer).get("version").asLong();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (String target : List.of("CONTACTED", "SPAM")) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return perform(patch("/api/v1/leads/" + leadId + "/status").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("status", target, "expectedVersion", version))), bearer);
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) statuses.add(future.get(30, TimeUnit.SECONDS).getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT version FROM leads WHERE id = ?", Long.class, leadId)).isEqualTo(version + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lead_events WHERE lead_id = ? AND type = 'STATUS_CHANGED'", Long.class, leadId))
                .isEqualTo(1);
        MockHttpServletResponse stale = perform(patch("/api/v1/leads/" + leadId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", "CLOSED", "expectedVersion", version))), bearer);
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(stale.getContentAsString()).contains("LEAD_VERSION_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT first_response_at IS NOT NULL FROM leads WHERE id = ?", Boolean.class, leadId)).isTrue();
    }

    @Test
    void transitionsAreCheckedHistoryIsRecordedAndQualificationNeedsAReason() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        UUID leadId = data.lead(data.listing(owner.id()).create().id()).create();
        String bearer = bearer(owner.id());
        JsonNode contacted = read(patch("/api/v1/leads/" + leadId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", "CONTACTED", "expectedVersion", 0, "note", "Đã gọi lần 1"))), bearer);
        assertThat(contacted.get("firstResponseAt").isNull()).isFalse();
        long version = contacted.get("version").asLong();
        assertThat(perform(patch("/api/v1/leads/" + leadId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", "NEW", "expectedVersion", version))), bearer).getStatus())
                .as("nothing goes back to NEW").isEqualTo(409);

        assertThat(perform(patch("/api/v1/leads/" + leadId + "/qualification").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qualification", "QUALIFIED", "expectedVersion", version))), bearer).getStatus())
                .isEqualTo(400);
        JsonNode qualified = read(patch("/api/v1/leads/" + leadId + "/qualification").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qualification", "QUALIFIED", "reason", "READY_TO_VIEW",
                        "note", "Có ngân sách", "expectedVersion", version))), bearer);
        assertThat(qualified.get("qualification").asText()).isEqualTo("QUALIFIED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_events WHERE name = 'lead_qualified' AND properties ->> 'leadId' = ?",
                Long.class, leadId.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_events WHERE name = 'lead_first_response' AND properties ->> 'leadId' = ?",
                Long.class, leadId.toString())).isEqualTo(1);

        JsonNode history = read(get("/api/v1/leads/" + leadId + "/history"), bearer);
        assertThat(history.findValuesAsText("type")).containsExactly("QUALIFIED", "STATUS_CHANGED");
        assertThat(history.get(1).get("note").asText()).isEqualTo("Đã gọi lần 1");
    }

    @Test
    void requesterSeesRealStateWithdrawsAndNeverSeesInternalData() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        TestData.TestUser buyer = data.user().create();
        UUID leadId = data.lead(data.listing(owner.id()).create().id()).requester(buyer.id()).create();
        String ownerBearer = bearer(owner.id());
        String buyerBearer = bearer(buyer.id());
        read(patch("/api/v1/leads/" + leadId + "/qualification").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qualification", "UNQUALIFIED", "reason", "BUDGET_MISMATCH",
                        "note", "ghi chú nội bộ", "expectedVersion", 0))), ownerBearer);

        JsonNode mine = read(get("/api/v1/me/inquiries"), buyerBearer);
        JsonNode item = mine.get("items").get(0);
        assertThat(item.get("id").asText()).isEqualTo(leadId.toString());
        assertThat(item.has("qualification")).isFalse();
        assertThat(item.has("assigneeId")).isFalse();
        assertThat(mine.toString()).doesNotContain("ghi chú nội bộ").doesNotContain(owner.email());
        JsonNode history = read(get("/api/v1/me/inquiries/" + leadId + "/history"), buyerBearer);
        assertThat(history.findValuesAsText("type")).doesNotContain("QUALIFIED");
        assertThat(perform(get("/api/v1/leads/" + leadId), buyerBearer).getStatus()).as("owner-side view refused").isIn(403, 404);

        long version = item.get("version").asLong();
        assertThat(perform(post("/api/v1/me/inquiries/" + leadId + "/withdraw").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", "Đã tìm được nhà", "expectedVersion", version - 1))), buyerBearer)
                .getStatus()).isEqualTo(409);
        JsonNode withdrawn = read(post("/api/v1/me/inquiries/" + leadId + "/withdraw").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", "Đã tìm được nhà", "expectedVersion", version))), buyerBearer);
        assertThat(withdrawn.get("status").asText()).isEqualTo("WITHDRAWN");
        assertThat(withdrawn.get("withdrawnAt").isNull()).isFalse();
        long after = read(get("/api/v1/leads/" + leadId), ownerBearer).get("version").asLong();
        assertThat(perform(patch("/api/v1/leads/" + leadId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", "CONTACTED", "expectedVersion", after))), ownerBearer).getStatus())
                .as("withdrawn is terminal").isEqualTo(409);
        assertThat(perform(get("/api/v1/leads/" + leadId + "/contact"), ownerBearer).getStatus()).isEqualTo(409);

        TestData.TestUser stranger = data.user().create();
        assertThat(perform(post("/api/v1/me/inquiries/" + leadId + "/withdraw").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("expectedVersion", after))), bearer(stranger.id())).getStatus()).isEqualTo(404);
    }

    @Test
    void teamMembersHandleOnlyAssignedLeadsAndLoseAccessWhenRemoved() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").create();
        TestData.TestUser member = data.user().role("BROKER").create();
        TestData.TestUser notBroker = data.user().role("OWNER").create();
        TestData.TestListing listing = data.listing(owner.id()).create();
        UUID assigned = data.lead(listing.id()).create();
        UUID notAssigned = data.lead(listing.id()).create();
        String ownerBearer = bearer(owner.id());
        String memberBearer = bearer(member.id());

        assertThat(perform(post("/api/v1/broker/team").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", notBroker.email()))), ownerBearer).getContentAsString())
                .contains("MEMBER_NOT_ELIGIBLE");
        assertThat(perform(post("/api/v1/broker/team").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "nobody-" + UUID.randomUUID() + "@example.test"))), ownerBearer)
                .getContentAsString()).contains("MEMBER_NOT_ELIGIBLE");
        JsonNode team = read(post("/api/v1/broker/team").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", member.email().toUpperCase()))), ownerBearer);
        assertThat(team.get(0).get("memberId").asText()).isEqualTo(member.id().toString());
        assertThat(team.get(0).get("email").asText()).doesNotContain(member.email()).contains("***@");

        assertThat(perform(patch("/api/v1/leads/" + assigned + "/assignee").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("assigneeId", notBroker.id(), "expectedVersion", 0))), ownerBearer)
                .getContentAsString()).contains("ASSIGNEE_NOT_IN_TEAM");
        JsonNode assignedLead = read(patch("/api/v1/leads/" + assigned + "/assignee").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("assigneeId", member.id(), "expectedVersion", 0))), ownerBearer);
        assertThat(assignedLead.get("assigneeId").asText()).isEqualTo(member.id().toString());

        JsonNode memberInbox = read(get("/api/v1/leads/inbox"), memberBearer);
        assertThat(memberInbox.findValuesAsText("id")).containsExactly(assigned.toString());
        assertThat(perform(get("/api/v1/leads/" + notAssigned), memberBearer).getStatus()).isEqualTo(404);
        assertThat(perform(get("/api/v1/leads/" + assigned + "/contact"), memberBearer).getStatus()).isEqualTo(200);
        long version = assignedLead.get("version").asLong();
        assertThat(perform(patch("/api/v1/leads/" + assigned + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", "CONTACTED", "expectedVersion", version))), memberBearer)
                .getStatus()).isEqualTo(200);
        assertThat(perform(patch("/api/v1/leads/" + assigned + "/assignee").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("assigneeId", owner.id(), "expectedVersion", version + 1))), memberBearer)
                .getStatus()).as("members cannot redistribute").isEqualTo(403);

        read(delete("/api/v1/broker/team/" + member.id()), ownerBearer);
        assertThat(jdbc.queryForObject("SELECT assignee_id FROM leads WHERE id = ?", UUID.class, assigned)).isNull();
        assertThat(perform(get("/api/v1/leads/" + assigned), memberBearer).getStatus()).isEqualTo(404);
        assertThat(read(get("/api/v1/leads/inbox"), memberBearer).get("totalElements").asLong()).isZero();
        assertThat(read(get("/api/v1/leads/" + assigned + "/history"), ownerBearer).findValuesAsText("type"))
                .containsSequence("ASSIGNED", "STATUS_CHANGED", "ASSIGNED");
    }

    private String bearer(UUID userId) {
        return "Bearer " + data.sessionFor(userId);
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        return mvc.perform(request.header("Authorization", bearer)).andReturn().getResponse();
    }

    private JsonNode read(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        MockHttpServletResponse response = perform(request, bearer);
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString());
    }
}
