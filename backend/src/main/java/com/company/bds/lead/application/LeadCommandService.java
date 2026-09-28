package com.company.bds.lead.application;

import com.company.bds.analytics.application.AnalyticsRecorder;
import com.company.bds.lead.application.LeadAccessService.LeadAccess;
import com.company.bds.lead.application.LeadAccessService.Side;
import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.notification.RealtimeNotificationService;
import com.company.bds.shared.error.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owner-side and requester-side lead writes (UI-09, UI-10, P-08, R-4). Every write is a compare-and-set on
 * {@code leads.version}: a stale {@code expectedVersion} gets 409 {@code LEAD_VERSION_CONFLICT} (with the current lead in
 * the problem body via {@link LeadVersionConflictException}), so two people answering the same lead never overwrite each
 * other silently. Each change appends a {@code lead_events} row in the same transaction.
 */
@Service
@Transactional
public class LeadCommandService {
    public static final Set<String> QUALIFIED_REASONS = Set.of("BUDGET_MATCH", "READY_TO_VIEW", "DECISION_MAKER", "OTHER");
    public static final Set<String> UNQUALIFIED_REASONS = Set.of("BUDGET_MISMATCH", "NOT_REACHABLE", "JUST_BROWSING",
            "DUPLICATE", "SPAM", "LISTING_UNAVAILABLE", "OTHER");

    private final JdbcTemplate jdbc;
    private final LeadAccessService access;
    private final LeadInboxQuery query;
    private final AnalyticsRecorder analytics;
    private final ObjectProvider<RealtimeNotificationService> notifications;
    private final Clock clock;

    public LeadCommandService(JdbcTemplate jdbc, LeadAccessService access, LeadInboxQuery query, AnalyticsRecorder analytics,
                              ObjectProvider<RealtimeNotificationService> notifications, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.query = query;
        this.analytics = analytics;
        this.notifications = notifications;
        this.clock = clock;
    }

    public LeadInboxQuery.LeadItem changeStatus(UUID leadId, UUID actorId, boolean privileged, LeadStatus target,
                                                Long expectedVersion, @Nullable String note) {
        if (target == LeadStatus.WITHDRAWN) {
            throw ApiException.badRequest("WITHDRAW_REQUESTER_ONLY", "Chỉ người gửi yêu cầu mới có thể rút yêu cầu liên hệ.");
        }
        LeadAccess lead = access.requireOwnerSide(leadId, actorId, privileged);
        // Legacy clients without expectedVersion still get an atomic compare-and-set against the version read here;
        // current clients always send it, so a change they have not seen is never overwritten.
        requireVersion(expectedVersion == null ? lead.version() : expectedVersion, lead);
        if (!lead.status().ownerCanMoveTo(target)) {
            throw ApiException.conflict("LEAD_TRANSITION_INVALID", "Không thể chuyển yêu cầu từ trạng thái "
                    + lead.status() + " sang " + target + ".");
        }
        Side side = access.sideOf(lead, actorId, privileged);
        Instant now = clock.instant();
        boolean firstResponse = side == Side.OWNER_SIDE && lead.firstResponseAt() == null && lead.status() == LeadStatus.NEW;
        compareAndSet(lead, "status = ?, first_response_at = COALESCE(first_response_at, ?)",
                target.name(), firstResponse ? Timestamp.from(now) : null);
        access.recordEvent(leadId, "STATUS_CHANGED", actorId, side.name(), lead.status().name(), target.name(),
                clean(note, 500), Map.of(), false, now);
        if (firstResponse) recordFirstResponse(lead, now);
        notifyRequester(lead, "Người đăng đã cập nhật yêu cầu của bạn", switch (target) {
            case CONTACTED -> "Người phụ trách đã tiếp nhận và sẽ liên hệ với bạn.";
            case APPOINTED -> "Yêu cầu của bạn đã được chuyển sang giai đoạn hẹn xem.";
            case CLOSED -> "Người phụ trách đã đóng yêu cầu liên hệ.";
            case SPAM -> "Yêu cầu liên hệ được đánh dấu không hợp lệ.";
            default -> "Trạng thái yêu cầu liên hệ đã thay đổi.";
        });
        return query.item(leadId);
    }

    /** Called by the appointment flow: a proposal from the owner side is also a first response. */
    public void markFirstResponseIfNeeded(LeadAccess lead, Instant now) {
        if (lead.firstResponseAt() != null) return;
        int updated = jdbc.update("UPDATE leads SET first_response_at = ? WHERE id = ? AND first_response_at IS NULL",
                Timestamp.from(now), lead.leadId());
        if (updated == 1) recordFirstResponse(lead, now);
    }

    public LeadInboxQuery.LeadItem qualify(UUID leadId, UUID actorId, boolean privileged, @Nullable String qualification,
                                           @Nullable String reason, @Nullable String note, Long expectedVersion) {
        LeadAccess lead = access.requireOwnerSide(leadId, actorId, privileged);
        if (access.sideOf(lead, actorId, privileged) != Side.OWNER_SIDE) {
            throw ApiException.forbidden("OWNER_SIDE_ONLY", "Chỉ người phụ trách tin mới đánh giá được lead.");
        }
        requireVersion(expectedVersion, lead);
        String value = qualification == null || qualification.isBlank() ? null : qualification.trim();
        String reasonCode = reason == null || reason.isBlank() ? null : reason.trim();
        if (value != null) {
            Set<String> allowed = switch (value) {
                case "QUALIFIED" -> QUALIFIED_REASONS;
                case "UNQUALIFIED" -> UNQUALIFIED_REASONS;
                default -> throw ApiException.badRequest("QUALIFICATION_INVALID", "Đánh giá lead không hợp lệ.");
            };
            if (reasonCode == null || !allowed.contains(reasonCode)) {
                throw ApiException.badRequest("QUALIFICATION_REASON_REQUIRED", "Chọn lý do đánh giá hợp lệ.");
            }
        } else reasonCode = null;
        Instant now = clock.instant();
        String cleanNote = value == null ? null : clean(note, 500);
        compareAndSet(lead, "qualification = ?, qualification_reason = ?, qualification_note = ?, qualified_at = ?",
                value, reasonCode, cleanNote, value == null ? null : Timestamp.from(now));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("qualification", value);
        data.put("reason", reasonCode);
        access.recordEvent(leadId, "QUALIFIED", actorId, Side.OWNER_SIDE.name(), null, null, cleanNote, data, true, now);
        if (value != null) {
            // Key per lead + value + version so a re-qualification is a new fact, a retry of the same one is not.
            analytics.recordServer("lead_qualified", 1, leadId + ":" + value + ":" + (lead.version() + 1), actorId,
                    lead.listingId(), Map.of("leadId", leadId.toString(), "qualification", value));
        }
        return query.item(leadId);
    }

    public LeadInboxQuery.LeadItem assign(UUID leadId, UUID actorId, boolean privileged, @Nullable UUID assigneeId,
                                          Long expectedVersion) {
        LeadAccess lead = access.load(leadId);
        // Only the listing owner distributes its leads (a member cannot hand leads on).
        if (!lead.ownerId().equals(actorId)) {
            access.sideOf(lead, actorId, privileged);
            throw ApiException.forbidden("OWNER_ONLY", "Chỉ chủ tin đăng mới phân công được lead.");
        }
        requireVersion(expectedVersion, lead);
        UUID target = assigneeId == null || assigneeId.equals(lead.ownerId()) ? null : assigneeId;
        if (target != null) {
            Boolean member = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM broker_team_members WHERE owner_id = ? AND member_id = ? AND removed_at IS NULL)
                    """, Boolean.class, lead.ownerId(), target);
            if (!Boolean.TRUE.equals(member)) {
                throw ApiException.badRequest("ASSIGNEE_NOT_IN_TEAM", "Người được phân công phải là thành viên nhóm của bạn.");
            }
        }
        Instant now = clock.instant();
        compareAndSet(lead, "assignee_id = ?, assigned_at = ?", target, Timestamp.from(now));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assigneeId", target);
        data.put("previousAssigneeId", lead.assigneeId());
        access.recordEvent(leadId, "ASSIGNED", actorId, Side.OWNER_SIDE.name(), null, null, null, data, true, now);
        if (target != null) {
            RealtimeNotificationService notifier = notifications.getIfAvailable();
            if (notifier != null) {
                notifier.notify(target, "LEAD_ASSIGNED", "Bạn được phân công một lead",
                        "Một yêu cầu liên hệ vừa được giao cho bạn trong Không gian môi giới.");
            }
        }
        return query.item(leadId);
    }

    /** Requester withdraws an open request (UI-10); open appointments are cancelled in the same transaction. */
    public LeadInboxQuery.InquiryItem withdraw(UUID leadId, UUID requesterId, @Nullable String reason, Long expectedVersion) {
        LeadAccess lead = access.loadForUpdate(leadId);
        if (!requesterId.equals(lead.requesterId())) {
            throw ApiException.notFound("LEAD_NOT_FOUND", "Không tìm thấy yêu cầu liên hệ.");
        }
        requireVersion(expectedVersion, lead);
        if (!LeadStatus.OPEN.contains(lead.status())) {
            throw ApiException.conflict("LEAD_NOT_OPEN", "Yêu cầu này đã kết thúc nên không thể rút.");
        }
        Instant now = clock.instant();
        String cleanReason = clean(reason, 300);
        compareAndSet(lead, "status = 'WITHDRAWN', withdrawn_at = ?, withdraw_reason = ?", Timestamp.from(now), cleanReason);
        List<UUID> cancelled = jdbc.queryForList("""
                UPDATE viewing_appointments SET status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?,
                       cancel_reason = 'Khách đã rút yêu cầu liên hệ', version = version + 1, updated_at = ?
                WHERE lead_id = ? AND status IN ('PROPOSED','CONFIRMED') RETURNING id
                """, UUID.class, requesterId, Timestamp.from(now), Timestamp.from(now), leadId);
        access.recordEvent(leadId, "WITHDRAWN", requesterId, Side.REQUESTER.name(), lead.status().name(), "WITHDRAWN",
                cleanReason, Map.of("cancelledAppointments", cancelled.size()), false, now);
        for (UUID appointmentId : cancelled) {
            analytics.recordServer("appointment_cancelled", 1, appointmentId.toString(), requesterId, lead.listingId(),
                    Map.of("appointmentId", appointmentId.toString(), "leadId", leadId.toString()));
        }
        RealtimeNotificationService notifier = notifications.getIfAvailable();
        if (notifier != null) {
            UUID handler = lead.assigneeActive() && lead.assigneeId() != null ? lead.assigneeId() : lead.ownerId();
            notifier.notify(handler, "LEAD_WITHDRAWN", "Khách đã rút yêu cầu liên hệ",
                    "Một yêu cầu liên hệ đã được người gửi rút lại" + (cancelled.isEmpty() ? "." : "; lịch hẹn liên quan đã hủy."));
        }
        return query.inquiry(requesterId, leadId);
    }

    private void compareAndSet(LeadAccess lead, String assignments, Object... values) {
        Object[] args = new Object[values.length + 3];
        System.arraycopy(values, 0, args, 0, values.length);
        args[values.length] = Timestamp.from(clock.instant());
        args[values.length + 1] = lead.leadId();
        args[values.length + 2] = lead.version();
        int updated = jdbc.update("UPDATE leads SET " + assignments + ", version = version + 1, updated_at = ? WHERE id = ? AND version = ?", args);
        if (updated != 1) throw conflict(lead.leadId());
    }

    private void requireVersion(Long expectedVersion, LeadAccess lead) {
        if (expectedVersion == null) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "EXPECTED_VERSION_REQUIRED",
                    "Thiếu expectedVersion: tải lại yêu cầu rồi thử lại.");
        }
        if (expectedVersion != lead.version()) throw conflict(lead.leadId());
    }

    private LeadVersionConflictException conflict(UUID leadId) {
        return new LeadVersionConflictException(leadId);
    }

    private void recordFirstResponse(LeadAccess lead, Instant now) {
        long minutes = Math.max(0, Duration.between(lead.createdAt(), now).toMinutes());
        analytics.recordServer("lead_first_response", 1, lead.leadId().toString(), null, lead.listingId(),
                Map.of("leadId", lead.leadId().toString(), "minutes", minutes));
    }

    private void notifyRequester(LeadAccess lead, String title, String message) {
        RealtimeNotificationService notifier = notifications.getIfAvailable();
        if (notifier != null && lead.requesterId() != null) notifier.notify(lead.requesterId(), "INQUIRY_UPDATED", title, message);
    }

    @Nullable
    static String clean(@Nullable String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) throw ApiException.badRequest("TEXT_TOO_LONG", "Nội dung tối đa " + max + " ký tự.");
        return trimmed;
    }
}
