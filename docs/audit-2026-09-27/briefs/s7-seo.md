# Brief S7-SEO (W4) — prerender + HTTP status, sitemap index, CMS public/preview/schedule, info, project/area pages, home

Branch `audit/s7-seo`; Flyway **V090–V094** (the contract's V075–V079 are unusable: `out-of-order` is off and V085
exists); backend 18121; Vite 5321; Redis/ES prefix `s7`.
Parallel in W4: S5-SEC phase B (auth/session, token pages) and S8-ANALYTICS. Do not touch their files; the shared
files S7 edits are `SecurityConfig` (public GET matchers), `RateLimitPolicies` (new public policies),
`frontend/nginx.conf` (keep `security-headers.conf` included in every location), `routes.tsx`, `root.tsx` footer.

Read first: `00_PLAN.md` (decision: prerender at the render layer, no SSR), `01_REQUIREMENTS.md`, `02_CONTRACTS.md`,
`03_AGENT_RULES.md`, `briefs/wave-rules.md`, `streams/s2-search.md` (v2 detail 404/410, `listingDocumentMeta`),
`streams/s0-fe.md` (`useDocumentMeta`), `streams/s1-media.md` (CMS covers are public media), `docs/ui/`. Audit: F16,
§4.1 Trang khu vực/dự án + Content + AI/3D/chat, §5 `/`, `/about…`, `*`, admin `/projects`, `/cms`, §7.3 cache table,
§8.3 Home layout.

Requirement IDs: F16.1, F16.3, F16.4, F16.5, F16.6 (EXTERNAL: Search Console steps), UI-01, UI-15, UI-16, UI-25, P-06,
P-07, P-12 (document), D-09 (project/area/CMS part), DS-05 (S7 pages).

## Architecture (prerender at the render layer)
Nginx serves real files (`/assets/*`, favicon…) itself. Every other page request falls through `try_files $uri
@prerender` to the backend `GET /render/<path>?<query>`, which takes the built SPA shell (`index.html`, fetched from
`app.seo.shell-location` and cached), injects per-route `<title>`, description, robots, canonical, Open Graph,
JSON-LD and a server-rendered main-content block into `#root`, and answers with the correct status:
`200`, `301` (canonical slug / id → slug / trailing slash), `404` (unknown route or entity), `410` (listing, project or
article that was public and is not any more). Private app routes get the shell with `noindex`. Backend down
(502/503/504) → Nginx serves the static shell (never an outage of the SPA). The SPA mounts over the prerendered
block; injected head tags carry `data-prerender` and are reset on boot so `useDocumentMeta` stays the single owner.

## Backend
1. `seo` module: route table, shell loader (classpath/file/http, TTL cache, last-good fallback), HTML injector (escaped
   attributes, JSON-LD `</script>`-safe), renderers for home, listing detail (read model via `ListingReadService`),
   seller, search (canonical `?purpose=`; any other filter → `noindex,follow`), projects, areas, articles, article
   preview (no-store + `X-Robots-Tag: noindex`), information pages, 404.
2. Sitemap index `/sitemap.xml` → parts `static`, `areas`, `projects`, `articles`, `listings-<n>` (chunk 10 000,
   keyset boundaries on `listing_public_read.listing_id`, `lastmod` per part = max `updated_at`), all from projections
   (no JPA entity hydration), snapshot cached (Redis, TTL 10 min ±jitter, generation bumped on CMS/project publish).
   Dynamic `robots.txt` (private routes disallowed, sitemap URL from `app.public-base-url`).
3. CMS (P-07): edit = new immutable revision (DB trigger forbids content changes after submission), submit, approve
   now or scheduled (`publish_at`), scheduled promotion task (ScheduledTaskLock) + read-time check, unpublish (→ 410),
   author + source name/URL, reviewer and publish date public; server-side HTML sanitising (jsoup safelist); preview
   token per revision (hashed, 24 h, staff only to mint); paged public list by category.
4. Projects (P-06): public fields (description, website, amenities with source + checked date), public API
   `/api/v2/public/projects[/{slug}]` with live inventory from the read model, statistics with method, sample size and
   `dataAsOf` (median only with ≥ 5 samples); LOCKED → 410.
5. Areas (P-06): slugs on `search_locations`, `/api/v2/public/areas[/{slug}]` with the same statistics, projects and
   newest listings.
6. Home (UI-01): `/api/v2/public/home` — newest listings per purpose, areas and projects with real counts, latest
   articles; no invented numbers.
7. Site info (UI-15): `/api/v1/public/site-info` — operator identity from configuration (`app.operator.*`, "chưa có
   dữ liệu" when unset) + approved LEGAL_POLICY articles.
8. Cache (D-09): project/area/home/sitemap payloads in the shared response cache 5–10 min, keyed by a content
   generation bumped on publish/unpublish/project change.
9. Tests on PostgreSQL (MockMvc, HTML without JS): title/canonical/JSON-LD/main content/status for home, listing
   (200/301/404/410), project, area, article (200/410/scheduled not visible/preview noindex), unknown route 404, private
   route noindex; sitemap with > 10 000 listings, bounded statements, chunk boundaries; CMS flows; sanitiser.

## Frontend (new UI kit and tokens; Vietnamese copy; no fabricated data)
10. Routes `/du-an`, `/du-an/:slug`, `/khu-vuc`, `/khu-vuc/:slug`, `/tin-tuc`, `/tin-tuc/:slug`,
    `/tin-tuc/xem-truoc/:token`; home rework (search first, real listings, areas, projects, articles); information
    pages with operator block and approved policy links; `useDocumentMeta` on each; boot-time reset of prerendered
    head tags; footer/nav links.
11. Admin CMS: new revision (edit), sources, schedule, preview link, unpublish, history. Admin projects: public fields,
    amenities with sources, link to the public page.
12. Unit tests (Vitest) for the pages, head reset and admin flows; bundle budgets for new routes.

## Nginx / ops
13. `@prerender` + static fallback, `/sitemap.xml`, `/sitemaps/`, `/robots.txt`; verified with `nginx -t` and an isolated
    container smoke test; security headers include unchanged in every location.
14. Report: Search Console steps (EXTERNAL F16.6), env vars, migration notes, P-12 criteria document.
