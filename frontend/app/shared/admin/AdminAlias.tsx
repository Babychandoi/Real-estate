import { Navigate, useLocation, useParams } from 'react-router-dom';

export const ADMIN_PREFIX = '/2026/nhadatchuan/admin';

/** Pages under the admin prefix; an alias to any other path lands on the prefix (→ moderation), never a dead end. */
export const ADMIN_PAGES = [
  'login',
  'moderation',
  'listings',
  'users',
  'leads-and-reports',
  'reports',
  'verification',
  'billing',
  'analytics',
  'projects',
  'cms',
  'security',
] as const;

/** Where a legacy admin URL (`/admin/<page>`, `/2026/nhadatchua/admin/<page>`) goes; query and hash are kept. */
export function adminAliasTarget(rest: string | undefined, search = '', hash = ''): string {
  const page = (rest ?? '').split('/')[0];
  const known = (ADMIN_PAGES as readonly string[]).includes(page);
  return `${ADMIN_PREFIX}${known ? `/${page}` : ''}${search}${hash}`;
}

/**
 * UI-26: legacy admin aliases keep working without becoming a second way in. They only rewrite the URL; the real
 * route's ProtectedRoute still decides (guest → admin login, non-staff → no admin content) and the API checks roles.
 */
export function AdminAlias() {
  const { '*': rest } = useParams();
  const { search, hash } = useLocation();
  return <Navigate to={adminAliasTarget(rest, search, hash)} replace />;
}
