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
    void verifyReportsEventsLeftOutsideTheChainAndAnAdminCanRunIt() throws Exception {
        trail.linkAll();
        trail.record(new AuditTrail.Entry(null, "POST", "/api/v1/w6-unchained-" + UUID.randomUUID(), 201, "f".repeat(64)));
        AuditTrail.Verification pending = trail.verify();
        assertThat(pending.firstBrokenSeq()).as("the chain itself is valid").isNull();
        assertThat(pending.unchained()).isGreaterThanOrEqualTo(1);
        assertThat(pending.intact()).as("not intact while an event is outside the chain").isFalse();

        String admin = "Bearer " + data.sessionFor(data.user().role("ADMIN").create().id());
        JsonNode view = json.readTree(mvc.perform(get("/api/v1/admin/audit-chain/verification").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString());
        assertThat(view.get("unchainedEvents").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(view.get("intact").asBoolean()).isFalse();

        trail.linkAll();
        JsonNode linked = json.readTree(mvc.perform(get("/api/v1/admin/audit-chain/verification").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString());
        assertThat(linked.get("intact").asBoolean()).isTrue();
        assertThat(linked.get("unchainedEvents").asLong()).isZero();
        String moderator = "Bearer " + data.sessionFor(data.user().role("MODERATOR").create().id());
        assertThat(mvc.perform(get("/api/v1/admin/audit-chain/verification").header("Authorization", moderator)).andReturn()
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
