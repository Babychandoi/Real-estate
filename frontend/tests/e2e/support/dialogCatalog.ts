import { expect, type Locator, type Page } from '@playwright/test';
import { ADMIN } from './routeCatalog';

// Detail and edit views opened from list rows (centred dialogs; ui-consistency.spec.ts checks their geometry and
// ux-dialogs.spec.ts their accessibility and target sizes). Each scenario opens the view and returns its opener.

export const rowButton = (page: Page, name: RegExp) => page.getByRole('button', { name }).first();

export interface DialogScenario {
  name: string;
  as: 'guest' | 'buyer' | 'broker' | 'admin' | 'moderator';
  path: string;
  /** Opens the view and returns the control that opened it (focus must return to it on Escape). */
  open: (page: Page) => Promise<Locator | null>;
  /** The view used to be a right-hand Sheet (its geometry is part of the owner's request); false for plain form dialogs. */
  wasSheet: boolean;
  /** An unbroken opener that only exists for some data: skipped when the seed has no such row. */
  optional?: boolean;
}

export const DIALOGS: DialogScenario[] = [
  {
    name: 'admin verification: evidence comparison',
    as: 'admin',
    path: `${ADMIN}/verification`,
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Đối chiếu /);
      await opener.click();
      await expect(page.getByRole('heading', { name: 'Đối chiếu bằng chứng' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin verification: reject reason (second layer)',
    as: 'admin',
    path: `${ADMIN}/verification`,
    wasSheet: false,
    open: async (page) => {
      await rowButton(page, /^Đối chiếu /).click();
      const opener = page.getByRole('button', { name: 'Từ chối…' });
      await opener.click();
      await expect(page.getByRole('dialog', { name: 'Từ chối hồ sơ giấy tờ' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin moderation: review of a submission',
    as: 'admin',
    path: `${ADMIN}/moderation`,
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Đối chiếu /);
      await opener.click();
      await expect(page.getByRole('heading', { name: /so với bản/i }).first()).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin listings: detail',
    as: 'admin',
    path: `${ADMIN}/listings`,
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Chi tiết /);
      await opener.click();
      await expect(page.getByRole('heading', { name: 'Lịch sử trạng thái' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin listings: lock reason',
    as: 'admin',
    path: `${ADMIN}/listings`,
    wasSheet: false,
    open: async (page) => {
      const opener = rowButton(page, /^Khóa tin: /);
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin users: history',
    as: 'admin',
    path: `${ADMIN}/users`,
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Lịch sử /);
      await opener.click();
      await expect(page.getByRole('heading', { name: 'Thao tác quản trị' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin users: identity file',
    as: 'admin',
    path: `${ADMIN}/users`,
    wasSheet: true,
    optional: true,
    open: async (page) => {
      const opener = rowButton(page, /^Hồ sơ định danh /);
      if ((await opener.count()) === 0) return null;
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin users: role change',
    as: 'admin',
    path: `${ADMIN}/users`,
    wasSheet: false,
    open: async (page) => {
      const opener = rowButton(page, /^Đổi vai trò /);
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin reports: case',
    as: 'admin',
    path: `${ADMIN}/reports`,
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Mở vụ việc/);
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin billing: order history',
    as: 'admin',
    path: `${ADMIN}/billing`,
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Lịch sử /);
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin cms: article',
    as: 'admin',
    path: `${ADMIN}/cms`,
    wasSheet: true,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Mở', exact: true }).first();
      await opener.click();
      await expect(page.getByRole('heading', { name: 'Lịch sử phiên bản' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin cms: new article',
    as: 'admin',
    path: `${ADMIN}/cms`,
    wasSheet: false,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Bài viết mới' });
      await opener.click();
      await expect(page.getByRole('dialog', { name: 'Bài viết mới' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin projects: public profile',
    as: 'admin',
    path: `${ADMIN}/projects`,
    wasSheet: false,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Trang công khai: mô tả, nguồn, tiện ích' }).first();
      await opener.click();
      await expect(page.getByRole('textbox', { name: 'Nguồn thông tin' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'admin projects: new project',
    as: 'admin',
    path: `${ADMIN}/projects`,
    wasSheet: false,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Tạo dự án' });
      await opener.click();
      await expect(page.getByRole('dialog', { name: 'Tạo dự án mới' })).toBeVisible();
      return opener;
    },
  },
  {
    name: 'account billing: order history',
    as: 'broker',
    path: '/billing',
    wasSheet: true,
    optional: true,
    open: async (page) => {
      const opener = rowButton(page, /^Lịch sử /);
      if ((await opener.count()) === 0) return null;
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'account leads: lead detail',
    as: 'broker',
    path: '/my-leads',
    wasSheet: true,
    open: async (page) => {
      const opener = rowButton(page, /^Xử lý yêu cầu của /);
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      return opener;
    },
  },
  {
    name: 'public: sign-in and registration',
    as: 'guest',
    path: '/',
    wasSheet: false,
    open: async (page) => {
      const opener = page.getByRole('button', { name: 'Đăng nhập' }).first();
      await opener.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await page.getByRole('dialog').getByRole('button', { name: 'Đăng ký', exact: true }).click();
      return opener;
    },
  },
];
