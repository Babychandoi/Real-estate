#!/usr/bin/env bash
set -euo pipefail

# This runner provisions its own project/database; it cannot attach to an existing demo or production stack.
[[ "${GITHUB_ACTIONS:-}" == true && "${BDS_PERF_ISOLATED:-}" == 1 ]] || {
  echo 'Requires GitHub Actions and BDS_PERF_ISOLATED=1.' >&2; exit 2;
}
[[ "${GITHUB_RUN_ID:-}" =~ ^[0-9]+$ && "${GITHUB_RUN_ATTEMPT:-}" =~ ^[0-9]+$ ]] || exit 2
# steady: soak (constant arrival, publication lag) + 3x burst + SQL statements per request type.
# faults: warm baseline, search-cache eviction, full cold restart, ES outage (transition, then steady degraded) and
#         recovery, Redis outage and recovery.
suite="${SUITE:-steady}"
[[ "$suite" == steady || "$suite" == faults ]] || exit 2
listings="${PERF_LISTINGS:-100000}"
[[ "$listings" =~ ^[0-9]+$ && "$listings" -ge 1000 ]] || exit 2
owners=$(( listings / 20 ))
soak="${PERF_SOAK:-10m}"
[[ "$soak" =~ ^[0-9]{1,2}m$ ]] || exit 2
publish_every="${PERF_PUBLISH_EVERY_S:-2}"
[[ "$publish_every" =~ ^[0-9]+$ ]] || exit 2
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
output_dir="$repo_dir/.artifacts/mixed-load/$suite"
mkdir -p "$output_dir"
work_dir="$(mktemp -d)"
project="bds-perf-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}-${suite}"
database="bds_perf_ci_${GITHUB_RUN_ID}_${GITHUB_RUN_ATTEMPT}"
export POSTGRES_DB="$database" APP_SECURITY_MFA_REQUIRED=false
# Every load client arrives through one CI IP. Rate limiting stays ON; the multiplier lets one IP carry the 3x burst
# (search-v2 300/min/IP x 100 = 500 reads/s). Every 429 still counts as a failed request.
export RATE_LIMIT_LIMIT_MULTIPLIER=100
compose=(docker compose --project-name "$project" --env-file .env.demo.example -f docker-compose.yml -f infra/perf/compose.perf.yaml)
[[ -z "$("${compose[@]}" ps -q)" ]] || { echo 'Refusing an existing project.' >&2; exit 2; }
sampler_pid=
cleanup() {
  [[ -z "$sampler_pid" ]] || kill "$sampler_pid" > /dev/null 2>&1 || true
  "${compose[@]}" logs --no-color --tail 400 backend > "$output_dir/backend-tail.log" 2>&1 || true
  "${compose[@]}" down -v --remove-orphans > /dev/null 2>&1 || true
  rm -rf "$work_dir"
}
trap cleanup EXIT
psql_db() {
  "${compose[@]}" exec -T postgres sh -ec 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"' sh "$@"
}
{
  printf '# Isolated load evidence: suite %s\n\n- Commit: %s\n- Run: %s/%s\n' "$suite" "${GITHUB_SHA:?}" "$GITHUB_RUN_ID" "$GITHUB_RUN_ATTEMPT"
  printf -- '- Runner: %s (%s), %s vCPU %s, %s MB RAM; the load generator (k6) shares the runner with the whole stack\n' \
    "${RUNNER_NAME:-?}" "${ImageOS:-?}" "$(nproc)" "$(lscpu | sed -n 's/^Model name: *//p' | head -1)" \
    "$(free -m | awk '/^Mem:/ {print $2}')"
  printf -- '- Project/database: %s / %s (fresh, Flyway-migrated by the backend)\n' "$project" "$database"
  printf -- '- Dataset: %s public listings + %s non-public, %s sellers (infra/perf/seed-listings.sql) + UAT demo data\n' \
    "$listings" "$(( listings / 5 ))" "$owners"
  echo '- Arrival: k6 constant-arrival-rate 100 reads/s + 10 draft creates/s (burst: ramping to 3x for 60 s)'
  echo '- Read mix: 25 % cached first page, 25 % filtered search, 10 % keyword, 10 % map, 25 % detail, 5 % seller page'
  echo '- Security: rate limiting ON, multiplier 100 for the single shared CI IP; every 429 counts as a failure'
  echo '- Stack: docker-compose.yml with .env.demo.example (APP_MODE=demo), PostgreSQL defaults + pg_stat_statements'
} > "$output_dir/report.md"
{
  uname -a; lscpu; free -m; df -h .; docker version
} > "$output_dir/environment.txt"

"${compose[@]}" build backend frontend > "$output_dir/build.txt" 2>&1
"${compose[@]}" up -d --wait > "$output_dir/startup.txt" 2>&1
password="$(sed -n 's/^DEMO_ACCOUNT_PASSWORD=//p' .env.demo.example)"
"${compose[@]}" run --rm --no-deps backend \
  --app.uat-seed.mode=seed \
  --app.uat-seed.accounts=demo.broker@bds.local,demo.user@bds.local,demo.moderator@bds.local,demo.admin@bds.local \
  --app.uat-seed.kyc-verified-accounts=demo.broker@bds.local,demo.user@bds.local \
  --app.uat-seed.password="$password" --server.port=18080 > "$output_dir/seed.txt" 2>&1

# Fixture: production-shaped listings written straight into the migrated schema, then the real index rebuild.
t0=$(date +%s)
psql_db -q -v start=1 -v finish="$listings" -v owners="$owners" < infra/perf/seed-listings.sql > "$output_dir/fixture.txt"
psql_db -q -c 'VACUUM (ANALYZE) users, user_roles, listings, listing_revisions, listing_public_read, leads;'
psql_db -q -c 'CREATE EXTENSION IF NOT EXISTS pg_stat_statements;'
# Quota for the publication-lag flow (one listing every few seconds); the fixture broker only.
psql_db -q -c "UPDATE users SET listing_quota_remaining = 100000, plan_expires_at = now() + interval '30 days'
               WHERE email = 'demo.broker@bds.local';"
echo "- Fixture load: $(( $(date +%s) - t0 )) s" >> "$output_dir/report.md"
"${compose[@]}" restart backend frontend > /dev/null
"${compose[@]}" up -d --wait > /dev/null
base_url=http://127.0.0.1:3000

# Session material stays in a private temporary directory and is deleted on exit; never upload these files.
export PERF_LOGIN_PASSWORD="$password" PERF_LOGIN_URL="$base_url"
python3 - "$work_dir/k6.env" "$work_dir/admin.token" <<'PY'
import json, os, secrets, sys, urllib.request

def login(path, email):
    payload = json.dumps({'email': email, 'password': os.environ['PERF_LOGIN_PASSWORD']}).encode()
    request = urllib.request.Request(os.environ['PERF_LOGIN_URL'] + path, data=payload,
                                     headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=30) as response:
        token = json.load(response)['accessToken']
    if not isinstance(token, str) or not token or '\n' in token or '\r' in token:
        raise ValueError('Invalid fixture token')
    return token

broker = login('/api/v1/auth/login', 'demo.broker@bds.local')
moderator = login('/api/v1/auth/admin/login', 'demo.moderator@bds.local')
admin = login('/api/v1/auth/admin/login', 'demo.admin@bds.local')
with open(sys.argv[1], 'w') as target:
    target.write('PERF_ACTOR_TOKENS_JSON=' + json.dumps([broker]) + '\n')
    target.write('PERF_MODERATOR_TOKEN=' + moderator + '\n')
    target.write('PERF_RUN_ID=' + ''.join(secrets.choice('abcdefghijklmnopqrstuvwxyz') for _ in range(20)) + '\n')
with open(sys.argv[2], 'w') as target:
    target.write(admin)
os.chmod(sys.argv[1], 0o600)
os.chmod(sys.argv[2], 0o600)
PY
unset PERF_LOGIN_PASSWORD password

# D-13 "rebuild index": full rebuild of the seeded read model through the admin API, timed until the alias swap.
python3 - "$base_url" "$work_dir/admin.token" "$listings" >> "$output_dir/report.md" <<'PY'
import json, sys, time, urllib.request
base, token_file, listings = sys.argv[1], sys.argv[2], int(sys.argv[3])
token = open(token_file).read().strip()

def call(method, path):
    request = urllib.request.Request(base + '/api/v2/admin/search/index' + path, method=method,
                                     headers={'Authorization': 'Bearer ' + token})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)

started = time.monotonic()
call('POST', '/rebuild')
while True:
    status = call('GET', '')
    building = [i for i in status['indices'] if i['role'] == 'BUILDING']
    active = [i for i in status['indices'] if i['role'] == 'ACTIVE']
    if not building and active and active[0]['documents'] >= status['readModelRows'] >= listings:
        break
    if time.monotonic() - started > 1200:
        raise SystemExit('Index rebuild did not finish within 20 minutes')
    time.sleep(2)
elapsed = time.monotonic() - started
call('POST', '/cleanup')  # PREVIOUS -> RETIRED: only the new generation is written during the measurement
print(f"- Index rebuild (admin API, {status['readModelRows']} read-model rows -> {active[0]['documents']} documents): "
      f"{elapsed:.1f} s = {status['readModelRows'] / elapsed:.0f} documents/s")
PY

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

scrape() {
  # Scrape from inside the backend container, without exposing an admin/metrics endpoint publicly.
  "${compose[@]}" exec -T backend curl -fsS --max-time 15 http://localhost:8080/actuator/prometheus
}

breaker_state() {
  scrape | sed -n 's/^bds_search_breaker_state\({[^}]*}\)\{0,1\} \([0-9.]*\)$/\2/p' | head -1
}

wait_breaker_open() {
  # The degraded phase starts only once the Elasticsearch breaker reports OPEN (1); a bbox search bypasses the cache
  # and reaches the engine, re-opening a half-open breaker whose probe fails.
  local state
  for attempt in $(seq 1 60); do
    state="$(breaker_state)"
    [[ "$state" == 1 || "$state" == 1.0 ]] && return 0
    curl -s -o /dev/null --max-time 10 "$base_url/api/v2/listings/search?purpose=SALE&bbox=105.70,20.95,105.90,21.10" || true
    sleep 0.5
  done
  echo "Breaker did not open (last state $state)." >&2
  return 1
}

snapshot_metrics() {
  local target="$1"
  scrape | grep -E '^(# (HELP|TYPE) )?(bds_|hikaricp_|jvm_memory_used_bytes|jvm_gc_pause_seconds|jvm_threads_live|process_cpu_usage|system_cpu_usage|http_server_requests_seconds_(count|sum|max))' \
    > "$target.prometheus"
  psql_db -At -F '|' -c "
    SELECT 'xact_commit', xact_commit FROM pg_stat_database WHERE datname = current_database()
    UNION ALL SELECT 'xact_rollback', xact_rollback FROM pg_stat_database WHERE datname = current_database()
    UNION ALL SELECT 'blks_read', blks_read FROM pg_stat_database WHERE datname = current_database()
    UNION ALL SELECT 'blks_hit', blks_hit FROM pg_stat_database WHERE datname = current_database()
    UNION ALL SELECT 'temp_bytes', temp_bytes FROM pg_stat_database WHERE datname = current_database()
    UNION ALL SELECT 'deadlocks', deadlocks FROM pg_stat_database WHERE datname = current_database()
    UNION ALL SELECT 'statements', coalesce(sum(calls), 0)::bigint FROM pg_stat_statements
              WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database());" > "$target.db.txt"
}

start_sampler() {
  local target="$1"
  echo 'ts,pending,search_index_pending,search_index_oldest_s,oldest_s,dead,outbox' > "$target"
  (
    while true; do
      psql_db -At -F ',' -c "
        SELECT to_char(clock_timestamp(), 'HH24:MI:SS'),
               count(*) FILTER (WHERE completed_at IS NULL AND dead_lettered_at IS NULL),
               count(*) FILTER (WHERE queue = 'search-index' AND completed_at IS NULL AND dead_lettered_at IS NULL),
               coalesce(extract(epoch FROM now() - min(created_at) FILTER (
                   WHERE queue = 'search-index' AND completed_at IS NULL AND dead_lettered_at IS NULL)), 0)::numeric(10, 1),
               coalesce(extract(epoch FROM now() - min(created_at) FILTER (
                   WHERE completed_at IS NULL AND dead_lettered_at IS NULL AND run_at <= now())), 0)::numeric(10, 1),
               count(*) FILTER (WHERE dead_lettered_at IS NOT NULL),
               (SELECT count(*) FROM outbox_events WHERE processed_at IS NULL)
        FROM background_jobs;" >> "$target" 2> /dev/null || true
      sleep 5
    done
  ) &
  sampler_pid=$!
}

stop_sampler() {
  [[ -z "$sampler_pid" ]] || { kill "$sampler_pid" > /dev/null 2>&1 || true; wait "$sampler_pid" 2> /dev/null || true; }
  sampler_pid=
}

top_statements() {
  echo '| calls | total ms | mean ms | max ms | rows | blocks read | statement |'
  echo '|---:|---:|---:|---:|---:|---:|---|'
  psql_db -At -F ' | ' -c "
    SELECT calls, round(total_exec_time::numeric), round(mean_exec_time::numeric, 2), round(max_exec_time::numeric, 1),
           rows, shared_blks_read, replace(left(regexp_replace(query, '\s+', ' ', 'g'), 220), '|', '¦')
    FROM pg_stat_statements WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
      AND query NOT LIKE '%pg_stat_statements%'
    ORDER BY total_exec_time DESC LIMIT 25;" | sed 's/^/| /; s/$/ |/'
}

timeline_bucket() {
  case "$1" in soak) echo 30 ;; transition) echo 2 ;; *) echo 10 ;; esac
}

image=grafana/k6:1.7.0
docker pull "$image" > /dev/null
docker image inspect --format '{{json .RepoDigests}}' "$image" >> "$output_dir/environment.txt"
docker run --rm "$image" version >> "$output_dir/environment.txt"
overall_status=0
# run_phase NAME EXPECTED_DEGRADED(0|1|any) PROFILE DURATION BLOCKING(1|0) [PUBLISH_EVERY_S]
run_phase() {
  local phase="$1" expected="$2" phase_profile="$3" phase_duration="$4" blocking="$5" publish="${6:-0}"
  local phase_dir="$output_dir/$phase" k6_status=0 effect_status=0 evidence_status=0 count marker started
  mkdir -p "$phase_dir"
  # Every phase has its own marker, so matching counts cannot be satisfied by an earlier phase's writes.
  marker="$(python3 -c 'import secrets; print("".join(secrets.choice("abcdefghijklmnopqrstuvwxyz") for _ in range(20)))')"
  sed "s/^PERF_RUN_ID=.*/PERF_RUN_ID=$marker/" "$work_dir/k6.env" > "$work_dir/phase.env"
  chmod 600 "$work_dir/phase.env"
  # Statement statistics per phase: the top statements by database time are reported for this phase only.
  psql_db -q -c 'SELECT pg_stat_statements_reset();' > /dev/null
  snapshot_metrics "$phase_dir/before"
  start_sampler "$phase_dir/backlog.csv"
  started="$(date -u +%H:%M:%S)"
  docker run --rm --network host --user "$(id -u):$(id -g)" \
    --env-file "$work_dir/phase.env" -e BDS_PERF_ISOLATED=1 -e BASE_URL="$base_url" \
    -e EXPECT_DEGRADED="$expected" -e PROFILE="$phase_profile" -e DURATION="$phase_duration" \
    -e READ_MIX=realistic -e LISTINGS="$listings" -e OWNERS="$owners" -e PUBLISH_EVERY_S="$publish" \
    -e PERF_SUMMARY_PATH=/results/summary.json \
    -v "$repo_dir/infra/k6:/scripts:ro" -v "$phase_dir:/results" \
    "$image" run --out csv=/results/samples.csv.gz /scripts/mixed-search-drafts.js > "$phase_dir/k6.txt" 2>&1 || k6_status=$?
  stop_sampler
  snapshot_metrics "$phase_dir/after"
  top_statements > "$phase_dir/statements.md" || true
  printf '\n## Phase: %s\n\n- Started %s UTC; profile/duration: %s/%s; expected degraded: %s; gate: %s\n- k6 exit status: %s\n' \
    "$phase" "$started" "$phase_profile" "$phase_duration" "$expected" \
    "$([[ "$blocking" == 1 ]] && echo 'blocking (k6 thresholds)' || echo 'NON-BLOCKING (reported only)')" "$k6_status" \
    >> "$output_dir/report.md"
  # Still verify durable effects when latency thresholds fail; never relabel a failed phase as PASS.
  if count="$(python3 scripts/perf-summary.py "$phase_dir/summary.json" --count)"; then
    local redis_expected=1 engine_args=()
    [[ "$phase" != redis-unavailable ]] || redis_expected=0
    [[ "$expected" == any ]] || engine_args=(--expected-degraded "$expected")
    python3 scripts/perf-summary.py "$phase_dir/summary.json" "${engine_args[@]}" \
      --redis-state "$redis_expected" --metrics "$phase_dir/after.prometheus" \
      >> "$output_dir/report.md" || evidence_status=$?
    "${compose[@]}" exec -T -e PERF_RUN_ID="$marker" -e PERF_COUNT="$count" postgres sh -ec \
      'psql -X -v ON_ERROR_STOP=1 -v run_id="$PERF_RUN_ID" -v expected_count="$PERF_COUNT" -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
      < infra/perf/verify-draft-writes.sql > "$phase_dir/persisted-effects.txt" 2>&1 || effect_status=$?
  else
    evidence_status=2; effect_status=2
  fi
  python3 scripts/perf-metrics.py "$phase_dir" >> "$output_dir/report.md" || true
  {
    printf '### Top statements by database time (pg_stat_statements, this phase)\n\n'
    head -n 10 "$phase_dir/statements.md"
    printf '\n### Timeline (k6, %s s buckets, latency in ms)\n\n' "$(timeline_bucket "$phase_profile")"
    python3 scripts/k6-timeline.py "$phase_dir/samples.csv.gz" --bucket "$(timeline_bucket "$phase_profile")" \
      || echo '- k6 samples unavailable'
  } >> "$output_dir/report.md"
  rm -f "$phase_dir/samples.csv.gz"
  printf -- '- Evidence parser status: %s\n- Persisted-effect status: %s\n' "$evidence_status" "$effect_status" \
    >> "$output_dir/report.md"
  if [[ "$blocking" == 1 && ( "$k6_status" != 0 || "$effect_status" != 0 || "$evidence_status" != 0 ) ]]; then
    overall_status=1
    echo "- **Phase $phase FAILED its gate**" >> "$output_dir/report.md"
  fi
}

query_counts() {
  local label="$1" types="${2:-}"
  local args=(--psql "${compose[*]} exec -T postgres psql -U bds -d $database" --base-url "$base_url"
              --env-file "$work_dir/k6.env" --listings "$listings" --out "$output_dir/query-counts-$label")
  [[ -z "$types" ]] || args+=(--types "$types")
  printf '\n## SQL statements per request type (%s)\n\n' "$label" >> "$output_dir/report.md"
  python3 scripts/query-counts.py "${args[@]}" >> "$output_dir/report.md" || {
    echo '- **Query-count measurement failed**' >> "$output_dir/report.md"; overall_status=1; }
}

# Warm-up after the rebuild and restarts (JIT, connection pools, caches): reported, never gating. The gated phases
# that follow measure a warm system; the cold-restart phase measures the cold one explicitly.
run_phase warmup 0 steady 1m 0
if [[ "$suite" == steady ]]; then
  # Soak: >= 10 minutes of constant arrival with the publication-lag flow running alongside.
  run_phase soak 0 soak "$soak" 1 "$publish_every"
  run_phase burst 0 burst 100s 1
  query_counts warm
else
  run_phase warm 0 steady 1m 1
  evict_search_cache
  run_phase cache-evicted 0 steady 1m 1

  # Full cold start: new JVM, PostgreSQL shared buffers, kernel page cache, Elasticsearch caches, Redis search keys.
  "${compose[@]}" stop backend frontend > /dev/null
  "${compose[@]}" restart postgres elasticsearch > /dev/null
  evict_search_cache
  sync
  echo 3 | sudo tee /proc/sys/vm/drop_caches > /dev/null
  "${compose[@]}" up -d --wait backend frontend > /dev/null
  wait_search_state 0
  run_phase cold-restart 0 steady 1m 1

  # Elasticsearch outage. The breaker-opening transition is measured on its own and never gates the run; the steady
  # degraded phase starts only after bds_search_breaker_state reports OPEN, with the normal thresholds.
  "${compose[@]}" stop elasticsearch > /dev/null
  evict_search_cache
  run_phase es-transition any transition 20s 0
  wait_breaker_open
  echo "- Breaker state before the degraded phase: $(breaker_state) (1 = open)" >> "$output_dir/report.md"
  run_phase es-unavailable 1 steady 1m 1
  query_counts es-unavailable search-filtered-uncached,search-keyword-uncached
  "${compose[@]}" up -d --wait --no-deps elasticsearch > /dev/null
  wait_search_state 0
  run_phase es-recovered 0 steady 1m 1

  # Obtain the session before stopping Redis: auth credential endpoints deliberately fail closed in an outage.
  "${compose[@]}" stop redis > /dev/null
  wait_search_state 0
  run_phase redis-unavailable 0 steady 1m 1
  "${compose[@]}" up -d --wait --no-deps redis > /dev/null
  wait_search_state 0
  run_phase redis-recovered 0 steady 1m 1
fi
echo "- Overall gate status: $overall_status" >> "$output_dir/report.md"
exit "$overall_status"
