# ADR 0001 — Mô hình phiên đăng nhập

## Trạng thái
Accepted — 28/09/2026 (audit 27/09/2026, yêu cầu F20.1). Các phần "pha B" đã được luồng S5-SEC pha B triển khai (nhánh `audit/s5b-sec`, báo cáo `docs/audit-2026-09-27/streams/s5b-sec.md`); kế hoạch kiểm thử bên dưới ánh xạ sang các lớp test ở đó.

## Bối cảnh

### Hiện trạng trong mã (đã đối chiếu tại `audit-2026-09-27`)
| Khía cạnh | Thực tế | Nơi xác nhận |
|---|---|---|
| Phát phiên | Đăng nhập (`POST /api/v1/auth/login`, `/api/v1/auth/admin/login`) sinh token opaque 32 byte từ `SecureRandom`, mã hóa Base64url | `AuthService.issueSession` |
| Lưu phía server | Chỉ lưu SHA-256 của token trong `auth_sessions(token_hash, user_id, expires_at, revoked_at)`; token gốc không nằm trong DB | `V008__security_baseline.sql`, `AuthService` |
| Thời hạn | 12 giờ tuyệt đối cho mọi vai trò, kể cả ADMIN/MODERATOR; không có idle timeout | `AuthService.SESSION_TTL` |
| Gửi kèm request | Header `Authorization: Bearer <token>`; `BearerTokenFilter` tra `auth_sessions` (chưa thu hồi, chưa hết hạn, user `ACTIVE`) và đọc vai trò từ DB ở mọi request | `BearerTokenFilter`, `AuthService.findByToken` |
| Spring Security | `SessionCreationPolicy.STATELESS`, CSRF tắt, CORS allowlist với `allowCredentials=false`, không cookie | `SecurityConfig` |
| Lưu phía trình duyệt | `sessionStorage['bds_access_token']` — mất khi đóng tab, không chia sẻ giữa tab | `frontend/app/shared/api/client.ts` |
| SSE | `fetch` + header Authorization (không dùng `EventSource`), nên không cần cookie | `AuthContext.tsx` |
| Thu hồi | Logout đặt `revoked_at`; đặt lại mật khẩu thu hồi mọi phiên; xác minh email xóa phiên; phiên hết hạn/đã thu hồi được dọn khi đăng nhập | `AuthService.logout/resetPassword/verifyEmail` |
| Khóa tài khoản | User không còn `ACTIVE` bị từ chối ngay ở request kế tiếp (điều kiện trong `findByToken`) | `AuthService.findByToken` |

Tài liệu baseline lại mô tả **Spring Session JDBC + cookie HttpOnly/Secure/SameSite + CSRF**
(`PROJECT_CODE_RULES_BDS.md` §2 và §12, `Ke_hoach_du_an_website_BDS_Waterfall.md`). Hai nguồn lệch nhau; audit F20 yêu
cầu chọn một mô hình và ghi lại lý do. Các tài liệu đó đã được sửa cùng ADR này để khớp thực tế.

### Mô hình đe dọa
| Mối đe dọa | Với mô hình hiện tại | Kiểm soát |
|---|---|---|
| XSS đọc token | **Có thể**: JavaScript đọc được `sessionStorage`; token bị đánh cắp dùng được từ máy khác đến khi hết hạn hoặc bị thu hồi | CSP chặt (không inline script, `object-src 'none'`, `base-uri`/`form-action 'self'`, `frame-ancestors 'none'`), React escape mặc định, không `dangerouslySetInnerHTML`, TTL, thu hồi, danh sách phiên (pha B) |
| XSS thao tác thay người dùng trong trang | Có thể — **đúng với cả mô hình cookie HttpOnly** | Như trên; cookie không giải quyết được điểm này |
| CSRF | **Không áp dụng**: trình duyệt không tự gửi header Authorization, không có cookie phiên | Nếu chuyển sang cookie thì bắt buộc bật CSRF (xem phương án A) |
| Lộ token qua URL, log, Referer | Token chỉ nằm trong header; access log Nginx không ghi header và (từ pha A) không ghi query string | Cấm đưa token vào URL/log (quy tắc S9) |
| Lộ bảng `auth_sessions` | Chỉ có SHA-256 của giá trị ngẫu nhiên 256 bit, không đảo ngược được | — |
| Chiếm phiên quản trị | Cùng TTL 12 giờ như người dùng, chưa có MFA | TTL ngắn + idle timeout + MFA TOTP (pha B) |
| Dò mật khẩu | Giới hạn theo IP và theo email mục tiêu (rate limiter v2, pha A) | `RateLimitPolicies` |
| Thay đổi quyền không có hiệu lực | Vai trò đọc từ DB mỗi request nên đổi vai trò/khóa tài khoản có hiệu lực ngay | — |
| Máy dùng chung | `sessionStorage` mất khi đóng tab | — |

## Quyết định
Giữ **bearer token opaque lưu phía server** (không JWT, không chuyển sang cookie ở giai đoạn này) và bổ sung:

1. **CSP chặt là kiểm soát bảo mật bắt buộc** (đã làm ở pha A, `frontend/nginx.conf` + `scripts/verify-headers.sh`):
   chỉ `script-src 'self'` và beacon Cloudflare, không Google Fonts, `object-src 'none'`, `frame-src 'none'`,
   `frame-ancestors 'none'`, API trả `default-src 'none'`. Mọi script bên thứ ba mới phải qua review CSP. Mục tiêu tiếp
   theo: bỏ `'unsafe-inline'` khỏi `style-src` khi UI kit không còn cần.
2. **TTL phiên staff ngắn hơn** (pha B): ADMIN/MODERATOR 8 giờ tuyệt đối + 30 phút không hoạt động; người dùng giữ
   12 giờ. Cần lưu thời điểm hoạt động cuối (migration V087 của pha B).
3. **MFA TOTP + recovery code dùng một lần cho ADMIN/MODERATOR** (pha B, UI-17).
4. **Danh sách phiên và thu hồi** (pha B): xem các phiên đang mở (thời điểm tạo, lần dùng cuối, thiết bị rút gọn),
   thu hồi từng phiên, "đăng xuất mọi nơi"; đổi vai trò hoặc đổi mật khẩu thu hồi mọi phiên của tài khoản đó.
5. Không lưu token trong `localStorage`, không đưa token vào URL/query, không log token hay header Authorization.

## Phương án đã cân nhắc
| Phương án | Lợi ích | Chi phí / rủi ro | Kết luận |
|---|---|---|---|
| A. Cookie phiên `HttpOnly; Secure; SameSite=Lax` + CSRF token (Spring Security `CsrfTokenRepository`) | JavaScript không đọc được token → XSS không mang token đi nơi khác | XSS vẫn thao tác được trong trang; phải bật CSRF cho mọi request ghi, đổi `apiClient` sang `credentials: 'include'`, cấu hình CORS có credential, quy tắc CDN không bao giờ cache response có `Set-Cookie`, SSE qua cookie; thay đổi mọi client hiện có | Chưa chọn. Xem lại khi: phát hiện XSS thật, CSP buộc phải nới cho script bên thứ ba, hoặc cần phiên dài ("ghi nhớ đăng nhập") |
| B. BFF (gateway giữ token, trình duyệt chỉ có cookie HttpOnly) | Như A, tách token khỏi trình duyệt | Thêm một thành phần vận hành cho một SPA cùng origin với một backend; bản chất vẫn là A | Không chọn |
| C. JWT access token ngắn + refresh token | Kiểm tra chữ ký không cần DB | Thu hồi cần denylist; bị đánh cắp vẫn dùng được đến khi hết hạn; thêm độ phức tạp xoay khóa ký | Không chọn |
| D. Giữ nguyên, không bổ sung | Không tốn công | Phiên quản trị dài, không MFA | Không chọn |

## Hệ quả
- **Mã nguồn:** pha A chỉ thay CSP/header và rate limit. Pha B thêm bảng/cột cho MFA và metadata phiên (V087–V089; dải V066–V067 ban đầu không dùng được vì V085 đã tồn tại và `out-of-order` tắt),
  sửa `AuthService`/`BearerTokenFilter`, trang quản lý phiên và bước MFA ở admin login.
- **Tài liệu:** `PROJECT_CODE_RULES_BDS.md` (§2, §12) và `Ke_hoach_du_an_website_BDS_Waterfall.md` được sửa để mô tả
  mô hình thật và trỏ về ADR này.
- **Bảo mật:** XSS là rủi ro chính → CSP và việc cấm script inline/bên thứ ba chưa review là điều kiện phát hành.
  CSRF không áp dụng cho tới khi có cookie xác thực.
- **Vận hành:** chuyển sang cookie về sau cần đồng thời: CSRF, cờ cookie, CORS có credential, quy tắc cache CDN.
- **Rollback:** không đổi cơ chế nên không cần rollback; các bước pha B phải có cờ bật/tắt MFA theo môi trường.

## Kế hoạch kiểm thử (pha B triển khai)
| Hành vi | Kiểm thử |
|---|---|
| Logout thu hồi token | Đã có: `SecurityIntegrationTests.authenticationAuthorizationAndRevocation` |
| Token hết hạn → 401 | Tích hợp: đặt `expires_at` về quá khứ, gọi `/api/v1/auth/me` |
| Đặt lại mật khẩu thu hồi mọi phiên | Tích hợp: hai token cùng user, reset, cả hai 401 |
| Khóa tài khoản có hiệu lực ngay | Tích hợp: đổi `status`, request kế tiếp 401 |
| Đổi vai trò có hiệu lực ngay và thu hồi phiên | Tích hợp |
| TTL/idle của staff ngắn hơn người dùng | Tích hợp với `Clock` giả lập |
| MFA bắt buộc cho staff; mã sai bị giới hạn; recovery code dùng một lần | Tích hợp + rate limit |
| Thu hồi một phiên chỉ vô hiệu phiên đó | Tích hợp |
| Header/CSP trên response cuối | `scripts/verify-headers.sh <domain>` sau mỗi lần deploy edge (pha A) |

## Liên kết
- Audit `Real-estate_Audit_2026-09-27.md` F20; ma trận `docs/audit-2026-09-27/01_REQUIREMENTS.md` F20.1–F20.4, UI-17.
- Mã: `backend/src/main/java/com/company/bds/iam/application/AuthService.java`,
  `backend/src/main/java/com/company/bds/shared/security/{BearerTokenFilter,SecurityConfig}.java`,
  `frontend/app/shared/api/client.ts`, `frontend/app/shared/auth/AuthContext.tsx`, `frontend/nginx.conf`.
- Báo cáo luồng: `docs/audit-2026-09-27/streams/s5-sec-a.md`.
