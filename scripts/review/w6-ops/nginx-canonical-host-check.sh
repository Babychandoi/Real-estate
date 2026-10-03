#!/usr/bin/env bash
# Review round 2 for PR #22 (W6-OPS): the 308 redirect target rendered from BDS_PUBLIC_HOST
# (frontend/nginx-templates/bds-canonical-host.conf.template, envsubst step of the nginx image).
# For each BDS_PUBLIC_HOST value: does nginx start, and where does an HTTP edge visitor get sent?
# Throwaway nginx:1.27-alpine containers on a free loopback port; "backend" resolves to 127.0.0.1.
set -u
REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
PORT="${PORT:-38441}"
NAME=revchk-w6-host
trap 'docker rm -f "$NAME" >/dev/null 2>&1' EXIT
V_HTTP='CF-Visitor: {"scheme":"http"}'

run_case() { # run_case <label> <mode: unset|set> [value]
  local label="$1" mode="$2" value="${3-}" envargs=() st loc out
  [ "$mode" = set ] && envargs=(-e "BDS_PUBLIC_HOST=$value")
  docker rm -f "$NAME" >/dev/null 2>&1
  docker run -d --name "$NAME" --add-host backend:127.0.0.1 -p "127.0.0.1:$PORT:3000" ${envargs[@]+"${envargs[@]}"} \
    -v "$REPO/frontend/nginx.conf:/etc/nginx/conf.d/default.conf:ro" -v "$REPO/frontend/nginx:/etc/nginx/bds:ro" \
    -v "$REPO/frontend/nginx-templates:/etc/nginx/templates:ro" nginx:1.27-alpine >/dev/null
  for _ in $(seq 1 20); do curl -fsS "http://127.0.0.1:$PORT/healthz" >/dev/null 2>&1 && break; sleep 0.3; done
  if ! curl -fsS "http://127.0.0.1:$PORT/healthz" >/dev/null 2>&1; then
    printf '  %-44s NGINX DOWN: %s\n' "$label" "$(docker logs "$NAME" 2>&1 | grep -E 'emerg|error' | tail -1)"
    return
  fi
  out=""
  for h in "Host: nhadatchuan.online" "Host: www.nhadatchuan.online" "Host: evil.example|X-Evil: attacker.example"; do
    local hargs=()
    IFS='|' read -r -a parts <<< "$h"; for p in "${parts[@]}"; do hargs+=(-H "$p"); done
    loc="$(curl -sS -o /dev/null -D - "${hargs[@]}" -H "$V_HTTP" -X POST "http://127.0.0.1:$PORT/api/x?a=1" | tr -d '\r' \
      | awk 'NR==1{s=$2} tolower($1)=="location:"{l=$2} END{print s" "l}')"
    out="$out [${parts[0]#Host: }] $loc;"
  done
  printf '  %-44s %s\n' "$label" "$out"
  docker exec "$NAME" sh -c 'grep -v "^#" /etc/nginx/conf.d/bds-canonical-host.conf | tr -s "\n" " "' 2>/dev/null | sed 's/^/      rendered: /'; echo
}

echo "== BDS_PUBLIC_HOST value -> POST /api/x?a=1 from an HTTP edge visitor, per request Host"
run_case "unset (variable not in env at all)" unset
run_case "empty"                               set ""
run_case "nhadatchuan.online"                  set "nhadatchuan.online"
run_case "with port nhadatchuan.online:443"    set "nhadatchuan.online:443"
run_case "with scheme https://nhadatchuan.online" set "https://nhadatchuan.online"
run_case "trailing space"                      set "nhadatchuan.online "
run_case "contains a double quote"             set 'nhadatchuan.online"'
run_case "nginx variable \$http_x_evil"        set '$http_x_evil'
run_case "CRLF injection attempt"              set $'nhadatchuan.online\r\nSet-Cookie: x=1'
