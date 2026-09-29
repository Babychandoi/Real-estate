import { expect, type APIRequestContext } from '@playwright/test';
import { apiLogin, demoPassword } from './helpers';

/**
 * Adversarial content for the admin overflow spec: very long unbroken strings, 150+ character Vietnamese titles,
 * multi-paragraph descriptions, long names and reasons. Everything is created through the public and staff APIs, so
 * the rows are the ones a real person could produce (the API limits still apply: title 200, name 150, email 254).
 * Each run uses its own tag, so it never clashes with the other specs that share the stack.
 */

const auth = (token: string) => ({ Authorization: `Bearer ${token}` });

/** A run tag made of letters only: the contact guard on listing text refuses digit runs that look like phone numbers. */
export function letterTag(seed = `${Date.now()}${Math.floor(Math.random() * 1e6)}`): string {
  return seed
    .split('')
    .map((digit) => 'abcdefghij'[Number(digit)])
    .join('');
}

export const unbroken = (length: number, tag = 'khongkhoangtrang') =>
  tag.repeat(Math.ceil(length / tag.length)).slice(0, length);

/** A URL with no break opportunity for the browser (hyphen, slash and dot break lines only in some engines). */
export const longUrl = (tag: string) => `https://example.invalid/${unbroken(280, `duong-dan-rat-dai-${tag}`)}`;

export const VIET_SENTENCE =
  'Căn nhà nằm ngay trung tâm, đường ô tô tránh nhau, gần trường học, chợ, bệnh viện và công viên, pháp lý minh bạch, sổ hồng riêng, sang tên trong ngày';

export const longVietnameseTitle = (tag: string) =>
  `Bán gấp nhà mặt tiền đường lớn ${tag} vị trí đắc địa kinh doanh sầm uất, pháp lý hoàn chỉnh, hỗ trợ vay ngân hàng đến bảy mươi phần trăm, nhà mới xây, nội thất cao cấp đầy đủ`.slice(
    0,
    198,
  );

/** Free text of a listing: the contact guard refuses links, so the unbroken "path" has no scheme. */
export const longParagraphs = (tag: string, withLink = true) =>
  [
    VIET_SENTENCE.repeat(6),
    `Đường dẫn tham khảo: ${withLink ? longUrl(tag) : longUrl(tag).replace('https://', '')}`,
    `Mã tham chiếu nội bộ ${unbroken(240, tag)} cần đối chiếu với hồ sơ gốc.`,
    VIET_SENTENCE.repeat(8),
  ].join('\n\n');

export interface AdversarialTokens {
  admin: string;
  broker: string;
  buyer: string;
}

export async function staffToken(request: APIRequestContext, email: string): Promise<string> {
  const response = await request.post('/api/v1/auth/admin/login', { data: { email, password: demoPassword() } });
  expect(response.ok(), `staff login for ${email} answered ${response.status()}`).toBe(true);
  return ((await response.json()) as { accessToken: string }).accessToken;
}

async function ok<T>(response: Awaited<ReturnType<APIRequestContext['get']>>, what: string): Promise<T> {
  const text = await response.text();
  expect(response.ok(), `${what} answered ${response.status()}: ${text.slice(0, 300)}`).toBe(true);
  return (text ? JSON.parse(text) : undefined) as T;
}

/**
 * Tops the broker's listing quota up (app.billing.quota-enforced) the way a real broker does: buys the standard plan
 * and staff records the exact transfer. The order is closed at once, so it never blocks the open order per user and
 * plan that admin.spec.ts needs for its own billing journey.
 */
async function ensureQuota(request: APIRequestContext, broker: string, admin: string, needed: number, tag: string) {
  const me = await ok<{ user?: { listingQuotaRemaining?: number }; listingQuotaRemaining?: number }>(
    await request.get('/api/v1/auth/me', { headers: auth(broker) }),
    'broker profile',
  );
  const remaining = me.user?.listingQuotaRemaining ?? me.listingQuotaRemaining ?? 0;
  if (remaining >= needed) return;
  const bank = await (await request.get('/api/v1/billing/admin/bank', { headers: auth(admin) })).text();
  if (!bank.trim()) {
    await request.put('/api/v1/billing/admin/bank', {
      headers: auth(admin),
      data: {
        bankBin: '970436',
        bankName: 'Ngân hàng kiểm thử E2E',
        accountNumber: '0123456789',
        accountName: 'CONG TY E2E',
        expectedVersion: null,
      },
    });
  }
  const order = await ok<{ id: string; reference: string; status: string; amountVnd: number }>(
    await request.post('/api/v1/billing/orders', {
      headers: { ...auth(broker), 'Idempotency-Key': `overflow-${tag}-a` },
      data: { planCode: 'STANDARD' },
    }),
    'top-up order',
  );
  if (order.status === 'CREATED') {
    await ok(await request.post(`/api/v1/billing/orders/${order.id}/reported`, { headers: auth(broker) }), 'reported');
  }
  await ok(
    await request.post(`/api/v1/billing/admin/reconciliation/${order.id}/receipt`, {
      headers: auth(admin),
      data: { receivedAmountVnd: order.amountVnd, receivedReference: `CK ${order.reference}` },
    }),
    'exact receipt',
  );
}

/** A second order whose receipt does not match (huge amount, long reference) and is then rejected with a long note. */
async function seedBillingException(request: APIRequestContext, broker: string, admin: string, tag: string) {
  const order = await ok<{ id: string; reference: string; status: string; amountVnd: number }>(
    await request.post('/api/v1/billing/orders', {
      headers: { ...auth(broker), 'Idempotency-Key': `overflow-${tag}-b` },
      data: { planCode: 'STANDARD' },
    }),
    'exception order',
  );
  if (order.status === 'CREATED') {
    await ok(await request.post(`/api/v1/billing/orders/${order.id}/reported`, { headers: auth(broker) }), 'reported');
  }
  await ok(
    await request.post(`/api/v1/billing/admin/reconciliation/${order.id}/receipt`, {
      headers: auth(admin),
      data: {
        receivedAmountVnd: 99_999_999_999_999,
        receivedReference: `CK ${unbroken(90, `noidungck${tag}`)}`,
        note: `${VIET_SENTENCE.repeat(2)} ${unbroken(150, `ghichudoisoat${tag}`)}`,
      },
    }),
    'mismatching receipt',
  );
  await ok(
    await request.post(`/api/v1/billing/admin/reconciliation/${order.id}/resolve`, {
      headers: auth(admin),
      data: {
        resolution: 'REJECT',
        note: `${VIET_SENTENCE.repeat(2)} ${unbroken(150, `ghichuxuly${tag}`)}`,
      },
    }),
    'resolve exception',
  );
}

export interface SeededAdversarial {
  tag: string;
  /** False when the requester's daily lead quota was used up (the lead steps are skipped then). */
  leadCreated: boolean;
  /** Staff session for the spec (created after the adversarial rows). */
  adminToken: string;
  listingTitles: string[];
  userName: string;
  userEmail: string;
  articleTitle: string;
  projectName: string;
  /** Data areas that could not be created through the API (each entry says why). */
  gaps: string[];
}

/** Creates one set of adversarial rows for every admin page. */
export async function seedAdversarial(request: APIRequestContext, seed?: string): Promise<SeededAdversarial> {
  const tag = letterTag(seed);
  const gaps: string[] = [];
  const admin = await staffToken(request, 'demo.admin@bds.local');
  const broker = await apiLogin(request, 'demo.broker@bds.local');
  const buyer = await apiLogin(request, 'demo.user@bds.local');

  await ensureQuota(request, broker, admin, 4, tag);
  try {
    await seedBillingException(request, broker, admin, tag);
  } catch (error) {
    gaps.push(`billing exception order: ${String(error).slice(0, 200)}`);
  }

  // Listings, all submitted: [0] long Vietnamese title (rejected with a long note), [1] a title ending in a 150
  // character unbroken token (claimed, then ownership evidence), [2] approved and public (leads and reports hang on
  // it), [3] left unclaimed.
  const listingTitles: string[] = [];
  const listingIds: string[] = [];
  const revisionIds: string[] = [];
  const titles = [
    longVietnameseTitle(tag),
    `Nha pho ${unbroken(180, `tieudekhongngat${tag}`)}`.slice(0, 198),
    `Căn hộ view sông ${tag} tầng cao thoáng mát cực đẹp, ${VIET_SENTENCE}`.slice(0, 195),
    `Đất nền dự án ${unbroken(60, `datnen${tag}`)} sổ đỏ trao tay, ${VIET_SENTENCE}`.slice(0, 195),
  ];
  for (const [index, title] of titles.entries()) {
    const created = await ok<{ listingId: string; revisionId: string }>(
      await request.post('/api/v1/listings', {
        headers: auth(broker),
        data: {
          purpose: index === 2 ? 'RENT' : 'SALE',
          propertyType: ['HOUSE', 'TOWNHOUSE', 'APARTMENT', 'LAND'][index],
          title,
          priceVnd: 98_765_432_100_000 + index,
          areaM2: 123456.78,
          description: longParagraphs(tag, false).slice(0, 4900),
          provinceCode: '79',
          districtCode: '760',
          wardCode: '26734',
          addressSummary: `Số nhà ${unbroken(190, `diachikhongngat${tag}`)}`,
          direction: 'Đông Nam',
          legalStatus: 'Sổ hồng riêng',
        },
      }),
      'create listing',
    );
    await ok(await request.post(`/api/v1/listings/${created.listingId}/submit`, { headers: auth(broker) }), 'submit');
    listingTitles.push(title);
    listingIds.push(created.listingId);
    revisionIds.push(created.revisionId);
  }

  const reasons = await ok<{ approve: Array<{ code: string }>; reject: Array<{ code: string }> }>(
    await request.get('/api/v1/moderation/reasons', { headers: auth(admin) }),
    'moderation reasons',
  );
  for (const id of [listingIds[0], listingIds[1], listingIds[2]]) {
    await ok(await request.post(`/api/v1/moderation/listings/${id}/claim`, { headers: auth(admin) }), 'claim');
  }
  await ok(
    await request.post(`/api/v1/moderation/listings/${listingIds[0]}/reject`, {
      headers: auth(admin),
      data: {
        revisionId: revisionIds[0],
        reasonCode: reasons.reject[0].code,
        reasonDetail: `${VIET_SENTENCE.repeat(4)} ${unbroken(300, `lydotuchoi${tag}`)}`,
      },
    }),
    'reject listing',
  );
  await ok(
    await request.post(`/api/v1/moderation/listings/${listingIds[2]}/approve`, {
      headers: auth(admin),
      data: {
        revisionId: revisionIds[2],
        reasonCode: reasons.approve[0].code,
        note: `${VIET_SENTENCE.repeat(3)} ${unbroken(200, `ghichuduyet${tag}`)}`,
      },
    }),
    'approve listing',
  );
  const publicListingId = listingIds[2];

  // Users: a 150 character unbroken name and a ~250 character e-mail address.
  const userName = unbroken(150, `Nguyenvanhoangminhquocbao${tag}`);
  const userEmail = `${unbroken(48, `nguoidung${tag}`)}@${unbroken(40, 'ten-mien-rat-dai')}.${unbroken(30, 'phan-mo-rong')}.${unbroken(20, 'vn-test')}`;
  const registered = await request.post('/api/v1/auth/register', {
    data: { email: userEmail, password: 'Adversarial-2026-pass', name: userName, accountType: 'USER' },
  });
  if (!registered.ok()) gaps.push(`user registration answered ${registered.status()}`);
  const users = await ok<{ items: Array<{ id: string; email: string | null }> }>(
    await request.get(`/api/v1/admin/users?q=${encodeURIComponent(userEmail.slice(0, 40))}&size=5`, {
      headers: auth(admin),
    }),
    'find user',
  );
  const target = users.items.find((user) => user.email === userEmail);
  if (target) {
    const changed = await request.patch(`/api/v1/admin/users/${target.id}/role`, {
      headers: auth(admin),
      data: { role: 'OWNER', reason: `${VIET_SENTENCE.repeat(3)} ${unbroken(250, `lydodoivaitro${tag}`)}` },
    });
    if (!changed.ok()) gaps.push(`role change answered ${changed.status()}`);
  } else gaps.push('registered user not found in the admin list');

  // One lead on the approved listing. Only demo.user can send it (the owner must have a verified eKYC profile, and
  // demo.broker cannot write to its own listing) and a requester may send 10 a day, of which journeys.spec.ts needs
  // its own: one is all this spec takes, and when the quota is used up the lead steps are skipped, not failed.
  const lead = await request.post('/api/v1/public/leads', {
    headers: auth(buyer),
    data: {
      listingId: publicListingId,
      fullName: unbroken(150, `KhachHang${tag}`),
      phone: `09${String(10000000 + Math.floor(Math.random() * 89999999))}`,
      note: `${VIET_SENTENCE.repeat(3)}\n\n${longUrl(tag)}`,
      consentPolicy: true,
      requestType: 'VIEWING',
    },
  });
  const leadCreated = lead.ok();
  const reportIds: string[] = [];
  for (let index = 0; index < 3; index += 1) {
    const report = await request.post('/api/v1/public/reports', {
      headers: index === 0 ? undefined : auth(buyer),
      data: {
        listingId: publicListingId,
        category: ['OTHER', 'SCAM_DEPOSIT', 'INCORRECT_PRICE'][index],
        description: `${VIET_SENTENCE.repeat(5)}\n\n${unbroken(400, `moatavipham${tag}`)}`,
        evidenceUrls: longUrl(tag),
        reporterPhone: '0912345678',
      },
    });
    if (report.ok()) {
      const body = (await report.json()) as { id?: string; reportId?: string };
      const id = body.id ?? body.reportId;
      if (id) reportIds.push(id);
    } else gaps.push(`report answered ${report.status()}: ${(await report.text()).slice(0, 120)}`);
  }
  if (reportIds[0]) {
    await request.post(`/api/v1/reports/${reportIds[0]}/claim`, { headers: auth(admin) });
    await request.post(`/api/v1/reports/${reportIds[0]}/notes`, {
      headers: auth(admin),
      data: { note: `${VIET_SENTENCE.repeat(4)} ${unbroken(300, `ghichunoibo${tag}`)}` },
    });
  }

  // Ownership verification with long certificate, owner and document URL.
  const verification = await request.post(`/api/v1/listings/${listingIds[1]}/verifications`, {
    headers: auth(broker),
    data: {
      verificationType: 'CERTIFICATE_OF_OWNERSHIP',
      certificateNumber: unbroken(100, `GCN${tag}`),
      documentUrls: longUrl(tag),
      ownerNameOnDoc: unbroken(150, `Chusohuu${tag}`),
    },
  });
  if (!verification.ok())
    gaps.push(`verification answered ${verification.status()}: ${(await verification.text()).slice(0, 160)}`);

  // Project and CMS article.
  const projectName = `Khu đô thị ${unbroken(185, `tenduanrataidai${tag}`)}`;
  const project = await request.post('/api/v1/catalog/projects', {
    headers: auth(admin),
    data: {
      name: projectName,
      developerName: `Tập đoàn ${unbroken(140, `chudautu${tag}`)}`,
      provinceCode: 'HCM',
      districtCode: unbroken(50, `quan${tag}`),
      address: `Số 1 ${unbroken(240, `diachiduan${tag}`)}`,
      totalAreaM2: 987654321.5,
      totalBlocks: 99,
      totalUnits: 987654,
      handoverYear: 2031,
      legalLicenseNumber: unbroken(100, `GPXD${tag}`),
    },
  });
  if (!project.ok()) gaps.push(`project answered ${project.status()}: ${(await project.text()).slice(0, 160)}`);

  const articleTitle = `Hướng dẫn chi tiết thủ tục mua bán nhà đất ${tag}: ${VIET_SENTENCE}`.slice(0, 190);
  const article = await request.post('/api/v1/cms/articles', {
    headers: auth(admin),
    data: {
      slug: `bai-viet-${unbroken(140, `slugdai${tag}`)}`,
      category: 'KNOWLEDGE',
      title: articleTitle,
      summary: `${VIET_SENTENCE.repeat(3)} ${unbroken(200, `tomtat${tag}`)}`,
      contentHtml: `<p>${longParagraphs(tag)}</p>`,
      authorName: `Biên tập viên ${unbroken(100, `tacgia${tag}`)}`,
      legalReference: `Luật Đất đai ${unbroken(120, `thamchieu${tag}`)}`,
      metaDescription: VIET_SENTENCE.repeat(2),
      sourceName: unbroken(90, `nguon${tag}`),
      sourceUrl: longUrl(tag),
    },
  });
  if (!article.ok()) gaps.push(`article answered ${article.status()}: ${(await article.text()).slice(0, 160)}`);

  // A second admin session from a device with a 400 character unbroken User-Agent: the security page lists devices.
  const device = await request.post('/api/v1/auth/admin/login', {
    headers: { 'User-Agent': `Mozilla/5.0 ${unbroken(400, `TrinhDuyetLa${tag}`)}` },
    data: { email: 'demo.admin@bds.local', password: demoPassword() },
  });
  if (!device.ok()) gaps.push(`long user-agent session answered ${device.status()}`);

  // The session the spec itself uses is the newest one.
  const adminToken = await staffToken(request, 'demo.admin@bds.local');

  return { tag, leadCreated, adminToken, listingTitles, userName, userEmail, articleTitle, projectName, gaps };
}
