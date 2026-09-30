#!/usr/bin/env bash
set -euo pipefail

# This runner provisions its own project/database; it cannot attach to an existing demo or production stack.
[[ "${GITHUB_ACTIONS:-}" == true && "${BDS_PERF_ISOLATED:-}" == 1 ]] || {
  echo 'Requires GitHub Actions and BDS_PERF_ISOLATED=1.' >&2; exit 2;
}
[[ "${GITHUB_RUN_ID:-}" =~ ^[0-9]+$ && "${GITHUB_RUN_ATTEMPT:-}" =~ ^[0-9]+$ ]] || exit 2
profile="${PROFILE:-steady}"
[[ "$profile" == steady || "$profile" == burst || "$profile" == soak ]] || exit 2
duration="${PERF_DURATION:-5m}"
[[ "$duration" == 1m || "$duration" == 5m ]] || exit 2
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
output_dir="$repo_dir/.artifacts/mixed-load"
mkdir -p "$output_dir"
work_dir="$(mktemp -d)"
project="bds-perf-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
database="bds_perf_ci_${GITHUB_RUN_ID}_${GITHUB_RUN_ATTEMPT}"
export POSTGRES_DB="$database" APP_SECURITY_MFA_REQUIRED=false
# All 100/200 read clients arrive through one CI IP. Keep enforcement enabled, report the scaled policy.
export RATE_LIMIT_LIMIT_MULTIPLIER=50
compose=(docker compose --project-name "$project" --env-file .env.demo.example)
[[ -z "$("${compose[@]}" ps -q)" ]] || { echo 'Refusing an existing project.' >&2; exit 2; }
cleanup() {
  "${compose[@]}" down -v --remove-orphans > /dev/null 2>&1 || true
  rm -rf "$work_dir"
}
trap cleanup EXIT
{
  printf '# Isolated mixed-load diagnostic\n\n- Commit: %s\n- Run: %s/%s\n' "${GITHUB_SHA:?}" "$GITHUB_RUN_ID" "$GITHUB_RUN_ATTEMPT"
  printf -- '- Project/database: %s / %s\n- Profile: %s; steady duration: %s\n' "$project" "$database" "$profile" "$duration"
  echo '- Fixture: small deterministic UAT dataset, not a 1M-listing capacity certification.'
  echo '- Phases: warm, search-cache cold start, ES outage/recovery, Redis outage/recovery.'
  echo '- Cache cold start evicts search keys once; this is not cold PostgreSQL/OS cache or continuously uncached traffic.'
  echo '- Security: rate limiting ON, multiplier 50 for the single shared CI IP; all 429 responses count as failures.'
  echo '- Writes: real KYC-verified broker draft creation, not publication/index lag.'
  echo '- Load generator shares the runner with the application. No production SLO/RPO/RTO is inferred.'
} > "$output_dir/report.md"
{
  uname -a
  lscpu
  free -m
  df -h .
  docker version
} > "$output_dir/environment.txt"

"${compose[@]}" build backend frontend > "$output_dir/build.txt" 2>&1
"${compose[@]}" up -d --wait > "$output_dir/startup.txt" 2>&1
password="$(sed -n 's/^DEMO_ACCOUNT_PASSWORD=//p' .env.demo.example)"
"${compose[@]}" run --rm --no-deps backend \
  --app.uat-seed.mode=seed \
  --app.uat-seed.accounts=demo.broker@bds.local,demo.user@bds.local,demo.moderator@bds.local,demo.admin@bds.local \
  --app.uat-seed.kyc-verified-accounts=demo.broker@bds.local,demo.user@bds.local \
  --app.uat-seed.password="$password" --server.port=18080 > "$output_dir/seed.txt" 2>&1
"${compose[@]}" restart backend frontend > /dev/null
"${compose[@]}" up -d --wait > /dev/null
base_url=http://127.0.0.1:3000
ready=0
for attempt in $(seq 1 60); do
  if curl -fsS "$base_url/api/v2/listings/search?purpose=SALE&size=24" > "$work_dir/search.json" && \
    python3 -c 'import json,sys; s=json.load(open(sys.argv[1])); sys.exit(0 if s.get("degraded") is False and s.get("items") else 1)' "$work_dir/search.json"; then
    ready=1; break
  fi
  sleep 3
done
[[ "$ready" == 1 ]] || { echo 'Index did not become ready.' >&2; exit 1; }

evict_search_cache() {
  # Only the throwaway project's public search keys. Preserve auth and rate-limit state.
  "${compose[@]}" exec -T redis sh -ec '
    export REDISCLI_AUTH="$REDIS_PASSWORD"
    redis-cli --scan --pattern "bds:search:v1:*" | while IFS= read -r key; do
      redis-cli UNLINK "$key" > /dev/null
    done
    test -z "$(redis-cli --scan --pattern "bds:search:v1:*")"
  '
}

wait_search_state() {
  local expected="$1"
  for attempt in $(seq 1 60); do
    if curl -fsS --max-time 10 "$base_url/api/v2/listings/search?purpose=SALE&size=24" > "$work_dir/search.json" && \
      python3 -c 'import json,sys; s=json.load(open(sys.argv[1])); sys.exit(0 if s.get("degraded") is (sys.argv[2] == "1") and s.get("items") else 1)' "$work_dir/search.json" "$expected"; then
      return 0
    fi
    sleep 2
  done
  echo "Search did not reach expected degraded=$expected." >&2
  return 1
}

snapshot_metrics() {
  local target="$1"
  # Scrape from inside the backend container, without exposing an admin/metrics endpoint publicly.
  "${compose[@]}" exec -T backend curl -fsS --max-time 15 http://localhost:8080/actuator/prometheus \
    | grep -E '^(# (HELP|TYPE) )?(bds_search_|bds_jobs_|bds_ratelimit_|hikaricp_|jvm_|process_)' \
    > "$target.prometheus"
  "${compose[@]}" exec -T postgres sh -ec \
    'psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT datname, numbackends, xact_commit, xact_rollback, blks_read, blks_hit, tup_returned, tup_fetched, tup_inserted, tup_updated, deadlocks, temp_bytes FROM pg_stat_database WHERE datname=current_database();"' \
    > "$target.postgres.txt"
}

# Session material stays in a private temporary directory and is deleted on exit; never upload this file.
export PERF_LOGIN_PASSWORD="$password" PERF_LOGIN_URL="$base_url"
python3 - "$work_dir/k6.env" <<'PY'
import json, os, secrets, sys, urllib.request
payload = json.dumps({'email': 'demo.broker@bds.local', 'password': os.environ['PERF_LOGIN_PASSWORD']}).encode()
request = urllib.request.Request(os.environ['PERF_LOGIN_URL'] + '/api/v1/auth/login', data=payload,
                                 headers={'Content-Type': 'application/json'})
with urllib.request.urlopen(request, timeout=20) as response:
    token = json.load(response)['accessToken']
if not isinstance(token, str) or not token or '\n' in token or '\r' in token:
    raise ValueError('Invalid fixture token')
with open(sys.argv[1], 'w') as target:
    target.write('PERF_ACTOR_TOKENS_JSON=' + json.dumps([token]) + '\n')
    target.write('PERF_RUN_ID=' + ''.join(secrets.choice('abcdefghijklmnopqrstuvwxyz') for _ in range(20)) + '\n')
os.chmod(sys.argv[1], 0o600)
PY
unset PERF_LOGIN_PASSWORD password
image=grafana/k6:1.7.0
docker pull "$image" > /dev/null
docker image inspect --format '{{json .RepoDigests}}' "$image" >> "$output_dir/environment.txt"
docker run --rm "$image" version >> "$output_dir/environment.txt"
overall_status=0
run_phase() {
  local phase="$1" expected="$2" phase_profile="$3" phase_duration="$4"
  local phase_dir="$output_dir/$phase" k6_status=0 effect_status=0 evidence_status=0 count marker
  mkdir -p "$phase_dir"
  # Every phase has its own marker, so matching counts cannot be satisfied by an earlier phase's writes.
  marker="$(python3 -c 'import secrets; print("".join(secrets.choice("abcdefghijklmnopqrstuvwxyz") for _ in range(20)))')"
  sed "s/^PERF_RUN_ID=.*/PERF_RUN_ID=$marker/" "$work_dir/k6.env" > "$work_dir/phase.env"
  chmod 600 "$work_dir/phase.env"
  snapshot_metrics "$phase_dir/before"
  docker run --rm --network host --user "$(id -u):$(id -g)" \
    --env-file "$work_dir/phase.env" -e BDS_PERF_ISOLATED=1 -e BASE_URL="$base_url" \
    -e EXPECT_DEGRADED="$expected" -e PROFILE="$phase_profile" -e DURATION="$phase_duration" \
    -e PERF_SUMMARY_PATH=/results/summary.json \
    -v "$repo_dir/infra/k6:/scripts:ro" -v "$phase_dir:/results" \
    "$image" run /scripts/mixed-search-drafts.js > "$phase_dir/k6.txt" 2>&1 || k6_status=$?
  snapshot_metrics "$phase_dir/after"
  printf '\n## Phase: %s\n\n- Profile/duration: %s/%s\n- Expected degraded: %s\n- k6 exit status: %s\n' \
    "$phase" "$phase_profile" "$phase_duration" "$expected" "$k6_status" >> "$output_dir/report.md"
  # Still verify durable effects when latency thresholds fail; never relabel a failed phase as PASS.
  if count="$(python3 scripts/perf-summary.py "$phase_dir/summary.json" --count)"; then
    local redis_expected=1
    [[ "$phase" != redis-unavailable ]] || redis_expected=0
    python3 scripts/perf-summary.py "$phase_dir/summary.json" --expected-degraded "$expected" \
      --redis-state "$redis_expected" --metrics "$phase_dir/after.prometheus" \
      >> "$output_dir/report.md" || evidence_status=$?
    "${compose[@]}" exec -T -e PERF_RUN_ID="$marker" -e PERF_COUNT="$count" postgres sh -ec \
      'psql -X -v ON_ERROR_STOP=1 -v run_id="$PERF_RUN_ID" -v expected_count="$PERF_COUNT" -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
      < infra/perf/verify-draft-writes.sql > "$phase_dir/persisted-effects.txt" 2>&1 || effect_status=$?
  else
    evidence_status=2; effect_status=2
  fi
  printf '\n- Evidence parser status: %s\n- Persisted-effect status: %s\n' \
    "$evidence_status" "$effect_status" >> "$output_dir/report.md"
  if [[ "$k6_status" != 0 || "$effect_status" != 0 || "$evidence_status" != 0 ]]; then overall_status=1; fi
}

# Only the baseline follows the selected profile. Fault/recovery probes remain one minute each even for soak.
[[ "$profile" != soak ]] || duration=30m
run_phase warm 0 "$profile" "$duration"
evict_search_cache
run_phase cache-cold-start 0 steady 1m

"${compose[@]}" stop elasticsearch > /dev/null
evict_search_cache
wait_search_state 1
run_phase es-unavailable 1 steady 1m
"${compose[@]}" up -d --wait --no-deps elasticsearch > /dev/null
wait_search_state 0
run_phase es-recovered 0 steady 1m

# Obtain the session before stopping Redis: auth credential endpoints deliberately fail closed in an outage.
"${compose[@]}" stop redis > /dev/null
wait_search_state 0
run_phase redis-unavailable 0 steady 1m
"${compose[@]}" up -d --wait --no-deps redis > /dev/null
wait_search_state 0
run_phase redis-recovered 0 steady 1m
exit "$overall_status"
