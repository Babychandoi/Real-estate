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
- Audit hardcode/action: trang chủ, tìm kiếm, chi tiết, đăng tin, moderation, CMS và quản lý dự án không còn dựng dữ liệu nghiệp vụ giả khi API rỗng/lỗi; mọi trạng thái có loading, empty hoặc error riêng.
- Tìm kiếm và bản đồ dùng cùng tập kết quả PostgreSQL/Elasticsearch đã lọc; marker được đồng bộ theo tin đang chọn, reset bộ lọc cập nhật URL một lần và lỗi API không giữ lại kết quả cũ.
- Chi tiết tin dùng `imageUrls` thật từ API/MinIO, quay lại `/search` và gửi báo cáo vi phạm qua API thật. Các trang giới thiệu, điều khoản, quyền riêng tư, liên hệ và 404 đã có route riêng.
- Form đăng tin không còn dữ liệu mẫu, không công bố định giá/điểm AI-GIS giả, lưu đủ các trường backend hỗ trợ và lưu revision mới nhất trước khi submit. Hai API giả lập còn giữ contract nhưng trả RFC Problem Details HTTP 501.
- Duyệt tin/CMS bắt buộc ghi lý do hoặc ghi chú; bỏ các nhãn pháp lý, SLA, chuẩn hóa, kích thước ảnh và thao tác toast-only không có bằng chứng backend. Quản lý dự án được thay bằng luồng list/search/create API thật.
- Bàn lead/báo xấu đã nối đủ thao tác tạm ẩn, giải quyết và bác bỏ với ghi chú bắt buộc; lỗi API được hiển thị và không giả cập nhật thành công.
- Bỏ nút sửa tin dẫn sai sang tạo tin mới cho tới khi có contract revision riêng tư; bỏ toggle eKYC vô hiệu hóa và cam kết SLA 8 giờ khỏi form đăng tin, dẫn người dùng sang hồ sơ eKYC thật.

## Bằng chứng đã chạy

- Backend Docker Java 17: 21 test, 0 failure, 0 error.
- Frontend TypeScript + Vite production build: thành công.
- Playwright live: navigation + dialog authentication 2/2 pass; visual regression, axe và overflow tại Chromium 320/768/1440, Android Chrome, iOS Safari, WebKit desktop và Firefox desktop: 20 pass trong lượt chính, WebKit search lỗi chụp ảnh tạm thời và pass khi retry riêng.
- PostgreSQL production đã áp dụng Flyway V015 và V016 thành công.
- Live smoke `nhadatchuan.online`: search trả 6 tin; `PRICE_ASC` đúng thứ tự; cả 6 tin mẫu có nhãn showcase; gửi lead vào showcase trả 409; 8 media object còn nguyên.
- Sau remediation hardcode: Docker backend/frontend build thành công; backend 21/21 test pass; toàn bộ container gồm Cloudflare tunnel healthy; `/`, `/search`, `/about` và `/api/v1/listings/search` trên domain trả HTTP 200.

## Chưa thể tuyên bố hoàn tất từ một máy

- Usability test với người dùng thật, load/soak trên workload đích và backup-restore drill có đo RPO/RTO.
- Failover thật qua nhiều host cho PostgreSQL/Redis/MinIO, CDN/DNS và secret manager của nhà cung cấp production.
- SSE/realtime đa pod bền vững, Elasticsearch indexing theo outbox/bulk thay cho full sync định kỳ, email hoàn toàn qua durable queue.
- MFA secret riêng từng admin, audit chain có serialization/anchor ngoài hệ thống và MinIO credential least-privilege riêng cho ứng dụng.
- GitHub Actions chỉ được xác nhận sau khi push; branch protection/ruleset phải cấu hình trên GitHub.

Không dùng các hạng mục chưa chạy ở trên để tuyên bố hệ thống chịu được hàng triệu kết nối đồng thời hoặc đạt SLA production.
