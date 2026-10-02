# W6-OPS — reboot auto-recovery, measured RPO/RTO and rollback, final response chain, owner preparation

Branch `audit/w6-ops` (from `13e41a2`), draft PR https://github.com/Babychandoi/Real-estate/pull/22. Nothing was
applied to the running production stack (`bds-production`); the coordinator deploys. Production was only touched by
paced HTTPS/HTTP GETs (≤ 2 req/s, no auth) on 2026-10-02 19:50–20:12 UTC.

## Status per row

| Row | Status claimed | Evidence | Left |
|---|---|---|---|
| Incident: production down after 3 reboots | Fixed in code (deploy + owner settings pending) | Every long-running service in `docker-compose.yml` and the production overlays: `restart: ${BDS_RESTART_POLICY:-always}` (rendered config checked with `docker compose config`: 9/9 services `always`, cloudflared keeps `depends_on: frontend: service_healthy`, backend keeps `service_healthy` on postgres/redis/minio/clamav/elasticsearch, one-shot init services stay `no`). CI daemon-restart drill: see "Restart policy" below | Owner: Docker Desktop "Start when you sign in", macOS login/pmset (`docs/operations/REBOOT_RECOVERY.md` §3). Coordinator: apply the policy (`docker update --restart=always …` or `up -d`) |
| F21.5 — measured RPO/RTO + compatible rollback | **DONE** (CI, synthetic data) | Run 37059275022 job `recovery-drill` + `rollback-drill` (artifacts `recovery-drill-37059275022-1`, `rollback-drill-37059275022-1`); numbers below; record `docs/ops/drills/2026-10-02-ci-drill.md` | Drill with a real production backup (owner, `scripts/restore-drill.sh --env production`); RTO on the Mac itself |
| F11.2 — final response through CDN/proxy | **DONE** for the real domain; one new finding fixed in code | `VERIFY_PACE_SECONDS=0.6 scripts/verify-headers.sh https://nhadatchuan.online` → **153 checks, 0 failed, 0 warnings**; `scripts/verify-prerender.sh https://nhadatchuan.online` → **49 checks, 0 failed** (2026-10-02T19:50Z). New: `http://nhadatchuan.online/` answered 200 with content instead of redirecting → Nginx 301 for `CF-Visitor` scheme http (`frontend/nginx.conf`), new check 9 in `verify-headers.sh`; CI: `plain HTTP answered 301 -> https://…` (155/155 local checks, run 37059275022) | After deploy: re-run `verify-headers.sh` on the domain (check 9 fails on production until then). Optional: Cloudflare "Always Use HTTPS" |
| R-6 — headers / IP chain / session from the final response; backup classified, restore evidenced | **DONE** with one stated limit | Headers: F11.2. Cache: private paths `/api/v1/auth/me`, `/me/sessions`, `/leads`, `/kyc/me` → 401 `no-cache, no-store, max-age=0, must-revalidate` + `Pragma`/`Expires` on the real site; public JSON is also `no-store` (`cf-cache-status: DYNAMIC`). IP chain: real Cloudflare edge → cloudflared 2026.9.1 → Nginx → backend in CI (quick tunnel): Cloudflare saw the runner as `172.183.91.x`, the app recorded `ipHint 172.183.91.x` despite a forged `X-Forwarded-For: 6.6.6.6`; cloudflared-like peer in the Compose network: `CF-Connecting-IP 203.0.113.77` + forged `X-Forwarded-For 6.6.6.6` / `X-Real-IP 198.51.100.9` → `203.0.113.x`; IPv6 `2001:db8:abcd:12::7` → `2001:db8:abcd::/48`; Nginx access log `203.0.113.0`, `2001:db8::/32`. Session: no `Set-Cookie` on any anonymous production response checked; logged-in (CI, Nginx and real edge) login and `/auth/me`: 0 `Set-Cookie`, `no-store` — sessions are bearer tokens in `sessionStorage`, so cookie flags do not apply. Backup classification: `docs/ops/BACKUP_CLASSIFICATION.md` (existing); restore evidence: F21.5 | The client IP on **production itself** was not observed (needs a login or container logs, both out of bounds); proven on the same images/config through the real Cloudflare edge in CI |
| F01.9 — required checks | EXTERNAL, prepared | `docs/ops/BRANCH_PROTECTION.md`: required `backend-tests`, `frontend-checks`, `e2e`, `security-scan` with `integration_id 15368` (names and app id read from the check runs of `a3799c2`); `mixed-load`, `recovery-drill`, `rollback-drill` advisory (path-filtered workflows never report on unrelated PRs); no `required_linear_history` (main uses merge commits) | Admin applies the ruleset |
| F16.6 — Search Console | EXTERNAL, prepared | `docs/operations/SEARCH_CONSOLE_STEPS.md` (from s7-seo §6) + pre-submission decision on the indexable `uat-` pages seen on production (`/du-an/uat-khu-do-thi-song-hong-xanh`, `/tin-tuc/uat-5-buoc-…`) | Owner's Google account |
| F17.5 — KYC policy decision | EXTERNAL, prepared | `docs/product/KYC_POLICY_DECISION_MEMO.md` (current behaviour from code, options A–D, data needed + queries, decision table) | Product/legal decision |
| F21.3 — git history / secret rotation plan | EXTERNAL, plan completed | `docs/ops/GIT_HISTORY_REMEDIATION_PLAN.md`: completeness table appended; gaps filled (missing secrets in the rotation list, rotate-before-rewrite order, `filter-repo` removes `origin` so the old push step failed, GitHub Support purge, forks/PR refs, scans before and after) | Owner executes |
| F21.4 — production separation | EXTERNAL, prepared | `docs/operations/PRODUCTION_SEPARATION_PLAN.md` (target topology, managed PG+PostGIS/object storage options, cost bands marked as estimates to re-quote, 17-step runbook, rollback criteria); `PRODUCTION_TOPOLOGY.md` §4 step 4 fixed: never run the live tunnel's connector on two hosts with two databases | Infrastructure work |
| DS-14 — usability test 5–8 per group | EXTERNAL, prepared | `docs/product/USABILITY_TEST_PROTOCOL.md` | Real participants |
| P-15 — pilot / supply interviews | EXTERNAL, prepared | `docs/product/PILOT_SUPPLY_INTERVIEW_GUIDE.md` | Real participants |

## F21.5 numbers (CI run 37059275022, GitHub-hosted `ubuntu-latest`, Docker 28.0.4, images prebuilt)

`scripts/ci-ops-drill.sh recovery` — real `docker-compose.yml` + `compose.pitr.yaml` + `compose.backup.yaml` +
`compose.pitr-backup.yaml`, UAT fixture (85 listings, 831 rows in 79 tables, 2 uploaded images → 6 objects), a marker
row committed every second. Backups: physical base backup, pg_dump (532 ms, 342 120 B), media set, WAL shipped; then the
production schedule (pg_dump hourly, WAL shipped every 300 s, `archive_timeout=300`) for 690 s, a user registered and an
image uploaded after the backups.

| Scenario | RTO: start of restore → app serving (prerendered listing 200 + search `degraded:false` with results) | RPO measured | RPO bound from config |
|---|---:|---|---|
| B. PostgreSQL volume + local WAL archive lost; PITR from base backup + 8 shipped segments | **30.8 s** (data 13.7 s); run 2: 27.0 s | **93 rows / 93.2 s** (run 2: 94 / 94.6 s) of commits lost; the user registered after the backup is present | ≈ 600 s (`archive_timeout` 300 s + shipping every 300 s) |
| A. Whole host lost (every container and volume), only `BACKUP_DIR`: pg_dump + media | **57.4 s** (DB 10.0 s, media 2.0 s, ClamAV/ES from empty volumes); run 2: 49.2 s | **688 rows / 694.2 s** (run 2: 683 / 696.4 s) = everything since the pg_dump; post-backup user and image absent; `media_objects` ↔ objects PASS | 1 h DB, 24 h images |
| `scripts/restore-drill.sh` on the same `BACKUP_DIR` (isolated `bds-drill`) | data RTO 6.2 s | row counts per table and sha256 per object: PASS | — |

Not included in RTO: provisioning a host, pulling/building images (build 134 s on the runner), tunnel/DNS. The Mac
under load will be slower; repeat with a real backup.

## Migration rollback drill (`scripts/ci-ops-drill.sh rollback`, same run)

Database migrated to V095 by the current image, UAT fixture seeded, then the runbook's rollback (`docker tag` +
`up -d --no-build --force-recreate backend frontend`) and roll forward:

| Previous image | Its newest migration | Starts on V095 | health / search v1 / login + `/auth/me` | Roll forward |
|---|---|---|---|---|
| `5a5632a` = `13e41a2^1` (main before #21) | V095 | healthy 26.8 s | 200 / 200 / 200 | healthy 17.2 s, all 200 |
| `831a010` = `dcc63c3^1` (release before V027–V095) | V026 | healthy 17.6 s; Flyway "validated 57 migrations", "schema (095) newer than latest available (026)", no migration | 200 / 200 (seeded listings) / 200 | healthy 17.0 s, all 200 |

No migration in V027–V095 breaks backward compatibility for start-up, reads and login. Static review: no
`DROP/RENAME COLUMN`, the only type change widens `listing_reports.reporter_phone` to `TEXT`, replaced CHECKs are
supersets, new `NOT NULL` columns have defaults or belong to tables the old app never writes. Limits: the old app has no
`/render`, so pages fall back to the SPA shell (200) during such a rollback; rows written by the new app with values the
old one does not know (`WITHDRAWN` leads, `EXCEPTION`/`REFUNDED` orders) may break the corresponding old screens.

## Restart policy (CI, Linux Docker Engine, `live-restore=false`)

Runs 37059275022 and 37061495493 (`rollback-drill`, after the fixture and the rollback steps, current images):

| Experiment | Run 1 | Run 2 |
|---|---|---|
| `systemctl restart docker` (daemon stops every container: SIGTERM/SIGKILL) → first page / app serving (search on ES) | 11.9 s / 66.7 s | 11.7 s / 63.0 s |
| Power-loss style (dockerd SIGKILL, then every container process SIGKILL = exit 137) → first page / serving | 4.3 s / 34.3 s | 4.5 s / 57.5 s |
| Stack containers running afterwards (all `always`) | 8/8 + controls | 8/8 + controls |
| Control `unless-stopped`, running at the restart | back | back |
| Control `unless-stopped`, stopped through the API first (`docker stop`) | — | **stays exited (137)** after both restarts |
| Control `always`, stopped through the API first | — | **back** after both restarts |
| `mailpit` stopped with `docker compose stop` before the restart (deliberate stop, e.g. mid-deploy) | back (`always`) | back |
| Container restart counts after recovery | 0 everywhere (no crash loop; backend did not start before its dependencies were reachable) | 0 |

So on Linux Engine a *running* `unless-stopped` container does come back; what `always` adds is recovery of containers
that were stopped through the API before the daemon went away (what Docker Desktop/an update can do on a Mac), and
AutoStart is still needed for the daemon to run at all. The Mac (Docker Desktop VM) itself was not measured: it is
production and must not be restarted by an agent; measure at the next reboot with `REBOOT_RECOVERY.md` §5.

## Other findings

- Production was slow at 2026-10-02 ~20:11 UTC: `/healthz` 4.2–8.9 s, `/` timed out at 30 s (19:50 runs were normal).
  Probing stopped; probably host contention on the Mac (production shares it with dev/test/agents).
- The backend kept its IP across `docker compose restart backend` (`172.18.0.8 -> 172.18.0.8`) and the API through Nginx
  served again 17.4 s after the restart, so Nginx's resolve-once upstream did not go stale in this test.
- UAT seed content (`uat-` slugs) is public and indexable on production (see F16.6 doc).

## Changes

- `docker-compose.yml`, `infra/compose.{backup,pitr,pitr-backup,observability,production-overlay}.yaml`: restart policy.
- `scripts/reset-demo.ps1`: demo keeps `unless-stopped` (no stopped demo racing production for `127.0.0.1:3000`).
- `frontend/nginx.conf`: 301 to HTTPS for Cloudflare HTTP visitors.
- `scripts/verify-headers.sh` (pacing, plain-HTTP check), `scripts/verify-prerender.sh` (pacing).
- `scripts/ci-ops-drill.sh`, `scripts/ops_drill_report.py`, `.github/workflows/operations-drills.yml` (advisory).
- Docs: `docs/operations/REBOOT_RECOVERY.md`, `docs/ops/PRODUCTION_TOPOLOGY.md` (§2, §4, §6 numbers, §8 deploy sequence and
  rollback results), `docs/operations/PRODUCTION_ENV.md`, `RUNBOOK.md`, `infra/production/README.md`,
  `docs/ops/drills/2026-10-02-ci-drill.md`, and the EXTERNAL preparation documents above.

## Checks

- CI run 37059274946 (`a3799c2`): backend-tests, frontend-checks, e2e, security-scan — all success (the e2e job runs the
  changed compose file and Nginx config).
- Operations drills run 37059275022 (`a3799c2`) and 37061495493 (`8b53822`): recovery-drill and rollback-drill success
  in both. Second recovery run: PITR RTO 27.0 s, RPO 94 rows / 94.6 s; host loss RTO 49.2 s, RPO 683 rows / 696.4 s;
  restore-drill PASS; references PASS. Second rollback run: `5a5632a` healthy 26.7 s, `831a010` 12.6 s, all checks 200.
- CI run 37061495476 (`8b53822`): backend-tests, frontend-checks, e2e, security-scan — success.
- No backend or TypeScript source changed: no local `mvn verify` / Vitest needed; `bash -n` on every changed script,
  `docker compose config` on the base file and the PITR/backup overlays.
