# Runbook vận hành

## Phát hành

1. Kiểm thử backend chạy trên PostgreSQL/PostGIS thật (không còn H2): bật hạ tầng test dùng chung `scripts/test-infra.sh up`,
   nạp biến `eval "$(scripts/test-infra.sh env)"`, rồi `cd backend && sh mvnw -B -ntp verify` (JDK 17). Thiếu
   `BDS_TEST_PG_URL` thì test dừng ngay với thông báo hướng dẫn. Frontend: `npm ci && npm audit && npm run build`.
   Lưu ý: `backend/Dockerfile` build image với `-DskipTests`, nên image không tự chứng minh test xanh — cổng phát hành là
   job CI `backend-tests` (cần bật làm required check trên nhánh chính).
2. Tạo và quét image; triển khai migration trên bản sao dữ liệu trước.
3. Đặt `APP_MODE=production`, secret mạnh, khóa PII, origin chính xác, giữ MinIO ở release đã vá hoặc mới hơn và giữ các capability nhà cung cấp ở `false` nếu chưa nghiệm thu. Compose hiện build MinIO từ source `RELEASE.2025-10-15T17-29-55Z`; phải quét lại image khi nâng phiên bản.
4. Triển khai canary, kiểm tra `/actuator/health/readiness`, `/health`, đăng nhập, tìm kiếm và gửi lead.
5. Theo dõi tỷ lệ lỗi, p95 và backlog; rollback image nếu vượt SLO.

## Hàng đợi công việc

Mọi email (xác minh, đặt lại mật khẩu, thông báo đối soát) đi qua hàng đợi bền vững `background_jobs` (queue `email`).
- Tín hiệu: `/actuator/health` thành phần `jobWorker` (DOWN khi worker bật nhưng dừng/treo), metric
  `bds_jobs_lag_seconds`, `bds_jobs_dead`, `bds_jobs_worker_running`, `bds_jobs_metrics_age_seconds`; luật cảnh báo đề xuất:
  `docs/audit-2026-09-27/streams/s0-be/alerts.rules.yml`.
- Worker treo (queue bị tạm dừng, `pausedQueues` trong health): kiểm tra phụ thuộc (SMTP), sau đó khởi động lại backend.
- Job chết (dead-letter) đã bị xóa nội dung bí mật (liên kết một lần, địa chỉ nhận), nên không gửi lại được: người dùng yêu
  cầu lại liên kết; đơn đối soát vẫn nằm trong hàng chờ quản trị. Xem lý do: `SELECT queue, last_error, count(*) FROM
  background_jobs WHERE dead_lettered_at IS NOT NULL GROUP BY 1, 2;`. Job chết được xóa sau 30 ngày.
- Công cụ một lần trỏ vào CSDL dùng chung phải chạy với `--app.jobs.enabled=false` (seeder UAT tự tắt worker và lịch chạy).

## Sao lưu và khôi phục

Sao lưu PostgreSQL và bucket MinIO mã hóa hằng ngày, giữ bản sao ngoài cụm và kiểm tra checksum. Hằng quý phải khôi phục đồng thời database + object storage vào môi trường cô lập, chạy Flyway validate và smoke test upload/đọc ảnh; ghi nhận RPO/RTO thực tế. Không coi một file dump hoặc bản sao bucket chưa thử khôi phục là bản sao lưu đạt chuẩn.

Công cụ: `infra/compose.backup.yaml` (pg_dump hằng giờ + object hằng ngày, mã hóa age, ra `BACKUP_DIR` ngoài repository), `infra/compose.pitr.yaml` + `infra/compose.pitr-backup.yaml` (PITR), `scripts/restore-drill.sh` (diễn tập, biên bản trong `docs/ops/drills/`). Quy trình, RPO/RTO và rollback: `docs/ops/PRODUCTION_TOPOLOGY.md`; phân loại dữ liệu sao lưu: `docs/ops/BACKUP_CLASSIFICATION.md`. Không bao giờ commit bản sao lưu vào Git.

## Giám sát và cảnh báo

`infra/compose.observability.yaml` (Prometheus, Alertmanager, Grafana, exporter; chỉ nghe trên 127.0.0.1). Xử lý từng cảnh báo: `docs/operations/ALERT_RUNBOOK.md`. Kiểm tra header bảo mật ở response cuối sau mỗi thay đổi edge: `scripts/verify-headers.sh https://<domain>`.

## Sự cố P0

Cô lập luồng ảnh hưởng, tắt capability liên quan, bảo toàn log/audit, luân chuyển secret nghi ngờ, thông báo đầu mối pháp lý và lập timeline. Không sửa/xóa audit event tại chỗ.
