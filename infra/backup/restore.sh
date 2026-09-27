#!/usr/bin/env bash
# Restore and verify encrypted backups made by backup.sh (audit F21.2). Never point it at a live database/bucket:
# `db` refuses to restore into an existing database.
#
#   restore.sh list [db|media]
#   restore.sh db <id|latest|path> <target-database>            decrypt + pg_restore into a new database
#   restore.sh media <id|latest|path> [bucket-suffix]           decrypt every object into its bucket (+ suffix)
#   restore.sh verify-db <id|latest|path> <target-database>     row counts per table vs manifest (Markdown report)
#   restore.sh verify-media <id|latest|path> [bucket-suffix]    objects, bytes and sha256 vs index (Markdown report)
#   restore.sh verify-references <target-database> [suffix]     media_objects rows vs restored objects (latest media set)
#   restore.sh basebackup <id|latest|path> <empty-dir>          PITR: unpack a physical base backup into a data dir
#   restore.sh wal <dir>                                        PITR: decrypt every shipped WAL segment into <dir>
#                                                               (then restore_command = 'cp <dir>/%f %p')
#
# Environment: AGE_IDENTITY_FILE (the private key, kept offline, never next to the backups), BACKUP_ROOT, BACKUP_ENV,
# PGHOST/PGPORT/PGUSER/PGPASSWORD of the restore server (PGDATABASE = maintenance database), MINIO_ENDPOINT,
# MINIO_ACCESS_KEY, MINIO_SECRET_KEY of the restore object store. verify-* print "RESULT: PASS|FAIL" last and exit 1
# on FAIL; every step prints "DURATION_MS <step> <ms>".
source "$(dirname "$(readlink -f "$0")")/lib.sh"

identity() {
  require_env AGE_IDENTITY_FILE
  [ -r "$AGE_IDENTITY_FILE" ] || die "AGE_IDENTITY_FILE is not readable"
  printf '%s' "$AGE_IDENTITY_FILE"
}

wait_for_server() { # a freshly started restore server may still be initialising
  local attempt
  for attempt in $(seq 1 60); do
    psql -X -At -c 'SELECT 1' >/dev/null 2>&1 && return 0
    sleep 1
  done
  die "restore server $PGHOST:${PGPORT:-5432} is not accepting connections"
}

check_files() { # check_files <set dir>: sha256 of every encrypted file listed in the manifest
  local set="$1" name expected actual
  while IFS=$'\t' read -r name expected; do
    actual="$(sha256_of "$set/$name")"
    [ "$actual" = "$expected" ] || die "checksum mismatch for $name (manifest $expected, file $actual)"
  done < <(jq -r '.files[] | [.name, .sha256] | @tsv' "$set/manifest.json")
}

restore_db() {
  local set target id started
  set="$(resolve_set db "$1")"
  target="$2"
  [[ "$target" =~ ^[a-z_][a-z0-9_]{0,62}$ ]] || die "invalid target database name"
  id="$(identity)"
  started="$(now_ms)"
  check_files "$set"
  wait_for_server
  local existing
  existing="$(psql -X -At -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM pg_database WHERE datname = '$target'")"
  [ "$existing" = 0 ] || die "database $target already exists; restores only go into a new database"
  psql -X -q -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$target\" TEMPLATE template0"
  local dump
  dump="$(jq -r '.files[0].name' "$set/manifest.json")"
  age -d -i "$id" "$set/$dump" \
    | pg_restore --dbname="$target" --no-owner --no-privileges --exit-on-error
  info "restore_db_done set=$(basename "$set") target=$target"
  echo "DURATION_MS restore-db $(( $(now_ms) - started ))"
}

restore_media() {
  local set suffix id started restored=0 bucket key file
  set="$(resolve_set media "$1")"
  suffix="${2:-}"
  id="$(identity)"
  started="$(now_ms)"
  check_files "$set"
  minio_alias dst
  for bucket in $(jq -r '.counts.buckets | keys[]' "$set/manifest.json"); do
    "$MC" mb --ignore-existing "dst/$bucket$suffix" >/dev/null
  done
  while IFS=$'\t' read -r bucket key file; do
    age -d -i "$id" "$set/$file" | "$MC" pipe --quiet --part-size 16MiB "dst/$bucket$suffix/$key" >/dev/null
    restored=$((restored + 1))
  done < <(jq -r '[.bucket, .key, .file] | @tsv' "$set/index.jsonl")
  info "restore_media_done set=$(basename "$set") objects=$restored"
  echo "DURATION_MS restore-media $(( $(now_ms) - started ))"
}

verify_db() {
  local set target started ok=1 rows
  set="$(resolve_set db "$1")"
  target="$2"
  started="$(now_ms)"
  local work
  work="$(mktemp -d /tmp/bds-verify-db.XXXXXX)"
  PGDATABASE="$target" psql -X -q -At -F $'\t' -v ON_ERROR_STOP=1 -c "$TABLE_COUNT_SQL" > "$work/restored.tsv"
  jq -r '.counts.tables | to_entries[] | [.key, (.value | tostring)] | @tsv' "$set/manifest.json" | sort -t $'\t' -k1,1 > "$work/source.tsv"
  sort -t $'\t' -k1,1 -o "$work/restored.tsv" "$work/restored.tsv"
  echo "| Bảng | Nguồn (manifest) | Sau khôi phục | Khớp |"
  echo "|---|---:|---:|---|"
  local name source restored
  # Full outer join of the two sorted lists.
  while IFS=$'\t' read -r name source restored; do
    if [ "$source" = "$restored" ]; then
      echo "| \`$name\` | $source | $restored | có |"
    else
      ok=0
      echo "| \`$name\` | ${source:-—} | ${restored:-—} | **KHÔNG** |"
    fi
  done < <(join -t $'\t' -a 1 -a 2 -e '' -o '0,1.2,2.2' "$work/source.tsv" "$work/restored.tsv")
  rows="$(awk -F'\t' '{s += $2} END {print s + 0}' "$work/restored.tsv")"
  echo
  echo "Tổng: $(wc -l < "$work/source.tsv" | tr -d ' ') bảng trong manifest, $(wc -l < "$work/restored.tsv" | tr -d ' ') bảng sau khôi phục; $(jq '.counts.totalRows' "$set/manifest.json") dòng trong manifest, $rows dòng sau khôi phục."
  rm -rf "$work"
  echo "DURATION_MS verify-db $(( $(now_ms) - started ))"
  if [ "$ok" -eq 1 ]; then echo "RESULT: PASS"; else echo "RESULT: FAIL"; return 1; fi
}

verify_media() {
  local set suffix started ok=1 expected_objects expected_bytes found_objects=0 found_bytes=0 mismatched=0 missing=0
  set="$(resolve_set media "$1")"
  suffix="${2:-}"
  started="$(now_ms)"
  minio_alias dst
  local work bucket key size sha actual_size actual_sha
  work="$(mktemp -d /tmp/bds-verify-media.XXXXXX)"
  for bucket in $(jq -r '.counts.buckets | keys[]' "$set/manifest.json"); do
    "$MC" ls --recursive --json "dst/$bucket$suffix" \
      | jq -r --arg b "$bucket" 'select(.type == "file") | [$b, .key, (.size | tostring)] | @tsv' >> "$work/restored.tsv"
  done
  declare -A restored_size=()
  while IFS=$'\t' read -r bucket key size; do
    restored_size["$bucket/$key"]="$size"
    found_objects=$((found_objects + 1))
    found_bytes=$((found_bytes + size))
  done < "$work/restored.tsv"
  expected_objects="$(jq '.counts.objects' "$set/manifest.json")"
  expected_bytes="$(jq '.counts.bytes' "$set/manifest.json")"
  # Every object: present, same size, same sha256 as the source object at backup time (downloaded again).
  while IFS=$'\t' read -r bucket key size sha; do
    actual_size="${restored_size[$bucket/$key]:-}"
    if [ -z "$actual_size" ]; then missing=$((missing + 1)); continue; fi
    actual_sha="$("$MC" cat "dst/$bucket$suffix/$key" | sha256sum | cut -d' ' -f1)"
    if [ "$actual_size" != "$size" ] || [ "$actual_sha" != "$sha" ]; then mismatched=$((mismatched + 1)); fi
  done < <(jq -r '[.bucket, .key, (.size | tostring), .sha256] | @tsv' "$set/index.jsonl")
  [ "$found_objects" -eq "$expected_objects" ] && [ "$found_bytes" -eq "$expected_bytes" ] && [ "$missing" -eq 0 ] && [ "$mismatched" -eq 0 ] || ok=0
  echo "| Chỉ số | Nguồn (index) | Sau khôi phục |"
  echo "|---|---:|---:|"
  echo "| Số object | $expected_objects | $found_objects |"
  echo "| Tổng byte | $expected_bytes | $found_bytes |"
  echo "| Thiếu | 0 | $missing |"
  echo "| Sai kích thước hoặc sha256 | 0 | $mismatched |"
  rm -rf "$work"
  echo "DURATION_MS verify-media $(( $(now_ms) - started ))"
  if [ "$ok" -eq 1 ]; then echo "RESULT: PASS"; else echo "RESULT: FAIL"; return 1; fi
}

# Database rows that point at objects (media_objects.object_key) vs the restored buckets. Objects uploaded after
# the media set was taken are expected to be missing when the database set is newer; the drill reports the gap.
verify_references() {
  local target="$1" suffix="${2:-}" set started work bucket references missing orphans
  set="$(resolve_set media latest)"
  started="$(now_ms)"
  work="$(mktemp -d /tmp/bds-verify-refs.XXXXXX)"
  local has_table
  has_table="$(PGDATABASE="$target" psql -X -At -v ON_ERROR_STOP=1 -c "SELECT to_regclass('public.media_objects') IS NOT NULL")"
  if [ "$has_table" != t ]; then
    echo "Không có bảng \`media_objects\` trong bản khôi phục; bỏ qua đối chiếu tham chiếu."
    echo "RESULT: SKIPPED"
    return 0
  fi
  PGDATABASE="$target" psql -X -At -c "SELECT object_key FROM media_objects" | sort -u > "$work/references.txt"
  minio_alias dst
  : > "$work/objects.txt"
  for bucket in $(jq -r '.counts.buckets | keys[]' "$set/manifest.json"); do
    "$MC" ls --recursive --json "dst/$bucket$suffix" | jq -r 'select(.type == "file") | .key' >> "$work/objects.txt"
  done
  sort -u -o "$work/objects.txt" "$work/objects.txt"
  references="$(wc -l < "$work/references.txt" | tr -d ' ')"
  missing="$(comm -23 "$work/references.txt" "$work/objects.txt" | wc -l | tr -d ' ')"
  orphans="$(comm -13 "$work/references.txt" "$work/objects.txt" | wc -l | tr -d ' ')"
  echo "| Chỉ số | Giá trị |"
  echo "|---|---:|"
  echo "| Dòng \`media_objects\` (tham chiếu tới object) | $references |"
  echo "| Có object tương ứng sau khôi phục | $((references - missing)) |"
  echo "| Tham chiếu không có object | $missing |"
  echo "| Object không có dòng \`media_objects\` | $orphans |"
  rm -rf "$work"
  echo "DURATION_MS verify-references $(( $(now_ms) - started ))"
  if [ "$missing" -eq 0 ]; then echo "RESULT: PASS"; else echo "RESULT: GAP"; fi
}

restore_basebackup() {
  local set target id started
  set="$(resolve_set base "$1")"
  target="$2"
  id="$(identity)"
  started="$(now_ms)"
  mkdir -p "$target"
  [ -z "$(ls -A "$target")" ] || die "target directory $target is not empty"
  check_files "$set"
  age -d -i "$id" "$set/base.tar.zst.age" | zstd -d -q | tar -x -C "$target"
  chmod 0700 "$target"
  info "restore_basebackup_done set=$(basename "$set") target=$target"
  echo "DURATION_MS restore-basebackup $(( $(now_ms) - started ))"
}

restore_wal() {
  local target id started count=0 file name
  target="$1"
  id="$(identity)"
  started="$(now_ms)"
  mkdir -p "$target"
  for file in "$(env_dir)/wal"/*.zst.age; do
    [ -f "$file" ] || continue
    name="$(basename "$file" .zst.age)"
    age -d -i "$id" "$file" | zstd -d -q > "$target/$name.partial"
    mv "$target/$name.partial" "$target/$name"
    count=$((count + 1))
  done
  info "restore_wal_done segments=$count target=$target"
  echo "DURATION_MS restore-wal $(( $(now_ms) - started ))"
}

# key=value summary of a set's manifest (for scripts without jq).
describe() {
  local set
  set="$(resolve_set "$1" "$2")"
  jq -r '"id=\(.id)", "createdAt=\(.createdAt)", "durationMs=\(.durationMs)",
         "bytes=\(if .kind == "db" then .files[0].bytes else .counts.bytes end)",
         "items=\(if .kind == "db" then .counts.tableCount else .counts.objects end)",
         "rows=\(.counts.totalRows // "")", "reused=\(.counts.reusedFromPrevious // "")",
         "sha256=\(.files[0].sha256)", "tools=\(.tools | to_entries | map("\(.key) \(.value)") | join(", "))",
         "source=\(.source | tostring)"' "$set/manifest.json"
}

list() {
  local kind set
  for kind in ${1:-db media}; do
    while IFS= read -r set; do
      jq -r --arg k "$kind" '[$k, .id, .createdAt, (.counts.totalRows // .counts.objects | tostring)] | @tsv' "$set/manifest.json"
    done < <(list_sets "$kind")
  done
}

case "${1:-}" in
  list) list "${2:-}" ;;
  describe) describe "${2:?kind}" "${3:?set}" ;;
  db) restore_db "${2:?set}" "${3:?target database}" ;;
  media) restore_media "${2:?set}" "${3:-}" ;;
  verify-db) verify_db "${2:?set}" "${3:?target database}" ;;
  verify-media) verify_media "${2:?set}" "${3:-}" ;;
  verify-references) verify_references "${2:?target database}" "${3:-}" ;;
  basebackup) restore_basebackup "${2:?set}" "${3:?target directory}" ;;
  wal) restore_wal "${2:?target directory}" ;;
  *) sed -n '2,18p' "$0" >&2; exit 2 ;;
esac
