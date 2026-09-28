#!/usr/bin/env bash
# Shared helpers for backup.sh and restore.sh (sourced, not executed).
#
# Layout under $BACKUP_ROOT (the host directory BACKUP_DIR, never inside the repository):
#   <env>/db/<UTC id>/      <database>.dump.age   pg_dump -Fc encrypted with age
#                           manifest.json         format, source, tool versions, sha256, row counts per table
#                           SUCCESS               written last; sets without it are incomplete and ignored
#   <env>/media/<UTC id>/   objects/xx/<id>.age   one age file per object (unchanged objects are hard links)
#                           index.jsonl           bucket, key, size, etag, sha256 of the plaintext, file
#                           manifest.json, SUCCESS
#   <env>/wal/<segment>.age                       WAL segments (only with infra/compose.pitr*.yaml)
#   metrics/*.prom                                node-exporter textfile metrics (best effort)
# Plaintext never touches disk: data is streamed from pg_dump / MinIO straight into age.
set -Eeuo pipefail
umask 077

BACKUP_ROOT="${BACKUP_ROOT:-/backups}"
BACKUP_ENV="${BACKUP_ENV:-production}"
export HOME="${HOME:-/tmp}"
export MC_CONFIG_DIR="${MC_CONFIG_DIR:-/tmp/.mcli}"
MC="${MC_BIN:-mcli}"
FORMAT_VERSION="bds-backup/v1"

log() { printf '%s level=%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$1" "$2" >&2; }
info() { log info "$*"; }
warn() { log warn "$*"; }
die() { log error "$*"; exit 1; }

require_env() {
  local name
  for name in "$@"; do
    [ -n "${!name:-}" ] || die "missing environment variable $name"
  done
}

[[ "$BACKUP_ENV" =~ ^[a-z0-9][a-z0-9-]{0,39}$ ]] || die "BACKUP_ENV must be lower-case letters, digits and dashes"

new_id() { date -u +%Y%m%dT%H%M%SZ; }
now_ms() { date +%s%3N; }
iso_now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
sha256_of() { sha256sum "$1" | cut -d' ' -f1; }
env_dir() { printf '%s/%s' "$BACKUP_ROOT" "$BACKUP_ENV"; }

# Epoch seconds of a set id such as 20260928T013000Z.
id_epoch() {
  local id="$1"
  date -u -d "${id:0:4}-${id:4:2}-${id:6:2}T${id:9:2}:${id:11:2}:${id:13:2}Z" +%s
}

# Successful sets of a kind, oldest first (one directory per line).
list_sets() {
  local kind="$1" dir
  [ -d "$(env_dir)/$kind" ] || return 0
  for dir in "$(env_dir)/$kind"/*/; do
    dir="${dir%/}"
    [[ "$dir" == *.partial ]] && continue
    [ -f "$dir/SUCCESS" ] && printf '%s\n' "$dir"
  done | sort
}

latest_set() { list_sets "$1" | tail -n 1; }

# resolve_set <kind> <id|latest|path>
resolve_set() {
  local kind="$1" ref="$2" dir
  case "$ref" in
    latest) dir="$(latest_set "$kind")" ;;
    /*) dir="$ref" ;;
    *) dir="$(env_dir)/$kind/$ref" ;;
  esac
  [ -n "$dir" ] && [ -f "$dir/SUCCESS" ] && [ -f "$dir/manifest.json" ] || die "no complete $kind backup set for '$ref' in $(env_dir)/$kind"
  printf '%s' "$dir"
}

# age recipient arguments from AGE_RECIPIENTS (space, comma or newline separated public keys) and/or
# AGE_RECIPIENTS_FILE. Prints one argument per line for mapfile.
age_recipient_args() {
  local recipient found=0
  if [ -n "${AGE_RECIPIENTS:-}" ]; then
    for recipient in $(printf '%s' "$AGE_RECIPIENTS" | tr ',\n' '  '); do
      printf '%s\n%s\n' -r "$recipient"
      found=1
    done
  fi
  if [ -n "${AGE_RECIPIENTS_FILE:-}" ]; then
    printf '%s\n%s\n' -R "$AGE_RECIPIENTS_FILE"
    found=1
  fi
  [ "$found" -eq 1 ] || die "set AGE_RECIPIENTS (age public key, age1...) or AGE_RECIPIENTS_FILE; the private key never belongs on the backup host"
}

recipients_json() {
  { [ -n "${AGE_RECIPIENTS:-}" ] && printf '%s' "$AGE_RECIPIENTS" | tr ', ' '\n\n'; \
    [ -n "${AGE_RECIPIENTS_FILE:-}" ] && grep -v '^#' "$AGE_RECIPIENTS_FILE"; true; } | sed '/^$/d' | jq -R . | jq -s .
}

minio_alias() {
  local alias="$1"
  require_env MINIO_ENDPOINT MINIO_ACCESS_KEY MINIO_SECRET_KEY
  "$MC" alias set "$alias" "$MINIO_ENDPOINT" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" --api S3v4 >/dev/null
}

buckets() { printf '%s' "${MINIO_BUCKETS:-bds-listings}" | tr ',' ' '; }

# Tables whose rows are backed up (extension-owned tables such as PostGIS spatial_ref_sys come from CREATE EXTENSION).
readonly TABLE_LIST_SQL="SELECT format('%I.%I', n.nspname, c.relname)
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relkind IN ('r', 'p') AND n.nspname NOT IN ('pg_catalog', 'information_schema') AND n.nspname NOT LIKE 'pg\\_toast%'
  AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.classid = 'pg_class'::regclass AND d.objid = c.oid AND d.deptype = 'e')"

# Exact row count of every table in one statement: "<schema.table>\t<rows>" per line.
readonly TABLE_COUNT_SQL="SELECT t.name, (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %s', t.name), false, true, '')))[1]::text::bigint
FROM (${TABLE_LIST_SQL}) AS t(name) ORDER BY 1"

# counts.tsv -> {"schema.table": rows, ...}
counts_json() { jq -R -s 'split("\n") | map(select(length > 0) | split("\t") | {(.[0]): (.[1] | tonumber)}) | add // {}' "$1"; }

# Prometheus textfile metrics for node-exporter; best effort, never fails a backup.
write_metrics() {
  local kind="$1" status="$2" duration="$3" bytes="$4"
  local dir="${BACKUP_METRICS_DIR:-$BACKUP_ROOT/metrics}" now file last_success
  mkdir -p "$dir" 2>/dev/null || { warn "metrics_dir_unwritable dir=$dir"; return 0; }
  file="$dir/bds_backup_${BACKUP_ENV}_${kind}.prom"
  now="$(date +%s)"
  last_success="$(sed -n 's/^bds_backup_last_success_timestamp_seconds{[^}]*} //p' "$file" 2>/dev/null || true)"
  [ "$status" -eq 1 ] && last_success="$now"
  {
    printf '# HELP bds_backup_last_run_timestamp_seconds Last backup attempt (Unix time).\n# TYPE bds_backup_last_run_timestamp_seconds gauge\n'
    printf 'bds_backup_last_run_timestamp_seconds{env="%s",kind="%s"} %s\n' "$BACKUP_ENV" "$kind" "$now"
    printf '# HELP bds_backup_last_status 1 if the last backup attempt succeeded.\n# TYPE bds_backup_last_status gauge\n'
    printf 'bds_backup_last_status{env="%s",kind="%s"} %s\n' "$BACKUP_ENV" "$kind" "$status"
    if [ -n "$last_success" ]; then
      printf '# HELP bds_backup_last_success_timestamp_seconds Last successful backup (Unix time).\n# TYPE bds_backup_last_success_timestamp_seconds gauge\n'
      printf 'bds_backup_last_success_timestamp_seconds{env="%s",kind="%s"} %s\n' "$BACKUP_ENV" "$kind" "$last_success"
    fi
    printf '# HELP bds_backup_last_duration_seconds Duration of the last backup attempt.\n# TYPE bds_backup_last_duration_seconds gauge\n'
    printf 'bds_backup_last_duration_seconds{env="%s",kind="%s"} %s\n' "$BACKUP_ENV" "$kind" "$duration"
    printf '# HELP bds_backup_last_size_bytes Size of the last successful backup set.\n# TYPE bds_backup_last_size_bytes gauge\n'
    printf 'bds_backup_last_size_bytes{env="%s",kind="%s"} %s\n' "$BACKUP_ENV" "$kind" "$bytes"
  } > "$file.tmp" 2>/dev/null && mv "$file.tmp" "$file" || warn "metrics_write_failed file=$file"
}
