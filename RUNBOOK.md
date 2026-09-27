# Runbook vận hành

## Phát hành

1. Chạy backend `./mvnw verify`, frontend `npm ci && npm audit && npm run build`.
2. Tạo và quét image; triển khai migration trên bản sao dữ liệu trước.
3. Đặt `APP_MODE=production`, secret mạnh, khóa PII, origin chính xác, giữ MinIO ở release đã vá hoặc mới hơn và giữ các capability nhà cung cấp ở `false` nếu chưa nghiệm thu. Compose hiện build MinIO từ source `RELEASE.2025-10-15T17-29-55Z`; phải quét lại image khi nâng phiên bản.
4. Triển khai canary, kiểm tra `/actuator/health/readiness`, `/health`, đăng nhập, tìm kiếm và gửi lead.
5. Theo dõi tỷ lệ lỗi, p95 và backlog; rollback image nếu vượt SLO.

## Sao lưu và khôi phục

Sao lưu PostgreSQL và bucket MinIO mã hóa hằng ngày, giữ bản sao ngoài cụm và kiểm tra checksum. Hằng quý phải khôi phục đồng thời database + object storage vào môi trường cô lập, chạy Flyway validate và smoke test upload/đọc ảnh; ghi nhận RPO/RTO thực tế. Không coi một file dump hoặc bản sao bucket chưa thử khôi phục là bản sao lưu đạt chuẩn.

Công cụ: `infra/compose.backup.yaml` (pg_dump hằng giờ + object hằng ngày, mã hóa age, ra `BACKUP_DIR` ngoài repository), `infra/compose.pitr.yaml` + `infra/compose.pitr-backup.yaml` (PITR), `scripts/restore-drill.sh` (diễn tập, biên bản trong `docs/ops/drills/`). Quy trình, RPO/RTO và rollback: `docs/ops/PRODUCTION_TOPOLOGY.md`; phân loại dữ liệu sao lưu: `docs/ops/BACKUP_CLASSIFICATION.md`. Không bao giờ commit bản sao lưu vào Git.

## Giám sát và cảnh báo

`infra/compose.observability.yaml` (Prometheus, Alertmanager, Grafana, exporter; chỉ nghe trên 127.0.0.1). Xử lý từng cảnh báo: `docs/operations/ALERT_RUNBOOK.md`. Kiểm tra header bảo mật ở response cuối sau mỗi thay đổi edge: `scripts/verify-headers.sh https://<domain>`.

## Sự cố P0

Cô lập luồng ảnh hưởng, tắt capability liên quan, bảo toàn log/audit, luân chuyển secret nghi ngờ, thông báo đầu mối pháp lý và lập timeline. Không sửa/xóa audit event tại chỗ.
