# Brief S3a-SUPPLY (W2) — listing write path, wizard, OWNER, my-listings, freshness, import, quality

Branch `audit/s3a-supply`; Flyway V045–V049; backend 18114; Vite 5314.
Parallel in W2: S2-SEARCH (read model, search/detail API v2, owns `ListingCard`, `/search`, `/listings/:id`,
`/compare`, `/nguoi-dang/:id`) and S4-ADMIN (moderation v2, admin pages, trust, billing, reports queue, assets/dedupe).

Read first: `00_PLAN.md`, `01_REQUIREMENTS.md`, `02_CONTRACTS.md` (§2, §3, §4, §5, §11, §12, §13), `03_AGENT_RULES.md`,
`briefs/wave-rules.md`. Audit: F07 (owner/editor path, lazy-loading swallow), F08.1, §4.1 Chủ nhà/Seller-broker/Dữ liệu
BĐS (source/freshness), §4.2 supply operations, §5 `/listings/new`, `/my-listings`, §8.3 Đăng tin, §8.5 Người đăng.

Requirement IDs: F04.1 (write side), F07.2 (owner/editor), F07.3, F08.1, F08.6 (own queries), UI-06, UI-07, P-05
(source + freshness), P-08 (import + quality), P-09 (UX), P-14 (freshness, expiry, reminders, sold-out → owner
confirmation), DS-12, R-4 (revision edits, no lost update).

## Backend
1. Write path: create/update draft accept/validate rent terms (RENT only), furnishing, legal code (+ detail), project id;
   map in entity/domain/mapper (you own these files this wave); keep `ContactInfoGuard`, immutable revisions, media
   ownership; remove the swallowed `LazyInitializationException` and load only what each use case needs; optimistic
   concurrency for draft updates (If-Match/expected version → 409 Problem Details); field errors `errors[{field,message}]`.
2. Owner APIs: `GET /api/v2/me/listings?status=&cursor|page&size` (paged, stable sort, counts per status, public version
   summary + pending edit summary with status/rejection reason), `GET /api/v2/me/listings/{id}/draft`,
   `GET /api/v2/me/listings/{id}/preview` (public-detail shape, owner/staff only, no-store). Capability = `Roles.POSTERS`.
3. OWNER: `POST /api/v1/me/become-owner` (USER → OWNER after explicit confirmation, audited); role label in `/auth/me`.
4. Freshness (P-14): `POST /api/v2/me/listings/{id}/confirm-availability` sets `availability_confirmed_at` and
   `expires_at` (policy 45 days, document it); reminder jobs 7 d and 2 d before (job queue, dedupe per listing+cycle) →
   notification + email; locked expiry task ACTIVE → EXPIRED through paths that fire S2's triggers; renew within 30 days
   reactivates without re-moderation if content unchanged, else resubmit. FAKE_SOLD public report → notify owner to
   confirm within 48 h, else auto-pause (job) — hook in the report creation service (S4 owns admin reports UI/history).
5. Import (P-08): CSV template + `POST /api/v2/me/listings/import` dryRun → per-row validation (types, ranges, contact
   guard, quota); commit → drafts per batch transaction, `source='IMPORT'`, idempotent per file hash; in `importing`.
6. Quality checklist returned with draft/preview/my-listings (≥5 images, description length, location, legal code, rent
   terms for RENT, price/m² plausibility vs district when ≥10 comparables else "chưa đủ dữ liệu"); guidance only.
7. Say who records `listing_published` (S0-BE already records it in the approval path — verify, don't duplicate).
   Tests on PostgreSQL for all of the above incl. concurrent draft updates (one wins, other 409), expiry + reminders
   with the worker, become-owner, import dry-run/commit, sold-out flow.

## Frontend
8. `/listings/new` 4 steps (Cơ bản → Vị trí → Ảnh → Xem trước) with autosave status ("Đang lưu…", "Đã lưu lúc …",
   offline/error retry, 409 conflict dialog), errors next to fields (FormField), correct input modes, role banner,
   rejection reason + guidance, preview + quality checklist + submit. MapLibre only via dynamic import (budget ≤140 kB).
9. `/my-listings`: server paging, status tabs with counts, "Bản đang hiển thị" vs "Bản sửa chờ duyệt/bị từ chối",
   actions (xác nhận còn hàng, gia hạn, ẩn/hiện, sửa, xem khách quan tâm), expiry warnings, CSV import flow, OWNER onboarding.
10. USER → "Tôi là chủ nhà muốn đăng tin" upgrade entry with explanation.
11. E2E chromium: owner posts through 4 steps with autosave, reload keeps draft, submit; my-listings tabs/paging; confirm
    availability.

Deliverables per `03_AGENT_RULES.md`: commits, `streams/s3a-supply.md` (policies: expiry/renewal/auto-pause), final message.
