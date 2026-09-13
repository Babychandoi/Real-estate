# Đánh giá đóng gói và độ hoàn thiện sản phẩm

> Repository: [Babychandoi/Real-estate](https://github.com/Babychandoi/Real-estate)  
> Commit: [4f52c76](https://github.com/Babychandoi/Real-estate/commit/4f52c76f99d982aae3d7fd37dc9b76d647599b80)  
> Ngày đánh giá: 2026-09-11

## 1. Kết luận

**Điểm product readiness: 4,0/10 — feature-rich prototype, chưa phải sản phẩm thương mại.**

Repository gây ấn tượng tốt về độ rộng nghiệp vụ và tài liệu, nhưng “độ rộng” cao hơn “độ sâu”. Nhiều màn hình và API thể hiện đúng ý tưởng sản phẩm, song authentication, media, search map, notifications, analytics, eKYC và escrow chưa có adapter production thật.

| Cấp phát hành | Sẵn sàng? | Ghi chú |
|---|---|---|
| Demo nội bộ | Có điều kiện | Dữ liệu giả, banner demo, không nhập PII |
| Alpha có người dùng mời | Chưa | Cần auth, quyền, error handling, seed ổn định |
| Beta công khai | Không | Thiếu security, reliability, support và vận hành |
| Giao dịch tiền thật | Tuyệt đối không | Escrow/OTP chỉ là state simulation |

## 2. Bằng chứng build và chất lượng

- Frontend: `npm ci` và `npm run build` **pass**.
- Output: CSS 102,93 KB (16,47 KB gzip); JS 568,05 KB (146,60 KB gzip).
- Vite cảnh báo chunk JavaScript trên 500 KB; routes đang import đồng bộ, chưa lazy load.
- `npm audit --omit=dev`: 2 cảnh báo Moderate ở React Router 6.30.6.
- Backend có 14 method `@Test` nhưng tất cả nằm trong một class tích hợp lớn. Test dùng H2, `ddl-auto=create-drop`, tắt Flyway; không xác nhận tính tương thích PostgreSQL/PostGIS/migration.
- Backend test không chạy được trong môi trường audit do Maven Central không truy cập được. Đây là giới hạn kiểm chứng, không được tính thành lỗi build của dự án.
- Không có `.github/workflows`, PR, issue, branch protection, release/tag hoặc pipeline phát hành. Repository mới có một commit.

## 3. Mức hoàn thiện theo phân hệ

| Phân hệ | Mức | Nhận xét |
|---|---:|---|
| Listing/revision | 65% | Domain/persistence/controller khá rõ; dùng owner demo và fallback data |
| Moderation | 55% | Có queue/diff/approve/reject; chưa auth, chưa pagination/audit |
| Lead/report | 45% | Có backend và UI; hai form lead không thống nhất, mock fallback |
| Verification/eKYC | 35% | Có model/UI/state; face match, encryption và document handling giả lập |
| Transaction/escrow | 20% | Chỉ mô phỏng state; không payment provider, ledger, reconciliation |
| Catalog/CMS | 50% | Có CRUD/revision cơ bản; thiếu quyền, pagination, media/sanitization |
| Search/map | 30% | SQL filter và map giả; chưa PostGIS/search engine/geocoding |
| IAM | 10% | Chỉ có schema/package-info; login/register ở frontend localStorage |
| Media | 5% | Package-info; người dùng dán URL ảnh |
| Privacy/import/audit | 5–10% | Chủ yếu package-info/schema/tài liệu, chưa adapter/service thực |
| Analytics | 20% | Số liệu phần lớn hardcode; có truy vấn `findAll()` |

## 4. Tính tương tác sản phẩm

### Tương tác tốt

- Luồng đăng tin 4 bước, preview, quality score và estimate price tạo cảm giác được hướng dẫn.
- Các trạng thái tin/kiểm duyệt/KYC/contract được trình bày dễ hiểu.
- Search filters, compare page, broker workspace và admin desk cho thấy product map tốt.
- Loading skeleton, disabled state và empty state đã xuất hiện ở nhiều trang.

### Tương tác chưa thật hoặc gây hiểu lầm

- [HomePage](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/frontend/app/routes/_public.home.tsx) có CTA tìm kiếm/filter/gợi ý không hoạt động.
- [LeadConsultationModal](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/frontend/app/features/lead/ui/LeadConsultationModal.tsx#L95-L137) gọi endpoint/payload không khớp và biến lỗi thành thành công.
- Workspace có các nút chỉ mở `alert()` thay vì thực hiện export/call/config.
- Các API frontend verification/lead tự fallback sang mock khi lỗi, khiến vận hành khó nhận biết outage và người dùng không phân biệt dữ liệu thật/giả.
- Analytics trả số baseline/hardcode nhưng UI có hình thức như dữ liệu production.
- Estimate price và quality score là rule đơn giản nhưng trình bày như AI/confidence chính xác cao.
- eKYC “face matching” là số ngẫu nhiên; OTP chấp nhận mọi mã 6 số.
- “Escrow locked/released” chỉ là thay đổi trạng thái database, không có tiền thật.

## 5. Đánh giá đóng gói

### Ưu điểm

- Backend/frontend tách rõ, Docker multi-stage, backend non-root.
- Có lockfile frontend, Maven wrapper scripts, Flyway và `.env.example`.
- README đủ để người kỹ thuật hiểu kiến trúc và URL.
- Quy tắc code/tài liệu/traceability rất chi tiết.

### Nhược điểm

- Frontend Dockerfile dùng `npm install`, không dùng `npm ci`; không copy lockfile trước dependency layer.
- Backend Dockerfile `-DskipTests package`; image có thể được tạo dù test lỗi.
- Tag base image không khóa digest; chưa tạo SBOM, ký image hoặc provenance.
- Compose production vẫn dùng profile `local`, public DB/Redis, default password.
- Biến Vite được đặt ở runtime dù cần build-time.
- Không healthcheck cho backend/frontend; không có readiness, startup probe hoặc smoke test.
- Không version API/schema/release notes theo một quy trình phát hành tự động.
- Không có license, contributing, code owners, security policy, support/runbook.

## 6. Phương án hoàn thiện sản phẩm

### Giai đoạn A — Honest demo package

- Biến mọi simulation thành interface + `DemoAdapter`; gắn badge DEMO toàn hệ thống.
- Dùng seed server-side ổn định; có một lệnh reset demo và self-test.
- Không catch lỗi rồi báo success; thêm error boundary, toast và retry.
- Vô hiệu hóa KYC/escrow thật; chỉ dùng hồ sơ giả.
- Hoàn thiện toàn bộ CTA đang chết hoặc xóa khỏi UI.

### Giai đoạn B — MVP marketplace

- IAM thật, quyền tài nguyên, hồ sơ người đăng.
- Upload media private/public pipeline; moderation ảnh.
- Search/map/geocoding thật; saved search/favorite/contact appointment.
- Notification worker qua outbox; email/SMS adapter.
- Pagination, API contract tests, E2E cho buyer/seller/moderator.

### Giai đoạn C — Commercial package

- Provider eKYC/ký số/payment/escrow thật; legal/compliance review.
- Billing/quota/subscription, invoice và support operations.
- Observability, SLO, incident response, audit và reconciliation.
- CI/CD ký artifact, SBOM, scan và rollout/rollback.

## 7. Chuẩn pipeline đề xuất

1. Lint + TypeScript + unit test.
2. Backend compile + unit + integration Testcontainers PostgreSQL/PostGIS/Redis.
3. API contract + frontend component + accessibility tests.
4. E2E buyer/seller/moderator.
5. SAST, SCA, secret scan, image scan, SBOM.
6. Build immutable images một lần; ký image.
7. Deploy staging; migration dry-run; smoke + security regression.
8. Manual approval cho production; canary; auto rollback theo SLO.

## 8. Definition of Done sản phẩm

- Không mock/fallback/hardcode chạy trong production profile.
- Mỗi CTA có loading/success/error và analytics event.
- Mỗi API có auth policy, validation, pagination và contract test.
- Mỗi provider bên ngoài có sandbox, timeout, retry, circuit breaker và idempotency.
- Tài liệu setup được kiểm tra trên máy sạch.
- Release có changelog, migration plan, rollback plan, SBOM và runbook.

## 9. Phán quyết

**Giữ kiến trúc modular monolith và product map hiện tại, nhưng đổi nhãn thành prototype.** Không nên tách microservice sớm; trước tiên cần biến các simulation thành adapter thật, thống nhất luồng người dùng và xây release pipeline có kiểm chứng.
