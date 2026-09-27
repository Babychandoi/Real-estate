#!/usr/bin/env bash
# Restore drill (audit F21.2, F21.5): restores the newest encrypted backup sets into the isolated Compose project
# bds-drill, checks them against their manifests, measures every step and writes docs/ops/drills/<date>-<env>.md.
#
# Usage: BACKUP_DIR=<dir> AGE_IDENTITY_FILE=<file> scripts/restore-drill.sh [--env NAME] [--keep] [--report-dir DIR]
#   BACKUP_DIR         directory written by the backup service (infra/compose.backup.yaml); must be outside this repo
#   AGE_IDENTITY_FILE  age private key; kept by the owner, mounted read-only into the drill containers only
#   --env NAME         backup environment label (default: production)
#   --keep             leave bds-drill running for inspection (then: docker compose -p bds-drill down -v)
#   --report-dir DIR   where the report goes (default: docs/ops/drills)
#   --note TEXT        operator note added to the report (data origin, incident number, ...); repeatable
#   TOOL_IMAGE         backup tool image (default bds-backup:1; built from infra/backup when missing)
# The drill never connects to the source database or bucket; it only reads BACKUP_DIR. Exit 1 when it fails.
# Compatible with the bash 3.2 shipped by macOS; needs only docker on the host.
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd -P)"
ENV_NAME=production
KEEP=0
REPORT_DIR="$REPO/docs/ops/drills"
TOOL_IMAGE="${TOOL_IMAGE:-bds-backup:1}"
PROJECT=bds-drill
COMPOSE_FILE="$REPO/infra/drill/compose.drill.yaml"
NETWORK="${PROJECT}_default"

usage() { sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; }
die() { echo "restore-drill: $*" >&2; exit 1; }
say() { echo "restore-drill: $*" >&2; }

while [ $# -gt 0 ]; do
  case "$1" in
    --env) ENV_NAME="${2:?}"; shift 2 ;;
    --keep) KEEP=1; shift ;;
    --report-dir) REPORT_DIR="${2:?}"; shift 2 ;;
    --note) NOTES="${NOTES:-}- ${2:?}"$'\n'; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

[[ "$ENV_NAME" =~ ^[a-z0-9][a-z0-9-]{0,39}$ ]] || die "invalid --env"
[ -n "${BACKUP_DIR:-}" ] || die "set BACKUP_DIR"
[ -n "${AGE_IDENTITY_FILE:-}" ] || die "set AGE_IDENTITY_FILE"
[ -d "$BACKUP_DIR" ] || die "BACKUP_DIR does not exist"
[ -r "$AGE_IDENTITY_FILE" ] || die "AGE_IDENTITY_FILE is not readable"
BACKUPS="$(cd "$BACKUP_DIR" && pwd -P)"
IDENTITY="$(cd "$(dirname "$AGE_IDENTITY_FILE")" && pwd -P)/$(basename "$AGE_IDENTITY_FILE")"
case "$BACKUPS/" in "$REPO/"*) die "BACKUP_DIR is inside the repository; backups must never live in Git" ;; esac
case "$IDENTITY" in "$REPO/"*) die "the age identity must never be stored in the repository" ;; esac
command -v docker >/dev/null || die "docker is required"
if [ -n "$(docker compose -p "$PROJECT" -f "$COMPOSE_FILE" ps -q 2>/dev/null)" ]; then
  die "Compose project $PROJECT is already running; remove it first (docker compose -p $PROJECT down -v)"
fi

WORK="$(mktemp -d "${TMPDIR:-/tmp}/restore-drill.XXXXXX")"
cleanup() {
  if [ "$KEEP" -eq 0 ]; then docker compose -p "$PROJECT" -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true; fi
  rm -rf "$WORK"
}
trap cleanup EXIT

# tool <network> <restore.sh arguments...>: backups and identity read-only, runs as the invoking user.
tool() {
  local network="$1"
  shift
  docker run --rm --network "$network" --user "$(id -u):$(id -g)" --memory 256m \
    -v "$BACKUPS:/backups:ro" -v "$IDENTITY:/run/drill/age-identity:ro" \
    -e BACKUP_ENV="$ENV_NAME" -e AGE_IDENTITY_FILE=/run/drill/age-identity -e HOME=/tmp -e MC_CONFIG_DIR=/tmp/.mcli \
    -e PGHOST=postgres -e PGPORT=5432 -e PGUSER=drill -e PGPASSWORD=drill-only-password -e PGDATABASE=drill_admin \
    -e MINIO_ENDPOINT=http://minio:9000 -e MINIO_ACCESS_KEY=drill-admin -e MINIO_SECRET_KEY=drill-admin-only-password \
    --entrypoint /usr/local/bin/restore.sh "$TOOL_IMAGE" "$@"
}
field() { sed -n "s/^$1=//p" "$2" | head -n 1; }
duration_ms() { local value; value="$(sed -n "s/^DURATION_MS $1 //p" "$2" | tail -n 1)"; echo "${value:-0}"; }
result_of() { sed -n 's/^RESULT: //p' "$1" | tail -n 1; }
body_of() { grep -v -e '^DURATION_MS ' -e '^RESULT: ' "$1" || true; }
seconds() { awk -v ms="${1:-0}" 'BEGIN { printf "%.1f", ms / 1000 }'; }
epoch_of_iso() { # ISO-8601 UTC -> epoch seconds (GNU date or BSD date)
  date -u -d "$1" +%s 2>/dev/null || date -j -u -f '%Y-%m-%dT%H:%M:%SZ' "$1" +%s
}

if ! docker image inspect "$TOOL_IMAGE" >/dev/null 2>&1; then
  say "building $TOOL_IMAGE from infra/backup"
  docker build -q -t "$TOOL_IMAGE" "$REPO/infra/backup" >/dev/null
fi

STARTED_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
STARTED="$(date +%s)"
tool none describe db latest > "$WORK/db.describe" || die "no complete database backup for env $ENV_NAME in $BACKUPS"
tool none describe media latest > "$WORK/media.describe" || die "no complete media backup for env $ENV_NAME in $BACKUPS"
DB_ID="$(field id "$WORK/db.describe")"; DB_CREATED="$(field createdAt "$WORK/db.describe")"
MEDIA_ID="$(field id "$WORK/media.describe")"; MEDIA_CREATED="$(field createdAt "$WORK/media.describe")"
say "database set $DB_ID ($DB_CREATED), media set $MEDIA_ID ($MEDIA_CREATED)"

say "starting isolated project $PROJECT"
T0="$(date +%s)"
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d --wait >/dev/null
INFRA_SECONDS=$(( $(date +%s) - T0 ))

STATUS=PASS
step() { # step <name> <restore.sh args...>: output in $WORK/<name>.out, errors in $WORK/<name>.log
  local name="$1"
  shift
  say "step $name"
  if ! tool "$NETWORK" "$@" > "$WORK/$name.out" 2> "$WORK/$name.log"; then
    say "step $name failed: $(tail -n 3 "$WORK/$name.log" | tr '\n' ' ')"
    return 1
  fi
}
step restore-db db latest bds_restore || STATUS=FAIL
step restore-media media latest || STATUS=FAIL
step verify-db verify-db latest bds_restore || STATUS=FAIL
step verify-media verify-media latest || STATUS=FAIL
step verify-references verify-references bds_restore || true
[ "$(result_of "$WORK/verify-db.out")" = PASS ] || STATUS=FAIL
[ "$(result_of "$WORK/verify-media.out")" = PASS ] || STATUS=FAIL
REFERENCES="$(result_of "$WORK/verify-references.out")"
TOTAL_SECONDS=$(( $(date +%s) - STARTED ))

RESTORE_DB_MS="$(duration_ms restore-db "$WORK/restore-db.out")"
RESTORE_MEDIA_MS="$(duration_ms restore-media "$WORK/restore-media.out")"
VERIFY_MS=$(( $(duration_ms verify-db "$WORK/verify-db.out") + $(duration_ms verify-media "$WORK/verify-media.out") ))
RTO_MS=$(( INFRA_SECONDS * 1000 + RESTORE_DB_MS + RESTORE_MEDIA_MS ))
DB_AGE=$(( STARTED - $(epoch_of_iso "$DB_CREATED") ))
MEDIA_AGE=$(( STARTED - $(epoch_of_iso "$MEDIA_CREATED") ))

mkdir -p "$REPORT_DIR"
REPORT="$REPORT_DIR/$(date -u +%Y-%m-%d)-$ENV_NAME.md"
[ -e "$REPORT" ] && REPORT="$REPORT_DIR/$(date -u +%Y-%m-%d-%H%M)-$ENV_NAME.md"
{
  echo "# Diễn tập khôi phục — môi trường \`$ENV_NAME\` — $(date -u +%Y-%m-%d)"
  echo
  echo "- **Kết quả:** $STATUS"
  echo "- **Bắt đầu:** $STARTED_ISO (UTC); công cụ: \`scripts/restore-drill.sh\`, image \`$TOOL_IMAGE\`"
  echo "- **Nơi khôi phục:** Compose project \`$PROJECT\` cô lập (\`infra/drill/compose.drill.yaml\`: postgis/postgis:16-3.4, MinIO cùng bản production), không publish cổng, xóa sau diễn tập"
  echo "- **Máy chạy:** $(uname -sm), Docker $(docker version --format '{{.Server.Version}}' 2>/dev/null || echo '?')"
  echo "- **Nguồn dữ liệu:** chỉ đọc \`BACKUP_DIR\` (không kết nối tới database/bucket nguồn); khóa age giải mã chỉ mount read-only vào container diễn tập"
  echo
  echo "## Bản sao lưu được khôi phục"
  echo
  echo "| Loại | Set | Tạo lúc (UTC) | Tuổi khi diễn tập | Dung lượng mã hóa | Nội dung | Công cụ |"
  echo "|---|---|---|---:|---:|---|---|"
  echo "| PostgreSQL | \`$DB_ID\` | $DB_CREATED | ${DB_AGE}s | $(field bytes "$WORK/db.describe") B | $(field items "$WORK/db.describe") bảng, $(field rows "$WORK/db.describe") dòng | $(field tools "$WORK/db.describe") |"
  echo "| Object (MinIO) | \`$MEDIA_ID\` | $MEDIA_CREATED | ${MEDIA_AGE}s | $(field bytes "$WORK/media.describe") B gốc | $(field items "$WORK/media.describe") object | $(field tools "$WORK/media.describe") |"
  echo
  echo "Checksum SHA-256 của mọi file mã hóa được kiểm tra với manifest trước khi giải mã."
  echo
  echo "## Thời gian"
  echo
  echo "| Bước | Thời gian |"
  echo "|---|---:|"
  echo "| Khởi động PostgreSQL + MinIO cô lập (đến healthy) | ${INFRA_SECONDS} s |"
  echo "| Giải mã + \`pg_restore\` vào database mới | $(seconds "$RESTORE_DB_MS") s |"
  echo "| Giải mã + nạp lại object | $(seconds "$RESTORE_MEDIA_MS") s |"
  echo "| Đối soát (đếm dòng từng bảng, sha256 từng object) | $(seconds "$VERIFY_MS") s |"
  echo "| **RTO đo được cho dữ liệu** (hạ tầng + DB + object, chưa gồm deploy ứng dụng và đổi DNS/tunnel) | **$(seconds "$RTO_MS") s** |"
  echo "| Toàn bộ diễn tập | ${TOTAL_SECONDS} s |"
  echo
  echo "**RPO:** tại thời điểm diễn tập, bản DB mới nhất đã ${DB_AGE} giây tuổi và bản object ${MEDIA_AGE} giây tuổi. Với lịch mặc định của"
  echo "\`infra/compose.backup.yaml\` (DB mỗi giờ, object mỗi ngày) RPO tối đa là 1 giờ cho dữ liệu DB và 24 giờ cho ảnh; bật"
  echo "\`infra/compose.pitr.yaml\` + \`infra/compose.pitr-backup.yaml\` để giảm RPO DB xuống khoảng 5–10 phút."
  echo
  echo "## Đối soát PostgreSQL"
  echo
  body_of "$WORK/verify-db.out"
  echo
  echo "Kết quả: **$(result_of "$WORK/verify-db.out")**"
  echo
  echo "## Đối soát object"
  echo
  body_of "$WORK/verify-media.out"
  echo
  echo "Kết quả: **$(result_of "$WORK/verify-media.out")**"
  echo
  echo "## Nhất quán DB ↔ object"
  echo
  body_of "$WORK/verify-references.out"
  echo
  case "$REFERENCES" in
    PASS) echo "Mọi dòng \`media_objects\` đều có object tương ứng sau khôi phục." ;;
    SKIPPED) echo "Bản khôi phục không có bảng \`media_objects\`; không có tham chiếu để đối chiếu." ;;
    GAP) echo "Có dòng \`media_objects\` không có object: ảnh tải lên sau lần sao lưu object gần nhất (bản DB mới hơn bản object). Đây là khoảng hở RPO của ảnh, cần được chấp nhận hoặc thu hẹp bằng lịch sao lưu object dày hơn." ;;
    *) echo "Không đối chiếu được (xem log của bước verify-references)." ;;
  esac
  if [ -n "${NOTES:-}" ]; then
    echo
    echo "## Ghi chú của người chạy"
    echo
    printf '%s' "$NOTES"
  fi
  if [ "$STATUS" != PASS ]; then
    echo
    echo "## Lỗi"
    echo
    for name in restore-db restore-media verify-db verify-media verify-references; do
      if [ -s "$WORK/$name.log" ]; then
        echo "### $name"; echo; echo '```'; tail -n 20 "$WORK/$name.log"; echo '```'
      fi
    done
  fi
} > "$REPORT"
say "report: ${REPORT#"$REPO/"} ($STATUS)"
[ "$STATUS" = PASS ]
