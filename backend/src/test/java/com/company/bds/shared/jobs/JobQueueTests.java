package com.company.bds.shared.jobs;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.MutableClock;
import com.company.bds.testsupport.TestData;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** Durable queue semantics (contract §3.1) on PostgreSQL. Every test uses its own queue, so tests never see each other's jobs. */
@BdsIntegrationTest
class JobQueueTests {
    private static final Duration LEASE = Duration.ofMinutes(2);

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

    // ------------------------------------------------------------------ coalescing and versions (§3.1, m2)

    @Test
    void coalescesPendingJobsWithTheSameDedupeKey() {
        UUID first = queue.enqueue(queueName, "listing-1", Map.of("v", 1), Instant.now().plus(Duration.ofHours(1)));
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
                awaitLatch(release);
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
    void failureAfterACoalescedChangeRunsTheChangeWithoutCountingTheAttempt() {
        UUID id = jdbc.queryForObject("SELECT bds_enqueue_job(?, 'listing-1', '{\"v\":1}'::jsonb, now(), 1)", UUID.class, queueName);
        List<Integer> versions = new CopyOnWriteArrayList<>();
        TestHandler handler = new TestHandler(queueName, jobs -> {
            int version = jobs.get(0).payload().get("v").asInt();
            versions.add(version);
            if (version == 1) {
                queue.enqueue(queueName, "listing-1", Map.of("v", 2), null); // a newer change arrives during the attempt
                throw new IllegalStateException("search engine rejected version 1");
            }
            return JobBatchResult.allSucceeded(jobs);
        });

        assertThat(worker("worker-a").processBatch(handler)).isEqualTo(1);
        Map<String, Object> afterFailure = jdbc.queryForMap(
                "SELECT attempts, dead_lettered_at IS NULL AS alive, run_at <= now() AS due FROM background_jobs WHERE id = ?", id);
        assertThat(afterFailure).as("max_attempts is 1, yet the untried version 2 is neither dead-lettered nor backed off")
                .containsEntry("attempts", 0).containsEntry("alive", true).containsEntry("due", true);

        assertThat(worker("worker-a").drain(handler)).isEqualTo(1);
        assertThat(versions).containsExactly(1, 2);
        assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM background_jobs WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(counter("rerun")).isEqualTo(1.0);
    }

    @Test
    void expiredLeaseOfAnOlderVersionIsNotCountedAgainstTheCoalescedChange() {
        UUID id = jdbc.queryForObject("SELECT bds_enqueue_job(?, 'listing-1', '{\"v\":1}'::jsonb, now(), 1)", UUID.class, queueName);
        assertThat(store.claim(queueName, 1, LEASE, "crashed-worker", 10)).hasSize(1);
        queue.enqueue(queueName, "listing-1", Map.of("v", 2), null);
        jdbc.update("UPDATE background_jobs SET locked_until = now() - interval '1 second' WHERE id = ?", id);
        TestHandler handler = new TestHandler(queueName, JobBatchResult::allSucceeded);

        assertThat(worker("worker-b").processBatch(handler)).isEqualTo(1);

        assertThat(handler.seen).extracting(job -> job.payload().get("v").asInt()).as("version 2 ran instead of being dead-lettered")
                .containsExactly(2);
        assertThat(jdbc.queryForMap("SELECT attempts, completed_at IS NOT NULL AS done FROM background_jobs WHERE id = ?", id))
                .containsEntry("attempts", 0).containsEntry("done", true);
    }

    // ------------------------------------------------------------------ leases, retries, dead letters

    @Test
    void expiredLeaseIsRecoveredByAnotherWorkerAndTheStaleWorkerCannotFinishIt() {
        UUID id = queue.enqueue(queueName, "job", Map.of(), null);
        List<ClaimedJob> crashed = store.claim(queueName, 10, LEASE, "worker-crashed", 10);
        assertThat(crashed).extracting(ClaimedJob::id).containsExactly(id);
        TestHandler handler = succeeding();

        assertThat(worker("worker-b").processBatch(handler)).as("lease still valid").isZero();

        jdbc.update("UPDATE background_jobs SET locked_until = now() - interval '1 second' WHERE id = ?", id);
        assertThat(worker("worker-b").processBatch(handler)).isEqualTo(1);

        assertThat(handler.seen).extracting(ClaimedJob::id).containsExactly(id);
        assertThat(jdbc.queryForObject("SELECT attempts FROM background_jobs WHERE id = ? AND completed_at IS NOT NULL", Integer.class, id))
                .as("the expired lease of the same version counts as an attempt").isEqualTo(1);
        assertThat(store.complete(crashed.get(0), Set.of())).isEqualTo(JobStore.CompletionOutcome.LEASE_LOST);
        assertThat(store.fail(crashed.get(0), "late failure", Duration.ZERO, Set.of())).isEqualTo(JobStore.FailureOutcome.LEASE_LOST);
    }

    @Test
    void jobThatKeepsKillingItsWorkerIsDeadLettered() {
        UUID id = jdbc.queryForObject("SELECT bds_enqueue_job(?, 'poison', '{}'::jsonb, now(), 2)", UUID.class, queueName);
        for (String crashedWorker : List.of("crashed-1", "crashed-2")) {
            assertThat(store.claim(queueName, 1, LEASE, crashedWorker, 10)).hasSize(1);
            jdbc.update("UPDATE background_jobs SET locked_until = now() - interval '1 second' WHERE id = ?", id);
        }
        TestHandler handler = succeeding();

        assertThat(worker("worker-a").processBatch(handler)).isEqualTo(1);

        assertThat(handler.seen).isEmpty();
        assertThat(jdbc.queryForMap("SELECT attempts, dead_lettered_at IS NOT NULL AS dead, last_error FROM background_jobs WHERE id = ?", id))
                .containsEntry("attempts", 2).containsEntry("dead", true)
                .containsEntry("last_error", "Lease expired before completion on all 2 attempts");
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
    void jobsFollowTheHandlersCurrentRetryLimitWhicheverPathEnqueuedThem() {
        UUID fromSql = jdbc.queryForObject("SELECT bds_enqueue_job(?, 'sql', '{}'::jsonb)", UUID.class, queueName);
        UUID fromJava = queue.enqueue(queueName, "java", Map.of(), null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_jobs WHERE queue = ? AND max_attempts IS NULL", Integer.class, queueName))
                .as("no limit frozen at enqueue time").isEqualTo(2);
        TestHandler failingTwice = new TestHandler(queueName, jobs -> { throw new IllegalStateException("down"); }).withMaxAttempts(2);
        JobWorker worker = worker("worker-a");

        worker.processBatch(failingTwice);
        makeDue(fromSql);
        makeDue(fromJava);
        worker.processBatch(failingTwice);

        assertThat(jdbc.queryForList("SELECT id FROM background_jobs WHERE queue = ? AND dead_lettered_at IS NOT NULL AND attempts = 2",
                UUID.class, queueName)).containsExactlyInAnyOrder(fromSql, fromJava);
    }

    @Test
    void blankDedupeKeysAreRejectedInJavaAndInSql() {
        assertThatThrownBy(() -> queue.enqueue(queueName, "  ", Map.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.enqueueOnce(queueName, "", Map.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT bds_enqueue_job(?, '   ', '{}'::jsonb)", UUID.class, queueName))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("dedupe key must not be blank");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO background_jobs(id, queue, dedupe_key) VALUES (?, ?, '')", UUID.randomUUID(), queueName))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rows()).isZero();
    }

    @Test
    void permanentFailureIsDeadLetteredAtOnceAndSensitiveKeysAreScrubbed() {
        UUID permanent = queue.enqueue(queueName, "permanent", Map.of("secret", "one-time-link", "keep", "masked"), null);
        UUID succeeds = queue.enqueue(queueName, "succeeds", Map.of("secret", "one-time-link", "keep", "masked"), null);
        TestHandler handler = new TestHandler(queueName, jobs -> {
            JobBatchResult.Builder result = JobBatchResult.builder();
            jobs.forEach(job -> {
                if (job.id().equals(permanent)) result.failPermanently(job, "550 5.1.1 mailbox unavailable");
                else result.succeed(job);
            });
            return result.build();
        }).withSensitiveKeys(Set.of("secret"));

        assertThat(worker("worker-a").processBatch(handler)).isEqualTo(2);

        assertThat(jdbc.queryForMap("""
                SELECT attempts, dead_lettered_at IS NOT NULL AS dead, last_error, payload->>'secret' IS NOT NULL AS has_secret,
                       payload->>'keep' AS keep
                FROM background_jobs WHERE id = ?
                """, permanent)).containsEntry("attempts", 1).containsEntry("dead", true)
                .containsEntry("last_error", "550 5.1.1 mailbox unavailable").containsEntry("has_secret", false).containsEntry("keep", "masked");
        assertThat(jdbc.queryForMap("SELECT completed_at IS NOT NULL AS done, payload->>'secret' IS NOT NULL AS has_secret FROM background_jobs WHERE id = ?",
                succeeds)).containsEntry("done", true).containsEntry("has_secret", false);
        assertThat(counter("dead")).isEqualTo(1.0);
    }

    // ------------------------------------------------------------------ worker robustness (M1)

    @Test
    void errorsNeverEscapeThePollLoopAndOtherQueuesKeepRunning() {
        UUID poison = queue.enqueue(queueName, "poison", Map.of(), null);
        AtomicInteger calls = new AtomicInteger();
        TestHandler handler = new TestHandler(queueName, jobs -> {
            if (calls.incrementAndGet() == 1) throw new StackOverflowError("simulated deep recursion");
            return JobBatchResult.allSucceeded(jobs);
        });
        JobWorker worker = worker("worker-a", handler);

        assertThat(worker.runOnce()).as("the Error failed the batch instead of escaping").isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT attempts, last_error, completed_at FROM background_jobs WHERE id = ?", poison))
                .containsEntry("attempts", 1).containsEntry("last_error", "StackOverflowError: simulated deep recursion");

        // An Error outside handle() (here while reading the handler's configuration) is contained by the loop too.
        String brokenQueue = queueName + "-broken";
        JobHandler broken = new JobHandler() {
            @Override public String queue() { return brokenQueue; }
            @Override public int batchSize() { throw new OutOfMemoryError("simulated"); }
            @Override public JobBatchResult handle(List<ClaimedJob> jobs) { return JobBatchResult.allSucceeded(jobs); }
        };
        UUID next = queue.enqueue(queueName, "next", Map.of(), null);
        JobWorker mixed = worker("worker-b", broken, handler);

        assertThat(mixed.runOnce()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM background_jobs WHERE id = ?", Boolean.class, next)).isTrue();
        assertThat(registry.get("bds.jobs.processed").tags("queue", brokenQueue, "outcome", "batch_error").counter().count()).isEqualTo(1.0);
    }

    @Test
    void hungHandlerIsInterruptedAtItsTimeoutAndItsQueueIsPausedUntilItReturns() throws Exception {
        UUID first = queue.enqueue(queueName, "first", Map.of(), null);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger interrupts = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        TestHandler hanging = new TestHandler(queueName, jobs -> {
            if (calls.incrementAndGet() == 1) {
                while (true) { // a wedged client that ignores interrupts
                    try {
                        if (release.await(30, TimeUnit.SECONDS)) break;
                    } catch (InterruptedException interrupted) {
                        interrupts.incrementAndGet();
                    }
                }
            }
            return JobBatchResult.allSucceeded(jobs);
        }).withLease(Duration.ofSeconds(2)).withTimeout(Duration.ofMillis(1_500));
        JobWorker worker = worker("worker-a");

        long started = System.nanoTime();
        assertThat(worker.processBatch(hanging)).isEqualTo(1);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isBetween(Duration.ofMillis(1_400), Duration.ofSeconds(5));
        await("the handler thread was interrupted").atMost(Duration.ofSeconds(5)).until(() -> interrupts.get() > 0);
        assertThat(jdbc.queryForMap("SELECT attempts, last_error FROM background_jobs WHERE id = ?", first))
                .containsEntry("attempts", 1).containsEntry("last_error", "Handler exceeded its time budget of 1 s");
        assertThat(worker.state().pausedQueues()).containsKey(queueName);

        UUID second = queue.enqueue(queueName, "second", Map.of(), null);
        assertThat(worker.processBatch(hanging)).as("paused while the hung thread is busy").isZero();
        assertThat(registry.get("bds.jobs.handler.timeouts").tag("queue", queueName).counter().count()).isEqualTo(1.0);

        release.countDown();
        await().atMost(Duration.ofSeconds(10)).until(() -> worker.state().pausedQueues().isEmpty());
        assertThat(worker.processBatch(hanging)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM background_jobs WHERE id = ?", Boolean.class, second)).isTrue();
    }

    @Test
    void stateReportsStoppedAndStalledWorkers() throws Exception {
        MutableClock clock = MutableClock.startingNow();
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch unblock = new CountDownLatch(1);
        AtomicInteger batchSizeCalls = new AtomicInteger();
        JobHandler stallsInsideTheLoop = new JobHandler() {
            @Override public String queue() { return queueName; }
            @Override public int batchSize() { // called by the poll loop before claiming: blocks the loop itself
                if (batchSizeCalls.incrementAndGet() == 3) {
                    blocked.countDown();
                    JobQueueTests.awaitLatch(unblock);
                }
                return 1;
            }
            @Override public JobBatchResult handle(List<ClaimedJob> jobs) { return JobBatchResult.allSucceeded(jobs); }
        };
        JobWorker worker = new JobWorker(store, metrics, List.of(stallsInsideTheLoop), true, 50,
                new JobBackoff(Duration.ofSeconds(30), Duration.ofHours(1)), "worker-stall", clock, Duration.ofMinutes(2));
        JobWorkerHealthIndicator health = new JobWorkerHealthIndicator(worker);
        assertThat(worker.state().healthy()).as("not started").isFalse();

        worker.start();
        try {
            assertThat(worker.state().healthy()).isTrue();
            assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
            assertThat(blocked.await(10, TimeUnit.SECONDS)).isTrue();

            clock.advance(Duration.ofMinutes(3));
            JobWorker.State stalled = worker.state();
            assertThat(stalled.running()).isTrue();
            assertThat(stalled.heartbeatAge()).isGreaterThan(Duration.ofMinutes(2));
            assertThat(stalled.healthy()).isFalse();
            assertThat(health.health().getStatus().getCode()).isEqualTo("DOWN");

            unblock.countDown();
            await().atMost(Duration.ofSeconds(10)).until(() -> worker.state().healthy());
            assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
        } finally {
            unblock.countDown();
            worker.stop();
        }
        assertThat(worker.state().running()).isFalse();
        assertThat(health.health().getStatus().getCode()).as("enabled but stopped").isEqualTo("DOWN");

        JobWorker disabled = new JobWorker(store, metrics, List.of(), false, 50,
                new JobBackoff(Duration.ofSeconds(30), Duration.ofHours(1)), "worker-off", clock, Duration.ofMinutes(2));
        disabled.start();
        assertThat(disabled.state().running()).isFalse();
        assertThat(new JobWorkerHealthIndicator(disabled).health().getStatus().getCode()).as("disabled by configuration").isEqualTo("UP");
    }

    @Test
    void twoConcurrentWorkersNeverProcessTheSameJobTwice() throws Exception {
        int total = 200;
        for (int i = 0; i < total; i++) queue.enqueue(queueName, "job-" + i, Map.of("i", i), null);
        Map<UUID, AtomicInteger> deliveries = new ConcurrentHashMap<>();
        CyclicBarrier bothHoldAClaim = new CyclicBarrier(2);
        Function<AtomicBoolean, TestHandler> handlerForOneWorker = firstBatch -> new TestHandler(queueName, 7, jobs -> {
            if (firstBatch.compareAndSet(false, true)) {
                // Both workers hold an overlapping claim before either finishes: SKIP LOCKED must have split the jobs.
                awaitBarrier(bothHoldAClaim);
            }
            jobs.forEach(job -> deliveries.computeIfAbsent(job.id(), key -> new AtomicInteger()).incrementAndGet());
            return JobBatchResult.allSucceeded(jobs);
        });
        TestHandler handlerOfA = handlerForOneWorker.apply(new AtomicBoolean());
        TestHandler handlerOfB = handlerForOneWorker.apply(new AtomicBoolean());
        JobWorker a = worker("worker-a");
        JobWorker b = worker("worker-b");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Future<Integer> claimedByA = pool.submit(() -> { go.await(); return a.drain(handlerOfA); });
            Future<Integer> claimedByB = pool.submit(() -> { go.await(); return b.drain(handlerOfB); });
            go.countDown();

            assertThat(claimedByA.get(60, TimeUnit.SECONDS) + claimedByB.get(60, TimeUnit.SECONDS)).isEqualTo(total);
        } finally {
            pool.shutdownNow();
        }
        assertThat(handlerOfA.seen).as("both workers processed jobs").isNotEmpty();
        assertThat(handlerOfB.seen).isNotEmpty();
        assertThat(deliveries).hasSize(total);
        assertThat(deliveries.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_jobs WHERE queue = ? AND completed_at IS NOT NULL", Integer.class, queueName))
                .isEqualTo(total);
    }

    // ------------------------------------------------------------------ transactions, SQL helper, once-only

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
            assertThat(jdbc.queryForObject("SELECT run_at > now() + interval '59 minutes' AND max_attempts IS NULL FROM background_jobs WHERE id = ?",
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

    // ------------------------------------------------------------------ retention and metrics

    @Test
    void retentionPurgesCompletedJobsAfterSevenDaysAndDeadJobsAfterThirty() {
        UUID oldDone = queue.enqueue(queueName, "old-done", Map.of(), null);
        UUID recentDone = queue.enqueue(queueName, "recent-done", Map.of(), null);
        UUID pending = queue.enqueue(queueName, "pending", Map.of(), null);
        UUID oldDead = queue.enqueue(queueName, "old-dead", Map.of(), null);
        UUID recentDead = queue.enqueue(queueName, "recent-dead", Map.of(), null);
        jdbc.update("UPDATE background_jobs SET completed_at = now() - interval '8 days' WHERE id = ?", oldDone);
        jdbc.update("UPDATE background_jobs SET completed_at = now() - interval '1 day' WHERE id = ?", recentDone);
        jdbc.update("UPDATE background_jobs SET dead_lettered_at = now() - interval '31 days' WHERE id = ?", oldDead);
        jdbc.update("UPDATE background_jobs SET dead_lettered_at = now() - interval '29 days' WHERE id = ?", recentDead);

        JobRetentionTask.Purged purged = retention.purgeNow();

        assertThat(purged.completed()).isGreaterThanOrEqualTo(1);
        assertThat(purged.dead()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id FROM background_jobs WHERE queue = ?", UUID.class, queueName))
                .containsExactlyInAnyOrder(recentDone, pending, recentDead);
    }

    @Test
    void metricsReportPendingDeadAndLagPerQueueWithoutAnyWorker() {
        UUID due = queue.enqueue(queueName, "due", Map.of(), null);
        queue.enqueue(queueName, "future", Map.of(), Instant.now().plus(Duration.ofHours(2)));
        UUID dead = queue.enqueue(queueName, "dead", Map.of(), null);
        jdbc.update("UPDATE background_jobs SET run_at = now() - interval '90 seconds' WHERE id = ?", due);
        jdbc.update("UPDATE background_jobs SET dead_lettered_at = now() WHERE id = ?", dead);

        metrics.refresh();

        assertThat(gauge("bds.jobs.pending")).isEqualTo(2.0);
        assertThat(gauge("bds.jobs.dead")).isEqualTo(1.0);
        double lag = gauge("bds.jobs.lag.seconds");
        assertThat(lag).isBetween(89.0, 150.0);
        await().atMost(Duration.ofSeconds(5)).until(() -> gauge("bds.jobs.lag.seconds") > lag);
        assertThat(registry.get("bds.jobs.metrics.age.seconds").gauge().value()).isBetween(0.0, 30.0);

        worker("worker-a").drain(succeeding());
        metrics.refresh();
        assertThat(gauge("bds.jobs.pending")).isEqualTo(1.0);
        assertThat(gauge("bds.jobs.lag.seconds")).isZero();
        assertThat(counter("success")).isEqualTo(1.0);
    }

    // ------------------------------------------------------------------ helpers

    private JobWorker worker(String id, JobHandler... handlers) {
        return new JobWorker(store, metrics, List.of(handlers), false, 1_000, new JobBackoff(Duration.ofSeconds(30), Duration.ofHours(1)), id,
                Clock.systemUTC(), Duration.ofMinutes(2));
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

    private double gauge(String name) {
        return registry.get(name).tag("queue", queueName).gauge().value();
    }

    static void awaitLatch(CountDownLatch latch) {
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
        private Duration lease = LEASE;
        private Duration timeout;
        private int maxAttempts = 10;
        private Set<String> sensitiveKeys = Set.of();

        TestHandler(String queue, Function<List<ClaimedJob>, JobBatchResult> behaviour) { this(queue, 20, behaviour); }

        TestHandler(String queue, int batchSize, Function<List<ClaimedJob>, JobBatchResult> behaviour) {
            this.queue = queue;
            this.batchSize = batchSize;
            this.behaviour = behaviour;
        }

        TestHandler withLease(Duration value) { lease = value; return this; }

        TestHandler withTimeout(Duration value) { timeout = value; return this; }

        TestHandler withMaxAttempts(int value) { maxAttempts = value; return this; }

        TestHandler withSensitiveKeys(Set<String> keys) { sensitiveKeys = keys; return this; }

        @Override public String queue() { return queue; }

        @Override public int batchSize() { return batchSize; }

        @Override public Duration lease() { return lease; }

        @Override public Duration timeout() { return timeout == null ? JobHandler.super.timeout() : timeout; }

        @Override public int maxAttempts() { return maxAttempts; }

        @Override public Set<String> sensitivePayloadKeys() { return sensitiveKeys; }

        @Override
        public JobBatchResult handle(List<ClaimedJob> jobs) {
            seen.addAll(jobs);
            return behaviour.apply(jobs);
        }
    }
}
