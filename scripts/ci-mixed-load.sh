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
  echo '- Cache: warmed by index readiness; workload repeats the SALE first page.'
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
marker="$(sed -n 's/^PERF_RUN_ID=//p' "$work_dir/k6.env")"
image=grafana/k6:1.7.0
docker pull "$image" > /dev/null
docker image inspect --format '{{json .RepoDigests}}' "$image" >> "$output_dir/environment.txt"
docker run --rm "$image" version >> "$output_dir/environment.txt"
k6_status=0
docker run --rm --network host --user "$(id -u):$(id -g)" \
  --env-file "$work_dir/k6.env" -e BDS_PERF_ISOLATED=1 -e BASE_URL="$base_url" \
  -e PROFILE="$profile" -e DURATION="$duration" -e PERF_SUMMARY_PATH=/results/summary.json \
  -v "$repo_dir/infra/k6:/scripts:ro" -v "$output_dir:/results" \
  "$image" run /scripts/mixed-search-drafts.js \
  > "$output_dir/k6.txt" 2>&1 || k6_status=$?
printf '\n- k6 exit status: %s (0 means every threshold passed).\n' "$k6_status" >> "$output_dir/report.md"

# Check durable effects even when latency/error thresholds fail. A failed generator never becomes a PASS report.
count="$(python3 scripts/perf-summary.py "$output_dir/summary.json" --count)"
python3 scripts/perf-summary.py "$output_dir/summary.json" >> "$output_dir/report.md"
effect_status=0
"${compose[@]}" exec -T -e PERF_RUN_ID="$marker" -e PERF_COUNT="$count" postgres sh -ec \
  'psql -X -v ON_ERROR_STOP=1 -v run_id="$PERF_RUN_ID" -v expected_count="$PERF_COUNT" -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < infra/perf/verify-draft-writes.sql > "$output_dir/persisted-effects.txt" 2>&1 || effect_status=$?
printf '\n- Persisted-effect verification exit status: %s\n' "$effect_status" >> "$output_dir/report.md"
[[ "$k6_status" == 0 && "$effect_status" == 0 ]]
