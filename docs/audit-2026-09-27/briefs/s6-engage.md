# Brief S6-ENGAGE (W3) — saved listings, shared shortlists, saved searches + alerts, notification centre, preferences, multi-node SSE

Branch `audit/s6-engage`; Flyway V068–V074; backend 18117; Vite 5317; Redis DB claimed per JVM (contract db 8); ES prefix
`s6` (not needed: alerts match on PostgreSQL). Parallel in W3: S3b-LEADS (calls `notify(...)` for lead/appointment
events; must keep compiling against the old signature) and S1-MEDIA (image variants; cards pick them up through
`PublicImageResolver`).

Read first: `00_PLAN.md`, `01_REQUIREMENTS.md`, `02_CONTRACTS.md` (§3 jobs/lock/mail, §5 events `listing_favorited`,
`listing_unfavorited`, `saved_search_created`, §7 filter schema, §8/§9 read model, **§11 notifications — S6 owns the
internals**), `03_AGENT_RULES.md`, `briefs/wave-rules.md`, `streams/s0-be.md`, `streams/s2-search.md`. Audit: F12, §4.1
"Giữ chân", §4 competitor table (Redfin shared saved searches), §5 `/account`, §7.x publication flow (notification is the
last step), §10 roadmap rows "Multi-node SSE/replay" and "Saved listings/search, alerts, notification preferences".

Requirement IDs: F12.1, F12.2, P-02, UI-13 (plus S6 parts of D-02 "→ notification", D-14 "notification delivery" metric,
F19.1 server events `listing_favorited`/`listing_unfavorited`/`saved_search_created`).

## Backend (module `com.company.bds.engagement` for saved listings/shortlists/saved searches; `notification` keeps the centre)
1. **Notifications v2 (§11).** `user_notifications` gains `seq` (global sequence, backfilled in created order), `category`,
   `link`, `dedupe_key` (unique per user). `notify(NotificationRequest{userId, type, title, message, link, dedupeKey,
   email})` + the old 4-arg signature (category derived from type). Stored in the caller transaction; published after
   commit. Preferences decide per category whether in-app and/or e-mail are delivered; mandatory categories (account,
   security, billing) cannot turn in-app off. E-mail goes through `MailOutbox.tryEnqueue` with a dedupe key.
   API: `GET /api/v1/notifications?cursor=&size=&unread=` (keyset on seq), `GET /unread-count`, `POST /{id}/read`,
   `POST /read-all` (up to a seq), `DELETE /{id}`. Links are validated as same-site relative paths.
2. **Multi-node SSE (F12.1).** Frames `id: <seq>`, `event: notification`, `data: {id, seq, type, title, message, link,
   createdAt}`; `Last-Event-ID` (header or `lastEventId` query) replays from the DB (`seq >` last, bounded, plus a short
   look-back window for transactions that committed out of sequence order — the client dedupes by id). After commit the
   node publishes to Redis channel `bds:notifications:v1`; every node (itself included) delivers to its local emitters.
   Redis down → local delivery + replay on reconnect (never lost, only delayed). Test: two application contexts on the
   same DB/Redis; write through A, a real HTTP SSE client on B receives it once.
3. **SSE hygiene (F12.2).** Heartbeat comment every 25 s with a `retry:` hint, per-user cap on open streams (oldest closed),
   emitters removed on completion/timeout/error/send failure, `bds.sse.connections` gauge and
   `bds.notifications.delivered{channel}` counter, stream timeout 30 min (client reconnects).
4. **Saved listings (favourites).** `saved_listings(user_id, listing_id, created_at)`; `PUT/DELETE /api/v1/me/saved-listings/{id}`
   (idempotent; only publicly visible listings can be saved), `GET /api/v1/me/saved-listings?cursor=` (summaries from the
   read model; listings no longer public shown as "không còn hiển thị" stubs with title only), `GET …/ids`. Cap 500 per
   user. Server events `listing_favorited` / `listing_unfavorited` (deterministic keys).
5. **Shared shortlists.** `shortlists` (owner, name, version), `shortlist_items`, `shortlist_members(role VIEWER|EDITOR,
   muted)`, share link (random 256-bit token stored as SHA-256; role carried by the link; rotate/revoke). Members get a
   `SHORTLIST` notification when items change unless muted; owner removes members/changes roles; members leave.
   Anonymous link view = public listings only, no owner/member PII (owner first name only). Optimistic lock on rename.
6. **Saved searches + alerts.** `saved_searches` store the canonical params (§7, via `SearchFilterParser` — same schema as
   the v2 API) + `filterHash`, name, frequency `INSTANT|DAILY|WEEKLY|OFF`, alert kinds (new, price drop, back on market),
   `paused`; unique (user, filterHash); cap 20 per user; `saved_search_created` event. Matching is event driven: a
   trigger on `listing_public_read` enqueues `engage-listing-change` (dedupe = listing id); the handler compares the row
   with `engage_listing_state` (last seen price/visibility) → NEW (first time public), PRICE_DROP (lower price, same
   purpose), BACK_ON_MARKET (public again after being hidden) → candidate searches by SQL on purpose/type/district/price
   → exact match with `SearchFilter.matches` → `saved_search_matches` unique (search, listing, kind, fact key) = dedupe.
   Favourites also get PRICE_DROP / BACK_ON_MARKET. The snapshot table is backfilled in the migration (no alert storm).
7. **Digest.** Locked task every minute: searches whose `next_digest_at` passed and with undelivered matches → re-check
   visibility → one in-app notification per search + one e-mail per search (≤ 10 listings, count of the rest), mark
   delivered atomically, `next_digest_at` = now + 15 min (INSTANT) / 1 day at 07:00 VN (DAILY) / 7 days (WEEKLY).
   E-mail carries a signed one-click unsubscribe link (`List-Unsubscribe` + `List-Unsubscribe-Post`, RFC 8058).
8. **Unsubscribe / preferences.** `GET /api/v1/public/alerts/unsubscribe?token=` (describe) and `POST` (apply; HMAC
   token = user + saved search or category + purpose, no login). `notification_preferences(user, category, in_app,
   email)` with `GET/PUT /api/v1/me/notification-preferences`. Rate-limit policies for the public endpoints; new private
   prefixes in `SensitiveResponseCacheFilter` (already covers `/api/v1/me/**`, `/api/v1/notifications/**`).
9. **Tests on PostgreSQL + Redis:** two-node fan-out, replay after reconnect incl. out-of-order commit, dead emitter
   cleanup/cap, preferences honoured (in-app/email), mandatory categories, favourite idempotency/visibility/cap/events,
   shortlist permissions (viewer cannot edit, revoked link 404, anonymous view without PII, muted member not notified),
   saved-search validation + dedupe, NEW/PRICE_DROP/BACK_ON_MARKET matching, no alert for listings older than the
   search, digest once under concurrency, unsubscribe token tamper/replay.

## Frontend (UI kit; Lucide icons; Vietnamese copy; no nested interactive elements)
10. `notificationStream.ts`: fetch-based SSE parser, `Last-Event-ID`, reconnect on error **and** normal EOF, exponential
    backoff with full jitter (1 s → 30 s), dedupe by id, abort on logout/unmount; unit-tested with fake streams.
11. Header bell with unread badge (accessible name with count), `/notifications` centre (unread filter, mark read/all,
    delete, deep links, load more), toast for live events.
12. Favourite button (`ListingCard.actions`, detail page), anonymous → login dialog; `/saved` page with tabs: saved
    listings, shortlists (create, add from saved, share link with role, members, mute, leave), saved searches
    (frequency, alert kinds, pause, delete, open search). "Lưu tìm kiếm" on `/search`. Public `/shortlists/:token`
    view; `/unsubscribe` page. `/account`: notification preferences + privacy section (UI-13), avatar fallback and
    profile cache sync.
13. Budgets for new routes in `bundle-budget.json`; shell stays within 128 kB.

Deliverables per `03_AGENT_RULES.md`: commits, `streams/s6-engage.md` (policies: frequencies/digest times, dedupe keys,
replay window, caps, token lifetime; deploy notes: Redis required for multi-node, env vars), final message.
