package com.company.bds.iam.application;

import com.company.bds.shared.error.ApiException;
import com.company.bds.shared.security.Roles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Reasoned admin actions on accounts (UI-20): role change (never the actor's own, never removing the last active ADMIN,
 * exactly one role row afterwards), lock/unlock, and their history in {@code user_admin_actions}.
 */
@Service
public class AdminUserService {
    /** Every role change takes this transaction-scoped advisory lock, so two admins demoting each other cannot both win. */
    private static final long ROLE_LOCK = 0x5344_4d49_4e52_4f4cL;
    private static final String EFFECTIVE_ROLE = "SELECT " + Roles.effectiveRoleSql("u.id") + " FROM users u WHERE u.id = ?";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AdminUserService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public RoleChange changeRole(UUID targetId, String requestedRole, String reason, UUID actorId) {
        String role = requestedRole == null ? "" : requestedRole.trim().toUpperCase(Locale.ROOT);
        if (!Roles.ALL.contains(role)) throw ApiException.badRequest("INVALID_ROLE", "Vai trò không hợp lệ.");
        String why = requireReason(reason);
        if (targetId.equals(actorId)) throw ApiException.conflict("OWN_ROLE", "Không thể tự thay đổi vai trò của chính mình.");
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, ROLE_LOCK);
        String current = jdbc.query(EFFECTIVE_ROLE, (rs, n) -> rs.getString(1), targetId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Không tìm thấy tài khoản."));
        Integer rowCount = jdbc.queryForObject("SELECT count(*) FROM user_roles WHERE user_id = ?", Integer.class, targetId);
        if (role.equals(current) && rowCount != null && rowCount == 1) {
            throw ApiException.conflict("ROLE_UNCHANGED", "Tài khoản đã có vai trò này.");
        }
        if (Roles.ADMIN.equals(current) && !Roles.ADMIN.equals(role)) {
            Integer otherAdmins = jdbc.queryForObject("""
                    SELECT count(DISTINCT u.id) FROM users u JOIN user_roles r ON r.user_id = u.id
                    WHERE r.role = 'ADMIN' AND u.status = 'ACTIVE' AND u.id <> ?
                    """, Integer.class, targetId);
            if (otherAdmins == null || otherAdmins == 0) {
                throw ApiException.conflict("LAST_ADMIN", "Không thể hạ quyền quản trị viên cuối cùng đang hoạt động.");
            }
        }
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", targetId);
        jdbc.update("INSERT INTO user_roles(user_id, role) VALUES (?, ?)", targetId, role);
        jdbc.update("UPDATE users SET updated_at = ? WHERE id = ?", Timestamp.from(clock.instant()), targetId);
        record(targetId, actorId, "ROLE_CHANGE", current, role, why);
        return new RoleChange(targetId, current, role);
    }

    /** ACTIVE ↔ SUSPENDED with a reason; locking revokes the target's sessions. Admin accounts are not locked here. */
    @Transactional
    public void changeStatus(UUID targetId, String requestedStatus, String reason, UUID actorId) {
        String requested = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "SUSPENDED").contains(requested)) throw ApiException.badRequest("INVALID_STATUS", "Trạng thái tài khoản không hợp lệ.");
        String why = requireReason(reason);
        if (actorId.equals(targetId)) throw ApiException.conflict("OWN_ACCOUNT", "Không thể khóa hoặc mở khóa chính tài khoản đang đăng nhập.");
        List<String[]> targets = jdbc.query("SELECT u.status, " + Roles.effectiveRoleSql("u.id") + " FROM users u WHERE u.id = ? FOR UPDATE OF u",
                (rs, n) -> new String[]{rs.getString(1), rs.getString(2)}, targetId);
        if (targets.isEmpty()) throw ApiException.notFound("USER_NOT_FOUND", "Không tìm thấy tài khoản.");
        String status = targets.get(0)[0];
        if (Roles.ADMIN.equals(targets.get(0)[1])) {
            throw ApiException.conflict("ADMIN_TARGET", "Không thể thay đổi trạng thái của tài khoản quản trị khác; hãy đổi vai trò trước.");
        }
        if ("ACTIVE".equals(requested) && !"SUSPENDED".equals(status)) throw ApiException.conflict("INVALID_TRANSITION", "Chỉ tài khoản đã khóa mới có thể được mở lại.");
        if ("SUSPENDED".equals(requested) && !"ACTIVE".equals(status)) throw ApiException.conflict("INVALID_TRANSITION", "Chỉ tài khoản đang hoạt động mới có thể bị khóa.");
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("UPDATE users SET status = ?, updated_at = ? WHERE id = ?", requested, now, targetId);
        if ("SUSPENDED".equals(requested)) {
            jdbc.update("UPDATE auth_sessions SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL", now, targetId);
        }
        record(targetId, actorId, "SUSPENDED".equals(requested) ? "LOCK" : "UNLOCK", status, requested, why);
    }

    @Transactional(readOnly = true)
    public List<AdminAction> history(UUID targetId) {
        return jdbc.query("""
                SELECT a.id, a.action, a.from_value, a.to_value, a.reason, a.actor_id, u.full_name, a.created_at
                FROM user_admin_actions a LEFT JOIN users u ON u.id = a.actor_id
                WHERE a.target_user_id = ? ORDER BY a.created_at DESC, a.id DESC LIMIT 100
                """, (rs, n) -> new AdminAction(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getObject(6, UUID.class), rs.getString(7), rs.getTimestamp(8).toInstant()), targetId);
    }

    private void record(UUID targetId, UUID actorId, String action, String from, String to, String reason) {
        jdbc.update("""
                INSERT INTO user_admin_actions(id, target_user_id, actor_id, action, from_value, to_value, reason, created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), targetId, actorId, action, from, to, reason, Timestamp.from(clock.instant()));
    }

    static String requireReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < 5) throw ApiException.badRequest("REASON_REQUIRED", "Cần nhập lý do (ít nhất 5 ký tự).");
        if (trimmed.length() > 1000) throw ApiException.badRequest("REASON_TOO_LONG", "Lý do tối đa 1000 ký tự.");
        return trimmed;
    }

    public record RoleChange(UUID userId, String fromRole, String toRole) {}

    public record AdminAction(UUID id, String action, String fromValue, String toValue, String reason, UUID actorId,
                              String actorName, Instant createdAt) {}
}
