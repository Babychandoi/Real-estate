import { expect, test } from '@playwright/test';

test('mở chi tiết từ kết quả tìm kiếm', async ({ page }) => {
  await page.goto('/search', { waitUntil: 'domcontentloaded' });
  const detailLink = page.getByRole('link', { name: /Chi tiết/ }).first();
  await expect(detailLink).toBeVisible();
  await detailLink.click();
  await expect(page).toHaveURL(/\/listings\/[0-9a-f-]+$/);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
});
