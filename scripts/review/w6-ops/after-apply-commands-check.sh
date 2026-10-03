#!/usr/bin/env bash
# Review round 2 for PR #22 (W6-OPS): after the documented policy switch (docker update --restart=always +
# BDS_RESTART_POLICY=always in .env), which of the *other* documented production commands still recreate a dependency?
# Shapes mirrored from the docs:
#   run --rm backup db            (ALERT_RUNBOOK, PRODUCTION_TOPOLOGY §6, SEPARATION_PLAN step 4: backup depends on postgres+minio)
#   up -d backup                  (PRODUCTION_TOPOLOGY §6 / compose.backup.yaml header: enable the backup schedule)
#   up -d --no-deps backend       (ALERT_RUNBOOK backend mitigation)
# Throwaway project "revchk-w6-after", alpine only, removed at the end.
set -u
dir=$(mktemp -d)
P=revchk-w6-after
trap 'docker compose -p $P --project-directory "$dir" -f "$dir/compose.yaml" --profile ops down -v -t 1 >/dev/null 2>&1; rm -rf "$dir"' EXIT
cat > "$dir/compose.yaml" <<'EOF'
services:
  postgres:
    image: alpine:3.22
    command: ["sleep", "3600"]
    restart: ${BDS_RESTART_POLICY:-unless-stopped}
    healthcheck: { test: ["CMD", "true"], interval: 2s }
  backend:
    image: alpine:3.22
    command: ["sleep", "3600"]
    restart: ${BDS_RESTART_POLICY:-unless-stopped}
    depends_on: { postgres: { condition: service_healthy } }
  backup:
    profiles: ["ops"]
    image: alpine:3.22
    command: ["sleep", "3600"]
    restart: ${BDS_RESTART_POLICY:-unless-stopped}
    depends_on: { postgres: { condition: service_healthy } }
EOF
dc() { docker compose -p "$P" --project-directory "$dir" -f "$dir/compose.yaml" "$@"; }
pg() { dc ps -q postgres; }

fresh() { # a running stack created before the switch, then the documented switch
  dc --profile ops down -v -t 1 >/dev/null 2>&1
  BDS_RESTART_POLICY= dc up -d --wait postgres backend >/dev/null 2>&1
  docker update --restart=always $(dc ps -aq) >/dev/null
  before="$(pg)"
}
export BDS_RESTART_POLICY=always
report() { local now; now="$(pg)"; if [ "$now" = "$before" ]; then echo "  kept       $1"; else echo "  RECREATED  $1 (postgres ${before:0:12} -> ${now:0:12})"; fi; }

fresh; dc --profile ops run --rm backup true >/dev/null 2>&1;            report "run --rm backup (no --no-deps)"
fresh; dc --profile ops run --rm --no-deps backup true >/dev/null 2>&1;  report "run --rm --no-deps backup"
fresh; dc up -d --no-deps backend >/dev/null 2>&1;                       report "up -d --no-deps backend"
fresh; dc --profile ops up -d backup >/dev/null 2>&1;                    report "up -d backup (no --no-deps)"
fresh; dc up -d backend >/dev/null 2>&1;                                 report "up -d backend (no --no-deps)"
fresh; BDS_RESTART_POLICY= dc --profile ops run --rm backup true >/dev/null 2>&1; report "control: run --rm backup, .env NOT switched"
