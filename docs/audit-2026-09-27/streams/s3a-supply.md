# Stream S3a-SUPPLY — listing write path, wizard, OWNER, my-listings, freshness, import, quality (wave W2)

Branch `audit/s3a-supply` (from `audit-2026-09-27` @ `b875b91`). Flyway V045–V048 used (V049 free), port 18114,
Vite 5314, Redis DB 5, E2E DB prefix `s3a_e2e`.

## 1. How to verify

```sh
eval "$(scripts/test-infra.sh env)"; cd backend && sh mvnw -B -ntp verify          # JDK 17
cd frontend && npm run lint && npm run typecheck && npm run format:check && npm run test:unit && npm run build && npm run check:bundle
E2E_BACKEND_PORT=18114 E2E_FRONTEND_PORT=5314 E2E_REDIS_DB=5 E2E_DB_PREFIX=s3a_e2e \
E2E_SQL_AFTER_SEED="<VERIFIED KYC for demo.broker, see §6>" scripts/e2e-local.sh --suites "supply authenticated" --projects chromium-1440
```

Results on the final commit: backend **172 tests, 0 failures, BUILD SUCCESS** (18 new in `com.company.bds.listing`);
frontend lint/typecheck/format clean, Vitest **16 files / 145 tests**, build OK, bundle check OK
(`/listings/new` **132.3 kB** gzip initial JS, was 604 kB; budget lowered to 140 kB); Playwright chromium-1440:
`supply` 2/2, `authenticated` 2/2. Mailpit was started with `docker compose -f infra/test/compose.yaml up -d --wait mailpit`
(needed by `e2e-local.sh`); it was left running (shared service, not stopped).

## 2. Scope → evidence

| ID | Delivered | Evidence | Status |
|---|---|---|---|
| F04.1 (write side) | Create/update draft accept `monthlyServiceFeeVnd`, `depositVnd` (RENT only, SALE → field error), `furnishing`, `legalStatusCode` (+`legalStatus` detail required for OTHER), `projectId` (must exist); mapped domain ↔ entity ↔ mapper; preview/draft return `price{amount,currency,period}`, `unitPrice`, `rentTerms`, `legal{code,label,detail}` | `ListingWritePathTests.rentDraftStoresRentTerms…`, `invalidValuesAreFieldErrorsNot500` | DONE |
| F07.2 (owner/editor) | `GET /api/v2/me/listings/{id}/draft` (latest revision + version/ETag), `/preview` (public-detail field names, redacted like the public page, `?version=public`), owner or STAFF only (others 404), `no-store`. An edit of a published listing keeps the public version live (`ACTIVE`) while the edit is reviewed; moderation queue includes listings with a SUBMITTED revision | `ListingWritePathTests.editOfAPublishedListingKeeps…` | DONE |
| F07.3 | Swallowed `LazyInitializationException` removed; media loaded with `@BatchSize(64)` inside the adapter transaction; my-listings uses a dedicated JDBC projection | mapper diff; `OwnerListingsTests` constant statement count | DONE |
| F08.1 / F08.6 (own queries) | `GET /api/v2/me/listings?status=&page=&size≤50`, sort `created_at DESC, id DESC`, counts per status, public version vs pending edit (+rejection reason), freshness, quality; 3 statements per page regardless of size; new index `idx_listings_owner_created` | `OwnerListingsTests.pagesAreStableBoundedAndCounted…` (21 rows over 3 pages = exact expected order incl. ties; `count(size=2) == count(size=20)`; 400 for size 500/unknown status; USER 403) | DONE |
| R-4 (revision edits) | Draft update locks the listing row (`SELECT … FOR UPDATE`) and checks `If-Match: "v<n>"` / `expectedVersion` → `409 VERSION_CONFLICT` with the current `ETag`; responses carry `version` + ETag; `ObjectOptimisticLockingFailureException` → 409 | `staleVersionGets409AndTheCurrentEtag`; `concurrentSavesOfTheSameVersionOneWinsTheOtherGets409` (4 concurrent writers on PostgreSQL: exactly one 200, three 409, version 1, one revision) | DONE |
| P-09 / §3 OWNER | `POST /api/v1/me/become-owner {confirmOwnProperty:true}`: USER → OWNER (row lock, one `user_roles` row), audited in `user_role_changes` (V046) + the request audit trail; idempotent for OWNER, 409 for BROKER/staff; `roleLabel` in `/auth/me`. UI `/become-owner` (explanation + explicit checkbox), card on `/my-inquiries`, header "Đăng tin" for USER points there | `OwnerListingsTests.userBecomesOwnerAfterExplicitConfirmation…`; E2E `authenticated` buyer test | DONE |
| P-14 / P-05 (freshness) | See §3 policies. `confirm-availability`, `renew`; locked sweep (`listing-freshness-sweep`, 10 min) expires ACTIVE → EXPIRED and pauses unanswered sold checks via plain `UPDATE listings` (fires S2 triggers, bumps `version`); reminders D7/D2 through queue `listing-expiry-reminder` (`enqueueOnce`, key `listing:cycle:kind`; handler re-checks state; `listing_expiry_reminders` PK prevents duplicates) → notification + e-mail (`MailOutbox.tryEnqueue`); approval starts validity | `ListingFreshnessTests` (5: 45-day period, reminders once per cycle with the worker + e-mail job + expiry, re-confirmation invalidates old reminders, renewal rules, sold report → 48 h → pause unless confirmed) | DONE |
| P-14 (sold out) | Public `FAKE_SOLD` report (hook in `ViolationReportApplicationService.submitReport`) sets `sold_check_due_at = now+48h` once, notifies + e-mails the owner, queues `listing-sold-check` at the deadline; confirmation clears it, hide clears it | `soldReportAsksTheOwnerAndPausesAfter48HoursUnlessConfirmed` | DONE |
| P-08 (import) | `GET …/import/template`, `POST /api/v2/me/listings/import?dryRun=` (multipart, ≤1 MB, ≤200 rows, RFC 4180 incl. `;`, UTF-8 only): per-row errors (types, ranges, enums, contact guard, legal detail, rent terms), quota warnings; commit only when every row is valid, one transaction, `source='IMPORT'`, `import_batch_id` (V047), idempotent per owner + SHA-256 (repeat → same batch, 200 `duplicate`); commit with errors → 422 with the report | `ListingImportTests` (3) | DONE |
| P-08 (quality) | Checklist on draft/preview/my-listings: ≥5 images, description ≥200, district + map point, legal code, rent deposit (RENT), price/m² within ⅓–3× district median of ≥10 published comparables (same purpose/type), else `NO_DATA` "Chưa đủ dữ liệu"; guidance only | `qualityChecklistComparesPricePerM2OnlyWithEnoughComparables` | DONE |
| UI-06 / DS-12 | `/listings/new`: 4 steps Cơ bản → Vị trí → Ảnh → Xem trước; debounced autosave (Đang lưu… / Đã lưu lúc HH:mm / offline auto-retry on `online` + Thử lại / error + Thử lại), id in URL after first save (reload keeps the draft), 409 dialog (Tải bản mới nhất / Giữ nội dung đang sửa), FormField errors next to fields (client + server `errors[]`), numeric/decimal input modes, role banner, rejection reason + guidance, server preview + checklist, submit; MapLibre only via `lazy()` on the location step | E2E `supply` test 1; `api.test.ts` (3) | DONE |
| UI-07 | `/my-listings`: server paging (`Pagination`), status tabs with counts (URL `?status=&page=`), "Bản đang hiển thị" vs "Bản sửa chờ duyệt/bị từ chối" + reason, actions xác nhận còn hàng / gia hạn / ẩn–hiện / sửa / gửi duyệt / xem khách quan tâm, expiry + sold-check warnings, CSV import dialog (check → create), OWNER onboarding empty state | E2E `supply` test 2 | DONE |
| `listing_published` | Recorded by S0-BE in `ModerationApplicationService.approve` (key `listingId:revisionNumber`); verified, not duplicated | code reading | n/a |

## 3. Policies (P-14)

- **Validity:** 45 days from the last confirmation. Approval of a revision, "Xác nhận còn hàng" and renewal each start a new period (`availability_confirmed_at`, `expires_at`). V045 gives existing ACTIVE listings `max(confirmed + 45 d, deploy + 14 d)` so nobody expires without both reminders.
- **Reminders:** 7 and 2 days before `expires_at` (in-app notification + e-mail). A listing already inside the 2-day window gets only the 2-day reminder.
- **Expiry:** ACTIVE → EXPIRED when `expires_at` passes (sweep every 10 min, cluster-exclusive via `ScheduledTaskLock`).
- **Renewal:** an EXPIRED listing whose content is unchanged since the public revision (no newer revision, or only an identical draft) can be renewed up to 30 days after expiry → ACTIVE without moderation. Otherwise `409 RENEWAL_REQUIRES_REVIEW`: edit + submit (moderation) — approval reactivates it.
- **Sold-out report:** a public `FAKE_SOLD` report opens a 48-hour check (one at a time). The owner confirms availability (clears it) or hides the listing; unanswered → PAUSED + notification. The owner can show it again. Admin handling of the report itself stays with S4.

## 4. Contract deviations

1. my-listings is **page-based** (`page`, `size`, `total`, `totalPages`, `counts`) rather than cursor: owners need counts and page numbers; stable sort documented.
2. Draft create/update stay on **v1** paths (`POST /api/v1/listings`, `PUT /api/v1/listings/{id}/draft`), extended backward-compatibly (new optional fields, `If-Match`, `version` in the body). New read/owner endpoints are `/api/v2/me/listings/**`.
3. Preview uses the §8 field names but images are URL-only (`srcset: []`), drafts' private media are not signed yet (S1).
4. `submit` of an edit of an ACTIVE/PAUSED listing no longer sets the listing to PENDING_REVIEW (the public version stays visible); the moderation queue query (`findPendingReviewListings`) now also returns listings with a SUBMITTED revision. Rejecting an edit of an expired listing leaves it EXPIRED.
5. `GlobalExceptionHandler`: `ListingDomainException` `LISTING_NOT_FOUND` → 404 and `FORBIDDEN` → 403 (were 409); new handlers for field validation, version conflict and unreadable JSON (400 instead of 500). S9 may fold these into its Problem Details pass.
6. `SchemaMigrationTests` now migrates to target 29 explicitly (it asserted "latest = V029", which any later migration breaks); its checks are unchanged.
7. `scripts/e2e-local.sh`: optional `E2E_SQL_AFTER_SEED` (fixture SQL after seeding). Needed because `demo.broker` has no VERIFIED KYC in the seed; the supply E2E inserts one for that account in its own E2E database.

## 5. Known gaps (honest)

- Only chromium-1440 was run for the new E2E (Firefox/WebKit/mobile only in CI). Visual baselines untouched.
- Reminder/expiry e-mails have plain-text bodies without links (no configured public base URL is used on purpose).
- `/my-leads?listingId=` link: the leads page (S3b) does not read the filter yet.
- Import creates drafts without images/coordinates; each draft must be completed in the wizard before submit. No per-owner rate limit on import beyond size/row caps (not public; `api-default` policy applies).
- Quality benchmark uses published listings of the same district code/purpose/type from `listings`/`listing_revisions` (not S2's read model); median band ⅓–3× is a heuristic, shown only as guidance.
- The wizard keeps the existing client-side eKYC gate (the server enforces `app.listing.require-verified-kyc`).
- Province/district/ward are still free-text codes (no administrative picker in this stream).

## 6. Deploy / production notes

- Migrations V045 (freshness columns, reminders table, 3 indexes, one guarded backfill of `expires_at`), V046 (`user_role_changes`), V047 (`listing_import_batches`, `listings.import_batch_id`). Additive; rollback = previous image. **Order:** V045–V047 are above S2's V033–V044; if this branch is deployed before S2, Flyway needs `outOfOrder=true` when S2's lower versions arrive (or merge S2 first).
- New job queues `listing-expiry-reminder`, `listing-sold-check` (require `APP_JOBS_ENABLED=true` on one instance). New property `app.listing.freshness.scheduler-enabled` (default true), `…sweep-ms` (600000), `…initial-delay-ms` (60000).
- New private prefixes `/api/v1/me/**`, `/api/v2/me/**` added to `SensitiveResponseCacheFilter`; security: `/api/v2/me/listings/**` POSTERS (draft/preview GET also STAFF), `/api/v1/me/become-owner` authenticated.
- E2E fixture SQL used: `INSERT INTO user_kyc_profiles(...) SELECT … 'VERIFIED' … WHERE email='demo.broker@bds.local'` (only in the throwaway E2E database).

## 7. Follow-ups for other streams

- **S2:** triggers on `listings` see expiry/pause/renewal (plain UPDATEs). `ListingDetailV2` can reuse `ListingViews` money/legal/rentTerms mapping. Freshness `availabilityConfirmedAt` is maintained here.
- **S4:** moderation queue now includes edits of live listings (SUBMITTED revision while listing is ACTIVE). Report history/UI for FAKE_SOLD checks (`listings.sold_check_due_at`).
- **S1:** signed URLs for private draft media in preview/wizard.
- **S3b:** read `?listingId=` on `/my-leads`.
- **S6:** reminders use `RealtimeNotificationService.notify` + `MailOutbox`; migrate to `NotificationRequest` with dedupe keys (`expiry:<id>:<cycle>:<kind>`, `sold-check:<id>:<due>`).

## 8. Review 2 fixes

Backend commit `b561d1b`, frontend commit `d829637` (see `git log`). Final: `mvnw verify` **178 tests, 0 failures**;
`ListingFreshnessTests` 5 consecutive runs 8/8; Vitest 17 files / 146 tests (run with `--testTimeout=30000`: the
pre-existing ESLint-plugin test timed out at 5 s under load average ~150, unrelated); build + bundle OK; E2E
`supply` 2/2, `authenticated` 2/2 (chromium-1440).

| Finding | Fix | Test |
|---|---|---|
| M1 flaky reminder test (app clock vs DB `now()`) | due reminders enqueued with `runAt = null` (DB clock) | `ListingFreshnessTests` ×5 green |
| m2 autosave self-conflict | `saveNow` waits in a loop for the running save; a waiter with nothing left reuses its result | `useDraftAutosave.test.ts` (3 overlapping saves, distinct versions) |
| m3 lost-update optional | wizard always sends If-Match (version known from create/load); v1 updates without a version are logged and counted `bds.listing.draft.unversioned_updates`. **Plan:** require If-Match on `PUT /listings/{id}/draft` once the old UI and other clients are gone (watch the counter reach 0) | code |
| m4 existence leak | ownership checked before the version; 404 for non-owners on PUT draft, draft, preview, confirm-availability, renew (no ETag) | `ListingWritePathTests.otherAccountsGet404WithoutLearningTheVersion` |
| m5 sold-report abuse | report saved first, sold check in the same transaction; after an answered check, a new one within 7 days needs ≥2 distinct reporter phones (`sold_check_cleared_at`, V048). Anonymous reports without a phone do not count toward the two | `afterAnAnsweredCheck…TwoDistinctReporters…`, `soldCheckOnlyExistsWhenTheReportIsSaved` |
| m6 reminder scan cap | keyset over `(expires_at, id)` in batches (500; settable) until the window is exhausted | `reminderScanReachesEveryListingInTheWindowAcrossBatches` (batch 3, 7 listings) |
| m7 import | dry run runs the KYC check and turns rows beyond quota into errors; a batch whose drafts were all deleted no longer blocks re-import; leading `= + @ - tab CR` removed from text cells (`- ` list dash kept); upload read with a 1 MB bound | `ListingImportEligibilityTests` (2, context with KYC + quota enforced) |
| m8 V045 | also backfills ACTIVE rows with NULL confirmation from `updated_at`. **Production procedure** for large tables: before deploying, run `CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_listings_active_expires …`, `idx_listings_sold_check …`, `idx_listings_owner_created …` (same definitions as V045) outside a transaction; V045's statements then do nothing | migration + note |
| m9 out-of-order my-listings | request sequence; only the latest response updates the page | code |
| NIT renewable flag | server renewal rule = list rule (no revision after the public one); `sameContentAs` removed | `expiredListingRenews…` |
| NIT become-owner lock | locks the `users` row (exists without a role row) | `userBecomesOwner…` |
| `listing_published` duplication | exactly one event per published revision, also after a repeated approve | `editOfAPublishedListing…` (`assertOnePublished`) |

## 9. Commits

See `git log audit-2026-09-27..audit/s3a-supply`.
