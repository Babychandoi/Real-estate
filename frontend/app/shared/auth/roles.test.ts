import { describe, expect, it } from 'vitest';
import { canOpen, ROUTE_ACCESS, type ProtectedPage } from './routeAccess';
import { hasRole, isRole, POSTERS, primaryRole, ROLE_LABELS, ROLES, STAFF, type Role } from './roles';

const pages = Object.keys(ROUTE_ACCESS) as ProtectedPage[];
const allowedPages = (role: Role) => pages.filter((page) => canOpen(role, page));

describe('route access by role (contract §2.5)', () => {
  it('lets an OWNER post and manage listings, leads and billing, but not the broker workspace', () => {
    expect(allowedPages(ROLES.OWNER)).toEqual([
      'createListing',
      'myListings',
      'myLeads',
      'billing',
      'kyc',
      'account',
      'saved',
      'notifications',
    ]);
    expect(canOpen(ROLES.OWNER, 'brokerWorkspace')).toBe(false);
  });

  it('keeps /broker/workspace for brokers and admins', () => {
    expect(ROUTE_ACCESS.brokerWorkspace).toEqual([ROLES.ADMIN, ROLES.BROKER]);
    expect(canOpen(ROLES.BROKER, 'brokerWorkspace')).toBe(true);
    expect(canOpen(ROLES.ADMIN, 'brokerWorkspace')).toBe(true);
  });

  it('gives a home seeker their inquiries and account pages only', () => {
    expect(allowedPages(ROLES.USER)).toEqual(['myInquiries', 'kyc', 'account', 'saved', 'notifications']);
  });

  it('limits moderators to the staff desks', () => {
    expect(allowedPages(ROLES.MODERATOR)).toEqual([
      'kyc',
      'account',
      'saved',
      'notifications',
      'adminModeration',
      'adminLeadsAndReports',
      'adminReports',
      'adminVerification',
      'adminAnalytics',
      'adminProjects',
      'adminCms',
      'adminSecurity',
    ]);
  });

  it('gives admins every page except the seeker inbox', () => {
    expect(allowedPages(ROLES.ADMIN)).toEqual(pages.filter((page) => page !== 'myInquiries'));
  });

  it('refuses unknown or missing roles', () => {
    expect(canOpen(undefined, 'kyc')).toBe(false);
    expect(canOpen(null, 'account')).toBe(false);
  });
});

describe('role helpers', () => {
  it('resolves one role by priority ADMIN > MODERATOR > BROKER > OWNER > USER', () => {
    expect(primaryRole([ROLES.USER, ROLES.OWNER])).toBe(ROLES.OWNER);
    expect(primaryRole([ROLES.OWNER, ROLES.BROKER])).toBe(ROLES.BROKER);
    expect(primaryRole([ROLES.USER, ROLES.ADMIN, ROLES.MODERATOR])).toBe(ROLES.ADMIN);
    expect(primaryRole([])).toBeNull();
  });

  it('exposes the capability groups', () => {
    expect(POSTERS).toEqual(['ADMIN', 'BROKER', 'OWNER']);
    expect(STAFF).toEqual(['ADMIN', 'MODERATOR']);
    expect(hasRole('OWNER', POSTERS)).toBe(true);
    expect(hasRole('USER', POSTERS)).toBe(false);
  });

  it('labels the owner persona in Vietnamese and validates role strings', () => {
    expect(ROLE_LABELS.OWNER).toBe('Chủ nhà');
    expect(isRole('OWNER')).toBe(true);
    expect(isRole('SUPERUSER')).toBe(false);
  });
});
