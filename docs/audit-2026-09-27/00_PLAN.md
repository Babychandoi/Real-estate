# Kế hoạch hoàn thiện yêu cầu audit 27/09/2026

## Mục tiêu và tiêu chí hoàn thành
- **Mục tiêu:** hoàn thiện toàn bộ yêu cầu trong `Real-estate_Audit_2026-09-27.md`.
- **Hoàn thành khi:** mọi dòng trong `01_REQUIREMENTS.md` ở trạng thái `DONE` có bằng chứng (test/lệnh/báo cáo trong
  `04_EVIDENCE.md`), hoặc `EXTERNAL` — việc bắt buộc cần con người/hệ thống bên ngoài (thử người dùng thật, Search
  Console, quyền admin GitHub, hạ tầng tách khỏi máy dev, quyết định pháp lý/sản phẩm) đã có đủ công cụ, tài liệu và
  bước thực hiện. Không dòng nào được đánh `DONE` chỉ vì “đã viết code”.

## Quy trình: Review → Code → Review → Code → Review
1. **Review 1 (đã làm):** đối chiếu báo cáo với mã nguồn tại `831a010`; bóc thành ma trận yêu cầu (`01`), hợp đồng kỹ
   thuật (`02`), quy tắc agent (`03`); dựng hạ tầng test dùng chung (`infra/test/compose.yaml`) và JDK 17 cục bộ.
2. **Code 1:** các luồng chạy theo đợt (tối đa 3 agent song song vì máy đang chạy production), mỗi agent một worktree và
   một nhánh `audit/<luồng>`.
3. **Review 2:** sau mỗi đợt, agent reviewer độc lập kiểm tra từng yêu cầu của luồng theo tiêu chí nghiệm thu, chạy lại
   test, soát lỗi bảo mật/hiệu năng; orchestrator gộp nhánh vào `audit-2026-09-27`, chạy toàn bộ test.
4. **Code 2:** sửa các lỗ hổng reviewer tìm thấy (gửi lại cho chính agent của luồng để giữ ngữ cảnh).
5. **Review 3:** review cuối trên nhánh tích hợp: toàn bộ backend/frontend/E2E, diễn tập migration trên bản sao dữ
   liệu, kiểm tra ma trận, cập nhật lịch sử (`IMPLEMENTATION_PLANS_HISTORY.md`, `WALKTHROUGHS_HISTORY.md`).

## Đợt và luồng
| Đợt | Luồng | Phạm vi chính |
|---|---|---|
| W1 | S0-BE | Test trên PostgreSQL thật (bỏ H2), schema dùng chung, job queue bền vững, khóa scheduler, mail outbox, ghi nhận analytics, vai trò OWNER, interface ảnh công khai |
| W1 | S0-FE | Design tokens, font tự host, UI kit, ESLint/Prettier, Vitest, hook metadata, `track()`, tái cấu trúc E2E (F01), artifact CI, budget bundle |
| W1 | S5-SEC (pha A) | Header Nginx dùng chung, IP thật qua tunnel, rate limit v2, ADR session, backup mã hóa/DR, observability |
| W2 | S2-SEARCH | Read model, pipeline chỉ mục (F05), API v2 search/map/detail (F02–F07, F09, F10), UI tìm kiếm/chi tiết/so sánh/người đăng |
| W2 | S3-SUPPLY | Đường ghi tin + wizard 4 bước, chủ nhà (OWNER), kho tin, lead (quota/idempotency/lịch sử/rút), hẹn xem, workspace môi giới, import, chất lượng tin, còn hàng/hết hạn |
| W2 | S4-ADMIN | Kiểm duyệt v2, quản trị tin/người dùng/báo xấu/xác minh/billing, trust, KYC, F18, chống trùng/tài sản/lịch sử giá |
| W3 | S1-MEDIA | Pipeline ảnh (WebP 320/640/960/1600, EXIF, xoay ảnh, placeholder), URL ký cho ảnh riêng tư, chính sách ảnh tin ẩn |
| W3 | S5-SEC (pha B) | MFA admin, quản lý phiên, test logout/revoke/expiry, least privilege, trang token (verify/forgot/reset) |
| W3 | S6-ENGAGE | Tin đã lưu, shortlist chia sẻ, tìm kiếm đã lưu + cảnh báo, trung tâm thông báo, tùy chọn thông báo, SSE nhiều node |
| W4 | S7-SEO | Prerender + status 404/410, sitemap index, CMS public/preview/lịch xuất bản, trang thông tin, trang dự án/khu vực, trang chủ |
| W4 | S8-ANALYTICS | Consent, lọc bot/nội bộ, retention, RUM, funnel/cohort/chỉ số trung tâm |
| W4 | S9-QUALITY | ArchUnit, OpenAPI snapshot + type sinh tự động, log có requestId/không PII, Problem Details (format toàn bộ chạy cuối cùng) |
| W5 | S10-PERF | Dataset 100k/1M, EXPLAIN, k6 constant-arrival-rate, test sự cố/cạnh tranh/rebuild/khôi phục |
| W5 | S11-UX | Rà soát UX/a11y/responsive cuối, E2E hành trình, baseline visual, alias admin |

## Quyết định đã chốt (có thể đổi nếu chủ sản phẩm yêu cầu)
- Giữ React + Spring Boot + PostgreSQL + modular monolith; Elasticsearch là chỉ mục dựng lại được.
- Giữ yêu cầu KYC khi gửi liên hệ (F17 để product/security quyết định; hệ thống bổ sung đo tỷ lệ bỏ cuộc).
- SEO bằng prerender ở tầng render (backend chèn metadata/nội dung chính vào shell SPA, trả đúng status) thay vì chuyển
  toàn bộ app sang SSR.
- Session: giữ bearer token opaque lưu phía server; ghi ADR, thêm MFA TOTP cho admin, thắt CSP (F20).
- Thêm vai trò OWNER (chủ nhà) với quyền đăng tin, không bắt chủ nhà tự nhận là môi giới.
- Backup trong Git LFS chứa dữ liệu production thật → không commit backup mới; kế hoạch xử lý lịch sử Git cần chủ repo
  quyết định (không tự viết lại lịch sử).

## Việc cần chủ dự án (EXTERNAL)
Bật branch protection/required checks (cần quyền admin repo), thử nghiệm người dùng 5–8 người mỗi nhóm, Search
Console, tách production khỏi máy dev/managed DB, pilot nguồn cung, thông tin pháp nhân vận hành cho trang giới thiệu/
liên hệ, người sở hữu SLA vận hành, quyết định viết lại lịch sử Git chứa backup.
