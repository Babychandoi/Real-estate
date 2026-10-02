import { expect, test, type APIRequestContext, type Page, type Request } from '@playwright/test';
import { gotoReady, waitUntilReady } from './support/helpers';

// R-2 (frontend side): search, filters, map and pagination tell one story.
//   1. URL ↔ filters: a URL with filters restores every control, a change made in the filter sheet is written back
//      to the URL in canonical form, a reload restores it, and the API request carries exactly the URL's filters;
//   2. "Xem thêm" pages beyond 100 results without duplicates and stops at the API's total;
//   3. the map's "N tin trong vùng này" equals the list total for the same filters (and the two APIs agree);
//   4. rent prices say "/tháng" in the list, on the map sheet's card and on the detail page; sale prices never do.
// Needs more than 100 public SALE listings: the UAT seed has 46, so the stack is started with
// tests/e2e/fixtures/search-volume.sql (E2E_SQL_AFTER_SEED_FILES in scripts/e2e-local.sh; CI seeds it). Without it test 2 fails with
// that hint instead of passing on too little data.

test.use({ actionTimeout: 20_000 });

test.beforeEach(({ browserName }, testInfo) => {
  void browserName;
  test.skip(testInfo.project.name !== 'chromium-1440', 'one desktop project; mutation-free but data heavy');
});

interface Envelope {
  items: Array<{ id: string; slug: string; price: { amount: number; period: string | null } | null }>;
  pageInfo: { hasNext: boolean; nextCursor: string | null };
  total: { value: number; relation: 'eq' | 'gte' } | null;
}

async function api(request: APIRequestContext, path: string): Promise<Envelope> {
  const response = await request.get(path);
  expect(response.ok(), `${path} answered ${response.status()}`).toBe(true);
  return response.json();
}

const numberIn = (text: string | null) => Number((text ?? '').replace(/[^\d]/g, ''));

/** Filter keys the page owns in the URL (view/place are presentation, not filters). */
function filterParams(search: URLSearchParams): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [key, value] of search) {
    if (['view', 'place', 'size', 'cursor'].includes(key)) continue;
    out[key] = out[key] ? `${out[key]},${value}` : value;
  }
  return out;
}

function lastSearchRequest(page: Page): () => Request | undefined {
  let last: Request | undefined;
  page.on('request', (request) => {
    if (request.url().includes('/api/v2/listings/search')) last = request;
  });
  return () => last;
}

test('URL ↔ filters round trip: controls restore from the URL, edits write back, the API gets the same filters', async ({
  page,
}) => {
  const lastRequest = lastSearchRequest(page);
  const start = '/search?purpose=RENT&type=APARTMENT&priceMin=5000000&priceMax=30000000&bedsMin=2&sort=PRICE_ASC';
  await gotoReady(page, start);

  // URL → controls.
  await expect(page.getByRole('group', { name: 'Nhu cầu' }).getByRole('button', { name: 'Thuê nhà' })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await expect(page.getByRole('combobox', { name: 'Sắp xếp kết quả' })).toHaveValue('PRICE_ASC');
  const filters = page.getByRole('button', { name: /^Bộ lọc/ });
  await expect(filters).toHaveText(/\(\d+\)/);
  await filters.click();
  const sheet = page.getByRole('dialog', { name: 'Bộ lọc' });
  await expect(sheet).toBeVisible();
  await expect(sheet.getByRole('group', { name: 'Loại hình' }).getByRole('button', { pressed: true })).toHaveCount(1);
  await expect(
    sheet.getByRole('group', { name: 'Số phòng ngủ tối thiểu' }).getByRole('button', { pressed: true }),
  ).toHaveCount(1);
  const priceFrom = sheet.getByLabel(/^Từ \(/);
  const priceTo = sheet.getByLabel(/^Đến \(/);
  await expect(priceFrom).not.toHaveValue('');
  await expect(priceTo).not.toHaveValue('');

  // The API request carries the URL's filters, nothing more and nothing less.
  const urlFilters = () => filterParams(new URL(page.url()).searchParams);
  const apiFilters = () => filterParams(new URL(lastRequest()!.url()).searchParams);
  expect(apiFilters()).toEqual(urlFilters());

  // Controls → URL: change the minimum bedrooms and the upper price, apply.
  await sheet.getByRole('group', { name: 'Số phòng ngủ tối thiểu' }).getByRole('button', { name: /^3/ }).click();
  await priceTo.fill('25');
  await sheet.getByRole('button', { name: /^Xem kết quả/ }).click();
  await expect(sheet).toBeHidden();
  await expect(page).toHaveURL(/bedsMin=3/);
  await expect(page).toHaveURL(/priceMax=25000000(&|$)/);
  await waitUntilReady(page);
  await expect.poll(() => apiFilters()).toEqual(urlFilters());
  const heading = await page.locator('#search-results-heading').textContent();

  // Reload: same controls, same result heading.
  await page.reload();
  await waitUntilReady(page);
  await expect(page).toHaveURL(/bedsMin=3/);
  await expect(page.locator('#search-results-heading')).toHaveText(heading ?? '');
  expect(apiFilters()).toEqual(urlFilters());

  // Back restores the previous filter set (an intentional change adds a history entry).
  await page.goBack();
  await expect(page).toHaveURL(/bedsMin=2/);
  await waitUntilReady(page);
  await expect.poll(() => apiFilters()).toEqual(urlFilters());
});

test('"Xem thêm" pages past 100 results with no duplicates and stops at the total', async ({ page, request }) => {
  test.setTimeout(240_000);
  const first = await api(request, '/api/v2/listings/search?purpose=SALE&size=24');
  expect(
    first.total?.value ?? 0,
    'needs > 100 public SALE listings: start the stack with E2E_SQL_AFTER_SEED_FILES=frontend/tests/e2e/fixtures/search-volume.sql',
  ).toBeGreaterThan(100);
  expect(first.total?.relation).toBe('eq');
  const total = first.total!.value;

  await gotoReady(page, '/search?purpose=SALE');
  const cards = page.getByRole('link', { name: /^Xem chi tiết: / });
  const more = page.getByRole('button', { name: 'Xem thêm' });
  await expect(page.locator('#search-results-heading')).toContainText(`${total.toLocaleString('vi-VN')} tin`);
  let loaded = await cards.count();
  expect(loaded).toBe(24);
  while (await more.isVisible()) {
    await more.click();
    await expect.poll(() => cards.count(), { timeout: 30_000 }).toBeGreaterThan(loaded);
    loaded = await cards.count();
    const summary = page.getByRole('status').filter({ hasText: /Đang hiển thị|Đã hiển thị tất cả/ });
    await expect(summary).toContainText(loaded.toLocaleString('vi-VN'));
  }
  const hrefs = await cards.evaluateAll((links) => links.map((link) => link.getAttribute('href')));
  expect(hrefs.length, 'every result was reached through "Xem thêm"').toBe(total);
  expect(hrefs.length).toBeGreaterThan(100);
  expect(new Set(hrefs).size, 'no listing is shown twice').toBe(hrefs.length);
  await expect(page.getByRole('status').filter({ hasText: 'Đã hiển thị tất cả' })).toContainText(
    total.toLocaleString('vi-VN'),
  );
});

test('the map count equals the list total for the same filters', async ({ page, request }) => {
  test.setTimeout(180_000);
  // Same filters through both APIs: identical totals.
  const bbox = '105.70,20.90,105.95,21.10';
  const search = await api(request, `/api/v2/listings/search?purpose=SALE&bbox=${bbox}&size=1`);
  const mapResponse = await request.get(`/api/v2/listings/map?purpose=SALE&bbox=${bbox}&zoom=12`);
  expect(mapResponse.ok()).toBe(true);
  const map = (await mapResponse.json()) as { total: { value: number } | null };
  expect(map.total?.value, 'map API total vs search API total for one bbox').toBe(search.total?.value);

  // In the page: pan the map (a user move writes the viewport into the filters), then both counts agree.
  await gotoReady(page, '/search?purpose=SALE&view=split');
  const region = page.getByRole('region', { name: 'Bản đồ kết quả tìm kiếm' });
  await expect(region).toBeVisible();
  const mapStatus = page.locator('[role="region"][aria-label="Bản đồ kết quả tìm kiếm"] + [role="status"]');
  await expect(mapStatus).toContainText('tin trong vùng này', { timeout: 30_000 });
  const box = (await region.boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2 + 60, box.y + box.height / 2 + 30, { steps: 8 });
  await page.mouse.up();
  await expect(page).toHaveURL(/bbox=/);
  await waitUntilReady(page);
  await expect(mapStatus).toContainText('tin trong vùng này', { timeout: 30_000 });
  const heading = page.locator('#search-results-heading');
  await expect
    .poll(async () => numberIn(await heading.textContent()) - numberIn(await mapStatus.textContent()), {
      timeout: 30_000,
      message: 'list total minus map count, same filters and viewport',
    })
    .toBe(0);
  expect(numberIn(await mapStatus.textContent())).toBeGreaterThan(0);
});

test('rent shows "/tháng" in the list and on the detail page; sale never does', async ({ page, request }) => {
  const rent = await api(request, '/api/v2/listings/search?purpose=RENT&size=24');
  expect(rent.items.length).toBeGreaterThan(0);
  expect(rent.items.every((item) => item.price?.period === 'MONTH')).toBe(true);

  await gotoReady(page, '/search?purpose=RENT');
  const prices = page.locator('article [data-price]');
  await expect(prices.first()).toBeVisible();
  const texts = await prices.allTextContents();
  expect(texts.length).toBeGreaterThan(0);
  for (const text of texts) expect(text, 'rent card price').toMatch(/\/tháng$/);

  await gotoReady(page, `/listings/${rent.items[0].slug}`);
  await expect(page.locator('main [data-price]').first()).toContainText('/tháng');

  await gotoReady(page, '/search?purpose=SALE');
  for (const text of await page.locator('article [data-price]').allTextContents()) {
    expect(text, 'sale card price').not.toContain('/tháng');
  }
});
