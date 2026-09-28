# Brief S1-MEDIA (W3) — image pipeline, signed private URLs, hidden-listing image policy

Branch `audit/s1-media` (from `audit-2026-09-27` @ `1dba873`); backend test port 18118; Vite 5318; stream prefix `s1`.
Parallel in W3: S3b-LEADS and S6-ENGAGE (no shared files expected; S1 owns `com.company.bds.media` and
`app/shared/ui/ResponsiveImage.tsx`).

**Flyway range:** the contract gives V030–V032, but `spring.flyway.out-of-order` is not enabled and V061 is already
applied on the integration line, so V030–V032 would be refused on every existing database. Per orchestrator instruction
S1 uses **V085–V086** instead (note: that is S9's contract range — S9 must start at V087+ after merge).

Read first: `00_PLAN.md`, `01_REQUIREMENTS.md`, `02_CONTRACTS.md` (§3 job queue, §10 images), `03_AGENT_RULES.md`,
`briefs/wave-rules.md`, `streams/s0-be.md` (PublicImageResolver, JobQueue), `streams/s2-search.md` (cards/detail use the
resolver), `streams/s3a-supply.md` (draft preview needs signed URLs), `streams/s4-admin.md` (KYC access log). Audit:
F14 (gallery/image pipeline), §7.3 cache table (public thumbnails; takedown policy), R-3.

Requirement IDs: F14.2, F14.3 (data + detail/lightbox request behaviour; gallery UI itself is S2's F14.1), F14.4,
D-09 (asset/thumbnail rows of §7.3), R-3 (private media part).

## Backend
1. **Schema (V085):** `media_objects` gains `processing_state` (`LEGACY` for rows that predate the pipeline, `PENDING`,
   `READY`, `FAILED`), intrinsic `width`/`height`, `dominant_color`, `lqip` (tiny WebP data URI ≤ 2 KB),
   `processed_at`, `processing_error`; new `media_variants(object_key → media_objects ON DELETE CASCADE, variant_key,
   width, height, format, size_bytes)`; index `listing_media(media_url)` (visibility check + existing delete check).
2. **Pipeline (queue `media-variants`, JobQueue):** upload stores the raw object (after ClamAV + magic-byte check +
   header-only dimension check ≤ 50 MP), inserts `PENDING` and enqueues the job in the same transaction. The handler
   (small batch, sequential) decodes with source subsampling (memory bound: never more than ~2× the 2048 px master),
   applies EXIF orientation (1–8), re-encodes the canonical object in its own format capped at 2048 px **without any
   metadata** (EXIF/GPS/XMP dropped), writes WebP variants at widths min(320/640/960/1600, original) (no
   upscaling, deduplicated), dominant colour + LQIP; state `READY`. Undecodable input → `FAILED` (permanent, never
   served publicly). AVIF output: no maintained JVM encoder → not produced (documented); AVIF input is refused at
   upload because it cannot be decoded/sanitised.
3. **Resolver:** `VariantPublicImageResolver` replaces `UrlOnlyPublicImageResolver` (one query for a whole list):
   width/height, srcset `<key>__w<width>.webp`, `placeholder {dominantColor, lqip}`; LEGACY/PENDING/external keep
   `srcset: []`.
4. **Public serving policy (F14.4, R-3):** `/api/v1/public/media/<key>[__w<n>.webp]` is served only when the object is
   `READY` (or `LEGACY` until backfilled) **and** referenced by a publicly visible listing (ACTIVE listing, APPROVED
   public revision, seller ACTIVE — same predicate as the read model), an ACTIVE user's avatar, or a published CMS
   cover. Drafts, pending edits, hidden/locked/expired listings and banned sellers → 404 at once.
   Objects are never deleted because a listing is hidden (revisions stay immutable; unhide restores access); the
   orphan sweep also removes variants. `Cache-Control: public, max-age=86400` (no longer 1 year immutable) so a
   takedown reaches shared caches within a day; CDN purge on emergency hide is a production step.
5. **Signed URLs (contract §10):** `GET /api/v1/media/signed/<key>[__w<n>.webp]?exp=&sig=` — HMAC-SHA256 over
   `v1|key|exp` with `app.media.signing-secret` (≥ 32 chars, required in production; optional previous secret for
   rotation), constant-time compare, max lifetime 1 h, `no-store`, `Referrer-Policy: no-referrer`. Issued by
   `POST /api/v1/media/signed-urls {urls ≤ 50}` to the object owner or staff (ADMIN/MODERATOR) only — others get
   nothing; external URLs are not signed. KYC/ownership documents keep the stronger header-gated, logged path
   (password re-confirmation + staff reason log) and are never turned into capability URLs.
6. **Backfill:** `POST /api/v2/admin/media/backfill {limit ≤ 500}` (ADMIN) enqueues `LEGACY` public objects in created
   order (idempotent through dedupe keys); `GET` returns counts per state for progress.
7. **Tests (PostgreSQL + shared MinIO, own bucket `s1-media-<rand>`):** EXIF orientation produces rotated masters;
   GPS EXIF gone from master and variants; variant widths/no upscaling; LQIP/colour; large source decoded subsampled;
   resolver one query + shape; public 404 for draft/hidden/banned/pending, 200 once public, unhide restores; signed URL
   expiry/tamper/other key/owner-only issuance/staff; backfill enqueue + processing; delete/orphan removes variants.

## Frontend
8. `ResponsiveImage`: `placeholder.lqip` shown blurred behind the image until it loads (data URI; CSP `img-src data:`
   already allowed), types updated. Gallery/cards already pass `sizes` (S2) — unit test that the LCP image carries
   `srcset`/`sizes`/`fetchpriority` and the lightbox uses variants.
9. `useSignedMediaUrls(urls)` (batch, cached until shortly before expiry) used by the wizard image grid + preview,
   my-listings thumbnails, admin listing images and the profile avatar preview, so owners/staff still see images of
   non-public listings after the public endpoint stops serving them.
10. E2E (chromium, mocked detail API): opening a detail with 20 images requests only variants (never the original).

Deliverables per `03_AGENT_RULES.md`: commits, `streams/s1-media.md` (requirement → evidence, deviations incl. the
Flyway range, gaps, production notes: signing secret, backfill procedure, CDN purge, cache-header change), final message.
