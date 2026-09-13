# Trạng thái khắc phục bốn báo cáo ngày 13/09/2026

## Đã xử lý và có bằng chứng trong lần triển khai này

- Media eKYC chỉ chấp nhận object riêng tư tồn tại và thuộc đúng tài khoản; delete/cleanup không xóa object còn được hồ sơ eKYC hoặc xác minh tin tham chiếu.
- Chỉ chủ tin được nộp xác minh; eKYC phải ở trạng thái `VERIFIED` trước khi nộp và trước khi admin duyệt nhãn chính chủ.
- Kết quả Elasticsearch luôn được hydrate lại từ PostgreSQL với điều kiện tin `ACTIVE`; sort `LATEST`, `PRICE_ASC`, `PRICE_DESC`, `AREA_DESC` có khóa phụ ổn định. HTTP 400 từ Elasticsearch không còn bị nuốt.
- Người đăng vai trò USER/BROKER xem được lead của tin mình, reveal số điện thoại đã mã hóa khi có consent, cập nhật trạng thái chăm sóc; tài khoản khác vẫn bị chặn.
- Chống spam lead dùng cửa sổ 24 giờ và kiểm tra replay idempotency trước quota.
- Có màn hình người dùng gửi eKYC gồm mặt trước CCCD, mặt sau CCCD, ảnh chân dung; admin xem ảnh riêng tư bằng Bearer token và duyệt/từ chối thủ công.
- Tin showcase trong PostgreSQL được API/UI gắn nhãn rõ và bị chặn tiếp nhận thông tin liên hệ thật.
- Thanh toán thủ công có tạo, báo chuyển khoản, duyệt, từ chối có lý do và người dùng hủy trước khi báo chuyển khoản; order lưu snapshot gói và tài khoản ngân hàng.
- Rate limit dùng Redis với TTL; không tin trực tiếp `X-Forwarded-For`. SSE phát sau commit, có heartbeat và Nginx tắt buffering cho stream.
- Geocoding bỏ khóa `synchronized`, dùng slot Redis một request/giây toàn stack, URL/contact cấu hình bằng env.
- Outbox webhook có connect/read timeout, backoff và dead-letter sau 10 lần lỗi.
- Tách mẫu env demo/production, thêm Mailpit, Redis volume, tunnel Cloudflare là profile tùy chọn/token-based; sửa preflight, giới hạn upload Nginx 12 MB và metrics nội bộ.
- CI dùng Maven wrapper qua `sh`, Trivy pin SHA, static type check thật, dựng integration stack trước Playwright và dọn stack sau chạy.

## Bằng chứng đã chạy

- Backend Docker Java 17: 21 test, 0 failure, 0 error.
- Frontend TypeScript + Vite production build: thành công.
- Playwright live: navigation + dialog authentication 2/2 pass; visual regression, axe và overflow tại Chromium 320/768/1440, Android Chrome, iOS Safari, WebKit desktop và Firefox desktop: 20 pass trong lượt chính, WebKit search lỗi chụp ảnh tạm thời và pass khi retry riêng.
- PostgreSQL production đã áp dụng Flyway V015 và V016 thành công.
- Live smoke `nhadatchuan.online`: search trả 6 tin; `PRICE_ASC` đúng thứ tự; cả 6 tin mẫu có nhãn showcase; gửi lead vào showcase trả 409; 8 media object còn nguyên.

## Chưa thể tuyên bố hoàn tất từ một máy

- Usability test với người dùng thật, load/soak trên workload đích và backup-restore drill có đo RPO/RTO.
- Failover thật qua nhiều host cho PostgreSQL/Redis/MinIO, CDN/DNS và secret manager của nhà cung cấp production.
- SSE/realtime đa pod bền vững, Elasticsearch indexing theo outbox/bulk thay cho full sync định kỳ, email hoàn toàn qua durable queue.
- MFA secret riêng từng admin, audit chain có serialization/anchor ngoài hệ thống và MinIO credential least-privilege riêng cho ứng dụng.
- GitHub Actions chỉ được xác nhận sau khi push; branch protection/ruleset phải cấu hình trên GitHub.

Không dùng các hạng mục chưa chạy ở trên để tuyên bố hệ thống chịu được hàng triệu kết nối đồng thời hoặc đạt SLA production.
