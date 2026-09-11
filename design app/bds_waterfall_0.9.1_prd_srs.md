# Kế hoạch triển khai website bất động sản
**Bản Markdown của hồ sơ Waterfall phiên bản 0.9.1**
Công nghệ: **Backend Spring Boot; Frontend React TypeScript**
Mã hồ sơ: **BDS WF 2026** • Phiên bản: **0.9.1** • Ngày: **10/09/2026**

---

### Tóm tắt cốt lõi dự án & Đặc tả hệ thống
- **Mô hình:** Nền tảng đăng tin BĐS nhiều chủ thể (Chủ nhà, Môi giới, Người tìm mua/thuê, Kiểm duyệt viên, Quản trị).
- **Phạm vi trọng tâm:** Căn hộ & Nhà ở tại 1 thành phố khởi đầu; minh bạch thông tin, kiểm duyệt trước đăng, bản đồ & vị trí được làm mờ (public location), tiếp nhận và điều phối lead (khách liên hệ) an toàn và chống trùng 24h.
- **32 Yêu cầu chức năng (FR01 - FR32)** & **12 NFR (NFR01 - NFR12)**.
- **8 Nhóm Use Case:** UC01 (Tài khoản & dữ liệu cá nhân), UC02 (Tạo & gửi tin), UC03 (Duyệt sửa & vòng đời), UC04 (Tìm kiếm & bản đồ), UC05 (Lưu & so sánh), UC06 (Khách liên hệ & lead), UC07 (Xử lý vi phạm & quản trị), UC08 (Nội dung, dự án, dữ liệu & báo cáo).
- **Thiết kế kỹ thuật:** Spring Boot modular monolith, PostgreSQL + PostGIS, Redis rate limit, Outbox worker, React TypeScript Tailwind CSS.
