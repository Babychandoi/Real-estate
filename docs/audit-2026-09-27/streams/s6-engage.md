# Stream S6-ENGAGE — saved listings, shared shortlists, saved searches + alerts, notification centre, multi-node SSE (W3)

Branch `audit/s6-engage` (from `audit-2026-09-27` @ `1dba873`). Flyway V068–V070 used (range V068–V074), backend port
18117, Vite 5317, Redis DB claimed per JVM by the test support, no Elasticsearch needed (alerts match on PostgreSQL).
Brief: `briefs/s6-engage.md`.

## 1. How to verify

```sh
eval "$(scripts/test-infra.sh env)"                 # PostgreSQL, Redis, Mailpit of the shared bds-test project
cd backend && sh mvnw -B -ntp verify
cd frontend && npm ci && npm run lint && npx tsc -b && npx vitest run --testTimeout=30000 && npm run build && npm run check:bundle
```

| Check | Result (final commit) |
|---|---|
| `mvnw verify` | **291 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** (270 on the base + 21 new) |
| New backend tests | 21 in 7 classes (`notification.*` 10, `engagement.*` 11), listed in §2 |
| `npm run lint` / `npx tsc -b` | clean |
| `npx vitest run` | **21 files, 165 tests passed** (7 new: `sseStream.test.ts` 4, `engagement.test.tsx` 3; `roles.test.ts` and `listing-detail.meta.test.tsx` updated for the new pages / the favourite button) |
| `npm run build` + `npm run check:bundle` | success, every route within budget (§4) |

## 2. Requirement → evidence

Backend tests: `backend/src/test/java/com/company/bds/notification/` and `…/engagement/`.

| ID | Delivered | Evidence | Status |
|---|---|---|---|
| F12.1 | DB keeps history + cursor: `user_notifications.seq` (global sequence, backfilled in creation order) is the SSE frame id; replay with `Last-Event-ID` reads PostgreSQL. Redis Pub/Sub fan-out on `bds:notifications:v1`: every instance (publisher included) subscribes and delivers to its own streams, so each stream gets a frame once whichever instance wrote it; publish after commit only; Redis down → local delivery + replay on reconnect | `NotificationFanoutTests.aNotificationWrittenOnInstanceAReachesAStreamOnInstanceBExactlyOnce` — **two real application instances** on the same DB/Redis (this context = A; B = a second Spring Boot app with an HTTP port): write through A, a real HTTP SSE client on B receives it once (id = seq, link, category), a rolled-back write publishes nothing. `NotificationUnitTests.whenRedisIsDownTheNotificationStillReachesThisInstancesStreams`, `publishedMessagesAreDelivered…AndGarbageIsIgnored` | DONE |
| F12.2 | Server: `: hb` comment heartbeat every 25 s, `retry:` hint, `event: ready` after replay, `event: resync` when more than 200 frames would be replayed, replay also returns rows of the last 2 minutes at or below the cursor (a transaction that took a lower seq can commit after a higher one was delivered), ≤ 5 streams per user and instance (oldest closed), emitters removed on completion/timeout/error/failed write, `bds.sse.connections`, `bds.sse.dropped`, `bds.notifications.delivered{channel}`. Client (`sseStream.ts`): fetch-based parser, reconnect on error **and normal EOF**, full-jitter backoff 1 s → 30 s (reset after a stable minute, honours `retry:`), `Last-Event-ID` = highest seq seen, dedupe by id (window 500), stop on 401/403 and on sign-out, restart on each sign-in | `NotificationFanoutTests.reconnectingWithLastEventIdReplaysWhatWasMissedIncludingALateCommit` (late commit with a lower seq is replayed; frames carry seq ids), `streamsAreCappedPerUserHeartbeatsFlowAndDeadStreamsAreRemoved` (7 streams → 5, oldest gets EOF, `hb` comment arrives, closed clients are forgotten). `sseStream.test.ts`: frames split across chunks, backoff bounds, **reconnect after EOF and after a network error with `Last-Event-ID` 2 then 3 and no duplicate delivery** (1,2,2,3,3,4 → 1,2,3,4), 401 stops, resync, `stop()` cancels a pending retry | DONE |
| P-02 favourites | `saved_listings`; idempotent `PUT/DELETE /api/v1/me/saved-listings/{id}` (public listings only, 500 cap under a per-user row lock), keyset `GET …?cursor=` with `ListingSummaryV2` cards and “không còn hiển thị” stubs (title only when the owner withdrew it, never for a moderation lock — same rule as the public 410), `GET …/ids`; server events `listing_favorited`/`listing_unfavorited`; heart on every card (search, home, seller, similar) and on the detail page | `SavedListingAndShortlistTests.favouritesAreIdempotentPublicOnlyPagedAndKeepUnavailableStubs` (6 concurrent saves → 1 row/1 event, draft/unknown 404, anonymous 401, locked title withheld, paging, ≤ 12 statements per page incl. auth), `theFavouriteCapIsEnforced`; `engagement.test.tsx` (sign-in when signed out, one ids load, optimistic toggle + rollback with the server's message) | DONE |
| P-02 alerts (new / price drop / back on market) | Trigger on `listing_public_read` (insert, delete, price/purpose change) → coalesced `engage-listing-change` job → the committed public row is compared with the locked last-seen state (`engage_listing_state`, backfilled: no alert storm on deploy) → NEW / PRICE_DROP / BACK_ON_MARKET → candidate searches by SQL (purpose, type, district, price range, created before the fact, account ACTIVE, not the listing owner) → exact match with `SearchFilter.matches` (the predicate the search API re-checks with) → `saved_search_matches` unique (search, listing, kind, fact) = dedupe. Favourites get price-drop / back-on-market notifications (dedupe per fact) | `SavedSearchAlertTests.newListingsPriceDropsAndReturnsMatchOnceAndReachFavourites` (only the matching listing, not the seller's own search, re-processing adds nothing, drop → match + favourite notification, increase → nothing, hide + show → BACK_ON_MARKET once, a search created later does not announce an existing listing); `EngagementDomainTests.classifiesNewPriceDropAndReturn` | DONE |
| P-02 saved search + frequency | `saved_searches` stores the canonical params of contract §7 (validated by `SearchFilterParser`, so invalid filters answer `400 INVALID_FILTER` with every error) + `filterHash`; same filter again → `409 SAVED_SEARCH_EXISTS`; ≤ 20; INSTANT (batched, ≤ 1 alert / 15 min) / DAILY 07:00 / WEEKLY Monday 07:00 Vietnam time / OFF; alert kinds; pause; optimistic update (`expectedVersion`); `saved_search_created` event. Digest: locked task every minute, each search in its own transaction (`FOR UPDATE SKIP LOCKED`, matches marked delivered in the same transaction as the notification and the e-mail job) → one in-app notification + one e-mail (≤ 10 listings, rest counted, listings hidden meanwhile left out) | `SavedSearchAlertTests.theFilterIsValidatedByTheSearchSchemaAndDeduplicatedByItsHash`, `aDigestIsSentOnceEvenWhenTwoInstancesRaceAndSkipsListingsHiddenMeanwhile` (two concurrent digest runs → one notification; e-mail via Mailpit lists the 2 visible listings, not the hidden one; INSTANT throttle); `EngagementDomainTests.digestsFollowVietnamTime`; UI “Lưu tìm kiếm” dialog + saved-search tab | DONE |
| P-02 unsubscribe | E-mails carry `List-Unsubscribe` + `List-Unsubscribe-Post: List-Unsubscribe=One-Click` (RFC 8058) and a page link; random 256-bit token, SHA-256 stored, bound to user + scope (one saved search, or the e-mail channel of a category), 400-day validity; `GET` describes (safe for mail prefetchers), `POST` applies, idempotent; `/unsubscribe` confirmation page | `NotificationCenterTests.anEmailCopyRespectsThePreferenceAndCarriesAWorkingOneClickUnsubscribe` (headers checked in Mailpit; category e-mail off, in-app unchanged, no further e-mail; tampered/short token 404); `SavedSearchAlertTests` (saved-search token → frequency OFF, nothing more collected) | DONE |
| P-02 shared shortlist with permissions and mute | `shortlists` / members / items; OWNER, EDITOR, VIEWER; share link carries a role; token random 256-bit, stored hashed, shown once, rotate invalidates the old one, revoke keeps members; join (never lowers a role); owner changes roles/removes members; members leave or mute the list; non-members get 404 (existence hidden); anonymous link view = list name, owner's given name, public listings only; members see each other by given name; optimistic rename (409) | `SavedListingAndShortlistTests.shortlistRolesShareLinksPublicViewMutingAndVersions` | DONE |
| Notification centre (contract §11) | `notify(NotificationRequest{userId, type, title, message, link, dedupeKey, email})` + the old 4-arg signature; once per user+dedupe key; links restricted to same-site paths; categories; `GET /feed` (keyset on seq), `/unread-count`, `POST /{id}/read`, `/read-all {upToSeq}` (what the user saw; later ones stay unread), `DELETE /{id}`; header bell with the count in its accessible name, live toasts, `/notifications` page | `NotificationCenterTests.dedupeLinksFeedPagingReadStateAndDeletionAreScopedToTheUser` (dedupe, `https://…`/`//…` links dropped, paging, other account cannot read/mark/delete, read-all bound, malformed paging → 400), `NotificationUnitTests.requestsKeepOnlySameSiteLinksAndClipLongText` | DONE |
| UI-13 `/account` | Notification preferences per category × channel (in-app locked on for account and own-listing categories, with the reason), privacy section (what shared lists expose, where to manage favourites/searches, read notifications deleted after 180 days), stored avatar that fails to load falls back to initials with a message; profile edits refresh the shared user (`refreshUser`, already present) | `NotificationCenterTests.preferencesMuteOptionalCategoriesButNeverMandatoryOnes` (400 for turning ACCOUNT in-app off / unknown category; muted SHORTLIST not stored; ACCOUNT always) | DONE (no Playwright spec, §5) |
| F19.1 (S6 events) | `listing_favorited`, `listing_unfavorited`, `saved_search_created{filterHash, frequency}` recorded in the business transaction with deterministic keys | assertions in the favourite and saved-search tests | DONE |
| D-14 (notification delivery) | `bds.notifications.created{category,outcome}`, `bds.notifications.delivered{channel,outcome}`, `bds.notifications.fanout{outcome}`, `bds.sse.connections`, `bds.sse.dropped`, `bds.alerts.facts{kind}`, `bds.alerts.matches{kind}`, `bds.alerts.digests{outcome,frequency}`, job metrics of `engage-listing-change` | code | DONE (alert rules = S5, §6) |

## 3. Policies

| Policy | Value |
|---|---|
| Notification categories | ACCOUNT, LISTINGS (in-app mandatory), LEADS, ALERTS (in-app + e-mail by default), SAVED_LISTINGS, SHORTLIST (in-app by default, e-mail off). E-mail only when the producer asks (`email=true`), the category allows it and the address is verified |
| Dedupe keys | notification: per user + `dedupeKey`; e-mail: `notification:<user>:<dedupeKey>`; favourite alerts: `saved-listing:<listing>:<fact>`; digest: `saved-search:<id>:digest:<last match id>`; matches: (search, listing, kind, fact) |
| Facts | NEW (first time public; first seen but published > 3 days ago counts as BACK_ON_MARKET), PRICE_DROP (lower price, same purpose; fact = new price), BACK_ON_MARKET (public again; fact = when it disappeared) |
| Digest | INSTANT: next run (≤ 1 min), then ≤ 1 per 15 min; DAILY 07:00; WEEKLY Monday 07:00 (Asia/Ho_Chi_Minh); OFF collects nothing |
| Caps | 500 favourites, 20 shortlists owned, 100 listings per shortlist, 20 saved searches, 5 SSE streams per user and instance, replay ≤ 200 frames |
| Replay | `seq > Last-Event-ID` plus the last 2 minutes (client dedupes); stream timeout 30 min |
| Retention (daily locked task) | delivered matches 90 days, undelivered 30 days, expired unsubscribe tokens, notifications read more than 180 days ago |
| Tokens | share link and unsubscribe: 32 random bytes, base64url, SHA-256 at rest; unsubscribe valid 400 days |

## 4. Bundle (gzip-1, `npm run check:bundle`)

| Route | Before (W2 budget) | Now | Budget |
|---|---|---|---|
| shell | ≈ 120 | 121.6 | 128 (stream client and bell load after sign-in) |
| `/search` | 143.4 | 145.9 | 147 (+ favourite button, “Lưu tìm kiếm”; dialog lazy) |
| `/nguoi-dang/:sellerId` | 122.4 (measured at S2) | 136.8 | 139 |
| `/saved` | new | 138.2 | 152 (shortlist and search tabs lazy) |
| `/notifications` | new | 129.8 | 143 |
| `/shortlists/:token` | new | 134.4 | 148 |
| `/unsubscribe` | new | 126.9 | 140 |

## 5. Contract deviations and extensions (backward compatible)

1. §11: `notify(NotificationRequest)` returns `Optional<UUID>` (empty = duplicate or muted); the 4-arg `notify` is unchanged, so S3b/S4 callers compile as before. The SSE `data` also carries `category`; extra events `ready`/`resync`; the heartbeat is an SSE comment (`: hb`) instead of `event: heartbeat` (browsers and the new client ignore comments; the old client only looked at `event: notification`). `lastEventId` query parameter accepted besides the header. `GET /api/v1/notifications` (v1 list of 50) kept; the centre uses `/feed`.
2. `MailOutbox` accepts `List-Unsubscribe` and `List-Unsubscribe-Post` besides `X-*` headers.
3. `SecurityConfig` permits `DispatcherType.ASYNC` (completing an SSE stream re-dispatches after the response is committed; it was denied and logged as an error). `GlobalExceptionHandler` treats `AsyncRequestNotUsableException` (client went away) as debug, not a 500. `Last-Event-ID` added to CORS allowed headers.
4. New rate-limit policies `public-unsubscribe` (GET), `public-unsubscribe-apply` (POST) 30/15 min/IP, `public-shortlist` 120/min/IP; no-store paths `/api/v1/public/unsubscribe`, `/api/v1/public/shortlists/**` (the other S6 endpoints are under `/api/v1/me/**` and `/api/v1/notifications/**`, already no-store).
5. Saved searches store the canonical params (sort included, as in the contract's `filterHash`), so the same filter with another sort is another saved search; `q` is stored normalised (lower case, no diacritics), which is what the link reopens.
6. `ListingSummaries` (public component in `search.api`) lets other modules build `ListingSummaryV2` cards (two statements for any number of ids).

## 6. Known gaps (honest)

- **No Playwright E2E** for the new pages (resource limits of this wave); behaviour is covered by backend integration tests and component/unit tests. S11 should add a journey (save → alert → notification → unsubscribe).
- While Redis is down, a client connected to *another* instance gets the notification only when its stream reconnects (at the latest after the 30-minute stream timeout) or when it reloads the centre; nothing is lost (DB replay), it is only late.
- Alerts are raised when a listing becomes public, changes price or returns. An edit that makes an already public listing newly match a search (e.g. more bedrooms) does not alert.
- The owner of a shortlist cannot mute one list (only the SHORTLIST category in preferences); members can.
- Digest e-mails are plain text; no HTML template.
- One unsubscribe token is issued per e-mail (purged after 400 days); fine at current volume.
- A mixed old/new rolling deploy: old instances publish notifications only to their own streams (clients on new instances get them on reconnect).
- `app/features/search/ui/SearchPanels.tsx` is not Prettier-formatted on the base branch (pre-existing, S2's file; left untouched).
- `.env.example` is not updated (this stream must not touch `.env*` files): the new optional variables are listed in §7 and mapped in `docker-compose.yml` with defaults.

## 7. Production / deploy notes

- **Migrations V068–V070** (each `lock_timeout 5s`): V068 adds columns to `user_notifications`, backfills `seq`/`category` in one UPDATE and builds four indexes (small table); V069 creates new tables; V070 creates the saved-search tables, backfills `engage_listing_state` from `listing_public_read` (one row per public listing) and adds two triggers on `listing_public_read` that only enqueue jobs. Additive; rollback = previous image (the triggers keep enqueueing `engage-listing-change` jobs nobody consumes: `DELETE FROM background_jobs WHERE queue = 'engage-listing-change'` if rolling back for long).
- **Redis is required as soon as two backend instances run** (`APP_NOTIFICATIONS_FANOUT=redis`, the default). A single instance without Redis can use `local`.
- New optional variables (defaults in `application.yml`, mapped in `docker-compose.yml` where operational): `APP_NOTIFICATIONS_FANOUT`, `APP_NOTIFICATIONS_HEARTBEAT_MS`, `APP_NOTIFICATIONS_STREAM_TIMEOUT`, `APP_NOTIFICATIONS_MAX_STREAMS_PER_USER`, `APP_ENGAGEMENT_SCHEDULER_ENABLED`, `APP_ENGAGEMENT_DIGEST_MS`, `APP_ENGAGEMENT_RETENTION_CRON`. No secret added.
- Keep `APP_JOBS_ENABLED=true` on at least one instance (queue `engage-listing-change` and the e-mails).
- Nginx: the existing SSE location (`proxy_buffering off`, `proxy_read_timeout 1h`) forwards `Last-Event-ID`; share and unsubscribe tokens appear in access-log paths — keep access logs private.
- Suggested alerts (S5): `bds_jobs_lag_seconds{queue="engage-listing-change"} > 120`, `bds_jobs_dead{queue="engage-listing-change"} > 0`, `increase(bds_notifications_fanout_total{outcome="local_fallback"}[10m]) > 0`.

## 8. Follow-ups for other streams

- **S3b**: lead/appointment notifications can move to `notify(new NotificationRequest(owner, "LEAD_…", title, message, "/my-leads", "lead:<id>:…", true))` to get links, dedupe and e-mail under the LEADS preference (types starting with `LEAD`/`APPOINTMENT` map to LEADS).
- **S4**: trust/billing notices can add links (`/kyc`, `/billing`) the same way.
- **S5**: alert rules above; the SSE location should keep forwarding `Last-Event-ID`.
- **S8**: `listing_favorited`/`saved_search_created` are recorded; favourites and saved searches can feed funnels.
- **S11**: E2E journey and visual baselines for `/saved`, `/notifications`, `/shortlists/:token`, `/unsubscribe`.
- `IMPLEMENTATION_PLANS_HISTORY.md` / `WALKTHROUGHS_HISTORY.md` left to the orchestrator (03_AGENT_RULES).

## 9. Self review (before hand-off)

Correctness, concurrency, security and privacy pass over the diff. Fixed on this branch: UUID keyset floor (`new UUID(MIN, MIN)` sorts in the middle for PostgreSQL's unsigned comparison: candidate/saver pages skipped ids — caught by the tests, now `00000000-…`), text-block SQL missing a space before `FROM`, async SSE dispatch denied, broken-pipe logged as 500, a stream stopped by an expired session not restarting after a re-login of the same account, digest links longer than the 300-character limit (now open the saved-search list), malformed feed paging answering 500 (now 400), and the Redis listener container failing application startup when Redis is unreachable (caught by `RequestRateLimitRedisOutageTests`; the subscription now starts in the background and retries with backoff). Checked and kept: one pending job per listing (coalescing) + row lock on the last-seen state (no double classification); per-user locks for caps; digest SKIP LOCKED + delivered-in-same-transaction; tokens hashed at rest, never logged, GET never changes state; no email/phone in any S6 response; links in notifications restricted to same-site paths.
