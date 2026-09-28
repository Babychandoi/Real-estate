package com.company.bds.asset;

import com.company.bds.shared.scheduling.ScheduledTaskLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Fingerprints new submissions and finds their duplicate candidates before a moderator opens them, without hooking the
 * listing write path (owned by S3a). One instance at a time, bounded batch per run.
 */
@Component
public class DuplicateDetectionSweep {
    private static final Logger log = LoggerFactory.getLogger(DuplicateDetectionSweep.class);
    private final PropertyAssetService assets;
    private final ScheduledTaskLock lock;

    public DuplicateDetectionSweep(PropertyAssetService assets, ScheduledTaskLock lock) {
        this.assets = assets;
        this.lock = lock;
    }

    @Scheduled(initialDelayString = "${app.moderation.dedupe-sweep-initial-ms:60000}",
            fixedDelayString = "${app.moderation.dedupe-sweep-ms:60000}")
    public void sweep() {
        lock.runExclusive("listing-dedupe-sweep", Duration.ofMinutes(5), this::runOnce);
    }

    /** Processes up to 100 submissions; returns how many were fingerprinted. */
    public int runOnce() {
        int done = 0;
        for (UUID[] row : assets.staleSubmissions(100)) {
            try {
                assets.refreshFingerprint(row[0], row[1]);
                assets.detectCandidates(row[0]);
                done++;
            } catch (RuntimeException ex) {
                log.warn("dedupe_sweep_failed listing={} error={}", row[0], ex.getClass().getSimpleName());
            }
        }
        return done;
    }
}
