package com.company.bds.broker;

import com.company.bds.lead.application.LeadAccessService;
import com.company.bds.lead.application.LeadInboxQuery;
import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.shared.error.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Broker workspace (UI-08, P-08): real SLA measurements, today's tasks, team + assignment, lead intake history and the
 * qualified-lead / ROI report. Every number is computed from recorded facts; a value that cannot be measured is
 * {@code null} ("chưa có dữ liệu"), never 0. The actor's scope is the leads of their own listings plus the leads assigned
 * to them as an active team member.
 */
@Service
@Transactional(readOnly = true)
public class BrokerWorkspaceService {
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int SLA_WINDOW_DAYS = 30;
    public static final int MAX_REPORT_DAYS = 366;
    private static final int DEFAULT_SLA = LeadInboxQuery.DEFAULT_SLA_MINUTES;
    /** Leads the actor handles: own listings, or assigned to them while an active member of the owner's team. */
    private static final String SCOPE = """
            (s.owner_id = ? OR (l.assignee_id = ? AND EXISTS (SELECT 1 FROM broker_team_members tm
             WHERE tm.owner_id = s.owner_id AND tm.member_id = l.assignee_id AND tm.removed_at IS NULL)))""";

    private final JdbcTemplate jdbc;
    private final LeadAccessService leads;
    private final ObjectProvider<RealtimeNotificationService> notifications;
    private final Clock clock;

    public BrokerWorkspaceService(JdbcTemplate jdbc, LeadAccessService leads,
                                  ObjectProvider<RealtimeNotificationService> notifications, Clock clock) {
        this.jdbc = jdbc;
        this.leads = leads;
        this.notifications = notifications;
        this.clock = clock;
    }

    public Map<String, Object> workspace(UUID actor) {
        Instant now = clock.instant();
        Timestamp nowTs = Timestamp.from(now);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", now);
        result.put("listingStats", jdbc.queryForMap("""
                SELECT COUNT(*) AS listings, COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active,
                       COUNT(*) FILTER (WHERE status = 'PENDING_REVIEW') AS pending FROM listings WHERE owner_id = ?
                """, actor));
        Map<String, Object> sla = sla(actor);
        result.put("sla", sla);
        result.put("slaMetrics", slaMetrics(actor, ((Number) sla.get("firstResponseMinutes")).intValue(), now));

        Map<String, Object> tasks = new LinkedHashMap<>();
        tasks.put("respond", jdbc.query("""
                SELECT l.id, l.full_name, l.request_type, l.created_at, rv.title,
                       l.created_at + make_interval(mins => COALESCE(bss.first_response_minutes, %d)) AS due_at
                FROM leads l JOIN listings s ON s.id = l.listing_id LEFT JOIN broker_sla_settings bss ON bss.user_id = s.owner_id
                LEFT JOIN listing_revisions rv ON rv.id = s.public_revision_id
                WHERE l.status = 'NEW' AND %s
                ORDER BY due_at ASC, l.id ASC LIMIT 10
                """.formatted(DEFAULT_SLA, SCOPE), (rs, n) -> {
            Map<String, Object> item = leadTask(rs);
            Instant due = rs.getTimestamp("due_at").toInstant();
            item.put("dueAt", due);
            item.put("overdue", due.isBefore(now));
            return item;
        }, actor, actor));
        LocalDate today = LocalDate.ofInstant(now, VIETNAM);
        Timestamp dayStart = Timestamp.from(today.atStartOfDay(VIETNAM).toInstant());
        Timestamp dayEnd = Timestamp.from(today.plusDays(1).atStartOfDay(VIETNAM).toInstant());
        tasks.put("appointmentsToday", appointments("""
                a.status = 'CONFIRMED' AND a.starts_at >= ? AND a.starts_at < ?""", "a.starts_at ASC", actor, dayStart, dayEnd));
        tasks.put("proposalsAwaitingMe", appointments("""
                a.status = 'PROPOSED' AND a.proposed_by_side = 'REQUESTER'""", "a.created_at ASC", actor));
        tasks.put("outcomesToRecord", appointments("""
                a.status = 'CONFIRMED' AND a.starts_at <= ?""", "a.starts_at ASC", actor, nowTs));
        result.put("tasks", tasks);

        result.put("intakeHistory", jdbc.query("""
                SELECT e.id, e.type, e.actor_side, e.from_status, e.to_status, e.created_at, l.id AS lead_id, l.full_name,
                       rv.title, u.full_name AS actor_name
                FROM lead_events e JOIN leads l ON l.id = e.lead_id JOIN listings s ON s.id = l.listing_id
                LEFT JOIN listing_revisions rv ON rv.id = s.public_revision_id LEFT JOIN users u ON u.id = e.actor_id
                WHERE e.type IN ('CREATED','ASSIGNED','STATUS_CHANGED','WITHDRAWN','QUALIFIED') AND %s
                ORDER BY e.created_at DESC, e.id DESC LIMIT 20
                """.formatted(SCOPE), (rs, n) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getObject("id", UUID.class));
            item.put("type", rs.getString("type"));
            item.put("actorSide", rs.getString("actor_side"));
            item.put("actorName", rs.getString("actor_name"));
            item.put("fromStatus", rs.getString("from_status"));
            item.put("toStatus", rs.getString("to_status"));
            item.put("createdAt", rs.getTimestamp("created_at").toInstant());
            item.put("leadId", rs.getObject("lead_id", UUID.class));
            item.put("leadName", rs.getString("full_name"));
            item.put("listingTitle", title(rs));
            return item;
        }, actor, actor));
        result.put("team", team(actor));
        result.put("memberOf", jdbc.query("""
                SELECT m.owner_id, u.full_name FROM broker_team_members m JOIN users u ON u.id = m.owner_id
                WHERE m.member_id = ? AND m.removed_at IS NULL ORDER BY m.added_at, m.owner_id
                """, (rs, n) -> Map.of("ownerId", rs.getObject(1, UUID.class), "ownerName", rs.getString(2)), actor));
        return result;
    }

    public Map<String, Object> sla(UUID actor) {
        return jdbc.query("SELECT first_response_minutes, reminder_enabled, daily_digest_enabled FROM broker_sla_settings WHERE user_id = ?",
                        (rs, n) -> {
                            Map<String, Object> value = new LinkedHashMap<>();
                            value.put("firstResponseMinutes", rs.getInt(1));
                            value.put("reminderEnabled", rs.getBoolean(2));
                            value.put("dailyDigestEnabled", rs.getBoolean(3));
                            value.put("configured", true);
                            return value;
                        }, actor).stream().findFirst()
                .orElseGet(() -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("firstResponseMinutes", DEFAULT_SLA);
                    value.put("reminderEnabled", true);
                    value.put("dailyDigestEnabled", true);
                    value.put("configured", false);
                    return value;
                });
    }

    @Transactional
    public void updateSla(UUID actor, int minutes, boolean reminder, boolean digest) {
        if (minutes < 5 || minutes > 1440) throw ApiException.badRequest("SLA_INVALID", "SLA phải từ 5 đến 1440 phút.");
        jdbc.update("""
                INSERT INTO broker_sla_settings(user_id, first_response_minutes, reminder_enabled, daily_digest_enabled) VALUES (?,?,?,?)
                ON CONFLICT (user_id) DO UPDATE SET first_response_minutes = EXCLUDED.first_response_minutes,
                    reminder_enabled = EXCLUDED.reminder_enabled, daily_digest_enabled = EXCLUDED.daily_digest_enabled,
                    updated_at = CURRENT_TIMESTAMP
                """, actor, minutes, reminder, digest);
    }

    /** SLA over the last 30 days of the actor's own listings; unmeasured leads (legacy, no first response yet) are excluded. */
    Map<String, Object> slaMetrics(UUID owner, int targetMinutes, Instant now) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COUNT(*) AS leads,
                       COUNT(*) FILTER (WHERE l.first_response_at IS NOT NULL) AS measured,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (l.first_response_at - l.created_at)) / 60)
                           FILTER (WHERE l.first_response_at IS NOT NULL) AS median_minutes,
                       percentile_cont(0.9) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (l.first_response_at - l.created_at)) / 60)
                           FILTER (WHERE l.first_response_at IS NOT NULL) AS p90_minutes,
                       COUNT(*) FILTER (WHERE l.first_response_at IS NOT NULL
                           AND l.first_response_at <= l.created_at + make_interval(mins => ?)) AS within_target,
                       COUNT(*) FILTER (WHERE l.status = 'NEW' AND l.created_at + make_interval(mins => ?) < ?) AS open_breaches,
                       COUNT(*) FILTER (WHERE l.status = 'NEW') AS open_new
                FROM leads l JOIN listings s ON s.id = l.listing_id
                WHERE s.owner_id = ? AND l.created_at >= ?
                """, targetMinutes, targetMinutes, Timestamp.from(now), owner,
                Timestamp.from(now.minus(Duration.ofDays(SLA_WINDOW_DAYS))));
        long measured = ((Number) row.get("measured")).longValue();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("windowDays", SLA_WINDOW_DAYS);
        metrics.put("targetMinutes", targetMinutes);
        metrics.put("leads", ((Number) row.get("leads")).longValue());
        metrics.put("measuredResponses", measured);
        metrics.put("medianFirstResponseMinutes", roundOrNull(row.get("median_minutes")));
        metrics.put("p90FirstResponseMinutes", roundOrNull(row.get("p90_minutes")));
        metrics.put("withinTargetPercent", measured == 0 ? null
                : Math.round(((Number) row.get("within_target")).doubleValue() * 1000.0 / measured) / 10.0);
        metrics.put("openBreaches", ((Number) row.get("open_breaches")).longValue());
        metrics.put("openNew", ((Number) row.get("open_new")).longValue());
        return metrics;
    }

    public List<Map<String, Object>> team(UUID owner) {
        return jdbc.query("""
                SELECT m.member_id, u.full_name, u.email, m.added_at,
                       (SELECT COUNT(*) FROM leads l JOIN listings s ON s.id = l.listing_id
                        WHERE s.owner_id = m.owner_id AND l.assignee_id = m.member_id
                          AND l.status IN ('NEW','CONTACTED','APPOINTED')) AS open_leads
                FROM broker_team_members m JOIN users u ON u.id = m.member_id
                WHERE m.owner_id = ? AND m.removed_at IS NULL ORDER BY m.added_at, m.member_id LIMIT 50
                """, (rs, n) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("memberId", rs.getObject("member_id", UUID.class));
            item.put("name", rs.getString("full_name"));
            item.put("email", maskEmail(rs.getString("email")));
            item.put("addedAt", rs.getTimestamp("added_at").toInstant());
            item.put("openLeads", rs.getLong("open_leads"));
            return item;
        }, owner);
    }

    @Transactional
    public List<Map<String, Object>> addMember(UUID owner, String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 150) throw ApiException.badRequest("EMAIL_REQUIRED", "Nhập e-mail tài khoản môi giới.");
        List<Map<String, Object>> users = jdbc.queryForList("""
                SELECT u.id, EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = u.id AND r.role = 'BROKER') AS broker
                FROM users u WHERE LOWER(u.email) = ? AND u.status = 'ACTIVE'
                """, normalized);
        // Same answer for "no such account" and "not a broker": the form must not reveal which e-mails exist.
        if (users.isEmpty() || !Boolean.TRUE.equals(users.get(0).get("broker"))) {
            throw ApiException.badRequest("MEMBER_NOT_ELIGIBLE", "Không thêm được: cần một tài khoản môi giới đang hoạt động với e-mail này.");
        }
        UUID member = (UUID) users.get(0).get("id");
        if (member.equals(owner)) throw ApiException.badRequest("MEMBER_IS_SELF", "Bạn không thể thêm chính mình.");
        Long active = jdbc.queryForObject("SELECT COUNT(*) FROM broker_team_members WHERE owner_id = ? AND removed_at IS NULL", Long.class, owner);
        if (active != null && active >= 20) throw ApiException.conflict("TEAM_FULL", "Nhóm tối đa 20 thành viên.");
        jdbc.update("""
                INSERT INTO broker_team_members(owner_id, member_id, added_at) VALUES (?,?,?)
                ON CONFLICT (owner_id, member_id) DO UPDATE SET removed_at = NULL, added_at = EXCLUDED.added_at
                    WHERE broker_team_members.removed_at IS NOT NULL
                """, owner, member, Timestamp.from(clock.instant()));
        RealtimeNotificationService notifier = notifications.getIfAvailable();
        if (notifier != null) {
            notifier.notify(member, "TEAM_ADDED", "Bạn được thêm vào một nhóm môi giới",
                    "Lead được phân công cho bạn sẽ xuất hiện trong Hộp thư khách quan tâm và Không gian môi giới.");
        }
        return team(owner);
    }

    /** Removes a member; their open leads go back to the owner (recorded in each lead's history). */
    @Transactional
    public List<Map<String, Object>> removeMember(UUID owner, UUID member) {
        Instant now = clock.instant();
        int removed = jdbc.update("UPDATE broker_team_members SET removed_at = ? WHERE owner_id = ? AND member_id = ? AND removed_at IS NULL",
                Timestamp.from(now), owner, member);
        if (removed == 0) throw ApiException.notFound("MEMBER_NOT_FOUND", "Không tìm thấy thành viên trong nhóm.");
        List<UUID> returned = jdbc.queryForList("""
                UPDATE leads l SET assignee_id = NULL, assigned_at = ?, version = l.version + 1, updated_at = ?
                FROM listings s WHERE s.id = l.listing_id AND s.owner_id = ? AND l.assignee_id = ? RETURNING l.id
                """, UUID.class, Timestamp.from(now), Timestamp.from(now), owner, member);
        for (UUID leadId : returned) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("assigneeId", null);
            data.put("previousAssigneeId", member.toString());
            data.put("reason", "MEMBER_REMOVED");
            leads.recordEvent(leadId, "ASSIGNED", owner, "OWNER_SIDE", null, null, null, data, true, now);
        }
        return team(owner);
    }

    /**
     * Qualified-lead / ROI report for leads created in [from, to) (dates in Vietnam time, inclusive end date). Spend =
     * APPROVED package orders of the owner approved in the period. Ratios are {@code null} when their inputs are missing.
     */
    public Map<String, Object> leadReport(UUID owner, @Nullable LocalDate from, @Nullable LocalDate to) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), VIETNAM);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw ApiException.badRequest("INVALID_PERIOD", "Ngày bắt đầu phải trước ngày kết thúc.");
        if (start.plusDays(MAX_REPORT_DAYS).isBefore(end)) {
            throw ApiException.badRequest("INVALID_PERIOD", "Khoảng thời gian tối đa " + MAX_REPORT_DAYS + " ngày.");
        }
        Timestamp fromTs = Timestamp.from(start.atStartOfDay(VIETNAM).toInstant());
        Timestamp toTs = Timestamp.from(end.plusDays(1).atStartOfDay(VIETNAM).toInstant());
        int target = ((Number) sla(owner).get("firstResponseMinutes")).intValue();
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COUNT(*) AS leads,
                       COUNT(*) FILTER (WHERE l.request_type = 'VIEWING') AS viewing_requests,
                       COUNT(*) FILTER (WHERE l.first_response_at IS NOT NULL) AS measured,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (l.first_response_at - l.created_at)) / 60)
                           FILTER (WHERE l.first_response_at IS NOT NULL) AS median_minutes,
                       COUNT(*) FILTER (WHERE l.first_response_at IS NOT NULL
                           AND l.first_response_at <= l.created_at + make_interval(mins => ?)) AS within_target,
                       COUNT(*) FILTER (WHERE l.qualification = 'QUALIFIED') AS qualified,
                       COUNT(*) FILTER (WHERE l.qualification = 'UNQUALIFIED') AS unqualified,
                       COUNT(*) FILTER (WHERE l.status = 'WITHDRAWN') AS withdrawn,
                       COUNT(*) FILTER (WHERE l.status = 'CLOSED') AS closed
                FROM leads l JOIN listings s ON s.id = l.listing_id
                WHERE s.owner_id = ? AND l.created_at >= ? AND l.created_at < ?
                """, target, owner, fromTs, toTs);
        Map<String, Object> appts = jdbc.queryForMap("""
                SELECT COUNT(DISTINCT a.lead_id) FILTER (WHERE a.confirmed_at IS NOT NULL) AS leads_with_confirmed,
                       COUNT(*) FILTER (WHERE a.status = 'COMPLETED') AS completed,
                       COUNT(*) FILTER (WHERE a.status = 'NO_SHOW') AS no_show
                FROM viewing_appointments a JOIN leads l ON l.id = a.lead_id
                WHERE a.owner_id = ? AND l.created_at >= ? AND l.created_at < ?
                """, owner, fromTs, toTs);
        Long spend = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount_vnd), 0) FROM package_orders
                WHERE user_id = ? AND status = 'APPROVED' AND reviewed_at >= ? AND reviewed_at < ?
                """, Long.class, owner, fromTs, toTs);
        Long approvedOrders = jdbc.queryForObject("""
                SELECT COUNT(*) FROM package_orders WHERE user_id = ? AND status = 'APPROVED' AND reviewed_at >= ? AND reviewed_at < ?
                """, Long.class, owner, fromTs, toTs);
        long leadsCount = ((Number) row.get("leads")).longValue();
        long measured = ((Number) row.get("measured")).longValue();
        long qualified = ((Number) row.get("qualified")).longValue();
        long unqualified = ((Number) row.get("unqualified")).longValue();
        long withConfirmed = ((Number) appts.get("leads_with_confirmed")).longValue();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("from", start.toString());
        report.put("to", end.toString());
        report.put("targetMinutes", target);
        report.put("leads", leadsCount);
        report.put("viewingRequests", ((Number) row.get("viewing_requests")).longValue());
        report.put("measuredResponses", measured);
        report.put("medianFirstResponseMinutes", roundOrNull(row.get("median_minutes")));
        report.put("withinTargetPercent", measured == 0 ? null
                : Math.round(((Number) row.get("within_target")).doubleValue() * 1000.0 / measured) / 10.0);
        report.put("qualified", qualified);
        report.put("unqualified", unqualified);
        report.put("unassessed", leadsCount - qualified - unqualified);
        report.put("qualifiedPercentOfAssessed", qualified + unqualified == 0 ? null
                : Math.round(qualified * 1000.0 / (qualified + unqualified)) / 10.0);
        report.put("withdrawn", ((Number) row.get("withdrawn")).longValue());
        report.put("closed", ((Number) row.get("closed")).longValue());
        report.put("leadsWithConfirmedAppointment", withConfirmed);
        report.put("appointmentRatePercent", leadsCount == 0 ? null : Math.round(withConfirmed * 1000.0 / leadsCount) / 10.0);
        report.put("appointmentsCompleted", ((Number) appts.get("completed")).longValue());
        report.put("appointmentsNoShow", ((Number) appts.get("no_show")).longValue());
        long spendValue = spend == null ? 0 : spend;
        boolean spendKnown = approvedOrders != null && approvedOrders > 0;
        report.put("spendVnd", spendKnown ? spendValue : null);
        report.put("approvedOrders", approvedOrders == null ? 0 : approvedOrders);
        report.put("costPerLeadVnd", spendKnown && leadsCount > 0 ? Math.round((double) spendValue / leadsCount) : null);
        report.put("costPerQualifiedLeadVnd", spendKnown && qualified > 0 ? Math.round((double) spendValue / qualified) : null);
        report.put("byListing", jdbc.query("""
                SELECT s.id, s.slug, rv.title, COUNT(*) AS leads,
                       COUNT(*) FILTER (WHERE l.qualification = 'QUALIFIED') AS qualified,
                       COUNT(DISTINCT a.lead_id) AS with_appointment
                FROM leads l JOIN listings s ON s.id = l.listing_id
                LEFT JOIN listing_revisions rv ON rv.id = s.public_revision_id
                LEFT JOIN viewing_appointments a ON a.lead_id = l.id AND a.confirmed_at IS NOT NULL
                WHERE s.owner_id = ? AND l.created_at >= ? AND l.created_at < ?
                GROUP BY s.id, s.slug, rv.title ORDER BY COUNT(*) DESC, s.id DESC LIMIT 20
                """, (rs, n) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("listingId", rs.getObject("id", UUID.class));
            item.put("slug", rs.getString("slug"));
            item.put("title", title(rs));
            item.put("leads", rs.getLong("leads"));
            item.put("qualified", rs.getLong("qualified"));
            item.put("withAppointment", rs.getLong("with_appointment"));
            return item;
        }, owner, fromTs, toTs));
        return report;
    }

    private List<Map<String, Object>> appointments(String condition, String order, UUID actor, Object... extra) {
        Object[] args = new Object[extra.length + 2];
        System.arraycopy(extra, 0, args, 0, extra.length);
        args[extra.length] = actor;
        args[extra.length + 1] = actor;
        return jdbc.query("""
                SELECT a.id AS appointment_id, a.status, a.starts_at, a.ends_at, a.version, a.created_at AS proposed_at,
                       l.id, l.full_name, l.request_type, l.created_at, rv.title
                FROM viewing_appointments a JOIN leads l ON l.id = a.lead_id JOIN listings s ON s.id = a.listing_id
                LEFT JOIN listing_revisions rv ON rv.id = s.public_revision_id
                WHERE %s AND %s ORDER BY %s, a.id ASC LIMIT 10
                """.formatted(condition, SCOPE, order), (rs, n) -> {
            Map<String, Object> item = leadTask(rs);
            item.put("appointmentId", rs.getObject("appointment_id", UUID.class));
            item.put("appointmentStatus", rs.getString("status"));
            item.put("appointmentVersion", rs.getLong("version"));
            Timestamp startsAt = rs.getTimestamp("starts_at");
            Timestamp endsAt = rs.getTimestamp("ends_at");
            item.put("startsAt", startsAt == null ? null : startsAt.toInstant());
            item.put("endsAt", endsAt == null ? null : endsAt.toInstant());
            item.put("proposedAt", rs.getTimestamp("proposed_at").toInstant());
            return item;
        }, args);
    }

    private static Map<String, Object> leadTask(ResultSet rs) throws SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("leadId", rs.getObject("id", UUID.class));
        item.put("leadName", rs.getString("full_name"));
        item.put("requestType", rs.getString("request_type"));
        item.put("createdAt", rs.getTimestamp("created_at").toInstant());
        item.put("listingTitle", title(rs));
        return item;
    }

    private static String title(ResultSet rs) throws SQLException {
        String title = rs.getString("title");
        return title == null ? "Tin đăng" : title;
    }

    @Nullable
    private static Long roundOrNull(@Nullable Object value) {
        return value == null ? null : Math.round(((Number) value).doubleValue());
    }

    static String maskEmail(@Nullable String email) {
        if (email == null || !email.contains("@")) return null;
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        return (local.isEmpty() ? "" : local.charAt(0)) + "***" + email.substring(at);
    }
}
