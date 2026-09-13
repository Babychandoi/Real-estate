# Đánh giá dưới vai trò người dùng: setup và mua/bán BĐS

> Repository: [Babychandoi/Real-estate](https://github.com/Babychandoi/Real-estate)  
> Commit được đánh giá: [4f52c76](https://github.com/Babychandoi/Real-estate/commit/4f52c76f99d982aae3d7fd37dc9b76d647599b80)  
> Ngày đánh giá: 2026-09-11  
> Phạm vi: đọc toàn bộ cây mã nguồn, kiểm tra cấu hình, build frontend; chưa thực hiện giao dịch thật hoặc kiểm thử người dùng có giám sát.

## 1. Kết luận nhanh

**Điểm trải nghiệm người dùng hiện tại: 3,8/10 — mức Alpha/Pilot.**

Giao diện tạo cảm giác sản phẩm đã có nhiều chức năng: tìm kiếm, xem chi tiết, đăng tin 4 bước, quản lý tin, xác thực pháp lý, lead và đặt cọc. Tuy nhiên, nhiều luồng đang là demo hoặc giả lập. Người dùng chưa thể tin rằng đăng nhập, xác minh OTP, eKYC hay escrow là giao dịch thật.

| Mục tiêu | Đánh giá | Kết luận |
|---|---:|---|
| Khởi động để xem demo | 6/10 | Có hướng dẫn một lệnh Docker, cấu trúc rõ |
| Đăng ký/đăng nhập | 2/10 | Xử lý hoàn toàn ở trình duyệt, không có tài khoản backend thật |
| Tìm kiếm BĐS | 5/10 | Có bộ lọc và danh sách; bản đồ và dữ liệu còn giả lập |
| Đăng bán/cho thuê | 4/10 | Wizard tốt về hình thức; ảnh chỉ nhập URL, chủ sở hữu dùng ID demo |
| Liên hệ người bán | 2/10 | Có hai luồng khác nhau; một luồng báo thành công kể cả khi API lỗi |
| Đặt cọc/giao dịch | 1/10 | Chỉ là mô phỏng trạng thái, không được dùng với tiền thật |
| Mobile/responsive | 4/10 | Nhiều breakpoint tốt nhưng thiếu điều hướng mobile chính |
| Mức độ tạo niềm tin | 2/10 | Thông điệp “100%” và “escrow” vượt quá năng lực thực tế |

**Khuyến nghị phát hành:** chỉ nên chạy dưới nhãn **“Demo — không thực hiện giao dịch hoặc tải giấy tờ thật”**. Chưa mở cho khách hàng đại trà.

## 2. Trải nghiệm setup nhanh

### Điểm tốt

- README nêu rõ Java, Node, Docker và đường dẫn truy cập.
- Có `.env.example`, Dockerfile hai tầng và Docker Compose cho PostgreSQL, Redis, backend, frontend.
- Frontend cài bằng `npm ci` và `npm run build` thành công trong lần audit này.
- Flyway giúp tạo schema tự động; health endpoint và Swagger có sẵn.

### Các điểm khiến “một lệnh là chạy” chưa đáng tin cậy

1. README yêu cầu `docker compose up -d` nhưng không yêu cầu sao chép/kiểm tra `.env`. Compose tự dùng mật khẩu `change-me`; người mới dễ vô tình chạy cấu hình yếu.
2. [V001](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/backend/src/main/resources/db/migration/V001__init_schema.sql) không seed người dùng. Trong khi [ListingController](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/backend/src/main/java/com/company/bds/listing/api/ListingController.java#L38-L69) luôn gán owner ID demo. Trên PostgreSQL thật, tạo tin có khả năng lỗi khóa ngoại vì owner này chưa tồn tại.
3. [AuthContext](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/frontend/app/shared/auth/AuthContext.tsx#L42-L92) chứa tài khoản/mật khẩu demo và lưu tài khoản đăng ký vào `localStorage`. Xóa dữ liệu trình duyệt là mất tài khoản.
4. Biến `VITE_PUBLIC_API_BASE_URL` được đặt ở runtime container, nhưng ứng dụng Vite là static bundle; biến Vite phải được cấp lúc build. Hiện bundle hoạt động nhờ giá trị fallback trong code, không nhờ cấu hình Compose như người vận hành có thể hiểu.
5. Dockerfile frontend dùng `npm install` và chỉ copy `package.json`, làm build container không tái lập đúng theo lockfile.
6. Chưa có lệnh seed dữ liệu mẫu, tài khoản demo server-side, Makefile/task runner, kiểm tra preflight hoặc script smoke test.

### Setup đề xuất

```bash
cp .env.example .env
# thay toàn bộ giá trị change-me
docker compose --profile demo up --build -d
./scripts/smoke-test.sh
```

Nên có hai profile tách biệt:

- `demo`: seed dữ liệu giả, banner demo, tắt tải giấy tờ và giao dịch tiền.
- `production`: không seed, từ chối khởi động nếu secret mặc định/rỗng, không public DB/Redis.

## 3. Hành trình người mua

### Luồng kỳ vọng

1. Tìm theo khu vực/giá/loại hình.
2. Xem ảnh, vị trí, pháp lý, lịch sử giá và độ tin cậy người đăng.
3. Lưu tin/so sánh.
4. Nhắn tin hoặc đặt lịch xem nhà.
5. Xác minh hai bên, xem hợp đồng, đặt cọc qua đơn vị thanh toán được cấp phép.

### Quan sát hiện tại

- Trang chủ có hero rõ, thẻ tin dễ quét và CTA nổi bật.
- Nút tìm kiếm, nút bộ lọc và các chip gợi ý trên [HomePage](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/frontend/app/routes/_public.home.tsx#L84-L111) chưa gắn hành động. Người dùng nhập từ khóa nhưng không được chuyển tới kết quả.
- Trang search có trải nghiệm split-view tốt trên desktop, nhưng phần bản đồ là mô phỏng tọa độ trên nền tĩnh, chưa phải bản đồ tương tác thật.
- Không có yêu thích, tìm kiếm đã lưu, cảnh báo giá, lịch sử giá, lịch hẹn, chat hoặc hồ sơ người bán đủ tin cậy.
- Form lead ở trang chi tiết gọi đúng endpoint, nhưng modal lead khác gọi sai endpoint và payload. Modal này vẫn hiển thị thành công khi request thất bại: [LeadConsultationModal](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/frontend/app/features/lead/ui/LeadConsultationModal.tsx#L95-L137).
- Route hợp đồng không được bảo vệ ở frontend; backend cũng không kiểm tra buyer/seller.

## 4. Hành trình người bán/môi giới

### Điểm tốt

- Wizard 4 bước chia nhỏ việc nhập thông tin.
- Có lưu nháp, tính điểm chất lượng, ước lượng giá, preview và gửi duyệt.
- Có kho tin và workspace môi giới với trạng thái rõ.

### Điểm cản trở bán hàng thật

- Không tải file từ máy; người dùng phải dán URL ảnh/CDN.
- Không geocode địa chỉ, kéo ghim bản đồ, kiểm tra trùng tin thật hoặc quản lý album.
- Không có xác thực tài khoản server-side; mọi người dùng cùng owner ID demo.
- Nhiều tác vụ trong workspace chỉ gọi `alert()`, ví dụ xuất báo cáo và gọi khách.
- Chưa có gói dịch vụ, thanh toán phí đăng tin, hóa đơn, lịch hết hạn hoặc quota thực.
- Không có cơ chế nháp tự động an toàn giữa các thiết bị.

## 5. Những ưu điểm đáng giữ

- Nội dung tiếng Việt và thuật ngữ BĐS dễ hiểu.
- Luồng trạng thái tin đăng, kiểm duyệt revision và lý do từ chối có cấu trúc tốt.
- Có consent khi gửi lead và có định hướng làm mờ PII.
- Skeleton/loading, empty state và layout responsive đã được suy nghĩ từ đầu.
- Tài liệu nghiệp vụ/Waterfall đầy đủ hơn mức thường thấy ở một bản alpha.

## 6. Kế hoạch cải thiện ưu tiên

### P0 — trước khi cho khách ngoài nhóm dự án dùng

- Gắn banner “Demo”, chặn nhập dữ liệu thật và chặn giao dịch tiền.
- Sửa đăng nhập thành API backend; loại bỏ seed password/localStorage account.
- Sửa luồng lead: một component, một contract API, không bao giờ báo thành công khi server lỗi.
- Seed demo server-side có kiểm soát hoặc bỏ hoàn toàn `DEFAULT_DEMO_USER_ID`.
- Làm nút tìm kiếm, bộ lọc và gợi ý ở trang chủ hoạt động.
- Bảo vệ route hợp đồng, eKYC, quản trị và chỉ hiện dữ liệu thuộc người dùng.

### P1 — MVP mua/bán có thể thử nghiệm

- Upload ảnh thật qua object storage, nén/WebP/AVIF, xoay/sắp xếp/xóa ảnh.
- Map thật, autocomplete địa chỉ, bán kính và khu vực hành chính.
- Hồ sơ người đăng, lịch hẹn xem nhà, yêu thích, chia sẻ, báo xấu.
- Trạng thái lỗi/retry rõ; không dùng mock fallback trong production.
- Luồng kiểm duyệt và thông báo email/SMS thật.

### P2 — trước giao dịch thương mại

- Tích hợp eKYC, ký số, thanh toán/escrow với nhà cung cấp hợp pháp.
- Hợp đồng có version, audit trail bất biến, xác nhận hai bên và cơ chế tranh chấp.
- Kiểm thử usability với tối thiểu 5 người mua và 5 người bán mỗi vòng; đo tỷ lệ hoàn thành và thời gian hoàn thành.

## 7. Tiêu chí nghiệm thu trải nghiệm

- Người mới setup demo trong dưới 10 phút theo README sạch.
- Ít nhất 90% người dùng thử hoàn thành tìm kiếm → xem chi tiết → gửi liên hệ mà không cần trợ giúp.
- Ít nhất 85% người bán hoàn thành đăng tin đầu tiên trong dưới 8 phút.
- Không có “false success”; mọi thành công phải có xác nhận từ backend.
- Không có CTA chết; toàn bộ nút chính có hành động, loading, success và error.
- Mobile 360 px sử dụng được đầy đủ menu và luồng đăng tin.

## 8. Phán quyết

**Dễ xem demo, chưa đủ thuận tiện và đáng tin để mua/bán thật.** Giá trị lớn nhất hiện tại là prototype nghiệp vụ và UI; cần hoàn thiện identity, dữ liệu, media, contact và transaction trước khi gọi là marketplace.
