/**
 * Account roles and capabilities (contract §2.5). Mirrors backend `shared/security/Roles.java`: route guards and
 * navigation read these constants instead of listing role strings inline.
 */
export const ROLES = {
  USER: 'USER',
  OWNER: 'OWNER',
  BROKER: 'BROKER',
  MODERATOR: 'MODERATOR',
  ADMIN: 'ADMIN',
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];

/** Every role, highest priority first (ADMIN > MODERATOR > BROKER > OWNER > USER). */
export const ROLE_PRIORITY: readonly Role[] = [ROLES.ADMIN, ROLES.MODERATOR, ROLES.BROKER, ROLES.OWNER, ROLES.USER];
export const ALL_ROLES: readonly Role[] = ROLE_PRIORITY;

/** Create/edit listings, my-listings, my-leads, billing. */
export const POSTERS: readonly Role[] = [ROLES.ADMIN, ROLES.BROKER, ROLES.OWNER];
/** Moderation and back-office desks. */
export const STAFF: readonly Role[] = [ROLES.ADMIN, ROLES.MODERATOR];
/** `/broker/workspace` stays with brokers; owners get the simpler my-listings/my-leads views. */
export const BROKER_WORKSPACE: readonly Role[] = [ROLES.ADMIN, ROLES.BROKER];
/** People looking for a home: their sent requests live in `/my-inquiries`. */
export const SEEKERS: readonly Role[] = [ROLES.USER];
export const ADMIN_ONLY: readonly Role[] = [ROLES.ADMIN];

/** Account types a person can pick when registering (staff roles are granted, never self-selected). */
export const SELF_SERVICE_ROLES = [ROLES.USER, ROLES.OWNER, ROLES.BROKER] as const;
export type SelfServiceRole = (typeof SELF_SERVICE_ROLES)[number];

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Quản trị viên',
  MODERATOR: 'Chuyên viên kiểm duyệt',
  BROKER: 'Môi giới BĐS',
  OWNER: 'Chủ nhà',
  USER: 'Người tìm nhà',
};

export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ALL_ROLES as readonly string[]).includes(value);
}

export function hasRole(role: Role | null | undefined, allowed: readonly Role[]): boolean {
  return role != null && allowed.includes(role);
}

export const isPoster = (role: Role | null | undefined) => hasRole(role, POSTERS);
export const isStaff = (role: Role | null | undefined) => hasRole(role, STAFF);
export const canUseBrokerWorkspace = (role: Role | null | undefined) => hasRole(role, BROKER_WORKSPACE);

/** The single role to act as when an account carries several (contract priority). */
export function primaryRole(roles: readonly Role[]): Role | null {
  return ROLE_PRIORITY.find((role) => roles.includes(role)) ?? null;
}
