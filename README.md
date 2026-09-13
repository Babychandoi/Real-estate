# Hệ thống Nền tảng Bất Động Sản (BDS WF 2026)

Dự án website bất động sản thương mại điện tử / nền tảng kết nối tin đăng bất động sản theo tiêu chuẩn chất lượng cao, thiết kế theo mô hình **Modular Monolith** kết hợp **Spring Boot (Java)** và **React TypeScript**.

---

## 1. Kiến trúc & Công nghệ nền tảng

- **Backend**: Java 17 / 21, Spring Boot 3.3+, Spring MVC, Spring Security, Spring Data JPA/JDBC, Spring Validation.
- **Frontend**: React 18/19, TypeScript (strict mode), React Router Framework Mode, Vite, Tailwind CSS & daisyUI.
- **Cơ sở dữ liệu**: PostgreSQL 16+ với extension **PostGIS** hỗ trợ truy vấn địa lý không gian; quản lý migration bằng **Flyway**.
- **Bộ nhớ đệm & Giới hạn tần suất**: Redis.
- **Lưu trữ ảnh**: MinIO bucket riêng tư; ảnh chỉ được tải lên qua API đã xác thực và đọc qua URL cùng origin.
- **Hàng đợi nghiệp vụ**: Bền vững hóa qua mẫu thiết kế **Durable Transactional Outbox** và Java Worker.
- **Hợp đồng API**: REST JSON, OpenAPI 3.1, xử lý lỗi chuẩn RFC 9457 Problem Details.
- **Container**: Docker multi-stage build, non-root runtime container, Docker Compose.

---

## 2. Cấu trúc thư mục

```text
.
├── backend/                  # Ứng dụng Spring Boot 3 (Modular Monolith)
│   ├── src/main/java/com/company/bds/
│   │   ├── shared/          # Cấu hình, lỗi, bảo mật, thời gian dùng chung
│   │   ├── iam/             # Quản lý tài khoản & phân quyền
│   │   ├── catalog/         # Danh mục dự án, chủ đầu tư, phân khu
│   │   ├── listing/         # Nghiệp vụ tin đăng & vòng đời revision bất biến
│   │   ├── media/           # Quản lý tập tin hình ảnh & watermark
│   │   ├── search/          # Tìm kiếm & truy vấn không gian PostGIS
│   │   ├── moderation/      # Kiểm duyệt tin & bàn đối chiếu diff
│   │   ├── verification/    # Thẩm định giấy tờ pháp lý & cấp huy hiệu
│   │   ├── lead/            # Tiếp nhận khách liên hệ & chống trùng 24h
│   │   ├── content/         # CMS bài viết, cẩm nang & điều khoản
│   │   ├── privacy/         # Bảo vệ PII, làm mờ tọa độ, HMAC tra cứu SĐT
│   │   ├── importing/       # Nhập dữ liệu nguồn tin hàng loạt (Dry-run)
│   │   └── audit/           # Nhật ký kiểm toán hệ thống append-only
│   └── src/main/resources/
│       ├── db/migration/    # Flyway SQL migrations
│       └── i18n/            # Đa ngôn ngữ (validation & message)
├── frontend/                 # Ứng dụng React TypeScript + Tailwind CSS
│   ├── app/
│   │   ├── routes/          # Các trang theo React Router
│   │   ├── features/        # Chức năng người dùng cụ thể
│   │   ├── entities/        # Thực thể nghiệp vụ (listing, user, lead)
│   │   ├── shared/          # UI components, API client, utils
│   │   └── styles/          # Tailwind CSS & theme tokens
├── infra/                    # Cấu hình Docker Compose (Postgres, Redis, App)
├── design app/               # 14 màn hình UI prototype Mobile & Design System
├── Ke_hoach_du_an_website_BDS_Waterfall.md # Hồ sơ SRS & Kế hoạch Waterfall (124 mục)
├── PROJECT_CODE_RULES_BDS.md # Bộ quy tắc phát triển mã nguồn bắt buộc
├── TRACEABILITY_MATRIX_G3_G4.md     # Ma trận truy vết 32 FR & 8 UC (100% Pass)
├── SECURITY_AND_PII_AUDIT_REPORT.md  # Báo cáo an toàn thông tin, bảo vệ PII & SLA Escrow
├── IMPLEMENTATION_PLANS_HISTORY.md   # Lịch sử toàn bộ kế hoạch triển khai (9 giai đoạn)
├── WALKTHROUGHS_HISTORY.md           # Lịch sử báo cáo nghiệm thu & video demo E2E tour
├── docker-compose.yml                # Cụm 5 dịch vụ: PostGIS, Redis, MinIO, Backend, Frontend
└── .env.example                      # Biến môi trường mẫu
```

---

## 3. Khởi động bản demo an toàn

Bản mặc định là **DEMO**: chỉ dùng dữ liệu giả, eKYC và giao dịch tiền thật bị khóa.

```powershell
Copy-Item .env.demo.example .env
powershell -ExecutionPolicy Bypass -File scripts/preflight.ps1
docker compose up --build -d
powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
```
- **Giao diện người dùng (Frontend SPA):** `http://localhost:3000`
- **Health Check qua frontend:** `http://localhost:3000/healthz`
- **Hộp thư email demo (Mailpit):** `http://localhost:8025`

Cloudflare Tunnel không chạy trong cấu hình demo mặc định. Khi triển khai domain, sao chép
`.env.production.example` thành `.env`, điền secret thật rồi chạy:

```powershell
docker compose --profile edge up --build -d
```
- **Health Check backend qua gateway:** `http://localhost:3000/backend-health`
- **MinIO Console (chỉ localhost):** `http://localhost:9001`; bucket mặc định `bds-listings`.

Ảnh tin đăng được kiểm tra magic bytes (JPEG/PNG/WebP/AVIF), giới hạn 10 MB/ảnh và 20 ảnh/tin. Object nằm trong volume `minio-data`; PostgreSQL chỉ lưu metadata và URL `/api/v1/public/media/{objectKey}`. Không mở cổng S3 `9000` ra host. Image MinIO được build từ source release vá bảo mật `RELEASE.2025-10-15T17-29-55Z` vì registry community không phát hành binary cho release này.

Reset toàn bộ dữ liệu demo bằng `powershell -ExecutionPolicy Bypass -File scripts/reset-demo.ps1 -ConfirmReset`. Script từ chối chạy khi `APP_MODE=production`.

Tài khoản demo được seed ở backend: `demo.user@bds.local`, `demo.broker@bds.local`, `demo.moderator@bds.local`, `demo.admin@bds.local`; mật khẩu lấy từ `DEMO_ACCOUNT_PASSWORD`. Không dùng dữ liệu thật. Production phải đặt `APP_MODE=production`, cấp hai khóa PII Base64 32 byte từ secret manager, tắt Swagger và không bật provider flag nếu chưa tích hợp nhà cung cấp được cấp phép.

---

## 4. Khởi động môi trường phát triển cục bộ (Local Development)

### Yêu cầu tiên quyết
- **Node.js**: >= 20.x
- **Java JDK**: 17 hoặc 21 (Hiện tại: JDK 17)
- **Maven**: 3.9+ hoặc sử dụng Maven Wrapper
- **Docker & Docker Compose**: Dùng để chạy PostgreSQL PostGIS & Redis

### Khởi động hạ tầng nền tảng (Postgres + PostGIS & Redis)
```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d postgres redis
```

### Khởi chạy Backend (Spring Boot)
```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; $env:PATH = "C:\Program Files\Java\jdk-17\bin;C:\Program Files\Java\apache-maven-3.9.14\bin;$env:PATH"
cd backend
mvn test                                # Chạy kiểm thử tự động (19/19 tests pass)
java -jar target/bds-backend-0.0.1-SNAPSHOT.jar # Chạy server trên cổng 8080
```

### Khởi chạy Frontend (React Vite)
```bash
cd frontend
npm ci
npm run build                           # Kiểm tra TypeScript strict & đóng gói
npm run dev                             # Chạy dev server tại http://localhost:3000
```

---

## 5. Quy ước bắt buộc & Bộ nhớ Agent
Mọi lập trình viên và AI Agent tham gia dự án **BẮT BUỘC** tuân thủ các quy tắc trong:
- [PROJECT_CODE_RULES_BDS.md](file:///d:/B%E1%BA%A5t%20%C4%91%E1%BB%99ngS%E1%BA%A3n/PROJECT_CODE_RULES_BDS.md) (Kiến trúc Modular Monolith, bất biến ContentRevision, bảo mật PII).
- [AGENTS.md](file:///d:/B%E1%BA%A5t%20%C4%91%E1%BB%99ngS%E1%BA%A3n/AGENTS.md) (Quy tắc đồng bộ lịch sử triển khai và nghiệm thu bộ nhớ kiên trì).
