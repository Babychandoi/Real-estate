# Stream S0-FE — frontend foundation, design system base, E2E/CI repair (wave W1)

Branch `audit/s0-fe` (from `audit-2026-09-27` @ `e77db38`). No Flyway range, ports 18111 (backend) / 5311 (Vite),
Redis DB 2, database prefix `s0fe`. No backend code and no `frontend/nginx.conf` changes.

## 1. How to verify

```sh
cd frontend && npm ci
npm run lint && npm run typecheck && npm run format:check && npm run test:unit && npm run build && npm run check:bundle
cd .. && scripts/test-infra.sh status        # shared bds-test project must be up
scripts/e2e-local.sh                         # navigation, a11y, auth-dialog, authenticated on chromium-1440 + chromium-320
scripts/e2e-local.sh --skip-build --suites "" --visual-determinism
```

Results on the final commit (Node 20.20, JDK 17 for the backend jar):

| Check | Result |
|---|---|
| `npm run lint` (ESLint, `--max-warnings=0`) | 0 errors, 0 warnings |
| `npm run typecheck` (tsc, app + Playwright specs + configs) | 0 errors |
| `npm run format:check` (Prettier) | all files formatted |
| `npm run test:unit` (Vitest, jsdom) | **11 files, 101 tests passed** |
| `npm run build` (`tsc -b && vite build`) | success; production build contains no `/__ui` chunk |
| `npm run check:bundle` | all 27 routes + shell within budget (numbers in §3) |
| `npm audit --audit-level=high` | 0 vulnerabilities |
| `scripts/e2e-local.sh` (this branch's backend) | navigation **10/10**, a11y **28/28**, auth-dialog **4/4**, authenticated **4/4** |
| same suites with `E2E_BACKEND_JAR=<S0-BE branch jar>` | **46/46**, seed log `UAT seed done: clock=2026-09-01T03:00:00Z …` (fixed clock applied) |
| `--visual-determinism` (two independently seeded stacks, `E2E_VISUAL_SNAPSHOT_DIR`) | visual **6/6** identical (home/search/compare × 2 projects); reviewed baselines untouched |
| Cross-stream API check against S0-BE | `POST /auth/register` with `accountType: OWNER` → 201; `POST /events` with the exact `track()` payload: JSON + consent denied → 202 (stored without ids/utm), `text/plain` beacon + consent granted → 202 (ids + utm stored) |

The GitHub Actions workflow was validated structurally (parsed, step graph reviewed) but **not executed** — pushing is
not allowed for this stream. Firefox/WebKit projects run only in CI (only Chromium is installed locally).

## 2. Scope done — requirement → evidence

| ID | Delivered | Evidence | Status |
|---|---|---|---|
| F01.1 | `navigation.spec.ts`: search → first card link `Xem chi tiết: …` → `/listings/<slug>` (no UUID), h1 = card title, canonical and `<title>` match; legacy UUID URL rewritten to the slug; unknown listing/route pages | navigation 10/10 locally (2 projects); old `/Chi tiết/` selector and UUID regex removed | DONE |
| F01.2 (frontend side) | No fixed sleeps: pages expose `data-ready="true"` when their data settled (home, search, compare, detail incl. seller profile, seller, information, 404, forgot-password); helpers wait for it, fonts and eager images; CI and `e2e-local.sh` seed with `--app.uat-seed.clock=2026-09-01T03:00:00Z`; visual tests freeze the browser clock | `grep -r waitForTimeout frontend/tests` → none; determinism run above | DONE (clock itself = S0-BE, verified) |
| F01.3 | Visual, a11y, navigation, auth-dialog and signed-in flows are separate specs, separate CI steps and separate reports; a11y uses soft assertions so axe and overflow both report | `tests/e2e/*.spec.ts`; CI steps with `if: !cancelled()` | DONE |
| F01.4 | On failure CI uploads `frontend/playwright-report/<suite>`, `frontend/test-results/<suite>` (traces, videos, screenshot diffs) and the Compose logs; JUnit files always | `.github/workflows/ci.yml` job `e2e`; Playwright `trace/video: retain-on-failure` | DONE in workflow (first CI run pending) |
| F01.6 (smoke) | `authenticated.spec.ts`: API login (`/api/v1/auth/login`, password from `DEMO_ACCOUNT_PASSWORD`), token injected as `sessionStorage['bds_access_token']`; buyer: inquiries, profile, refused posting page; broker: my-listings, my-leads, broker workspace, create listing | authenticated 4/4 locally; CI step on `chromium-1440` | DONE (journeys = S11) |
| F01.7 | `npm run lint` = ESLint flat config (typescript-eslint, react-hooks rules/deps, jsx-a11y recommended, DS guard rails); `npm run typecheck` = tsc; the 66 initial findings fixed without behaviour change (24 unassociated labels, modal backdrops, keyboard-unreachable moderation cards, `any`, 10 intentional hook dependency lists annotated with their reason) | `eslint.config.js`; commit `4896b61` | DONE |
| F15.2 | Vite manifest (`vite build --manifest`, never shipped) + `scripts/check-bundle-budget.mjs`: per-route initial JS (gzip -9) against `bundle-budget.json`, fails when over budget or when a route module disappears; wired into `frontend-checks` | `scripts/check-bundle-budget.test.mjs` (2 tests); numbers §3 | DONE |
| F16.2 (frontend hook) | `useDocumentMeta({title, description, canonical, robots, og, jsonLd})` restores index.html defaults and removes created tags on unmount/data change; the detail page uses it (before, canonical/title/description leaked into the next route) and marks a missing listing `noindex` | `useDocumentMeta.test.tsx` (5), `listing-detail.meta.test.tsx` (real page, leaving to `/search` and `/`), navigation.spec “leaving a listing …” | DONE (prerender = S7) |
| UI-27 | `_account.contracts.tsx` and `entities/transaction/*` removed after grep + tsc showed no importer | commit `43c4746` | DONE |
| DS-01 | `app/styles/tokens.css` single source (colours as RGB channels, audit §8.2 type scale, spacing, radius 8/12/16/pill, shadows, focus ring, control 36/44/48, icon 16/20/24, motion, layers); Tailwind maps every existing class name to it, daisyUI theme derived from it; `docs/design-system.md` | `/__ui`; build fails on a missing token | DONE |
| DS-02 | Be Vietnam Pro self-hosted (`@fontsource/be-vietnam-pro`), weights 400/500/600/700, vietnamese + latin, woff2, `font-display: swap`; Google Fonts `<link>`s (incl. unused Material Symbols) removed | 8 fingerprinted woff2 in `/assets` (≈135 kB total, fetched per subset/weight on demand); browser check: 8 faces loaded; no Google Fonts reference left in `dist/` | DONE (CSP hosts: S5 branch already drops them) |
| DS-03 (mechanical) | Every emoji/symbol icon → Lucide (`aria-hidden`, icon-only buttons labelled); all 38 `text-[10px]/[11px]` → 12 px; ESLint now rejects both; kit controls are 44/48 px | commit `a34afea`; lint rules verified with a probe file | DONE for S0-FE (page-wide 44 px targets = S11) |
| DS-08 (kit) | FormField, DataTable/Queue (server sort, per-page selection, loading/refresh/empty/error/partial error/permission denied/conflict), Toast + InlineFeedback (success, retryable error, conflict, offline), TrustBadge/TrustPanel, Sheet — plus the rest of contract §12 | `overlays.test.tsx`, `controls.test.tsx`, `content.test.tsx` (36 tests); `/__ui` axe WCAG 2.2 AA: 0 violations at 320/1280/1440 px | PARTIAL: kit DONE; SearchBox, FilterBar, ListingCard, Gallery, ContactPanel, Compare are built by S2/S3 on the kit |
| §12 contract | Tokens, UI kit, `/__ui` (DEV or `VITE_ENABLE_UI_CATALOG=true`), `useDocumentMeta`, `track()`, Lucide-only, ≥ 12 px text, ≥ 44 px primary controls | see rows above | DONE |
| §4 money (frontend) | `app/shared/format/money.ts`: `formatMoney` (“3,95 tỷ”, “850 triệu”, “14,5 triệu/tháng”, `compact:false` = full digits), `formatUnitPrice` (“~48,2 triệu/m²”), `formatRentTerms`, v1 adapters; integer rounding (999.950.000 → “1 tỷ”); legacy `formatPriceVnd`/`calculateUnitPrice` delegate (decimal comma everywhere) | `money.test.ts` (28), `types.test.ts` (2) | DONE (card/detail/compare adoption = S2, F04.3) |
| §5/§12 analytics (web) | `track(name, properties, {listingId})`: catalog v1 web events only, catalogued keys only, nulls only where nullable, listing events require a UUID `listingId` (typed); consent per event from `bds.consent.analytics` (no choice = denied → no ids/utm); batches ≤ 50 (flush at 20 or 5 s), `fetch(keepalive)` with bearer token on hidden, `sendBeacon` (`text/plain`) on `pagehide`; 4xx dropped, network/429/5xx retried with backoff; consent withdrawal strips waiting events | `track.test.ts` (12); cross-check against S0-BE `EventIngestionService` above | DONE (wiring events into pages = S2/S3/S8; consent UI = S8) |
| P-09 / §2.5 (frontend) | `app/shared/auth/roles.ts` (USER, OWNER, BROKER, MODERATOR, ADMIN; POSTERS, STAFF, BROKER_WORKSPACE, priority, labels) + `routeAccess.ts`; routes and navigation (header, user menu, mobile menu, AdminShell) use the same map: OWNER reaches listings/new, my-listings, my-leads, billing and manages own listings; `/broker/workspace` stays BROKER/ADMIN; register dialog asks “Bạn là” as a radio group: Người tìm nhà / **Chủ nhà** / Môi giới BĐS | `roles.test.ts` (9), `ProtectedRoute.test.tsx` (5), auth-dialog.spec (register shows 3 types) | DONE (OWNER dashboard copy = S3) |

Extras found and fixed by the new suites (all small, no layout change): the login dialog never returned focus to its
opener (autoFocus ran before the opener was recorded) and its password toggle was a 16 px target (WCAG 2.5.8); on
`/search` at 320 px the list/map toggle lost its accessible name and the result toolbar overflowed; 18 pages rendered a
second `<main>` inside the layout's `<main>`; `ProtectedRoute` flashed “Vui lòng đăng nhập” to signed-in visitors while
the session was checked; 14 `<Link><Button>` nestings became `ButtonLink`. `WITHDRAWN` (S0-BE) added to the frontend
`LeadStatus`; owners/admins cannot select it.

## 3. Route bundle budget (initial JS, gzip -9, `npm run check:bundle`)

| Route | Now | Budget | Note |
|---|---|---|---|
| shell (`index.html`) | 101.5 kB | 112 kB | React, router, auth, layout, toast |
| `/` | 107.3 kB | 119 kB | |
| `/search` | **381.4 kB** | 420 kB | MapLibre imported statically; target ≤ 130 kB after F15.1 (S2) |
| `/listings/:slug` | 111.9 kB | 124 kB | |
| `/compare` | 108.4 kB | 120 kB | |
| `/nguoi-dang/:sellerId` | 107.4 kB | 119 kB | |
| information pages | 104.9 kB | 116 kB | |
| `/listings/new` | **381.9 kB** | 421 kB | map picker; target ≤ 140 kB when loaded on the location step (S3) |
| account pages | 103.4–107.0 kB | 114–118 kB | my-listings, my-leads, my-inquiries, broker workspace, billing, kyc, account |
| admin pages | 103.1–108.3 kB | 114–120 kB | |

Budgets = measurement + ~10 %; lower them when a route gets lighter. The main chunk grew ≈3 kB gzip in this stream
(Vite report: 98.46 → 101.74 kB; toast region, role map, `cn()` token config, useCallback-stable auth context).

## 4. Contract deviations

1. **`track()` signature**: `track(name, properties, context?)`; `context.listingId` is required by the types for
   `listing_detail_viewed` and `lead_form_opened` (the S0-BE validator needs it in the envelope, §5 lists it as
   `listingId?`).
2. **Consent without a stored choice** is treated as denied and events are still sent with `consent: "denied"` (no
   ids/utm), as the §5 server semantics describe. `getStoredAnalyticsConsent()` returns `null` so S8's banner can ask.
3. **`formatMoney`**: `compact` defaults to `true`; amounts below 1 triệu print every digit (“850.000 ₫”), not “850
   nghìn”; negative amounts keep the sign (price changes). §4 does not specify these cases.
4. **`useDocumentMeta(null)`** keeps the defaults (loading state); a missing listing sets `robots: noindex`.
5. **Budget measurement** is gzip level 9 over the static import closure; dynamic imports inside a route are not counted.
6. **Visual suite is non-blocking in CI** (`continue-on-error`) until S11 commits reviewed baselines (F01.5); its diffs are
   still uploaded. Remove `continue-on-error` at that point.
7. **`/__ui`** is a top-level route (no site header) so component checks are not mixed with layout issues.
8. **TrustBadge** shows `REJECTED` identity/ownership checks like “chưa xác minh” in public copy (the reason is for the
   owner and staff, not for visitors).

## 5. Known gaps (honest)

- **Visual baselines are stale.** Self-hosted fonts, 12 px labels, 36/44 px buttons and token changes alter pixels; the
  moved baselines (`tests/e2e/visual.spec.ts-snapshots/`, unchanged images) will not match. By instruction they are not
  regenerated here (S11 after review). Determinism is shown locally on Chromium only.
- **CI not executed** (no push). Firefox and WebKit results are unknown until the first run; tests open dialogs from the
  keyboard because WebKit does not focus buttons on click.
- **Kit adoption**: pages still use their own markup; only emoji, label sizes, `ButtonLink`, landmarks and the listed a11y
  fixes were changed. 10 legacy effects carry a justified `eslint-disable-next-line react-hooks/exhaustive-deps` (S2–S4 rewrites should remove them).
- `track()` is not called by any page yet; `/search` and `/listings/new` exceed their target budgets (MapLibre).
- Pre-existing, out of scope: the staff dropdown links “Tài liệu API Swagger” to `http://localhost:8080`; two daisyUI
  `loading` spinners remain in the listing wizard.
- The authenticated smoke runs on one desktop project because `/api/v1/auth/*` is limited to 10 requests/min per IP
  (current limiter; S5's v2 may allow more).

## 6. Manual / production steps

- No migrations. No new runtime environment variables in production.
- Build arg `VITE_ENABLE_UI_CATALOG` (frontend Dockerfile, default `false`): only CI's E2E image sets it; never set it for
  production images. Optional kill switch `VITE_ANALYTICS_DISABLED=true`.
- E2E variables: `PLAYWRIGHT_BASE_URL`, `PLAYWRIGHT_SUITE`, `DEMO_ACCOUNT_PASSWORD` (demo value in `.env.demo.example`),
  `E2E_REQUIRE_UI_CATALOG=1`; local helper overrides `E2E_BACKEND_PORT`, `E2E_FRONTEND_PORT`, `E2E_REDIS_DB`,
  `E2E_SEED_CLOCK`, `E2E_JAVA_HOME`, `E2E_BACKEND_JAR`, `E2E_VISUAL_SNAPSHOT_DIR`.
- Fonts are served from `/assets/*.woff2` (same origin, immutable cache). Deploy this before or with S5's CSP, which no
  longer allows the Google font hosts.
- Make `frontend-checks` and `e2e` required status checks when branch protection is enabled (F01.9, repo admin).
- `git config blame.ignoreRevsFile .git-blame-ignore-revs` hides the Prettier pass from blame.

## 7. Merge notes and follow-ups for other streams

- **Orchestrator / S0-BE:** in `ci.yml` keep S0-BE's `backend-tests` job instead of this branch's `backend` placeholder;
  keep the other jobs from here. The e2e job builds the backend image with S0-BE's Dockerfile (`-DskipTests`). The
  frontend Dockerfile gains two `ARG/ENV` lines; S5 also edits it (`COPY nginx/`) — trivial merge.
- **S2:** lazy-load MapLibre (F15.1) and lower the `/search` budget; move cards/detail/compare to `Money` with the
  purpose-aware money (F04.3); build FilterSheet on `Sheet`/`Chip`, paging on `LoadMore`, images on `ResponsiveImage`,
  trust on `TrustBadge`/`TrustPanel`; call `track()` for `search_performed`, `search_results_viewed`,
  `listing_detail_viewed`, `compare_opened`; keep `data-ready` markers when rewriting pages.
- **S3:** load the wizard map on the location step (budget), use `FormField`/`DataTable`, emit `lead_form_opened` and
  `kyc_required_shown`, add the withdraw action (`WITHDRAWN`), replace the daisyUI spinners, OWNER copy on my-listings.
- **S4:** admin queues on `DataTable` (states incl. conflict/permission), reject/approve dialogs on `Dialog`, `Toast`.
- **S5:** rate-limit policy for `/api/v1/events`; keep `style-src 'unsafe-inline'` (React style attributes) or move to
  `style-src-attr`.
- **S7:** `useDocumentMeta` for home/information/project/area pages; prerender should emit the same fields.
- **S8:** consent banner on `setAnalyticsConsent`/`onAnalyticsConsentChange`; `web_vital` via `track()`.
- **S11:** review diffs and regenerate the visual baselines, then make the visual step blocking; extend a11y and
  authenticated flows to account/admin pages and to mobile, Firefox and WebKit.

## 8. Commits

```
c828553 test(e2e): let e2e-local.sh run another backend build (E2E_BACKEND_JAR)
23ec1a1 feat(leads): know the WITHDRAWN lead status (contract §2.4)
fed2dc4 feat(analytics): match track() to the S0-BE event validator
e6aa006 docs: design system reference and frontend commands (DS-01)
d7109d9 ci: independent frontend-checks and e2e jobs with failure artefacts (F01.3/F01.4)
e404c7f build(frontend): per-route initial JS budget from the Vite manifest (F15.2)
2eb7017 test(e2e): independent navigation, a11y, visual and auth suites (F01)
4590b84 fix(a11y): login dialog returns focus and meets the 24px target size
c937932 fix(a11y): one main landmark per page
a34afea refactor(ui): Lucide instead of emoji icons, 12px minimum text (DS-03)
1aa6242 feat(ui): shared UI kit with every state and a /__ui catalog (DS-08, §12)
a797ebb feat(analytics): consent-aware track() with batching and beacons
c06d049 feat(seo): useDocumentMeta resets route metadata on leave (F16.2)
3686110 feat(auth): role constants with an OWNER persona that can post (P-09)
838044a feat(design-system): self-host Be Vietnam Pro (DS-02)
c8e4c1a feat(design-system): one token source mapped into Tailwind (DS-01)
fa6ada4 feat(format): shared money formatter per contract §4, with Vitest
4896b61 build(frontend): lint with ESLint and fix every finding (F01.7)
5c7a979 style(frontend): add Prettier and format app, tests and config
43c4746 chore(frontend): remove the unrouted deposit contract page (UI-27)
```

History files (`IMPLEMENTATION_PLANS_HISTORY.md`, `WALKTHROUGHS_HISTORY.md`) and `01_REQUIREMENTS.md` are left to the
orchestrator as `03_AGENT_RULES.md` requires.
