# Hệ thống thiết kế — Nhà Đất Chuẩn

Nguồn duy nhất cho giao diện web (DS-01). Kế thừa `design web desktop/modern_trust_real_estate/DESIGN.md` và bảng
token mục 8.2 của `Real-estate_Audit_2026-09-27.md`. Hướng thiết kế: giữ navy, nền sáng và Be Vietnam Pro; tăng độ rõ
thông tin, giảm nhãn trang trí, để ảnh thật và giá dẫn mắt.

| Ở đâu | Là gì |
|---|---|
| `frontend/app/styles/tokens.css` | **Nguồn token duy nhất** (CSS custom properties): màu, chữ, khoảng cách, bo góc, đổ bóng, focus, kích thước điều khiển, icon, chuyển động, lớp |
| `frontend/tailwind.config.js` | Ánh xạ theme Tailwind sang các biến trên (tên class cũ giữ nguyên); theme daisyUI được sinh từ cùng tệp; thiếu token → build lỗi |
| `frontend/app/styles/fonts.css` | Be Vietnam Pro tự host |
| `frontend/app/shared/ui/` | Bộ thành phần dùng chung (UI kit) |
| `/__ui` | Thư viện giao diện: mọi thành phần ở mọi trạng thái. Chỉ có trong bản dev hoặc khi build với `VITE_ENABLE_UI_CATALOG=true` (CI bật để chạy kiểm thử a11y); bản production không chứa |

## 1. Màu

Màu khai báo dạng kênh RGB (`--color-primary: 0 53 95`) để modifier độ mờ của Tailwind vẫn hoạt động
(`bg-primary/10`). Dùng tên ngữ nghĩa, không dùng mã hex trong component.

| Vai trò (audit 8.2) | Token / class Tailwind | Giá trị | Dùng cho |
|---|---|---|---|
| Brand primary | `primary` | `#00355F` | CTA chính, điều hướng đang chọn, giá nổi bật |
| Brand hover/container | `primary-container` | `#0F4C81` | Hover/selected của primary; không phủ vùng lớn |
| Success | `success` (= `secondary`) | `#006C4A` | Xác minh có căn cứ, thành công — luôn kèm nhãn chữ |
| Warning | `warning` | `#8A4B00` | Cần chú ý; **không** dùng để làm giá trông gấp |
| Error | `error` | `#BA1A1A` | Lỗi, hành động phá hủy |
| Info | `info` | `#0F4C81` | Thông tin trung tính |
| Nền trang | `surface` | `#F8F9FF` | Nền tổng thể (`body`) |
| Bề mặt | `surface-container-lowest` | `#FFFFFF` | Card, form, menu, dialog |
| Chữ chính | `on-surface` | `#0B1C30` | Nội dung chính |
| Chữ phụ | `on-surface-variant` | `#42474F` | Metadata, mô tả |
| Viền điều khiển | `outline` | `#727780` | Viền input, checkbox, chip chưa chọn |
| Đường phân cách | `outline-variant` | `#C2C7D1` | Chỉ để phân cách, **không** làm viền duy nhất của điều khiển |
| Focus | `focus` | `#0F4C81` | Vòng focus (xem mục 7) |

Mỗi màu phản hồi có bộ nền/chữ: `success-container` + `success-on-container`, `warning-container` +
`warning-on-container`, `error-container` + `error-on-container`, `info-container` + `info-on-container`.

**Độ tương phản đã đo trên cặp màu thực tế** (WCAG 2.2: chữ thường ≥ 4,5:1, chữ lớn ≥ 3:1, thành phần không phải chữ
≥ 3:1):

| Cặp | Tỷ lệ |
|---|---|
| `on-surface` trên `surface` | 16,3:1 |
| `on-surface-variant` trên `surface` / trắng | 8,9:1 / 9,4:1 |
| trắng trên `primary` / `primary-container` | 12,5:1 / 8,9:1 |
| `primary` trên trắng | 12,5:1 |
| `success` trên trắng | 6,5:1 |
| `warning` trên trắng / `warning-container` | 6,8:1 / 6,1:1 |
| `error` trên trắng / `error-container` | 6,5:1 / 5,0:1 |
| chữ `*-on-container` trên nền `*-container` (success, warning, error, info) | 8,3 / 9,9 / 7,2 / 9,6 :1 |
| `outline` (viền input) trên trắng | 4,5:1 |
| `outline-variant` trên trắng | **1,7:1** — không đủ cho viền điều khiển |
| `focus` trên trắng | 8,9:1 (trên chính nút `primary` chỉ 1,4:1 — vì vậy vòng focus nằm ngoài nút, có offset) |

Không dùng màu làm tín hiệu duy nhất: trạng thái luôn có chữ hoặc biểu tượng đi kèm (chip chọn có dấu ✓ Lucide,
badge xác minh có nhãn đầy đủ).

## 2. Chữ

**Be Vietnam Pro, tự host** (DS-02) qua `@fontsource/be-vietnam-pro`: weight 400/500/600/700, subset `vietnamese` +
`latin` (chia bằng `unicode-range`, chỉ woff2), `font-display: swap`. Trình duyệt chỉ tải subset/weight trang thực sự
dùng. Không còn gọi `fonts.googleapis.com` / `fonts.gstatic.com`. `font-extrabold`/`font-black` hiển thị bằng 700 —
dùng `font-bold`.

Thang chữ (px / line-height, audit 8.2):

| Utility | Cỡ | Dùng cho |
|---|---|---|
| `text-display` | 40/48 | H1 hero desktop |
| `text-display-mobile` | 28/36 | H1 trên mobile (dưới `sm`) |
| `text-headline-lg` | 32/40 | H1 trang desktop |
| `text-headline-md` | 24/32 | H2 |
| `text-headline-sm` | 18/26 | H3, tiêu đề card/dialog |
| `text-body` | 16/26 | Nội dung; **input luôn 16 px** (tránh iOS phóng to) |
| `text-body-sm` | 14/22 | Nội dung phụ |
| `text-label` | 12/16 | Nhãn nhỏ nhất cho thông tin có nghĩa |

Class Tailwind mặc định (`text-xs` … `text-5xl`) vẫn dùng được. **Cấm** `text-[8–11px]`: ESLint báo lỗi (DS-03).
`cn()` (`app/shared/ui/cn.ts`) đã dạy tailwind-merge các utility token — luôn ghép class bằng `cn()`, không dùng
`twMerge` trực tiếp (nếu không, `text-body text-on-surface` sẽ mất cỡ chữ).

## 3. Khoảng cách

Thang 4 · 8 · 12 · 16 · 24 · 32 · 48 · 64 px = Tailwind `1 · 2 · 3 · 4 · 6 · 8 · 12 · 16` (CSS: `--space-*`). Dùng
thường xuyên 8/16/24 (`2/4/6`); tránh giá trị tùy ý (`p-[13px]`). Lề trang mobile 16 px (`px-4`), container desktop
1200–1280 px (`max-w-7xl`).

## 4. Bo góc, đổ bóng, lớp

| Token | Giá trị | Dùng cho |
|---|---|---|
| `rounded-input` (= `rounded`) | 8 px | Input, nút |
| `rounded-card` / `rounded-dialog` (= `rounded-md`) | 12 px | Card, dialog nhỏ |
| `rounded-panel` (= `rounded-lg`) | 16 px | Panel, sheet |
| `rounded-pill` | 9999 px | Chỉ chip/badge — không bo mọi thứ thành viên thuốc |
| `shadow-card` / `shadow-card-hover` | nhẹ | Card (ưu tiên viền + khoảng cách) |
| `shadow-elevated` | rõ | Popup, dialog, sheet, toast |
| `shadow-cta` | nhẹ | Khối tìm kiếm/CTA nổi |
| `z-header` 50 · `z-overlay` 60 · `z-toast` 70 | | Header dính, lớp phủ modal, thông báo |

`rounded-xl` giữ giá trị cũ 24 px (lịch sử dự án); code mới dùng token ngữ nghĩa.

## 5. Kích thước điều khiển và vùng chạm

| Token | Cao | Dùng cho |
|---|---|---|
| `control-sm` (`h-control-sm`, `min-h-control-sm`) | 36 px | Thanh công cụ desktop dày đặc |
| `control-md` | 44 px | **Mặc định** — nút, input, chip, hàng checkbox/radio |
| `control-lg` | 48 px | Hành động chính trên màn hình cảm ứng |

Mục tiêu sản phẩm 44–48 px cho điều khiển chính trên thiết bị chạm. Mức tối thiểu WCAG 2.2 (2.5.8) là 24×24 px — bộ
kiểm thử axe đang chặn mọi vùng chạm nhỏ hơn.

## 6. Biểu tượng

- **Chỉ Lucide** (`lucide-react`), cỡ 16/20/24 (`size-icon-sm/md/lg` hoặc `<Icon icon={…} size="sm|md|lg" />`), một
  độ dày nét.
- **Không dùng emoji hay ký tự biểu tượng** (✕ ✓ ➔ 🏠 …) làm icon: ESLint báo lỗi.
- Icon trang trí: `aria-hidden="true"` (`<Icon>` tự thêm). Icon mang nghĩa một mình: `<Icon label="…">`.
- Nút chỉ có icon: `<IconButton icon={X} aria-label="Đóng" />` — kiểu TypeScript bắt buộc `aria-label`.

## 7. Focus và bàn phím

- Một chỉ báo focus cho mọi điều khiển: `:focus-visible { outline: 3px solid focus; outline-offset: 3px }`
  (`--focus-ring-width`, `--focus-ring-offset`). Không `outline-none` nếu không có thay thế rõ tương đương.
- Không lồng điều khiển: dùng `ButtonLink` thay vì `<Link><Button/></Link>`; card có một liên kết chính, các nút
  (so sánh, yêu thích) nằm ngoài liên kết.
- Dialog/Sheet: focus vào trong khi mở, Tab/Shift+Tab không thoát ra, Esc đóng (chỉ modal trên cùng), khóa cuộn nền,
  đóng xong trả focus về nút đã mở.
- Mỗi trang một `<main>` (của layout); trang con dùng `<div>`.
- Chuyển động tôn trọng `prefers-reduced-motion` (`motion-safe:`/`motion-reduce:`; `--duration-*` về 0).

## 8. Thành phần (UI kit, `app/shared/ui/`)

Tất cả xem được ở `/__ui`. Import trực tiếp từ tệp (`@/shared/ui/Button`), ghép class bằng `cn()`.

| Thành phần | Hợp đồng | Trạng thái có sẵn |
|---|---|---|
| `Button`, `ButtonLink`, `buttonClasses` | primary/secondary/outline/ghost/danger; sm 36 · md 44 · lg 48 | mặc định, hover, focus, disabled, loading (`aria-busy`) |
| `IconButton`, `Icon` | `aria-label` bắt buộc; Lucide 16/20/24 | như Button |
| `FormField` | label thật, hint, lỗi qua `aria-describedby`, `aria-invalid` | chưa nhập, đang nhập, lỗi, disabled, đang lưu, đã lưu |
| `TextInput`, `TextArea`, `Select` | 44/48 px, chữ 16 px, viền `outline` | mặc định, lỗi, disabled |
| `Checkbox`, `Radio`, `RadioGroup` | input gốc + label; group = fieldset + legend | chọn, bỏ chọn, một phần, disabled, lỗi nhóm |
| `Switch` | `role="switch"`, áp dụng ngay | bật, tắt, disabled |
| `Chip`, `ChipGroup` | `aria-pressed` + dấu ✓, 44/36 px | chọn, chưa chọn, disabled |
| `Dialog`, `Sheet` | modal có bẫy focus, Esc, trả focus; Sheet = bottom sheet mobile, panel phải từ `sm` | mở/đóng, có footer |
| `ToastProvider`/`useToast`, `InlineFeedback` | live region polite/assertive; dừng hẹn giờ khi hover/focus | thành công, lỗi có thử lại, xung đột, ngoại tuyến, thông tin, cảnh báo |
| `EmptyState`, `ErrorState` | nói lý do và bước tiếp; lỗi có “Thử lại” | trống, lỗi, đang thử lại |
| `Skeleton`, `SkeletonText`, `LoadingStatus` | skeleton trang trí + trạng thái có chữ cho trình đọc màn hình | đang tải |
| `Badge` | nhãn ≥ 12 px, không chỉ dựa vào màu | neutral, primary, success, warning, error, info |
| `TrustBadge`, `TrustPanel` | hợp đồng §6: danh tính / nội dung tin / giấy tờ sở hữu, kèm phạm vi và ngày | mọi trạng thái của §6; kiểm tra bị từ chối hiển thị như “chưa xác minh” ở nơi công khai |
| `Money`, `UnitPriceText` | hợp đồng §4 (`app/shared/format/money.ts`): “3,95 tỷ”, “14,5 triệu/tháng”, “~48,2 triệu/m²” | có giá, không có giá (“Chưa có giá”) |
| `ResponsiveImage` | `ImageDto` §10: srcset/sizes, giữ tỷ lệ, màu chủ đạo, dự phòng khi lỗi | có biến thể, chỉ URL gốc, lỗi tải, chưa có ảnh |
| `Avatar` | ảnh + dự phòng chữ cái đầu | ảnh, chữ cái, ảnh lỗi, không tên |
| `Pagination`, `LoadMore` | trang có `aria-current`; “Xem thêm” không bao giờ lấy độ dài trang làm tổng (“hơn 10.000”) | chờ, đang tải, lỗi, hết dữ liệu |
| `DataTable` | sắp xếp/lọc/phân trang do máy chủ; chọn theo trang | sẵn sàng, tải lần đầu, làm mới, trống, lỗi, lỗi một phần, không có quyền, xung đột |
| `Tabs` | WAI-ARIA tabs, phím mũi tên/Home/End | chọn, disabled, có số đếm |
| `Card`, `PrivateMediaImage` | có sẵn từ trước | |

Thành phần theo nghiệp vụ (SearchBox, FilterBar/Sheet, ListingCard, Gallery, ContactPanel, Compare) do luồng sở hữu
trang xây trên bộ kit này (S2, S3).

## 9. Kiểm thử và quy trình

- `npm run lint`: ESLint (jsx-a11y recommended, rules of hooks, cấm emoji-icon và chữ < 12 px).
- `tests/e2e/a11y.spec.ts`: axe WCAG 2.2 AA + kiểm tra cuộn ngang cho từng trang công khai ở 320–1440 px, và `/__ui`
  (mọi trạng thái, bẫy focus dialog/sheet, phím tabs).
- `tests/e2e/visual.spec.ts`: ảnh chụp tách riêng; baseline chỉ cập nhật sau khi đã xem ảnh diff (F01.5).
- Thêm hoặc đổi token: sửa `tokens.css` (và `tailwind.config.js` nếu là nhóm mới), đo lại tương phản trên cặp thực tế,
  cập nhật bảng ở tài liệu này và thêm trạng thái vào `/__ui`.
