# Trạng thái đợt W6 (hoàn thiện các mục còn mở) — 2026-10-04

Đợt này chạy vòng code → review → sửa → review cho các mục còn mở của `01_REQUIREMENTS.md`.
Đã **tiếp tục** theo yêu cầu ngày 2026-10-04. PR #26, #22, #23, #24 đã merge; `main` hiện `e4bc1dd`.
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
| #24 | `audit/w6-backend` | **Đã merge** tại `e4bc1dd`; review không còn BLOCKER/MAJOR | Head `55cb498`, core CI `37121779106` và smoke xanh. Audit/checkpoint/V103–V106, quyền API/cache/media/ES, hạn tin và owner ACTIVE khi nhận lead, legacy idempotency đã sửa. Recovery schema V106 đo 31,6/57,2 s; artifact restart còn health 503 dù workflow xanh — đang sửa gate/chờ healthy trên nhánh tổng hợp, chưa nghiệm thu phần này |
| #25 | `audit/w6-ux` | Còn hai lỗi kiểm tra vòng Tab trong CMS/Dự án | `a63b339`: các route/axe/reflow/touch targets, filter thuê và Chromium visual đã xanh. Trace xác định native date/time input có nhiều segment Tab dùng cùng DOM node; phép đo blur/refocus làm sai vòng Tab. Đang sửa phép đo với regression native browser, giữ assertion; thêm fixture Redis UUID chỉ chứa chữ để tránh số điện thoại ngẫu nhiên |
| #23 | `audit/w6-perf` | **Đã merge** tại `84df652`; core CI + smoke xanh ở `841971e` | HTTP warmup giới hạn cả body trên JDK 17, bbox dự phòng chính xác và thời điểm snapshot thật, cache có giới hạn đồng thời, PostgreSQL shm 256 MB, SQL extractor đọc được guard ghép chuỗi. Smoke 10k/1 phút p95 đọc 8,05 ms / ghi 10,61 ms, 0 lỗi/rớt; đang đo lại strict 100k/1M trên bản tích hợp, không coi smoke là nghiệm thu tải đầy đủ |

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
