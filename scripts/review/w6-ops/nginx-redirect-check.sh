#!/usr/bin/env bash
# Review check for PR #22 (W6-OPS): the HTTP->HTTPS redirect in frontend/nginx.conf.
# Runs a throwaway nginx:1.27-alpine (the runtime image of frontend/Dockerfile) with the repo's config on a free
# loopback port; "backend" resolves to 127.0.0.1 (nothing listens on 8080, so proxied paths fall back as when the
# backend is down). Prints status + Location (+ HSTS) for each request shape.
set -u
REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
PORT="${PORT:-38431}"
NAME=revchk-w6-nginx
docker rm -f "$NAME" >/dev/null 2>&1 || true
trap 'docker rm -f "$NAME" >/dev/null 2>&1' EXIT
echo "== nginx -t"
docker run --rm --add-host backend:127.0.0.1 \
  -v "$REPO/frontend/nginx.conf:/etc/nginx/conf.d/default.conf:ro" -v "$REPO/frontend/nginx:/etc/nginx/bds:ro" \
  nginx:1.27-alpine nginx -t 2>&1 | sed 's/^/   /'
docker run -d --name "$NAME" --add-host backend:127.0.0.1 -p "127.0.0.1:$PORT:3000" \
  -v "$REPO/frontend/nginx.conf:/etc/nginx/conf.d/default.conf:ro" -v "$REPO/frontend/nginx:/etc/nginx/bds:ro" \
  nginx:1.27-alpine >/dev/null
for _ in $(seq 1 20); do curl -fsS "http://127.0.0.1:$PORT/healthz" >/dev/null 2>&1 && break; sleep 0.5; done

probe() { # probe <label> <method> <path> [curl header args...]
  local label="$1" method="$2" path="$3"; shift 3
  local out
  out="$(curl -sS -o /dev/null -D - -X "$method" -H 'Host: nhadatchuan.online' "$@" "http://127.0.0.1:$PORT$path" \
    | tr -d '\r' | awk 'NR==1{s=$2} tolower($1)=="location:"{l=$2} tolower($1)=="strict-transport-security:"{h="HSTS"} END{print s, (l?l:"-"), (h?h:"")}')"
  printf '   %-62s %s\n' "$label" "$out"
}
V_HTTP='CF-Visitor: {"scheme":"http"}'
V_HTTPS='CF-Visitor: {"scheme":"https"}'
echo "== request shape -> status location [HSTS]"
probe "no CF-Visitor (docker healthcheck, host curl) /healthz"     GET /healthz
probe "no CF-Visitor /"                                            GET /
probe "edge https visitor /"                                        GET / -H "$V_HTTPS" -H 'X-Forwarded-Proto: https'
probe "edge http visitor /"                                         GET / -H "$V_HTTP" -H 'X-Forwarded-Proto: http'
probe "edge http visitor /tin-dang/x?utm=1&a=b"                     GET '/tin-dang/x?utm=1&a=b' -H "$V_HTTP"
probe "edge http visitor /healthz"                                  GET /healthz -H "$V_HTTP"
probe "edge http visitor /api/v1/notifications/stream (SSE)"        GET /api/v1/notifications/stream -H "$V_HTTP"
probe "edge http visitor POST /api/v1/auth/login"                   POST /api/v1/auth/login -H "$V_HTTP"
probe "CF-Visitor http + X-Forwarded-Proto https (spoof mix)"       GET / -H "$V_HTTP" -H 'X-Forwarded-Proto: https'
probe "CF-Visitor with space {\"scheme\": \"http\"}"                 GET / -H 'CF-Visitor: {"scheme": "http"}'
probe "two CF-Visitor lines (https, http)"                          GET / -H "$V_HTTPS" -H "$V_HTTP"
probe "edge http visitor, Host: evil.example"                       GET / -H "$V_HTTP" -H 'Host: evil.example'
probe "edge http visitor, no Host (HTTP/1.0)"                       GET / -H "$V_HTTP" -H 'Host:' --http1.0
