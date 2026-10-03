# Stream W6-UX — DS-03/04/05/06/08/15, R-2, R-7, UI-12 (completion round W6)

Branch `audit/w6-ux` from `origin/main` @ `13e41a2`; integrated npm-audit fix from `main` @ `16677a0`. Frontend only (one SQL test fixture, two opt-in options in
`scripts/e2e-local.sh`, CI steps). No backend change, no Flyway migration. Ports: backend 18141, preview 5341, Redis DB
15, DB prefix `w6ux_e2e`.

## 1. How to verify

```sh
cd frontend && npm ci && npm run lint && npx tsc -b && npx vitest run --testTimeout=30000 && npm run build && npm run check:bundle

# Every route/role, every overlay, the keyboard journeys, the existing suites (fresh seeded stack, 2 workers):
E2E_WORKERS=2 E2E_BACKEND_PORT=18141 E2E_FRONTEND_PORT=5341 E2E_REDIS_DB=15 E2E_DB_PREFIX=w6ux_e2e \
  scripts/e2e-local.sh --projects "chromium-1440 chromium-320" \
  --suites "navigation a11y auth-dialog authenticated responsive-auth-a11y search supply journeys admin engagement places media ux-audit ux-dialogs ui-consistency admin-overflow keyboard"

# R-2 needs more than 100 public listings (the UAT seed has 46 SALE):
E2E_SQL_AFTER_SEED_FILES=frontend/tests/e2e/fixtures/search-volume.sql E2E_WORKERS=2 E2E_BACKEND_PORT=18141 \
  E2E_FRONTEND_PORT=5341 E2E_REDIS_DB=15 E2E_DB_PREFIX=w6ux_e2e scripts/e2e-local.sh --projects chromium-1440 \
  --suites "search-consistency search"

# Optional: UX_AUDIT_DUMP=<dir> writes every DS-03/axe/reflow finding per route as JSON; UX_DIALOG_SHOTS=<dir> saves
# a screenshot of every open overlay.
```

CI (`.github/workflows/ci.yml`) now runs `ux-audit` and `ux-dialogs` after the visual step, seeds the volume fixture
(after the visual baselines, then re-syncs Elasticsearch), runs `search-consistency`, and runs `keyboard` after the
acceptance journeys. The workflow YAML parses (js-yaml); it was not executed on GitHub from this stream.

## 2. Row → status → evidence

Environment for every number below: MacBook (darwin, Apple Silicon), Chromium 140 (Playwright 1.55.1) headless,
shared `bds-test` PostGIS/Redis, backend jar of this branch in `APP_MODE=demo` with the UAT seed (clock
2026-09-01T03:00:00Z), search on the PostgreSQL path (Elasticsearch pointed at a closed port by the script), load
average 60–137 during the first runs (production + 3 other agents on the machine).

| Row | Status claimed | Evidence | Left |
|---|---|---|---|
| DS-03 | DONE | `tests/e2e/support/targetAudit.ts` measures, in the page, every visible text node (computed font size: nothing < 12 px; price `data-price`, status `role=status`, error `role=alert`/`data-error`, action = label of a control ≤ 64 px tall: ≥ 14 px), every interactive element (44×44 on a 390 px touch phone; 24×24 or the WCAG 2.5.8 spacing exception on desktop; inline-link, disabled, hidden-input→label and stretched-link rules written into the measurement), non-Lucide `<svg>` and emoji. `ux-audit.spec.ts`: all 52 route/role combinations at 390 touch + 1440. `ux-dialogs.spec.ts`: 32 overlays at 390 touch + 1440. **Same final spec on the base commit's build vs this branch (same seeded DB):** small-target 681 findings on 52/52 routes → 0; small important text 735 findings on 32 routes → 0; non-Lucide icon 0 → 0; emoji 0 → 0 (source scan: no emoji in `app/`); overlays failing 18/32 → 0/32. Allowlist `support/uxAllow.ts` is **empty**. Shared-source fixes, not per-page patches: `--control-sm` becomes 44 px under `pointer: coarse` (tokens.css), Button/Chip sm text 14 px, Checkbox/Radio/DataTable label targets, footer/header/breadcrumb links, login dialog, admin shell. | — |
| DS-04 | DONE (automatable part) | axe with `wcag2a, wcag2aa, wcag21a, wcag21aa, wcag22aa` on every route **as its role** (guest, seeker, poster/broker, moderator, admin) at 390 and 1440 (`ux-audit`), and inside every dialog/sheet/menu/filter sheet/map point sheet/gallery lightbox (`ux-dialogs`). Base build: color-contrast (admin leads hub, `opacity-75` caption) and link-in-text-block (broker workspace history) → fixed; final 0 violations. Overlays: no focus leak over 12 Tabs, visible focus at every stop, Escape closes and focus returns to the opener (fixed: account menu did not return focus). Contrast is measured on the real rendered colour pairs by axe. | Real screen readers: EXTERNAL, script in §6 |
| DS-15 (a11y part) | DONE (automatable part) | as DS-04, explicitly including filter sheet (also with an invalid price range), map point sheet (390 px, real marker click), gallery lightbox with 20 photos (arrow keys, Escape, focus return) | Screen readers EXTERNAL; CWV p75 is not this stream (S8/EXTERNAL) |
| R-7 (automatable part) | DONE | `keyboard.spec.ts` (`support/keyboard.ts`): Tab/Shift+Tab/Enter/Space/arrows/typing only — (1) seeker: skip link → search box → result card → detail → contact form → sent, at 1440 **and** 390; (2) owner: 4-step wizard → submitted; (3) moderator: that submission → review → approve with reason; (4) admin: report case → conclusion with note. At each stop: focus ring visible (a visually hidden radio must show it on its label), focused control not covered by a sticky bar/overlay (WCAG 2.4.11, element under the centre point), focus stays in an open dialog. Base build: journey 1 failed (focus under the sticky header ×3, invisible focus on the lead form's request-type radios) → fixed with `scroll-padding-top/bottom` and a label focus ring; final **5/5**. Native `<select>`: on macOS the OS popup opens on ArrowDown, so the value is set with `selectOption` on the focused element and annotated (on Linux CI the arrow keys change it). | Screen readers EXTERNAL |
| DS-06 | DONE | `ux-audit` on every route: reflow at 390 px; at 200 % browser zoom (1280 window ⇒ 640 CSS px, deviceScaleFactor 2); at 200 % text (root font-size 200 % at 1280). Checks: no page-level sideways scroll (tables/tab lists/navs may scroll in their frame), no clipped text, sticky/fixed bars ≤ 1/3 of the viewport. Base build: page-scroll on 18 routes (signed-in header at 200 % text: 1606/1768 px wide; analytics funnel), sticky overlap on 3 listing routes at 200 % zoom → 0. Plus a deterministic long-content test (240-character unbroken title/address/description/seller, huge rent): 3017 px wide at 390 px before, fits after. 360/1024 stay covered by `responsive-auth-a11y.spec.ts`, 320–1920 gutters by `ui-consistency.spec.ts` (both green). | — |
| DS-05 | PARTIAL | Table 8.3 + PAGE_MATRIX compared clause by clause (§4). Fixed: gallery swipe (touch/pen, 48 px, unit test), dead split-map CSS removed, contact CTA no longer flips to "Xác minh eKYC" while the status loads. Remaining deviations are product decisions or larger work (§4). | Owner decisions in §4 |
| DS-08 | DONE for the listed components; N/A states in §5 | `app/test/component-states.test.tsx` (34 tests) renders loading/empty/error/disabled/long states of SearchBox, FilterSheet, ListingCard, TrustBadge, Gallery, FormField, DataTable, Dialog, Toast, EmptyState, ErrorState, InlineFeedback, Pagination/LoadMore. Implemented the missing ones: SearchBox "no place matched" + long keyword; Gallery viewer image error + swipe; ContactPanel loading CTA; FormField 14 px errors + wrapping; wrap rules on Toast/InlineFeedback/Empty/ErrorState/compare cells; Dialog focusable scrolling body. | Initial loading/no eKYC detour has regression B8 (§9); lead duplicate replay/unavailable and Compare refresh-error transitions have dedicated tests (§11) |
| R-2 (frontend) | DONE | `search-consistency.spec.ts` 4/4 on a stack with `fixtures/search-volume.sql` (143 public SALE): URL → controls (purpose, sort, type, price range, beds), sheet edit → canonical URL, reload, Back, and the API request's filter params equal the URL's every time; "Xem thêm" reaches **all 143** results, 0 duplicates, ends with "Đã hiển thị tất cả 143 tin"; map count = list total after a real map pan (and `/listings/map` total = `/listings/search` total for one bbox: 133 = 133); every rent card price and the rent detail price end in "/tháng", no sale price does. `search.spec.ts` 6/6 on the same data. These are acceptance tests of S2's implementation (no product change was needed). | — |
| UI-12 | PARTIAL-EXTERNAL | `KycScopePanel.test.tsx` (3): unset/blank `VITE_KYC_RETENTION_NOTICE` shows a plain "chưa được công bố" sentence with no invented duration + privacy link; a configured notice renders verbatim without the fallback. The unset sentence was reworded to read correctly. | The retention wording and period are the owner's decision |

## 3. Checks of the first implementation (before review fixes; latest checks in §9)

- `npm run lint`: 0 problems. `npx tsc -b`: 0 errors. `npx vitest run`: **294/294** in 43 files. `npm run build`: OK.
- `npm run check:bundle`: all routes ok after **+1 kB on eight routes** with a written reason in
  `bundle-budget.json` (they were 0.1–0.3 kB under their ceiling; growth +0.4–0.6 kB, base → branch:
  `/` 137.7→138.2, `/search` 149.7→150.3, `/compare` 138.7→139.1, seller 139.8→140.3, `/my-inquiries` 137.9→138.4,
  admin/projects 131.7→132.1, `/du-an` 140.7→141.2, `/tin-tuc` 141.7→142.1 kB gzip-1).
- Prettier: every touched TS/TSX/CSS file is clean (`prettier --list-different` on the diff: none). `docs/ui/DESIGN_SYSTEM.md`
  and `ci.yml` were not reformatted (both already differ from prettier on the base commit).
- E2E, fresh seeded stack, chromium-1440 + chromium-320, 2 workers (`runA`): navigation 12, a11y 28, auth-dialog 4,
  authenticated 4, responsive-auth-a11y 10, search 12, supply 4, journeys 6, admin 6, engagement 14, places 12, media 4 —
  **116/116**; keyboard 5/5. That run found regressions in the new/heavy suites (sign-in dialog 828 px > 90 % of 900,
  analytics label column collapsed, a 12 px shortlist meta line, a gallery route rewrite on a 429); fixed in `5fac370`,
  then on a second fresh stack: ux-audit + ux-dialogs + ui-consistency + admin-overflow **184/189** (`runB`: 3 dialogs
  with a non-focusable scrolling body → fixed in `6d22d60`; 1 backend 500 `DataAccessResourceFailureException` on login
  at 04:20 while the shared infra was being recreated by the coordinator; 1 click timeout that passed on the next run),
  then **201/202** (`runC`: the adversarial rows seeded by admin-overflow exposed the listing-detail long-text overflow →
  fixed in `6d22d60`), then final **ux-audit 53 + ux-dialogs 32 + keyboard 5 = 90/90**. ui-consistency (117 tests at
  chromium-1440) and admin-overflow (12) green in `runC`/`runB`.
- Visual (chromium-320/768/1440, android-chrome): **4/12 pass, the same 4 that pass on the base commit's build** in this
  environment; the other 8 (home/search at 320/1440/android, search 768, compare android) already fail on the base build
  here (data/time/degraded-search notice) and were **not** regenerated in this first pass. This assessment was corrected by CI evidence during review (§9). compare 320/768/1440 and home 768 changed only
  because of this stream (footer 44 px rows, 14 px compare button, nav link minimum width): actual images reviewed,
  regenerated, re-run 4/4.
- Backend untouched: no `mvnw verify` run.

## 4. DS-05 — table 8.3 vs the implementation

Match (evidence in code): home container 1280 px, hero = search, real listings right after it, popular areas with
counts, 16 px phone gutter, one search CTA; search filter bar + result summary, split view exactly 55/45
(`minmax(0,11fr)_minmax(0,9fr)`), card ↔ pin highlight, list default on phones with a map button, filter bottom sheet
below 640 px, scroll position kept when returning from a detail; detail 2/3 + sticky 1/3 contact, phone price + facts,
bottom contact bar that no longer hides focus (scroll-padding), gallery with buttons + keys **and now swipe**; seller
identity/role/verification scope, paged inventory, response stats only above the sample threshold; wizard 4-step
stepper, autosave state, checklist, numeric/decimal keyboards, errors at the field; broker task queue and cards with one
main action on phones; admin server-side filters, tables scrolling in their own frame, mobile menu.

Deviations that need an owner decision or larger work (not changed here):
1. No search history on home/search ("giữ lịch sử tìm kiếm"): needs storage + a privacy/consent decision.
2. Split list/map is opt-in (default `list`), not the default on wide screens.
3. Listing detail has no location section/map (address line marked "vị trí gần đúng" only): needs a precision policy.
4. Seller inventory has no purpose/type filter (likely API work).
5. Broker leads are cards at every width — no table with column choice, no explicit "next action" field (data model).
6. Filter sheet is a right-hand panel from 640 px (tablets), a bottom sheet only on phones.
7. Admin detail views are centred dialogs, not drawers — **agreed owner decision** (ui-consistency.spec.ts §3); table
   8.3 should be updated, not the code.
8. Admin tables have no compact density; detail/seller/wizard containers are `max-w-6xl` (1152 px), not 1280.
9. Wizard step 1 is long on phones; the preview is a custom card, not `ListingCard`.

## 5. DS-08 — N/A states

N/A (no such state): Skeleton error/empty/disabled; TrustBadge loading/disabled (data is passed in); Dialog/Toast
loading/empty (the caller's body); Pagination long content (numbers). DataTable has no row-level disabled state
(select-all is disabled without rows). The previously missing dedicated lead duplicate/unavailable and Compare
refresh-error tests are recorded in §11.

## 6. Real screen readers — manual script (EXTERNAL)

NVDA 2024+ with Firefox and Chrome on Windows; VoiceOver on macOS Safari and iOS Safari. Stack: any seeded demo stack.
For each step note what is announced; a step fails when the name, role, state or the change is not announced.
1. Home: Tab once → "Bỏ qua điều hướng" link; Enter → focus lands in main. H (NVDA) / VO+Cmd+H: one h1 per page.
2. Search: the box announces "Tìm theo từ khóa hoặc địa điểm, combobox"; typing announces the suggestion count;
   arrow keys read the options; Enter applies. After a filter change the heading "N tin đang bán" is read (polite).
3. Filter sheet: opening announces "Bộ lọc, dialog"; virtual cursor/VO cannot leave the sheet (aria-modal; the page
   behind is `inert` and `aria-hidden`; verify this also on iOS VoiceOver); an inverted price range reads the error with the field.
4. Map view (390 px): marker buttons read "<loại>, giá …"; activating one opens "Tin trên bản đồ, dialog".
5. Detail: gallery button "Mở ảnh 1/N cỡ lớn"; in the viewer "Ảnh k/N" is announced on arrow keys; Escape returns to
   the button. Swipe (iOS) moves between photos.
6. Contact as a verified seeker: "Hẹn xem bất động sản, dialog"; request-type radios read checked state; the consent
   checkbox reads its label; after sending, "Yêu cầu đã được ghi nhận" is read; "Hoàn tất" returns focus.
7. Owner wizard: each step heading is focused and read after "Tiếp tục"; field errors are read with their field;
   autosave status ("Đã lưu lúc …") is announced politely.
8. Moderator: queue row "Đối chiếu <tiêu đề>"; the decision dialog's reason select and note are named; the result
   "Đã phê duyệt: <tiêu đề>" is announced.
9. Admin report: "Mở vụ việc CASE-…", the case dialog title, "Kết luận vi phạm…" dialog, success message.
10. Zoom/large text: iOS Dynamic Type at the largest size and Windows text size 200 %: no text cut, header not covering.

## 7. Before/after screenshots (five worst issues)

`/private/tmp/claude-501/-Users-connecty-Real-estate/a32839ff-3f25-435f-95eb-754cc3d06f65/scratchpad/w6-ux-shots/`
(`*-before.png` = base commit build, `*-after.png` = this branch, same seeded data; targets outlined in red on 1, 2, 5):
1. `1-footer-touch-targets-390-*`: every public page's footer links were 32 px tall touch targets (privacy-preferences
   button 20 px) → 44 px rows with the same visual rhythm.
2. `2-my-listings-actions-390-*`: `sm` buttons 36 px tall with 12 px labels on a phone → 44 px, 14 px.
3. `3-header-200pct-text-*`: signed-in header at 200 % text pushed the page to 1606 px wide → wraps, 1280 px, stops
   being sticky when it would cover more than a quarter of the window.
4. `4-listing-200pct-zoom-sticky-*`: at 200 % zoom the sticky header + fixed contact bar covered 150 of 400 px →
   header scrolls away on short screens; only the contact bar remains.
5. `5-login-dialog-390-*`: sign-in dialog with 42 px tabs, a 16 px tall "Quên mật khẩu?" link and 12 px "Đăng ký ngay"
   → 44 px targets, 14 px text, errors announced (`role=alert`), dialog capped at 90 dvh.

## 8. Environment notes

- At the start the shared `bds-test` Postgres/Redis had no published ports (another Compose project held 55432/56379).
  Until the coordinator fixed it, this stream ran on two private containers (`w6ux-pg`, `w6ux-redis` on 55441/56441)
  with a scratchpad copy of `e2e-local.sh`; both containers were removed. All final numbers (§3) come from the official
  `scripts/e2e-local.sh` on the repaired shared infra. Databases dropped, Redis DB 15 flushed.
- Production was not touched; no `.env*` file was edited.


## 9. Review fixes and continuation — 2026-10-03

All round-1 BLOCKER/MAJOR findings have code changes and regression checks on this branch. This is a self-review
of the full diff, not a new independent reviewer approval. Main integration: `16677a0`; no production change.

| Finding | Fix and evidence |
|---|---|
| BLOCKER: eight Chromium visual baselines stale in CI | `c3f1aa2` replaces home 320/1440/android, search 320/768/1440/android, compare android with reviewed actuals from [CI run 37100238694](https://github.com/Babychandoi/Real-estate/actions/runs/37100238694). Verified SHA-256 equality between all eight committed files and the corresponding downloaded CI actuals; reviewed top/footer and full desktop layouts again. Home/search changes are header icon sizing, 14 px action labels and 44 px footer rows. No clipped/overlapping content in those captures. The old local-only assessment in §3 is superseded. Fresh visual execution is required in CI. |
| MAJOR: modal background exposed to screen-reader virtual cursor | `763a62a`: shared `useModal` applies inert + aria-hidden outside the modal layer, reference-counts nested overlays and restores previous attributes; the toast announcement region stays reachable. `modal-inert.test.tsx` covers single/nested/restoration; reviewer B9 checks Chromium's actual accessibility tree. |
| MAJOR: focus audit accepts transparent outline/resting shadow | `1332072`: shared `focusProblem` compares focused/resting appearance; rejects transparent outlines and unchanged shadows. Every overlay is tabbed through its whole cycle (up to 150 stops) plus reverse wrap. Compare picker has a visible focus-within outline. Adversarial A7/A8/A10 and B4/B5 verify the checks and the actual field. |
| MINOR: controls/text omitted; hidden modal suppresses audit; nested target skipped | `e79ce4d`: measures input/select/textarea text; only visible modal layers suppress background measurement; nested controls are audited; missing seeded IDs and missing data-ready fail. Reviewer A4/A5/A9 are passing ordinary regression tests. |
| MINOR: disclosure arrow removed; eKYC button flashes initially | `5e41d3d`: analytics summary keeps native list-item marker; KYC status is keyed to the authenticated user and remains LOADING until fetched, ignoring obsolete responses. B8 observes every CTA text, including a single render, to reject the verification detour for a verified seeker. |
| MINOR/NIT: density, redundant region name, unused disabled API, fixtures | `5e41d3d`, `6b6812b`: tighter table cell/action padding preserving touch heights; scrolling dialog region named “Nội dung: …”; unused SearchBox disabled prop removed; SQL paths resolve from repo root and are quoted; bounded curl in CI. |

`5edcd37` promotes all 24 reviewer probes from the review branch (`e1fc2c7`) to normal CI regressions with no
expected-failure markers. CI runs them after the overlay audit, before the volume fixture and mutation journeys.
Local tests on the continued branch:

- `npm run lint`: zero problems; `npm run build` (`tsc -b` + Vite): passes.
- `npx vitest run --maxWorkers=1`: **302/302**, 45 files.
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:5341 PLAYWRIGHT_SUITE=ux-review-synthetic npx playwright test tests/e2e/review-ux-measurement.spec.ts --project=chromium-1440 --workers=1 -g 'the audit functions on synthetic pages'`: **10/10**, no app server or backend needed.
- `npm run check:bundle`: all 36 routes and shell pass after `9c257ba`: +1 kB ceilings on /kyc, /account and /khu-vuc with explicit reasons (shared nested modal isolation adds ~0.4–0.5 kB gzip-1; measured 137.2, 134.2, 140.1 kB). Compression and checking rules unchanged.
- CI YAML parses with js-yaml; `bash -n scripts/e2e-local.sh scripts/review-w6-ux-mutations.sh`; `git diff --check`: passes.

Fresh seeded full-stack E2E, visuals and real-page adversarial probes run on GitHub CI; no backend JVM/full stack
was started locally during this continuation because the shared machine is constrained. The earlier local review
run showed 15 ordinary passes plus 9 unexpected passes of expected-failure regressions after the fixes; those nine
markers are now removed. Source-mutant builds were not re-run in this continuation. Manual screen readers,
DS-05 product decisions, and UI-12 retention policy remain open as described above. Root histories/matrix are
updated by the coordinator after merge; this stream records evidence here.

## 10. CI failure follow-up — 2026-10-03

[Run 37103393159](https://github.com/Babychandoi/Real-estate/actions/runs/37103393159) at `cf74476` passed all
12 required Chromium visual comparisons and all 24 reviewer probes. The remaining required failures were two
39×44 px “Mở” buttons (reports/CMS), rent-range setup leaving focus on body, and CMS/project modal wrap-around.
The nine Firefox/WebKit/iOS visual mismatches were in the existing advisory step, not the required Chromium step.
Those advisory baselines were not changed in this follow-up.

- Shared `Button` now enforces minimum width matching its height token, including 44 px on coarse pointers.
- `focusableWithin` excludes native controls with negative tabindex, disabled fieldset descendants and descendants
  of CSS `display:none` ancestors. They cannot become an unreachable first/last stop in a modal focus trap.
- The invalid rent-range scenario leaves the field with native Tab, triggering validation while focus stays inside
  the sheet; its focus/axe/size/wrap assertions remain unchanged. `focusProblem` blur/refocus was verified to preserve
  the current stop, so that helper needed no change.
- The new regression fails against the previous helper: it includes all three excluded controls. With the fix,
  forward and reverse trap boundaries select the reachable controls. Focused overlay suites: **15/15**, three files.
- A temporary Vite harness using the production Dialog/Tabs/Button components passed native Chromium Tab/Shift+Tab
  and `focusProblem` at 390 and 1440 px: six stops per cycle, focus stays inside, no hidden/roving/disabled stops.
  “Mở” measured **45.28×44 px** on touch and **45.28×36 px** on desktop. The harness and dev server were removed.
- `npm run lint`, `npm run build`, touched-file Prettier and `git diff --check` pass. Full Vitest: **303/303**, 45 files.
  Bundle check: all **36 routes plus shell** pass. The ~0.1 kB gzip-1 shared accessibility growth put /verify-email
  and /my-inquiries just over their old ceilings; each ceiling increased 1 kB with the reason recorded in the budget.

Fresh CI verification of these fixes remains required; no local backend/full stack or production command was run.

## 11. DS-08 dedicated transition coverage — 2026-10-03

Three added unit regressions exercise the real components with controlled API promises, without a backend:

- `LeadConsultationModal.test.tsx`: lost response → announced error with name/phone/note/consent retained → retry
  with the same Idempotency-Key and payload → successful replay's original request code, with the form replaced by
  confirmation. This matches the server's `replayed: true` response, rather than inventing a duplicate error code.
- The same file rejects submission with the real `LISTING_NOT_ACCEPTING_LEADS` problem code: its explanation is
  announced, input remains available, success is absent and the unrelated KYC detour is absent.
- `_public.compare.test.tsx`: one ready column plus one failed refresh → error and retry → loading only for the
  failed column → ready. It asserts the ready listing stays visible and is not fetched again.

Focused tests **3/3**, TypeScript (`tsc -b`), lint and touched-file Prettier pass. These tests close the dedicated
coverage gaps formerly listed in §5; they introduce no production-code change or new backend acceptance claim.
