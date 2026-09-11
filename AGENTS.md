# Antigravity Agent Memory & Operational Rules (BDS WF 2026)

## 📌 QUY TẮC BẮT BUỘC: ĐỒNG BỘ LỊCH SỬ KẾ HOẠCH & NGHIỆM THU (MEMORY PERSISTENCE)

Mọi Agent (Antigravity AI) khi thực hiện bất kỳ tác vụ triển khai, chỉnh sửa kiến trúc, thêm tính năng, migration CSDL, hay kiểm thử tự động mới trong dự án này **BẮT BUỘC** phải tuân thủ nguyên tắc ghi nhớ và cập nhật đồng bộ sau:

### 1. Luôn cập nhật [IMPLEMENTATION_PLANS_HISTORY.md](file:///d:/B%E1%BA%A5t%20%C4%91%E1%BB%99ngS%E1%BA%A3n/IMPLEMENTATION_PLANS_HISTORY.md)
- Mỗi khi có đợt triển khai mới (tính năng, phân hệ, tái cấu trúc), ngay sau khi lập kế hoạch, Agent phải bổ sung mục tương ứng vào file `IMPLEMENTATION_PLANS_HISTORY.md` theo thứ tự thời gian.
- Nội dung mỗi đợt bao gồm:
  - Tên đợt triển khai & Mã yêu cầu (FRs, UCs, NFRs).
  - Căn cứ thiết kế mẫu (Design prototypes trong `design web desktop` hoặc `design app`).
  - Kiến trúc kỹ thuật: Flyway migration, Backend module (Domain, Persistence, Service, Controller), Frontend routes & components.
  - Các ràng buộc nghiệp vụ & Kế hoạch kiểm thử tương ứng.

### 2. Luôn cập nhật [WALKTHROUGHS_HISTORY.md](file:///d:/B%E1%BA%A5t%20%C4%91%E1%BB%99ngS%E1%BA%A3n/WALKTHROUGHS_HISTORY.md)
- Sau khi thực thi và chạy kiểm thử tự động, Agent phải bổ sung kết quả nghiệm thu vào file `WALKTHROUGHS_HISTORY.md`.
- Nội dung mỗi đợt nghiệm thu bao gồm:
  - Danh mục các file, module và route giao diện đã xây dựng.
  - Bằng chứng kiểm thử tự động Backend (JUnit/MockMvc `mvn test` - số test cases pass).
  - Bằng chứng đóng gói Frontend (TypeScript strict check `tsc -b` & Vite build `npm run build` - 0 errors).
  - Hướng dẫn vận hành & Luồng tương tác người dùng (User flows).
  - Cập nhật lại Bảng tổng hợp kiểm thử và Bảng ánh xạ route toàn hệ thống ở cuối file.

### 3. Quy chuẩn công nghệ & Lệnh kiểm thử
- **Lệnh chạy Maven Backend trên Windows JDK 17:**
  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; $env:PATH = "C:\Program Files\Java\jdk-17\bin;C:\Program Files\Java\apache-maven-3.9.14\bin;$env:PATH"; & 'C:\Program Files\Java\apache-maven-3.9.14\bin\mvn.cmd' test
  ```
- **Lệnh build Frontend:**
  ```bash
  npm run build
  ```
  (Luôn dọn sạch biến unused import để tuân thủ `noUnusedLocals: true`).

- **Kiến trúc Modular Monolith:** Tuân thủ triệt để [PROJECT_CODE_RULES_BDS.md](file:///d:/B%E1%BA%A5t%20%C4%91%E1%BB%99ngS%E1%BA%A3n/PROJECT_CODE_RULES_BDS.md) (Hexagonal Architecture, không rò rỉ JPA entity ra API, bất biến ContentRevision ERD04/ED04, che mờ PII an toàn).
