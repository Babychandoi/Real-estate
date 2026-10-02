import type { APIRequestContext, Browser, BrowserContextOptions, Page } from '@playwright/test';
import { staffToken } from './adminAdversarial';
import { apiLogin, DEMO_ACCOUNTS, skipConsentBanner, useSession } from './helpers';

/**
 * Every route of the router (app/routes.tsx), opened as the role that is meant to use it: guest for public pages,
 * the seeker (demo.user) for seeker pages, the poster/broker (demo.broker) for supply pages, the moderator for the
 * moderation desk and the admin for the other staff desks. Slugs and ids come from the seeded API, never fixtures.
 */
export const ADMIN = '/2026/nhadatchuan/admin';

export type Role = 'guest' | 'buyer' | 'broker' | 'moderator' | 'admin';

export interface Session {
  tokens: Record<Exclude<Role, 'guest'>, string>;
  ids: {
    listing: string;
    rentListing: string;
    seller: string;
    project: string;
    area: string;
    article: string;
    ownListingId: string;
  };
}

export interface RouteSpec {
  name: string;
  path: (s: Session) => string;
  as: Role;
}

export const ROUTES: RouteSpec[] = [
  // public
  { name: 'home', path: () => '/', as: 'guest' },
  { name: 'search-sale', path: () => '/search?purpose=SALE', as: 'guest' },
  { name: 'search-rent', path: () => '/search?purpose=RENT', as: 'guest' },
  { name: 'listing', path: (s) => `/listings/${s.ids.listing}`, as: 'guest' },
  { name: 'listing-rent', path: (s) => `/listings/${s.ids.rentListing}`, as: 'guest' },
  { name: 'listing-signed-in', path: (s) => `/listings/${s.ids.listing}`, as: 'buyer' },
  { name: 'compare', path: () => '/compare', as: 'guest' },
  { name: 'seller', path: (s) => `/nguoi-dang/${s.ids.seller}`, as: 'guest' },
  { name: 'projects', path: () => '/du-an', as: 'guest' },
  { name: 'project', path: (s) => `/du-an/${s.ids.project}`, as: 'guest' },
  { name: 'areas', path: () => '/khu-vuc', as: 'guest' },
  { name: 'area', path: (s) => `/khu-vuc/${s.ids.area}`, as: 'guest' },
  { name: 'news', path: () => '/tin-tuc', as: 'guest' },
  { name: 'article', path: (s) => `/tin-tuc/${s.ids.article}`, as: 'guest' },
  { name: 'article-preview-invalid', path: () => '/tin-tuc/xem-truoc/khong-hop-le', as: 'guest' },
  { name: 'about', path: () => '/about', as: 'guest' },
  { name: 'terms', path: () => '/terms', as: 'guest' },
  { name: 'privacy', path: () => '/privacy', as: 'guest' },
  { name: 'contact', path: () => '/contact', as: 'guest' },
  { name: 'forgot-password', path: () => '/forgot-password', as: 'guest' },
  { name: 'reset-password', path: () => '/reset-password?token=khong-hop-le', as: 'guest' },
  { name: 'verify-email', path: () => '/verify-email?token=khong-hop-le', as: 'guest' },
  { name: 'unsubscribe', path: () => '/unsubscribe?token=khong-hop-le', as: 'guest' },
  { name: 'shortlist-invalid', path: () => '/shortlists/khong-hop-le', as: 'guest' },
  { name: 'not-found', path: () => '/khong-ton-tai-e2e', as: 'guest' },
  // seeker
  { name: 'account', path: () => '/account', as: 'buyer' },
  { name: 'kyc', path: () => '/kyc', as: 'buyer' },
  { name: 'saved', path: () => '/saved', as: 'buyer' },
  { name: 'saved-searches', path: () => '/saved?tab=searches', as: 'buyer' },
  { name: 'saved-shortlists', path: () => '/saved?tab=shortlists', as: 'buyer' },
  { name: 'notifications', path: () => '/notifications', as: 'buyer' },
  { name: 'inquiries', path: () => '/my-inquiries', as: 'buyer' },
  { name: 'become-owner', path: () => '/become-owner', as: 'buyer' },
  // poster / broker
  { name: 'listing-new', path: () => '/listings/new', as: 'broker' },
  { name: 'listing-edit', path: (s) => `/listings/new?edit=${s.ids.ownListingId}`, as: 'broker' },
  { name: 'my-listings', path: () => '/my-listings', as: 'broker' },
  { name: 'my-leads', path: () => '/my-leads', as: 'broker' },
  { name: 'billing', path: () => '/billing', as: 'broker' },
  { name: 'broker-workspace', path: () => '/broker/workspace', as: 'broker' },
  { name: 'broker-kyc', path: () => '/kyc', as: 'broker' },
  // staff
  { name: 'admin-login', path: () => `${ADMIN}/login`, as: 'guest' },
  { name: 'admin-moderation', path: () => `${ADMIN}/moderation`, as: 'moderator' },
  { name: 'admin-listings', path: () => `${ADMIN}/listings`, as: 'admin' },
  { name: 'admin-users', path: () => `${ADMIN}/users`, as: 'admin' },
  { name: 'admin-leads-and-reports', path: () => `${ADMIN}/leads-and-reports`, as: 'admin' },
  { name: 'admin-reports', path: () => `${ADMIN}/reports`, as: 'admin' },
  { name: 'admin-verification', path: () => `${ADMIN}/verification`, as: 'admin' },
  { name: 'admin-billing', path: () => `${ADMIN}/billing`, as: 'admin' },
  { name: 'admin-analytics', path: () => `${ADMIN}/analytics`, as: 'admin' },
  { name: 'admin-projects', path: () => `${ADMIN}/projects`, as: 'admin' },
  { name: 'admin-cms', path: () => `${ADMIN}/cms`, as: 'admin' },
  { name: 'admin-security', path: () => `${ADMIN}/security`, as: 'admin' },
];

export async function loadSession(request: APIRequestContext): Promise<Session> {
  const first = async (path: string, pick: (body: unknown) => string | undefined, headers = {}) => {
    const response = await request.get(path, { headers });
    if (!response.ok()) return 'khong-co';
    return pick(await response.json()) ?? 'khong-co';
  };
  type Items = { items?: Array<{ slug?: string; id?: string }> };
  const firstSlug = (body: unknown) => (body as Items).items?.[0]?.slug;
  const broker = await apiLogin(request, DEMO_ACCOUNTS.broker);
  const listings = (await (await request.get('/api/v1/listings/search?purpose=SALE&size=1')).json()) as Array<{
    slug: string;
    sellerId: string;
  }>;
  return {
    tokens: {
      buyer: await apiLogin(request, DEMO_ACCOUNTS.buyer),
      broker,
      moderator: await staffToken(request, 'demo.moderator@bds.local'),
      admin: await staffToken(request, 'demo.admin@bds.local'),
    },
    ids: {
      listing: listings[0]?.slug ?? 'khong-co',
      seller: listings[0]?.sellerId ?? 'khong-co',
      rentListing: await first(
        '/api/v1/listings/search?purpose=RENT&size=1',
        (b) => (b as Array<{ slug?: string }>)[0]?.slug,
      ),
      project: await first('/api/v2/public/projects?page=0&size=1', firstSlug),
      area: await first('/api/v2/public/areas', firstSlug),
      article: await first('/api/v2/public/articles?page=0&size=1', firstSlug),
      ownListingId: await first('/api/v2/me/listings?size=1', (b) => (b as Items).items?.[0]?.id, {
        Authorization: `Bearer ${broker}`,
      }),
    },
  };
}

/** Waits for the page's own readiness signal; static states without one get a short grace period. */
export async function settle(page: Page) {
  await page
    .locator('[data-ready="true"]')
    .first()
    .waitFor({ state: 'attached', timeout: 15_000 })
    .catch(() => undefined);
  await page.evaluate(async () => {
    await document.fonts.ready;
  });
  await page.waitForTimeout(300);
}

export async function openRoute(
  browser: Browser,
  session: Session,
  spec: RouteSpec,
  context: BrowserContextOptions,
  before?: (page: Page) => Promise<void>,
) {
  const ctx = await browser.newContext({ locale: 'vi-VN', timezoneId: 'Asia/Ho_Chi_Minh', ...context });
  const page = await ctx.newPage();
  await skipConsentBanner(page);
  if (spec.as !== 'guest') await useSession(page, session.tokens[spec.as]);
  if (before) await before(page);
  await page.goto(spec.path(session));
  await settle(page);
  return { page, close: () => ctx.close() };
}
