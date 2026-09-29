import { mkdirSync } from 'node:fs';
import { expect, test, type Browser, type Locator, type Page } from '@playwright/test';
import { apiLogin, DEMO_ACCOUNTS, skipConsentBanner, useSession, waitUntilReady } from './support/helpers';
import { staffToken } from './support/adminAdversarial';
import { auditFieldRows, auditGutters } from './support/uiAudit';

// UI consistency (owner review): the same four defects were reported on one page each and fixed on all of them, so
// this spec walks every public, account and admin page instead of the ones in the screenshots.
//   1. the public header (desktop) and the mobile menu link to the news page ("Tin tức"), and to projects and areas;
//   2. nothing sits closer than the gutter to the left or right viewport edge, at 320 … 1920 px;
//   3. every detail/edit view that used to slide in from the right is a centred dialog (see the last section);
//   4. controls that share a row of a form start at the same y.
// Set UI_CONSISTENCY_SHOTS=<dir> to save a screenshot of every checked state.

const WIDTHS = [320, 360, 768, 1024, 1280, 1440, 1920] as const;
const ADMIN = '/2026/nhadatchuan/admin';
const SHOTS = process.env.UI_CONSISTENCY_SHOTS;

// Runs once, in the chromium-1440 project: the spec sets its own viewports.
test.beforeEach(({ browserName }, testInfo) => {
  void browserName;
  test.skip(
    testInfo.project.name !== 'chromium-1440',
    'sets its own viewports: runs once, in the chromium-1440 project',
  );
});
test.use({ actionTimeout: 15_000 });

interface Tokens {
  admin: string;
  broker: string;
  buyer: string;
  slugs: { project: string; area: string; article: string; listing: string };
}
let tokens: Tokens;

test.beforeAll(async ({ playwright }, workerInfo) => {
  if (workerInfo.project.name !== 'chromium-1440') return;
  const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL });
  const first = async (path: string, pick: (body: unknown) => string | undefined): Promise<string> => {
    const response = await request.get(path);
    if (!response.ok()) return 'khong-co';
    return pick(await response.json()) ?? 'khong-co';
  };
  type Items = { items?: Array<{ slug?: string }> };
  const firstItem = (body: unknown) => (body as Items).items?.[0]?.slug;
  tokens = {
    admin: await staffToken(request, 'demo.admin@bds.local'),
    broker: await apiLogin(request, DEMO_ACCOUNTS.broker),
    buyer: await apiLogin(request, DEMO_ACCOUNTS.buyer),
    slugs: {
      project: await first('/api/v2/public/projects?page=0&size=1', firstItem),
      area: await first('/api/v2/public/areas', firstItem),
      article: await first('/api/v2/public/articles?page=0&size=1', firstItem),
      listing: await first(
        '/api/v1/listings/search?purpose=SALE&size=1',
        (b) => (b as Array<{ slug?: string }>)[0]?.slug,
      ),
    },
  };
  await request.dispose();
});

async function settle(page: Page) {
  try {
    await waitUntilReady(page);
  } catch {
    await page.waitForLoadState('load');
  }
  await page.evaluate(async () => {
    await document.fonts.ready;
  });
  await page.waitForTimeout(250);
}

async function shot(page: Page, name: string, width: number) {
  if (!SHOTS) return;
  mkdirSync(SHOTS, { recursive: true });
  await page.screenshot({ path: `${SHOTS}/${name.replace(/[^a-z0-9-]+/gi, '_')}-${width}.png`, fullPage: true });
}

type As = 'guest' | 'buyer' | 'broker' | 'admin';
interface PageSpec {
  name: string;
  path: (t: Tokens) => string;
  as: As;
}

const PAGES: PageSpec[] = [
  // public
  { name: 'home', path: () => '/', as: 'guest' },
  { name: 'search', path: () => '/search?purpose=SALE', as: 'guest' },
  { name: 'listing', path: (t) => `/listings/${t.slugs.listing}`, as: 'guest' },
  { name: 'compare', path: () => '/compare', as: 'guest' },
  { name: 'projects', path: () => '/du-an', as: 'guest' },
  { name: 'project', path: (t) => `/du-an/${t.slugs.project}`, as: 'guest' },
  { name: 'areas', path: () => '/khu-vuc', as: 'guest' },
  { name: 'area', path: (t) => `/khu-vuc/${t.slugs.area}`, as: 'guest' },
  { name: 'news', path: () => '/tin-tuc', as: 'guest' },
  { name: 'article', path: (t) => `/tin-tuc/${t.slugs.article}`, as: 'guest' },
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
  { name: 'listing-new', path: () => '/listings/new', as: 'broker' },
  // account
  { name: 'account', path: () => '/account', as: 'buyer' },
  { name: 'kyc', path: () => '/kyc', as: 'buyer' },
  { name: 'saved', path: () => '/saved', as: 'buyer' },
  { name: 'saved-searches', path: () => '/saved?tab=searches', as: 'buyer' },
  { name: 'saved-shortlists', path: () => '/saved?tab=shortlists', as: 'buyer' },
  { name: 'notifications', path: () => '/notifications', as: 'buyer' },
  { name: 'inquiries', path: () => '/my-inquiries', as: 'buyer' },
  { name: 'become-owner', path: () => '/become-owner', as: 'buyer' },
  { name: 'my-listings', path: () => '/my-listings', as: 'broker' },
  { name: 'my-leads', path: () => '/my-leads', as: 'broker' },
  { name: 'billing', path: () => '/billing', as: 'broker' },
  { name: 'broker-workspace', path: () => '/broker/workspace', as: 'broker' },
  // admin
  { name: 'admin-login', path: () => `${ADMIN}/login`, as: 'guest' },
  { name: 'admin-moderation', path: () => `${ADMIN}/moderation`, as: 'admin' },
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

async function openPage(browser: Browser, spec: PageSpec, width: number, height = 900) {
  const context = await browser.newContext({ viewport: { width, height }, locale: 'vi-VN' });
  const page = await context.newPage();
  await skipConsentBanner(page);
  if (spec.as !== 'guest') await useSession(page, tokens[spec.as]);
  await page.goto(spec.path(tokens));
  await settle(page);
  return { page, close: () => context.close() };
}

test.describe('1. public navigation', () => {
  test('the desktop header links to news, projects and areas and "Tin tức" opens the news page', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/');
    const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
    for (const name of ['Dự án', 'Khu vực', 'Tin tức']) await expect(nav.getByRole('link', { name })).toBeVisible();
    await nav.getByRole('link', { name: 'Tin tức' }).click();
    await expect(page).toHaveURL(/\/tin-tuc$/);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(nav.getByRole('link', { name: 'Tin tức' })).toHaveAttribute('aria-current', 'page');
  });

  test('the mobile menu links to news, projects and areas and "Tin tức" opens the news page', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 800 });
    await page.goto('/');
    await page.getByRole('button', { name: 'Mở menu' }).click();
    const menu = page.getByRole('navigation', { name: 'Điều hướng di động' });
    for (const name of ['Dự án', 'Khu vực', 'Tin tức']) await expect(menu.getByRole('link', { name })).toBeVisible();
    await menu.getByRole('link', { name: 'Tin tức' }).click();
    await expect(page).toHaveURL(/\/tin-tuc$/);
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });

  for (const width of [320, 360, 768, 1024, 1280, 1440]) {
    test(`the header does not overflow at ${width} px (signed out, broker and admin)`, async ({ browser }) => {
      for (const as of ['guest', 'broker', 'admin'] as const) {
        const { page, close } = await openPage(browser, { name: 'home', path: () => '/', as }, width);
        const header = await page.locator('.ndc-header').boundingBox();
        expect(header?.width).toBeCloseTo(width, 0);
        const overflow = await page.evaluate(() => {
          const inner = document.querySelector('.ndc-header-inner') as HTMLElement;
          const box = inner.getBoundingClientRect();
          return Array.from(inner.querySelectorAll<HTMLElement>('a, button'))
            .filter((el) => el.getBoundingClientRect().width > 0)
            .filter(
              (el) =>
                el.getBoundingClientRect().right > box.right + 0.5 || el.getBoundingClientRect().left < box.left - 0.5,
            )
            .map((el) => `${el.textContent?.trim() || el.getAttribute('aria-label')}`);
        });
        expect(overflow, `${as} header at ${width}px`).toEqual([]);
        if (width >= 1280) {
          // With the full navigation showing, the brand keeps its whole name and no link wraps onto a second line.
          const squeezed = await page.evaluate(() => {
            const brand = document.querySelector('.ndc-brand strong') as HTMLElement;
            const wrapped = Array.from(document.querySelectorAll<HTMLElement>('header nav a')).filter(
              (el) => el.getBoundingClientRect().height > 48,
            );
            return {
              brandTruncated: brand.scrollWidth > brand.clientWidth + 1,
              wrapped: wrapped.map((el) => el.textContent),
            };
          });
          expect(squeezed, `${as} header at ${width}px`).toEqual({ brandTruncated: false, wrapped: [] });
        }
        expect(
          await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth),
        ).toBe(true);
        await shot(page, `header-${as}`, width);
        await close();
      }
    });
  }
});

test.describe('2. gutters', () => {
  for (const spec of PAGES) {
    test(`${spec.name}: nothing touches the left or right edge of the viewport`, async ({ browser }) => {
      test.setTimeout(120_000);
      const failures: string[] = [];
      for (const width of WIDTHS) {
        const { page, close } = await openPage(browser, spec, width);
        for (const finding of await auditGutters(page)) failures.push(`@${width}: ${finding}`);
        await shot(page, spec.name, width);
        await close();
      }
      expect(failures, spec.name).toEqual([]);
    });
  }
});

// --- 3. detail and edit views are centred dialogs, 4. their form rows line up ------------------------------------------

const rowButton = (page: Page, name: RegExp) => page.getByRole('button', { name }).first();

interface DialogScenario {
  name: string;
  as: As;
  path: string;
  /** Opens the view and returns the control that opened it (focus must return to it on Escape). */
  open: (page: Page) => Promise<Locator | null>;
  /** The view used to be a right-hand Sheet (its geometry is part of the owner's request); false for plain form dialogs. */
  wasSheet: boolean;
  /** An unbroken opener that only exists for some data: skipped when the seed has no such row. */
  optional?: boolean;
}

const DIALOGS: DialogScenario[] = [
  {
    name: 'admin verification: evidence comparison',
    as: 'admin',
    path: `${ADMIN}/verification`,
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Đối chiếu /);
      await opener.click();
      await expect(page.getByRole('heading', { name: 'Đối chiếu bằng chứng' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin verification: reject reason (second layer)',
    as: 'admin',
    path: `${ADMIN}/verification`,
    wasSheet: false,
    open: async (page) => {
      await rowButton(page, /^Đối chiếu /).click();
      const opener = page.getByRole('button', { name: 'Từ chối…' });
      await opener.click();
      await expect(page.getByRole('dialog', { name: 'Từ chối hồ sơ giấy tờ' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin moderation: review of a submission',
    as: 'admin',
    path: `${ADMIN}/moderation`,
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Đối chiếu /);
      await opener.click();
      await expect(page.getByRole('heading', { name: /so với bản/i }).first()).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin listings: detail',
    as: 'admin',
    path: `${ADMIN}/listings`,
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Chi tiết /);
      await opener.click();
      await expect(page.getByRole('heading', { name: 'Lịch sử trạng thái' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin listings: lock reason',
    as: 'admin',
    path: `${ADMIN}/listings`,
    wasSheet: false,
    open: async (page) => {
      const opener = rowButton(page, /^Khóa tin: /);
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin users: history',
    as: 'admin',
    path: `${ADMIN}/users`,
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Lịch sử /);
      await opener.click();
      await expect(page.getByRole('heading', { name: 'Thao tác quản trị' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin users: identity file',
    as: 'admin',
    path: `${ADMIN}/users`,
    wasSheet: true,
    optional: true,
    open: async (page) => {
      const opener = rowButton(page, /^Hồ sơ định danh /);
      if ((await opener.count()) === 0) return null;
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin users: role change',
    as: 'admin',
    path: `${ADMIN}/users`,
    wasSheet: false,
    open: async (page) => {
      const opener = rowButton(page, /^Đổi vai trò /);
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin reports: case',
    as: 'admin',
    path: `${ADMIN}/reports`,
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Mở vụ việc/);
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin billing: order history',
    as: 'admin',
    path: `${ADMIN}/billing`,
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Lịch sử /);
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin cms: article',
    as: 'admin',
    path: `${ADMIN}/cms`,
    wasSheet: true,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Mở', exact: true }).first();
      await opener.click();
      await expect(page.getByRole('heading', { name: 'Lịch sử phiên bản' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin cms: new article',
    as: 'admin',
    path: `${ADMIN}/cms`,
    wasSheet: false,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Bài viết mới' });
      await opener.click();
      await expect(page.getByRole('dialog', { name: 'Bài viết mới' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin projects: public profile',
    as: 'admin',
    path: `${ADMIN}/projects`,
    wasSheet: false,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Trang công khai: mô tả, nguồn, tiện ích' }).first();
      await opener.click();
      await expect(page.getByRole('textbox', { name: 'Nguồn thông tin' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin projects: new project',
    as: 'admin',
    path: `${ADMIN}/projects`,
    wasSheet: false,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Tạo dự án' });
      await opener.click();
      await expect(page.getByRole('dialog', { name: 'Tạo dự án mới' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'account billing: order history',
    as: 'broker',
    path: '/billing',
    wasSheet: true,
    optional: true,
    open: async (page) => {
      const opener = rowButton(page, /^Lịch sử /);
      if ((await opener.count()) === 0) return null;
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'account leads: lead detail',
    as: 'broker',
    path: '/my-leads',
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Xử lý yêu cầu của /);
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'public: sign-in and registration',
    as: 'guest',
    path: '/',
    wasSheet: false,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Đăng nhập' }).first();
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await page.getByRole('dialog').getByRole('button', { name: 'Đăng ký', exact: true }).click();
      return opener;
    },
  },
];

/** The dialog opened last, tagged so the row audit can be scoped to it. */
async function topDialog(page: Page): Promise<Locator> {
  await page.evaluate(() => {
    document.querySelectorAll('[data-audit-top]').forEach((el) => el.removeAttribute('data-audit-top'));
    const all = document.querySelectorAll('[role="dialog"]');
    all[all.length - 1]?.setAttribute('data-audit-top', '1');
  });
  return page.locator('[data-audit-top]');
}

async function expectCentredDialog(page: Page, width: number, height: number) {
  await page.waitForTimeout(300); // the enter animation of the panel
  const dialog = await topDialog(page);
  const box = (await dialog.boundingBox())!;
  const centre = box.x + box.width / 2;
  expect(Math.abs(centre - width / 2), `dialog centre ${centre} vs viewport centre ${width / 2}`).toBeLessThanOrEqual(
    2,
  );
  expect(box.x, 'dialog left edge').toBeGreaterThanOrEqual(0);
  expect(box.x + box.width, 'dialog right edge').toBeLessThanOrEqual(width);
  expect(box.y, 'dialog top edge').toBeGreaterThanOrEqual(0);
  expect(box.y + box.height, 'dialog bottom edge').toBeLessThanOrEqual(height);
  // Not a right-hand panel: it never spans the full height on a desktop and keeps a margin on both sides.
  if (width >= 640) {
    expect(box.x, 'left margin').toBeGreaterThanOrEqual(16);
    expect(width - (box.x + box.width), 'right margin').toBeGreaterThanOrEqual(16);
    expect(box.height, 'about 90 % of the viewport at most').toBeLessThanOrEqual(height * 0.9 + 2);
  }
  // Named by its visible title.
  const labelledBy = await dialog.getAttribute('aria-labelledby');
  expect(labelledBy, 'aria-labelledby').toBeTruthy();
  await expect(page.locator(`#${labelledBy}`)).not.toBeEmpty();
  await expect(dialog).toHaveAttribute('aria-modal', 'true');
  // The actions (or the close button) are on screen without scrolling the page or the body.
  const footerButtons = dialog.locator(':scope > div').last().getByRole('button');
  if ((await footerButtons.count()) > 0) {
    const first = (await footerButtons.first().boundingBox())!;
    expect(first.y + first.height, 'footer button bottom').toBeLessThanOrEqual(height);
    expect(first.y, 'footer button top').toBeGreaterThanOrEqual(box.y);
  }
  const close = dialog.getByRole('button', { name: /^Đóng/ }).first();
  await expect(close).toBeInViewport();
}

test.describe('3. dialogs are centred, 4. rows in a dialog line up', () => {
  for (const scenario of DIALOGS) {
    test(`${scenario.name}`, async ({ browser }) => {
      test.setTimeout(150_000);
      for (const [width, height] of [
        [360, 740],
        [768, 900],
        [1440, 900],
      ] as const) {
        const context = await browser.newContext({ viewport: { width, height }, locale: 'vi-VN' });
        const page = await context.newPage();
        await skipConsentBanner(page);
        if (scenario.as !== 'guest') await useSession(page, tokens[scenario.as]);
        await page.goto(scenario.path);
        await settle(page);
        const opener = await scenario.open(page);
        if (!opener && scenario.optional) {
          test
            .info()
            .annotations.push({ type: 'skipped', description: `${scenario.name}: no row to open in the seed` });
          await context.close();
          continue;
        }
        await expectCentredDialog(page, width, height);
        const rows = await auditFieldRows(page, '[data-audit-top]');
        expect(rows, `${scenario.name} at ${width}px: row alignment`).toEqual([]);
        if (SHOTS)
          await page.screenshot({ path: `${SHOTS}/dialog-${scenario.name.replace(/[^a-z0-9]+/gi, '_')}-${width}.png` });
        // Escape closes the top layer and gives focus back to what opened it.
        const layers = await page.getByRole('dialog').count();
        await page.keyboard.press('Escape');
        await expect(page.getByRole('dialog')).toHaveCount(layers - 1);
        if (layers === 1 && opener) await expect(opener).toBeFocused();
        await context.close();
      }
    });
  }

  test('a click on the dimmed backdrop closes the evidence dialog', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'vi-VN' });
    const page = await context.newPage();
    await skipConsentBanner(page);
    await useSession(page, tokens.admin);
    await page.goto(`${ADMIN}/verification`);
    await settle(page);
    await rowButton(page, /^Đối chiếu /).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.mouse.click(8, 450);
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await context.close();
  });

  test('the dialog body scrolls while the title and the footer stay put', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 1440, height: 500 }, locale: 'vi-VN' });
    const page = await context.newPage();
    await skipConsentBanner(page);
    await useSession(page, tokens.admin);
    await page.goto(`${ADMIN}/cms`);
    await settle(page);
    await page.getByRole('button', { name: 'Bài viết mới' }).click();
    const dialog = page.getByRole('dialog', { name: 'Bài viết mới' });
    const title = dialog.getByRole('heading', { name: 'Bài viết mới' });
    const save = dialog.getByRole('button', { name: 'Lưu bản nháp' });
    const before = [(await title.boundingBox())!.y, (await save.boundingBox())!.y];
    await dialog.getByLabel('Căn cứ pháp lý').scrollIntoViewIfNeeded();
    const after = [(await title.boundingBox())!.y, (await save.boundingBox())!.y];
    expect(after).toEqual(before);
    await expect(save).toBeInViewport();
    await context.close();
  });
});

test.describe('4. form rows', () => {
  test('listing wizard: every step, for sale and for rent', async ({ browser }) => {
    test.setTimeout(120_000);
    const failures: string[] = [];
    for (const width of [768, 1280]) {
      const { page, close } = await openPage(
        browser,
        { name: 'wizard', path: () => '/listings/new', as: 'broker' },
        width,
      );
      for (const purpose of ['Bán', 'Cho thuê']) {
        await page.getByRole('button', { name: /Bước 1 \/ 4/ }).click();
        await page.getByRole('button', { name: purpose, exact: true }).click();
        await expect(page.getByRole('button', { name: purpose, exact: true })).toHaveAttribute('aria-pressed', 'true');
        for (let step = 1; step <= 4; step += 1) {
          await page.getByRole('button', { name: new RegExp(`Bước ${step} / 4`) }).click();
          await page.waitForTimeout(200);
          for (const finding of await auditFieldRows(page))
            failures.push(`@${width} ${purpose} step ${step}: ${finding}`);
          for (const finding of await auditGutters(page))
            failures.push(`@${width} ${purpose} step ${step}: ${finding}`);
        }
      }
      await close();
    }
    expect(failures).toEqual([]);
  });

  for (const spec of PAGES) {
    test(`${spec.name}: controls in one row start at the same y`, async ({ browser }) => {
      test.setTimeout(60_000);
      const failures: string[] = [];
      for (const width of [768, 1280]) {
        const { page, close } = await openPage(browser, spec, width);
        for (const finding of await auditFieldRows(page)) failures.push(`@${width}: ${finding}`);
        await close();
      }
      expect(failures, spec.name).toEqual([]);
    });
  }
});
