#!/usr/bin/env bash
# Local E2E run on a disposable stack (contract §13, stream S0-FE ports by default).
#
#   1. fresh PostgreSQL database on the shared test server (bds-test, scripts/test-infra.sh) — never the demo or
#      production Compose projects; Redis DB index and ports are the stream's own
#   2. backend jar: first boot migrates and creates the demo accounts, second boot seeds UAT data with a fixed
#      clock (--app.uat-seed.clock) and keeps serving on the stream port
#   3. production build of the frontend (UI catalog enabled) served by `vite preview`, /api proxied to the backend
#   4. Playwright suites, each with its own report (frontend/playwright-report/<suite>, test-results/<suite>)
#   5. on exit: preview server and backend stopped, database dropped
#
# Usage: scripts/e2e-local.sh [options]
#   --suites "navigation a11y auth-dialog authenticated"   suites to run (visual is opt-in, see below)
#   --projects "chromium-1440 chromium-320"                Playwright projects (Chromium only is installed locally)
#   --visual-determinism   run visual.spec.ts twice against a temporary snapshot dir (baselines are untouched);
#                          with E2E_VISUAL_SNAPSHOT_DIR set the reference shots persist, so a second script run
#                          compares two independently seeded stacks
#   --skip-build           reuse backend/target/*.jar and frontend/dist
#   --skip-backend-build   reuse backend/target/*.jar, rebuild the frontend
#   --keep-db              leave the database for inspection (prints its name)
# Environment overrides: E2E_BACKEND_PORT (18111), E2E_FRONTEND_PORT (5311), E2E_REDIS_DB (2), E2E_DB_PREFIX
# (s0fe_e2e), E2E_SEED_CLOCK (2026-09-01T03:00:00Z), E2E_JAVA_HOME (else $HOME/.local/opt/jdk17, else JAVA_HOME),
# E2E_BACKEND_JAR (run another build of the backend, e.g. an integration branch; skips the backend build).
#
# Search runs on the PostgreSQL path: Elasticsearch is pointed at a closed port on purpose, so the shared test
# cluster's index is never written. CI exercises the Elasticsearch path with the full Compose stack.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_PORT="${E2E_BACKEND_PORT:-18111}"
FRONTEND_PORT="${E2E_FRONTEND_PORT:-5311}"
REDIS_DB="${E2E_REDIS_DB:-2}"
DB_PREFIX="${E2E_DB_PREFIX:-s0fe_e2e}"
SEED_CLOCK="${E2E_SEED_CLOCK:-2026-09-01T03:00:00Z}"
SEED_ACCOUNTS="demo.broker@bds.local,demo.user@bds.local,demo.moderator@bds.local,demo.admin@bds.local"
SUITES="navigation a11y auth-dialog authenticated"
PROJECTS="chromium-1440 chromium-320"
VISUAL_DETERMINISM=0
SKIP_BUILD=0
SKIP_BACKEND_BUILD=0
KEEP_DB=0

while [ $# -gt 0 ]; do
  case "$1" in
    --suites) SUITES="$2"; shift 2 ;;
    --projects) PROJECTS="$2"; shift 2 ;;
    --visual-determinism) VISUAL_DETERMINISM=1; shift ;;
    --skip-build) SKIP_BUILD=1; SKIP_BACKEND_BUILD=1; shift ;;
    --skip-backend-build) SKIP_BACKEND_BUILD=1; shift ;;
    --keep-db) KEEP_DB=1; shift ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

log() { printf '\n[e2e-local] %s\n' "$*"; }
COMPOSE=(docker compose -f "$ROOT/infra/test/compose.yaml")
psql_admin() { "${COMPOSE[@]}" exec -T postgres psql -U bds_test -d bds_test_admin -v ON_ERROR_STOP=1 -qtA -c "$1"; }

# --- prerequisites -------------------------------------------------------------------------------------------------
eval "$("$ROOT/scripts/test-infra.sh" env)"
for service in postgres redis mailpit; do
  if ! "${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -qx "$service"; then
    echo "Shared test infra is not running ($service). Start it with: scripts/test-infra.sh up" >&2
    exit 1
  fi
done
# The backend needs JDK 17+; an ambient JAVA_HOME may point to an older JDK, so pick the first suitable one.
java_major() { "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\)[.".].*/\1/p' | head -1; }
SELECTED_JDK=""
for candidate in "${E2E_JAVA_HOME:-}" "$HOME/.local/opt/jdk17" "${JAVA_HOME:-}"; do
  if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ] && [ "$(java_major "$candidate")" -ge 17 ] 2>/dev/null; then
    SELECTED_JDK="$candidate"
    break
  fi
done
[ -n "$SELECTED_JDK" ] || { echo "JDK 17+ not found (set E2E_JAVA_HOME)" >&2; exit 1; }
export JAVA_HOME="$SELECTED_JDK"
export PATH="$JAVA_HOME/bin:$PATH"
DEMO_ACCOUNT_PASSWORD="$(grep '^DEMO_ACCOUNT_PASSWORD=' "$ROOT/.env.demo.example" | cut -d= -f2-)"
export DEMO_ACCOUNT_PASSWORD
for port in "$BACKEND_PORT" "$FRONTEND_PORT"; do
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port $port is already in use; stop that process or set E2E_BACKEND_PORT/E2E_FRONTEND_PORT." >&2
    exit 1
  fi
done

DB_NAME="${DB_PREFIX}_$(date +%Y%m%d%H%M%S)"
LOG_DIR="$ROOT/frontend/test-results/e2e-local"
mkdir -p "$LOG_DIR"
BACKEND_PID=""
PREVIEW_PID=""

cleanup() {
  local status=$?
  set +e
  if [ -n "$PREVIEW_PID" ] && kill -0 "$PREVIEW_PID" 2>/dev/null; then
    log "stopping vite preview ($PREVIEW_PID)"
    kill "$PREVIEW_PID" 2>/dev/null
    # Bounded wait, then SIGKILL (NIT): a plain `wait` blocks forever if the process ignores SIGTERM, hanging the
    # whole script (and CI) instead of finishing cleanup, same as the backend stop below already does.
    for _ in $(seq 1 30); do kill -0 "$PREVIEW_PID" 2>/dev/null || break; sleep 1; done
    kill -9 "$PREVIEW_PID" 2>/dev/null
    wait "$PREVIEW_PID" 2>/dev/null
  fi
  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    log "stopping backend ($BACKEND_PID)"
    kill "$BACKEND_PID" 2>/dev/null
    for _ in $(seq 1 30); do kill -0 "$BACKEND_PID" 2>/dev/null || break; sleep 1; done
    kill -9 "$BACKEND_PID" 2>/dev/null
  fi
  if [ "$KEEP_DB" = 1 ]; then
    log "database kept: $DB_NAME"
  else
    psql_admin "DROP DATABASE IF EXISTS \"$DB_NAME\" WITH (FORCE)" >/dev/null 2>&1 && log "dropped database $DB_NAME"
  fi
  exit $status
}
trap cleanup EXIT INT TERM

# --- build ---------------------------------------------------------------------------------------------------------
if [ "$SKIP_BACKEND_BUILD" = 0 ] && [ -z "${E2E_BACKEND_JAR:-}" ]; then
  log "building backend jar"
  (cd "$ROOT/backend" && MAVEN_OPTS="${MAVEN_OPTS:--Xmx1g}" sh mvnw -B -ntp -q -DskipTests package)
fi
if [ "$SKIP_BUILD" = 0 ]; then
  log "building frontend (UI catalog enabled)"
  (cd "$ROOT/frontend" && VITE_ENABLE_UI_CATALOG=true npm run build >"$LOG_DIR/frontend-build.log" 2>&1)
fi
JAR="${E2E_BACKEND_JAR:-$(ls "$ROOT"/backend/target/bds-backend-*.jar 2>/dev/null | grep -v plain | head -1)}"
[ -n "$JAR" ] && [ -f "$JAR" ] || { echo "backend jar missing; run without --skip-build" >&2; exit 1; }

# --- database and backend --------------------------------------------------------------------------------------------
log "creating database $DB_NAME"
psql_admin "CREATE DATABASE \"$DB_NAME\""
"${COMPOSE[@]}" exec -T redis redis-cli -n "$REDIS_DB" FLUSHDB >/dev/null

export APP_MODE=demo SPRING_PROFILES_ACTIVE=local SERVER_PORT="$BACKEND_PORT" SPRINGDOC_API_DOCS_ENABLED=false
export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:55432/$DB_NAME"
export SPRING_DATASOURCE_USERNAME="$BDS_TEST_PG_USER" SPRING_DATASOURCE_PASSWORD="$BDS_TEST_PG_PASSWORD"
export SPRING_DATA_REDIS_HOST="$BDS_TEST_REDIS_HOST" SPRING_DATA_REDIS_PORT="$BDS_TEST_REDIS_PORT"
export SPRING_DATA_REDIS_DATABASE="$REDIS_DB" SPRING_DATA_REDIS_PASSWORD=""
export SPRING_ELASTICSEARCH_URIS="http://127.0.0.1:9"
export SPRING_MAIL_HOST="$BDS_TEST_SMTP_HOST" SPRING_MAIL_PORT="$BDS_TEST_SMTP_PORT"
export APP_MEDIA_STORAGE_ENABLED=false CLAMAV_ENABLED=false OUTBOX_ENABLED=false
export APP_GEOCODING_PROVIDER_URL="http://127.0.0.1:9"
export APP_ALLOWED_ORIGINS="http://127.0.0.1:$FRONTEND_PORT,http://localhost:$FRONTEND_PORT"
export APP_PUBLIC_BASE_URL="http://127.0.0.1:$FRONTEND_PORT"
JVM=(java -Xmx768m -jar "$JAR")
SEED_ARGS=(--app.uat-seed.mode=seed "--app.uat-seed.accounts=$SEED_ACCOUNTS" "--app.uat-seed.clock=$SEED_CLOCK")

wait_ready() {
  for _ in $(seq 1 180); do
    curl -fsS "http://127.0.0.1:$BACKEND_PORT/actuator/health/readiness" >/dev/null 2>&1 && return 0
    kill -0 "$BACKEND_PID" 2>/dev/null || { tail -60 "$1"; echo "backend exited" >&2; return 1; }
    sleep 1
  done
  echo "backend not ready after 180 s" >&2
  return 1
}
stop_backend() {
  kill "$BACKEND_PID" 2>/dev/null
  for _ in $(seq 1 30); do kill -0 "$BACKEND_PID" 2>/dev/null || break; sleep 1; done
  BACKEND_PID=""
}

# Boot 1 has no seeder at all: application runners run in no fixed order and the seeder ends its JVM with
# System.exit, so the demo accounts it seeds data for must already exist when it runs.
log "boot 1/2: migrations and demo accounts"
"${JVM[@]}" >"$LOG_DIR/backend-boot1.log" 2>&1 &
BACKEND_PID=$!
wait_ready "$LOG_DIR/backend-boot1.log"
stop_backend

log "boot 2/2: UAT seed (clock $SEED_CLOCK), then serve on :$BACKEND_PORT"
"${JVM[@]}" "${SEED_ARGS[@]}" --app.uat-seed.exit=false >"$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
wait_ready "$LOG_DIR/backend.log"
grep -o 'UAT seed done[^"]*' "$LOG_DIR/backend.log" | head -1 || true
CLOCK_APPLIED="$("${COMPOSE[@]}" exec -T postgres psql -U bds_test -d "$DB_NAME" -qtA \
  -c "SELECT COALESCE(MAX(created_at) <= timestamptz '$SEED_CLOCK', false) FROM listings WHERE id::text LIKE 'ee5eed%'")"
if [ "$CLOCK_APPLIED" != "t" ]; then
  echo "note: this backend ignores --app.uat-seed.clock (S0-BE adds it); seeded dates follow the real clock."
fi

# --- frontend ------------------------------------------------------------------------------------------------------
log "serving frontend on :$FRONTEND_PORT"
(cd "$ROOT/frontend" && API_INTERNAL_URL="http://127.0.0.1:$BACKEND_PORT" \
  exec ./node_modules/.bin/vite preview --host 127.0.0.1 --port "$FRONTEND_PORT" --strictPort) >"$LOG_DIR/preview.log" 2>&1 &
PREVIEW_PID=$!
for _ in $(seq 1 60); do
  curl -fsS "http://127.0.0.1:$FRONTEND_PORT/" >/dev/null 2>&1 && break
  sleep 1
done
curl -fsS "http://127.0.0.1:$FRONTEND_PORT/api/v1/listings/search?size=1" >/dev/null \
  || { echo "frontend proxy to the backend failed" >&2; exit 1; }

# --- Playwright ----------------------------------------------------------------------------------------------------
PROJECT_ARGS=()
for project in $PROJECTS; do PROJECT_ARGS+=("--project=$project"); done
export PLAYWRIGHT_BASE_URL="http://127.0.0.1:$FRONTEND_PORT" E2E_REQUIRE_UI_CATALOG=1

FAILED=()
run_suite() {
  local suite="$1"; shift
  log "suite: $suite"
  if (cd "$ROOT/frontend" && PLAYWRIGHT_SUITE="$suite" npx playwright test "tests/e2e/$suite.spec.ts" "$@"); then
    echo "[e2e-local] $suite: passed"
  else
    echo "[e2e-local] $suite: FAILED (report: frontend/playwright-report/$suite)"
    FAILED+=("$suite")
  fi
}
for suite in $SUITES; do run_suite "$suite" "${PROJECT_ARGS[@]}"; done

if [ "$VISUAL_DETERMINISM" = 1 ]; then
  # E2E_VISUAL_SNAPSHOT_DIR keeps the reference shots between two script runs, so the second run compares two
  # fresh stacks. Without it both runs happen here, against a temporary directory.
  SNAPSHOTS="${E2E_VISUAL_SNAPSHOT_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/bds-visual-XXXXXX")}"
  mkdir -p "$SNAPSHOTS"
  log "visual determinism against $SNAPSHOTS (reviewed baselines untouched)"
  if [ -z "$(ls -A "$SNAPSHOTS")" ]; then
    (cd "$ROOT/frontend" && PLAYWRIGHT_SUITE=visual-seed PLAYWRIGHT_SNAPSHOT_DIR="$SNAPSHOTS" \
      npx playwright test tests/e2e/visual.spec.ts "${PROJECT_ARGS[@]}" --update-snapshots >/dev/null) || true
  fi
  export PLAYWRIGHT_SNAPSHOT_DIR="$SNAPSHOTS"
  run_suite visual "${PROJECT_ARGS[@]}"
  unset PLAYWRIGHT_SNAPSHOT_DIR
  if [ -z "${E2E_VISUAL_SNAPSHOT_DIR:-}" ]; then rm -rf "$SNAPSHOTS"; fi
fi

if [ ${#FAILED[@]} -gt 0 ]; then
  log "failed suites: ${FAILED[*]}"
  exit 1
fi
log "all suites passed"
