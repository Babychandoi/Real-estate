# Runbook vận hành

## Phát hành

1. Chạy backend `./mvnw verify`, frontend `npm ci && npm audit && npm run build`.
2. Tạo và quét image; triển khai migration trên bản sao dữ liệu trước.
3. Đặt `APP_MODE=production`, secret mạnh, khóa PII, origin chính xác, giữ MinIO ở release đã vá hoặc mới hơn và giữ các capability nhà cung cấp ở `false` nếu chưa nghiệm thu. Compose hiện build MinIO từ source `RELEASE.2025-10-15T17-29-55Z`; phải quét lại image khi nâng phiên bản.
4. Triển khai canary, kiểm tra `/actuator/health/readiness`, `/health`, đăng nhập, tìm kiếm và gửi lead.
5. Theo dõi tỷ lệ lỗi, p95 và backlog; rollback image nếu vượt SLO.

## Sao lưu và khôi phục

Sao lưu PostgreSQL và bucket MinIO mã hóa hằng ngày, giữ bản sao ngoài cụm và kiểm tra checksum. Hằng quý phải khôi phục đồng thời database + object storage vào môi trường cô lập, chạy Flyway validate và smoke test upload/đọc ảnh; ghi nhận RPO/RTO thực tế. Không coi một file dump hoặc bản sao bucket chưa thử khôi phục là bản sao lưu đạt chuẩn.

## Sự cố P0

Cô lập luồng ảnh hưởng, tắt capability liên quan, bảo toàn log/audit, luân chuyển secret nghi ngờ, thông báo đầu mối pháp lý và lập timeline. Không sửa/xóa audit event tại chỗ.
