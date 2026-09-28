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

## 9. Review 2 fixes

All findings of Review 2 are fixed. The only new migration is V061. V060 was edited before merge, which is allowed because it never ran outside tests.

| # | Finding | Fix | Test |
|---|---|---|---|
| MAJOR 1 | No four-eyes rule in moderation or random audit | `ModerationWorkflowService.decide` returns 409 `OWN_DECISION` when the actor owns the listing. This covers single approve/reject and bulk, where the item outcome is `OWN_DECISION`. `RandomAuditService.review` returns 409 `OWN_DECISION` when the reviewer is the moderator of the original approval. | `ModerationV2Tests.nobodyModeratesTheirOwnListingSingleOrBulk`, `weeklyRandomAuditDrawsOnceAndRecordsTheSecondLook`. `BdsApplicationTests` now approves with a separate moderator. |
| MAJOR 2 | Ownership approved on an expired identity | Approval requires the identity to be `VERIFIED`, with `revoked_at IS NULL` and `expires_at` null or in the future. The identity row is read `FOR SHARE`. | `TrustDecisionTests.ownershipIsNotApprovedOnAnExpiredOrRevokedIdentity` |
| 3 | Idempotency-key race across plans | An advisory lock is taken on (user, key) before the (user, plan) lock, always in that order. A key insert that affects 0 rows now returns 409 instead of passing silently. | `BillingReconciliationTests.oneKeyRacedAcrossPlansIsBoundToExactlyOneOrder`: 10 parallel requests, same key, 2 plans → 1 order |
| 4 | Legacy approve skips reconciliation | Deprecated. The route is ADMIN only. It requires a note of at least 5 characters, like `APPROVE_WITH_NOTE`. The row is locked and the order must be `TRANSFER_REPORTED`, otherwise 409 `ORDER_STATE_CHANGED`. Legacy reject returns 409 on the wrong state. No UI uses legacy approve; new clients use `/receipt`. | `legacyApproveNeedsANoteAndTheReportedStateAndOtherwiseConflicts` |
| 5 | Report actions not state-guarded | `assertActionable` locks the case row `FOR UPDATE`, then checks it exists (404), is open (409 `REPORT_CLOSED`), and is not claimed by another staff member (409 `CLAIM_CONFLICT`). Concurrent actions serialise on the lock and the second one sees the closed state. The row lock is used instead of an `@Version` column. | `ReportDeskTests.closedCasesCannotBeActedOnAgainAndConcurrentClosingHasOneEffect`: parallel resolve and dismiss → one 200, one 409, one closing event |
| 6 | Reporter-phone migration gaps | The seeder stores `pii.protect(...)`. The migrator is now `@Scheduled` instead of an `ApplicationRunner`: first run about 30 s after start, then hourly, under a task lock. It no longer blocks readiness, is resumable, and catches plaintext rows written by old instances. | `UatDataSeederTests` asserts no plaintext seeded phones; the migrator tests are unchanged |
| 7 | Migration lock and index details | V060 adds the FK `NOT VALID`; V061 runs `VALIDATE CONSTRAINT`, which takes SHARE UPDATE EXCLUSIVE. V061 cancels older duplicate `CREATED` orders (with a history event), then creates `uq_package_orders_open_per_plan` if no duplicates remain. | `SchemaMigrationTests` (full migrate + no-op re-run) |
| 8 | Duplicate-detection quality | The candidate query covers the block OR the exact fingerprint, whatever price or purpose. Rows are ordered by exact match, then similarity, before the 200 cap. `decide` locks the pair and returns 409 `DUPLICATE_ALREADY_DECIDED` unless it is OPEN. CONFIRMED links both listings to one asset: the first one's, else the other's, else a new asset from the fingerprint. | `exactFingerprintIsComparedAcrossPriceBucketsAndConfirmedPairsShareOneAsset`, re-decide 409 in `duplicateCandidatesComeFromTheBlockOnly...` |
| 9 | Trust semantics | See the rules below this table. | `revokingOneCheckKeepsTheBadgeWhileAnotherIsValidAndRevokingIdentityCascades`, `expiryBacklogLargerThanOneBatchIsClearedInOneRun` (450 rows) |
| 10 | ReasonDialog preselects a reason | The choice starts empty ("— Chọn lý do —"). Submitting without a choice shows "Cần chọn …". | E2E `admin.spec.ts` moderator journey checks the empty value, the error, then selects a reason |
| 11 | Admin budgets raised without justification | Measured per chunk at gzip-1. The shared admin kit, cached across admin routes, is about 11.5 kB: adminUi 4.5, DataTable 2.9, Skeleton 1.4, Dialog 0.9, Pagination 0.8, Badge+Chip 1.0. The route chunks add the rest: moderation 7.7 kB, billing 3.4 kB. Budgets stay at measured + about 10%, the same policy as every other route. The breakdown is recorded as `note` in `bundle-budget.json`. | `check:bundle` all ok |
| NIT | Grant token not bound to its log row | `kyc_access_log.grant_token_hash` is written on open. `staffMayRead` requires the log row of the presented token, so a grant obtained without a reason does not work. | `AdminListingsAndUsersTests.kycDocuments...` (unreasoned grant → false) |
| NIT | `escalate` checks before locking; escalation logged as `NOTE` | `escalate` takes the row lock first. It records the new event type `ESCALATED`, which V061 adds to the CHECK and to which it backfills old NOTE rows carrying `severityTo`. The UI label is "Đổi mức độ ưu tiên". | `fakeSoldOwnerOutcomeIsShownInTheQueue` |

Trust rules (finding 9):
- `revokeOwnership` recomputes the listing badge from the remaining valid checks, using `refreshListingFlag`.
- Revoking an identity revokes every `VERIFIED_OWNER` check approved with that identity. Each revocation uses the same reason code, writes its own history row and recomputes its listing's badge.
- Pending checks are not revoked; they stay pending but cannot be approved until the identity is valid again.
- Identity expiry does not cascade: each ownership check keeps its own 180-day validity.
- `TrustExpiryTask` repeats batches of 200, each in its own transaction, until the backlog is empty (safety cap of 500 rounds).

**Production runbook: open-order index.** If V061 logs `package_orders still has several reported/exception orders...`, list the remaining duplicates with this query:

```sql
SELECT user_id, plan_code, array_agg(id ORDER BY created_at) FROM package_orders
WHERE status IN ('CREATED','TRANSFER_REPORTED','EXCEPTION') GROUP BY 1, 2 HAVING count(*) > 1;
```

Resolve each duplicate through `/billing` by recording a receipt, rejecting it or marking it refunded. Then create the index:

```sql
CREATE UNIQUE INDEX CONCURRENTLY uq_package_orders_open_per_plan ON package_orders (user_id, plan_code)
    WHERE status IN ('CREATED','TRANSFER_REPORTED','EXCEPTION');
```

Until then, the advisory lock keeps order creation serialised.

**Rolling-deploy caveat (reporter phones).** While old-release instances are still serving, they write new reporter phones in plaintext. The hourly migrator re-run encrypts those rows. In the meantime every API response shows only the mask.

**Re-verification.**
- Backend `mvnw verify`: see the final numbers in the commit/hand-off.
- Frontend: lint 0, typecheck 0, vitest 15 files / 142 tests, build OK, `check:bundle` all ok.
