# Stream S2-SEARCH — read model, index pipeline, search/map/detail API v2, search/detail/compare/seller UI (W2)

Branch `audit/s2-search` (from `audit-2026-09-27` @ `b875b91`). Flyway V033–V035 used (range V033–V044), backend port
18113, Vite 5313, Redis DB 4, ES index prefix `s2`.

## 1. How to verify

```sh
docker compose -f infra/test/compose.yaml up -d --wait elasticsearch   # ES tests (prefix s2); shared services untouched
eval "$(scripts/test-infra.sh env)"; export BDS_TEST_ES_PREFIX=s2
cd backend && sh mvnw -B -ntp verify
cd frontend && npm ci && npm run lint && npm run typecheck && npm run format:check && npm run test:unit && npm run build && npm run check:bundle
E2E_BACKEND_PORT=18113 E2E_FRONTEND_PORT=5313 E2E_REDIS_DB=4 E2E_DB_PREFIX=s2_e2e scripts/e2e-local.sh \
  --suites "search navigation a11y auth-dialog authenticated" --projects "chromium-1440 chromium-320"
```

| Check | Result (final commit) |
|---|---|
| `mvnw verify` | **203 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** (111 before S2 + 92 new/changed) |
| `npm run lint` / `typecheck` / `format:check` | clean |
| `npm run test:unit` | **16 files, 154 tests passed** |
| `npm run build` + `check:bundle` | success, every route within budget (numbers §4) |
| E2E (chromium-1440 + 320) | search **10/10**, navigation **10/10**, a11y **28/28**, auth-dialog **4/4**, authenticated **4/4** |

## 2. Requirement → evidence

Backend tests are in `backend/src/test/java/com/company/bds/search/`. "DB" = `SearchApiDatabaseEngineTests`, "ES" =
`SearchElasticsearchEngineTests`.

| ID | Delivered | Evidence | Status |
|---|---|---|---|
| F02.1 | v2 envelope `items/pageInfo/total/queryVersion/dataAsOf/engine/degraded/notices/suggestions`, size ≤ 48 (UI 24), "Xem thêm" | DB `pagesThroughMoreThan250…`; e2e `"Xem thêm" loads the next page…` | DONE |
| F02.2 | `total` first page only, cap 10 000, `eq`/`gte`; UI label via `loadedSummary` | DB (total 260 eq, null on later pages) | DONE |
| F02.3 | `/api/v2/listings/map` points (zoom ≥ 12 and ≤ 400) or grid clusters, same filters | DB `mapAnswersPoints…`; e2e map test | DONE |
| F02.4 | keyset (DB) / `search_after` (ES) on unique tuples | DB: 260 listings with price/area/time ties, 4 sorts, no dup/miss, exact tuple order; ES parity walks size 5 | DONE |
| F03.1 | one schema each side: `SearchFilterParser` / `filterSchema.ts`, same canonical JSON + hash | `SearchFilterParserTests.hashOfTheCanonicalJsonIsStable` + `filterSchema.test.ts` shared vector | DONE |
| F03.2 | every filter server-side incl. `verified=IDENTITY|OWNERSHIP` | DB `verifiedFilterIsServerSide…` (verified listings outside page 1, expired checks excluded) | DONE |
| F03.3 / F03.4 | URL = source of truth; push on intent, replace on map move/view; filter change restarts cursor | e2e `filters live in the URL and back/forward restores them` (reload too) | DONE |
| F04.2 | RENT presets per month, type-aware, non-overlapping; purpose switch clears price | `filterSchema.test.ts` presets + `withPurpose` | DONE |
| F04.3 | `price{amount,currency,period}`, `unitPrice` SALE only, `rentTerms`; Money everywhere (card/detail/compare/map/lead modal) | DB `rentListingsCarryAMonthlyPeriod…`; e2e "/tháng" | DONE |
| F05.2 | `search-index` batch handler, `_bulk` external version = `row_version`, delete when hidden, replay refused | ES `pipelineIndexesWithExternalVersions…` | DONE |
| F05.3 | keyset backfill, BUILDING dual-write, atomic alias swap, PREVIOUS kept & written, rollback, 2-phase cleanup; legacy concrete `bds-listings` migrated | ES `rebuildDuringChanges…`, `firstStartMigratesALegacyConcreteIndex…` | DONE |
| F05.4 | retry/backoff/DLQ from S0 queue (maxAttempts 50 ≈ 2 days); metrics `bds.search.index.lag`, `bds.search.index.bulk.failures`, `bds.search.index.documents`, `bds.jobs.*{queue=search-index}` | ES + lag test | PARTIAL: alert rules not added (S5 owns `infra/observability`) — see §6 |
| F05.5 | no full scan; **measured through the real worker (poll 250 ms): update p50 254 ms / p95 473 ms, hide p95 342 ms** (full-suite run; isolated run 324/377 ms) | `SearchIndexLagTests` | DONE (S10 load report pending) |
| F06.1 | 400 `INVALID_FILTER` with every `{param,message}`, unknown params rejected, sort whitelist | `SearchFilterParserTests`, DB `invalidParameters…` | DONE |
| F06.2 | HMAC cursor `{v,e,s,k,h,x}` 30 min; tamper/expiry/hash → 400 `CURSOR_INVALID`, other engine → 409 `CURSOR_ENGINE_CHANGED`; UI restarts with notice | `SearchCursorCodecTests`, DB `cursorsAreSigned…`, ES `aCursorIsRefusedByTheOtherEngine` | DONE |
| F06.3 | 800 ms budget, failure-rate breaker with half-open probe, query 4xx do not trip, `bds.search.requests{engine,outcome}`, `bds.search.breaker.state` | `SearchCircuitBreakerTests`, `SearchEngineFallbackTests` (hanging ES → DB < 2.5 s, breaker opens, no engine call while open) | DONE |
| F06.4 | parity on the same data: diacritics, aliases (HBT, NTL), ties, filters, bbox, stale hidden doc, invalid query, deep paging | ES `bothEnginesReturnTheSamePagesForTheSameData` (10 queries, identical sequences; RELEVANCE same set) | DONE |
| F07.1 / F07.4 | cards from `listing_public_read` only; **search page of 24 = 2 statements, cache hit 1, detail 2–3** with 1 or 10 revisions (≤ 4) | DB `aPageOf24CardsAndADetail…` (QueryCount), `ListingCacheTests` | DONE |
| F07.2 (public part) | detail = public revision + media; drafts never exposed (404, not 410) | DB `publicResponsesNeverCarryDraft…`, `detailIs404ForUnknownAndDraft410ForHidden…` | DONE (owner draft view = S3a) |
| F08.5 | `/api/v2/public/sellers/{id}/listings` cursor, no cap | DB `sellerInventoryIsPagedBeyondSixty…` (70 listings, 3 pages); e2e seller | DONE |
| F09.1 | dynamic predicates, one ORDER BY per sort, no `OR :p IS NULL` on the public path | `JdbcListingReadModelAdapter` | DONE |
| F09.2 | `public_location geometry` + GiST, `search_tsv` GIN, keyset indexes (V033) | index DDL; EXPLAIN at scale = S10 | PARTIAL (EXPLAIN evidence to S10) |
| F10.1 | detail cache keyed by listing + row_version (+ trust expiry stamp), ETag/304; first page cache by search generation, rows re-read and re-checked on hit | `ListingCacheTests.detailCacheFollows…`, `firstPageCacheServesFreshRows…`; DB ETag test | DONE |
| F10.3 | geocode cache: normalised key per provider/lang, 14 d expiry, negative 1 h | `GeocodeCacheTests` | DONE |
| F10.4 | Redis lock + wait, in-JVM single flight, ±20 % TTL jitter, Redis error → bypass window | `ListingCacheTests.concurrentMisses…`, `redisOutageBypasses…` | DONE |
| F14.1 | gallery + lightbox: all photos, counter, arrows, Esc, focus trap/return, alt per photo | `Gallery.test.tsx` (20 photos) | DONE |
| F14.3 (FE part) | srcset/sizes, hero eager + fetchpriority | `Gallery.test.tsx` | DONE (variants = S1) |
| F15.1 | MapLibre + worker and the filter form via dynamic import | e2e `list mode never downloads the map`; bundle §4 | DONE (budget deviation §3) |
| UI-02–UI-05 | search / detail / seller / compare rewritten on v2 | e2e + unit above; a11y 28/28 | DONE |
| P-01 | beds, legal, furnishing, rent terms, accent-insensitive + alias keyword, full URL, results beyond page 1 | tests above | DONE |
| P-04 (display) | identity / listing / ownership separate with dates, EXPIRED at read time, daily re-index | `TrustBadge` usage, DB verified test, `ListingReadModelTests.trustExpiryTask…` | DONE (decisions = S4) |
| P-05 (price history) | `/price-history`, `priceChange` from previous approved revision | DB `priceChangeAndHistory…` | DONE |
| D-01/D-02 | PG source of truth; deferred trigger refreshes at commit → hide is immediate on DB path; ES hits re-checked | DB `hidingALockedOrPaused…`, ES stale-doc case | DONE |
| D-03, D-04, D-06, D-07 | envelope, map clusters + span ≤ 3° (`BBOX_TOO_LARGE`), keyset policy (live, no PIT), bbox `&&` on geometry, precision APPROXIMATE | DB tests | DONE |
| D-05 | indexes created for the known query shapes | V033 | PARTIAL (EXPLAIN = S10) |
| D-08 | normalisation v1 identical in Java/SQL/TS, explicit ES mapping `vi_fold`, alias table | `ListingReadModelTests.javaAndSqlNormalise…`, ES `mappingIsExplicit…` | DONE |
| D-11 | similar listings + zero-result relaxations with counts | DB `similarListings…`, `zeroResults…` | DONE |
| DS-09 / DS-10 | stretched link, no nested controls; badges say what was checked | ListingCard; a11y suite | DONE |
| R-2 / R-3 | unified search/map/paging, no 100 cap, rent units; no drafts/private data/contacts publicly | tests above | DONE |

## 3. Contract deviations

1. **Read model**: `listing_public_read` also stores the full `description` and `media_urls[]` so a detail needs one row
   query (+ image resolver). `ward_name` is always NULL (no ward dictionary exists). 410 is returned only for listings
   that were public before (have an approved public revision); never-published drafts answer 404 so their titles never leak.
2. **Map endpoint** always runs on the database engine (`engine: "database"`); ES is used for list search only.
3. **Engine switch**: a DB cursor is refused (409) once ES is back, per contract; when ES is disabled by configuration
   responses are `database` with `degraded=false`; enabled but not ready/failing → `degraded=true`.
4. **Suggestions**: extra envelope field `suggestions:[{type, drop[], total}]` (first empty page only).
5. **Seller profile v2** `/api/v2/public/sellers/{id}`: `{id,name,avatarUrl,role,memberSince,identity,activeListingCount,
   ownershipVerifiedListingCount,responseStats|null}`; stats only with ≥ 5 answered leads (median minutes to first response).
6. **Admin**: `GET/POST /api/v2/admin/search/index[/rebuild|/rollback|/cleanup]` (ADMIN). Cleanup is two-phase
   (PREVIOUS→RETIRED, delete after 2 min) so an in-flight batch never auto-creates a deleted index.
7. **v1 search** wrapper now defaults `purpose=SALE` (v2 rule) and returns 400 for invalid values; `page>0` → `[]`.
8. **Bundle**: `/search` list view is 142.6 kB (shell 119.7 + 22.9). The ≤ 130 kB target was written against the old
   gzip-9 metric; S0-FE's corrected gzip-1 metric puts the shell alone at ~120 kB. Budget set to 157 kB, note in
   `bundle-budget.json`. MapLibre is no longer in the initial JS (was 602 kB).
9. Frontend `filterHash` uses Web Crypto (async).
10. Test infra: `BdsIntegrationTestInitializer` now deletes the versioned indices behind the alias (alias names cannot
    be deleted). `SchemaMigrationTests` targets V029 explicitly (it asserted "3 migrations to the latest").

## 4. Bundle (gzip-1, `npm run check:bundle`)

| Route | Before | Now | Budget |
|---|---|---|---|
| shell | 120 | 119.7 | 128 |
| `/search` | 602.6 | **142.6** | 157 (was 663) |
| `/listings/:slug` | 127.8 | 140.9 | 155 |
| `/compare` | 123.8 | 134.0 | 137 |
| `/nguoi-dang/:sellerId` | 122.4 | 132.3 | 135 |

## 5. Known gaps (honest)

- Alert rules for search lag / bulk failures / dead search-index jobs not written (S5's observability files).
- No EXPLAIN (ANALYZE) evidence at 100k/1M rows for the new indexes (S10).
- The UAT seed has one photo per listing, so the 20-photo gallery is proven in a component test, not E2E.
- Firefox/WebKit E2E not run locally; visual baselines not updated (S11).
- Favourite button slot exists on the card (`actions`) but favourites are S6.
- ES gc_deletes window (10 min): a stale write delayed longer than that could resurrect a deleted doc until the next change; results are still re-checked on PostgreSQL, so it can only cause a missing hit, never a hidden listing shown.
- `search_text` is built from unredacted text (display is redacted); a phone number typed as keyword could match.
- Map points/sheet on mobile use the first page data or one detail fetch; no clustering animation.

## 6. Production / deploy notes

- **Migrations V033–V035** (one transaction each; V033 backfills all ACTIVE listings with the refresh function — seconds at
  current size; `statement_timeout` 15 min). Additive; rollback = previous image (tables/triggers stay; triggers only
  enqueue jobs — with the old image nobody consumes queue `search-index`: purge with
  `DELETE FROM background_jobs WHERE queue IN ('search-index','search-rebuild')` if rolling back for long).
- **`SEARCH_CURSOR_SECRET`** (≥ 32 chars, same on every instance) is **required** in production (startup refuses otherwise).
  Optional: `APP_SEARCH_TIMEOUT` (PT0.8S), `APP_SEARCH_BREAKER_OPEN_FOR`, `APP_SEARCH_INDEX_REPLICAS`,
  `APP_SEARCH_BOOTSTRAP_ON_STARTUP`, `APP_SEARCH_TRUST_EXPIRY_CRON`, `APP_SEARCH_CACHE_ENABLED`; `app.geocoding.cache-ttl`
  (P14D), `negative-cache-ttl` (PT1H). Add them to `docker-compose.yml`/`.env.example` at merge.
- **Index migration**: on first start (task-locked across instances) the existing concrete index `bds-listings` is replaced
  by alias `bds-listings` → `bds-listings-v2-<ts>` backfilled from the read model; until done search uses the DB engine.
- **Rebuild**: `POST /api/v2/admin/search/index/rebuild` → watch `GET …/index` (backfilled rows, pending jobs) → swapped
  automatically; `POST …/rollback` swaps back losslessly; `POST …/cleanup` twice (≥ 2 min apart) deletes old generations.
- Keep `APP_JOBS_ENABLED=true` on at least one instance (index + e-mail).
- Suggested alerts (S5): `bds_jobs_lag_seconds{queue="search-index"} > 30`, `increase(bds_search_index_bulk_failures_total[10m]) > 0`,
  `bds_jobs_dead{queue="search-index"} > 0`, `bds_search_breaker_state == 1` for 5 min.

## 7. Follow-ups for other streams

- **S3a**: listing write paths already trigger the read model (deferred trigger on `listings`, job on revisions/media);
  fill `legal_status_code`, `furnishing`, rent terms, `project_id`, `availability_confirmed_at` so search filters see them;
  owner draft preview stays on v1 endpoints.
- **S4**: trust decisions (KYC/ownership expiry, revocation) reindex automatically via triggers; call nothing extra.
- **S1**: replacing `PublicImageResolver` gives cards/detail srcset automatically (≤ 1 query per page).
- **S5**: alert rules above; rate-limit policies `search-v2`, `search-map-v2`, `admin-search-index` were added.
- **S6**: pass the favourite button through `ListingCard.actions`; saved searches should store `serializeFilters` output and `filterHash`.
- **S7**: prerender detail can use `GET /api/v2/listings/{slug}` (404/410 statuses) and `listingDocumentMeta`.
- **S10**: EXPLAIN at scale for `idx_lpr_*`, lag under load, rebuild drill.
- `IMPLEMENTATION_PLANS_HISTORY.md` / `WALKTHROUGHS_HISTORY.md` left to the orchestrator (03_AGENT_RULES).
