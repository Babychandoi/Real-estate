# Design system — Nhà Đất Chuẩn

## Hướng thiết kế

Bề mặt sáng, navy làm màu hành động, xanh lục dùng cho xác thực. Thông tin quyết định nằm trước trang trí: giá, diện tích, vị trí, trạng thái tin và hành động tiếp theo. Không thêm số liệu giao dịch, đánh giá hay nhãn chứng thực nếu API không cung cấp. Nhãn xác minh danh tính người đăng khác với nhãn xác thực tin.

Nguồn triển khai: `frontend/tailwind.config.js`, `app/styles/index.css`, `app/shared/ui/` và `app/entities/listing/ui/`. File JSON đi kèm là bản xuất tài liệu; Tailwind/CSS là nguồn chạy thực tế.

## Token

| Token | Giá trị | Mục đích |
| --- | --- | --- |
| primary | `#00355f` | CTA chính, tiêu đề trọng tâm |
| primary-container | `#0f4c81` | Hover CTA, focus ring |
| primary-on | `#ffffff` | Chữ trên CTA navy |
| secondary | `#006c4a` | Trạng thái tích cực/xác thực |
| surface | `#f8f9ff` | Nền toàn trang |
| surface-container-lowest | `#ffffff` | Card, form, modal |
| surface-container-low | `#eff4ff` | Hero, khu vực phụ |
| surface-container | `#e5eeff` | Tab, trạng thái hover |
| on-surface | `#0b1c30` | Nội dung chính |
| on-surface-variant | `#42474f` | Nội dung phụ cần đọc |
| outline-variant | `#c2c7d1` | Viền form |
| ndc-border | `#dce3eb` | Viền card và shell |
| error | `#ba1a1a` | Lỗi |
| error-container / on-container | `#ffdad6` / `#93000a` | Nền/chữ cảnh báo lỗi |

Badge xác thực dùng emerald-50/800; VIP dùng amber-50/900. Không dùng chữ nhạt trên nền cùng tông cho thông tin quan trọng. Focus: viền 3 px, offset 3 px. Shadow card hover/dialog: `0 12px 32px rgb(11 28 48 / 7%)`.

Spacing ưu tiên 4/8/12/16/20/24/32/40/48/64 px. Radius hiện hữu có override: `sm=4`, mặc định `8`, `md=12`, `lg=16`, `xl=24`, `2xl=16` theo Tailwind mặc định, `full=9999` px. Vì `xl` và `2xl` không tăng tuyến tính trong theme hiện tại, designer phải dùng số px của component; không suy diễn từ tên class. Chưa đổi bảng radius toàn cục để tránh ảnh hưởng màn hình nghiệp vụ.

## Typography và layout

- Font: Be Vietnam Pro; fallback system sans-serif nếu chưa tải font. Không thêm thư viện font mới.
- Hero: 30 px trên điện thoại, 48 px từ `sm`; semibold, line-height chặt. H2 khối: 24 px. Tiêu đề card/body: 16 px. Nội dung phụ: 14 px. Badge/caption: 12 px.
- Input/select/textarea trên mobile: tối thiểu 16 px để tránh zoom form ngoài ý muốn.
- Content công khai: max-width 1280 px; gutter 16 px mobile, 24 px từ 640, 32 px từ 1024.
- Header công khai cao tối thiểu 80 px, sticky. Menu mobile mở dialog; link đang chọn có nền riêng. Account navigation cuộn ngang trong vùng của nó.
- Listing grid: 1 cột dưới 640; 2 cột từ 640; 3 cột từ 1024. Gap 20 px. Ảnh tỷ lệ 4:3.
- Màn hình chi tiết: gallery, khối giá/thông số, mô tả và liên hệ. Desktop có cột liên hệ sticky; mobile chuyển theo luồng dọc.
- Admin: sidebar sáng trên desktop, menu dialog trên mobile; bảng được cuộn trong vùng bảng. Form bộ lọc không làm tràn toàn trang.

## Component và hành vi

| Component | Biến thể/trạng thái | Quy tắc |
| --- | --- | --- |
| Button | primary, secondary, outline, ghost; loading, disabled | Tối thiểu 44 px ở component dùng chung; chỉ dùng button cho hành động |
| Link CTA | primary-link, text-link, icon | Điều hướng bằng Link; không bọc button trong Link |
| ListingCard | SALE, RENT; verified; ảnh lỗi; so sánh chọn/chưa chọn | Giá thuê có `/tháng`; tiêu đề và người đăng là link độc lập; compare không điều hướng |
| ListingGallery | không ảnh, thumbnail, viewer | Hiển thị tất cả ảnh API trả về; Escape đóng, phím mũi tên chuyển ảnh; trả focus về nút mở |
| Dialog | menu, gallery, report, picker | Có tên, khóa cuộn nền, giới hạn focus trong dialog; nội dung cuộn trong max-height 90dvh |
| StatePanel | loading, empty, error, not-found | Error có hành động thử lại; không dùng số 0 giả thay cho dữ liệu tải thất bại |
| ListingSkeleton | 6 card chờ tải | Giữ cấu trúc bố cục trước khi dữ liệu tới |
| Filter | select, search combobox, purpose tabs | Nhãn rõ; thay filter đặt lại page; URL là nguồn trạng thái |
| Badge | verified, vip, neutral, error | Có chữ, không chỉ dựa vào màu; verified tin không đồng nghĩa giấy tờ pháp lý đã bảo đảm |
| AdminShell / AccountNavigation | role, active route, mobile | Menu theo quyền hiện hữu; backend vẫn là nơi thực thi quyền |

Một số modal/biểu mẫu nghiệp vụ legacy vẫn giữ cấu trúc cũ. PR này chưa chứng nhận toàn bộ trạng thái modal của hệ thống đáp ứng WCAG; các màn hình và hành vi đã kiểm tra được liệt kê trong QA.

## Dữ liệu và microcopy

- Format giá theo `vi-VN`; phân biệt mua với thuê. Không dùng ngưỡng giá bán cho thuê nhà.
- Trang tìm kiếm trả 24 tin/lần từ API. Khi API chưa có `total/hasNext`, chỉ đưa điều hướng trước/tiếp; không hiển thị tổng số tự suy đoán. Trang cuối đủ đúng 24 tin có thể dẫn tới trang rỗng tiếp theo; cần API trả `hasNext` để loại bỏ.
- Bộ lọc xác thực và bản đồ ghi rõ phạm vi trang hiện tại. Không mô tả lọc client là tìm trên toàn bộ kho tin.
- Mất kết nối: giữ thông báo rõ và nút retry. Ảnh lỗi có nền/icon thay thế. Bản đồ lỗi có hành động tải lại, người dùng vẫn xem được danh sách.
- Thông báo mutation chỉ hiện thành công sau khi API đáp ứng; đang lưu thì khóa thao tác lặp.

## Accessibility và responsive

Giữ một `main` và h1 hiển thị mỗi trang; skip link, nhãn form, focus-visible, native dialog cho component mới. Tôn trọng `prefers-reduced-motion`. Nút đóng và icon action có accessible name. Tránh nội dung chỉ xuất hiện khi hover. Mục tiêu sản phẩm là WCAG 2.2 AA; bằng chứng hiện tại chỉ là tập kiểm tra tự động theo tag WCAG 2/2.1 A/AA và các thao tác bàn phím nêu trong QA, chưa phải chứng nhận.
