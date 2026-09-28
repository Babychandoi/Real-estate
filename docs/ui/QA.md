# Kết quả kiểm tra UI — 28/09/2026

## Kết quả cuối

| Kiểm tra | Kết quả | Phạm vi |
| --- | --- | --- |
| `npm run test:ui` | **192/192 đạt**, 0 lỗi, 0 bỏ qua, retries=0; khoảng 5,2 phút | Chromium; viewport 360/768/1024/1440 × 48 trường hợp |
| `npm run typecheck` | Đạt | TypeScript app và vite config |
| `npm run lint` | Đạt | Script hiện tại là `tsc --noEmit`, không phải ESLint |
| `npm run build` | Đạt | TypeScript + Vite production build; còn cảnh báo chunk MapLibre >500 kB |
| Fixture từ chối mutation chưa hỗ trợ | Đạt 4 yêu cầu kiểm tra | PUT ngân hàng/tin/dự án/CMS chưa mô phỏng trả 405 |
| `git diff --check` | Đạt | Không lỗi khoảng trắng |
| Backend `sh ./mvnw -B verify -Dstyle.color=never` | **Bị chặn trước khi chạy test** | Không phân giải được `repo.maven.apache.org` để tải Spring Boot parent; không có số test backend đạt cho đợt này |

Môi trường: Node.js 24, Java 17, Playwright 1.55.1 / Chromium 140, Linux. Dữ liệu API được mô phỏng; kết quả không thay thế end-to-end với dịch vụ thật. Bản tóm tắt máy đọc: [qa-summary.json](qa-summary.json).

## 48 trường hợp ở mỗi chiều rộng

- 34 tình huống route: public, account, admin, mua/thuê, tạo/sửa, lỗi đường dẫn và prefix admin. Kiểm tra h1 hiển thị, đúng một main, không tràn ngang toàn trang, không pageerror và không endpoint fixture chưa khai báo.
- 12 luồng UI: phân trang/refresh/back; URL sai/error/retry/empty; gallery 20 ảnh/phím mũi tên/Escape/trả focus; compare độc lập và persistence; menu mobile/focus/role; mutation kho tin thành công/lỗi; chặn quyền USER ở admin; axe; bản đồ lazy/khu vực URL/lỗi tile/không refetch lặp; login/cập nhật navigation; CMS retry; dọn metadata khi rời chi tiết.
- 2 test hành vi hiện hữu được cập nhật selector phù hợp giao diện: dialog đăng nhập/keyboard/axe và link từ search tới slug chi tiết. Không xóa hoặc skip test cũ.

Axe ở 1440 px quét home, search, detail, compare, account, my-listings, admin users/moderation/billing/CMS. Ở 360/768/1024, bài quét màn hình chạy trên search. Các bài này không có vi phạm `serious/critical` với tag WCAG 2/2.1 A/AA. Test dialog đăng nhập kiểm tra toàn bộ vi phạm trả về trong phạm vi dialog. Đây không phải chứng nhận WCAG cho mọi màn hình và trạng thái.

Screenshot được tạo cho home, search, detail, my-listings và admin users ở mobile/desktop. Đã kiểm tra trực quan bố cục mobile và desktop. Hình ảnh, tên, số liệu trong screenshot là mô phỏng.

## Độ ổn định và giới hạn

Lượt kiểm tra trước phát hiện lỗi dropdown che nút search trên mobile, filter admin users tràn ở 1024 px, nhãn input login, focus dialog và tương phản CMS/moderation; đã sửa rồi chạy lại. Bài axe quét 10 trang được cấp 120 giây; assertion UI có 10 giây để chờ Vite/fixture trong môi trường chạy song song. Không giảm tiêu chí kiểm tra để đạt kết quả. Stylesheet font ngoài được stub trong nhóm UI để tránh phụ thuộc CDN; đây không phải đo tốc độ hay kiểm tra font tải từ production. Vite có cảnh báo preload trong phiên dev; build production hoàn thành, nhưng cần kiểm tra cache/CDN thật khi triển khai.

Chưa chạy lại toàn bộ bộ `test:e2e` đa trình duyệt với backend thật. Các baseline ảnh của giao diện cũ không được tự động chấp nhận/ghi đè; cần cập nhật có review hình ảnh trên môi trường UAT ổn định. Safari, Firefox, thiết bị thật, upload, gửi email, KYC, thanh toán, kiểm duyệt revision có dữ liệu thật và quyết định phân quyền phía server cần nghiệm thu tiếp. Chưa có Lighthouse/Core Web Vitals hoặc load test DB/cache trong PR này.

## Lặp lại trên máy

```bash
cd frontend
npm ci
npx playwright install chromium
npm run typecheck
npm run lint
npm run build
npm run test:ui
```

Báo cáo JSON đầy đủ được tạo ở `frontend/test-results/ui-results.json`, screenshot ở `frontend/test-results/screenshots/`. Báo cáo sinh tự động và `dist/node_modules` không được đưa vào commit. Kết quả được ghi trong tài liệu này và lịch sử nghiệm thu của repo.
