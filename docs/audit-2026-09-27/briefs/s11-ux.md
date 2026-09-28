# Brief S11-UX (W5) — final UX/a11y/responsive review, journey E2E, visual baselines, admin aliases

Branch `audit/s11-ux` (from `audit-2026-09-27` ≥ `40323a2`); no Flyway migration expected (V100+ range shared with
S9/S10 if one is needed); backend 18125; Vite 5325; E2E database prefix `s11ux_e2e`, Redis DB index 11.
Parallel in W5: S9-QUALITY (may reformat files — keep diffs focused, no repo-wide formatting) and S10-PERF.

Read first: `00_PLAN.md`, `01_REQUIREMENTS.md`, `02_CONTRACTS.md` §12–§13, `03_AGENT_RULES.md`, `briefs/wave-rules.md`,
`docs/ui/` (DESIGN_SYSTEM, PAGE_MATRIX, QA), every report in `streams/`. Audit: §5 (route table incl. aliases),
§8 (design system, flows), §10 (release conditions).

Requirement IDs: F01.5, F01.8, UI-26, DS-03 (page-wide 44 px targets), DS-04, DS-05 (visual/E2E pass), DS-06, DS-07,
DS-11 (journey E2E), DS-15 (a11y part; CWV RUM is S8), R-1 (E2E part), R-7; E2E follow-ups of UI-08/09/10 (S3b), P-02
(S6), S7 pages, S8 consent banner, S5B MFA, S1 hidden-listing images.

## Test infrastructure
1. `scripts/e2e-local.sh`: stream ports/prefix via env (`E2E_BACKEND_PORT=18125 E2E_FRONTEND_PORT=5325
   E2E_DB_PREFIX=s11ux_e2e E2E_REDIS_DB=11`); new suites added to the default list where they are cheap.
2. **Supply caveat:** `demo.broker` has no VERIFIED KYC in a fresh database (V018 runs before the demo accounts exist).
   The UAT seeder gets `app.uat-seed.kyc-verified-accounts` (listed real accounts without a KYC profile get a synthetic
   VERIFIED one, id `ee5eed07-…`, removed by purge, never touching an existing profile); `e2e-local.sh` and CI pass
   `demo.broker@bds.local`. `E2E_SQL_AFTER_SEED` is no longer needed for the supply suite. Backend test in
   `UatDataSeederTests`.
3. **Admin parallel caveat:** "claim and approve" picks the first unclaimed row, so chromium-1440 and chromium-320 race
   for the same submission. Each Playwright project gets its own submission: the test selects a submission by a
   deterministic index derived from the project (seed has enough PENDING_REVIEW rows), claims through the UI, and
   tolerates nothing shared. Same for any other mutating journey (unique titles/notes per project).

## E2E journeys (chromium-1440 + chromium-320, ≤ 2 workers)
4. Seeker: search → detail → contact while signed out → sign-in keeps the intent (`?contact=1`) → lead sent → appears
   in `/my-inquiries`; withdraw; conflict message where applicable.
5. Owner/poster: `/my-leads` sees the lead, proposes an appointment → seeker confirms in `/my-inquiries` → owner records
   the outcome; `/broker/workspace` shows SLA/today's tasks.
6. Engagement: favourite + saved search → `/saved`; `/notifications` centre; `/shortlists/:token` shared view;
   `/unsubscribe` with a real token (from the API) and with a bad token.
7. Consent banner: refuse → no `/api/v1/events` request; accept → events sent.
8. S7 pages: `/du-an`, `/du-an/:slug`, `/khu-vuc`, `/khu-vuc/:slug`, `/tin-tuc`, `/tin-tuc/:slug`, unknown slug → not
   found state.
9. MFA: staff account enrols TOTP (secret read from the enrolment response, code computed in Node, RFC 6238), verifies,
   and the next staff login asks for the code (uses a throw-away staff account or resets afterwards; never the shared
   demo accounts' state across projects).
10. Admin aliases (UI-26): `/admin`, `/admin/<page>`, `/2026/nhadatchua/admin/<page>` land on the right real page for
    staff, on the admin login for guests, and never show admin content to a buyer (regression test per alias).

## UX / a11y / responsive review
11. axe WCAG 2.2 AA + horizontal-overflow check extended from the public pages to account and admin pages (signed in)
    and the S6/S7 pages, on 320/360/768/1024/1440 (chromium projects; 360/1024 added as projects).
12. Keyboard: skip link, focus visible, dialog/sheet focus trap and return, gallery keyboard, filter sheet, map controls
    reachable; 200 % zoom = 640 px reflow check on core pages.
13. Copy: no internal terms (FR/UC codes, enum names such as `PENDING_REVIEW`, English status names) on seeker-facing
    pages — a DOM check in E2E plus fixes.
14. States: empty/error/loading/offline visible on core pages (API failure injected with `page.route`).
15. S1 follow-up: lead/inquiry cards use `ResponsiveImage` (fallback when a hidden listing's image 404s).
16. Fix what the review finds (focused commits); record the rest as gaps.

## Visual baselines (F01.5)
17. Review current diffs, regenerate the chromium baselines after looking at every image, add baselines for the main
    new pages (listing detail, S7 pages, account pages, an admin page incl. `/admin/security`), document the review
    procedure, make the CI visual step blocking for chromium (remove `continue-on-error`).

## Out of scope / EXTERNAL
- Usability tests with 5–8 real users per group (DS-14): protocol only.
- Firefox/WebKit/real devices and screen readers (NVDA/VoiceOver) runs: not installed locally; CI runs other browsers.
- CWV RUM p75 (S8 data, production traffic).

Deliverables per `03_AGENT_RULES.md`: commits, `streams/s11-ux.md` (route-by-route review, E2E coverage matrix, fixed
issues, gaps), final message (branch, commits, unit/E2E/visual counts, gaps).
