# Đánh giá Senior Security — bản cập nhật 13/09/2026

**Ngày đánh giá:** 13/09/2026. **Repository:** [Babychandoi/Real-estate](https://github.com/Babychandoi/Real-estate).

**Bản được kiểm tra:** `main` tại [8afba2c](https://github.com/Babychandoi/Real-estate/commit/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2), commit “feat: complete production platform workflows”, lúc 07:43:03 UTC ngày 13/09/2026. Đối chiếu bản trước `4f52c76`; có 1 commit mới, 200 tệp thay đổi. Mọi liên kết source trong báo cáo được cố định theo SHA này.

**Cách đọc bằng chứng:** “Xác nhận từ source” là kết luận về đường đi trong mã/cấu hình; “đã chạy” là kết quả kiểm tra thực tế; “cần kiểm thử” là giả thuyết hoặc tiêu chí nghiệm thu còn thiếu. Đã đối chiếu nội dung 328 tệp văn bản với Git blob SHA. Đây không phải chứng nhận kiểm thử toàn bộ sản phẩm. Không thực hiện pentest, tải lớn hay thao tác mua bán trên hệ thống công khai; chưa xác nhận website đang chạy đúng SHA này.


## 1. Kết luận an toàn thông tin

**Chưa đạt điều kiện mở production rộng rãi với hồ sơ cá nhân thật.** Các biện pháp nền tảng đã cải thiện: xác thực server, phân quyền mặc định chặn, mã hóa PII thật, bucket riêng tư, kiểm tra upload, MFA và outbox có fencing. Tuy vậy vẫn có đường đi gây mất tài liệu, sai quyền nộp hồ sơ xác minh, công khai lại tin đã bị ẩn và né rate limit.

Không gán CVSS hoặc tuyên bố đã khai thác thực tế khi chưa chạy PoC trong môi trường cô lập. Mức độ bên dưới là đánh giá kỹ thuật theo tác động và điều kiện trong source. **P0** là chặn phát hành; **P1** là sửa trước mở rộng; **P2** là tăng độ bền sau khi các luồng cốt lõi đạt kiểm thử. Severity và mức ưu tiên là hai khái niệm khác nhau.

## 2. Các điểm đã làm đúng và cần giữ

| Kiểm soát | Bằng chứng | Nhận xét |
| --- | --- | --- |
| API deny-by-default, quyền quản trị | [Quy tắc truy cập API](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/SecurityConfig.java#L55) | Cải thiện lớn so với truy cập quá rộng; vẫn cần kiểm tra quyền ở từng tài nguyên |
| BCrypt cost 12, token ngẫu nhiên, lưu hash, hết hạn/thu hồi | [Đăng ký, đăng nhập và phiên](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/iam/application/AuthService.java#L48) | Không nên thay bằng token tự suy diễn ở frontend; truy cập token vẫn phải chống XSS |
| AES-GCM nonce ngẫu nhiên, HMAC-SHA256 với khóa riêng | [AES-GCM và blind index](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/PiiProtectionService.java#L28) | Đã là mã hóa thực; không còn đánh đồng Base64 với bảo mật |
| KYC đọc theo owner hoặc người có quyền | [Phân quyền hồ sơ KYC](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/api/KycController.java#L34), [Lưu và đọc media](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/media/MediaStorageService.java#L63) | Đường đọc private được bảo vệ; lỗi nộp tham chiếu vẫn tồn tại |
| Ảnh random object key, giới hạn kích thước, magic bytes, ClamAV | [Lưu và đọc media](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/media/MediaStorageService.java#L63), [Docker Compose](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/docker-compose.yml#L2) | Giảm rủi ro upload; không bảo đảm media lifecycle chính xác |
| Production từ chối secret thiếu và provider chưa nghiệm thu | [Điều kiện khởi động production](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/config/ProductionSafetyValidator.java#L58) | Fail-fast hợp lý; phải thống nhất ý nghĩa flag với API đang hoạt động |
| Outbox `SKIP LOCKED`, lease token và HMAC webhook | [Outbox có lease và fencing](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/outbox/OutboxLeaseRepository.java#L17), [Gửi webhook outbox](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/outbox/OutboxWebhookDispatcher.java#L34) | Giảm worker tranh chấp; vẫn là at-least-once, receiver phải chống trùng |
| CORS allowlist, CSP, header bảo vệ trình duyệt | [Quy tắc truy cập API](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/SecurityConfig.java#L55), [Gateway Nginx](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/nginx.conf#L1) | Có lớp bảo vệ nền tảng; không thay cho object-level authorization |

CSRF bị tắt không tự động là lỗ hổng trong thiết kế hiện dùng Bearer header và không tự gửi credential bằng cookie. Nếu chuyển sang cookie HttpOnly, cần thiết kế CSRF/SameSite phù hợp. Session token trong `sessionStorage` vẫn đọc được bằng JavaScript, nên phải quản lý rủi ro XSS; chưa tìm thấy bằng chứng XSS thực thi trong lần đánh giá này. [Quản lý phiên frontend](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/shared/auth/AuthContext.tsx#L1); [Quy tắc truy cập API](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/SecurityConfig.java#L55).

## 3. Danh mục phát hiện

| ID | Severity | Ưu tiên | Phát hiện | Độ chắc chắn |
| --- | --- | --- | --- | --- |
| SEC-01 | High | P0 | Cleanup/xóa media không bảo toàn tài liệu KYC đang tham chiếu | Xác nhận từ SQL và service |
| SEC-02 | High | P0 | Người đăng nhập nộp xác minh cho tin không thuộc mình | Xác nhận thiếu owner check trên đường gọi |
| SEC-03 | High | P0 | Tin PAUSED/LOCKED vẫn có thể xuất hiện trong search public qua ES | Xác nhận chuỗi index → hydrate |
| SEC-04 | High | P1 | KYC nhận URL đúng prefix mà không xác minh object/owner; duyệt thiếu ràng buộc | Xác nhận từ service |
| SEC-05 | High | P1 | Tin cậy X-Forwarded-For tùy ý; rate limit mỗi tiến trình, sai path reports | Xác nhận code; khai thác tùy đường đi origin/proxy |
| SEC-06 | Medium | P1 | Toàn bộ admin/moderator dùng chung TOTP secret, chưa chặn replay | Xác nhận từ cấu hình và verifier |
| SEC-07 | Medium | P1 | Audit hash chain có thể phân nhánh; lỗi ghi không ngăn nghiệp vụ; bỏ qua đọc nhạy cảm | Xác nhận thiết kế, cần test đồng thời |
| SEC-08 | Medium | P1 | Credential MinIO root được cấp cho backend; lifecycle khóa PII chưa đủ | Xác nhận cấu hình và mã khóa |

### SEC-01 — Tài liệu đã nộp bị xử lý như ảnh mồ côi

**Bằng chứng:** `cleanupOrphans()` chọn `media_objects` cũ hơn 24 giờ chỉ dựa trên việc không nằm trong `listing_media` với URL public. Không có lọc `visibility='PUBLIC'`, không kiểm tra `user_kyc_profiles` hay tài liệu xác minh. `delete()` cũng chỉ kiểm tra tham chiếu từ tin đăng. [Xóa media và dọn orphan](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/media/MediaStorageService.java#L124).

**Điều kiện/tác động:** khi media storage và lịch cleanup hoạt động, object KYC riêng tư tồn tại hơn 24 giờ có thể được chọn ở một lần chạy sau, tối đa 100 object/lần. Hồ sơ vẫn còn trong DB nhưng ảnh bị xóa khỏi MinIO. Người sở hữu cũng có thể đi qua API xóa ảnh để xóa tài liệu của mình đã gắn vào hồ sơ nếu biết object key. Không kết luận mọi ảnh bị xóa chính xác tại mốc 24 giờ.

**Sửa:** ngay lập tức loại KYC khỏi cleanup public; thiết kế bảng tham chiếu media dùng chung, trạng thái staging/attached/quarantined/deletion-pending, retention và khóa/chống race khi gắn ảnh. Ưu tiên soft delete có thời gian khôi phục cho tài liệu nghiệp vụ. Backup hiện có cần được kiểm tra trước khi chạy tác vụ dọn dữ liệu.

**Nghiệm thu tại staging với ảnh giả:** hồ sơ pending/verified/rejected còn hạn lưu → chạy cleanup sau mốc thời gian → object vẫn đọc được đúng quyền. Ảnh staging thực sự bỏ dở được dọn; gắn ảnh đồng thời với cleanup không tạo tham chiếu gãy. Không dùng dữ liệu CCCD thật để kiểm thử.

### SEC-02 — BOLA ở nghiệp vụ xin xác minh tin đăng

**Bằng chứng:** controller dùng `CurrentUser.id()` nhưng `submitVerification()` chỉ đọc listing, không so sánh `listing.ownerId` với actor. Nó lấy KYC của người gửi rồi gắn vào listing được truyền vào. `approveVerification()` có thể đặt `markVerifiedOwner(true)` mà không xác nhận KYC đã VERIFIED hoặc thuộc đúng chủ tin. [API nộp xác minh tin](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/api/ListingVerificationController.java#L41); [Nộp và duyệt xác minh tin](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/application/ListingVerificationApplicationService.java#L37); [Quy tắc truy cập API](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/SecurityConfig.java#L55).

**Điều kiện/tác động:** tài khoản thông thường biết UUID của một tin có thể nộp hồ sơ cho tin đó. Việc cấp nhãn vẫn cần ADMIN/MODERATOR duyệt; đây không phải tự động chiếm quyền admin. Tuy nhiên dữ liệu hồ sơ và căn cứ cho badge bị người không có quyền tác động.

**Sửa:** bắt buộc owner hoặc quyền đại diện được lưu rõ ràng; khóa/ràng buộc trạng thái nộp; verify người nộp, tài liệu và quan hệ ủy quyền trước khi approve. Badge phải mang nghĩa chính xác: xác minh người đăng không đồng nghĩa bảo đảm quyền sở hữu pháp lý.

**Nghiệm thu:** A không thể nộp/sửa hồ sơ của listing B; moderator có quyền duyệt nhưng không bỏ qua điều kiện tài liệu; hồ sơ bị thu hồi hoặc quyền đại diện hết hạn gỡ badge nhất quán.

### SEC-03 — Chỉ mục cũ làm bỏ qua quyết định ẩn/khóa tin

**Bằng chứng:** đồng bộ ES chỉ PUT các tin ACTIVE, không DELETE tin chuyển trạng thái. Search trả UUID; `hydrateInOrder` tải revisions theo ID mà không kiểm tra ACTIVE; PAUSED/LOCKED vẫn giữ `publicRevisionId`. [Đồng bộ và tìm kiếm Elasticsearch](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/search/ElasticsearchListingIndex.java#L20); [Tải tin từ kết quả tìm kiếm](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/infrastructure/persistence/adapter/ListingPersistenceAdapter.java#L69); [Truy vấn tin đăng và revisions](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/infrastructure/persistence/repository/ListingJpaRepository.java#L25); [Ẩn và khóa tin đăng](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/listing/domain/model/Listing.java#L225).

**Tác động:** summary, giá, vị trí công khai và ảnh của tin cần gỡ có thể tiếp tục được trả qua search, ngay cả khi API detail đã chặn. Tắt ES để fallback chỉ giảm một nhánh lỗi, không phải cách sửa kiến trúc lâu dài.

**Sửa:** enforce trạng thái public tại lớp đọc authoritative; sự kiện UPSERT/DELETE bền vững; version/tombstone để sự kiện cũ không làm sống lại tin. Không chấp nhận HTTP 400 chung cho mọi request ES: chỉ bỏ qua đúng mã lỗi “index đã tồn tại” tại thao tác tạo index.

**Nghiệm thu:** ACTIVE → PAUSED/LOCKED → search/detail/map/cache không lộ tin; sau restart worker hoặc giao event lệch thứ tự vẫn không xuất hiện lại.

### SEC-04 — KYC kiểm tra chuỗi URL thay cho kiểm tra bằng chứng

`submitKyc()` chỉ kiểm tra ba prefix `/api/v1/media/kyc/`; không đối chiếu object tồn tại, owner, visibility, trạng thái scan hoặc tài liệu đang được dùng. Có thể tạo hồ sơ với URL không tồn tại hoặc URL của tài khoản khác nếu đã biết. Điều này chưa chứng minh đọc trộm ảnh: đường `readPrivate` vẫn có quyền riêng. [Nộp và duyệt KYC](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/application/KycApplicationService.java#L12); [Lưu và đọc media](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/media/MediaStorageService.java#L63).

Response KYC trả cả ba URL là null, và bàn thẩm định UI không có bộ xem tài liệu. Duyệt thủ công hiện thiếu đầu vào kiểm chứng trong sản phẩm. Ngoài ra `FEATURE_REAL_KYC=false` không tắt các endpoint manual KYC trong controller; production validator chỉ từ chối bật provider flag. Cần định nghĩa rõ flag nào quản lý nhận hồ sơ thủ công và flag nào cho provider. [Dữ liệu hồ sơ KYC trả về](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/api/response/UserKycResponse.java#L25); [Bàn thẩm định frontend](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/app/routes/_admin.verification.tsx#L1); [Phân quyền hồ sơ KYC](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/verification/api/KycController.java#L34); [Điều kiện khởi động production](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/config/ProductionSafetyValidator.java#L58).

**Sửa/nghiệm thu:** submit dùng media ID có FK/reference và owner check; reviewer đọc tài liệu bằng endpoint phân quyền được audit; không cấp badge khi hồ sơ thiếu/chưa được kiểm chứng. Test URL giả, owner khác, object đã xóa, hồ sơ gửi lại và duyệt đồng thời.

### SEC-05 — Rate limit dễ bị né và không bảo vệ đúng tất cả endpoint

Filter lấy phần tử đầu của `X-Forwarded-For`; Nginx dùng `$proxy_add_x_forwarded_for`, giữ prefix do client gửi. Nếu origin nhận request không được một trusted edge chuẩn hóa, thay header tạo bucket mới. Root Compose mở frontend `3000:3000`, nên phải bảo vệ origin bằng network policy/firewall thực tế. Map bộ nhớ không có eviction theo số lượng identity; nhiều key mới có thể làm tăng heap. Path `/api/v1/public/reports` lại không khớp path `/api/v1/reports` trong filter. [Giới hạn tần suất](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/RequestRateLimitFilter.java#L18); [Gateway Nginx](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/frontend/nginx.conf#L1); [Docker Compose](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/docker-compose.yml#L2).

**Sửa:** chuẩn hóa client IP tại proxy tin cậy, chỉ cho edge truy cập origin; rate limit Redis với TTL và số key được kiểm soát; kết hợp IP + account cho login/MFA, resource cho upload và số điện thoại cho lead. Thêm đúng endpoint reports, geocoding; hỗ trợ Retry-After. Giữ giới hạn khi tăng replica hoặc restart.

**Nghiệm thu:** header do client chèn không tăng quota; thử qua gateway thật trên staging, gồm direct-origin bị chặn; hai pod dùng cùng ngưỡng; heap không tăng vô hạn theo IP giả. Không thực hiện flood lên production.

### SEC-06 — MFA chung secret không phù hợp vận hành nhiều quản trị viên

Verifier chỉ nhận role và code, dùng một secret từ env cho mọi ADMIN/MODERATOR; cửa sổ ±1 timestep không lưu code/step đã dùng. Chưa có enrollment, recovery, revoke theo người. Một secret lộ ảnh hưởng tất cả tài khoản đặc quyền cùng dùng nó. [Xác thực TOTP](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/iam/application/TotpVerifier.java#L15).

**Sửa/nghiệm thu:** mỗi người một factor hoặc identity provider có MFA; secret mã hóa và quản lý theo user; chống replay một timestep, recovery code dùng một lần, thu hồi session khi đổi factor. Test mã của A không dùng cho B và mã đã dùng không đăng nhập lại trong cửa sổ chấp nhận.

### SEC-07 — Audit chưa có tính chống sửa và tính liên tục như tài liệu kỳ vọng

Filter đọc hash mới nhất rồi insert bản ghi riêng sau khi nghiệp vụ trả về, không khóa chuỗi. Hai request có thể cùng chọn một predecessor; SHA256 thuần không ngăn người có quyền ghi DB tính lại chuỗi. Lỗi audit chỉ được log; GET tài liệu KYC bị bỏ qua. Không nên coi đây là nhật ký bất biến/tamper-proof đã được chứng minh. [Ghi audit và chuỗi hash](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/AuditTrailFilter.java#L28).

**Sửa/nghiệm thu:** audit nghiệp vụ quan trọng trong transaction/outbox; log đọc PII; gửi sang kho append-only với quyền ghi riêng và retention; checkpoint ký/HMAC ngoài DB nếu cần chống sửa. Test đồng thời, mất đích audit, restart và truy vết actor/resource/requestId; không log token, số điện thoại đầy đủ hay nội dung CCCD.

### SEC-08 — Giảm quyền storage và hoàn thiện vòng đời khóa

Backend nhận `MINIO_ROOT_USER/PASSWORD` thay vì credential chỉ đủ cho bucket được giao. Mã PII có version định dạng nhưng không có key ID/registry phục vụ rotation và giải mã có kiểm soát. Demo có khóa mặc định có thể suy ra từ source; đây là fallback có chủ đích, chỉ phù hợp dữ liệu giả. [Docker Compose](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/docker-compose.yml#L2); [AES-GCM và blind index](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/main/java/com/company/bds/shared/security/PiiProtectionService.java#L28).

**Sửa/nghiệm thu:** tạo service account giới hạn bucket/prefix, tách quyền cleanup và đọc KYC nếu cần; key ID + secret manager/KMS + kế hoạch re-encrypt/dual-read. Diễn tập rotate và restore phải giữ được khả năng đọc dữ liệu cũ đúng quyền. Không chuyển secret vào biến `VITE_*` hoặc artifact frontend.

## 4. Chất lượng bằng chứng kiểm thử

| Kiểm tra | Kết quả độc lập trong lần đánh giá này |
| --- | --- |
| `npm ci` và `npm run build` | **PASS** trên Node 24.19.0, npm 11.9.0; TypeScript và Vite build thành công. CI cấu hình Node 22, nên vẫn cần pipeline trên đúng runtime CI. |
| `npm audit --json` | **0 vulnerability được registry báo cáo** cho cây dependency frontend tại thời điểm kiểm tra; không đại diện bảo mật backend/container hay lỗi nghiệp vụ. |
| `sh mvnw --batch-mode verify` | **CHƯA XÁC MINH**: không resolve được parent Spring Boot 3.3.4 vì DNS của `repo.maven.apache.org` trong môi trường đánh giá. Chưa đến bước compile/test; không kết luận code backend build lỗi. |
| GitHub Actions của commit | **FAIL ở Set up job**: không tìm thấy `aquasecurity/trivy-action@0.28.0`; job chưa chạy các bước kiểm thử/build. [Run 34745938355](https://github.com/Babychandoi/Real-estate/actions/runs/34745938355). |
| Docker toàn bộ stack, E2E nghiệp vụ, load/soak, restore | **CHƯA CHẠY trong lần đánh giá này**. Môi trường không có Docker daemon; không suy ra các kiểm tra đã pass từ tài liệu trong repo. |


Backend có **19 phương thức `@Test`** trong ba class; con số này là kiểm kê source, không phải kết quả chạy lần này. Profile test dùng H2, tắt Flyway và tạo schema bằng Hibernate; quota bị tắt. Vì vậy ngay cả khi 19 test pass cũng không chứng minh SQL PostgreSQL, migration, quota, ES, MinIO/ClamAV và multi-instance hoạt động đúng. [Profile kiểm thử backend](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/src/test/resources/application-test.yml#L1).

CI hiện tham chiếu Trivy sai; thêm nữa file `backend/mvnw` có Git mode `100644`, trong khi workflow gọi `./mvnw` trên Ubuntu. Sau khi sửa action cần sửa executable bit hoặc gọi `sh mvnw`. Đây là lỗi kế tiếp suy ra từ cấu hình, chưa phải bước đã chạy thất bại trong log CI. [Workflow CI](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/.github/workflows/ci.yml#L26); [Maven Wrapper](https://github.com/Babychandoi/Real-estate/blob/8afba2c24e19eb2fb7cc90fee7c71e8ff283f6e2/backend/mvnw#L1).

Không tìm thấy vulnerability qua npm audit không chứng minh không có lỗ hổng. Backend dependency scan, image scan, secret scan đầy đủ lịch sử và pentest động chưa được xác nhận; không suy đoán CVE chỉ từ số phiên bản.

## 5. Kế hoạch khắc phục và cổng an toàn

| Chặng | Công việc | Bằng chứng phải có |
| --- | --- | --- |
| Trước release | SEC-01/02/03; bảo vệ dữ liệu và trạng thái công khai | Regression tests trên PostgreSQL + ES + MinIO; phân quyền A/B |
| Trước nhận hồ sơ thật | SEC-04/06/08; vòng đời tài liệu, MFA cá nhân, key rotation | UAT tài liệu giả, ACL matrix, restore và rotation drill |
| Trước mở rộng traffic | SEC-05/07; chống abuse phân tán và audit | Kiểm thử nhiều pod, thử lỗi proxy/audit, dashboard và alert |
| Gate CI | Sửa pipeline; scan deps/container; test authz và workflow | Report gắn đúng commit và image digest, checks bắt buộc trước merge |

Đề xuất dùng **OWASP ASVS 5.0** làm khung truy vết yêu cầu xác thực, quyền truy cập, validation, data protection và logging; xác định phạm vi mục tiêu cùng đội dự án, không ghi “đạt chuẩn” chỉ vì có checklist. [OWASP ASVS](https://owasp.org/www-project-application-security-verification-standard/).

**Quyết định phát hành:** No-Go cho production mở rộng chứa KYC thật cho đến khi các P0 được đóng bằng bằng chứng kiểm thử và P1 liên quan dữ liệu nhạy cảm được xử lý. Đây là đánh giá kỹ thuật source của phiên bản chỉ định, chưa thay thế kiểm thử triển khai thực tế.
