# Đánh giá sản phẩm, hardcode/fallback và hành động

Snapshot: [`b8d66bf`](https://github.com/Babychandoi/Real-estate/commit/b8d66bf251c117b2775a6b586a55ea8262ccaaff) — 2026-09-19

## Kết luận

Phiên bản mới đã loại bỏ nhiều “màn hình trưng bày”: dự án, billing, lead, profile và quản lý tin dùng API thật. Các endpoint chưa có như định giá/quality-score trả 501 thay vì dựng dữ liệu giả; UI giao dịch nói rõ chưa nhận tiền cọc thật. Đây là hướng đúng.

Vẫn còn ba dạng vấn đề:

1. **Hành động có vẻ hoàn chỉnh nhưng sai nghiệp vụ:** eKYC, billing snapshot, CMS public revisions, report P0.
2. **Cấu hình được lưu nhưng không có engine thực thi:** nhắc lead quá hạn/daily digest.
3. **Luồng UI dở dang hoặc tự chọn dữ liệu:** compare, CMS submit/edit, analytics nhiều chỉ số bằng 0, search không phân trang.

## Phân loại hardcode/fallback

| Khu vực | Tình trạng | Kết luận |
|---|---|---|
| Home/search/detail | Dùng API, không fallback business giả | Đạt |
| Seed/demo data | Có và được mô tả cho demo | Chấp nhận nếu production không chạy seed |
| Estimate price / server quality score | Trả 501, không giả kết quả | Đạt về tính trung thực |
| Điểm “hoàn thiện biểu mẫu” client | Công thức cứng, tối đa 80 nhưng ghi `/100` | Cần sửa copy/công thức |
| Projects admin | API create/search/list thật | Cải thiện; chưa edit/version/export |
| Billing | API/state thật | Lỗi snapshot có thể làm sai quyền lợi/thụ hưởng |
| CMS | API create/review thật một phần | Luồng UI bị kẹt ở DRAFT; public API lộ revisions |
| Broker reminders/digest | Chỉ lưu cờ | Không được coi là tính năng hoạt động |
| Analytics | API thật | `impressions` và `detailViews` chưa thu thập, phần lớn funnel bằng 0 |
| Compare | Dữ liệu API thật | Nếu thiếu ids, tự lấy tin đầu; không có selection UI |
| Transaction/escrow | Feature flag + production guard | Không được quảng bá là thật; code OTP giả vẫn tồn tại |
| Recent broker listings | API thật | Query không lấy title, UI hardcode `Tin <8 ký tự UUID>` |

## Kiểm tra hành động chính

| Hành động | Server call | Authorization/ownership | Feedback | Kết luận |
|---|---:|---:|---:|---|
| Đăng nhập/đăng ký/reset | Có | Có | Có | Đạt cơ bản |
| Upload avatar | Có | Có | Có | Đạt |
| Upload ảnh listing | Có | Owner | Có | Đạt; cần streaming/CDN khi scale |
| Submit eKYC | Có | Session identity | Có lỗi chung | **Không hoạt động do DTO** |
| Xem KYC của chính mình | Có | Reauth 10 phút | Có | Đạt |
| Staff xem KYC | Có | Role | Không step-up/audit read | Cần harden |
| Lưu/chỉnh sửa draft listing | Có | Owner | Có | Đạt |
| Submit listing lần đầu | Có | KYC/owner | Có | Mất mã địa giới |
| Ẩn/hiện listing | Có | Owner/admin | Thiếu error/busy | Đạt một phần |
| Search/map/sort | Có | Public ACTIVE | Có | Đạt một phần, thiếu pagination |
| Gửi lead | Có | Auth + KYC | UI báo sau submit | Đạt server, UX chưa tốt |
| Reveal lead phone | Có | Listing owner + consent | Có | Đạt, có audit |
| Report listing | Có | Anonymous | Severity tin client | **Nguy hiểm/P0** |
| Appeal report | Có | Bị role admin/mod chặn | Không có UI owner hoàn chỉnh | Không đạt |
| Mua/report/cancel order | Có | Owner | Có | Đạt một phần |
| Approve/reject order | Có | Admin | Có | Sai snapshot |
| Tạo project | Có | Admin/mod | Có | Đạt |
| Tạo CMS article | Có | Admin/mod | Có | Đạt một phần |
| Submit/edit CMS revision | Backend có phần API | UI thiếu | Không | Không hoàn chỉnh |
| Export analytics CSV | Có | Admin/mod | Có | Đạt, nhưng dữ liệu nguồn thiếu |

## Phát hiện

### PRD-01 — P0: eKYC “có nút/có API” nhưng hợp đồng request làm hỏng tính năng

Đây là ví dụ quan trọng của nút trông hoạt động nhưng backend từ chối trước controller. Xem chi tiết trong báo cáo người dùng. Cần test tại boundary HTTP chứ unit test service không bắt được.

### PRD-02 — P0: report severity là input nghiệp vụ không được phép tin client

Frontend công khai hiện gửi `MEDIUM`, nhưng attacker không bị ràng buộc bởi frontend và có thể gửi `P0_EMERGENCY`. Không được đánh dấu tính năng an toàn chỉ vì UI không có lựa chọn P0.

Bằng chứng: [`_public.listings.$listingId.tsx` gửi MEDIUM](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_public.listings.$listingId.tsx#L55) nhưng [`ViolationReportController`](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/lead/api/ViolationReportController.java#L38-L47) nhận severity tùy ý.

### PRD-03 — P0: order snapshot chỉ được ghi, không được sử dụng

Snapshot tạo cảm giác hệ thống đã cố định thỏa thuận mua, nhưng read/approve vẫn dựa vào bank/plan hiện tại. Đây là lỗi integrity chứ không chỉ UI.

Bằng chứng: [`BillingService.java`](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/billing/BillingService.java#L34-L79).

### PRD-04 — P1: reminder/digest là “configuration without execution”

Backend chỉ có GET/PUT SLA settings. Không có job truy vấn lead quá hạn hoặc gửi tổng hợp; mặc định lại bật. Nên gắn nhãn “sắp ra mắt” hoặc ẩn toggle cho đến khi có worker và delivery log.

Bằng chứng: [`BrokerWorkspaceController.java`](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/broker/BrokerWorkspaceController.java).

### PRD-05 — P1: CMS admin tạo xong nhưng không thể đưa DRAFT sang review

UI chỉ tải và tạo. Approval phụ thuộc trạng thái SUBMITTED, nhưng không có nút submit; edit/revision cũng thiếu. Một bài tạo từ UI bị kẹt ở DRAFT trừ khi gọi API ngoài giao diện.

Bằng chứng: [`_admin.cms.tsx`](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_admin.cms.tsx).

### PRD-06 — P1: compare tự điền dữ liệu không do người dùng chọn

Thiếu `ids` thì code lấy các listing bán mới nhất. Điều này không phải fallback kỹ thuật mà là hành vi sản phẩm dễ gây hiểu nhầm. Search cũng chưa có “add to compare”.

Bằng chứng: [`_public.compare.tsx` dòng 26–69](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_public.compare.tsx#L26-L69).

### PRD-07 — P1: analytics thật về transport nhưng chưa thật về measurement

UI gọi API và export CSV thật. Backend chủ động đặt `impressions=0` và `detailViews=0` vì chưa thu thập, nên funnel chưa đại diện hành vi thực. Copy hiện tương đối trung thực, nhưng không nên dùng dashboard này để ra quyết định tăng trưởng.

Khuyến nghị: event schema/version, anonymous/session identity phù hợp privacy, idempotency, attribution window, data-quality monitor; hiển thị “chưa đo” thay vì số 0.

### PRD-08 — P2: nhãn listing trong broker workspace dùng UUID thay title

Query recent listings chỉ chọn `id,status,created_at,updated_at`; frontend buộc hiển thị `Tin ${id.slice(0,8)}`. Đây là hardcode UI xuất phát từ read model thiếu dữ liệu.

Bằng chứng: [`BrokerWorkspaceController.java`](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/broker/BrokerWorkspaceController.java) và [`_account.broker-workspace.tsx`](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_account.broker-workspace.tsx).

### PRD-09 — P2: project search gọi lại theo mỗi phím và submit có thể gọi trùng

Màn project đã là API thật — cải thiện lớn so với bản cũ. Tuy nhiên callback/effect phụ thuộc từ khóa khiến request theo từng keystroke và form submit có thể tạo thêm request. Cần debounce/cancel và server pagination.

### PRD-10 — P1: code escrow/OTP giả vẫn nằm trong sản phẩm

UI không quảng bá nhận tiền cọc thật và production validator chặn bật — đúng. Nhưng service vẫn có seller phone hardcode và mọi OTP sáu ký tự đều hợp lệ. Không nên để code này chung đường build production.

Bằng chứng: [`DepositTransactionApplicationService.java` dòng 60–95](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/transaction/application/DepositTransactionApplicationService.java#L60-L95).

## Những mục trước đây đã được cải thiện

- Listing card và map marker đi được tới detail; edit revision từ thư viện tin hoạt động.
- Owner và admin có visibility controls thật.
- Lead inbox có total qua phân trang, filter và status transitions.
- Profile/avatar/password recovery là API thật, không còn placeholder.
- VietQR reconciliation có queue và hành động approve/reject thật.
- Project admin không còn mảng giả runtime.
- CMS mảng demo lịch sử đã bị comment, không dùng làm fallback runtime.
- Home/search/detail lấy business data từ backend; không dựng danh sách giả khi API lỗi.
- Endpoint chưa triển khai trả 501 thay vì trả kết quả ước lượng giả.

## Definition of Done đề xuất cho mỗi tính năng

Một nút/toggle không được đánh dấu “hoàn tất” nếu thiếu bất kỳ mục nào:

1. API/state transition thực và idempotent.
2. Server-side authorization/ownership/validation, không tin trường nhạy cảm từ client.
3. Loading, success, empty, retry và actionable error trên UI.
4. E2E cho happy path và ít nhất một denied/failure path.
5. Audit/metric nếu là KYC, billing, moderation, CMS publish hoặc contact reveal.
6. Không có mock/fallback trong production path; demo data phải tách profile.
7. Tài liệu và copy phản ánh đúng khả năng thực tế.

## Backlog ưu tiên sản phẩm

1. Sửa eKYC/report/billing/CMS leak và thêm E2E.
2. Hoàn thiện compare selection, CMS lifecycle và owner appeal.
3. Thực thi reminder/digest hoặc tắt toggle.
4. Bổ sung pagination/search URL state/feedback my-listings.
5. Xây instrumentation thật cho funnel; phân biệt zero với not-measured.
6. Loại code transaction giả khỏi production artifact.
