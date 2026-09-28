import { expect, test } from '@playwright/test';
import { demoPassword, totpCode } from './support/helpers';

/** Waits until a new 30 s TOTP step begins (Totp.STEP_SECONDS), so a code computed after this never repeats one
 * already consumed a moment earlier (server-side replay protection rejects a step at or before the last used one). */
async function waitForFreshTotpStep(): Promise<void> {
  const msIntoStep = Date.now() % 30_000;
  await new Promise((resolve) => setTimeout(resolve, 30_000 - msIntoStep + 500));
}

// S5B follow-up (S11): browser E2E of staff MFA enroll → sign out → sign in with a TOTP code, and the recovery-code
// path. Unlike every other suite, this one needs APP_SECURITY_MFA_REQUIRED=true (the other suites bypass MFA by
// injecting a session directly, so they need it false) — run with:
//   E2E_MFA_REQUIRED=true scripts/e2e-local.sh --suites mfa --projects chromium-1440
// demo.moderator has no authenticator in the seed, so its first staff sign-in in a fresh database always enrols one;
// test.describe.serial keeps that one account's state consistent across the two tests below.

test.describe.configure({ mode: 'serial' });

test.describe('staff MFA (chromium-1440 only: this suite needs its own MFA-required stack)', () => {
  test.skip(({ browserName }) => browserName !== 'chromium', 'chromium only per wave rules');

  // Captured by the enrol test and reused by the sign-in test below (same worker, serial mode).
  let secret = '';

  test('a staff account with no authenticator enrols one on first sign-in and gets recovery codes', async ({
    page,
  }) => {
    await page.goto('/2026/nhadatchuan/admin/login');
    await page.getByLabel('Email').fill('demo.moderator@bds.local');
    await page.locator('input[name="admin-password"]').fill(demoPassword());
    await page.locator('form').getByRole('button', { name: 'Tiếp tục' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Thiết lập xác thực hai lớp' })).toBeVisible();
    await expect(page.getByText('Tài khoản nhân viên bắt buộc có xác thực hai lớp')).toBeVisible();
    await page.getByRole('button', { name: 'Tạo khóa cho ứng dụng xác thực' }).click();
    secret = (await page.getByLabel('Khóa thiết lập').textContent())!.replace(/\s+/g, '');
    await page.getByLabel('Mã xác thực').fill(totpCode(secret));
    await page.getByRole('button', { name: 'Xác nhận và bật xác thực hai lớp' }).click();

    // Ten recovery codes, shown once; "Vào trang quản trị" lands the now-signed-in moderator on the real app.
    await expect(page.getByRole('heading', { name: /mã khôi phục/i })).toBeVisible();
    await page.getByLabel('Tôi đã lưu các mã này ở nơi an toàn').check();
    await page.getByRole('button', { name: 'Vào trang quản trị' }).click();
    await expect(page).toHaveURL(/\/2026\/nhadatchuan\/admin\/moderation$/);
  });

  test('a wrong TOTP code is rejected with a clear inline message (not a silent accept, not a full restart)', async ({
    page,
  }) => {
    test.skip(!secret, 'depends on the previous test enrolling the authenticator');
    // The enrolment step above just consumed a code for the current 30 s step; a step boundary keeps every later
    // verify code in this file from ever colliding with that one (server-side replay protection).
    await waitForFreshTotpStep();
    await page.goto('/2026/nhadatchuan/admin/login');
    await page.getByLabel('Email').fill('demo.moderator@bds.local');
    await page.locator('input[name="admin-password"]').fill(demoPassword());
    await page.locator('form').getByRole('button', { name: 'Tiếp tục' }).click();
    await expect(page.getByLabel('Mã xác thực')).toBeVisible();

    await page.getByLabel('Mã xác thực').fill('000000');
    await page.locator('form').getByRole('button', { name: 'Xác nhận' }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    // Still on the same challenge, not bounced back to the password step.
    await expect(page.getByLabel('Mã xác thực')).toBeVisible();
  });

  test("signing in again with the enrolled authenticator's current code signs the moderator in", async ({ page }) => {
    test.skip(!secret, 'depends on the first test enrolling the authenticator');
    await page.goto('/2026/nhadatchuan/admin/login');
    await page.getByLabel('Email').fill('demo.moderator@bds.local');
    await page.locator('input[name="admin-password"]').fill(demoPassword());
    await page.locator('form').getByRole('button', { name: 'Tiếp tục' }).click();
    await expect(page.getByLabel('Mã xác thực')).toBeVisible();
    // Computed right before submitting: TOTP is time-boxed (30 s step, ±1 step tolerance).
    await page.getByLabel('Mã xác thực').fill(totpCode(secret));
    await page.locator('form').getByRole('button', { name: 'Xác nhận' }).click();
    await expect(page).toHaveURL(/\/2026\/nhadatchuan\/admin\/moderation$/);
  });
});
