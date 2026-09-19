package com.company.bds.iam.api;

import com.company.bds.shared.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private static final Set<String> ROLES = Set.of("USER", "BROKER", "MODERATOR", "ADMIN");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED", "PENDING_EMAIL_VERIFICATION");
    private final JdbcTemplate jdbc;

    public AdminUserController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public UserPage list(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String normalizedQuery = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        String normalizedRole = normalizeFilter(role, ROLES);
        String normalizedStatus = normalizeFilter(status, STATUSES);
        String where = """
                WHERE (? = '%%' OR LOWER(u.full_name) LIKE ? OR LOWER(COALESCE(u.email, '')) LIKE ?)
                  AND (? = '' OR ur.role = ?)
                  AND (? = '' OR u.status = ?)
                """;
        Object[] countArgs = {normalizedQuery, normalizedQuery, normalizedQuery, normalizedRole, normalizedRole, normalizedStatus, normalizedStatus};
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM users u JOIN user_roles ur ON ur.user_id=u.id " + where, Long.class, countArgs);
        Object[] dataArgs = {normalizedQuery, normalizedQuery, normalizedQuery, normalizedRole, normalizedRole, normalizedStatus, normalizedStatus, safeSize, safePage * safeSize};
        List<UserSummary> items = jdbc.query("""
                SELECT u.id,u.full_name,u.email,u.status,u.created_at,u.email_verified_at,
                       u.plan_code,u.plan_expires_at,u.listing_quota_remaining,ur.role,
                       COALESCE(k.status, 'NOT_SUBMITTED') AS kyc_status,
                       (SELECT MAX(s.created_at) FROM auth_sessions s WHERE s.user_id=u.id) AS last_login_at,
                       (SELECT COUNT(*) FROM listings l WHERE l.owner_id=u.id) AS listing_count
                FROM users u
                JOIN user_roles ur ON ur.user_id=u.id
                LEFT JOIN user_kyc_profiles k ON k.user_id=u.id
                """ + where + " ORDER BY u.created_at DESC LIMIT ? OFFSET ?", (rs, row) -> new UserSummary(
                rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("email"),
                rs.getString("role"), rs.getString("status"), rs.getString("kyc_status"),
                rs.getString("plan_code"), rs.getInt("listing_quota_remaining"), rs.getLong("listing_count"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("email_verified_at")),
                instant(rs.getTimestamp("last_login_at")), instant(rs.getTimestamp("plan_expires_at"))), dataArgs);
        return new UserPage(items, safePage, safeSize, total == null ? 0 : total);
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest request, Authentication authentication) {
        String requested = request.status() == null ? "" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "SUSPENDED").contains(requested)) throw new IllegalArgumentException("Trạng thái tài khoản không hợp lệ.");
        if (CurrentUser.id(authentication).equals(id)) throw new IllegalArgumentException("Không thể khóa hoặc mở khóa chính tài khoản đang đăng nhập.");
        List<TargetUser> targets = jdbc.query("""
                SELECT u.status,ur.role FROM users u JOIN user_roles ur ON ur.user_id=u.id WHERE u.id=?
                """, (rs, row) -> new TargetUser(rs.getString(1), rs.getString(2)), id);
        if (targets.isEmpty()) throw new IllegalArgumentException("Không tìm thấy tài khoản.");
        TargetUser target = targets.get(0);
        if ("ADMIN".equals(target.role())) throw new IllegalArgumentException("Không thể thay đổi trạng thái của tài khoản quản trị khác.");
        if ("ACTIVE".equals(requested) && !"SUSPENDED".equals(target.status())) throw new IllegalArgumentException("Chỉ tài khoản đã khóa mới có thể được mở lại.");
        if ("SUSPENDED".equals(requested) && !"ACTIVE".equals(target.status())) throw new IllegalArgumentException("Chỉ tài khoản đang hoạt động mới có thể bị khóa.");
        jdbc.update("UPDATE users SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", requested, id);
        if ("SUSPENDED".equals(requested)) jdbc.update("UPDATE auth_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL", id);
        return ResponseEntity.noContent().build();
    }

    private static String normalizeFilter(String value, Set<String> accepted) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return "";
        if (!accepted.contains(normalized)) throw new IllegalArgumentException("Bộ lọc không hợp lệ.");
        return normalized;
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    public record UserPage(List<UserSummary> items, int page, int size, long total) {}
    public record UserSummary(UUID id, String fullName, String email, String role, String status, String kycStatus,
                              String planCode, int listingQuotaRemaining, long listingCount, Instant createdAt,
                              Instant emailVerifiedAt, Instant lastLoginAt, Instant planExpiresAt) {}
    public record UpdateStatusRequest(String status) {}
    private record TargetUser(String status, String role) {}
}
