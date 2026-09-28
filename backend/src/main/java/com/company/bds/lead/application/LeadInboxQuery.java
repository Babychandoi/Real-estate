package com.company.bds.lead.application;

import com.company.bds.lead.domain.model.LeadRequestType;
import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.shared.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read side of the lead inbox (F08.3, UI-09) and of the requester's inquiries (UI-10).
 *
 * <p>The owner inbox is one JOIN {@code leads ⋈ listings ON owner_id} (plus leads assigned to the actor as an active team
 * member): it never loads the owner's listings first, so an owner with 10,000 listings opens a 20-lead page with two
 * statements (page + status counts). Listing title/slug/address/thumbnail, the assignee name, the SLA due time and the
 * open appointment are joined in the page statement. Order is stable: {@code created_at DESC, id DESC}; size ≤ 50.</p>
 */
@Service
@Transactional(readOnly = true)
public class LeadInboxQuery {
    public static final int MAX_SIZE = 50;
    public static final int DEFAULT_SLA_MINUTES = 30;
    private static final Set<String> QUALIFICATIONS = Set.of("QUALIFIED", "UNQUALIFIED", "UNSET");

    public record InboxFilter(@Nullable UUID ownerId, @Nullable UUID listingId, @Nullable LeadStatus status, @Nullable LeadRequestType requestType,
                              @Nullable String qualification, boolean overdueOnly, @Nullable UUID assigneeId,
                              @Nullable String keyword) {
        public static InboxFilter none() { return new InboxFilter(null, null, null, null, null, false, null, null); }
    }

    public record AppointmentSummary(UUID id, String status, @Nullable Instant startsAt, @Nullable Instant endsAt,
                                     String proposedBySide, long version) {}

    /** Owner-side / staff view of a lead. Phone masked; never the seller's contact. */
    public record LeadItem(UUID id, UUID listingId, String fullName, String maskedPhone, LeadRequestType requestType,
                           @Nullable String note, boolean consentPolicy, LeadStatus status, Instant createdAt, long version,
                           Instant updatedAt, @Nullable Instant firstResponseAt, Instant responseDueAt, boolean overdue,
                           @Nullable String qualification, @Nullable String qualificationReason,
                           @Nullable String qualificationNote, @Nullable UUID assigneeId, @Nullable String assigneeName,
                           @Nullable Instant withdrawnAt, String listingTitle, @Nullable String listingSlug,
                           @Nullable String listingAddress, @Nullable String listingImageUrl,
                           @Nullable AppointmentSummary openAppointment) {}

    /** Requester view: no owner-internal data (qualification, assignee, internal notes). */
    public record InquiryItem(UUID id, UUID listingId, LeadRequestType requestType, @Nullable String note, LeadStatus status,
                              Instant createdAt, long version, Instant updatedAt, @Nullable Instant firstResponseAt,
                              @Nullable Instant withdrawnAt, String listingTitle, @Nullable String listingSlug,
                              @Nullable String listingAddress, @Nullable String listingImageUrl, boolean listingAvailable,
                              @Nullable AppointmentSummary openAppointment) {}

    public record PageResult<T>(List<T> items, long totalElements, int page, int size, int totalPages,
                                Map<LeadStatus, Long> statusCounts) {}

    private static final String LISTING_CONTEXT = """
            LEFT JOIN LATERAL (SELECT r.id, r.title, r.address_summary FROM listing_revisions r WHERE r.listing_id = s.id
                               ORDER BY (r.id = s.public_revision_id) DESC NULLS LAST, r.revision_number DESC LIMIT 1) rv ON TRUE
            LEFT JOIN LATERAL (SELECT m.media_url FROM listing_media m WHERE m.revision_id = rv.id
                               ORDER BY m.is_primary DESC, m.sort_order ASC LIMIT 1) img ON TRUE
            LEFT JOIN viewing_appointments va ON va.lead_id = l.id AND va.status IN ('PROPOSED','CONFIRMED')
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LeadInboxQuery(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public PageResult<LeadItem> inbox(UUID actorId, boolean privileged, InboxFilter filter, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(MAX_SIZE, size));
        Timestamp now = Timestamp.from(clock.instant());
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (!privileged) {
            where.append(" AND").append("""
                     (s.owner_id = ? OR (l.assignee_id = ? AND EXISTS (SELECT 1 FROM broker_team_members tm
                          WHERE tm.owner_id = s.owner_id AND tm.member_id = l.assignee_id AND tm.removed_at IS NULL)))""");
            args.add(actorId);
            args.add(actorId);
        }
        if (filter.ownerId() != null) {
            where.append(" AND s.owner_id = ?");
            args.add(filter.ownerId());
        }
        if (filter.listingId() != null) {
            where.append(" AND l.listing_id = ?");
            args.add(filter.listingId());
        }
        if (filter.requestType() != null) {
            where.append(" AND l.request_type = ?");
            args.add(filter.requestType().name());
        }
        if (filter.qualification() != null && !filter.qualification().isBlank()) {
            String q = filter.qualification().trim().toUpperCase(Locale.ROOT);
            if (!QUALIFICATIONS.contains(q)) throw ApiException.badRequest("INVALID_FILTER", "Bộ lọc đánh giá lead không hợp lệ.");
            if ("UNSET".equals(q)) where.append(" AND l.qualification IS NULL");
            else {
                where.append(" AND l.qualification = ?");
                args.add(q);
            }
        }
        if (filter.assigneeId() != null) {
            where.append(" AND l.assignee_id = ?");
            args.add(filter.assigneeId());
        }
        if (filter.overdueOnly()) {
            where.append(" AND l.status = 'NEW' AND l.created_at + make_interval(mins => COALESCE(bss.first_response_minutes, ")
                    .append(DEFAULT_SLA_MINUTES).append(")) < ?");
            args.add(now);
        }
        String keyword = filter.keyword() == null ? "" : filter.keyword().trim().replaceAll("\\s+", " ");
        if (keyword.length() > 100) throw ApiException.badRequest("INVALID_FILTER", "Từ khóa tối đa 100 ký tự.");
        if (!keyword.isEmpty()) {
            String like = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
            where.append(" AND (LOWER(l.full_name) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(l.note, '')) LIKE ? ESCAPE '\\')");
            args.add(like);
            args.add(like);
        }
        String base = " FROM leads l JOIN listings s ON s.id = l.listing_id LEFT JOIN broker_sla_settings bss ON bss.user_id = s.owner_id";

        // Status counts under every filter except the status itself (one statement, also gives the total).
        Map<LeadStatus, Long> counts = new EnumMap<>(LeadStatus.class);
        for (LeadStatus value : LeadStatus.values()) counts.put(value, 0L);
        jdbc.query("SELECT l.status, COUNT(*)" + base + where + " GROUP BY l.status",
                rs -> { counts.put(LeadStatus.valueOf(rs.getString(1)), rs.getLong(2)); }, args.toArray());

        List<Object> pageArgs = new ArrayList<>();
        pageArgs.add(now);
        pageArgs.addAll(args);
        String statusClause = "";
        if (filter.status() != null) {
            statusClause = " AND l.status = ?";
            pageArgs.add(filter.status().name());
        }
        pageArgs.add(safeSize);
        pageArgs.add((long) safePage * safeSize);
        String sql = """
                SELECT l.id, l.listing_id, l.full_name, l.phone_encrypted, l.request_type, l.note, l.consent_policy, l.status,
                       l.created_at, l.version, l.updated_at, l.first_response_at, l.qualification, l.qualification_reason,
                       l.qualification_note, l.assignee_id, au.full_name AS assignee_name, l.withdrawn_at,
                       l.created_at + make_interval(mins => COALESCE(bss.first_response_minutes, %d)) AS due_at,
                       (l.status = 'NEW' AND l.created_at + make_interval(mins => COALESCE(bss.first_response_minutes, %d)) < ?) AS overdue,
                       rv.title, s.slug, rv.address_summary, img.media_url,
                       va.id AS appt_id, va.status AS appt_status, va.starts_at AS appt_start, va.ends_at AS appt_end,
                       va.proposed_by_side AS appt_side, va.version AS appt_version
                """.formatted(DEFAULT_SLA_MINUTES, DEFAULT_SLA_MINUTES)
                + base + " LEFT JOIN users au ON au.id = l.assignee_id " + LISTING_CONTEXT
                + where + statusClause + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?";
        List<LeadItem> items = jdbc.query(sql, (rs, row) -> mapItem(rs), pageArgs.toArray());
        long total = filter.status() != null ? counts.get(filter.status()) : counts.values().stream().mapToLong(Long::longValue).sum();
        return new PageResult<>(items, total, safePage, safeSize, totalPages(total, safeSize), counts);
    }

    /**
     * A listing filter is allowed for the listing owner and for members of the owner's team; anyone else gets 403 (the
     * legacy contract of {@code GET /leads?listingId=}), never an empty page that would hide the refusal.
     */
    public void requireListingAccess(UUID listingId, UUID actorId) {
        Boolean allowed = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM listings s WHERE s.id = ? AND (s.owner_id = ? OR EXISTS (
                    SELECT 1 FROM broker_team_members tm WHERE tm.owner_id = s.owner_id AND tm.member_id = ? AND tm.removed_at IS NULL)))
                """, Boolean.class, listingId, actorId, actorId);
        if (!Boolean.TRUE.equals(allowed)) {
            throw new org.springframework.security.access.AccessDeniedException("Không có quyền xem lead của tin đăng này.");
        }
    }

    /** One lead for the owner side / staff (access already checked by the caller). */
    public LeadItem item(UUID leadId) {
        Timestamp now = Timestamp.from(clock.instant());
        List<LeadItem> rows = jdbc.query("""
                SELECT l.id, l.listing_id, l.full_name, l.phone_encrypted, l.request_type, l.note, l.consent_policy, l.status,
                       l.created_at, l.version, l.updated_at, l.first_response_at, l.qualification, l.qualification_reason,
                       l.qualification_note, l.assignee_id, au.full_name AS assignee_name, l.withdrawn_at,
                       l.created_at + make_interval(mins => COALESCE(bss.first_response_minutes, %d)) AS due_at,
                       (l.status = 'NEW' AND l.created_at + make_interval(mins => COALESCE(bss.first_response_minutes, %d)) < ?) AS overdue,
                       rv.title, s.slug, rv.address_summary, img.media_url,
                       va.id AS appt_id, va.status AS appt_status, va.starts_at AS appt_start, va.ends_at AS appt_end,
                       va.proposed_by_side AS appt_side, va.version AS appt_version
                FROM leads l JOIN listings s ON s.id = l.listing_id LEFT JOIN broker_sla_settings bss ON bss.user_id = s.owner_id
                LEFT JOIN users au ON au.id = l.assignee_id
                """.formatted(DEFAULT_SLA_MINUTES, DEFAULT_SLA_MINUTES) + LISTING_CONTEXT + " WHERE l.id = ?",
                (rs, row) -> mapItem(rs), now, leadId);
        if (rows.isEmpty()) throw ApiException.notFound("LEAD_NOT_FOUND", "Không tìm thấy yêu cầu liên hệ.");
        return rows.get(0);
    }

    /** One inquiry of the requester, or 404. */
    public InquiryItem inquiry(UUID requesterId, UUID leadId) {
        List<InquiryItem> rows = jdbc.query(INQUIRY_SELECT + " WHERE l.requester_id = ? AND l.id = ?",
                (rs, row) -> mapInquiry(rs), requesterId, leadId);
        if (rows.isEmpty()) throw ApiException.notFound("LEAD_NOT_FOUND", "Không tìm thấy yêu cầu liên hệ.");
        return rows.get(0);
    }

    public PageResult<InquiryItem> inquiries(UUID requesterId, @Nullable LeadStatus status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(MAX_SIZE, size));
        Map<LeadStatus, Long> counts = new EnumMap<>(LeadStatus.class);
        for (LeadStatus value : LeadStatus.values()) counts.put(value, 0L);
        jdbc.query("SELECT status, COUNT(*) FROM leads WHERE requester_id = ? GROUP BY status",
                rs -> { counts.put(LeadStatus.valueOf(rs.getString(1)), rs.getLong(2)); }, requesterId);
        List<Object> args = new ArrayList<>(List.of(requesterId));
        String statusClause = "";
        if (status != null) {
            statusClause = " AND l.status = ?";
            args.add(status.name());
        }
        args.add(safeSize);
        args.add((long) safePage * safeSize);
        List<InquiryItem> items = jdbc.query(INQUIRY_SELECT + " WHERE l.requester_id = ?" + statusClause
                + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?", (rs, row) -> mapInquiry(rs), args.toArray());
        long total = status != null ? counts.get(status) : counts.values().stream().mapToLong(Long::longValue).sum();
        return new PageResult<>(items, total, safePage, safeSize, totalPages(total, safeSize), counts);
    }

    /** History; the requester never sees internal entries (qualification, assignment) nor who on the owner side acted. */
    public List<Map<String, Object>> history(UUID leadId, boolean requesterView) {
        return jdbc.query("""
                SELECT e.id, e.type, e.actor_side, e.from_status, e.to_status, e.note, e.created_at, u.full_name AS actor_name
                FROM lead_events e LEFT JOIN users u ON u.id = e.actor_id
                WHERE e.lead_id = ?""" + (requesterView ? " AND e.internal = FALSE" : "")
                + " ORDER BY e.created_at DESC, e.id DESC LIMIT 200", (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getObject("id", UUID.class));
            item.put("type", rs.getString("type"));
            item.put("actorSide", rs.getString("actor_side"));
            item.put("fromStatus", rs.getString("from_status"));
            item.put("toStatus", rs.getString("to_status"));
            String side = rs.getString("actor_side");
            // A requester sees notes they wrote and the reason of a cancellation, not owner-side internal remarks.
            item.put("note", requesterView && !"REQUESTER".equals(side) && !rs.getString("type").startsWith("APPOINTMENT_")
                    ? null : rs.getString("note"));
            item.put("actorName", requesterView ? null : rs.getString("actor_name"));
            item.put("createdAt", instant(rs, "created_at"));
            return item;
        }, leadId);
    }

    static String maskedPhone(String encrypted) {
        if (encrypted != null && encrypted.startsWith("v1:")) return encrypted.split(":", 4)[1];
        return "***";
    }

    private static final String INQUIRY_SELECT = """
            SELECT l.id, l.listing_id, l.request_type, l.note, l.status, l.created_at, l.version, l.updated_at,
                   l.first_response_at, l.withdrawn_at, rv.title, s.slug, rv.address_summary, img.media_url,
                   (s.status = 'ACTIVE' AND s.public_revision_id IS NOT NULL) AS available,
                   va.id AS appt_id, va.status AS appt_status, va.starts_at AS appt_start, va.ends_at AS appt_end,
                   va.proposed_by_side AS appt_side, va.version AS appt_version
            FROM leads l JOIN listings s ON s.id = l.listing_id
            """ + LISTING_CONTEXT;

    private static InquiryItem mapInquiry(ResultSet rs) throws SQLException {
        return new InquiryItem(rs.getObject("id", UUID.class), rs.getObject("listing_id", UUID.class),
                LeadRequestType.valueOf(rs.getString("request_type")), rs.getString("note"),
                LeadStatus.valueOf(rs.getString("status")), instant(rs, "created_at"), rs.getLong("version"),
                instant(rs, "updated_at"), instant(rs, "first_response_at"), instant(rs, "withdrawn_at"),
                title(rs), rs.getString("slug"), rs.getString("address_summary"), rs.getString("media_url"),
                rs.getBoolean("available"), appointment(rs));
    }

    private static LeadItem mapItem(ResultSet rs) throws SQLException {
        return new LeadItem(rs.getObject("id", UUID.class), rs.getObject("listing_id", UUID.class),
                rs.getString("full_name"), maskedPhone(rs.getString("phone_encrypted")),
                LeadRequestType.valueOf(rs.getString("request_type")), rs.getString("note"), rs.getBoolean("consent_policy"),
                LeadStatus.valueOf(rs.getString("status")), instant(rs, "created_at"), rs.getLong("version"),
                instant(rs, "updated_at"), instant(rs, "first_response_at"), instant(rs, "due_at"),
                rs.getBoolean("overdue"), rs.getString("qualification"), rs.getString("qualification_reason"),
                rs.getString("qualification_note"), rs.getObject("assignee_id", UUID.class), rs.getString("assignee_name"),
                instant(rs, "withdrawn_at"), title(rs), rs.getString("slug"), rs.getString("address_summary"),
                rs.getString("media_url"), appointment(rs));
    }

    private static String title(ResultSet rs) throws SQLException {
        String title = rs.getString("title");
        return title == null ? "Tin đăng" : title;
    }

    @Nullable
    private static AppointmentSummary appointment(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("appt_id", UUID.class);
        if (id == null) return null;
        return new AppointmentSummary(id, rs.getString("appt_status"), instant(rs, "appt_start"), instant(rs, "appt_end"),
                rs.getString("appt_side"), rs.getLong("appt_version"));
    }

    @Nullable
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static int totalPages(long total, int size) {
        return total == 0 ? 0 : (int) Math.ceil((double) total / size);
    }

    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
