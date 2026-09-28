package com.company.bds.verification.application;

import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.shared.mail.MailMessage;
import com.company.bds.shared.mail.MailOutbox;
import com.company.bds.shared.scheduling.ScheduledTaskLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Daily (03:15 Asia/Ho_Chi_Minh, cluster-wide lock): reminds users 30 days before an identity or ownership check
 * expires (once per validity period, in-app + e-mail through the outbox), and closes expired ownership checks
 * (listing flag off, EXPIRED history entry). An expired identity keeps status VERIFIED with a past {@code expires_at},
 * which the trust contract already reads as EXPIRED; the EXPIRED history entry is written once.
 */
@Component
public class TrustExpiryTask {
    public static final Duration REMINDER_LEAD = Duration.ofDays(30);
    static final int BATCH = 200;
    private static final int MAX_ROUNDS = 500;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JdbcTemplate jdbc;
    private final RealtimeNotificationService notifications;
    private final MailOutbox mail;
    private final TrustDecisionService trust;
    private final ScheduledTaskLock lock;
    private final Clock clock;
    private final TransactionTemplate tx;

    public TrustExpiryTask(JdbcTemplate jdbc, RealtimeNotificationService notifications, MailOutbox mail, TrustDecisionService trust,
                           ScheduledTaskLock lock, Clock clock, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.mail = mail;
        this.trust = trust;
        this.lock = lock;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${app.trust.expiry-cron:0 15 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void daily() {
        lock.runExclusive("trust-expiry", Duration.ofMinutes(20), Duration.ofHours(1), this::runOnce);
    }

    /** Returns {reminders sent, ownership checks expired}. */
    public int[] runOnce() {
        // Each batch in its own short transaction, repeated until the backlog is empty (bounded as a safety net), so a
        // missed day or a bulk import never leaves stale badges behind.
        int reminders = drain(this::remindKyc) + drain(this::remindOwnership);
        int expired = drain(this::expireOwnership);
        Integer kyc = tx.execute(s -> recordExpiredKyc());
        return new int[]{reminders, expired + (kyc == null ? 0 : kyc)};
    }

    private int drain(java.util.function.IntSupplier batch) {
        int total = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            Integer done = tx.execute(s -> batch.getAsInt());
            int n = done == null ? 0 : done;
            total += n;
            if (n < BATCH) break;
        }
        return total;
    }

    private int remindKyc() {
        Instant now = clock.instant();
        List<Object[]> due = jdbc.query("""
                SELECT k.id, k.user_id, k.expires_at, u.email FROM user_kyc_profiles k JOIN users u ON u.id = k.user_id
                WHERE k.status = 'VERIFIED' AND k.expires_at > ? AND k.expires_at <= ?
                  AND NOT EXISTS (SELECT 1 FROM trust_expiry_notices n WHERE n.subject_type = 'KYC' AND n.subject_id = k.id AND n.expires_at = k.expires_at)
                ORDER BY k.expires_at, k.id LIMIT ?
                """, (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant(), rs.getString(4)},
                Timestamp.from(now), Timestamp.from(now.plus(REMINDER_LEAD)), BATCH);
        for (Object[] row : due) {
            UUID kycId = (UUID) row[0];
            UUID userId = (UUID) row[1];
            Instant expires = (Instant) row[2];
            if (!notice("KYC", kycId, expires)) continue;
            String message = "Xác minh danh tính của bạn hết hiệu lực ngày " + DATE.format(expires)
                    + ". Hãy gửi lại giấy tờ trước hạn để giữ nhãn \"Đã xác minh danh tính người đăng\".";
            notifications.notify(userId, "KYC_EXPIRING", "Xác minh danh tính sắp hết hạn", message);
            email((String) row[3], "[Nhà Đất Chuẩn] Xác minh danh tính sắp hết hạn", message, "kyc-expiring:" + kycId + ":" + expires.getEpochSecond());
        }
        return due.size();
    }

    private int remindOwnership() {
        Instant now = clock.instant();
        List<Object[]> due = jdbc.query("""
                SELECT v.id, l.owner_id, v.expires_at, u.email, r.title FROM listing_verifications v
                JOIN listings l ON l.id = v.listing_id JOIN users u ON u.id = l.owner_id
                LEFT JOIN listing_revisions r ON r.id = l.public_revision_id
                WHERE v.status = 'VERIFIED_OWNER' AND v.revoked_at IS NULL AND v.expires_at > ? AND v.expires_at <= ?
                  AND NOT EXISTS (SELECT 1 FROM trust_expiry_notices n WHERE n.subject_type = 'OWNERSHIP' AND n.subject_id = v.id AND n.expires_at = v.expires_at)
                ORDER BY v.expires_at, v.id LIMIT ?
                """, (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant(),
                rs.getString(4), rs.getString(5)}, Timestamp.from(now), Timestamp.from(now.plus(REMINDER_LEAD)), BATCH);
        for (Object[] row : due) {
            UUID verificationId = (UUID) row[0];
            Instant expires = (Instant) row[2];
            if (!notice("OWNERSHIP", verificationId, expires)) continue;
            String message = "Đối chiếu giấy tờ chủ sở hữu của tin \"" + (row[4] == null ? "của bạn" : row[4]) + "\" hết hiệu lực ngày "
                    + DATE.format(expires) + ". Hãy gửi lại giấy tờ để gia hạn.";
            notifications.notify((UUID) row[1], "OWNERSHIP_EXPIRING", "Đối chiếu giấy tờ sắp hết hạn", message);
            email((String) row[3], "[Nhà Đất Chuẩn] Đối chiếu giấy tờ sắp hết hạn", message, "ownership-expiring:" + verificationId + ":" + expires.getEpochSecond());
        }
        return due.size();
    }

    private int expireOwnership() {
        Instant now = clock.instant();
        List<Object[]> expired = jdbc.query("""
                SELECT v.id, v.listing_id, v.expires_at FROM listing_verifications v
                WHERE v.status = 'VERIFIED_OWNER' AND v.revoked_at IS NULL AND v.expires_at <= ?
                  AND NOT EXISTS (SELECT 1 FROM trust_decisions d WHERE d.subject_type = 'OWNERSHIP' AND d.subject_id = v.id
                                  AND d.decision = 'EXPIRED' AND d.expires_at = v.expires_at)
                ORDER BY v.expires_at, v.id LIMIT ?
                """, (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant()},
                Timestamp.from(now), BATCH);
        for (Object[] row : expired) {
            UUID listingId = (UUID) row[1];
            // The listing keeps its badge only if another ownership check of it is still valid.
            Integer stillValid = jdbc.queryForObject("""
                    SELECT count(*) FROM listing_verifications WHERE listing_id = ? AND id <> ? AND status = 'VERIFIED_OWNER'
                      AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > ?)
                    """, Integer.class, listingId, row[0], Timestamp.from(now));
            if (stillValid == null || stillValid == 0) trust.setListingVerified(listingId, false, now);
            trust.record("OWNERSHIP", (UUID) row[0], null, listingId, "EXPIRED", null, "Hết hiệu lực 180 ngày", (Instant) row[2], null, now);
        }
        return expired.size();
    }

    private int recordExpiredKyc() {
        Instant now = clock.instant();
        return jdbc.update("""
                INSERT INTO trust_decisions(id, subject_type, subject_id, user_id, decision, reason_code, note, expires_at, created_at)
                SELECT uuid_generate_v4(), 'KYC', k.id, k.user_id, 'EXPIRED', 'EXPIRED', 'Hết hiệu lực 24 tháng', k.expires_at, ?
                FROM user_kyc_profiles k
                WHERE k.status = 'VERIFIED' AND k.expires_at <= ?
                  AND NOT EXISTS (SELECT 1 FROM trust_decisions d WHERE d.subject_type = 'KYC' AND d.subject_id = k.id
                                  AND d.decision = 'EXPIRED' AND d.expires_at = k.expires_at)
                """, Timestamp.from(now), Timestamp.from(now));
    }

    private boolean notice(String type, UUID subjectId, Instant expires) {
        return jdbc.update("""
                INSERT INTO trust_expiry_notices(subject_type, subject_id, expires_at, notified_at) VALUES (?,?,?,?)
                ON CONFLICT DO NOTHING
                """, type, subjectId, Timestamp.from(expires), Timestamp.from(clock.instant())) > 0;
    }

    private void email(String to, String subject, String body, String dedupeKey) {
        if (to == null || to.isBlank()) return;
        mail.tryEnqueue(MailMessage.text(to.trim(), subject, body, "TRUST_EXPIRING", dedupeKey));
    }
}
