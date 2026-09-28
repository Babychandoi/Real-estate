# Brief S4-ADMIN (W2) — moderation v2, admin pages, trust decisions, KYC page, billing F18, assets/dedupe

Branch `audit/s4-admin`; Flyway V055–V064; backend 18115; Vite 5315.
Parallel in W2: S2-SEARCH (read model/public UI, owns `ListingCard`) and S3a-SUPPLY (write path, owns listing
entity/mapper; adds a FAKE_SOLD → owner-confirmation hook in public report creation).

Read first: `00_PLAN.md`, `01_REQUIREMENTS.md`, `02_CONTRACTS.md` (§2, §3, §5, §6 — you own trust decisions; S2 reads
them), `03_AGENT_RULES.md`, `briefs/wave-rules.md`. Audit: F08.2, F08.4, F18, §4.1 Trust/Dữ liệu BĐS/Thanh toán,
§5 admin rows + `/billing` + `/kyc`, §7.4 duplicates, §8.3 Admin, §8.5 Admin flow.

Requirement IDs: F08.2, F08.4, F08.6 (own queries), F18.2, F18.3, F18.4, UI-11, UI-12, UI-18–UI-23, P-04 (types,
validity, complaint history), P-05 (assets, duplicates), P-11, P-14 (random audit, report handling), D-10, DS-13, R-3
(admin previews never public), R-4 (package approval).

## Backend
1. Moderation v2: paged queue (filters: first submission vs edit, age/SLA breach 24 h, duplicates, random audit),
   claim/release with 30 min expiry, `moderation_decisions` with the **real** moderator id (remove hard-coded `…0099`),
   reason code, note; bulk approve/reject ≤50 under the actor's claims with per-item result; weekly locked random-audit
   sampling; keep diff; metrics queue size/age.
2. Admin listings: paged + filters (status, owner, keyword, district, source, pending edit), revision history,
   lock/hide/unhide with mandatory reason (`listing_status_history`), admin-only private preview (no-store).
3. Admin users (ADMIN only): role change (not own role, never remove the last ADMIN, one role row per user),
   lock/unlock with reason, `user_admin_actions` history; list never shows phones; KYC document viewing requires a
   reason and is logged (`kyc_access_log`) through the existing access grant.
4. Reports queue separate from lead oversight: SLA by severity (P0 1 h, HIGH 4 h, MEDIUM 24 h, LOW 72 h), claim,
   `report_events` history, resolution notes, emergency hide kept; show owner response/auto-pause outcome for FAKE_SOLD.
   Privacy: `listing_reports.reporter_phone` is plaintext — encrypt new values with `PiiProtectionService` (masked
   display) and migrate existing values safely.
5. Verification & trust: evidence comparison (KYC name vs `owner_name_on_doc`, certificate number, private document
   images, listing address), decisions with reason codes; approval sets `expires_at` (ownership 180 d, KYC 24 months) and
   `decided_by`; revoke; history; notification 30 days before expiry; fix missing `listingTitle`. `/kyc` copy: what it
   proves and doesn't (not ownership, not legal status), why documents, who sees them/retention, status timeline,
   rejection reason, resubmission.
6. Billing F18: order idempotency (Idempotency-Key scoped to actor + one open order per user+plan returns existing),
   bank settings compare-and-set with `expectedVersion` (409), reconciliation paged by status, exceptions (received
   amount/reference; mismatch → `EXCEPTION` with resolution approve-with-note/reject/refunded offline — extend the status
   CHECK in your migration), `package_order_events` history, single-effect approval under concurrency, mail via
   `MailOutbox`. User `/billing`: paged history, clear statuses, snapshots, no duplicates. P-11 copy: package payment,
   never a property deposit; verify deposit/escrow endpoints are disabled unless `FEATURE_REAL_TRANSACTIONS=true` (test).
7. Assets & duplicates: `property_assets` + FK `listings.property_asset_id`; fingerprint on approval (normalized address
   + type + district + rounded area); exact match links the asset; blocking keys (district+type+area ±5% + price bucket)
   then pg_trgm similarity only over candidates; `listing_duplicate_candidates` (score, reasons, OPEN/DISMISSED/CONFIRMED)
   in the moderation queue with actions; test that comparisons scale with candidates, not n².
8. Tests on PostgreSQL: claim conflicts, bulk scope, real-actor audit, last-admin protection, reporter phone encryption,
   verification expiry, bank conflict, concurrent approval (one effect), 20 parallel order requests → one order,
   exception flow, duplicate candidates.

## Frontend (UI kit DataTable/Dialog/Sheet/Tabs; destructive buttons separated; never colour-only)
9. Admin moderation (queue table with SLA/age, claim, drawer with public vs submitted diff + duplicates, reasoned
   decision, bulk bar showing exact scope), listings, users (roles/status with reason + history), reports (new separate
   route with SLA badges/history), lead oversight kept separate, verification (evidence comparison), billing
   (reconciliation by status, exceptions, history); user `/billing` and `/kyc` improvements; routes + admin nav.
10. E2E chromium: moderator claims and approves with reason; admin resolves a billing exception; admin changes a role
    with reason and sees history.

Deliverables per `03_AGENT_RULES.md`: commits, `streams/s4-admin.md` (policies: SLA, claim expiry, validity periods;
reporter-phone migration notes), final message.
