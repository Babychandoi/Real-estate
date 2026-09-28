# Stream S7-SEO — prerender + HTTP status, sitemap index, CMS public/preview/schedule, info, project/area pages, home (W4)

Branch `audit/s7-seo` from `audit-2026-09-27` @ `58ea814` (W1–W3 + UI redesign). Flyway **V090–V091** used (range
V090–V094; the contract's V075–V079 are unusable because `out-of-order` is off and V085 exists). Backend 18121, Nginx
smoke 5321, Redis DB 9, ES prefix `s7`.

## 1. How to verify

```sh
eval "$(scripts/test-infra.sh env)"; export BDS_TEST_ES_PREFIX=s7
cd backend && sh mvnw -B -ntp verify                       # or -Dtest='SeoPrerenderTests,SitemapTests,CmsPublishingTests'
cd frontend && npm ci && npm run lint && npx tsc -b && npx vitest run --testTimeout=30000 && npm run build && npm run check:bundle
scripts/seo-smoke.sh            # real Nginx image + config + backend jar + UAT seed; no browser, no JavaScript
scripts/verify-prerender.sh https://nhadatchuan.online      # the same no-JS checks against any deployment
```

| Check | Result (final commit) |
|---|---|
| `mvnw verify` | **350 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** |
| targeted: `SeoPrerenderTests` 12, `SitemapTests` 2, `CmsPublishingTests` 4, `BdsApplicationTests#cms*/#projectCatalog*` 2 | all pass |
| `npm run lint` (0 warnings) / `npx tsc -b` | clean |
| `npx vitest run` | **27 files, 191 tests passed** (+3 files / +10 tests: `prerenderHead.test.ts`, `seo-pages.test.tsx`, `admin-cms.test.tsx`) |
| `npm run build` + `check:bundle` | success, every route within budget (§4) |
| prettier on touched files | clean (`_public.listings.new.tsx` was already unformatted on the base; not touched) |
| `nginx -t` (nginx:1.27-alpine, repo config) | ok |
| `scripts/seo-smoke.sh` | **verify-prerender 50/50, verify-headers 153/153, fallback ok** (backend stopped → static shell 200) |

## 2. Architecture (prerender at the render layer, 00_PLAN decision)

```
browser/crawler ──► Nginx ──(static file?)──► /assets, favicon …
                        └──(page)── try_files $uri @prerender ──► backend GET /render/<path>?<query>
                                                                   │  route table (PrerenderService)
                                                                   │  data from the public read models
                                                                   ▼
                              built index.html (SpaShell: fetched from Nginx, cached 1 min, last good kept)
                              + <title>, description, robots, canonical, og:*, JSON-LD (data-prerender)
                              + main content in #root (<div data-prerender-content>)
                              → 200 / 301 / 404 / 410, Cache-Control, X-Robots-Tag
        backend down / prerender error (502/503/504) ──► @spa_shell: static index.html (200)
```

- The SPA mounts over the server content (`createRoot` replaces it). Injected head tags carry `data-prerender` and,
  when they replaced a shell default, `data-default`. `resetPrerenderedHeadOnNavigation` (router subscription registered
  before `RouterProvider`) restores the defaults on the first client-side navigation; `useDocumentMeta` treats a
  prerendered tag's previous value as its default and replaces prerendered JSON-LD instead of duplicating it. So a
  crawler that runs JavaScript keeps the page's own metadata, and metadata never leaks into the next route.
- Any exception in the render path answers 503 → Nginx serves the static shell: SEO can degrade, the site cannot.

## 3. Requirement → evidence

Backend tests: `backend/src/test/java/com/company/bds/seo/` and `…/cms/CmsPublishingTests.java`. Frontend tests:
`frontend/app/shared/seo/prerenderHead.test.ts`, `frontend/app/routes/seo-pages.test.tsx`, `frontend/app/routes/admin-cms.test.tsx`.

| ID | Delivered | Evidence | Status |
|---|---|---|---|
| F16.1 | Initial HTML of home, listing, project, area, article (and lists, seller, search, info pages) carries title, description, canonical, robots, Open Graph, JSON-LD (WebSite+SearchAction, Product/Offer with monthly `UnitPriceSpecification` for rent, Place, Article, BreadcrumbList) and the main content (h1, facts, description, listing links, statistics, sanitised article body) | `SeoPrerenderTests.aListingPageHasItsTitleCanonicalJsonLdAndContentWithoutJavaScript`, `theHomePageShows…`, `projectPagesShow…`, `areaStatistics…`, `articlesArePublic…`; `scripts/seo-smoke.sh` (real Nginx, 50 checks) | DONE |
| F16.3 | 404 unknown route/listing/project/area/article; 410 listing that was public (S2 rule), locked project, unpublished article; 301 id→slug, trailing slash (query kept); drafts 404 without leaking the title | `anIdOrATrailingSlashRedirects…`, `missingListingsAre404…410`, `unknownRoutesAre404…`, `projectPages…` (410 locked), `articlesArePublic…` (410); smoke `listing-slash 301`, `gone 410` | DONE |
| F16.4 | Sitemap index → `static`, `areas`, `projects`, `articles`, `listings-<n>` (≤ 10 000 URLs, lastmod per URL and per part). Listing parts = keyset ranges of `listing_public_read.listing_id` computed by one window aggregate (the snapshot), each part one bounded range read of two columns; snapshot and parts cached 10 min under the content generation (CMS/project changes invalidate at once) | `SitemapTests.theIndexSplitsMoreThanTenThousandListings…` (10 050 listings: 2 parts, every public listing exactly once, lastmod present, index ≤ 10 statements, each part ≤ 4 statements), `unknownParts…` | DONE |
| F16.5 | `/search` indexable only as `?purpose=SALE|RENT`; any other parameter (or an invalid one) → `noindex,follow` with canonical to the plain purpose URL; account/tool/admin/token pages `noindex,nofollow` + `X-Robots-Tag` + `no-store`; robots.txt (dynamic, from `app.public-base-url`) disallows `/api/` (except public media), `/render/`, previews and account pages — the hidden admin path is deliberately **not** listed | `onlyThePlainSearchPagesAreIndexable…`, `unknownRoutesAre404AndAppRoutesAreNoindexShells`, `unknownPartsAre404AndRobotsPointsAtTheIndex` | DONE |
| F16.6 | Search Console | steps in §6 | EXTERNAL |
| UI-01 | Home: search form first (also works without JS in the prerendered HTML), newest real listings, then areas/projects/articles **only when they have data** (no counts invented, zero-listing entries left out); demo title "BDS WF 2026…" removed from `index.html` | `theHomePageShowsRealListings…` (no "N+" figures), home page code (`HomeExtras` lazy chunk) | DONE |
| UI-15 | `/about`, `/terms`, `/privacy`, `/contact`: operator block from configuration `APP_OPERATOR_*` (legal name, registration, tax code, address, representative, e-mail, phone, hours) — unset fields say "Chưa có dữ liệu"; approved LEGAL_POLICY articles linked; prerendered with the same data | `informationPagesShowTheConfiguredOperator…` (backend), `seo-pages.test.tsx › information pages` | DONE (real entity details = EXTERNAL, product owner) |
| UI-16 | Real 404 at the render layer (was SPA fallback 200) | `unknownRoutesAre404…`; smoke `missing 404`; `verify-headers.sh` §8 | DONE |
| UI-25 | Admin CMS completes the public journey (create → draft edits → submit → publish now / schedule → preview link → reject with reason → new revision → unpublish), public link shown; admin projects edit the public profile (description, source, check date, website, amenities with sources, lock) and link the public page | `CmsPublishingTests` (4), `admin-cms.test.tsx`, `projectPagesShow…` (profile PUT + refusal without source) | DONE |
| P-06 | Public `/du-an`, `/du-an/:slug`, `/khu-vuc`, `/khu-vuc/:slug`: live inventory from the read model, median asking price per m² (sale) / per month (rent) **only with ≥ 5 listings**, method text, sample size, computation time and newest listing change; amenities only with source + check date; projects per area; listing strips via the public search API | `projectPagesShow…`, `areaStatisticsNeedFiveListingsBeforeAMedianIsShown`, `seo-pages.test.tsx` (median vs "Chưa đủ 5 tin") | DONE (content: projects need real descriptions/sources entered by staff) |
| P-07 | Approved CMS content is public; immutable revisions (DB trigger), new revision per edit, SUPERSEDED on publish, scheduled publishing (exact at read time + durable promotion by a locked scheduler), unpublish → 410, preview tokens (SHA-256 stored, 24 h, no-store + noindex), author, source name/URL, legal reference, reviewer date; jsoup allow-list sanitising on write **and** read | `CmsPublishingTests.*`, `articlesArePublicOnlyWhileLive…`, `previewPagesAreNeverIndexedOrCached` | DONE |
| P-12 | AI/3D/chat not simulated: `estimate-price` / `quality-score` stay 501 (existing tests); opening criteria per capability | `docs/product/ai-3d-chat-criteria.md`, `BdsApplicationTests` (501) | DONE (document) |
| D-09 (S7 part) | Project/area/home/sitemap payloads in the shared response cache (Redis, single-flight, jitter) 10 min, keyed by `seo_content_state.generation` which triggers bump on every `cms_articles`/`projects`/`project_amenities` change; public JSON `Cache-Control: public, max-age=300`; HTML `no-store` like the shell; previews/admin no-store | `PublicCatalogService`, `SitemapService`, V090/V091 triggers; `CmsPublishingTests.writesAreStaffOnly…` (no-store) | DONE |
| DS-05 (S7 pages) | Container 1280, 16 px gutters, listing grid 1/2/3 columns, project detail 2/3 + facts 1/3 (stacks on mobile), 44 px targets, breadcrumbs, loading/empty/error/not-found/gone states | components in `features/places/`, `routes/_public.{projects,areas,articles}.tsx` | DONE (visual baselines = S11) |

## 4. Bundle (gzip-1, `npm run check:bundle`)

| Route | Before | Now | Budget |
|---|---|---|---|
| shell | ~119.7 | 121.5 | 128 |
| `/` | — | 135.1 | 138 (was 135; shell +1.8 kB: seven lazy route entries + head reset; home extras are a lazy chunk) |
| `/du-an` | new | 138.0 | 141 |
| `/khu-vuc` | new | 136.5 | 140 |
| `/tin-tuc` | new | 139.0 | 142 |
| admin/cms | 124.1 | 140.2 | 147 (now on the admin kit like the S4 routes, 146–150) |

## 5. Contract deviations

1. **Flyway range** V090–V094 (orchestrator instruction). Note: the integration branch now also has **V095** (S8). A
   database that already applied V095 without V090/V091 would fail validation (out-of-order off); every environment must
   receive this wave in version order (fresh test DBs and the next production deploy do).
2. **Render endpoint** is `/render/**` on the backend (not under `/api`), so the SPA CSP of `nginx.conf` applies to pages
   unchanged; rate-limit policy `prerender` (600/min/IP) and `cms-preview` (60/15 min/IP) added to `RateLimitPolicies`.
3. **CMS persistence** moved from JPA entities to JDBC (`JdbcArticleStore`); API paths kept. v1 admin list keeps its array
   body and adds `X-Total-Count`; list rows no longer embed every revision (detail does). **Public** article responses
   dropped `reviewedBy` (a user id) and `publishedRevisionId`; the only consumer was the removed static info page.
   Article `canonicalUrl` is stored but the public canonical is always `/tin-tuc/<slug>`.
4. **HTML caching**: prerendered HTML is `no-store, no-cache, must-revalidate` (same as the static shell and what
   `verify-headers.sh` asserts); the 5–15 min policy of audit §7.3 is implemented on the page data (Redis) and public
   JSON (`max-age=300`), not on HTML.
5. `ResponseCachePort` (search module) is reused by catalog/seo for Redis caching; S9's ArchUnit rules should allow it
   or move the port to `shared`.
6. Statistics are **asking prices** of public listings, not transaction prices (method text says so); no price index.
7. Static `frontend/public/robots.txt` removed (Nginx proxies `/robots.txt` to the backend).

## 6. Production notes

**Deploy order:** backend image first (migrations V090/V091 are additive: columns, tables, triggers, indexes; V090 backfills
`published_at` of published articles; V091 creates `idx_lpr_district`/`idx_lpr_project` on the read model — seconds at
current size, `CREATE INDEX` without `CONCURRENTLY`, brief write lock on `listing_public_read`), then the frontend image
(new `nginx.conf`). If the new Nginx meets an old backend during a rollout, the old image answers `/render/**` with
401/403, which Nginx turns into the static shell (as for 405 and 502–504), so the site keeps working, just without
prerendering, until the backend is updated.

**Environment (all optional):** `APP_SEO_SHELL_LOCATION` (default `http://frontend:3000/index.html`, the Compose service),
`APP_SEO_SHELL_TTL` (PT1M), `APP_SEO_SITEMAP_CHUNK_SIZE` (10000), `APP_SEO_SITEMAP_TTL` (PT10M), `APP_CMS_PUBLISH_POLL`
(PT1M), `APP_OPERATOR_LEGAL_NAME`, `…_BUSINESS_REGISTRATION`, `…_TAX_CODE`, `…_ADDRESS`, `…_REPRESENTATIVE`, `…_EMAIL`,
`…_PHONE`, `…_HOTLINE_HOURS` (wired in `docker-compose.yml`; `.env*` files were not touched — add values to the
production `.env` when the product owner provides them). `APP_PUBLIC_BASE_URL` must be the public origin
(`https://nhadatchuan.online`): canonical, og:url, JSON-LD and sitemap URLs use it.

**Nginx changes (`frontend/nginx.conf`):** `location /` now `try_files $uri @prerender`; new `@prerender` (rewrite to
`/render$uri`, proxy with 3 s connect / 10 s read, `proxy_intercept_errors` only for 401/403/405/502/503/504 → `@spa_shell`);
`@spa_shell` (static `index.html`, no-store); `^~ /sitemaps/` and `= /robots.txt` proxied to the backend. The
`security-headers.conf` include is in every location, including the new ones (`verify-headers.sh` 153/153).
Cloudflare: do not enable HTML caching rules for pages (the backend decides status/robots per request).

**After deploy:** `scripts/verify-prerender.sh https://nhadatchuan.online` and `scripts/verify-headers.sh https://nhadatchuan.online`.

**Search Console (F16.6, needs the owner's Google account):**
1. Add a *Domain* property `nhadatchuan.online` (DNS TXT record in Cloudflare) — covers http/https and subdomains.
2. Sitemaps → submit `https://nhadatchuan.online/sitemap.xml` (the index; parts are discovered from it). Expect
   "Success" and the discovered URL count ≈ public listings + projects + areas + articles + 10 static pages.
3. URL Inspection → test live URL for `/`, one `/listings/<slug>`, one `/du-an/<slug>`, one `/khu-vuc/<slug>`, one
   `/tin-tuc/<slug>`: "URL is available to Google", user-declared canonical = Google-selected canonical, rendered HTML
   shows the title; "View tested page → More info" has no blocked resources except `/api/` calls.
4. Rich results test (search.google.com/test/rich-results) on a listing (Product), an article (Article) and the home page
   (Sitelinks search box).
5. Pages report after 1–2 weeks: filtered search URLs must appear under "Excluded by 'noindex' tag" (intended), removed
   listings/articles under "Not found (404)" or 410; investigate any "Soft 404" or "Duplicate without user-selected
   canonical".
6. Optional: Bing Webmaster Tools → import from Search Console.

## 7. Known gaps (honest)

- No Playwright E2E in a browser for the new pages (unit tests + no-JS HTTP checks cover them); Firefox/WebKit and
  visual baselines = S11.
- The UAT seed's projects have no descriptions/amenities; real project content and sources must be entered by staff.
- Operator identity is empty until the product owner supplies it (pages say "Chưa có dữ liệu").
- `areas()` aggregates the whole read model (one grouped scan, cached 10 min); EXPLAIN at 1M rows = S10.
- Listing parts are computed from a 10-minute snapshot: a part can list a listing hidden since (it then answers 410)
  or miss one published in the last 10 minutes.
- No sitemap `<image:image>` extension and no `hreflang` (Vietnamese only).
- The CMS editor is a sanitised HTML textarea (no WYSIWYG); cover images reuse the S1 upload flow by URL.

## 8. Commits, merge notes, follow-ups

Commits on `audit/s7-seo` (oldest first): brief `17210f4`; CMS/catalog `52083aa`; prerender/sitemap `b6bae33`;
frontend `9dc2493`; read-side sanitising + HTML no-store `2f082c8`; Nginx + smoke scripts `38cc919`; this report and
the Nginx 401/403/405 fallback (last commit).

**Merge into the current `audit-2026-09-27` (now with S5-B and S8):** a trial `merge-tree` shows three textual
conflicts, all "both sides appended to a list": `SensitiveResponseCacheFilter` (prefix list), `RateLimitPolicies`
(policy list) and `frontend/app/main.tsx` (imports/boot lines); keep both sides. Re-run `check:bundle` after the merge
(the shell is shared) and `SchemaMigrationTests` (V090/V091 sit before S8's V095).

**Follow-ups:** S8: page views of `/du-an`, `/khu-vuc`, `/tin-tuc` via `track()`. S9: ArchUnit allowance for
`catalog`/`seo` using `search.application.port.ResponseCachePort` (or move it to `shared`), OpenAPI snapshot of the new
endpoints. S10: EXPLAIN of the sitemap snapshot and `areas()` at 1M rows. S11: browser E2E and visual baselines of the
new pages, WYSIWYG decision for the CMS editor.
