import { expect, test } from '@playwright/test';

test('mở chi tiết từ kết quả tìm kiếm', async ({ page }) => {
  await page.goto('/search', { waitUntil: 'domcontentloaded' });
  const detailLink = page.getByRole('link', { name: /^Xem chi tiết:/ }).first();
  await expect(detailLink).toBeVisible();
  const detailPath = await detailLink.getAttribute('href');
  expect(detailPath).toMatch(/^\/listings\/[^/?#]+$/);
  await detailLink.click();
  await expect.poll(() => new URL(page.url()).pathname).toBe(detailPath);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
});
