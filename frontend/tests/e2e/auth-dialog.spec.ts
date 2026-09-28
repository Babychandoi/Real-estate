import { expect, test } from '@playwright/test';
import { axeViolations, gotoReady } from './support/helpers';

test('login dialog is keyboard accessible and returns focus when closed', async ({ page }) => {
  await gotoReady(page, '/');
  const trigger = page.getByRole('button', { name: 'Đăng nhập', exact: true });
  // Opened from the keyboard: focus return matters for keyboard users (WebKit does not focus buttons on click).
  await trigger.focus();
  await page.keyboard.press('Enter');

  const dialog = page.getByRole('dialog', { name: 'Đăng nhập' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByLabel('Email')).toBeFocused();
  expect(await axeViolations(page, '[role="dialog"]'), 'axe violations in the dialog').toEqual([]);

  for (let step = 0; step < 12; step += 1) {
    await page.keyboard.press('Tab');
    expect(await dialog.evaluate((element) => element.contains(document.activeElement)), `Tab ${step + 1}`).toBe(true);
  }

  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
  await expect(trigger).toBeFocused();
});

test('registration asks for the account type: seeker, owner or broker', async ({ page }) => {
  await gotoReady(page, '/');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: 'Đăng ký', exact: true }).click();

  const accountType = dialog.getByRole('group', { name: 'Bạn là' });
  await expect(accountType.getByRole('radio')).toHaveCount(3);
  await expect(accountType.getByRole('radio', { name: /Người tìm nhà/ })).toBeChecked();

  await accountType.getByRole('radio', { name: /Chủ nhà/ }).check();
  await expect(accountType.getByRole('radio', { name: /Chủ nhà/ })).toBeChecked();
  await expect(dialog.getByRole('button', { name: 'Đăng ký Chủ nhà' })).toBeVisible();
  await expect(dialog.getByLabel('Họ và tên')).toBeVisible();
  expect(await axeViolations(page, '[role="dialog"]'), 'axe violations in the register form').toEqual([]);
});
