import { expect, test as base, type Page } from '@playwright/test';
import { apiLogin, DEMO_ACCOUNTS, useSession } from './support/helpers';

// F01.6: minimal signed-in smoke flows for a buyer and a broker. The session comes from an API login injected into
// sessionStorage (like a returning visitor), so the UI login form is not what is under test here. Each test loads
// one page and moves on with in-app links: /auth/* is rate limited per IP, so full reloads are kept to a minimum.

// One API login per role and worker, reused by its tests.
const test = base.extend<object, { buyerToken: string; brokerToken: string }>({
  buyerToken: [
    async ({ playwright }, use, workerInfo) => {
      const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL });
      await use(await apiLogin(request, DEMO_ACCOUNTS.buyer));
      await request.dispose();
    },
    { scope: 'worker' },
  ],
  brokerToken: [
    async ({ playwright }, use, workerInfo) => {
      const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL });
      await use(await apiLogin(request, DEMO_ACCOUNTS.broker));
      await request.dispose();
    },
    { scope: 'worker' },
  ],
});

async function openAccountMenu(page: Page) {
  await page.getByRole('button', { name: 'Mở menu tài khoản' }).click();
}

/** Client-side navigation (no reload, so no extra /auth/me request). */
async function navigateInApp(page: Page, path: string) {
  await page.evaluate((target) => {
    window.history.pushState({}, '', target);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, path);
}

test.describe('buyer (demo.user)', () => {
  test('reaches their inquiries and profile but not the posting pages', async ({ page, buyerToken }) => {
    await useSession(page, buyerToken);
    await page.goto('/my-inquiries');
    await expect(page.getByRole('heading', { level: 1, name: 'Tin đã liên hệ' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Đăng tin' })).toHaveCount(0);

    await openAccountMenu(page);
    await expect(page.getByRole('link', { name: 'Kho tin của tôi' })).toHaveCount(0);
    await page
      .getByRole('link', { name: /Thông tin cá nhân/ })
      .first()
      .click();
    await expect(page.getByRole('heading', { level: 1, name: 'Thông tin cá nhân' })).toBeVisible();

    await navigateInApp(page, '/listings/new');
    await expect(page).toHaveURL(/\/my-inquiries$/);
  });
});

test.describe('broker (demo.broker)', () => {
  test('manages listings, leads and the broker workspace', async ({ page, brokerToken }) => {
    await useSession(page, brokerToken);
    await page.goto('/my-listings');
    await expect(page.getByRole('heading', { level: 1, name: 'Quản lý kho tin đăng' })).toBeVisible();

    await openAccountMenu(page);
    await page.getByRole('link', { name: 'Khách quan tâm' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Hộp thư khách quan tâm' })).toBeVisible();

    await navigateInApp(page, '/broker/workspace');
    await expect(page).toHaveURL(/\/broker\/workspace$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Hiệu suất từ dữ liệu thật' })).toBeVisible();

    await navigateInApp(page, '/listings/new');
    await expect(page).toHaveURL(/\/listings\/new$/);
    await expect(page.getByRole('heading', { name: 'Vui lòng đăng nhập' })).toHaveCount(0);
    // The page renders one of its two legitimate authenticated states (NIT), not just "no login prompt": the
    // create-listing wizard once the broker is eKYC-verified, or the eKYC gate otherwise. Either proves the route
    // actually rendered content instead of, say, a silent blank page or an unrelated error state.
    await expect(
      page.getByRole('heading', { name: /Soạn thảo & Đăng tin|Xác minh danh tính trước khi đăng tin/ }),
    ).toBeVisible();
  });
});
