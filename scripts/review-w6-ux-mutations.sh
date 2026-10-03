#!/usr/bin/env bash
# Review of PR #25 (W6-UX): do the new specs fail when the app is broken? Each mutant is a copy of frontend/ with one
# source change, built and served on its own port against an already running seeded stack; the spec that should
# catch it is run and must FAIL. "KILLED" = the spec failed (good), "SURVIVED" = the spec still passed (blind spot).
#
#   1. start a stack:  E2E_SQL_AFTER_SEED_FILES=$PWD/frontend/tests/e2e/fixtures/search-volume.sql \
#        E2E_BACKEND_PORT=18151 E2E_FRONTEND_PORT=5351 E2E_REDIS_DB=10 E2E_DB_PREFIX=rvux_e2e scripts/e2e-local.sh --serve
#   2. run:            MUT_BACKEND_PORT=18151 MUT_PORT=5352 scripts/review-w6-ux-mutations.sh [mutant ...]
#
# Mutants: dup drop touch price overflow focus (default: all). Needs DEMO_ACCOUNT_PASSWORD like the suites.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_PORT="${MUT_BACKEND_PORT:?set MUT_BACKEND_PORT (the running stack backend)}"
PORT="${MUT_PORT:?set MUT_PORT (a free port for the mutant build)}"
WORK="${MUT_WORK:-$(mktemp -d)}"
MUTANTS="${*:-dup drop touch price overflow focus}"
export DEMO_ACCOUNT_PASSWORD="${DEMO_ACCOUNT_PASSWORD:-$(grep '^DEMO_ACCOUNT_PASSWORD=' "$ROOT/.env.demo.example" | cut -d= -f2-)}"

# name | file (under frontend/) | perl substitution | spec | grep
mutant() {
  case "$1" in
    dup) echo 'app/features/search/useListingSearch.ts|s/items: \[\.\.\.previous\.items, \.\.\.newItems\]/items: [...previous.items, ...newItems, ...newItems.slice(0, 1)]/|tests/e2e/search-consistency.spec.ts|Xem thêm' ;;
    drop) echo 'app/features/search/useListingSearch.ts|s/items: \[\.\.\.previous\.items, \.\.\.newItems\]/items: [...previous.items, ...newItems.slice(1)]/|tests/e2e/search-consistency.spec.ts|Xem thêm' ;;
    touch) echo 'app/styles/tokens.css|s/--control-sm: 2\.75rem;/--control-sm: 2.25rem;/|tests/e2e/ux-audit.spec.ts|^.*my-listings \(broker\)' ;;
    price) echo "app/shared/ui/Money.tsx|s/cn\('tabular-nums', !text/cn('tabular-nums !text-[13px]', !text/|tests/e2e/ux-audit.spec.ts|listing \\(guest\\)" ;;
    overflow) echo 'app/root.tsx|s#<footer className="ndc-footer">#<footer className="ndc-footer"><div style={{ width: 430, height: 4 }} />#|tests/e2e/ux-audit.spec.ts|home \(guest\)' ;;
    focus) echo "app/shared/ui/Button.tsx|s/'focus-visible:outline focus:ring-2 focus:ring-primary\/20 /'focus-visible:outline-none /|tests/e2e/ux-dialogs.spec.ts|public: sign-in dialog" ;;
    *) echo "unknown mutant $1" >&2; return 1 ;;
  esac
}

serve() {
  local dist="$1"
  local pid
  pid=$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null | head -1)
  [ -n "$pid" ] && kill "$pid" && sleep 1
  (cd "$ROOT/frontend" && API_INTERNAL_URL="http://127.0.0.1:$BACKEND_PORT" \
    exec ./node_modules/.bin/vite preview --host 127.0.0.1 --port "$PORT" --strictPort --outDir "$dist") \
    >"$WORK/preview.log" 2>&1 &
  for _ in $(seq 1 60); do curl -fsS "http://127.0.0.1:$PORT/" >/dev/null 2>&1 && return 0; sleep 0.5; done
  echo "preview did not start" >&2
  return 1
}

declare -a RESULTS=()
for name in $MUTANTS; do
  IFS='|' read -r file subst spec grep <<<"$(mutant "$name")"
  src="$WORK/$name"
  rm -rf "$src" && mkdir -p "$src"
  rsync -a --exclude node_modules --exclude dist --exclude test-results --exclude playwright-report "$ROOT/frontend/" "$src/"
  ln -s "$ROOT/frontend/node_modules" "$src/node_modules"
  before=$(shasum "$src/$file" | cut -c1-40)
  perl -0pi -e "$subst" "$src/$file"
  if [ "$before" = "$(shasum "$src/$file" | cut -c1-40)" ]; then
    RESULTS+=("$name: NOT APPLIED (pattern not found in $file)")
    continue
  fi
  echo "[mutant $name] $file changed; building"
  (cd "$src" && VITE_ENABLE_UI_CATALOG=true ./node_modules/.bin/vite build --outDir "$src/dist" >"$WORK/$name-build.log" 2>&1) \
    || { RESULTS+=("$name: BUILD FAILED"); continue; }
  serve "$src/dist" || { RESULTS+=("$name: SERVE FAILED"); continue; }
  if (cd "$ROOT/frontend" && PLAYWRIGHT_BASE_URL="http://127.0.0.1:$PORT" E2E_REQUIRE_UI_CATALOG=1 PLAYWRIGHT_SUITE="mutant-$name" \
    npx playwright test "$spec" --project=chromium-1440 --workers=1 --retries=0 -g "$grep" >"$WORK/$name-test.log" 2>&1); then
    RESULTS+=("$name: SURVIVED ($spec -g '$grep' still passes)")
  else
    RESULTS+=("$name: KILLED ($spec -g '$grep' fails)")
  fi
done
pid=$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null | head -1)
[ -n "$pid" ] && kill "$pid"
printf '\n%s\n' "${RESULTS[@]}"
echo "logs: $WORK"
