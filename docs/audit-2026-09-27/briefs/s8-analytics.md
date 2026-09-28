# Brief S8-ANALYTICS (W4) — consent, bot/internal filtering, retention + aggregation, RUM, dashboards

Branch `audit/s8-analytics`; Flyway **V095–V099** (the plan's V080–V084 are unusable: `out-of-order` is off and V085
exists); backend 18122; Vite 5322; Redis db 10, prefix `s8`.
Parallel in W4: S5-SEC phase B and S7-SEO. S8 touches `root.tsx` (banner mount + footer link) and
`RateLimitPolicies`/`SecurityConfig`/`SensitiveResponseCacheFilter` only to add its own rows.

Read first: `00_PLAN.md`, `01_REQUIREMENTS.md`, `02_CONTRACTS.md` §5, `03_AGENT_RULES.md`, `briefs/wave-rules.md`;
stream reports `s0-be.md` (AnalyticsRecorder, `POST /api/v1/events`, kill switch `APP_ANALYTICS_INGESTION_ENABLED`),
`s0-fe.md` (`track()`, consent module), `s2`/`s3a`/`s3b`/`s4`/`s6` (events they emit; S3b's `lead-funnel`).
Audit: F15.3, F17.4, F19, §4.1 Analytics, §5 admin `/analytics`, §7 observability, P-10, P-13.

Requirement IDs: F15.3, F17.4 (dashboard part), F19.2, F19.3, UI-24, P-10, P-13, DS-15 (RUM part), R-8 (dashboard part),
D-14 (CWV part).

## Backend
1. **Consent (Decree 13/2023/NĐ-CP):** analytics is non-essential processing → opt-in only. `POST /api/v1/consents`
   (public, rate limited, kill switch shared with ingestion) stores a consent record: random client consent id,
   purpose `analytics`, choice granted/withdrawn, policy version, time, user id from the token (never the body), no IP.
   Records are the proof of consent (Art. 11) and are kept for a bounded period. Ingestion stores **nothing** for a
   batch whose consent is not `granted` (202 with `dropped`), so an old or modified client cannot track before consent.
2. **Internal traffic:** staff token (existing) + configurable internal networks (`APP_ANALYTICS_INTERNAL_NETWORKS`,
   CIDR, resolved client IP via `ClientIpResolver`) + devices that sent a staff event are remembered
   (`analytics_client_flags`) so their later anonymous events are internal too; past events of that device are re-marked.
3. **Bots:** UA heuristic (existing) + behavioural flag in the maintenance job (too many events per device per hour)
   → device flagged, its events re-marked `is_bot`; flagged devices are marked at ingestion.
4. **Retention + aggregation** (locked tasks, bounded batches, Vietnam days): hourly recompute of today/yesterday and a
   nightly recompute of the last 8 days into `analytics_daily_metrics` (events + distinct sessions per
   name/device/area/source) and `analytics_visitor_days` (cohorts/return rate); identifiers stripped after 90 days,
   raw events deleted after 180 days, visitor days after 90 days, daily metrics after 25 months, consent records after
   3 years, flags after 180 days. All configurable.
5. **Dashboard API** `GET /api/v1/analytics/dashboard?from&to&device&area&source` (staff, ≤ 92 days): every metric is
   `{value, status: MEASURED|NOT_MEASURED, reason, definition, source}` so “chưa đo” is never shown as 0; sections:
   search→detail→lead funnel (sessions), KYC drop-off (F17.4), posting funnel, lead response SLA, lead→appointment →
   completed, qualified leads, north-star (confirmed appointments / qualified seekers / week), zero-result, return rate,
   weekly cohorts, CWV p75 by metric/device, breakdowns by source/area/device, daily trend, alerts, data freshness.
   Internal and bot traffic always excluded. Queries on indexed ranges (`(name, occurred_at)`, BRIN on time, new
   `leads(created_at)`, `listings(created_at)`), bounded windows and LIMITs.

## Frontend
6. Consent banner (lazy chunk, only when no choice/outdated policy; equal-weight “Đồng ý”/“Từ chối”, link to
   `/privacy`, preferences dialog with purpose description; footer “Tùy chọn quyền riêng tư” reopens it; withdrawal as
   easy as consent). `track()` sends nothing until consent is granted and drops queued events on withdrawal.
7. RUM: `web-vitals` loaded dynamically only after consent, sampled per session (`VITE_RUM_SAMPLE_RATE`, default 0.25),
   route pattern not raw path, one report per metric per page view.
8. Admin `/analytics` page on the new UI kit: filters (range, device, area, source), metric tiles with “Chưa đo” vs 0,
   definitions + freshness, funnels, cohort table, CWV, breakdowns, alerts; CSV export.

## Tests
PostgreSQL integration: consent records + denied batches store nothing; internal network/device flag; behavioural bot
flag; retention strips/deletes only what is due; aggregation idempotent; one fixture journey (search → detail → lead
form → lead → first response → qualified → appointment confirmed → completed) is counted exactly once in every funnel,
while bot/internal copies of it are not; NOT_MEASURED vs 0. Frontend: banner/preferences, no send before consent,
RUM sampling, dashboard rendering of NOT_MEASURED vs 0.

Deliverables per `03_AGENT_RULES.md`: commits, `streams/s8-analytics.md` (kill-switch rollout, retention periods),
final message.
