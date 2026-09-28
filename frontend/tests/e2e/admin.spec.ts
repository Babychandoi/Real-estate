import { expect, test as base, type APIRequestContext } from '@playwright/test';
import { apiLogin, demoPassword, projectSlot, useSession } from './support/helpers';

// S4-ADMIN journeys (chromium desktop): a moderator claims and approves with a reason; an admin resolves a billing
// exception; an admin changes a role with a reason and sees it in the history. Staff sessions come from the staff
// login API (the public login refuses staff accounts); one login per role and worker because /auth is rate limited.
// Projects run in parallel against one stack, so every journey works on its own row (projectSlot): its own
// submission, its own plan's order and its own target account (S11).

async function staffLogin(request: APIRequestContext, email: string): Promise<string> {
  const response = await request.post('/api/v1/auth/admin/login', { data: { email, password: demoPassword() } });
  expect(response.ok(), `staff login for ${email} answered ${response.status()}`).toBe(true);
  return ((await response.json()) as { accessToken: string }).accessToken;
}

const test = base.extend<object, { moderatorToken: string; adminToken: string; brokerToken: string }>({
  moderatorToken: [
    async ({ playwright }, use, workerInfo) => {
      const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL });
      await use(await staffLogin(request, 'demo.moderator@bds.local'));
      await request.dispose();
    },
    { scope: 'worker' },
  ],
  adminToken: [
    async ({ playwright }, use, workerInfo) => {
      const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL });
      await use(await staffLogin(request, 'demo.admin@bds.local'));
      await request.dispose();
    },
    { scope: 'worker' },
  ],
  brokerToken: [
    async ({ playwright }, use, workerInfo) => {
      const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL });
      await use(await apiLogin(request, 'demo.broker@bds.local'));
      await request.dispose();
    },
    { scope: 'worker' },
  ],
});

const auth = (token: string) => ({ Authorization: `Bearer ${token}` });

test('moderator claims a submission and approves it with a reason', async ({ page, moderatorToken }, info) => {
  await useSession(page, moderatorToken);
  await page.goto('/2026/nhadatchuan/admin/moderation');
  await expect(page.getByRole('heading', { level: 1, name: 'Kiểm duyệt tin đăng' })).toBeVisible();
  await page.getByRole('button', { name: 'Chưa ai nhận' }).click();
  // Its own row per project: the first project takes the first unclaimed submission, the second the next one, …
  const slot = projectSlot(info.project.name);
  await expect(page.getByRole('button', { name: /^Nhận xử lý / }).first()).toBeVisible();
  const unclaimed = await page.getByRole('button', { name: /^Nhận xử lý / }).count();
  expect(unclaimed, `the seed needs more than ${slot} unclaimed submissions`).toBeGreaterThan(slot);
  const claim = page.getByRole('button', { name: /^Nhận xử lý / }).nth(slot);
  const title = ((await claim.getAttribute('aria-label')) ?? '').replace(/^Nhận xử lý /, '');
  await claim.click();
  // The claimed row leaves the "unclaimed" view once the queue has reloaded.
  await expect(page.getByRole('button', { name: `Nhận xử lý ${title}` })).toHaveCount(0);

  await page.getByRole('button', { name: 'Tôi đang xử lý' }).click();
  await expect(page.getByRole('button', { name: `Đối chiếu ${title}` }).first()).toBeVisible();
  await expect(page.getByRole('button', { name: /^Nhận xử lý / })).toHaveCount(0);
  await page
    .getByRole('button', { name: `Đối chiếu ${title}` })
    .first()
    .click();
  const sheet = page.getByRole('dialog', { name: title });
  await expect(sheet.getByText(/Bạn đang nhận xử lý đến/)).toBeVisible();
  await sheet.getByRole('button', { name: 'Phê duyệt…' }).click();

  const decision = page.getByRole('dialog', { name: 'Phê duyệt nội dung tin' });
  await expect(decision.getByLabel('Lý do')).toHaveValue('');
  await decision.getByLabel('Ghi chú nội bộ').fill('Ảnh rõ, địa chỉ khớp bản đồ');
  await decision.getByRole('button', { name: 'Phê duyệt 1 tin' }).click();
  await expect(decision.getByText('Cần chọn lý do.')).toBeVisible();
  await decision.getByLabel('Lý do').selectOption('MEETS_STANDARDS');
  await decision.getByRole('button', { name: 'Phê duyệt 1 tin' }).click();
  await expect(page.getByText(`Đã phê duyệt: ${title}`)).toBeVisible();
});

test('admin resolves a billing exception with a note', async ({ page, request, adminToken, brokerToken }, info) => {
  // Setup through the API: a bank account, a broker order reported as paid, a receipt that does not match.
  const bank = await (await request.get('/api/v1/billing/admin/bank', { headers: auth(adminToken) })).text();
  if (!bank.trim()) {
    const saved = await request.put('/api/v1/billing/admin/bank', {
      headers: auth(adminToken),
      data: {
        bankBin: '970436',
        bankName: 'Ngân hàng kiểm thử E2E',
        accountNumber: '0123456789',
        accountName: 'CONG TY E2E',
        expectedVersion: null,
      },
    });
    // 409: another project saved the bank account at the same moment, which is just as good.
    expect(saved.ok() || saved.status() === 409, await saved.text()).toBe(true);
  }
  // One open order per user and plan: each project uses its own paid plan so the orders are distinct.
  const plans = (await (await request.get('/api/v1/billing/plans')).json()) as Array<{
    code: string;
    priceVnd: number;
  }>;
  const paid = plans.filter((plan) => plan.priceVnd > 0);
  const slot = projectSlot(info.project.name);
  expect(paid.length, `needs more than ${slot} paid plans`).toBeGreaterThan(slot);
  const created = await request.post('/api/v1/billing/orders', {
    headers: { ...auth(brokerToken), 'Idempotency-Key': `e2e-${info.project.name}-${Date.now()}` },
    data: { planCode: paid[slot].code },
  });
  expect(created.ok(), await created.text()).toBe(true);
  const order = (await created.json()) as { id: string; reference: string; status: string; amountVnd: number };
  if (order.status === 'CREATED') {
    expect(
      (await request.post(`/api/v1/billing/orders/${order.id}/reported`, { headers: auth(brokerToken) })).ok(),
    ).toBe(true);
  }
  if (order.status !== 'EXCEPTION') {
    const receipt = await request.post(`/api/v1/billing/admin/reconciliation/${order.id}/receipt`, {
      headers: auth(adminToken),
      data: { receivedAmountVnd: order.amountVnd - 9000, receivedReference: `CK ${order.reference}` },
    });
    expect(((await receipt.json()) as { status: string }).status).toBe('EXCEPTION');
  }

  await useSession(page, adminToken);
  await page.goto('/2026/nhadatchuan/admin/billing');
  await expect(page.getByRole('heading', { level: 1, name: 'Đơn hàng và đối soát' })).toBeVisible();
  await page.getByRole('button', { name: /^Cần đối chiếu thêm/ }).click();
  await page.getByRole('button', { name: `Xử lý ngoại lệ ${order.reference}` }).click();
  const dialog = page.getByRole('dialog', { name: `Xử lý ngoại lệ ${order.reference}` });
  await dialog.getByLabel('Cách xử lý').selectOption('APPROVE_WITH_NOTE');
  await dialog.getByLabel('Ghi chú xử lý').fill('Khách chuyển bổ sung 9.000đ, đã đối chiếu sao kê');
  await dialog.getByRole('button', { name: 'Lưu quyết định' }).click();
  await expect(page.getByText(`${order.reference}: Đã kích hoạt`)).toBeVisible();
});

test('admin changes a role with a reason and sees it in the history', async ({ page, request, adminToken }, info) => {
  const list = await request.get('/api/v1/admin/users?role=USER&size=100', { headers: auth(adminToken) });
  const users = ((await list.json()) as { items: Array<{ id: string; fullName: string; email: string | null }> }).items;
  const candidates = users
    .filter((u) => u.email && !u.email.startsWith('demo.'))
    .sort((a, b) => a.email!.localeCompare(b.email!));
  const target = candidates[projectSlot(info.project.name)];
  expect(target, 'the seed needs one USER account (other than the demo accounts) per project').toBeTruthy();

  await useSession(page, adminToken);
  await page.goto('/2026/nhadatchuan/admin/users');
  await page.getByLabel('Tìm theo tên hoặc email').fill(target!.email!);
  await page.getByRole('button', { name: 'Tìm', exact: true }).click();
  await page
    .getByRole('button', { name: `Đổi vai trò ${target!.fullName}` })
    .first()
    .click();
  const dialog = page.getByRole('dialog', { name: `Đổi vai trò: ${target!.fullName}` });
  await dialog.getByLabel('Vai trò mới').selectOption('OWNER');
  await dialog.getByLabel('Lý do').fill('Đã xác nhận là chủ nhà tự đăng');
  await dialog.getByRole('button', { name: 'Đổi vai trò' }).click();
  await expect(page.getByText(`${target!.fullName}: Người tìm nhà sang Chủ nhà`)).toBeVisible();

  await page
    .getByRole('button', { name: `Lịch sử ${target!.fullName}` })
    .first()
    .click();
  const history = page.getByRole('dialog', { name: `Lịch sử: ${target!.fullName}` });
  await expect(history.getByText('Đổi vai trò', { exact: true })).toBeVisible();
  await expect(history.getByText('Lý do: Đã xác nhận là chủ nhà tự đăng')).toBeVisible();
});
