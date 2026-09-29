import { expect, test } from '@playwright/test';
import {
  apiLogin,
  axeViolations,
  DEMO_ACCOUNTS,
  demoPassword,
  firstPublicListing,
  gotoReady,
  horizontalOverflow,
  skipConsentBanner,
  useSession,
} from './support/helpers';

// DS-06: these widths close the gaps between the existing 320/768/1440 projects.
// A 320 CSS-pixel viewport also exercises the reflow expected at 200% zoom on a 640 CSS-pixel window.
for (const { name, width, height } of [
  { name: '360px mobile', width: 360, height: 800 },
  { name: '1024px tablet', width: 1024, height: 768 },
  { name: '320px reflow equivalent to 200% zoom at 640px', width: 320, height: 800 },
]) {
  test(`home, search and listing fit ${name}`, async ({ page, request }) => {
    const listing = await firstPublicListing(request);
    await page.setViewportSize({ width, height });
    for (const path of ['/', '/search', `/listings/${listing.slug}`]) {
      await gotoReady(page, path);
      expect(await horizontalOverflow(page), `${path} at ${name}`).toEqual([]);
    }
  });
}

test('buyer inquiries and profile pass axe at mobile and desktop widths', async ({ page, request }) => {
  const token = await apiLogin(request, DEMO_ACCOUNTS.buyer);
  await useSession(page, token);
  await skipConsentBanner(page);
  for (const width of [360, 1024]) {
    await page.setViewportSize({ width, height: 800 });
    for (const path of ['/my-inquiries', '/account']) {
      await gotoReady(page, path);
      expect.soft(await axeViolations(page), `${path} axe at ${width}px`).toEqual([]);
      expect.soft(await horizontalOverflow(page), `${path} overflow at ${width}px`).toEqual([]);
    }
  }
});

test('moderation desk passes axe at mobile and desktop widths', async ({ page, request }) => {
  const response = await request.post('/api/v1/auth/admin/login', {
    data: { email: 'demo.moderator@bds.local', password: demoPassword() },
  });
  expect(response.ok(), `moderator login answered ${response.status()}`).toBe(true);
  const { accessToken } = (await response.json()) as { accessToken: string };
  await useSession(page, accessToken);
  await skipConsentBanner(page);
  for (const width of [360, 1024]) {
    await page.setViewportSize({ width, height: 800 });
    await page.goto('/2026/nhadatchuan/admin/moderation');
    await expect(page.getByRole('heading', { level: 1, name: 'Kiểm duyệt tin đăng' })).toBeVisible();
    expect.soft(await axeViolations(page), `moderation axe at ${width}px`).toEqual([]);
    expect.soft(await horizontalOverflow(page), `moderation overflow at ${width}px`).toEqual([]);
  }
});
