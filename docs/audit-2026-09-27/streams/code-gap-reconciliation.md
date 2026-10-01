# Code-gap reconciliation — 2026-09-30

This separates implementation from acceptance. It does not turn TODO/PARTIAL into DONE without evidence.

| Audit group | Code present / completion in this batch | Remaining condition |
|---|---|---|
| R-2 | v2 keyset pagination; SearchApiDatabaseEngineTests traverses 260 rows in every sort. SearchElasticsearchEngineTests compares engine pages. Added stale-map rejection, removal of old markers, retry; snapshot cache refuses a previous query's rows. | CI 36681537495: six search navigation/pagination/lazy-map tests PASS. Full marker/filter parity and manual UX review remain. No remaining 100-row cap demonstrated in the v2 user flow. |
| R-3 | Private-media policy/cache tests already exist. User and staff preview URLs now revoke on expiry/unmount and ignore late responses; staff can reauthenticate. | Review complete role/status matrix; production response verification. |
| R-4 | BillingReconciliationTests.concurrentApprovalsGrantTheQuotaOnce, cross-plan key race test; row locks and single-effect quota/invoice update already shipped. Lead/revision concurrency covered in their streams. | Consolidated PostgreSQL acceptance; no missing approval implementation found. |
| F17.4 | AnalyticsDashboardService already computes KYC funnel/abandonment; admin analytics renders all funnels and measurement state. | Actual traffic for rates; matrix predates S8 dashboard. |
| UI-12 | Scope/rejection/resubmit already shipped; retention disclosure configurable via VITE_KYC_RETENTION_NOTICE, privacy link, explicit unset state. | Owner-approved policy text and actual retention/deletion policy decision; not inferable by code. |
| DS-03/04/05/06/08/15, R-7 | Existing design system and responsive/authenticated axe suites. This batch fixes loading/error/retry on map, makes point controls 44px high, and completes expired/private-image feedback. | All-page visual/manual assistive-tech review, true zoom and real RUM/traffic remain acceptance, not a claim of absent UI implementation. |
| F05.5/F09.2/F09.3/D-05 | Read-model indexes, incremental sync, cache, fallback and 100k/1M CI EXPLAIN capture already merged. | Review archived planner evidence and production-shaped distribution. |
| D-13/R-5 | Mixed 100 read/10 draft-write arrival-rate harness with burst/soak, separate thresholds and persisted-write SQL check added. Existing cache outage, index lag/rebuild tests and metrics remain in use. | One-minute small UAT warm baseline passed on run 36686479978 (601 verified drafts, 0 dropped iterations). Six-phase cache/ES/Redis outage/recovery runner and observability snapshots added; integrated extension pending. Published-write lag, 1M/DB-cold/per-query counts/rebuild measurements remain. Draft creation is not publication throughput. |
| F21.5/R-6 | restore-drill.sh already measures data restore duration and snapshot age. W1 synthetic report contains 16.5s data RTO and 25s/16s DB/object snapshot ages. | Those are synthetic data-only observations, not end-to-end production RPO/RTO. App/deploy/migration compatibility and final proxy response require actual target releases/environment. |
| F11.2 | Proxy/header verification tooling present. | Final CDN/domain responses and access. |
| R-8 | SLA dashboard distinguishes MEASURED/NOT_MEASURED. | Organization must appoint owners/commit SLA targets. |

External primary rows (branch protection, Search Console, KYC policy, credential rotation, production topology,
pilot, user research) remain owner/operations work. No fabricated owner, policy period, capacity number or
production result is inserted to make the matrix look complete.
