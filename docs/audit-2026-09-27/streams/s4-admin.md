# Stream S4-ADMIN — moderation v2, admin desks, trust decisions, billing F18, assets/dedupe (wave W2)

Branch `audit/s4-admin` (from `audit-2026-09-27` @ `b875b91`). Flyway V055–V060 used (range V055–V064), backend port 18115,
Vite 5315, Redis DB 6, E2E DB prefix `s4_e2e`.

## 1. How to verify

```sh
eval "$(scripts/test-infra.sh env)"; cd backend && sh mvnw -B -ntp verify        # JDK 17, MAVEN_OPTS=-Xmx1g
cd frontend && npm ci && npm run lint && npm run typecheck && npm run build && npx vitest run && npm run check:bundle
E2E_BACKEND_PORT=18115 E2E_FRONTEND_PORT=5315 E2E_REDIS_DB=6 E2E_DB_PREFIX=s4_e2e \
  scripts/e2e-local.sh --suites "admin authenticated navigation" --projects chromium-1440
```

Results on the final commit:
- Backend `mvnw verify`: **184 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** (PostgreSQL/PostGIS test server).
- Frontend: lint 0 warnings, `tsc --noEmit` 0 errors, `npm run build` OK, vitest **142/142**, bundle budgets all ok.
- E2E (chromium-1440, fresh seeded stack): **admin 3/3**, authenticated 2/2, navigation 5/5.

## 2. Scope done — requirement → evidence

| ID | Delivered | Evidence | Status |
|---|---|---|---|
| F08.2 / UI-18 | Moderation queue: server paging (≤100, stable `submitted_at, listing_id`), filters ALL/FIRST_SUBMISSION/EDIT/SLA_BREACH/DUPLICATES/MINE/UNCLAIMED, stats; claims 30 min; decisions with real moderator, reason code, note; bulk ≤50 only under own claims with per-item result; diff vs **public** revision; UI table with SLA/age badges, drawer (diff, duplicates, history), reasoned dialog, bulk bar stating exact scope | `ModerationV2Tests` (7): claim conflict, real actor (no `…0099`), expired claim takeover/release, bulk scope + >50 refused, concurrent approvals → one 200 + one 409 and one decision row, filters/paging/diff, 403 for USER; E2E `admin.spec.ts` moderator journey | DONE |
| P-14 (random audit, report handling) | Weekly locked sampling (5 % of last week's approvals, 1..20, once per week), second-look review with reason; report queue below | `ModerationV2Tests.weeklyRandomAuditDrawsOnceAndRecordsTheSecondLook` | DONE (S4 part) |
| Metrics | `bds.moderation.queue.size`, `.sla.breached`, `.oldest.age.seconds` (30 s cached) | gauge presence asserted in `ModerationV2Tests` | DONE |
| P-05 / D-10 | `property_assets` + FK `listings.property_asset_id`; `listing_fingerprints`; exact fingerprint on approval links a shared asset; blocking keys (district+type+purpose, price bucket ±1, area ±5 %) then pg_trgm similarity only over the block (cap 200); `listing_duplicate_candidates` (score, reasons, OPEN/DISMISSED/CONFIRMED) in the queue with actions; locked sweep fingerprints new submissions | `ModerationV2Tests.duplicateCandidatesComeFromTheBlockOnlyAndShowInTheQueue`: 300 noise listings in other blocks, `compared == 1`; decided pair not reopened; same asset for exact match | DONE |
| Admin listings (UI-19 area) | `/api/v1/admin/listings` paged + filters (status, owner, keyword, district, source, pending edit), revisions, status history, LOCK/UNLOCK/HIDE/UNHIDE with mandatory reason (`listing_status_history`), admin-only private preview (`no-store`, `X-Robots-Tag`) | `AdminListingsAndUsersTests` (listing search, status actions, preview) | DONE |
| R-3 | Private preview ADMIN only, never cached; draft stays 404 publicly | `privatePreviewIsAdminOnlyAndNeverCached` | DONE (S4 part) |
| UI-20 | Role change with reason (not own, never last active ADMIN under an advisory lock, one role row), lock/unlock with reason (sessions revoked), `user_admin_actions` history, list without phones; KYC documents need password + reason, logged in `kyc_access_log`; staff reads of private images require grant **and** a live log entry for the image owner | `roleChangeRulesAndHistory`, `theLastActiveAdminCannotBeDemotedEvenUnderConcurrency`, `theOnlyActiveAdminCannotBeDemoted`, `kycDocumentsNeedPasswordAndReasonAndEveryOpeningIsLogged`; E2E role-change journey | DONE |
| UI-21 | Separate report queue `/2026/nhadatchuan/admin/reports` (lead oversight page keeps leads only): SLA P0 1 h / HIGH 4 h / MEDIUM 24 h / LOW 72 h, ordered by due time, breached/mine filters, claims, `report_events` history, notes, severity change with reason, emergency hide kept (claim-aware, `listing_status_history`), owner outcome for FAKE_SOLD | `ReportDeskTests` (5) | DONE |
| Reporter phone privacy | New values encrypted (`PiiProtectionService.protect`), API returns mask only; legacy plaintext encrypted by `ReporterPhoneEncryptionMigrator` | `reporterPhoneIsStoredEncryptedAndOnlyShownMasked`, `legacyPlaintextPhonesAreEncryptedInPlaceAndTheMigrationIsResumable` | DONE |
| P-04 / UI-22 / contract §6 | Trust decisions with reason codes per kind, decider, validity (ownership 180 d, KYC 24 months), revoke, four-eyes (no decision on own identity/listing), history `trust_decisions`, evidence comparison (name ignoring accents, certificate reused by another owner, address similarity, identity validity, private docs via logged grant), `listingTitle` fixed; daily locked reminders 30 days before expiry (in-app + outbox, once per validity) and expiry of ownership | `TrustDecisionTests` (5) | DONE (S4 part; badges read by S2) |
| UI-12 `/kyc` | Scope copy (proves identity, not ownership/legal status), why documents, who sees them, retention = "chưa có dữ liệu" placeholder, status timeline, rejection reason, validity, resubmission after rejection/expiry | `/api/v1/kyc/me/status` in `TrustDecisionTests`; UI `KycScopePanel` | DONE (retention period not configured — see gaps) |
| F18.2 / R-4 | Idempotency-Key scoped to actor, bound to plan (409 on reuse); advisory lock per user+plan returns the open order; single-effect approval | `twentyParallelOrderRequestsCreateOneOrder`, `idempotencyKeyIsScopedToTheActorAndBoundToThePlan`, `concurrentApprovalsGrantTheQuotaOnce` | DONE |
| F18.3 | Bank settings compare-and-set with `expectedVersion` (409 `BANK_SETTINGS_CONFLICT`, 400 when missing); UI offers reload on conflict | `bankSettingsUseCompareAndSet` | DONE |
| F18.4 / UI-23 / P-11 | `package_order_events`; reconciliation paged by status with counts; receipt → exact match approves, mismatch → `EXCEPTION`; resolution APPROVE_WITH_NOTE / REJECT / REFUNDED_OFFLINE with note; customer mails through `MailOutbox`; deposit/escrow disabled without `FEATURE_REAL_TRANSACTIONS` | `mismatchedPaymentBecomesAnExceptionWithAnExplicitResolution`, `RealTransactionsFlagTests`; E2E billing-exception journey | DONE |
| UI-11 `/billing` | Paged history, clear statuses with next step, snapshots (plan/bank at order time), per-order history, idempotent creation, "service fee, not a property deposit" copy | covered by API tests above; UI reviewed manually | DONE |
| F08.4 | Billing mine + queue paged | tests above | DONE |
| F08.6 (own queries) | Every S4 list: bounded size + stable tie-breaker id | code review of all S4 queries; paging asserted in moderation/admin/billing tests | DONE |
| DS-13 | Priority → claim → compare → reasoned decision → audit; statuses text + icon (never colour only); approve/reject buttons separated (`justify-between`, gap-6) | UI | DONE |

## 3. Contract deviations / breaking changes (all consumers are in this branch)

1. `GET /api/v1/moderation/queue` returns a page envelope `{items,page,size,total,stats}` instead of an array.
2. `GET /api/v1/billing/orders` returns `{items,page,size,total}`; `GET /billing/admin/reconciliation` returns a page with `counts`.
3. `PUT /billing/admin/bank` requires `expectedVersion` once a row exists.
4. Approve responses keep `success/status/publicRevisionId` and add decision fields; approval without `reasonCode` defaults to `MEETS_STANDARDS`.
5. Identity revocation has no REVOKED KYC status in the contract: it becomes `REJECTED` + `revoked_at` (trust history says REVOKED).
6. Four-eyes rule on trust decisions (not in the brief): no staff decides their own identity/listing check (409 `OWN_DECISION`).
7. `ApiException` (coded Problem Details) and `OptimisticLockingFailureException → 409 CONCURRENT_UPDATE` added to `GlobalExceptionHandler`.
8. Tests adapted (not weakened): `BdsApplicationTests` (paged queue; separate reviewer for trust decisions), `BillingNotificationTests` (CAS version), `SensitiveResponseCacheFilterTests` (staff KYC grant mocked), `SchemaMigrationTests` (pinned to V029, then later migrations over the legacy rows), `AnalyticsRecorderTests` (moderator must be a real user).

## 4. Policies

- Moderation SLA 24 h from submission; claim 30 min (renewable; expired claims are taken over, no sweeper); bulk ≤ 50.
- Random audit: Monday 02:00 Asia/Ho_Chi_Minh, 5 % of previous week's approvals, min 1, max 20, once per week.
- Report SLA: P0 1 h, HIGH 4 h, MEDIUM 24 h, LOW 72 h from submission; report claim 30 min.
- Validity: ownership 180 days, identity 24 months; reminder 30 days before, once per validity period; daily task 03:15.

## 5. Reporter-phone migration notes

V057 widens `listing_reports.reporter_phone` to TEXT (metadata-only). Encryption needs the PII key, so it runs in the app:
`ReporterPhoneEncryptionMigrator` (ApplicationRunner, task lock `reporter-phone-encryption`, batches of 500 with
`FOR UPDATE SKIP LOCKED`, skips values already `v1:`, blanks → NULL, logs only a row count). Resumable and idempotent.
Until it has run, APIs show only a last-3-digits mask. Disable with `app.reports.encrypt-legacy-phones=false`.
Rollback: the previous release would display the ciphertext string (no leak); redeploy forward.

## 6. Known gaps (honest)

- KYC document retention period is not configured anywhere; `/kyc` says so ("chưa có dữ liệu cấu hình") instead of inventing one.
- FAKE_SOLD owner outcome: S4 reads `report_events` of type `OWNER_RESPONSE` / `AUTO_PAUSED`; S3a's hook must write them via `ReportDeskService.recordEvent(...)` (see follow-ups). Public report creation is otherwise untouched (severity stays MEDIUM; staff escalate with reason).
- Duplicate detection for submissions runs in a minute-level sweep (no hook in S3a's write path); approval recomputes synchronously after commit.
- `MediaController` staff read path (grant + log) is unit-level covered (`staffMayRead`); no MinIO end-to-end test.
- Admin UI pages have no dedicated vitest; covered by E2E journeys and typecheck. E2E ran on chromium-1440 only.
- V059 creates the unique open-order index only if production rows comply (warning otherwise); the advisory lock enforces it regardless.
- `IMPLEMENTATION_PLANS_HISTORY.md` / `WALKTHROUGHS_HISTORY.md` not edited (reserved for the orchestrator).

## 7. Deploy notes

Migrations V055–V060 are additive (V059 swaps the `package_orders` status CHECK; V060 creates extension `pg_trgm` — needs
contrib, present in the postgis image). New optional properties: `app.moderation.audit-cron`, `app.moderation.dedupe-sweep-ms`,
`app.trust.expiry-cron`, `app.reports.encrypt-legacy-phones`. No new secrets. Keep `APP_JOBS_ENABLED=true` on one instance
(billing/trust mails use the outbox).

## 8. Follow-ups for other streams

- **S3a:** on FAKE_SOLD owner confirmation/timeout call `ReportDeskService.recordEvent(reportId, "OWNER_RESPONSE" | "AUTO_PAUSED", actor, note, data)`; record auto-pauses in `listing_status_history` with action `AUTO_PAUSE`.
- **S2:** trust reads: KYC `expires_at`, ownership `expires_at`/`revoked_at` are now set on every decision; `listings.is_verified_owner` is cleared on revoke/expiry.
- **S5-B:** admin MFA should gate the new admin endpoints; rate-limit policy not needed (no new public endpoint).
- **S9:** fold `ApiException` into the Problem Details work; OpenAPI snapshot will include the new admin endpoints.
