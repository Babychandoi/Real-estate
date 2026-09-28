# AI, 3D và chat — hiện trạng và tiêu chí mở (audit P-12)

Nguyên tắc: không giả lập. Tính năng chưa có dữ liệu hoặc quy trình kiểm chứng thì API trả `501 FEATURE_NOT_IMPLEMENTED`
rõ ràng và giao diện không hiển thị con số, nhãn hay trợ lý giả.

## Hiện trạng (27/09/2026)

| Khả năng | Hiện trạng trong mã | Bằng chứng |
|---|---|---|
| Định giá tự động (AVM) | `POST /api/v1/listings/estimate-price` trả 501 "Chưa có nguồn dữ liệu định giá được kiểm chứng." | `ListingController.estimatePrice`, `BdsApplicationTests` (gọi endpoint, kỳ vọng 501) |
| Điểm chất lượng phía máy chủ | `POST /api/v1/listings/quality-score` trả 501; checklist chất lượng khi đăng tin chạy phía người dùng (S3a) | `ListingController.calculateQualityScore`, `BdsApplicationTests` |
| Thống kê giá khu vực/dự án | Có, nhưng là **trung vị giá chào** trên tin đang hiển thị, chỉ khi có ≥ 5 tin, luôn kèm phương pháp và thời điểm; không phải giá giao dịch | `PublicCatalogService` (S7), `SeoPrerenderTests.areaStatisticsNeedFiveListingsBeforeAMedianIsShown` |
| 3D / tham quan ảo | Không có | — |
| Chat / trợ lý AI | Không có; liên hệ đi qua yêu cầu tư vấn/hẹn xem (lead) có SLA | S3b |

## Tiêu chí mở từng khả năng

**Định giá (AVM).** Mở khi đồng thời có: (1) nguồn giá giao dịch hoặc giá thuê hợp đồng được phép sử dụng, ghi rõ nguồn
và ngày; (2) tối thiểu 12 tháng dữ liệu và ≥ 30 mẫu so sánh cho mỗi khu vực × loại hình hiển thị; (3) kiểm định ngoài
mẫu với sai số trung vị (MdAPE) ≤ 10 % và công bố sai số đó cạnh kết quả; (4) hiển thị khoảng ước tính, độ phủ và câu
"ước tính, không phải thẩm định giá". Chủ sở hữu: product + dữ liệu; cần rà soát pháp lý trước khi công khai.

**Điểm chất lượng phía máy chủ.** Mở khi tiêu chí chấm được sản phẩm và kiểm duyệt phê duyệt (trọng số, ngưỡng) và có
đo tương quan với tỷ lệ bị báo xấu/tỷ lệ lead đủ điều kiện trên ít nhất một quý dữ liệu.

**3D / tham quan ảo.** Mở khi có nhà cung cấp và luồng kiểm duyệt nội dung 3D (không lộ thông tin cá nhân trong ảnh,
xác minh đúng bất động sản), và ngân sách tải trang cho trang chi tiết vẫn đạt (`bundle-budget.json`, tải theo yêu cầu).

**Chat / trợ lý AI.** Mở khi: đo được nhu cầu (tỷ lệ bỏ cuộc ở form liên hệ, S8), có quy tắc không để lộ số điện thoại
trước khi người đăng đồng ý, lưu trữ/kiểm soát nội dung theo chính sách dữ liệu, và trợ lý chỉ trả lời từ dữ liệu tin
đang hiển thị kèm nguồn — không tự tạo thông tin pháp lý hay giá.

Mọi mở khóa phải có test chứng minh endpoint không còn 501 chỉ khi các điều kiện trên được cấu hình, và báo cáo ghi rõ
nguồn dữ liệu.
