#!/usr/bin/env bash
set -euo pipefail

# Usage: BDS_PERF_DATABASE_URL=postgresql://.../bds_perf_run1 scripts/run-search-explain.sh 100000
# A disposable database with migrations through V095 and PostGIS is required. Production is never a target.
size="${1:-}"
if [[ "$size" != 100000 && "$size" != 1000000 ]]; then
  echo "Select exactly 100000 or 1000000 rows." >&2
  exit 2
fi
: "${BDS_PERF_DATABASE_URL:?Set BDS_PERF_DATABASE_URL to an isolated bds_perf_* database}"
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir="${BDS_PERF_OUTPUT_DIR:-$repo_dir/.artifacts/perf}"
mkdir -p "$output_dir"
output_file="$output_dir/explain-search-${size}-$(date -u +%Y%m%dT%H%M%SZ).txt"
psql "$BDS_PERF_DATABASE_URL" -X -v ON_ERROR_STOP=1 -v dataset_size="$size" \
  -f "$repo_dir/infra/perf/explain-search.sql" > "$output_file"
echo "EXPLAIN evidence: $output_file"
