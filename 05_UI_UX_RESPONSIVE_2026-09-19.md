# Đánh giá UI/UX, accessibility và responsive

Snapshot: [`b8d66bf`](https://github.com/Babychandoi/Real-estate/commit/b8d66bf251c117b2775a6b586a55ea8262ccaaff) — 2026-09-19

## Kết luận

Hệ thống hình ảnh nhìn chung nhất quán: palette, card, typography, khoảng cách, trạng thái và shell admin đã được cải thiện; touch target chủ yếu đạt khoảng 44 px. Các ảnh baseline trước đó cho thấy giao diện tốt ở 393/412/768/1440 px.

Không thể cấp chứng nhận visual cho redesign hiện tại vì 20/21 visual cases không khớp snapshot và không có artifact “received” đầy đủ để duyệt trong repo. Một lỗi thật đã được xác nhận: trang search ở viewport 320 px chụp ra chiều rộng 337 px, tức có horizontal overflow. Ngoài ra, cấu trúc test hiện khiến axe/overflow assertion không chạy nếu screenshot assertion thất bại.

## Kết quả Playwright tại CI

Tại [run 57](https://github.com/Babychandoi/Real-estate/actions/runs/35048152964):

| Nhóm | Kết quả | Diễn giải |
|---|---|---|
| Tổng | 8/35 pass, 27 fail | CI đỏ |
| Navigation | 7 fail | Test tìm accessible name `/Chi tiết/`, UI hiện là `Xem chi tiết: ...`; lệch chữ hoa/thường |
| Visual | 20/21 fail | Baseline chưa cập nhật sau redesign |
| 320 px search | Overflow thật | Received screenshot rộng 337 px thay vì 320 |
| Axe/overflow | Không được chạy ở đa số case | Screenshot assertion đứng trước trong cùng test và abort case |

Điều quan trọng: 7 lỗi locator không chứng minh card navigation bị hỏng; card đã có link. Nhưng pipeline vẫn cần sửa vì test stale làm che các regression thật.

## Điểm mạnh UI hiện tại

- Layout public có max-width, spacing và card hierarchy rõ.
- Mobile navigation đưa login/action vào vị trí hợp lý hơn.
- Listing card có toàn bộ vùng click và accessible label mô tả chi tiết.
- Search có list/map mode rõ, marker liên kết detail.
- Forms dùng label, trạng thái loading/error ở nhiều màn chính và nút cao tối thiểu 44 px.
- Auth modal và lead modal có focus-management/focusable query; KYC có `role=alert/status` ở các trạng thái quan trọng.
- Admin shell mới tách khỏi public shell, sidebar/mobile menu nhất quán; bảng có wrapper overflow ở nhiều nơi.
- Listing images có alt theo thứ tự, lazy loading và async decoding.

## Phát hiện UX/responsive

### UI-01 — P1: tràn ngang thật ở 320 px

Ảnh nhận được trong CI có chiều rộng 337 px tại viewport 320 px. Trang search có toolbar/filter chips/sort controls và các vùng flex dễ vượt viewport. Đây không chỉ là snapshot mismatch.

Khuyến nghị:

- chạy `document.documentElement.scrollWidth <= innerWidth` độc lập trước/sau screenshot;
- log phần tử có `getBoundingClientRect().right > innerWidth`;
- thêm `min-w-0`, wrapping, `max-w-full`, tránh width cố định/padding cộng dồn;
- test 320, 360, 375, 393, 412 px và zoom 200%.

### UI-02 — P1: visual test architecture che accessibility test

Các case kết hợp `toHaveScreenshot`, axe và overflow trong một test tuyến tính. Khi screenshot fail, các assertion sau không chạy. Do đó “27 failed” không đồng nghĩa đã kiểm tra accessibility cho các case đó.

Khuyến nghị: tách ba spec/project độc lập:

1. functional/navigation;
2. accessibility + keyboard + overflow;
3. visual snapshots.

Visual update phải được review ảnh diff, không cập nhật hàng loạt mù quáng.

### UI-03 — P1: coverage tập trung vào home/search/compare, bỏ luồng rủi ro cao

E2E functional chỉ có navigation và auth modal; visual chủ yếu home/search/compare. Không có luồng KYC, create/edit listing, lead, billing, report, admin moderation hoặc CMS. Đây là lý do hợp đồng eKYC sai vẫn lọt qua trong khi unit/backend tests xanh.

Khuyến nghị: ưu tiên E2E theo risk, không theo số màn hình. Mỗi P0 workflow có happy path + forbidden/error state.

### UI-04 — P1: search state và responsive interaction chưa nhất quán

Sort/price filter không khởi tạo đầy đủ từ URL; chip filter không phải lúc nào cũng cập nhật URL ngay. Back/forward/share link có thể tạo màn hình và kết quả khác nhau. `onlyVerified` chỉ lọc page client, làm count và empty state gây hiểu nhầm.

Bằng chứng: [`_public.search.tsx` dòng 18–94](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_public.search.tsx#L18-L94).

Khuyến nghị: URL-driven state, server filtering, mobile bottom-sheet có apply/reset rõ, sticky controls không che nội dung.

### UI-05 — P1: compare thiếu selection UX

Trang tự điền listing khi không có `ids`; nút “Chọn lại” quay về search nhưng search không có selection tray. Người dùng không có mental model “đã chọn gì, tối đa bao nhiêu, vì sao tin bị loại”.

Khuyến nghị: checkbox/action trên card, sticky compare tray, counter 0/3, disable khác purpose kèm giải thích; empty state không tự chọn.

### UI-06 — P1: KYC gate xuất hiện quá muộn

Người chưa verified vẫn thấy CTA liên hệ bình thường, mở modal và điền xong mới bị backend từ chối. Nên surface requirement trước thao tác và giữ intent sau khi user hoàn thành KYC.

### UI-07 — P2: “Đã tự động lưu” và score `/100` làm giảm độ tin cậy

Thực tế chỉ lưu khi bấm; score tối đa 80. Đây là microcopy/feedback sai, đặc biệt nhạy cảm trong form dài.

Bằng chứng: [`_public.listings.new.tsx` dòng 115–180](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/app/routes/_public.listings.new.tsx#L115-L180).

### UI-08 — P1: nhiều admin state chưa phân biệt loading/empty/error

CMS nuốt lỗi fetch và chỉ set state khi array không rỗng; lỗi mạng và “chưa có bài” có thể trông giống nhau. Một số action khác chỉ log console. Với hệ thống vận hành, failure state phải cho biết dữ liệu có cũ hay không và hành động retry.

### UI-09 — P2: trang search dài và thiếu progressive loading

Không có pagination/load-more; mobile chỉ cuộn danh sách đã tải. Count phản ánh số item current response, không phải tổng matching inventory. MapLibre chunk lớn nên cần lazy mount khi chọn map.

### UI-10 — P2: SEO/crawler experience chưa đầy đủ

Meta/canonical/JSON-LD listing được set sau JavaScript trong SPA, không bảo đảm crawler/social bot luôn nhận. Sitemap chỉ có tối đa 10.000 listing. `robots.txt` chặn `/admin/`, nhưng admin mới ở `/2026/nhadatchuan/admin/`, nên namespace thật không bị disallow.

Bằng chứng:

- [`frontend/public/robots.txt`](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/frontend/public/robots.txt)
- [`ListingSeoController.java` dòng 27–43](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/listing/api/ListingSeoController.java#L27-L43)

Khuyến nghị: SSR/prerender public detail, OpenGraph validation, sitemap index/shards/cache; robots chặn namespace admin/account mới (robots không phải security boundary).

## Accessibility cần kiểm tra lại sau khi làm xanh snapshot

Không kết luận “đạt WCAG” từ code hoặc vài axe runs. Bộ test cần bao gồm:

- keyboard-only: mở/đóng menu/modal, focus return, Escape, focus trap;
- visible focus và contrast ở normal/hover/disabled/error;
- form errors được liên kết `aria-describedby`, summary và focus tới field đầu lỗi;
- accessible name ổn định cho icon-only buttons, map controls, pagination;
- table/card semantics ở admin mobile;
- reduced motion, zoom 200–400%, reflow 320 CSS px;
- screen reader announcements cho save/upload/submit/reconciliation;
- map có danh sách tương đương, không bắt buộc pointer/visual interaction.

## Breakpoint test matrix đề xuất

| Viewport | Mục tiêu |
|---|---|
| 320 × 568 | Reflow tối thiểu, không horizontal scroll |
| 360 × 800 | Android phổ biến |
| 393 × 852 | iPhone phổ biến |
| 412 × 915 | Android lớn |
| 768 × 1024 | Tablet portrait |
| 1024 × 768 | Tablet landscape/small laptop |
| 1280 × 720 | Laptop thấp, kiểm tra vertical space |
| 1440 × 900 | Desktop baseline |

Chạy với text scale/zoom, locale tiếng Việt dài và dữ liệu cực trị: tiêu đề dài, giá lớn, địa chỉ nhiều dòng, 0/1/20 ảnh, hàng trăm lead.

## Cách sửa pipeline visual

1. Sửa locator navigation theo role/link và accessible name ổn định, tránh regex phụ thuộc chữ hoa.
2. Chạy functional/a11y/overflow; sửa lỗi thật trước.
3. Sinh ảnh received/diff cho toàn bộ viewport và lưu artifact CI.
4. Review thủ công từng diff trên commit redesign; chỉ sau đó cập nhật baseline.
5. Chạy lại Chromium/Firefox/WebKit; required check phải xanh.
6. Không để visual failure ngăn security scan.

## Điều kiện UI/UX để public beta

- Không overflow từ 320 px và zoom 200%.
- Functional, a11y và visual suites độc lập, đều xanh.
- E2E có KYC, listing, lead, report, billing và admin moderation.
- Search/compare state có thể share/back/forward đúng.
- Mọi action có busy/success/error/retry rõ; không còn copy “đã chạy” cho tác vụ chưa tồn tại.
- Lighthouse/Web Vitals được đo trên build production với thiết bị/network phổ thông; budget cho MapLibre và route chunks.
