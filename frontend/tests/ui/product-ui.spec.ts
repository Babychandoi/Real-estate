import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { createFixtureApi, listings, SELLER_ID, photo } from './fixtures.mjs';
const admin = '/2026/nhadatchuan/admin';
const routes: Array<[string, string]> = [
  ['/', 'GUEST'],
  ['/search', 'GUEST'],
  ['/search?purpose=RENT', 'GUEST'],
  [`/listings/${listings[0].slug}`, 'GUEST'],
  ['/compare', 'GUEST'],
  [`/nguoi-dang/${SELLER_ID}`, 'GUEST'],
  ['/about', 'GUEST'],
  ['/terms', 'GUEST'],
  ['/privacy', 'GUEST'],
  ['/contact', 'GUEST'],
  ['/forgot-password', 'GUEST'],
  ['/reset-password', 'GUEST'],
  ['/verify-email', 'GUEST'],
  ['/missing-page', 'GUEST'],
  ['/account', 'BROKER'],
  ['/my-listings', 'BROKER'],
  ['/my-leads', 'BROKER'],
  ['/broker/workspace', 'BROKER'],
  ['/billing', 'BROKER'],
  ['/kyc', 'USER'],
  ['/my-inquiries', 'USER'],
  ['/listings/new', 'BROKER'],
  [`/listings/new?edit=${listings[1].id}`, 'BROKER'],
  [admin, 'ADMIN'],
  ...[
    'moderation',
    'listings',
    'users',
    'leads-and-reports',
    'verification',
    'billing',
    'analytics',
    'projects',
    'cms',
  ].map((route) => [`${admin}/${route}`, 'ADMIN'] as [string, string]),
  [`${admin}/login`, 'GUEST'],
];
async function fixture(page: Page, role = 'GUEST') {
  // Keep local UI regressions independent of the external font CDN.
  await page.route('https://fonts.googleapis.com/**', (route) => route.fulfill({ contentType: 'text/css', body: '' }));
  const api = createFixtureApi(role === 'GUEST' ? 'USER' : role);
  const unknown: string[] = [];
  if (role !== 'GUEST') await page.addInitScript(() => sessionStorage.setItem('bds_access_token', 'ui-test-only'));
  await page.route('**/__preview/image-*', (route) => route.fulfill({ contentType: 'image/svg+xml', body: photo(1) }));
  await page.route(/\/api\/v[12]\//, (route) => {
    const req = route.request();
    let body = {};
    try {
      body = req.postDataJSON() || {};
    } catch {
      /* form */
    }
    const result = api(req.url(), req.method(), body);
    if (result.status === 404) unknown.push(new URL(req.url()).pathname);
    return route.fulfill({
      status: result.status,
      contentType: result.contentType || 'application/json',
      body: result.status === 204 ? '' : result.text || JSON.stringify(result.body),
    });
  });
  return unknown;
}
for (const [url, role] of routes) {
  test(`route ${url} (${role})`, async ({ page }, testInfo) => {
    const unknown = await fixture(page, role);
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.goto(url);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByRole('main')).toHaveCount(1);
    await expect(page.getByRole('heading', { name: 'Chưa thể tải nội dung' })).toHaveCount(0);
    await expect
      .poll(() => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth))
      .toBeLessThanOrEqual(1);
    expect(unknown).toEqual([]);
    expect(errors).toEqual([]);
    if (
      ['ui-360', 'ui-1440'].includes(testInfo.project.name) &&
      ['/', '/search', '/my-listings', `${admin}/users`, `/listings/${listings[0].slug}`].includes(url)
    )
      await page.screenshot({
        path: `test-results/screenshots/${testInfo.project.name}-${url.replace(/[^a-z0-9]/gi, '_') || 'home'}.png`,
        fullPage: true,
      });
  });
}
/** Price bands are toolbar chips from 1024px; below that they live in the "Bộ lọc" sheet. */
async function choosePriceBand(page: Page, label: string) {
  const toolbar = page.getByRole('group', { name: 'Giá thuê mỗi tháng' }).getByRole('button', { name: label });
  if (await toolbar.isVisible()) return toolbar.click();
  await page.getByRole('button', { name: /^Bộ lọc/ }).click();
  const sheet = page.getByRole('dialog', { name: 'Bộ lọc' });
  await sheet.getByRole('button', { name: label, exact: true }).click();
  await sheet.getByRole('button', { name: 'Xem kết quả', exact: true }).click();
}
test('search loads more with a cursor and restores filters after reload and back', async ({ page }) => {
  await fixture(page);
  await page.goto('/search');
  await expect(page.getByRole('article')).toHaveCount(24);
  const nextPage = page.waitForRequest(
    (request) => request.url().includes('/api/v2/listings/search') && request.url().includes('cursor='),
  );
  await page.getByRole('button', { name: 'Xem thêm', exact: true }).click();
  await nextPage;
  await expect(page.getByRole('article')).toHaveCount(30);
  await expect(page.getByText('Đã hiển thị tất cả 30 tin')).toBeVisible();
  // The cursor is not a shareable filter: it never reaches the URL.
  await expect(page).not.toHaveURL(/cursor=/);
  // Back from a detail page restores the loaded pages (in-memory snapshot of this history entry).
  await page.getByRole('link', { name: `Xem chi tiết: ${listings[29].title}`, exact: true }).click();
  await expect(page).toHaveURL(new RegExp(listings[29].slug));
  await page.goBack();
  await expect(page.getByRole('article')).toHaveCount(30);
  // A reload starts again from the first page.
  await page.reload();
  await expect(page.getByRole('article')).toHaveCount(24);
  await page.getByRole('button', { name: 'Thuê nhà', exact: true }).click();
  await expect(page).toHaveURL(/purpose=RENT/);
  await choosePriceBand(page, '5 – dưới 10 triệu/tháng');
  await expect(page).toHaveURL(/priceMin=5000000&priceMax=9999999/);
  await expect(page.getByRole('article').first()).toContainText('/tháng');
  const request = page.waitForRequest(
    (request) =>
      request.url().includes('/api/v2/listings/search') &&
      request.url().includes('priceMin=10000000') &&
      request.url().includes('priceMax=19999999'),
  );
  await choosePriceBand(page, '10 – dưới 20 triệu/tháng');
  await request;
  await page.reload();
  await expect(page).toHaveURL(/priceMin=10000000&priceMax=19999999/);
  await expect(page.getByRole('article').first()).toContainText('/tháng');
  await page.goBack();
  await expect(page).toHaveURL(/priceMin=5000000&priceMax=9999999/);
  await expect(page.getByRole('button', { name: 'Thuê nhà', exact: true })).toHaveAttribute('aria-pressed', 'true');
});
test('search handles invalid URL and retry/empty states', async ({ page }) => {
  await fixture(page);
  await page.goto('/search?purpose=invalid&cursor=-20&sort=invalid');
  await expect(page.getByRole('article')).toHaveCount(24);
  await expect(page.getByLabel('Sắp xếp kết quả')).toHaveValue('NEWEST');
  await page.route('**/api/v2/listings/search**', (route) =>
    route.fulfill({
      status: 503,
      json: {
        title: 'Unavailable',
        detail: 'Fixture unavailable',
        status: 503,
      },
    }),
  );
  await page.getByLabel('Sắp xếp kết quả').selectOption('PRICE_ASC');
  await expect(page.getByRole('alert')).toBeVisible();
  await page.unroute('**/api/v2/listings/search**');
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(page.getByRole('article')).toHaveCount(24);
  await page.getByRole('combobox', { name: 'Tìm theo từ khóa hoặc địa điểm' }).fill('no-result-xyz');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/q=no-result-xyz/);
  await expect(page.getByRole('heading', { name: 'Chưa có tin phù hợp' })).toBeVisible();
});
test('gallery exposes all 20 images with keyboard and restores focus', async ({ page }) => {
  await fixture(page);
  await page.goto(`/listings/${listings[0].slug}`);
  const trigger = page.getByRole('button', { name: 'Xem tất cả 20 ảnh' });
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: listings[0].title });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: /^Xem ảnh \d+\/20$/ })).toHaveCount(20);
  await dialog.getByRole('button', { name: 'Xem ảnh 20/20', exact: true }).click();
  await expect(dialog.getByText('Ảnh 20/20')).toBeVisible();
  await page.keyboard.press('ArrowRight');
  await expect(dialog.getByText('Ảnh 1/20')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(trigger).toBeFocused();
});
test('card compare is independent of navigation and persists', async ({ page }) => {
  await fixture(page);
  await page.goto('/search');
  await page
    .getByRole('button', {
      name: `Thêm ${listings[0].title} vào danh sách so sánh`,
      exact: true,
    })
    .click();
  await page
    .getByRole('button', {
      name: `Thêm ${listings[1].title} vào danh sách so sánh`,
      exact: true,
    })
    .click();
  await expect(page).toHaveURL(/\/search/);
  await page.goto('/compare');
  await expect(page.getByRole('main')).toContainText(listings[0].title);
  await expect(page.getByRole('main')).toContainText(listings[1].title);
  await page.reload();
  await expect(page.getByRole('main')).toContainText(listings[1].title);
});
test('mobile menu has keyboard containment and role links', async ({ page }) => {
  await fixture(page, 'BROKER');
  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto('/');
  const trigger = page.getByRole('button', { name: 'Mở menu', exact: true });
  await trigger.click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('link', { name: 'Tin đăng của tôi' })).toBeVisible();
  for (let i = 0; i < 20; i++) {
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => document.activeElement?.closest('[role="dialog"]') !== null)).toBeTruthy();
  }
  await page.keyboard.press('Escape');
  await expect(trigger).toBeFocused();
});
test('my listings confirms mutation and reports failed save', async ({ page }) => {
  await fixture(page, 'BROKER');
  await page.goto('/my-listings');
  const rows = page.getByTestId('my-listing');
  await page.getByRole('tab', { name: /^Nháp/ }).click();
  await expect(page).toHaveURL(/status=DRAFT/);
  await expect(rows).toHaveCount(3);
  await rows.first().getByRole('button', { name: 'Gửi duyệt', exact: true }).click();
  await expect(page.getByText('Đã gửi duyệt.')).toBeVisible();
  // The server-side tab reloads: the submitted draft leaves the "Nháp" tab.
  await expect(rows).toHaveCount(2);
  await page.getByRole('tab', { name: /^Đang hiển thị/ }).click();
  await expect(page).toHaveURL(/status=ACTIVE/);
  await rows.first().getByRole('button', { name: 'Xác nhận còn hàng', exact: true }).click();
  await expect(page.getByText('Đã xác nhận còn hàng thêm 45 ngày.')).toBeVisible();
  await page.route('**/api/v1/listings/*/visibility', (route) =>
    route.fulfill({
      status: 500,
      json: { title: 'Failure', detail: 'Failure', status: 500 },
    }),
  );
  const count = await rows.count();
  await rows.first().getByRole('button', { name: 'Ẩn tin', exact: true }).click();
  await expect(page.getByText('Chưa thực hiện được, vui lòng thử lại.')).toBeVisible();
  await expect(rows).toHaveCount(count);
  await expect(rows.first().getByRole('button', { name: 'Ẩn tin', exact: true })).toBeVisible();
});
test('protected admin page does not expose role controls to USER', async ({ page }) => {
  await fixture(page, 'USER');
  await page.goto(`${admin}/users`);
  await expect(page).toHaveURL(/\/my-inquiries$/);
  await expect(page.getByRole('button', { name: /Khóa tài khoản/ })).toHaveCount(0);
});
test('key screens have no serious WCAG A/AA violations', async ({ page }, testInfo) => {
  test.setTimeout(120_000);
  if (testInfo.project.name !== 'ui-1440') {
    await fixture(page);
    await page.goto('/search');
  } else {
    await fixture(page, 'ADMIN');
  }
  const checks =
    testInfo.project.name === 'ui-1440'
      ? [
          '/',
          '/search',
          `/listings/${listings[0].slug}`,
          '/compare',
          '/account',
          '/my-listings',
          `${admin}/users`,
          `${admin}/moderation`,
          `${admin}/billing`,
          `${admin}/cms`,
        ]
      : ['/search'];
  for (const url of checks) {
    await page.goto(url);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
    expect(
      result.violations
        .filter((item) => item.impact === 'critical' || item.impact === 'serious')
        .map((item) => ({
          id: item.id,
          nodes: item.nodes.map((node) => node.html),
        })),
      url,
    ).toEqual([]);
  }
});

test('map is lazy and preserves area URL without refetch loop', async ({ page }) => {
  await fixture(page);
  const maps: string[] = [];
  const searches: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('maplibre')) maps.push(request.url());
    if (request.url().includes('/api/v2/listings/search')) searches.push(decodeURIComponent(request.url()));
  });
  await page.route('https://tiles.openfreemap.org/**', (route) => route.abort());
  await page.goto('/search');
  await expect(page.getByRole('article')).toHaveCount(24);
  searches.length = 0;
  await page.getByRole('combobox', { name: 'Tìm theo từ khóa hoặc địa điểm' }).fill('Cầu Giấy');
  await page.getByRole('option', { name: 'Khu vực: Cầu Giấy, Hà Nội', exact: true }).click();
  await expect(page).toHaveURL(/bbox=105\.76(%2C|,)21\.01(%2C|,)105\.83(%2C|,)21\.08/);
  await expect(page.getByText('Trong khu vực:')).toContainText('Cầu Giấy, Hà Nội');
  await expect.poll(() => searches.filter((url) => url.includes('bbox=105.76,21.01')).length).toBe(1);
  // List mode never downloads MapLibre.
  expect(maps).toEqual([]);
  // Only requests made after the area was chosen must keep it (the first, area-less load came before).
  const beforeArea = searches.findIndex((url) => url.includes('bbox=105.76,21.01'));
  searches.splice(0, beforeArea);
  await page.getByRole('button', { name: 'Bản đồ', exact: true }).click();
  await expect(page).toHaveURL(/view=map/);
  await expect(page.getByRole('region', { name: 'Bản đồ kết quả tìm kiếm' })).toBeVisible();
  await expect.poll(() => maps.length).toBeGreaterThan(0);
  // Switching the view keeps the area and never starts a request loop: the count settles.
  await page.waitForTimeout(1500);
  const settled = searches.length;
  await page.waitForTimeout(1500);
  expect(searches.length).toBe(settled);
  expect(searches.every((url) => url.includes('bbox=105.76,21.01'))).toBe(true);
  await page.reload();
  await expect(page.getByRole('button', { name: 'Bỏ khu vực', exact: true })).toBeVisible();
  await expect(page).toHaveURL(/bbox=105\.76/);
  await page.getByRole('button', { name: 'Danh sách', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Bản đồ kết quả tìm kiếm' })).toHaveCount(0);
});
test('login opens a real form and updates the account navigation', async ({ page }) => {
  await fixture(page);
  await page.goto('/');
  if (await page.getByRole('button', { name: 'Mở menu', exact: true }).isVisible()) {
    await page.getByRole('button', { name: 'Mở menu', exact: true }).click();
    await page.getByRole('button', { name: 'Đăng nhập / Đăng ký' }).click();
  } else await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByLabel('Email', { exact: true }).fill('preview@example.test');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('PreviewPassword123');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).last().click();
  await expect(page.getByRole('dialog', { name: 'Đăng nhập', exact: true })).not.toBeVisible();
  const accountMenu = page.getByRole('button', { name: 'Mở menu tài khoản', exact: true });
  await accountMenu.click();
  await expect(accountMenu).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByRole('banner').getByRole('link', { name: /Thông tin cá nhân/ })).toBeVisible();
  await page.keyboard.press('Escape');
  await page.goto('/account');
  await expect(page.getByRole('heading', { name: 'Thông tin cá nhân' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Tài khoản của bạn' })).toBeVisible();
});
test('CMS load failure is visible and retry recovers', async ({ page }) => {
  await fixture(page, 'ADMIN');
  await page.route('**/api/v1/cms/articles', (route) =>
    route.fulfill({ status: 503, json: { title: 'Unavailable', status: 503 } }),
  );
  await page.goto(`${admin}/cms`);
  await expect(page.getByRole('alert')).toContainText('Không thể tải nội dung CMS');
  await page.unroute('**/api/v1/cms/articles');
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('listing metadata is restored when leaving the detail page', async ({ page }) => {
  await fixture(page);
  await page.goto('/search');
  await expect(page.getByRole('article')).toHaveCount(24);
  // The search page sets its own document title (audit useDocumentMeta); capture it once it has rendered.
  await expect(page).not.toHaveTitle(/^BDS WF 2026/);
  const originalTitle = await page.title();
  const originalCanonical = await page
    .locator('link[rel="canonical"]')
    .evaluateAll((nodes) => nodes[0]?.getAttribute('href') ?? null);
  await page
    .getByRole('link', {
      name: `Xem chi tiết: ${listings[0].title}`,
      exact: true,
    })
    .click();
  await expect(page).toHaveTitle(`${listings[0].title} | Nhà Đất Chuẩn`);
  await expect(page.locator('head script[type="application/ld+json"]')).not.toHaveCount(0);
  await page.goBack();
  await expect(page.getByRole('article')).toHaveCount(24);
  await expect(page).toHaveTitle(originalTitle);
  await expect(page.locator('head script[type="application/ld+json"]')).toHaveCount(0);
  const canonical = await page
    .locator('link[rel="canonical"]')
    .evaluateAll((nodes) => nodes[0]?.getAttribute('href') ?? null);
  expect(canonical).toBe(originalCanonical);
});
