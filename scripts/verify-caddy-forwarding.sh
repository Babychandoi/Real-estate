#!/usr/bin/env bash
# Automated regression test for infra/production/Caddyfile's client-address forwarding (audit F13.1 follow-up,
# Review 2 MAJOR finding on infra/production/Caddyfile + ClientIpResolver).
#
# The production overlay (infra/compose.production-overlay.yaml) puts Caddy in front of Nginx/backend, both of which
# treat the Compose network as a trusted proxy chain (ClientIpResolver's default trusted-proxies list). That is only
# safe if Caddy makes it impossible for a client to inject a value into X-Real-IP, X-Forwarded-For or
# CF-Connecting-IP that survives to the upstream. This script proves it against a REAL Caddy container running the
# actual Caddyfile, not just by reading the config: it starts an isolated network, a stub HTTP server that plays
# both "backend" and "frontend" and echoes the headers it received, runs infra/production/Caddyfile unmodified
# (PUBLIC_HOST=:80 so Caddy skips automatic HTTPS/ACME and just serves plain HTTP), sends forged headers through
# it, and asserts the stub never sees the forged values.
#
# Usage: scripts/verify-caddy-forwarding.sh
# Requires: docker. Uses only bds-s5-caddy-fwd-* container/network names; everything is removed on exit, including
# on failure. Exit status: 0 all checks passed, 1 a check failed, 2 usage/environment error.
set -uo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd -P)"
NET=bds-s5-caddy-fwd-net
STUB=bds-s5-caddy-fwd-stub
CADDY=bds-s5-caddy-fwd-caddy
CLIENT=bds-s5-caddy-fwd-client
CADDYFILE="$REPO/infra/production/Caddyfile"
STUB_IMAGE=python:3-alpine
CLIENT_IMAGE=curlimages/curl:8.10.1
CADDY_IMAGE=caddy:2.8.4-alpine # pin matches infra/compose.production-overlay.yaml's ingress image

WORK="$(mktemp -d "${TMPDIR:-/tmp}/verify-caddy-forwarding.XXXXXX")"
FAILURES=0
CHECKS=0
pass() { CHECKS=$((CHECKS + 1)); printf 'PASS  %s\n' "$1"; }
fail() { CHECKS=$((CHECKS + 1)); FAILURES=$((FAILURES + 1)); printf 'FAIL  %s\n' "$1"; }

cleanup() {
  docker rm -f "$CLIENT" "$CADDY" "$STUB" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

command -v docker >/dev/null || { echo "docker is required" >&2; exit 2; }
[ -f "$CADDYFILE" ] || { echo "not found: $CADDYFILE" >&2; exit 2; }
for existing in "$STUB" "$CADDY" "$CLIENT"; do
  [ -z "$(docker ps -aq -f name="^${existing}\$")" ] || { echo "container $existing already exists" >&2; exit 2; }
done

cat > "$WORK/echo_server.py" <<'PY'
# Echoes the request headers it received, as JSON, on every port it is told to listen on.
import json
import sys
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps(dict(self.headers.items())).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, format, *args):
        pass

ports = [int(p) for p in sys.argv[1:]] or [8080]
servers = [HTTPServer(("0.0.0.0", p), Handler) for p in ports]
for s in servers[1:]:
    threading.Thread(target=s.serve_forever, daemon=True).start()
servers[0].serve_forever()
PY

echo "starting isolated network and stub (aliased as both backend:8080 and frontend:3000)"
docker network create "$NET" >/dev/null
docker run -d --name "$STUB" --network "$NET" --network-alias backend --network-alias frontend --memory 64m \
  -v "$WORK/echo_server.py:/echo_server.py:ro" "$STUB_IMAGE" python3 /echo_server.py 8080 3000 >/dev/null

echo "starting Caddy with the real infra/production/Caddyfile (PUBLIC_HOST=:80, no ACME)"
docker run -d --name "$CADDY" --network "$NET" --memory 96m \
  -e PUBLIC_HOST=":80" -v "$CADDYFILE:/etc/caddy/Caddyfile:ro" "$CADDY_IMAGE" >/dev/null
for _ in $(seq 1 30); do
  docker exec "$CADDY" wget -qO- http://127.0.0.1/api/v1/x >/dev/null 2>&1 && break
  sleep 1
done

# All requests below run from a single, reused client container: comparing "the same real peer" across requests is
# only meaningful when it is genuinely the same TCP source, not an artifact of Docker handing two different ephemeral
# containers two different IPs. --network-alias none needed; the container just needs one stable identity.
docker create --name "$CLIENT" --network "$NET" --memory 32m --entrypoint sh "$CLIENT_IMAGE" -c 'sleep 300' >/dev/null
docker start "$CLIENT" >/dev/null

request() { # request <path> <extra curl args...> -> JSON body the stub received, or "" on failure
  local path="$1"
  shift
  docker exec "$CLIENT" curl -s -f "$@" "http://$CADDY$path" 2>/dev/null || true
}
field() { python3 -c "import json,sys; print(json.loads(sys.argv[1]).get(sys.argv[2], ''))" "$1" "$2" 2>/dev/null; }

echo "== forged headers on an API path (Caddy's backend:8080 handle)"
BODY="$(request /api/v1/listings/search \
  -H 'X-Forwarded-For: 1.2.3.4, 5.6.7.8, 9.9.9.9' -H 'X-Real-IP: 6.6.6.6' -H 'CF-Connecting-IP: 7.7.7.7')"
[ -n "$BODY" ] || { echo "no response from stub via Caddy (api path)" >&2; exit 1; }
peer="$(field "$BODY" X-Forwarded-For)" # the value Caddy actually forwarded, used as this run's expected peer
[[ "$peer" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "api: X-Forwarded-For is not a plain IPv4 peer address: '$peer'"
[[ "$peer" != *,* ]] && pass "api: X-Forwarded-For has no comma (forged hops were not appended)" \
  || fail "api: X-Forwarded-For contains multiple hops: '$peer'"
[[ "$peer" != *1.2.3.4* && "$peer" != *5.6.7.8* && "$peer" != *9.9.9.9* ]] && pass "api: forged X-Forwarded-For values do not appear" \
  || fail "api: a forged X-Forwarded-For value survived: '$peer'"
[ "$(field "$BODY" X-Real-Ip)" = "$peer" ] && pass "api: X-Real-IP equals the real peer, not the forged 6.6.6.6" \
  || fail "api: X-Real-IP is '$(field "$BODY" X-Real-Ip)', expected '$peer'"
[ -z "$(field "$BODY" Cf-Connecting-Ip)" ] && pass "api: CF-Connecting-IP was stripped" \
  || fail "api: CF-Connecting-IP survived: '$(field "$BODY" Cf-Connecting-Ip)'"

echo "== forged headers on a non-API path (Caddy's frontend:3000 handle)"
BODY2="$(request / -H 'X-Forwarded-For: 1.2.3.4' -H 'X-Real-IP: 6.6.6.6' -H 'CF-Connecting-IP: 7.7.7.7')"
[ -n "$BODY2" ] || { echo "no response from stub via Caddy (default path)" >&2; exit 1; }
peer2="$(field "$BODY2" X-Forwarded-For)"
[ "$peer2" = "$peer" ] && pass "default path: same real peer as the api path" \
  || fail "default path: peer '$peer2' differs from api path's '$peer' (both requests came from the same client)"
[[ "$peer2" != *1.2.3.4* ]] && pass "default path: forged X-Forwarded-For does not appear" \
  || fail "default path: forged X-Forwarded-For survived: '$peer2'"
[ "$(field "$BODY2" X-Real-Ip)" = "$peer2" ] && pass "default path: X-Real-IP equals the real peer" \
  || fail "default path: X-Real-IP is '$(field "$BODY2" X-Real-Ip)', expected '$peer2'"
[ -z "$(field "$BODY2" Cf-Connecting-Ip)" ] && pass "default path: CF-Connecting-IP was stripped" \
  || fail "default path: CF-Connecting-IP survived: '$(field "$BODY2" Cf-Connecting-Ip)'"

echo "== a client sending no address headers at all still gets a real peer forwarded"
BODY3="$(request /api/v1/x)"
[ -n "$BODY3" ] || { echo "no response from stub via Caddy (no headers)" >&2; exit 1; }
[ "$(field "$BODY3" X-Forwarded-For)" = "$peer" ] && pass "no forged headers: still forwards the real peer" \
  || fail "no forged headers: X-Forwarded-For is '$(field "$BODY3" X-Forwarded-For)', expected '$peer'"

echo "----"
echo "$CHECKS checks, $FAILURES failed"
[ "$FAILURES" -eq 0 ]
