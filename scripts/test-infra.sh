#!/usr/bin/env bash
# Shared test infrastructure (PostGIS, Elasticsearch, Redis, MinIO, Mailpit) on loopback ports.
# It never touches the demo (bds-enterprise-stack) or production (bds-production) Compose projects.
#   scripts/test-infra.sh up      start and wait until healthy
#   scripts/test-infra.sh down    stop and remove everything (data is tmpfs, nothing persists)
#   scripts/test-infra.sh status  show container state
#   scripts/test-infra.sh env     print the environment variables tests read
set -euo pipefail
cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f infra/test/compose.yaml)

case "${1:-status}" in
  up) "${COMPOSE[@]}" up -d --wait ;;
  down) "${COMPOSE[@]}" down -v ;;
  status) "${COMPOSE[@]}" ps ;;
  env)
    cat <<'EOF'
export BDS_TEST_PG_URL=jdbc:postgresql://127.0.0.1:55432/bds_test_admin
export BDS_TEST_PG_USER=bds_test
export BDS_TEST_PG_PASSWORD=bds_test_only
export BDS_TEST_ES_URL=http://127.0.0.1:59200
export BDS_TEST_REDIS_HOST=127.0.0.1
export BDS_TEST_REDIS_PORT=56379
export BDS_TEST_MINIO_URL=http://127.0.0.1:59000
export BDS_TEST_MINIO_USER=bds-test-media
export BDS_TEST_MINIO_PASSWORD=bds-test-media-only
export BDS_TEST_SMTP_HOST=127.0.0.1
export BDS_TEST_SMTP_PORT=51025
export BDS_TEST_MAILPIT_API=http://127.0.0.1:58025
EOF
    ;;
  *) echo "usage: $0 up|down|status|env" >&2; exit 2 ;;
esac
