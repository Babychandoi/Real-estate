#!/usr/bin/env bash
set -euo pipefail

# Only the ephemeral GitHub-hosted integration stack is an authorized target.
[[ "${GITHUB_ACTIONS:-}" == true && "${BDS_PERF_ISOLATED:-}" == 1 ]] || {
  echo "Requires GitHub Actions and BDS_PERF_ISOLATED=1." >&2
  exit 2
}
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
output_dir="$repo_dir/.artifacts/perf"
mkdir -p "$output_dir"
compose=(docker compose --env-file .env.demo.example)
database="bds_perf_ci_${GITHUB_RUN_ID:?}_${GITHUB_RUN_ATTEMPT:?}"
[[ "$database" =~ ^bds_perf_ci_[0-9]+_[0-9]+$ ]] || exit 2

cleanup() {
  "${compose[@]}" exec -T -e PERF_DATABASE="$database" postgres sh -ec \
    'dropdb --if-exists -U "$POSTGRES_USER" "$PERF_DATABASE"'
}
trap cleanup EXIT
"${compose[@]}" exec -T -e PERF_DATABASE="$database" postgres sh -ec \
  'createdb -U "$POSTGRES_USER" "$PERF_DATABASE"'

# Copy schema only from the already Flyway-migrated CI database, never its users/data.
"${compose[@]}" exec -T postgres sh -ec \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --schema-only --no-owner --no-privileges' \
  > "$output_dir/schema.sql"
"${compose[@]}" exec -T -e PERF_DATABASE="$database" postgres sh -ec \
  'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$PERF_DATABASE"' \
  < "$output_dir/schema.sql"
rm "$output_dir/schema.sql"

{
  printf 'Commit: %s\nRun: %s/%s\n' "${GITHUB_SHA:?}" "$GITHUB_RUN_ID" "$GITHUB_RUN_ATTEMPT"
  echo 'Cache: post-insert/index-build/ANALYZE; warm synthetic TEMP relation, not cold production I/O.'
  echo 'Concurrent services: CI integration stack; timings are diagnostic, not a production SLO.'
  uname -a
  lscpu
  free -m
  df -h .
  "${compose[@]}" exec -T -e PERF_DATABASE="$database" postgres sh -ec \
    'psql -X -U "$POSTGRES_USER" -d "$PERF_DATABASE" -c "SELECT version(), postgis_full_version();" -c "SHOW shared_buffers;" -c "SHOW work_mem;"'
  docker inspect --format '{{.Image}} {{json .HostConfig.Memory}} {{json .HostConfig.NanoCpus}}' \
    "$("${compose[@]}" ps -q postgres)"
} > "$output_dir/environment.txt"

for size in 100000 1000000; do
  report="$output_dir/explain-search-${size}.txt"
  # Only publish a completed report; an SQL error must fail the job.
  "${compose[@]}" exec -T -e PERF_DATABASE="$database" -e PERF_SIZE="$size" postgres sh -ec \
    'psql -X -v ON_ERROR_STOP=1 -v dataset_size="$PERF_SIZE" -U "$POSTGRES_USER" -d "$PERF_DATABASE"' \
    < infra/perf/explain-search.sql > "$report.partial"
  mv "$report.partial" "$report"
done
echo 'Completed 100k and 1M query plans; review the uploaded S10 evidence.'
