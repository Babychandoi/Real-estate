# Bộ giao diện Nhà Đất Chuẩn

Bản bàn giao CR-UI-2026-09 cung cấp mã React/TypeScript chạy trong frontend hiện hữu. Phạm vi gồm tất cả route hiện có: website công khai, tài khoản, đăng tin và quản trị. Các trang chủ, tìm kiếm, gallery chi tiết và kho tin được làm lại sâu; những màn hình nghiệp vụ còn lại được đồng bộ shell, màu sắc, chữ, form và responsive. Mức thay đổi từng trang được ghi tại [PAGE_MATRIX.md](PAGE_MATRIX.md).

Đây là bộ mã giao diện, chưa phải file Figma native `.fig`. [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md), [design-tokens.json](design-tokens.json) và [FIGMA_HANDOFF.md](FIGMA_HANDOFF.md) là đặc tả để dựng lại component/frame trong Figma.

## Xem thử ngay trên máy

Môi trường đã kiểm tra: Node.js 24, npm, Chromium trên Linux.

```bash
git fetch origin
git switch --track origin/feat/ui-product-design-20260928
cd frontend
npm ci
npm run preview:ui
```

Nếu đã có nhánh local, dùng `git switch feat/ui-product-design-20260928`. Mở `http://127.0.0.1:4173`. Server fixture nội bộ dùng cổng 4174; cả hai cổng cần trống.

- Đăng nhập: `preview@example.test`, mật khẩu bất kỳ không rỗng. Đây là mô phỏng UI, không xác minh mật khẩu hay quyền ở backend.
- Vai trò mặc định: `ADMIN`. Để xem đúng menu môi giới hoặc người mua: `npm run preview:ui -- --role=BROKER` hoặc `--role=USER`; cũng hỗ trợ `MODERATOR`.
- Sau khi đổi vai trò, đăng xuất và đăng nhập lại hoặc mở cửa sổ riêng tư.
- Dữ liệu minh họa gồm 55 tin, người dùng và một số lead/gói đăng tin. Ảnh là SVG mô phỏng, có nhãn. Các hàng đợi kiểm duyệt, CMS, dự án, đơn hàng và thẩm định khởi tạo rỗng.
- Mô phỏng có tìm kiếm, phân trang, so sánh, gallery, đăng nhập, cập nhật hồ sơ văn bản, gửi duyệt/ẩn tin, SLA, gửi liên hệ/báo xấu. Những hành động chưa mô phỏng trả lỗi rõ ràng. Upload, eKYC, thanh toán và phê duyệt nghiệp vụ phải kiểm tra trên backend thật.
- Dữ liệu nằm trong bộ nhớ, đặt lại khi khởi động lại server. Không đưa chế độ này ra Internet hoặc dùng thông tin cá nhân thật trong bản mô phỏng.

## Chạy với API thật

Khởi động backend theo README gốc. Frontend mặc định proxy `/api` tới `http://localhost:8080`.

```bash
cd frontend
npm ci
npm run dev
```

Mở `http://localhost:3000`; đăng nhập bằng tài khoản backend. Nếu backend dùng địa chỉ khác, đặt `API_INTERNAL_URL` khi chạy Vite. Ví dụ Bash: `API_INTERNAL_URL=http://localhost:8080 npm run dev`.

`npm run build` tạo bản production trong `frontend/dist`. Fixture nằm trong `tests/ui` và `scripts`, không được import vào mã `app/`. Banner mô phỏng chỉ hiện trong Vite mode `ui-preview`.

## Kiểm tra

```bash
cd frontend
npm run typecheck
npm run lint
npm run build
npx playwright install chromium
npm run test:ui
```

Trong repo hiện tại `lint` chạy `tsc --noEmit`, không phải ESLint. `test:ui` tự khởi động server mô phỏng, kiểm tra 34 tình huống route, 12 luồng hồi quy mới và 2 luồng hiện hữu (login/đi tới chi tiết) ở bốn chiều rộng 360/768/1024/1440 px. Báo cáo JSON ở `test-results/ui-results.json`; ảnh trang chính ở `test-results/screenshots/`. Kết quả thực chạy và giới hạn: [QA.md](QA.md).

## Tài liệu bàn giao

| Tài liệu | Nội dung |
| --- | --- |
| [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | Token, bố cục, typography, component, tương tác và trạng thái |
| [FIGMA_HANDOFF.md](FIGMA_HANDOFF.md) | Cấu trúc file và đặc tả Auto Layout/Variants để chuyển sang Figma |
| [design-tokens.json](design-tokens.json) | Danh mục token có thể đọc bằng máy |
| [PAGE_MATRIX.md](PAGE_MATRIX.md) | Toàn bộ route, mức triển khai, phần cần nghiệm thu backend |
| [QA.md](QA.md) | Bằng chứng build, regression, a11y và việc chưa xác minh |
| [PERFORMANCE_HANDOFF.md](PERFORMANCE_HANDOFF.md) | Cải thiện dữ liệu frontend và việc còn cần API/SQL/cache |
| [ADR](../architecture/adr/ADR-UI-2026-09-existing-spa.md) | Đề xuất giữ SPA/auth/API hiện hữu trong phạm vi UI |

PR là bản để review. Việc merge, deploy và nghiệm thu dữ liệu sản xuất chưa thực hiện trong đợt này.
