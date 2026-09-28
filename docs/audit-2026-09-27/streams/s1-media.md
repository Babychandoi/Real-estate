# Stream S1-MEDIA (W3) — report

Branch `audit/s1-media` from `audit-2026-09-27` @ `1dba873`. Brief: `briefs/s1-media.md`.

Commits: `f14c79d` brief · `46b0b84` backend pipeline/policy/signed URLs/backfill · `3c19f08` frontend ·
`86d18b6` self-review fixes · (this report).

## Requirement → evidence

| ID | What was built | Evidence | Status |
|---|---|---|---|
| F14.2 | Upload: ClamAV + magic bytes + **header-only** probe (corrupt or > 50 MP refused, AVIF refused for listing/avatar images) → row `PENDING` + job `media-variants` in the same transaction. `MediaVariantJobHandler` (batch 4, sequential; `ImageProcessor` admits one image per JVM): subsampled decode (decoded raster < 2 × 2048 px long edge), EXIF orientation 1–8 applied, master re-encoded in its own format ≤ 2048 px from pixels only (EXIF/GPS/XMP/IPTC/comments gone — the original object is overwritten), WebP variants `min(320/640/960/1600, width)` without upscaling, dominant colour, 16 px WebP LQIP (≤ 2 KB data URI); `READY`, or `FAILED` once for undecodable input (never retried forever, never public). AVIF output: not produced (no maintained JVM encoder). | `ImageProcessorTests` (6): orientation read + metadata detection; orientation 6 rotates and every output is metadata-free; orientations 3 and 8; variant widths/no upscaling/aspect; dominant colour + LQIP decodable; 6000×4000 decoded subsampled, 10000×10000 PNG header refused before decoding, garbage refused. `MediaPipelineIntegrationTests.uploadIsProcessedByTheJobIntoASanitisedUprightMasterWithVariants` (real MinIO: job enqueued, READY, GPS gone from the stored master, variants 320/400, second delivery is a no-op), `invalidUploadsAreRefusedAndUndecodableObjectsNeverBecomePublic` | DONE (WebP only; no AVIF output, see deviations) |
| F14.3 | `VariantPublicImageResolver` replaces the URL-only fallback: one query per list, width/height, ascending WebP `srcset`, `placeholder {dominantColor, lqip}`; LEGACY/PENDING/external stay `srcset: []`. Cards/detail (S2) pick it up without change. `ResponsiveImage` paints the LQIP (only a validated base64 WebP data URI reaches CSS). | `resolverReturnsSrcsetAndPlaceholderInOneQuery` (`QueryCount.assertAtMost(1)`); `content.test.tsx` LQIP + CSS-injection case; E2E `tests/e2e/media.spec.ts` (chromium, 1440 and 390 px, 20 photos, mocked API): the hero's `currentSrc` is `__w640.webp`, at most 5 image requests on open, **no original requested**, lightbox navigation still uses variants. S2's `Gallery.test.tsx` covers srcset/sizes/`fetchpriority` | DONE (field LCP = S8 RUM) |
| F14.4 | Public media policy: `/api/v1/public/media/<key>` and `<key>__w<n>.webp` are served only while `READY` (or `LEGACY` until backfilled) **and** referenced by an ACTIVE listing's APPROVED public revision with an ACTIVE seller, an ACTIVE user's avatar, or a PUBLISHED CMS article's cover — evaluated on the source tables per request (indexed), so hide/lock/expire/ban take effect on the next request. Nothing is deleted on hide (revisions immutable; unhide restores). Owners/staff see non-public images through signed URLs. Orphan sweep also removes variants and now keeps CMS covers. | `publicServingFollowsListingVisibility` (draft and pending review → 404; ACTIVE → 200 + variant; PAUSED/LOCKED/EXPIRED → 404 original and variant; unhide → 200; locked seller → 404; avatar only once attached), `deletingAnUnattachedImageRemovesItsVariants` | DONE |
| D-09 (media rows of §7.3) | Public media `Cache-Control: public, max-age=86400` (was `max-age=31536000, public, immutable`): URLs are content-stable per key, but a takedown must reach shared caches within a day. Signed and KYC media `no-store` (also forced by `SensitiveResponseCacheFilter` for `/api/v1/media/**`). 404s are `no-store`. | `publicServingFollowsListingVisibility` asserts the header; signed `no-store` asserted in the upload test | DONE (CDN purge = production step) |
| R-3 (private media) | Signed URLs `GET /api/v1/media/signed/<key>?exp&sig`: HMAC-SHA256(`v1\|key\|exp`), constant-time compare, lifetime ≤ 1 h (issuer caps, verifier refuses farther expiries), current + previous secret (rotation), any state of a public-bucket object, never KYC documents. Issued by `POST /api/v1/media/signed-urls` (≤ 50) only for objects the caller owns, or any listing image for ADMIN/MODERATOR; external/KYC/foreign URLs are left out. Upload returns `previewUrl`. Frontend `useSignedMediaUrls` (batched, cached until 1 min before expiry, renders nothing while signing so no public 404 request) in the wizard grid + preview, my-listings and the profile avatar. Production refuses to start without `MEDIA_SIGNING_SECRET` ≥ 32 chars. | `MediaUrlSignerTests` (4): expiry, bound to key/variant/expiry/secret, rotation, 1 h cap + far-future refusal + weak config refused; `signedUrlsAreIssuedOnlyToTheOwnerOrStaffAndCannotBeTamperedWith` (owner 2 of 4 URLs signed, stranger 0, moderator yes, anonymous 401, tampered/moved/changed-expiry/missing sig → 404, KYC key with a valid signature → 404); `useSignedMediaUrls.test.tsx` (3) | DONE |
| Backfill | `POST /api/v2/admin/media/backfill?limit≤500` (ADMIN) enqueues LEGACY public objects without a pending job, oldest first (repeatable, crash-safe); `GET` → counts per state + queue backlog. | `backfillProcessesLegacyImagesWhichStayServedUntilThen` (legacy served raw until processed, owner 403, no double enqueue, READY + EXIF-free + orientation 8 applied, srcset appears) | DONE |

## Test runs (this branch)
- Backend targeted: `ImageProcessorTests` 6, `MediaUrlSignerTests` 4, `PublicImageResolverTests` 3, `MediaStorageServiceTests` 2,
  `MediaPipelineIntegrationTests` 7 — all green (PostgreSQL + shared MinIO `127.0.0.1:59000`, bucket `s1-media-it`).
- Backend full `sh mvnw -B -ntp verify`: 289 tests; 279 green on the first run, 1 failure = `SensitiveResponseCacheFilterTests` still asserting the old 1-year immutable media header (updated to the new 1-day policy, plus a signed-URL `no-store` check), 9 errors = Elasticsearch tests because the shared ES container was not running. After starting ES and fixing the assertion, those classes (`SearchElasticsearchEngineTests` 8, `SearchIndexLagTests` 1, `SensitiveResponseCacheFilterTests` 4) are green, so all 289 pass.
- Frontend: `npm run lint` 0 warnings, `npx tsc -b` 0 errors, `npx vitest run --testTimeout=30000` 20 files / 162 tests,
  `npm run build` ok, `npm run check:bundle` ok (`/search` 143.5 / 144 kB — unchanged by S1 beyond a few bytes).
- E2E: `PLAYWRIGHT_BASE_URL=http://127.0.0.1:5318 npx playwright test tests/e2e/media.spec.ts --project=chromium-1440` → 2 passed
  (Vite dev on 5318, mocked API; stopped afterwards).

## Deviations from the contract
1. **Flyway V085 instead of V030–V032.** `spring.flyway.out-of-order` is off and V061 is applied, so a lower version would
   fail validation on every existing database. V085 is inside S9's contract range (V085–V086): **S9 must start at V087**
   (or the orchestrator reassigns). Only V085 is used; V086 is free.
2. `ImageDto.Placeholder` gained `lqip` (additive; the one-argument constructor remains).
3. KYC/ownership documents are **not** turned into capability URLs: they keep the password-reconfirmed, staff-reason-logged
   header path (`X-Kyc-Document-Access`, S4). A bearer-less link to an identity document could be forwarded; the signed
   URL endpoint refuses `KYC_PRIVATE` objects even with a valid signature.
4. AVIF: uploads of AVIF listing/avatar images are refused (they cannot be decoded, resized or stripped of GPS on the
   JVM); KYC uploads still accept AVIF (stored untouched, private). AVIF variants are not generated.
5. The master replaces the original object (≤ 2048 px, re-encoded): originals with EXIF are not retained anywhere.

## Known gaps / risks
- **LEGACY images are served as before (raw, possibly with EXIF/GPS) until the backfill has run** — run it right after deploy.
- Buyer-side pages that show a listing image after that listing was hidden (e.g. `/my-inquiries`, lead cards) now get a
  404 and show the browser's broken-image/`ResponsiveImage` fallback; intended by the policy, but the raw `<img>` tags
  there have no fallback styling (S11-UX).
- Signed URLs are `no-store`, so my-listings thumbnails are re-downloaded on each visit (small, bounded by page size).
- Native libwebp is bundled for linux x86_64/aarch64 (glibc) and macOS; verified on macOS arm64 only. The handler logs
  `WebP encoder unavailable` at startup if the native library cannot load (jobs then retry and dead-letter; images stay
  PENDING and are not public). Verify once in the production image (see below).
- Rate limiting of the new endpoints relies on the `api-default` policy (S5 owns `RateLimitPolicies`).
- No AVIF output (no maintained JVM encoder); WebP only.

## Production notes
1. **Secret (required, production refuses to start without it):** `MEDIA_SIGNING_SECRET` ≥ 32 random chars, identical on
   every instance (`openssl rand -base64 48`). `docker-compose.yml` now requires it (`:?`). The `.env*` templates were
   not touched — add the variable to the production env file. Rotation: move the old value to
   `MEDIA_PREVIOUS_SIGNING_SECRET`, set a new one, remove the previous after `MEDIA_SIGNED_URL_TTL` (default 15 min).
2. **Migration V085** (additive; `lock_timeout 5s`): new columns with constant defaults (metadata-only), one table, three
   indexes (`listing_media(media_url)`, partial `users(avatar_media_url)`, partial LEGACY backlog) — non-concurrent
   builds, milliseconds at current size. Rollback = previous image (it ignores the new columns; its uploads are LEGACY).
3. **Check WebP in the image:** after deploy, `docker logs <backend> | grep "WebP encoder unavailable"` must be empty;
   `GET /api/v2/admin/media/backfill` shows `PENDING` falling to 0 after an upload.
4. **Backfill procedure** (ADMIN bearer token, repeat until `states.LEGACY` is 0; safe to interrupt and repeat):
   ```sh
   while :; do
     R=$(curl -fsS -X POST -H "Authorization: Bearer $TOKEN" "https://<host>/api/v2/admin/media/backfill?limit=200")
     echo "$R"; echo "$R" | grep -q '"enqueued":0' && break; sleep 60
   done
   curl -fsS -H "Authorization: Bearer $TOKEN" https://<host>/api/v2/admin/media/backfill   # FAILED = undecodable originals
   ```
   Throughput is one image at a time per instance (≈ 0.2–1 s each); `bds.jobs.lag.seconds{queue="media-variants"}` and
   `bds.media.processed{outcome}` show progress. `FAILED` objects (e.g. CMYK JPEG) are not public; re-upload them.
5. **Caches:** public media used to be `immutable` for a year. Browsers/Cloudflare may keep the old (unsanitised) bytes of
   LEGACY images for that long — purge `/api/v1/public/media/*` at the CDN once after the backfill, and on an emergency
   hide purge the listing's image URLs (origin stops serving immediately; shared caches within 1 day otherwise).
6. New properties: `app.media.signing-secret`, `app.media.previous-signing-secret`, `app.media.signed-url-ttl` (≤ PT1H).

## Follow-ups for other streams
- **S9:** Flyway range moves to V087+ (V085 is taken by S1).
- **S2/S11:** raw `<img>` in lead/inquiry cards could use `ResponsiveImage` (fallback for hidden listings, srcset).
- **S3a:** `/api/v2/me/listings/*/preview` still returns public-style `ImageDto`s; the wizard signs them client side. A
  server-side signed `previewImages` would save one round trip.
- **S5:** consider a dedicated rate-limit policy for `POST /api/v1/media/signed-urls` and `GET /api/v1/media/signed/**`;
  nginx CSP for `/api/v1/media/signed/` could mirror the public-media one.
- **S7:** published CMS covers uploaded through `/media/images` are public by the policy above; CMS draft previews should
  use `useSignedMediaUrls`.
