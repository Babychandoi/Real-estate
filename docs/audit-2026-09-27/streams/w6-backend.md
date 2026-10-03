# W6-BACKEND — audit trail race, R-2, R-3, R-4, F17.4

Branch `audit/w6-backend` (from `13e41a2`). Flyway: `V103__audit_chain_head.sql` (edited in the review round — not deployed anywhere), `V104__audit_chain_check_validate.sql`, `V105__idempotency_response_snapshot.sql`. See [Review round](#review-round-pr-24) for the fixes after the independent review.

**Test environment.** The shared `bds-test` PostgreSQL, Redis and MinIO containers were up, but they could not publish
their host ports: `127.0.0.1:55432`, `:56379` and `:59000` were already held by another Compose project on this machine.
So this stream ran on its own throw-away containers with the same images and settings:

| Service | Container | Port |
|---|---|---|
| PostGIS 16-3.4 (amd64 emulation, `fsync=off`) | `w6be-postgres` | `:55452` |
| Redis 7 | `w6be-redis` | `:56399`, DB 14 |
| MinIO | `w6be-minio` | `:59010` |
| Elasticsearch | shared `bds-test` | `:59200` |

Other settings: JDK 17, Hikari pool 6 (test profile). Load average was 80 during the pre-fix audit reproduction and 5–10
afterwards. The containers were removed at the end.

## Summary

| Row | Status claimed | Evidence | Left |
|---|---|---|---|
| **audit_write_failed** (new bug) | **DONE** | See [Root cause](#audit_write_failed--root-cause) below. **Pre-fix:** reproduction on `main`'s filter, with the swallowed cause temporarily logged. **Post-fix:** `AuditTrailConcurrencyTests` 5/5 pass. | Rows written before V103 may already contain forks; they cannot be repaired. Verification covers events from V103 on, linked to the old history through `genesis_hash`. The owner still has to route the new alert `BdsAuditWriteFailures` to a receiver (alertmanager has none, see ALERT_RUNBOOK). |
| **R-3** — no draft/private media leak; owner/admin/inactive checks; badge scope | **DONE** (backend) | `ListingAccessMatrixTests`: 4 tests, about 450 request assertions. See [R-3 details](#r-3--permission-matrix). **Pre-fix:** on `main`'s listing API, 2 of 4 fail. **Post-fix:** 4/4 pass. | Public images keep `Cache-Control: public, max-age=86400` by design (S1). A browser or CDN copy may outlive a hide by up to 24 h. The origin itself answers 404 at once (asserted). Shortening this is an owner/CDN decision. |
| **R-4** — lead, package approval, revision, retry: no double effect or lost update | **DONE** | Existing lead and revision tests verified passing. New `BillingApprovalConcurrencyTests` (7). See [R-4 details](#r-4--package-approval-races). **Pre-fix:** on `main`'s billing code, 4 of 7 fail. | The admin reconciliation UI does not send `Idempotency-Key` yet (frontend follow-up). The API accepts it, and state guards already stop double effects without it. `If-Match` is still optional on v1 `PUT /listings/{id}/draft` (s3a m3, counted by a metric). |
| **F17.4** — abandonment before and after KYC | **DONE** in code | `KycAbandonmentFunnelTests` (ingestion enabled in the test profile). See [F17.4 details](#f174--kyc-abandonment-funnel). **Pre-fix:** fails on `main` at the first server-side count (`expected 1, was 0`). | Turning on `APP_ANALYTICS_INGESTION_ENABLED` in production is an owner decision. Without it, the two web metrics report NOT_MEASURED with a reason; the server metric is still measured. |
| **R-2** (backend) — search/filter/map/pagination consistent; no 60/100 cap; rent unit | **DONE** (backend) | `SearchConsistencyAcceptanceTests` (both engines, 300 listings) and `MapClusterLimitTests`. See [R-2 details](#r-2--search-consistency). **Pre-fix:** the acceptance test fails at the v1 profile (60-row cap, no total); `MapClusterLimitTests` fails (2,000 of 3,000 cells kept). The v2 engine paging, filters and map checks already passed on `main`. | The first page reports a total capped at 10,000 (`relation: gte`) by contract §4. Results themselves are not capped. The owner draft editor DTO (`DraftView`) has `purpose` but no `pricePeriod`. |

Every "pre-fix" run above used the same test against `origin/main`'s production code, swapped in temporarily, and the
fix was restored afterwards. Logs are kept in the stream's scratchpad and are not committed.

## audit_write_failed — root cause

`AuditTrailFilter` read "the latest hash" with `SELECT … ORDER BY occurred_at DESC LIMIT 1` and inserted its event,
with no lock and no transaction. Two failure modes followed.

1. **Concurrent requests read the same predecessor, so the chain forked.** When two of them also had the same actor,
   method, path, status and `Instant.now()`, their hash material was identical. The second insert then hit
   `audit_events_event_hash_key`, and that audit record was lost.
2. **The audit write needs a pooled connection after the business request.** Under load it timed out waiting for one
   ("Failed to obtain JDBC Connection").

The filter logged `audit_write_failed` without the cause, which is why neither mode was visible. It is not tied to
Redis: the same failures happen with Redis up or down.

**Reproduction on `main`.** The filter was temporarily changed to log the cause; the test ran at load average about 80:

| Scenario | Result |
|---|---|
| 16 identical parallel anonymous `POST /api/v1/listings` | 5 × `audit_write_failed … duplicate key value violates unique constraint "audit_events_event_hash_key"` |
| Parallel draft creates | 2 × `audit_write_failed status=201 cause=Failed to obtain JDBC Connection` |
| 12 parallel drafts with the same title | HTTP 500 on `uq_listings_slug` (45 duplicate-key errors in the log). This is a second bug in the same flow: slug allocation was check-then-insert. |

**Fix.**

`AuditTrail` stores and links events in two steps:

1. **Record.** On the request thread, a single plain `INSERT` of the unlinked event: no read and no lock, so it cannot
   collide.
2. **Link.** One linker at a time, cluster-wide (`audit_chain_head` row, `FOR UPDATE SKIP LOCKED`, every second), gives
   pending events the next `chain_seq`, `previous_hash` and `event_hash` in arrival order. `chain_seq` is UNIQUE, so a
   fork is impossible whoever writes.

Request threads therefore never wait on the chain while holding a connection.

Failure handling:
- A failed insert is retried twice with the same event id (`ON CONFLICT (id) DO NOTHING`), except when no pooled connection could be obtained: no retry then (review round).
- If it still fails, it is counted in `bds.audit.write.failures` and logged with its exception class and message.
- Alert `BdsAuditWriteFailures` fires on any such failure; its promtool test and runbook entry are included.
- Link failures are counted too, and `bds.audit.chain.backlog` reports the number of unlinked events.

`AuditTrail.verify()` recomputes the chain.

Slug allocation now takes `pg_advisory_xact_lock(hashtext('listing-slug'), hashtext(base))`. After five collisions it
uses a random suffix.

**Tests (`AuditTrailConcurrencyTests`, 5/5).**
- 10 parallel draft creates, by the same broker and by different brokers: all 201, one audit record each, no failure
  logged, chain verifies.
- 12 parallel drafts with the same title: all 201, 12 distinct slugs.
- 30 identical writes while two linkers run concurrently: every request audited, no fork, positions 1..n without gaps,
  `verify()` intact.
- A tampered event breaks verification at its own position.
- A transient failure is retried; a persistent one is counted and logged with its cause.

**Other audit writers checked.** `audit_events` is the only hash chain. These tables all use random UUID keys with no
read-then-write chain, so the race does not apply:
- `auth_security_events`, `kyc_access_log`
- `lead_events`, `package_order_events`, `report_events`
- `listing_status_history`, the moderation tables

## R-3 — permission matrix

**Actors:** anonymous, another user, owner, moderator, admin.

**Listings:** draft, pending, active, active with an unapproved edit (and its new image), hidden (PAUSED), expired,
rejected, locked, and the active listing of a suspended owner.

**Endpoints:**
- v1 detail by id and by slug
- v2 detail
- v2 and v1 search
- v2 and v1 seller pages
- owner draft (`/api/v2/me/listings/{id}/draft`)
- admin preview
- public media
- signed-URL issuance
- KYC upload and read

**Leaks found on `main` and fixed:**
- `GET /api/v1/listings/{id}` and `/by-slug/{slug}` served the ACTIVE listing of a **suspended seller** to anonymous
  users. Public viewers now need an ACTIVE listing with an approved public revision of an ACTIVE owner, and they only
  ever get that revision.
- The v1 ownership badge appeared in detail, my-listings, the admin list and profile cards. It used
  `listings.is_verified_owner`, which the daily expiry task clears up to a day late, so **an expired check showed as
  verified**. It is now evaluated at request time (VERIFIED_OWNER, not revoked, not expired), as in v2.
- The v1 search wrapper now uses the validity at request time.
- The v1 profile showed an **expired or revoked identity** as verified.

**Also asserted:**
- v1 detail is always `no-store`.
- The admin preview is ADMIN-only and `no-store`.
- The owner draft is visible to the owner and staff only; strangers get 404 and anonymous users get 401.
- Signed URLs go to the owner (own images) and staff only.
- KYC documents are never public, never signed, and need password re-confirmation or a reasoned staff access.
- Hiding a listing removes it at once from v2 detail (410, despite a warm Redis detail cache), v1, search and public
  media.
- Badges keep their scope: identity verified never means ownership verified. Expired identity reads EXPIRED; expired
  ownership reads EXPIRED.

## R-4 — package approval races

Pre-existing tests verified in the full run: `LeadSubmissionConcurrencyTests` (7), `ListingWritePathTests`
(revision `If-Match` / concurrent saves) and `ModerationV2Tests.concurrentApprovalsOfOneRevisionHaveExactlyOneEffect`.

`BillingApprovalConcurrencyTests` checks every effect of an approval: quota, invoice, APPROVED event, in-app
notification and mail job.

**Races that `main` already handled correctly** (these tests pass on `main`):
- 8 admins record the same matching receipt at once: one 200 and seven 409, one effect.
- approve-with-note vs receipt vs reject, three rounds: one outcome.
- APPROVE_WITH_NOTE vs REFUNDED_OFFLINE vs REJECT on an EXCEPTION order, three rounds: one outcome, one notification.

**Bugs found on `main` and fixed:**
- A **user cancel that lost the race** to an admin receipt answered 200 with the APPROVED order, which the client read as
  "cancelled". It now answers 409 `ORDER_STATE_CHANGED`. Over 6 raced rounds, every round ended in exactly one state.
  Repeating a transition that already happened (a second cancel, a second report) is still answered 200.
- **Retries of the admin approval endpoints** used to give 409 even when the first attempt had committed. Receipt,
  resolve, approve and reject now accept an optional `Idempotency-Key`:
  - The key is scoped to the admin and bound to the action, the order and the payload, and kept for 24 h.
  - 5 parallel attempts plus a late retry with the same key: all 200, 5 with `Idempotent-Replayed: true`, one effect.
  - The same key with another payload, or another admin's retry: 409.
  - One key raced over two orders binds exactly one; the other request is rolled back.

## F17.4 — KYC abandonment funnel

The lead API now records `lead_kyc_blocked` server-side when it refuses a request with `KYC_REQUIRED`:
- It is a new catalog v1 server event, added to contract §5.
- One event per requester, listing and day.
- It is written in its own transaction after the refused one has rolled back, and it never changes the 409.

`lead_submitted` was already recorded server-side. `lead_form_opened` and the detail-page KYC gate can only be emitted
by the browser.

The dashboard (`GET /api/v1/analytics/dashboard`) now exposes three abandonment rates, each with its definition:

| Metric | Measures | Source |
|---|---|---|
| `leadAbandonmentWithoutKyc` ("before KYC") | Sessions that opened the lead form without meeting the KYC wall and sent no lead | Web |
| `kycAbandonment` ("after KYC", existing) | Sessions that met the KYC wall and sent no lead | Web |
| `kycAbandonmentServer` | Requesters blocked by the API who sent no lead after their first block | Server events only, no consent needed |

**Test.** Five users go through the real lead API and consented web sessions:
- u1 is blocked twice and never verifies.
- u2 is blocked, verifies and then sends a lead.
- u3 sends a lead without meeting the wall.
- u4 and u5 open the form and leave.

**Results:**
- Funnel steps 5 / 2 / 1.
- After-KYC abandonment 50 %.
- Before-KYC abandonment 66.7 % (2 of 3).
- The server metric grows by 2 blocked requesters, 1 of whom never sent a lead.
- `lead_kyc_blocked` is stored once for u1, despite the retry.

## R-2 — search consistency

`SearchConsistencyAcceptanceTests` seeds 300 public listings (200 sale, 100 rent, four districts, 30 without a
location) and checks:

- **Both engines.** Five filter sets (purpose, districts, price range, bbox, sorts) are walked to the end on
  Elasticsearch and on PostgreSQL. Each gives exactly the expected set, the correct `total` with relation `eq`, no
  duplicates, and the same order on both engines.
- **Map.** For sale and rent, the map total equals the list total for the same filters, and the clusters add up to the
  located matches. Zoomed-in points equal the list.
- **Seller pages.** The v2 cursor pages and the v1 pages (`page`/`size`, `X-Total-Count: 300`) both return all 300
  listings.
- **v1 search.** It reaches every page up to 2 400 results deep (400 beyond, review round); it used to return `[]` after page 0.
- **Rent unit.** Every rent price carries `MONTH` on the v2 cards, map points, v1 cards, v1 detail and v2 detail; every
  sale price carries none.

**Code changes:**
- The map kept only the 2,000 largest grid cells, so the clusters could add up to less than the list total. It now
  uses a cell size computed up front from the bbox and zoom so that at most 2 000 cells exist, with one query (`MapClusterLimitTests`; review round).
- `pricePeriod` was added to the v1 detail, v1 search and v1 profile card DTOs.
- The v1 profile listings are paged, and marked deprecated with a link to the v2 seller endpoint.

## Checks

| Check | Result |
|---|---|
| `sh mvnw -B -ntp verify` (backend, full) | BUILD SUCCESS — 470 tests in 93 classes, 0 failures, 0 errors, 0 skipped (3 min 15 s). The first full run had 2 failures, both fixed: the architecture rule (the shared kernel must not call `iam`, so `AuditTrail` has its own SHA-256), and the R-2 seed overlapping another test's fixed Hà Nội box (the seed moved to Nha Trang coordinates). |
| `npm run lint` | 0 errors, 0 warnings (`--max-warnings=0`) |
| `npx tsc -b` | 0 errors |
| `npx vitest run --testTimeout=30000` | 41 files, 257 tests passed |
| `npm run build` | OK |
| `npm run check:bundle` | OK (all routes within budget) |
| `npm run check:api` / prettier on the generated types | match / clean |
| `promtool test rules` / `check rules` (prom/prometheus v3.5.0) | SUCCESS / 34 rules |

The only frontend change is the regenerated `app/shared/api/generated/openapi.ts`, which follows the OpenAPI snapshot.

## Review round (PR #24)

The reviewer's tests (`0bcf613`, 4 classes, 10 tests) were cherry-picked unchanged as `dd1e222`. To confirm them, I swapped
`fdd9fe9`'s production code back in on a private PostGIS container (`:55452`, 4 GiB): **8 of 10 failed**, as reported.
On the current branch all 10 pass. No reviewer test was weakened or adjusted.

| Finding | Fix | Evidence (fails on fdd9fe9 → passes now) |
|---|---|---|
| **MAJOR 1** — v1 `?page=N` ran N+1 unbounded searches | A v1 page is served from v2 pages of 48. One request runs at most 50 searches. Results deeper than 2 400 (`page × size`) get 400 `INVALID_FILTER`, pointing to the v2 cursor API. New rate-limit policy `search-v1` = v2's 300/min per IP. `CURSOR_ENGINE_CHANGED` restarts the walk once on the new engine instead of returning 409. | `LegacySearchV1PagingReviewTests` (fail → pass). `LegacySearchV1BoundsTests` (3): depth 400 without any search, deepest page = exactly 50 searches, engine switch → 200, policy equal to v2. |
| **MAJOR 2** — per-title slug lock deadlocked CSV imports | Each slug candidate is claimed with `pg_try_advisory_xact_lock`, which never waits: a candidate held by another open transaction is skipped. As a result, no insert ever meets an uncommitted slug on the unique index. Any remaining `PessimisticLockingFailureException` (deadlock, lock timeout, serialization) → 503 `TRY_AGAIN` with `Retry-After: 1`, never 500. | `SlugAllocationLockReviewTests`: on fdd9fe9, "deadlock detected" → pass now. `W6ReviewFollowUpTests.aLockFailureIsA503ProblemNotA500`. `AuditTrailConcurrencyTests` same-title test still passes (12 parallel drafts → 12 slugs). |
| **MAJOR 3** — audit retries tripled the wait under pool exhaustion | No retry on `CannotGetJdbcConnectionException`. Other failures are still retried, counted and logged with their cause. | `AuditTrailReviewTests.underAnExhaustedPool…` (fail → pass, < 1 s with a 500 ms timeout). |
| **MINOR 4** — previous-image rows after V103 never chained; `verify()` said intact | The head row gets `cutover_at`. The linker also takes rows after it that have a hash but no position (rolling deploy) and re-hashes them into the chain, using two partial indexes in a `UNION ALL`. `verify()` reports `unchained`; `intact` = valid chain AND none unchained. | `AuditTrailReviewTests.anEventStoredByThePreviousImage…` (fail → pass). `W6ReviewFollowUpTests.verifyReportsEventsLeftOutsideTheChain…` |
| **MINOR 5** — a retry stored the event twice | One UUID per record, `INSERT … ON CONFLICT (id) DO NOTHING`. | `AuditTrailReviewTests.aRetryAfterACommittedButUnacknowledged…` (fail: 2 rows → pass: 1). |
| **MINOR 6** — a replay returned the current order state | The response body is stored with the key (`api_idempotency_keys.response_snapshot`, V105), and the replay returns it. | `BillingReviewIdempotencyReviewTests.aReplayReturns…` (fail: APPROVED → pass: EXCEPTION). |
| **MINOR 7** — billing keys never purged; an expired key blocked forever | Billing keys (`billing-order:`, `billing-review:`) live 24 h (legacy rows count from `created_at`). The lookup ignores expired keys. Binding takes over an expired key (`ON CONFLICT … DO UPDATE … WHERE expired`). `IdempotencyKeyPurgeTask` purges both scopes. | `…anExpiredAdminReviewKeyIsPurged`, `…aKeyThatExpiredCanBeUsedForANewAction` (fail → pass). `LeadSubmissionConcurrencyTests.expiredKeysCanBeReusedAndArePurged` still passes (the unrelated `billing:` scope is kept). |
| **MINOR 8** — stalled linker unnoticed | New `bds.audit.chain.last.link.success` gauge. The backlog gauge is now also updated when a run throws. `spring.task.scheduling.pool.size` = 4. A missing head row is recreated from the chain, logged as `audit_chain_head_missing` and counted as a link failure. New alert `BdsAuditChainStalled`: no completed run for 5 min, or the backlog is never drained over 10 min and growing. | promtool: 3 new test groups (healthy / stopped / growing), `test rules` SUCCESS, 35 rules. `W6ReviewFollowUpTests.aDeletedHeadRowIsRecreated…`, `…theBacklogGaugeIsUpdatedEvenWhenTheLinkerFails`. |
| **MINOR 9** — V103 lock duration | **V103 edited** (not deployed). All metadata-only changes, plus `CHECK … NOT VALID`; the CHECK is validated in **V104** under SHARE UPDATE EXCLUSIVE. Indexes stay plain `CREATE INDEX` (Flyway runs each migration in a transaction; `CONCURRENTLY` would need non-transactional migrations and `postgresql.transactional.lock=false`). The runbook documents pre-creating them `CONCURRENTLY IF NOT EXISTS` for large tables. | Measured on a 1 M-row, 289 MB `audit_events` (PostGIS 16, amd64 emulation, fsync off): see the timing table below. |
| **MINOR 10** — map re-aggregated per doubling | The cell is computed up front from bbox and zoom, so the grid has at most 2 000 cells; then one query. | `MapClusterLimitTests` (rewritten: grid ≤ 2 000 at zooms 3–20, finest grid that fits, exactly one `mapClusters` call). `SearchConsistencyAcceptanceTests` still adds up exactly. |
| **MINOR 11** — account billing after a 409 | Reloads the orders. Shows "Yêu cầu vừa được xử lý nên thao tác chưa được thực hiện. Trạng thái hiện tại: <label>." with no raw status code. | `frontend/app/routes/_account.billing.test.tsx` (new). |
| NIT — `verify()` loads everything; no operator path | `verify()` pages through 5 000 rows at a time. New ADMIN endpoint `GET /api/v1/admin/audit-chain/verification`. Runbook section `BdsAuditChainStalled` explains how to run it. | `W6ReviewFollowUpTests` (admin 200 / moderator 403, intact after linking). |
| NIT — matrix body assertions | Every state now carries real private text: description, internal moderator note, owner e-mail, never-approved title, pending-edit title and description. PAUSED, EXPIRED and LOCKED now use real tokens. Private image keys are asserted absent from every non-insider body (v1/v2 detail, both searches, both seller pages). | `ListingAccessMatrixTests` 4/4. See the `ownerId` note below. |
| NIT — CORS | CORS now exposes `X-Total-Count`, `Idempotent-Replayed` and `X-Order-Reused`. | `W6ReviewFollowUpTests.theSpaCanReadTheTotalAndReplayHeadersCrossOrigin`. |
| NIT — v1 profile `size>100` | Clamped to 1..100 (and `page` to ≥ 0) instead of answering 400. | `SearchConsistencyAcceptanceTests` (`size=500&page=-1` → 100 cards). |

**MINOR 9 timings** (1 M rows, setup as above):

| Step | Time | Lock held |
|---|---|---|
| V103 metadata changes | < 5 ms | ACCESS EXCLUSIVE |
| `uq_audit_events_chain_seq` | 1.18 s | SHARE (reads continue, audit inserts wait) |
| `idx_audit_events_unlinked` | 0.33 s | SHARE |
| `idx_audit_events_unchained` | 0.88 s | SHARE |
| V104 `VALIDATE CONSTRAINT` | 0.33 s | SHARE UPDATE EXCLUSIVE |

The linker query, after 900 k linked rows, takes about 30 ms.

**Notes for the owner:**
- **`ownerId` in v1 detail.** It is the seller's public id, the same id v2 exposes through the seller page link. It is not treated as private. The private owner data asserted absent is e-mail and phone.
- **`lead_kyc_blocked.user_id`.** Left as is, as instructed. It stores the requester's user id (the same as `lead_submitted`), so the server metric can count users. Whether that identifier should be kept, pseudonymised or shortened for retention is an owner decision.

**Checks after the review round:**

| Check | Result |
|---|---|
| `sh mvnw -B -ntp verify` | BUILD SUCCESS — 488 tests in 99 classes, 0 failures, 0 errors, 0 skipped (4 min 2 s) |
| `npm run lint` | 0 |
| `npx tsc -b` | 0 |
| `npx vitest run --testTimeout=30000` | 42 files, 258 tests passed |
| `npm run build` | OK |
| `npm run check:bundle` | OK |
| `npm run check:api` | match |
| prettier on touched files | clean |
| promtool | `test rules` SUCCESS, `check rules` 35 rules |

## Resume / review round 3 — 2026-10-03

This section supersedes the round-1/2 descriptions above where they differ. All W6 changes remain on PR #24 until the
orchestrator merges them after PR #23 (V100–V102). No production command or production data was used in this round.

- **Concurrent verification:** capture the head first and verify only the committed prefix through that position.
  `AuditTrailRound2ReviewTests` covers the original race; `W6ReviewFollowUpTests.aLinkerCommitAfterTheHeadReadIsOutsideTheVerifiedPrefix`
  also appends *after* the head was captured and proves no false tamper report.
- **Routine backlog:** expose both pending and stale (>5 minutes) unchained counts. A fresh event awaiting the normal
  one-second linker remains intact; a stuck event does not. `verifyReportsTheBacklogSeparatelyAndOnlyAStuckEventMakesTheChainNonIntact`.
- **Old image / clock skew:** V103 adds `stored_at` without backfilling, then installs its DB-clock default. The linker
  chains every post-V103 insert by this marker even if an old pod supplies an `occurred_at` before cutover. Legacy rows
  retain their hashes and NULL marker. `AuditTrailRound2ReviewTests` and `SchemaMigrationTests.auditCutoverPreservesLegacyRowsAndDatesOldImageInsertsWithTheDatabaseClock`.
- **Migration locks:** V103 metadata and genesis selection commit before V104 validation and V106 index construction.
  V106 has the unique chain position index and one small partial pending index on `(stored_at,id)`. Plain builds hold
  SHARE (inserts wait); they no longer inherit V103's ACCESS EXCLUSIVE lock. The old timing table is historical, not a
  measurement of V106. The runbook states this limit and explains prerequisites for concurrent pre-creation.
- **Bounded operator verification:** the new, previously unreleased endpoint is now POST; each call checks at most
  50,000 positions and stores a checkpoint, rate-limited to 30 calls/hour/account. `complete` is separate from `intact`.
  Restart explicitly rechecks historical rows; changes behind an old checkpoint require that restart. Checkpoint row
  locking serializes runs with restart without locking audit inserts or the linker. OpenAPI and generated TS match,
  including restart's 204. `anAdminRunsBoundedIncrementalVerificationsThatContinueFromTheCheckpoint`.
- **Stalled-linker signal:** a run that only skips a locked head does not refresh `last.link.success`. A fixed backlog
  cannot mask a stuck competing linker. Regression holds the real head lock and checks the unchanged success gauge;
  releasing it and draining updates the gauge. `skippingAHeadLockedByAnotherLinkerDoesNotRefreshItsSuccessGauge`.
- **Legacy request bound:** one budget counts every search attempt (also the failed engine-switch call) across the
  restart. Beyond depth 2,400, one first-page probe returns an empty array only when an exact total or exhausted page
  proves the offset is beyond the end; otherwise 400 with the cursor successor. Reviewer tests from `8535980`, already
  cherry-picked as `52617df`, remain unchanged. Existing bound test now expects this one documented probe.
- **Configuration:** the two audit scheduler settings and shared scheduler pool size are documented in both example
  env files and passed through Compose. No secrets, dependencies or framework changes were introduced.
- **Analytics wording:** the two-group abandonment comparison describes association; the metric description no longer
  attributes the difference causally to KYC. Existing server event `user_id` is retained as the owner's earlier choice.

**Bounded v1 limitation:** after a very late engine switch or unusually short rechecked pages, the remaining shared
50-search budget can be exhausted before the requested window is reached. The legacy response can then be empty or
short; it never combines items from different engines. Use the v2 cursor endpoint for complete deep traversal.

**Checks recorded while resuming:**

| Check | Result |
|---|---|
| Focused Maven `spotless:apply test -Dtest=AuditTrailRound2ReviewTests,LegacySearchV1Round2ReviewTests,SlugClaimRound2ReviewTests,W6ReviewFollowUpTests,AuditTrailReviewTests,AuditTrailConcurrencyTests,LegacySearchV1BoundsTests,OpenApiSnapshotTests -Dopenapi.snapshot.update=true` | Initial resume: 27 tests, 8 classes, 0 failures/errors/skips, 30 s; later follow-up tests are included in the final verify below. |
| `npm run gen:api`, generated-file Prettier, `npm run lint`, `npm run build`, `npm run check:api`, `npm run check:bundle` | Passed; lint 0 errors/warnings, TS + Vite build and all bundle budgets passed. |
| `npx vitest run app/routes/_account.billing.test.tsx --testTimeout=30000` | 1 file / 1 test passed. |
| `MEDIA_SIGNING_SECRET=<dummy test value> docker compose --env-file .env.example -f docker-compose.yml config --quiet` | Passed using examples only. |

Full backend verification and post-integration results are recorded below after they finish. No local full stack,
production smoke test, large dataset/load test or new large-table timing was run. The orchestrator updates the root
requirement matrix and histories after integration.

**Additional root review fixes while resuming:** public v1 detail/profile list/count and v2 database/detail/cache
validation now exclude `ACTIVE` listings whose deadline has passed, before the sweep changes their state. Public
media applies the same live cutoff; authenticated owner/staff views remain available. The v2 predicate anti-joins
active expired ids via the existing `idx_listings_active_expires`, avoiding a read-model column/backfill. The access
matrix now includes a real `ACTIVE/PAST_EXPIRY` row across every actor and listing/media/search/seller endpoint.
Receipt Idempotency-Key payload binding uses a JSON record, preventing `reference="A|B", note="C"` from colliding
with `reference="A", note="B|C"`; the second payload returns 409, the exact retry still replays once.


**Elasticsearch expiry / schema transition:** mapping version 2 stores each listing deadline from one batched live
listing lookup per indexing batch (NULL is an explicit infinity value); the same `expires_at > now` filter runs on
search hits and direct map aggregations. It therefore excludes an elapsed ACTIVE document without waiting for the
expiry sweep or a new indexing event. Existing strict version-1 aliases keep serving through the database while an
automatic asynchronous schema rebuild runs. Dual-write removes the new field only on old targets; rollback to an
old mapping returns 409 `MAPPING_VERSION_MISMATCH`, while same-version rollback remains available. No read-model
column/backfill or public DTO change was needed. Outage map aggregates remain approximate for the cache's nominal
60-second TTL (±20% jitter); SQL computes fresh visibility when that aggregate is refreshed.

`SearchElasticsearchEngineTests` now proves elapsed ACTIVE documents disappear from both search and map counts,
NULL expiry stays visible, an old alias is not marked ready, repeated bootstrap reuses its build, old strict dual-write
succeeds, and the completed schema swap cannot roll back to an incompatible index.

Fresh focused Maven `spotless:apply test -Dtest=SearchElasticsearchEngineTests,SchemaMigrationTests,BillingReviewIdempotencyReviewTests,MediaPipelineIntegrationTests,W6ReviewFollowUpTests`:
**33 tests / 5 classes, 0 failures/errors/skips, BUILD SUCCESS, 40.522 s.** This includes the legacy migration upgrade,
receipt delimiter-collision regression and media pipeline. Earlier full runs failed: a mistaken test-only Flyway target
was corrected to the real pre-W6 version 95; one later media job had a PostgreSQL I/O/closed-connection failure during
the run and passed this fresh focused rerun. The full integrated verify is still pending below.


Integrated full verification at `f986b40` (expiry commit `801f897` plus `origin/main` operations):
`JAVA_HOME=~/.local/opt/jdk17 MAVEN_OPTS=-Xmx1g bash mvnw -B verify`, shared disposable test infrastructure:
**BUILD SUCCESS, 519 tests, 0 failures/errors/skips, 3:12.** Includes every backend test, architecture checks,
OpenAPI snapshot, package and Spotless check. No production access. A final safety guard now rejects activation of
an incompatible mapping before changing the alias, covering a retired old-schema job that was already running;
its focused validation will be recorded with the subsequent performance warmup integration.

Safety-guard focused validation: `bash mvnw -B spotless:apply test -Dtest=SearchElasticsearchEngineTests`:
**BUILD SUCCESS, 11 tests, 0 failures/errors/skips, 23.016 s.** Also proves the live finite expiry is copied exactly
and a NULL expiry remains visible. Compose config with both OPS and audit env settings passed using example env
and a dummy signing value.


**Final integrated follow-up — lead expiry and replay:** `LeadApplicationService` now reads the live deadline under
the existing FOR SHARE listing lock. New submissions reject elapsed ACTIVE listings with
`LISTING_NOT_ACCEPTING_LEADS`; eligibility uses the same rule. The actor-scoped committed replay resolves before
current eligibility, so an exact retry still returns its original lead after expiry, including after the sweep sets
EXPIRED. Failed new submissions leave no lead or idempotency row and consume no daily quota. The regression checks
valid KYC actors, fresh acceptance, both elapsed states, one CREATED event and one quota-counted lead.
Legacy pipe-joined request hashes remain valid across deployment, while replay also compares every canonical stored
lead field (actor, listing, name, phone blind index, request type, note, consent). A crafted fullName/note collision with
the exact same legacy hash returns 409 for changed input; the old exact payload still replays. No migration needed.

Integrated final perf `841971e` (bounded full-response HTTP warmup, exact bbox and cache data timestamp) via `c8ecede`.
The map merge retains bounded cluster cell selection, the requested bbox and cached snapshot `dataAsOf`; cache key
version 2 avoids prior-shaped cached values. SQL catalog `seller.profile.top` / `detail.gone.slug` now copies the live
expiry guards; `query-plans.py` correctly extracts the concatenated OWNER_ACTIVE anti-join: 3 Python tests passed,
38 catalog shapes. The previous nominal cache TTL caveat applies to aggregate counts/bounds; snapshot time is exposed.

Final integrated focused Maven command:
`bash mvnw -B spotless:apply test -Dtest=LeadSubmissionConcurrencyTests,LeadInboxAndCommandTests,SearchWarmupTests,LoopbackHttpWarmupTests,SearchElasticsearchEngineTests,MapEngineSelectionTests,MapClusterLimitTests,LocalPageCacheTests`
**BUILD SUCCESS, 45 tests / 8 classes, 0 failures/errors/skips.** Includes the new lead expiry/legacy-key collision
regressions, bounded loopback HTTP body timeout, map cache bounds/time, current ES expiry/schema and cell completeness.
Full CI and the remote final EXPLAIN/load evidence must be evaluated on the pushed integrated head before merge.


**Final owner-state consistency fix:** new lead submission locks the owner and listing rows together (`users u JOIN
listings l ... FOR SHARE OF u,l`), captures `owner_status`, and requires both ACTIVE plus a fresh/NULL deadline.
Eligibility uses the same visibility rule; KYC eligibility also requires an ACTIVE account. A completed actor-scoped
exact replay still resolves before current policy and returns its historical lead when the owner is suspended.
`AdminUserService.changeStatus` takes the user FOR UPDATE; its status trigger only enqueues an owner-index job and
never takes a listing lock. The lead's shared owner lock therefore makes a later suspension wait until commit.
The actual PostgreSQL regression holds that accepted lead transaction, proves a concurrent owner suspension cannot
acquire the lock, then proves suspension succeeds after release and the original lead stays valid. Separate fixtures
keep the listing ACTIVE and owner KYC VERIFIED while setting SUSPENDED and PENDING_EMAIL_VERIFICATION: new requests
return 409 `LISTING_NOT_ACCEPTING_LEADS`, leave no new key/quota-counted lead, and eligibility is false. Historical
exact retries remain 201 with the original lead id and one CREATED event.

`bash mvnw -B spotless:apply test -Dtest=LeadSubmissionConcurrencyTests,LeadInboxAndCommandTests,KycAbandonmentFunnelTests`:
**BUILD SUCCESS, 18 tests / 3 classes, 0 failures/errors/skips.** This is a focused follow-up; final integrated CI is
still required. No production commands/data or local load stacks.
