import { expect, test } from '@playwright/test';
import { escapeRegExp, firstPublicListing, gotoReady, UUID_PATTERN, waitUntilReady } from './support/helpers';

// F01.1: navigation follows the real routes — cards link to the canonical slug URL /listings/<slug>.

test('opens a listing from the search results on its canonical slug URL', async ({ page }) => {
  await gotoReady(page, '/search');

  const card = page.getByRole('link', { name: /^Xem chi tiết: / }).first();
  await expect(card).toBeVisible();
  const title = (await card.getAttribute('aria-label'))!.replace(/^Xem chi tiết: /, '');
  const href = (await card.getAttribute('href'))!;
  expect(href).toMatch(/^\/listings\/[a-z0-9-]+$/);
  expect(href).not.toMatch(UUID_PATTERN);

  await card.click();
  await expect(page).toHaveURL(new RegExp(`${escapeRegExp(href)}$`));
  await waitUntilReady(page);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', new RegExp(`${escapeRegExp(href)}$`));
  await expect(page).toHaveTitle(`${title} | Nhà Đất Chuẩn`);
});

test('rewrites a legacy UUID link to the canonical slug without losing the page', async ({ page, request }) => {
  const listing = await firstPublicListing(request);
  await gotoReady(page, `/listings/${listing.id}`);

  await expect(page).toHaveURL(new RegExp(`/listings/${escapeRegExp(listing.slug)}$`));
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(listing.title);
});

test('leaving a listing removes its canonical link and structured data', async ({ page, request }) => {
  const listing = await firstPublicListing(request);
  await gotoReady(page, `/listings/${listing.slug}`);
  await expect(page.locator('link[rel="canonical"]')).toHaveCount(1);
  await expect(page.locator('script[type="application/ld+json"]')).toHaveCount(1);

  await page.getByRole('link', { name: 'Quay lại danh sách' }).click();
  await expect(page).toHaveURL(/\/search$/);
  await waitUntilReady(page);
  await expect(page.locator('link[rel="canonical"]')).toHaveCount(0);
  await expect(page.locator('script[type="application/ld+json"]')).toHaveCount(0);
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', /^index,follow/);
});

test('an unknown listing shows a not-found page marked noindex', async ({ page }) => {
  await gotoReady(page, '/listings/tin-khong-ton-tai-e2e');
  await expect(page.getByRole('heading', { name: 'Không tìm thấy bất động sản' })).toBeVisible();
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex');
});

test('an unknown route shows the 404 page inside the layout', async ({ page }) => {
  await gotoReady(page, '/duong-dan-khong-ton-tai-e2e');
  await expect(page.getByRole('heading', { level: 1, name: 'Không tìm thấy trang' })).toBeVisible();
  await expect(page.getByRole('main')).toHaveCount(1);
});

test('loads the next search cursor without duplicate cards', async ({ page }) => {
  await gotoReady(page, '/search');
  const cards = page.getByRole('link', { name: /^Xem chi tiết: / });
  await expect(cards).toHaveCount(24);
  await page.getByRole('button', { name: 'Xem thêm' }).click();
  await expect(cards).toHaveCount(46);
  const destinations = await cards.evaluateAll((links) => links.map((link) => link.getAttribute('href')));
  expect(new Set(destinations).size).toBe(destinations.length);
  await expect(page.getByRole('button', { name: 'Xem thêm' })).toHaveCount(0);
});
