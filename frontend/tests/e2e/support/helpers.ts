import AxeBuilder from '@axe-core/playwright';
import { expect, type APIRequestContext, type Page } from '@playwright/test';

/** WCAG 2.2 AA rule set used by every accessibility check. */
export const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

/** Browser clock for screenshots: a little after the fixed seed clock (2026-09-01T03:00:00Z). */
export const FIXED_NOW = new Date('2026-09-01T05:00:00Z');

/**
 * Opens a page and waits for its own readiness signal instead of sleeping: pages set `data-ready="true"` once their
 * data has loaded (or failed with a visible message). Fonts and eager images must be settled too.
 */
export async function gotoReady(page: Page, path: string): Promise<void> {
  await page.goto(path);
  await waitUntilReady(page);
}

export async function waitUntilReady(page: Page): Promise<void> {
  await expect(page.locator('[data-ready="true"]').first()).toBeAttached({ timeout: 20_000 });
  await page.evaluate(async () => {
    await document.fonts.ready;
  });
  await page.waitForFunction(() =>
    Array.from(document.images)
      .filter((image) => image.loading !== 'lazy')
      .every((image) => image.complete),
  );
}

export interface ReadableViolation {
  rule: string;
  impact: string | null | undefined;
  help: string;
  targets: string[];
}

/** Runs axe with the WCAG 2.2 AA tags and returns violations in a form that reads well in a test report. */
export async function axeViolations(page: Page, include?: string): Promise<ReadableViolation[]> {
  let builder = new AxeBuilder({ page }).withTags(WCAG_TAGS).exclude('.maplibregl-ctrl-attrib');
  if (include) builder = builder.include(include);
  const results = await builder.analyze();
  return results.violations.map((violation) => ({
    rule: violation.id,
    impact: violation.impact,
    help: violation.help,
    targets: violation.nodes.slice(0, 5).map((node) => node.target.join(' ')),
  }));
}

/** Elements that make the page scroll sideways (empty when the layout fits the viewport). */
export async function horizontalOverflow(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const root = document.documentElement;
    const width = root.clientWidth;
    if (root.scrollWidth <= width + 1) return [];
    const offenders = Array.from(document.body.querySelectorAll<HTMLElement>('*')).filter((element) => {
      const rect = element.getBoundingClientRect();
      return rect.width > 0 && rect.right > width + 1;
    });
    return [`scrollWidth ${root.scrollWidth} > ${width}`].concat(
      offenders
        .slice(0, 8)
        .map(
          (element) =>
            `${element.tagName.toLowerCase()}.${String(element.className).split(/\s+/).slice(0, 4).join('.')}`,
        ),
    );
  });
}

/** Regions whose pixels legitimately change between runs; masked in screenshots. */
export function volatileRegions(page: Page) {
  return [page.locator('.maplibregl-canvas'), page.locator('[data-volatile]')];
}

export interface PublicListing {
  id: string;
  slug: string;
  title: string;
}

/** First public SALE listing from the seeded data (for pages that need a real slug). */
export async function firstPublicListing(request: APIRequestContext): Promise<PublicListing> {
  const response = await request.get('/api/v1/listings/search?purpose=SALE&sortBy=LATEST&size=1');
  expect(response.ok(), `search API answered ${response.status()}`).toBe(true);
  const [listing] = (await response.json()) as PublicListing[];
  expect(listing, 'the E2E data set needs at least one public SALE listing (run the UAT seeder)').toBeTruthy();
  return listing;
}

export const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

export const UUID_PATTERN = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;

/** Demo accounts created by DemoAccountInitializer (APP_MODE=demo). */
export const DEMO_ACCOUNTS = {
  buyer: 'demo.user@bds.local',
  broker: 'demo.broker@bds.local',
} as const;

export function demoPassword(): string {
  const password = process.env.DEMO_ACCOUNT_PASSWORD;
  if (!password) {
    throw new Error('Set DEMO_ACCOUNT_PASSWORD for the authenticated E2E flows (demo value: .env.demo.example).');
  }
  return password;
}

/** Logs in through the API (not the UI) and returns the bearer token. */
export async function apiLogin(request: APIRequestContext, email: string): Promise<string> {
  const response = await request.post('/api/v1/auth/login', { data: { email, password: demoPassword() } });
  expect(response.ok(), `login for ${email} answered ${response.status()}: ${await response.text()}`).toBe(true);
  const { accessToken } = (await response.json()) as { accessToken: string };
  return accessToken;
}

/** Makes every page of this test start signed in, like a returning visitor (sessionStorage token). */
export async function useSession(page: Page, token: string): Promise<void> {
  await page.addInitScript((value) => window.sessionStorage.setItem('bds_access_token', value), token);
}

/**
 * A stable, distinct slot per Playwright project (0 for chromium-1440, 1 for chromium-320, …). Journeys that change
 * shared seed data pick "their" row by this slot, so projects running in parallel against one stack never race for
 * the same submission, order or account.
 */
export function projectSlot(projectName: string): number {
  const known = ['chromium-1440', 'chromium-320', 'chromium-768', 'chromium-360', 'chromium-1024'];
  const index = known.indexOf(projectName);
  if (index >= 0) return index;
  let hash = 0;
  for (const char of projectName) hash = (hash * 31 + char.charCodeAt(0)) % 97;
  return known.length + hash;
}
