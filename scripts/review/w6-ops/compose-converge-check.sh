#!/usr/bin/env bash
# Review check for PR #22 (W6-OPS): what does the documented deploy sequence
#   compose build; compose stop backend frontend; compose up -d --no-build backend frontend
# do to *dependencies* when (a) only their restart policy changed and (b) the command omits an overlay that the
# running containers were created with (production on the Mac uses infra/compose.apple-silicon.yaml)?
# Uses a throwaway project "revchk-w6" with alpine containers only; removes it at the end.
set -u
dir=$(mktemp -d)
trap 'docker compose -p revchk-w6 --project-directory "$dir" -f "$dir/compose.yaml" down -v -t 1 >/dev/null 2>&1; rm -rf "$dir"' EXIT
cat > "$dir/compose.yaml" <<'EOF'
name: revchk-w6
services:
  dep:
    image: ${DEP_IMAGE:-alpine:3.22}
    command: ["sleep", "3600"]
    restart: ${P:-always}
    healthcheck: { test: ["CMD", "true"], interval: 2s }
  web:
    image: alpine:3.22
    command: ["sleep", "3600"]
    restart: ${P:-always}
    depends_on: { dep: { condition: service_healthy } }
EOF
cat > "$dir/overlay.yaml" <<'EOF'
services:
  dep:
    image: alpine:latest
EOF
dc() { docker compose -p revchk-w6 --project-directory "$dir" "$@"; }
state() { for c in $(docker ps -aq --filter label=com.docker.compose.project=revchk-w6); do
  docker inspect -f '   {{.Name}} id={{printf "%.12s" .Id}} policy={{.HostConfig.RestartPolicy.Name}} image={{.Config.Image}} status={{.State.Status}}' "$c"; done; }

echo "== A. restart policy change only: created with unless-stopped, then documented deploy with the new file"
P=unless-stopped dc -f "$dir/compose.yaml" up -d --wait >/dev/null 2>&1
state
dc -f "$dir/compose.yaml" stop web >/dev/null 2>&1
dc -f "$dir/compose.yaml" up -d --no-build web 2>&1 | sed 's/^/   | /'
state
dc -f "$dir/compose.yaml" down -t 1 >/dev/null 2>&1

echo "== B. running stack created WITH overlay; deploy command WITHOUT overlay; base image of dep not pullable"
dc -f "$dir/compose.yaml" -f "$dir/overlay.yaml" up -d --wait >/dev/null 2>&1
state
dc -f "$dir/compose.yaml" stop web >/dev/null 2>&1
DEP_IMAGE=revchk-w6-nonexistent/none:1 dc -f "$dir/compose.yaml" up -d --no-build web 2>&1 | tail -3 | sed 's/^/   | /'
echo "   exit/after:"; state

echo "== C. same as B but with --no-deps (what the 2026-09 production deploy actually used)"
DEP_IMAGE=revchk-w6-nonexistent/none:1 dc -f "$dir/compose.yaml" up -d --no-build --no-deps web 2>&1 | tail -3 | sed 's/^/   | /'
state
