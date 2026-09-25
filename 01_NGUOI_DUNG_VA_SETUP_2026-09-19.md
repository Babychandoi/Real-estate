# Đánh giá người dùng và setup

Snapshot: [`b8d66bf`](https://github.com/Babychandoi/Real-estate/commit/b8d66bf251c117b2775a6b586a55ea8262ccaaff) — 2026-09-19

## Kết luận

Khả năng sử dụng đã tăng đáng kể: hồ sơ, avatar, email, quên mật khẩu, thư viện tin, lead inbox, billing và admin workspace đều có luồng thật. Điểm chặn lớn nhất là **nộp eKYC không hoạt động với payload hiện tại**; do KYC được yêu cầu trước đăng tin và xem liên hệ, lỗi này làm đứt hai hành trình chuyển đổi cốt lõi.

Setup demo đã thực tế hơn nhờ `.env.demo.example`, Mailpit, MinIO, ClamAV, Redis và Elasticsearch; GitHub CI chứng minh Compose có thể dựng trên Ubuntu. Tài liệu vẫn thiên về PowerShell và có nhiều mô tả lỗi thời.

## Hành trình người dùng

| Hành trình | Trạng thái | Nhận xét |
|---|---|---|
| Đăng ký → xác minh email → đăng nhập | Đạt cơ bản | Có token xác minh, resend và thông báo; cần test browser đầy đủ |
| Quên/đặt lại mật khẩu | Đạt | Token hash, 30 phút, dùng một lần; thu hồi phiên sau reset |
| Cập nhật hồ sơ/avatar | Đạt | Upload và avatar public có fallback |
| Nộp eKYC | **Không đạt/P0** | Payload frontend thiếu trường backend bắt buộc; quy tắc số CCCD cũng lệch |
| Tạo/lưu/chỉnh sửa/nộp tin | Đạt một phần | Edit đã hoạt động; submit thẳng làm mất mã tỉnh/huyện/xã |
| Ẩn/hiện tin của tôi | Đạt | API thật; lỗi thao tác chưa hiển thị cho người dùng |
| Tìm kiếm/lọc/sắp xếp/bản đồ | Đạt một phần | Dữ liệu thật; không phân trang/load-more, URL state chưa nhất quán |
| Xem chi tiết/gửi lead | Đạt có điều kiện | Backend yêu cầu KYC VERIFIED; UI vẫn mở modal trước rồi mới báo lỗi |
| Quản lý lead | Đạt tốt hơn | Có phân trang, tổng số, filter/search, consent và reveal contact |
| So sánh | Chưa hoàn thiện | Không có UI chọn tin từ search; thiếu `ids` thì tự lấy 2–3 tin đầu |
| Mua gói/VietQR | Luồng có thật nhưng P0 dữ liệu | Có tạo/report/cancel/reconcile; snapshot order không được dùng đúng |
| Khiếu nại report | Không đạt | Route bị giới hạn cho ADMIN/MODERATOR, chủ tin không thể gọi |

## Phát hiện chi tiết

### U-01 — P0: eKYC bị chặn bởi hợp đồng API không khớp

Backend khai báo `SubmitKycRequest.userId` là `@NotNull`, nhưng controller bỏ qua giá trị đó và dùng `CurrentUser.id(authentication)`. Frontend gửi trực tiếp `form` không có `userId`. Ngoài ra HTML chấp nhận 9–12 chữ số trong khi backend yêu cầu đúng 12.

Bằng chứng:

- [`SubmitKycRequest.java` dòng 9–24](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/verification/api/request/SubmitKycRequest.java#L9-L24)
- [`KycController.java` dòng 38–49](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/verification/api/KycController.java#L38-L49)
- [`_account.kyc.tsx` dòng 62–67 và 90](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_account.kyc.tsx#L62-L90)

Khuyến nghị: xóa `userId` khỏi DTO public, giữ identity hoàn toàn server-side; thống nhất quy tắc CCCD; thêm integration/E2E cho tài khoản mới → upload ba ảnh → submit → pending.

### U-02 — P1: submit tin lần đầu làm mất mã địa giới

Nhánh “Lưu bản nháp” và “cập nhật draft” gửi `provinceCode`, `districtCode`, `wardCode`. Nhánh “Nộp duyệt” khi chưa có `listingId` tạo draft mới nhưng bỏ cả ba trường. Người dùng hoàn tất wizard rồi nộp thẳng có thể lưu địa chỉ không đầy đủ.

Bằng chứng: [`_public.listings.new.tsx` dòng 126–170 và 183–235](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_public.listings.new.tsx#L126-L235).

Khuyến nghị: dùng một hàm `buildListingPayload()` duy nhất cho create/update/submit và contract test payload.

### U-03 — P1: giao diện tuyên bố tự động lưu nhưng chỉ lưu khi bấm nút

`handleSaveDraft` chỉ được gọi theo hành động thủ công; `autosaveTime` được cập nhật sau lần lưu đó. Không có interval/debounce effect tự lưu, nhưng màn hình hiển thị “Đã tự động lưu”. Đây là copy gây hiểu nhầm và có thể làm mất dữ liệu.

Bằng chứng: [`_public.listings.new.tsx` dòng 126–180 và 360–370](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_public.listings.new.tsx#L126-L180).

Khuyến nghị: hoặc triển khai autosave debounce với trạng thái dirty/saving/saved/error, hoặc đổi thành “Đã lưu bản nháp lúc…”.

### U-04 — P2: điểm hoàn thiện hiển thị `/100` nhưng tối đa chỉ 80

Công thức hiện tại cộng tối đa 20 + 20 + 25 + 15 = 80, sau đó hiển thị `/100` và dùng phần trăm cho progress bar. Người dùng không bao giờ đạt 100 dù điền đủ mọi trường.

Bằng chứng: [`_public.listings.new.tsx` dòng 115–124](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_public.listings.new.tsx#L115-L124).

Khuyến nghị: chuẩn hóa trọng số về 100 hoặc hiển thị checklist thay vì “quality score”.

### U-05 — P1: tìm kiếm thiếu mô hình phân trang và URL state đầy đủ

- API/UI trả một list trang hiện tại; không có total/cursor và không có load-more.
- “Chỉ tin đã xác thực” lọc client trên trang đã tải, không phải toàn bộ kết quả.
- `priceRange` và `sortBy` được ghi vào URL nhưng state khởi tạo lần lượt là `ALL` và `LATEST`, bỏ qua URL.
- Một số chip thay đổi state nhưng chỉ cập nhật URL khi submit.

Bằng chứng: [`_public.search.tsx` dòng 18–94](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_public.search.tsx#L18-L94).

Khuyến nghị: server-side filter toàn bộ tiêu chí, trả `items/total/page` hoặc cursor; URL là nguồn trạng thái duy nhất.

### U-06 — P1: so sánh không có đường chọn tin hoàn chỉnh

Khi URL không có `ids`, trang tự lấy 2–3 tin bán đầu tiên. Nút “Chọn lại” chỉ quay về search, trong khi card search không có thao tác “thêm vào so sánh”. Kết quả nhìn như lựa chọn của người dùng nhưng thực chất là mặc định ngẫu nhiên theo kết quả mới nhất.

Bằng chứng: [`_public.compare.tsx` dòng 26–69 và 97–104](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_public.compare.tsx#L26-L104).

Khuyến nghị: selection tray trên search, tối đa ba tin cùng purpose; không tự chọn nếu `ids` trống.

### U-07 — P1: phản hồi lỗi ở “Tin của tôi” chưa đủ

Các thao tác submit/visibility đã nối backend thật, nhưng nhiều lỗi chỉ ghi console, không có status/busy nhất quán. Người dùng có thể bấm lặp và không biết server đã nhận hay chưa. Danh sách cũng tải toàn bộ, không phân trang.

Khuyến nghị: optimistic state có rollback hoặc disable theo item, toast/inline error, phân trang/cursor.

### U-08 — P2: CTA liên hệ không phản ánh điều kiện KYC

Backend đúng khi chặn tài khoản chưa VERIFIED, nhưng UI vẫn mở modal lead và chỉ báo lỗi sau submit. Nên hiển thị trạng thái “Xác minh để liên hệ”, dẫn sang `/kyc`, và quay lại listing sau khi hoàn tất.

### U-09 — P1: cấu hình nhắc lead/digest chưa có tác vụ thực thi

Workspace lưu `reminderEnabled` và `dailyDigestEnabled`, mặc định cả hai là `true`, nhưng không có scheduler/worker sử dụng các cờ này. Người dùng nhận xác nhận “Đã lưu mục tiêu phản hồi và lịch nhắc” dù không có lịch nhắc nào chạy.

Bằng chứng: [`BrokerWorkspaceController.java`](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/broker/BrokerWorkspaceController.java) và [`_account.broker-workspace.tsx`](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_account.broker-workspace.tsx).

Khuyến nghị: mặc định false cho đến khi worker tồn tại; hoặc gắn job idempotent, timezone, opt-out, delivery log.

### U-10 — P2: CMS admin không hoàn thành vòng đời nội dung

Màn tạo bài gọi API thật và tạo DRAFT, nhưng không có nút submit DRAFT; nút approve chỉ xuất hiện khi revision đã SUBMITTED. Copy nói có sửa bài/phiên bản, nhưng UI không có hành động edit/revision. Load lỗi và API trả rỗng cũng gần như cùng một trạng thái.

Bằng chứng: [`_admin.cms.tsx` dòng 148–225](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_admin.cms.tsx#L148-L225).

Khuyến nghị: thêm create → edit → submit → review → publish; phân biệt loading/empty/error; không nuốt exception.

## Setup và tài liệu

### Điểm tốt

- Có `.env.demo.example`; tunnel là profile tùy chọn.
- CI chạy `docker compose ... up -d --build --wait` thành công tại snapshot.
- Mailpit giúp kiểm tra email verification/reset mà không cần SMTP thật.
- README mô tả production guardrail và cảnh báo không dùng demo transaction/KYC như dịch vụ thật.

### Sai lệch cần sửa

| Vấn đề | Tác động | Sửa đề xuất |
|---|---|---|
| Quickstart chính chỉ có PowerShell | Người dùng Linux/macOS không có quy trình tương đương | Thêm Bash script cùng preflight/smoke/reset |
| Link `file:///d:/...` tới tài liệu nội bộ | Không mở được trên máy khác/GitHub | Dùng relative link trong repo |
| README ghi 19 backend tests | CI hiện chạy 21 | Tự động sinh badge/count hoặc bỏ số cứng |
| Mô tả “5 services” lệch Compose | Gây nhầm khi debug | Liệt kê chính xác core/optional profiles |
| Gọi React Router “Framework Mode” | Code dùng SPA `createBrowserRouter` | Sửa mô tả kiến trúc |
| Nói cả runtime container non-root | Backend non-root; frontend Nginx chưa đặt `USER` | Sửa claim hoặc harden image frontend |

## Bộ smoke test tối thiểu trước beta

1. Đăng ký → Mailpit verify → login → reset password → phiên cũ bị thu hồi.
2. Upload ba ảnh KYC hợp lệ/sai MIME/nhiễm test signature → submit → moderator approve → user xem ảnh sau reauth.
3. Tạo tin có địa giới/bản đồ/ảnh → lưu nháp → reload → sửa → submit → approve → search/detail.
4. Lead: tài khoản chưa KYC bị hướng dẫn; tài khoản verified gửi lead; chủ tin reveal contact và đổi trạng thái.
5. Report: public chỉ tạo mức thường; moderator P0 hide; owner appeal; dismiss không resume khi còn P0 khác.
6. Billing: tạo order, đổi plan/bank, tải lại QR, approve; kết quả phải giữ nguyên snapshot lúc mua.
7. CMS: draft không xuất hiện public; revision mới chưa publish không lộ; publish đúng revision.
8. Responsive 320/360/393/768/1024/1440 và keyboard-only cho home/search/detail/auth/admin.
