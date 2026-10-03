#!/usr/bin/env bash
# Operations drills on a disposable CI runner (audit F21.5, R-6, F11.2/F13.1 and the 2026-10 reboot incident).
#
#   BDS_DRILL_ISOLATED=1 scripts/ci-ops-drill.sh recovery   backup -> writes -> loss -> PITR restore and full-host
#                                                           restore; measures RTO (to a serving app) and RPO (lost rows)
#   BDS_DRILL_ISOLATED=1 scripts/ci-ops-drill.sh rollback   current image -> previous images on the migrated database
#                                                           -> roll forward; client-IP chain and session headers;
#                                                           Docker daemon restart (restart policy) with timings
#
# It runs the real docker-compose.yml (project bds-ops, demo credentials from .env.demo.example) plus the backup/PITR
# overlays, builds every image itself and destroys volumes on purpose. It refuses to run without BDS_DRILL_ISOLATED=1
# and is not meant for a workstation: `rollback` restarts the Docker daemon. Evidence: .artifacts/ops-drill/.
# Environment: LOSS_AFTER_SECONDS (default 690; time between the backups and the loss),
#              PREVIOUS_REFS (default: derived at run time from origin/main, see previous_refs),
#              DRILL_QUICK_TUNNEL=1 (also test through the real Cloudflare edge with a quick tunnel; opt-in).
# Production runs with BDS_RESTART_POLICY=always in its .env; the drill sets the same (docker-compose.yml header).
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd -P)"
MODE="${1:-}"
die() { echo "ci-ops-drill: $*" >&2; exit 1; }
[ "${BDS_DRILL_ISOLATED:-}" = 1 ] || die "refusing to run: only for disposable CI runners (set BDS_DRILL_ISOLATED=1)"
case "$MODE" in recovery|rollback) ;; *) die "usage: ci-ops-drill.sh recovery|rollback" ;; esac
command -v python3 >/dev/null || die "python3 is required"

PROJECT=bds-ops
NETWORK="${PROJECT}_bds-network"
ENV_FILE="$REPO/.env.demo.example"
BASE=http://127.0.0.1:3000
ART="$REPO/.artifacts/ops-drill/$MODE"
mkdir -p "$ART"
RESULTS="$ART/results.env"
: > "$RESULTS"
TMP="${RUNNER_TEMP:-/tmp}/bds-ops-drill"
mkdir -p "$TMP"
DEMO_PASSWORD="$(sed -n 's/^DEMO_ACCOUNT_PASSWORD=//p' "$ENV_FILE")"
PG_PASSWORD="$(sed -n 's/^POSTGRES_PASSWORD=//p' "$ENV_FILE")"
MINIO_USER="$(sed -n 's/^MINIO_ROOT_USER=//p' "$ENV_FILE")"
MINIO_PASSWORD="$(sed -n 's/^MINIO_ROOT_PASSWORD=//p' "$ENV_FILE")"
MINIO_IMAGE=bds-minio:RELEASE.2025-10-15T17-29-55Z
export BACKUP_ENV=ci-drill
export BDS_RESTART_POLICY=always # what the production .env sets

COMPOSE_FILES=(-f "$REPO/docker-compose.yml")
if [ "$MODE" = recovery ]; then
  COMPOSE_FILES+=(-f "$REPO/infra/compose.pitr.yaml" -f "$REPO/infra/compose.backup.yaml" -f "$REPO/infra/compose.pitr-backup.yaml")
fi
compose() { docker compose -p "$PROJECT" --env-file "$ENV_FILE" "${COMPOSE_FILES[@]}" "$@"; }

ms() { date +%s%3N; }
secs() { awk -v a="$1" -v b="$2" 'BEGIN { printf "%.1f", (b - a) / 1000 }'; }
log() { echo "[$(date -u +%H:%M:%S)] $*" | tee -a "$ART/drill.log" >&2; }
record() { echo "$1=$2" >> "$RESULTS"; log "RESULT $1=$2"; }
psql_app() { compose exec -T postgres psql -X -q -At -v ON_ERROR_STOP=1 -U bds -d "${DB:-bds}" -c "$1"; }
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1"; }

collect_logs() {
  compose ps -a > "$ART/compose-ps.txt" 2>&1 || true
  compose logs --no-color --timestamps > "$ART/compose.log" 2>&1 || true
}
trap 'status=$?; collect_logs; exit $status' EXIT

# --- helpers --------------------------------------------------------------------------------------------------------
wait_healthy() { # wait_healthy <service> <timeout s>
  local service="$1" timeout="$2" id state i
  for i in $(seq 1 "$timeout"); do
    id="$(compose ps -q "$service" 2>/dev/null || true)"
    if [ -n "$id" ]; then
      state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" 2>/dev/null || true)"
      [ "$state" = healthy ] && return 0
    fi
    sleep 1
  done
  log "$service not healthy after ${timeout}s"
  return 1
}

# The compose healthcheck uses the socket, which the image's temporary init server also answers on first start; the
# real server is the one listening on TCP. Dropping the bootstrap database during init would break the init scripts.
wait_tcp_postgres() { # wait_tcp_postgres <timeout s>
  local i ok=0
  for i in $(seq 1 "$1"); do
    if compose exec -T postgres pg_isready -q -h 127.0.0.1 -U bds -d bds 2>/dev/null; then
      ok=$((ok + 1)); [ "$ok" -ge 3 ] && return 0
    else ok=0; fi
    sleep 1
  done
  log "postgres not accepting TCP connections after $1 s"
  return 1
}

# The app serves: Nginx up, a prerendered listing page from the restored data, search answered by Elasticsearch
# (degraded=false) with at least one result. Echoes the epoch ms when all three first held.
wait_serving() { # wait_serving <timeout s> <listing path>
  local timeout="$1" listing="$2" deadline body first_ok=""
  deadline=$(( $(date +%s) + timeout ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if curl -fsS -o /dev/null --max-time 5 "$BASE/healthz" 2>/dev/null \
       && [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -H 'Accept: text/html' "$BASE$listing")" = 200 ]; then
      [ -n "$first_ok" ] || first_ok="$(ms)"
      body="$(curl -fsS --max-time 10 "$BASE/api/v2/listings/search" 2>/dev/null || true)"
      if echo "$body" | grep -q '"degraded":false' && echo "$body" | python3 -c 'import json,sys; d=json.load(sys.stdin); sys.exit(0 if (d.get("items") or d.get("content") or d.get("results")) else 1)' 2>/dev/null; then
        echo "$first_ok $(ms)"
        return 0
      fi
    fi
    sleep 1
  done
  return 1
}

login() { # login <email> [extra curl args...] -> access token
  local email="$1"
  shift
  curl -fsS --max-time 15 -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' "$@" \
    -d "{\"email\":\"$email\",\"password\":\"$DEMO_PASSWORD\"}" | json 'd["accessToken"]'
}

png() { # png <file> <seed>: a small, valid, unique PNG (no image tooling needed on the runner)
  python3 - "$1" "$2" <<'PY'
import struct, sys, zlib, random
path, seed = sys.argv[1], int(sys.argv[2])
rnd = random.Random(seed)
w, h = 640, 480
rows = []
for y in range(h):
    base = bytes((rnd.randrange(256), (y + seed) % 256, (seed * 37) % 256))
    rows.append(b"\x00" + base * w)
def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) \
    + chunk(b"IDAT", zlib.compress(b"".join(rows), 6)) + chunk(b"IEND", b"")
open(path, "wb").write(png)
PY
}

upload_image() { # upload_image <token> <seed> -> object key
  png "$TMP/img-$2.png" "$2"
  curl -fsS --max-time 60 -X POST "$BASE/api/v1/media/images" -H "Authorization: Bearer $1" \
    -F "file=@$TMP/img-$2.png;type=image/png" | json 'd["objectKey"]'
}

seed_fixture() {
  log "seeding fixture data (UAT seeder, fixed clock)"
  compose run --rm --no-deps backend \
    --app.uat-seed.mode=seed \
    --app.uat-seed.accounts=demo.broker@bds.local,demo.user@bds.local,demo.moderator@bds.local,demo.admin@bds.local \
    --app.uat-seed.kyc-verified-accounts=demo.broker@bds.local,demo.user@bds.local \
    --app.uat-seed.password="$DEMO_PASSWORD" --app.uat-seed.clock=2026-09-01T03:00:00Z --server.port=18080 \
    > "$ART/seed.log" 2>&1
  compose restart backend >/dev/null
  wait_healthy backend 300
}

first_listing() { curl -fsS --max-time 20 "$BASE/sitemaps/listings-0.xml" | grep -o '<loc>[^<]*</loc>' | awk 'NR == 1' | sed 's#<[^>]*>##g; s#^https\{0,1\}://[^/]*##'; }

counts() { # one line: listings users media_objects markers
  psql_app "SELECT (SELECT count(*) FROM listings) || ' ' || (SELECT count(*) FROM users) || ' ' || (SELECT count(*) FROM media_objects) || ' ' || (SELECT coalesce(max(seq), 0) FROM ops_drill_marker)"
}

# Backup tool as uid 999 (owner of BACKUP_DIR with the PITR overlays), backups and age identity read-only.
tool() { # tool <docker run args...> -- <restore.sh args...>
  local args=()
  while [ "$1" != -- ]; do args+=("$1"); shift; done
  shift
  docker run --rm --user 999:999 "${args[@]}" -v "$BACKUP_DIR:/backups:ro" -v "$TMP/age-identity.txt:/run/id:ro" \
    -e BACKUP_ENV="$BACKUP_ENV" -e AGE_IDENTITY_FILE=/run/id -e HOME=/tmp -e MC_CONFIG_DIR=/tmp/.mcli \
    --entrypoint /usr/local/bin/restore.sh bds-backup:1 "$@"
}

manifest_field() { sudo jq -r "$2" "$BACKUP_DIR/$BACKUP_ENV/$1/manifest.json"; }

# ====================================================================================================================
recovery() {
  BACKUP_DIR="$TMP/backups"
  export BACKUP_DIR
  case "$BACKUP_DIR/" in "$REPO/"*) die "BACKUP_DIR must be outside the repository" ;; esac
  sudo rm -rf "$BACKUP_DIR" && mkdir -p "$BACKUP_DIR" && sudo chown 999:999 "$BACKUP_DIR" && sudo chmod 700 "$BACKUP_DIR"
  export BACKUP_AGE_RECIPIENTS=placeholder

  log "building images"
  local t; t="$(ms)"
  compose --profile ops build backend frontend minio backup > "$ART/build.log" 2>&1
  record build_seconds "$(secs "$t" "$(ms)")"
  docker run --rm --entrypoint age-keygen bds-backup:1 > "$TMP/age-identity.txt" 2>/dev/null
  chmod 644 "$TMP/age-identity.txt" # throwaway CI key; uid 999 in the tool container must read it
  BACKUP_AGE_RECIPIENTS="$(grep -o 'age1[0-9a-z]*' "$TMP/age-identity.txt" | awk 'NR == 1')"
  export BACKUP_AGE_RECIPIENTS

  log "starting the stack with WAL archiving (compose.pitr.yaml)"
  t="$(ms)"
  compose up -d --wait postgres redis mailpit minio clamav elasticsearch backend frontend > "$ART/up.log" 2>&1
  record cold_start_seconds "$(secs "$t" "$(ms)")"
  seed_fixture
  psql_app "CREATE TABLE ops_drill_marker (seq bigserial PRIMARY KEY, written_at timestamptz NOT NULL DEFAULT clock_timestamp(), phase text NOT NULL)"
  psql_app "INSERT INTO ops_drill_marker(phase) SELECT 'before-backup' FROM generate_series(1, 5)"
  local listing; listing="$(first_listing)"
  [ -n "$listing" ] || die "no listing in the sitemap after seeding"
  record listing_path "$listing"
  wait_serving 300 "$listing" > /dev/null || die "stack not serving after seeding"
  local broker; broker="$(login demo.broker@bds.local)"
  record image_before_backup_1 "$(upload_image "$broker" 1)"
  record image_before_backup_2 "$(upload_image "$broker" 2)"
  record counts_before_backup "$(counts)"

  log "backups: physical base backup, pg_dump, media, WAL shipped so far"
  compose --profile ops run --rm --no-deps postgres-basebackup basebackup > "$ART/backup-base.log" 2>&1
  compose --profile ops run --rm --no-deps backup db > "$ART/backup-db.log" 2>&1
  compose --profile ops run --rm --no-deps backup media > "$ART/backup-media.log" 2>&1
  compose --profile ops run --rm --no-deps backup wal > "$ART/backup-wal.log" 2>&1
  local db_set media_set base_set
  db_set="$(sudo ls "$BACKUP_DIR/$BACKUP_ENV/db" | grep -v partial | tail -n 1)"
  media_set="$(sudo ls "$BACKUP_DIR/$BACKUP_ENV/media" | grep -v partial | tail -n 1)"
  base_set="$(sudo ls "$BACKUP_DIR/$BACKUP_ENV/base" | grep -v partial | tail -n 1)"
  record db_set "$db_set"; record media_set "$media_set"; record base_set "$base_set"
  record db_backup_ms "$(manifest_field "db/$db_set" .durationMs)"
  record db_backup_bytes "$(manifest_field "db/$db_set" '.files[0].bytes')"
  record db_backup_rows "$(manifest_field "db/$db_set" .counts.totalRows)"
  record media_backup_objects "$(manifest_field "media/$media_set" .counts.objects)"
  record base_backup_bytes "$(manifest_field "base/$base_set" '.files[0].bytes')"
  local backup_done_epoch; backup_done_epoch="$(date +%s)"

  log "production schedule from here: pg_dump hourly, WAL shipped every 300 s, archive_timeout=300; writer: 1 row/s"
  compose --profile ops run -d --no-deps --name bds-ops-backup-schedule -e RUN_ON_START=false backup schedule > /dev/null
  compose exec -d -T postgres psql -X -U bds -d bds -c "DO \$\$ BEGIN FOR i IN 1..100000 LOOP INSERT INTO ops_drill_marker(phase) VALUES ('after-backup'); COMMIT; PERFORM pg_sleep(1); END LOOP; END \$\$;"
  sleep 60
  local reg
  reg="$(curl -sS -o "$ART/register.json" -w '%{http_code}' --max-time 20 -X POST "$BASE/api/v1/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"drill.after.backup@bds.local\",\"password\":\"drill-after-backup-2026\",\"name\":\"Drill After Backup\",\"accountType\":\"USER\"}")"
  record register_after_backup_status "$reg"
  record image_after_backup "$(upload_image "$broker" 3)"
  local loss_after="${LOSS_AFTER_SECONDS:-690}" now
  now="$(date +%s)"
  [ $((backup_done_epoch + loss_after)) -le "$now" ] || sleep $((backup_done_epoch + loss_after - now))

  log "LOSS: PostgreSQL killed, its data volume and local WAL archive deleted (BACKUP_DIR survives)"
  local last
  last="$(psql_app "SELECT max(seq) || ' ' || to_char(max(written_at) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"') FROM ops_drill_marker")"
  record counts_at_loss "$(counts)"
  record last_marker_at_loss "$last"
  docker logs bds-ops-backup-schedule > "$ART/backup-schedule.log" 2>&1 || true
  docker rm -f bds-ops-backup-schedule > /dev/null 2>&1 || true
  compose kill -s SIGKILL postgres > /dev/null
  compose stop backend > /dev/null
  compose rm -sf postgres postgres-wal-archive-init > /dev/null
  docker volume rm "${PROJECT}_postgres-data" "${PROJECT}_postgres-wal-archive" > /dev/null
  sudo find "$BACKUP_DIR/$BACKUP_ENV/wal" -name '*.zst.age' -printf '%f %TY-%Tm-%TdT%TH:%TM:%TS\n' | sort > "$ART/wal-shipped.txt"
  record wal_segments_shipped "$(wc -l < "$ART/wal-shipped.txt" | tr -d ' ')"

  # ---- Scenario B: database volume lost, PITR from base backup + shipped WAL ----------------------------------------
  log "scenario B: PITR restore into a fresh volume"
  local b0 b_db b_first b_serving cv
  b0="$(ms)"
  cv="$(docker compose version --short)"
  for v in postgres-data postgres-wal-archive; do
    docker volume create --label com.docker.compose.project="$PROJECT" --label com.docker.compose.volume="$v" \
      --label com.docker.compose.version="$cv" "${PROJECT}_$v" > /dev/null
  done
  docker run --rm -v "${PROJECT}_postgres-data:/d" -v "${PROJECT}_postgres-wal-archive:/w" alpine:3.22 chown 999:999 /d /w
  tool -v "${PROJECT}_postgres-data:/restore-data" -- basebackup "$base_set" /restore-data > "$ART/pitr-basebackup.log" 2>&1
  tool -v "${PROJECT}_postgres-wal-archive:/restore-wal" -- wal /restore-wal > "$ART/pitr-wal.log" 2>&1
  docker run --rm --user 999:999 -v "${PROJECT}_postgres-data:/d" alpine:3.22 sh -c \
    "printf \"%s\n\" \"restore_command = 'cp /var/lib/postgresql/wal-archive/%f %p'\" >> /d/postgresql.auto.conf && touch /d/recovery.signal"
  compose up -d --wait postgres > "$ART/pitr-up.log" 2>&1
  for _ in $(seq 1 600); do [ "$(psql_app 'SELECT pg_is_in_recovery()' 2>/dev/null)" = f ] && break; sleep 1; done
  [ "$(psql_app 'SELECT pg_is_in_recovery()')" = f ] || die "PITR recovery did not finish"
  b_db="$(ms)"
  compose up -d --wait backend > /dev/null
  read -r b_first b_serving < <(wait_serving 600 "$listing") || die "scenario B: app not serving"
  record pitr_data_restore_seconds "$(secs "$b0" "$b_db")"
  record pitr_rto_first_page_seconds "$(secs "$b0" "$b_first")"
  record pitr_rto_seconds "$(secs "$b0" "$b_serving")"
  record pitr_restored_last_marker "$(psql_app "SELECT max(seq) || ' ' || to_char(max(written_at) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"') FROM ops_drill_marker")"
  record pitr_counts "$(counts)"
  record pitr_user_after_backup "$(psql_app "SELECT count(*) FROM users WHERE email = 'drill.after.backup@bds.local'")"
  compose logs --no-color postgres > "$ART/pitr-postgres.log" 2>&1 || true

  # ---- Verified restore of the logical sets with the project's drill script ------------------------------------------
  log "restore-drill.sh against BACKUP_DIR (isolated project bds-drill)"
  sudo -E env PATH="$PATH" BACKUP_DIR="$BACKUP_DIR" AGE_IDENTITY_FILE="$TMP/age-identity.txt" TOOL_IMAGE=bds-backup:1 \
    DRILL_MINIO_IMAGE="$MINIO_IMAGE" "$REPO/scripts/restore-drill.sh" --env "$BACKUP_ENV" --report-dir "$ART" \
    --note "CI recovery drill (scripts/ci-ops-drill.sh), GitHub-hosted runner, synthetic UAT fixture" \
    > "$ART/restore-drill.log" 2>&1 && record restore_drill PASS || record restore_drill FAIL
  sudo chown -R "$(id -u):$(id -g)" "$ART"

  # ---- Scenario A: whole host lost, only BACKUP_DIR survives: pg_dump + media sets ----------------------------------
  log "scenario A: every container and volume destroyed; restore from the hourly pg_dump and the media set"
  compose kill > /dev/null 2>&1 || true
  compose down -v --remove-orphans > /dev/null 2>&1
  local a0 a_db a_media a_first a_serving
  a0="$(ms)"
  compose up -d postgres redis mailpit minio clamav elasticsearch > "$ART/hostloss-up.log" 2>&1
  wait_tcp_postgres 300
  DB=postgres psql_app "DROP DATABASE bds" # the image's empty bootstrap database; restore.sh only restores into a new one
  tool --network "$NETWORK" -e PGHOST=postgres -e PGPORT=5432 -e PGUSER=bds -e PGPASSWORD="$PG_PASSWORD" -e PGDATABASE=postgres \
    -- db "$db_set" bds > "$ART/hostloss-restore-db.log" 2>&1
  a_db="$(ms)"
  wait_healthy minio 300
  tool --network "$NETWORK" -e MINIO_ENDPOINT=http://minio:9000 -e MINIO_ACCESS_KEY="$MINIO_USER" -e MINIO_SECRET_KEY="$MINIO_PASSWORD" \
    -- media "$media_set" > "$ART/hostloss-restore-media.log" 2>&1
  a_media="$(ms)"
  compose up -d --wait backend frontend > /dev/null
  read -r a_first a_serving < <(wait_serving 900 "$listing") || die "scenario A: app not serving"
  record hostloss_db_restore_seconds "$(secs "$a0" "$a_db")"
  record hostloss_media_restore_seconds "$(secs "$a_db" "$a_media")"
  record hostloss_rto_first_page_seconds "$(secs "$a0" "$a_first")"
  record hostloss_rto_seconds "$(secs "$a0" "$a_serving")"
  record hostloss_restored_last_marker "$(psql_app "SELECT max(seq) || ' ' || to_char(max(written_at) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"') FROM ops_drill_marker")"
  record hostloss_counts "$(counts)"
  record hostloss_user_after_backup "$(psql_app "SELECT count(*) FROM users WHERE email = 'drill.after.backup@bds.local'")"
  tool --network "$NETWORK" -e PGHOST=postgres -e PGPORT=5432 -e PGUSER=bds -e PGPASSWORD="$PG_PASSWORD" -e PGDATABASE=postgres \
    -e MINIO_ENDPOINT=http://minio:9000 -e MINIO_ACCESS_KEY="$MINIO_USER" -e MINIO_SECRET_KEY="$MINIO_PASSWORD" \
    -- verify-references "$media_set" bds > "$ART/hostloss-verify-references.txt" 2>&1 || true
  record hostloss_media_references "$(sed -n 's/^RESULT: //p' "$ART/hostloss-verify-references.txt" | tail -n 1)"
  python3 "$REPO/scripts/ops_drill_report.py" recovery "$RESULTS" > "$ART/summary.md"
  cat "$ART/summary.md"
}

# ====================================================================================================================
check_app() { # check_app <label> <listing path>: health, prerendered listing, search, login + /auth/me
  local label="$1" listing="$2" token code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$BASE/backend-health")"; record "${label}_backend_health" "$code"
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 -H 'Accept: text/html' "$BASE$listing")"; record "${label}_listing_page" "$code"
  code="$(curl -s -o "$ART/$label-search.json" -w '%{http_code}' --max-time 15 "$BASE/api/v1/listings/search?page=0&size=5")"; record "${label}_search_v1" "$code"
  token="$(login demo.user@bds.local 2>/dev/null || true)"
  if [ -n "$token" ]; then
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -H "Authorization: Bearer $token" "$BASE/api/v1/auth/me")"
  else code=login-failed; fi
  record "${label}_login_me" "$code"
}

swap_backend() { # swap_backend <image> <label>: the runbook's rollback (tag + up --no-build --force-recreate backend frontend)
  local image="$1" label="$2" t
  docker tag "$image" "${PROJECT}-backend:latest"
  t="$(ms)"
  compose up -d --no-build --no-deps --force-recreate backend frontend > "$ART/$label-up.log" 2>&1 || true
  if wait_healthy backend 300; then
    record "${label}_healthy_seconds" "$(secs "$t" "$(ms)")"
    record "${label}_start" PASS
  else
    record "${label}_start" FAIL
  fi
  compose logs --no-color backend > "$ART/$label-backend.log" 2>&1 || true
  grep -E 'Schema-validation|FlywayException|Migration|Caused by|APPLICATION FAILED|ERROR' "$ART/$label-backend.log" | awk 'NR <= 40' > "$ART/$label-errors.txt" || true
}

ip_chain() {
  log "client IP chain: cloudflared-like peer in the Compose network -> Nginx -> backend"
  local token sessions forged headers
  # cloudflared forwards Cloudflare's CF-Connecting-IP and an X-Forwarded-For whose left part the visitor controls.
  forged=(-H 'CF-Connecting-IP: 203.0.113.77' -H 'X-Forwarded-For: 6.6.6.6, 203.0.113.77' -H 'X-Real-IP: 198.51.100.9')
  headers="$ART/ipchain-login-headers.txt"
  token="$(docker run --rm --network "$NETWORK" curlimages/curl:8.10.1 -fsS -D /dev/stderr -X POST http://frontend:3000/api/v1/auth/login \
      "${forged[@]}" -H 'User-Agent: Mozilla/5.0 (Windows NT 10.0) Chrome/130.0' -H 'Content-Type: application/json' \
      -d "{\"email\":\"demo.user@bds.local\",\"password\":\"$DEMO_PASSWORD\"}" 2> "$headers" | json 'd["accessToken"]')"
  sessions="$(curl -fsS -H "Authorization: Bearer $token" "$BASE/api/v1/me/sessions")"
  echo "$sessions" > "$ART/ipchain-sessions.json"
  record ipchain_v4_ip_hint "$(echo "$sessions" | json '[s["ipHint"] for s in d if s["current"]][0]')"
  token="$(docker run --rm --network "$NETWORK" curlimages/curl:8.10.1 -fsS -X POST http://frontend:3000/api/v1/auth/login \
      -H 'CF-Connecting-IP: 2001:db8:abcd:12::7' -H 'X-Forwarded-For: 6.6.6.6' -H 'Content-Type: application/json' \
      -d "{\"email\":\"demo.user@bds.local\",\"password\":\"$DEMO_PASSWORD\"}" | json 'd["accessToken"]')"
  record ipchain_v6_ip_hint "$(curl -fsS -H "Authorization: Bearer $token" "$BASE/api/v1/me/sessions" | json '[s["ipHint"] for s in d if s["current"]][0]')"
  compose logs --no-color frontend 2>/dev/null | grep 'POST /api/v1/auth/login' | tail -n 3 > "$ART/ipchain-nginx-log.txt" || true
  record ipchain_nginx_log_client "$(awk '{print $3}' "$ART/ipchain-nginx-log.txt" | sort -u | tr '\n' ' ')"

  log "session token transport: login and /auth/me responses set no cookie and are no-store"
  curl -sS -D "$ART/session-login-headers.txt" -o /dev/null -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
    -H 'X-Forwarded-Proto: https' -d "{\"email\":\"demo.user@bds.local\",\"password\":\"$DEMO_PASSWORD\"}"
  record session_login_set_cookie "$(grep -ci '^set-cookie:' "$ART/session-login-headers.txt" || true)"
  record session_login_cache_control "$(grep -i '^cache-control:' "$ART/session-login-headers.txt" | awk 'NR == 1' | cut -d: -f2- | tr -d '\r' | sed 's/^ *//')"
  curl -sS -D "$ART/session-me-headers.txt" -o /dev/null -H "Authorization: Bearer $token" "$BASE/api/v1/auth/me"
  record session_me_set_cookie "$(grep -ci '^set-cookie:' "$ART/session-me-headers.txt" || true)"
  record session_me_cache_control "$(grep -i '^cache-control:' "$ART/session-me-headers.txt" | awk 'NR == 1' | cut -d: -f2- | tr -d '\r' | sed 's/^ *//')"
  VERIFY_BEARER_TOKEN="$token" "$REPO/scripts/verify-headers.sh" "$BASE" --simulate-https > "$ART/verify-headers-local.txt" 2>&1 \
    && record verify_headers_local PASS || record verify_headers_local FAIL
  record verify_headers_local_summary "$(tail -n 1 "$ART/verify-headers-local.txt")"

  if [ "${DRILL_QUICK_TUNNEL:-0}" = 1 ]; then quick_tunnel; else record edge_ip_hint "skipped (DRILL_QUICK_TUNNEL=1 runs it; manual dispatch only)"; fi
}

# The real Cloudflare edge through a quick tunnel (trycloudflare.com) run by the same cloudflared image as production.
# Opt-in (manual workflow dispatch): it exposes the throwaway CI stack on a public URL for a few minutes.
quick_tunnel() {
  local token
  log "Cloudflare quick tunnel (best effort): real edge -> cloudflared -> Nginx -> backend"
  docker run -d --name bds-ops-quick-tunnel --network "$NETWORK" cloudflare/cloudflared:2026.9.1 \
    tunnel --no-autoupdate --url http://frontend:3000 > /dev/null 2>&1 || true
  local url="" i edge_ip
  for i in $(seq 1 60); do
    url="$(docker logs bds-ops-quick-tunnel 2>&1 | grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' | awk 'NR == 1' || true)"
    [ -n "$url" ] && break
    sleep 2
  done
  if [ -n "$url" ]; then
    for i in $(seq 1 30); do curl -fsS -o /dev/null --max-time 10 "$url/healthz" 2>/dev/null && break; sleep 3; done
    edge_ip="$(curl -fsS --max-time 15 "$url/cdn-cgi/trace" 2>/dev/null | sed -n 's/^ip=//p' || true)"
    token="$(curl -fsS --max-time 20 -X POST "$url/api/v1/auth/login" -H 'Content-Type: application/json' -H 'X-Forwarded-For: 6.6.6.6' \
      -d "{\"email\":\"demo.user@bds.local\",\"password\":\"$DEMO_PASSWORD\"}" -D "$ART/edge-login-headers.txt" | json 'd["accessToken"]' 2>/dev/null || true)"
    record edge_visitor_ip_seen_by_cloudflare "$(echo "$edge_ip" | sed -E 's/\.[0-9]+$/.x/')"
    if [ -n "$token" ]; then
      record edge_ip_hint "$(curl -fsS --max-time 20 -H "Authorization: Bearer $token" "$url/api/v1/me/sessions" | json '[s["ipHint"] for s in d if s["current"]][0]')"
      record edge_login_set_cookie "$(grep -i '^set-cookie:' "$ART/edge-login-headers.txt" | sed -E 's/=[^;]*/=<value>/' | tr -d '\r' | tr '\n' ' ')"
    else
      record edge_ip_hint unavailable
    fi
  else
    record edge_ip_hint "unavailable (no quick tunnel)"
  fi
  docker logs bds-ops-quick-tunnel > "$ART/quick-tunnel.log" 2>&1 || true
  docker rm -f bds-ops-quick-tunnel > /dev/null 2>&1 || true
}

daemon_restart() { # daemon_restart <label> <listing> <graceful|hard>
  local label="$1" listing="$2" kind="$3" t first serving
  docker ps -a --format '{{.Names}} {{.Status}}' > "$ART/$label-before.txt"
  t="$(ms)"
  if [ "$kind" = graceful ]; then
    sudo systemctl restart docker # dockerd stops every container (SIGTERM, then SIGKILL after the stop timeout)
  else
    # Power loss / VM killed: the daemon never stops anything; it dies first, then every container process (137).
    local pids
    pids="$(docker ps -q | xargs -r docker inspect -f '{{.State.Pid}}' | tr '\n' ' ')"
    sudo systemctl kill --kill-whom=main --signal=SIGKILL docker.service || true
    # shellcheck disable=SC2086
    sudo kill -9 $pids 2>/dev/null || true
    sleep 3
    sudo systemctl start docker
  fi
  for _ in $(seq 1 60); do docker info > /dev/null 2>&1 && break; sleep 1; done
  if read -r first serving < <(wait_serving 600 "$listing"); then
    record "${label}_first_page_seconds" "$(secs "$t" "$first")"
    record "${label}_serving_seconds" "$(secs "$t" "$serving")"
  else
    record "${label}_serving_seconds" "not serving after 600 s"
  fi
  docker ps -a --format '{{.Names}}' | while read -r name; do
    docker inspect -f '{{.Name}} policy={{.HostConfig.RestartPolicy.Name}} status={{.State.Status}} restarts={{.RestartCount}} exit={{.State.ExitCode}}' "$name"
  done > "$ART/$label-after.txt"
  record "${label}_running" "$(grep -c 'status=running' "$ART/$label-after.txt" || true)/$(wc -l < "$ART/$label-after.txt" | tr -d ' ')"
}

# Previous images to roll back to, derived from history at run time (no SHA survives a history rewrite):
# origin/main (what is deployed when this branch is a PR), main before its last merge, and the commit before the most
# recent first-parent commit on main that added a Flyway migration (the last schema-changing release).
previous_refs() {
  local main=origin/main schema
  git -C "$REPO" rev-parse --verify -q "$main" > /dev/null || main=HEAD
  schema="$(git -C "$REPO" log --first-parent --diff-filter=A --format=%H -1 "$main" -- backend/src/main/resources/db/migration)"
  {
    git -C "$REPO" rev-parse --short "$main"
    git -C "$REPO" rev-parse --short "$main^1"
    [ -n "$schema" ] && git -C "$REPO" rev-parse --short "$schema^1"
  } | awk '!seen[$0]++' | tr '\n' ' '
}

rollback() {
  log "restart policy rendering: default unless-stopped, production (BDS_RESTART_POLICY=always) always"
  record policy_default "$(BDS_RESTART_POLICY='' docker compose --env-file "$ENV_FILE" -f "$REPO/docker-compose.yml" --profile edge config --format json | python3 -c 'import json,sys; print(sorted({s.get("restart") for s in json.load(sys.stdin)["services"].values()}))')"
  record policy_production "$(docker compose --env-file "$ENV_FILE" -f "$REPO/docker-compose.yml" --profile edge config --format json | python3 -c 'import json,sys; print(sorted({s.get("restart") for s in json.load(sys.stdin)["services"].values()}))')"

  log "building current images"
  local t; t="$(ms)"
  compose build backend frontend minio > "$ART/build.log" 2>&1
  record build_seconds "$(secs "$t" "$(ms)")"
  docker tag "${PROJECT}-backend:latest" bds-backend:current
  local refs="${PREVIOUS_REFS:-$(previous_refs)}" ref sha label resolved_refs=""
  # Outcome keys use resolved SHAs; preserve that correspondence for symbolic overrides too.
  for ref in $refs; do
    resolved_refs="$resolved_refs $(git -C "$REPO" rev-parse --short "$ref")"
  done
  record previous_refs "${resolved_refs# }"
  for ref in $refs; do
    sha="$(git -C "$REPO" rev-parse --short "$ref")"
    log "building backend image of $ref ($sha)"
    rm -rf "$TMP/src-$sha" && mkdir -p "$TMP/src-$sha"
    git -C "$REPO" archive "$sha" backend | tar -x -C "$TMP/src-$sha"
    docker build -q -t "bds-backend:$sha" "$TMP/src-$sha/backend" > "$ART/build-$sha.log" 2>&1
    record "previous_${sha}_subject" "$(git -C "$REPO" log -1 --format='%ad %s' --date=short "$sha" | tr '=' '-')"
    record "previous_${sha}_latest_migration" "$(git -C "$REPO" ls-tree --name-only "$sha" backend/src/main/resources/db/migration/ | sed 's#.*/##' | sort -V | tail -n 1)"
  done

  log "current image on a fresh database, fixture seeded"
  compose up -d --wait postgres redis mailpit minio clamav elasticsearch backend frontend > "$ART/up.log" 2>&1
  seed_fixture
  local listing; listing="$(first_listing)"
  record listing_path "$listing"
  wait_serving 300 "$listing" > /dev/null || die "current image not serving"
  record schema_version "$(psql_app "SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL")"
  record schema_history_rows "$(psql_app 'SELECT count(*) FROM flyway_schema_history')"
  check_app current "$listing"
  ip_chain

  for ref in $refs; do
    sha="$(git -C "$REPO" rev-parse --short "$ref")"
    label="rollback_$sha"
    log "ROLLBACK to $ref ($sha) on the migrated database"
    swap_backend "bds-backend:$sha" "$label"
    if grep -q "^${label}_start=PASS" "$RESULTS"; then check_app "$label" "$listing"; fi
    record "${label}_schema_history_rows" "$(psql_app 'SELECT count(*) FROM flyway_schema_history')"
    log "ROLL FORWARD to the current image"
    swap_backend bds-backend:current "forward_after_$sha"
    check_app "forward_after_$sha" "$listing"
  done

  log "backend restarted alone (crash, OOM kill, docker compose restart backend): does Nginx still reach it?"
  local ip_before ip_after first serving
  ip_before="$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$(compose ps -q backend)")"
  t="$(ms)"
  compose restart backend > /dev/null
  wait_healthy backend 300 || true
  ip_after="$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$(compose ps -q backend)")"
  record backend_only_restart_ip "$ip_before -> $ip_after"
  if read -r first serving < <(wait_serving 180 "$listing"); then
    record backend_only_restart_api PASS
    record backend_only_restart_serving_seconds "$(secs "$t" "$serving")"
  else
    record backend_only_restart_api "FAIL (API through Nginx not served 180 s after the backend restart)"
    curl -s -o "$ART/backend-only-restart-search.json" -w '%{http_code}\n' "$BASE/api/v2/listings/search" > "$ART/backend-only-restart-status.txt" || true
    compose restart frontend > /dev/null # recover for the next steps
    wait_serving 180 "$listing" > /dev/null || true
  fi

  log "restart policy: control containers + Docker daemon restarts"
  record live_restore "$(docker info --format '{{.LiveRestoreEnabled}}')"
  # ctl-<policy>: running at the restart; ctl-stopped-<policy>: stopped through the API first (docker stop, which is
  # also how a desktop app or an update can take containers down before the daemon goes away).
  for p in always unless-stopped; do
    docker run -d --name "ctl-$p" --restart "$p" alpine:3.22 sleep 1d > /dev/null
    docker run -d --name "ctl-stopped-$p" --restart "$p" alpine:3.22 sleep 1d > /dev/null
    docker stop -t 1 "ctl-stopped-$p" > /dev/null
  done
  compose stop mailpit > /dev/null # a service stopped on purpose (e.g. mid-deploy) before the reboot
  daemon_restart daemon_graceful "$listing" graceful
  record ctl_after_graceful "$(docker ps -a --filter name=ctl- --format '{{.Names}}={{.State}}' | sort | tr '\n' ' ')"
  record mailpit_after_graceful "$(docker inspect -f '{{.State.Status}}' "$(compose ps -aq mailpit)")"
  docker start ctl-unless-stopped > /dev/null 2>&1 || true
  daemon_restart daemon_hard "$listing" hard
  record ctl_after_hard "$(docker ps -a --filter name=ctl- --format '{{.Names}}={{.State}}' | sort | tr '\n' ' ')"
  check_app after_restarts "$listing"
  python3 "$REPO/scripts/ops_drill_report.py" rollback "$RESULTS" > "$ART/summary.md"
  cat "$ART/summary.md"
}

"$MODE"
