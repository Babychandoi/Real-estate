package com.company.bds.lead.application;

import com.company.bds.lead.domain.model.ListingReport;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff actions on a report case with their audit: the claim is respected, every action is an event with the actor,
 * and any listing status change it causes is written to {@code listing_status_history} with the case as reason.
 */
@Service
public class ReportActionService {
    private final ViolationReportApplicationService reports;
    private final ReportDeskService desk;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final Clock clock;

    public ReportActionService(ViolationReportApplicationService reports, ReportDeskService desk, JdbcTemplate jdbc,
                               EntityManager entityManager, Clock clock) {
        this.reports = reports;
        this.desk = desk;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public ListingReport emergencyHide(UUID reportId, String reason, UUID actorId) {
        desk.assertActionable(reportId, actorId);
        String before = listingStatusOfReport(reportId);
        ListingReport report = reports.emergencyHideListing(reportId, reason);
        afterListingChange(report, before, "EMERGENCY_HIDE", reason, actorId);
        desk.recordEvent(reportId, "EMERGENCY_HIDDEN", actorId, reason, null);
        return report;
    }

    @Transactional
    public ListingReport resolve(UUID reportId, String note, boolean lock, UUID actorId) {
        desk.assertActionable(reportId, actorId);
        String before = listingStatusOfReport(reportId);
        ListingReport report = reports.resolveReport(reportId, note, lock);
        afterListingChange(report, before, "REPORT_LOCK", note, actorId);
        desk.recordEvent(reportId, "RESOLVED", actorId, note, Map.of("listingLocked", lock));
        desk.closed(reportId, actorId);
        return report;
    }

    @Transactional
    public ListingReport dismiss(UUID reportId, String note, boolean resume, UUID actorId) {
        desk.assertActionable(reportId, actorId);
        String before = listingStatusOfReport(reportId);
        ListingReport report = reports.dismissReport(reportId, note, resume);
        afterListingChange(report, before, "REPORT_RESUME", note, actorId);
        desk.recordEvent(reportId, "DISMISSED", actorId, note, Map.of("listingResumed", resume));
        desk.closed(reportId, actorId);
        return report;
    }

    @Transactional
    public ListingReport appeal(UUID reportId, String evidence, UUID actorId) {
        ListingReport report = reports.appealReport(reportId, evidence);
        desk.recordEvent(reportId, "APPEALED", actorId, "Bổ sung giải trình/bằng chứng", null);
        return report;
    }

    private String listingStatusOfReport(UUID reportId) {
        List<String> status = jdbc.queryForList("""
                SELECT l.status FROM listing_reports r JOIN listings l ON l.id = r.listing_id WHERE r.id = ?
                """, String.class, reportId);
        return status.isEmpty() ? null : status.get(0);
    }

    private void afterListingChange(ListingReport report, String before, String action, String reason, UUID actorId) {
        entityManager.flush();
        List<String> after = jdbc.queryForList("SELECT status FROM listings WHERE id = ?", String.class, report.getListingId());
        if (after.isEmpty() || before == null || before.equals(after.get(0))) return;
        String why = "Vụ việc " + report.getCaseNumber() + ": " + (reason == null || reason.isBlank() ? "xử lý báo cáo" : reason.trim());
        jdbc.update("""
                INSERT INTO listing_status_history(id, listing_id, from_status, to_status, action, reason, actor_id, created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), report.getListingId(), before, after.get(0), action,
                why.length() > 1000 ? why.substring(0, 1000) : why, actorId, Timestamp.from(clock.instant()));
    }
}
