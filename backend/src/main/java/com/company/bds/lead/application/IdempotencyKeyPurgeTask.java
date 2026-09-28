package com.company.bds.lead.application;

import com.company.bds.shared.scheduling.ScheduledTaskLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;

/**
 * Deletes lead idempotency keys (scopes {@code lead:<actor>} and the legacy {@code PUBLIC_LEAD}; other modules keep
 * their own policy) past their retention (F17.2: 24 h; legacy rows without {@code expires_at} count from
 * {@code created_at}). Runs under {@link ScheduledTaskLock} so only one instance purges; bounded batches keep locks short.
 */
@Component
public class IdempotencyKeyPurgeTask {
    private static final int BATCH = 5_000;

    private final JdbcTemplate jdbc;
    private final ScheduledTaskLock lock;
    private final Clock clock;

    public IdempotencyKeyPurgeTask(JdbcTemplate jdbc, ScheduledTaskLock lock, Clock clock) {
        this.jdbc = jdbc;
        this.lock = lock;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.leads.idempotency-purge-cron:0 17 * * * *}")
    public void scheduled() {
        lock.runExclusive("idempotency-key-purge", Duration.ofMinutes(10), this::purge);
    }

    /** @return rows deleted */
    public int purge() {
        Timestamp now = Timestamp.from(clock.instant());
        int total = 0;
        for (int round = 0; round < 100; round++) {
            int deleted = jdbc.update("""
                    DELETE FROM api_idempotency_keys WHERE ctid IN (
                        SELECT ctid FROM api_idempotency_keys
                        WHERE (scope LIKE 'lead:%' OR scope = 'PUBLIC_LEAD')
                          AND COALESCE(expires_at, created_at + interval '24 hours') <= ? LIMIT ?)
                    """, now, BATCH);
            total += deleted;
            if (deleted < BATCH) break;
        }
        return total;
    }
}
