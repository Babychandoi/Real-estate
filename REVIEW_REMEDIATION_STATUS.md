# Trạng thái khắc phục 5 báo cáo rà soát

Ngày đối soát: 2026-09-12. Trạng thái `GATED` nghĩa là không giả lập thành công và bị khóa mặc định cho tới khi có nhà cung cấp, secret và nghiệm thu thật.

| Nhóm | Kết quả |
|---|---|
| Usability & setup | `CLOSED` — banner demo, tài khoản demo phía server, tìm kiếm bằng URL, mobile nav, protected routes, lead đúng contract và chỉ báo thành công khi có `leadId`. |
| Authentication/RBAC/IDOR | `CLOSED` — USER/BROKER self-register, BCrypt, opaque session token có thu hồi/hết hạn, deny-by-default, role gate và ownership/participant checks. |
| PII & audit | `CLOSED` cho nền tảng — AES-GCM + HMAC blind index, không trả URL tài liệu KYC, audit mutation có hash chain, production fail-fast khi thiếu key/secret. Quản lý khóa tập trung là yêu cầu hạ tầng production. |
| eKYC/escrow/payment | `GATED` — tắt mặc định, API trả lỗi capability rõ ràng; UI không còn OTP/thanh toán giả. Chỉ bật sau tích hợp sandbox, webhook ký, idempotency và nghiệm thu pháp lý. |
| Dữ liệu demo/mock | `CLOSED` — không còn local account/password hoặc false-success fallback ở runtime; nội dung minh họa được gắn nhãn. |
| Accessibility/UI | `CLOSED` ở mức source/build — skip link, focus-visible, dialog semantics, focus trap, Escape/restore focus, live status, touch target, mobile menu và route lazy loading. Chưa có phiên in-app browser để nghiệm thu ảnh thật. |
| Packaging/supply chain | `CLOSED` — lockfile + npm ci, Docker multi-stage chạy test, non-root runtime, health check, CI, Trivy, Dependabot và tài liệu hỗ trợ/bảo mật. |
| Deployment/scale | `CLOSED` baseline — secret bắt buộc, không public DB/Redis, probes, graceful shutdown, Prometheus, gzip/cache, k6 thresholds và HPA/PDB. Benchmark, HA database và restore thật phải chạy trên hạ tầng đích. |

## Điều kiện bật production

Không đặt `APP_MODE=production` cho tới khi có secret manager, TLS ingress, khóa PII ngẫu nhiên 32 byte, database/Redis managed hoặc HA, object storage/CDN có allowlist, backup restore drill, quan sát SLO và nhà cung cấp eKYC/payment đã nghiệm thu. Cấu hình trong repo là baseline có thể kiểm thử, không phải bằng chứng production đã đạt các điều kiện đó.

## Đối soát bổ sung Đợt 12

- `CLOSED`: MFA TOTP cho tài khoản đặc quyền ở production; IDOR lead/eKYC/CMS/verification/transaction; idempotency public lead; pagination có trần 100; outbox claim/lease/fenced-finalize; preflight, smoke test và reset demo có chốt an toàn.
- `CLOSED baseline`: Docker/Flyway đã chạy thật trên PostgreSQL 16 với đủ V001-V011; readiness backend, frontend health và public search đã vượt smoke test qua gateway.
- `CLOSED` cho lưu ảnh nội bộ: API upload xác thực lưu object vào bucket MinIO riêng tư, kiểm tra magic bytes/10 MB, giới hạn 20 ảnh, kiểm tra quyền sở hữu khi gắn vào tin, dọn object mồ côi và health check. CDN/HA/object-storage managed vẫn là hạng mục hạ tầng production.
- `GATED`: eKYC, ký số, escrow/payment, HA database, backup restore và benchmark tải phải được nghiệm thu với provider/hạ tầng đích trước khi mở production.
# Cập nhật 2026-09-12

- eKYC: manual review implemented for three required private images (CCCD front/back and selfie); documents are ClamAV-scanned and authorization-protected in MinIO.
- Packaging/payment: FREE/STANDARD/PRO plans, quotas, manual VietQR reconciliation, invoices, admin bank settings, persisted/SSE notifications, immediate account refresh and admin SMTP notification implemented.
- Product boundary: broker workspace no longer offers escrow/buying actions; it reports persisted listing/lead metrics and SLA settings for a trusted listing/contact platform.
- Search/map: Elasticsearch index with PostgreSQL fallback, MapLibre clustering, bounding-box “search this area”, and cached/rate-limited Nominatim geocoding endpoint implemented.
- Runtime: eight-service Compose stack healthy; V012 applied; 19/19 backend tests and frontend production build pass.
