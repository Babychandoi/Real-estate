# Cấu hình môi trường production

Máy chủ tạm thời dùng file `.env` ở thư mục gốc. File này bị Git bỏ qua và không được gửi lên repository. Sau khi sửa biến runtime, chạy `docker compose up -d --force-recreate`; thay build arg frontend thì chạy thêm `--build`.

## Biến có thể thay trực tiếp

| Biến | Giá trị hiện tại/mục đích | Cách thay |
| --- | --- | --- |
| `APP_BASE_URL`, `PUBLIC_HOST`, `K8S_PUBLIC_HOST` | Domain công khai | Đổi đồng thời khi chuyển domain. |
| `APP_ALLOWED_ORIGINS` | Danh sách origin HTTPS | Chỉ nhập origin thật, phân cách bằng dấu phẩy; production không chấp nhận HTTP. |
| `APP_LOG_LEVEL` | Mức log ứng dụng | Nên giữ `INFO`; chỉ dùng `DEBUG` tạm thời khi xử lý lỗi. |
| `SPRINGDOC_API_DOCS_ENABLED` | Công khai OpenAPI | Giữ `false` ở production. |
| `CLAMAV_TIMEOUT_MS` | Thời gian chờ quét file | Có thể tăng nếu file lớn hoặc máy yếu. |
| `OUTBOX_POLL_DELAY_MS` | Chu kỳ đọc outbox | Chỉ ảnh hưởng worker khi outbox được bật. |
| `APP_MEDIA_ALLOWED_HOSTS` | Host ảnh được hệ thống tin cậy | Chỉ thêm domain/CDN do dự án kiểm soát. |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | Máy chủ SMTP | Hiện dùng Gmail SMTP cổng 587. |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Tài khoản và App Password SMTP | Là secret; không commit và phải xoay nếu lộ. |
| `SPRING_MAIL_SMTP_AUTH`, `SPRING_MAIL_STARTTLS_ENABLE`, `SPRING_MAIL_STARTTLS_REQUIRED` | Bảo mật SMTP | Giữ `true` với Gmail cổng 587. |
| `APP_MAIL_FROM` | Địa chỉ người gửi | Phải là địa chỉ được tài khoản/provider SMTP cho phép gửi. |

## Secret chỉ thay khi xoay đồng bộ

| Biến | Lưu ý |
| --- | --- |
| `POSTGRES_PASSWORD` và `SPRING_DATASOURCE_PASSWORD` | Hai giá trị phải giống nhau. Với database đã có dữ liệu, phải `ALTER ROLE` trước khi recreate container. |
| `REDIS_PASSWORD` và `SPRING_DATA_REDIS_PASSWORD` | Hai giá trị phải giống nhau và recreate Redis/backend cùng lúc. |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | Recreate MinIO/backend cùng lúc. Không đổi tùy tiện khi đang có phiên upload. |
| `PII_ENCRYPTION_KEY` | Không được thay nếu chưa re-encrypt dữ liệu PII cũ; thay trực tiếp sẽ làm mất khả năng giải mã. |
| `PII_INDEX_KEY` | Không được thay nếu chưa dựng lại blind index PII. |
| `APP_ADMIN_MFA_SECRET_BASE64` | Thay sẽ làm mã TOTP admin/moderator hiện tại đổi ngay. |
| `OUTBOX_SIGNING_KEY` | Phải đồng bộ với hệ thống nhận webhook trước khi bật outbox. |
| Credential Cloudflare Tunnel | Đang mount read-only từ `%USERPROFILE%/.cloudflared`; chuyển máy phải chép credential hoặc cấp tunnel mới. |

## Cờ production

- `APP_MODE=production` bật kiểm tra fail-fast và không seed lại tài khoản demo.
- `SPRING_PROFILES_ACTIVE=production` đánh dấu đúng môi trường runtime.
- `FEATURE_REAL_KYC=false` là đúng: hệ thống đang nhận ảnh CCCD/mặt người dùng và admin duyệt thủ công, không gọi nhà cung cấp eKYC tự động.
- `FEATURE_REAL_TRANSACTIONS=false` là đúng: website không làm giao dịch bất động sản; thanh toán gói đăng tin được admin đối soát thủ công.
- `OUTBOX_ENABLED=false` và `OUTBOX_WEBHOOK_URL` để trống là cặp cấu hình hợp lệ. Chỉ bật khi có endpoint HTTPS thật và bên nhận đã kiểm tra chữ ký.

## Cấu hình không nằm trong `.env`

Tên ngân hàng, BIN ngân hàng, số tài khoản, chủ tài khoản và email nhận thông báo được quản lý trong trang admin **Gói dịch vụ / Đối soát**. Không đặt các dữ liệu này vào image frontend hay Git.

## Giới hạn của máy chủ tạm thời

Compose hiện là một node và lưu secret trong `.env`; đây là runtime production tạm thời, chưa phải hạ tầng production HA. Khi chuyển sang cụm Kubernetes chính thức, secret phải lấy từ Vault/External Secrets và dữ liệu phải được backup/restore có kiểm chứng.
