package com.company.bds.iam.api;

import com.company.bds.iam.application.AdminUserService;
import com.company.bds.shared.security.CurrentUser;
import com.company.bds.verification.application.KycDocumentAccessService;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.PostMapping;
import com.company.bds.shared.security.Roles;
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
    private static final Set<String> ROLES = Roles.ALL;
    /** One row per user with its effective role (priority order), also for accounts with several role rows. */
    private static final String USERS_WITH_ROLE = "FROM users u CROSS JOIN LATERAL (SELECT "
            + Roles.effectiveRoleSql("u.id") + " AS role) ur ";
    private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED", "PENDING_EMAIL_VERIFICATION");
    private final JdbcTemplate jdbc;
    private final AdminUserService adminUsers;
    private final KycDocumentAccessService kycAccess;

    public AdminUserController(JdbcTemplate jdbc, AdminUserService adminUsers, KycDocumentAccessService kycAccess) {
        this.jdbc = jdbc;
        this.adminUsers = adminUsers;
        this.kycAccess = kycAccess;
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
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + USERS_WITH_ROLE + where, Long.class, countArgs);
        Object[] dataArgs = {normalizedQuery, normalizedQuery, normalizedQuery, normalizedRole, normalizedRole, normalizedStatus, normalizedStatus, safeSize, safePage * safeSize};
        List<UserSummary> items = jdbc.query("""
                SELECT u.id,u.full_name,u.email,u.status,u.created_at,u.email_verified_at,
                       u.plan_code,u.plan_expires_at,u.listing_quota_remaining,ur.role,
                       COALESCE(k.status, 'NOT_SUBMITTED') AS kyc_status,
                       (SELECT MAX(s.created_at) FROM auth_sessions s WHERE s.user_id=u.id) AS last_login_at,
                       (SELECT COUNT(*) FROM listings l WHERE l.owner_id=u.id) AS listing_count
                """ + USERS_WITH_ROLE + """
                LEFT JOIN user_kyc_profiles k ON k.user_id=u.id
                """ + where + " ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?", (rs, row) -> new UserSummary(
                rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("email"),
                rs.getString("role"), rs.getString("status"), rs.getString("kyc_status"),
                rs.getString("plan_code"), rs.getInt("listing_quota_remaining"), rs.getLong("listing_count"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("email_verified_at")),
                instant(rs.getTimestamp("last_login_at")), instant(rs.getTimestamp("plan_expires_at"))), dataArgs);
        return new UserPage(items, safePage, safeSize, total == null ? 0 : total);
    }

    /** Lock (SUSPENDED) or unlock (ACTIVE) with a mandatory reason; recorded in the account history. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest request, Authentication authentication) {
        adminUsers.changeStatus(id, request.status(), request.reason(), CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    /** Role change with a mandatory reason: never one's own role, never the last active ADMIN, one role row afterwards. */
    @PatchMapping("/{id}/role")
    public AdminUserService.RoleChange updateRole(@PathVariable UUID id, @RequestBody UpdateRoleRequest request,
                                                  Authentication authentication) {
        return adminUsers.changeRole(id, request.role(), request.reason(), CurrentUser.id(authentication));
    }

    @GetMapping("/{id}/history")
    public List<AdminUserService.AdminAction> history(@PathVariable UUID id) {
        return adminUsers.history(id);
    }

    /** Opens the identity documents of a user: password re-confirmation + reason, logged in kyc_access_log. */
    @PostMapping("/{id}/kyc-documents")
    public ResponseEntity<KycDocumentAccessService.DocumentAccess> openKycDocuments(@PathVariable UUID id,
                                                                                   @RequestBody KycAccessRequest request,
                                                                                   Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(kycAccess.open(CurrentUser.id(authentication), id, request.password(), request.reason()));
    }

    @GetMapping("/{id}/kyc-access-log")
    public List<KycDocumentAccessService.AccessLogEntry> kycAccessLog(@PathVariable UUID id) {
        return kycAccess.log(id);
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
    public record UpdateStatusRequest(String status, String reason) {}
    public record UpdateRoleRequest(String role, String reason) {}
    public record KycAccessRequest(String password, String reason) {}
}
