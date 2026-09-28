package com.company.bds.lead.application;

import com.company.bds.shared.scheduling.ScheduledTaskLock;
import com.company.bds.shared.security.PiiProtectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Resumable encryption of reporter phone numbers stored in plaintext before V057 (the key lives in the secret store,
 * so SQL cannot do it). It runs as a scheduled background task (never an {@code ApplicationRunner}, so a large table
 * does not delay readiness): first shortly after startup, then every hour, on one instance at a time (task lock), in
 * batches of 500 rows, each in its own transaction with {@code FOR UPDATE SKIP LOCKED}. Already protected values
 * ("v1:") are never touched, so a crash or a second run simply continues. Blank values become NULL. Nothing about the
 * numbers is logged.
 *
 * <p>Rolling deploy: an instance of the previous release keeps writing plaintext phones until it is replaced; the
 * hourly re-run encrypts those rows too (they are masked in every API response in the meantime).
 */
@Component
public class ReporterPhoneEncryptionMigrator {
    private static final Logger log = LoggerFactory.getLogger(ReporterPhoneEncryptionMigrator.class);
    private static final int BATCH = 500;
    private final JdbcTemplate jdbc;
    private final PiiProtectionService pii;
    private final ScheduledTaskLock lock;
    private final TransactionTemplate tx;
    private final boolean enabled;

    public ReporterPhoneEncryptionMigrator(JdbcTemplate jdbc, PiiProtectionService pii, ScheduledTaskLock lock,
                                           PlatformTransactionManager transactionManager,
                                           @Value("${app.reports.encrypt-legacy-phones:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.pii = pii;
        this.lock = lock;
        this.tx = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
    }

    @Scheduled(initialDelayString = "${app.reports.encrypt-legacy-phones-initial-ms:30000}",
            fixedDelayString = "${app.reports.encrypt-legacy-phones-ms:3600000}")
    public void sweep() {
        if (enabled) lock.runExclusive("reporter-phone-encryption", Duration.ofMinutes(30), this::migrateAll);
    }

    /** Encrypts every remaining plaintext value; returns how many rows were changed. */
    public int migrateAll() {
        int total = 0;
        while (true) {
            Integer changed = tx.execute(status -> migrateBatch());
            if (changed == null || changed == 0) break;
            total += changed;
        }
        if (total > 0) log.info("reporter_phone_encryption_done rows={}", total);
        return total;
    }

    private int migrateBatch() {
        List<Row> rows = jdbc.query("""
                SELECT id, reporter_phone FROM listing_reports
                WHERE reporter_phone IS NOT NULL AND reporter_phone NOT LIKE 'v1:%'
                ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getString(2)), BATCH);
        for (Row row : rows) {
            String value = row.phone().isBlank() ? null : pii.protect(row.phone()).encrypted();
            jdbc.update("UPDATE listing_reports SET reporter_phone = ? WHERE id = ?", value, row.id());
        }
        return rows.size();
    }

    private record Row(UUID id, String phone) {}
}
