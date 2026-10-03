# Cấu hình môi trường production

Máy chủ tạm thời dùng file `.env` ở thư mục gốc. File này bị Git bỏ qua và không được gửi lên repository. Sau khi sửa biến runtime, tạo lại đúng các dịch vụ dùng biến đó: `docker compose -p bds-production -f docker-compose.yml -f infra/compose.apple-silicon.yaml up -d --no-deps --force-recreate <dịch vụ...>`; thay build arg frontend thì chạy thêm `--build`. Luôn dùng `-p bds-production` và overlay Apple Silicon (lý do: `docs/operations/REBOOT_RECOVERY.md` §3).

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
| `APP_SECURITY_TRUSTED_PROXIES` | Dải CIDR của proxy mà backend tin `X-Real-IP`/`X-Forwarded-For` (mặc định loopback + dải Docker) | Chỉ thêm dải của proxy thật; sai dải làm rate limit gộp mọi người vào một IP hoặc cho phép giả mạo IP. CIDR sai làm backend từ chối khởi động. |
| `BDS_RESTART_POLICY` | Restart policy của mọi service chạy lâu dài. Mặc định trong compose là `unless-stopped` | **Production đặt `BDS_RESTART_POLICY=always`** (container đã bị dừng trước khi tắt máy vẫn chạy lại khi Docker khởi động). Demo/dev để trống. Container đang chạy không tự đổi theo biến: dùng `docker update --restart=always`, xem `docs/operations/REBOOT_RECOVERY.md` §4. |
| `PUBLIC_HOST` (cho frontend) | Host chuẩn của chuyển hướng HTTP→HTTPS trong Nginx (`BDS_PUBLIC_HOST`), ví dụ `nhadatchuan.online` | Chỉ tên hostname hợp lệ, không scheme, port, khoảng trắng hay biến Nginx; giá trị sai bị từ chối trước khi render cấu hình. Để trống thì Nginx dùng Host của request (demo/local). Đổi xong: tạo lại frontend với `--no-deps`. |
| `APP_RATE_LIMIT_ENABLED` | Bật/tắt rate limiter (mặc định `true`) | Chỉ tắt tạm khi xử lý sự cố. |
| `RATE_LIMIT_LIMIT_MULTIPLIER` | Nhân mọi giới hạn tần suất (mặc định `1`) | Giữ `1` ở production; chỉ stack demo/E2E nâng lên. |
| `BACKUP_DIR`, `BACKUP_AGE_RECIPIENTS`, `BACKUP_ENV` | Thư mục sao lưu ngoài repository, public key age, nhãn môi trường | Xem `docs/ops/PRODUCTION_TOPOLOGY.md` mục 6. |
| `GRAFANA_ADMIN_PASSWORD`, `PG_EXPORTER_USER`, `PG_EXPORTER_PASSWORD` | Overlay giám sát | Mật khẩu Grafana là secret; exporter nên dùng role `pg_monitor` riêng. |

## Secret chỉ thay khi xoay đồng bộ

| Biến | Lưu ý |
| --- | --- |
| `POSTGRES_PASSWORD` và `SPRING_DATASOURCE_PASSWORD` | Hai giá trị phải giống nhau. Với database đã có dữ liệu, phải `ALTER ROLE` trước khi recreate container. |
| `REDIS_PASSWORD` và `SPRING_DATA_REDIS_PASSWORD` | Hai giá trị phải giống nhau và recreate Redis/backend cùng lúc. |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | Recreate MinIO/backend cùng lúc. Không đổi tùy tiện khi đang có phiên upload. |
| `PII_ENCRYPTION_KEY` | Không được thay nếu chưa re-encrypt dữ liệu PII cũ; thay trực tiếp sẽ làm mất khả năng giải mã. |
| `PII_INDEX_KEY` | Không được thay nếu chưa dựng lại blind index PII. |
| `OUTBOX_SIGNING_KEY` | Phải đồng bộ với hệ thống nhận webhook trước khi bật outbox. |
| `RATE_LIMIT_KEY_PEPPER` | Bí mật trộn vào băm khóa rate limit trong Redis. Đổi giá trị làm mọi bộ đếm đang chạy về 0 (chấp nhận được); không log. |
| Private key age của bản sao lưu | Không bao giờ nằm trên máy production hay trong `.env`; chỉ mang vào khi khôi phục/diễn tập. Mất key = mất mọi bản sao lưu. |
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
