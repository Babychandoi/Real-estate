package com.company.bds.shared.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Account roles (contract §2.5). A user has one {@code user_roles} row; if several exist, the effective role is the first
 * in {@link #PRIORITY}. Frontend mirror: {@code app/shared/auth/roles.ts}.
 */
public final class Roles {
    public static final String USER = "USER";
    /** Private owner (chủ nhà) who posts their own property; not a broker. */
    public static final String OWNER = "OWNER";
    public static final String BROKER = "BROKER";
    public static final String MODERATOR = "MODERATOR";
    public static final String ADMIN = "ADMIN";

    /** Highest first. */
    public static final List<String> PRIORITY = List.of(ADMIN, MODERATOR, BROKER, OWNER, USER);
    public static final Set<String> ALL = Set.copyOf(PRIORITY);
    /** May create/edit listings, see my-listings and their leads, and buy listing packages. */
    public static final List<String> POSTERS = List.of(ADMIN, BROKER, OWNER);
    /** Back-office staff. */
    public static final List<String> STAFF = List.of(ADMIN, MODERATOR);
    /** Account types a visitor may choose when registering. */
    public static final Set<String> SELF_REGISTRATION = Set.of(USER, OWNER, BROKER);

    /** SQL ordering of {@code ur.role} by {@link #PRIORITY}. */
    private static final String PRIORITY_ORDER =
            "CASE ur.role WHEN 'ADMIN' THEN 1 WHEN 'MODERATOR' THEN 2 WHEN 'BROKER' THEN 3 WHEN 'OWNER' THEN 4 ELSE 5 END";

    private Roles() {}

    /** SQL expression for the effective role of the user whose id is {@code userIdColumn} (USER when no row exists). */
    public static String effectiveRoleSql(String userIdColumn) {
        if (!userIdColumn.matches("[a-z_]+\\.[a-z_]+|[a-z_]+")) throw new IllegalArgumentException("Invalid column reference");
        return "COALESCE((SELECT ur.role FROM user_roles ur WHERE ur.user_id=" + userIdColumn
                + " ORDER BY " + PRIORITY_ORDER + " LIMIT 1), 'USER')";
    }

    /** Effective role among several role values. */
    public static String highest(Collection<String> roles) {
        return PRIORITY.stream().filter(roles::contains).findFirst().orElse(USER);
    }

    /** Vietnamese label shown next to the account (UI copy, P-09). */
    public static String label(String role) {
        if (role == null) return "Người dùng";
        return switch (role) {
            case OWNER -> "Chủ nhà";
            case BROKER -> "Môi giới";
            case MODERATOR -> "Kiểm duyệt viên";
            case ADMIN -> "Quản trị viên";
            default -> "Người dùng";
        };
    }

    public static boolean isStaff(String role) { return STAFF.contains(role); }

    public static boolean isPoster(String role) { return POSTERS.contains(role); }

    /** Union of role groups as the varargs array Spring Security's {@code hasAnyRole} expects. */
    @SafeVarargs
    public static String[] anyOf(List<String>... groups) {
        Set<String> union = new LinkedHashSet<>();
        for (List<String> group : groups) union.addAll(group);
        return union.toArray(String[]::new);
    }
}
