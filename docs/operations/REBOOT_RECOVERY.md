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
bản arm64). Deploy/rollback ứng dụng luôn thêm `--no-deps` để không bao giờ chạm dependency
(`scripts/review/w6-ops/compose-converge-check.sh` chứng minh cả hai rủi ro).
```bash
cd /path/to/Real-estate   # thư mục có .env production
PROD="docker compose -p bds-production -f docker-compose.yml -f infra/compose.apple-silicon.yaml --profile edge"
```

## 4. Áp dụng thay đổi lên máy Mac đang chạy (coordinator/chủ máy, không phải agent)

Không tạo lại dependency; đã kiểm chứng bằng `scripts/review/w6-ops/restart-policy-apply-check.sh` (project tạm, 8/8 PASS:
container dependency giữ nguyên id, policy `always`, deploy app với `--no-deps` không đụng dependency).
```bash
$PROD ps -aq | xargs docker update --restart=always            # 1. đổi policy tại chỗ cho mọi container hiện có
# 2. thêm một dòng vào .env production (không commit): BDS_RESTART_POLICY=always
docker inspect -f '{{.Name}} {{.HostConfig.RestartPolicy.Name}}' $($PROD ps -aq)   # 3. kiểm tra: tất cả always
```
`docker update` không đổi nhãn cấu hình compose của container: một lệnh `$PROD up -d` **không** có `--no-deps` sau này sẽ
tạo lại các dependency một lần (cùng policy `always`) — chỉ chạy nó trong cửa sổ bảo trì. Nếu dùng overlay backup/PITR
thì thêm các `-f` đó vào `$PROD` khi đổi policy cho các container tương ứng.

Việc của chủ máy (một lần): Docker Desktop → Settings → General → bật **Start Docker Desktop when you sign in to your
computer**; quyết định A/B ở mục 1; lên lịch cập nhật macOS/Docker Desktop vào giờ có người ở máy.

## 5. Deploy với `restart: always`

Build trong lúc bản cũ còn phục vụ, `stop` có chủ đích, rồi `up -d --no-deps`:
```bash
$PROD build backend frontend
$PROD stop backend frontend
$PROD up -d --no-build --no-deps backend frontend
```
- Nếu máy khởi động lại giữa `stop` và `up -d`, Docker chạy lại container cũ (ý nghĩa của `always`): chỉ cần chạy lại
  `up -d`. Muốn một service **không** quay lại sau reboot thì gỡ hẳn: `$PROD rm -sf <service>`.
- Quy trình trước/sau deploy và rollback: `docs/ops/PRODUCTION_TOPOLOGY.md` mục 8.

## 6. Kiểm tra sau khi máy khởi động lại

```bash
$PROD ps                                   # mọi service running/healthy
docker inspect -f '{{.Name}} policy={{.HostConfig.RestartPolicy.Name}} restarts={{.RestartCount}}' $($PROD ps -aq)
curl -fsS http://127.0.0.1:3000/healthz && curl -fsS http://127.0.0.1:3000/backend-health
VERIFY_PACE_SECONDS=0.6 scripts/verify-headers.sh https://nhadatchuan.online
```
Nếu backend healthy nhưng API qua Nginx vẫn trả 503 (`SERVICE_UNAVAILABLE`): `$PROD restart frontend` và ghi vào sổ sự cố.

Số đo trên runner CI (Linux Docker Engine, `BDS_RESTART_POLICY=always`, 2 lần chạy): sau `systemctl restart docker`, trang
đầu tiên 11,7–11,9 s, app phục vụ đầy đủ (tìm kiếm qua ES) 63–67 s; kiểu mất điện (daemon và mọi container bị SIGKILL)
34–58 s; mọi container chạy lại, restart count 0. Trên Mac, thời gian thật = thời gian tới khi có người đăng nhập (nếu giữ
FileVault) + khởi động Docker Desktop VM + các con số trên; cần đo ở lần khởi động lại kế tiếp.
