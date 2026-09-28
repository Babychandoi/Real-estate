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

---

## Đợt 11 — Khắc phục tổng thể 5 báo cáo review (SEC, UX, Packaging, Scalability)

- Căn cứ: `01_USER_USABILITY_SETUP_REVIEW.md` đến `05_UI_UX_DESIGN_REVIEW.md` và thiết kế Modern Trust Real Estate.
- Backend: migration V008 cho session/audit/outbox; xác thực opaque token + BCrypt; RBAC deny-by-default; ownership/participant guard; AES-GCM/HMAC PII; rate limit; feature gate eKYC/giao dịch; metrics/probes.
- Frontend: auth API thật, lead contract thật, bỏ false-success/mock runtime, protected routes, search URL, mobile navigation, accessibility dialog/focus, copy minh bạch, route-level lazy loading.
- Vận hành: Docker hardened, secret bắt buộc, CI/audit/Trivy/Dependabot, Prometheus, k6, Kubernetes HPA/PDB, runbook/SLO/security/support.
- Ràng buộc: eKYC/escrow/payment không được bật nếu chưa có provider và nghiệm thu; production phải fail-fast khi secret/PII key không đạt yêu cầu.
- Kế hoạch kiểm thử: Maven/JUnit/MockMvc, TypeScript + Vite build, npm audit, Compose config, Impeccable detector và browser visual khi phiên trình duyệt khả dụng.

---

## Đợt 12 — Chốt tính đúng, chống phát lại và nghiệm thu Docker thật

- Bổ sung MFA TOTP cho ADMIN/MODERATOR ở production; production fail-fast khi thiếu MFA, secret Redis/DB, HTTPS CORS, CDN riêng hoặc cấu hình outbox không an toàn.
- Thêm idempotency cho public lead, outbox với lease token/fenced finalize, pagination và giới hạn kích thước trang trên các danh sách quản trị.
- Chặn IDOR lead/eKYC/CMS/verification/transaction theo actor hiện tại; không nhận `userId` hay danh tính người duyệt từ payload.
- Chính sách media chỉ cho URL HTTPS thuộc allowlist, giới hạn số ảnh và chặn host minh họa khi production.
- Thêm preflight, reset demo có khóa chống chạy production, readiness backend qua gateway và smoke test chạy trên cùng cổng public.
- Kế hoạch xác minh: 17 test Spring/MockMvc, Vite build, npm audit, Docker build, Flyway PostgreSQL 16 và smoke readiness/frontend/search.

---

## Đợt 13 — Lưu ảnh tin đăng trên MinIO

- Thêm MinIO vào hai Compose, bucket riêng tư có volume bền vững, console chỉ bind localhost và backend đợi storage healthy.
- Thêm migration V011 lưu metadata object; API upload/read/delete, giới hạn 10 MB, kiểm tra magic bytes JPEG/PNG/WebP/AVIF và tác vụ dọn object mồ côi sau 24 giờ.
- Giao diện tạo tin upload multipart trực tiếp, hiển thị tiến trình/xóa/preview và chỉ đưa URL nội bộ đã upload vào revision.
- Chặn IDOR bằng cách xác minh object thuộc chính actor khi tạo/cập nhật tin; object đã gắn lịch sử revision không bị xóa vật lý.
- Production fail-fast nếu storage hoặc credential/bucket không hợp lệ; preflight và CI được bổ sung biến MinIO.
- Kế hoạch xác minh: 19 test backend, frontend build/audit, Compose config, Docker runtime PostgreSQL 16 + MinIO, upload/read/listing/ownership/security smoke.
# 2026-09-12 - Manual trust, package payment and search hardening

- Replace provider-gated eKYC with an explicitly manual CCCD front/back/selfie review flow backed by private MinIO objects and admin decisions.
- Add listing packages, quota ledger, VietQR manual payment intents, admin bank configuration, idempotent reconciliation, invoices, durable notifications, SSE account refresh and admin email delivery.
- Replace broker demo-state widgets with persisted listing/lead metrics, notifications and configurable SLA data; remove escrow/buying entry points because the product only connects parties.
- Add MapLibre map clustering, geocoding with caching/rate limits, and bounding-box “search this area”.
- Add Elasticsearch-backed public listing search with PostgreSQL fallback and synchronization after moderation.
- Add fail-closed ClamAV INSTREAM scanning before any object reaches MinIO.
- Verify backend tests, frontend type/build, Compose configuration and live Docker health/smoke; record exact results in WALKTHROUGHS_HISTORY.md.
# 2026-09-12 - Release assurance and production infrastructure readiness

- Add Playwright screenshot regression, axe WCAG checks, and responsive/device projects spanning 320-1440 px with Chromium, Firefox and WebKit.
- Add a repeatable real-device/usability protocol; do not substitute emulation for signed human/device evidence.
- Add non-destructive production overlays for PgBouncer, TLS ingress and Docker secrets, plus HA deployment manifests/runbooks for PostgreSQL, Redis and MinIO.
- Add k6 smoke/load/soak profiles with thresholds and machine-readable summaries.
- Add PostgreSQL/MinIO/Redis backup and isolated restore-drill automation that measures achieved RPO/RTO without touching live volumes.

# 2026-09-12 - Local Kubernetes HA deployment

- Create a three-node kind cluster without removing the current Docker volumes.
- Deploy Redis primary/replicas with Sentinel quorum, four-node distributed MinIO, Vault HA Raft, ingress/TLS and local DNS endpoint.
- Build/load application images, deploy the BDS workloads, then verify readiness, failover topology and external HTTPS routing.
- Keep public CDN and authoritative DNS provider activation explicit because it requires a domain/provider account; provide deployable integration configuration without claiming an external service exists.

# 2026-09-26 - Local Docker deployment on Apple Silicon

- Run the full root Compose stack locally in demo mode with `--env-file .env.demo.example`, leaving the production `.env` untouched.
- Replace the MinIO runtime base (`quay.io/minio/minio` now returns 401 upstream) with Alpine + curl; the MinIO binary is still built from the patched source release.
- Add `infra/compose.apple-silicon.yaml`: `clamav/clamav-debian:1.4_base` (native arm64, same ClamAV release) and amd64 emulation for `postgis/postgis:16-3.4`.
- Pin `user_kyc_profiles.status` JPA mapping to `varchar(30)` so the H2 test schema matches Flyway V004 instead of generating an ENUM column.
- Verification: backend `mvn verify` inside the Docker build, all containers healthy, health/search/login smoke checks.

# 2026-09-26 - Move production runtime to the macOS host

- Restore `backups/20260926-005833` (Git LFS raw volume snapshots, SHA-256 verified) into an isolated Compose project `bds-production`; the demo project and its volumes are kept but stopped.
- ClamAV signatures are copied from the demo volume instead of restoring the 215 MB archive.
- `infra/cloudflared/config.yml` (gitignored) targets tunnel `046d1ad4-3b90-4fb8-9479-b3b13ecb75b1`; the tunnel is started only after the Windows connector is offline.

# 2026-09-26 - Compare selection, map place search and UAT data seeder

- Compare: replace the auto-picked "first 3 sale listings" with an explicit selection (max 3, same purpose) kept in `features/compare/compareStore.ts`; "+ So sánh" toggles on listing cards and detail page, a floating tray, an in-page listing picker, extended criteria (unit price, rooms, frontage, road, direction, legal) with "Tốt nhất" highlights.
- Map search: combobox suggestions from `/public/geocoding` (keyword search or "go to place"); choosing a place flies/fits the map, drops a marker and searches listings inside the visible area. The map stays mounted while results reload. Geocoding filters to Vietnam, biases to Hanoi, returns `boundingbox`, versioned cache key `v2:`; the per-IP limiter now targets the real `/api/v1/public/geocoding` path.
- `shared/uat/UatDataSeeder`: one-off runner (`--app.uat-seed.mode=seed|purge`) creating fake users, listings in every lifecycle state (incl. revision diffs), leads with real PII encryption, reports, verifications, projects, CMS articles and billing orders. All ids start with `ee5eed`, text keys with `UAT`; configured accounts are referenced, never modified; no bank settings are created.

# 2026-09-28 - Audit 2026-09-27 remediation programme

- Source: `Real-estate_Audit_2026-09-27.md` (22 technical findings F01–F22, route review, product gaps, data/performance design, design system, release conditions).
- Review 1 deliverables in `docs/audit-2026-09-27/`: `00_PLAN.md` (goal, workflow Review→Code→Review→Code→Review, waves), `01_REQUIREMENTS.md` (traceability matrix with acceptance criteria per requirement), `02_CONTRACTS.md` (shared schema V027–V029, durable job queue, mail outbox, analytics catalog, money/trust contracts, search filter schema, search/map/detail API v2, read model + index pipeline, image DTO, notification and frontend contracts, Flyway ranges and ports per stream), `03_AGENT_RULES.md`.
- Execution: streams S0-BE, S0-FE, S1-MEDIA (W1) → S2-SEARCH, S3-SUPPLY, S4-ADMIN (W2) → S5-SEC, S6-ENGAGE, S7-SEO (W3) → S8-ANALYTICS, S9-QUALITY, S10-PERF (W4) → S11-UX (W5); each on `audit/<stream>` in its own worktree, reviewed and merged into `audit-2026-09-27`.
- Shared isolated test infrastructure `infra/test/compose.yaml` (`scripts/test-infra.sh`): PostGIS, Elasticsearch, Redis, MinIO, Mailpit on loopback ports; backend tests move from H2 to PostgreSQL + Flyway.

# 2026-09-28 - Audit W2 plan: search read model/index, supply write path, admin desks (S2-SEARCH, S3a-SUPPLY, S4-ADMIN)

- Requirement IDs: F02–F10 (search/map/detail/cache), F14.1/F14.3/F15.1 (gallery, bundle), P-01/P-04/P-05/P-08/P-11/P-14, D-01–D-11, UI-02–UI-07/UI-11/UI-12/UI-18–UI-23, DS-09/DS-10/DS-12/DS-13 — see `docs/audit-2026-09-27/01_REQUIREMENTS.md` for the per-row status set in this wave.
- Design basis: `02_CONTRACTS.md` §6–9 (money/trust, search filter schema, search/map/detail API v2, read model + index pipeline), each stream's own worktree branch off `audit-2026-09-27` @ `b875b91`.
- **S2-SEARCH** (branch `audit/s2-search`, Flyway V033–V036, backend port 18113): `listing_public_read` JDBC read model (contact-redacted via `bds_redact_contact`), deferred-trigger + `search-index` queue → Elasticsearch with alias-swapped rebuild; `SearchApiController`/`SearchController` v2 (`GET /api/v2/listings/search|map|{slugOrId}`, `GET /api/v2/public/sellers/{id}/listings`), HMAC-signed keyset/`search_after` cursors, ES circuit breaker with DB fallback; frontend `features/search` (SearchPanels lazy chunk), `Gallery`, `ListingCard`/`TrustBadge`, `/search`, `/listings/:slug`, `/compare`, `/nguoi-dang/:sellerId` rewritten on the v2 API.
- **S3a-SUPPLY** (branch `audit/s3a-supply`, Flyway V045–V048, backend port 18114): write-path domain/service changes for rent terms/legal/furnishing/project, `GET /api/v2/me/listings` + `/draft` + `/preview` (optimistic-lock `If-Match`/409), CSV import (`/api/v2/me/listings/import`), OWNER self-service (`POST /api/v1/me/become-owner`), freshness sweep (45-day validity, D7/D2 reminders, sold-check pause) and quality checklist; frontend 4-step wizard `/listings/new` with autosave, `/my-listings` with status tabs and import dialog.
- **S4-ADMIN** (branch `audit/s4-admin`, Flyway V055–V061, backend port 18115): moderation v2 (`/api/v1/moderation/queue` paged, claims, bulk ≤50, four-eyes), duplicate-listing detection (`property_assets`, `listing_fingerprints`, pg_trgm block+similarity), trust decisions (KYC/ownership validity, revoke, four-eyes), reporter-phone encryption at rest, separate report desk with severity SLA, billing idempotency/CAS/reconciliation exceptions (F18.2–F18.4); frontend admin moderation/listings/users/reports/verification/billing pages.
- Business constraints carried into the wave: `410 Gone` listing-detail body key is `listingTitle` (not `title`, which stays the generic RFC 9457 problem title) — `02_CONTRACTS.md` §8 updated to match; sold-check cooldown after an answered report requires ≥2 distinct decrypted reporter phones because S4 encrypts each phone with a random IV (fixed in the integration merge, commit `1dba873`); `/search`, `/billing`, `/kyc` bundle budgets raised to measured + ~10% margin, with the reduction itself left to S10-PERF.
- Test plan executed per stream (backend `mvnw verify` on isolated PostgreSQL/PostGIS/ES/Redis test infra, frontend lint/typecheck/format/vitest/build/`check:bundle`, Playwright E2E on dedicated ports/DB prefixes) and again on the integration branch after merge — see `docs/audit-2026-09-27/streams/{s2-search,s3a-supply,s4-admin}.md` §1 and Review 2 sections, and `WALKTHROUGHS_HISTORY.md` for the merged-branch results.


## CR-UI-2026-09 — Bộ giao diện toàn tuyến (28/09/2026)
- Yêu cầu: bộ mã giao diện hoàn chỉnh cho các trang của sản phẩm hiện tại; dùng design system nhất quán, responsive, thao tác bàn phím và trạng thái dữ liệu rõ ràng.
- Tham chiếu: các prototype trong `design web desktop`, `design app` và báo cáo audit 27/09. Dùng theme navy/teal hiện có, nâng cấp typography, spacing, navigation và component.
- Kiến trúc: frontend React/TypeScript/Tailwind hiện tại; không đổi schema, API, phân quyền hoặc backend. Ngoại lệ SPA hiện hữu được ghi nhận trong ADR của PR, chưa được coi là đã duyệt.
- Phạm vi: shell công khai/tài khoản/quản trị, trang chủ, tìm kiếm URL và phân trang, gallery toàn ảnh, so sánh, chuẩn hóa form/bảng và trạng thái lỗi.
- Kiểm thử: build/typecheck; browser regression với fixture tách khỏi runtime sản phẩm; viewport 360/768/1024/1440, axe và keyboard. Thử backend verify và ghi rõ nếu môi trường chặn.
- Bàn giao: nhánh riêng + draft PR, hướng dẫn chạy và ma trận trang. Không merge/deploy trong đợt này.
- Cập nhật nghiệm thu 28/09: 192/192 test UI đạt trên 4 viewport; bổ sung preview độc lập, đặc tả Figma và ma trận route. Bộ Maven wrapper bị chặn tải dependency do DNS; ghi rõ trong WALKTHROUGHS_HISTORY và docs/ui/QA.md. Giữ visual baseline cũ để review riêng trên UAT.

# 2026-09-28 - Audit W3 plan: media pipeline, leads/appointments, engagement (S1-MEDIA, S3b-LEADS, S6-ENGAGE) + UI merge

- Requirement IDs: F14.2–F14.4, D-09 (media rows), R-3 (media) — S1; F08.3/F08.6, F17.1–F17.4, P-03, P-08 (SLA/ROI), D-12, R-4 (leads), UI-08–UI-10, DS-05/DS-11 (lead pages) — S3b; F12.1/F12.2, P-02, UI-13, F19.1/D-14 (notification events/metrics) — S6. Per-row status in `docs/audit-2026-09-27/01_REQUIREMENTS.md` (`DONE (W3)`/`PARTIAL (W3)`).
- Design basis: `02_CONTRACTS.md` §10 (image DTO, signed URLs) and §11 (notifications); product UI redesign from `origin/feat/ui-product-design-20260928` (`docs/ui/`), re-applied onto the audit logic on `audit/ui-design-merge`. Each stream branched off `audit-2026-09-27` @ `1dba873`.
- **S1-MEDIA** (`audit/s1-media`, Flyway **V085**): `MediaVariantJobHandler` on queue `media-variants` (sanitised upright master ≤2048 px, WebP 320/640/960/1600, dominant colour + LQIP), `VariantPublicImageResolver` (srcset in one query), public-serving policy evaluated per request, HMAC signed URLs (`POST /api/v1/media/signed-urls`, `GET /api/v1/media/signed/**`), admin backfill `/api/v2/admin/media/backfill`; frontend `useSignedMediaUrls`, LQIP in `ResponsiveImage`.
- **S3b-LEADS** (`audit/s3b-leads`, Flyway V050–V052): owner-JOIN inbox, advisory-lock quota, actor-scoped idempotency, CAS writes with `expectedVersion`, appointments with slots/confirm/reschedule/no-show, queues `appointment-reminder` + `lead-sla-reminder`, broker workspace (SLA, tasks, team), qualified-lead/ROI report, lead funnel API; routes `/my-leads`, `/my-inquiries`, `/broker/workspace`.
- **S6-ENGAGE** (`audit/s6-engage`, Flyway V068–V070): notification centre (seq cursor, Redis Pub/Sub fan-out `APP_NOTIFICATIONS_FANOUT`), resilient SSE, favourites, saved searches + alerts (queue `engage-listing-change`, digests), RFC 8058 unsubscribe, shared shortlists; routes `/saved`, `/notifications`, `/shortlists/:token`, `/unsubscribe`, `/account` preferences.
- **Flyway ranges reassigned** (out-of-order is off): V085 S1, V087–V089 S5B, V090–V094 S7, V095–V099 S8, V100+ S9/S10/S11 (`00_PLAN.md`). S3b's V050–V052 sit below V055+: fine for production (V026); DBs that already applied V055+ need a fresh DB or one `out-of-order` run.
- **UI merge** (`fa7b1b6` on `audit/ui-design-merge`, merged as `eb08ec4`): UI shells/components kept on top of the audit behaviour; dropped/adapted design elements: offset paging (→ v2 cursor "Xem thêm"), `searchState.ts` (→ `filterSchema` URL state), `ListingGallery` (→ S2 `Gallery`), eager MapLibre in the wizard (→ lazy chunk), client-side quality score (→ server checklist); header keeps the "Mở menu tài khoản" menu, `AccountNavigation` gated by `isPoster`/`canOpen`, UI Playwright fixture ported to the v2 API.
- Test plan: per stream `mvnw verify` on the shared test infra (PostgreSQL, Redis, MinIO, ES), frontend lint/tsc/vitest/build/`check:bundle`, media E2E (S1); integration run on the S6 merge. E2E for S3b/S6 pages handed to S11.

# 2026-09-28 - Audit W4 plan: staff MFA/sessions, SEO prerender/CMS, analytics/consent/RUM (S5-SEC phase B, S7-SEO, S8-ANALYTICS)

- Requirement IDs: F20.2–F20.4, UI-14, UI-17, F05.4 (alert follow-up), D-14 (alert follow-up), DS-11 (return-path part) — S5-SEC phase B; F16.1/F16.3–F16.6, UI-01, UI-15, UI-16, UI-25, P-06, P-07, P-12 (doc), D-09 (CMS/catalog rows) — S7-SEO; F15.3, F19.2, F19.3, F17.4 (dashboard), P-10, P-13, UI-24, DS-15 (RUM part), R-8 (dashboard part) — S8-ANALYTICS. Per-row status in `docs/audit-2026-09-27/01_REQUIREMENTS.md` (`DONE (W4)`/`PARTIAL (W4)`).
- Design basis: `02_CONTRACTS.md` (session/MFA, SEO render contract, analytics catalog); each stream branched off `audit-2026-09-27` @ `58ea814` (W1–W3 + UI redesign).
- **S5-SEC phase B** (`audit/s5b-sec`, Flyway **V087–V089**, backend port 18120): staff TOTP MFA (RFC 6238, sealed secret, 10 HMAC-stored recovery codes, admin reset with reason), session lifecycle (absolute + idle timeout, device list, revoke, password/role-change revocation), least-privilege access matrix scan (`AccessMatrixTests` over every `/api/**` route), verify-email/reset-password token states (`TOKEN_INVALID/EXPIRED/USED/SUPERSEDED`), `returnTo` same-site-only post-verification redirect, search-index lag/DLQ Prometheus alert rules, rate-limit policies for the new auth/account endpoints.
- **S7-SEO** (`audit/s7-seo`, Flyway **V090–V091**, backend port 18121): render-layer prerender (`/render/**` behind Nginx `@prerender`, static-shell fallback on 401/403/405/502–504) serving title/canonical/OG/JSON-LD/content without JavaScript for home/listing/project/area/article/search/seller/info pages; real 404/410/301 status; sitemap index split into keyset parts (≤10k URLs) from a 10-minute snapshot; `/search` indexable only as `?purpose=SALE|RENT`, robots.txt from `APP_PUBLIC_BASE_URL`; CMS on JDBC with immutable revisions, scheduled publishing, preview tokens; public `/du-an`, `/khu-vuc` pages with median-price (≥5-listing gate) and sourced amenities; `/about|terms|privacy|contact` operator block from `APP_OPERATOR_*`.
- **S8-ANALYTICS** (`audit/s8-analytics`, Flyway **V095**): opt-in consent (`POST /api/v1/events/consent`, nothing stored pre-`granted`), `APP_ANALYTICS_INTERNAL_NETWORKS` CIDR + staff-device internal flagging, >600 events/h bot flagging, retention jobs (90/180/760 days identifiers/raw/aggregates, 3 years consent records), staff-only dashboard (`GET /api/v1/analytics/dashboard`) reporting `MEASURED`/`NOT_MEASURED` per metric with definition/source/freshness, cohorts, north-star and funnel metrics (P-13), RUM (`web-vitals`, consent-gated, `VITE_RUM_SAMPLE_RATE` sampled) feeding p75 CWV per route/device. Kill switch `APP_ANALYTICS_INGESTION_ENABLED` stays `false` until rate limits are confirmed in production.
- **Flyway order**: V090/V091 (S7) sort before V095 (S8) — fine for fresh databases and for production, which is at V026. V087–V089 (S5B) sit below both.
- **Integration fixes** (on `audit-2026-09-27`, commits `b4669c5`, `40323a2`): the anonymous consent endpoint allowlisted in the access matrix; `web-vitals` installed; S7/S5B textual merge conflicts resolved keeping both sides in `SensitiveResponseCacheFilter`, `RateLimitPolicies` and `frontend/app/main.tsx`; bundle budgets for `/verify-email`, `/listings/new`, `/kyc`, `/account` raised to measured + margin (reduction owned by S10).
- Test plan executed per stream (backend `mvnw verify` on isolated test infra, frontend lint/tsc/vitest/build/`check:bundle`, plus `promtool check/test rules` for S5B and `scripts/seo-smoke.sh`/`verify-prerender.sh`/`verify-headers.sh` for S7) and again on the integration branch after merge — see `docs/audit-2026-09-27/streams/{s5b-sec,s7-seo,s8-analytics}.md` §1, and `WALKTHROUGHS_HISTORY.md` for the merged-branch results.


## 2026-09-29 — CI recovery after W1–W5 merge (F01)
- Based on main `dcc63c3`: restore the backend integration environment by building and starting the project’s test MinIO at `127.0.0.1:59000`, with explicit `BDS_TEST_MINIO_*` settings and a readiness probe.
- Add a disposable demo media signing key to `.env.demo.example` so E2E Docker Compose can interpolate the required backend setting. Production still uses separately managed secrets.
- Validate both independent CI gates on the PR; inspect any subsequent test failures without hiding or skipping suites.
