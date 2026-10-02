# Tự phục hồi sau khi máy production khởi động lại

Sự cố tháng 10/2026: production trên máy Mac sập ba lần sau khi máy khởi động lại. Mọi container thoát lúc tắt máy
(mã 0/137/143) và không tự chạy lại. Tài liệu này ghi nguyên nhân, thay đổi trong repository, việc chủ máy phải làm
và cách kiểm tra. Số đo: `docs/audit-2026-09-27/streams/w6-ops.md` (luồng W6-OPS).

## 1. Nguyên nhân

| Lớp | Trước | Hệ quả |
|---|---|---|
| Restart policy trong `docker-compose.yml` và các overlay production | `unless-stopped` | Container bị dừng qua API trước khi daemon tắt (`docker stop`, ứng dụng desktop hay bản cập nhật dừng container) không được chạy lại khi daemon khởi động. Trên runner CI (Linux Docker Engine), container `unless-stopped` **đang chạy** lúc daemon khởi động lại thì vẫn quay lại — nên trên Mac nguyên nhân là tổ hợp của dòng này với hai dòng dưới; `always` loại bỏ được phần do policy |
| Docker Desktop | AutoStart tắt | Sau khi máy khởi động, daemon không chạy cho tới khi có người mở Docker Desktop: không policy nào có hiệu lực |
| macOS | Đăng nhập thủ công (FileVault), ngủ máy, tự cập nhật | Docker Desktop là ứng dụng của người dùng: chỉ chạy sau khi có phiên đăng nhập |

## 2. Thay đổi trong repository (W6-OPS)

- Mọi service chạy lâu dài trong `docker-compose.yml` và các overlay dùng cho `bds-production`
  (`infra/compose.backup.yaml`, `compose.pitr.yaml`, `compose.pitr-backup.yaml`, `compose.observability.yaml`,
  `compose.production-overlay.yaml`) dùng `restart: ${BDS_RESTART_POLICY:-always}`. Service chạy một lần
  (`postgres-wal-archive-init`, init của observability) giữ `restart: "no"`.
- `always`: Docker khởi động lại container mỗi khi daemon chạy, kể cả container bị daemon dừng lúc tắt máy hoặc chết
  cùng VM. Thứ tự: `depends_on: condition: service_healthy` chỉ áp dụng cho `docker compose up`; khi daemon khởi động
  lại, Docker chạy mọi container cùng lúc. Backend khởi động trước PostgreSQL/Redis/Elasticsearch sẽ thoát lỗi và được
  Docker chạy lại (backoff tăng dần) cho tới khi phụ thuộc sẵn sàng; cloudflared chạy trước Nginx chỉ trả 502 vài giây.
  Thời gian phục hồi đo được: xem mục 5.
- Stack demo/dev dùng cùng file và cùng cổng `127.0.0.1:3000`: đặt `BDS_RESTART_POLICY=no` (hoặc `unless-stopped`)
  khi chạy demo, và luôn gỡ demo bằng `docker compose down` chứ không `stop`. `scripts/reset-demo.ps1` tự đặt
  `unless-stopped`. `infra/test/compose.yaml` (`bds-test`) không có restart policy và không đổi.
- Không đặt `BDS_RESTART_POLICY` trong `.env` production.

## 3. Việc chủ máy phải làm (một lần)

1. Docker Desktop → Settings → General → bật **Start Docker Desktop when you sign in to your computer**.
2. Bảo đảm máy có phiên đăng nhập sau khi khởi động: với FileVault, macOS dừng ở màn hình mở khóa cho tới khi có người
   nhập mật khẩu — trong thời gian đó site sập. Lên lịch cập nhật macOS/Docker Desktop vào giờ có người ở máy.
3. Không cho máy ngủ khi cắm điện và tự bật lại sau mất điện:
   `sudo pmset -c sleep 0 disksleep 0` và `sudo pmset -a autorestart 1`; kiểm tra bằng `pmset -g`.
4. Áp dụng policy mới cho container đang chạy (coordinator/chủ máy, không phải agent):
   ```bash
   # cách 1, không downtime: đổi policy tại chỗ cho mọi container của project
   docker update --restart=always $(docker compose -p bds-production --profile edge ps -aq)
   # cách 2, ở lần deploy kế tiếp: compose thấy cấu hình đổi và tạo lại container
   docker compose -p bds-production --profile edge up -d
   docker inspect -f '{{.Name}} {{.HostConfig.RestartPolicy.Name}}' $(docker compose -p bds-production --profile edge ps -aq)
   ```
   Cách 1 không đổi file compose của container; lần `up -d` sau vẫn tạo lại container theo file mới (cùng policy).

## 4. Deploy với `restart: always`

Trình tự deploy: build trước, rồi `docker compose stop` các service cần thay, rồi `up -d`:
```bash
docker compose -p bds-production build backend frontend            # image mới, bản cũ vẫn phục vụ
docker compose -p bds-production stop backend frontend             # dừng có chủ đích
docker compose -p bds-production up -d --no-build backend frontend  # tạo container mới từ image mới
```
- Container bị `stop` có chủ đích vẫn được Docker chạy lại nếu máy khởi động lại trước bước `up -d` (đó là ý nghĩa của
  `always`). Không gây hại: chạy lại `up -d` sau khi máy lên là xong. Muốn một service **không** quay lại sau reboot thì
  gỡ hẳn: `docker compose -p bds-production rm -sf <service>`.
- Toàn bộ quy trình trước/sau deploy và rollback: `docs/ops/PRODUCTION_TOPOLOGY.md` mục 8.

## 5. Kiểm tra sau khi máy khởi động lại

```bash
docker compose -p bds-production ps            # mọi service running/healthy
docker inspect -f '{{.Name}} policy={{.HostConfig.RestartPolicy.Name}} restarts={{.RestartCount}}' \
  $(docker compose -p bds-production --profile edge ps -aq)
curl -fsS http://127.0.0.1:3000/healthz && curl -fsS http://127.0.0.1:3000/backend-health
VERIFY_PACE_SECONDS=0.6 scripts/verify-headers.sh https://nhadatchuan.online
```
Nếu backend healthy nhưng API qua Nginx vẫn trả 503 (`SERVICE_UNAVAILABLE`): `docker compose -p bds-production
restart frontend` và ghi lại vào sổ sự cố.

Số đo trên runner CI (Linux Docker Engine, khởi động lại daemon nhẹ nhàng và kiểu mất điện): xem
`docs/audit-2026-09-27/streams/w6-ops.md` mục "Restart policy". Docker Desktop trên macOS chạy daemon trong VM; thời
gian thật trên máy Mac cần đo lại ở lần khởi động lại kế tiếp.
