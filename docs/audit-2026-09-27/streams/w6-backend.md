# W6-BACKEND — audit trail race, R-2, R-3, R-4, F17.4

Branch `audit/w6-backend` (from `13e41a2`). Flyway: `V103__audit_chain_head.sql` (V104–V105 unused).

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
- A failed insert is retried twice.
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
- **v1 search.** It reaches every page; it used to return `[]` after page 0.
- **Rent unit.** Every rent price carries `MONTH` on the v2 cards, map points, v1 cards, v1 detail and v2 detail; every
  sale price carries none.

**Code changes:**
- The map kept only the 2,000 largest grid cells, so the clusters could add up to less than the list total. It now
  doubles the cell size until all cells fit (`MapClusterLimitTests`).
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
