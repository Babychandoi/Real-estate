package com.company.bds.lead.application;

import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.shared.jobs.ClaimedJob;
import com.company.bds.shared.jobs.JobBatchResult;
import com.company.bds.shared.jobs.JobHandler;
import com.company.bds.shared.mail.MailMessage;
import com.company.bds.shared.mail.MailOutbox;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sends appointment reminders (D-12) from the durable queue {@value AppointmentService#REMINDER_QUEUE}. Each job names an
 * appointment, the confirmed version it was scheduled for and a kind (H24/H2). The handler re-checks that the appointment
 * is still CONFIRMED at that version and in the future (a reschedule/cancel makes old jobs no-ops) and records
 * {@code appointment_reminders_sent} in the same transaction as the notifications, so a reminder is delivered at most
 * once per kind although the queue delivers at least once. Content never contains the other party's phone or e-mail.
 */
@Component
public class AppointmentReminderHandler implements JobHandler {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectProvider<RealtimeNotificationService> notifications;
    private final MailOutbox mail;
    private final Clock clock;
    private final String publicBaseUrl;

    public AppointmentReminderHandler(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                      ObjectProvider<RealtimeNotificationService> notifications, MailOutbox mail, Clock clock,
                                      @Value("${app.public-base-url}") String publicBaseUrl) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.notifications = notifications;
        this.mail = mail;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override public String queue() { return AppointmentService.REMINDER_QUEUE; }

    @Override
    public JobBatchResult handle(List<ClaimedJob> jobs) {
        JobBatchResult.Builder result = JobBatchResult.builder();
        for (ClaimedJob job : jobs) {
            try {
                UUID appointmentId = UUID.fromString(job.text("appointmentId"));
                long version = Long.parseLong(job.text("version"));
                String kind = job.text("kind");
                if (!AppointmentService.REMINDERS.containsKey(kind)) {
                    result.failPermanently(job, "unknown reminder kind");
                    continue;
                }
                send(appointmentId, version, kind);
                result.succeed(job);
            } catch (IllegalArgumentException | NullPointerException ex) {
                result.failPermanently(job, "invalid reminder payload");
            } catch (RuntimeException ex) {
                result.fail(job, ex.getClass().getSimpleName());
            }
        }
        return result.build();
    }

    /** @return whether a reminder was sent now (false when stale, already sent or no longer applicable) */
    public boolean send(UUID appointmentId, long version, String kind) {
        Boolean sent = tx.execute(status -> {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT a.status, a.version, a.starts_at, a.owner_id, a.requester_id, l.assignee_id,
                           COALESCE(rv.title, 'Tin đăng') AS title,
                           EXISTS (SELECT 1 FROM broker_team_members m WHERE m.owner_id = a.owner_id AND m.member_id = l.assignee_id
                                   AND m.removed_at IS NULL) AS assignee_active
                    FROM viewing_appointments a JOIN leads l ON l.id = a.lead_id JOIN listings s ON s.id = a.listing_id
                    LEFT JOIN listing_revisions rv ON rv.id = s.public_revision_id
                    WHERE a.id = ? FOR UPDATE OF a
                    """, appointmentId);
            if (rows.isEmpty()) return false;
            Map<String, Object> row = rows.get(0);
            Instant start = ((Timestamp) row.get("starts_at")).toInstant();
            if (!"CONFIRMED".equals(row.get("status")) || ((Number) row.get("version")).longValue() != version
                    || !start.isAfter(clock.instant())) {
                return false;
            }
            int inserted = jdbc.update("INSERT INTO appointment_reminders_sent(appointment_id, kind) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    appointmentId, kind);
            if (inserted == 0) return false;
            String when = TIME.format(start.atZone(VIETNAM));
            String title = (String) row.get("title");
            UUID ownerSide = Boolean.TRUE.equals(row.get("assignee_active")) && row.get("assignee_id") != null
                    ? (UUID) row.get("assignee_id") : (UUID) row.get("owner_id");
            remind(ownerSide, appointmentId, kind, "Nhắc lịch hẹn xem lúc " + when,
                    "Bạn có lịch dẫn khách xem \"" + title + "\" lúc " + when + ".", "/my-leads");
            UUID requester = (UUID) row.get("requester_id");
            if (requester != null) {
                remind(requester, appointmentId, kind, "Nhắc lịch hẹn xem lúc " + when,
                        "Bạn có lịch xem \"" + title + "\" lúc " + when + ". Nếu không thể đến, hãy đổi hoặc hủy lịch trên website.",
                        "/my-inquiries");
            }
            return true;
        });
        return Boolean.TRUE.equals(sent);
    }

    private void remind(UUID userId, UUID appointmentId, String kind, String subject, String message, String path) {
        RealtimeNotificationService notifier = notifications.getIfAvailable();
        if (notifier != null) notifier.notify(userId, "APPOINTMENT_REMINDER", subject, message);
        List<String> emails = jdbc.queryForList(
                "SELECT email FROM users WHERE id = ? AND status = 'ACTIVE' AND email IS NOT NULL AND email_verified_at IS NOT NULL",
                String.class, userId);
        if (emails.isEmpty()) return;
        mail.tryEnqueue(MailMessage.text(emails.get(0), subject,
                message + "\n\nXem chi tiết: " + publicBaseUrl + path
                        + "\n\nMọi liên hệ và trao đổi được thực hiện qua nền tảng; chúng tôi không yêu cầu đặt cọc.",
                "APPOINTMENT_REMINDER", "appt-reminder:" + appointmentId + ":" + kind + ":" + userId));
    }
}
