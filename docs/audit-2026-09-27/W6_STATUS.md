# Trạng thái đợt W6 (hoàn thiện các mục còn mở) — 2026-10-03

Đợt này chạy vòng code → review → sửa → review cho các mục còn mở của `01_REQUIREMENTS.md`.
Đã **tiếp tục** theo yêu cầu ngày 2026-10-03. PR #26 đã merge tại `16677a0`, PR #22 đã merge tại `12149d8`; các PR còn lại đang hoàn thiện review.
Production vẫn là `13e41a2`; chưa deploy W6.

## 1. Đã làm xong và đã lên production (trước W6)
- PR #16 (sửa CI, ảnh mẫu visual, phân trang search), #17 (QR khi đăng ký MFA), #19 (chữ dài tràn card ở trang quản trị),
  #20 (menu Tin tức/Dự án/Khu vực, lề trang, hộp thoại căn giữa, ô nhập thẳng hàng), #21 (trạng thái tải/lỗi, đo tải CI,
  Redis lỗi thì bỏ qua ngay thay vì chờ 2 s).
- Ma trận tại `13e41a2`: 143 DONE · 16 PARTIAL · 6 TODO · 7 EXTERNAL (ma trận chưa cập nhật theo #16–#21 và W6).

## 2. W6 — code đã viết, CHƯA merge (4 PR nháp + 1 PR CI)

| PR | Nhánh | Trạng thái review | Còn phải làm trước khi merge |
|---|---|---|---|
| #26 | `fix/npm-audit-braces` | Đã merge; CI backend/frontend/security/e2e đều xanh | Hoàn tất, merge đầu tiên tại `16677a0` |
| #22 | `audit/w6-ops` | **Đã merge**, review vòng 3 không còn BLOCKER/MAJOR; toàn bộ CI và artifact khôi phục/rollback PASS | Hoàn tất `--no-deps`, hàm shell bash/zsh, env mẫu, xác thực hostname trước envsubst; CI `37103278682` và drills `37103278738` xanh, RTO PITR 25,2 s / mất máy 47,5 s (CI) |
| #24 | `audit/w6-backend` | Review vòng 2: **chưa merge được** | Vòng 3 đang làm dở (worktree `agent-a978170b66845c17b`): `verify()` báo nhầm "bị sửa" khi bộ ghép chạy (MAJOR); endpoint kiểm tra báo `intact=false` vì backlog 1 s; V103 vẫn khóa bảng suốt lúc tạo index (tách index ra migration riêng); giới hạn 50 lượt tìm/request phải tính chung cả lần khởi động lại; vài NIT. Test tái hiện của reviewer: nhánh local `review2/w6-backend` commit `8535980` |
| #25 | `audit/w6-ux` | Review vòng 1 xong: 1 BLOCKER, 2 MAJOR | Vòng 2 đang làm dở (worktree `agent-a3d5d7ca89c67e347`): tạo lại 8 ảnh mẫu visual từ ảnh CI; đặt `inert` cho nền khi mở hộp thoại (trình đọc màn hình vẫn đọc được nền); kiểm tra focus đủ chặt + sửa ô tìm trong trang So sánh không có focus; mũi tên mở rộng ở trang Phân tích; nút eKYC hiện nhầm lúc đầu. Test của reviewer: nhánh local `review/w6-ux` commit `e1fc2c7` |
| #23 | `audit/w6-perf` | Review vòng 1 xong: 0 BLOCKER, 3 MAJOR | Vòng 2 đang làm dở (worktree `agent-a5a713c90ea2fb7df`): sửa nhãn CPU sai ở 3 chỗ; sửa chẩn đoán khởi động nguội; workflow đo tải chỉ chạy bản rút gọn trên PR; làm bản đồ nhanh hơn (O1 → nếu chưa đạt thì O2: gom cụm bằng Elasticsearch) + làm ấm DB trước khi nhận traffic. Test của reviewer: nhánh local `review/w6-perf` commit `5f1ca20` |

**Thứ tự merge bắt buộc** (Flyway không cho chạy lệch thứ tự): #26 → #23 (V100–V102) → #24 (V103+) ; #22, #25 lúc nào cũng được.
**Deploy:** dùng đúng lệnh trong runbook mới của #22: `-p bds-production -f docker-compose.yml -f infra/compose.apple-silicon.yaml`, app dùng `--no-deps`.

## 3. Kết quả W6 đã có bằng chứng (chờ merge mới tính DONE)
- F09.3, D-05, F09.2: EXPLAIN 100k/1M trên CI; thêm index slug (1M: 223 ms → 0,1 ms).
- F05.5: độ trễ xuất bản p95 ≈ 1,7 s (mục tiêu 10 s).
- F21.5: RTO 27–31 s (mất DB), 49–68 s (mất cả máy); rollback 3 bản cũ đều chạy.
- F11.2, R-6: header 153/153, prerender 49/49 trên site thật; IP qua Cloudflare đúng; phát hiện + sửa http→https.
- R-3 (3 lỗ hổng API v1), R-4 (tranh chấp duyệt gói), F17.4 (phễu KYC), R-2 (300 tin, 2 engine), `audit_write_failed` (2 lỗi thật).
- DS-03 (681→0 vùng bấm nhỏ), DS-04/axe 0 lỗi, DS-06 (zoom 200%), DS-08, R-7 phần tự động.

## 4. Chưa đạt / còn mở
- D-13, R-5: tải đột biến 3× thất bại (p95 ≈ 3,3 s, ~4000 rớt; bản đồ chiếm 86 % thời gian DB); khởi động nguội ~25 s chậm.
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
Mỗi worktree trong `.claude/worktrees/` giữ nguyên phần sửa dở (có thể có thay đổi chưa commit). Để làm tiếp: mở lại từng
luồng theo bảng mục 2, chạy hết vòng sửa, review lại, rồi merge theo đúng thứ tự.
