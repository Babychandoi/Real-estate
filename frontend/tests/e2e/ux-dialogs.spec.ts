import { mkdirSync } from 'node:fs';
import { expect, test, type Locator, type Page } from '@playwright/test';
import { axeViolations } from './support/helpers';
import { DIALOGS } from './support/dialogCatalog';
import { ADMIN, loadSession, openRoute, type Role, type Session } from './support/routeCatalog';
import { auditUi } from './support/targetAudit';
import { ALLOW } from './support/uxAllow';

// W6-UX DS-04 / DS-15 (a11y part) / DS-03 for every overlay the product has: each dialog, sheet, menu, the filter
// sheet, the map point sheet and the gallery lightbox is opened on a 390 px touch phone and a 1440 px desktop and
//   - axe (WCAG 2.0/2.1/2.2 A+AA) passes with the overlay open;
//   - text and target sizes inside the overlay pass the DS-03 audit (support/targetAudit.ts);
//   - Tab never leaves a modal (no focus leak) and every focused element shows a visible focus indicator;
//   - Escape closes it and focus returns to the control that opened it.
// Runs once, in chromium-1440 (own viewports). UX_DIALOG_SHOTS=<dir> saves a screenshot of every open state.

const SHOTS = process.env.UX_DIALOG_SHOTS;

interface Opened {
  opener: Locator | null;
  /** The overlay; modal overlays trap focus. */
  panel: Locator;
  modal: boolean;
}

interface Scenario {
  name: string;
  as: Role;
  path: (s: Session) => string;
  open: (page: Page) => Promise<Opened | null>;
  /** Only where the opener exists (e.g. the hamburger menu is phone/tablet only). */
  widths?: readonly number[];
  /** Data-dependent: skipped (with a note) when the seed has no row to open. */
  optional?: boolean;
  before?: (page: Page) => Promise<void>;
}

const topDialog = (page: Page) => page.locator('[role="dialog"][aria-modal="true"], dialog[open]').last();

async function dialogNamed(page: Page, name: string | RegExp, opener: Locator): Promise<Opened> {
  const panel = page.getByRole('dialog', { name });
  await expect(panel).toBeVisible();
  return { opener, panel, modal: true };
}

/** Listing detail with 20 photos: the UAT seed runs without media storage, so the API answer is given photos. */
async function withPhotos(page: Page) {
  await page.route(/\/api\/v2\/listings\/[^/?]+(\?.*)?$/, async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    const svg = (n: number) =>
      `data:image/svg+xml,${encodeURIComponent(
        `<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="1000"><rect width="100%" height="100%" fill="#${(0x335577 + n * 0x0a0a0a).toString(16).slice(-6)}"/></svg>`,
      )}`;
    body.images = Array.from({ length: 20 }, (_, n) => ({ url: svg(n), srcset: [] }));
    await route.fulfill({ response, json: body });
  });
}

const EXTRA: Scenario[] = [
  {
    name: 'public: mobile navigation menu',
    as: 'guest',
    path: () => '/',
    widths: [390],
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Mở menu' });
      await opener.click();
      return { opener, panel: topDialog(page), modal: true };
    },
  },
  {
    name: 'public: sign-in dialog',
    as: 'guest',
    path: () => '/',
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Đăng nhập' }).first();
      await opener.click();
      return dialogNamed(page, 'Đăng nhập', opener);
    },
  },
  {
    name: 'public: privacy preferences',
    as: 'guest',
    path: () => '/about',
    open: async (page) => {
      const opener = page.getByRole('contentinfo').getByRole('button', { name: 'Tùy chọn quyền riêng tư' });
      await opener.click();
      return dialogNamed(page, 'Tùy chọn quyền riêng tư', opener);
    },
  },
  {
    name: 'search: filter sheet',
    as: 'guest',
    path: () => '/search?purpose=SALE',
    open: async (page) => {
      const opener = page.getByRole('button', { name: /^Bộ lọc/ });
      await opener.click();
      return dialogNamed(page, 'Bộ lọc', opener);
    },
  },
  {
    name: 'search: rent filter sheet with an invalid range',
    as: 'guest',
    path: () => '/search?purpose=RENT',
    open: async (page) => {
      const opener = page.getByRole('button', { name: /^Bộ lọc/ });
      await opener.click();
      const opened = await dialogNamed(page, 'Bộ lọc', opener);
      await opened.panel.getByLabel(/^Từ \(/).fill('50');
      await opened.panel.getByLabel(/^Đến \(/).fill('5');
      await opened.panel.getByLabel(/^Đến \(/).blur();
      return opened;
    },
  },
  {
    name: 'search: save-search dialog',
    as: 'buyer',
    path: () => '/search?purpose=SALE',
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Lưu tìm kiếm' });
      await opener.click();
      return dialogNamed(page, 'Lưu tìm kiếm và nhận cảnh báo', opener);
    },
  },
  {
    name: 'search: map point sheet',
    as: 'guest',
    path: () => '/search?purpose=SALE&view=map',
    widths: [390],
    optional: true,
    open: async (page) => {
      const region = page.getByRole('region', { name: 'Bản đồ kết quả tìm kiếm' });
      await expect(region).toBeVisible();
      const marker = region.getByRole('button', { name: /giá/ }).first();
      const ready = await marker.waitFor({ timeout: 20_000 }).then(
        () => true,
        () => false,
      );
      if (!ready) return null;
      await marker.click();
      return dialogNamed(page, 'Tin trên bản đồ', marker);
    },
  },
  {
    name: 'listing: gallery lightbox with 20 photos',
    as: 'guest',
    path: (s) => `/listings/${s.ids.listing}`,
    before: withPhotos,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Mở ảnh 1/20 cỡ lớn' });
      await opener.click();
      const panel = topDialog(page);
      await expect(panel).toContainText('Ảnh 1/20');
      await page.keyboard.press('ArrowRight');
      await expect(panel).toContainText('Ảnh 2/20');
      return { opener, panel, modal: true };
    },
  },
  {
    name: 'listing: report dialog',
    as: 'guest',
    path: (s) => `/listings/${s.ids.listing}`,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Báo cáo tin vi phạm' }).first();
      await opener.click();
      return dialogNamed(page, 'Báo cáo tin vi phạm', opener);
    },
  },
  {
    name: 'listing: contact (lead) form of a verified seeker',
    as: 'buyer',
    path: (s) => `/listings/${s.ids.listing}`,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Hẹn xem & nhận tư vấn' }).first();
      await opener.click();
      return dialogNamed(page, 'Hẹn xem bất động sản', opener);
    },
  },
  {
    name: 'compare: listing picker',
    as: 'guest',
    path: () => '/compare',
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Chọn tin ngay tại đây' });
      await opener.click();
      return { opener, panel: topDialog(page), modal: true };
    },
  },
  {
    name: 'account: account menu',
    as: 'buyer',
    path: () => '/account',
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Mở menu tài khoản' });
      await opener.click();
      await expect(opener).toHaveAttribute('aria-expanded', 'true');
      const id = await opener.evaluate((el) => {
        const panel = el.parentElement?.querySelector(':scope > div');
        if (panel && !panel.id) panel.id = 'ux-account-menu';
        return panel?.id ?? '';
      });
      return { opener, panel: page.locator(`#${id}`), modal: false };
    },
  },
  {
    name: 'account: withdraw a request',
    as: 'buyer',
    path: () => '/my-inquiries',
    optional: true,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Rút yêu cầu' }).first();
      if ((await opener.count()) === 0) return null;
      await opener.click();
      return dialogNamed(page, 'Rút yêu cầu liên hệ?', opener);
    },
  },
  {
    name: 'my listings: CSV import',
    as: 'broker',
    path: () => '/my-listings',
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Nhập từ CSV' });
      await opener.click();
      return dialogNamed(page, 'Nhập tin từ tệp CSV', opener);
    },
  },
  {
    name: 'admin: mobile navigation menu',
    as: 'admin',
    path: () => `${ADMIN}/listings`,
    widths: [390],
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Mở menu quản trị' });
      await opener.click();
      return { opener, panel: topDialog(page), modal: true };
    },
  },
];

const SCENARIOS: Scenario[] = [
  ...EXTRA,
  ...DIALOGS.map((d) => ({
    name: d.name,
    as: d.as,
    path: () => d.path,
    optional: d.optional,
    open: async (page: Page): Promise<Opened | null> => {
      const opener = await d.open(page);
      if (!opener) return null;
      return { opener, panel: topDialog(page), modal: true };
    },
  })),
];

const PHONE = { viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true, deviceScaleFactor: 2 };
const DESKTOP = { viewport: { width: 1440, height: 900 } };

let session: Session;

test.describe.configure({ mode: 'parallel' });
test.use({ actionTimeout: 20_000 });

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

/** The focused element draws a focus indicator (outline or ring) different from its unfocused look. */
async function focusIsVisible(page: Page): Promise<string | null> {
  // Let a focus transition (`transition-all` animates the outline) finish before reading the style.
  await page.waitForTimeout(200);
  return page.evaluate(() => {
    const el = document.activeElement as HTMLElement | null;
    if (!el || el === document.body) return 'nothing focused';
    const cs = getComputedStyle(el);
    const outline = cs.outlineStyle !== 'none' && parseFloat(cs.outlineWidth) >= 1;
    const ring = cs.boxShadow !== 'none';
    const after = getComputedStyle(el, '::after');
    const stretched = after.content !== 'none' && after.boxShadow !== 'none';
    if (outline || ring || stretched || el.matches(':focus-visible') === false) return null;
    const name = (el.getAttribute('aria-label') || el.textContent || el.tagName).trim().slice(0, 40);
    return `no visible focus indicator on ${el.tagName.toLowerCase()} "${name}"`;
  });
}

for (const scenario of SCENARIOS) {
  test(`${scenario.name} (${scenario.as}): axe, sizes, focus and Escape`, async ({ browser }) => {
    test.setTimeout(180_000);
    for (const [label, context] of [
      ['390 touch', PHONE],
      ['1440 desktop', DESKTOP],
    ] as const) {
      if (scenario.widths && !scenario.widths.includes(context.viewport.width)) continue;
      const { page, close } = await openRoute(
        browser,
        session,
        { name: scenario.name, as: scenario.as, path: scenario.path },
        context,
        scenario.before,
      );
      const opened = await scenario.open(page);
      if (!opened) {
        expect(scenario.optional, `${scenario.name}: nothing to open`).toBe(true);
        test
          .info()
          .annotations.push({ type: 'skipped', description: `${scenario.name} @${label}: no row in the seed` });
        await close();
        continue;
      }
      await page.waitForTimeout(300); // enter animation
      await opened.panel.evaluate((el) => el.setAttribute('data-ux-scope', '1'));
      if (SHOTS) {
        mkdirSync(SHOTS, { recursive: true });
        await page.screenshot({
          path: `${SHOTS}/${scenario.name.replace(/[^a-z0-9]+/gi, '_')}-${label.replace(' ', '_')}.png`,
        });
      }
      expect.soft(await axeViolations(page), `axe @${label}`).toEqual([]);
      expect
        .soft(
          await auditUi(page, { touch: label.startsWith('390'), allow: ALLOW, scope: '[data-ux-scope]' }),
          `DS-03 @${label}`,
        )
        .toEqual([]);

      // Keyboard: focus stays inside a modal and is always visible.
      const leaks: string[] = [];
      const invisible: string[] = [];
      for (let step = 0; step < 12; step += 1) {
        await page.keyboard.press('Tab');
        if (opened.modal) {
          const inside = await opened.panel.evaluate((el) => el.contains(document.activeElement));
          if (!inside) leaks.push(`Tab #${step + 1} left the overlay`);
        }
        const problem = await focusIsVisible(page);
        if (problem && !invisible.includes(problem)) invisible.push(problem);
        if (!opened.modal) break;
      }
      expect.soft(leaks, `focus trap @${label}`).toEqual([]);
      expect.soft(invisible, `visible focus @${label}`).toEqual([]);

      // Escape closes the overlay and gives focus back to its opener.
      await page.keyboard.press('Escape');
      await expect.soft(page.locator('[data-ux-scope]'), `Escape closes @${label}`).toBeHidden();
      const layers = await page.locator('[role="dialog"][aria-modal="true"]').count();
      if (opened.opener && layers === 0 && (await opened.opener.isVisible())) {
        await expect.soft(opened.opener, `focus returns to the opener @${label}`).toBeFocused();
      }
      await close();
    }
  });
}
