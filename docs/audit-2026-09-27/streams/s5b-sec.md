# S5-SEC phase B — báo cáo luồng

Nhánh: `audit/s5b-sec`, tạo từ `audit-2026-09-27` tại `58ea814` (W1–W3 + UI redesign). Brief:
`docs/audit-2026-09-27/briefs/s5b-sec.md`. Flyway **V087–V089** (dải V066–V067 của hợp đồng không dùng được: V085 đã
tồn tại và `out-of-order` tắt). Cổng: backend test 18120, Vite 5320 (không chạy dev server — chỉ Vitest/build).

## Danh sách commit

```
1acc606 docs(audit): brief for S5-SEC phase B
4d8e6e8 feat(security): staff TOTP MFA, session management and single-use token states
7587842 feat(security): least-privilege access matrix, rate-limit and alert follow-ups
d15dd7a test(security): the token-flood test spies on the session lookup used by the filter
93e71e8 feat(auth-ui): token pages tell expired, used, replaced and invalid links apart
60432d9 feat(security-ui): staff MFA login, account security and admin session controls
35bf6c9 fix(security): self-review hardening of MFA and staff sessions
```

Ghi chú: `93e71e8` import `securityApi.ts` được thêm ở `60432d9` — hai commit frontend chỉ build được khi đi cùng nhau
(không bisect riêng lẻ được); backend mỗi commit build và test được.

## Yêu cầu → bằng chứng

| ID | Yêu cầu | Trạng thái | Bằng chứng |
|---|---|---|---|
| F20.2 | Test logout/revoke/expiry | DONE | `SessionLifecycleTests` (10): logout chỉ thu hồi token đó; hết hạn tuyệt đối → 401; khóa tài khoản → 401 ngay; staff 8 h + idle 30 phút (29 phút còn sống và ghi hoạt động, 31 phút → 401), thành viên 12 h không idle; `last_seen_at` ghi tối đa 1 lần/phút; stream thông báo không làm "sống lại" phiên staff; danh sách phiên (thiết bị/dải mạng, không IP đầy đủ, `no-store`), thu hồi một phiên, phiên người khác → 404, "đăng xuất thiết bị khác"; đổi mật khẩu cần mật khẩu cũ, giữ phiên hiện tại, thu hồi phiên khác, gửi mail thông báo (outbox); reset mật khẩu thu hồi mọi phiên; đổi vai trò thu hồi phiên và buộc qua MFA; admin "đăng xuất mọi nơi" có lý do + lịch sử; xác minh email bỏ phiên tạo trước đó |
| F20.3 | MFA admin (TOTP + recovery codes) | DONE | `Totp` (RFC 6238, SHA-1/6 số/30 s, ±1 bước, chặn replay qua `last_used_step`); `TotpAndClientContextTests` (6) gồm vector RFC 6238 phụ lục B; `AdminMfaTests` (7): staff chưa có authenticator phải ENROLL, challenge không phải bearer, secret lưu sealed (AES-GCM gắn mục đích), mã sai → 400 còn N lần, 10 mã khôi phục dạng `XXXXX-XXXXX`; đã enroll → VERIFY, mã đã dùng không dùng lại; mã khôi phục dùng một lần, tạo lại cần mã TOTP mới, mã cũ ngừng; 5 mã sai hủy challenge, challenge hết hạn/cũ bị thay; trần 10 mã sai/giờ/tài khoản (429); mã khôi phục lưu HMAC có khóa (không phải SHA-256 trần); admin đặt lại MFA của người khác (có lý do, không tự đặt, MODERATOR bị 403) → phiên người đó chấm dứt, lần sau ENROLL; cổng thành viên/cổng staff vẫn từ chối chéo; `AuthPolicy` từ chối `mfa.required=false` khi `app.mode=production` |
| F20.4 | Least privilege | DONE | `AccessMatrixTests` (4) quét **mọi** route `/api/**` từ `RequestMappingHandlerMapping` (>150): ẩn danh 401 trừ allowlist công khai tường minh; USER 403 ở mọi route staff/admin; MODERATOR 403 ở mọi route chỉ-ADMIN (>20); allowlist không được chứa route không tồn tại. Kiểm tra đột biến: bỏ `GET /api/v1/billing/plans` khỏi allowlist → test báo đúng route. Ma trận + phát hiện: `docs/security/ACCESS_MATRIX.md` |
| UI-14 | `/verify-email`, `/forgot-password`, `/reset-password` | DONE | Backend: mã máy `TOKEN_INVALID` (400) / `TOKEN_EXPIRED` (410) / `TOKEN_USED` (409) / `TOKEN_SUPERSEDED` (409), `POST /auth/verify-email` (GET giữ cho mail cũ), `POST /auth/password-reset/status` (không tiêu thụ link), link cũ bị "thay thế" khi gửi lại; `AuthTokenPagesTests` (5). Frontend: token bị xóa khỏi thanh địa chỉ ngay khi đọc, một request/lần thử kể cả StrictMode, trạng thái riêng cho hết hạn/đã dùng/đã thay/không hợp lệ/mất mạng, form gửi lại trung tính + cooldown 60 s + tôn trọng `Retry-After` 429; reset kiểm tra link trước khi hiện form; Vitest `accountSecurity.test.tsx` (6 test trang token) |
| UI-17 | Admin login: MFA, kiểm soát phiên, audit | DONE | Cổng staff 2 bước (mật khẩu → mã/mã khôi phục; lần đầu: khóa thiết lập + liên kết `otpauth://` + xác nhận mã + hiện mã khôi phục một lần, phải tick "đã lưu" mới vào), hết hạn challenge tự quay lại bước mật khẩu; trang admin "Bảo mật tài khoản" (trạng thái MFA, tạo lại mã, thiết bị, đổi mật khẩu, nhật ký); Quản lý người dùng: badge MFA cho staff, "Đăng xuất mọi nơi", "Đặt lại MFA" có lý do; `auth_security_events` + `GET /me/security-events`; metric `bds.auth.events{type}`; alert `BdsMfaChallengeLocked`/`BdsMfaFailuresSpike`; Vitest 3 test luồng MFA |
| DS-11 (phần S5) | Quay về mục đích ban đầu sau xác minh | DONE | Đăng ký gửi `returnTo` (trang hiện tại); server chỉ giữ đường dẫn tương đối cùng site (không `//`, `\`, scheme, ký tự điều khiển, không cổng admin/trang token; `safeReturnPath` + CHECK trong V089); gửi lại link giữ `returnTo`; sau xác minh, đăng nhập ngay trên trang → điều hướng về trang cũ. Test: `TotpAndClientContextTests.returnPathsAreSameSiteRelativeOnly`, `AuthTokenPagesTests.registrationStoresOnlyASafeReturnPath`, Vitest verify-email |
| F05.4 (alert) | Cảnh báo lag/DLQ chỉ mục | DONE (phần S5) | `BdsSearchIndexLagHigh` (>30 s/5 phút), `BdsSearchBulkFailures`, `BdsSearchBreakerOpen`; DLQ đã có `BdsJobDeadLetters` (mọi queue) |
| D-14 (follow-up S0/S2/S6) | Alert rule được yêu cầu | DONE | Thêm `BdsEngageAlertLagHigh` (>120 s), `BdsNotificationFanoutFallback`; email dùng rule chung `BdsJobQueueLagHigh`/`BdsJobDeadLetters`. `promtool check rules`: **33 rules**; `promtool test rules`: **SUCCESS** (5 nhóm test mới). Runbook: `docs/operations/ALERT_RUNBOOK.md` |
| Rate limit follow-up (S0, S1, mới) | Policy cho endpoint mới | DONE | `/api/v1/events` đã có từ trước (`analytics-events`); thêm `auth-verify-email-post`, `auth-reset-status`, `auth-mfa-verify`, `auth-mfa-enroll` (IP, FAIL_CLOSED), `account-password`, `account-mfa-codes` (IP + ACCOUNT, FAIL_CLOSED), `media-signed-urls`, `media-signed-get` (S1). `AuthRateLimitPolicyTests` (3) |

## Kết quả kiểm thử

| Việc | Lệnh | Kết quả |
|---|---|---|
| Backend toàn bộ | `sh mvnw -B -ntp verify` (JDK 17, `bds-test`) | 367 test: 351 pass, 16 lỗi chỉ vì ES/MinIO chưa chạy trong `bds-test` lúc đó (`SearchElasticsearchEngineTests` 8, `SearchIndexLagTests` 1, `MediaPipelineIntegrationTests` 7); khởi động hai dịch vụ và chạy lại 3 lớp đó: **16/16 pass**. Tổng: 367/367 |
| Backend lớp mới/sửa | `-Dtest=com.company.bds.iam.*Tests,RequestRateLimitFilterTests` | 48/48 pass |
| Frontend lint | `npm run lint` | 0 lỗi, 0 cảnh báo |
| Frontend typecheck | `npx tsc -b` | 0 lỗi |
| Frontend unit | `npx vitest run --testTimeout=30000` | 25 file, **188/188** pass (10 test mới) |
| Frontend build | `npm run build` | thành công |
| Budget | `npm run check:bundle` | mọi route ok (`/account` 131.3/132 kB sau khi lazy-load mục bảo mật; `admin/login` 130.3/136; `admin/security` 131.5/145) |
| Prettier | `npx prettier --check` trên file đã sửa | sạch (`_public.listings.new.tsx` lệch từ trước, không sửa) |
| Alert rules | `promtool check rules` + `promtool test rules` (image `prom/prometheus:v3.5.0`) | 33 rules, SUCCESS |
| E2E Playwright | — | **Không chạy** (xem khoảng trống) |

## Sai lệch so với hợp đồng / brief

- Flyway V087–V089 thay cho V066–V067 (lý do ở đầu báo cáo).
- Phản hồi `POST /api/v1/auth/admin/login` đổi hình dạng: khi cần MFA trả `{mfaRequired: true, mfaState, challengeToken,
  challengeExpiresAt}` và **không** có `accessToken`. Khi không cần MFA (chỉ môi trường tắt `mfa.required` và tài khoản
  chưa enroll) vẫn trả `accessToken/expiresAt/user` như cũ. Client cũ không hiểu MFA sẽ không đăng nhập được staff —
  có chủ đích.
- `AuthResult` thêm trường `idleExpiresAt` (tương thích ngược). Danh sách admin người dùng thêm `mfaEnrolled`.
- Token trang xác minh/reset: lỗi token giờ có `code` và status 410/409 thay vì luôn 400 (`TOKEN_INVALID` vẫn 400).
- Không có mã QR cho enroll: chỉ khóa thiết lập (chia nhóm 4 ký tự) + liên kết `otpauth://`. Thêm thư viện QR sẽ tăng
  bundle cổng admin; mọi ứng dụng xác thực phổ biến đều nhận khóa nhập tay.
- Brief ghi `POST /auth/token-status`; triển khai là `POST /api/v1/auth/password-reset/status` (chỉ link reset cần kiểm
  tra trước form; xác minh email tự tiêu thụ ngay).

## Khoảng trống (thành thật)

1. **E2E Playwright chưa chạy** trên nhánh này (máy dùng chung, brief yêu cầu hạn chế). Stack E2E (`scripts/e2e-local.sh`,
   job `e2e` trong CI) đặt `APP_SECURITY_MFA_REQUIRED=false` vì tài khoản demo staff không có authenticator — spec
   `admin.spec.ts` vẫn nhận session trực tiếp. Luồng MFA vì thế chỉ được chứng minh bằng test tích hợp backend + Vitest,
   chưa có E2E trình duyệt thật. Nên thêm spec E2E enroll → verify (tính TOTP trong Node) ở S11.
2. **Luồng SSE đang mở không bị cắt khi phiên bị thu hồi**: `/api/v1/notifications/stream` chỉ kiểm tra phiên lúc kết nối;
   stream đã mở tiếp tục nhận thông báo của chính tài khoản đó cho đến lần kết nối lại (tối đa theo timeout 1 h của
   Nginx). Mọi request API khác bị từ chối ngay. Rủi ro thấp (chỉ đọc, dữ liệu của chính người đó) nhưng nên đóng stream
   khi thu hồi (S6/S9).
3. `auth_security_events` chưa có retention/purge (tăng theo số lần đăng nhập). Đề xuất S8 thêm job giữ 12 tháng
   (`ScheduledTaskLock`) — cần index theo `created_at` nếu xóa theo thời gian.
4. `LOGIN_FAILED` chỉ ghi cho email tồn tại (bằng transaction riêng): chênh thời gian cỡ một INSERT so với email không tồn
   tại, nhỏ so với jitter BCrypt (cost 12) — chưa đo.
5. Đăng ký với email đã tồn tại vẫn trả "Email đã được sử dụng" (liệt kê tài khoản) — ngoài phạm vi UI-14, không sửa.
6. `last_login_at` trong danh sách admin vẫn tính từ `auth_sessions` (bị dọn khi hết hạn) — ghi trong ma trận quyền.
7. Kiểm tra đột biến chỉ làm thủ công một lần cho `AccessMatrixTests`; không có trong CI.
8. F11.2 (header qua domain thật) và các mục EXTERNAL của pha A không đổi.

## Ghi chú production

### Biến môi trường mới

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `APP_SECURITY_MFA_REQUIRED` | `true` | **Giữ `true` ở production.** Backend từ chối khởi động nếu `false` khi `APP_MODE=production`. Chỉ stack E2E/demo dùng một lần đặt `false`. Đã truyền qua `docker-compose.yml` |
| `APP_SECURITY_MFA_ISSUER` | `Nhà Đất Chuẩn` | Tên hiện trong ứng dụng xác thực |
| `APP_SECURITY_SESSION_STAFF_TTL` | `PT8H` | Thời hạn tuyệt đối phiên ADMIN/MODERATOR |
| `APP_SECURITY_SESSION_STAFF_IDLE_TIMEOUT` | `PT30M` | Tự đăng xuất staff sau thời gian không hoạt động |
| `APP_SECURITY_SESSION_USER_TTL` | `PT12H` | Phiên thành viên (không đổi) |

Khóa bí mật MFA dùng `app.security.pii-encryption-key` (seal AES-GCM, purpose `mfa-totp-secret`), mã khôi phục dùng
`app.security.pii-index-key` (HMAC) — **không xoay hai khóa này** mà không có kế hoạch: xoay khóa mã hóa làm mọi
authenticator đã enroll không giải mã được (phải đặt lại MFA cho mọi staff), xoay khóa index làm mọi mã khôi phục vô hiệu
(staff tạo lại từ trang Bảo mật).

### Triển khai MFA cho admin hiện có

1. Trước deploy: báo cho mọi ADMIN/MODERATOR chuẩn bị ứng dụng xác thực trên điện thoại.
2. Migration V088 **thu hồi mọi phiên staff đang mở** (`revoked_reason='MFA_ROLLOUT'`) — ngay sau deploy mọi staff bị đăng
   xuất; lần đăng nhập đầu qua cổng quản trị sẽ yêu cầu thiết lập (không có session trước khi xác nhận mã).
3. **Cửa sổ rủi ro:** ai biết mật khẩu staff trước khi chính chủ kịp thiết lập có thể tự enroll authenticator của mình.
   Giảm thiểu: deploy vào giờ có mặt đội vận hành, yêu cầu mọi staff đăng nhập và thiết lập ngay; sau đó kiểm tra
   `SELECT u.email, m.confirmed_at FROM users u JOIN user_roles r ON r.user_id=u.id AND r.role IN ('ADMIN','MODERATOR') LEFT JOIN user_mfa m ON m.user_id=u.id;`
   — tài khoản enroll vào thời điểm bất thường → admin khác "Đặt lại MFA" + chủ tài khoản đổi mật khẩu.
4. Mất điện thoại: một ADMIN khác → Quản lý người dùng → "Đặt lại MFA" (bắt buộc lý do, ghi lịch sử). Nếu **admin cuối
   cùng** mất cả điện thoại và mã khôi phục: xóa hàng `user_mfa`/`user_mfa_recovery_codes` của tài khoản đó bằng SQL trên DB
   production (người có quyền DB), ghi biên bản — không có đường vòng qua ứng dụng.
5. Theo dõi alert `BdsMfaChallengeLocked`/`BdsMfaFailuresSpike` (cần receiver Alertmanager — EXTERNAL, xem pha A).

### Migration

- V087: thêm cột vào `auth_sessions` (nhanh, không rewrite — cột nullable + backfill `last_seen_at = created_at` trên
  bảng nhỏ), cột `users.password_changed_at`.
- V088: bảng MFA/challenge/sự kiện; mở rộng CHECK `user_admin_actions.action`; thu hồi phiên staff.
- V089: `return_path`, `superseded_at` cho token xác minh/reset. Link reset cũ trong mail đang lưu hành vẫn dùng được
  đến khi hết hạn (không bị đánh dấu thay thế hồi tố).
- Rollback ứng dụng về bản trước V087 vẫn chạy được với schema mới (chỉ thêm cột/bảng; bản cũ tạo session không điền
  cột mới — `last_seen_at` có default).

## Việc luồng khác cần biết

- **S6/S9:** đóng SSE stream khi phiên bị thu hồi (khoảng trống 2); giữ `/api/v1/notifications/stream` là đường dẫn duy
  nhất `BearerTokenFilter` coi là "không phải hoạt động" — nếu đổi path, sửa hằng số ở đó.
- **S8:** retention cho `auth_security_events`; `bds_auth_events_total{type}` có sẵn cho dashboard bảo mật.
- **S9:** thêm `ApiException(HttpStatus.GONE/TOO_MANY_REQUESTS, …)` mới vào chuẩn Problem Details; OpenAPI snapshot sẽ
  gồm `/auth/admin/mfa/*`, `/me/sessions*`, `/me/password`, `/me/mfa*`, `/me/security-events`, `/admin/users/{id}/mfa/reset`,
  `/admin/users/{id}/sessions/revoke`. Endpoint mới cần qua `AccessMatrixTests` (allowlist công khai) — xem
  `docs/security/ACCESS_MATRIX.md` §6.
- **S11:** E2E trình duyệt cho enroll/verify MFA và các trang token; baseline visual cho `/2026/nhadatchuan/admin/security`.
- `IMPLEMENTATION_PLANS_HISTORY.md` / `WALKTHROUGHS_HISTORY.md` / `01_REQUIREMENTS.md`: để orchestrator tổng hợp (03_AGENT_RULES).
