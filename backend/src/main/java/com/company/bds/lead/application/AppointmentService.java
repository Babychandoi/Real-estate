package com.company.bds.lead.application;

import com.company.bds.analytics.application.AnalyticsRecorder;
import com.company.bds.lead.application.LeadAccessService.LeadAccess;
import com.company.bds.lead.application.LeadAccessService.Side;
import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.jobs.JobQueue;
import com.company.bds.shared.security.ContactInfoGuard;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Viewing appointments (P-03): proposals with 1–3 slots, confirmation by the other side, counter-proposal/reschedule,
 * cancellation with reason and outcome (completed / no-show) recorded by the owner side, plus durable reminders (D-12).
 *
 * <p>Concurrency: every command locks the lead row first, then the appointment row (fixed order), and checks the
 * appointment {@code version}; confirmation additionally takes an advisory lock per owner so two leads can never
 * confirm overlapping visits for the same owner. A second confirm of the same proposal therefore gets 409 and has no
 * effect (R-4).</p>
 */
@Service
@Transactional
public class AppointmentService {
    public static final String REMINDER_QUEUE = "appointment-reminder";
    public static final Duration MIN_LEAD_TIME = Duration.ofMinutes(30);
    public static final Duration MAX_AHEAD = Duration.ofDays(60);
    public static final Duration MIN_SLOT = Duration.ofMinutes(15);
    public static final Duration MAX_SLOT = Duration.ofMinutes(180);
    public static final int MAX_SLOTS = 3;
    /** Reminder kinds and how long before the start they fire. */
    public static final Map<String, Duration> REMINDERS = Map.of("H24", Duration.ofHours(24), "H2", Duration.ofHours(2));

    public record SlotInput(Instant startsAt, Instant endsAt) {}

    public record Slot(UUID id, Instant startsAt, Instant endsAt) {}

    public record AppointmentView(UUID id, UUID leadId, UUID listingId, String status, String proposedBySide,
                                  @Nullable Instant startsAt, @Nullable Instant endsAt, @Nullable Instant confirmedAt,
                                  @Nullable Instant cancelledAt, @Nullable String cancelReason, @Nullable Instant outcomeAt,
                                  @Nullable String noShowParty, @Nullable String note, long version, Instant createdAt,
                                  List<Slot> slots, boolean awaitingMe) {}

    private final JdbcTemplate jdbc;
    private final LeadAccessService access;
    private final LeadCommandService leads;
    private final JobQueue jobs;
    private final AnalyticsRecorder analytics;
    private final ObjectProvider<RealtimeNotificationService> notifications;
    private final Clock clock;

    public AppointmentService(JdbcTemplate jdbc, LeadAccessService access, LeadCommandService leads, JobQueue jobs,
                              AnalyticsRecorder analytics, ObjectProvider<RealtimeNotificationService> notifications, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.leads = leads;
        this.jobs = jobs;
        this.analytics = analytics;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Proposes slots. When an open appointment exists, {@code replacesVersion} must be its current version: it becomes
     * RESCHEDULED (a counter-proposal or a reschedule of a confirmed visit) and points to the new proposal.
     */
    public AppointmentView propose(UUID leadId, UUID actorId, boolean privileged, List<SlotInput> slots, @Nullable String note,
                                   @Nullable Long replacesVersion) {
        LeadAccess lead = access.loadForUpdate(leadId);
        Side side = access.sideOf(lead, actorId, privileged);
        if (side == Side.STAFF) throw ApiException.forbidden("PARTIES_ONLY", "Chỉ hai bên của yêu cầu mới đặt lịch hẹn.");
        if (!LeadStatus.OPEN.contains(lead.status())) {
            throw ApiException.conflict("LEAD_NOT_OPEN", "Yêu cầu đã kết thúc nên không thể đặt lịch hẹn.");
        }
        Instant now = clock.instant();
        List<SlotInput> ordered = validateSlots(slots, now);
        String cleanNote = LeadCommandService.clean(note, 500);
        // The requester sees owner-side notes: contact goes through the platform, never the seller's phone/e-mail.
        if (side == Side.OWNER_SIDE) ContactInfoGuard.requireNoContact(cleanNote);

        Map<String, Object> open = openAppointment(leadId);
        UUID newId = UUID.randomUUID();
        String eventType = "APPOINTMENT_PROPOSED";
        if (open != null) {
            if (replacesVersion == null || replacesVersion != ((Number) open.get("version")).longValue()) {
                throw ApiException.conflict("APPOINTMENT_VERSION_CONFLICT",
                        "Lịch hẹn vừa thay đổi. Tải lại để xem lịch mới nhất trước khi đề xuất giờ khác.");
            }
            eventType = "APPOINTMENT_RESCHEDULED";
        }
        if (open != null) {
            // Free the one-open-per-lead slot before inserting the replacement; replaced_by is set right after.
            jdbc.update("UPDATE viewing_appointments SET status = 'RESCHEDULED', version = version + 1, updated_at = ? WHERE id = ?",
                    Timestamp.from(now), open.get("id"));
        }
        jdbc.update("""
                INSERT INTO viewing_appointments(id, lead_id, listing_id, owner_id, requester_id, status, proposed_by_side,
                    proposed_by, note, version, created_at, updated_at)
                VALUES (?,?,?,?,?,'PROPOSED',?,?,?,0,?,?)
                """, newId, leadId, lead.listingId(), lead.ownerId(), lead.requesterId(), side.name(), actorId, cleanNote,
                Timestamp.from(now), Timestamp.from(now));
        for (SlotInput slot : ordered) {
            jdbc.update("INSERT INTO appointment_slots(id, appointment_id, starts_at, ends_at) VALUES (?,?,?,?)",
                    UUID.randomUUID(), newId, Timestamp.from(slot.startsAt()), Timestamp.from(slot.endsAt()));
        }
        if (open != null) {
            jdbc.update("UPDATE viewing_appointments SET replaced_by = ? WHERE id = ?", newId, open.get("id"));
        }
        if (side == Side.OWNER_SIDE) leads.markFirstResponseIfNeeded(lead, now);
        access.recordEvent(leadId, eventType, actorId, side.name(), null, null, cleanNote,
                Map.of("appointmentId", newId.toString(), "slots", ordered.size()), false, now);
        analytics.recordServer("appointment_proposed", 1, newId.toString(), actorId, lead.listingId(),
                Map.of("appointmentId", newId.toString(), "leadId", leadId.toString()));
        notifyOther(lead, side, open == null ? "Có đề xuất lịch hẹn xem" : "Lịch hẹn xem được đề xuất lại",
                "Có " + ordered.size() + " khung giờ đang chờ bạn xác nhận.");
        return view(newId, actorId, lead, side);
    }

    public AppointmentView confirm(UUID appointmentId, UUID actorId, UUID slotId, Long expectedVersion) {
        UUID leadId = leadOf(appointmentId);
        LeadAccess lead = access.loadForUpdate(leadId);
        Side side = access.sideOf(lead, actorId, false);
        Map<String, Object> row = lockAppointment(appointmentId, expectedVersion);
        if (!"PROPOSED".equals(row.get("status"))) {
            throw ApiException.conflict("APPOINTMENT_NOT_PROPOSED", "Lịch hẹn này không còn chờ xác nhận.");
        }
        if (side.name().equals(row.get("proposed_by_side"))) {
            throw ApiException.conflict("CONFIRM_BY_OTHER_SIDE", "Lịch hẹn cần được bên còn lại xác nhận.");
        }
        List<Map<String, Object>> slot = jdbc.queryForList(
                "SELECT starts_at, ends_at FROM appointment_slots WHERE id = ? AND appointment_id = ?", slotId, appointmentId);
        if (slot.isEmpty()) throw ApiException.badRequest("SLOT_NOT_FOUND", "Khung giờ không thuộc đề xuất này.");
        Instant start = ((Timestamp) slot.get(0).get("starts_at")).toInstant();
        Instant end = ((Timestamp) slot.get(0).get("ends_at")).toInstant();
        Instant now = clock.instant();
        if (!start.isAfter(now)) throw ApiException.conflict("SLOT_IN_PAST", "Khung giờ này đã qua; hãy đề xuất giờ khác.");

        // Serialise confirmations per owner, then refuse an overlap with another confirmed visit of the same owner.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "appt-owner:" + lead.ownerId());
        Boolean overlap = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM viewing_appointments WHERE owner_id = ? AND status = 'CONFIRMED' AND id <> ?
                               AND starts_at < ? AND ends_at > ?)
                """, Boolean.class, lead.ownerId(), appointmentId, Timestamp.from(end), Timestamp.from(start));
        if (Boolean.TRUE.equals(overlap)) {
            throw ApiException.conflict("APPOINTMENT_OVERLAP", "Người đăng đã có lịch hẹn khác trùng khung giờ này; hãy chọn giờ khác.");
        }
        long newVersion = ((Number) row.get("version")).longValue() + 1;
        jdbc.update("""
                UPDATE viewing_appointments SET status = 'CONFIRMED', starts_at = ?, ends_at = ?, confirmed_by = ?, confirmed_at = ?,
                       version = ?, updated_at = ? WHERE id = ?
                """, Timestamp.from(start), Timestamp.from(end), actorId, Timestamp.from(now), newVersion, Timestamp.from(now),
                appointmentId);
        if (lead.status() == LeadStatus.NEW || lead.status() == LeadStatus.CONTACTED) {
            jdbc.update("UPDATE leads SET status = 'APPOINTED', version = version + 1, updated_at = ? WHERE id = ?",
                    Timestamp.from(now), leadId);
            access.recordEvent(leadId, "STATUS_CHANGED", null, "SYSTEM", lead.status().name(), "APPOINTED",
                    null, Map.of("appointmentId", appointmentId.toString()), false, now);
        }
        if (side == Side.OWNER_SIDE) leads.markFirstResponseIfNeeded(lead, now);
        access.recordEvent(leadId, "APPOINTMENT_CONFIRMED", actorId, side.name(), null, null, null,
                Map.of("appointmentId", appointmentId.toString(), "startsAt", start.toString()), false, now);
        scheduleReminders(appointmentId, newVersion, start, now);
        analytics.recordServer("appointment_confirmed", 1, appointmentId.toString(), actorId, lead.listingId(),
                Map.of("appointmentId", appointmentId.toString(), "leadId", leadId.toString()));
        notifyOther(lead, side, "Lịch hẹn xem đã được xác nhận", "Hai bên đã thống nhất thời gian xem nhà.");
        return view(appointmentId, actorId, lead, side);
    }

    public AppointmentView cancel(UUID appointmentId, UUID actorId, @Nullable String reason, Long expectedVersion) {
        UUID leadId = leadOf(appointmentId);
        LeadAccess lead = access.loadForUpdate(leadId);
        Side side = access.sideOf(lead, actorId, false);
        Map<String, Object> row = lockAppointment(appointmentId, expectedVersion);
        String status = (String) row.get("status");
        if (!"PROPOSED".equals(status) && !"CONFIRMED".equals(status)) {
            throw ApiException.conflict("APPOINTMENT_NOT_OPEN", "Lịch hẹn này đã kết thúc.");
        }
        String cleanReason = LeadCommandService.clean(reason, 300);
        if (cleanReason == null || cleanReason.length() < 3) {
            throw ApiException.badRequest("CANCEL_REASON_REQUIRED", "Vui lòng cho biết lý do hủy lịch hẹn.");
        }
        if (side == Side.OWNER_SIDE) ContactInfoGuard.requireNoContact(cleanReason);
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE viewing_appointments SET status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, cancel_reason = ?,
                       version = version + 1, updated_at = ? WHERE id = ?
                """, actorId, Timestamp.from(now), cleanReason, Timestamp.from(now), appointmentId);
        access.recordEvent(leadId, "APPOINTMENT_CANCELLED", actorId, side.name(), null, null, cleanReason,
                Map.of("appointmentId", appointmentId.toString()), false, now);
        analytics.recordServer("appointment_cancelled", 1, appointmentId.toString(), actorId, lead.listingId(),
                Map.of("appointmentId", appointmentId.toString(), "leadId", leadId.toString()));
        notifyOther(lead, side, "Lịch hẹn xem đã bị hủy", "Lý do: " + cleanReason);
        return view(appointmentId, actorId, lead, side);
    }

    /** Owner side records what happened once the visit has started. */
    public AppointmentView recordOutcome(UUID appointmentId, UUID actorId, String outcome, @Nullable String noShowParty,
                                         @Nullable String note, Long expectedVersion) {
        UUID leadId = leadOf(appointmentId);
        LeadAccess lead = access.loadForUpdate(leadId);
        Side side = access.sideOf(lead, actorId, false);
        if (side != Side.OWNER_SIDE) throw ApiException.forbidden("OWNER_SIDE_ONLY", "Người phụ trách tin ghi nhận kết quả buổi xem.");
        Map<String, Object> row = lockAppointment(appointmentId, expectedVersion);
        if (!"CONFIRMED".equals(row.get("status"))) {
            throw ApiException.conflict("APPOINTMENT_NOT_CONFIRMED", "Chỉ ghi nhận kết quả cho lịch hẹn đã xác nhận.");
        }
        Instant now = clock.instant();
        Instant start = ((Timestamp) row.get("starts_at")).toInstant();
        if (now.isBefore(start)) throw ApiException.conflict("APPOINTMENT_NOT_STARTED", "Buổi xem chưa diễn ra.");
        String value = outcome == null ? "" : outcome.trim();
        String party = null;
        if ("NO_SHOW".equals(value)) {
            if (!"REQUESTER".equals(noShowParty) && !"OWNER_SIDE".equals(noShowParty)) {
                throw ApiException.badRequest("NO_SHOW_PARTY_REQUIRED", "Cho biết bên nào không đến.");
            }
            party = noShowParty;
        } else if (!"COMPLETED".equals(value)) {
            throw ApiException.badRequest("OUTCOME_INVALID", "Kết quả phải là COMPLETED hoặc NO_SHOW.");
        }
        String cleanNote = LeadCommandService.clean(note, 500);
        ContactInfoGuard.requireNoContact(cleanNote);
        jdbc.update("""
                UPDATE viewing_appointments SET status = ?, outcome_by = ?, outcome_at = ?, no_show_party = ?,
                       note = COALESCE(?, note), version = version + 1, updated_at = ? WHERE id = ?
                """, value, actorId, Timestamp.from(now), party, cleanNote, Timestamp.from(now), appointmentId);
        access.recordEvent(leadId, "COMPLETED".equals(value) ? "APPOINTMENT_COMPLETED" : "APPOINTMENT_NO_SHOW", actorId,
                side.name(), null, null, cleanNote, party == null ? Map.of("appointmentId", appointmentId.toString())
                        : Map.of("appointmentId", appointmentId.toString(), "noShowParty", party), false, now);
        analytics.recordServer("COMPLETED".equals(value) ? "appointment_completed" : "appointment_no_show", 1,
                appointmentId.toString(), actorId, lead.listingId(),
                Map.of("appointmentId", appointmentId.toString(), "leadId", leadId.toString()));
        return view(appointmentId, actorId, lead, side);
    }

    @Transactional(readOnly = true)
    public List<AppointmentView> forLead(UUID leadId, UUID actorId, boolean privileged) {
        LeadAccess lead = access.load(leadId);
        Side side = access.sideOf(lead, actorId, privileged);
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM viewing_appointments WHERE lead_id = ? ORDER BY created_at DESC, id DESC LIMIT 20", UUID.class, leadId);
        List<AppointmentView> result = new ArrayList<>();
        for (UUID id : ids) result.add(view(id, actorId, lead, side));
        return result;
    }

    /** Enqueues the reminder jobs of a confirmed version; past reminder times are skipped. */
    void scheduleReminders(UUID appointmentId, long version, Instant start, Instant now) {
        for (Map.Entry<String, Duration> reminder : REMINDERS.entrySet()) {
            Instant runAt = start.minus(reminder.getValue());
            if (!runAt.isAfter(now)) continue;
            jobs.enqueueOnce(REMINDER_QUEUE, "appt:" + appointmentId + ":v" + version + ":" + reminder.getKey(),
                    Map.of("appointmentId", appointmentId.toString(), "version", version, "kind", reminder.getKey()), runAt);
        }
    }

    static List<SlotInput> validateSlots(List<SlotInput> slots, Instant now) {
        if (slots == null || slots.isEmpty() || slots.size() > MAX_SLOTS) {
            throw ApiException.badRequest("SLOTS_INVALID", "Đề xuất từ 1 đến " + MAX_SLOTS + " khung giờ.");
        }
        List<SlotInput> ordered = new ArrayList<>(slots);
        for (SlotInput slot : ordered) {
            if (slot == null || slot.startsAt() == null || slot.endsAt() == null) {
                throw ApiException.badRequest("SLOTS_INVALID", "Mỗi khung giờ cần thời điểm bắt đầu và kết thúc.");
            }
            Duration length = Duration.between(slot.startsAt(), slot.endsAt());
            if (length.compareTo(MIN_SLOT) < 0 || length.compareTo(MAX_SLOT) > 0) {
                throw ApiException.badRequest("SLOT_LENGTH_INVALID", "Mỗi buổi xem dài từ 15 đến 180 phút.");
            }
            if (slot.startsAt().isBefore(now.plus(MIN_LEAD_TIME))) {
                throw ApiException.badRequest("SLOT_TOO_SOON", "Khung giờ phải bắt đầu sau ít nhất 30 phút kể từ bây giờ.");
            }
            if (slot.startsAt().isAfter(now.plus(MAX_AHEAD))) {
                throw ApiException.badRequest("SLOT_TOO_FAR", "Chỉ đặt lịch trong vòng 60 ngày tới.");
            }
        }
        ordered.sort(Comparator.comparing(SlotInput::startsAt));
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i).startsAt().isBefore(ordered.get(i - 1).endsAt())) {
                throw ApiException.badRequest("SLOTS_OVERLAP", "Các khung giờ đề xuất không được chồng lên nhau.");
            }
        }
        return ordered;
    }

    private UUID leadOf(UUID appointmentId) {
        List<UUID> rows = jdbc.queryForList("SELECT lead_id FROM viewing_appointments WHERE id = ?", UUID.class, appointmentId);
        if (rows.isEmpty()) throw ApiException.notFound("APPOINTMENT_NOT_FOUND", "Không tìm thấy lịch hẹn.");
        return rows.get(0);
    }

    private Map<String, Object> lockAppointment(UUID appointmentId, Long expectedVersion) {
        if (expectedVersion == null) {
            throw ApiException.preconditionRequired("EXPECTED_VERSION_REQUIRED", "Thiếu expectedVersion của lịch hẹn.");
        }
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, proposed_by_side, version, starts_at FROM viewing_appointments WHERE id = ? FOR UPDATE", appointmentId);
        if (((Number) row.get("version")).longValue() != expectedVersion) {
            throw ApiException.conflict("APPOINTMENT_VERSION_CONFLICT", "Lịch hẹn vừa được cập nhật. Tải lại để xem trạng thái mới nhất.");
        }
        return row;
    }

    @Nullable
    private Map<String, Object> openAppointment(UUID leadId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, version, status FROM viewing_appointments WHERE lead_id = ? AND status IN ('PROPOSED','CONFIRMED') FOR UPDATE",
                leadId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private AppointmentView view(UUID appointmentId, UUID actorId, LeadAccess lead, Side side) {
        List<Slot> slots = jdbc.query("SELECT id, starts_at, ends_at FROM appointment_slots WHERE appointment_id = ? ORDER BY starts_at, id",
                (rs, n) -> new Slot(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getTimestamp(3).toInstant()),
                appointmentId);
        return jdbc.queryForObject("""
                SELECT id, lead_id, listing_id, status, proposed_by_side, starts_at, ends_at, confirmed_at, cancelled_at,
                       cancel_reason, outcome_at, no_show_party, note, version, created_at
                FROM viewing_appointments WHERE id = ?
                """, (rs, n) -> map(rs, slots, side), appointmentId);
    }

    private static AppointmentView map(ResultSet rs, List<Slot> slots, Side viewer) throws SQLException {
        String status = rs.getString("status");
        String proposedBy = rs.getString("proposed_by_side");
        boolean awaitingMe = "PROPOSED".equals(status) && viewer != Side.STAFF && !viewer.name().equals(proposedBy);
        return new AppointmentView(rs.getObject("id", UUID.class), rs.getObject("lead_id", UUID.class),
                rs.getObject("listing_id", UUID.class), status, proposedBy, ts(rs, "starts_at"), ts(rs, "ends_at"),
                ts(rs, "confirmed_at"), ts(rs, "cancelled_at"), rs.getString("cancel_reason"), ts(rs, "outcome_at"),
                rs.getString("no_show_party"), rs.getString("note"), rs.getLong("version"), ts(rs, "created_at"), slots,
                awaitingMe);
    }

    @Nullable
    private static Instant ts(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private void notifyOther(LeadAccess lead, Side actorSide, String title, String message) {
        RealtimeNotificationService notifier = notifications.getIfAvailable();
        if (notifier == null) return;
        if (actorSide == Side.OWNER_SIDE) {
            if (lead.requesterId() != null) notifier.notify(lead.requesterId(), "APPOINTMENT_UPDATED", title, message);
        } else {
            UUID handler = lead.assigneeActive() && lead.assigneeId() != null ? lead.assigneeId() : lead.ownerId();
            notifier.notify(handler, "APPOINTMENT_UPDATED", title, message);
        }
    }
}
