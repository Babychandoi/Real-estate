import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { staffToken } from './support/adminAdversarial';
import { apiLogin, DEMO_ACCOUNTS, skipConsentBanner, useSession, waitUntilReady } from './support/helpers';
import { KeyboardWalker } from './support/keyboard';
import { ADMIN } from './support/routeCatalog';

// R-7 / DS-04 (automatable part): the four core journeys completed with the keyboard alone — Tab, Shift+Tab, Enter,
// Space, arrows and typing; no click, no `fill`. At every focus stop the focus ring is visible, the focused control is
// not hidden under a sticky bar or overlay, and focus never leaves an open dialog (support/keyboard.ts).
//   1. seeker: search box → result card → listing detail → contact form → request sent;
//   2. owner: four-step listing wizard → submitted for review;
//   3. moderator: the owner's submission → review → approve with a reason;
//   4. admin: a reported listing → case → conclusion with a note.
// Journeys 2 → 3 share the listing (serial). Desktop 1440 (keyboard users) and a 390 px phone with a keyboard
// (switch access / external keyboard) for journey 1. Real screen readers stay a manual check (see the W6-UX report).

test.describe.configure({ mode: 'serial' });
test.use({ actionTimeout: 20_000 });

test.beforeEach(({ browserName }, testInfo) => {
  void browserName;
  test.skip(testInfo.project.name !== 'chromium-1440', 'one project: the journeys change shared data');
});

const auth = (token: string) => ({ Authorization: `Bearer ${token}` });

let ownerTitle = '';

async function eligibleListing(request: APIRequestContext, buyer: string) {
  // From the end of the newest-first SALE results, so the other journeys' listings (start of demo.broker's list) stay
  // untouched; the first one the seeker has no open request for and that accepts requests.
  const response = await request.get('/api/v2/listings/search?purpose=SALE&size=48');
  const items = ((await response.json()) as { items: Array<{ id: string; slug: string; title: string }> }).items;
  for (const item of [...items].reverse()) {
    const eligibility = await request.get(`/api/v1/me/inquiries/eligibility?listingId=${item.id}`, {
      headers: auth(buyer),
    });
    if (!eligibility.ok()) continue;
    const body = (await eligibility.json()) as { listingAcceptsLeads: boolean; openLeadId: string | null };
    if (body.listingAcceptsLeads && !body.openLeadId) return item;
  }
  throw new Error('no SALE listing left that demo.user can contact (use a fresh database)');
}

async function seekerJourney(page: Page, request: APIRequestContext, width: number) {
  const buyer = await apiLogin(request, DEMO_ACCOUNTS.buyer);
  const listing = await eligibleListing(request, buyer);
  await useSession(page, buyer);
  await skipConsentBanner(page);
  await page.setViewportSize({ width, height: width < 600 ? 844 : 900 });
  const keys = new KeyboardWalker(page, `seeker @${width}`);

  await page.goto('/search?purpose=SALE');
  await waitUntilReady(page);
  // The skip link is the first stop and takes the keyboard past the navigation.
  await keys.press('Tab');
  await expect(page.getByRole('link', { name: 'Bỏ qua điều hướng' })).toBeFocused();
  await keys.press('Enter');
  const search = page.getByRole('combobox', { name: 'Tìm theo từ khóa hoặc địa điểm' });
  await keys.tabTo(search);
  await keys.type(listing.title);
  // The first suggestion is the plain keyword; Enter picks it. (Escape would clear a search field in Chromium.)
  await keys.press('Enter');
  await expect(page).toHaveURL(/[?&]q=/);
  await waitUntilReady(page);

  const card = page.getByRole('link', { name: `Xem chi tiết: ${listing.title}` }).first();
  await keys.tabTo(card);
  await keys.press('Enter');
  await expect(page).toHaveURL(new RegExp(`/listings/${listing.slug}`));
  await waitUntilReady(page);

  const contact = page.getByRole('button', { name: 'Hẹn xem & nhận tư vấn' }).first();
  await keys.tabTo(contact);
  await keys.press('Enter');
  const form = page.getByRole('dialog', { name: 'Hẹn xem bất động sản' });
  await expect(form).toBeVisible();
  const viewing = form.getByRole('radio', { name: 'Hẹn xem trực tiếp' });
  await keys.tabTo(viewing, { within: form });
  await keys.press('Space', form);
  await expect(viewing).toBeChecked();
  await keys.tabTo(form.getByRole('textbox', { name: /Họ và tên/ }), { within: form });
  await keys.type('Người dùng Demo');
  await keys.tabTo(form.getByRole('textbox', { name: /Số điện thoại/ }), { within: form });
  await keys.type('0912345678');
  await keys.tabTo(form.getByRole('textbox', { name: /Thời gian hoặc lời nhắn/ }), { within: form });
  await keys.type(`Bàn phím W6 ${width}px: xem nhà cuối tuần`);
  const consent = form.getByRole('checkbox', { name: /Tôi đồng ý gửi tên và số điện thoại/ });
  await keys.tabTo(consent, { within: form });
  await keys.press('Space', form);
  await expect(consent).toBeChecked();
  await keys.tabTo(form.getByRole('button', { name: 'Gửi yêu cầu' }), { within: form });
  await keys.press('Enter');
  await expect(form.getByRole('heading', { name: 'Yêu cầu đã được ghi nhận' })).toBeVisible();
  await keys.tabTo(form.getByRole('button', { name: 'Hoàn tất' }), { within: form });
  await keys.press('Enter');
  await expect(form).toBeHidden();
  // Focus returns to where the journey started the dialog from, never to the top of the page.
  await expect(contact).toBeFocused();
  keys.assertClean();
}

test('1. seeker: search → detail → contact form, keyboard only (desktop)', async ({ page, request }) => {
  test.setTimeout(240_000);
  await seekerJourney(page, request, 1440);
});

test('1b. seeker: search → detail → contact form, keyboard only (390 px phone)', async ({ page, request }) => {
  test.setTimeout(240_000);
  await seekerJourney(page, request, 390);
});

test('2. owner: posts a listing through the four-step wizard, keyboard only', async ({ page, request }) => {
  test.setTimeout(300_000);
  const broker = await apiLogin(request, DEMO_ACCOUNTS.broker);
  await useSession(page, broker);
  await skipConsentBanner(page);
  await page.setViewportSize({ width: 1440, height: 900 });
  const keys = new KeyboardWalker(page, 'owner wizard');
  ownerTitle = `Căn hộ bàn phím W6 ${Date.now()}`;

  await page.goto('/listings/new');
  await waitUntilReady(page);
  await expect(page.getByRole('heading', { level: 1, name: 'Đăng tin mới' })).toBeVisible();
  await keys.tabTo(page.getByLabel('Tiêu đề'));
  await keys.type(ownerTitle);
  await keys.tabTo(page.getByLabel('Giá bán (VNĐ)'));
  await keys.type('3950000000');
  await expect(page.getByText('= 3,95 tỷ')).toBeVisible();
  await keys.tabTo(page.getByLabel('Diện tích (m²)'));
  await keys.type('72,5');
  const legal = page.getByLabel('Giấy tờ pháp lý');
  await keys.tabTo(legal);
  await keys.chooseOption(legal, 'PINK_BOOK');
  await expect(legal).toHaveValue('PINK_BOOK');
  await expect(legal).toBeFocused();
  await expect(page.getByTestId('autosave-status')).toHaveAttribute('data-state', 'saved', { timeout: 20_000 });

  await keys.tabTo(page.getByRole('button', { name: /Tiếp tục: Vị trí/ }));
  await keys.press('Enter');
  await expect(page.getByRole('heading', { level: 2, name: 'Bước 2: Vị trí' })).toBeFocused();
  await keys.tabTo(page.getByLabel('Mã quận/huyện'));
  await keys.type('005');
  await keys.tabTo(page.getByLabel('Địa chỉ hiển thị'));
  await keys.type('Cầu Giấy, Hà Nội');
  await keys.tabTo(page.getByRole('button', { name: /Tiếp tục: Ảnh/ }));
  await keys.press('Enter');
  await keys.tabTo(page.getByRole('button', { name: /Tiếp tục: Xem trước/ }));
  await keys.press('Enter');
  await expect(page.getByTestId('listing-preview').getByRole('heading', { name: ownerTitle })).toBeVisible();
  await keys.tabTo(page.getByRole('button', { name: 'Gửi duyệt' }));
  await keys.press('Enter');
  await expect(page.getByTestId('submit-success')).toBeVisible();
  keys.assertClean();
});

test('3. moderator: reviews the owner’s submission and approves it with a reason, keyboard only', async ({
  page,
  request,
}) => {
  test.setTimeout(300_000);
  expect(ownerTitle, 'journey 2 must run first (serial)').not.toBe('');
  const moderator = await staffToken(request, 'demo.moderator@bds.local');
  // Setup outside the journey: the queue is paged and oldest-first, so the new submission is claimed through the API
  // and found in "Tôi đang xử lý"; everything the moderator then does is keyboard.
  const queue = await request.get('/api/v1/moderation/queue?filter=UNCLAIMED&page=0&size=100', {
    headers: auth(moderator),
  });
  expect(queue.ok(), await queue.text()).toBe(true);
  const items = ((await queue.json()) as { items: Array<{ listingId: string; title: string }> }).items;
  const item = items.find((entry) => entry.title === ownerTitle);
  expect(item, `"${ownerTitle}" in the moderation queue`).toBeTruthy();
  const claim = await request.post(`/api/v1/moderation/listings/${item!.listingId}/claim`, {
    headers: auth(moderator),
  });
  expect(claim.ok(), await claim.text()).toBe(true);

  await useSession(page, moderator);
  await skipConsentBanner(page);
  await page.setViewportSize({ width: 1440, height: 900 });
  const keys = new KeyboardWalker(page, 'moderator approve');
  await page.goto(`${ADMIN}/moderation`);
  await expect(page.getByRole('heading', { level: 1, name: 'Kiểm duyệt tin đăng' })).toBeVisible();
  await waitUntilReady(page);
  await keys.tabTo(page.getByRole('button', { name: 'Tôi đang xử lý' }));
  await keys.press('Enter');
  const review = page.getByRole('button', { name: `Đối chiếu ${ownerTitle}` }).first();
  await expect(review).toBeVisible();
  await keys.tabTo(review);
  await keys.press('Enter');
  const detail = page.getByRole('dialog', { name: ownerTitle });
  await expect(detail).toBeVisible();
  await keys.tabTo(detail.getByRole('button', { name: 'Phê duyệt…' }), { within: detail });
  await keys.press('Enter');
  const decision = page.getByRole('dialog', { name: 'Phê duyệt nội dung tin' });
  await expect(decision).toBeVisible();
  const reason = decision.getByLabel('Lý do');
  await keys.tabTo(reason, { within: decision });
  await keys.chooseOption(reason, 'MEETS_STANDARDS', decision);
  await expect(reason).toHaveValue('MEETS_STANDARDS');
  await keys.tabTo(decision.getByLabel('Ghi chú nội bộ'), { within: decision });
  await keys.type('Duyệt bằng bàn phím: ảnh và địa chỉ khớp');
  await keys.tabTo(decision.getByRole('button', { name: 'Phê duyệt 1 tin' }), { within: decision });
  await keys.press('Enter');
  await expect(page.getByText(`Đã phê duyệt: ${ownerTitle}`)).toBeVisible();
  keys.assertClean();
});

test('4. admin: concludes a reported listing with a note, keyboard only', async ({ page, request }) => {
  test.setTimeout(300_000);
  const admin = await staffToken(request, 'demo.admin@bds.local');
  const listing = (await (await request.get('/api/v2/listings/search?purpose=RENT&size=1')).json()) as {
    items: Array<{ id: string }>;
  };
  // Setup outside the journey: a visitor's report (public API) claimed by the admin, so the case is in "Tôi đang xử lý".
  const created = await request.post('/api/v1/public/reports', {
    data: {
      listingId: listing.items[0].id,
      category: 'OTHER',
      severity: 'MEDIUM',
      description: `Báo cáo kiểm thử bàn phím W6 ${Date.now()}`,
      evidenceUrls: '',
      reporterPhone: '',
    },
  });
  expect(created.ok(), await created.text()).toBe(true);
  const report = (await created.json()) as { id: string; caseNumber: string };
  const claimed = await request.post(`/api/v1/reports/${report.id}/claim`, { headers: auth(admin) });
  expect(claimed.ok(), await claimed.text()).toBe(true);

  await useSession(page, admin);
  await skipConsentBanner(page);
  await page.setViewportSize({ width: 1440, height: 900 });
  const keys = new KeyboardWalker(page, 'admin report');
  await page.goto(`${ADMIN}/reports`);
  await waitUntilReady(page);
  await keys.tabTo(page.getByRole('button', { name: 'Tôi đang xử lý' }));
  await keys.press('Enter');
  const open = page.getByRole('button', { name: `Mở vụ việc ${report.caseNumber}` });
  await expect(open).toBeVisible();
  await keys.tabTo(open);
  await keys.press('Enter');
  const caseDialog = page.getByRole('dialog', { name: new RegExp(report.caseNumber) });
  await expect(caseDialog).toBeVisible();
  await keys.tabTo(caseDialog.getByRole('button', { name: 'Kết luận vi phạm…' }), { within: caseDialog });
  await keys.press('Enter');
  const conclude = page.getByRole('dialog', { name: 'Kết luận có vi phạm' });
  await expect(conclude).toBeVisible();
  await keys.tabTo(conclude.getByLabel('Lý do / ghi chú xử lý'), { within: conclude });
  await keys.type('Thông tin sai lệch, đã xác minh với người báo cáo.');
  await keys.tabTo(conclude.getByRole('button', { name: 'Lưu kết luận' }), { within: conclude });
  await keys.press('Enter');
  await expect(page.getByText(`Đã cập nhật ${report.caseNumber}`)).toBeVisible();
  keys.assertClean();
});
