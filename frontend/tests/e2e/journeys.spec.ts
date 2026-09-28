import { expect, test as base, type APIRequestContext, type Page } from '@playwright/test';
import { apiLogin, DEMO_ACCOUNTS, demoPassword, projectSlot, useSession, waitUntilReady } from './support/helpers';

// S11 journeys across roles on one seeded stack (fresh database per run):
// - seeker (DS-11): browses signed out, signs in from the contact button, the form reopens by itself, sends the lead;
//   an unverified seeker keeps the intent through the KYC page instead;
// - owner/broker (UI-08/UI-09) and seeker (UI-10): the broker finds the lead, proposes a viewing, the seeker picks a
//   slot, the broker marks the request done; the seeker withdraws another request.
// Each Playwright project works on its own listing (projectSlot), so projects never race for the same lead.
// demo.user and demo.broker have a VERIFIED KYC profile from the seed (app.uat-seed.kyc-verified-accounts); the
// synthetic seeker uat.bui.khanh.linh has a PENDING one and signs in with the demo password (app.uat-seed.password).

const UNVERIFIED_SEEKER = 'uat.bui.khanh.linh@example.invalid';

const test = base.extend<object, { brokerToken: string; buyerToken: string }>({
  brokerToken: [
    async ({ playwright }, use, workerInfo) => {
      const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL });
      await use(await apiLogin(request, DEMO_ACCOUNTS.broker));
      await request.dispose();
    },
    { scope: 'worker' },
  ],
  buyerToken: [
    async ({ playwright }, use, workerInfo) => {
      const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL });
      await use(await apiLogin(request, DEMO_ACCOUNTS.buyer));
      await request.dispose();
    },
    { scope: 'worker' },
  ],
});

const auth = (token: string) => ({ Authorization: `Bearer ${token}` });

interface OwnListing {
  id: string;
  slug: string;
  publicVersion: { title: string } | null;
}

/** A published listing of demo.broker that demo.user has no open request for; a different one per project. */
async function listingForProject(
  request: APIRequestContext,
  brokerToken: string,
  buyerToken: string,
  projectName: string,
  offset = 0,
): Promise<OwnListing> {
  const response = await request.get('/api/v2/me/listings?status=ACTIVE&size=50', { headers: auth(brokerToken) });
  expect(response.ok(), await response.text()).toBe(true);
  const items = ((await response.json()) as { items: OwnListing[] }).items;
  // chromium-1440 uses the even positions, chromium-320 the odd ones; `offset` skips that many eligible listings.
  let skip = offset;
  for (let index = projectSlot(projectName) % 2; index < items.length; index += 2) {
    const eligibility = await request.get(`/api/v1/me/inquiries/eligibility?listingId=${items[index].id}`, {
      headers: auth(buyerToken),
    });
    const body = (await eligibility.json()) as { listingAcceptsLeads: boolean; openLeadId: string | null };
    if (body.listingAcceptsLeads && !body.openLeadId && skip-- === 0) return items[index];
  }
  throw new Error(
    `no demo.broker listing left for ${projectName} (the seed gives demo.broker 6 published listings; use a fresh database)`,
  );
}

async function signInFromDialog(page: Page, email: string) {
  const dialog = page.getByRole('dialog', { name: 'Đăng nhập' });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel('Email').fill(email);
  await dialog.getByLabel('Mật khẩu', { exact: true }).fill(demoPassword());
  await dialog.locator('form').getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(dialog).toBeHidden();
}

async function sendLead(page: Page, note: string) {
  const form = page.getByRole('dialog', { name: 'Hẹn xem bất động sản' });
  await expect(form).toBeVisible();
  await form.getByText('Hẹn xem trực tiếp').click();
  await form.getByLabel(/Họ và tên/).fill('Người dùng Demo');
  await form.getByLabel(/Số điện thoại/).fill('0912345678');
  await form.getByLabel(/Thời gian hoặc lời nhắn/).fill(note);
  await form.getByLabel(/Tôi đồng ý gửi tên và số điện thoại/).check();
  await form.getByRole('button', { name: 'Gửi yêu cầu' }).click();
  await expect(form.getByRole('heading', { name: 'Yêu cầu đã được ghi nhận' })).toBeVisible();
  await form.getByRole('button', { name: 'Hoàn tất' }).click();
  await expect(form).toBeHidden();
}

test('seeker signs in from the contact button, the form reopens and the lead reaches the broker, who proposes a viewing that the seeker confirms', async ({
  page,
  browser,
  request,
  brokerToken,
  buyerToken,
}, info) => {
  const listing = await listingForProject(request, brokerToken, buyerToken, info.project.name);
  const note = `E2E ${info.project.name} ${Date.now()}: xem nhà cuối tuần`;

  // 1. Signed out: the listing is readable; contacting asks for a sign-in and remembers the intent (?contact=1).
  await page.goto(`/listings/${listing.slug}`);
  await waitUntilReady(page);
  await page.getByRole('button', { name: 'Đăng nhập để liên hệ' }).first().click();
  await expect(page).toHaveURL(/[?&]contact=1/);
  await signInFromDialog(page, DEMO_ACCOUNTS.buyer);
  // 2. Verified seeker: the form opens by itself after the sign-in (no second click), and the intent is consumed.
  await sendLead(page, note);
  await expect(page).not.toHaveURL(/[?&]contact=1/);

  // 3. The broker finds the request in the inbox (server search) and proposes a viewing slot.
  const broker = await browser.newPage();
  await useSession(broker, brokerToken);
  await broker.goto('/my-leads');
  await expect(broker.getByRole('heading', { level: 1, name: 'Hộp thư khách quan tâm' })).toBeVisible();
  await broker.getByLabel('Tìm theo tên hoặc lời nhắn').fill(note);
  await broker.getByRole('button', { name: 'Tìm', exact: true }).click();
  // The card shows the listing title, not the note; each project uses its own listing (listingForProject), so that
  // title is unique among this buyer's leads even across repeated local runs against the same seeded database.
  const card = broker
    .getByRole('article')
    .filter({ hasText: 'Người dùng Demo' })
    .filter({ hasText: listing.publicVersion!.title });
  await expect(card).toHaveCount(1);
  await expect(card).toContainText('Mới nhận');
  await card.getByRole('button', { name: /^Xử lý yêu cầu của / }).click();
  const sheet = broker.getByRole('dialog', { name: 'Người dùng Demo' });
  await expect(sheet.getByText(note)).toBeVisible();
  await sheet.getByRole('button', { name: 'Đề xuất lịch hẹn xem' }).click();
  // Tomorrow 10:00 (Vietnam time) is always ≥ 30 minutes ahead and inside the 60-day window.
  const tomorrow = new Date(Date.now() + 24 * 3600 * 1000).toLocaleDateString('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
  });
  // Distinct hour per project so demo.broker's calendar never collides across parallel projects.
  const hour = String(10 + projectSlot(info.project.name)).padStart(2, '0');
  await sheet.getByLabel('Ngày của khung giờ 1').fill(tomorrow);
  await sheet.getByLabel('Giờ bắt đầu khung giờ 1').fill(`${hour}:00`);
  await sheet.getByLabel('Ghi chú cho lịch hẹn').fill('Gặp ở sảnh tòa nhà');
  await sheet.getByRole('button', { name: 'Gửi đề xuất' }).click();
  await expect(sheet.getByText('Bạn đã đề xuất')).toBeVisible();

  // 4. The seeker sees the proposal in "Yêu cầu đã gửi", picks the slot and confirms it.
  await page.goto('/my-inquiries');
  await expect(page.getByRole('heading', { level: 1, name: 'Yêu cầu đã gửi' })).toBeVisible();
  // Newest first (server order): .first() picks this run's request even if an earlier local run left one behind.
  const inquiry = page
    .getByRole('article')
    .filter({ has: page.locator(`a[href="/listings/${listing.slug}"]`) })
    .first();
  await expect(inquiry).toContainText('người đăng đã đề xuất giờ, mời bạn chọn');
  await inquiry.getByRole('button', { name: 'Lịch hẹn & lịch sử' }).click();
  await inquiry.getByRole('radio').first().check();
  await inquiry.getByRole('button', { name: 'Xác nhận khung giờ' }).click();
  await expect(inquiry.getByText('Đã xác nhận').first()).toBeVisible();

  // 5. The broker sees the confirmed appointment and closes the request as done, with a note.
  await broker.reload();
  await broker.getByLabel('Tìm theo tên hoặc lời nhắn').fill(note);
  await broker.getByRole('button', { name: 'Tìm', exact: true }).click();
  await card.getByRole('button', { name: /^Xử lý yêu cầu của / }).click();
  await expect(sheet.getByText('Đã xác nhận').first()).toBeVisible();
  await sheet.getByLabel('Chuyển trạng thái').selectOption({ label: 'Hoàn tất' });
  await sheet.getByLabel('Ghi chú cho thay đổi').fill('Khách đã chốt lịch xem');
  await sheet.getByRole('button', { name: 'Lưu trạng thái' }).click();
  await expect(sheet.getByText('Đã cập nhật trạng thái.')).toBeVisible();
  await broker.close();
});

test('seeker withdraws a request and the broker sees it withdrawn', async ({
  page,
  request,
  brokerToken,
  buyerToken,
}, info) => {
  const listing = await listingForProject(request, brokerToken, buyerToken, info.project.name, 1);
  const created = await request.post('/api/v1/public/leads', {
    headers: { ...auth(buyerToken), 'Idempotency-Key': `e2e-withdraw-${info.project.name}-${Date.now()}` },
    data: {
      listingId: listing.id,
      fullName: 'Người dùng Demo',
      phone: '0912345678',
      requestType: 'CONSULTATION',
      note: `E2E rút yêu cầu ${info.project.name}`,
      consentPolicy: true,
    },
  });
  expect(created.ok(), await created.text()).toBe(true);

  await useSession(page, buyerToken);
  await page.goto('/my-inquiries');
  const inquiry = page
    .getByRole('article')
    .filter({ has: page.locator(`a[href="/listings/${listing.slug}"]`) })
    .first();
  await inquiry.getByRole('button', { name: 'Rút yêu cầu' }).click();
  const dialog = page.getByRole('dialog', { name: 'Rút yêu cầu liên hệ?' });
  await dialog.getByLabel('Lý do (không bắt buộc)').fill('Đã tìm được nhà');
  await dialog.getByRole('button', { name: 'Rút yêu cầu' }).click();
  await expect(dialog).toBeHidden();
  await expect(inquiry).toContainText('Bạn đã rút yêu cầu');
  await expect(inquiry.getByRole('button', { name: 'Rút yêu cầu' })).toHaveCount(0);
});

test('an unverified seeker keeps the contact intent through sign-in and the KYC page', async ({ page }) => {
  const listings = await page.request.get('/api/v1/listings/search?purpose=SALE&sortBy=LATEST&size=1');
  const [listing] = (await listings.json()) as Array<{ slug: string }>;
  await page.goto(`/listings/${listing.slug}`);
  await waitUntilReady(page);
  await page.getByRole('button', { name: 'Đăng nhập để liên hệ' }).first().click();
  await signInFromDialog(page, UNVERIFIED_SEEKER);
  const gate = page.getByRole('link', { name: 'Xác minh eKYC để liên hệ' }).first();
  await expect(gate).toBeVisible();
  await expect(gate).toHaveAttribute(
    'href',
    `/kyc?returnTo=${encodeURIComponent(`/listings/${listing.slug}?contact=1`)}`,
  );
  await gate.click();
  // This seeker's documents are waiting for review: the page says so instead of asking for them again.
  await expect(page.getByRole('heading', { level: 1, name: 'Đang chờ duyệt thủ công' })).toBeVisible();
  await expect(page).toHaveURL(/\/kyc\?returnTo=/);
});
