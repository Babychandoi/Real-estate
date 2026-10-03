#!/usr/bin/env bash
# W6-OPS fix check for review BLOCKER 2: the procedure in docs/operations/REBOOT_RECOVERY.md §3 that moves a running
# stack from unless-stopped to always without recreating its dependencies:
#   1. docker update --restart=always <every container of the project>
#   2. BDS_RESTART_POLICY=always in the project's .env (simulated here with the environment)
#   3. app deploys only with: -f <base> -f <overlay> stop backend frontend; up -d --no-build --no-deps backend frontend
# Throwaway project "revchk-w6-apply" with alpine containers; removed at the end. Exit 1 when a check fails.
set -u
dir=$(mktemp -d)
P=revchk-w6-apply
trap 'docker compose -p $P --project-directory "$dir" -f "$dir/compose.yaml" -f "$dir/overlay.yaml" down -v -t 1 >/dev/null 2>&1; rm -rf "$dir"' EXIT
cat > "$dir/compose.yaml" <<'EOF'
services:
  dep:
    image: ${DEP_IMAGE:-alpine:3.22}
    command: ["sleep", "3600"]
    restart: ${BDS_RESTART_POLICY:-unless-stopped}
    healthcheck: { test: ["CMD", "true"], interval: 2s }
  web:
    image: alpine:3.22
    command: ["sleep", "3600"]
    restart: ${BDS_RESTART_POLICY:-unless-stopped}
    depends_on: { dep: { condition: service_healthy } }
EOF
cat > "$dir/overlay.yaml" <<'EOF'
services:
  dep:
    image: alpine:latest
EOF
dc() { docker compose -p "$P" --project-directory "$dir" -f "$dir/compose.yaml" -f "$dir/overlay.yaml" "$@"; }
info() { docker inspect -f '{{.Name}} {{printf "%.12s" .Id}} {{.HostConfig.RestartPolicy.Name}} {{.State.Status}}' "$@"; }
FAIL=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1 ($2)"; else echo "FAIL  $1: got '$2', expected '$3'"; FAIL=1; fi; }

dc up -d --wait >/dev/null 2>&1                      # the running stack, created with the old default
dep_id="$(dc ps -q dep)"; web_id="$(dc ps -q web)"
check "created with the old default" "$(docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' "$dep_id")" unless-stopped

docker update --restart=always $(dc ps -aq) >/dev/null # step 1: in place, no recreate
check "dep policy after docker update" "$(docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' "$dep_id")" always
check "dep container kept" "$(dc ps -q dep)" "$dep_id"
check "web container kept" "$(dc ps -q web)" "$web_id"

export BDS_RESTART_POLICY=always                      # step 2
# step 3: an app deploy; the base image of dep is not pullable, as in production where the overlay replaces it
dc stop web >/dev/null 2>&1
DEP_IMAGE=revchk-w6-nonexistent/none:1 dc up -d --no-build --no-deps web >/dev/null 2>&1
check "dep untouched by the app deploy" "$(dc ps -q dep)" "$dep_id"
check "dep still running" "$(docker inspect -f '{{.State.Status}}' "$dep_id")" running
new_web="$(dc ps -q web)"
check "web running after deploy" "$(docker inspect -f '{{.State.Status}}' "$new_web")" running
check "web policy after deploy" "$(docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' "$new_web")" always
info "$dep_id" "$new_web" | sed 's/^/      /'
exit "$FAIL"
