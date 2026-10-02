package com.company.bds.shared.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * W6 {@code audit_write_failed}: the request audit trail is a hash chain ({@code previous_hash -> event_hash}).
 * Concurrent writes must neither lose an audit record nor fork the chain, and concurrent draft creations must not fail.
 * Before the fix the chain head was read without a lock (records lost on {@code event_hash UNIQUE}, forks) and the
 * draft slug was allocated check-then-insert (500 on {@code uq_listings_slug}).
 */
@BdsIntegrationTest
class AuditTrailConcurrencyTests {
    private static final String DRAFT = """
            {"purpose":"SALE","propertyType":"APARTMENT","title":"Căn hộ kiểm thử chuỗi kiểm toán đồng thời %s",
             "priceVnd":3200000000,"areaM2":55.0,"description":"Căn hộ tầng trung view thoáng, kiểm thử ghi kiểm toán song song.",
             "provinceCode":"01","districtCode":"019","wardCode":"00600","addressSummary":"Tây Mỗ, Nam Từ Liêm, Hà Nội"}
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Autowired AuditTrail trail;
    @Autowired PlatformTransactionManager transactions;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final List<Logger> loggers = new ArrayList<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        for (Class<?> type : List.of(AuditTrailFilter.class, AuditTrail.class)) {
            Logger logger = (Logger) LoggerFactory.getLogger(type);
            logger.addAppender(logs);
            loggers.add(logger);
        }
    }

    @AfterEach
    void releaseLogs() {
        loggers.forEach(logger -> logger.detachAppender(logs));
        logs.stop();
    }

    @Test
    void parallelDraftCreatesBySameAndDifferentBrokersAreAllCreatedAndAudited() throws Exception {
        List<String> bearers = new ArrayList<>();
        Set<UUID> actors = new HashSet<>();
        TestData.TestUser repeat = data.user().role("BROKER").verifiedKyc().plan("PRO", 100).create();
        actors.add(repeat.id());
        for (int i = 0; i < 5; i++) bearers.add("Bearer " + data.sessionFor(repeat.id()));
        for (int i = 0; i < 5; i++) {
            TestData.TestUser broker = data.user().role("BROKER").verifiedKyc().plan("PRO", 100).create();
            actors.add(broker.id());
            bearers.add("Bearer " + data.sessionFor(broker.id()));
        }

        List<MockHttpServletResponse> responses = parallel(bearers.size(), i -> mvc.perform(post("/api/v1/listings")
                .header("Authorization", bearers.get(i)).contentType(MediaType.APPLICATION_JSON).content(DRAFT.formatted(UUID.randomUUID())))
                .andReturn().getResponse());

        for (MockHttpServletResponse response : responses) {
            assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
        }
        assertNoAuditFailureLogged();
        Integer recorded = jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_events WHERE action = 'POST' AND resource = '/api/v1/listings'
                AND actor_id = ANY(?::uuid[]) AND result_status = 201
                """, Integer.class, (Object) actors.stream().map(UUID::toString).toArray(String[]::new));
        assertThat(recorded).as("one audit record per created draft").isEqualTo(bearers.size());
        trail.linkAll();
        assertThat(trail.verify().intact()).isTrue();
    }

    @Test
    void parallelDraftsWithTheSameTitleGetDistinctSlugsInsteadOfA500() throws Exception {
        List<String> bearers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            bearers.add("Bearer " + data.sessionFor(data.user().role("BROKER").verifiedKyc().plan("PRO", 100).create().id()));
        }
        String title = UUID.randomUUID().toString().substring(0, 8);
        List<MockHttpServletResponse> responses = parallel(bearers.size(), i -> mvc.perform(post("/api/v1/listings")
                .header("Authorization", bearers.get(i)).contentType(MediaType.APPLICATION_JSON).content(DRAFT.formatted(title)))
                .andReturn().getResponse());
        for (MockHttpServletResponse response : responses) {
            assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT slug) FROM listings WHERE slug LIKE ?", Integer.class,
                "%" + title + "%")).isEqualTo(bearers.size());
    }

    @Test
    void identicalConcurrentWritesWhileTwoLinkersRunAreAllStoredOnOneUnforkedVerifiableChain() throws Exception {
        String path = "/api/v1/listings";
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE resource = ? AND actor_id IS NULL AND result_status = 401",
                Integer.class, path);
        AtomicBoolean writing = new AtomicBoolean(true);
        ExecutorService linkers = Executors.newFixedThreadPool(2);
        List<Future<Long>> linked = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            linked.add(linkers.submit(() -> {
                long total = 0;
                while (writing.get()) total += trail.linkAll();
                return total;
            }));
        }
        // Identical method, path, status and (null) actor: only the chain position tells the records apart.
        List<MockHttpServletResponse> responses;
        try {
            responses = parallel(30, i -> mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(DRAFT.formatted("x")))
                    .andReturn().getResponse());
        } finally {
            writing.set(false);
        }
        for (Future<Long> future : linked) future.get(60, TimeUnit.SECONDS);
        linkers.shutdown();
        assertThat(responses).allSatisfy(response -> assertThat(response.getStatus()).isEqualTo(401));
        assertNoAuditFailureLogged();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE resource = ? AND actor_id IS NULL AND result_status = 401",
                Integer.class, path)).as("every request audited").isEqualTo(before + 30);

        trail.linkAll();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE event_hash IS NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM (SELECT previous_hash FROM audit_events WHERE chain_seq IS NOT NULL GROUP BY previous_hash HAVING COUNT(*) > 1) f
                """, Integer.class)).as("no two linked events share a predecessor").isZero();
        assertThat(jdbc.queryForObject("SELECT MAX(chain_seq) = COUNT(chain_seq) FROM audit_events", Boolean.class))
                .as("positions 1..n without gaps").isTrue();
        AuditTrail.Verification verification = trail.verify();
        assertThat(verification.intact()).isTrue();
        assertThat(verification.verified()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void aTamperedEventBreaksVerificationAtItsPosition() throws Exception {
        mvc.perform(post("/api/v1/listings").contentType(MediaType.APPLICATION_JSON).content(DRAFT.formatted("t")));
        trail.linkAll();
        Long last = jdbc.queryForObject("SELECT MAX(chain_seq) FROM audit_events", Long.class);
        Integer original = jdbc.queryForObject("SELECT result_status FROM audit_events WHERE chain_seq = ?", Integer.class, last);
        jdbc.update("UPDATE audit_events SET result_status = ? WHERE chain_seq = ?", original + 1, last);
        try {
            assertThat(trail.verify().firstBrokenSeq()).isEqualTo(last);
        } finally {
            jdbc.update("UPDATE audit_events SET result_status = ? WHERE chain_seq = ?", original, last);
        }
        assertThat(trail.verify().intact()).isTrue();
    }

    @Test
    void aTransientFailureIsRetriedAndAPersistentOneIsCountedAndLoggedWithItsCause() {
        AtomicInteger failuresLeft = new AtomicInteger(2);
        JdbcTemplate flaky = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public int update(String sql, Object... args) {
                if (failuresLeft.getAndDecrement() > 0) throw new DataAccessResourceFailureException("connection reset");
                return super.update(sql, args);
            }
        };
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AuditTrail retrying = new AuditTrail(flaky, transactions, meters, false);
        AuditTrail.Entry entry = new AuditTrail.Entry(null, "POST", "/api/v1/it-audit-retry", 201, "f".repeat(64));
        assertThat(retrying.record(entry)).isTrue();
        assertThat(meters.counter("bds.audit.write.retries").count()).isEqualTo(2);
        assertThat(meters.counter("bds.audit.write.failures").count()).isZero();

        failuresLeft.set(Integer.MAX_VALUE);
        assertThat(retrying.record(entry)).isFalse();
        assertThat(meters.counter("bds.audit.write.failures").count()).isEqualTo(1);
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.startsWith("audit_write_failed method=POST path=/api/v1/it-audit-retry status=201 attempts=3")
                        && message.contains("DataAccessResourceFailureException") && message.contains("connection reset"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE resource = '/api/v1/it-audit-retry'", Integer.class))
                .isEqualTo(1);
    }

    private void assertNoAuditFailureLogged() {
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.startsWith("audit_write_failed"));
    }

    interface IndexedCall<T> { T call(int index) throws Exception; }

    private static <T> List<T> parallel(int n, IndexedCall<T> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                Callable<T> task = () -> { start.await(); return call.call(index); };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(180, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
