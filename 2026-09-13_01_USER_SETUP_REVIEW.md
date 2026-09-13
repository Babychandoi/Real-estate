# Đánh giá người dùng và khả năng setup — bản cập nhật 13/09/2026

**Ngày đánh giá:** 13/09/2026. **Repository:** [Babychandoi/Real-estate](https://github.com/Babychandoi/Real-estate).

**Bản được kiểm tra:** `main` tại [8afba2c](https://github.com/Babychandoi/Real-estate/commit/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2), commit “feat: complete production platform workflows”, lúc 07:43:03 UTC ngày 13/09/2026. Đối chiếu bản trước `4f52c76`; có 1 commit mới, 200 tệp thay đổi. Mọi liên kết source trong báo cáo được cố định theo SHA này.

**Cách đọc bằng chứng:** “Xác nhận từ source” là kết luận về đường đi trong mã/cấu hình; “đã chạy” là kết quả kiểm tra thực tế; “cần kiểm thử” là giả thuyết hoặc tiêu chí nghiệm thu còn thiếu. Đã đối chiếu nội dung 328 tệp văn bản với Git blob SHA. Đây không phải chứng nhận kiểm thử toàn bộ sản phẩm. Không thực hiện pentest, tải lớn hay thao tác mua bán trên hệ thống công khai; chưa xác nhận website đang chạy đúng SHA này.


## 1. Nhận xét chính

**Trải nghiệm đã vượt mức giao diện mô phỏng, nhưng chuỗi “tìm nhà → gửi liên hệ → người bán gọi lại” chưa hoàn chỉnh.** Tôi có cơ sở để dùng bản này làm beta có hỗ trợ vận hành sau khi sửa các lỗi chặn dưới đây. Tôi chưa khuyến nghị mở rộng thu hút khách hoặc thu phí đăng tin đại trà.

Ở vai người mua, thao tác xem tin, lọc và gửi yêu cầu dễ hiểu hơn trước. Ở vai người bán, vấn đề lớn nhất là không có hành trình đầy đủ để tiếp nhận và sử dụng thông tin liên hệ của khách. Ở vai người cài đặt, hướng dẫn hiện tại chưa tái lập được một bản demo nhanh trên máy mới chỉ bằng việc đổi vài mật khẩu.

Những nhận xét dưới đây là walkthrough từ giao diện và API trong code, không phải khảo sát người dùng thực tế hay tuyên bố đã thao tác thành công trên website live.

## 2. Tiến bộ so với bản trước

| Hạng mục | Bản mới có gì tốt | Phần vẫn cần hoàn thiện |
| --- | --- | --- |
| Đăng nhập | Tài khoản server, BCrypt, phiên có hạn và thu hồi; có xác minh email | Cấu hình SMTP, phục hồi tài khoản, xử lý lỗi gửi email |
| Hẹn xem nhà | POST thật, giữ dữ liệu khi lỗi, chỉ báo thành công khi có `leadId`, có idempotency key | Bên nhận chưa có đầy đủ màn hình và quyền khai thác lead |
| Ảnh và nội dung | Upload MinIO, chi tiết dùng dữ liệu backend | Cơ chế giữ/xóa tài liệu còn lỗi; cần tối ưu ảnh nhiều kích thước |
| Đăng tin và gói dịch vụ | Có quota, mã chuyển khoản, admin đối soát | Quy trình ngoại lệ và hỗ trợ thanh toán chưa đủ |
| Trải nghiệm frontend | Lazy-load từng trang, dialog có focus/Escape, bố cục responsive | Chưa có bằng chứng toàn bộ hành trình trên mobile; bundle tìm kiếm còn lớn |

Nguồn: [Đăng ký, đăng nhập và phiên](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/iam/application/AuthService.java#L48), [Form hẹn xem nhà](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/features/lead/ui/LeadConsultationModal.tsx#L24), [Lưu và đọc media](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/media/MediaStorageService.java#L63), [Mua gói và đối soát](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/billing/BillingService.java#L28), [Router và phân quyền trang](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes.tsx#L6).

## 3. Walkthrough người mua và người bán

| Bước thực tế | Nhận xét theo code | Đánh giá |
| --- | --- | --- |
| Mở trang, xem tin bán/thuê | Có danh sách thật qua API, trạng thái tải, trang chi tiết, giá và diện tích rõ | Có nền tảng sử dụng |
| Lọc và sắp xếp | UI gửi `sortBy`, nhưng Elasticsearch không đưa sort vào query, DB fallback luôn theo ngày tạo | Chức năng có trên UI nhưng không nhất quán |
| Xem tin đã bị ẩn | ES giữ ID tin cũ; đường hydrate không kiểm tra lại `ACTIVE` | Có thể thấy kết quả không còn được phép công khai |
| Gửi hẹn xem | Có consent, validation cơ bản, mã yêu cầu và giữ form khi lỗi | Cải thiện rõ |
| Chờ người bán liên hệ | DTO chỉ có số điện thoại đã che; chưa có API giải mã/reveal hoặc kênh gọi thay thế | **Chặn chuyển đổi cốt lõi** |
| Chủ nhà USER nhận lead | USER được đăng tin, nhưng `/api/v1/leads/**` không cho role USER; bàn lead frontend chỉ ADMIN/MODERATOR | **Chặn tự phục vụ của chủ nhà** |
| Môi giới nhận lead | API có truy vấn theo broker; workspace UI chỉ thống kê/SLA và danh sách tin | Chưa có bàn chăm sóc khách hoàn chỉnh |
| Mua gói đăng tin | Có VietQR, báo đã chuyển khoản, admin duyệt và cộng quota | Dùng được về mặt thiết kế cho đối soát thủ công, chưa được E2E xác minh |
| Xác minh danh tính/tin | Backend có nộp và duyệt, nhưng frontend chưa có hành trình KYC cá nhân; bàn duyệt không hiển thị tài liệu | Chưa hoàn thành hành trình tạo niềm tin |

Nguồn: [Bộ lọc và trạng thái tìm kiếm](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L9), [Đồng bộ và tìm kiếm Elasticsearch](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/search/ElasticsearchListingIndex.java#L20), [Tải tin từ kết quả tìm kiếm](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/infrastructure/persistence/adapter/ListingPersistenceAdapter.java#L69), [Thông tin liên hệ trả về](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/api/response/LeadResponse.java#L9), [Quy tắc truy cập API](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/SecurityConfig.java#L55), [Giao diện workspace môi giới](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_account.broker-workspace.tsx#L1), [Router và phân quyền trang](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes.tsx#L6), [Bàn thẩm định frontend](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.verification.tsx#L1).

### U-01 — Người bán không có đủ thông tin để gọi lại khách — P0

Form nói thông tin sẽ gửi đến người phụ trách, nhưng chỉ lưu lead chưa đủ để thực hiện lời hứa đó. `LeadResponse` chỉ trả `maskedPhone`; trong source được rà soát chưa có endpoint reveal, hàm giải mã số điện thoại hay luồng gọi/chuyển tiếp thay thế. Outbox lead chỉ mang ID, không tự giải quyết khoảng trống này. USER còn bị chặn ở lớp security trước khi kiểm tra quyền chủ tin. [Form hẹn xem nhà](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/features/lead/ui/LeadConsultationModal.tsx#L24); [Thông tin liên hệ trả về](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/api/response/LeadResponse.java#L9); [AES-GCM và blind index](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/PiiProtectionService.java#L28); [Nhận và xử lý lead](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/application/LeadApplicationService.java#L46); [Quy tắc truy cập API](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/SecurityConfig.java#L55).

**Cách sửa:** tạo “Khách quan tâm tin của tôi” cho cả chủ nhà USER và BROKER; phân quyền theo chủ sở hữu tin ở backend. Thêm thao tác xem thông tin liên hệ hoặc kênh liên lạc trung gian theo consent; ghi audit, hạn mức và lý do truy cập. Không mở số điện thoại cho toàn bộ người đăng nhập.

**Nghiệm thu:** khách A gửi yêu cầu vào tin của B → B thấy thông báo và lead → B liên hệ được → chuyển trạng thái chăm sóc → tài khoản C không đọc được lead/số điện thoại. Chạy cho cả USER và BROKER, gồm phiên hết hạn và retry.

### U-02 — Setup “demo nhanh” không khớp cấu hình mẫu — P0

README yêu cầu copy `.env.example`, đổi một số mật khẩu rồi chạy. Nhưng mẫu hiện đặt `APP_MODE=production`, yêu cầu khóa PII, MFA và SMTP thật; `APP_PUBLIC_BASE_URL` còn là `https://example.com`. Preflight kiểm tra `APP_SECURITY_ALLOWED_ORIGINS`, trong khi Compose và Spring dùng `APP_ALLOWED_ORIGINS`. Ngay cả khi điền đúng biến ứng dụng, preflight production vẫn từ chối vì đọc sai tên. [Hướng dẫn khởi động](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/README.md#L63); [Biến môi trường mẫu](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/.env.example#L4); [Kiểm tra cấu hình trước khởi động](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/scripts/preflight.ps1#L17).

Cloudflared nằm trong stack mặc định, cần file config không được track và đường dẫn `${USERPROFILE}` tới credential của một máy cụ thể. Máy Linux hoặc máy Windows mới không có sẵn các tệp này. Điều đó làm stack không khởi động đầy đủ; các service ứng dụng khác có thể vẫn khởi động riêng. Fallback SMTP là `mailpit` nhưng Compose không có service tương ứng; còn mẫu `.env` dùng Gmail nên cần tài khoản SMTP thật. [Cloudflare Tunnel trong Compose](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/docker-compose.yml#L144); [Docker Compose](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/docker-compose.yml#L2).

**Cách sửa:** phát hành hai mẫu `.env.demo.example` và `.env.production.example`; demo có mail sandbox nội bộ, dữ liệu giả được gắn nhãn và không cần tunnel. Chuyển tunnel sang profile tùy chọn; chuẩn hóa một tên biến duy nhất. Cung cấp script cho Bash và PowerShell, in URL đăng nhập/inbox và lỗi cấu hình dễ sửa. Không giảm yêu cầu secret của production để làm demo chạy.

**Nghiệm thu:** người chưa biết repo chạy theo README trên máy Windows và Linux sạch; không sửa source, không cần credential của tác giả; tạo tài khoản, nhận email, đăng nhập được. Chỉ công bố thời gian setup sau khi đo cold build và lần chạy sau trên cấu hình máy/mạng được ghi rõ.

### U-03 — Người dùng thấy tin đã ẩn hoặc sắp xếp không đúng — P0/P1

ES chỉ upsert các tin đang ACTIVE, không xóa tài liệu của tin chuyển sang PAUSED/LOCKED. Khi tìm kiếm trả ID cũ, backend hydrate không lọc lại trạng thái. Đây là lỗi tính đúng đắn và kiểm duyệt, không chỉ độ trễ cache 60 giây. `sortBy` cũng không được dùng trong query ES và fallback. [Đồng bộ và tìm kiếm Elasticsearch](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/search/ElasticsearchListingIndex.java#L20); [Tải tin từ kết quả tìm kiếm](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/infrastructure/persistence/adapter/ListingPersistenceAdapter.java#L69); [Truy vấn tin đăng và revisions](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/infrastructure/persistence/repository/ListingJpaRepository.java#L25); [Ẩn và khóa tin đăng](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/domain/model/Listing.java#L225).

**Cách sửa:** chặn ngay kết quả không công khai bằng dữ liệu trạng thái authoritative; đồng bộ delete/update bằng sự kiện bền vững. Ánh xạ rõ LATEST/PRICE_ASC/PRICE_DESC/AREA_DESC và thứ tự phụ ổn định ở cả hai engine.

**Nghiệm thu:** tin đang tìm thấy → chủ tin ẩn hoặc admin khóa → không còn xuất hiện qua search/API public. Bộ dữ liệu giá/diện tích được biết trước trả đúng thứ tự khi ES hoạt động và khi fallback.

### U-04 — Khách hợp lệ có thể bị chặn lâu dài sau 10 yêu cầu — P1

`countByPhoneLookupHash` đếm toàn bộ lịch sử, không có cửa sổ thời gian. Thông báo lại nói “trong thời gian ngắn”. Tại 10 lead của cùng hash, những yêu cầu sau sẽ bị chặn cho tới khi dữ liệu được xử lý theo cách khác; ngay cả replay idempotency cũng bị kiểm tra ngưỡng trước. [Nhận và xử lý lead](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/application/LeadApplicationService.java#L46); [Đếm lead theo số điện thoại](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/lead/infrastructure/persistence/repository/LeadJpaRepository.java#L17).

**Cách sửa:** chuẩn hóa số điện thoại, giới hạn theo cửa sổ có TTL; kiểm tra replay hợp lệ trước quota chống spam; cho khách biết thời điểm thử lại. Đo ảnh hưởng đến tỷ lệ tạo lead thành công.

### U-05 — Tin trải nghiệm được đưa vào dữ liệu chung — P1

Migration V013 tạo 6 tin ACTIVE thuộc tài khoản seed có `password_hash=NULL`, không thể đăng nhập. Migration nằm trong đường Flyway dùng chung, không có điều kiện demo. Không thấy nhãn UI và cơ chế chặn lead riêng cho các tin này. Khách có thể gửi yêu cầu vào tin không có người bán thực tiếp nhận. Đây là dữ liệu mẫu được lưu DB, chưa trở thành nguồn cung thương mại thật. [Migration dữ liệu trải nghiệm](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/resources/db/migration/V013__seed_experience_listings.sql#L1); [Cấu hình Spring Boot](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/resources/application.yml#L19); [Trang chi tiết và liên hệ](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.listings.$listingId.tsx#L132).

**Cách sửa:** tách seed demo khỏi production bằng migration mới/quy trình dữ liệu có kiểm soát; không sửa migration đã áp dụng. Nếu giữ trang trình diễn, gắn nhãn rõ và chặn CTA gửi thông tin thật. Admin phải có cách xử lý các lead mẫu đã phát sinh.

### U-06 — Xác minh và thanh toán cần các nhánh hỗ trợ — P1

Thiếu đường đi KYC cá nhân đầy đủ; tài liệu có nguy cơ bị cleanup xóa dù đang được tham chiếu. Email gửi sau DB commit chưa có hàng đợi retry: SMTP lỗi có thể làm khách nhận lỗi dù tài khoản đã được tạo. Thanh toán mới có nhánh báo chuyển khoản/duyệt; chưa có hủy, từ chối có lý do, hoàn tiền hay trang xem invoice. [Dữ liệu hồ sơ KYC trả về](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/api/response/UserKycResponse.java#L25); [Xóa media và dọn orphan](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/media/MediaStorageService.java#L124); [Gửi email sau commit](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/iam/application/AuthService.java#L120); [Mua gói và đối soát](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/billing/BillingService.java#L28).

**Cách sửa:** hoàn thành các trạng thái “chờ xử lý”, “cần bổ sung”, “bị từ chối”, “gửi lại”; hiển thị thời gian phản hồi dự kiến và kênh hỗ trợ. Không gọi bản ghi invoice nội bộ là hóa đơn điện tử hợp lệ nếu chưa có nghiệp vụ tương ứng.

## 4. Responsive và cảm giác sử dụng

Điểm tốt trong code là bố cục grid có breakpoint, dialog giới hạn chiều cao, nút chạm tối thiểu khoảng 44px ở các form mới, focus trap/Escape và thông báo `role=alert/status`. Router lazy-load giúp bundle entry giảm: bản mới khoảng **293,47 kB JS / 92,43 kB gzip**, so với lần build bản trước **568,05 / 146,60 kB**. Tuy nhiên chunk trang search chứa bản đồ vẫn khoảng **1.041,19 / 280,65 kB**, chưa tính worker và CSS. Đây là kích thước build, không phải số đo thời gian tải hay Core Web Vitals. [Form hẹn xem nhà](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/features/lead/ui/LeadConsultationModal.tsx#L24); [Router và phân quyền trang](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes.tsx#L6); [Bộ lọc và trạng thái tìm kiếm](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_public.search.tsx#L9).

Ưu tiên trì hoãn import bản đồ đến khi mở chế độ bản đồ, ảnh thumbnail responsive và bảo toàn bộ lọc khi quay về từ chi tiết. Link “Quay lại danh sách” hiện trỏ về `/`; lỗi tải chi tiết bị gom thành “Không tìm thấy”, khiến lỗi mạng giống tin không tồn tại. Cần phân biệt lỗi mạng/404/tin bị ẩn và có nút thử lại.

Repo có Playwright cho 320/768/1440 và các engine, nhưng test `authenticated-flows` hiện chỉ mở/đóng dialog đăng nhập; không chứng minh mua gói, đăng tin hay xử lý lead thành công. [Cấu hình kiểm thử trình duyệt](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/playwright.config.ts#L2); [Test hộp thoại đăng nhập](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/tests/e2e/authenticated-flows.spec.ts#L1); [Test ảnh, accessibility và tràn ngang](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/tests/e2e/public.visual-a11y.spec.ts#L1).

## 5. Kế hoạch khắc phục theo giá trị sử dụng

| Thứ tự | Việc cần hoàn thành | Người phụ trách phù hợp | Điều kiện kết thúc |
| --- | --- | --- | --- |
| P0 | Setup demo tái lập; loại phụ thuộc tunnel/máy cá nhân | Backend + DevOps | Fresh setup hai hệ điều hành và email activation pass |
| P0 | Luồng lead đến đúng chủ tin và liên hệ được | Backend + Frontend + QA | U-01 pass với hai vai người bán và kiểm tra quyền chéo |
| P0 | Không hiện tin đã ẩn; không xóa tài liệu đang dùng | Backend + QA | Test trạng thái tin và vòng đời media pass |
| P1 | Sort đúng, rate limit có thời gian, tách tin mẫu | Backend + Product | Bộ dữ liệu kiểm tra cho kết quả đúng; khách hợp lệ không bị khóa vô hạn |
| P1 | Ngoại lệ thanh toán, KYC, SMTP và hỗ trợ người dùng | Product + Full stack | UAT có cả nhánh chuẩn và ngoại lệ |
| P2 | Tối ưu mobile và tăng chuyển đổi | Frontend + UX | Đo trên thiết bị thực, có dữ liệu tốc độ và hoàn thành tác vụ |

**Bộ UAT bắt buộc:** đăng ký → email → login; tạo nháp → upload → gửi duyệt → bị từ chối → sửa → duyệt; tìm kiếm → hẹn xem → người bán nhận và liên hệ; mua gói → đối soát → quota; cùng các nhánh mất mạng, hết phiên, gửi lặp, tin khóa, ảnh lỗi, SMTP tạm ngừng.

## 6. Kết quả kiểm chứng và quyết định sử dụng

| Kiểm tra | Kết quả độc lập trong lần đánh giá này |
| --- | --- |
| `npm ci` và `npm run build` | **PASS** trên Node 24.19.0, npm 11.9.0; TypeScript và Vite build thành công. CI cấu hình Node 22, nên vẫn cần pipeline trên đúng runtime CI. |
| `npm audit --json` | **0 vulnerability được registry báo cáo** cho cây dependency frontend tại thời điểm kiểm tra; không đại diện bảo mật backend/container hay lỗi nghiệp vụ. |
| `sh mvnw --batch-mode verify` | **CHƯA XÁC MINH**: không resolve được parent Spring Boot 3.3.4 vì DNS của `repo.maven.apache.org` trong môi trường đánh giá. Chưa đến bước compile/test; không kết luận code backend build lỗi. |
| GitHub Actions của commit | **FAIL ở Set up job**: không tìm thấy `aquasecurity/trivy-action@0.28.0`; job chưa chạy các bước kiểm thử/build. [Run 34745938355](https://github.com/Babychandoi/Real-estate/actions/runs/34745938355). |
| Docker toàn bộ stack, E2E nghiệp vụ, load/soak, restore | **CHƯA CHẠY trong lần đánh giá này**. Môi trường không có Docker daemon; không suy ra các kiểm tra đã pass từ tài liệu trong repo. |


**Quyết định:** thích hợp để tiếp tục hoàn thiện beta; chưa đạt “cài nhanh rồi tự mua/bán thuận tiện” cho người dùng mới. Hoàn thành luồng liên hệ và setup có tác động thực tế lớn hơn việc thêm nhiều trang mới. Sản phẩm hiện có phạm vi kết nối tin đăng và bán gói đăng tin; giao dịch sở hữu BĐS/đặt cọc trực tuyến chưa thuộc phần đã được nghiệm thu.
