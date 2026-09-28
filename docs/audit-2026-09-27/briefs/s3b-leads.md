# Brief S3b-LEADS (W3) — leads, viewing appointments + reminders, broker workspace, qualified-lead/ROI

Branch `audit/s3b-leads`; Flyway V050–V054; backend 18116; Vite 5316; Redis/ES prefix `s3b` (DB 14).
(The orchestrator assigned ports 18116/5316 for this run; contract §1 lists 18123/5323 — deviation noted in the report.)
Parallel in W3: S6-ENGAGE (owns notification internals; S3b calls `RealtimeNotificationService.notify(userId, type,
title, message)` only) and S1-MEDIA (images; S3b only shows the listing thumbnail URL it already had).

Read first: `00_PLAN.md`, `01_REQUIREMENTS.md`, `02_CONTRACTS.md` (§2.4 lead columns, §3 jobs, §5 events,
§11 notifications), `03_AGENT_RULES.md`, `briefs/wave-rules.md`, `streams/s0-be.md` §6 (follow-ups for S3),
`streams/s3a-supply.md` (sold check / pause, `/my-leads?listingId=`). Audit: F08.3, F08.6, F17, §4.1 Hẹn xem +
Seller/broker, §5 `/broker/workspace` `/my-leads` `/my-inquiries`, §7.4 reminders, §8.3 Broker workspace, R-4.

Requirement IDs: F08.3, F08.6 (own queries), F17.1, F17.2, F17.3, F17.4 (events + funnel read; dashboards = S8),
UI-08, UI-09, UI-10, P-03, P-08 (SLA, qualified lead, ROI), D-12 (reminder jobs), DS-05 (lead/workspace layouts),
DS-11 (return to the contact intent after KYC), R-4 (lead part).

## Policies to fix (documented in the stream report)
- **Quota:** at most 10 leads per phone (blind index) and 10 per requester account in a rolling 24 h window; the check and
  the insert run under transaction-scoped advisory locks on both keys (taken in a fixed order), so parallel requests
  cannot exceed it. Replays of an idempotent request never count against the quota.
- **Idempotency:** `Idempotency-Key` bound to scope `lead:<actorId>` (actor + route) and the SHA-256 of the canonical
  payload; 24 h retention (`expires_at`), expired keys may be reused, purge by a locked scheduled task. Same key + other
  payload → 409 `IDEMPOTENCY_KEY_REUSED`; another actor with the same key gets its own lead, never a replay. Concurrent
  duplicates wait on the key row and return the winner's `leadId` (`Idempotent-Replayed: true`).
- **Pause vs lead:** lead creation locks the listing row `FOR SHARE` and re-checks `ACTIVE` + public revision inside the
  lock. A pause/hide/lock committed first refuses the lead (409 `LISTING_NOT_ACCEPTING_LEADS`); a pause that arrives
  while a lead insert is in flight waits and the lead stays valid (the owner still answers it). Leads of a paused
  listing are kept; requesters may withdraw.
- **Lost update:** every owner-side write (status, qualification, assignment) carries `expectedVersion`; the update is a
  compare-and-set on `leads.version` → 409 `LEAD_VERSION_CONFLICT` with the current lead in the body.
- **Status transitions:** NEW → CONTACTED/APPOINTED/CLOSED/SPAM; CONTACTED → APPOINTED/CLOSED/SPAM; APPOINTED →
  CONTACTED/CLOSED/SPAM; CLOSED/SPAM → CONTACTED (reopen); WITHDRAWN terminal. `first_response_at` = first owner-side
  transition out of NEW (or the first appointment proposal), never overwritten. The requester withdraws from NEW/
  CONTACTED/APPOINTED; open appointments are cancelled.
- **SLA:** target = `broker_sla_settings.first_response_minutes` (default 30); a lead breaches when it is still NEW past
  `created_at + target`. Workspace metrics over 30 days: median + p90 first-response minutes, % answered within target,
  open breaches. Only measured leads count (NULL `first_response_at` of legacy rows = not measured, never 0).
- **Appointments:** one active (PROPOSED/CONFIRMED) appointment per lead (partial unique index). A proposal has 1–3
  slots of 15–180 min, starting ≥ 30 min from now and ≤ 60 days ahead. Proposer = one side; the other side confirms one
  slot (two-party confirmation). Reschedule = counter-proposal (old one `RESCHEDULED`). Cancel with reason by either side.
  After the start the owner side records `COMPLETED` or `NO_SHOW` (with who did not show). An owner cannot confirm two
  overlapping CONFIRMED appointments (advisory lock on the owner, overlap check). Confirm is compare-and-set on version.
- **Reminders:** durable jobs on queue `appointment-reminder`, dedupe key per appointment version and kind (24 h and 2 h
  before start, skipped when already past); the handler re-checks status + version and writes
  `appointment_reminders_sent` (PK) so a reminder is sent at most once per kind even with at-least-once delivery;
  in-app notification + e-mail via `MailOutbox.tryEnqueue`. No phone/e-mail of the other party in the content.
- **Assignment:** a BROKER may add other BROKER accounts to a team (`broker_team_members`); a lead of the owner's
  listings can be assigned to the owner or an active member; members handle only leads assigned to them. Removing a
  member returns their open leads to the owner. Staff read-only oversight stays in the admin page.
- **Qualified lead / ROI:** owner side marks QUALIFIED / UNQUALIFIED with a reason code (+ note). Report per period:
  leads, measured first responses, qualified, appointments confirmed/completed, spend = APPROVED package orders of the
  owner in the period; cost per qualified lead only when both exist, otherwise "chưa có dữ liệu" (never 0).

## Backend
1. V050 leads/idempotency: `assignee_id`, `assigned_at`, `withdrawn_at`, `withdraw_reason`,
   `qualification_note`; `api_idempotency_keys.expires_at`; `lead_events` (history: CREATED, STATUS_CHANGED, ASSIGNED,
   QUALIFIED, WITHDRAWN, APPOINTMENT_*), indexes for the owner JOIN, assignee and requester paths.
2. V051 `broker_team_members`. V052 `viewing_appointments`, `appointment_slots`, `appointment_reminders_sent`.
3. Lead write path: atomic quota, scoped idempotency with retention, listing `FOR SHARE`, `KYC_REQUIRED` /
   `OWNER_KYC_REQUIRED` problem codes, eligibility endpoint `GET /api/v1/me/inquiries/eligibility?listingId=`.
4. Inbox read path `GET /api/v1/leads/inbox`: one JOIN query `leads ⋈ listings ON owner_id` (+ assignee), server filters
   (listingId, status, requestType, qualification, overdue, assignedTo, q), stable order `(created_at DESC, id DESC)`,
   size ≤ 50, separate COUNT and status counts; listing title/slug/address/thumbnail joined in the same query (no
   listing aggregate loading). Existing `/leads/search` and `/leads` for owners switch to the JOIN.
5. `PATCH /leads/{id}/status|qualification|assignee` with `expectedVersion`; `GET /leads/{id}/history`;
   `POST /me/inquiries/{id}/withdraw`; requester history view without owner-internal notes.
6. Appointments API (owner side under `/api/v1/leads/{id}/appointments`, requester side under
   `/api/v1/me/inquiries/{id}/appointments`, shared actions `/api/v1/appointments/{id}/confirm|cancel|outcome`), reminder
   `JobHandler`, notifications, events `appointment_*`, `lead_first_response`, `lead_qualified`.
7. Broker workspace v2 `GET /api/v1/broker/workspace`: SLA metrics, today's tasks (overdue/near-due NEW leads,
   appointments today, proposals waiting for me, outcomes to record), recent intake history, team; team endpoints;
   `GET /api/v1/broker/reports/leads?from&to` (ROI). Staff `GET /api/v1/analytics/lead-funnel` (lead_form_opened →
   kyc_required_shown → lead_submitted, distinct sessions, internal/bot excluded).
8. Security routes, `SensitiveResponseCacheFilter` prefixes (`/api/v1/appointments/**`; `/me/**` already), rate-limit
   policy unchanged for `POST /public/leads` (exists).
9. Tests on PostgreSQL: 20 parallel same key → 1 lead, other actor → own lead, retry → same leadId, expired key reuse;
   20 parallel distinct keys vs quota 10 → exactly 10; pause vs lead (both orders); concurrent status updates → one
   wins, 409 for the other; history; withdraw; owner with 10,000 listings opens a 20-lead page with a bounded number of
   queries and no listing aggregate load; appointment slot rules, two-party confirm, concurrent confirm → one effect,
   overlap refusal, reschedule, cancel, outcome; reminder job once per kind even when run twice / after reschedule;
   team assignment access; SLA and ROI numbers; funnel.

## Frontend (UI kit Sheet/Dialog/Tabs/Badge/InlineFeedback; never colour-only; mobile first)
10. `/my-leads`: unified inbox (reads `?listingId=`), server filters, lead Sheet with history, status/qualification/
    assignment with conflict handling (409 → reload + message), appointment proposal (slots) and outcome.
11. `/my-inquiries`: real response state (first response time), appointment card (confirm a slot, counter-propose,
    cancel), withdraw with confirmation dialog, history.
12. `/broker/workspace`: SLA metrics, today's task cards (1–2 primary actions each), team + assignment, intake
    history, qualified-lead/ROI report with period selector and "chưa có dữ liệu" states.
13. Lead modal: `lead_form_opened`, `kyc_required_shown` on `KYC_REQUIRED`, KYC link returning to the listing with the
    contact intent (`?contact=1`), which reopens the modal.
14. Vitest for slot validation/labels/conflict handling helpers and key components; lint, typecheck, build, bundle.

Deliverables per `03_AGENT_RULES.md`: commits, `streams/s3b-leads.md`, final message.
