# Ma trận giao diện và phạm vi nghiệm thu

`Thiết kế sâu`: thay cấu trúc và component chính. `Đồng bộ`: dùng shell/theme/typography/form responsive mới, giữ luồng nghiệp vụ đang có. Tất cả route dưới đây có mã hoạt động trong router; không đồng nghĩa mọi mutation đã được nghiệm thu trên production.

## Website công khai

| Route | Thay đổi | Dữ liệu / nghiệm thu bổ sung |
| --- | --- | --- |
| `/` | Thiết kế sâu: hero, tìm mua/thuê, danh mục, card, loading/error/empty | Tin mới từ listing search; không thêm thống kê giả |
| `/search` | Thiết kế sâu: URL filters, 24 tin/trang, giá theo mục đích, list/map, retry | API chưa trả total/hasNext; verified và pins giới hạn trong trang |
| `/listings/:listingId` | Gallery toàn bộ ảnh, giá thuê, trạng thái 404/network, metadata cleanup, contact/report | Kiểm tra lead/report và quyền chủ tin với API thật |
| `/compare` | Card/picker/dialog, giá thuê, điều hướng và theme | Giữ chọn tối đa 3 tin cùng mục đích; persistence trong browser |
| `/nguoi-dang/:sellerId` | Card và theme đồng bộ, phân biệt xác minh danh tính | Profile và tin từ API người đăng |
| `/about` | Shell/theme/typography | Nội dung hiện hữu; chủ sản phẩm cần duyệt trước phát hành |
| `/terms` | Shell/theme/typography | Điều khoản hiện hữu; chưa thẩm định pháp lý |
| `/privacy` | Shell/theme/typography | Chính sách hiện hữu; chưa thẩm định pháp lý |
| `/contact` | Shell/theme/typography | Cần kiểm tra kênh liên hệ vận hành thực tế |
| `/forgot-password` | Form và shell đồng bộ | Email gửi thật cần kiểm tra riêng |
| `/reset-password` | Form, h1 và trạng thái thiếu token | Luồng token hợp lệ/hết hạn cần backend |
| `/verify-email` | Shell và trạng thái thông báo | Luồng email/token thực cần backend |
| `*` | Trang không tìm thấy trong shell | Có đường quay lại |
| Login/register modal | Label liên kết input, form mobile, focus/keyboard, đồng bộ độ dài password đăng ký | Test login bằng fixture; đăng ký/OTP/email thực chưa nghiệm thu |

## Tài khoản

| Route | Thay đổi | Dữ liệu / nghiệm thu bổ sung |
| --- | --- | --- |
| `/account` | Navigation theo role, form/theme đồng bộ | API hồ sơ hiện hữu; upload avatar thật cần kiểm tra |
| `/my-listings` | Thiết kế sâu: thống kê, filter, card, loading/error, busy/notice | API hiện trả toàn bộ tin của chủ; phân trang 9 tin là client-side |
| `/listings/new` | Shell/form/typography responsive | Giữ wizard, upload, quota, KYC, submit hiện hữu; không mô phỏng đầy đủ upload |
| `/listings/new?edit=:id` | Cùng form đăng tin, trạng thái sửa | Thử mở bằng fixture; revision/submit thật cần backend |
| `/my-leads` | Navigation, chữ/form đồng bộ | Giữ API phân trang; reveal PII/status cần backend |
| `/my-inquiries` | Navigation, chữ/form đồng bộ | API sent leads phân trang 12; dành cho USER |
| `/broker/workspace` | Navigation và layout/theme đồng bộ | SLA và thống kê API hiện hữu |
| `/billing` | Navigation, chữ/form/bảng đồng bộ | Thanh toán/gói/đối soát thực chưa nghiệm thu |
| `/kyc` | Navigation, form/typography đồng bộ | Không dùng giấy tờ thật với fixture; upload/grant/review cần backend |

## Quản trị

Prefix: `/2026/nhadatchuan/admin`. AdminShell dùng layout sáng, sidebar theo quyền, menu mobile và vùng nội dung có landmark.

| Route sau prefix | Thay đổi | Dữ liệu / nghiệm thu bổ sung |
| --- | --- | --- |
| `/` | Chuyển tới moderation | Không còn shell rỗng khi truy cập prefix |
| `/login` | Layout độc lập, h1/main, form đồng bộ | Quyền backend vẫn giữ nguyên |
| `/moderation` | Chuẩn hóa nền/chữ, queue, toolbar; bỏ mã FR trong tiêu đề | Fixture queue rỗng; diff revision và quyết định kiểm duyệt cần API thật |
| `/listings` | Shell, bảng, trạng thái và theme | Giữ API page/size=20; visibility thật cần backend |
| `/users` | Filter responsive không tràn 1024 px; bảng và theme | Pagination server hiện hữu; khóa/mở khóa chưa nghiệm thu thật |
| `/leads-and-reports` | Shell/theme/typography, vùng cuộn | Quy trình lead/report thật cần backend |
| `/verification` | Shell/theme/typography | Queue fixture rỗng; quyết định thẩm định cần backend |
| `/billing` | H1, filter labels, shell/theme/bảng | Đơn hàng, ngân hàng, duyệt tiền cần backend thật |
| `/analytics` | Shell/theme/typography | Fixture số liệu gắn nhãn demo; chưa đo funnel production |
| `/projects` | Shell/theme/form | Danh sách fixture rỗng; CRUD dự án cần backend |
| `/cms` | Shell/theme/filter contrast; load/error/retry; bỏ fixture chết trong app | Fixture queue rỗng; revision/publish thật cần backend |

Redirect legacy `/admin/*` và `/2026/nhadatchua/admin/*` được giữ. Quyền menu và ProtectedRoute không thay thế kiểm soát quyền API.

## Phủ kiểm thử

34 tình huống route ở mỗi viewport, gồm mua/thuê, tạo/sửa tin, prefix admin, đăng nhập, trang không tìm thấy. Mỗi tình huống kiểm tra h1, đúng một main, không tràn ngang toàn trang, không pageerror và không gọi endpoint fixture chưa khai báo. Không phải kiểm thử tất cả biến thể dữ liệu của từng trang. Các luồng chi tiết và kết quả thực chạy ở QA.md.
