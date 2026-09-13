# Đánh giá Senior đóng gói sản phẩm — bản cập nhật 13/09/2026

**Ngày đánh giá:** 13/09/2026. **Repository:** [Babychandoi/Real-estate](https://github.com/Babychandoi/Real-estate).

**Bản được kiểm tra:** `main` tại [8afba2c](https://github.com/Babychandoi/Real-estate/commit/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2), commit “feat: complete production platform workflows”, lúc 07:43:03 UTC ngày 13/09/2026. Đối chiếu bản trước `4f52c76`; có 1 commit mới, 200 tệp thay đổi. Mọi liên kết source trong báo cáo được cố định theo SHA này.

**Cách đọc bằng chứng:** “Xác nhận từ source” là kết luận về đường đi trong mã/cấu hình; “đã chạy” là kết quả kiểm tra thực tế; “cần kiểm thử” là giả thuyết hoặc tiêu chí nghiệm thu còn thiếu. Đã đối chiếu nội dung 328 tệp văn bản với Git blob SHA. Đây không phải chứng nhận kiểm thử toàn bộ sản phẩm. Không thực hiện pentest, tải lớn hay thao tác mua bán trên hệ thống công khai; chưa xác nhận website đang chạy đúng SHA này.


## 1. Độ hoàn thiện thực tế

**Bản mới là beta có nhiều nghiệp vụ thật, chưa phải gói sản phẩm có thể bàn giao để khách hàng tự vận hành ổn định.** Tiến bộ đáng ghi nhận là auth, ảnh, lead, đối soát gói đăng tin, notification và các cấu hình vận hành đã đi vào source. Tuy nhiên nhiều module chưa nối thành hành trình hoàn chỉnh và các tuyên bố nghiệm thu chưa được CI chứng minh.

Phạm vi hợp lý ở bản này là **nền tảng kết nối tin bán/cho thuê và bán gói đăng tin**. Chi tiết tin đã nói rõ không nhận cọc hoặc ký hợp đồng thay khách; provider giao dịch/eKYC thật bị chặn bật trong production. Không tính escrow chưa tích hợp là lỗi nếu được ghi rõ ngoài phạm vi release. Cần phân biệt điều đó với manual KYC đang có API và phải chịu trách nhiệm bảo vệ dữ liệu khi nhận hồ sơ. [Trang chi tiết và liên hệ](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.$listingId.tsx#L132); [Điều kiện khởi động production](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/config/ProductionSafetyValidator.java#L58); [Phân quyền hồ sơ KYC](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/api/KycController.java#L34).

Không chấm một tỷ lệ “hoàn thiện 90%” từ số lượng màn hình hay số FR trong tài liệu. Bảng sau đánh giá theo đường đi có thể nghiệm thu, gồm kết quả, ngoại lệ và khả năng vận hành.

## 2. Ma trận mức sẵn sàng theo module

| Module/hành trình | Phần đã hiện thực | Khoảng trống bàn giao | Mức hiện tại |
| --- | --- | --- | --- |
| Identity | BCrypt, session server, email activation, role | SMTP reliability, MFA cá nhân, phục hồi tài khoản | Một phần |
| Đăng tin | Draft/revision, upload, gửi duyệt, quota | Cần UAT revision/quota/retry và quyền theo actor | Có nền tảng |
| Search/map | API thật, ES, tọa độ, MapLibre | Sort bỏ qua, index không gỡ tin, search bundle lớn | Bị chặn bởi tính đúng đắn |
| Lead → chăm sóc | Ghi lead, idempotency, trạng thái backend | USER thiếu quyền, broker thiếu bàn lead, không có đường liên hệ đầy đủ | **Chưa khép kín** |
| Gói đăng tin | VietQR, báo chuyển khoản, duyệt một lần, quota/invoice row | Hủy/từ chối/hoàn tiền, snapshot điều khoản, tra cứu invoice | MVP đối soát thủ công |
| Thẩm định | Submit/approve/reject/revoke backend, queue UI | Ownership, tài liệu thật, viewer riêng tư, chống xóa hồ sơ | **Chưa đạt** |
| Thông báo | Lưu DB, lấy 50 thông báo gần nhất, SSE | Phát nhiều pod, replay, heartbeat, phát sau commit | Một phần |
| Broker SLA | Lưu ngưỡng và tùy chọn nhắc việc/digest | Chưa thấy worker thực thi các tùy chọn này | Cấu hình chưa thành dịch vụ |
| Docker/setup | Multi-stage, healthcheck, env, scripts | Mẫu demo/production mâu thuẫn, tunnel cá nhân, tên biến sai | Chưa tự phục vụ |
| QA/release | Java tests, Playwright/Axe, workflow scan | CI fail, môi trường E2E thiếu backend, thiếu kiểm thử nghiệp vụ thật | Chưa có release gate tin cậy |

Nguồn: [Đăng ký, đăng nhập và phiên](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/iam/application/AuthService.java#L48), [Trừ quota đăng tin](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/application/service/ListingApplicationService.java#L120), [Đồng bộ và tìm kiếm Elasticsearch](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/search/ElasticsearchListingIndex.java#L20), [Nhận và xử lý lead](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/application/LeadApplicationService.java#L46), [Workspace môi giới và SLA](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/broker/BrokerWorkspaceController.java#L4), [Mua gói và đối soát](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/billing/BillingService.java#L28), [Nộp và duyệt xác minh tin](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/application/ListingVerificationApplicationService.java#L37), [Thông báo và SSE](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/notification/RealtimeNotificationService.java#L20), [Docker Compose](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/docker-compose.yml#L2), [Workflow CI](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/.github/workflows/ci.yml#L26).

## 3. Các lỗi tích hợp cần chặn release

### PRD-01 — Hoàn thành đầu nhận lead trước khi thu hút traffic — P0

UI đã ghi nhận yêu cầu bằng mã thật, nhưng người bán USER không qua được quyền `/leads/**`. BROKER có endpoint riêng nhưng không có bàn lead tương ứng trong router; DTO chỉ trả số điện thoại đã che và chưa có luồng reveal/gọi thay thế. Các notification/outbox ID không tự tạo ra khả năng gọi khách. [Thông tin liên hệ trả về](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/api/response/LeadResponse.java#L9); [Quy tắc truy cập API](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/SecurityConfig.java#L55); [Router và phân quyền trang](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes.tsx#L6); [Giao diện workspace môi giới](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.broker-workspace.tsx#L1).

**Đầu ra cần bàn giao:** màn hình “Khách quan tâm”, quyền theo chủ tin, contact có consent/audit, trạng thái NEW/CONTACTED/APPOINTED/CLOSED/SPAM, lịch sử thao tác và tín hiệu quá hạn chăm sóc. Bài UAT phải chứng minh khách gửi → đúng chủ tin nhận → liên hệ được → đóng lead.

### PRD-02 — Bảo đảm nội dung công khai và hồ sơ không mất — P0

ES giữ tài liệu của tin đã ẩn/khóa, hydrate không recheck trạng thái. Cleanup ảnh xét mỗi `listing_media`, có thể xóa KYC hơn 24 giờ đang dùng. Hai lỗi này đánh thẳng vào niềm tin sản phẩm và phải sửa trước khi cải thiện dashboard. [Đồng bộ và tìm kiếm Elasticsearch](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/search/ElasticsearchListingIndex.java#L20); [Tải tin từ kết quả tìm kiếm](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/infrastructure/persistence/adapter/ListingPersistenceAdapter.java#L69); [Xóa media và dọn orphan](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/media/MediaStorageService.java#L124).

**Đầu ra:** hợp đồng trạng thái hiển thị nhất quán giữa search/detail/map; chính sách lưu/xóa media với tham chiếu rõ; test chuyển trạng thái và cleanup có concurrency. Không công bố tính năng xác minh đáng tin cậy trước khi có đủ đường nộp/duyệt tài liệu đúng quyền. [Nộp và duyệt xác minh tin](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/application/ListingVerificationApplicationService.java#L37); [Nộp và duyệt KYC](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/application/KycApplicationService.java#L12).

### PRD-03 — Gói cài đặt cần tái lập, không phụ thuộc máy tác giả — P0

`.env.example` mặc định production trong khi README hướng dẫn demo. Preflight đọc sai tên CORS env. Cloudflared mặc định cần file không được track và credential dưới USERPROFILE. README local dùng `mvn test` rồi chạy JAR, thiếu bước package trên checkout mới. [Biến môi trường mẫu](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/.env.example#L4); [Kiểm tra cấu hình trước khởi động](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/scripts/preflight.ps1#L17); [Cloudflare Tunnel trong Compose](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/docker-compose.yml#L144); [Hướng dẫn khởi động](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/README.md#L63).

**Đầu ra:** release archive/checkout có Quick Start demo riêng, env reference đúng tên, profile tùy chọn cho tunnel/production, one-command health smoke sau khi người dùng điền biến, hướng dẫn backup/update/rollback theo từng phiên bản. Pin artifact theo commit/image digest; đừng build source tùy ý trên máy production.

### PRD-04 — Chốt ngoại lệ của thu phí gói đăng tin — P1

Điểm tốt: approve chỉ cập nhật order đang TRANSFER_REPORTED; khi đã APPROVED không cộng quota lần nữa. Quota đăng tin có điều kiện còn hạn gói. Không kết luận “duyệt lại là cộng tiền/lượt hai lần” vì source đã chặn nhánh này. [Mua gói và đối soát](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/billing/BillingService.java#L28); [Trừ quota đăng tin](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/application/service/ListingApplicationService.java#L120).

Khoảng trống còn lại: tạo order chưa có idempotency; thiếu reject/cancel/refund; QR cho order cũ lấy cấu hình ngân hàng hiện tại qua join, chưa snapshot tài khoản nhận tiền; cấp quota khi approve lấy định nghĩa gói hiện tại. Invoice chỉ là bản ghi nội bộ trong đường ghi đã rà soát, chưa có luồng tra cứu/xuất chứng từ cho khách. [Mua gói và đối soát](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/billing/BillingService.java#L28); [Giao diện thanh toán gói đăng tin](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.billing.tsx#L1).

**Đầu ra:** snapshot giá/quota/thời hạn/ngân hàng/điều khoản tại thời điểm mua; idempotency và reference unique; quy trình đối soát sai nội dung, thiếu/thừa tiền, hết hạn, hủy/từ chối/hoàn tiền; lịch sử người duyệt; không quảng cáo “tự động nhận tiền” nếu vẫn duyệt thủ công. Những phần chứng từ chịu yêu cầu nghiệp vụ riêng cần được xác định trước, không suy ra từ tên bảng `invoices`.

### PRD-05 — Notification và SLA phải có hành vi thật — P1

`notify()` lưu DB rồi phát SSE ngay trong tiến trình, kể cả khi transaction của billing chưa commit. Client có thể nhận sự kiện trước khi đọc thấy trạng thái mới; nhiều pod không chia sẻ emitter. UI SLA lưu `reminder_enabled` và `daily_digest_enabled`, nhưng source được rà soát chưa có tác vụ gửi nhắc hoặc digest tương ứng. [Thông báo và SSE](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/notification/RealtimeNotificationService.java#L20); [Mua gói và đối soát](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/billing/BillingService.java#L28); [Workspace môi giới và SLA](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/broker/BrokerWorkspaceController.java#L4).

**Đầu ra:** outbox/after-commit phát sự kiện; phân phối sự kiện đến đúng gateway, heartbeat/reconnect/replay; worker SLA có chính sách chống gửi lặp và thử lại. Nếu chưa làm worker, UI phải nói rõ mức hỗ trợ thay vì cho bật một chức năng không thực thi.

### PRD-06 — Tách dữ liệu showcase khỏi nguồn cung thực — P1

V013 seed 6 tin ACTIVE thuộc chủ thể không có mật khẩu đăng nhập, trong migration dùng chung production. Dữ liệu thật trong DB không đồng nghĩa tin thật có người bán chịu trách nhiệm. [Migration dữ liệu trải nghiệm](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/resources/db/migration/V013__seed_experience_listings.sql#L1); [Cấu hình Spring Boot](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/resources/application.yml#L19).

**Đầu ra:** demo có cờ rõ, không thu thông tin khách thật; production không có tin mẫu chủ vô chủ. Nếu migration đã chạy, dùng migration/quy trình chuyển dữ liệu mới có truy vết, không sửa checksum migration cũ.

## 4. Tuân thủ quy tắc code và khả năng maintain

Kiến trúc module hiện có giúp tiếp tục phát triển Spring Boot + React TypeScript; chưa cần tách microservices chỉ để tạo cảm giác “enterprise”. Vấn đề hiện tại là một số phần thêm mới không tuân thủ ngay quy tắc đã ghi trong repo.

| Quy tắc đã yêu cầu | Thực trạng bản mới | Cách đưa về chuẩn |
| --- | --- | --- |
| Controller mỏng, application điều phối | BrokerWorkspaceController và GeocodingController query DB/điều phối trực tiếp; nhiều class bị nén vào vài dòng | Tách application service, port/adapter phù hợp, formatter bắt buộc; tránh tạo abstraction không có nhu cầu |
| Lỗi field vào `validation.properties`, lỗi nghiệp vụ vào messages | Auth/KYC/Billing/Broker còn nhiều chuỗi Việt hardcode trong exception/controller | Mã lỗi ổn định + message key, centralized mapper; FE xử lý code thay vì so khớp chuỗi |
| FE có quality gate thật | `lint` chỉ echo “Linting check passed” | ESLint hoặc công cụ lint thật, format check, typecheck; gate phải fail khi có lỗi |
| Env thống nhất, không phụ thuộc máy | CORS key lệch; domain/contact geocoding và tunnel mount hardcode | Typed config, một schema env có validation; kiểm tra tài liệu/config cùng nguồn |
| API contract có kiểu | Module mới dùng nhiều Map/Object và logic SQL trực tiếp | DTO có validation, contract test ở boundary, OpenAPI diff theo version |

Nguồn: [Quy tắc cấu trúc và phân lớp](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/PROJECT_CODE_RULES_BDS.md#L206), [Quy tắc validation và message](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/PROJECT_CODE_RULES_BDS.md#L430), [Workspace môi giới và SLA](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/broker/BrokerWorkspaceController.java#L4), [Geocoding](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/search/GeocodingController.java#L12), [Nộp và duyệt KYC](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/application/KycApplicationService.java#L12), [Mua gói và đối soát](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/billing/BillingService.java#L28), [Scripts và dependencies frontend](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/package.json#L6), [Kiểm tra cấu hình trước khởi động](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/scripts/preflight.ps1#L17).

Các khuyến nghị này nhằm khôi phục đúng quy ước maintain của dự án, không ép đổi stack. Đặt mục tiêu release không còn thông báo lỗi hardcode ở backend theo rule đã thống nhất, nhưng không cần xây hệ i18n quá mức cho mọi nội dung marketing.

## 5. Đánh giá QA và tuyên bố nghiệm thu

| Kiểm tra | Kết quả độc lập trong lần đánh giá này |
| --- | --- |
| `npm ci` và `npm run build` | **PASS** trên Node 24.19.0, npm 11.9.0; TypeScript và Vite build thành công. CI cấu hình Node 22, nên vẫn cần pipeline trên đúng runtime CI. |
| `npm audit --json` | **0 vulnerability được registry báo cáo** cho cây dependency frontend tại thời điểm kiểm tra; không đại diện bảo mật backend/container hay lỗi nghiệp vụ. |
| `sh mvnw --batch-mode verify` | **CHƯA XÁC MINH**: không resolve được parent Spring Boot 3.3.4 vì DNS của `repo.maven.apache.org` trong môi trường đánh giá. Chưa đến bước compile/test; không kết luận code backend build lỗi. |
| GitHub Actions của commit | **FAIL ở Set up job**: không tìm thấy `aquasecurity/trivy-action@0.28.0`; job chưa chạy các bước kiểm thử/build. [Run 34745938355](https://github.com/Babychandoi/Real-estate/actions/runs/34745938355). |
| Docker toàn bộ stack, E2E nghiệp vụ, load/soak, restore | **CHƯA CHẠY trong lần đánh giá này**. Môi trường không có Docker daemon; không suy ra các kiểm tra đã pass từ tài liệu trong repo. |


Các rủi ro gate tiếp theo có bằng chứng cấu hình:

1. Maven wrapper Git mode `100644`, workflow chạy trực tiếp trên Ubuntu; sửa executable bit hoặc dùng `sh` sau khi sửa Trivy. [Workflow CI](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/.github/workflows/ci.yml#L26); [Maven Wrapper](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/mvnw#L1).
2. Playwright chỉ khởi động Vite; workflow chưa khởi động backend/DB trước test điều hướng cần tin thật. Vì vậy cần môi trường ứng dụng hoặc mock contract được khai báo rõ, rồi E2E nghiệp vụ riêng với backend thật. [Cấu hình kiểm thử trình duyệt](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/playwright.config.ts#L2); [Test tìm kiếm sang chi tiết](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/tests/e2e/public-navigation.spec.ts#L1); [Workflow CI](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/.github/workflows/ci.yml#L26).
3. Tree có 21 baseline ảnh `*-win32.png`, CI chạy Ubuntu và config không chuẩn hóa platform snapshot path. Cần baseline Linux được review cho CI hoặc chiến lược platform rõ ràng; đây là rủi ro gate suy ra, chưa chạy screenshot test lần này. [Cấu hình kiểm thử trình duyệt](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/playwright.config.ts#L2); [Test ảnh, accessibility và tràn ngang](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/tests/e2e/public.visual-a11y.spec.ts#L1).
4. Backend kiểm kê có 19 `@Test`, nhưng profile H2 tắt Flyway và quota. Thêm integration test đúng PostgreSQL + migration cho billing, quota, outbox, media và search; không thay kiểm thử SQL thực bằng số test unit. [Profile kiểm thử backend](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/test/resources/application-test.yml#L1).
5. Test tên `authenticated-flows` hiện chỉ kiểm tra khả năng truy cập dialog bằng bàn phím; không chứa đăng nhập và thực hiện nghiệp vụ. [Test hộp thoại đăng nhập](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/tests/e2e/authenticated-flows.spec.ts#L1).

README vẫn ghi “100% Pass” ở traceability. Các nhãn CLOSED/GATED hoặc video/walkthrough nội bộ là đầu vào hữu ích, nhưng cần gắn test ID, dữ liệu, môi trường, report và SHA để được coi là bằng chứng release. Không sử dụng trạng thái tài liệu để ghi đè lỗi quan sát từ source/CI.

## 6. Đóng gói nghiệm thu theo Waterfall

| Gate | Hồ sơ cần cập nhật | Tiêu chí qua gate |
| --- | --- | --- |
| G1–G2: phạm vi và yêu cầu | Baseline phạm vi tin/lead/gói đăng tin; phân định escrow/provider; user story, use case chuẩn và ngoại lệ | Product owner xác nhận đầu ra và acceptance criteria cho từng vai |
| G3: phân tích/thiết kế | Sequence submit/approve/contact, state listing/media/payment, ERD media references, ACL matrix | Không còn đường chuyển trạng thái/ủy quyền mơ hồ |
| G4: hiện thực | Source + migration + API + env + UI gắn UC/FR | Feature có cả frontend, backend và xử lý ngoại lệ; không chỉ tạo route |
| G5: verification/UAT | Test result trên đúng DB/runtime và dữ liệu đại diện | UAT người mua/người bán/moderator/admin pass, P0 đóng, P1 được xử lý theo phạm vi |
| G6: bàn giao vận hành | Release manifest, runbook, backup/restore, rollback, SLO, hướng dẫn hỗ trợ | CI xanh đúng SHA, triển khai tái lập và diễn tập phục hồi có evidence |

Đề xuất ma trận truy vết mỗi dòng: `FR/UC → user story → trạng thái/sequence → API/UI/source → test chuẩn → test ngoại lệ → report/SHA → người nghiệm thu`. Không chỉnh các tài liệu trong repo ở lần review này; đây là yêu cầu đầu ra cho đợt khắc phục.

## 7. Backlog bàn giao theo thứ tự

| Ưu tiên | Kết quả cần đạt | Chủ trì |
| --- | --- | --- |
| P0 | Lead đến đúng chủ và liên hệ được; tin khóa không công khai; hồ sơ không bị xóa | Backend/Frontend + QA |
| P0 | Pipeline chạy; demo setup tái lập; test trên Postgres/migration thật | DevOps + QA |
| P1 | Các ngoại lệ billing/KYC/SLA/notification hoàn chỉnh | Product + Full stack |
| P1 | Tài liệu và code rules đồng bộ, seed demo tách khỏi production | Tech lead |
| P2 | Tối ưu bản đồ/ảnh, theo dõi funnel và usability test trên thiết bị thực | Frontend + UX |

**Quyết định bàn giao:** chưa ký nghiệm thu sản phẩm production hoàn chỉnh. Có thể nghiệm thu từng thành phần đã có bằng chứng và lập release candidate sau khi chốt ba đường xuyên suốt: đăng tin/kiểm duyệt, khách liên hệ/người bán xử lý, mua gói/đối soát/quota.
