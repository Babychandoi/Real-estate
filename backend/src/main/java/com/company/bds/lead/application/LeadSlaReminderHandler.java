package com.company.bds.lead.application;

import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.shared.jobs.ClaimedJob;
import com.company.bds.shared.jobs.JobBatchResult;
import com.company.bds.shared.jobs.JobHandler;
import com.company.bds.shared.jobs.JobQueue;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Nhắc lead quá hạn phản hồi" (broker_sla_settings.reminder_enabled, default on): when a lead is created a job is queued
 * once at its SLA due time; if the lead is still NEW then, the handler (assignee while an active member, otherwise the
 * listing owner) gets one in-app reminder. The target in force at creation decides the time; a later SLA change does not
 * move queued reminders (documented policy).
 */
@Component
public class LeadSlaReminderHandler implements JobHandler {
    public static final String QUEUE = "lead-sla-reminder";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectProvider<RealtimeNotificationService> notifications;

    public LeadSlaReminderHandler(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                  ObjectProvider<RealtimeNotificationService> notifications) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.notifications = notifications;
    }

    /** Queues the reminder of a new lead (caller's transaction); at most once per lead. */
    public static void schedule(JdbcTemplate jdbc, JobQueue jobs, UUID leadId, UUID ownerId, Instant createdAt) {
        List<Integer> minutes = jdbc.queryForList(
                "SELECT first_response_minutes FROM broker_sla_settings WHERE user_id = ?", Integer.class, ownerId);
        int target = minutes.isEmpty() ? LeadInboxQuery.DEFAULT_SLA_MINUTES : minutes.get(0);
        jobs.enqueueOnce(QUEUE, "lead-sla:" + leadId, Map.of("leadId", leadId.toString()),
                createdAt.plus(Duration.ofMinutes(target)));
    }

    @Override public String queue() { return QUEUE; }

    @Override
    public JobBatchResult handle(List<ClaimedJob> jobs) {
        JobBatchResult.Builder result = JobBatchResult.builder();
        for (ClaimedJob job : jobs) {
            try {
                remind(UUID.fromString(job.text("leadId")));
                result.succeed(job);
            } catch (IllegalArgumentException | NullPointerException ex) {
                result.failPermanently(job, "invalid payload");
            } catch (RuntimeException ex) {
                result.fail(job, ex.getClass().getSimpleName());
            }
        }
        return result.build();
    }

    /** @return whether a reminder was sent (false when the lead was answered, withdrawn, or reminders are off) */
    public boolean remind(UUID leadId) {
        Boolean sent = tx.execute(status -> {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT l.status, s.owner_id, l.assignee_id, COALESCE(bss.reminder_enabled, TRUE) AS enabled,
                           EXISTS (SELECT 1 FROM broker_team_members m WHERE m.owner_id = s.owner_id AND m.member_id = l.assignee_id
                                   AND m.removed_at IS NULL) AS assignee_active
                    FROM leads l JOIN listings s ON s.id = l.listing_id
                    LEFT JOIN broker_sla_settings bss ON bss.user_id = s.owner_id WHERE l.id = ?
                    """, leadId);
            if (rows.isEmpty()) return false;
            Map<String, Object> row = rows.get(0);
            if (!"NEW".equals(row.get("status")) || !Boolean.TRUE.equals(row.get("enabled"))) return false;
            UUID handler = Boolean.TRUE.equals(row.get("assignee_active")) && row.get("assignee_id") != null
                    ? (UUID) row.get("assignee_id") : (UUID) row.get("owner_id");
            RealtimeNotificationService notifier = notifications.getIfAvailable();
            if (notifier == null) return false;
            notifier.notify(handler, "LEAD_SLA_OVERDUE", "Có lead quá hạn phản hồi",
                    "Một yêu cầu liên hệ đã quá thời gian phản hồi mục tiêu. Mở Hộp thư khách quan tâm để phản hồi khách.");
            return true;
        });
        return Boolean.TRUE.equals(sent);
    }
}
