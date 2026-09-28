# Stream S3b-LEADS — leads, viewing appointments, broker workspace, qualified-lead/ROI (wave W3)

Branch `audit/s3b-leads` (from `audit-2026-09-27` @ `1dba873`). Flyway V050–V052 (V053–V054 unused), backend port 18116,
Vite 5316 (as assigned for this run), Redis DB claimed per JVM by the test support. Brief: `briefs/s3b-leads.md`.

## 1. How to verify

```sh
eval "$(scripts/test-infra.sh env)"; export JAVA_HOME=$HOME/.local/opt/jdk17 PATH=$HOME/.local/opt/jdk17/bin:$PATH MAVEN_OPTS=-Xmx1g
cd backend && sh mvnw -B -ntp verify
sh mvnw -B -ntp -Dtest='LeadSubmissionConcurrencyTests,LeadInboxAndCommandTests,AppointmentTests,BrokerWorkspaceTests' test
cd ../frontend && npm run lint && npx tsc -b && npx vitest run --testTimeout=30000 && npm run build && npm run check:bundle
```

Results on the final commit: see §7.

## 2. Requirement → evidence

| ID | Delivered | Evidence | Status |
|---|---|---|---|
| F08.3 | Owner inbox is one statement `leads ⋈ listings ON owner_id` (+ leads assigned to the actor as an active team member), listing title/slug/address/thumbnail, assignee, SLA due time and open appointment joined in the page query; status counts in a second statement. `GET /leads`, `/leads/search`, `/leads/inbox` all use it; the owner's listings are never loaded. | `LeadInboxAndCommandTests.ownerWithTenThousandListingsOpensATwentyLeadPageInTwoStatements` (10,000 listings + 25 leads → `QueryCount.assertAtMost(2)`, 20 items, page 2 = remaining 5, no overlap) | DONE |
| F08.6 (S3b queries) | Stable order everywhere (`created_at DESC, id DESC`; tasks by due time + id; report by count + id; `/leads/listings` gained `s.id DESC`); sizes capped (inbox/inquiries 50, history 200, appointments 20, team 50, tasks 10, report rows 20); LIKE input escaped | same test (size 500 → 50), `serverFiltersNarrowTheInbox…` (`%` matches nothing) | DONE |
| F17.1 | Quota 10 leads / 24 h per phone blind index **and** per requester account, counted and inserted under `pg_advisory_xact_lock` on both keys (sorted order) | `LeadSubmissionConcurrencyTests.twentyParallelDistinctRequestsNeverExceedTheDailyQuota` (20 parallel → exactly 10 × 201, 10 × 429 `LEAD_QUOTA_EXCEEDED`; the phone quota also blocks another account) | DONE |
| F17.2 | Key scope `lead:<actorId>` + SHA-256 of the canonical payload; `INSERT … ON CONFLICT` (duplicates wait on the key row); `expires_at` = +24 h, expired keys reusable; purge task (locked, lead scopes only) | `twentyParallelRequestsWithOneKeyCreateOneLeadAndEveryRetryGetsItsId` (20 parallel → 1 lead, 19 replays, retry gets the id, other payload → 409 `IDEMPOTENCY_KEY_REUSED`), `anotherActorWithTheSameKeyNeverReceivesTheReplay`, `expiredKeysCanBeReusedAndArePurged` (billing keys untouched) | DONE |
| F17.3 | Listing row `FOR SHARE` + ACTIVE/public-revision check inside the lock (policy §3) | `pauseCommittedFirstRefusesTheLeadAndAPauseDuringTheInsertWaitsForIt` (pause first → 409 `LISTING_NOT_ACCEPTING_LEADS`; pause during an open lead transaction hits `lock_timeout`, succeeds after commit, lead stays NEW) | DONE |
| F17.4 (S3b part) | Web events `lead_form_opened` (modal open), `kyc_required_shown{context:lead_form}` (KYC gate on the detail page and `KYC_REQUIRED` from the API); server `lead_submitted` (existing), `lead_first_response`, `lead_qualified`, `appointment_*`; staff `GET /api/v1/analytics/lead-funnel?days=` (internal/bot excluded, qualified share as guardrail, null when not measurable) | `BrokerWorkspaceTests.kycFunnelCountsRecordedEventsAndExcludesInternalTraffic`; event rows asserted in `LeadInboxAndCommandTests.transitions…`, `AppointmentTests.ownerProposes…` | PARTIAL — dashboards/cohorts are S8 |
| UI-08 | `/broker/workspace`: measured SLA (median, p90, % within target, open breaches, over 30 days; null = "Chưa có dữ liệu"), today's tasks (respond by due time, appointments today, proposals awaiting me, outcomes to record; one "Mở" action each → lead sheet), team (add by e-mail, remove → open leads return to owner), intake history (last 20 events), SLA settings, ROI tab | `BrokerWorkspaceTests.slaIsMeasuredFromRecordedFirstResponsesOnly`, `todaysTasksListAppointmentsAndProposalsAndTheIntakeHistory`; `LeadInboxAndCommandTests.teamMembersHandleOnlyAssignedLeadsAndLoseAccessWhenRemoved` | DONE |
| UI-09 | `/my-leads`: one inbox over all listings, reads `?listingId=` (S3a link) and `?lead=` (workspace link), server filters (status, request type, qualification, overdue, keyword), status counts, lead sheet (status + note, qualification + reason, assignment, reveal phone, appointments, history); every write sends `expectedVersion`, 409 reloads and says so | `LeadInboxAndCommandTests.serverFilters…`, `concurrentStatusUpdatesWithOneVersionLetExactlyOneWin` (2 parallel → 200 + 409, one history row), `transitionsAreChecked…`; Vitest `labels.test.ts` (conflict detection, transitions) | DONE |
| UI-10 | `/my-inquiries` (`/api/v1/me/inquiries`): real state (first response time, "chưa có phản hồi"), listing availability, appointment card (choose a slot, counter-propose, cancel), withdraw with confirmation (compare-and-set; open appointments cancelled; owner notified; phone no longer revealable), requester history without internal entries | `LeadInboxAndCommandTests.requesterSeesRealStateWithdrawsAndNeverSeesInternalData`; Vitest `AppointmentPanel.test.tsx` | DONE |
| P-03 | Appointments: 1–3 slots (15–180 min, ≥ 30 min ahead, ≤ 60 days, no overlap), confirmation by the other side, reschedule/counter-proposal (`replacesVersion`), cancel with reason, outcome COMPLETED / NO_SHOW (+ party) by the owner side after the start, one open appointment per lead (unique index), owner overlap guard | `AppointmentTests` (7 tests: slot rules, two-party confirm, 5 parallel confirms → 1 effect, overlap refusal, reschedule/cancel/outcome, contact guard) | DONE |
| D-12 (S3b part) | Reminder jobs `appointment-reminder` at start −24 h / −2 h, dedupe key per confirmed version and kind (`enqueueOnce`), handler re-checks status/version/future and writes `appointment_reminders_sent` in the same transaction → at most once per kind; in-app + e-mail via `MailOutbox.tryEnqueue` (verified e-mails only); SLA overdue reminder job `lead-sla-reminder` at the due time | `AppointmentTests.remindersAreSentOncePerKindAndStaleJobsAreNoOps`, `ownerProposesRequesterConfirmsAndRemindersAreQueued` (job rows + run_at); `LeadSubmissionConcurrencyTests.aNewLeadQueuesOneSlaReminder…` | DONE |
| P-08 (S3b part) | Qualification QUALIFIED/UNQUALIFIED with reason codes + note (owner side only, internal); report `GET /api/v1/leads/report` (every poster, OWNER included) and the workspace tab: leads, viewing requests, measured responses, median, % within target, qualified/unqualified/unassessed, appointment rate, completed/no-show, spend from APPROVED package orders in the period, cost per lead / per qualified lead only when spend exists; per-listing rows | `BrokerWorkspaceTests.qualifiedLeadReportComputesRoiOnlyFromRecordedSpend` (null without spend, 499,000 / 2 = 249,500; period validation) | DONE |
| R-4 (lead part) | Scoped idempotency, atomic quota, CAS on status/qualification/assignment/withdraw, appointment confirm serialised on the lead row + version | tests above (all on PostgreSQL) | DONE |
| DS-05 (lead pages) | Card lists instead of wide tables, 1–2 primary actions per card, Sheet for details (mobile bottom sheet), filters stack on mobile | manual review; build | PARTIAL — visual/E2E pass is S11 |
| DS-11 (contact) | Intent kept across sign-in (`?contact=1` set before the login modal) and KYC (`/kyc?returnTo=…?contact=1`, same-site paths only); the form reopens automatically once the visitor can send | code (`_public.listings.$listingId.tsx`, `_account.kyc.tsx`); no E2E | PARTIAL — E2E journey is S11 |

## 3. Policies

- **Quota:** 10 leads per phone and per account per rolling 24 h; replays never count.
- **Idempotency:** 24 h retention; same key + other payload → 409; other actor → own lead; response is 201 with
  `replayed` / `Idempotent-Replayed` telling a replay apart.
- **Pause vs lead:** a pause committed first wins (lead refused); a pause during an in-flight lead waits and the lead is
  kept. Leads of paused listings stay answerable and withdrawable.
- **Transitions (owner side):** NEW → CONTACTED/APPOINTED/CLOSED/SPAM; CONTACTED → APPOINTED/CLOSED/SPAM; APPOINTED →
  CONTACTED/CLOSED/SPAM; CLOSED/SPAM → CONTACTED; WITHDRAWN terminal (requester only); nothing returns to NEW.
  Confirming an appointment moves NEW/CONTACTED to APPOINTED (SYSTEM history entry).
- **First response:** first owner-side status change out of NEW or first owner-side appointment proposal/confirmation;
  never overwritten; legacy NULL = not measured.
- **SLA:** target from `broker_sla_settings` (default 30 min); breach = still NEW after the target. Reminder queued at
  creation with the target in force then.
- **Appointments:** see P-03 row; the owner side records outcomes; a reminder is never sent for a stale version,
  a cancelled/rescheduled appointment or a start in the past; confirmations less than 2 h ahead get no reminder.
- **Team:** BROKER accounts only, ≤ 20 active members, not self; members see only leads assigned to them; only the
  listing owner assigns; removal returns open leads (history entry `ASSIGNED` with reason). The "add member" error is
  identical for unknown e-mail and non-broker (no account enumeration); member e-mails are shown masked.
- **Privacy:** requester views never contain qualification, assignee, owner-side status notes or actor names; owner-side
  appointment notes, cancel reasons and outcome notes (visible to the requester) are refused when they contain a phone,
  e-mail or messenger link (`ContactInfoGuard`); phone reveal is refused after withdrawal; notifications and reminder
  e-mails never contain the other party's contact details.

## 4. Contract deviations

1. Ports 18116/5316 (orchestrator assignment for this run) instead of 18123/5323 in `02_CONTRACTS.md` §1.
2. Replays of `POST /public/leads` answer **201** (not 200) so existing clients keep treating them as success; the body
   field `replayed` and header `Idempotent-Replayed: true` identify them.
3. `PATCH /leads/{id}/status` still accepts requests without `expectedVersion` (legacy clients/tests); it then
   compare-and-sets against the version read in the same request. Every frontend caller now sends it. All new write
   endpoints (qualification, assignee, withdraw, appointments) require it (428 `EXPECTED_VERSION_REQUIRED`).
4. The 409 `LEAD_VERSION_CONFLICT` body is standard Problem Details (no embedded lead); the UI reloads the lead.
5. `GET /leads`, `/leads/search`, `/leads/sent` return the new item shapes — a superset of the old fields, so the admin
   oversight page and existing clients keep working. A poster can no longer send a lead to their own listing
   (`SELF_LEAD`); `BdsApplicationTests.leadLifecycleAndCrm_flow` now sends its lead as a separate verified buyer.
6. KYC check for leads now also requires `expires_at` null or future (contract §6 identity definition).
7. Flyway: V053–V054 unused.

## 5. Known gaps (honest)

- **E2E:** no Playwright spec written or run for these pages (machine load; journey E2E is S11). Frontend behaviour is
  covered by Vitest for the slot rules, labels and the appointment panel only.
- **Daily digest** (`daily_digest_enabled`) is stored but no digest is sent (pre-existing flag; needs S6 notification
  preferences/e-mail templates).
- SLA reminders are in-app only (no e-mail) and are not rescheduled when the owner changes the target later.
- Appointment reminders use the S0 queue as is; if the worker is down past the start time the reminder is skipped.
- The legacy `/leads/listings` grouping query still uses a correlated sub-select for the latest revision (bounded by
  page size; not on the hot path of the new inbox).
- Admin lead oversight page (`/admin/leads-and-reports`) only got version-aware status changes; no new staff features.
- Bundle budgets raised (see `frontend/bundle-budget.json` notes): /my-leads 138, /my-inquiries 138, /broker/workspace
  138 (new features, heavy parts lazy-loaded); /search 145 and /nguoi-dang 136 for +0.4/+0.1 kB of shared UI-kit chunk
  regrouping caused by the new lazy boundaries (no search/seller code changed).
- The KYC page shows a "quay lại tin" link but KYC approval is manual, so the return happens after approval, not
  immediately.

## 6. Production / deploy notes

- **Migrations V050–V052** are additive (new columns nullable, new tables, one backfill `INSERT … SELECT` of a `CREATED`
  history row per existing lead, `lock_timeout 5s`). Flyway ordering: they are numbered below V055–V061 (S4). Production
  is at V026 and receives all audit migrations together, so the order is fine; any environment that already applied
  V055+ from an earlier integration build needs `spring.flyway.out-of-order=true` once (or a fresh DB).
- New job queues: `appointment-reminder`, `lead-sla-reminder` (need `APP_JOBS_ENABLED=true` on one instance). Alerts:
  `bds_jobs_dead{queue="appointment-reminder"} > 0`.
- New scheduled task `idempotency-key-purge` (hourly, `app.leads.idempotency-purge-cron`, locked).
- New private prefixes (`/api/v1/me/inquiries/**`, `/api/v1/appointments/**`) are `no-store` and authenticated.
- UI copy states the platform connects parties and never takes deposits; no invented statistics (unmeasured = "Chưa có
  dữ liệu").

## 7. Test results and follow-ups

- Backend full `mvnw verify` on the final code: **296 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**.
- New backend tests: `LeadSubmissionConcurrencyTests` (7), `LeadInboxAndCommandTests` (6), `AppointmentTests` (7),
  `BrokerWorkspaceTests` (4) = 24, all on PostgreSQL.
- Frontend: lint 0, `tsc -b` 0, Vitest 21 files / 167 tests (new: `labels.test.ts` 6, `AppointmentPanel.test.tsx` 3),
  build OK, `check:bundle` 0 over budget.

Follow-ups: **S6** — daily digest and e-mail for SLA reminders through notification preferences; SSE link field for
lead/appointment notifications (`link` = `/my-leads?lead=<id>` / `/my-inquiries`). **S8** — funnel/cohort dashboards on
`lead_form_opened → kyc_required_shown → lead_submitted` and `lead_qualified`, `appointment_*`. **S11** — E2E journeys:
buyer sends lead → owner proposes → buyer confirms → owner records outcome; withdraw; conflict message; visual pass of
the three pages. **S10** — EXPLAIN of the owner inbox at 1M leads (indexes `idx_leads_listing_created`,
`idx_leads_assignee_created`, `idx_listings_owner_status_created`).
