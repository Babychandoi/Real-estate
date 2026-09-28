import { expect, test, type Page } from '@playwright/test';

/*
 * F14.3 (S1-MEDIA): opening a listing detail downloads resized WebP variants picked by srcset/sizes — never the
 * original of any of its 20 photos — and the lightbox keeps using variants. The API is mocked so the spec needs no
 * seeded MinIO; the image bytes are a 1×1 PNG whatever the URL says.
 */
const PIXEL = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);
const WIDTHS = [320, 640, 960, 1600];
const key = (i: number) => `8f0c4b8e-1111-4a5b-9c3d-${String(i).padStart(12, '0')}`;
const image = (i: number) => ({
  url: `/api/v1/public/media/${key(i)}.jpg`,
  width: 2048,
  height: 1365,
  srcset: WIDTHS.map((width) => ({ url: `/api/v1/public/media/${key(i)}__w${width}.webp`, width })),
  placeholder: { dominantColor: '#c8b8a0', lqip: null },
});
const images = Array.from({ length: 20 }, (_, i) => image(i + 1));
const listing = {
  id: '5b1d6f0e-2f55-4c1e-9d0a-2a6f5d7e8c01',
  slug: 'can-ho-2pn-cau-giay-2601',
  title: 'Căn hộ 2PN Cầu Giấy view thoáng',
  purpose: 'SALE',
  propertyType: 'APARTMENT',
  price: { amount: 3_950_000_000, currency: 'VND', period: null },
  unitPrice: { amount: 56_428_571, per: 'M2' },
  areaM2: 70,
  bedrooms: 2,
  bathrooms: 2,
  location: {
    districtCode: '005',
    districtName: 'Cầu Giấy',
    addressSummary: 'Cầu Giấy, Hà Nội',
    precision: 'APPROXIMATE',
  },
  image: images[0],
  imageCount: 20,
  trust: { identity: { status: 'VERIFIED' }, listing: { status: 'CHECKED' }, ownership: { status: 'NOT_SUBMITTED' } },
  freshness: { publishedAt: '2026-09-01T03:00:00Z', updatedAt: '2026-09-01T03:00:00Z' },
  seller: { id: 'owner-1', name: 'Môi giới Demo', role: 'BROKER' },
  project: null,
  priceChange: null,
  description: 'Căn hộ hai phòng ngủ.',
  images,
  facts: { bedrooms: 2, bathrooms: 2 },
  rentTerms: null,
  legal: null,
  furnishing: null,
  revisionNumber: 1,
};

async function mockApi(page: Page): Promise<string[]> {
  const requested: string[] = [];
  // Only the backend (/api/…): the dev server also serves modules such as /app/shared/api/client.ts.
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const url = new URL(route.request().url());
      const path = url.pathname;
      if (path.startsWith('/api/v1/public/media/')) {
        requested.push(path);
        return route.fulfill({ status: 200, contentType: 'image/png', body: PIXEL });
      }
      if (path === `/api/v2/listings/${listing.slug}`) return route.fulfill({ json: listing });
      if (path.endsWith('/price-history'))
        return route.fulfill({ json: { listingId: listing.id, purpose: 'SALE', points: [] } });
      if (path.endsWith('/similar')) return route.fulfill({ json: [] });
      return route.fulfill({
        status: 404,
        contentType: 'application/problem+json',
        json: { title: 'Not found', status: 404, detail: 'Không tìm thấy' },
      });
    },
  );
  return requested;
}

for (const viewport of [
  { width: 1440, height: 1000, hero: 640 },
  { width: 390, height: 844, hero: 640 },
]) {
  test(`detail at ${viewport.width}px loads only variants, the hero sized for the slot`, async ({ browser }) => {
    const context = await browser.newContext({ viewport, deviceScaleFactor: 1 });
    const page = await context.newPage();
    const requested = await mockApi(page);
    await page.goto(`/listings/${listing.slug}`);
    const hero = page.getByRole('img', { name: `Ảnh 1/20 của ${listing.title}` });
    await expect(hero).toBeVisible();
    await expect.poll(() => hero.evaluate((img: HTMLImageElement) => img.complete && img.currentSrc)).toBeTruthy();

    const heroSrc = await hero.evaluate((img: HTMLImageElement) => new URL(img.currentSrc).pathname);
    expect(heroSrc).toBe(`/api/v1/public/media/${key(1)}__w${viewport.hero}.webp`);
    expect(requested.length).toBeGreaterThan(0);
    expect(
      requested.filter((path) => !/__w\d+\.webp$/.test(path)),
      'no original is downloaded',
    ).toEqual([]);
    // Lazy photos beyond the visible grid are not fetched on open (the 20 originals certainly not).
    expect(requested.length).toBeLessThanOrEqual(5);

    await page.getByRole('button', { name: 'Xem tất cả 20 ảnh' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Ảnh 1/20')).toBeVisible();
    await page.keyboard.press('ArrowRight');
    await expect(dialog.getByText('Ảnh 2/20')).toBeVisible();
    const large = dialog.getByRole('img', { name: `Ảnh 2/20 của ${listing.title}` });
    await expect.poll(() => large.evaluate((img: HTMLImageElement) => img.currentSrc)).toContain('__w');
    expect(
      requested.filter((path) => !/__w\d+\.webp$/.test(path)),
      'lightbox keeps using variants',
    ).toEqual([]);
    await context.close();
  });
}
