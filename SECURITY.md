# Chính sách bảo mật

Không đăng lỗ hổng hoặc dữ liệu cá nhân lên issue công khai. Gửi báo cáo riêng cho đội vận hành, kèm phiên bản, tác động, bước tái hiện tối thiểu và phương án giảm thiểu nếu có. Đội dự án xác nhận tiếp nhận trong 2 ngày làm việc; sự cố P0 được xử lý ngay theo `RUNBOOK.md`.

Mật khẩu, khóa PII, token nhà cung cấp và dữ liệu thật không được commit. Môi trường production phải dùng secret manager, HTTPS tại ingress, khóa PII độc lập có quy trình luân chuyển, và bật nhà cung cấp đã ký hợp đồng trước khi bật capability eKYC/giao dịch.

