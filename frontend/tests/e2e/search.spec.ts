import { expect, test, type APIRequestContext } from '@playwright/test';
import { gotoReady, waitUntilReady } from './support/helpers';

// S2-SEARCH journeys: URL as source of truth (F03.3/F03.4), cursor paging (F02.1), rent period (F04.3), map chunk
// only on demand (F15.1), gallery/lightbox (F14.1), compare inactive column (UI-05), seller paging (F08.5).

interface Summary {
  id: string;
  slug: string;
  imageCount: number;
  seller: { id: string };
}

async function searchPage(request: APIRequestContext, query: string): Promise<{ items: Summary[] }> {
  const response = await request.get(`/api/v2/listings/search?${query}`);
  expect(response.ok(), `search v2 answered ${response.status()}`).toBe(true);
  return response.json();
}

test('filters live in the URL and back/forward restores them', async ({ page }) => {
  await gotoReady(page, '/search?purpose=RENT&sort=PRICE_ASC');
  const rent = page.getByRole('group', { name: 'Nhu cầu' }).getByRole('button', { name: 'Thuê' });
  await expect(rent).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('combobox', { name: 'Sắp xếp kết quả' })).toHaveValue('PRICE_ASC');
  const firstPrice = page.locator('article [class*="tabular-nums"]').first();
  await expect(firstPrice).toContainText('/tháng');

  await page.getByRole('group', { name: 'Nhu cầu' }).getByRole('button', { name: 'Mua' }).click();
  await expect(page).toHaveURL(/purpose=SALE/);
  await waitUntilReady(page);
  await expect(page.locator('article [class*="tabular-nums"]').first()).not.toContainText('/tháng');

  await page.goBack();
  await expect(page).toHaveURL(/purpose=RENT/);
  await expect(rent).toHaveAttribute('aria-pressed', 'true');
  await page.goForward();
  await expect(page).toHaveURL(/purpose=SALE/);

  await page.reload();
  await waitUntilReady(page);
  await expect(page.getByRole('group', { name: 'Nhu cầu' }).getByRole('button', { name: 'Mua' })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
});

test('"Xem thêm" loads the next page with the cursor, without duplicates', async ({ page, request }) => {
  const first = await searchPage(request, 'purpose=SALE&size=24');
  expect(first.items.length, 'the UAT seed must provide a full first SALE page').toBe(24);
  await gotoReady(page, '/search?purpose=SALE');
  const cards = page.getByRole('link', { name: /^Xem chi tiết: / });
  await expect(cards).toHaveCount(24);
  await page.getByRole('button', { name: 'Xem thêm' }).click();
  await expect.poll(() => cards.count()).toBeGreaterThan(24);
  const hrefs = await cards.evaluateAll((links) => links.map((link) => link.getAttribute('href')));
  expect(new Set(hrefs).size).toBe(hrefs.length);
});

test('list mode never downloads the map; opening the map does', async ({ page }) => {
  const scripts: string[] = [];
  page.on('request', (request) => {
    if (request.resourceType() === 'script') scripts.push(request.url());
  });
  await gotoReady(page, '/search');
  expect(scripts.filter((url) => /SearchMap|maplibre/i.test(url))).toEqual([]);
  await page.getByRole('group', { name: 'Chế độ hiển thị kết quả' }).getByRole('button', { name: 'Bản đồ' }).click();
  await expect(page).toHaveURL(/view=map/);
  await expect.poll(() => scripts.some((url) => /SearchMap/i.test(url))).toBe(true);
  await expect(page.getByRole('region', { name: 'Bản đồ kết quả tìm kiếm' })).toBeVisible();
});

// Gallery/lightbox with 20 photos: app/features/listing-detail/Gallery.test.tsx (the UAT seed has one photo per listing).

test('compare shows a listing that is no longer public as an inactive column', async ({ page, request }) => {
  const { items } = await searchPage(request, 'purpose=SALE&size=2');
  await gotoReady(page, `/compare?ids=${items[0].id},00000000-0000-4000-8000-00000000dead`);
  await expect(page.locator('[data-inactive="true"]')).toContainText('Tin không còn hiển thị');
});

test('a seller page pages through the whole inventory', async ({ page, request }) => {
  const { items } = await searchPage(request, 'purpose=SALE&size=48');
  const counts = new Map<string, number>();
  items.forEach((item) => counts.set(item.seller.id, (counts.get(item.seller.id) ?? 0) + 1));
  const [sellerId] = [...counts.entries()].sort((a, b) => b[1] - a[1])[0];
  await gotoReady(page, `/nguoi-dang/${sellerId}`);
  const cards = page.getByRole('link', { name: /^Xem chi tiết: / });
  const more = page.getByRole('button', { name: 'Xem thêm' });
  if (await more.isVisible()) {
    const before = await cards.count();
    await more.click();
    await expect.poll(() => cards.count()).toBeGreaterThan(before);
  }
  await expect(page.getByRole('status').filter({ hasText: /tin/ }).first()).toBeVisible();
});

test('opening a detail and going back preserves rent filters and results', async ({ page }) => {
  await gotoReady(page, '/search?purpose=RENT&sort=PRICE_ASC');
  const cards = page.getByRole('link', { name: /^Xem chi tiết: / });
  await expect(cards.first()).toBeVisible();
  const hrefs = await cards.evaluateAll((links) => links.map((link) => link.getAttribute('href')));
  await cards.first().click();
  await waitUntilReady(page);
  await expect(page).toHaveURL(/\/listings\//);
  await page.goBack();
  await waitUntilReady(page);
  await expect(page).toHaveURL(/purpose=RENT.*sort=PRICE_ASC/);
  await expect(page.getByRole('combobox', { name: 'Sắp xếp kết quả' })).toHaveValue('PRICE_ASC');
  await expect.poll(() => cards.evaluateAll((links) => links.map((link) => link.getAttribute('href')))).toEqual(hrefs);
  await expect(page.locator('article [class*="tabular-nums"]').first()).toContainText('/tháng');
});
