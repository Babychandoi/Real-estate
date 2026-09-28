import { ADMIN_ONLY, ALL_ROLES, BROKER_WORKSPACE, POSTERS, SEEKERS, STAFF, type Role } from './roles';

/**
 * Who may open each protected page. routes.tsx guards with these lists and the navigation (root.tsx,
 * AdminShell.tsx) shows a link only when the same list allows it, so a menu never offers a page that the guard
 * then refuses. The backend still authorises every API call on its own.
 */
export const ROUTE_ACCESS = {
  createListing: POSTERS,
  myListings: POSTERS,
  myLeads: POSTERS,
  billing: POSTERS,
  brokerWorkspace: BROKER_WORKSPACE,
  myInquiries: SEEKERS,
  kyc: ALL_ROLES,
  account: ALL_ROLES,
  adminModeration: STAFF,
  adminListings: ADMIN_ONLY,
  adminUsers: ADMIN_ONLY,
  adminLeadsAndReports: STAFF,
  adminVerification: STAFF,
  adminBilling: ADMIN_ONLY,
  adminAnalytics: STAFF,
  adminProjects: STAFF,
  adminCms: STAFF,
} as const satisfies Record<string, readonly Role[]>;

export type ProtectedPage = keyof typeof ROUTE_ACCESS;

export function canOpen(role: Role | null | undefined, page: ProtectedPage): boolean {
  return role != null && ROUTE_ACCESS[page].includes(role);
}
