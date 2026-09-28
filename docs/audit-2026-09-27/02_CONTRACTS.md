# Technical contracts — audit 2026-09-27 implementation

Binding for every stream. When a contract is wrong or insufficient, do not silently diverge: implement the closest
compatible behaviour, write the deviation in your stream report (`streams/<stream>.md`, section "Contract deviations").

## 1. Streams, ownership, migration ranges, ports

| Stream | Scope (summary) | Flyway range | Backend port | Vite port | ES/Redis prefix |
|---|---|---|---|---|---|
| S0-BE (W1) | Test infra on PostgreSQL, shared schema, job queue, scheduler lock, mail outbox, analytics recorder+ingestion, roles, `PublicImageResolver` interface + URL-only impl | V027–V029 | 18110 | 5310 | `s0be` / db 1 |
| S0-FE (W1) | Tokens, fonts, UI kit, ESLint/Prettier, meta hook, `track()`, E2E restructure, CI artifacts, route budgets | — | 18111 | 5311 | `s0fe` / db 2 |
| S1-MEDIA (W3) | Image variants, EXIF/orientation, placeholders, signed private URLs, asset policy | V030–V032 | 18112 | 5312 | `s1` / db 3 |
| S2-SEARCH (W2) | Read model, index pipeline, search/map/detail API v2, caches, price history, search/detail/compare/seller UI | V033–V044 | 18113 | 5313 | `s2` / db 4 |
| S3a-SUPPLY (W2) | Listing write path + wizard, OWNER persona, my-listings paging, freshness/expiry, import, quality | V045–V049 | 18114 | 5314 | `s3a` / db 5 |
| S3b-LEADS (W3) | Leads (owner JOIN, atomic quota, scoped idempotency, history, optimistic lock, withdraw), appointments + reminders, broker workspace, qualified-lead/ROI report | V050–V054 | 18123 | 5323 | `s3b` / db 14 |
| S4-ADMIN (W2) | Moderation v2, admin listings/users/reports/verification/billing, trust decisions, KYC page, billing F18, property assets/dedupe | V055–V064 | 18115 | 5315 | `s4` / db 6 |
| S5-SEC (A: W1, B: W4) | A: Nginx headers (sole owner of `frontend/nginx.conf`), real IP, rate limit v2 (sole owner of `RequestRateLimitFilter`), session ADR, backups/DR tooling, observability. B: admin MFA + sessions, session tests, least privilege, auth token pages | A: V065, B: V066–V067 | 18116 | 5316 | `s5` / db 7 |
| S6-ENGAGE (W3) | Favorites, shortlist sharing, saved searches + alerts, notification center, preferences/unsubscribe, SSE multi-node | V068–V074 | 18117 | 5317 | `s6` / db 8 |
| S7-SEO (W4) | Prerender renderer + statuses, sitemap index, CMS public/preview/schedule, info pages, project & area pages, home | V075–V079 | 18118 | 5318 | `s7` / db 9 |
| S8-ANALYTICS (W4) | Consent, bot/internal filtering, retention, RUM, funnel/cohort/metric dashboards | V080–V084 | 18119 | 5319 | `s8` / db 10 |
| S9-QUALITY (W5) | ArchUnit, OpenAPI snapshot + generated TS types, Spotless, logging/requestId/PII masking, Problem Details | V085–V086 | 18120 | 5320 | `s9` / db 11 |
| S10-PERF (W5) | Synthetic datasets, EXPLAIN reports, k6 constant-arrival-rate, failure/concurrency/rebuild/restore drills | V087–V088 | 18121 | 5321 | `s10` / db 12 |
| S11-UX (W5) | Final UI/a11y/responsive pass, journey E2E, visual baselines, alias regression | — | 18122 | 5322 | `s11` / db 13 |

Never edit `V001`–`V026` or another stream's migrations (production already applied V001–V026; checksums must stay).

## 2. Shared schema delivered by S0-BE (V027–V029)

S0-BE writes the SQL; semantics below are fixed.

### 2.1 Listing money and attributes (`listing_revisions`)
- `price_period VARCHAR(10) GENERATED ALWAYS AS (CASE WHEN purpose = 'RENT' THEN 'MONTH' END) STORED`: `NULL` for SALE,
  `'MONTH'` for RENT. Derived, so existing write paths (JPA entity, seeder) keep working without mapping it.
- `monthly_service_fee_vnd BIGINT NULL CHECK (>= 0)`, `deposit_vnd BIGINT NULL CHECK (>= 0)` (RENT only; ignored for SALE).
- `furnishing VARCHAR(20) NULL CHECK IN ('NONE','BASIC','FULL')`.
- `legal_status_code VARCHAR(30) NULL CHECK IN ('RED_BOOK','PINK_BOOK','SALE_CONTRACT','PENDING_CERTIFICATE','OTHER')`,
  backfilled from the free-text `legal_status` (sổ đỏ → RED_BOOK, sổ hồng → PINK_BOOK, hợp đồng mua bán/HĐMB → SALE_CONTRACT,
  chờ sổ → PENDING_CERTIFICATE, any other non-empty → OTHER). Keep `legal_status` text as display detail.
- `project_id UUID NULL REFERENCES projects(id)`.

### 2.2 Listing lifecycle / data quality (`listings`)
- `availability_confirmed_at TIMESTAMPTZ` (backfill ACTIVE rows with `updated_at`), `expires_at TIMESTAMPTZ` (NULL = no expiry yet).
- `source VARCHAR(20) NOT NULL DEFAULT 'DIRECT' CHECK IN ('DIRECT','IMPORT','SEED')`.
- `property_asset_id UUID NULL` (FK added by S4 when it creates `property_assets`).

### 2.3 Trust validity
- `user_kyc_profiles.expires_at TIMESTAMPTZ` — backfill `verified_at + 24 months` for VERIFIED rows.
- `listing_verifications.expires_at TIMESTAMPTZ`, `revoked_at TIMESTAMPTZ`, `decided_by UUID` — backfill `verified_at + 180 days` for VERIFIED_OWNER rows.

### 2.4 Leads
- Status CHECK adds `'WITHDRAWN'` (requester withdrew). Java `LeadStatus` gains `WITHDRAWN`.
- `version BIGINT NOT NULL DEFAULT 0` (optimistic locking), `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`,
  `first_response_at TIMESTAMPTZ` (first transition out of NEW by the owner side),
  `qualification VARCHAR(20) NULL CHECK IN ('QUALIFIED','UNQUALIFIED')`, `qualified_at TIMESTAMPTZ`, `qualification_reason VARCHAR(200)`.

### 2.5 Roles
`user_roles.role` values: `USER`, `OWNER`, `BROKER`, `MODERATOR`, `ADMIN` (one row per user). Priority when resolving a single
role: ADMIN > MODERATOR > BROKER > OWNER > USER. Capabilities (constants in backend `shared/security/Roles.java`,
frontend `app/shared/auth/roles.ts`):
- `POSTERS` = ADMIN, BROKER, OWNER (create/edit listings, my-listings, my-leads, billing, broker workspace*)
- `STAFF` = ADMIN, MODERATOR
- *`/broker/workspace` stays BROKER/ADMIN; OWNER gets the simplified owner dashboard inside my-listings/my-leads.

### 2.6 Infrastructure tables
- `background_jobs` (durable queue; §3), `scheduled_task_locks` (§3.3), `analytics_events` (§5).

### 2.7 Indexes (only if EXPLAIN on the test data shows use; otherwise leave to S2/S10)
`listings (owner_id, status, created_at DESC, id DESC)`; `listings (created_at DESC, id DESC) WHERE status='ACTIVE'`.

## 3. Durable job queue (S0-BE)

### 3.1 Semantics
- Table `background_jobs(id, queue, dedupe_key, payload jsonb, run_at, attempts, max_attempts, enqueue_seq, locked_by,
  locked_until, lease_token, last_error, completed_at, dead_lettered_at, created_at, updated_at)`.
- Partial unique index `(queue, dedupe_key)` over pending rows (not completed, not dead-lettered) → **coalescing**:
  enqueuing an existing pending key updates payload, sets `run_at = LEAST(old, new)` and increments `enqueue_seq`.
- Claim: `FOR UPDATE SKIP LOCKED`, due rows only (`run_at <= now()`), lease (`locked_until`, `lease_token`).
- Complete: only if the lease token matches **and** `enqueue_seq` is unchanged since claim; if the seq changed the row is
  released for immediate re-run (attempts not incremented) so no change is lost.
- Failure: `attempts+1`, `run_at = now() + min(1h, 30s·2^attempts) ± 20% jitter`, dead-letter at `max_attempts`.
- Enqueue participates in the caller's transaction (JdbcTemplate on the Spring-managed DataSource).
- SQL helper `bds_enqueue_job(p_queue text, p_dedupe text, p_payload jsonb, p_run_at timestamptz DEFAULT now())`
  for triggers (S2 uses it).
- Completed rows older than 7 days are purged by a locked scheduled task.
- Metrics: `bds.jobs.lag.seconds{queue}` (age of oldest due pending), `bds.jobs.pending{queue}`, `bds.jobs.dead{queue}`,
  `bds.jobs.processed{queue,outcome}`.

### 3.2 Java API (package `com.company.bds.shared.jobs`)
```java
public interface JobQueue {
    UUID enqueue(String queue, @Nullable String dedupeKey, Map<String, ?> payload, @Nullable Instant runAt);
}
public interface JobHandler {                 // one bean per queue
    String queue();
    default int batchSize() { return 20; }
    default Duration lease() { return Duration.ofMinutes(2); }
    /** Return the ids that succeeded; throw or omit ids to fail them (error message recorded). */
    JobBatchResult handle(List<ClaimedJob> jobs);
}
```
A single `JobWorker` polls every registered queue (`app.jobs.poll-ms`, default 1000; `app.jobs.enabled`, default true;
false in unit tests that don't need it). Multi-instance safe.

### 3.3 Scheduled task lock
`scheduled_task_locks(name PK, locked_until, locked_by)`; `ScheduledTaskLock.runExclusive(String name, Duration maxRun, Runnable)`.
Every `@Scheduled` business task (expiry sweeps, digests, retention, orphan cleanup, heartbeat excluded) must use it.

### 3.4 Mail outbox
`MailOutbox.enqueue(MailMessage message)` → queue `email`; handler sends via `JavaMailSender`, retries per §3.1.
`MailMessage(to, subject, textBody, @Nullable htmlBody, category, @Nullable dedupeKey, Map<String,String> headers)`.
S0-BE migrates the three existing synchronous sends (AuthService ×2, BillingService ×1) to the outbox.

## 4. Money and listing attributes (API)

```jsonc
"price": { "amount": 14500000, "currency": "VND", "period": "MONTH" },   // period null for SALE
"unitPrice": { "amount": 48170000, "per": "M2" },                        // SALE only, null otherwise
"rentTerms": { "monthlyServiceFee": 1200000, "deposit": 29000000 },      // RENT only, fields nullable
"legal": { "code": "PINK_BOOK", "label": "Sổ hồng" },                    // nullable
"furnishing": "FULL"                                                        // NONE | BASIC | FULL | null
```
Frontend formatting lives in `app/shared/format/money.ts` (S0-FE): `formatMoney(price, {compact})` →
SALE `"3,95 tỷ"`, `"850 triệu"`; RENT `"14,5 triệu/tháng"`; `formatUnitPrice` → `"~48,2 triệu/m²"`. vi-VN decimal comma.
Never compare or sort RENT against SALE prices; compare/search keep one purpose at a time.

## 5. Analytics (S0-BE recorder + ingestion; S8 consent/bot/retention/dashboards)

Table `analytics_events(event_id UUID PK, name VARCHAR(60), schema_version SMALLINT, occurred_at, received_at,
anonymous_id VARCHAR(64), session_id VARCHAR(64), user_id UUID NULL, listing_id UUID NULL, is_internal BOOLEAN,
is_bot BOOLEAN, origin VARCHAR(10) CHECK IN ('web','server'), device VARCHAR(20), area_code VARCHAR(20), properties JSONB,
utm JSONB)`; indexes `(name, occurred_at)`, `(listing_id, name, occurred_at) WHERE listing_id IS NOT NULL`.

- Server events: `AnalyticsRecorder.recordServer(name, version, deterministicKey, userId, listingId, properties)`;
  `event_id = UUID.nameUUIDFromBytes("server:" + name + ":" + deterministicKey)` → exactly-once per business fact.
- Web ingestion: `POST /api/v1/events` (public, rate limited), body `{ "consent": "granted"|"denied", "events": [ ... ≤ 50 ] }`,
  each `{eventId, name, v, occurredAt, anonymousId, sessionId, listingId?, properties, page?, utm?, device?}`. Unknown
  name/version → 400. Without consent the server stores the event with `anonymous_id`/`session_id` = NULL and no utm.
  `user_id` comes from the bearer token, never from the body. `is_internal` = staff role; `is_bot` = UA heuristics.

Event catalog v1 (properties are the only allowed keys):

| name | origin | properties |
|---|---|---|
| `search_performed` | web | `filterHash`, `purpose`, `resultCount` (nullable), `zeroResult`, `engine`, `hasBbox`, `hasKeyword` |
| `search_results_viewed` | web | `filterHash`, `listingIds` (≤ 48), `offset` |
| `listing_detail_viewed` | web | `purpose`, `propertyType`, `district` |
| `listing_favorited` / `listing_unfavorited` | server | — |
| `compare_opened` | web | `listingIds` |
| `saved_search_created` | server | `filterHash`, `frequency` |
| `lead_form_opened` | web | `requestType` |
| `kyc_required_shown` | web | `context` |
| `lead_submitted` | server | `leadId`, `requestType` |
| `lead_first_response` | server | `leadId`, `minutes` |
| `lead_qualified` | server | `leadId`, `qualification` |
| `appointment_proposed` / `appointment_confirmed` / `appointment_completed` / `appointment_no_show` / `appointment_cancelled` | server | `appointmentId`, `leadId` |
| `listing_published` | server | `revisionNumber` |
| `web_vital` | web | `metric` (LCP/INP/CLS/TTFB), `value`, `rating`, `route` |

Adding an event = new catalog row + version; never reuse a name with different semantics.

## 6. Trust (identity / listing / ownership)

```jsonc
"trust": {
  "identity":  { "status": "VERIFIED", "checkedAt": "…", "expiresAt": "…" },   // VERIFIED|PENDING|REJECTED|EXPIRED|NOT_SUBMITTED
  "listing":   { "status": "CHECKED",  "checkedAt": "…" },                    // CHECKED|NOT_CHECKED (moderation of the public revision)
  "ownership": { "status": "PENDING",  "checkedAt": null, "expiresAt": null, "documentType": "CERTIFICATE_OF_OWNERSHIP" }
                                                                              // VERIFIED|PENDING|REJECTED|REVOKED|EXPIRED|NOT_SUBMITTED
}
```
- identity VERIFIED = seller KYC `VERIFIED` and (`expires_at` null or future). EXPIRED when past.
- ownership VERIFIED = a `listing_verifications` row `VERIFIED_OWNER`, not revoked, not expired, for this listing.
- listing CHECKED = public revision has `moderated_at`.
- Search filter `verified=IDENTITY|OWNERSHIP` filters server-side on these definitions.
- UI copy (TrustBadge): identity → “Đã xác minh danh tính người đăng”; listing → “Nội dung tin đã qua kiểm duyệt”;
  ownership → “Đã đối chiếu giấy tờ chủ sở hữu”. Each badge has a scope note, e.g. identity: “Không bảo đảm quyền sở hữu
  hay pháp lý giao dịch”. Never a bare “Đã xác thực”.

## 7. Search filter schema (shared by API, URL, saved searches)

| Param | Values | Notes |
|---|---|---|
| `purpose` | `SALE`\|`RENT` | default SALE; changing purpose clears `priceMin/priceMax` |
| `type` | CSV of `APARTMENT,HOUSE,VILLA,TOWNHOUSE,LAND` | |
| `priceMin`, `priceMax` | integer VND, inclusive | RENT = per month |
| `areaMin`, `areaMax` | decimal m², inclusive | |
| `bedsMin` | 1..10 | |
| `legal` | CSV of legal codes | |
| `furnishing` | CSV of `NONE,BASIC,FULL` | |
| `verified` | `IDENTITY`\|`OWNERSHIP` | |
| `district` | CSV of district codes (`001`…) | |
| `project` | project UUID | |
| `q` | ≤ 100 chars | Vietnamese normalization: lowercase, NFD strip marks, `đ→d`, collapse spaces |
| `bbox` | `minLng,minLat,maxLng,maxLat` | WGS84, rounded to 5 decimals; span ≤ 3° |
| `sort` | `NEWEST`\|`PRICE_ASC`\|`PRICE_DESC`\|`AREA_DESC`\|`RELEVANCE` | RELEVANCE only with `q` (default when `q` present) |
| `size` | 1..48, default 24 | API only |
| `cursor` | opaque | API only, never in shareable URL |
| `view` | `list`\|`map`\|`split` | UI only |
| `place` | label of the chosen place | UI only, paired with `bbox` |

Invalid values → `400` Problem Details, `code=INVALID_FILTER`, `errors=[{param, message}]` (never silently ignored).
`filterHash` = first 32 hex chars of SHA-256 over the canonical JSON (sorted keys, sorted CSV values, no cursor/size/view/place).
Frontend: one module `app/features/search/filterSchema.ts` owns parse/serialize/validate.

## 8. Search API v2 (S2)

`GET /api/v2/listings/search?<filters>`
```jsonc
{
  "items": [ ListingSummaryV2 ],
  "pageInfo": { "hasNext": true, "nextCursor": "…", "size": 24 },
  "total": { "value": 1200, "relation": "gte" },   // first page only; relation eq|gte; cap 10 000; null if not computed
  "queryVersion": "v2",
  "dataAsOf": "2026-09-27T00:00:00Z",
  "engine": "search" | "database",
  "degraded": false,
  "notices": []                                     // e.g. "RELEVANCE_APPROXIMATE", "SEARCH_ENGINE_UNAVAILABLE"
}
```
- Sort tuples: NEWEST `(published_at DESC, listing_id DESC)`, PRICE_ASC `(price_vnd ASC, listing_id ASC)`,
  PRICE_DESC `(price_vnd DESC, listing_id DESC)`, AREA_DESC `(area_m2 DESC, listing_id DESC)`,
  RELEVANCE `(_score DESC, published_at DESC, listing_id DESC)` (ES only; DB fallback = NEWEST + notice).
- Cursor = `base64url(json{v:1, e:engine, s:sort, k:[…last sort values…], h:filterHash, x:expiryEpochSec})` + `.` +
  HMAC-SHA256 (`app.search.cursor-secret`, env `SEARCH_CURSOR_SECRET`; required in production). Expiry 30 min.
  Tampered/expired/hash mismatch → `400 CURSOR_INVALID`; engine differs from current → `409 CURSOR_ENGINE_CHANGED`
  (UI restarts from page 1 and tells the user).
- Consistency policy: live keyset, no PIT; a listing whose sort value changes between pages may move; documented.
- ES timeout budget 800 ms per call; circuit breaker opens on failure rate, half-open probes; query-validation errors do
  not trip it. Metrics: `bds.search.requests{engine,outcome}`, `bds.search.breaker.state`.
- `/api/v1/listings/search` stays as a deprecated thin wrapper (first page, `Deprecation` header).

`ListingSummaryV2`:
```jsonc
{
  "id", "slug", "title", "purpose", "propertyType",
  "price": Money, "unitPrice": UnitPrice|null, "areaM2", "bedrooms", "bathrooms",
  "location": { "districtCode", "districtName", "wardName", "addressSummary", "lat", "lng", "precision": "APPROXIMATE" },
  "image": ImageDto|null, "imageCount",
  "trust": Trust,
  "freshness": { "publishedAt", "updatedAt", "availabilityConfirmedAt" },
  "seller": { "id", "name", "avatarUrl", "role": "BROKER"|"OWNER"|"ADMIN" },
  "project": { "id", "slug", "name" } | null,
  "priceChange": { "previousAmount", "changedAt", "direction": "DOWN"|"UP" } | null
}
```
`GET /api/v2/listings/{slugOrId}` → `ListingDetailV2` (summary + `description`, `images[]`, facts, `rentTerms`, `legal`,
`furnishing`, `revisionNumber`) — `404 LISTING_NOT_FOUND` never existed / `410 LISTING_GONE` exists but not publicly
visible (body has `slug` + optional `listingTitle`, only for the “tin không còn hiển thị” page; `listingTitle` is
withheld for moderation-locked listings and for banned sellers — see `streams/s2-search.md` gap 4/5/9 — and the RFC
9457 problem `title` is always the generic problem title, never the listing's). ETag + `If-None-Match` → 304.

`GET /api/v2/listings/map?<filters>&bbox=…&zoom=3..20` →
`{ "mode": "points"|"clusters", "points": [{id, slug, lat, lng, price, propertyType}], "clusters": [{lat, lng, count, bbox}],
"total": {value, relation}, "engine", "dataAsOf" }`; points when zoom ≥ 12 and count ≤ 400, else grid clusters;
bbox span > 3° → `400 BBOX_TOO_LARGE`.

`GET /api/v2/public/sellers/{id}/listings?cursor=&size=` → same envelope, NEWEST order (replaces the 60-row cap).

## 9. Read model and index pipeline (S2)

- Table `listing_public_read`: one row per publicly visible listing (status ACTIVE with a valid APPROVED public revision).
  Columns cover `ListingSummaryV2` + filters: ids/slug/owner, title, description excerpt, purpose, type, price_vnd,
  price_period, unit price, area, bedrooms, bathrooms, floors, frontage, road width, direction, legal code, furnishing,
  rent terms, province/district/ward codes + names, address summary, `public_location geometry(Point,4326)`, lat/lng,
  project id/slug/name, thumbnail + image count, seller id/name/avatar/role, trust columns (§6), listing checked_at,
  published_at, updated_at, availability_confirmed_at, previous price/changed_at, `search_text` (normalized),
  `search_tsv tsvector`, `row_version BIGINT` (from a sequence, strictly increasing per refresh), `refreshed_at`.
- PL/pgSQL `bds_refresh_listing_public_read(listing uuid)` upserts or deletes the row. Triggers on `listings`,
  `listing_revisions` (public revision fields), `listing_media`, `users` (name/avatar/status), `user_roles`,
  `user_kyc_profiles`, `listing_verifications`, `projects` enqueue `search-index` jobs (dedupe = listing id) via
  `bds_enqueue_job`. A daily locked task enqueues listings whose trust expiry passed.
- Job handler `search-index` (batch): refresh read model rows, then ES `_bulk` index/delete with
  `version_type=external`, `version=row_version`. Alias `bds-listings` → concrete `bds-listings-v2-<timestamp>`.
  Rebuild: create new index, mark it in `search_index_state` so the handler dual-writes, keyset backfill in batches,
  atomic alias swap, keep old index until explicit cleanup (rollback = swap back). Existing concrete index named
  `bds-listings` is migrated on first start.
- Visibility is decided by PostgreSQL: results from ES are re-checked against `listing_public_read` (cheap id lookup);
  missing rows are dropped and the page is topped up at most once.

## 10. Images (S1)

```jsonc
"image": { "url": "/api/v1/public/media/<key>.jpg", "width": 1600, "height": 1000,
           "srcset": [ {"url": "/api/v1/public/media/<key>__w320.webp", "width": 320}, … ],
           "placeholder": { "dominantColor": "#c8b8a0" } }
```
`PublicImageResolver.resolve(List<String> mediaUrls)` returns `ImageDto`s in one query. S0-BE ships the interface
(`com.company.bds.media`) with a URL-only implementation (`srcset: []`, width/height null); S1 replaces the bean with the
variant-aware one backed by a `media_variants` table. Legacy images without variants keep `srcset: []`. Frontend `<ResponsiveImage image sizes alt/>` (S0-FE shell, S1 wires
data). Public media is served only for objects referenced by a publicly visible listing, a user avatar, or a published
CMS/project asset; drafts/private use short-lived signed URLs (`?exp=…&sig=…`) issued to owner/staff.

## 11. Notifications (S6 owns internals; others call the API)

Call `RealtimeNotificationService.notify(userId, type, title, message)` today; S6 extends it to
`notify(NotificationRequest{userId, type, title, message, link, dedupeKey, email: boolean})` and keeps the old signature.
SSE frames: `id: <seq>`, `event: notification`, `data: {id, seq, type, title, message, link, createdAt}`; replay via
`Last-Event-ID`; Redis channel `bds:notifications:v1` for cross-node fanout.

## 12. Frontend shared contracts (S0-FE)

- Tokens: `app/styles/tokens.css` CSS custom properties = single source; `tailwind.config.js` maps to them. Scale from audit §8.2.
- UI kit `app/shared/ui/`: Button, IconButton (aria-label required), FormField (label, hint, error via aria-describedby),
  TextInput, Select, Checkbox, Radio, Switch, Chip (aria-pressed), Dialog (focus trap, Esc, focus return), Sheet (mobile
  bottom sheet), Toast + InlineFeedback, EmptyState, ErrorState (retry), Skeleton, Badge, TrustBadge (§6), Money (§4),
  ResponsiveImage (§10), Avatar, Pagination/LoadMore, DataTable (server-driven; states empty/loading/refresh/partial
  error/permission denied/conflict), Tabs. Catalog page `/__ui` rendered only when `import.meta.env.DEV` or
  `VITE_ENABLE_UI_CATALOG=true`.
- `useDocumentMeta({title, description, canonical, robots, jsonLd, og})` — resets everything it set on unmount.
- `track(name, properties)` in `app/shared/analytics/track.ts`: consent-gated (`bds.consent.analytics`), batching,
  `sendBeacon` on `pagehide`; no-op for unknown names.
- Icons: Lucide only. No emoji as icons.
- Text: no `text-[10px]`/`text-[11px]` for meaningful info; minimum 12px labels, 14px body-sm, 16px body.
- Touch targets ≥ 44px for primary controls.

## 13. Testing contract (S0-BE, S0-FE)

- Backend integration tests run on PostgreSQL + PostGIS with Flyway (no H2). Base annotation `@BdsIntegrationTest`
  creates a fresh database per JVM run on the shared test server (`scripts/test-infra.sh env`), drops it at the end.
  CI uses GitHub service containers with the same env variables. Missing env → the test fails with a clear message.
- Query counting: datasource-proxy helper `QueryCount.assertAtMost(n, () -> …)`.
- ES tests use index names prefixed with the stream prefix + random suffix; Redis tests use their stream's DB index.
- Frontend: `npm run lint` (ESLint), `npm run typecheck`, `npm run build`, `npm run test:e2e -- --project=<p>`.
- E2E fixtures: `scripts/e2e-local.sh` (S0-FE) boots a backend on the stream port against a fresh DB seeded by
  `UatDataSeeder` with a fixed clock (`--app.uat-seed.clock=2026-09-01T03:00:00Z`, S0-BE) and Vite on the stream port.
