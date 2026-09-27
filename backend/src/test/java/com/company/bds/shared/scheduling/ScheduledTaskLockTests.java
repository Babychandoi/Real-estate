package com.company.bds.shared.scheduling;

import com.company.bds.shared.jobs.JobRetentionTask;
import com.company.bds.shared.jobs.JobQueue;
import com.company.bds.testsupport.BdsIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Two lock instances stand for two application instances sharing the database. */
@BdsIntegrationTest
class ScheduledTaskLockTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JobRetentionTask retentionTask;
    @Autowired JobQueue queue;

    private ScheduledTaskLock instanceA;
    private ScheduledTaskLock instanceB;
    private String task;

    @BeforeEach
    void twoInstances() {
        instanceA = new ScheduledTaskLock(jdbc, transactionManager, "instance-a");
        instanceB = new ScheduledTaskLock(jdbc, transactionManager, "instance-b");
        task = "test-task-" + UUID.randomUUID();
    }

    @Test
    void onlyOneInstanceRunsTheTaskAtATime() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger runsOnB = new AtomicInteger();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> ranOnA = pool.submit(() -> instanceA.runExclusive(task, Duration.ofMinutes(5), () -> {
                running.countDown();
                awaitQuietly(finish);
            }));
            assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(instanceB.runExclusive(task, Duration.ofMinutes(5), runsOnB::incrementAndGet)).isFalse();
            assertThat(runsOnB).hasValue(0);
            assertThat(jdbc.queryForObject("SELECT locked_by FROM scheduled_task_locks WHERE name = ?", String.class, task))
                    .startsWith("instance-a#");

            finish.countDown();
            assertThat(ranOnA.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(instanceB.runExclusive(task, Duration.ofMinutes(5), runsOnB::incrementAndGet)).isTrue();
        assertThat(runsOnB).hasValue(1);
    }

    @Test
    void lockOfACrashedInstanceIsTakenOverOnceItExpires() {
        jdbc.update("INSERT INTO scheduled_task_locks(name, locked_until, locked_by) VALUES (?, now() + interval '1 minute', 'crashed#1')", task);
        assertThat(instanceB.runExclusive(task, Duration.ofMinutes(5), () -> { })).isFalse();

        jdbc.update("UPDATE scheduled_task_locks SET locked_until = now() - interval '1 second' WHERE name = ?", task);
        assertThat(instanceB.runExclusive(task, Duration.ofMinutes(5), () -> { })).isTrue();
    }

    @Test
    void failingTaskReleasesTheLockAndPropagatesTheError() {
        assertThatThrownBy(() -> instanceA.runExclusive(task, Duration.ofMinutes(5), () -> { throw new IllegalStateException("boom"); }))
                .hasMessage("boom");
        assertThat(instanceB.runExclusive(task, Duration.ofMinutes(5), () -> { })).isTrue();
    }

    @Test
    void minimumHoldStopsASecondInstanceFromRepeatingADailyTask() {
        assertThat(instanceA.runExclusive(task, Duration.ofMinutes(10), Duration.ofMinutes(5), () -> { })).isTrue();

        assertThat(instanceB.runExclusive(task, Duration.ofMinutes(10), Duration.ofMinutes(5), () -> { })).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT locked_until BETWEEN now() + interval '4 minutes' AND now() + interval '5 minutes'
                FROM scheduled_task_locks WHERE name = ?
                """, Boolean.class, task)).isTrue();
    }

    @Test
    void lockIsVisibleToOtherInstancesEvenInsideACallerTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        AtomicInteger runsOnB = new AtomicInteger();
        transaction.executeWithoutResult(status -> instanceA.runExclusive(task, Duration.ofMinutes(5),
                () -> assertThat(instanceB.runExclusive(task, Duration.ofMinutes(5), runsOnB::incrementAndGet)).isFalse()));
        assertThat(runsOnB).hasValue(0);
    }

    @Test
    void scheduledJobPurgeSkipsWhileAnotherInstanceHoldsItsLock() {
        UUID old = queue.enqueue("test-purge-" + task.substring(10, 18), null, Map.of(), null);
        jdbc.update("UPDATE background_jobs SET completed_at = now() - interval '8 days' WHERE id = ?", old);
        jdbc.update("""
                INSERT INTO scheduled_task_locks(name, locked_until, locked_by) VALUES ('background-jobs-purge', now() + interval '1 minute', 'other#1')
                ON CONFLICT (name) DO UPDATE SET locked_until = EXCLUDED.locked_until, locked_by = EXCLUDED.locked_by
                """);
        try {
            retentionTask.purgeCompletedJobs();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_jobs WHERE id = ?", Integer.class, old)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM scheduled_task_locks WHERE name = 'background-jobs-purge'");
        }
        retentionTask.purgeCompletedJobs();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_jobs WHERE id = ?", Integer.class, old)).isZero();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
