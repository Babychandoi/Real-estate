# Stream S8-ANALYTICS — consent, bot/internal filtering, retention, RUM, dashboards (wave W4)

Branch `audit/s8-analytics` (from `audit-2026-09-27` @ `58ea814`). Flyway **V095** (range V095–V099; V096–V099 unused).
Brief: `briefs/s8-analytics.md`.

## 1. How to verify

```sh
eval "$(scripts/test-infra.sh env)"
cd backend && sh mvnw -B -ntp verify        # JDK 17
cd frontend && npm run lint && npx tsc -b && npx vitest run --testTimeout=30000 && npm run build && npm run check:bundle
```

Results on the final commit:
- **Backend** `mvnw verify`: 345 tests. 329 passed on the first run. The 16 errors were `SearchElasticsearchEngineTests` (8),
  `SearchIndexLagTests` (1) and `MediaPipelineIntegrationTests` (7): Elasticsearch and MinIO were not running in the shared
  `bds-test` project. After `docker compose -f infra/test/compose.yaml up -d --wait elasticsearch minio`, those 3
  classes were re-run: 16/16 passed. New S8 tests: 13 integration + 4 unit, listed below.
- **Frontend**: lint 0; `tsc -b` 0; vitest 27 files / **192 tests** passed; build OK; `check:bundle`: every route and the
  shell are within budget (shell 122.6 kB of 128 kB).

## 2. Requirement → evidence

| ID | Delivered | Evidence | Status |
|---|---|---|---|
| F19.2 consent | Opt-in only (Decree 13/2023/NĐ-CP). **Browser:** before the visitor chooses "granted" for the current policy version, `track()` does nothing: no queue, no request, no analytics id, no session id, and the UTM is not written to storage (it stays in memory). Withdrawing consent drops queued events and removes the ids. **Server:** a batch without `granted` is acknowledged `202 {accepted:0, dropped:n}` and nothing is stored. **Proof of consent:** `POST /api/v1/events/consent` stores a random consent id, the choice (granted/withdrawn), the policy version, the source (banner/preferences) and the user id from the token (never from the body). It stores no IP address or UA. It is rate limited (`analytics-consent`, 30 / 15 min per IP), `no-store`, and kept out of the audit chain. **Banner:** refuse and accept have equal weight, nothing is pre-selected, there is a preferences dialog, and the footer link "Tùy chọn quyền riêng tư" reopens it. | `EventIngestionTests.withoutConsentNothingIsStored`; `AnalyticsConsentAndTrafficTests.consentDecisionsAreRecordedAsProofWithTheUserFromTheTokenOnly`; `track.test.ts` (before consent: undecided, denied, old policy version; a pre-consent event is not sent later; withdrawal drops queued events; UTM stays in memory); `consent.test.tsx` (5) | DONE |
| F19.2 bot/internal | **Internal:** staff token (existing). New: `APP_ANALYTICS_INTERNAL_NETWORKS` (CIDR, literal IPs only, no DNS, applied to the IP from `ClientIpResolver`). New: a device (anonymous id) seen in a staff session is flagged `INTERNAL`; its earlier events are re-marked and its later signed-out events are internal. **Bot:** UA heuristic (existing). New: the hourly rule flags a device with more than 600 events/hour as `BOT`, re-marks its events, and ingestion marks it from then on. Leads, listings and appointments made by staff accounts are excluded from the dashboard. | `AnalyticsConsentAndTrafficTests.aDeviceUsedByStaffBecomesInternal…`, `aDeviceSendingTooManyEventsPerHourIsFlaggedAsABot`; `AnalyticsUnitTests.internalNetworksMatchCidrs…`, `trafficFromAnInternalNetworkIsMarkedInternal` | DONE |
| F19.2 dedupe | Event id is the primary key (S0). A retried batch and a repeated server fact are counted once in every funnel. | `AnalyticsDashboardTests.oneJourneyIsCountedOnce…` (batch sent twice, `lead_submitted` recorded twice) | DONE |
| F19.2 retention | Locked tasks `analytics-hourly` and `analytics-nightly`. Deletes run in batches of 5,000 rows. Periods are in §5. | `AnalyticsRetentionTests` (3): strips/deletes only rows past their period and keeps recent ones; running again changes nothing; aggregation is idempotent, uses Vietnam days and excludes bots/internal; invalid policies are rejected | DONE |
| F19.3 / UI-24 / R-8 (dashboard) | `GET /api/v1/analytics/dashboard?from&to&device&area&source` (staff only, 1–92 days, Vietnam days). Every number is `{status: MEASURED\|NOT_MEASURED, value, unit, reason, numerator, denominator}` plus its definition and source (web, database or aggregate). The UI shows "Chưa đo" with the reason, never 0. A filter that does not apply to a metric gives NOT_MEASURED (for example, server data has no traffic source), and so does a rate with no sample. The response also carries freshness (latest web event, last aggregation) and a latency note. Breakdowns by source, device and area; daily trend. | `AnalyticsDashboardTests` (3): one fixture journey gives exactly 1 at every step of the search, KYC and lead funnels, while its Googlebot and moderator copies and a staff lead do not count; measured 0 vs NOT_MEASURED; validation (6 bad inputs → 400, 91 days OK, broker 403, anonymous 401). `AnalyticsUnitTests.withWebCollectionOffWebMetricsAreNotMeasured…`; `_admin.analytics.test.tsx` | DONE |
| F17.4 (dashboard) | KYC funnel by session: lead form opened → KYC required shown → submitted after KYC; KYC abandonment rate. S3b's `lead-funnel` endpoint is kept. | same as above | DONE |
| P-10 | Event pipeline + cohorts: weekly first-seen device cohorts with week-N retention. Attribution is limited to the session's first-touch `utm_source`; no cross-device attribution. Lead quality: qualified share. | `AnalyticsDashboardTests` (cohort row, source breakdown) | DONE |
| P-13 | North star: both-party confirmed appointments ÷ qualified seekers ÷ weeks. Also: zero-result rate, search→detail, detail→lead, response time median/p90, % within the owner's SLA (default 30 min), lead→appointment, appointment held, "sold still listed" (FAKE_SOLD reports ÷ active listings), return rate, broker cost per qualified lead and renewal/repurchase rate, posting funnel (created → submitted → approved → active) with median hours to approval. | `AnalyticsDashboardTests` (lead funnel 1/1/1/1/1, SLA 100 %, median 10 min, held 100 %, confirmed 1) | DONE (definitions are ours; product should confirm them) |
| F15.3 / D-14 (CWV) / DS-15 (RUM part) | `web-vitals` 4 loaded on demand, only after consent, for a sampled session (`VITE_RUM_SAMPLE_RATE`, default 0.25). Sends LCP, INP, CLS and TTFB once per page load, tagged with the route pattern. Dashboard: p75 per metric and device, sample count, rating against web.dev thresholds; alert when poor with ≥ 20 samples. | `rum.test.ts` (4); dashboard test (LCP row, rating "good") | DONE (collection). The p75 targets themselves need production traffic: EXTERNAL |
| Alerts | In the dashboard: ingestion off, no web events for 24 h, aggregates older than 3 h, < 80 % of leads answered within SLA (≥ 10 leads), > 30 % zero-result searches (≥ 50), poor CWV. | unit + dashboard tests | DONE (in-app; no Prometheus rule) |

## 3. Contract deviations
1. **§5 consent semantics changed.** Before: `denied` → stored without identifiers. Now: nothing is stored without
   `granted`, and the response gains `dropped`. The S0 test was rewritten to the new rule (`withoutConsentNothingIsStored`).
   Web funnels therefore count only consented visitors. The dashboard says so ("Chỉ gồm khách đã đồng ý phân tích").
2. The consent endpoint is `/api/v1/events/consent`, not a separate module path. It shares the analytics security rows.
3. `EventIngestionService.Viewer` gains `internalNetwork`. The 3-argument constructor is kept.
4. `consent.ts`: a choice is valid only together with `bds.consent.analytics.version == CONSENT_POLICY_VERSION`
   (`2026-09-28`). Bump the version whenever purposes or retention change, and the banner asks again.
5. Commit `e9192f6` also contains the dashboard API. The commit message lists only consent, flags and jobs.

## 4. Known gaps (honest)
- `/api/v1/analytics/funnel` and `/overview` (the pre-audit endpoints) remain and still return placeholder zeros. The UI no
  longer uses them. S9 or the orchestrator should remove them or point them at the dashboard.
- Moderators can read the whole dashboard, including package revenue (cost per qualified lead). Restrict it to ADMIN if
  that is sensitive.
- The `/privacy` page (owned by S7) does not yet describe analytics consent, the retention periods or the withdrawal link.
  Its copy should match the banner.
- Session funnels ignore step order within a session ("in the same session"). The lead step needs a signed-in, consented
  session.
- Cohorts and the return rate are limited to 90 days (the visitor-days retention). After 90 days, user ids are stripped
  from raw events, so the lead step of the search funnel is limited to a 92-day window. That is the maximum window anyway.
- No Prometheus alert rule for the analytics jobs. Alerts are shown in the dashboard only.
- No Playwright E2E for the banner. It is covered by the vitest component tests.

## 5. Production notes
- **Kill switch rollout.** `APP_ANALYTICS_INGESTION_ENABLED` stays `false` by default. With it off, the dashboard marks web
  metrics "Chưa đo" and raises an alert; the database metrics work.
  1. Deploy with it off. The banner appears and consent records are written. Consent recording does not depend on the
     switch, and is rate limited.
  2. Confirm that S5's `analytics-events` rate limit is active.
  3. Set it to `true` on all instances.
  4. Check the dashboard "Sự kiện web mới nhất" value within minutes.
  5. Roll back by setting it to `false`: data already collected stays.
- **Retention periods** (env overrides, ISO-8601):

  | Data | Kept for | Env |
  |---|---|---|
  | Identifiers (anonymous/session/user id, UTM) on raw events | 90 days | `APP_ANALYTICS_RETENTION_IDENTIFIERS` |
  | Raw events | 180 days | `APP_ANALYTICS_RETENTION_RAW_EVENTS` |
  | Visitor days (cohorts) | 90 days | `APP_ANALYTICS_RETENTION_VISITOR_DAYS` |
  | Daily aggregates (no identifiers) | 760 days | `APP_ANALYTICS_RETENTION_DAILY_METRICS` |
  | Consent records | 3 years | `APP_ANALYTICS_RETENTION_CONSENT_RECORDS` |
  | Device flags | 180 days | `APP_ANALYTICS_RETENTION_DEVICE_FLAGS` |

  The policy refuses identifiers kept longer than the raw events, visitor days kept longer than identifiers, and raw
  retention under 8 days. The legal owner should confirm these periods.
- **Other env:** `APP_ANALYTICS_INTERNAL_NETWORKS` (office/VPN CIDRs), `APP_ANALYTICS_BOT_MAX_EVENTS_PER_HOUR` (600),
  `APP_ANALYTICS_MAINTENANCE_ENABLED` (true), `APP_ANALYTICS_HOURLY_CRON` / `APP_ANALYTICS_NIGHTLY_CRON` (Vietnam time),
  and the frontend build variable `VITE_RUM_SAMPLE_RATE`. `docker-compose.yml` passes the internal-networks and maintenance
  variables through. `.env*.example` files were not edited (by rule), so the orchestrator should add these variables there.
- **V095** creates indexes on `leads`, `listings` and `analytics_events` without `CONCURRENTLY` (the migration runs in a
  transaction with `lock_timeout 5s`). This is fine at current sizes. On a large production table, create them
  beforehand with `CONCURRENTLY IF NOT EXISTS` (`idx_leads_created`, `idx_listings_created`).
- Dependency added: `web-vitals@^4.2.4`, in a lazy chunk. Bundle budgets were raised by about 1 kB for `/search`,
  `/compare` and `/billing` (the shell grew by the consent gate and the RUM starter). `admin/analytics` budget is 140 kB.

## 6. Follow-ups
- **S7:** privacy page copy for analytics consent (purposes, periods above, withdrawal via the footer link).
- **S9:** remove or replace `/analytics/funnel` and `/overview`; the OpenAPI snapshot now includes the dashboard and consent
  endpoints.
- **S10:** run EXPLAIN on the dashboard queries at 1M events (BRIN on `occurred_at`; `(name, occurred_at)`).
- **S11:** E2E of the banner (refuse → no `/events` request; accept → events sent).
- **Infra:** Elasticsearch and MinIO were started in `bds-test` for the full verify and left running (shared).
