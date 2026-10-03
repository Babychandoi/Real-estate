import { mkdirSync, writeFileSync } from 'node:fs';
import { test } from '@playwright/test';
import { loadSession, openRoute, ADMIN, type Role, type Session } from './support/routeCatalog';

// Review tool (PR #25): screenshots and layout numbers of the pages the W6-UX size changes touch, so two builds can be
// compared on the same seeded data. REVIEW_SHOTS=<dir> REVIEW_BUILD=<main|branch>; skipped without REVIEW_SHOTS.

const DIR = process.env.REVIEW_SHOTS;
const BUILD = process.env.REVIEW_BUILD ?? 'build';

test.describe.configure({ mode: 'parallel' });

let session: Session;
test.beforeAll(async ({ playwright }, workerInfo) => {
  if (!DIR || workerInfo.project.name !== 'chromium-1440') return;
  test.setTimeout(240_000);
  const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL, timeout: 90_000 });
  session = await loadSession(request);
  await request.dispose();
});
test.beforeEach(({ browserName }, testInfo) => {
  void browserName;
  test.skip(!DIR || testInfo.project.name !== 'chromium-1440', 'review tool: REVIEW_SHOTS=<dir>, chromium-1440 only');
});

const PAGES: Array<{ name: string; as: Role; path: (s: Session) => string }> = [
  { name: 'home', as: 'guest', path: () => '/' },
  { name: 'home-admin', as: 'admin', path: () => '/' },
  { name: 'home-broker', as: 'broker', path: () => '/' },
  { name: 'search', as: 'guest', path: () => '/search?purpose=SALE' },
  { name: 'listing', as: 'buyer', path: (s) => `/listings/${s.ids.listing}` },
  { name: 'compare', as: 'guest', path: () => '/compare' },
  { name: 'my-listings', as: 'broker', path: () => '/my-listings' },
  { name: 'saved', as: 'buyer', path: () => '/saved' },
  { name: 'admin-listings', as: 'admin', path: () => `${ADMIN}/listings` },
  { name: 'admin-users', as: 'admin', path: () => `${ADMIN}/users` },
  { name: 'admin-moderation', as: 'moderator', path: () => `${ADMIN}/moderation` },
  { name: 'admin-billing', as: 'admin', path: () => `${ADMIN}/billing` },
  { name: 'admin-analytics', as: 'admin', path: () => `${ADMIN}/analytics` },
];
const WIDTHS = [390, 768, 1024, 1280, 1440];

for (const spec of PAGES) {
  test(`screens: ${spec.name}`, async ({ browser }) => {
    test.setTimeout(240_000);
    const metrics: Record<string, unknown> = {};
    for (const width of WIDTHS) {
      const touch = width === 390 ? { hasTouch: true, isMobile: true, deviceScaleFactor: 2 } : {};
      const { page, close } = await openRoute(browser, session, spec, {
        viewport: { width, height: width === 390 ? 844 : 900 },
        ...touch,
      });
      await page.waitForTimeout(800);
      metrics[width] = await page.evaluate(() => {
        const header = document.querySelector('header');
        const rows = Array.from(document.querySelectorAll('table tbody tr')).map((r) =>
          Math.round(r.getBoundingClientRect().height),
        );
        const firstTable = document.querySelector('table');
        return {
          scrollWidth: document.documentElement.scrollWidth,
          clientWidth: document.documentElement.clientWidth,
          headerHeight: header ? Math.round(header.getBoundingClientRect().height) : null,
          headerPosition: header ? getComputedStyle(header).position : null,
          docHeight: document.documentElement.scrollHeight,
          tableRows: rows.length,
          tableRowHeightAvg: rows.length ? Math.round(rows.reduce((a, b) => a + b, 0) / rows.length) : null,
          tableWidth: firstTable ? Math.round(firstTable.getBoundingClientRect().width) : null,
          tableFrameWidth: firstTable?.parentElement ? Math.round(firstTable.parentElement.clientWidth) : null,
        };
      });
      mkdirSync(DIR!, { recursive: true });
      await page.screenshot({ path: `${DIR}/${spec.name}-${width}-${BUILD}.png`, fullPage: width !== 1440 });
      if (width === 1440)
        await page.screenshot({ path: `${DIR}/${spec.name}-${width}-${BUILD}-full.png`, fullPage: true });
      await close();
    }
    writeFileSync(`${DIR}/${spec.name}-${BUILD}.json`, JSON.stringify(metrics, null, 2));
  });
}
