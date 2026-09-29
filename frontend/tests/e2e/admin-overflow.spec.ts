import { mkdirSync } from 'node:fs';
import { expect, test, type Browser, type BrowserContext, type Locator, type Page } from '@playwright/test';
import { seedAdversarial, type SeededAdversarial } from './support/adminAdversarial';
import { skipConsentBanner, useSession, waitUntilReady } from './support/helpers';
import { auditLayout, type LayoutFinding } from './support/layoutAudit';

// Long text in the staff pages ("quản trị"): every admin route is opened with rows that carry adversarial content
// (200+ character unbroken titles, e-mail addresses and URLs, 150+ character Vietnamese titles, multi-paragraph
// descriptions, long names, reasons and notes, huge amounts; see support/adminAdversarial.ts) and checked at 360, 768,
// 1024 and 1440 px: no sideways scrolling of the document, nothing sticking out of the card / table cell / dialog that
// holds it (support/layoutAudit.ts), and cards side by side in one row keep the same height. Drawers and dialogs that
// show the long text in full (detail sheets, reason dialogs) are opened and checked too.
//
// The rows are this spec's own (fresh letter-only tag per run, own listings, users, reports, orders …), created through
// the APIs; the spec never edits rows other specs use. It runs only in the chromium-1440 project because it sets its
// own viewports.
//
// Set ADMIN_OVERFLOW_SHOTS=<dir> to save a full-page screenshot of every checked state (for looking at the result).

const WIDTHS = [360, 768, 1024, 1440] as const;
const BASE = '/2026/nhadatchuan/admin';
const SHOTS = process.env.ADMIN_OVERFLOW_SHOTS;

// A missing button fails in seconds with its locator, not after the whole test timeout.
test.use({ actionTimeout: 15_000 });

let seeded: SeededAdversarial;

test.beforeAll(async ({ playwright }, workerInfo) => {
  test.skip(
    workerInfo.project.name !== 'chromium-1440',
    'sets its own viewports: runs once, in the chromium-1440 project',
  );
  test.setTimeout(180_000);
  const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL });
  seeded = await seedAdversarial(request);
  await request.dispose();
  // Rows that a later step needs must exist; anything missing is reported here, not as a puzzling layout failure.
  expect(seeded.gaps, 'adversarial rows that could not be created').toEqual([]);
});

interface Checker {
  page: Page;
  route: string;
  width: number;
  findings: string[];
  check: (step: string) => Promise<void>;
}

async function newChecker(context: BrowserContext, route: string, width: number): Promise<Checker> {
  const page = await context.newPage();
  const findings: string[] = [];
  const check = async (step: string) => {
    // Let layout settle: fonts, images and the sheet's slide-in.
    await page.evaluate(async () => {
      await document.fonts.ready;
    });
    await page.waitForTimeout(250);
    const list: LayoutFinding[] = await auditLayout(page);
    for (const finding of list) findings.push(`[${step}] ${finding.kind}: ${finding.detail}`);
    if (SHOTS) {
      mkdirSync(SHOTS, { recursive: true });
      const name = `${route}-${step}`.replace(/[^a-z0-9-]+/gi, '_');
      await page.screenshot({ path: `${SHOTS}/${name}-${width}.png`, fullPage: true });
    }
  };
  return { page, route, width, findings, check };
}

/** Escape closes the topmost dialog or sheet only; a stack of them needs one press per layer. */
async function closeTopDialog(page: Page) {
  const dialogs = page.getByRole('dialog');
  const before = await dialogs.count();
  if (before === 0) return;
  await page.keyboard.press('Escape');
  await expect(dialogs).toHaveCount(before - 1);
}

/** One admin route at one width: `steps` opens the states worth checking after the plain page. */
async function run(
  browser: Browser,
  route: string,
  width: number,
  steps?: (c: Checker) => Promise<void>,
  options: { signedIn?: boolean } = {},
) {
  const context = await browser.newContext({ viewport: { width, height: 900 }, locale: 'vi-VN' });
  try {
    const c = await newChecker(context, route, width);
    await skipConsentBanner(c.page);
    if (options.signedIn !== false) await useSession(c.page, seeded.adminToken);
    await c.page.goto(`${BASE}/${route}`);
    await waitUntilReady(c.page);
    await c.check('page');
    await steps?.(c);
    expect(c.findings, `${route} at ${width}px`).toEqual([]);
  } finally {
    await context.close();
  }
}

const rowAction = (page: Page, name: string | RegExp): Locator => page.getByRole('button', { name }).last();

const scenarios: Array<{
  route: string;
  steps?: (c: Checker) => Promise<void>;
  signedIn?: boolean;
}> = [
  {
    route: 'moderation',
    steps: async (c) => {
      const { page } = c;
      // Newest first is not the queue order (oldest first): the rows of this run are the ones this admin claimed.
      await page.getByRole('button', { name: 'Tôi đang xử lý' }).click();
      await expect(page.getByRole('button', { name: /^Đối chiếu Nha pho tieudekhongngat/ }).first()).toBeVisible();
      await c.check('mine');
      await rowAction(page, /^Đối chiếu Nha pho tieudekhongngat/).click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await expect(
        page.getByRole('heading', { name: 'Bản công khai so với bản gửi duyệt', exact: false }),
      ).toBeVisible();
      await c.check('review-sheet');
      await page.getByRole('button', { name: 'Từ chối…' }).click();
      await c.check('reject-dialog');
      await closeTopDialog(page);
      await closeTopDialog(page);
      // Bulk decision: the dialog lists every selected title.
      await page.getByRole('checkbox', { name: 'Chọn tất cả các dòng trên trang này' }).check();
      await page.getByRole('button', { name: /^Từ chối \d+ tin$/ }).click();
      await c.check('bulk-dialog');
      await closeTopDialog(page);
      // Last page of the whole queue and the random-check tab.
      await page.getByRole('button', { name: 'Tất cả', exact: true }).click();
      const last = page.getByRole('navigation', { name: 'Phân trang' }).getByRole('listitem').last();
      if (await last.isVisible()) {
        await last.getByRole('button').click();
        await c.check('last-page');
      }
      await page.getByRole('tab', { name: 'Kiểm tra ngẫu nhiên' }).click();
      await c.check('audit-tab');
    },
  },
  {
    route: 'listings',
    steps: async (c) => {
      const { page } = c;
      await page.getByLabel('Từ khóa').fill(seeded.tag);
      await page.getByRole('button', { name: 'Lọc', exact: true }).click();
      await expect(page.getByRole('button', { name: /^Chi tiết Nha pho tieudekhongngat/ }).first()).toBeVisible();
      await c.check('filtered');
      await rowAction(page, /^Chi tiết Nha pho tieudekhongngat/).click();
      await expect(page.getByRole('heading', { name: 'Lịch sử trạng thái' })).toBeVisible();
      await c.check('detail-sheet');
      // The preview shows the multi-paragraph description with its unbroken URL.
      await page
        .getByRole('button', { name: /^Xem trước phiên bản/ })
        .first()
        .click();
      await expect(page.getByRole('heading', { name: /^Xem trước riêng tư/ })).toBeVisible();
      await c.check('preview');
      await closeTopDialog(page);
      await rowAction(page, /^Khóa tin: Nha pho tieudekhongngat/).click();
      await c.check('lock-dialog');
    },
  },
  {
    route: 'users',
    steps: async (c) => {
      const { page } = c;
      await page.getByLabel('Tìm theo tên hoặc email').fill(seeded.userEmail.slice(0, 40));
      await page.getByRole('button', { name: 'Tìm', exact: true }).click();
      await expect(page.getByRole('button', { name: /^Lịch sử Nguyenvanhoang/ }).first()).toBeVisible();
      await c.check('filtered');
      await rowAction(page, /^Lịch sử Nguyenvanhoang/).click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await c.check('history');
      await closeTopDialog(page);
      await rowAction(page, /^Đổi vai trò Nguyenvanhoang/).click();
      await c.check('role-dialog');
    },
  },
  {
    route: 'leads-and-reports',
    steps: async (c) => {
      const { page } = c;
      if (!seeded.leadCreated) {
        test.info().annotations.push({ type: 'skipped', description: 'daily lead quota of the requester is used up' });
        return;
      }
      await page.getByPlaceholder('Tìm theo tiêu đề hoặc địa chỉ').fill(seeded.tag);
      await page.getByRole('button', { name: 'Tìm', exact: true }).click();
      const card = page.getByRole('button', { name: /Căn hộ view sông/ }).first();
      await expect(card).toBeVisible();
      await c.check('cards');
      await card.click();
      await expect(page.getByRole('button', { name: 'Quay lại danh sách bài đăng' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Xem thêm' }).first()).toBeVisible();
      await c.check('leads');
      await page.getByRole('button', { name: 'Xem thêm' }).first().click();
      await c.check('lead-note-expanded');
    },
  },
  {
    route: 'reports',
    steps: async (c) => {
      const { page } = c;
      await page.getByRole('button', { name: 'Tôi đang xử lý' }).click();
      await expect(page.getByRole('button', { name: /^Mở vụ việc/ }).first()).toBeVisible();
      await c.check('mine');
      await rowAction(page, /^Mở vụ việc/).click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await c.check('case-sheet');
      await page.getByRole('button', { name: 'Ghi chú…' }).click();
      await c.check('note-dialog');
      await closeTopDialog(page);
      await closeTopDialog(page);
      await page.getByRole('button', { name: 'Đang mở', exact: true }).click();
      const last = page.getByRole('navigation', { name: 'Phân trang' }).getByRole('listitem').last();
      if (await last.isVisible()) {
        await last.getByRole('button').click();
        await c.check('last-page');
      }
    },
  },
  {
    route: 'verification',
    steps: async (c) => {
      const { page } = c;
      await expect(page.getByRole('button', { name: /^Đối chiếu Nha pho tieudekhongngat/ }).first()).toBeVisible();
      await rowAction(page, /^Đối chiếu Nha pho tieudekhongngat/).click();
      await expect(page.getByRole('heading', { name: 'Đối chiếu bằng chứng' })).toBeVisible();
      await c.check('evidence-sheet');
      await page.getByRole('button', { name: 'Từ chối…' }).click();
      await c.check('reject-dialog');
    },
  },
  {
    route: 'billing',
    steps: async (c) => {
      const { page } = c;
      const chips = page.getByRole('group', { name: 'Lọc theo trạng thái' }).getByRole('button');
      const labels = (await chips.allInnerTexts()).map((text) => text.replace(/\s*\(\d+\)$/, '').trim());
      for (const label of labels) {
        await page
          .getByRole('group', { name: 'Lọc theo trạng thái' })
          .getByRole('button', { name: new RegExp(`^${label}`) })
          .click();
        await c.check(`status-${label}`);
      }
      await page
        .getByRole('group', { name: 'Lọc theo trạng thái' })
        .getByRole('button', { name: /^Không được duyệt/ })
        .click();
      await expect(page.getByRole('button', { name: /^Lịch sử / }).first()).toBeVisible();
      await page
        .getByRole('button', { name: /^Lịch sử / })
        .first()
        .click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await c.check('history-sheet');
    },
  },
  { route: 'analytics' },
  {
    route: 'projects',
    steps: async (c) => {
      const { page } = c;
      await page.getByPlaceholder('Tìm theo tên dự án').fill('Khu đô thị tenduanrataidai');
      await page.getByRole('button', { name: 'Tìm', exact: true }).click();
      await expect(page.getByRole('heading', { level: 2 }).first()).toBeVisible();
      await c.check('filtered');
      await page.getByRole('button', { name: 'Trang công khai: mô tả, nguồn, tiện ích' }).first().click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await c.check('profile-dialog');
      await closeTopDialog(page);
      await page.getByRole('button', { name: 'Tạo dự án' }).click();
      const dialog = page.getByRole('dialog', { name: 'Tạo dự án mới' });
      await dialog.getByLabel('Tên dự án').fill(seeded.projectName);
      await dialog.getByLabel('Địa chỉ').fill(seeded.projectName);
      await c.check('create-dialog');
    },
  },
  {
    route: 'cms',
    steps: async (c) => {
      const { page } = c;
      await expect(page.getByRole('button', { name: 'Mở', exact: true }).first()).toBeVisible();
      await c.check('list');
      await page.getByRole('button', { name: 'Mở', exact: true }).first().click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await c.check('article-sheet');
      await closeTopDialog(page);
      await page.getByRole('button', { name: 'Bài viết mới' }).click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await c.check('editor');
    },
  },
  { route: 'security' },
];

for (const scenario of scenarios) {
  test(`admin/${scenario.route}: long text stays inside its cards, cells and drawers`, async ({ browser }) => {
    test.setTimeout(240_000);
    for (const width of WIDTHS) await run(browser, scenario.route, width, scenario.steps);
  });
}

test('admin/login: a very long e-mail address and error message stay inside the form card', async ({ browser }) => {
  test.setTimeout(120_000);
  for (const width of WIDTHS) {
    await run(
      browser,
      'login',
      width,
      async (c) => {
        const { page } = c;
        const long = `${'nguoidunglong'.repeat(6).slice(0, 62)}@${'tenmiendai'.repeat(6)}.${'phanmorong'.repeat(6)}.vn`;
        await page.getByLabel('Email', { exact: false }).fill(long);
        await page.getByPlaceholder('Nhập mật khẩu').fill('mat-khau-sai-1234567');
        await page.getByRole('button', { name: 'Tiếp tục' }).click();
        await expect(page.getByRole('alert')).toBeVisible();
        await c.check('error');
      },
      { signedIn: false },
    );
  }
});
