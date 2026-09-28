# Ma trận quyền truy cập API (F20.4 — least privilege)

Rà soát ngày 28/09/2026 (luồng S5-SEC pha B) trên nhánh `audit/s5b-sec`. Nguồn thực thi: quy tắc URL trong
`backend/src/main/java/com/company/bds/shared/security/SecurityConfig.java` (khớp theo thứ tự, quy tắc cuối
`anyRequest().denyAll()`), `@PreAuthorize` ở hai phương thức, và kiểm tra quyền sở hữu trong service. Vai trò hiệu lực:
ADMIN > MODERATOR > BROKER > OWNER > USER (`Roles.effectiveRoleSql`), đọc lại từ DB ở mỗi request.

**Bằng chứng tự động:** `backend/src/test/java/com/company/bds/iam/AccessMatrixTests.java` quét **mọi** route
`/api/**` đăng ký trong `RequestMappingHandlerMapping` (không phải danh sách chọn tay):

1. Khách chưa đăng nhập nhận 401 ở mọi route, trừ danh sách công khai tường minh (bảng 1). Route mới quên quy tắc →
   test đỏ. Mục trong danh sách không còn tồn tại → test đỏ (tránh danh sách mở rộng âm thầm).
2. Tài khoản USER nhận 403 ở mọi route nhân viên và quản trị (bảng 2, 3).
3. MODERATOR nhận 403 ở mọi route chỉ dành cho ADMIN (bảng 3).

Đã kiểm tra đột biến: bỏ `GET /api/v1/billing/plans` khỏi danh sách công khai → test báo đúng route đó.

## 1. Công khai (không cần phiên)

| Route | Ghi chú kiểm soát |
|---|---|
| `POST /api/v1/auth/{login,admin/login,register,resend-verification,forgot-password,reset-password}` | Rate limit IP + email (FAIL_CLOSED); câu trả lời trung tính cho forgot/resend |
| `GET/POST /api/v1/auth/verify-email`, `POST /api/v1/auth/password-reset/status` | Token 256 bit, băm SHA-256, dùng một lần, có hạn; rate limit IP |
| `POST /api/v1/auth/admin/mfa/{verify,enroll,enroll/confirm}` | Chỉ nhận challenge token (5 phút, dùng một lần, hủy sau 5 mã sai); rate limit IP |
| `GET /api/v1/public/**`, `/api/v1/listings/by-slug/**`, `/api/v1/listings/search`, `/api/v1/listings/{id}`, `/api/v2/listings/**`, `/api/v2/public/**` | Chỉ dữ liệu đã duyệt; số điện thoại/email không bao giờ có trong response công khai |
| `GET /api/v1/media/signed/**` | URL ký HMAC có hạn (capability), không phiên |
| `POST /api/v1/public/reports`, `POST /api/v1/public/unsubscribe`, `POST /api/v1/events` | Rate limit; unsubscribe mang token riêng; events không nhận user id từ body |
| `GET /api/v1/billing/plans` | Bảng giá công khai |

## 2. Nhân viên (MODERATOR, ADMIN)

`/api/v1/moderation/**`, `/api/v1/analytics/**`, `/api/v1/reports/**`, `/api/v1/verifications/**`,
`/api/v1/kyc/queue`, `/api/v1/kyc/*/{approve,reject,revoke}`, `/api/v1/catalog/**`, `/api/v1/cms/**`.
Nhân viên còn đọc được hộp lead (`/api/v1/leads/**`) ở chế độ giám sát; số điện thoại người hỏi luôn ở dạng che
(`LeadInboxQuery`), không bao giờ là liên hệ của người bán. Phiên nhân viên bắt buộc MFA, 8 giờ tuyệt đối, 30 phút không
hoạt động.

## 3. Chỉ ADMIN

`/api/v1/admin/**` (người dùng: vai trò, khóa/mở, đặt lại MFA, đăng xuất mọi nơi, xem giấy tờ KYC có lý do + nhật ký),
`/api/v2/admin/**` (chỉ mục tìm kiếm), `/api/v1/listings/admin/**`, `/api/v1/billing/admin/**`,
`POST /api/v1/transactions/deposits/*/{release,refund}`, `POST /api/v1/moderation/audit-samples/draw` (`@PreAuthorize`).

## 4. Đã đăng nhập (mọi vai trò) — quyền theo chủ sở hữu trong service

| Nhóm | Ai | Kiểm soát ở service |
|---|---|---|
| Đăng tin, kho tin, gói dịch vụ (`/api/v1/listings` ghi, `/my-listings`, `/api/v1/billing/**`, `/api/v2/me/listings/**`) | POSTERS (OWNER, BROKER, ADMIN) | Chỉ tin của chính mình; preview/draft nhân viên xem được (không public, no-store) |
| Lead phía người bán (`/api/v1/leads/**`) | POSTERS + STAFF | Người không phải nhân viên chỉ thấy lead của tin mình/được phân công; lạ → 404 |
| Không gian môi giới (`/api/v1/broker/**`) | BROKER, ADMIN | Theo đội (`broker_team_members`) |
| `/api/v1/me/**` (phiên, mật khẩu, MFA, tin đã lưu, tìm kiếm đã lưu, yêu cầu đã gửi), `/api/v1/appointments/**`, `/api/v1/notifications/**`, `/api/v1/kyc/**` (hồ sơ của mình), `/api/v1/media/**`, `/api/v1/transactions/**` | Mọi tài khoản | Luôn lọc theo `CurrentUser.id`; phiên của người khác → 404; giấy tờ KYC cần nhập lại mật khẩu (10 phút) |

## 5. Phát hiện của lượt rà soát và xử lý

| # | Phát hiện | Xử lý |
|---|---|---|
| 1 | Đổi vai trò có hiệu lực ngay (vai trò đọc mỗi request) nhưng phiên cũ vẫn sống: người được nâng lên MODERATOR dùng tiếp phiên 12 giờ, không MFA | `AdminUserService.changeRole` thu hồi mọi phiên của tài khoản; lần sau phải qua cổng nhân viên + MFA (`SessionLifecycleTests.roleChangeTakesEffectAtOnceAndSignsTheAccountOut`) |
| 2 | Phiên nhân viên cùng TTL 12 giờ như thành viên, không idle timeout, không yếu tố thứ hai | TTL 8 giờ + 30 phút idle; MFA TOTP bắt buộc (`AdminMfaTests`) |
| 3 | Không có cách thu hồi phiên từ xa (mất máy), đổi mật khẩu không có trong ứng dụng | `/api/v1/me/sessions`, `/me/password`; admin "đăng xuất mọi nơi" có lý do |
| 4 | `app.security.mfa.required=false` có thể lọt vào production | `AuthPolicy` từ chối khởi động khi `app.mode=production` |
| 5 | Nhân viên (MODERATOR) có quyền ghi CMS/danh mục dự án và đọc hộp lead (đã che số) | Giữ nguyên — đúng mô tả công việc hiện tại của bàn kiểm duyệt; **quyết định sản phẩm**: nếu tách vai trò biên tập nội dung, thêm vai trò mới thay vì mở rộng ADMIN |
| 6 | `/actuator/prometheus` permitAll ở ứng dụng | Không đi qua Nginx (chỉ `/api/`, `/backend-health`); cổng backend không publish ra host. Giữ, ghi chú: không bao giờ publish cổng 8080 |
| 7 | `last_login_at` trong danh sách admin tính từ `auth_sessions` (bị dọn khi hết hạn) | Ghi chú; sự kiện `LOGIN_SUCCEEDED` trong `auth_security_events` là nguồn đúng về sau |

## 6. Khi thêm endpoint mới

1. Thêm quy tắc vào `SecurityConfig` **trước** các quy tắc catch-all; không dựa vào `denyAll` để "chặn tạm".
2. Nếu công khai: thêm vào `AccessMatrixTests.PUBLIC` với lý do trong PR, thêm policy trong `RateLimitPolicies`.
3. Nếu riêng tư: kiểm tra prefix đã có trong `SensitiveResponseCacheFilter` (no-store).
4. Chạy `AccessMatrixTests`.
