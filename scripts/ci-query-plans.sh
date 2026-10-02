#!/usr/bin/env bash
set -euo pipefail

# W6-PERF F09.3/D-05: EXPLAIN (ANALYZE, BUFFERS) of the application's query shapes at 100k and 1M public listings, warm
# and cold, on a fresh PostgreSQL/PostGIS container (same image and default settings as docker-compose.yml) with the
# real Flyway migrations. "before" = schema through BASE_VERSION (main), "after" = every migration in this tree.
# Only a disposable GitHub-hosted runner is an authorised target (it drops the OS page cache with sudo).
[[ "${GITHUB_ACTIONS:-}" == true && "${BDS_PERF_ISOLATED:-}" == 1 ]] || {
  echo 'Requires GitHub Actions and BDS_PERF_ISOLATED=1.' >&2; exit 2;
}
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
base_version="${BASE_VERSION:-95}"
small="${PLANS_SMALL:-100000}"
large="${PLANS_LARGE:-1000000}"
owners="${PLANS_OWNERS:-50000}"
modes="${PLANS_MODES:-warm,cold}"
[[ "$base_version" =~ ^[0-9]+$ && "$small" =~ ^[0-9]+$ && "$large" =~ ^[0-9]+$ && "$owners" =~ ^[0-9]+$ ]] || exit 2
out="$repo_dir/.artifacts/query-plans"
mkdir -p "$out"
work_dir="$(mktemp -d)"
pg="bds-plans-${GITHUB_RUN_ID:?}-${GITHUB_RUN_ATTEMPT:?}"
db=bds_perf_plans
password="$(python3 -c 'import secrets; print(secrets.token_hex(16))')"
port=55432
image=postgis/postgis:16-3.4
flyway_image=flyway/flyway:11.20.3-alpine
cleanup() { docker rm -f "$pg" > /dev/null 2>&1 || true; rm -rf "$work_dir"; }
trap cleanup EXIT

wait_ready() {
  for _ in $(seq 1 90); do
    if docker exec "$pg" psql -X -U bds -d "$db" -Atc 'SELECT 1' > /dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo 'PostgreSQL did not become ready.' >&2; return 1
}
sql() { docker exec -i "$pg" psql -X -v ON_ERROR_STOP=1 -U bds -d "$db" "$@"; }
flyway() {
  docker run --rm --network host -v "$repo_dir/backend/src/main/resources/db/migration:/flyway/sql:ro" "$flyway_image" \
    -url="jdbc:postgresql://127.0.0.1:$port/$db" -user=bds -password="$password" -connectRetries=10 "$@"
}
seed() {
  local start="$1" finish="$2" t0
  t0=$(date +%s)
  sql -q -v start="$start" -v finish="$finish" -v owners="$owners" < infra/perf/seed-listings.sql
  sql -q -c 'VACUUM (ANALYZE, PARALLEL 0) users, user_roles, listings, listing_revisions, listing_public_read, leads;'
  echo "- Seeded public listings $start..$finish (+1 non-public per 5) in $(( $(date +%s) - t0 )) s incl. VACUUM ANALYZE" >> "$out/report.md"
}
dataset() {
  sql -c "SELECT (SELECT count(*) FROM listing_public_read) AS public_rows, (SELECT count(*) FROM listings) AS listings,
                 (SELECT count(*) FROM listing_revisions) AS revisions, (SELECT count(*) FROM users) AS users,
                 (SELECT count(*) FROM leads) AS leads;" \
      -c "SELECT relname, pg_size_pretty(pg_total_relation_size(oid)) AS total, pg_size_pretty(pg_relation_size(oid)) AS heap
          FROM pg_class WHERE relname IN ('listing_public_read', 'listings', 'listing_revisions', 'leads', 'users')
          ORDER BY pg_total_relation_size(oid) DESC;" \
      -c "SELECT purpose, count(*) FROM listing_public_read GROUP BY 1 ORDER BY 1;" \
      -c "SELECT district_code, count(*) FROM listing_public_read GROUP BY 1 ORDER BY 2 DESC LIMIT 5;" \
      -c "SELECT count(*) AS top_owner_listings FROM listing_public_read GROUP BY owner_id ORDER BY 1 DESC LIMIT 1;"
}
# Cold: restart PostgreSQL (empty shared buffers and catalog caches) and drop the kernel page cache.
cat > "$work_dir/cold-reset.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
docker restart "$pg" > /dev/null
sync
echo 3 | sudo tee /proc/sys/vm/drop_caches > /dev/null
for _ in \$(seq 1 90); do
  docker exec "$pg" psql -X -U bds -d "$db" -Atc 'SELECT 1' > /dev/null 2>&1 && exit 0
  sleep 1
done
exit 1
EOF
chmod +x "$work_dir/cold-reset.sh"
plans() {
  local label="$1" t0
  t0=$(date +%s)
  python3 scripts/query-plans.py --psql "docker exec -i $pg psql -X -U bds -d $db" --label "$label" --out "$out" \
    --modes "$modes" --cold-reset "$work_dir/cold-reset.sh" | tee "$out/$label.log"
  echo "- Plans $label ($modes) captured in $(( $(date +%s) - t0 )) s" >> "$out/report.md"
}

{
  printf '# W6-PERF query plans\n\n- Commit: %s\n- Run: %s/%s\n' "${GITHUB_SHA:?}" "$GITHUB_RUN_ID" "$GITHUB_RUN_ATTEMPT"
  printf -- '- Image: %s, default settings (as docker-compose.yml); Flyway %s\n' "$image" "$flyway_image"
  printf -- '- Before = migrations through V%s, after = all migrations in this commit\n' "$base_version"
  printf -- '- Sizes: %s and %s public listings, %s owners (infra/perf/seed-listings.sql)\n' "$small" "$large" "$owners"
  echo '- warm = third execution; cold = PostgreSQL restarted and kernel page cache dropped before each statement'
  echo
} > "$out/report.md"
{
  uname -a; lscpu; free -m; df -h /; docker version --format '{{.Server.Version}}'
} > "$out/environment.txt"

docker run -d --name "$pg" -e POSTGRES_USER=bds -e POSTGRES_PASSWORD="$password" -e POSTGRES_DB="$db" \
  -p "127.0.0.1:$port:5432" "$image" > /dev/null
sleep 3
wait_ready
# The image's entrypoint restarts the server once after initdb; wait for the final server.
sleep 5
wait_ready
sql -c 'SELECT version(), postgis_full_version();' -c 'SHOW shared_buffers;' -c 'SHOW work_mem;' \
  -c 'SHOW effective_cache_size;' -c 'SHOW random_page_cost;' -c 'SHOW max_parallel_workers_per_gather;' >> "$out/environment.txt"
docker inspect --format '{{.Image}} mem={{.HostConfig.Memory}} cpus={{.HostConfig.NanoCpus}}' "$pg" >> "$out/environment.txt"

flyway -target="$base_version" migrate > "$out/flyway-before.txt" 2>&1
seed 1 "$small"
dataset > "$out/dataset-small.txt"
plans "${small}-before"
seed $(( small + 1 )) "$large"
dataset > "$out/dataset-large.txt"
plans "${large}-before"

pending="$(find backend/src/main/resources/db/migration -name 'V*__*.sql' | sed -E 's#.*/V([0-9]+)__.*#\1#' \
  | awk -v b="$base_version" '$1 + 0 > b' | sort -n | tr '\n' ' ')"
if [[ -n "${pending// }" ]]; then
  t0=$(date +%s)
  flyway migrate > "$out/flyway-after.txt" 2>&1
  echo "- Applied V${pending% } on the ${large}-listing dataset in $(( $(date +%s) - t0 )) s" >> "$out/report.md"
  sql -q -c 'ANALYZE listing_public_read; ANALYZE listings; ANALYZE listing_revisions;'
  plans "${large}-after"
else
  echo "- No migration after V$base_version: no after-plans" >> "$out/report.md"
fi
python3 scripts/plan-compare.py "$out" >> "$out/report.md"
echo 'Query plans completed.'
