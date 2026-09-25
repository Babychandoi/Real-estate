# Tái thẩm định Real-estate — Tổng quan điều hành

Ngày đánh giá: 2026-09-19  
Kho mã: [Babychandoi/Real-estate](https://github.com/Babychandoi/Real-estate)  
Snapshot cố định: [`b8d66bf251c117b2775a6b586a55ea8262ccaaff`](https://github.com/Babychandoi/Real-estate/commit/b8d66bf251c117b2775a6b586a55ea8262ccaaff) — `fix: namespace admin routes under 2026 path`  
Phạm vi thay đổi so với lần đánh giá trước: [89 tệp, +2.067/−641 dòng](https://github.com/Babychandoi/Real-estate/compare/0b394fea5397d6f7d3deb7623b0de8650f878fce...b8d66bf251c117b2775a6b586a55ea8262ccaaff)

## Kết luận ngắn

Phiên bản mới **tiến bộ rõ rệt**, đặc biệt ở quản lý tin đăng, lead, hồ sơ người dùng, khôi phục mật khẩu, ảnh đại diện, bảo vệ tài liệu eKYC, đối soát VietQR và shell quản trị. Frontend cài đặt/lint/build thành công, `npm audit --audit-level=high` không phát hiện lỗ hổng; CI chạy qua 21 kiểm thử backend và dựng được stack tích hợp.

Tuy nhiên, bản hiện tại vẫn là **beta có vận hành hỗ trợ**, chưa đạt điều kiện phát hành công khai có thu tiền và chưa có bằng chứng chịu tải lớn. Có bốn lỗi phải chặn phát hành:

1. Luồng nộp eKYC của người dùng bình thường luôn có nguy cơ trả `400`: backend bắt buộc `userId`, frontend không gửi trường này, trong khi controller thực tế lấy người dùng từ phiên đăng nhập.
2. API công khai cho phép người gọi tự chọn mức `P0_EMERGENCY`; service tự động tạm ẩn tin. Người ẩn danh có thể lợi dụng để đánh sập tin hợp lệ.
3. Đơn hàng lưu snapshot gói/ngân hàng nhưng khi hiển thị QR và duyệt lại dùng cấu hình hiện tại, làm thay đổi thụ hưởng/quyền lợi sau thời điểm đặt hàng.
4. API CMS công khai trả revision mới nhất và toàn bộ revision, nên có thể lộ nội dung nháp/từ chối của một bài đã xuất bản.

**Phán quyết:**

| Mốc sử dụng | Kết luận | Điều kiện |
|---|---|---|
| Demo/local | GO có lưu ý | Không dùng dữ liệu thật; chấp nhận CI đỏ do E2E cũ |
| Beta nội bộ | GO có điều kiện | Sửa toàn bộ P0, làm xanh CI, chạy smoke test luồng chính |
| Phát hành công khai/thu tiền | NO-GO | Cần sửa P0/P1, MFA/step-up, kiểm thử nghiệp vụ và DR |
| Hàng triệu người dùng đồng thời | NO-GO | Chưa có kiến trúc hoặc benchmark chứng minh |

## Điểm đánh giá

Điểm phản ánh snapshot này, không phải tiềm năng của thiết kế.

| Nhóm tiêu chí | Điểm | Nhận định |
|---|---:|---|
| Người dùng & setup | 6,5/10 | Luồng chính rộng hơn, nhưng eKYC bị chặn và README còn lệch thực tế |
| Sản phẩm, hardcode & hành động | 5,5/10 | Nhiều hành động đã nối API thật; vẫn có tác vụ “lưu cấu hình nhưng không chạy” và luồng dở dang |
| Bảo mật | 4,5/10 | Nền tảng xác thực/mã hóa tốt hơn, nhưng có lạm dụng report P0, lộ CMS draft, thiếu MFA và audit chưa bền vững |
| UI/UX & responsive | 6,0/10 | Giao diện có hệ thống và responsive ở đa số breakpoint; CI visual đỏ và có tràn ngang thật ở 320 px |
| Triển khai, hiệu năng & mở rộng | 3,0/10 | Demo stack tốt; manifest production chưa đồng bộ, tìm kiếm/SSE/media chưa sẵn sàng cho scale lớn |
| **Tổng hợp** | **5,1/10** | **Beta có vận hành hỗ trợ; chưa production-ready** |

## Những cải thiện đã xác nhận

- Sửa và bổ sung chỉnh sửa revision tin, ẩn/hiện tin, liên kết card và marker bản đồ, slug/canonical URL.
- Lead inbox có phân trang, lọc, tìm kiếm, tổng số lead, xem số điện thoại có consent và audit.
- Có hồ sơ cá nhân, avatar, xác minh email, quên/đặt lại mật khẩu; reset token được hash, dùng một lần và thu hồi phiên.
- eKYC media là private, kiểm tra ownership khi tải lên, xác nhận lại mật khẩu 10 phút cho chính chủ, magic-byte/MIME/ClamAV.
- Thanh toán VietQR và màn đối soát là luồng thật, có trạng thái, hủy, báo chuyển khoản, duyệt/từ chối.
- Màn dự án đã chuyển từ dữ liệu giả sang API thật; admin có shell riêng và route riêng.
- Không còn giả lập giá/quality từ backend: endpoint chưa triển khai trả 501 thay vì dữ liệu bịa; giao dịch thật vẫn bị production validator chặn.
- Docker Compose được CI khởi động thành công; frontend build chia route và có CSP/Nginx cache cơ bản.

## Rủi ro ưu tiên

### P0 — sửa trước mọi phát hành

| Mã | Rủi ro | Tác động | Hành động tối thiểu |
|---|---|---|---|
| P0-01 | DTO eKYC bắt buộc `userId` nhưng frontend không gửi | Chặn đăng tin/liên hệ do hệ thống yêu cầu KYC | Bỏ `userId` khỏi request, luôn lấy từ session; đồng nhất CCCD 12 số; thêm E2E |
| P0-02 | Anonymous report tự chọn `P0_EMERGENCY` | Đối thủ/bot có thể tự động tạm ẩn bất kỳ tin ACTIVE | Server cố định severity public; P0 chỉ từ moderator/rule tin cậy; chống abuse |
| P0-03 | Billing không dùng snapshot khi QR/approve | Sai tài khoản nhận tiền hoặc cấp sai gói/quota | Đọc toàn bộ dữ liệu từ snapshot của order; test thay đổi cấu hình sau khi đặt |
| P0-04 | CMS public trả draft/rejected revisions | Rò rỉ nội dung chưa duyệt/pháp lý | DTO public chỉ trả đúng `publishedRevisionId`; không trả danh sách revision |
| P0-05 | CI đỏ, main không bảo vệ, Trivy bị skip | Có thể merge/push bản lỗi và không quét security | Bảo vệ branch, required checks; tách Trivy thành job chạy độc lập/`always()` |

### P1 — hoàn tất trước public beta

- Thêm MFA hoặc step-up cho admin/moderator, đặc biệt eKYC, billing và moderation.
- Ghi audit khi staff đọc tài liệu KYC; tuần tự hóa hash chain hoặc dùng append-only store/sequence; quyết định fail-closed cho hành động nhạy cảm.
- Sửa quyền appeal theo chủ tin và kiểm tra ownership; không resume nếu còn report P0 chưa giải quyết.
- Không tin trực tiếp `X-Real-IP`; chỉ nhận từ trusted proxy, bổ sung throttle theo tài khoản/credential.
- Cập nhật Playwright locator/snapshot, tách visual–axe–overflow thành assertion độc lập; sửa tràn 320 px.
- Hoàn thiện chọn tin để so sánh, submit CMS, feedback thao tác my-listings, URL state/pagination của search.
- Chuyển đồng bộ Elasticsearch sang outbox/delta + bulk; dùng cursor/search-after; phân phối SSE qua pub/sub.

### P2 — trước khi tuyên bố production/scale lớn

- Chuẩn hóa một bộ K8s production triển khai được từ đầu đến cuối: frontend/backend/namespace/service/Ingress/HA dependencies/securityContext/NetworkPolicy/anti-affinity/HPA.
- CDN hoặc direct object delivery cho media; upload streaming; image variants.
- Sitemap index theo shard, cache; SSR/prerender cho trang công khai quan trọng; cập nhật robots cho namespace admin mới.
- Benchmark theo hành trình thực, capacity model và connection budget; chaos/restore test và bằng chứng RPO/RTO.

## Kết quả kiểm chứng

| Kiểm tra | Kết quả |
|---|---|
| HEAD từ GitHub | Khớp `b8d66bf251c117b2775a6b586a55ea8262ccaaff`; commit chưa ký |
| Branch protection | `main` không được bảo vệ; không có required checks |
| Frontend `npm ci` | Đạt |
| `npm audit --audit-level=high` | 0 lỗ hổng |
| Frontend lint/build | Đạt; MapLibre chunk khoảng 1,04 MB minified, worker khoảng 507 KB |
| Backend test trong GitHub CI | 21 test, 0 fail/error |
| Docker Compose trong CI | Stack khởi động và health check đạt |
| Playwright | 35 test: 8 đạt, 27 lỗi |
| Nguyên nhân E2E | 7 locator lệch chữ hoa/thường; 20 visual snapshot cũ; có overflow thật ở 320 px |
| Trivy | Bị skip vì nằm sau bước E2E lỗi trong cùng job |
| Backend verify cục bộ | Không chạy được do môi trường đánh giá không phân giải Maven Central; dùng kết quả CI làm bằng chứng |

Chi tiết CI: [GitHub Actions run 57](https://github.com/Babychandoi/Real-estate/actions/runs/35048152964).

## Cách đọc bộ báo cáo

- `01_NGUOI_DUNG_VA_SETUP_2026-09-19.md`: onboarding, setup và các hành trình người dùng.
- `02_BAO_MAT_2026-09-19.md`: auth, phân quyền, PII/media, abuse, audit và supply chain.
- `03_SAN_PHAM_HARDCODE_HANH_DONG_2026-09-19.md`: hardcode/fallback, nút/hành động và độ hoàn thiện nghiệp vụ.
- `04_TRIEN_KHAI_HIEU_NANG_MO_RONG_2026-09-19.md`: CI/CD, K8s, dữ liệu, tìm kiếm, SSE, media và scale.
- `05_UI_UX_RESPONSIVE_2026-09-19.md`: UI, accessibility, responsive, visual regression và SEO giao diện.

## Giới hạn

Đây là thẩm định mã nguồn và pipeline tại đúng commit nêu trên. Không có quyền truy cập dữ liệu production, telemetry, hạ tầng thật, kết quả pentest độc lập, restore drill hay benchmark quy mô lớn; vì vậy không thể suy diễn “chịu hàng triệu concurrent” chỉ từ manifest hoặc code.
