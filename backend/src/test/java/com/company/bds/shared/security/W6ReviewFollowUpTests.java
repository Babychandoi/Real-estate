package com.company.bds.shared.security;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** W6 review follow-ups not covered by the reviewer's tests (MINOR 4, 8; MAJOR 2 error mapping; NITs). */
@BdsIntegrationTest
class W6ReviewFollowUpTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired AuditTrail trail;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @Autowired com.company.bds.shared.error.GlobalExceptionHandler errors;

    @Test
    void aLinkerCommitAfterTheHeadReadIsOutsideTheVerifiedPrefix() {
        trail.linkAll();
        long capturedHead = jdbc.queryForObject("SELECT last_seq FROM audit_chain_head", Long.class);
        java.util.concurrent.atomic.AtomicBoolean once = new java.util.concurrent.atomic.AtomicBoolean(true);
        JdbcTemplate racing = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public Map<String, Object> queryForMap(String sql) {
                Map<String, Object> captured = super.queryForMap(sql);
                if (sql.contains("FROM audit_chain_head") && once.getAndSet(false)) {
                    trail.record(new AuditTrail.Entry(null, "POST", "/api/v1/w6-snapshot-" + UUID.randomUUID(), 201, "f".repeat(64)));
                    trail.linkAll();
                }
                return captured;
            }
        };
        AuditTrail.Verification verified = new AuditTrail(racing, transactions, new SimpleMeterRegistry(), false).verify();
        assertThat(verified.verified()).isEqualTo(capturedHead);
        assertThat(verified.intact()).isTrue();
        assertThat(trail.verify().verified()).isGreaterThan(capturedHead);
    }

    @Test
    void verifyReportsTheBacklogSeparatelyAndOnlyAStuckEventMakesTheChainNonIntact() {
        trail.linkAll();
        String resource = "/api/v1/w6-unchained-" + UUID.randomUUID();
        trail.record(new AuditTrail.Entry(null, "POST", resource, 201, "f".repeat(64)));
        AuditTrail.Verification fresh = trail.verify();
        assertThat(fresh.firstBrokenSeq()).isNull();
        assertThat(fresh.unchained()).isGreaterThanOrEqualTo(1);
        assertThat(fresh.staleUnchained()).isZero();
        assertThat(fresh.intact()).as("an event inside the linking delay is routine").isTrue();
        jdbc.update("UPDATE audit_events SET stored_at = now() - interval '10 minutes' WHERE resource = ?", resource);
        AuditTrail.Verification stuck = trail.verify();
        assertThat(stuck.staleUnchained()).isGreaterThanOrEqualTo(1);
        assertThat(stuck.intact()).as("an event outside the chain for 10 minutes").isFalse();
        trail.linkAll();
        assertThat(trail.verify().intact()).isTrue();
    }

    @Test
    void anAdminRunsBoundedIncrementalVerificationsThatContinueFromTheCheckpoint() throws Exception {
        String admin = "Bearer " + data.sessionFor(data.user().role("ADMIN").create().id());
        for (int i = 0; i < 3; i++) trail.record(new AuditTrail.Entry(null, "POST", "/api/v1/w6-incr-" + UUID.randomUUID(), 201, "f".repeat(64)));
        trail.linkAll();
        assertThat(mvc.perform(post("/api/v1/admin/audit-chain/verification/restart").header("Authorization", admin)).andReturn()
                .getResponse().getStatus()).isEqualTo(204);
        long head = jdbc.queryForObject("SELECT last_seq FROM audit_chain_head", Long.class);

        // Two positions per run: the runs walk the chain from the checkpoint, never rescanning what was proved.
        AuditTrail.IncrementalVerification first = trail.verifyIncrementally(2);
        assertThat(first.fromSeq()).isZero();
        assertThat(first.verifiedThrough()).isEqualTo(Math.min(2, head));
        AuditTrail.IncrementalVerification second = trail.verifyIncrementally(2);
        assertThat(second.fromSeq()).isEqualTo(first.verifiedThrough());
        JsonNode rest = json.readTree(mvc.perform(post("/api/v1/admin/audit-chain/verification").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString());
        assertThat(rest.get("complete").asBoolean()).isTrue();
        assertThat(rest.get("intact").asBoolean()).isTrue();
        assertThat(rest.get("verifiedThrough").asLong()).isGreaterThanOrEqualTo(head);

        // A tampered event after the checkpoint is found by the next run, which then does not move the checkpoint.
        for (int i = 0; i < 2; i++) trail.record(new AuditTrail.Entry(null, "POST", "/api/v1/w6-incr-" + UUID.randomUUID(), 201, "f".repeat(64)));
        trail.linkAll();
        long last = jdbc.queryForObject("SELECT last_seq FROM audit_chain_head", Long.class);
        Integer original = jdbc.queryForObject("SELECT result_status FROM audit_events WHERE chain_seq = ?", Integer.class, last - 1);
        jdbc.update("UPDATE audit_events SET result_status = ? WHERE chain_seq = ?", original + 1, last - 1);
        try {
            AuditTrail.IncrementalVerification broken = trail.verifyIncrementally(AuditTrail.VERIFY_MAX_ROWS_PER_RUN);
            assertThat(broken.firstBrokenSeq()).isEqualTo(last - 1);
            assertThat(broken.intact()).isFalse();
            assertThat(jdbc.queryForObject("SELECT verified_seq FROM audit_chain_checkpoint", Long.class)).isEqualTo(rest.get("verifiedThrough").asLong());
        } finally {
            jdbc.update("UPDATE audit_events SET result_status = ? WHERE chain_seq = ?", original, last - 1);
        }
        assertThat(trail.verifyIncrementally(AuditTrail.VERIFY_MAX_ROWS_PER_RUN).intact()).isTrue();

        String moderator = "Bearer " + data.sessionFor(data.user().role("MODERATOR").create().id());
        assertThat(mvc.perform(post("/api/v1/admin/audit-chain/verification").header("Authorization", moderator)).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void aDeletedHeadRowIsRecreatedFromTheChainAndCountedAsAFailure() {
        trail.record(new AuditTrail.Entry(null, "POST", "/api/v1/w6-head-" + UUID.randomUUID(), 201, "f".repeat(64)));
        trail.linkAll();
        Map<String, Object> head = jdbc.queryForMap("SELECT last_seq, last_hash, genesis_hash, cutover_at FROM audit_chain_head");
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AuditTrail linker = new AuditTrail(jdbc, transactions, meters, false);
        jdbc.update("DELETE FROM audit_chain_head");
        try {
            assertThat(linker.linkPending(AuditTrail.LINK_BATCH)).isZero();
            assertThat(meters.counter("bds.audit.chain.link.failures").count()).isEqualTo(1);
            Map<String, Object> recreated = jdbc.queryForMap("SELECT last_seq, last_hash, genesis_hash FROM audit_chain_head");
            assertThat(recreated.get("last_seq")).isEqualTo(head.get("last_seq"));
            assertThat(((String) recreated.get("last_hash")).strip()).isEqualTo(((String) head.get("last_hash")).strip());
            assertThat(((String) recreated.get("genesis_hash")).strip()).isEqualTo(((String) head.get("genesis_hash")).strip());
        } finally {
            jdbc.update("UPDATE audit_chain_head SET cutover_at = ?", head.get("cutover_at"));
        }
        assertThat(trail.verify().intact()).isTrue();
    }

    @Test
    void skippingAHeadLockedByAnotherLinkerDoesNotRefreshItsSuccessGauge() throws Exception {
        trail.linkAll();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AuditTrail linker = new AuditTrail(jdbc, transactions, meters, false);
        linker.record(new AuditTrail.Entry(null, "POST", "/api/v1/w6-busy-linker-" + UUID.randomUUID(), 201, "f".repeat(64)));
        try (java.sql.Connection held = jdbc.getDataSource().getConnection()) {
            held.setAutoCommit(false);
            try (java.sql.Statement statement = held.createStatement()) {
                statement.execute("SELECT singleton_id FROM audit_chain_head WHERE singleton_id = 1 FOR UPDATE");
                assertThat(linker.linkAll()).isZero();
                assertThat(meters.get("bds.audit.chain.backlog").gauge().value()).isGreaterThanOrEqualTo(1);
                assertThat(meters.get("bds.audit.chain.last.link.success").gauge().value()).as("no linker completed").isZero();
            } finally {
                held.rollback();
            }
        } finally {
            trail.linkAll();
        }
        linker.linkAll();
        assertThat(meters.get("bds.audit.chain.last.link.success").gauge().value()).isGreaterThan(0);
    }

    @Test
    void theBacklogGaugeIsUpdatedEvenWhenTheLinkerFails() {
        MeterRegistry meters = new SimpleMeterRegistry();
        JdbcTemplate failing = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public int update(String sql, Object... args) {
                if (sql.contains("audit_chain_head")) throw new DataAccessResourceFailureException("linker killed");
                return super.update(sql, args);
            }
        };
        AuditTrail linker = new AuditTrail(failing, transactions, meters, false);
        linker.record(new AuditTrail.Entry(null, "POST", "/api/v1/w6-gauge-" + UUID.randomUUID(), 201, "f".repeat(64)));
        assertThatThrownBy(linker::linkAll).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(meters.get("bds.audit.chain.backlog").gauge().value()).isGreaterThanOrEqualTo(1);
        assertThat(meters.get("bds.audit.chain.last.link.success").gauge().value()).as("no completed run").isZero();
        trail.linkAll();
    }

    @Test
    void aLockFailureIsA503ProblemNotA500() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/listings/import");
        ResponseEntity<com.company.bds.shared.error.ProblemDetails> answer = errors.handleLockFailure(
                new CannotAcquireLockException("deadlock detected"), request);
        assertThat(answer.getStatusCode().value()).isEqualTo(503);
        assertThat(answer.getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(answer.getBody().code()).isEqualTo("TRY_AGAIN");
    }

    @Test
    void theSpaCanReadTheTotalAndReplayHeadersCrossOrigin() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/v1/billing/plans").header("Origin", "http://localhost:3000"))
                .andReturn().getResponse();
        assertThat(response.getHeader("Access-Control-Expose-Headers"))
                .contains("X-Total-Count").contains("Idempotent-Replayed").contains("X-Order-Reused");
        assertThat(mvc.perform(options("/api/v1/billing/plans").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
