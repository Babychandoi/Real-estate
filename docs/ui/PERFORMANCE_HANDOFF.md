# Hiệu suất và dữ liệu — phần đã làm, phần tiếp theo

## Trong PR giao diện

- Các route tiếp tục lazy-load; bản đồ của tìm kiếm chỉ import khi chọn map. Build vẫn cảnh báo chunk MapLibre lớn, cần tối ưu/đo riêng nếu ngân sách tải bản đồ chưa đạt.
- Kết quả tìm kiếm lấy 24 tin/lần. URL giữ purpose, sort, price, area, page và vùng tọa độ hợp lệ; đổi filter đặt lại page. Không tải toàn bộ kho tin để tìm kiếm trên trình duyệt.
- Gợi ý địa điểm debounce 450 ms và hủy request cũ; cập nhật kết quả tìm kiếm có cleanup để response cũ không ghi đè trạng thái mới. Bản đồ nhận focus không gây vòng lặp refetch ở luồng được kiểm thử.
- Ảnh card lazy-load, decoding async, tỷ lệ cố định và fallback; gallery dùng tất cả ảnh API cung cấp. Trạng thái loading/error/empty tách rõ.
- Wrapper compare nằm trong feature, entity card nhận action qua prop. Không thêm global state manager hay package UI.

## Giới hạn API hiện hữu

| Vấn đề | Hệ quả UI hiện tại | Hướng triển khai tiếp |
| --- | --- | --- |
| Search trả array, chưa có `hasNext/total` | Nút tiếp dựa trên đủ 24 tin; có thể tới trang rỗng khi số tin chia hết 24 | Hợp đồng response `{items, hasNext, nextCursor}` hoặc tổng nếu chi phí phù hợp; đồng bộ OpenAPI |
| Verified lọc sau phân trang | Chỉ lọc trang hiện tại, có nhãn giải thích | Thêm tham số server, lọc trước page/limit và kiểm tra index |
| Map lấy cùng trang list | Chỉ có pins của trang hiện tại | Endpoint bbox/zoom, giới hạn số features, clustering server/vector tiles khi đủ lớn |
| My-listings trả toàn bộ tin của chủ | Phân trang 9 là client-side; chưa scale với chủ nhiều tin | Page/cursor + counts theo trạng thái từ server; tránh tải lại cả kho sau mutation |
| SPA / bearer sessionStorage hiện hữu | Metadata client-side chưa thay SSR; phiên đăng nhập giữ kiến trúc cũ | ADR riêng cho SSR/OpenAPI client/HttpOnly cookie, CSRF và migration session |

## Kế hoạch đo và tối ưu backend

PR này không thay SQL, cache hoặc thuật toán backend, không có số đo tải production để khẳng định “tối ưu nhất”. Trước khi làm, thu baseline p50/p95/p99, throughput, error rate, DB time và Core Web Vitals trên dữ liệu đại diện.

1. Với query nóng, thu `EXPLAIN (ANALYZE, BUFFERS)` trên môi trường kiểm thử an toàn; kiểm tra predicate, index kết hợp theo status/purpose/sort, truy vấn theo ID và N+1. Index tọa độ phải dựa trên kiểu dữ liệu/query thực tế; không thêm index chỉ từ phỏng đoán.
2. Chuyển page sâu sang keyset với thứ tự ổn định và tie-breaker ID; kiểm thử không lặp/mất tin khi có insert mới. Tìm kiếm chữ tiếng Việt và khoảng địa lý cần query plan phù hợp trước khi chọn engine ngoài.
3. Cache read công khai theo bộ filter chuẩn hóa, TTL, invalidation khi publish/update; không cache nhầm dữ liệu theo người dùng/PII vào key dùng chung. Đo hit rate và stampede thay vì tăng TTL tùy ý.
4. Tách API pins/cluster khỏi card chi tiết; giới hạn bbox, zoom và số bản ghi. Chỉ dùng thuật toán/cấu trúc dữ liệu mới khi profiling chỉ ra bottleneck.
5. Load test các luồng search/detail/publish/lead với dữ liệu phân bố thực, xác định SLO cùng đội sản phẩm. Kiểm tra cache lạnh/ấm, truy vấn không có kết quả và lỗi hạ tầng.

Các bước này là backlog kỹ thuật để nghiệm thu tiếp, chưa được tính là hoàn thành trong bộ giao diện.
