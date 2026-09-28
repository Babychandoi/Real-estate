package com.company.bds.search.infrastructure.indexing;

import com.company.bds.search.infrastructure.ReadModelRefresher;
import com.company.bds.shared.scheduling.ScheduledTaskLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Daily, cluster-wide once (task lock): listings whose identity or ownership check expired since their last refresh are
 * re-indexed so their trust status and the {@code verified} filter of the index follow the expiry. Reads already treat
 * an expired check as EXPIRED at request time; this keeps stored statuses and the index in line.
 */
@Component
public class TrustExpiryReindexTask {
    private static final Logger log = LoggerFactory.getLogger(TrustExpiryReindexTask.class);
    static final int MAX_PER_RUN = 50_000;

    private final ReadModelRefresher refresher;
    private final ScheduledTaskLock taskLock;

    public TrustExpiryReindexTask(ReadModelRefresher refresher, ScheduledTaskLock taskLock) {
        this.refresher = refresher;
        this.taskLock = taskLock;
    }

    @Scheduled(cron = "${app.search.trust-expiry-cron:0 40 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        taskLock.runExclusive("search-trust-expiry", Duration.ofMinutes(10), Duration.ofHours(1), () -> {
            int enqueued = refresher.enqueueExpiredTrust(MAX_PER_RUN);
            if (enqueued > 0) log.info("Re-indexing {} listings whose trust check expired", enqueued);
        });
    }

    /** Test hook: one run without the schedule. */
    public int runOnce() {
        return refresher.enqueueExpiredTrust(MAX_PER_RUN);
    }
}
