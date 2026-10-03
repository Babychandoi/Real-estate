#!/usr/bin/env bash
# Checks what crawlers get from the prerender layer (audit F16, stream S7): plain HTTP, no JavaScript.
#
#   scripts/verify-prerender.sh <baseUrl>        e.g. https://nhadatchuan.online or http://127.0.0.1:5321
#
# For the home page, a listing, a project, an area and an article (taken from the live sitemap) it checks status,
# <title>, canonical, JSON-LD type and server-rendered main content; then 301 for a trailing slash, 404 for unknown
# pages and listings, noindex for filtered searches and account pages, robots.txt and the sitemap index.
# Optional: VERIFY_GONE_PATH=/tin-tuc/<unpublished-slug> expects 410; VERIFY_PACE_SECONDS=0.6 pauses before every
# request (stay under 2 req/s against production).
# Exit status: 0 all checks passed, 1 at least one failed, 2 usage or connection error.
set -uo pipefail

BASE_URL="${1:-}"
[ -n "$BASE_URL" ] || { sed -n '2,12p' "$0"; exit 2; }
BASE_URL="${BASE_URL%/}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
CHECKS=0
FAILURES=0

pass() { CHECKS=$((CHECKS + 1)); printf '  ok    %-28s %s\n' "$1" "$2"; }
fail() { CHECKS=$((CHECKS + 1)); FAILURES=$((FAILURES + 1)); printf '  FAIL  %-28s %s\n' "$1" "$2"; }

# fetch <name> <path>: body in $WORK/<name>.body, headers in $WORK/<name>.headers, status in STATUS
pace() { [ "${VERIFY_PACE_SECONDS:-0}" = 0 ] || sleep "$VERIFY_PACE_SECONDS"; }
fetch() {
  pace
  STATUS="$(curl -sS -o "$WORK/$1.body" -D "$WORK/$1.headers" -w '%{http_code}' --max-time 20 \
    -H 'Accept: text/html' -A 'verify-prerender/1.0' "$BASE_URL$2")" || { echo "cannot reach $BASE_URL$2" >&2; exit 2; }
}
header() { grep -i "^$2:" "$WORK/$1.headers" | tail -1 | cut -d: -f2- | tr -d '\r' | sed 's/^ *//'; }
title() { tr '\n' ' ' <"$WORK/$1.body" | grep -o '<title[^>]*>[^<]*</title>' | head -1 | sed 's/<[^>]*>//g'; }
canonical() { grep -o '<link rel="canonical" href="[^"]*"' "$WORK/$1.body" | head -1 | sed 's/.*href="//;s/"$//'; }
robots() { grep -o '<meta name="robots" content="[^"]*"' "$WORK/$1.body" | head -1 | sed 's/.*content="//;s/"$//'; }
jsonld_has() { grep -o '<script type="application/ld+json"[^>]*>[^<]*' "$WORK/$1.body" | grep -q "\"@type\":\"$2\""; }
main_has() { tr '\n' ' ' <"$WORK/$1.body" | grep -o '<div data-prerender-content>.*' | grep -q "$2"; }

expect_status() { if [ "$2" = "$3" ]; then pass "$1" "status $2"; else fail "$1" "status $2, expected $3"; fi; }

# page <name> <path> <jsonLdType>: a public page with complete metadata
page() {
  fetch "$1" "$2"
  expect_status "$1" "$STATUS" 200
  local t c
  t="$(title "$1")"
  c="$(canonical "$1")"
  if [ -n "$t" ] && { [ "$2" = / ] || [ "$t" != "Nhà Đất Chuẩn — Mua bán, cho thuê nhà đất" ]; }; then
    pass "$1" "title: $t"
  else
    fail "$1" "generic or missing title: '$t'"
  fi
  if [ -n "$c" ] && [ "${c#"$BASE_URL"}" != "$c" ]; then pass "$1" "canonical $c"; else fail "$1" "canonical '$c' is not on $BASE_URL"; fi
  case "$(robots "$1")" in index*) pass "$1" "robots index" ;; *) fail "$1" "robots '$(robots "$1")'" ;; esac
  if jsonld_has "$1" "$3"; then pass "$1" "JSON-LD $3"; else fail "$1" "no JSON-LD $3"; fi
  if main_has "$1" '<h1>'; then pass "$1" "main content has <h1>"; else fail "$1" "no server-rendered <h1>"; fi
  if grep -q '<script type="module"' "$WORK/$1.body"; then pass "$1" "SPA bundle still referenced"; else fail "$1" "SPA script missing"; fi
}

# first <loc> of a sitemap part, as a path
first_loc() {
  pace
  curl -sS --max-time 20 "$BASE_URL/sitemaps/$1.xml" | grep -o '<loc>[^<]*</loc>' | head -1 | sed 's/<[^>]*>//g;s#^'"$BASE_URL"'##'
}

echo "Verifying prerendered pages on $BASE_URL"

page home / WebSite
if main_has home '<form action="/search" method="get"'; then pass home "search form works without JS"; else fail home "no plain search form"; fi

fetch robots /robots.txt
expect_status robots "$STATUS" 200
if grep -q "^Sitemap: $BASE_URL/sitemap.xml" "$WORK/robots.body"; then pass robots "points at the sitemap index"; else fail robots "no Sitemap line for $BASE_URL"; fi

fetch sitemap /sitemap.xml
expect_status sitemap "$STATUS" 200
if grep -q '<sitemapindex' "$WORK/sitemap.body" && grep -q '/sitemaps/listings-0.xml' "$WORK/sitemap.body"; then
  pass sitemap "index with listing parts"
else
  fail sitemap "not a sitemap index with listing parts"
fi

listing="$(first_loc listings-0)"
if [ -n "$listing" ]; then
  page listing "$listing" Product
  fetch listing-slash "$listing/"
  expect_status listing-slash "$STATUS" 301
  loc="$(header listing-slash Location)"
  if [ "${loc%"$listing"}" != "$loc" ]; then pass listing-slash "Location $loc"; else fail listing-slash "Location '$loc'"; fi
else
  fail listing "sitemap has no listing"
fi
project="$(first_loc projects)"
if [ -n "$project" ]; then page project "$project" Place; else echo "  skip  project                      (no public project)"; fi
area="$(first_loc areas)"
if [ -n "$area" ]; then page area "$area" Place; else echo "  skip  area                         (no area with listings)"; fi
article="$(first_loc articles)"
if [ -n "$article" ]; then page article "$article" Article; else echo "  skip  article                      (no published article)"; fi

fetch missing "/khong-co-trang-nay-$$"
expect_status missing "$STATUS" 404
case "$(robots missing)" in noindex*) pass missing "noindex" ;; *) fail missing "robots '$(robots missing)'" ;; esac
fetch missing-listing "/listings/khong-ton-tai-$$"
expect_status missing-listing "$STATUS" 404

fetch filtered "/search?purpose=SALE&priceMin=1000000000&district=005"
expect_status filtered "$STATUS" 200
if [ "$(robots filtered)" = "noindex,follow" ]; then pass filtered "filter combination is noindex,follow"; else fail filtered "robots '$(robots filtered)'"; fi

fetch account /my-listings
expect_status account "$STATUS" 200
case "$(header account X-Robots-Tag)" in noindex*) pass account "X-Robots-Tag noindex" ;; *) fail account "X-Robots-Tag '$(header account X-Robots-Tag)'" ;; esac

if [ -n "${VERIFY_GONE_PATH:-}" ]; then
  fetch gone "$VERIFY_GONE_PATH"
  expect_status gone "$STATUS" 410
fi

echo "----"
echo "$CHECKS checks, $FAILURES failed"
[ "$FAILURES" -eq 0 ]
