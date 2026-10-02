# Kế hoạch tách production khỏi máy dev (managed DB + object storage)

Yêu cầu audit F21.4 (EXTERNAL: việc hạ tầng của chủ hệ thống). Hiện trạng, rủi ro và topology đích đã mô tả ở
`docs/ops/PRODUCTION_TOPOLOGY.md` §1–4; tài liệu này **không lặp lại** mà bổ sung: lựa chọn nhà cung cấp, chi phí ước
tính, việc kỹ thuật cần làm trước, runbook chuyển đổi có diễn tập và tiêu chí rollback. **Chưa bước nào được thực hiện.**
Mọi giá ở đây là ước tính thô từ bảng giá công khai, **cần báo giá lại** trước khi quyết định.

## 1. Topology đích (tóm tắt — sơ đồ ở PRODUCTION_TOPOLOGY.md §3)

| Thành phần | Nơi chạy | Ghi chú |
|---|---|---|
| Nginx (frontend), backend, Redis, Elasticsearch, ClamAV, cloudflared ×2, backup, observability | **Một VM Linux** riêng (≥ 4 vCPU, 8 GB RAM, SSD) | Elasticsearch dựng lại được từ PostgreSQL (`POST /api/v2/admin/search/index/rebuild`); Redis không phải nguồn dữ liệu |
| PostgreSQL 16 + PostGIS | **Dịch vụ managed**, cùng vùng với VM | Nguồn dữ liệu chuẩn; PITR của nhà cung cấp |
| Ảnh tin, avatar, ảnh KYC | **Object storage S3-compatible**, bucket riêng tư | Ảnh công khai đã đi qua backend (`/api/v1/public/media/…`) và cache Cloudflare, nên bucket không cần public |
| Edge | Cloudflare Tunnel, 2 connector trên VM | Không mở cổng vào VM |

**VM và DB phải cùng vùng, cùng nhà cung cấp (hoặc cùng thành phố).** Backend chạy nhiều truy vấn cho mỗi request: đặt VM ở
Việt Nam và DB ở Singapore sẽ cộng độ trễ mạng vào từng truy vấn. Người dùng ở Việt Nam đi qua PoP Cloudflare trong nước;
độ trễ edge → origin ở Singapore cần đo trong buổi diễn tập, không giả định.

## 2. Lựa chọn nhà cung cấp

Yêu cầu tối thiểu với PostgreSQL: bản 16, các extension mà migration Flyway tạo — `postgis`, `pg_trgm`, `unaccent`,
`uuid-ossp` — và nên có `pg_stat_statements` (dashboard `infra/observability/grafana/dashboards/bds-database.json`).

| PostgreSQL managed | Vùng gần | PostGIS | PITR | Ghi chú |
|---|---|---|---|---|
| AWS RDS for PostgreSQL | ap-southeast-1 (Singapore) | Có | Có (giữ tối đa 35 ngày) | Multi-AZ cho HA; parameter group cho `pg_stat_statements` |
| Google Cloud SQL for PostgreSQL | asia-southeast1 (Singapore) | Có | Có | HA regional; private IP qua VPC |
| DigitalOcean Managed PostgreSQL | SGP1 | Có | Có (khoảng 7 ngày) | Đơn giản nhất cho đội nhỏ; standby node cho HA |
| Aiven for PostgreSQL | Singapore (trên AWS/GCP/DO…) | Có | Có | Chạy trên nhiều cloud; giá theo gói |
| Viettel IDC / Viettel Cloud, FPT Cloud, VNG Cloud, Bizfly Cloud | Hà Nội / TP.HCM | **Cần xác minh PostGIS** và bản 16 | Cần xác minh | Dữ liệu ở trong nước; hỏi rõ PITR, HA, extension, SLA |

| Object storage | Vùng | Ghi chú |
|---|---|---|
| Cloudflare R2 | Tự động | Không tính phí egress; hợp với Cloudflare đang dùng |
| AWS S3 | ap-southeast-1 | Versioning, Object Lock; có phí egress |
| DigitalOcean Spaces | SGP1 | Gói cố định kèm dung lượng + băng thông |
| Backblaze B2 | Cần xác minh vùng gần nhất | Rẻ cho dung lượng; tương thích S3 |
| VNG Cloud vStorage, FPT Object Storage, Viettel / Bizfly object storage | Việt Nam | Cần xác minh độ tương thích S3 (presign, path-style) |

Điểm cần cân nhắc khi chọn:

| Điểm | Câu hỏi |
|---|---|
| Lưu trữ dữ liệu ở đâu (pháp lý) | DB và ảnh KYC chứa dữ liệu cá nhân. Đặt ở nước ngoài có phải lập hồ sơ chuyển dữ liệu ra nước ngoài theo Nghị định 13/2023/NĐ-CP (và Luật Bảo vệ dữ liệu cá nhân 2025 — cần xác nhận văn bản hiện hành)? Nghị định 53/2022/NĐ-CP về lưu trữ dữ liệu tại Việt Nam có áp dụng cho dự án không? **Cần luật sư trả lời trước khi chọn vùng** |
| PITR | Cửa sổ khôi phục ≥ 7 ngày; RPO thực tế của nhà cung cấp (đối chiếu mục tiêu RPO ≤ 15 phút, RTO ≤ 60 phút ở PRODUCTION_TOPOLOGY.md §6) |
| Egress | Ảnh đi qua backend + cache Cloudflare: egress từ object storage tới VM cùng vùng thường rẻ hoặc miễn phí; tới VM khác nhà cung cấp thì tính phí |
| Tương thích MinIO client | Backend dùng thư viện MinIO với `MINIO_ENDPOINT` và access key (`MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`), chưa có cấu hình region: phải thử upload/đọc/presign trên staging với nhà cung cấp đã chọn |
| Mạng | DB chỉ nhận kết nối từ IP/VPC của VM; bắt buộc TLS (`sslmode=require` hoặc `verify-full` với CA của nhà cung cấp) |

## 3. Chi phí ước tính (USD/tháng)

Ước tính thô từ bảng giá niêm yết công khai cho vùng Singapore, chưa gồm VAT, egress vượt mức, hỗ trợ; nhà cung cấp Việt
Nam chưa có số. **Cần báo giá lại.**

| Hạng mục | Cấu hình nhỏ | Cấu hình vừa |
|---|---|---|
| VM ứng dụng | 4 vCPU / 8 GB: ~40–170 | 4–8 vCPU / 16 GB: ~80–300 |
| PostgreSQL managed | 1 node, 2–4 GB RAM, không HA: ~30–120 | Có standby HA: ~120–400 |
| Object storage ~100 GB | ~2–10 | ~5–30 (thêm versioning, bản sao khác vùng) |
| Đích sao lưu ngoài nhà cung cấp chính | ~1–10 | ~5–20 |
| Cloudflare Tunnel | 0 (gói Free) | 0 |
| **Tổng** | **~80–300** | **~200–750** |

## 4. Việc kỹ thuật cần làm trước khi chuyển (trong repo)

| Việc | Vì sao | Trạng thái |
|---|---|---|
| Overlay Compose cho DB/storage bên ngoài: đặt `SPRING_DATASOURCE_URL` (kèm `sslmode`), `MINIO_ENDPOINT` cho backend; `PGHOST`, `MINIO_ENDPOINT` cho `infra/compose.backup.yaml`; bỏ `depends_on: postgres` và các service `postgres`/`minio` | `docker-compose.yml` và `infra/compose.backup.yaml` đang cố định `postgres:5432` và `http://minio:9000` | Chưa có |
| Bucket KYC riêng | Hiện một bucket `MINIO_BUCKET` (mặc định `bds-listings`), ảnh KYC phân biệt bằng `media_objects.visibility = 'KYC_PRIVATE'` | Chưa có (luồng S1-MEDIA); chuyển được trước với một bucket riêng tư, tách sau |
| Tạo extension bằng tài khoản quản trị của nhà cung cấp trước khi Flyway chạy | Role ứng dụng có thể không đủ quyền `CREATE EXTENSION` | Việc vận hành |
| Thiết lập tham số DB tương đương `infra/postgres/conf.d/observability.conf` (`log_min_duration_statement`, `pg_stat_statements`…) qua parameter group | Không sửa được file cấu hình trên dịch vụ managed | Việc vận hành |

## 5. Runbook chuyển đổi

Biến dùng chung: `BACKUP_DIR` (ngoài repo), `AGE_IDENTITY_FILE` (private key, chỉ mang vào lúc khôi phục), thông tin
kết nối DB đích (`PGHOST`/`PGPORT`/`PGUSER`/`PGPASSWORD`) và storage đích (`MINIO_ENDPOINT`/`MINIO_ACCESS_KEY`/
`MINIO_SECRET_KEY`) — xem đầu file `infra/backup/restore.sh`. `restore.sh` chạy trong image công cụ `bds-backup:1`
(build từ `infra/backup`), theo mẫu `docker run --rm … --entrypoint /usr/local/bin/restore.sh bds-backup:1 <lệnh>` ở
PRODUCTION_TOPOLOGY.md §6; các bước dưới chỉ ghi phần `<lệnh>`.

**A. Chuẩn bị (không ảnh hưởng người dùng)**
1. Tạo VM, DB managed, bucket; giới hạn mạng DB theo IP/VPC của VM; tạo extension (mục 4).
2. Trên VM: clone repo, tạo `.env` production **mới** (không chép nguyên file từ laptop), cấp secret mới theo
   `docs/ops/GIT_HISTORY_REMEDIATION_PLAN.md` §4 nhóm "Khi tách máy" — trừ `PII_ENCRYPTION_KEY`/`PII_INDEX_KEY` phải
   giữ nguyên giá trị cũ (đổi = mất khả năng giải mã).
3. Tạo một tunnel Cloudflare **mới** cho VM với hostname thử (ví dụ `staging.nhadatchuan.online`, nên che bằng
   Cloudflare Access). Không chạy connector của tunnel production hiện tại trên VM: hai connector cùng tunnel sẽ chia
   traffic giữa laptop và VM (hai database khác nhau). Điều này chi tiết hóa PRODUCTION_TOPOLOGY.md §4 bước 4: chỉ
   chuyển hostname công khai sau khi laptop đã dừng ghi (bước 11–14).

**B. Diễn tập (ít nhất một lần, đo thời gian từng bước)**
4. Trên laptop: tạo bản sao lưu mới — `docker compose -p bds-production -f docker-compose.yml -f infra/compose.backup.yaml --profile ops run --rm backup db` (và `… run --rm backup media`).
5. Kiểm tra chính bản sao lưu: `scripts/restore-drill.sh --env production`, lưu biên bản ở `docs/ops/drills/`.
6. Khôi phục vào DB managed với tên database thử: `restore.sh db latest bds_rehearsal`, rồi `restore.sh verify-db latest bds_rehearsal` (phải in `RESULT: PASS`).
7. Nạp ảnh vào bucket thử: `restore.sh media latest -rehearsal`, `restore.sh verify-media latest -rehearsal`,
   `restore.sh verify-references <media-set> bds_rehearsal -rehearsal`.
8. Chạy stack trên VM trỏ vào `bds_rehearsal` + bucket thử; Flyway validate khi backend khởi động; rebuild chỉ mục ES
   (`POST /api/v2/admin/search/index/rebuild` bằng tài khoản admin).
9. Kiểm tra qua hostname thử: `scripts/verify-headers.sh https://staging.nhadatchuan.online`,
   `scripts/verify-prerender.sh https://staging.nhadatchuan.online`; thủ công: đăng nhập, tìm kiếm, mở tin, xem ảnh,
   upload ảnh, gửi lead thử bằng tài khoản nội bộ, admin mở hồ sơ KYC (nhập lại mật khẩu).
10. Cộng thời gian các bước 4–8 (dòng `DURATION_MS` của `restore.sh`) → độ dài cửa sổ bảo trì cần xin.

**C. Cut-over (cửa sổ bảo trì đã thông báo, giờ thấp điểm)**
11. Dừng ghi: `docker compose -p bds-production rm -sf backend` trên laptop (người dùng thấy lỗi tạm; volume giữ nguyên).
    Dùng `rm -sf` thay vì `stop`: service production có `restart: always`, laptop khởi động lại thì container đã `stop`
    sẽ chạy lại (`docs/ops/PRODUCTION_TOPOLOGY.md` §8).
12. Sao lưu cuối (bước 4), khôi phục vào database production trên managed (`restore.sh db <set> bds`), `verify-db` PASS,
    nạp + `verify-media` + `verify-references` PASS.
13. Khởi động stack trên VM trỏ vào database/bucket production; rebuild ES; chạy lại bước 9 trên hostname thử.
14. Chuyển hostname công khai sang tunnel mới:
    `cloudflared tunnel route dns --overwrite-dns <tunnel-mới> nhadatchuan.online` (và `www`); chạy 2 connector:
    `docker compose -p bds-production --profile edge up -d --scale cloudflared=2`.
15. `scripts/verify-headers.sh https://nhadatchuan.online` và `scripts/verify-prerender.sh https://nhadatchuan.online`;
    theo dõi `infra/compose.observability.yaml` (5xx, p95, outbox, backup) 60 phút.

**Tiêu chí rollback** (quyết định trong cửa sổ, đề xuất tối đa 2 giờ sau bước 14; quá mốc này thì sửa tiến):
bất kỳ `verify-*` nào FAIL; `verify-headers.sh`/`verify-prerender.sh` lỗi; tỷ lệ 5xx hoặc p95 vượt ngưỡng chủ dự án đặt
từ baseline trong 15 phút liên tục; đăng nhập/tìm kiếm/upload thất bại.
**Cách rollback:** trỏ DNS về tunnel cũ (`cloudflared tunnel route dns --overwrite-dns <tunnel-cũ> nhadatchuan.online`),
`docker compose -p bds-production up -d --no-build backend` trên laptop. Dữ liệu ghi trên hệ mới sau bước 14 không tự quay về laptop:
ghi rõ trong biên bản và nhập lại thủ công nếu cần.

**D. Sau chuyển đổi**
16. Bật sao lưu trên VM (logical dump độc lập với nhà cung cấp, đồng bộ `BACKUP_DIR` ra ngoài) + giám sát + receiver
    cảnh báo thật; lịch diễn tập theo PRODUCTION_TOPOLOGY.md §6.
17. Sau 7–14 ngày ổn định: gỡ stack production khỏi laptop (`docker compose -p bds-production down`, giữ volume tới
    khi hủy có biên bản), xóa tunnel cũ (`cloudflared tunnel delete <tunnel-cũ>`), xoay các secret còn lại.

## 6. Quyết định chủ dự án cần chốt

| # | Quyết định | Lựa chọn / ghi chú | Người, ngày |
|---|---|---|---|
| 1 | Vùng lưu dữ liệu (trong nước hay Singapore) — sau ý kiến luật sư | | |
| 2 | Nhà cung cấp PostgreSQL và gói (HA hay không) | | |
| 3 | Nhà cung cấp object storage | | |
| 4 | Ngân sách tháng tối đa | | |
| 5 | RPO/RTO mục tiêu | Gợi ý: RPO ≤ 15 phút, RTO ≤ 60 phút | |
| 6 | Cửa sổ bảo trì và kênh thông báo người dùng | | |
| 7 | Ngưỡng rollback (5xx, p95) và người có quyền quyết | | |
| 8 | Người trực trong và sau cut-over; receiver cảnh báo | | |
| 9 | Thời hạn giữ volume cũ trên laptop trước khi hủy | | |
