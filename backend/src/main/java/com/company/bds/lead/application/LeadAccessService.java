package com.company.bds.lead.application;

import com.company.bds.lead.domain.model.LeadStatus;
import com.company.bds.shared.error.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who may act on a lead, and on which side. The owner side is the listing owner or the lead's assignee while they are an
 * active member of the owner's team (V051); the requester side is the account that sent the lead; staff can read and
 * moderate. Every lead command resolves its actor here, so the rule lives in one place. Also writes the append-only
 * lead history ({@code lead_events}).
 */
@Service
public class LeadAccessService {
    public enum Side { REQUESTER, OWNER_SIDE, STAFF }

    public record LeadAccess(UUID leadId, UUID listingId, UUID ownerId, @Nullable UUID requesterId, @Nullable UUID assigneeId,
                             LeadStatus status, long version, Instant createdAt, @Nullable Instant firstResponseAt,
                             boolean assigneeActive) {
        public boolean isOwnerSide(UUID actor) {
            return ownerId.equals(actor) || (assigneeActive && actor.equals(assigneeId));
        }
    }

    private static final String SELECT = """
            SELECT l.id, l.listing_id, s.owner_id, l.requester_id, l.assignee_id, l.status, l.version, l.created_at,
                   l.first_response_at,
                   EXISTS (SELECT 1 FROM broker_team_members m WHERE m.owner_id = s.owner_id AND m.member_id = l.assignee_id
                           AND m.removed_at IS NULL) AS assignee_active
            FROM leads l JOIN listings s ON s.id = l.listing_id
            WHERE l.id = ?
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public LeadAccessService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public LeadAccess load(UUID leadId) {
        List<LeadAccess> rows = jdbc.query(SELECT, LeadAccessService::map, leadId);
        if (rows.isEmpty()) throw ApiException.notFound("LEAD_NOT_FOUND", "Không tìm thấy yêu cầu liên hệ.");
        return rows.get(0);
    }

    /** Locks the lead row (appointment and withdrawal flows serialise on it). */
    public LeadAccess loadForUpdate(UUID leadId) {
        List<LeadAccess> rows = jdbc.query(SELECT.replace("WHERE l.id = ?", "WHERE l.id = ? FOR UPDATE OF l"),
                LeadAccessService::map, leadId);
        if (rows.isEmpty()) throw ApiException.notFound("LEAD_NOT_FOUND", "Không tìm thấy yêu cầu liên hệ.");
        return rows.get(0);
    }

    /** The side the actor acts on; staff only when {@code privileged}. Anyone else gets 404 (no existence leak). */
    public Side sideOf(LeadAccess lead, UUID actor, boolean privileged) {
        if (lead.isOwnerSide(actor)) return Side.OWNER_SIDE;
        if (actor.equals(lead.requesterId())) return Side.REQUESTER;
        if (privileged) return Side.STAFF;
        throw ApiException.notFound("LEAD_NOT_FOUND", "Không tìm thấy yêu cầu liên hệ.");
    }

    public LeadAccess requireOwnerSide(UUID leadId, UUID actor, boolean privileged) {
        LeadAccess lead = load(leadId);
        Side side = sideOf(lead, actor, privileged);
        if (side == Side.REQUESTER) throw ApiException.forbidden("OWNER_SIDE_ONLY", "Chỉ người phụ trách tin mới thực hiện được thao tác này.");
        return lead;
    }

    public void recordEvent(UUID leadId, String type, @Nullable UUID actorId, String actorSide, @Nullable String fromStatus,
                            @Nullable String toStatus, @Nullable String note, Map<String, ?> data, boolean internal, Instant at) {
        String payload;
        try {
            payload = json.writeValueAsString(data == null ? Map.of() : data);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialise lead event data", ex);
        }
        jdbc.update("""
                INSERT INTO lead_events(id, lead_id, type, actor_id, actor_side, from_status, to_status, note, data, internal, created_at)
                VALUES (?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?)
                """, UUID.randomUUID(), leadId, type, actorId, actorSide, fromStatus, toStatus, note, payload, internal,
                Timestamp.from(at));
    }

    private static LeadAccess map(ResultSet rs, int row) throws SQLException {
        Timestamp first = rs.getTimestamp("first_response_at");
        return new LeadAccess(rs.getObject("id", UUID.class), rs.getObject("listing_id", UUID.class),
                rs.getObject("owner_id", UUID.class), rs.getObject("requester_id", UUID.class),
                rs.getObject("assignee_id", UUID.class), LeadStatus.valueOf(rs.getString("status")), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), first == null ? null : first.toInstant(),
                rs.getBoolean("assignee_active"));
    }
}
