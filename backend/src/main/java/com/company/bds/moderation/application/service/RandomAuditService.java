package com.company.bds.moderation.application.service;

import com.company.bds.moderation.domain.model.ApprovalReason;
import com.company.bds.moderation.domain.model.StandardModerationReason;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.scheduling.ScheduledTaskLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * P-14 random audit: once per week (Monday 02:00 Asia/Ho_Chi_Minh, under a cluster-wide task lock) 5% of the previous
 * week's approvals, at least one and at most 20, are drawn for a second look. A week is drawn only once (unique
 * week/listing and an existence check), so a retried or concurrent run cannot resample.
 */
@Service
public class RandomAuditService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ScheduledTaskLock lock;
    private final TransactionTemplate tx;

    public RandomAuditService(JdbcTemplate jdbc, Clock clock, ScheduledTaskLock lock, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.lock = lock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${app.moderation.audit-cron:0 0 2 * * MON}", zone = "Asia/Ho_Chi_Minh")
    public void weekly() {
        lock.runExclusive("moderation-random-audit", Duration.ofMinutes(10), Duration.ofHours(1), () -> tx.executeWithoutResult(s -> drawPreviousWeek()));
    }

    /** Draws the sample of the week before the current one (Monday-based); returns the number of new samples. */
    @Transactional
    public int drawPreviousWeek() {
        LocalDate thisMonday = LocalDate.ofInstant(clock.instant(), ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return draw(thisMonday.minusWeeks(1));
    }

    @Transactional
    public int draw(LocalDate weekStart) {
        // Serialise draws of the same week even outside the scheduled lock (manual trigger).
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext('moderation-random-audit'), ?)", Object.class,
                (int) weekStart.toEpochDay());
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM moderation_audit_samples WHERE week_start = ?",
                Integer.class, Date.valueOf(weekStart));
        if (existing != null && existing > 0) return 0;
        Instant from = weekStart.atStartOfDay(ZONE).toInstant();
        Instant to = weekStart.plusWeeks(1).atStartOfDay(ZONE).toInstant();
        Integer approvals = jdbc.queryForObject("""
                SELECT count(*) FROM moderation_decisions WHERE decision = 'APPROVED' AND created_at >= ? AND created_at < ?
                """, Integer.class, Timestamp.from(from), Timestamp.from(to));
        if (approvals == null || approvals == 0) return 0;
        int sample = Math.min(ModerationPolicy.AUDIT_MAX, Math.max(1, (int) Math.ceil(approvals * ModerationPolicy.AUDIT_RATE)));
        return jdbc.update("""
                INSERT INTO moderation_audit_samples(id, week_start, listing_id, revision_id, original_decision_id, status, created_at)
                SELECT uuid_generate_v4(), ?, s.listing_id, s.revision_id, s.id, 'OPEN', ?
                FROM (SELECT DISTINCT ON (d.listing_id) d.listing_id, d.revision_id, d.id
                      FROM moderation_decisions d
                      WHERE d.decision = 'APPROVED' AND d.created_at >= ? AND d.created_at < ?
                      ORDER BY d.listing_id, d.created_at DESC) s
                ORDER BY random()
                LIMIT ?
                ON CONFLICT (week_start, listing_id) DO NOTHING
                """, Date.valueOf(weekStart), Timestamp.from(clock.instant()), Timestamp.from(from), Timestamp.from(to), sample);
    }

    @Transactional(readOnly = true)
    public SamplePage page(String status, int page, int size) {
        String normalized = status == null || status.isBlank() ? "OPEN" : status.trim().toUpperCase();
        if (!List.of("OPEN", "PASSED", "FAILED").contains(normalized)) {
            throw ApiException.badRequest("INVALID_FILTER", "Trạng thái kiểm tra ngẫu nhiên không hợp lệ.");
        }
        int safeSize = Math.min(ModerationPolicy.MAX_PAGE_SIZE, Math.max(1, size));
        int safePage = Math.max(0, page);
        Long total = jdbc.queryForObject("SELECT count(*) FROM moderation_audit_samples WHERE status = ?", Long.class, normalized);
        List<Sample> items = jdbc.query("""
                SELECT s.id, s.week_start, s.listing_id, s.revision_id, r.revision_number, r.title, r.address_summary, r.price_vnd,
                       s.status, s.note, s.reviewed_at, ru.full_name AS reviewer, d.moderator_id, du.full_name AS original_moderator,
                       d.reason_code AS original_reason, d.created_at AS approved_at, l.status AS listing_status
                FROM moderation_audit_samples s
                JOIN listing_revisions r ON r.id = s.revision_id
                JOIN listings l ON l.id = s.listing_id
                LEFT JOIN users ru ON ru.id = s.reviewed_by
                LEFT JOIN moderation_decisions d ON d.id = s.original_decision_id
                LEFT JOIN users du ON du.id = d.moderator_id
                WHERE s.status = ?
                ORDER BY s.week_start DESC, s.created_at, s.id
                LIMIT ? OFFSET ?
                """, (rs, n) -> new Sample(rs.getObject("id", UUID.class), rs.getDate("week_start").toLocalDate(),
                rs.getObject("listing_id", UUID.class), rs.getObject("revision_id", UUID.class), rs.getInt("revision_number"),
                rs.getString("title"), rs.getString("address_summary"), rs.getLong("price_vnd"), rs.getString("status"),
                rs.getString("note"), rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant(),
                rs.getString("reviewer"), rs.getObject("moderator_id", UUID.class), rs.getString("original_moderator"),
                rs.getString("original_reason"), rs.getTimestamp("approved_at") == null ? null : rs.getTimestamp("approved_at").toInstant(),
                rs.getString("listing_status")), normalized, safeSize, safePage * safeSize);
        return new SamplePage(items, safePage, safeSize, total == null ? 0 : total);
    }

    /** Records the second look: PASSED (with an approval reason) or FAILED (with a standard rejection reason). */
    @Transactional
    public void review(UUID sampleId, String outcome, String reasonCode, String note, UUID actorId) {
        boolean passed = "PASSED".equals(outcome);
        if (!passed && !"FAILED".equals(outcome)) throw ApiException.badRequest("INVALID_OUTCOME", "Kết quả kiểm tra không hợp lệ.");
        boolean validReason = passed ? ApprovalReason.isValid(reasonCode)
                : Arrays.stream(StandardModerationReason.values()).anyMatch(r -> r.name().equals(reasonCode));
        if (!validReason) throw ApiException.badRequest("INVALID_REASON", "Cần chọn lý do trong danh mục chuẩn.");
        Instant now = clock.instant();
        List<UUID> original = jdbc.query("""
                SELECT d.moderator_id FROM moderation_audit_samples s
                LEFT JOIN moderation_decisions d ON d.id = s.original_decision_id WHERE s.id = ?
                """, (rs, n) -> rs.getObject(1, UUID.class), sampleId);
        if (!original.isEmpty() && actorId.equals(original.get(0))) {
            // The second look must come from someone other than the moderator who approved the listing.
            throw ApiException.conflict("OWN_DECISION", "Không thể tự kiểm tra lại quyết định duyệt của chính mình.");
        }
        List<UUID[]> sample = jdbc.query("""
                UPDATE moderation_audit_samples SET status = ?, reviewed_by = ?, reviewed_at = ?, note = ?
                WHERE id = ? AND status = 'OPEN' RETURNING listing_id, revision_id
                """, (rs, n) -> new UUID[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)},
                outcome, actorId, Timestamp.from(now), note == null || note.isBlank() ? null : note.trim(), sampleId);
        if (sample.isEmpty()) throw ApiException.conflict("ALREADY_REVIEWED", "Mẫu kiểm tra đã được xử lý hoặc không tồn tại.");
        jdbc.update("""
                INSERT INTO moderation_decisions(id, listing_id, revision_id, moderator_id, decision, reason_code, note, created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), sample.get(0)[0], sample.get(0)[1], actorId, passed ? "AUDIT_PASSED" : "AUDIT_FAILED",
                reasonCode, note == null || note.isBlank() ? null : note.trim(), Timestamp.from(now));
    }

    public record Sample(UUID id, LocalDate weekStart, UUID listingId, UUID revisionId, int revisionNumber, String title,
                         String addressSummary, long priceVnd, String status, String note, Instant reviewedAt, String reviewerName,
                         UUID originalModeratorId, String originalModeratorName, String originalReasonCode, Instant approvedAt,
                         String listingStatus) {}

    public record SamplePage(List<Sample> items, int page, int size, long total) {}
}
