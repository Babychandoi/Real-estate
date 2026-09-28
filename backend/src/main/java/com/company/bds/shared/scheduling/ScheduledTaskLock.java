package com.company.bds.shared.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Cluster-wide mutual exclusion for {@code @Scheduled} business tasks (contract §3.3), backed by {@code scheduled_task_locks}.
 * Every instance keeps its schedule; only the one that takes an unexpired lock runs the task. The lock is taken and
 * released in their own transactions, so they are visible to other instances immediately, whatever the caller does.
 * Times come from the database, so clock skew between instances only shifts when their schedules fire.
 *
 * <p>The lock is a lease, not a fence: a task that runs longer than {@code maxRun} may overlap with the next holder.
 * Tasks guarded by it must therefore be idempotent and choose {@code maxRun} well above their normal duration (today's
 * tasks are: orphan cleanup, full search sync, job purge). Use {@code minHold} for periodic tasks that should run once per
 * period across N instances rather than once per instance.
 */
@Component
public class ScheduledTaskLock {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskLock.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;
    private final String instanceId;

    @Autowired
    public ScheduledTaskLock(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this(jdbc, transactionManager, defaultInstanceId());
    }

    public ScheduledTaskLock(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, String instanceId) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.instanceId = instanceId;
    }

    /**
     * Runs the task if no other instance holds the lock. {@code maxRun} bounds how long a crashed holder blocks the task.
     *
     * @return whether the task ran on this instance
     */
    public boolean runExclusive(String name, Duration maxRun, Runnable task) {
        return runExclusive(name, maxRun, Duration.ZERO, task);
    }

    /**
     * Like {@link #runExclusive(String, Duration, Runnable)} but keeps the lock for at least {@code minHold} after the task
     * started, so an instance whose schedule fires a little later (clock skew) does not run a daily task a second time.
     */
    public boolean runExclusive(String name, Duration maxRun, Duration minHold, Runnable task) {
        Objects.requireNonNull(task);
        if (maxRun.isNegative() || maxRun.isZero() || minHold.isNegative() || minHold.compareTo(maxRun) > 0) {
            throw new IllegalArgumentException("Lock durations must satisfy 0 <= minHold <= maxRun and maxRun > 0");
        }
        String holder = holderId();
        Integer acquired = ownTransaction.execute(status -> jdbc.update("""
                INSERT INTO scheduled_task_locks (name, locked_until, locked_by)
                VALUES (?, now() + make_interval(secs => ?), ?)
                ON CONFLICT (name) DO UPDATE SET locked_until = EXCLUDED.locked_until, locked_by = EXCLUDED.locked_by
                WHERE scheduled_task_locks.locked_until <= now()
                """, name, seconds(maxRun), holder));
        if (acquired == null || acquired == 0) {
            log.debug("scheduled_task_skipped task={} reason=locked_elsewhere instance={}", name, instanceId);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            // locked_until - maxRun is the acquisition time; keep at least minHold from then, otherwise release now.
            ownTransaction.executeWithoutResult(status -> jdbc.update("""
                    UPDATE scheduled_task_locks
                    SET locked_until = GREATEST(now(), locked_until - make_interval(secs => ?) + make_interval(secs => ?))
                    WHERE name = ? AND locked_by = ?
                    """, seconds(maxRun), seconds(minHold), name, holder));
        }
    }

    public String instanceId() { return instanceId; }

    private String holderId() {
        String holder = instanceId + "#" + UUID.randomUUID().toString().substring(0, 8);
        return holder.length() <= 120 ? holder : holder.substring(holder.length() - 120);
    }

    private static double seconds(Duration duration) {
        return duration.toMillis() / 1000.0;
    }

    private static String defaultInstanceId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            host = "unknown-host";
        }
        return host + "/" + ProcessHandle.current().pid();
    }
}
