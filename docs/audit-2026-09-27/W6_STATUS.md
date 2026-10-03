# Trạng thái đợt W6 (hoàn thiện các mục còn mở) — 2026-10-03

Đợt này chạy vòng code → review → sửa → review cho các mục còn mở của `01_REQUIREMENTS.md`.
Đã **tiếp tục** theo yêu cầu ngày 2026-10-03. PR #26 đã merge tại `16677a0`, PR #22 đã merge tại `12149d8`; các PR còn lại đang hoàn thiện review.
Production vẫn là `13e41a2`; chưa deploy W6.

## 1. Đã làm xong và đã lên production (trước W6)
- PR #16 (sửa CI, ảnh mẫu visual, phân trang search), #17 (QR khi đăng ký MFA), #19 (chữ dài tràn card ở trang quản trị),
  #20 (menu Tin tức/Dự án/Khu vực, lề trang, hộp thoại căn giữa, ô nhập thẳng hàng), #21 (trạng thái tải/lỗi, đo tải CI,
  Redis lỗi thì bỏ qua ngay thay vì chờ 2 s).
- Ma trận tại `13e41a2`: 143 DONE · 16 PARTIAL · 6 TODO · 7 EXTERNAL (ma trận chưa cập nhật theo #16–#21 và W6).

## 2. W6 — tiến độ tích hợp

| PR | Nhánh | Trạng thái review | Còn phải làm trước khi merge |
|---|---|---|---|
| #26 | `fix/npm-audit-braces` | Đã merge; CI backend/frontend/security/e2e đều xanh | Hoàn tất, merge đầu tiên tại `16677a0` |
| #22 | `audit/w6-ops` | **Đã merge**, review vòng 3 không còn BLOCKER/MAJOR; toàn bộ CI và artifact khôi phục/rollback PASS | Hoàn tất `--no-deps`, hàm shell bash/zsh, env mẫu, xác thực hostname trước envsubst; CI `37103278682` và drills `37103278738` xanh, RTO PITR 25,2 s / mất máy 47,5 s (CI) |
| #24 | `audit/w6-backend` | Đã sửa vòng 3; full verify và review tích hợp đang chạy | `801f897`: sửa kiểm tra audit đồng thời/backlog, tách index V106 khỏi V103, checkpoint kiểm tra tăng dần, giới hạn 50 lần tìm chung, idempotency lưu response và chống hash nhập nhằng. Rà quyền phát hiện thêm tin ACTIVE đã quá hạn: sửa SQL/cache/media và mapping ES v2, tự rebuild mapping cũ. 33 test tập trung xanh; chưa kết luận CI cuối |
| #25 | `audit/w6-ux` | Đã sửa baseline/inert/focus; CI `37103393159` còn lỗi E2E | `cf74476`: frontend/backend/security xanh. Đang sửa vùng bấm nhỏ ở CMS/reports, focus ban đầu của filter thuê và thứ tự Tab trong CMS/projects; kiểm tra riêng visual theo browser, không giảm assertion |
| #23 | `audit/w6-perf` | CI + PR smoke xanh tại `3128409`; phép đo đầy đủ còn lỗi | Đã gom cụm bản đồ bằng ES, giới hạn truy vấn DB, cache fallback và warmup/readiness. Burst/soak 100k đã có lượt xanh; fault/cold và 1M chưa đạt. Đang bổ sung làm ấm HTTP trước readiness, `shm_size: 256mb` cho PostgreSQL và ràng buộc dung lượng cache; phải đo lại |

**Thứ tự merge bắt buộc** (Flyway không cho chạy lệch thứ tự): #26 → #23 (V100–V101; V102 không dùng) → #24 (V103–V106); #22, #25 lúc nào cũng được.
**Deploy:** dùng đúng lệnh trong runbook mới của #22: `-p bds-production -f docker-compose.yml -f infra/compose.apple-silicon.yaml`, app dùng `--no-deps`.

## 3. Kết quả W6 đã có bằng chứng (chờ merge mới tính DONE)
- F09.3, D-05, F09.2: EXPLAIN 100k/1M trên CI; thêm index slug (1M: 223 ms → 0,1 ms).
- F05.5: độ trễ xuất bản p95 ≈ 1,7 s (mục tiêu 10 s).
- F21.5: RTO 27–31 s (mất DB), 49–68 s (mất cả máy); rollback 3 bản cũ đều chạy.
- F11.2, R-6: header 153/153, prerender 49/49 trên site thật; IP qua Cloudflare đúng; phát hiện + sửa http→https.
- R-3 (3 lỗ hổng API v1), R-4 (tranh chấp duyệt gói), F17.4 (phễu KYC), R-2 (300 tin, 2 engine), `audit_write_failed` (2 lỗi thật).
- DS-03 (681→0 vùng bấm nhỏ), DS-04/axe 0 lỗi, DS-06 (zoom 200%), DS-08, R-7 phần tự động.

## 4. Chưa đạt / còn mở
- D-13, R-5: lượt đo burst cũ thất bại đã có sửa và lượt 100k xanh, nhưng cold/fault 100k và 1M vẫn chưa đạt; không dùng kết quả PR smoke để thay phép đo đầy đủ.
- DS-05: 8 chỗ lệch bảng 8.3 cần chủ sản phẩm chọn sửa code hay sửa tài liệu.
- UI-12: câu chữ thời hạn lưu giấy tờ KYC (biến `VITE_KYC_RETENTION_NOTICE`).
- Cập nhật `01_REQUIREMENTS.md`, `IMPLEMENTATION_PLANS_HISTORY.md`, `WALKTHROUGHS_HISTORY.md` sau khi merge.

## 5. Việc của chủ dự án (không làm bằng code được)
- **Repo đang công khai, file sao lưu chứa CCCD/KYC/email/mật khẩu băm ai cũng tải được** (`backups/20260926-005833/`,
  có trong cây `main` hiện tại). Chủ dự án đã chọn giữ công khai. Kế hoạch xử lý: `docs/ops/GIT_HISTORY_REMEDIATION_PLAN.md`
  (trong PR #22). Cần đánh giá nghĩa vụ thông báo theo pháp luật.
- Docker Desktop → Settings → General → bật "Start Docker Desktop when you sign in". Quyết định FileVault (có người đăng nhập
  sau mất điện, hoặc chấp nhận site sập tới khi có người đăng nhập).
- 7 mục EXTERNAL đã có tài liệu hướng dẫn (trong PR #22): branch protection, Search Console, quyết định KYC, lịch sử git,
  tách production khỏi máy này, thử với 5–8 người dùng thật, phỏng vấn nguồn cung.
- Quyết định: trang dữ liệu thử `uat-…` đang công khai (xóa / noindex / giữ); `lead_kyc_blocked` lưu `user_id`.
- Cổng 55432/56379/59000 bị project `ai-marketing` chiếm khi nó chạy → đổi cổng một bên.

## 6. Tiếp tục thế nào
Các worktree trong `.claude/worktrees/` đang tiếp tục theo bảng mục 2. Nhánh `audit/w6-consolidation` giữ cập nhật trạng thái,
gate npm audit không chấp nhận báo cáo lỗi/thiếu dữ liệu, gate diễn tập thất bại khi outcome sai, và runbook chờ backend
healthy trước khi mở frontend. Chưa merge/deploy nhánh tổng hợp này; vẫn cần CI tích hợp và diễn tập với schema mới.
