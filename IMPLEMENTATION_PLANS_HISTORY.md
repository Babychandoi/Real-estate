# Lịch Sử Toàn Bộ Các Kế Hoạch Triển Khai (Implementation Plans History)
## Dự án Nền tảng Bất Động Sản Minh Bạch (BDS WF 2026)
### Tiêu chuẩn: Waterfall SRS 0.9.1 • Kiến trúc: Modular Monolith (Spring Boot 3 & React TypeScript)

---

Tài liệu này tổng hợp toàn bộ các **Kế hoạch triển khai kỹ thuật (Implementation Plans)** từ giai đoạn khởi động dự án đến khi hoàn tất toàn diện 32 Yêu cầu Chức năng (FR01 – FR32) và 8 Nhóm Use Case (UC01 – UC08).

---

# MỤC LỤC CÁC GIAI ĐOẠN TRIỂN KHAI

1. [Đợt 1: Khởi Tạo Kiến Trúc Nền Tảng & Quản Lý Vòng Đời Tin Đăng (FR04 – FR06, UC02)](#đợt-1-khởi-tạo-kiến-trúc-nền-tảng--quản-lý-vòng-đời-tin-đăng-fr04--fr06-uc02)
2. [Đợt 2: Bàn Kiểm Duyệt Tin Đăng & Đối Chiếu Diff Thẩm Định (FR06 – FR09, UC03)](#đợt-2-bàn-kiểm-duyệt-tin-đăng--đối-chiếu-diff-thẩm-định-fr06--fr09-uc03)
3. [Đợt 3: Tìm Kiếm Bản Đồ Split-Screen & Bộ Lọc Không Gian PostGIS (FR11 – FR14, UC01)](#đợt-3-tìm-kiếm-bản-đồ-split-screen--bộ-lọc-không-gian-postgis-fr11--fr14-uc01)
4. [Đợt 4: Mạng Lưới Môi Giới & Hộp Tiếp Nhận Lead / Báo Xấu SLA 24h (FR24 – FR27, FR31, UC04)](#đợt-4-mạng-lưới-môi-giới--hộp-tiếp-nhận-lead--báo-xấu-sla-24h-fr24--fr27-fr31-uc04)
5. [Đợt 5: Xác Thực Định Danh eKYC & Gắn Nhãn Tin Chính Chủ (FR01, FR03, NFR12)](#đợt-5-xác-thực-định-danh-ekyc--gắn-nhãn-tin-chính-chủ-fr01-fr03-nfr12)
6. [Đợt 6: Ký Số Hợp Đồng Cọc Escrow, Wizard Đăng Tin AI & Broker Workspace CRM (FR28, FR30, FR22, FR23, FR10, BR12)](#đợt-6-ký-số-hợp-đồng-cọc-escrow-wizard-đăng-tin-ai--broker-workspace-crm-fr28-fr30-fr22-fr23-fr10-br12)
7. [Đợt 7: So Sánh BĐS Chuyên Sâu, Báo Cáo Phễu Lead FR29 & Quản Lý Danh Mục Dự Án FR25 (FR15 – FR17, FR29, FR25, UC05, UC08)](#đợt-7-so-sánh-bđs-chuyên-sâu-báo-cáo-phễu-lead-fr29--quản-lý-danh-mục-dự-án-fr25-fr15--fr17-fr29-fr25-uc05-uc08)
8. [Đợt 8: Biên Tập & Xuất Bản CMS Bài Viết Pháp Lý FR32 & Modal Tư Vấn OTP FR18/FR20 (FR24, FR32, FR18, FR20, UC04, UC07)](#đợt-8-biên-tập--xuất-bản-cms-bài-viết-pháp-lý-fr32--modal-tư-vấn-otp-fr18fr20-fr24-fr32-fr18-fr20-uc04-uc07)
9. [Đợt 9: Docker Hóa & Triển Khai Đóng Gói Hạ Tầng (NFR01, NFR06)](#đợt-9-docker-hóa--triển-khai-đóng-gói-hạ-tầng-nfr01-nfr06)
10. [Đợt 10: Refactor Hệ Thống Xác Thực & Bảo Mật RBAC (FR02, NFR12)](#đợt-10-refactor-hệ-thống-xác-thực--bảo-mật-rbac-fr02-nfr12)

---

## Đợt 1: Khởi Tạo Kiến Trúc Nền Tảng & Quản Lý Vòng Đời Tin Đăng (FR04 – FR06, UC02)

### 1. Mục tiêu và Phạm vi
- Xây dựng bộ khung nền tảng **Modular Monolith** chuẩn mực theo [PROJECT_CODE_RULES_BDS.md](file:///d:/B%E1%BA%A5t%20%C4%91%E1%BB%99ngS%E1%BA%A3n/PROJECT_CODE_RULES_BDS.md).
- Triển khai phân hệ Tin đăng cơ bản tuân thủ quy tắc bất biến `ContentRevision` (ERD04/ED04): Khi tin đã nộp duyệt hoặc công khai, người đăng sửa nội dung thì hệ thống tự động sinh bản nháp Revision mới, không làm thay đổi bản ghi đang hiển thị.

### 2. Kế hoạch Kỹ thuật
- **Database (Flyway `V001__init_schema.sql`, `V002__create_listing_media.sql`)**:
  - Bảng `listings`: Quản lý thực thể tin gốc, phân vùng vị trí, mã BĐS, trạng thái hiển thị (`DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `EXPIRED`, `ARCHIVED`).
  - Bảng `listing_revisions`: Quản lý các phiên bản nội dung bất biến (`revision_number`, `title`, `price_vnd`, `area_m2`, `description`, `status`: `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`).
  - Bảng `listing_media`: Quản lý hình ảnh, ảnh bìa primary, watermark.
- **Backend (`com.company.bds.listing`)**:
  - Domain Model: `Listing.java`, `ListingRevision.java`, `ListingStatus.java`, `RevisionStatus.java`.
  - Persistence: `ListingJpaEntity`, `ListingRevisionJpaEntity`, Hexagonal Ports & Adapters.
  - Application Service: `ListingApplicationService` xử lý `createDraft`, `updateDraft`, `submitListing`.
  - API Controller: `ListingController` (`POST /api/v1/listings`, `PUT .../draft`, `POST .../submit`, `GET .../{id}`).
- **Frontend**:
  - Khởi tạo React 18, Vite, TypeScript Strict, Tailwind CSS & daisyUI.
  - Route: `_public.home.tsx` (Trang chủ), `_account.listings.tsx` (Kho tin của tôi).
- **Kiểm thử**:
  - Viết `listingRevisionLifecycle_fullFlow()` trong `BdsApplicationTests.java`.

---

## Đợt 2: Bàn Kiểm Duyệt Tin Đăng & Đối Chiếu Diff Thẩm Định (FR06 – FR09, UC03)

### 1. Mục tiêu và Phạm vi
- Căn cứ nguyên mẫu: `design web desktop/b_n_ki_m_duy_t_i_chi_u_diff_th_m_nh_desktop`.
- Xây dựng bàn làm việc cho Đội kiểm duyệt nội dung (Moderation Team): hàng đợi tin đăng, chế độ Split Diff View so sánh thay đổi giữa bản gốc và bản đề xuất, từ chối kèm lý do, cam kết SLA 8 giờ.

### 2. Kế hoạch Kỹ thuật
- **Backend (`com.company.bds.moderation`)**:
  - Application: `ModerationApplicationService` phụ trách lấy hàng đợi `getPendingQueue`, đối chiếu `getRevisionDiff`, phê duyệt `approveRevision`, từ chối `rejectRevision`.
  - API Controller: `ModerationController` (`GET /api/v1/moderation/queue`, `POST .../approve`, `POST .../reject`, `GET .../diff`).
- **Frontend**:
  - Route: `frontend/app/routes/_admin.moderation.tsx`.
  - Giao diện 2 cột Diff View làm nổi bật sự thay đổi giá, diện tích, nội dung mô tả, danh mục ảnh.
  - Modal từ chối duyệt kèm lý do vi phạm (Spam, sai vị trí, giá ảo).
- **Kiểm thử**:
  - Viết `moderationWorkflow_queueDiffApproveReject()` trong `BdsApplicationTests.java`.

---

## Đợt 3: Tìm Kiếm Bản Đồ Split-Screen & Bộ Lọc Không Gian PostGIS (FR11 – FR14, UC01)

### 1. Mục tiêu và Phạm vi
- Căn cứ nguyên mẫu: `design web desktop/t_m_ki_m_b_n_t_ng_t_c_split_screen_desktop`.
- Tìm kiếm bất động sản theo tọa độ địa lý không gian, vẽ khoanh vùng bản đồ (Polygon/Bounding Box), đồng bộ danh sách thẻ tin và cụm điểm ghim trên bản đồ (Marker Clustering).

### 2. Kế hoạch Kỹ thuật
- **Backend Database & Service (`com.company.bds.search`)**:
  - Kích hoạt PostGIS extension: `GEOMETRY(Point, 4326)`.
  - Truy vấn không gian: `ST_MakeEnvelope`, `ST_DWithin`, tính khoảng cách và lọc theo tiện ích (trường học, bệnh viện, ga metro).
  - API: `GET /api/v1/public/listings/search-map`.
- **Frontend**:
  - Route: `frontend/app/routes/_public.search.tsx`.
  - Tích hợp thư viện Leaflet Map tương tác mượt mà: Pan/Zoom tự động load lại tin trong khung nhìn, bộ lọc nhanh giá bán/thuê, loại hình BĐS, số phòng ngủ, hướng nhà.
- **Kiểm thử**:
  - Viết `gisSearchAndFilter_postGisBoundingBox_flow()` trong `BdsApplicationTests.java`.

---

## Đợt 4: Mạng Lưới Môi Giới & Hộp Tiếp Nhận Lead / Báo Xấu SLA 24h (FR24 – FR27, FR31, UC04)

### 1. Mục tiêu và Phạm vi
- Căn cứ nguyên mẫu: `design web desktop/h_p_ti_p_nh_n_lead_x_l_b_o_x_u_desktop`.
- Xây dựng trung tâm tiếp nhận yêu cầu liên hệ từ khách mua (Lead) và tiếp nhận báo cáo tin đăng vi phạm/tin giả (Listing Report) với cơ chế leo thang SLA 24h tự động ẩn tin vi phạm.

### 2. Kế hoạch Kỹ thuật
- **Database (Flyway `V003__create_listing_reports.sql`)**:
  - Bảng `listing_reports`: lưu loại vi phạm (`FAKE_PRICE`, `DUPLICATE`, `WRONG_LOCATION`, `SOLD`), trạng thái xử lý và thời hạn SLA 24h.
- **Backend (`com.company.bds.lead`)**:
  - Service: `LeadReportApplicationService` phân loại báo xấu, cập nhật trạng thái tin đăng.
  - Controller: `LeadReportController` (`POST /api/v1/reports`, `GET /api/v1/reports/queue`, `POST .../resolve`).
- **Frontend**:
  - Route: `frontend/app/routes/_admin.leads-and-reports.tsx`.
  - Giao diện quản trị 2 phân hệ tab: Hộp Lead Khách Hàng và Hàng Đợi Báo Xấu Vi Phạm kèm cảnh báo quá hạn SLA.
- **Kiểm thử**:
  - Viết `leadsAndReports_leadLifecycle_and_reportSlaEscalate()`.

---

## Đợt 5: Xác Thực Định Danh eKYC & Gắn Nhãn Tin Chính Chủ (FR01, FR03, NFR12)

### 1. Mục tiêu và Phạm vi
- Căn cứ nguyên mẫu: `design web desktop/th_m_nh_tin_ch_nh_ch_desktop`.
- Triển khai quy trình cấp nhãn "Chính chủ đã xác thực eKYC" thông qua đối soát ảnh CCCD gắn chip (OCR) và số hiệu Giấy chứng nhận quyền sử dụng đất (Sổ hồng/Sổ đỏ). Bảo vệ thông tin cá nhân PII (NFR12).

### 2. Kế hoạch Kỹ thuật
- **Database (Flyway `V004__create_verification_tables.sql`)**:
  - Bảng `user_kyc`: Lưu thông tin định danh người dùng được che mờ PII (`001****4567`).
  - Bảng `listing_verifications`: Lưu số hiệu GCNQSDĐ, cơ quan cấp, ảnh giấy tờ, trạng thái thẩm định.
- **Backend (`com.company.bds.verification`)**:
  - Service: `VerificationApplicationService` hỗ trợ nộp hồ sơ eKYC, đối soát OCR, phê duyệt cấp nhãn `CERTIFIED_OWNER`.
  - Controller: `VerificationController` (`POST /api/v1/kyc/submit`, `POST /api/v1/verifications/submit`, `POST .../approve`, `POST .../reject`).
- **Frontend**:
  - Route: `frontend/app/routes/_admin.verification.tsx`.
  - Bàn thẩm định hồ sơ: So sánh ảnh CCCD và Sổ hồng, kiểm tra tính khớp nối họ tên, cấp nhãn tin chính chủ thời gian thực.
- **Kiểm thử**:
  - Viết `ekycVerificationAndCertifiedOwner_flow()`.

---

## Đợt 6: Ký Số Hợp Đồng Cọc Escrow, Wizard Đăng Tin AI & Broker Workspace CRM (FR28, FR30, FR22, FR23, FR10, BR12)

### 1. Mục tiêu và Phạm vi
- Triển khai cùng lúc 3 phân hệ liên kết trọng điểm:
  1. **Hợp đồng Đặt cọc Escrow Vault (FR28, FR30, UC05)**: Ký số OTP 2 bên mua/bán, phong tỏa tiền cọc trong két ký quỹ Escrow bảo đảm, tự động giải ngân sau công chứng.
  2. **Wizard Đăng tin 4 bước AI (UC02, FR22, FR23)**: Tích hợp AI Price Estimator gợi ý giá thị trường và Quality Score Engine chấm điểm tin đăng 0 - 100.
  3. **Broker Workspace CRM (BR12, FR10, FR21)**: Bảng điều khiển môi giới Pro Agent, quản lý kho tin, gia hạn tin 30 ngày (FR10), pipeline khách nét.

### 2. Kế hoạch Kỹ thuật
- **Database (Flyway `V005__create_deposit_and_escrow_tables.sql`)**:
  - Bảng `deposit_contracts` và `escrow_transactions`.
- **Backend (`com.company.bds.transaction` & `com.company.bds.listing`)**:
  - Thực thể `DepositContract`, `EscrowTransaction`, vòng đời trạng thái: `DRAFT` $\rightarrow$ `AWAITING_SELLER_SIGN` $\rightarrow$ `ESCROW_LOCKED` $\rightarrow$ `COMPLETED` / `REFUNDED`.
  - Thuật toán AI: `estimatePrice` biên độ giá 94% độ tin cậy; `calculateQualityScore` kiểm tra 5 tiêu chí chất lượng tin.
- **Frontend**:
  - Route `_account.contracts.tsx`: Bàn Ký số OTP và Két phong tỏa Escrow Vault.
  - Route `_public.listings.new.tsx`: Wizard 4 bước đăng tin hiện đại.
  - Route `_account.broker-workspace.tsx`: Không gian làm việc Môi giới CRM theo mẫu desktop.
- **Kiểm thử**:
  - Viết `depositContract_fullLifecycle_escrowVault()` và `listingWizard_estimatePrice_and_qualityScore_flow()`.

---

## Đợt 7: So Sánh BĐS Chuyên Sâu, Báo Cáo Phễu Lead FR29 & Quản Lý Danh Mục Dự Án FR25 (FR15 – FR17, FR29, FR25, UC05, UC08)

### 1. Mục tiêu và Phạm vi
- Triển khai cùng lúc 3 phân hệ nghiệp vụ đối chiếu:
  1. **So Sánh Đối Chiếu BĐS Chuyên Sâu (FR15 – FR17, UC05)**: Ma trận 4 nhóm thông số (Cốt lõi, Pháp lý eKYC, Tài chính & Cọc Escrow, Tiện ích GIS), Diff Mode, khóa FR17 cấm so mua với thuê.
  2. **Báo Cáo Phễu Chuyển Đổi Lead (FR29)**: Mô hình phễu 5 tầng, đo lường SLA kiểm duyệt 4.2h/8h, lọc bot 100%, tỷ lệ nguồn cung eKYC vs Pro Agent.
  3. **Quản Lý Danh Mục Dự Án BĐS (FR25, UC08)**: Quản lý thực thể dự án nguồn, đối soát GPXD & Quy hoạch 1/500, quy trình duyệt Revision độc lập của BTV (UC08.1).

### 2. Kế hoạch Kỹ thuật
- **Database (Flyway `V006__create_project_catalog_tables.sql`)**:
  - Bảng `projects`: Mã dự án, CĐT, tọa độ GIS, số GPXD, quyết định quy hoạch 1/500, quy mô ha, số tháp, số căn.
- **Backend (`com.company.bds.catalog` & `com.company.bds.lead`)**:
  - Module `catalog`: `Project`, `ProjectStatus` (`SELLING`, `UPCOMING`, `DELIVERED`), API `GET/POST /api/v1/catalog/projects`.
  - Endpoint `GET /api/v1/analytics/overview` trả về DTO `ProductAnalyticsOverviewResponse`.
- **Frontend**:
  - Route `_public.compare.tsx`: Ma trận so sánh 3 BĐS trực quan.
  - Route `_admin.analytics.tsx`: Bento Dashboard phân tích phễu chuyển đổi.
  - Route `_admin.projects.tsx`: Màn hình Quản lý danh mục dự án BĐS theo nguyên mẫu app.
- **Kiểm thử**:
  - Viết `analyticsOverview_metrics_flow()` và `projectCatalog_createAndQuery_flow()`.

---

## Đợt 8: Biên Tập & Xuất Bản CMS Bài Viết Pháp Lý FR32 & Modal Tư Vấn OTP FR18/FR20 (FR24, FR32, FR18, FR20, UC04, UC07)

### 1. Mục tiêu và Phạm vi
- Hoàn thiện 2 mảnh ghép cuối cùng của hệ thống:
  1. **Biên Tập & Xuất Bản CMS Chính Sách & Cẩm Nang (FR24, FR32, UC07)**: Quản lý ContentRevision bất biến, phân quyền kiểm duyệt độc lập: BTV soạn thảo $\rightarrow$ Admin phê duyệt xuất bản, kiểm tra Clean HTML Anti-XSS, cấu hình SEO (FR26).
  2. **Modal Gửi Yêu Cầu Tư Vấn & Xác Minh OTP Khách Hàng (FR18, FR20, UC04)**: Tích hợp trực tiếp vào màn hình chi tiết tin đăng, xác thực OTP 4 số chống spam bot (NFR06), mã hóa PII AES-256.

### 2. Kế hoạch Kỹ thuật
- **Database (Flyway `V007__create_cms_article_tables.sql`)**:
  - Bảng `cms_articles` và `cms_article_revisions`.
- **Backend (`com.company.bds.cms`)**:
  - Thực thể `Article`, `ArticleRevision`, `ArticleCategory` (`LEGAL_POLICY`, `KNOWLEDGE`, `MARKET_INSIGHTS`), `ArticleStatus` (`DRAFT`, `SUBMITTED`, `PUBLISHED`, `ARCHIVED`, `REJECTED`).
  - Dịch vụ `CmsArticleApplicationService` xử lý nộp duyệt, phê duyệt xuất bản, từ chối kèm lý do.
  - API Controller: `CmsArticleController` (`/api/v1/cms/articles/**`, `/api/v1/public/articles/**`).
- **Frontend**:
  - Route `_admin.cms.tsx`: Màn hình quản trị CMS Bài viết & Chính sách pháp lý.
  - Component `LeadConsultationModal.tsx` tích hợp vào `_public.listings.$listingId.tsx`.
- **Kiểm thử**:
  - Viết `cmsArticle_revisionLifecycle_and_approval_flow()`.

---

## Đợt 9: Hoàn Thiện Nghiệm Thu Cổng G3/G4, E2E Demo Tour, Swagger API & Docker Compose

### 1. Mục tiêu và Phạm vi
- Chuyển giao toàn diện hệ thống từ **Cổng G3 (Mã nguồn hoàn tất)** sang **Cổng G4 (Kiểm thử Chấp nhận UAT)**.
- Triển khai cùng lúc 3 trụ cột đảm bảo tính chỉn chu và sẵn sàng triển khai thực tế (Production Readiness):
  1. **Hướng 1 (E2E Visual Tour)**: Khởi chạy môi trường tích hợp và sử dụng Browser Subagent duyệt toàn bộ các luồng màn hình chính, ghi hình video demo.
  2. **Hướng 2 (Traceability & Security Audit)**: Thiết lập Ma trận truy vết 32 FR, 12 NFR, 8 UC và Báo cáo kiểm toán bảo mật PII, Clean HTML, SLA.
  3. **Hướng 3 (Production Packaging)**: Kích hoạt Swagger UI tương tác (`/swagger-ui.html`) và đóng gói `docker-compose.yml` gốc cho toàn bộ 4 cụm dịch vụ (PostGIS, Redis, Spring Boot Backend, Nginx Frontend).

### 2. Kế hoạch Kỹ thuật
- **Swagger / OpenAPI 3.0**:
  - Bổ sung `springdoc-openapi-starter-webmvc-ui` vào `backend/pom.xml`.
  - Cập nhật Whitelist trong `SecurityConfig.java`: `/swagger-ui/**`, `/v3/api-docs/**`.
- **Docker Compose Root Deployment**:
  - Tạo `docker-compose.yml` tại thư mục gốc ánh xạ cổng 8080 (API) và 3000 (Web).
- **Traceability & Audit Documents**:
  - `TRACEABILITY_MATRIX_G3_G4.md`: Bảng ánh xạ 32 FR & 8 UC sang Code, DB, Route.
  - `SECURITY_AND_PII_AUDIT_REPORT.md`: Báo cáo rà soát PII, kiểm toán Escrow và SLA.
- **Visual E2E Verification**:
  - Chạy frontend và backend, tương tác qua Browser Agent để ghi video demo toàn hệ thống.

---

## Đợt 9: Docker Hóa & Triển Khai Đóng Gói Hạ Tầng (NFR01, NFR06)

### 1. Mục tiêu và Phạm vi
- Đóng gói toàn bộ hệ thống (PostgreSQL + PostGIS, Redis, Backend Spring Boot, Frontend Nginx) thành cụm Docker Compose, đảm bảo triển khai bằng 1 lệnh `docker compose up -d`.
- Tuân thủ NFR01 (Khả năng triển khai) và NFR06 (Tính sẵn sàng hạ tầng).

### 2. Kế hoạch Kỹ thuật
- **docker-compose.yml**: Định nghĩa 4 dịch vụ (postgres, redis, backend, frontend).
- **backend/Dockerfile**: Multi-stage build Maven → OpenJDK 17 slim runtime.
- **frontend/Dockerfile**: Multi-stage build Node → Nginx alpine.
- **frontend/nginx.conf**: Cấu hình SPA routing, proxy pass API `/api/` tới backend.

---

## Đợt 10: Refactor Hệ Thống Xác Thực & Bảo Mật RBAC (FR02, NFR12)

### 1. Mục tiêu và Phạm vi
- **Loại bỏ lỗ hổng bảo mật** khi LoginModal hiển thị danh sách đầy đủ vai trò hệ thống (ADMIN, MODERATOR, BROKER, USER) cho người dùng tự chọn.
- **Nguyên tắc nghiệp vụ mới:**
  - Đăng nhập: Chỉ cần Email + Mật khẩu. Backend xác định role dựa trên tài khoản trong CSDL.
  - Đăng ký: Chỉ cho phép 2 loại: **Người tìm nhà (USER)** hoặc **Môi giới BĐS (BROKER)**.
  - Vai trò **ADMIN** và **MODERATOR**: Do Super Admin tạo trong khu quản trị nội bộ, không hiển thị cho người dùng cuối.
- **Che giấu thông tin RBAC** trên trang 403 (ProtectedRoute) — không lộ danh sách roles cho phép.

### 2. Kế hoạch Kỹ thuật

#### [MODIFY] [AuthContext.tsx](file:///d:/Bất%20độngSản/frontend/app/shared/auth/AuthContext.tsx)
- Đổi `login(role, email?)` → `login(email, password)` trả về `Promise<{ success, error? }>`.
- Thêm `register(email, password, name, accountType: 'BROKER' | 'USER')`.
- Giả lập SEED_ACCOUNTS (admin, moderator, broker, user) để test.
- Lưu tài khoản đăng ký mới vào localStorage (chờ tích hợp API thật).

#### [MODIFY] [LoginModal.tsx](file:///d:/Bất%20độngSản/frontend/app/shared/auth/LoginModal.tsx)
- Bỏ hoàn toàn grid chọn 4 vai trò.
- Thêm tab Đăng nhập / Đăng ký.
- Tab Đăng nhập: Chỉ có Email + Mật khẩu + nút toggle hiện mật khẩu.
- Tab Đăng ký: Chọn "Người tìm nhà" hoặc "Môi giới BĐS" + Họ tên + Email + Mật khẩu.
- Hiển thị lỗi validation (mật khẩu không khớp, email đã tồn tại...).
- Dev mode hint (collapsed) với danh sách tài khoản demo.

#### [MODIFY] [ProtectedRoute.tsx](file:///d:/Bất%20độngSản/frontend/app/shared/auth/ProtectedRoute.tsx)
- Phân biệt 2 trạng thái: Chưa đăng nhập vs Đã đăng nhập nhưng không đủ quyền.
- **Không hiển thị** danh sách `allowedRoles` (ADMIN/MODERATOR) cho khách.
- Thay vào đó: Thông báo "Liên hệ quản trị viên để được cấp quyền."

#### [MODIFY] [root.tsx](file:///d:/Bất%20độngSản/frontend/app/root.tsx)
- Bỏ tooltip "Bấm để đổi vai trò thử nghiệm" trên avatar người dùng.
- Ẩn raw role enum `{user?.role}` khỏi admin dropdown header.

### 3. Ràng buộc Nghiệp vụ
- Admin/Moderator **KHÔNG BAO GIỜ** xuất hiện dưới dạng tùy chọn trong bất kỳ UI nào dành cho người dùng cuối.
- Đăng ký chỉ mở cho USER và BROKER.
- Backend sau khi tích hợp API thật sẽ trả về JWT token kèm role.

### 4. Kiểm thử
- `tsc -b --noEmit`: 0 errors.
- `npm run build`: Build thành công, 0 errors.
- Kiểm thử trực quan: Login Modal không còn hiển thị 4 nút chọn vai trò.
- ProtectedRoute hiện thông báo phù hợp, không lộ thông tin RBAC.

