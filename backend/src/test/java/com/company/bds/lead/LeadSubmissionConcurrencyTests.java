package com.company.bds.lead;

import com.company.bds.lead.application.IdempotencyKeyPurgeTask;
import com.company.bds.lead.application.LeadApplicationService;
import com.company.bds.lead.domain.model.LeadRequestType;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** F17.1–F17.3 and R-4 (lead part) on PostgreSQL: scoped idempotency, atomic quota, pause vs lead. */
@BdsIntegrationTest
class LeadSubmissionConcurrencyTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired ObjectMapper json;
    @Autowired LeadApplicationService leads;
    @Autowired IdempotencyKeyPurgeTask purge;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void twentyParallelRequestsWithOneKeyCreateOneLeadAndEveryRetryGetsItsId() throws Exception {
        TestData.TestListing listing = listing();
        TestData.TestUser buyer = data.user().verifiedKyc().create();
        String bearer = "Bearer " + data.sessionFor(buyer.id());
        String key = "it-key-" + UUID.randomUUID();
        String body = body(listing.id(), "0911000001");

        List<MockHttpServletResponse> responses = parallel(20, () -> submit(bearer, key, body));
        Set<String> leadIds = new HashSet<>();
        for (MockHttpServletResponse response : responses) {
            assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
            leadIds.add(json.readTree(response.getContentAsString()).get("leadId").asText());
        }
        assertThat(leadIds).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM leads WHERE listing_id = ?", listing.id())).isEqualTo(1);
        assertThat(responses.stream().filter(r -> "true".equals(r.getHeader("Idempotent-Replayed"))).count()).isEqualTo(19);
        assertThat(count("SELECT COUNT(*) FROM lead_events WHERE lead_id = ?::uuid AND type = 'CREATED'", leadIds.iterator().next()))
                .isEqualTo(1);

        // A retry after a timeout gets the same lead id.
        MockHttpServletResponse retry = submit(bearer, key, body);
        assertThat(json.readTree(retry.getContentAsString()).get("leadId").asText()).isEqualTo(leadIds.iterator().next());
        assertThat(json.readTree(retry.getContentAsString()).get("replayed").asBoolean()).isTrue();

        // Same key, different payload: refused, nothing created.
        MockHttpServletResponse other = submit(bearer, key, body(listing.id(), "0911000002"));
        assertThat(other.getStatus()).isEqualTo(409);
        assertThat(other.getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(count("SELECT COUNT(*) FROM leads WHERE listing_id = ?", listing.id())).isEqualTo(1);
    }

    @Test
    void anotherActorWithTheSameKeyNeverReceivesTheReplay() throws Exception {
        TestData.TestListing listing = listing();
        TestData.TestUser first = data.user().verifiedKyc().create();
        TestData.TestUser second = data.user().verifiedKyc().create();
        String key = "shared-key-" + UUID.randomUUID();
        String body = body(listing.id(), "0911000010");

        JsonNode a = json.readTree(submit("Bearer " + data.sessionFor(first.id()), key, body).getContentAsString());
        MockHttpServletResponse secondResponse = submit("Bearer " + data.sessionFor(second.id()), key, body);
        assertThat(secondResponse.getStatus()).isEqualTo(201);
        JsonNode b = json.readTree(secondResponse.getContentAsString());
        assertThat(b.get("leadId").asText()).isNotEqualTo(a.get("leadId").asText());
        assertThat(b.get("replayed").asBoolean()).isFalse();
        assertThat(jdbc.queryForList("SELECT requester_id FROM leads WHERE listing_id = ?", UUID.class, listing.id()))
                .containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void expiredKeysCanBeReusedAndArePurged() throws Exception {
        TestData.TestListing listing = listing();
        TestData.TestUser buyer = data.user().verifiedKyc().create();
        String bearer = "Bearer " + data.sessionFor(buyer.id());
        String key = "expiring-" + UUID.randomUUID();
        String firstLead = json.readTree(submit(bearer, key, body(listing.id(), "0911000020")).getContentAsString()).get("leadId").asText();
        assertThat(jdbc.queryForObject("SELECT expires_at > now() + interval '23 hours' FROM api_idempotency_keys WHERE scope = ? AND idempotency_key = ?",
                Boolean.class, "lead:" + buyer.id(), key)).isTrue();

        jdbc.update("UPDATE api_idempotency_keys SET expires_at = now() - interval '1 minute' WHERE scope = ? AND idempotency_key = ?",
                "lead:" + buyer.id(), key);
        String secondLead = json.readTree(submit(bearer, key, body(listing.id(), "0911000021")).getContentAsString()).get("leadId").asText();
        assertThat(secondLead).isNotEqualTo(firstLead);

        jdbc.update("UPDATE api_idempotency_keys SET expires_at = now() - interval '1 minute' WHERE scope = ?", "lead:" + buyer.id());
        String unrelated = "billing:" + UUID.randomUUID();
        jdbc.update("INSERT INTO api_idempotency_keys(scope, idempotency_key, request_hash, resource_id, created_at) VALUES (?,?,?,?, now() - interval '3 days')",
                unrelated, "k-" + UUID.randomUUID(), "h", UUID.randomUUID());
        assertThat(purge.purge()).isGreaterThanOrEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM api_idempotency_keys WHERE scope = ?", "lead:" + buyer.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM api_idempotency_keys WHERE scope = ?", unrelated)).as("other modules' keys kept").isEqualTo(1);
    }

    @Test
    void twentyParallelDistinctRequestsNeverExceedTheDailyQuota() throws Exception {
        TestData.TestListing listing = listing();
        TestData.TestUser buyer = data.user().verifiedKyc().create();
        String bearer = "Bearer " + data.sessionFor(buyer.id());
        List<MockHttpServletResponse> responses = parallel(20, () ->
                submit(bearer, "quota-" + UUID.randomUUID(), body(listing.id(), "0911000030")));
        long created = responses.stream().filter(r -> r.getStatus() == 201).count();
        long refused = responses.stream().filter(r -> r.getStatus() == 429).count();
        assertThat(created).isEqualTo(LeadApplicationService.QUOTA_PER_DAY);
        assertThat(refused).isEqualTo(20 - LeadApplicationService.QUOTA_PER_DAY);
        assertThat(responses.stream().filter(r -> r.getStatus() == 429).findFirst().orElseThrow().getContentAsString())
                .contains("LEAD_QUOTA_EXCEEDED");
        assertThat(count("SELECT COUNT(*) FROM leads WHERE requester_id = ?", buyer.id())).isEqualTo(LeadApplicationService.QUOTA_PER_DAY);

        // The phone quota is shared across accounts: another account with the same phone is refused too.
        TestData.TestUser other = data.user().verifiedKyc().create();
        assertThat(submit("Bearer " + data.sessionFor(other.id()), "quota-other-" + UUID.randomUUID(),
                body(listing.id(), "0911000030")).getStatus()).isEqualTo(429);
    }

    @Test
    void pauseCommittedFirstRefusesTheLeadAndAPauseDuringTheInsertWaitsForIt() throws Exception {
        TestData.TestUser buyer = data.user().verifiedKyc().create();
        TestData.TestListing paused = listing();
        jdbc.update("UPDATE listings SET status = 'PAUSED' WHERE id = ?", paused.id());
        MockHttpServletResponse refused = submit("Bearer " + data.sessionFor(buyer.id()), null, body(paused.id(), "0911000040"));
        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(refused.getContentAsString()).contains("LISTING_NOT_ACCEPTING_LEADS");

        TestData.TestListing listing = listing();
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<UUID> lead = pool.submit(() -> tx.execute(status -> {
                UUID id = leads.submit(buyer.id(), listing.id(), "Khách", "0911000041", LeadRequestType.VIEWING, null, true, null)
                        .lead().getId();
                inserted.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                return id;
            }));
            assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
            // While the lead transaction is open the pause cannot take the row lock.
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '300ms'");
                jdbc.update("UPDATE listings SET status = 'PAUSED' WHERE id = ?", listing.id());
            })).hasMessageContaining("lock");
            release.countDown();
            UUID leadId = lead.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.update("UPDATE listings SET status = 'PAUSED' WHERE id = ?", listing.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM leads WHERE id = ?", String.class, leadId))
                    .as("the lead created before the pause stays valid").isEqualTo("NEW");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void kycAndSelfLeadAreRefusedWithStableCodesAndEligibilityExplainsWhy() throws Exception {
        TestData.TestUser owner = data.user().role("BROKER").verifiedKyc().create();
        TestData.TestListing listing = data.listing(owner.id()).create();
        TestData.TestUser unverified = data.user().create();
        MockHttpServletResponse noKyc = submit("Bearer " + data.sessionFor(unverified.id()), null, body(listing.id(), "0911000050"));
        assertThat(noKyc.getStatus()).isEqualTo(409);
        assertThat(noKyc.getContentAsString()).contains("\"code\":\"KYC_REQUIRED\"");
        MockHttpServletResponse self = submit("Bearer " + data.sessionFor(owner.id()), null, body(listing.id(), "0911000051"));
        assertThat(self.getContentAsString()).contains("SELF_LEAD");

        JsonNode eligibility = json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/me/inquiries/eligibility").param("listingId", listing.id().toString())
                        .header("Authorization", "Bearer " + data.sessionFor(unverified.id())))
                .andReturn().getResponse().getContentAsString());
        assertThat(eligibility.get("requesterKycVerified").asBoolean()).isFalse();
        assertThat(eligibility.get("ownerKycVerified").asBoolean()).isTrue();
        assertThat(eligibility.get("listingAcceptsLeads").asBoolean()).isTrue();
        assertThat(eligibility.toString()).doesNotContain("@").doesNotContain("phone");
    }

    private TestData.TestListing listing() {
        TestData.TestUser owner = data.user().role("BROKER").verifiedKyc().create();
        return data.listing(owner.id()).create();
    }

    private String body(UUID listingId, String phone) throws Exception {
        return json.writeValueAsString(Map.of("listingId", listingId, "fullName", "Khách kiểm thử", "phone", phone,
                "requestType", "VIEWING", "note", "Muốn xem cuối tuần", "consentPolicy", true));
    }

    private MockHttpServletResponse submit(String bearer, String key, String body) throws Exception {
        var request = post("/api/v1/public/leads").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) request.header("Idempotency-Key", key);
        return mvc.perform(request).andReturn().getResponse();
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static <T> List<T> parallel(int n, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(60, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
