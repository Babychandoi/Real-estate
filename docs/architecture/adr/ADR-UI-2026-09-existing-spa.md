# ADR UI-2026-09 — Thiết kế giao diện trên frontend hiện hữu

- Trạng thái: Đề xuất để review cùng draft PR; chưa được phê duyệt.
- Ngày: 2026-09-28.
- Phạm vi: CR-UI-2026-09; toàn bộ route UI hiện hữu.

## Bối cảnh
Repo hiện chạy React Router SPA, apiClient viết tay và bearer token trong sessionStorage. Quy chuẩn dự án hướng tới SSR, OpenAPI client và cookie HttpOnly. Đổi toàn bộ kiến trúc/auth trong đợt thiết kế UI sẽ mở rộng phạm vi và cần backend, migration/session và kiểm thử riêng.

## Quyết định đề xuất
Giữ API, cơ chế xác thực, phân quyền và schema hiện tại trong PR này. Dùng React/TypeScript/Tailwind đã cài, không thêm framework hoặc state manager. Component dùng chung nằm trong shared/ui; gallery trong entities/listing/ui; wrapper so sánh ở features/compare để entity không phụ thuộc feature. Model bộ lọc ở features/search/model. Giữ module bản đồ legacy trong shared/map trong đợt này.

## Hệ quả và việc tiếp theo
UI chạy với backend hiện tại. PR này không tuyên bố hoàn thành SEO SSR, bảo mật session, tìm kiếm keyset/total/verified toàn tập, hoặc tối ưu SQL/cache. Những hạng mục đó cần PR riêng, đo tải và review kiến trúc. Native dialog dùng cho navigation/gallery/report; các modal nghiệp vụ legacy còn cần kiểm tra riêng. Khả năng quay lại: revert commit của nhánh UI; không có migration dữ liệu.
