import { mkdirSync, writeFileSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
import { axeViolations } from './support/helpers';
import { loadSession, openRoute, ROUTES, type Session } from './support/routeCatalog';
import { auditReflow, auditUi } from './support/targetAudit';
import { ALLOW } from './support/uxAllow';

// W6-UX: one pass over EVERY route of the router (support/routeCatalog.ts), each opened as the role that uses it.
//   DS-03  text sizes (nothing under 12 px; price/status/error/action ≥ 14 px), target sizes (44 × 44 on a touch
//          phone, 24 × 24 or the WCAG 2.5.8 spacing exception on desktop), Lucide-only icons, no emoji;
//   DS-04  axe with the WCAG 2.0/2.1/2.2 A+AA tags, phone and desktop;
//   DS-06  reflow at 390 px, at 200 % browser zoom (a 1280 px window at 200 % = 640 CSS px with deviceScaleFactor 2)
//          and at 200 % text size (root font-size doubled): no page-level sideways scroll (tables and tab lists may
//          scroll inside their frame), no clipped text, sticky bars never cover more than a third of the screen.
// Runs once, in the chromium-1440 project: it sets its own viewports. UX_AUDIT_DUMP=<dir> writes every finding as JSON.

const DUMP = process.env.UX_AUDIT_DUMP;

const REFLOW_ALLOW: Array<{ selector: string; reason: string }> = [];

test.describe.configure({ mode: 'parallel' });
test.use({ actionTimeout: 15_000 });

let session: Session;

test.beforeAll(async ({ playwright }, workerInfo) => {
  if (workerInfo.project.name !== 'chromium-1440') return;
  test.setTimeout(240_000);
  const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL, timeout: 90_000 });
  session = await loadSession(request);
  await request.dispose();
});

test.beforeEach(({ browserName }, testInfo) => {
  void browserName;
  test.skip(testInfo.project.name !== 'chromium-1440', 'sets its own viewports: runs once, in chromium-1440');
});

const PHONE = { viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true, deviceScaleFactor: 3 };
const DESKTOP = { viewport: { width: 1440, height: 900 } };
const ZOOM_200 = { viewport: { width: 640, height: 400 }, deviceScaleFactor: 2 };
const TEXT_200 = { viewport: { width: 1280, height: 900 } };

async function doubleRootFontSize(page: Page) {
  await page.addInitScript(() => {
    const apply = () => document.documentElement.style.setProperty('font-size', '200%');
    if (document.documentElement) apply();
    document.addEventListener('DOMContentLoaded', apply);
  });
}

for (const spec of ROUTES) {
  test(`${spec.name} (${spec.as}): DS-03 sizes, DS-04 axe, DS-06 reflow`, async ({ browser }) => {
    test.setTimeout(180_000);
    const report: Record<string, unknown> = {};

    {
      const { page, close } = await openRoute(browser, session, spec, PHONE);
      const ui = await auditUi(page, { touch: true, allow: ALLOW });
      const axe = await axeViolations(page);
      const reflow = await auditReflow(page, REFLOW_ALLOW);
      Object.assign(report, { phoneUi: ui, phoneAxe: axe, phoneReflow: reflow });
      expect.soft(ui, 'DS-03 at 390 px (touch)').toEqual([]);
      expect.soft(axe, 'axe at 390 px').toEqual([]);
      expect.soft(reflow, 'reflow at 390 px').toEqual([]);
      await close();
    }
    {
      const { page, close } = await openRoute(browser, session, spec, DESKTOP);
      const ui = await auditUi(page, { touch: false, allow: ALLOW });
      const axe = await axeViolations(page);
      Object.assign(report, { desktopUi: ui, desktopAxe: axe });
      expect.soft(ui, 'DS-03 at 1440 px (desktop)').toEqual([]);
      expect.soft(axe, 'axe at 1440 px').toEqual([]);
      await close();
    }
    {
      const { page, close } = await openRoute(browser, session, spec, ZOOM_200);
      const reflow = await auditReflow(page, REFLOW_ALLOW);
      report.zoomReflow = reflow;
      expect.soft(reflow, 'reflow at 200 % zoom (640 CSS px, DPR 2)').toEqual([]);
      await close();
    }
    {
      const { page, close } = await openRoute(browser, session, spec, TEXT_200, doubleRootFontSize);
      expect(await page.evaluate(() => getComputedStyle(document.documentElement).fontSize)).toBe('32px');
      const reflow = await auditReflow(page, REFLOW_ALLOW);
      report.textReflow = reflow;
      expect.soft(reflow, 'reflow at 200 % text size').toEqual([]);
      await close();
    }
    if (DUMP) {
      mkdirSync(DUMP, { recursive: true });
      writeFileSync(`${DUMP}/${spec.name}.json`, JSON.stringify(report, null, 2));
    }
  });
}

// User content is not under our control: a listing whose title, address, description and seller name are single
// 240-character words (and a huge rent) must still reflow. The seeded data has no such listing, so the detail API
// answer is rewritten for this test only; the page code under test is the real one.
const LONG = 'Khônggiannhàởcaocấpkhuvựctrungtâmthànhphố'.repeat(6);

async function longListing(page: Page) {
  await page.route(/\/api\/v2\/listings\/[^/?]+(\?.*)?$/, async (route) => {
    const response = await route.fetch();
    if (!response.ok()) return route.fulfill({ response });
    const body = await response.json();
    body.title = LONG;
    body.description = `${LONG}\n${LONG}`;
    body.location = { ...body.location, addressSummary: LONG, districtName: LONG };
    body.seller = { ...body.seller, name: LONG };
    body.price = { amount: 98_765_430_000_000, currency: 'VND', period: 'MONTH' };
    await route.fulfill({ response, json: body });
  });
}

async function longListingWithLargeText(page: Page) {
  await longListing(page);
  await doubleRootFontSize(page);
}

test('listing detail with 240-character unbroken user text reflows at 390 px, 200 % zoom and 200 % text', async ({
  browser,
}) => {
  test.setTimeout(180_000);
  const spec = { name: 'listing-long-text', as: 'guest' as const, path: (s: Session) => `/listings/${s.ids.listing}` };
  const cases = [
    ['390 px', PHONE, longListing],
    ['200 % zoom', ZOOM_200, longListing],
    ['200 % text', TEXT_200, longListingWithLargeText],
  ] as const;
  for (const [label, context, before] of cases) {
    const { page, close } = await openRoute(browser, session, spec, context, before);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(LONG.slice(0, 40));
    expect.soft(await auditReflow(page, REFLOW_ALLOW), `reflow at ${label}`).toEqual([]);
    await close();
  }
});
