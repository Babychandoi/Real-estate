# Đặc tả chuyển giao sang Figma

Chưa tạo file `.fig` hoặc tài liệu trong tài khoản Figma. Tài liệu này ánh xạ mã giao diện đã chạy sang bộ frame/component để designer dựng và duy trì. JSON token là danh mục thông số, không cam kết tương thích trực tiếp mọi plugin import.

## Cấu trúc file đề xuất

| Page trong Figma | Nội dung |
| --- | --- |
| 00 Foundations | Color variables, typography, spacing, radius, grid |
| 01 Components | Button, input, select, badge, listing card, dialog, navigation, state panel |
| 02 Public | Home, search/list/map, detail/gallery, compare, seller, information, auth |
| 03 Account | Profile, my listings, post/edit, leads, inquiries, broker, billing, KYC |
| 04 Admin | Moderation, listings, users, reports/leads, verification, billing, analytics, projects, CMS |
| 05 States & Flows | Loading/empty/error, modal, filter, validation và luồng theo vai trò |

Frame: Desktop 1440 px, Laptop 1024 px, Tablet 768 px, Mobile 360 px. Chiều cao theo nội dung, không cố định bằng ảnh chụp. Grid công khai max 1280 px căn giữa; tham số và breakpoint tại DESIGN_SYSTEM.md.

## Component specs

- `Button`: Auto Layout ngang, center, min-height 44 px; padding ngang 12/16/20 theo size; gap 6/8/10; variant primary/secondary/outline/ghost; state default/hover/focus/disabled/loading; optional leading/trailing icon.
- `Field`: Auto Layout dọc, label → control → helper/error; gap 6 px; width Fill container; control min-height 44 px. Text không nằm trong placeholder thay label.
- `ListingCard`: Auto Layout dọc, Fill width, image ratio 4:3, content padding 20 px; title tối đa 2 dòng; metadata và price wrap khi cần. Property purpose/verified/image-error/compare-selected; optional seller row.
- `Dialog`: overlay riêng; width min(available − 32 px, component max); max-height 90% viewport; header cố định, body scroll. Frame gallery mở rộng riêng theo class triển khai; không gom mọi modal về cùng kích thước.
- `Navigation`: public header, account strip, admin sidebar là ba component khác nhau; property viewport/role/active-item. Mobile menu là dialog có tên.
- `Feedback`: variant loading/empty/error; title + explanation + optional retry, min-height 256 px ở StatePanel.

## Frame bắt buộc cho mỗi màn hình

Dựng trạng thái có dữ liệu từ preview, sau đó các trạng thái thực sự có trong API/UI: chờ tải, không có dữ liệu, lỗi, quyền không phù hợp. Không điền số liệu thật bằng số minh họa. Với bảng dùng horizontal overflow, thể hiện vùng cuộn trong frame; không làm cả màn hình rộng vượt viewport.

## Luồng prototype

1. Home → tìm mua/thuê → lọc giá/khu vực → trang 2 → detail → gallery → liên hệ.
2. Search → chọn 2 tin → compare → bỏ/chọn tin; mục đích SALE/RENT phải nhất quán.
3. Login → my listings → đăng/sửa → nộp duyệt; lỗi lưu ở đúng bước.
4. Admin login → moderation/users → chi tiết nghiệp vụ → trạng thái sau xử lý. Hành động cần backend được đánh dấu trong PAGE_MATRIX.

Đối chiếu frame với app chạy thực tế và screenshot QA; CSS/React là nguồn xác định hành vi. Chỉ coi Figma hoàn thành khi có file thật với variables, components và prototype được designer kiểm tra; bộ mã hiện tại không giả lập file đó.
