#!/usr/bin/env bash
# End-to-end check of the prerender layer through the real Nginx config (stream S7, audit F16): no browser, no JS.
#
#   1. fresh PostgreSQL database on the shared test server (bds-test), backend jar on :18121 with the UAT seed
#   2. the production Nginx image and config (frontend/nginx.conf + frontend/nginx/) serving frontend/dist on
#      127.0.0.1:5321, with `backend:8080` pointed at the host backend
#   3. scripts/verify-prerender.sh and scripts/verify-headers.sh against Nginx
#   4. backend stopped: page requests must still get the static SPA shell (200) from Nginx
#   5. on exit: container and backend stopped, database dropped
#
# Usage: scripts/seo-smoke.sh [--skip-build]
# Environment: SMOKE_BACKEND_PORT (18121), SMOKE_NGINX_PORT (5321), SMOKE_REDIS_DB (9), SMOKE_DB_PREFIX (s7_smoke).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_PORT="${SMOKE_BACKEND_PORT:-18121}"
NGINX_PORT="${SMOKE_NGINX_PORT:-5321}"
REDIS_DB="${SMOKE_REDIS_DB:-9}"
DB_PREFIX="${SMOKE_DB_PREFIX:-s7_smoke}"
CONTAINER="s7-seo-smoke-nginx"
SKIP_BUILD=0
[ "${1:-}" = "--skip-build" ] && SKIP_BUILD=1

log() { printf '\n[seo-smoke] %s\n' "$*"; }
COMPOSE=(docker compose -f "$ROOT/infra/test/compose.yaml")
psql_admin() { "${COMPOSE[@]}" exec -T postgres psql -U bds_test -d bds_test_admin -v ON_ERROR_STOP=1 -qtA -c "$1"; }

eval "$("$ROOT/scripts/test-infra.sh" env)"
export JAVA_HOME="${SMOKE_JAVA_HOME:-$HOME/.local/opt/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"
DEMO_ACCOUNT_PASSWORD="$(grep '^DEMO_ACCOUNT_PASSWORD=' "$ROOT/.env.demo.example" | cut -d= -f2-)"
export DEMO_ACCOUNT_PASSWORD
for port in "$BACKEND_PORT" "$NGINX_PORT"; do
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then echo "Port $port is in use" >&2; exit 1; fi
done

DB_NAME="${DB_PREFIX}_$(date +%Y%m%d%H%M%S)"
LOG_DIR="$ROOT/frontend/test-results/seo-smoke"
WORK="$(mktemp -d)"
mkdir -p "$LOG_DIR"
BACKEND_PID=""

stop_backend() {
  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null
    for _ in $(seq 1 30); do kill -0 "$BACKEND_PID" 2>/dev/null || break; sleep 1; done
    kill -9 "$BACKEND_PID" 2>/dev/null || true
  fi
  BACKEND_PID=""
}
cleanup() {
  local status=$?
  set +e
  docker rm -f "$CONTAINER" >/dev/null 2>&1
  stop_backend
  psql_admin "DROP DATABASE IF EXISTS \"$DB_NAME\" WITH (FORCE)" >/dev/null 2>&1 && log "dropped database $DB_NAME"
  rm -rf "$WORK"
  exit $status
}
trap cleanup EXIT INT TERM

if [ "$SKIP_BUILD" = 0 ]; then
  log "building backend jar and frontend"
  (cd "$ROOT/backend" && MAVEN_OPTS="${MAVEN_OPTS:--Xmx1g}" sh mvnw -B -ntp -q -DskipTests package)
  (cd "$ROOT/frontend" && npm run build >"$LOG_DIR/frontend-build.log" 2>&1)
fi
JAR="$(ls "$ROOT"/backend/target/bds-backend-*.jar 2>/dev/null | grep -v plain | head -1)"
[ -f "$JAR" ] || { echo "backend jar missing" >&2; exit 1; }

log "creating database $DB_NAME"
psql_admin "CREATE DATABASE \"$DB_NAME\""
"${COMPOSE[@]}" exec -T redis redis-cli -n "$REDIS_DB" FLUSHDB >/dev/null

export APP_MODE=demo SPRING_PROFILES_ACTIVE=local SERVER_PORT="$BACKEND_PORT" SPRINGDOC_API_DOCS_ENABLED=false
export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:55432/$DB_NAME"
export SPRING_DATASOURCE_USERNAME="$BDS_TEST_PG_USER" SPRING_DATASOURCE_PASSWORD="$BDS_TEST_PG_PASSWORD"
export SPRING_DATA_REDIS_HOST="$BDS_TEST_REDIS_HOST" SPRING_DATA_REDIS_PORT="$BDS_TEST_REDIS_PORT"
export SPRING_DATA_REDIS_DATABASE="$REDIS_DB" SPRING_DATA_REDIS_PASSWORD=""
export SPRING_ELASTICSEARCH_URIS="http://127.0.0.1:9" SPRING_MAIL_HOST="$BDS_TEST_SMTP_HOST" SPRING_MAIL_PORT="$BDS_TEST_SMTP_PORT"
export APP_MEDIA_STORAGE_ENABLED=false CLAMAV_ENABLED=false OUTBOX_ENABLED=false APP_GEOCODING_PROVIDER_URL="http://127.0.0.1:9"
export APP_PUBLIC_BASE_URL="http://127.0.0.1:$NGINX_PORT" APP_SEO_SHELL_LOCATION="http://127.0.0.1:$NGINX_PORT/index.html"
JVM=(java -Xmx768m -jar "$JAR")

wait_ready() {
  for _ in $(seq 1 180); do
    curl -fsS "http://127.0.0.1:$BACKEND_PORT/actuator/health/readiness" >/dev/null 2>&1 && return 0
    kill -0 "$BACKEND_PID" 2>/dev/null || { tail -60 "$1"; echo "backend exited" >&2; return 1; }
    sleep 1
  done
  return 1
}

log "boot 1/2: migrations and demo accounts"
"${JVM[@]}" >"$LOG_DIR/backend-boot1.log" 2>&1 &
BACKEND_PID=$!
wait_ready "$LOG_DIR/backend-boot1.log"
stop_backend
log "boot 2/2: UAT seed, then serve on :$BACKEND_PORT"
"${JVM[@]}" --app.uat-seed.mode=seed \
  "--app.uat-seed.accounts=demo.broker@bds.local,demo.user@bds.local,demo.moderator@bds.local,demo.admin@bds.local" \
  --app.uat-seed.exit=false >"$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
wait_ready "$LOG_DIR/backend.log"

log "starting Nginx (production config) on :$NGINX_PORT"
sed "s#http://backend:8080#http://host.docker.internal:$BACKEND_PORT#g" "$ROOT/frontend/nginx.conf" >"$WORK/default.conf"
docker run -d --name "$CONTAINER" -p "127.0.0.1:$NGINX_PORT:3000" \
  -v "$WORK/default.conf:/etc/nginx/conf.d/default.conf:ro" -v "$ROOT/frontend/nginx:/etc/nginx/bds:ro" \
  -v "$ROOT/frontend/dist:/usr/share/nginx/html:ro" nginx:1.27-alpine >/dev/null
for _ in $(seq 1 30); do curl -fsS "http://127.0.0.1:$NGINX_PORT/healthz" >/dev/null 2>&1 && break; sleep 1; done

ARCHIVED="$("${COMPOSE[@]}" exec -T postgres psql -U bds_test -d "$DB_NAME" -qtA \
  -c "SELECT slug FROM cms_articles WHERE status = 'ARCHIVED' AND first_published_at IS NOT NULL LIMIT 1")"
status=0
VERIFY_GONE_PATH="${ARCHIVED:+/tin-tuc/$ARCHIVED}" "$ROOT/scripts/verify-prerender.sh" "http://127.0.0.1:$NGINX_PORT" \
  | tee "$LOG_DIR/verify-prerender.txt" || status=1
"$ROOT/scripts/verify-headers.sh" "http://127.0.0.1:$NGINX_PORT" | tee "$LOG_DIR/verify-headers.txt" || status=1

log "backend stopped: Nginx must fall back to the static shell"
stop_backend
code="$(curl -sS -o "$WORK/fallback.html" -w '%{http_code}' "http://127.0.0.1:$NGINX_PORT/listings/any-page")"
if [ "$code" = 200 ] && grep -q '<div id="root"></div>' "$WORK/fallback.html"; then
  echo "  ok    fallback                     static shell served (200) while the backend is down"
else
  echo "  FAIL  fallback                     status $code"
  status=1
fi
exit $status
