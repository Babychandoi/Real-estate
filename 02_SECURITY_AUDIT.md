# Security Audit — Nền tảng Real-estate

> Repository: [Babychandoi/Real-estate](https://github.com/Babychandoi/Real-estate)  
> Commit: [4f52c76](https://github.com/Babychandoi/Real-estate/commit/4f52c76f99d982aae3d7fd37dc9b76d647599b80)  
> Ngày audit: 2026-09-11  
> Phương pháp: static code/config review, dependency audit frontend, đối chiếu OWASP WSTG và Spring Security. Không phải penetration test hoàn chỉnh.

## 1. Kết luận điều hành

**Điểm bảo mật: 1,5/10 — CRITICAL / NO-GO production.**

Không triển khai Internet, không thu CCCD/selfie, không nhận tiền cọc và không cho người dùng thật đăng ký trên phiên bản này. Lý do không nằm ở một lỗi đơn lẻ: authentication, authorization, mã hóa PII và xác minh giao dịch đều đang là mô phỏng.

| Mức | Số phát hiện |
|---|---:|
| Critical | 5 |
| High | 6 |
| Medium | 4 |

## 2. Phát hiện Critical

### SEC-001 — Toàn bộ API nhạy cảm đang `permitAll`

**Bằng chứng:** [SecurityConfig.java](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/backend/src/main/java/com/company/bds/shared/security/SecurityConfig.java#L27-L51) public listings, moderation, leads, reports, analytics, KYC, verification, transaction, catalog và CMS. Không có `@PreAuthorize`, `@RolesAllowed` hoặc `@Secured` trong source.

**Tác động:** người không đăng nhập có thể duyệt lead, phê duyệt/từ chối KYC, duyệt tin, xuất bản CMS, release/refund escrow và xem hợp đồng.

**Khắc phục:** deny-by-default; chỉ public GET tin đã duyệt, public article và submit lead/report có rate limit. Áp dụng policy theo quyền và theo quan hệ tài nguyên: owner, buyer, seller, assigned broker, moderator. Viết integration test cho từng endpoint/role và test IDOR.

### SEC-002 — Authentication/role giả lập ở frontend, mật khẩu plaintext

**Bằng chứng:** [AuthContext.tsx](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/frontend/app/shared/auth/AuthContext.tsx#L42-L176) chứa bốn mật khẩu demo, lưu tài khoản gồm mật khẩu trong `localStorage`, và tin vào role phía client.

**Tác động:** bất kỳ người dùng nào cũng sửa localStorage thành ADMIN; XSS có thể lấy toàn bộ tài khoản/mật khẩu; frontend guard không bảo vệ backend.

**Khắc phục:** xóa toàn bộ authentication giả lập. Xây IAM backend với Argon2id/bcrypt, email/phone verification, MFA cho admin, session HttpOnly Secure SameSite hoặc access token ngắn hạn + refresh rotation. Role/permission và resource ownership chỉ quyết định ở server.

### SEC-003 — “Mã hóa” PII có thể đảo ngược trực tiếp; hash dễ va chạm

**Bằng chứng:** [UserKycProfile](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/backend/src/main/java/com/company/bds/verification/domain/model/UserKycProfile.java#L61-L92) lưu `ENC_ID:` + CCCD và Java `String.hashCode()`; [Lead.java](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/backend/src/main/java/com/company/bds/lead/domain/model/Lead.java#L47-L69) làm tương tự với số điện thoại.

**Tác động:** database leak đồng nghĩa lộ CCCD/số điện thoại; hash 32-bit dễ brute-force và collision; vi phạm mục tiêu bảo vệ PII của chính dự án.

**Khắc phục:** envelope encryption AES-256-GCM với per-record nonce và KMS/HSM; tách key khỏi DB; blind index bằng HMAC-SHA-256 với key riêng và chuẩn hóa input; rotation/version key; audit decrypt; retention/deletion policy; không trả document URL trực tiếp.

### SEC-004 — OTP và escrow có thể bị chiếm quyền

**Bằng chứng:** [DepositTransactionApplicationService](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/backend/src/main/java/com/company/bds/transaction/application/DepositTransactionApplicationService.java#L71-L107) chấp nhận mọi chuỗi dài 6; [DepositContractController](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/backend/src/main/java/com/company/bds/transaction/api/DepositContractController.java#L45-L85) cho release/refund public và nhận `operatorId` từ request.

**Tác động:** kẻ tấn công biết/đoán contract UUID có thể ký thay hai bên, release hoặc refund giao dịch.

**Khắc phục:** OTP sinh bằng CSPRNG, lưu hash, TTL 2–5 phút, one-time consume atomic, giới hạn lần thử/gửi, binding theo user + contract + action, step-up authentication và idempotency key. Release/refund cần maker-checker hoặc webhook có chữ ký từ payment provider; operator lấy từ principal server-side.

### SEC-005 — Public KYC/contract gây IDOR và lộ dữ liệu cực nhạy cảm

**Bằng chứng:** [KycController](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/backend/src/main/java/com/company/bds/verification/api/KycController.java#L31-L84) cho submit/query/queue/approve/reject không auth. [UserKycResponse](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/backend/src/main/java/com/company/bds/verification/api/response/UserKycResponse.java#L9-L40) trả họ tên, ngày sinh, địa chỉ, ảnh CCCD và selfie. Contract trả tên/số điện thoại buyer/seller.

**Tác động:** lộ danh tính và giấy tờ, chiếm đoạt tài khoản, lừa đảo, doxxing.

**Khắc phục:** kiểm tra quyền đối tượng mọi request; response tối thiểu hóa; signed URL ngắn hạn sau authorization; object storage private; watermark; audit access; redaction theo role; rate limit và anomaly detection.

## 3. Phát hiện High

| ID | Phát hiện | Bằng chứng/tác động | Giải pháp |
|---|---|---|---|
| SEC-006 | API quản trị public | Moderation, reports, analytics, catalog write, CMS approve/reject đều public | Tách `/admin`, RBAC/ABAC, MFA, network policy, audit append-only |
| SEC-007 | CSRF bị tắt | [SecurityConfig](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/backend/src/main/java/com/company/bds/shared/security/SecurityConfig.java#L24-L27). Nguy hiểm khi chuyển sang cookie/session | Bật CSRF cho SPA hoặc dùng token kiến trúc đúng; xem [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) |
| SEC-008 | Không rate limit/brute-force protection | Redis được khai báo nhưng không có code sử dụng | Rate limit phân tán cho login, OTP, lead, report, search; device/IP/account quotas |
| SEC-009 | Database/Redis public và secret mặc định | [docker-compose.yml](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/docker-compose.yml) map 5432/6379, password `change-me`, Redis không auth/TLS | Không map port production; secret manager; Redis ACL/TLS; fail-fast nếu default secret |
| SEC-010 | Không kiểm soát media/document URL | User gửi URL bất kỳ; KYC lưu URL tài liệu | Upload trực tiếp private bucket, MIME/magic-byte scan, AV scan, size limit, host allowlist, signed URL |
| SEC-011 | Thiếu audit trail thực thi | Có bảng outbox nhưng không có publisher/worker; module audit chỉ có package-info | Audit append-only, tamper-evident, actor/action/resource/result/IP/device, SIEM alert |

## 4. Phát hiện Medium

| ID | Phát hiện | Hướng xử lý |
|---|---|---|
| SEC-012 | Swagger public | Tắt ở production hoặc giới hạn admin/VPN |
| SEC-013 | Thiếu security headers ở Nginx | CSP, HSTS, frame-ancestors, nosniff, Referrer-Policy, Permissions-Policy; TLS tại edge |
| SEC-014 | CORS hardcode localhost và allow credentials | Cấu hình theo environment, exact origins; không dùng wildcard; test preflight |
| SEC-015 | Dependency frontend có 2 cảnh báo Moderate | `npm audit --omit=dev` báo React Router 6.30.6 liên quan [GHSA-wrjc-x8rr-h8h6](https://github.com/advisories/GHSA-wrjc-x8rr-h8h6) và [GHSA-337j-9hxr-rhxg](https://github.com/advisories/GHSA-337j-9hxr-rhxg). Nâng cấp có kiểm thử tương thích; thêm Dependabot/SCA gate |

## 5. Điểm tốt

- DTO có Bean Validation và một phần message i18n.
- Error response theo Problem Details, không trả stacktrace trực tiếp.
- Backend container chạy non-root; JPA `open-in-view=false`; Flyway quản lý schema.
- CORS không dùng wildcard và giới hạn localhost ở cấu hình hiện tại.
- Một số response đã mask CCCD/điện thoại; domain đã nhận diện nhu cầu bảo vệ PII.

Các điểm này là nền tốt nhưng chưa bù được thiếu authentication/authorization và crypto giả lập.

## 6. Kế hoạch khắc phục

### P0 — đóng bề mặt nguy hiểm

- Chặn public deployment; banner demo; cấm dữ liệu thật.
- Deny-by-default; auth backend; resource authorization; xóa client-side credentials.
- Tắt transaction/KYC/verification/CMS write nếu chưa hoàn thiện.
- Đổi secret; không public Postgres/Redis/Swagger.
- Sửa PII encryption/HMAC; rotate dữ liệu thử nghiệm nếu từng chứa dữ liệu thật.

### P1 — security baseline

- MFA admin, CSRF/session/token strategy, rate limit, idempotency, signed URLs.
- Security headers, TLS, secret manager, immutable audit log.
- SAST, SCA, secret scanning, container scanning, SBOM và CI security gates.
- Test theo [OWASP WSTG](https://owasp.org/www-project-web-security-testing-guide/): authentication, authorization, session, input validation, business logic và API.

### P2 — trước giao dịch tiền thật

- Threat model cho escrow/eKYC; kiểm tra nhà cung cấp và webhook signatures.
- Pen-test độc lập; remediation verification.
- Incident response, breach notification, key compromise drill, backup restore drill.
- Fraud/risk engine, maker-checker, reconciliation tài chính và ledger kép.

## 7. Security acceptance gates

- 0 Critical/High mở; Medium có owner và deadline.
- 100% endpoint mutation có authentication, authorization và audit.
- Test IDOR cho mọi tài nguyên theo user/role/relationship.
- OTP không thể reuse, brute-force hoặc dùng cho action khác.
- Không PII plaintext/pseudo-encryption trong DB, log, cache, URL hoặc response.
- Secret scanning sạch; dependency/container scan không có Critical/High.
- Pen-test và restore/IR drill pass trước production.

## 8. Phán quyết

**NO-GO.** Phiên bản này chỉ phù hợp làm demo nội bộ với dữ liệu giả. Không có biện pháp cấu hình đơn lẻ nào biến nó thành an toàn; cần hoàn thiện identity, authorization, crypto và transaction trust boundary ở cấp kiến trúc.
