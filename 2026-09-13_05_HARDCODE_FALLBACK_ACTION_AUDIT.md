# Kiểm kê hardcode, fallback và hành động UI không hoạt động

**Dự án:** `Babychandoi/Real-estate`
**Nhánh:** `main`
**Snapshot đã kiểm tra:** [`8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2`](https://github.com/Babychandoi/Real-estate/commit/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2)
**Ngày đánh giá:** 13/09/2026
**Phạm vi:** toàn bộ 41 file nguồn TypeScript/TSX của frontend, đối chiếu các API Spring Boot, DTO, service và persistence liên quan. Đây là đánh giá chỉ đọc; không sửa repository.

## 1. Kết luận ngắn

Frontend **build production thành công**, nhưng chưa thể coi là hoàn thiện về chức năng. Không phải tất cả nút đều hỏng: kiểm kê AST tìm thấy **251 phần tử tương tác/form**, gồm **125 button**, **49 link/anchor**, **67 input/select/textarea**, **7 form** và **3 container có sự kiện**. Phần lớn thao tác điều hướng cơ bản, đăng nhập, tìm kiếm, gửi lead, upload ảnh, kiểm duyệt tin và thanh toán đã có handler/API.

Tuy nhiên có bốn nhóm lỗi lớn:

1. **Thông tin tạo niềm tin sai:** chi tiết tin có thể hiện “Đã xác thực người đăng” dù chủ tin chưa được xác thực; màn hình kiểm duyệt hiển thị các kết luận pháp lý hardcode; toggle làm mờ tọa độ không có tác dụng; màn hình tạo tin luôn báo đã gửi thẩm định Sổ hồng dù luồng xác minh bị vô hiệu hóa.
2. **Chức năng giả hoặc chỉ hiện toast:** quản lý dự án có sáu nhóm thao tác không ghi DB/không điều hướng; CMS có ba nút chỉ hiện thông báo; footer có nhiều link đi về trang chủ do route không tồn tại.
3. **API có nhưng UI không hoàn tất workflow:** báo xấu không có form công khai và không có nút xử lý; CMS không có nút nộp duyệt; verification không hiển thị tài liệu; broker lưu cờ nhắc việc nhưng không có worker gửi nhắc; lead chỉ trả số điện thoại đã che và không có đường giải mã có kiểm soát.
4. **Sai hợp đồng frontend–backend:** Project và Analytics dùng tên trường hoàn toàn khác; trang chi tiết dùng `primaryImageUrl` trong khi API trả `imageUrls`; nhiều trường trên form đăng tin không được gửi hoặc không tồn tại trong domain.

**Khuyến nghị phát hành:** chưa nên đưa các màn hình Project, Analytics, Verification/eKYC và các tuyên bố pháp lý ra production. Cần xử lý các mục P0/P1 bên dưới trước khi mời người dùng thật.

## 2. Quy ước đánh giá

| Ký hiệu | Ý nghĩa |
|---|---|
| ✅ Hoạt động | Có handler/route/API và kết quả đúng với nhãn hành động |
| ⚠️ Một phần | Có handler hoặc API nhưng dữ liệu, trạng thái, phản hồi lỗi hay kết quả nghiệp vụ chưa đúng |
| ❌ Không hoạt động | No-op, chỉ toast, chỉ đổi state cục bộ, sai route, sai DTO hoặc chắc chắn thất bại |
| ➖ Thiếu | Workflow cần hành động nhưng UI không cung cấp |
| P0 | Có thể gây sai dữ liệu, mất niềm tin, rò rỉ riêng tư hoặc khóa workflow cốt lõi |
| P1 | Chức năng chính hỏng/thiếu, cần sửa trước beta công khai |
| P2 | Trải nghiệm lỗi hoặc không nhất quán nhưng có đường vòng |
| P3 | Hardcode cấu hình/copy hoặc nợ kỹ thuật |

## 3. Các lỗi P0 cần khóa phát hành

### P0-01 — Huy hiệu “Đã xác thực người đăng” có thể bị cấp sai

- Search API dùng đúng `listing.isVerifiedOwner()`, nhưng Detail API lại gán `isVerified = revision.status == APPROVED`: [ListingController L208-L220](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/api/ListingController.java#L208-L220), [L240-L259](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/api/ListingController.java#L240-L259).
- Trang chi tiết dùng cờ này để hiện “Đã xác thực người đăng”: [Listing Detail L75-L80](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.%24listingId.tsx#L75-L80).
- Sáu tin seed đều có `is_verified_owner = FALSE` nhưng revision là `APPROVED`, nên đây không chỉ là tình huống lý thuyết: [V013 L19-L41](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/resources/db/migration/V013__seed_experience_listings.sql#L19-L41).

**Sửa:** Detail response phải lấy `listing.isVerifiedOwner()`. Thêm contract test chứng minh `APPROVED + is_verified_owner=false` không hiện badge xác thực.

### P0-02 — Toggle bảo vệ vị trí không làm gì, tọa độ thật vẫn được công khai

- UI cho phép bật/tắt “Làm mờ bán kính 100m”: [Create Listing L530-L565](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L530-L565).
- Biến `obfuscateLocation` không có trong payload; tọa độ nhập được gửi thẳng: [L91-L105](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L91-L105), [L155-L169](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L155-L169).
- Search lại tuyên bố bán kính “khoảng 200m”: [Search L220-L229](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L220-L229), trong khi API trả nguyên tọa độ lưu trong revision: [ListingController L203-L218](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/api/ListingController.java#L203-L218).

**Sửa:** lưu tọa độ chính xác ở cột private, sinh tọa độ public ổn định theo listing bằng PostGIS; không cho client tự quyết định tọa độ public. Chọn một bán kính duy nhất và kiểm thử khoảng cách.

### P0-03 — Form đăng tin hiển thị nhiều trường nhưng không lưu DB

Các trường `province`, `district`, `ward`, `bedrooms`, `bathrooms`, `direction`, `legalDoc`, `obfuscateLocation` có thể chỉnh trên UI nhưng bị loại khỏi POST/PUT: [state L19-L40](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L19-L40), [payload L91-L121](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L91-L121). Backend đã hỗ trợ `provinceCode`, `districtCode`, `wardCode` và tọa độ trong DTO, nhưng chưa có phòng ngủ/phòng tắm/hướng/pháp lý: [Create DTO L13-L41](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/api/request/CreateListingDraftRequest.java#L13-L41).

**Hệ quả:** người dùng thấy preview đúng nhưng tin lưu/hiển thị lại mất dữ liệu. **Sửa:** mở rộng domain/migration/DTO cho các trường cần thiết, gửi mã địa giới chuẩn, và có test round-trip “nhập → lưu → tải lại → giống nhau”.

### P0-04 — Có thể nộp bản cũ, làm mất các chỉnh sửa mới nhất

Sau khi đã có `listingId`, nút “Gửi phê duyệt” chỉ gọi `/submit`; nó không PUT form hiện tại trước khi submit: [Create Listing L153-L177](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L153-L177). Kịch bản lỗi: lưu nháp → sửa giá/mô tả → bấm gửi duyệt → server duyệt revision cũ.

**Sửa:** `saveDraft(currentForm)` phải thành công ngay trước `submit`; hoặc tạo endpoint transaction `PUT .../draft-and-submit` có idempotency/optimistic version.

### P0-05 — “AI/GIS định giá” thực chất là bảng giá hardcode, không đọc DB

- Frontend dùng giá cố định `180m/125m/53m`, biên `0.92–1.12`, độ tin cậy 94%: [Create Listing L52-L84](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L52-L84), [L619-L647](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L619-L647).
- Backend cũng hardcode bảng khác (`APARTMENT=48.5m`, `LAND=65m`, biên trên 1.15) và câu “thuật toán GIS Hà Nội 2026”: [ListingApplicationService L164-L195](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/application/service/ListingApplicationService.java#L164-L195).
- Frontend không gọi endpoint estimate/quality đã tồn tại: [ListingController L275-L285](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/api/ListingController.java#L275-L285).

**Sửa:** tạm đổi nhãn thành “Công thức minh họa” và bỏ confidence; sau đó xây nguồn comparable từ DB theo loại, ô địa lý, thời gian, loại bỏ outlier, trả số mẫu và ngày dữ liệu. Không gọi là AI/GIS khi chưa có mô hình/dataset.

### P0-06 — Luồng xác minh bị khóa nhưng UI vẫn tuyên bố đã gửi hồ sơ

- `requestVerification=false`, checkbox bị `disabled`, các trường hồ sơ `readOnly`: [Create Listing L43-L50](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L43-L50), [L806-L877](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L806-L877).
- Thành công lại luôn hiện “Đã gửi thẩm định Sổ hồng”: [L249-L260](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L249-L260).

**Sửa:** hoặc hoàn thiện upload KYC/legal và POST `/listings/{id}/verifications`, hoặc ẩn toàn bộ phần xác minh và không hiện kết luận thành công.

### P0-07 — Bàn xác minh cho phép duyệt “mù”

- UI nói “chỉ hiển thị giấy tờ” nhưng chỉ hiện tên/số/loại/ngày; không render `documentUrls` hay ảnh KYC: [Verification page](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.verification.tsx#L1-L5).
- Response có `documentUrls`, nhưng ba URL ảnh KYC bị backend cố ý trả `null`: [ListingVerificationResponse L10-L22](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/api/response/ListingVerificationResponse.java#L10-L22), [UserKycResponse L25-L42](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/api/response/UserKycResponse.java#L25-L42).
- Nút “Xác nhận đạt” gửi một ghi chú mặc định khẳng định đã đối soát: [verificationApi L9-L13](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/entities/verification/api/verificationApi.ts#L9-L13).

**Sửa:** chặn approve cho đến khi tải được tài liệu qua URL ngắn hạn, bắt buộc checklist + note, confirmation, busy state và audit actor; tách rõ hàng đợi KYC `/kyc/queue` và hàng đợi pháp lý `/verifications`.

### P0-08 — Lead được thu thập nhưng người phụ trách không thể liên hệ

API chỉ trả `maskedPhone`: [LeadResponse L9-L29](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/api/response/LeadResponse.java#L9-L29). Domain gọi phương thức là “giải mã” nhưng thực tế chỉ tách phần mask; `PiiProtectionService` có `protect()` mà không có `decrypt()`: [Lead L59-L65](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/domain/model/Lead.java#L59-L65), [PiiProtectionService L28-L66](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/PiiProtectionService.java#L28-L66). UI cũng chỉ hiện số che và dropdown trạng thái: [Leads page](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.leads-and-reports.tsx#L1-L5).

**Sửa:** thêm endpoint reveal/call proxy có RBAC theo owner listing, audit log, reason, rate limit, TTL và masking mặc định. Không trả plaintext trong API danh sách.

### P0-09 — Module Project và Analytics sai DTO nên không dùng được

- Project frontend đọc/gửi `code`, `investorName`, `district`, `provinceCity`, `scaleHa`, `buildingPermitNo`...: [Project FE L153-L224](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.projects.tsx#L153-L224). Backend dùng `developerName`, `districtCode`, `provinceCode`, `totalAreaM2`, `totalBlocks`, `legalLicenseNumber`: [Project request L6-L28](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/catalog/api/request/CreateProjectRequest.java#L6-L28), [Project response L10-L25](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/catalog/api/response/ProjectResponse.java#L10-L25). POST chắc chắn thiếu ba trường `@NotBlank`; GET tạo `undefined`, và khi gõ tìm kiếm có thể lỗi tại `.toLowerCase()`.
- Analytics FE mong `totalLeads/totalContacted/totalClosed/conversionRate` và step `step/name/percentOfPrevious`: [Analytics FE L6-L18](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.analytics.tsx#L6-L18). Backend trả `leadsSubmitted/contactedCount/dealsClosed/conversionRatePercent` và `stepIndex/stepName/percentage`: [Funnel DTO L5-L19](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/api/response/FunnelAnalyticsResponse.java#L5-L19).

**Sửa:** sinh TypeScript client/types từ OpenAPI và thêm consumer-driven contract test trong CI. Không duy trì DTO viết tay trùng lặp; frontend đã có một `FunnelAnalytics` đúng nhưng trang Analytics không dùng nó.

## 4. Kiểm kê chi tiết theo trang

### 4.1 Điều hướng, footer và xác thực

| ID | Mức | Trạng thái | Phát hiện | Phương án |
|---|---:|---|---|---|
| NAV-01 | P1 | ❌ | Footer trỏ `/about`, `/terms`, `/privacy`, `/contact`, nhưng router không khai báo; wildcard âm thầm render Home nên người dùng tưởng link hoạt động. [Footer L380-L401](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/root.tsx#L380-L401), [routes L26-L42](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes.tsx#L26-L42) | Tạo bốn trang hoặc bỏ link; wildcard phải là trang 404, không phải Home. |
| NAV-02 | P0 | ❌ | “Báo cáo vi phạm” công khai lại trỏ vào bàn admin có ProtectedRoute; backend đã có `POST /public/reports`. [Footer L398-L400](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/root.tsx#L398-L400), [Violation API L35-L49](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/api/ViolationReportController.java#L35-L49) | Modal báo xấu ngay trên chi tiết tin; bắt listingId, category, mô tả, bằng chứng, consent. |
| NAV-03 | P1 | ❌ | Link Swagger hardcode `http://localhost:8080`; hỏng khi deploy và có thể trỏ vào máy người dùng. [root L258-L271](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/root.tsx#L258-L271) | Dùng biến môi trường, reverse-proxy path hoặc ẩn trong production. |
| NAV-04 | P1 | ❌ | Nút “Liên hệ hỗ trợ” của trang thiếu quyền trỏ `/`, không phải support. [ProtectedRoute L75-L90](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/shared/auth/ProtectedRoute.tsx#L75-L90) | Trỏ route contact/ticket thật. |
| AUTH-01 | P2 | ⚠️ | Placeholder/minLength nói 6 ký tự, handler và backend yêu cầu 10. [LoginModal L395-L409](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/shared/auth/LoginModal.tsx#L395-L409), [handler L105-L115](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/shared/auth/LoginModal.tsx#L105-L115), [AuthController L61-L66](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/iam/api/AuthController.java#L61-L66) | Đồng bộ constraint 10 và thêm strength/help text. |
| AUTH-02 | P1 | ➖ | Không có “Quên mật khẩu”; backend cũng không có reset-password endpoint. [AuthController L22-L55](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/iam/api/AuthController.java#L22-L55) | Token một lần, TTL, revoke session, rate limit, email generic chống enumeration. |
| AUTH-03 | P2 | ⚠️ | Nút gửi lại email chỉ xuất hiện khi chuỗi lỗi chứa đúng “xác minh email”; trang verify thất bại không có nút resend. [LoginModal L191-L195](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/shared/auth/LoginModal.tsx#L191-L195), [Verify page L16-L20](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.verify-email.tsx#L16-L20) | Dựa trên error code, cho resend trực tiếp ở trạng thái token hết hạn. |
| NAV-05 | P1 | ➖ | Backend có inbox/SSE/read notification nhưng UI chỉ xử lý sự kiện `PLAN_UPGRADED`; không có chuông/inbox. Trong khi trang đăng tin hứa gửi thông báo vào hộp thư. [NotificationController L11-L19](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/notification/NotificationController.java#L11-L19), [AuthContext L45-L47](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/shared/auth/AuthContext.tsx#L45-L47) | Thêm inbox, unread count, mark-read và reconnect SSE. |

### 4.2 Trang chủ

| ID | Mức | Trạng thái | Phát hiện | Phương án |
|---|---:|---|---|---|
| HOME-01 | P3 | ✅/hardcode | Ba từ khóa gợi ý được viết cứng: Vinhomes Green Bay, Cầu Giấy 2PN, Nhà phố Đống Đa. Nút có điều hướng thật. [Home L105-L118](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.home.tsx#L105-L118) | Đưa vào CMS/trending-query API nếu muốn phản ánh nhu cầu thực. |
| HOME-02 | P1 | ⚠️ | Khối “Tin đăng nổi bật” chỉ gọi search theo purpose, không có featured flag/ranking. [Home L16-L33](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.home.tsx#L16-L33), [L123-L149](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.home.tsx#L123-L149) | Đổi nhãn “Tin mới” hoặc bổ sung cơ chế featured có tiêu chí minh bạch. |
| HOME-03 | P2 | ⚠️ | API lỗi chỉ `console.error`; màn hình giống trạng thái DB không có tin và không có retry. [Home L20-L29](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.home.tsx#L20-L29), [L139-L155](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.home.tsx#L139-L155) | Tách `error`, `empty`, `loading`; thêm retry. |

### 4.3 Tìm kiếm và bản đồ

| ID | Mức | Trạng thái | Phát hiện | Phương án |
|---|---:|---|---|---|
| SEARCH-01 | P1 | ⚠️ | Nút `✕` gọi `setKeyword('')` rồi fetch ngay; fetch vẫn đọc keyword cũ do React state chưa cập nhật. [Search L31-L52](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L31-L52), [L100-L107](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L100-L107) | `fetchListings({keyword:''})`, reducer/query state, hoặc effect theo URLSearchParams. |
| SEARCH-02 | P1 | ⚠️ | “Xóa bộ lọc” không reset `onlyVerified`, không đồng bộ URL và có thể không fetch nếu chỉ keyword đang khác. [Search L265-L280](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L265-L280) | Một hàm `resetFilters()` atomic, cập nhật URL và fetch đúng một lần. |
| SEARCH-03 | P1 | ⚠️ | “Chính chủ eKYC” chỉ lọc list client-side; số lượng và map vẫn dùng toàn bộ `listings`. [Search L198-L217](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L198-L217), [L241-L246](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L241-L246), [L283-L396](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L283-L396) | Filter server-side hoặc tạo `visibleListings` dùng chung cho count/list/map. |
| SEARCH-04 | P1 | ❌ | Sort được gửi lên API nhưng Elasticsearch không dùng `sortBy`; fallback JPA luôn `createdAt DESC`. [listingApi L5-L23](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/entities/listing/api/listingApi.ts#L5-L23), [Elasticsearch L23](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/search/ElasticsearchListingIndex.java#L23), [JPA L46-L58](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/infrastructure/persistence/repository/ListingJpaRepository.java#L46-L58) | Whitelist bốn sort ở cả ES và DB, kiểm thử thứ tự. |
| SEARCH-05 | P1 | ❌ | Link “So sánh” không gửi IDs; trang search không có add-to-compare. Compare tự lấy 2–3 tin đầu nếu thiếu query. [Search L211-L217](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L211-L217), [Compare L25-L64](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.compare.tsx#L25-L64) | Selection tray 2–3 tin cùng purpose; link `/compare?ids=...`; không tự chọn hộ người dùng. |
| SEARCH-06 | P2 | ⚠️ | Click/hover card chỉ đổi viền và nhãn “Đang hiển thị trên bản đồ”; `selectedId` không truyền vào `ListingMap`, nên map không focus/highlight. [Search L283-L304](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L283-L304), [L392-L397](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L392-L397) | Prop `selectedId`; `flyTo`, state layer và popup/detail action. |
| SEARCH-07 | P2 | ⚠️ | Lỗi tải chỉ ghi console; kết quả cũ có thể còn trên màn hình sau khi filter mới thất bại. [Search L51-L60](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L51-L60) | Error banner, retry và policy giữ/xóa stale data rõ ràng. |
| SEARCH-08 | P3 | hardcode | “Hà Nội Pilot”, tâm map `[105.82,21.03]`, zoom 10 và URL tile được viết cứng. [Search L88-L96](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L88-L96), [ListingMap L11-L14](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/shared/map/ListingMap.tsx#L11-L14) | Config theo thị trường/env; fallback dùng vị trí/khu vực đã chọn. |

### 4.4 Chi tiết và so sánh tin

| ID | Mức | Trạng thái | Phát hiện | Phương án |
|---|---:|---|---|---|
| DETAIL-01 | P0 | ❌ | FE cast Detail response thành `Listing` và đọc `primaryImageUrl`, nhưng backend trả `imageUrls`; ảnh DB không hiện. [FE model L1-L15](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/entities/listing/model/types.ts#L1-L15), [Detail L17-L21 & L64-L74](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.%24listingId.tsx#L17-L21), [Detail DTO L8-L29](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/api/response/ListingDetailResponse.java#L8-L29) | Tạo `ListingDetail` type và gallery dùng `imageUrls`; contract test. |
| DETAIL-02 | P1 | ⚠️ | “Quay lại danh sách” trỏ `/`, làm mất query/filter/search context. [Detail L52-L61](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.%24listingId.tsx#L52-L61) | Lưu `location.state.from` hoặc trỏ `/search` với query cũ. |
| DETAIL-03 | P2 | ⚠️ | Network error, 403 và 404 đều thành “Không tìm thấy”; không retry. [Detail L17-L49](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.%24listingId.tsx#L17-L49) | Phân loại theo Problem Details/status. |
| DETAIL-04 | P1 | ➖ | Không có nút báo xấu trên chi tiết — vị trí tự nhiên nhất để gọi public reports. | Thêm hành động cạnh CTA liên hệ. |
| DETAIL-05 | P3 | dead hardcode | `ownerName: 'Anh Hoàng (Chính chủ)'` được truyền vào modal nhưng modal hiện không sử dụng prop này. [Detail L176-L189](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.%24listingId.tsx#L176-L189), [Lead modal props L6-L9](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/features/lead/ui/LeadConsultationModal.tsx#L6-L9) | Xóa prop hoặc lấy owner display name an toàn từ API. |
| COMPARE-01 | P1 | ❌ | “Chọn lại” chỉ quay về search; không có UI chọn lại. Nếu không có IDs, hệ thống tự chọn tin đầu. [Compare L51-L69](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.compare.tsx#L51-L69), [L96-L103](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.compare.tsx#L96-L103) | Hoàn tất selection tray; trạng thái rỗng khi chưa chọn. |
| COMPARE-02 | P2 | ✅ | “Chỉ xem điểm khác biệt”, bỏ tin, link chi tiết hoạt động cục bộ/điều hướng đúng. | Giữ, thêm test. |

### 4.5 Tạo và quản lý tin

| ID | Mức | Trạng thái | Phát hiện | Phương án |
|---|---:|---|---|---|
| CREATE-01 | P0 | hardcode DB-bound | Form mới được điền sẵn giá 3,85 tỷ, 72,5m², 2PN/2WC, Sổ hồng, Matrix One, tọa độ và mô tả như tài sản thật. [Create L19-L41](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L19-L41) | Mặc định rỗng; demo data chỉ bật bằng explicit demo mode và gắn nhãn. |
| CREATE-02 | P1 | ⚠️ | PUT cập nhật draft bỏ `publicLatitude/publicLongitude`; người dùng sửa tọa độ sau lần lưu đầu sẽ không được lưu. [Create L108-L121](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L108-L121), trong khi backend hỗ trợ [Update DTO L15-L37](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/api/request/UpdateListingDraftRequest.java#L15-L37) | Gửi toàn bộ normalized command hoặc PATCH có semantics rõ. |
| CREATE-03 | P1 | ❌ | Nút “Sửa tin (Tạo vN)” chỉ đi `/listings/new`; trang create không nhận ID, không tải tin và sẽ tạo listing mới. [My Listings L182-L199](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.listings.tsx#L182-L199), [routes L29-L31](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes.tsx#L29-L31) | Route `/listings/:id/edit`, load current/latest draft, PUT và optimistic version. |
| CREATE-04 | P2 | ⚠️ | Stepper cho nhảy thẳng bước 4 không validate; label “Đã tự động lưu” chỉ xuất hiện sau thao tác lưu thủ công. [Create L337-L378](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L337-L378), [L86-L130](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L86-L130), [L288-L303](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L288-L303) | Khóa bước chưa hợp lệ; hoặc triển khai debounce autosave thật và dirty/saved/error state. |
| CREATE-05 | P2 | ⚠️ | Fallback title `Bản nháp tin đăng BĐS mới` được ghi DB khi lưu rỗng; success có fallback ID giả `LST-WF-2026-NEW`. [Create L91-L115](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L91-L115), [L249-L253](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L249-L253) | Cho title nullable ở draft hoặc dùng nhãn chỉ ở view; không dựng ID giả. |
| CREATE-06 | P2 | ⚠️ | Nút thành công “Không gian Môi giới” hiển thị cho cả USER, nhưng route chỉ cho BROKER/ADMIN. [Create L264-L270](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.new.tsx#L264-L270), [routes L38-L40](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes.tsx#L38-L40) | Render theo role hoặc thay bằng kho tin. |
| MY-01 | P2 | ⚠️ | “Nộp duyệt” gọi API thật nhưng không busy/disable, lỗi chỉ console; có thể double-click và người dùng không biết thất bại. [My Listings L48-L55](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.listings.tsx#L48-L55), [L182-L190](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.listings.tsx#L182-L190) | Busy theo row, success/error toast, idempotent submit. |
| MY-02 | P2 | ⚠️ | Lỗi tải kho tin bị nuốt và hiển thị như “chưa có tin”; không retry. [My Listings L32-L42](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.listings.tsx#L32-L42), [L204-L216](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.listings.tsx#L204-L216) | Error state riêng và retry. |
| MY-03 | P2 | ⚠️ | Tabs chỉ có ACTIVE/PENDING_REVIEW/DRAFT; REJECTED/ARCHIVED/SUSPENDED chỉ lẫn trong “Tất cả”, làm workflow sửa lỗi khó thấy. [My Listings L103-L126](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.listings.tsx#L103-L126) | Tabs/status filter từ enum backend. |

### 4.6 Billing và Broker workspace

| ID | Mức | Trạng thái | Phát hiện | Phương án |
|---|---:|---|---|---|
| BILL-01 | P2 | ⚠️ | Khi plan API chưa tải/lỗi hoặc code không khớp, UI khẳng định gói hiện tại là “Miễn phí”; order không khớp thành “Gói đăng tin”. [Billing L7-L11](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.billing.tsx#L7-L11) | Loading/unknown rõ ràng; plan snapshot lưu trong order. |
| BILL-02 | P1 | ⚠️ | “Xác nhận đã nhận tiền” gọi API thật nhưng luôn ghi note hardcode `Đã đối chiếu thủ công`, không nhập chứng từ/note/confirmation. [Billing L10-L11](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.billing.tsx#L10-L11) | Modal nhập note, đối chiếu số tiền/reference, confirm và audit. |
| BILL-03 | P2 | ➖ | Không có cancel order phía user; admin không có reject/mismatch/refund. Backend cũng chỉ có approve. [BillingController L11-L19](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/billing/BillingController.java#L11-L19) | Hoàn thiện state machine và hành động có quyền. |
| BROKER-01 | P1 | ⚠️ | “Nhắc lead quá hạn” và “Gửi tổng hợp hằng ngày” được lưu DB, nhưng toàn repo chỉ đọc/ghi hai cờ tại controller; không có scheduler/worker sử dụng. [Broker FE L45](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.broker-workspace.tsx#L45), [BrokerController L7-L8](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/broker/BrokerWorkspaceController.java#L7-L8) | Worker định kỳ + outbox/email/in-app notification; test clock. |
| BROKER-02 | P2 | hardcode | Nếu chưa có row cấu hình, backend trả mặc định `30,true,true`, khiến UI thể hiện hai chức năng đang bật dù chúng chưa chạy. [BrokerController L7](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/broker/BrokerWorkspaceController.java#L7) | Seed row hoặc trả `configured=false`; mặc định off cho chức năng chưa triển khai. |
| BROKER-03 | P1 | ➖ | Workspace chỉ có thống kê lead, không có danh sách/chi tiết/call; tin gần đây chỉ hiện `Tin {id}` do API không trả title. [Broker FE L40-L45](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.broker-workspace.tsx#L40-L45) | Trả summary DTO, lead inbox và hành động follow-up. |

### 4.7 Bàn kiểm duyệt tin

| ID | Mức | Trạng thái | Phát hiện | Phương án |
|---|---:|---|---|---|
| MOD-01 | P0 | hardcode sai sự thật | Mọi hồ sơ được chọn đều hiện “Sổ đỏ/Sổ hồng chứng thực”, “Đã có sổ hồng chính chủ”, “Ảnh chuẩn • 0 vi phạm”, “Không trùng tọa độ” mà không đọc API. [Moderation L432-L458](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.moderation.tsx#L432-L458) | Chỉ hiển thị kết quả có nguồn, timestamp, rule/version; nếu chưa kiểm tra phải là `NOT_CHECKED`. |
| MOD-02 | P1 | hardcode | “SLA ≤8h”, “Diff 8 trường”, “Chuẩn hóa 100%” là copy cố định, không phải metric. [Moderation L140-L151](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.moderation.tsx#L140-L151), [L173-L194](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.moderation.tsx#L173-L194) | Tính elapsed/SLA thật; số trường lấy `diff.diffs.length`; bỏ “100%”. |
| MOD-03 | P0 | ⚠️ | Nếu moderator để note trống, approve ghi “Hồ sơ pháp lý đầy đủ” dù UI không có chứng cứ. [Moderation L77-L89](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.moderation.tsx#L77-L89) | Note bắt buộc hoặc note trung tính; tách duyệt nội dung khỏi xác minh pháp lý. |
| MOD-04 | P2 | ⚠️ | Item hàng đợi là `div onClick`, không keyboard/role/tabIndex. [Moderation L262-L303](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.moderation.tsx#L262-L303) | Dùng `<button>`/listbox đúng ARIA. |
| MOD-05 | P2 | ⚠️ | Lỗi tải danh sách lý do chỉ console; khi rỗng người dùng không biết nguyên nhân. [Moderation L34-L43](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.moderation.tsx#L34-L43) | Error + retry riêng. |
| MOD-06 | — | ✅ | Làm mới, lọc, chọn hồ sơ, tải diff, approve và reject đều có API thật và có busy/error chính. [moderationApi L10-L33](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/entities/moderation/api/moderationApi.ts#L10-L33) | Giữ, bổ sung E2E nghiệp vụ. |

### 4.8 Quản lý dự án

Ngoài lỗi DTO P0-09, các hành động sau là giả:

| Hành động | Trạng thái | Bằng chứng |
|---|---|---|
| Xuất báo cáo | ❌ Chỉ toast, không tạo/download file | [Projects L339-L355](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.projects.tsx#L339-L355) |
| Duyệt thay đổi | ❌ Chỉ đổi React state, không gọi API; toast luôn nói Masteri West Heights | [Projects L266-L282](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.projects.tsx#L266-L282) |
| Đối chiếu Diff | ❌ Chỉ toast | [Projects L662-L683](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.projects.tsx#L662-L683) |
| Sửa hồ sơ | ❌ Chỉ toast, không mở form/API | [Projects L685-L695](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.projects.tsx#L685-L695) |
| Lịch sử Revision | ❌ Chỉ toast | [Projects L696-L704](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.projects.tsx#L696-L704) |
| Quản lý N tin | ❌ Chỉ toast, không điều hướng | [Projects L705-L715](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.projects.tsx#L705-L715) |

Hardcode ghi DB tiềm ẩn: form mặc định Nam Từ Liêm/Hà Nội/5ha/3 tòa/1.200 căn/Sở Xây dựng; POST luôn gửi tọa độ `21.002,105.748`, dù UI không cho chọn: [Projects L129-L144](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.projects.tsx#L129-L144), [L202-L219](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.projects.tsx#L202-L219). Các chỉ số “100% khởi tạo chuẩn” và “100% pháp lý” cũng là tĩnh: [L359-L379](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.projects.tsx#L359-L379).

Backend Project hiện chỉ có create/list/get, không có update/revision/approve/export: [ProjectController L26-L64](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/catalog/api/ProjectController.java#L26-L64). Vì vậy phải thiết kế API/state machine trước, không chỉ nối handler frontend.

### 4.9 CMS

| ID/Hành động | Mức | Trạng thái | Phát hiện |
|---|---:|---|---|
| CMS-01 Đối chiếu Diff | P1 | ❌ | Chỉ toast cố định “Revision 1 và Revision 2”. [CMS L680-L688](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.cms.tsx#L680-L688) |
| CMS-02 Xem trang công khai | P1 | ❌ | Chỉ toast; router không có route bài viết public dù backend có API public theo slug. [CMS L690-L700](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.cms.tsx#L690-L700), [CMS controller L99-L123](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/cms/api/CmsArticleController.java#L99-L123) |
| CMS-03 Tạo Revision mới | P1 | ❌ | Chỉ toast; backend chưa có create/update revision endpoint. [CMS L701-L711](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.cms.tsx#L701-L711) |
| CMS-04 Nộp duyệt draft | P1 | ➖ | Modal nói “cần nộp duyệt”; backend có endpoint submit, nhưng UI không có nút gọi nó. [CMS L777-L780](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.cms.tsx#L777-L780), [controller L71-L77](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/cms/api/CmsArticleController.java#L71-L77) |
| CMS-05 Timestamp/reviewer | P2 | hardcode | Mọi record tải về có `createdAt='Hôm nay'`; record mới “Vừa xong”; approve tự gán `Admin Tổng biên tập`, bỏ qua response/actor thật. [CMS L151-L198](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.cms.tsx#L151-L198), [L271-L299](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.cms.tsx#L271-L299) |
| CMS-06 Reject reason | P1 | ⚠️ | Cho gửi reason rỗng rồi tự ghi một lý do pháp lý cố định. [CMS L302-L334](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.cms.tsx#L302-L334) |
| CMS-07 Compliance badge | P0 | hardcode sai sự thật | Mọi bài hiện “1200x630”, “Clean HTML Anti-XSS ✓”, “Canonical self-referencing ✓” dù không có kiểm tra. [CMS L590-L605](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.cms.tsx#L590-L605), [L642-L651](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.cms.tsx#L642-L651). Không tìm thấy sanitizer trong CMS service/domain; `contentHtml` được lưu trực tiếp. |
| CMS-08 Load error | P2 | ⚠️ | GET non-OK thành `null`, catch rỗng; không có loading/error/retry. [CMS L151-L201](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.cms.tsx#L151-L201) |
| CMS-09 Create/approve/reject | — | ✅/⚠️ | Ba API có thật; create/approve/reject hoạt động ở transport, nhưng approve/reject đang tự sửa local state thay vì dùng response server. [CMS controller L33-L96](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/cms/api/CmsArticleController.java#L33-L96) |

**Sửa an toàn:** sanitize bằng allowlist ở server khi ghi và render; tạo public article route; thêm submit/edit/revision/archive endpoints; tất cả trạng thái sau mutation phải lấy response server hoặc reload.

### 4.10 Analytics

Ngoài sai DTO P0-09:

- “Xuất CSV” tạo file thật nhưng cột lấy từ field `undefined`, nên kết quả không dùng được: [Analytics FE L13-L18](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.analytics.tsx#L13-L18).
- Backend hardcode `impressions=0`, `detailViews=0`; endpoint overview gần như toàn số 0. [FunnelController L26-L60](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/api/FunnelAnalyticsController.java#L26-L60).

**Sửa:** event table/outbox cho impression/detail/lead/contact/appointment/closed; aggregate theo time window; CSV dùng đúng typed DTO và escape chống CSV injection.

### 4.11 Lead và báo xấu

| ID | Mức | Trạng thái | Phát hiện | Phương án |
|---|---:|---|---|---|
| OPS-01 | P0 | ➖ | Card report không có bất kỳ nút tạm ẩn/resolve/dismiss/appeal. Trong khi API helper và backend đều có. [Leads page](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.leads-and-reports.tsx#L1-L5), [leadApi L4-L15](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/entities/lead/api/leadApi.ts#L4-L15), [Violation API L79-L137](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/api/ViolationReportController.java#L79-L137) | Modal xử lý theo state machine, bắt buộc lý do, confirm hành động phá hủy, audit. |
| OPS-02 | P1 | ⚠️ | Dropdown lead gọi PATCH nhưng không busy/try-catch/rollback; lỗi trở thành unhandled promise và UI không giải thích. [Leads page](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.leads-and-reports.tsx#L1-L5) | Optimistic update có rollback hoặc pessimistic busy, toast lỗi. |
| OPS-03 | P1 | ⚠️ | Hai API mặc định chỉ lấy 50, UI dùng `array.length` làm tổng số và không có pagination/load more. [LeadController L66-L94](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/api/LeadController.java#L66-L94), [Violation API L54-L68](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/api/ViolationReportController.java#L54-L68) | Page response `{items,total,page,size}` và pagination. |
| OPS-04 | P1 | security prerequisite | Rate-limit filter kiểm tra nhầm `/api/v1/reports` thay vì public path `/api/v1/public/reports`; cần sửa trước khi mở form báo xấu. [RateLimit L20-L33](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/RequestRateLimitFilter.java#L20-L33) | Rate limit distributed theo IP/session/listing; chỉ tin trusted proxy headers. |

### 4.12 Verification desk

Ngoài P0-07:

| ID | Mức | Trạng thái | Phát hiện | Phương án |
|---|---:|---|---|---|
| VERIFY-01 | P1 | fallback luôn xảy ra | FE dùng `listingTitle || Tin {id}`, nhưng backend response không có `listingTitle`; vì vậy title thật không bao giờ hiện. [FE type L24-L43](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/entities/verification/model/types.ts#L24-L43), [backend DTO L10-L22](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/api/response/ListingVerificationResponse.java#L10-L22) | Backend projection join listing summary hoặc FE fetch detail. |
| VERIFY-02 | P1 | ⚠️ | Approve/reject không busy, confirm hay try-catch; double-click/unhandled rejection có thể xảy ra. [Verification page](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.verification.tsx#L1-L5) | Busy theo hồ sơ, Problem Details, confirmation và reload server response. |
| VERIFY-03 | P1 | ➖ | Trang có nhãn eKYC nhưng chỉ gọi `/verifications`; không có UI `/kyc/queue`, approve/reject KYC, hay revoke nhãn dù backend có endpoint. [KycController L65-L94](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/api/KycController.java#L65-L94), [ListingVerificationController L127-L139](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/api/ListingVerificationController.java#L127-L139) | Tách hai tab/workspace và quyền rõ ràng. |

### 4.13 Trang Contracts

`_account.contracts.tsx` chỉ là trang cảnh báo tĩnh, nút “Về trang chủ” hoạt động, nhưng component **không được import/đăng ký trong router**, nên là dead page: [Contracts](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.contracts.tsx#L1-L15), [routes L6-L42](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes.tsx#L6-L42). Quyết định rõ: thêm route nếu đây là trang an toàn giao dịch, hoặc xóa file.

## 5. Ma trận đầy đủ các nhóm hành động UI

Ma trận này nhóm các button lặp theo cùng hành vi; không đếm mỗi card động như một chức năng khác nhau.

| Màn hình | Nhóm hành động ✅ hoạt động | Nhóm ⚠️/❌/➖ cần sửa |
|---|---|---|
| Header/Auth | mở/đóng menu, login/register, logout, link route đã khai báo | ❌ 4 footer route; ❌ public report; ❌ Swagger localhost; ❌ support; ➖ forgot password; ➖ notification inbox |
| Home | đổi SALE/RENT; submit search; 3 chip gợi ý; Bộ lọc; mở detail | ⚠️ “nổi bật” không có logic nổi bật; ⚠️ lỗi API giống empty |
| Search/Map | submit search; purpose/type/price filter; detail; đóng banner; search bounds | ⚠️ clear keyword; ⚠️ reset; ⚠️ verified list/count/map; ❌ sort; ❌ compare selection; ⚠️ card-map sync |
| Compare | diff-only; remove; detail; back | ❌ “Chọn lại” không có selector; ❌ tự chọn tin |
| Detail/Lead modal | mở/đóng modal; consent; gửi lead có idempotency; hoàn tất | ❌ ảnh detail; ❌ badge verified; ⚠️ back; ➖ report violation |
| Create listing | purpose/type/step local; upload/remove ảnh; save/submit transport; back | ⚠️ save thiếu field; ⚠️ submit revision cũ; ❌ privacy toggle; ❌ verification; ❌ fake estimate; ⚠️ broker CTA theo role |
| My Listings | filter; create; view; submit transport | ❌ edit; ⚠️ submit error/busy; ⚠️ load error |
| Billing | mua gói; báo đã chuyển; lưu bank; approve transport | ⚠️ fallback plan; ⚠️ fixed approval note; ➖ cancel/reject/refund |
| Broker | reload; billing/detail links; lưu SLA | ⚠️ reminder/digest không có worker; ➖ lead action |
| Moderation | reload; filter; select; diff; approve/reject API; modal controls | ❌ kết luận pháp lý hardcode; ⚠️ default audit note; ⚠️ row keyboard |
| Projects | modal/filter controls | ❌ create DTO; ❌ export; ❌ approve revision; ❌ diff; ❌ edit; ❌ history; ❌ manage listings |
| CMS | modal/filter; create draft; approve/reject transport | ❌ diff; ❌ public view; ❌ new revision; ➖ submit draft; ❌ compliance badges |
| Analytics | reload transport; tạo file CSV ở browser | ❌ DTO hiển thị; ❌ nội dung CSV; ⚠️ dữ liệu funnel hardcode 0 |
| Leads/Reports | tải dữ liệu; PATCH trạng thái lead | ➖ public submit UI; ➖ hide/resolve/dismiss/appeal; ➖ reveal/call lead; ⚠️ PATCH feedback |
| Verification | tải queue; approve/reject transport | ❌ không xem chứng từ; ❌ title fallback; ⚠️ blind approve; ➖ KYC/revoke |
| Verify email | gọi verify; về home | ➖ resend ở trạng thái lỗi |
| Contracts | về home | ❌ page không có route |

## 6. Phân loại toàn bộ hardcode/fallback đáng chú ý

### 6.1 Phải bỏ hoặc chuyển thành dữ liệu thật

1. Form đăng tin prefill một tài sản Matrix One, giá/diện tích/pháp lý/tọa độ/mô tả.
2. Công thức định giá và confidence 94% ở cả FE/BE; hai công thức còn mâu thuẫn nhau.
3. Quality score tính riêng ở FE và BE với trọng số khác nhau (`location` 15 vs 20, `title` 20 vs 15).
4. Privacy 100m/200m trong copy, nhưng không có thuật toán làm mờ.
5. Kết luận pháp lý/ảnh/trùng tọa độ tại Moderation.
6. Default approval note nói hồ sơ pháp lý đầy đủ.
7. Project defaults, fixed coordinates, fixed authority, fixed toast Masteri và metric 100%.
8. CMS timestamp “Hôm nay/Vừa xong”, reviewer “Admin Tổng biên tập”, reject reason mặc định, 1200x630, Anti-XSS, canonical ✓.
9. Billing plan fallback “Miễn phí” và fixed reconciliation note.
10. Broker SLA mặc định bật dù không có worker.
11. Backend listing fallback title/purpose/type/price/area và tọa độ Hà Nội `21.0/105.8` khi revision thiếu: [ListingController L203-L220](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/api/ListingController.java#L203-L220), [L230-L259](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/api/ListingController.java#L230-L259). Fallback này che lỗi dữ liệu và ghim record lỗi lên Hà Nội; nên fail/omit coordinate thay vì bịa.

### 6.2 Có thể giữ nhưng nên chuyển thành cấu hình/CMS

- Quick search terms ở Home.
- Nhãn Hà Nội Pilot, tâm/zoom bản đồ, URL tile provider.
- Các price band dưới 3 tỷ/3–5 tỷ/trên 5 tỷ.
- Danh sách loại BĐS, district Project, category CMS, nhãn trạng thái. Nên sinh từ enum/reference API hoặc một source-of-truth typed config.
- Branding/copyright/copy hướng dẫn có thể là static content, nhưng các tuyên bố đo lường như “100%”, “SLA 8h”, “đã xác thực” phải lấy từ dữ liệu.

### 6.3 Fallback hiển thị an toàn, có thể giữ

- Placeholder khi không có ảnh.
- “Người đăng chưa cung cấp mô tả”, “Chưa cung cấp”, “Chưa xác định”, “Chưa đủ dữ liệu”, với điều kiện không biến thiếu dữ liệu thành một kết luận tích cực.
- `VITE_PUBLIC_API_BASE_URL || '/api/v1'` là fallback cấu hình hợp lệ cho same-origin reverse proxy: [client L1-L18](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/shared/api/client.ts#L1-L18).

### 6.4 Không phải runtime fallback

- `INITIAL_PROJECTS` và `INITIAL_ARTICLES` nằm trọn trong block comment, nên **không chạy**. Không nên báo nhầm hai mảng này là nguồn dữ liệu hiện tại: [Projects L48-L119](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.projects.tsx#L48-L119), [CMS L49-L122](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.cms.tsx#L49-L122).
- Sáu tin trải nghiệm được insert thật vào PostgreSQL qua Flyway; đây là **seed data**, không phải fallback frontend. Tuy nhiên production nên tách profile demo/seed, gắn nhãn dữ liệu mẫu hoặc không chạy migration này trên dữ liệu thật: [V013 L1-L13](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/resources/db/migration/V013__seed_experience_listings.sql#L1-L13).

## 7. Kế hoạch sửa theo thứ tự

### Đợt 0 — Hotfix niềm tin và dữ liệu (trước release)

1. Sửa `isVerified` Detail; tạm ẩn mọi badge/kết luận pháp lý không có evidence.
2. Tắt/ẩn privacy toggle và verification CTA cho đến khi backend hỗ trợ thật; không hiện success giả.
3. Tạo `ListingSummaryDto` và `ListingDetailDto` riêng ở FE; sửa gallery ảnh.
4. Sửa save/submit transaction; đảm bảo mọi field nhìn thấy đều round-trip DB.
5. Tạm đổi estimate thành “công thức minh họa” hoặc tắt; bỏ confidence 94%.
6. Disable Project/Analytics bằng feature flag cho đến khi hợp đồng API đúng.

### Đợt 1 — Hoàn tất workflow cốt lõi

1. Edit listing theo ID và revision thật.
2. Report violation public + bàn xử lý đầy đủ.
3. Lead reveal/call proxy an toàn và broker lead inbox.
4. Verification document viewer/checklist/KYC queue/revoke.
5. Search sort, verified filter nhất quán và compare selection.
6. CMS submit/edit/new revision/public article; Project update/revision/approve/export/listing link.

### Đợt 2 — Độ tin cậy và maintainability

1. OpenAPI-generated client, schema validation runtime ở boundary, contract tests.
2. Chuẩn hóa error code/message; không match theo câu tiếng Việt.
3. Pagination response có total; loading/error/empty thống nhất.
4. Feature flags cho chức năng chưa có worker/backend.
5. Tách demo seed khỏi migration production.

## 8. Definition of Done bắt buộc cho mọi nút/action

Một control chỉ được coi “hoạt động” khi đạt đủ:

- Nhãn mô tả đúng kết quả; không dùng toast để giả lập mutation/điều hướng.
- Có trạng thái idle/loading/success/error; mutation bị disable khi đang chạy.
- API response được validate theo schema; UI lấy trạng thái cuối từ server, không tự bịa actor/time/status.
- Refresh trang vẫn thấy kết quả đã lưu; test DB round-trip chứng minh persistence.
- Có xử lý 400/401/403/404/409/422/429/500/network timeout.
- Hành động nhạy cảm có confirm, reason, audit actor/time/request ID.
- Có Playwright test click control và assert **side effect cuối cùng**, không chỉ assert toast/URL.
- Link phải có route thật; wildcard phải trả 404.
- Không hiển thị claim “verified/clean/100%/SLA” nếu không có evidence từ server.

## 9. Bộ regression test tối thiểu cần bổ sung

1. `listing-detail-contract.spec`: API `imageUrls` hiển thị gallery; unverified owner không có badge.
2. `listing-draft-roundtrip.spec`: mọi trường nhập được lưu và tải lại nguyên vẹn.
3. `listing-save-then-submit.spec`: thay đổi sau lần save vẫn nằm trong revision được submit.
4. `location-obfuscation.spec`: public point cách private point trong range cam kết và ổn định qua refresh.
5. `search-sort-filter.spec`: bốn sort và verified cho cùng count/list/map.
6. `compare-selection.spec`: chỉ các ID user chọn xuất hiện.
7. `project-openapi-contract.spec` và `analytics-openapi-contract.spec`.
8. `report-lifecycle.spec`: submit → emergency hide → resolve/dismiss → audit.
9. `lead-contact-access.spec`: owner được reveal có audit; người khác 403; list vẫn masked.
10. `verification-evidence-gate.spec`: không thể approve khi chứng từ chưa tải/checklist chưa đủ.
11. `cms-lifecycle.spec`: draft → submit → reject/revise → approve → public route; payload XSS bị sanitize.
12. `navigation-links.spec`: quét toàn bộ internal href và yêu cầu route khác 404/home fallback.

## 10. Ghi chú xác minh

- `npm run build` tại snapshot trên: **PASS** (`tsc -b && vite build`). Điều này chỉ chứng minh compile/bundle; TypeScript không phát hiện được DTO runtime do code đang cast/khai báo sai kiểu.
- Build cảnh báo chunk Search khoảng 1,04 MB; đây là vấn đề hiệu năng riêng, không làm thay đổi kết luận chức năng.
- E2E hiện chỉ kiểm tra mở detail, accessibility modal login và visual/a11y của Home/Search/Compare; chưa kiểm tra side effect của các action quản trị: [public navigation test](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/tests/e2e/public-navigation.spec.ts#L1-L10), [authenticated test](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/tests/e2e/authenticated-flows.spec.ts#L1-L2), [visual test](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/tests/e2e/public.visual-a11y.spec.ts#L1-L3).

---

**Phán quyết:** có nền tảng code/API thật ở nhiều luồng, nhưng sản phẩm hiện pha trộn chức năng thật, chức năng local-only và tuyên bố hardcode. Rủi ro lớn nhất không phải “nút không đổi màu”, mà là **nút/tuyên bố khiến người dùng tin rằng dữ liệu đã được lưu, xác thực, làm mờ hoặc đối soát trong khi hệ thống chưa thực hiện việc đó**.
