package com.company.bds.lead.application;


import com.company.bds.lead.domain.model.ReportSeverity;
import com.company.bds.shared.error.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Violation report queue (UI-21, P-04, P-14), separate from lead oversight: SLA by severity (P0 1 h, HIGH 4 h,
 * MEDIUM 24 h, LOW 72 h from submission), claims (30 min), an append-only event history and the owner-response /
 * auto-pause outcome of FAKE_SOLD cases (events written by the supply flow through {@link #recordEvent}).
 */
@Service
public class ReportDeskService {
    public static final Duration CLAIM_TTL = Duration.ofMinutes(30);
    private static final Set<String> OPEN = Set.of("PENDING", "WAITING_REPLY", "APPEALED");
    private static final Set<String> STATUSES = Set.of("PENDING", "WAITING_REPLY", "APPEALED", "RESOLVED", "DISMISSED");
    private static final Set<String> EVENT_TYPES = Set.of("SUBMITTED", "CLAIMED", "RELEASED", "EMERGENCY_HIDDEN", "RESOLVED",
            "DISMISSED", "APPEALED", "OWNER_RESPONSE", "AUTO_PAUSED", "NOTE");
    private static final String SLA_MINUTES = "CASE r.severity WHEN 'P0_EMERGENCY' THEN 60 WHEN 'HIGH' THEN 240 WHEN 'MEDIUM' THEN 1440 ELSE 4320 END";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper json;

    public ReportDeskService(JdbcTemplate jdbc, Clock clock, ObjectMapper json) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.json = json;
    }

    public static Duration sla(ReportSeverity severity) {
        return switch (severity) {
            case P0_EMERGENCY -> Duration.ofHours(1);
            case HIGH -> Duration.ofHours(4);
            case MEDIUM -> Duration.ofHours(24);
            case LOW -> Duration.ofHours(72);
        };
    }

    @Transactional(readOnly = true)
    public QueuePage queue(String status, String severity, Boolean breached, boolean mine, UUID actorId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Timestamp now = Timestamp.from(clock.instant());
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        String normalizedStatus = status == null || status.isBlank() ? "OPEN" : status.trim().toUpperCase(Locale.ROOT);
        if ("OPEN".equals(normalizedStatus)) where.append(" AND r.status IN ('PENDING','WAITING_REPLY','APPEALED')");
        else if (!"ALL".equals(normalizedStatus)) {
            if (!STATUSES.contains(normalizedStatus)) throw ApiException.badRequest("INVALID_FILTER", "Trạng thái vụ việc không hợp lệ.");
            where.append(" AND r.status = ?");
            args.add(normalizedStatus);
        }
        if (severity != null && !severity.isBlank()) {
            try { args.add(ReportSeverity.valueOf(severity.trim().toUpperCase(Locale.ROOT)).name()); }
            catch (IllegalArgumentException ex) { throw ApiException.badRequest("INVALID_FILTER", "Mức độ không hợp lệ."); }
            where.append(" AND r.severity = ?");
        }
        if (Boolean.TRUE.equals(breached)) {
            where.append(" AND r.status IN ('PENDING','WAITING_REPLY','APPEALED') AND r.created_at + make_interval(mins => " + SLA_MINUTES + ") < ?");
            args.add(now);
        }
        if (mine) {
            where.append(" AND r.claimed_by = ? AND r.claimed_until > ?");
            args.add(actorId);
            args.add(now);
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM listing_reports r" + where, Long.class, args.toArray());
        List<Object> dataArgs = new ArrayList<>(List.of(now, now, actorId));
        dataArgs.addAll(args);
        dataArgs.add(safeSize);
        dataArgs.add(safePage * safeSize);
        List<QueueItem> items = jdbc.query("""
                SELECT r.id, r.case_number, r.listing_id, r.category, r.severity, r.status, r.description, r.reporter_phone,
                       r.created_at, r.resolved_at, r.resolution_note,
                       r.created_at + make_interval(mins => %s) AS sla_due_at,
                       r.status IN ('PENDING','WAITING_REPLY','APPEALED') AND r.created_at + make_interval(mins => %s) < ? AS breached,
                       CASE WHEN r.claimed_until > ? THEN r.claimed_by END AS claimed_by, cu.full_name AS claimer, r.claimed_until,
                       r.claimed_by = ? AS claimed_by_me,
                       l.status AS listing_status, l.owner_id, rev.title AS listing_title, l.slug,
                       (SELECT e.type FROM report_events e WHERE e.report_id = r.id AND e.type IN ('OWNER_RESPONSE','AUTO_PAUSED')
                        ORDER BY e.created_at DESC, e.id DESC LIMIT 1) AS owner_outcome
                FROM listing_reports r
                LEFT JOIN users cu ON cu.id = r.claimed_by
                LEFT JOIN listings l ON l.id = r.listing_id
                LEFT JOIN listing_revisions rev ON rev.id = COALESCE(l.public_revision_id,
                    (SELECT x.id FROM listing_revisions x WHERE x.listing_id = l.id ORDER BY x.revision_number DESC LIMIT 1))
                """.formatted(SLA_MINUTES, SLA_MINUTES) + where
                + " ORDER BY (r.status IN ('PENDING','WAITING_REPLY','APPEALED')) DESC, sla_due_at ASC, r.id ASC LIMIT ? OFFSET ?",
                (rs, n) -> {
                    Instant due = rs.getTimestamp("sla_due_at").toInstant();
                    UUID claimedBy = rs.getObject("claimed_by", UUID.class);
                    return new QueueItem(rs.getObject("id", UUID.class), rs.getString("case_number"), rs.getObject("listing_id", UUID.class),
                            rs.getString("listing_title"), rs.getString("slug"), rs.getString("listing_status"),
                            rs.getObject("owner_id", UUID.class), rs.getString("category"), rs.getString("severity"),
                            rs.getString("status"), rs.getString("description"), maskedPhone(rs.getString("reporter_phone")),
                            rs.getTimestamp("created_at").toInstant(), due, rs.getBoolean("breached"),
                            Duration.between(clock.instant(), due).toMinutes(),
                            claimedBy == null ? null : new Claim(claimedBy, rs.getString("claimer"),
                                    rs.getTimestamp("claimed_until").toInstant(), rs.getBoolean("claimed_by_me")),
                            rs.getString("owner_outcome"), rs.getString("resolution_note"),
                            rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toInstant());
                }, dataArgs.toArray());
        return new QueuePage(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional
    public Claim claim(UUID reportId, UUID actorId) {
        Instant now = clock.instant();
        Instant until = now.plus(CLAIM_TTL);
        int changed = jdbc.update("""
                UPDATE listing_reports SET claimed_by = ?, claimed_until = ?, updated_at = ?
                WHERE id = ? AND status IN ('PENDING','WAITING_REPLY','APPEALED')
                  AND (claimed_by IS NULL OR claimed_until <= ? OR claimed_by = ?)
                """, actorId, Timestamp.from(until), Timestamp.from(now), reportId, Timestamp.from(now), actorId);
        if (changed == 0) {
            List<String> state = jdbc.queryForList("SELECT status FROM listing_reports WHERE id = ?", String.class, reportId);
            if (state.isEmpty()) throw ApiException.notFound("REPORT_NOT_FOUND", "Không tìm thấy vụ việc.");
            if (!OPEN.contains(state.get(0))) throw ApiException.conflict("REPORT_CLOSED", "Vụ việc đã đóng.");
            throw conflict(reportId);
        }
        recordEvent(reportId, "CLAIMED", actorId, null, null);
        String name = jdbc.queryForObject("SELECT full_name FROM users WHERE id = ?", String.class, actorId);
        return new Claim(actorId, name, until, true);
    }

    @Transactional
    public void release(UUID reportId, UUID actorId) {
        int changed = jdbc.update("UPDATE listing_reports SET claimed_by = NULL, claimed_until = NULL, updated_at = ? WHERE id = ? AND claimed_by = ?",
                Timestamp.from(clock.instant()), reportId, actorId);
        if (changed > 0) recordEvent(reportId, "RELEASED", actorId, null, null);
    }

    /** Actions on a case are refused while another staff member holds a live claim on it. */
    @Transactional(readOnly = true)
    public void assertActionable(UUID reportId, UUID actorId) {
        List<UUID> holder = jdbc.query("SELECT claimed_by FROM listing_reports WHERE id = ? AND claimed_until > ?",
                (rs, n) -> rs.getObject(1, UUID.class), reportId, Timestamp.from(clock.instant()));
        if (!holder.isEmpty() && holder.get(0) != null && !holder.get(0).equals(actorId)) throw conflict(reportId);
    }

    /** Closes the claim and records who closed the case. */
    @Transactional
    public void closed(UUID reportId, UUID actorId) {
        jdbc.update("UPDATE listing_reports SET claimed_by = NULL, claimed_until = NULL, resolved_by = ?, updated_at = ? WHERE id = ?",
                actorId, Timestamp.from(clock.instant()), reportId);
    }

    @Transactional
    public void escalate(UUID reportId, String severity, String reason, UUID actorId) {
        ReportSeverity target;
        try { target = ReportSeverity.valueOf(severity == null ? "" : severity.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw ApiException.badRequest("INVALID_SEVERITY", "Mức độ không hợp lệ."); }
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 5) throw ApiException.badRequest("REASON_REQUIRED", "Cần nhập lý do (ít nhất 5 ký tự).");
        assertActionable(reportId, actorId);
        List<String> previous = jdbc.queryForList("SELECT severity FROM listing_reports WHERE id = ? FOR UPDATE", String.class, reportId);
        if (previous.isEmpty()) throw ApiException.notFound("REPORT_NOT_FOUND", "Không tìm thấy vụ việc.");
        jdbc.update("UPDATE listing_reports SET severity = ?, updated_at = ? WHERE id = ?", target.name(), Timestamp.from(clock.instant()), reportId);
        recordEvent(reportId, "NOTE", actorId, why, Map.of("severityFrom", previous.get(0), "severityTo", target.name()));
    }

    /** Appends one history entry. Public so the supply flow can record OWNER_RESPONSE / AUTO_PAUSED for FAKE_SOLD cases. */
    @Transactional
    public void recordEvent(UUID reportId, String type, UUID actorId, String note, Map<String, ?> data) {
        if (!EVENT_TYPES.contains(type)) throw new IllegalArgumentException("Unknown report event " + type);
        String trimmed = note == null || note.isBlank() ? null : (note.length() > 1000 ? note.substring(0, 1000) : note.trim());
        jdbc.update("INSERT INTO report_events(id, report_id, type, actor_id, note, data, created_at) VALUES (?,?,?,?,?,CAST(? AS jsonb),?)",
                UUID.randomUUID(), reportId, type, actorId, trimmed, data == null ? null : toJson(data), Timestamp.from(clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<Event> events(UUID reportId) {
        return jdbc.query("""
                SELECT e.id, e.type, e.actor_id, u.full_name, e.note, e.data::text, e.created_at
                FROM report_events e LEFT JOIN users u ON u.id = e.actor_id
                WHERE e.report_id = ? ORDER BY e.created_at, e.id LIMIT 200
                """, (rs, n) -> new Event(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant()), reportId);
    }

    /** Masked phone for staff views: the mask embedded in the protected value, never the plaintext. */
    public static String maskedPhone(String stored) {
        if (stored == null || stored.isBlank()) return null;
        if (stored.startsWith("v1:")) {
            String[] parts = stored.split(":", 3);
            return parts.length >= 2 ? parts[1] : "***";
        }
        // Legacy plaintext not yet migrated: show only the last three digits.
        String digits = stored.replaceAll("\\D", "");
        return digits.length() < 7 ? "***" : "*******" + digits.substring(digits.length() - 3);
    }

    private ApiException conflict(UUID reportId) {
        String holder = jdbc.query("SELECT u.full_name FROM listing_reports r JOIN users u ON u.id = r.claimed_by WHERE r.id = ?",
                (rs, n) -> rs.getString(1), reportId).stream().findFirst().orElse("nhân viên khác");
        return ApiException.conflict("CLAIM_CONFLICT", "Vụ việc đang được " + holder + " xử lý.");
    }

    private String toJson(Map<String, ?> data) {
        try { return json.writeValueAsString(data); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    public record Claim(UUID staffId, String staffName, Instant expiresAt, boolean mine) {}

    public record QueueItem(UUID id, String caseNumber, UUID listingId, String listingTitle, String listingSlug, String listingStatus,
                            UUID ownerId, String category, String severity, String status, String description, String reporterPhoneMasked,
                            Instant createdAt, Instant slaDueAt, boolean slaBreached, long minutesToDue, Claim claim,
                            String ownerOutcome, String resolutionNote, Instant resolvedAt) {}

    public record QueuePage(List<QueueItem> items, int page, int size, long total) {}

    public record Event(UUID id, String type, UUID actorId, String actorName, String note, String data, Instant createdAt) {}
}
