#!/usr/bin/env bash
# Verifies the security and caching headers of the responses a browser finally receives (audit F11.1/F11.2, F10.2).
# Run it against the public domain (through Cloudflare) after every edge/Nginx change, or against a local origin.
#
# Usage: scripts/verify-headers.sh <baseUrl> [--simulate-https]
#   <baseUrl>          https://nhadatchuan.online, http://127.0.0.1:3000, ...
#   --simulate-https   for a plain-HTTP origin: send the headers Cloudflare adds for an HTTPS visitor
#                      (X-Forwarded-Proto, CF-Visitor) and expect HSTS like in production.
# Environment:
#   VERIFY_BEARER_TOKEN  optional session token; the SSE check then expects 200 text/event-stream.
#   VERIFY_API_PATH      public JSON endpoint to probe (default /api/v1/listings/search?page=0&size=1).
# Exit status: 0 all required headers present, 1 at least one check failed, 2 usage or connection error.
# Compatible with the bash 3.2 shipped by macOS.
set -uo pipefail

usage() { sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; }

BASE_URL=""
SIMULATE_HTTPS=0
for arg in "$@"; do
  case "$arg" in
    --simulate-https) SIMULATE_HTTPS=1 ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "unknown option: $arg" >&2; usage >&2; exit 2 ;;
    *) BASE_URL="${arg%/}" ;;
  esac
done
[ -n "$BASE_URL" ] || { usage >&2; exit 2; }
command -v curl >/dev/null || { echo "curl is required" >&2; exit 2; }

API_PATH="${VERIFY_API_PATH:-/api/v1/listings/search?page=0&size=1}"
EXPECT_HSTS=0
case "$BASE_URL" in https://*) EXPECT_HSTS=1 ;; esac
[ "$SIMULATE_HTTPS" -eq 1 ] && EXPECT_HSTS=1

WORK="$(mktemp -d "${TMPDIR:-/tmp}/verify-headers.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

FAILURES=0
WARNINGS=0
CHECKS=0
pass() { CHECKS=$((CHECKS + 1)); printf 'PASS  %-12s %s\n' "$1" "$2"; }
fail() { CHECKS=$((CHECKS + 1)); FAILURES=$((FAILURES + 1)); printf 'FAIL  %-12s %s\n' "$1" "$2"; }
warn() { WARNINGS=$((WARNINGS + 1)); printf 'WARN  %-12s %s\n' "$1" "$2"; }
lower() { tr '[:upper:]' '[:lower:]'; }

# fetch <target> <path> <max-time> [extra curl args...]
# Writes $WORK/<target>.headers and .body and sets STATUS. Runs in the main shell so a connection error ends the script.
STATUS=""
fetch() {
  local target="$1" path="$2" max_time="$3"
  shift 3
  local extra=()
  if [ "$SIMULATE_HTTPS" -eq 1 ]; then
    extra+=(-H 'X-Forwarded-Proto: https' -H 'CF-Visitor: {"scheme":"https"}')
  fi
  curl -sS -o "$WORK/$target.body" -D "$WORK/$target.headers" -w '%{http_code}' --max-time "$max_time" \
    ${extra[@]+"${extra[@]}"} "$@" "$BASE_URL$path" >"$WORK/$target.status" 2>"$WORK/$target.err"
  local rc=$?
  # 28 = timeout: expected for an open event stream once its headers have arrived.
  if [ $rc -ne 0 ] && ! { [ $rc -eq 28 ] && [ -s "$WORK/$target.headers" ]; }; then
    echo "ERROR $target: curl exit $rc for $BASE_URL$path: $(cat "$WORK/$target.err")" >&2
    exit 2
  fi
  STATUS="$(cat "$WORK/$target.status")"
}

# values <target> <header>: one value per line (header names are case-insensitive; HTTP/2 lower-cases them)
values() { grep -i "^$2:" "$WORK/$1.headers" | sed -E 's/^[^:]+:[[:space:]]*//; s/[[:space:]]*$//' | tr -d '\r'; }
value() { values "$1" "$2" | head -n 1; }
count() { grep -ci "^$2:" "$WORK/$1.headers" || true; }

expect_status() {
  local target="$1" actual="$2" expected="$3"
  if [ "$actual" = "$expected" ]; then pass "$target" "status $actual"; else fail "$target" "status $actual (expected $expected)"; fi
}

expect_contains() { # target header needle description
  local target="$1" header="$2" needle="$3" what="${4:-$2 contains $3}"
  if value "$target" "$header" | lower | grep -qF -- "$(printf '%s' "$needle" | lower)"; then pass "$target" "$what"
  else fail "$target" "$what (got: $(value "$target" "$header"))"; fi
}

expect_absent_text() { # target header needle description
  local target="$1" header="$2" needle="$3" what="$4"
  if value "$target" "$header" | lower | grep -qF -- "$(printf '%s' "$needle" | lower)"; then fail "$target" "$what (got: $(value "$target" "$header"))"
  else pass "$target" "$what"; fi
}

security_headers() { # target csp-kind(app|api)
  local target="$1" kind="$2" header n
  for header in Content-Security-Policy X-Content-Type-Options X-Frame-Options Referrer-Policy Permissions-Policy Cross-Origin-Opener-Policy; do
    n="$(count "$target" "$header")"
    if [ "$n" -eq 0 ]; then fail "$target" "$header missing"
    else
      pass "$target" "$header: $(value "$target" "$header" | cut -c1-70)"
      [ "$n" -gt 1 ] && warn "$target" "$header sent $n times (proxy/CDN duplicates it)"
    fi
  done
  expect_contains "$target" X-Content-Type-Options nosniff "X-Content-Type-Options is nosniff"
  expect_contains "$target" X-Frame-Options deny "X-Frame-Options is DENY"
  expect_contains "$target" Content-Security-Policy "frame-ancestors 'none'" "CSP frame-ancestors 'none'"
  if [ "$kind" = app ]; then
    expect_contains "$target" Content-Security-Policy "object-src 'none'" "CSP object-src 'none'"
    expect_contains "$target" Content-Security-Policy "base-uri 'self'" "CSP base-uri 'self'"
    expect_contains "$target" Content-Security-Policy "form-action 'self'" "CSP form-action 'self'"
    expect_absent_text "$target" Content-Security-Policy "fonts.googleapis.com" "CSP has no Google Fonts host"
  else
    expect_contains "$target" Content-Security-Policy "default-src 'none'" "API CSP default-src 'none'"
  fi
  n="$(count "$target" Strict-Transport-Security)"
  if [ "$EXPECT_HSTS" -eq 1 ]; then
    if [ "$n" -eq 0 ]; then fail "$target" "Strict-Transport-Security missing on an HTTPS response"
    else
      pass "$target" "Strict-Transport-Security: $(value "$target" Strict-Transport-Security)"
      [ "$n" -gt 1 ] && warn "$target" "Strict-Transport-Security sent $n times"
    fi
  elif [ "$n" -eq 0 ]; then pass "$target" "no HSTS on a plain-HTTP response"
  else fail "$target" "HSTS sent on a plain-HTTP response"
  fi
}

echo "Verifying $BASE_URL (HSTS expected: $([ "$EXPECT_HSTS" -eq 1 ] && echo yes || echo no))"

# 1. HTML shell
fetch html / 20 -H 'Accept: text/html'
expect_status html "$STATUS" 200
expect_contains html Content-Type text/html "Content-Type is HTML"
security_headers html app
expect_contains html Cache-Control no-store "HTML is not stored (Cache-Control no-store)"

# 2. A hashed build asset referenced by the HTML
asset="$(grep -oE '/assets/[A-Za-z0-9._-]+\.(js|css)' "$WORK/html.body" | head -n 1)"
if [ -z "$asset" ]; then
  fail asset "no /assets/*.js or *.css referenced by the HTML"
else
  fetch asset "$asset" 20
  expect_status asset "$STATUS" 200
  expect_contains asset Cache-Control immutable "hashed asset is immutable"
  expect_contains asset Cache-Control max-age=31536000 "hashed asset cached for one year"
  security_headers asset app
fi

# 3. Public API JSON
fetch api "$API_PATH" 20 -H 'Accept: application/json'
expect_status api "$STATUS" 200
expect_contains api Content-Type json "Content-Type is JSON"
security_headers api api

# 4. Private API answered without a session: 401 Problem Details that no cache may store
fetch private /api/v1/auth/me 20 -H 'Accept: application/json'
expect_status private "$STATUS" 401
expect_contains private Cache-Control no-store "private API response is no-store"
security_headers private api

# 5. Error page: missing asset
fetch notfound "/assets/verify-headers-missing-$$.js" 20
expect_status notfound "$STATUS" 404
expect_absent_text notfound Cache-Control immutable "404 is not cached as immutable"
security_headers notfound app

# 6. Health endpoint
fetch healthz /healthz 20
expect_status healthz "$STATUS" 200
expect_contains healthz Cache-Control no-store "health check is no-store"
security_headers healthz app

# 7. Notification stream (SSE)
if [ -n "${VERIFY_BEARER_TOKEN:-}" ]; then
  fetch sse /api/v1/notifications/stream 4 -H 'Accept: text/event-stream' -H "Authorization: Bearer $VERIFY_BEARER_TOKEN"
  expect_status sse "$STATUS" 200
  expect_contains sse Content-Type text/event-stream "Content-Type is text/event-stream"
else
  fetch sse /api/v1/notifications/stream 20 -H 'Accept: text/event-stream'
  expect_status sse "$STATUS" 401
fi
expect_contains sse Cache-Control no-store "event stream is no-store"
security_headers sse api

# 8. Prerendered pages (S7): an unknown page is a real 404 HTML page, robots.txt and the sitemap index come from the
#    backend through Nginx, all with the same security headers.
fetch missingpage "/verify-headers-missing-page-$$" 20 -H 'Accept: text/html'
expect_status missingpage "$STATUS" 404
expect_contains missingpage Content-Type text/html "404 page is HTML"
expect_contains missingpage X-Robots-Tag noindex "404 page is noindex"
security_headers missingpage app

fetch robots /robots.txt 20
expect_status robots "$STATUS" 200
security_headers robots app

fetch sitemap /sitemap.xml 20
expect_status sitemap "$STATUS" 200
expect_contains sitemap Content-Type xml "sitemap index is XML"
security_headers sitemap app

echo "----"
echo "$CHECKS checks, $FAILURES failed, $WARNINGS warnings"
[ "$FAILURES" -eq 0 ]
