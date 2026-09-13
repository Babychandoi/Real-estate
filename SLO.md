# SLO và kế hoạch tải

- API tìm kiếm: 99,9% availability/tháng, p95 dưới 500 ms ở tải chuẩn.
- API ghi lead: 99,9% availability/tháng; không báo thành công nếu chưa có `leadId` bền vững.
- Error budget: 43 phút/tháng; dừng phát hành tính năng khi tiêu thụ trên 50% giữa kỳ.
- Mức ban đầu: 100 request/s đọc và 10 request/s ghi; kiểm tra bằng k6 trước mỗi thay đổi hạ tầng lớn.
- Cảnh báo: error rate trên 1% trong 5 phút, p95 trên 750 ms trong 10 phút, readiness down, pool DB trên 80% hoặc disk trên 80%.

Đây là mục tiêu khởi điểm, không phải kết quả benchmark production. Điều chỉnh sau mỗi lần đo tải và diễn tập khôi phục.

