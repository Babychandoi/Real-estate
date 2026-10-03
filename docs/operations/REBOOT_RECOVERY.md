# Tự phục hồi sau khi máy production khởi động lại

Sự cố tháng 10/2026: production trên máy Mac sập ba lần sau khi máy khởi động lại; mọi container thoát lúc tắt máy
(mã 0/137/143) và không chạy lại. Tài liệu này ghi nguyên nhân, biện pháp, việc chủ máy phải quyết và cách áp dụng /
kiểm tra. Số đo: `docs/audit-2026-09-27/streams/w6-ops.md` (luồng W6-OPS).

## 1. Nguyên nhân và biện pháp

**Nguyên nhân gốc:** Docker Desktop không chạy sau khi máy khởi động lại, vì (1) AutoStart của Docker Desktop tắt và
(2) FileVault bật: sau reboot, macOS dừng ở màn hình mở khóa FileVault cho tới khi có người nhập mật khẩu, và Docker
Desktop là ứng dụng của người dùng nên chỉ chạy sau khi có phiên đăng nhập. Khi daemon không chạy thì không restart
policy nào có tác dụng. (`pmset`: `autorestart 1`, `sleep 0` đã được đặt sẵn trên máy.)

**Biện pháp giảm thiểu, không thay cho việc sửa nguyên nhân gốc:** production dùng `restart: always`. Đo trên runner CI
(Linux Docker Engine): container `unless-stopped` **đang chạy** lúc daemon khởi động lại vẫn quay lại; container
`unless-stopped` đã bị dừng qua API trước khi daemon tắt (`docker stop`, ứng dụng desktop hay bản cập nhật dừng
container) thì nằm im (exit 137); `always` trong cùng tình huống chạy lại. Vậy `always` chỉ lo phần "container đã bị
dừng trước khi tắt máy".

**Hệ quả phải nói rõ:** khi FileVault bật, sau mất điện hoặc reboot không có người, site **sập cho tới khi có người
đăng nhập vào máy**. Chủ dự án phải chọn một trong hai:

| Lựa chọn | Được | Mất |
|---|---|---|
| A. Tắt FileVault và bật tự đăng nhập (System Settings → Users & Groups → Automatically log in as) | Phục hồi không cần người | Đĩa không mã hóa khi máy tắt; mất/trộm máy = lộ toàn bộ dữ liệu, `.env`, khóa PII, credential tunnel (`docs/ops/PRODUCTION_TOPOLOGY.md` §2) |
| B. Giữ FileVault, chấp nhận downtime tới khi có người đăng nhập | Dữ liệu được mã hóa khi máy tắt | RTO sau mất điện = thời gian tới khi có người tới máy; cần cảnh báo uptime từ bên ngoài để biết site sập |

Giải pháp bền vững là tách production khỏi máy cá nhân (`docs/operations/PRODUCTION_SEPARATION_PLAN.md`).

## 2. Thay đổi trong repository (W6-OPS)

- Mọi service chạy lâu dài trong `docker-compose.yml` và các overlay (`infra/compose.backup.yaml`, `compose.pitr.yaml`,
  `compose.pitr-backup.yaml`, `compose.observability.yaml`, `compose.production-overlay.yaml`) dùng
  `restart: ${BDS_RESTART_POLICY:-unless-stopped}`. **Mặc định giữ `unless-stopped`** (demo, dev, mọi stack khác không
  đổi hành vi); **production đặt `BDS_RESTART_POLICY=always` trong `.env` của nó** (`docs/operations/PRODUCTION_ENV.md`).
  Service chạy một lần (`postgres-wal-archive-init`, init của observability) giữ `restart: "no"`. `infra/test/compose.yaml`
  không có restart policy và không đổi.
- Thứ tự khởi động: `depends_on: condition: service_healthy` chỉ áp dụng cho `docker compose up`; khi daemon khởi động lại,
  Docker chạy mọi container cùng lúc, service khởi động quá sớm thoát lỗi và được chạy lại. Trên CI không có vòng lặp
  crash (restart count 0), xem mục 5.

## 3. Lệnh production: một cách gọi duy nhất

Các dependency của `bds-production` (PostgreSQL chạy `platform: linux/amd64`, ClamAV image `clamav/clamav-debian`) được
tạo **với** `infra/compose.apple-silicon.yaml`; backend/frontend từng được tạo chỉ với file gốc. Overlay này không đổi gì ở
backend/frontend, nên **mọi** lệnh production dùng cùng một cách gọi có overlay — nếu bỏ overlay, compose thấy định nghĩa
PostgreSQL/ClamAV khác với container đang chạy và một lệnh `up` có dependency sẽ tạo lại chúng (ClamAV với image không có
bản arm64). Deploy/rollback ứng dụng luôn thêm `--no-deps` (`scripts/review/w6-ops/compose-converge-check.sh`).

Dùng **hàm** shell, không dùng biến (`PROD="docker compose …"; $PROD …` hỏng trong zsh — shell mặc định của macOS — vì
zsh không tách từ của biến; `scripts/review/w6-ops/prod-var-shell-check.sh`). Hàm chạy được trong cả zsh và bash:
```bash
cd /path/to/Real-estate   # thư mục có .env production
bds_prod() { docker compose -p bds-production -f docker-compose.yml -f infra/compose.apple-silicon.yaml --profile edge "$@"; }
# khi cần dịch vụ backup (BACKUP_DIR, BACKUP_AGE_RECIPIENTS đã có trong .env):
bds_prod_ops() { docker compose -p bds-production -f docker-compose.yml -f infra/compose.apple-silicon.yaml \
  -f infra/compose.backup.yaml --profile edge --profile ops "$@"; }
```

## 4. Áp dụng thay đổi lên máy Mac đang chạy (coordinator/chủ máy, không phải agent)

Thêm `BDS_RESTART_POLICY=always` vào `.env` làm **mọi** service đổi cấu hình theo compose (policy nằm trong hash cấu hình),
nên từ lúc đó mọi lệnh **không** có `--no-deps` sẽ tạo lại dependency của nó (đã kiểm chứng:
`scripts/review/w6-ops/after-apply-commands-check.sh` — `run --rm backup` và `up -d backup`/`up -d backend` không có
`--no-deps` tạo lại PostgreSQL; bản có `--no-deps` thì không). Vì vậy làm theo đúng thứ tự, và kết thúc bằng **một lần tạo
lại có kế hoạch** để hash hội tụ:
```bash
# 1. Bảo vệ ngay, không tạo lại gì: policy tại chỗ cho mọi container hiện có
bds_prod ps -aq | xargs docker update --restart=always
# 2. Thêm vào .env production (không commit):  BDS_RESTART_POLICY=always   và   PUBLIC_HOST=nhadatchuan.online
# 3. Sao lưu trước cửa sổ bảo trì (--no-deps: không đụng PostgreSQL/MinIO)
bds_prod_ops run --rm --no-deps backup db          # nếu chưa bật overlay backup: dùng một pg_dump thủ công
# 4. Cửa sổ bảo trì (vài phút gián đoạn; ClamAV cần tới ~90 s): xem trước rồi tạo lại MỘT lần mọi container
bds_prod up -d --dry-run                           # liệt kê các container sẽ "Recreate"
bds_prod up -d --wait
# 5. Kiểm tra hội tụ: không còn gì để tạo lại, mọi policy là always
bds_prod up -d --dry-run | grep -c Recreate        # phải là 0
docker inspect -f '{{.Name}} {{.HostConfig.RestartPolicy.Name}}' $(bds_prod ps -aq)
```
Sau bước 4, lệnh có hay không có `--no-deps` đều không tạo lại dependency ngoài ý muốn (cùng script
`restart-policy-apply-check.sh`, 13/13 PASS: dry-run báo Recreate trước, một lần tạo lại, sau đó 0 và `up` không có
`--no-deps` giữ nguyên dependency). Nếu dùng overlay backup/PITR/observability, chạy bước 1 và 4 với cùng các `-f` đó.

**Cho tới khi bước 4 xong**, các lệnh sau vẫn tạo lại dependency — chỉ chạy trong cửa sổ bảo trì:

| Lệnh (ở đâu) | Tạo lại | Ghi chú |
|---|---|---|
| Mọi `up -d <service>` / `run <service>` **không** `--no-deps` | Dependency của service đó | Các runbook đã dùng `--no-deps` cho deploy, rollback, backup thủ công, Redis, secret |
| `bds_prod up -d` (không chỉ định service), `--scale cloudflared=2` (`PRODUCTION_SEPARATION_PLAN.md`) | Mọi container | Chính là bước 4 |
| Bật PITR: `… -f infra/compose.pitr.yaml … up -d postgres backup postgres-basebackup` (`PRODUCTION_TOPOLOGY.md` §6) | PostgreSQL | Cố ý: `archive_mode` cần khởi động lại PostgreSQL |
| Bật giám sát: `… -f infra/compose.observability.yaml --profile observability up -d` (header của overlay) | PostgreSQL + mọi service | Cố ý: overlay đổi cấu hình log của PostgreSQL |

Việc của chủ máy (một lần): Docker Desktop → Settings → General → bật **Start Docker Desktop when you sign in to your
computer**; quyết định A/B ở mục 1; lên lịch cập nhật macOS/Docker Desktop vào giờ có người ở máy.

## 5. Deploy với `restart: always`

Build trong lúc bản cũ còn phục vụ, `stop` có chủ đích, rồi `up -d --no-deps`:
```bash
bds_prod build backend frontend
bds_prod stop backend frontend
bds_prod up -d --no-build --no-deps backend frontend
```
- Nếu máy khởi động lại giữa `stop` và `up -d`, Docker chạy lại container cũ (ý nghĩa của `always`): chỉ cần chạy lại
  `up -d`. Muốn một service **không** quay lại sau reboot thì gỡ hẳn: `bds_prod rm -sf <service>`.
- Quy trình trước/sau deploy và rollback: `docs/ops/PRODUCTION_TOPOLOGY.md` mục 8.

## 6. Kiểm tra sau khi máy khởi động lại

```bash
bds_prod ps                                   # mọi service running/healthy
docker inspect -f '{{.Name}} policy={{.HostConfig.RestartPolicy.Name}} restarts={{.RestartCount}}' $(bds_prod ps -aq)
curl -fsS http://127.0.0.1:3000/healthz && curl -fsS http://127.0.0.1:3000/backend-health
VERIFY_PACE_SECONDS=0.6 scripts/verify-headers.sh https://nhadatchuan.online
```
Nếu backend healthy nhưng API qua Nginx vẫn trả 503 (`SERVICE_UNAVAILABLE`): `bds_prod restart frontend` và ghi vào sổ
sự cố.

Số đo trên runner CI (Linux Docker Engine, `BDS_RESTART_POLICY=always`, 3 lần chạy): sau `systemctl restart docker`, trang
đầu tiên 11,7–11,9 s, app phục vụ đầy đủ (tìm kiếm qua ES) 63–67 s; kiểu mất điện (daemon và mọi container bị SIGKILL)
34–58 s; mọi container chạy lại, restart count 0. Trên Mac, thời gian thật = thời gian tới khi có người đăng nhập (nếu giữ
FileVault) + khởi động Docker Desktop VM + các con số trên; cần đo ở lần khởi động lại kế tiếp.
