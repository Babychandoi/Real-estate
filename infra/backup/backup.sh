#!/usr/bin/env bash
# Encrypted backups of PostgreSQL and MinIO (audit F21.2).
#
#   backup.sh db        pg_dump -Fc of $PGDATABASE, row counts taken in the same snapshot, encrypted with age
#   backup.sh media     every object of $MINIO_BUCKETS, one age file per object; unchanged objects are hard-linked
#                       from the previous set, so daily sets cost only the new objects
#   backup.sh wal       zstd + encrypt new WAL segments from $WAL_ARCHIVE_DIR (PITR overlays only)
#   backup.sh basebackup  physical base backup for PITR (pg_basebackup tar -> zstd -> age); runs in a sidecar that
#                       shares PostgreSQL's network namespace (infra/compose.pitr-backup.yaml)
#   backup.sh prune     apply retention
#   backup.sh schedule  run the jobs listed in SCHEDULE_JOBS (default db,media,wal) at their intervals: db every
#                       DB_INTERVAL_SECONDS, media every MEDIA_INTERVAL_SECONDS, wal every WAL_INTERVAL_SECONDS (only
#                       when WAL_ARCHIVE_DIR is set), basebackup every BASEBACKUP_INTERVAL_SECONDS; prunes after each
#
# Environment: PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD (libpq), MINIO_ENDPOINT MINIO_ACCESS_KEY MINIO_SECRET_KEY
# MINIO_BUCKETS, AGE_RECIPIENTS or AGE_RECIPIENTS_FILE (public keys only), BACKUP_ROOT (default /backups),
# BACKUP_ENV (default production), retention: RETENTION_DB_HOURS (48, keep every set), RETENTION_DB_DAYS (14, one
# per day), RETENTION_MEDIA_DAYS (14), RETENTION_BASE_DAYS (7), RETENTION_WAL_DAYS (7), RETENTION_MIN_SETS (3).
source "$(dirname "$(readlink -f "$0")")/lib.sh"

SCHEDULE_JOBS="${SCHEDULE_JOBS:-db,media,wal}"
DB_INTERVAL_SECONDS="${DB_INTERVAL_SECONDS:-3600}"
MEDIA_INTERVAL_SECONDS="${MEDIA_INTERVAL_SECONDS:-86400}"
WAL_INTERVAL_SECONDS="${WAL_INTERVAL_SECONDS:-300}"
BASEBACKUP_INTERVAL_SECONDS="${BASEBACKUP_INTERVAL_SECONDS:-86400}"
RETENTION_DB_HOURS="${RETENTION_DB_HOURS:-48}"
RETENTION_DB_DAYS="${RETENTION_DB_DAYS:-14}"
RETENTION_MEDIA_DAYS="${RETENTION_MEDIA_DAYS:-14}"
RETENTION_BASE_DAYS="${RETENTION_BASE_DAYS:-7}"
RETENTION_WAL_DAYS="${RETENTION_WAL_DAYS:-7}"
RETENTION_MIN_SETS="${RETENTION_MIN_SETS:-3}"

finish_set() { # finish_set <work dir> <final dir>
  mv "$1" "$2"
  iso_now > "$2/SUCCESS"
}

# encrypt_stream <fifo> <sha256 out> <encrypted out> <age args...>: stdin -> age, hashing the plaintext on the way.
# The hasher reads a FIFO and is waited for by PID; process substitution is not used because `$!` does not reliably
# name it after a pipeline, and waiting on the wrong job deadlocks with the snapshot holder.
encrypt_stream() {
  local fifo="$1" digest="$2" target="$3" hasher
  shift 3
  sha256sum < "$fifo" | cut -d' ' -f1 > "$digest" &
  hasher=$!
  tee "$fifo" | age "$@" -o "$target"
  wait "$hasher"
}

backup_db() {
  require_env PGHOST PGDATABASE PGUSER
  local age_args id dir work sync started server_version snapshot dump bytes rows
  mapfile -t age_args < <(age_recipient_args)
  id="$(new_id)"
  dir="$(env_dir)/db/$id"
  work="$dir.partial"
  mkdir -p "$work"
  started="$(now_ms)"
  server_version="$(psql -X -At -v ON_ERROR_STOP=1 -c 'SHOW server_version')"

  # A REPEATABLE READ transaction exports its snapshot and stays open (psql waits in \!) until the dump and the
  # counts, which import that snapshot, are done: the manifest counts describe exactly the dumped data.
  sync="$(mktemp -d /tmp/bds-db-backup.XXXXXX)"
  export BDS_SYNC="$sync"
  trap 'touch "$BDS_SYNC/done" 2>/dev/null || true' EXIT
  psql -X -q -At -v ON_ERROR_STOP=1 -v snapshot_file="$sync/snapshot" >"$sync/holder.log" 2>&1 <<'SQL' &
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT pg_export_snapshot() \g :snapshot_file
\! i=0; while [ ! -f "$BDS_SYNC/done" ] && [ $i -lt 86400 ]; do sleep 0.25; i=$((i+1)); done
COMMIT;
SQL
  local holder=$!
  for _ in $(seq 1 240); do
    [ -s "$sync/snapshot" ] && break
    kill -0 "$holder" 2>/dev/null || die "snapshot transaction failed: $(tr '\n' ' ' < "$sync/holder.log")"
    sleep 0.25
  done
  snapshot="$(tr -d '[:space:]' < "$sync/snapshot" 2>/dev/null || true)"
  [ -n "$snapshot" ] || die "could not export a snapshot from $PGHOST/$PGDATABASE"

  psql -X -q -At -F $'\t' -v ON_ERROR_STOP=1 >"$work/counts.tsv" <<SQL
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET TRANSACTION SNAPSHOT '${snapshot}';
${TABLE_COUNT_SQL};
COMMIT;
SQL

  dump="${PGDATABASE}.dump.age"
  mkfifo "$sync/plain.fifo"
  pg_dump --snapshot="$snapshot" --format=custom --compress=6 \
    | encrypt_stream "$sync/plain.fifo" "$sync/plain.sha256" "$work/$dump" "${age_args[@]}"
  touch "$sync/done"
  wait "$holder" || die "snapshot transaction did not commit cleanly: $(tr '\n' ' ' < "$sync/holder.log")"

  bytes="$(stat -c %s "$work/$dump")"
  rows="$(awk -F'\t' '{s += $2} END {print s + 0}' "$work/counts.tsv")"
  jq -n \
    --arg format "$FORMAT_VERSION" --arg env "$BACKUP_ENV" --arg id "$id" --arg createdAt "$(iso_now)" \
    --argjson durationMs "$(( $(now_ms) - started ))" \
    --arg host "$PGHOST" --arg database "$PGDATABASE" --arg serverVersion "$server_version" --arg snapshot "$snapshot" \
    --arg pgDump "$(pg_dump --version)" --arg age "$(age --version)" --argjson recipients "$(recipients_json)" \
    --arg file "$dump" --argjson bytes "$bytes" --arg sha256 "$(sha256_of "$work/$dump")" \
    --arg plaintextSha256 "$(cat "$sync/plain.sha256")" \
    --argjson tables "$(counts_json "$work/counts.tsv")" --argjson totalRows "$rows" \
    '{format: $format, kind: "db", env: $env, id: $id, createdAt: $createdAt, durationMs: $durationMs,
      source: {host: $host, database: $database, serverVersion: $serverVersion, snapshot: $snapshot},
      tools: {pgDump: $pgDump, age: $age},
      encryption: {scheme: "age", recipients: $recipients},
      files: [{name: $file, bytes: $bytes, sha256: $sha256, plaintextSha256: $plaintextSha256}],
      counts: {tables: $tables, tableCount: ($tables | length), totalRows: $totalRows}}' > "$work/manifest.json"
  rm -f "$work/counts.tsv"
  finish_set "$work" "$dir"
  rm -rf "$sync"
  trap - EXIT
  info "backup_db_done id=$id tables=$(jq '.counts.tableCount' "$dir/manifest.json") rows=$rows bytes=$bytes durationMs=$(( $(now_ms) - started ))"
  write_metrics db 1 "$(( ($(now_ms) - started) / 1000 ))" "$bytes"
}

backup_media() {
  local age_args id dir work tmp previous bucket objects bytes copied reused started
  mapfile -t age_args < <(age_recipient_args)
  minio_alias src
  id="$(new_id)"
  dir="$(env_dir)/media/$id"
  work="$dir.partial"
  tmp="$(mktemp -d /tmp/bds-media-backup.XXXXXX)"
  mkfifo "$tmp/object.fifo"
  mkdir -p "$work/objects"
  started="$(now_ms)"
  previous="$(latest_set media || true)"

  # Objects already in the previous set with the same key, size and ETag are hard links, not new copies.
  declare -A reuse=()
  if [ -n "$previous" ] && [ -f "$previous/index.jsonl" ]; then
    local b k e s sha file
    while IFS=$'\t' read -r b k e s sha file; do
      reuse["$b/$k|$e|$s"]="$sha|$file"
    done < <(jq -r '[.bucket, .key, .etag, (.size | tostring), .sha256, .file] | @tsv' "$previous/index.jsonl")
  fi

  objects=0; bytes=0; copied=0; reused=0
  : > "$work/index.jsonl"
  local per_bucket="{}"
  for bucket in $(buckets); do
    "$MC" ls --recursive --json "src/$bucket" > "$tmp/list.jsonl" || die "cannot list bucket $bucket"
    local bucket_objects=0 bucket_bytes=0 key size etag fid rel lookup sha
    while IFS=$'\t' read -r key size etag; do
      fid="$(printf '%s/%s' "$bucket" "$key" | sha256sum | cut -d' ' -f1)"
      rel="objects/${fid:0:2}/$fid.age"
      mkdir -p "$work/objects/${fid:0:2}"
      lookup="$bucket/$key|$etag|$size"
      if [ -n "${reuse[$lookup]:-}" ] && [ -f "$previous/${reuse[$lookup]#*|}" ]; then
        sha="${reuse[$lookup]%%|*}"
        ln "$previous/${reuse[$lookup]#*|}" "$work/$rel" 2>/dev/null || cp -p "$previous/${reuse[$lookup]#*|}" "$work/$rel"
        reused=$((reused + 1))
      else
        "$MC" cat "src/$bucket/$key" \
          | encrypt_stream "$tmp/object.fifo" "$tmp/object.sha256" "$work/$rel" "${age_args[@]}"
        sha="$(cat "$tmp/object.sha256")"
        copied=$((copied + 1))
      fi
      jq -cn --arg bucket "$bucket" --arg key "$key" --argjson size "$size" --arg etag "$etag" --arg sha256 "$sha" --arg file "$rel" \
        '{bucket: $bucket, key: $key, size: $size, etag: $etag, sha256: $sha256, file: $file}' >> "$work/index.jsonl"
      bucket_objects=$((bucket_objects + 1))
      bucket_bytes=$((bucket_bytes + size))
    done < <(jq -r 'select(.type == "file") | [.key, (.size | tostring), (.etag // "")] | @tsv' "$tmp/list.jsonl")
    per_bucket="$(jq -c --arg b "$bucket" --argjson o "$bucket_objects" --argjson s "$bucket_bytes" '. + {($b): {objects: $o, bytes: $s}}' <<<"$per_bucket")"
    objects=$((objects + bucket_objects))
    bytes=$((bytes + bucket_bytes))
  done

  jq -n \
    --arg format "$FORMAT_VERSION" --arg env "$BACKUP_ENV" --arg id "$id" --arg createdAt "$(iso_now)" \
    --argjson durationMs "$(( $(now_ms) - started ))" --arg endpoint "$MINIO_ENDPOINT" \
    --arg mc "$("$MC" --version | head -n 1 | cut -d" " -f1-3)" --arg age "$(age --version)" --argjson recipients "$(recipients_json)" \
    --argjson buckets "$per_bucket" --argjson objects "$objects" --argjson bytes "$bytes" \
    --argjson copied "$copied" --argjson reused "$reused" --arg indexSha256 "$(sha256_of "$work/index.jsonl")" \
    --arg previous "$(basename "${previous:-none}")" \
    '{format: $format, kind: "media", env: $env, id: $id, createdAt: $createdAt, durationMs: $durationMs,
      source: {endpoint: $endpoint, buckets: ($buckets | keys)}, tools: {mc: $mc, age: $age},
      encryption: {scheme: "age", recipients: $recipients},
      files: [{name: "index.jsonl", sha256: $indexSha256}],
      counts: {buckets: $buckets, objects: $objects, bytes: $bytes, copied: $copied, reusedFromPrevious: $reused, previousSet: $previous}}' \
    > "$work/manifest.json"
  finish_set "$work" "$dir"
  rm -rf "$tmp"
  info "backup_media_done id=$id objects=$objects bytes=$bytes copied=$copied reused=$reused durationMs=$(( $(now_ms) - started ))"
  write_metrics media 1 "$(( ($(now_ms) - started) / 1000 ))" "$bytes"
}

# WAL segments are 16 MB even when forced by archive_timeout on an idle server; zstd shrinks those to a few KB.
backup_wal() {
  require_env WAL_ARCHIVE_DIR
  local age_args dest segment name count=0
  mapfile -t age_args < <(age_recipient_args)
  dest="$(env_dir)/wal"
  mkdir -p "$dest"
  for segment in "$WAL_ARCHIVE_DIR"/*; do
    [ -f "$segment" ] || continue
    name="$(basename "$segment")"
    [ -f "$dest/$name.zst.age" ] && continue
    zstd -q -3 -c "$segment" | age "${age_args[@]}" -o "$dest/$name.zst.age.partial"
    mv "$dest/$name.zst.age.partial" "$dest/$name.zst.age"
    count=$((count + 1))
  done
  info "backup_wal_done new_segments=$count"
  write_metrics wal 1 0 "$(du -sb "$dest" | cut -f1)"
}

# Physical base backup for PITR. PGHOST must be 127.0.0.1 inside PostgreSQL's network namespace, where the
# image's default pg_hba.conf allows replication connections (a remote container is refused).
backup_basebackup() {
  require_env PGHOST PGUSER
  local age_args id dir work tmp started bytes
  mapfile -t age_args < <(age_recipient_args)
  id="$(new_id)"
  dir="$(env_dir)/base/$id"
  work="$dir.partial"
  tmp="$(mktemp -d /tmp/bds-basebackup.XXXXXX)"
  mkfifo "$tmp/plain.fifo"
  mkdir -p "$work"
  started="$(now_ms)"
  pg_basebackup --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" --no-password \
      --pgdata=- --format=tar --wal-method=none --checkpoint=fast --label="bds-$BACKUP_ENV-$id" \
    | zstd -q -3 \
    | encrypt_stream "$tmp/plain.fifo" "$tmp/plain.sha256" "$work/base.tar.zst.age" "${age_args[@]}"
  bytes="$(stat -c %s "$work/base.tar.zst.age")"
  jq -n --arg format "$FORMAT_VERSION" --arg env "$BACKUP_ENV" --arg id "$id" --arg createdAt "$(iso_now)" \
    --argjson durationMs "$(( $(now_ms) - started ))" --arg host "$PGHOST" \
    --arg serverVersion "$(psql -X -At -h "$PGHOST" -U "$PGUSER" -d "${PGDATABASE:-postgres}" -c 'SHOW server_version')" \
    --arg pgBasebackup "$(pg_basebackup --version)" --arg age "$(age --version)" --argjson recipients "$(recipients_json)" \
    --argjson bytes "$bytes" --arg sha256 "$(sha256_of "$work/base.tar.zst.age")" --arg plaintextSha256 "$(cat "$tmp/plain.sha256")" \
    '{format: $format, kind: "base", env: $env, id: $id, createdAt: $createdAt, durationMs: $durationMs,
      source: {host: $host, serverVersion: $serverVersion}, tools: {pgBasebackup: $pgBasebackup, age: $age},
      encryption: {scheme: "age", recipients: $recipients}, compression: "zstd",
      files: [{name: "base.tar.zst.age", bytes: $bytes, sha256: $sha256, plaintextSha256: $plaintextSha256}],
      counts: {}}' > "$work/manifest.json"
  finish_set "$work" "$dir"
  rm -rf "$tmp"
  info "backup_basebackup_done id=$id bytes=$bytes durationMs=$(( $(now_ms) - started ))"
  write_metrics base 1 "$(( ($(now_ms) - started) / 1000 ))" "$bytes"
}

# prune_kind <kind> <keep-all-hours> <keep-daily-days>
prune_kind() {
  local kind="$1" keep_hours="$2" keep_days="$3" now index=0 set id age_hours day keep
  local -A seen_day=()
  now="$(date +%s)"
  while IFS= read -r set; do
    id="$(basename "$set")"
    age_hours=$(( (now - $(id_epoch "$id")) / 3600 ))
    day="${id:0:8}"
    keep=0
    [ "$index" -lt "$RETENTION_MIN_SETS" ] && keep=1
    [ "$age_hours" -lt "$keep_hours" ] && keep=1
    if [ "$age_hours" -lt $((keep_days * 24)) ] && [ -z "${seen_day[$day]:-}" ]; then keep=1; fi
    seen_day[$day]=1
    if [ "$keep" -eq 0 ]; then
      rm -rf "$set"
      info "pruned kind=$kind id=$id ageHours=$age_hours"
    fi
    index=$((index + 1))
  done < <(list_sets "$kind" | sort -r)
  [ -d "$(env_dir)/$kind" ] && find "$(env_dir)/$kind" -mindepth 1 -maxdepth 1 -name '*.partial' -mmin +1440 -exec rm -rf {} + || true
}

# prune [db|media|basebackup|wal]: one kind (what the scheduler just ran, so two services with different retention
# settings never prune each other's sets) or, without argument, every kind.
prune() {
  local kind
  for kind in ${1:-db media basebackup wal}; do
    case "$kind" in
      db) prune_kind db "$RETENTION_DB_HOURS" "$RETENTION_DB_DAYS" ;;
      media) prune_kind media 0 "$RETENTION_MEDIA_DAYS" ;;
      basebackup) prune_kind base 0 "$RETENTION_BASE_DAYS" ;;
      wal) [ ! -d "$(env_dir)/wal" ] || find "$(env_dir)/wal" -type f -name '*.age' -mtime +"$RETENTION_WAL_DAYS" -delete ;;
    esac
  done
}

# Each job runs as its own process (see schedule): bash ignores `set -e` inside anything whose exit status is being
# tested, so a job must never run as `if job` / `job || ...` in the same shell or a failed pg_dump would continue.
run_job() {
  local kind="$1" started
  started="$(date +%s)"
  # errtrace also runs the trap inside command substitutions; report once, from the main shell.
  trap 'if [ "$BASH_SUBSHELL" -eq 0 ]; then warn "backup_failed kind='"$kind"'"; write_metrics '"$kind"' 0 "$(( $(date +%s) - '"$started"' ))" 0; fi' ERR
  "backup_$kind"
  trap - ERR
}

schedule() {
  local now self job interval
  local -A next=()
  self="$(readlink -f "$0")"
  now="$(date +%s)"
  for job in $(printf '%s' "$SCHEDULE_JOBS" | tr ',' ' '); do
    case "$job" in
      db|media|basebackup) ;;
      wal) [ -n "${WAL_ARCHIVE_DIR:-}" ] || continue ;;
      *) die "unknown job '$job' in SCHEDULE_JOBS" ;;
    esac
    next[$job]="$now"
    [ "${RUN_ON_START:-true}" = true ] || next[$job]=$((now + $(interval_of "$job")))
  done
  [ ${#next[@]} -gt 0 ] || die "SCHEDULE_JOBS selects no job"
  info "schedule_started env=$BACKUP_ENV jobs=${!next[*]} dbEvery=${DB_INTERVAL_SECONDS}s mediaEvery=${MEDIA_INTERVAL_SECONDS}s"
  while true; do
    now="$(date +%s)"
    for job in "${!next[@]}"; do
      [ "$now" -ge "${next[$job]}" ] || continue
      "$self" "$job" || true
      interval="$(interval_of "$job")"
      next[$job]=$((now + interval))
      "$self" prune "$job" || warn "prune_failed kind=$job"
    done
    sleep 30
  done
}

interval_of() {
  case "$1" in
    db) echo "$DB_INTERVAL_SECONDS" ;;
    media) echo "$MEDIA_INTERVAL_SECONDS" ;;
    wal) echo "$WAL_INTERVAL_SECONDS" ;;
    basebackup) echo "$BASEBACKUP_INTERVAL_SECONDS" ;;
  esac
}

case "${1:-schedule}" in
  db|media|wal|basebackup) run_job "$1" ;;
  prune) prune "${2:-}" ;;
  schedule) schedule ;;
  *) echo "usage: backup.sh db|media|wal|basebackup|prune|schedule" >&2; exit 2 ;;
esac
