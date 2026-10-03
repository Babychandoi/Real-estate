import { expect, test, type Page } from '@playwright/test';
import { KeyboardWalker } from './support/keyboard';
import { loadSession, openRoute, type Session } from './support/routeCatalog';
import { auditReflow, auditUi } from './support/targetAudit';

// Adversarial review of the W6-UX measurement (PR #25): can the DS-03/DS-06 audits and the focus checks fail at all?
//
// Part A (no backend): the audit functions are run on small synthetic pages. Tests marked `test.fail` document a
// BLIND SPOT: the assertion states what the audit should report, and today it does not. When the blind spot is fixed
// the test turns into an "unexpected pass" and the `test.fail` marker must be removed.
// Part B (seeded stack, PLAYWRIGHT_BASE_URL): the real pages are mutated in the browser (a smaller button, a smaller
// price, an overflowing element, a removed focus style) and the same audits must catch the mutation.
//
// The focus-indicator rule is the same in ux-dialogs.spec.ts `focusIsVisible` and support/keyboard.ts `check`
// (`boxShadow !== 'none'` counts as a focus ring), so KeyboardWalker stands for both here.

test.beforeEach(({ browserName }, testInfo) => {
  void browserName;
  test.skip(testInfo.project.name !== 'chromium-1440', 'sets its own viewports: runs once, in chromium-1440');
});

const PHONE = { viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true, deviceScaleFactor: 2 };

async function synthetic(page: Page, body: string, css = '') {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.setContent(
    `<!doctype html><html><head><style>
      *{box-sizing:border-box} body{margin:0;font:16px/1.4 sans-serif}
      :focus-visible{outline:2px solid #0050b3;outline-offset:2px}
      ${css}
    </style></head><body>${body}</body></html>`,
  );
}

test.describe('A. the audit functions on synthetic pages', () => {
  test('A1 positive: a 30 px button on a touch phone is a small target', async ({ page }) => {
    await synthetic(page, '<main><button style="height:30px;width:120px">Lưu</button></main>');
    const findings = await auditUi(page, { touch: true });
    expect(findings.map((f) => f.kind)).toContain('small-target');
  });

  test('A2 positive: a 10 px price is tiny text, a 13 px price is small important text', async ({ page }) => {
    await synthetic(
      page,
      '<main><p><span data-price style="font-size:10px">3,95 tỷ</span></p><p><span data-price style="font-size:13px">2,1 tỷ</span></p></main>',
    );
    const kinds = (await auditUi(page, { touch: false })).map((f) => f.kind);
    expect(kinds).toContain('tiny-text');
    expect(kinds).toContain('small-important-text');
  });

  test('A3 positive: a 600 px element at 390 px is a page-level sideways scroll', async ({ page }) => {
    await synthetic(page, '<main><div style="width:600px;height:20px;background:#ccc">rộng</div></main>');
    const kinds = (await auditReflow(page)).map((f) => f.kind);
    expect(kinds).toContain('page-scroll');
  });

  test('A4 BLIND SPOT: text typed in a 10 px input or shown in a 10 px select is never measured', async ({ page }) => {
    test.fail(true, 'auditUi only walks text nodes; input values, placeholders and <select> text are not text nodes');
    await synthetic(
      page,
      '<main><label>Giá <input value="3950000000" style="font-size:10px;height:44px;width:200px"></label>' +
        '<label>Sắp xếp <select style="font-size:10px;height:44px"><option>Mới nhất</option></select></label></main>',
    );
    const kinds = (await auditUi(page, { touch: true })).map((f) => f.kind);
    expect(kinds).toContain('tiny-text');
  });

  test('A5 BLIND SPOT: one hidden [aria-modal] element anywhere switches off the audit of the whole page', async ({
    page,
  }) => {
    test.fail(
      true,
      'hiddenTree() treats everything outside the FIRST [aria-modal="true"] in the DOM as covered, even a display:none one',
    );
    await synthetic(
      page,
      '<div role="dialog" aria-modal="true" style="display:none"><button>x</button></div>' +
        '<main><button style="height:20px;width:20px">+</button><span data-price style="font-size:9px">1 tỷ</span></main>',
    );
    const kinds = (await auditUi(page, { touch: true })).map((f) => f.kind);
    expect(kinds).toContain('small-target');
    expect(kinds).toContain('tiny-text');
  });

  test('A6 positive: body { overflow-x: hidden } does not hide a too-wide element from the reflow audit', async ({
    page,
  }) => {
    // The usual way to mask reflow failures. Chromium still reports the content width in
    // documentElement.scrollWidth when body's overflow is propagated to the viewport, so the audit sees it.
    await synthetic(
      page,
      '<main><p style="width:700px">Thông tin quan trọng bị cắt ở bên phải màn hình điện thoại</p></main>',
      'body{overflow-x:hidden}',
    );
    expect((await auditReflow(page)).map((f) => f.kind)).toContain('page-scroll');
  });

  test('A7 BLIND SPOT: a control with a permanent box-shadow and no focus style passes the focus check', async ({
    page,
  }) => {
    test.fail(true, 'focus "ring" = boxShadow !== none, without comparing with the unfocused look (shadow-sm buttons)');
    await synthetic(
      page,
      '<main><a href="#x">trước</a> <button id="b" style="outline:none;box-shadow:0 1px 2px rgba(0,0,0,.05)">Gửi yêu cầu</button></main>',
      'button:focus-visible{outline:none}',
    );
    const keys = new KeyboardWalker(page, 'A7');
    await keys.tabTo(page.locator('#b'));
    expect(keys.problems.join('\n')).toContain('no visible focus indicator');
  });

  test('A8 positive: the same control without the shadow is reported', async ({ page }) => {
    await synthetic(
      page,
      '<main><a href="#x">trước</a> <button id="b">Gửi yêu cầu</button></main>',
      'button:focus-visible{outline:none}',
    );
    const keys = new KeyboardWalker(page, 'A8');
    await keys.tabTo(page.locator('#b'));
    expect(keys.problems.join('\n')).toContain('no visible focus indicator');
  });

  test('A10 BLIND SPOT: Tailwind `outline-none` (2px solid transparent) counts as a visible focus indicator', async ({
    page,
  }) => {
    test.fail(true, 'the check reads outline-style/width only; a transparent outline colour is accepted as visible');
    await synthetic(
      page,
      '<main><a href="#x">trước</a> <input id="f" aria-label="Lọc tin đăng"></main>',
      // exactly what Tailwind 3 generates for `focus:outline-none`
      '#f:focus{outline:2px solid transparent;outline-offset:2px}',
    );
    const keys = new KeyboardWalker(page, 'A10');
    await keys.tabTo(page.locator('#f'));
    expect(keys.problems.join('\n')).toContain('no visible focus indicator');
  });

  test('A9 BLIND SPOT: a small button nested in a large link or [role=button] is never measured', async ({ page }) => {
    test.fail(true, 'auditUi keeps only the outermost of nested targets');
    await synthetic(
      page,
      '<main><div role="button" tabindex="0" style="display:block;height:120px;width:360px">Thẻ tin' +
        '<button aria-label="Lưu tin" style="width:20px;height:20px">♡</button></div></main>',
    );
    const kinds = (await auditUi(page, { touch: true })).map((f) => f.kind);
    expect(kinds).toContain('small-target');
  });
});

// --- B. real pages, mutated in the browser -------------------------------------------------------------------------

let session: Session;

test.describe('B. mutations of the real pages are caught', () => {
  test.beforeAll(async ({ playwright }, workerInfo) => {
    if (workerInfo.project.name !== 'chromium-1440') return;
    test.setTimeout(240_000);
    const request = await playwright.request.newContext({ baseURL: workerInfo.project.use.baseURL, timeout: 90_000 });
    session = await loadSession(request);
    await request.dispose();
  });

  test('B1 a header button shrunk to 30 px on a touch phone fails DS-03', async ({ browser }) => {
    const { page, close } = await openRoute(browser, session, { name: 'home', as: 'guest', path: () => '/' }, PHONE);
    expect(await auditUi(page, { touch: true })).toEqual([]);
    await page.addStyleTag({ content: '.ndc-header button{min-height:0!important;height:30px!important}' });
    const findings = await auditUi(page, { touch: true });
    expect(findings.filter((f) => f.kind === 'small-target').length).toBeGreaterThan(0);
    await close();
  });

  test('B2 a listing price at 10 px / 13 px fails DS-03', async ({ browser }) => {
    const spec = { name: 'listing', as: 'guest' as const, path: (s: Session) => `/listings/${s.ids.listing}` };
    const { page, close } = await openRoute(browser, session, spec, { viewport: { width: 1440, height: 900 } });
    expect(await auditUi(page, { touch: false })).toEqual([]);
    await page.addStyleTag({ content: '[data-price]{font-size:13px!important}' });
    expect((await auditUi(page, { touch: false })).map((f) => f.kind)).toContain('small-important-text');
    await page.addStyleTag({ content: '[data-price]{font-size:10px!important}' });
    expect((await auditUi(page, { touch: false })).map((f) => f.kind)).toContain('tiny-text');
    await close();
  });

  test('B3 an element 40 px wider than the phone fails DS-06 reflow', async ({ browser }) => {
    const { page, close } = await openRoute(browser, session, { name: 'home', as: 'guest', path: () => '/' }, PHONE);
    expect(await auditReflow(page)).toEqual([]);
    await page.evaluate(() => {
      const wide = document.createElement('div');
      wide.style.cssText = 'width:430px;height:10px';
      document.querySelector('main')?.prepend(wide);
    });
    expect((await auditReflow(page)).map((f) => f.kind)).toContain('page-scroll');
    await close();
  });

  test('B4 BLIND SPOT: the focus style removed from the primary button in the sign-in dialog is not noticed', async ({
    browser,
  }) => {
    test.fail(
      true,
      'primary Button has a resting shadow, so boxShadow is never "none" and the check passes without a ring',
    );
    const { page, close } = await openRoute(
      browser,
      session,
      { name: 'home', as: 'guest', path: () => '/' },
      {
        viewport: { width: 1440, height: 900 },
      },
    );
    await page.getByRole('button', { name: 'Đăng nhập' }).first().click();
    const dialog = page.getByRole('dialog', { name: 'Đăng nhập' });
    await expect(dialog).toBeVisible();
    const submit = dialog.getByRole('button', { name: 'Đăng nhập', exact: true }).last();
    // The mutation, on this one button only: no outline and no ring on focus; its resting shadow stays (what a
    // careless refactor of Button's focus classes would do).
    await submit.evaluate((el) => el.setAttribute('data-mut', ''));
    await page.addStyleTag({
      content:
        '[data-mut]:focus,[data-mut]:focus-visible{outline:none!important;--tw-ring-shadow:0 0 #0000!important;--tw-ring-offset-shadow:0 0 #0000!important}',
    });
    const keys = new KeyboardWalker(page, 'B4');
    await keys.tabTo(submit, { within: dialog });
    expect(await focusLooksDifferent(page), 'strict check: the mutated button really shows no focus').toBe(false);
    expect(keys.problems.join('\n')).toContain('no visible focus indicator on button "Đăng nhập"');
    await close();
  });

  test('B5 DEFECT missed by ux-dialogs: the compare picker search field shows no focus at all', async ({ browser }) => {
    test.fail(
      true,
      'app/routes/_public.compare.tsx:228 <input class="… focus:outline-none"> in a label without focus-within: the ' +
        'outline is transparent; the shipped checks accept it (A10) and never re-check the initially focused field',
    );
    const { page, close } = await openRoute(
      browser,
      session,
      { name: 'compare', as: 'guest', path: () => '/compare' },
      {
        viewport: { width: 1440, height: 900 },
      },
    );
    await page.getByRole('button', { name: 'Chọn tin ngay tại đây' }).click();
    const field = page.getByRole('textbox', { name: 'Lọc tin đăng' });
    await expect(field).toBeVisible();
    // Keyboard: away and back (Shift+Tab), so the field has keyboard focus.
    await page.keyboard.press('Tab');
    await page.keyboard.press('Shift+Tab');
    await expect(field).toBeFocused();
    expect(await focusLooksDifferent(page)).toBe(true);
    await close();
  });

  test('B6 the filter sheet has more Tab stops than the 12 Tabs ux-dialogs presses', async ({ browser }) => {
    const spec = { name: 'search', as: 'guest' as const, path: () => '/search?purpose=SALE' };
    const { page, close } = await openRoute(browser, session, spec, { viewport: { width: 1440, height: 900 } });
    await page.getByRole('button', { name: /^Bộ lọc/ }).click();
    const sheet = page.getByRole('dialog', { name: 'Bộ lọc' });
    await expect(sheet).toBeVisible();
    const count = await sheet.evaluate(
      (el) =>
        el.querySelectorAll('a[href],button:not([disabled]),input:not([disabled]),select,textarea,[tabindex="0"]')
          .length,
    );
    test.info().annotations.push({ type: 'tab-stops', description: `filter sheet: ${count}` });
    // More than 12 stops: the 12-Tab loop never reaches the wrap-around, so a broken trap at the end is not caught.
    expect(count).toBeGreaterThan(12);
    await close();
  });

  // Strict sweep: every Tab stop (up to 80) of a few core pages and overlays must look different when focused.
  const SWEEP: Array<{ name: string; path: string; as: 'guest' | 'buyer'; open?: (page: Page) => Promise<void> }> = [
    { name: 'home', path: '/', as: 'guest' },
    { name: 'search', path: '/search?purpose=SALE', as: 'guest' },
    { name: 'listing', path: 'LISTING', as: 'buyer' },
    {
      name: 'sign-in dialog',
      path: '/',
      as: 'guest',
      open: async (page) => {
        await page.getByRole('button', { name: 'Đăng nhập' }).first().click();
        await expect(page.getByRole('dialog', { name: 'Đăng nhập' })).toBeVisible();
      },
    },
    {
      name: 'filter sheet',
      path: '/search?purpose=SALE',
      as: 'guest',
      open: async (page) => {
        await page.getByRole('button', { name: /^Bộ lọc/ }).click();
        await expect(page.getByRole('dialog', { name: 'Bộ lọc' })).toBeVisible();
      },
    },
    {
      name: 'lead form',
      path: 'LISTING',
      as: 'buyer',
      open: async (page) => {
        await page.getByRole('button', { name: 'Hẹn xem & nhận tư vấn' }).first().click();
        await expect(page.getByRole('dialog', { name: 'Hẹn xem bất động sản' })).toBeVisible();
      },
    },
  ];
  for (const target of SWEEP) {
    test(`B7 strict focus sweep: ${target.name}`, async ({ browser }) => {
      test.setTimeout(180_000);
      const path = target.path === 'LISTING' ? `/listings/${session.ids.listing}` : target.path;
      const { page, close } = await openRoute(
        browser,
        session,
        { name: target.name, as: target.as, path: () => path },
        {
          viewport: { width: 1440, height: 900 },
        },
      );
      if (target.open) await target.open(page);
      const invisible = new Set<string>();
      const seen = new Set<string>();
      for (let step = 0; step < 80; step += 1) {
        await page.keyboard.press('Tab');
        await page.waitForTimeout(150);
        const id = await page.evaluate(() => {
          const el = document.activeElement as HTMLElement;
          if (!el.dataset.sweep) el.dataset.sweep = String(Math.random()).slice(2, 10);
          const name = (el.getAttribute('aria-label') || el.textContent || el.tagName).replace(/\s+/g, ' ').trim();
          return `${el.dataset.sweep}|${el.tagName.toLowerCase()} "${name.slice(0, 50)}"|${el.tabIndex}`;
        });
        const [key, label, tabIndex] = id.split('|');
        if (seen.has(key)) break; // wrapped around
        seen.add(key);
        if (tabIndex === '-1') continue;
        if (!(await focusLooksDifferent(page))) invisible.add(label);
      }
      test.info().annotations.push({ type: 'stops', description: `${seen.size} Tab stops` });
      expect([...invisible], `${target.name}: focused but looks the same as unfocused`).toEqual([]);
      await close();
    });
  }

  test('B8 a verified seeker never sees the "Xác minh eKYC để liên hệ" detour on a listing (contact CTA fix)', async ({
    browser,
  }) => {
    test.fail(
      true,
      'DEFECT: kycStatus starts as NONE and becomes LOADING only in an effect after the first commit, so the eKYC ' +
        'link is rendered once (and kyc_required_shown is tracked) for an already verified seeker',
    );
    const spec = { name: 'listing', as: 'buyer' as const, path: (s: Session) => `/listings/${s.ids.listing}` };
    const record = async (page: Page) => {
      // Every text the contact CTA ever shows, including states that last a single commit.
      await page.addInitScript(() => {
        const w = window as unknown as { __ctaSeen: string[] };
        w.__ctaSeen = [];
        new MutationObserver(() => {
          for (const text of ['Xác minh eKYC để liên hệ', 'Đang kiểm tra xác minh…', 'Hẹn xem & nhận tư vấn']) {
            if (!w.__ctaSeen.includes(text) && document.body?.innerText.includes(text)) w.__ctaSeen.push(text);
          }
        }).observe(document, { subtree: true, childList: true, characterData: true });
      });
    };
    const { page, close } = await openRoute(browser, session, spec, { viewport: { width: 1440, height: 900 } }, record);
    await expect(page.getByRole('button', { name: 'Hẹn xem & nhận tư vấn' }).first()).toBeVisible();
    const seen = await page.evaluate(() => (window as unknown as { __ctaSeen: string[] }).__ctaSeen);
    test.info().annotations.push({ type: 'cta texts seen', description: seen.join(' → ') });
    expect(seen).not.toContain('Xác minh eKYC để liên hệ');
    await close();
  });

  test('B9 with a dialog open, the page behind is out of the accessibility tree (virtual cursor)', async ({
    browser,
  }) => {
    test.fail(
      true,
      'DEFECT: Chromium keeps the page behind an aria-modal dialog in its accessibility tree (not ignored); nothing ' +
        'sets inert/aria-hidden on the app root, so a screen-reader virtual cursor can leave the dialog',
    );
    const { page, close } = await openRoute(
      browser,
      session,
      { name: 'home', as: 'guest', path: () => '/' },
      {
        viewport: { width: 1440, height: 900 },
      },
    );
    await page.getByRole('button', { name: 'Đăng nhập' }).first().click();
    await expect(page.getByRole('dialog', { name: 'Đăng nhập' })).toBeVisible();
    const cdp = await page.context().newCDPSession(page);
    const { nodes } = (await cdp.send('Accessibility.getFullAXTree')) as {
      nodes: Array<{ ignored: boolean; role?: { value: string }; name?: { value: string } }>;
    };
    const exposed = nodes
      .filter((n) => !n.ignored && n.role?.value === 'link')
      .map((n) => n.name?.value ?? '')
      .filter((name) => ['Khu vực', 'Dự án', 'Giới thiệu', 'Mua nhà'].includes(name));
    test.info().annotations.push({ type: 'page links exposed behind the dialog', description: exposed.join(', ') });
    // Chromium honours aria-modal; other engines/screen readers (older iOS VoiceOver) need inert or aria-hidden.
    expect(exposed).toEqual([]);
    await close();
  });
});

/**
 * Strict focus check: the focused element (or the label that draws a visually hidden input's focus, or its
 * stretched ::after) must LOOK different from the same element unfocused — an outline with a visible colour, a
 * changed box-shadow, background or border. The shipped checks only ask "is there an outline style or any shadow".
 */
async function focusLooksDifferent(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const el = document.activeElement as HTMLElement | null;
    if (!el || el === document.body) return false;
    const rect = el.getBoundingClientRect();
    const hidden = rect.width <= 2 || rect.height <= 2;
    const drawn = (hidden ? ((el as HTMLInputElement).labels?.[0] ?? el.closest('label')) : el) as HTMLElement | null;
    const targets = [drawn, el.closest('label'), el.parentElement].filter(Boolean) as HTMLElement[];
    const look = () =>
      targets
        .map((t) => {
          const cs = getComputedStyle(t);
          const after = getComputedStyle(t, '::after');
          const alpha = (color: string) => {
            const m = color.match(/rgba?\(([^)]+)\)/);
            if (!m) return 1;
            const parts = m[1].split(/[,\s/]+/).filter(Boolean);
            return parts.length > 3 ? parseFloat(parts[3]) : 1;
          };
          const outline =
            cs.outlineStyle !== 'none' && parseFloat(cs.outlineWidth) >= 1 && alpha(cs.outlineColor) > 0.1
              ? `${cs.outlineStyle} ${cs.outlineWidth} ${cs.outlineColor}`
              : 'none';
          return [outline, cs.boxShadow, cs.backgroundColor, cs.borderColor, after.boxShadow, after.outlineStyle].join(
            '|',
          );
        })
        .join('#');
    const focused = look();
    el.blur();
    const unfocused = look();
    el.focus({ preventScroll: true });
    return focused !== unfocused;
  });
}
