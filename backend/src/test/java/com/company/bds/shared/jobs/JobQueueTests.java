package com.company.bds.shared.jobs;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Durable queue semantics (contract §3.1) on PostgreSQL. Every test uses its own queue, so tests never see each other's jobs. */
@BdsIntegrationTest
class JobQueueTests {
    @Autowired JobQueue queue;
    @Autowired JobStore store;
    @Autowired JobMetrics metrics;
    @Autowired JobRetentionTask retention;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MeterRegistry registry;
    @Autowired TestData data;

    private String queueName;

    @BeforeEach
    void useFreshQueue() {
        queueName = "test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void coalescesPendingJobsWithTheSameDedupeKey() {
        UUID first = queue.enqueue(queueName, "listing-1", Map.of("v", 1), java.time.Instant.now().plus(Duration.ofHours(1)));
        UUID second = queue.enqueue(queueName, "listing-1", Map.of("v", 2), null);
        UUID other = queue.enqueue(queueName, "listing-2", Map.of("v", 9), null);

        assertThat(second).isEqualTo(first);
        assertThat(other).isNotEqualTo(first);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT payload->>'v' AS v, enqueue_seq, run_at <= now() AS due FROM background_jobs WHERE id = ?
                """, first);
        assertThat(row).containsEntry("v", "2").containsEntry("enqueue_seq", 2L).containsEntry("due", true);
        assertThat(rows()).isEqualTo(2);

        queue.enqueue(queueName, null, Map.of(), null);
        queue.enqueue(queueName, null, Map.of(), null);
        assertThat(rows()).as("jobs without a dedupe key never coalesce").isEqualTo(4);
    }

    @Test
    void changeCoalescedWhileAJobRunsIsProcessedAfterwards() throws Exception {
        UUID id = queue.enqueue(queueName, "listing-1", Map.of("v", 1), null);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> versions = new CopyOnWriteArrayList<>();
        TestHandler handler = new TestHandler(queueName, jobs -> {
            jobs.forEach(job -> versions.add(job.payload().get("v").asInt()));
            if (versions.size() == 1) {
                started.countDown();
                await(release);
            }
            return JobBatchResult.allSucceeded(jobs);
        });
        JobWorker worker = worker("worker-a");

        CompletableFuture<Integer> firstRun = CompletableFuture.supplyAsync(() -> worker.processBatch(handler));
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(queue.enqueue(queueName, "listing-1", Map.of("v", 2), null)).isEqualTo(id);
        release.countDown();
        assertThat(firstRun.get(10, TimeUnit.SECONDS)).isEqualTo(1);

        Map<String, Object> afterFirst = jdbc.queryForMap(
                "SELECT completed_at IS NULL AS pending, attempts, enqueue_seq, locked_until FROM background_jobs WHERE id = ?", id);
        assertThat(afterFirst).containsEntry("pending", true).containsEntry("attempts", 0).containsEntry("enqueue_seq", 2L);
        assertThat(afterFirst.get("locked_until")).isNull();

        assertThat(worker.drain(handler)).isEqualTo(1);
        assertThat(versions).containsExactly(1, 2);
        assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL AND attempts = 0 FROM background_jobs WHERE id = ?", Boolean.class, id))
                .isTrue();
        assertThat(rows()).isEqualTo(1);
        assertThat(counter("rerun")).isEqualTo(1.0);
        assertThat(counter("success")).isEqualTo(1.0);
    }

    @Test
    void expiredLeaseIsRecoveredByAnotherWorkerAndTheStaleWorkerCannotFinishIt() {
        UUID id = queue.enqueue(queueName, "job", Map.of(), null);
        List<ClaimedJob> crashed = store.claim(queueName, 10, Duration.ofMinutes(2), "worker-crashed");
        assertThat(crashed).extracting(ClaimedJob::id).containsExactly(id);
        TestHandler handler = succeeding();

        assertThat(worker("worker-b").processBatch(handler)).as("lease still valid").isZero();

        jdbc.update("UPDATE background_jobs SET locked_until = now() - interval '1 second' WHERE id = ?", id);
        assertThat(worker("worker-b").processBatch(handler)).isEqualTo(1);

        assertThat(handler.seen).extracting(ClaimedJob::id).containsExactly(id);
        assertThat(jdbc.queryForObject("SELECT attempts FROM background_jobs WHERE id = ? AND completed_at IS NOT NULL", Integer.class, id))
                .as("the expired lease counts as an attempt").isEqualTo(1);
        assertThat(store.complete(crashed.get(0))).isEqualTo(JobStore.CompletionOutcome.LEASE_LOST);
        assertThat(store.fail(crashed.get(0), "late failure", Duration.ZERO)).isEqualTo(JobStore.FailureOutcome.LEASE_LOST);
    }

    @Test
    void jobThatKeepsKillingItsWorkerIsDeadLettered() {
        UUID id = jdbc.queryForObject("SELECT bds_enqueue_job(?, 'poison', '{}'::jsonb, now(), 2)", UUID.class, queueName);
        for (String crashedWorker : List.of("crashed-1", "crashed-2")) {
            assertThat(store.claim(queueName, 1, Duration.ofMinutes(2), crashedWorker)).hasSize(1);
            jdbc.update("UPDATE background_jobs SET locked_until = now() - interval '1 second' WHERE id = ?", id);
        }
        TestHandler handler = succeeding();

        assertThat(worker("worker-a").processBatch(handler)).isEqualTo(1);

        assertThat(handler.seen).isEmpty();
        assertThat(jdbc.queryForObject("SELECT dead_lettered_at IS NOT NULL FROM background_jobs WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT last_error FROM background_jobs WHERE id = ?", String.class, id)).contains("Lease expired");
    }

    @Test
    void failedAttemptsBackOffExponentiallyAndDeadLetterAtMaxAttempts() {
        UUID id = jdbc.queryForObject("SELECT bds_enqueue_job(?, 'always-fails', '{}'::jsonb, now(), 3)", UUID.class, queueName);
        TestHandler failing = new TestHandler(queueName, jobs -> {
            throw new IllegalStateException("SMTP 421 khi gửi tới khach.hang@example.test, SĐT 0912 345 678");
        });
        JobWorker worker = worker("worker-a");

        assertThat(worker.processBatch(failing)).isEqualTo(1);
        assertRetryScheduled(id, 1, 30);
        String error = jdbc.queryForObject("SELECT last_error FROM background_jobs WHERE id = ?", String.class, id);
        assertThat(error).contains("IllegalStateException", "SMTP 421", "<email>", "<phone>")
                .doesNotContain("khach.hang@example.test").doesNotContain("345 678");

        assertThat(worker.processBatch(failing)).as("not due before the backoff").isZero();
        makeDue(id);
        assertThat(worker.processBatch(failing)).isEqualTo(1);
        assertRetryScheduled(id, 2, 60);

        makeDue(id);
        assertThat(worker.processBatch(failing)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempts FROM background_jobs WHERE id = ? AND dead_lettered_at IS NOT NULL", Integer.class, id))
                .isEqualTo(3);
        makeDue(id);
        assertThat(worker.processBatch(failing)).as("dead-lettered jobs are never claimed").isZero();
        assertThat(counter("retry")).isEqualTo(2.0);
        assertThat(counter("dead")).isEqualTo(1.0);

        UUID fresh = queue.enqueue(queueName, "always-fails", Map.of(), null);
        assertThat(fresh).as("a dead-lettered key no longer blocks new jobs").isNotEqualTo(id);
    }

    @Test
    void twoConcurrentWorkersNeverProcessTheSameJobTwice() throws Exception {
        int total = 200;
        for (int i = 0; i < total; i++) queue.enqueue(queueName, "job-" + i, Map.of("i", i), null);
        Map<UUID, AtomicInteger> deliveries = new ConcurrentHashMap<>();
        Set<String> threadsWithABatch = ConcurrentHashMap.newKeySet();
        CyclicBarrier bothHoldAClaim = new CyclicBarrier(2);
        TestHandler handler = new TestHandler(queueName, 7, jobs -> {
            if (threadsWithABatch.add(Thread.currentThread().getName())) {
                // Both workers hold an overlapping claim before either finishes: SKIP LOCKED must have split the jobs.
                awaitBarrier(bothHoldAClaim);
            }
            jobs.forEach(job -> deliveries.computeIfAbsent(job.id(), key -> new AtomicInteger()).incrementAndGet());
            return JobBatchResult.allSucceeded(jobs);
        });
        JobWorker a = worker("worker-a");
        JobWorker b = worker("worker-b");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Future<Integer> claimedByA = pool.submit(() -> { go.await(); return a.drain(handler); });
            Future<Integer> claimedByB = pool.submit(() -> { go.await(); return b.drain(handler); });
            go.countDown();

            assertThat(claimedByA.get(60, TimeUnit.SECONDS) + claimedByB.get(60, TimeUnit.SECONDS)).isEqualTo(total);
        } finally {
            pool.shutdownNow();
        }
        assertThat(threadsWithABatch).hasSize(2);
        assertThat(deliveries).hasSize(total);
        assertThat(deliveries.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_jobs WHERE queue = ? AND completed_at IS NOT NULL", Integer.class, queueName))
                .isEqualTo(total);
    }

    @Test
    void enqueueCommitsAndRollsBackWithTheBusinessTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        String email = "rolled-back-" + UUID.randomUUID() + "@example.test";

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            data.user().email(email).create();
            queue.enqueue(queueName, "rolled-back", Map.of("email", email), null);
            throw new IllegalStateException("business failure");
        })).hasMessage("business failure");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email)).isZero();
        assertThat(rows()).isZero();

        transaction.executeWithoutResult(status -> {
            data.user().email(email).create();
            queue.enqueue(queueName, "committed", Map.of("email", email), null);
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email)).isEqualTo(1);
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void sqlHelperLetsTriggersEnqueueAndCoalesce() {
        String suffix = queueName.substring(5);
        String table = "s0be_trigger_probe_" + suffix;
        jdbc.execute("CREATE TABLE " + table + " (id uuid PRIMARY KEY, label text NOT NULL)");
        jdbc.execute("CREATE FUNCTION " + table + "_enqueue() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "PERFORM bds_enqueue_job('" + queueName + "', NEW.id::text, jsonb_build_object('id', NEW.id, 'label', NEW.label)); "
                + "RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER " + table + "_jobs AFTER INSERT OR UPDATE ON " + table
                + " FOR EACH ROW EXECUTE FUNCTION " + table + "_enqueue()");
        try {
            UUID first = UUID.randomUUID();
            jdbc.update("INSERT INTO " + table + "(id, label) VALUES (?, 'first')", first);
            jdbc.update("UPDATE " + table + " SET label = 'second' WHERE id = ?", first);
            jdbc.update("INSERT INTO " + table + "(id, label) VALUES (?, 'other')", UUID.randomUUID());
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO " + table + "(id, label) VALUES (?, 'rolled back')", UUID.randomUUID());
                throw new IllegalStateException("rollback");
            })).hasMessage("rollback");

            assertThat(rows()).isEqualTo(2);
            assertThat(jdbc.queryForMap("SELECT payload->>'label' AS label, enqueue_seq FROM background_jobs WHERE queue = ? AND dedupe_key = ?",
                    queueName, first.toString())).containsEntry("label", "second").containsEntry("enqueue_seq", 2L);

            UUID delayed = jdbc.queryForObject("SELECT bds_enqueue_job(?, 'delayed', '{}'::jsonb, now() + interval '1 hour')",
                    UUID.class, queueName);
            assertThat(jdbc.queryForObject("SELECT run_at > now() + interval '59 minutes' AND max_attempts = 10 FROM background_jobs WHERE id = ?",
                    Boolean.class, delayed)).isTrue();
        } finally {
            jdbc.execute("DROP TABLE " + table);
            jdbc.execute("DROP FUNCTION " + table + "_enqueue()");
        }
    }

    @Test
    void enqueueOnceRefusesKeysThatArePendingOrAlreadyProcessed() {
        Optional<UUID> first = queue.enqueueOnce(queueName, "mail-1", Map.of("n", 1), null);
        Optional<UUID> duplicate = queue.enqueueOnce(queueName, "mail-1", Map.of("n", 2), null);

        assertThat(first).isPresent();
        assertThat(duplicate).isEmpty();
        assertThat(jdbc.queryForObject("SELECT payload->>'n' FROM background_jobs WHERE id = ?", String.class, first.get())).isEqualTo("1");

        assertThat(worker("worker-a").drain(succeeding())).isEqualTo(1);
        assertThat(queue.enqueueOnce(queueName, "mail-1", Map.of("n", 3), null)).as("already processed").isEmpty();

        UUID dead = queue.enqueueOnce(queueName, "mail-2", Map.of(), null).orElseThrow();
        jdbc.update("UPDATE background_jobs SET dead_lettered_at = now() WHERE id = ?", dead);
        assertThat(queue.enqueueOnce(queueName, "mail-2", Map.of(), null)).as("a dead-lettered job may be replaced").isPresent();
    }

    @Test
    void retentionPurgesOnlyCompletedJobsOlderThanSevenDays() {
        UUID old = queue.enqueue(queueName, "old", Map.of(), null);
        UUID recent = queue.enqueue(queueName, "recent", Map.of(), null);
        UUID pending = queue.enqueue(queueName, "pending", Map.of(), null);
        UUID dead = queue.enqueue(queueName, "dead", Map.of(), null);
        jdbc.update("UPDATE background_jobs SET completed_at = now() - interval '8 days' WHERE id = ?", old);
        jdbc.update("UPDATE background_jobs SET completed_at = now() - interval '1 day' WHERE id = ?", recent);
        jdbc.update("UPDATE background_jobs SET dead_lettered_at = now() - interval '30 days' WHERE id = ?", dead);

        assertThat(retention.purgeNow()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForList("SELECT id FROM background_jobs WHERE queue = ?", UUID.class, queueName))
                .containsExactlyInAnyOrder(recent, pending, dead);
    }

    @Test
    void metricsReportPendingDeadAndLagPerQueue() {
        UUID due = queue.enqueue(queueName, "due", Map.of(), null);
        queue.enqueue(queueName, "future", Map.of(), java.time.Instant.now().plus(Duration.ofHours(2)));
        UUID dead = queue.enqueue(queueName, "dead", Map.of(), null);
        jdbc.update("UPDATE background_jobs SET run_at = now() - interval '90 seconds' WHERE id = ?", due);
        jdbc.update("UPDATE background_jobs SET dead_lettered_at = now() WHERE id = ?", dead);

        metrics.refresh();

        assertThat(registry.get("bds.jobs.pending").tag("queue", queueName).gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("bds.jobs.dead").tag("queue", queueName).gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("bds.jobs.lag.seconds").tag("queue", queueName).gauge().value()).isBetween(89.0, 150.0);

        worker("worker-a").drain(succeeding());
        metrics.refresh();
        assertThat(registry.get("bds.jobs.pending").tag("queue", queueName).gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("bds.jobs.lag.seconds").tag("queue", queueName).gauge().value()).isZero();
        assertThat(counter("success")).isEqualTo(1.0);
    }

    // ------------------------------------------------------------------ helpers

    private JobWorker worker(String id) {
        return new JobWorker(store, metrics, List.of(), false, 1_000, new JobBackoff(Duration.ofSeconds(30), Duration.ofHours(1)), id);
    }

    private TestHandler succeeding() {
        return new TestHandler(queueName, JobBatchResult::allSucceeded);
    }

    private int rows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM background_jobs WHERE queue = ?", Integer.class, queueName);
    }

    private void makeDue(UUID id) {
        jdbc.update("UPDATE background_jobs SET run_at = now() WHERE id = ?", id);
    }

    private void assertRetryScheduled(UUID id, int attempts, int baseSeconds) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT attempts, dead_lettered_at IS NULL AS alive, EXTRACT(EPOCH FROM run_at - now())::float8 AS delay_seconds
                FROM background_jobs WHERE id = ?
                """, id);
        assertThat(row).containsEntry("attempts", attempts).containsEntry("alive", true);
        assertThat((Double) row.get("delay_seconds")).isBetween(baseSeconds * 0.8 - 2, baseSeconds * 1.2);
    }

    private double counter(String outcome) {
        var found = registry.find("bds.jobs.processed").tags("queue", queueName, "outcome", outcome).counter();
        return found == null ? 0 : found.count();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("latch timed out");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(20, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("the other worker never claimed a batch", ex);
        }
    }

    static final class TestHandler implements JobHandler {
        final List<ClaimedJob> seen = new CopyOnWriteArrayList<>();
        private final String queue;
        private final int batchSize;
        private final Function<List<ClaimedJob>, JobBatchResult> behaviour;

        TestHandler(String queue, Function<List<ClaimedJob>, JobBatchResult> behaviour) { this(queue, 20, behaviour); }

        TestHandler(String queue, int batchSize, Function<List<ClaimedJob>, JobBatchResult> behaviour) {
            this.queue = queue;
            this.batchSize = batchSize;
            this.behaviour = behaviour;
        }

        @Override public String queue() { return queue; }

        @Override public int batchSize() { return batchSize; }

        @Override
        public JobBatchResult handle(List<ClaimedJob> jobs) {
            seen.addAll(jobs);
            return behaviour.apply(jobs);
        }
    }
}
