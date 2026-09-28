import { expect, test as base } from '@playwright/test';
import { apiLogin, DEMO_ACCOUNTS, useSession, waitUntilReady } from './support/helpers';

// S3a-SUPPLY: a poster writes a listing through the four steps with autosave, a reload keeps the draft, the draft is
// submitted; my-listings has status tabs with counts and server paging; "xác nhận còn hàng" on a published listing.
// The session is an API login (demo.broker) reused per worker; /auth/* is rate limited per IP.

const test = base.extend<object, { posterToken: string }>({
  posterToken: [
    async ({ playwright }, use, workerInfo) => {
      const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL });
      await use(await apiLogin(request, DEMO_ACCOUNTS.broker));
      await request.dispose();
    },
    { scope: 'worker' },
  ],
});

test.describe.configure({ mode: 'serial' });

test('posts a listing in four steps with autosave, keeps the draft on reload and submits it', async ({
  page,
  posterToken,
}, info) => {
  await useSession(page, posterToken);
  const title = `Căn hộ kiểm thử E2E ${info.project.name} ${Date.now()}`;
  await page.goto('/listings/new');
  await expect(page.getByRole('heading', { level: 1, name: 'Đăng tin mới' })).toBeVisible();
  await expect(page.getByText(/Bạn đăng với vai trò/)).toBeVisible();

  // Field errors appear next to the field when trying to continue.
  await page.getByRole('button', { name: /Tiếp tục: Vị trí/ }).click();
  await expect(page.getByText('Tiêu đề cần ít nhất 10 ký tự.')).toBeVisible();

  await page.getByLabel('Tiêu đề').fill(title);
  await page.getByLabel('Giá bán (VNĐ)').fill('3950000000');
  await expect(page.getByText('= 3,95 tỷ')).toBeVisible();
  await page.getByLabel('Diện tích (m²)').fill('72,5');
  await page.getByLabel('Giấy tờ pháp lý').selectOption('PINK_BOOK');

  const status = page.getByTestId('autosave-status');
  await expect(status).toHaveAttribute('data-state', 'saved', { timeout: 15_000 });
  await expect(status).toContainText('Đã lưu lúc');
  await expect(page).toHaveURL(/[?&]edit=[0-9a-f-]{36}/);

  await page.reload();
  await waitUntilReady(page);
  await expect(page.getByLabel('Tiêu đề')).toHaveValue(title);
  await expect(page.getByLabel('Giá bán (VNĐ)')).toHaveValue('3.950.000.000');
  await expect(page.getByRole('heading', { level: 1, name: 'Sửa tin đăng' })).toBeVisible();

  await page.getByRole('button', { name: /Tiếp tục: Vị trí/ }).click();
  await expect(page.getByRole('heading', { level: 2, name: 'Bước 2: Vị trí' })).toBeFocused();
  await page.getByLabel('Mã quận/huyện').fill('005');
  await page.getByLabel('Địa chỉ hiển thị').fill('Cầu Giấy, Hà Nội');
  await page.getByRole('button', { name: /Tiếp tục: Ảnh/ }).click();
  await page.getByRole('button', { name: /Tiếp tục: Xem trước/ }).click();

  const preview = page.getByTestId('listing-preview');
  await expect(preview.getByRole('heading', { name: title })).toBeVisible();
  await expect(preview).toContainText('3,95 tỷ');
  await expect(preview).toContainText('Pháp lý: Sổ hồng');
  await expect(page.getByTestId('quality-checklist')).toContainText('Có ít nhất 5 ảnh');

  await page.getByRole('button', { name: 'Gửi duyệt' }).click();
  await expect(page.getByTestId('submit-success')).toBeVisible();

  await page.getByRole('button', { name: 'Về Tin đăng của tôi' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Tin đăng của tôi' })).toBeVisible();
  const pendingTab = page.getByRole('tab', { name: /Chờ duyệt/ });
  await pendingTab.click();
  await expect(page).toHaveURL(/status=PENDING_REVIEW/);
  await expect(page.getByTestId('my-listing').filter({ hasText: title })).toBeVisible();
});

test('my-listings pages on the server and confirms availability', async ({ page, posterToken }) => {
  await useSession(page, posterToken);
  await page.goto('/my-listings');
  await waitUntilReady(page);
  const allTab = page.getByRole('tab', { name: /Tất cả/ });
  await expect(allTab).toHaveAttribute('aria-selected', 'true');
  const total = Number((await allTab.textContent())?.replace(/\D/g, '') ?? '0');
  expect(total).toBeGreaterThan(0);
  await expect(page.getByTestId('my-listing')).toHaveCount(Math.min(total, 10));
  if (total > 10) {
    const first = await page.getByTestId('my-listing').first().getAttribute('data-listing-id');
    await page.getByRole('button', { name: 'Trang 2' }).click();
    await expect(page).toHaveURL(/page=1/);
    await expect(page.getByTestId('my-listing').first()).not.toHaveAttribute('data-listing-id', first ?? '');
  }

  const activeLoaded = page.waitForResponse((r) => r.url().includes('/api/v2/me/listings?status=ACTIVE') && r.ok());
  await page.getByRole('tab', { name: /Đang hiển thị/ }).click();
  await activeLoaded;
  await expect(page).toHaveURL(/status=ACTIVE/);
  await expect(page.locator('ul[aria-busy="true"]')).toHaveCount(0);
  await expect(page.getByTestId('my-listing').first()).toContainText('Đang hiển thị');
  const id = await page.getByTestId('my-listing').first().getAttribute('data-listing-id');
  const listing = page.locator(`[data-listing-id="${id}"]`);
  await listing.getByRole('button', { name: 'Xác nhận còn hàng' }).click();
  await expect(page.getByText('Đã xác nhận còn hàng thêm 45 ngày.')).toBeVisible();
  await expect(listing).toContainText(/còn 4[45] ngày/);
});
