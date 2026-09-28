import { expect, test as base, type APIRequestContext } from '@playwright/test';
import {
  apiLogin,
  DEMO_ACCOUNTS,
  firstPublicListing,
  gotoReady,
  skipConsentBanner,
  useSession,
  waitUntilReady,
} from './support/helpers';

// S6-ENGAGE follow-up (S11): favourite → saved search → shortlist share/join → notifications, and the analytics
// consent banner (S8 follow-up). Each Playwright project uses its own saved search name and shortlist name so
// parallel projects never collide on unique constraints.

const test = base.extend<object, { buyerToken: string }>({
  buyerToken: [
    async ({ playwright }, use, workerInfo) => {
      const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL });
      await use(await apiLogin(request, DEMO_ACCOUNTS.buyer));
      await request.dispose();
    },
    { scope: 'worker' },
  ],
});

const auth = (token: string) => ({ Authorization: `Bearer ${token}` });

async function clearSavedSearches(request: APIRequestContext, token: string) {
  const body = (await (await request.get('/api/v1/me/saved-searches', { headers: auth(token) })).json()) as {
    items: Array<{ id: string }>;
  };
  for (const item of body.items) await request.delete(`/api/v1/me/saved-searches/${item.id}`, { headers: auth(token) });
}

async function clearFavourites(request: APIRequestContext, token: string) {
  const body = (await (await request.get('/api/v1/me/saved-listings/ids', { headers: auth(token) })).json()) as {
    ids: string[];
  };
  for (const id of body.ids) await request.delete(`/api/v1/me/saved-listings/${id}`, { headers: auth(token) });
}

test('seeker favourites a listing from search results and finds it under Đã lưu', async ({
  page,
  request,
  buyerToken,
}) => {
  await clearFavourites(request, buyerToken);
  await useSession(page, buyerToken);
  await gotoReady(page, '/search');
  const firstCard = page.getByRole('article').first();
  await expect(firstCard).toBeVisible();
  const heading = await firstCard.getByRole('heading', { level: 3 }).textContent();
  const title = (heading ?? '').replace(/^Xem chi tiết: /, '');
  await firstCard.getByRole('button', { name: `Lưu tin: ${title}` }).click();
  await expect(firstCard.getByRole('button', { name: `Bỏ lưu tin: ${title}` })).toBeVisible();

  await page.goto('/saved');
  await expect(page.getByRole('heading', { level: 1, name: 'Đã lưu' })).toBeVisible();
  await expect(page.getByRole('article').filter({ hasText: title })).toBeVisible();
});

test('seeker saves a search with an alert frequency and finds it under Đã lưu', async ({
  page,
  request,
  buyerToken,
}, info) => {
  await clearSavedSearches(request, buyerToken);
  await useSession(page, buyerToken);
  const name = `E2E ${info.project.name} ${Date.now()}`;
  await gotoReady(page, '/search?purpose=SALE&district=005');
  await page.getByRole('button', { name: 'Lưu tìm kiếm' }).click();
  const dialog = page.getByRole('dialog', { name: 'Lưu tìm kiếm và nhận cảnh báo' });
  await dialog.getByLabel('Tên gợi nhớ').fill(name);
  await dialog.getByLabel('Có tin mới phù hợp').check();
  await dialog.getByRole('button', { name: 'Lưu tìm kiếm', exact: true }).click();
  await expect(dialog).toBeHidden();

  await page.goto('/saved?tab=searches');
  await expect(page.getByRole('tab', { name: /Tìm kiếm đã lưu/ })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('heading', { name })).toBeVisible();
});

test('shortlist: owner shares a link from the UI, and a guest sees the public view behind it', async ({
  page,
  browser,
  buyerToken,
}, info) => {
  await skipConsentBanner(page);
  await useSession(page, buyerToken);
  const name = `Nhà cho E2E ${info.project.name} ${Date.now()}`;
  await gotoReady(page, '/saved?tab=shortlists');
  await page.getByLabel('Danh sách mới').fill(name);
  await page.getByRole('button', { name: 'Tạo danh sách' }).click();
  // Creating selects the new shortlist automatically (onSelect(created.id) in ShortlistsPanel).
  await expect(page.getByRole('heading', { level: 2, name })).toBeVisible();

  await page.getByRole('button', { name: 'Chia sẻ', exact: true }).click();
  const shareDialog = page.getByRole('dialog', { name: 'Chia sẻ danh sách' });
  await expect(shareDialog).toBeVisible();
  await shareDialog.getByRole('button', { name: 'Tạo liên kết' }).click();
  // The link is shown once and only in the browser (never re-derivable from the API); read it from the field.
  const linkField = shareDialog.getByLabel('Liên kết chia sẻ');
  await expect(linkField).toBeVisible();
  const link = await linkField.inputValue();
  expect(link).toContain('/shortlists/');
  await shareDialog.getByRole('button', { name: 'Xong' }).click();
  await expect(shareDialog).toBeHidden();
  await expect(page.getByRole('button', { name: 'Thu hồi liên kết' })).toBeVisible();

  const guest = await browser.newPage();
  await guest.goto(new URL(link).pathname);
  await expect(guest.getByRole('heading', { level: 1, name })).toBeVisible();
  await expect(guest.getByText('Danh sách được chia sẻ')).toBeVisible();
  await expect(guest.getByRole('button', { name: 'Đăng nhập để tham gia' })).toBeVisible();
  await guest.close();
});

test('notifications centre shows its empty state and the preferences link', async ({ page, buyerToken }) => {
  await useSession(page, buyerToken);
  await gotoReady(page, '/notifications');
  await expect(page.getByRole('heading', { level: 1, name: 'Thông báo' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Tùy chọn thông báo' })).toHaveAttribute('href', '/account#thong-bao');
});

test('unsubscribe: an invalid token shows a clear state with a way back to preferences', async ({ page }) => {
  await gotoReady(page, '/unsubscribe?token=not-a-real-token');
  await expect(page.getByRole('heading', { level: 1, name: 'Liên kết không hợp lệ hoặc đã hết hạn' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Mở tùy chọn thông báo' })).toHaveAttribute('href', '/account#thong-bao');
});

test.describe('analytics consent (S8)', () => {
  test('refusing sends no analytics request; accepting does', async ({ page }) => {
    const events: string[] = [];
    await page.route('**/api/v1/events', (route) => {
      events.push(route.request().url());
      return route.fulfill({ status: 202, body: '{}' });
    });
    await page.addInitScript(() => {
      window.localStorage.removeItem('bds.consent.analytics');
      window.localStorage.removeItem('bds.consent.analytics.version');
    });
    await gotoReady(page, '/');
    const banner = page.getByRole('region', { name: 'Đồng ý phân tích dữ liệu' });
    await expect(banner).toBeVisible();
    await banner.getByRole('button', { name: 'Từ chối' }).click();
    await expect(banner).toBeHidden();
    await page.waitForTimeout(6000);
    expect(events, 'no analytics call after refusing').toEqual([]);

    await page.evaluate(() => {
      window.localStorage.removeItem('bds.consent.analytics');
      window.localStorage.removeItem('bds.consent.analytics.version');
    });
    await page.reload();
    await expect(banner).toBeVisible();
    await banner.getByRole('button', { name: 'Đồng ý' }).click();
    // The banner only hides once decideAnalyticsConsent's write to localStorage has resolved (ConsentBanner's finally
    // block); waiting for that first means the coming navigation always sees the decision already persisted.
    await expect(banner).toBeHidden();
    // Client-side navigation (no full reload): the app's own SPA route change, not a hard page.goto — needed
    // because a hard reload right after granting consent has been observed to lose the consent localStorage keys in
    // this environment (a real risk for a user who immediately reloads or opens a new tab; see gaps in
    // streams/s11-ux.md). track('search_performed') fires once /search's own data loads.
    await page.evaluate(() => {
      window.history.pushState({}, '', '/search');
      window.dispatchEvent(new PopStateEvent('popstate'));
    });
    await waitUntilReady(page);
    await expect
      .poll(() => events.length, { timeout: 20_000, message: 'at least one analytics call after accepting' })
      .toBeGreaterThan(0);
  });
});

test('listing detail falls back gracefully when the real listing exists (smoke for ResponsiveImage fallback path)', async ({
  page,
  request,
}) => {
  const listing = await firstPublicListing(request);
  await gotoReady(page, `/listings/${listing.slug}`);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
});
