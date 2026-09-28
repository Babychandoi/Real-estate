import { expect, test, type Page } from '@playwright/test';
import { axeViolations, firstPublicListing, gotoReady, horizontalOverflow } from './support/helpers';

// F01.3: accessibility and responsive containment run on their own, independent of screenshots, so a visual diff
// can never hide an axe or overflow regression. Each page is one test with soft assertions: both checks report.

const PAGES: Array<{ name: string; path: string | ((page: Page) => Promise<string>) }> = [
  { name: 'home', path: '/' },
  { name: 'search', path: '/search' },
  { name: 'compare', path: '/compare' },
  {
    name: 'listing detail',
    path: async (page) => `/listings/${(await firstPublicListing(page.request)).slug}`,
  },
  { name: 'about', path: '/about' },
  { name: 'terms', path: '/terms' },
  { name: 'privacy', path: '/privacy' },
  { name: 'contact', path: '/contact' },
  { name: 'forgot password', path: '/forgot-password' },
  { name: 'not found', path: '/duong-dan-khong-ton-tai-e2e' },
];

for (const { name, path } of PAGES) {
  test(`${name}: WCAG 2.2 AA (axe) and no horizontal scrolling`, async ({ page }) => {
    await gotoReady(page, typeof path === 'string' ? path : await path(page));

    expect.soft(await axeViolations(page), 'axe violations').toEqual([]);
    expect.soft(await horizontalOverflow(page), 'elements wider than the viewport').toEqual([]);
  });
}

test.describe('UI kit catalog (/__ui)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/__ui');
    const catalog = page.getByRole('heading', { level: 1, name: 'Thư viện giao diện' });
    // The catalog is compiled only into dev builds or builds with VITE_ENABLE_UI_CATALOG=true (CI enables it).
    const enabled = await catalog.waitFor({ timeout: 10_000 }).then(
      () => true,
      () => false,
    );
    // CI builds with the catalog and sets E2E_REQUIRE_UI_CATALOG=1, so there a missing catalog fails, never skips.
    if (process.env.E2E_REQUIRE_UI_CATALOG === '1') expect(enabled, 'UI catalog missing from the CI build').toBe(true);
    test.skip(!enabled, 'UI catalog not compiled into this build (set VITE_ENABLE_UI_CATALOG=true)');
    await gotoReady(page, '/__ui');
  });

  test('every component state passes axe and fits the viewport', async ({ page }) => {
    expect.soft(await axeViolations(page), 'axe violations').toEqual([]);
    expect.soft(await horizontalOverflow(page), 'elements wider than the viewport').toEqual([]);
  });

  test('dialog traps focus, closes on Escape and returns focus', async ({ page }) => {
    const opener = page.getByRole('button', { name: 'Mở hộp thoại' });
    await opener.focus();
    await page.keyboard.press('Enter');
    const dialog = page.getByRole('dialog', { name: 'Báo cáo tin vi phạm' });
    await expect(dialog).toBeVisible();
    expect(await axeViolations(page, '[role="dialog"]'), 'axe violations in the dialog').toEqual([]);

    for (let step = 0; step < 6; step += 1) {
      await page.keyboard.press('Tab');
      expect(await dialog.evaluate((element) => element.contains(document.activeElement))).toBe(true);
    }
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(opener).toBeFocused();
  });

  test('filter sheet is a modal dialog and closes with its button', async ({ page }) => {
    const opener = page.getByRole('button', { name: 'Mở bảng bộ lọc' });
    await opener.focus();
    await page.keyboard.press('Enter');
    const sheet = page.getByRole('dialog', { name: 'Bộ lọc' });
    await expect(sheet).toBeVisible();
    expect(await axeViolations(page, '[role="dialog"]'), 'axe violations in the sheet').toEqual([]);
    await sheet.getByRole('button', { name: 'Đóng' }).click();
    await expect(sheet).toBeHidden();
    await expect(opener).toBeFocused();
  });

  test('tabs follow the arrow keys', async ({ page }) => {
    const first = page.getByRole('tab', { name: /Đang hiển thị/ });
    await first.focus();
    await page.keyboard.press('ArrowRight');
    await expect(page.getByRole('tab', { name: /Chờ duyệt/ })).toBeFocused();
    await expect(page.getByRole('tab', { name: /Chờ duyệt/ })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('tabpanel', { name: /Chờ duyệt/ })).toContainText('2 tin chờ duyệt');
  });
});
