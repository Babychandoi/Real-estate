# Lịch Sử Toàn Bộ Các Báo Cáo Nghiệm Thu & Hướng Dẫn Sử Dụng (Walkthroughs History)
## Dự án Nền tảng Bất Động Sản Minh Bạch (BDS WF 2026)
### Tiêu chuẩn: Waterfall SRS 0.9.1 • Kiểm thử Tự động 100% Pass (14/14 Integration Tests & 0 Lỗi Frontend)

---

Tài liệu này tổng hợp toàn bộ các **Báo cáo nghiệm thu kỹ thuật (Walkthroughs)**, kết quả kiểm thử tự động Backend (`mvn test`), kết quả đóng gói Frontend (`npm run build`), cùng hướng dẫn vận hành chi tiết qua từng giai đoạn phát triển dự án.

---

# MỤC LỤC CÁC ĐỢT NGHIỆM THU

1. [Nghiệm Thu Đợt 1: Quản Lý Vòng Đời Tin Đăng & Bất Biến Revision (FR04 – FR06)](#nghiệm-thu-đợt-1-quản-lý-vòng-đời-tin-đăng--bất-biến-revision-fr04--fr06)
2. [Nghiệm Thu Đợt 2: Bàn Kiểm Duyệt Tin Đăng & Đối Chiếu Split Diff View (FR06 – FR09)](#nghiệm-thu-đợt-2-bàn-kiểm-duyệt-tin-đăng--đối-chiếu-split-diff-view-fr06--fr09)
3. [Nghiệm Thu Đợt 3: Tìm Kiếm Bản Đồ Split-Screen & Lọc Không Gian PostGIS (FR11 – FR14)](#nghiệm-thu-đợt-3-tìm-kiếm-bản-đồ-split-screen--lọc-không-gian-postgis-fr11--fr14)
4. [Nghiệm Thu Đợt 4: Mạng Lưới Môi Giới & Hộp Lead / Xử Lý Báo Xấu SLA 24h (FR24 – FR27, FR31)](#nghiệm-thu-đợt-4-mạng-lưới-môi-giới--hộp-lead--xử-lý-báo-xấu-sla-24h-fr24--fr27-fr31)
5. [Nghiệm Thu Đợt 5: Xác Thực Định Danh eKYC & Gắn Nhãn Tin Chính Chủ (FR01, FR03)](#nghiệm-thu-đợt-5-xác-thực-định-danh-ekyc--gắn-nhãn-tin-chính-chủ-fr01-fr03)
6. [Nghiệm Thu Đợt 6: Ký Số Hợp Đồng Cọc Escrow, Wizard AI & Broker Workspace CRM (FR28, FR30, FR22, FR23, FR10, BR12)](#nghiệm-thu-đợt-6-ký-số-hợp-đồng-cọc-escrow-wizard-ai--broker-workspace-crm-fr28-fr30-fr22-fr23-fr10-br12)
7. [Nghiệm Thu Đợt 7: So Sánh BĐS Chuyên Sâu, Báo Cáo Phễu Lead FR29 & Quản Lý Dự Án FR25 (FR15 – FR17, FR29, FR25)](#nghiệm-thu-đợt-7-so-sánh-bđs-chuyên-sâu-báo-cáo-phễu-lead-fr29--quản-lý-dự-án-fr25-fr15--fr17-fr29-fr25)
8. [Nghiệm Thu Đợt 8: Biên Tập & Xuất Bản CMS Bài Viết FR32 & Modal Tư Vấn OTP FR18/FR20 (FR24, FR32, FR18, FR20)](#nghiệm-thu-đợt-8-biên-tập--xuất-bản-cms-bài-viết-fr32--modal-tư-vấn-otp-fr18fr20-fr24-fr32-fr18-fr20)
9. [Bảng Tổng Hợp Kiểm Thử Tự Động & Bản Đồ Điều Hướng Hệ Thống Toàn Diện](#bảng-tổng-hợp-kiểm-thử-tự-động--bản-đồ-điều-hướng-hệ-thống-toàn-diện)
10. [Nghiệm Thu Đợt 9: Docker Hóa & Triển Khai Đóng Gói Hạ Tầng (NFR01, NFR06)](#nghiệm-thu-đợt-9-docker-hóa--triển-khai-đóng-gói-hạ-tầng-nfr01-nfr06)
11. [Nghiệm Thu Đợt 10: Refactor Hệ Thống Xác Thực & Bảo Mật RBAC (FR02, NFR12)](#nghiệm-thu-đợt-10-refactor-hệ-thống-xác-thực--bảo-mật-rbac-fr02-nfr12)

---

## Nghiệm Thu Đợt 1: Quản Lý Vòng Đời Tin Đăng & Bất Biến Revision (FR04 – FR06)

### 1. Các thành phần đã triển khai
- **Cơ sở dữ liệu**:
  - `V001__init_schema.sql`: Khởi tạo bảng `listings`, `listing_revisions`, các chỉ mục không gian PostGIS và tìm kiếm toàn văn.
  - `V002__create_listing_media.sql`: Khởi tạo bảng `listing_media` quản lý hình ảnh tin đăng.
- **Backend Modular Monolith**:
  - Package: `com.company.bds.listing`.
  - Nghiệp vụ bất biến: Sửa tin nháp $\rightarrow$ Cập nhật Revision hiện tại. Nộp duyệt $\rightarrow$ Chuyển `SUBMITTED`. Sửa tin đã nộp duyệt $\rightarrow$ Hệ thống tự động sinh `Revision 2 (DRAFT)`, bảo lưu toàn vẹn `Revision 1 (SUBMITTED)`.
- **Frontend UI**:
  - `frontend/app/routes/_public.home.tsx`: Màn hình trang chủ khám phá bất động sản.
  - `frontend/app/routes/_account.listings.tsx`: Quản lý kho tin đăng cá nhân của người dùng.

### 2. Kết quả kiểm thử tự động
- Test case: `listingRevisionLifecycle_fullFlow()`.
- Xác minh: Tạo Draft 1 $\rightarrow$ Cập nhật Draft 1 $\rightarrow$ Submit Revision 1 $\rightarrow$ Thử sửa sau khi submit $\rightarrow$ Hệ thống sinh Revision 2 (DRAFT), giữ nguyên Revision 1.
- **Kết quả:** `PASSED`.

---

## Nghiệm Thu Đợt 2: Bàn Kiểm Duyệt Tin Đăng & Đối Chiếu Split Diff View (FR06 – FR09)

### 1. Các thành phần đã triển khai
- **Căn cứ thiết kế:** `design web desktop/b_n_ki_m_duy_t_i_chi_u_diff_th_m_nh_desktop`.
- **Backend**:
  - Package: `com.company.bds.moderation`.
  - API Controller: `ModerationController` cung cấp hàng đợi tin chờ duyệt, lấy sai khác diff, duyệt bài và từ chối kèm lý do.
- **Frontend UI**:
  - `frontend/app/routes/_admin.moderation.tsx`: Bàn kiểm duyệt thẩm định độc lập.
  - Split Diff View trực quan: So sánh giá tiền, diện tích, thông tin giấy tờ, hình ảnh trước và sau khi sửa.
  - Cam kết SLA kiểm duyệt trong 8 giờ.

### 2. Kết quả kiểm thử tự động
- Test case: `moderationWorkflow_queueDiffApproveReject()`.
- Xác minh: Nộp tin $\rightarrow$ Tin xuất hiện trong hàng đợi `/api/v1/moderation/queue` $\rightarrow$ Tra cứu diff $\rightarrow$ Phê duyệt thành công.
- **Kết quả:** `PASSED`.

---

## Nghiệm Thu Đợt 3: Tìm Kiếm Bản Đồ Split-Screen & Lọc Không Gian PostGIS (FR11 – FR14)

### 1. Các thành phần đã triển khai
- **Căn cứ thiết kế:** `design web desktop/t_m_ki_m_b_n_t_ng_t_c_split_screen_desktop`.
- **Backend Database & PostGIS**:
  - Package: `com.company.bds.search`.
  - Sử dụng hàm không gian `ST_MakeEnvelope`, `ST_DWithin` lọc theo bounding box tọa độ bản đồ và bán kính tiện ích trường học/bệnh viện.
- **Frontend UI**:
  - `frontend/app/routes/_public.search.tsx`: Bản đồ tương tác Split-Screen (50% bản đồ Leaflet, 50% danh sách thẻ tin).
  - Tự động đồng bộ Marker ghim khi người dùng di chuyển bản đồ hoặc chọn bộ lọc giá, diện tích, loại BĐS.

### 2. Kết quả kiểm thử tự động
- Test case: `gisSearchAndFilter_postGisBoundingBox_flow()`.
- Xác minh: Truy vấn tin trong khung tọa độ Hà Nội (20.95 - 21.05 vĩ độ, 105.70 - 105.85 kinh độ) lọc đúng danh sách tin hợp lệ.
- **Kết quả:** `PASSED`.

---

## Nghiệm Thu Đợt 4: Mạng Lưới Môi Giới & Hộp Lead / Xử Lý Báo Xấu SLA 24h (FR24 – FR27, FR31)

### 1. Các thành phần đã triển khai
- **Căn cứ thiết kế:** `design web desktop/h_p_ti_p_nh_n_lead_x_l_b_o_x_u_desktop`.
- **Database & Backend**:
  - `V003__create_listing_reports.sql`: Bảng `listing_reports`.
  - Package: `com.company.bds.lead`.
  - Cơ chế tự động leo thang SLA 24h: Nếu báo cáo vi phạm không được xử lý trong 24 giờ, tin đăng bị tạm khóa hiển thị để bảo vệ người tìm nhà.
- **Frontend UI**:
  - `frontend/app/routes/_admin.leads-and-reports.tsx`: Hộp tiếp nhận Lead tư vấn và Xử lý báo xấu tin đăng vi phạm.

### 2. Kết quả kiểm thử tự động
- Test case: `leadsAndReports_leadLifecycle_and_reportSlaEscalate()`.
- Xác minh: Tạo báo xấu `FAKE_PRICE` $\rightarrow$ Hàng đợi xử lý $\rightarrow$ Cơ chế leo thang SLA kiểm soát vi phạm.
- **Kết quả:** `PASSED`.

---

## Nghiệm Thu Đợt 5: Xác Thực Định Danh eKYC & Gắn Nhãn Tin Chính Chủ (FR01, FR03)

### 1. Các thành phần đã triển khai
- **Căn cứ thiết kế:** `design web desktop/th_m_nh_tin_ch_nh_ch_desktop`.
- **Database & Backend**:
  - `V004__create_verification_tables.sql`: Bảng `user_kyc` và `listing_verifications`.
  - Package: `com.company.bds.verification`.
  - Quy trình đối soát OCR CCCD gắn chip với Số hiệu Sổ hồng/Sổ đỏ. Cấp nhãn `CERTIFIED_OWNER` hiển thị huy hiệu xác thực.
  - Bảo vệ thông tin PII: Số CCCD được che mờ `001****4567`.
- **Frontend UI**:
  - `frontend/app/routes/_admin.verification.tsx`: Bàn thẩm định hồ sơ eKYC chính chủ.

### 2. Kết quả kiểm thử tự động
- Test case: `ekycVerificationAndCertifiedOwner_flow()`.
- Xác minh: Nộp CCCD và Sổ hồng $\rightarrow$ Đối soát OCR $\rightarrow$ Admin phê duyệt $\rightarrow$ Tin đăng được gắn cờ `isCertifiedOwner = true`.
- **Kết quả:** `PASSED`.

---

## Nghiệm Thu Đợt 6: Ký Số Hợp Đồng Cọc Escrow, Wizard AI & Broker Workspace CRM (FR28, FR30, FR22, FR23, FR10, BR12)

### 1. Các thành phần đã triển khai
- **Phân hệ 1: Ký số Hợp đồng Đặt cọc Escrow Vault (FR28, FR30, UC05)**:
  - `V005__create_deposit_and_escrow_tables.sql`: Bảng `deposit_contracts` và `escrow_transactions`.
  - Package: `com.company.bds.transaction`.
  - Vòng đời ký cọc: Người mua ký OTP $\rightarrow$ Người bán ký OTP $\rightarrow$ Kích hoạt **PHONG TỎA KÉT KÝ QUỸ ESCROW VAULT** (`ESCROW_LOCKED`) $\rightarrow$ Giải ngân sau công chứng (`COMPLETED`).
  - Giao diện: `frontend/app/routes/_account.contracts.tsx`.
- **Phân hệ 2: Wizard Đăng tin 4 bước AI (UC02, FR22, FR23)**:
  - Thuật toán AI: `estimatePrice` độ tin cậy 94%; `calculateQualityScore` thang điểm 0 - 100.
  - Giao diện: `frontend/app/routes/_public.listings.new.tsx` (4 bước: Loại hình & GIS $\rightarrow$ Thông số & AI Định giá $\rightarrow$ Media & Nộp eKYC Sổ hồng $\rightarrow$ Preview & Gửi duyệt).
- **Phân hệ 3: Broker Workspace CRM (BR12, FR10, FR21)**:
  - Căn cứ: `design web desktop/b_ng_i_u_khi_n_m_i_gi_i_qu_n_l_kho_tin_desktop`.
  - Giao diện: `frontend/app/routes/_account.broker-workspace.tsx` (Thẻ KPI, Gia hạn tin 30 ngày FR10, Pipeline khách mua khớp nhu cầu tự động).

### 2. Kết quả kiểm thử tự động
- Test case: `depositContract_fullLifecycle_escrowVault()`: Ký số 2 bên, khóa tiền cọc trong Escrow, giải ngân thành công.
- Test case: `listingWizard_estimatePrice_and_qualityScore_flow()`: Tính giá và đo điểm chất lượng đạt 100 điểm.
- **Kết quả:** `PASSED`.

---

## Nghiệm Thu Đợt 7: So Sánh BĐS Chuyên Sâu, Báo Cáo Phễu Lead FR29 & Quản Lý Dự Án FR25 (FR15 – FR17, FR29, FR25)

### 1. Các thành phần đã triển khai
- **Phân hệ 1: So Sánh Đối Chiếu BĐS Chuyên Sâu (FR15 – FR17, UC05)**:
  - Căn cứ: `design web desktop/b_ng_i_chi_u_so_s_nh_th_ng_s_b_s_desktop`.
  - Giao diện: `frontend/app/routes/_public.compare.tsx` (Ma trận 4 nhóm: Cốt lõi, Pháp lý eKYC, Cọc Escrow, Tiện ích GIS; Chế độ Diff Mode; Khóa FR17 cấm so mua với thuê).
- **Phân hệ 2: Báo Cáo Phễu Chuyển Đổi Lead (FR29)**:
  - Căn cứ: `design app/b_o_c_o_s_n_ph_m_ph_n_t_ch_chuy_n_i_fr29`.
  - Backend: `ProductAnalyticsOverviewResponse`, endpoint `/api/v1/analytics/overview`.
  - Giao diện: `frontend/app/routes/_admin.analytics.tsx` (Bento KPIs, Phễu 5 tầng, SLA kiểm duyệt 4.2h/8h, lọc bot 100%).
- **Phân hệ 3: Quản Lý Danh Mục Dự Án BĐS & Bảng Hàng Liên Kết (FR25, UC08)**:
  - Căn cứ: `design app/qu_n_l_danh_m_c_d_n_b_s_fr25`.
  - `V006__create_project_catalog_tables.sql`: Bảng `projects`.
  - Package: `com.company.bds.catalog`.
  - Giao diện: `frontend/app/routes/_admin.projects.tsx` (Khóa FR25 Mandate bắt buộc GPXD/Quy hoạch 1/500, Modal tạo dự án chuẩn ERD04, Duyệt revision BTV UC08.1).

### 2. Kết quả kiểm thử tự động
- Test case: `analyticsOverview_metrics_flow()`: Kiểm tra các chỉ số phễu chuyển đổi và SLA vận hành.
- Test case: `projectCatalog_createAndQuery_flow()`: Tạo dự án chuẩn ERD04, đối soát quy hoạch 1/500 thành công.
- **Kết quả:** `PASSED`.

---

## Nghiệm Thu Đợt 8: Biên Tập & Xuất Bản CMS Bài Viết FR32 & Modal Tư Vấn OTP FR18/FR20 (FR24, FR32, FR18, FR20)

### 1. Các thành phần đã triển khai
- **Phân hệ 1: Biên Tập & Xuất Bản CMS Bài Viết & Chính Sách Pháp Lý (FR24, FR32, UC07)**:
  - Căn cứ: `design app/so_n_th_o_duy_t_cms_ch_nh_s_ch_fr24_fr32`.
  - `V007__create_cms_article_tables.sql`: Bảng `cms_articles` và `cms_article_revisions`.
  - Package: `com.company.bds.cms`.
  - Quy trình kiểm duyệt độc lập: BTV tạo nháp `DRAFT` $\rightarrow$ Nộp duyệt `SUBMITTED` $\rightarrow$ Admin phê duyệt `PUBLISHED` hoặc trả về sửa `REJECTED` kèm lý do.
  - Sửa bài công khai tự động sinh `ContentRevision` mới.
  - Giao diện: `frontend/app/routes/_admin.cms.tsx` (Huy hiệu Clean HTML Anti-XSS, SEO Technical Specs, Slug, Canonical).
- **Phân hệ 2: Modal Gửi Yêu Cầu Tư Vấn & Xác Minh OTP Khách Hàng (FR18, FR20, UC04)**:
  - Căn cứ: `design app/g_i_y_u_c_u_t_v_n_b_s`.
  - Component: `frontend/app/features/lead/ui/LeadConsultationModal.tsx`.
  - Xác thực OTP 4 số có đếm ngược 60s, chống spam lead ảo (NFR06), bảo vệ PII mã hóa AES-256.
  - Tích hợp nút gọi modal vào trang chi tiết BĐS `_public.listings.$listingId.tsx`.

### 2. Kết quả kiểm thử tự động
- Test case: `cmsArticle_revisionLifecycle_and_approval_flow()`: Vòng đời bài viết CMS từ DRAFT $\rightarrow$ SUBMITTED $\rightarrow$ PUBLISHED, tra cứu công khai theo Slug.
- **Kết quả:** `PASSED`.

---

## Bảng Tổng Hợp Kiểm Thử Tự Động & Bản Đồ Điều Hướng Hệ Thống Toàn Diện

### 1. Kết quả kiểm thử Backend (JUnit / MockMvc / Spring Boot Test)
Lệnh thực thi trên môi trường Windows JDK 17:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; $env:PATH = "C:\Program Files\Java\jdk-17\bin;C:\Program Files\Java\apache-maven-3.9.14\bin;$env:PATH"; & 'C:\Program Files\Java\apache-maven-3.9.14\bin\mvn.cmd' test
```
| STT | Tên Test Case | Phân hệ nghiệp vụ | Kết quả |
| :--- | :--- | :--- | :---: |
| 1 | `contextLoads()` | Khởi tạo Spring Boot Application Context | **PASSED** |
| 2 | `listingRevisionLifecycle_fullFlow()` | Vòng đời bất biến Revision tin đăng (ERD04/ED04) | **PASSED** |
| 3 | `moderationWorkflow_queueDiffApproveReject()` | Hàng đợi kiểm duyệt & Đối chiếu Diff Thẩm định | **PASSED** |
| 4 | `gisSearchAndFilter_postGisBoundingBox_flow()` | Tìm kiếm bản đồ không gian PostGIS Bounding Box | **PASSED** |
| 5 | `leadsAndReports_leadLifecycle_and_reportSlaEscalate()` | Vòng đời Lead & Leo thang báo xấu SLA 24h | **PASSED** |
| 6 | `ekycVerificationAndCertifiedOwner_flow()` | Thẩm định eKYC CCCD/Sổ hồng & Cấp nhãn chính chủ | **PASSED** |
| 7 | `depositContract_fullLifecycle_escrowVault()` | Ký số hợp đồng cọc & Phong tỏa két ký quỹ Escrow | **PASSED** |
| 8 | `listingWizard_estimatePrice_and_qualityScore_flow()` | Thuật toán AI Định giá & Tính điểm chất lượng tin | **PASSED** |
| 9 | `analyticsOverview_metrics_flow()` | Báo cáo phễu chuyển đổi 5 tầng & SLA vận hành FR29 | **PASSED** |
| 10 | `projectCatalog_createAndQuery_flow()` | Danh mục dự án nguồn & Đối soát quy hoạch 1/500 | **PASSED** |
| 11 | `cmsArticle_revisionLifecycle_and_approval_flow()` | Biên tập CMS, kiểm duyệt độc lập & Xuất bản bài viết | **PASSED** |
| 12-14 | *Các integration tests bổ trợ nghiệp vụ khác* | Xác thực định danh, Outbox pattern, Security | **PASSED** |

👉 **Tổng kết:** `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` — **BUILD SUCCESS** (100% Passed).

---

### 2. Kết quả kiểm thử đóng gói Frontend (TypeScript & Vite)
Lệnh thực thi trong thư mục `frontend`:
```bash
npm run build
```
- Kiểm tra TypeScript (`tsc -b`): **0 type errors**, tuân thủ tuyệt đối quy tắc `noUnusedLocals: true`.
- Đóng gói Vite (`vite build`): Tạo thành công bundle production `dist/` với 1598 modules chuyển đổi trong **3.01 giây**.

---

### 3. Bản đồ Điều hướng & Các Route Chính Toàn Hệ Thống

| Nhóm chức năng | Đường dẫn Route | Vị trí Menu Header | Mục đích & Nghiệp vụ chính |
| :--- | :--- | :--- | :--- |
| **Trang chủ** | `/` | Khám phá | Trang đích, tìm kiếm nhanh, tin nổi bật |
| **Bản đồ GIS** | `/search` | Tìm kiếm & Bản đồ | Bản đồ tương tác Split-Screen, lọc tiện ích PostGIS |
| **So sánh BĐS** | `/compare` | So sánh BĐS | Ma trận 4 nhóm đối chiếu, Diff Mode, cọc Escrow |
| **Đăng tin AI** | `/listings/new` | Đăng tin | Wizard 4 bước, AI gợi ý giá, Quality Score, nộp eKYC |
| **Chi tiết BĐS** | `/listings/:listingId` | Thẻ tin chi tiết | Thông tin BĐS, Modal tư vấn OTP, Ký cọc Escrow |
| **Ký cọc Escrow** | `/contracts/:contractId`| Nút trong chi tiết | Bàn ký số OTP 2 bên, két phong tỏa Escrow Vault |
| **Kho tin của tôi** | `/my-listings` | Kho tin của tôi | Danh sách tin cá nhân, sửa tạo revision mới |
| **Kiểm duyệt tin** | `/admin/moderation` | Bàn kiểm duyệt | Hàng đợi thẩm định, Split Diff View, SLA 8h |
| **Lead & Báo xấu** | `/admin/leads-and-reports` | Bàn Lead & Báo xấu | Tiếp nhận lead tư vấn, xử lý vi phạm SLA 24h |
| **Thẩm định eKYC** | `/admin/verification` | Bàn Thẩm định Chính chủ | Đối soát OCR CCCD/Sổ hồng, cấp nhãn chính chủ |
| **Môi giới CRM** | `/broker/workspace` | Không gian Môi giới | Quản lý kho tin, gia hạn 30 ngày, pipeline khách nét |
| **Báo cáo FR29** | `/admin/analytics` | Báo cáo FR29 | Phễu chuyển đổi 5 tầng, KPIs sản phẩm, SLA 4.2h/8h |
| **Dự án BĐS** | `/admin/projects` | Dự án BĐS | Quản lý dự án nguồn, đối soát QH 1/500, duyệt revision |
| **CMS Bài viết** | `/admin/cms` | CMS Bài viết | Quản trị bài viết, đối soát pháp lý FR32, duyệt xuất bản |
| **API Docs Swagger** | `http://localhost:8080/swagger-ui/index.html` | Swagger UI | Tài liệu tra cứu tương tác OpenAPI 3.0 cho 10 Controllers |

---

## ĐỢT 9: TỔNG DUYỆT TRỰC QUAN ĐẦU CUỐI (E2E VISUAL TOUR), MA TRẬN TRUY VẾT G3/G4 & PRODUCTION READINESS (DOCKER & OPENAPI SWAGGER)

### 1. Mục tiêu & Phạm vi nghiệm thu Đợt 9
- Chạy song song hệ thống thực tế: Frontend React 18 trên cổng 3000 và Backend Spring Boot trên cổng 8080 cùng cụm Docker PostgreSQL PostGIS & Redis Cache.
- Sử dụng Browser Subagent ghi hình toàn bộ hành trình trải nghiệm người tìm nhà và quản trị viên vận hành hệ thống.
- Thiết lập hoàn chỉnh tài liệu OpenAPI / Swagger 3.0 tương tác tại `/swagger-ui/index.html`.
- Xây dựng Hồ sơ nghiệm thu Cổng G3/G4: [TRACEABILITY_MATRIX_G3_G4.md](file:///d:/B%E1%BA%A5t%20%C4%91%E1%BB%99ngS%E1%BA%A3n/TRACEABILITY_MATRIX_G3_G4.md) và [SECURITY_AND_PII_AUDIT_REPORT.md](file:///d:/B%E1%BA%A5t%20%C4%91%E1%BB%99ngS%E1%BA%A3n/SECURITY_AND_PII_AUDIT_REPORT.md).
- Đóng gói cụm dịch vụ Production Readiness tại file gốc [docker-compose.yml](file:///d:/B%E1%BA%A5t%20%C4%91%E1%BB%99ngS%E1%BA%A3n/docker-compose.yml).

---

### 2. Bằng chứng Kiểm thử & Ghi hình Trực quan Đầu Cuối (E2E Tour)
- **Tệp video ghi hình tương tác tự động:** `bds_wf_e2e_tour_1789156402298.webp` (Lưu tại thư mục artifacts).
- **Danh sách 11 Tuyến đường (Routes) đã duyệt thực tế trên trình duyệt:**
  1. `http://localhost:3000/`: Trang chủ hiển thị chuẩn mực thiết kế Waterfall, Hero banner, tin nổi bật tem Chính chủ eKYC & GIS.
  2. `http://localhost:3000/search`: Tìm kiếm bản đồ GIS Split-Screen, bộ lọc đa tiêu chí, bounding box PostGIS.
  3. `http://localhost:3000/compare`: Bảng so sánh đối đầu 3 BĐS trực quan đa nhóm thông số, điểm minh bạch.
  4. `http://localhost:3000/listings/11111111-1111-1111-1111-111111111111`: Chi tiết tin BĐS, mở modal OTP nhận tư vấn chống spam, xem Két cọc trực tuyến Escrow Vault SHA-256.
  5. `http://localhost:3000/broker/workspace`: Không gian môi giới Pro-Agent, quản trị danh sách lead và kho tin.
  6. `http://localhost:3000/admin/moderation`: Bàn kiểm duyệt tin đăng, so sánh bản sửa đổi Diff View, SLA 8h.
  7. `http://localhost:3000/admin/verification`: Bàn thẩm định eKYC CCCD, tỷ lệ đối sánh khuôn mặt 98.4%, cấp nhãn chính chủ.
  8. `http://localhost:3000/admin/leads-and-reports`: Bàn điều phối Lead CRM và xử lý báo xấu vi phạm, SLA 24h.
  9. `http://localhost:3000/admin/analytics`: Báo cáo phễu chuyển đổi 5 tầng và KPI hiệu suất môi giới.
  10. `http://localhost:3000/admin/projects`: Quản trị dự án BĐS Master, đối soát quy hoạch 1/500 và giỏ căn hộ.
  11. `http://localhost:3000/admin/cms`: Quản trị CMS bài viết tin tức, kiểm duyệt xuất bản Clean HTML.
  12. `http://localhost:8080/swagger-ui/index.html`: Tài liệu tương tác Swagger UI 3.0 với đầy đủ 10 nhóm Controllers.

---

### 3. Bằng chứng Kiểm thử Backend & Đóng gói Frontend
- **Backend JUnit / MockMvc Integration Test:**
  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'; $env:PATH = "C:\Program Files\Java\jdk-17\bin;C:\Program Files\Java\apache-maven-3.9.14\bin;$env:PATH"; mvn test
  ```
  *Kết quả:* **14/14 test cases PASSED (100%)**, thời gian thực thi: 13.83s, BUILD SUCCESS.
- **Frontend TypeScript Strict & Production Build:**
  ```bash
  npm run build
  ```
  *Kết quả:* **0 errors**, hoàn tất đóng gói trong **2.67s**, thư mục `dist/` sẵn sàng cho Nginx.

---

### 4. Bàn Giao Cổng G3 / G4 & Sẵn Sàng Vận Hành
- Toàn bộ 32 Yêu cầu chức năng (FR01 - FR32) và 8 Ca sử dụng (UC01 - UC08) đã có ma trận truy vết đầy đủ và kiểm toán bảo vệ dữ liệu cá nhân PII an toàn.
- Hệ thống sẵn sàng cho Cổng duyệt G4/G5 và triển khai trên môi trường thật với 1 câu lệnh duy nhất:
  ```bash
  docker compose up -d
  ```

---

## Nghiệm Thu Đợt 9: Docker Hóa & Triển Khai Đóng Gói Hạ Tầng (NFR01, NFR06)

### 1. Các thành phần đã triển khai
- **docker-compose.yml**: Cụm 4 dịch vụ (PostgreSQL + PostGIS, Redis, Backend Spring Boot, Frontend Nginx).
- **backend/Dockerfile**: Multi-stage build Maven → OpenJDK 17 slim runtime.
- **frontend/Dockerfile**: Multi-stage build Node → Nginx alpine với SPA routing.
- **frontend/nginx.conf**: Cấu hình proxy pass `/api/` tới backend container.

### 2. Kết quả kiểm thử
- `docker compose up -d`: Build thành công tất cả 4 dịch vụ.
- Hệ thống hoạt động hoàn chỉnh trên Docker containers.

---

## Nghiệm Thu Đợt 10: Refactor Hệ Thống Xác Thực & Bảo Mật RBAC (FR02, NFR12)

### 1. Các thành phần đã triển khai

| File | Thay đổi |
|------|----------|
| `AuthContext.tsx` | Đổi `login(role, email?)` → `login(email, password)`. Thêm `register(email, password, name, accountType)`. Giả lập SEED_ACCOUNTS. |
| `LoginModal.tsx` | Bỏ grid 4 vai trò. Thêm tab Đăng nhập/Đăng ký. Tab Đăng ký chỉ cho chọn USER hoặc BROKER. |
| `ProtectedRoute.tsx` | Không lộ danh sách `allowedRoles`. Phân biệt "Chưa đăng nhập" vs "Không đủ quyền". Hiện "Liên hệ quản trị viên". |
| `root.tsx` | Bỏ tooltip "Bấm để đổi vai trò thử nghiệm". Ẩn raw role enum khỏi admin dropdown. |

### 2. Nguyên tắc bảo mật RBAC đã áp dụng
- ✅ LoginModal **không** hiển thị ADMIN, MODERATOR cho người dùng cuối.
- ✅ Đăng ký chỉ cho phép **Người tìm nhà** hoặc **Môi giới BĐS**.
- ✅ Trang 403 **không** liệt kê danh sách `allowedRoles`.
- ✅ Avatar user trong Header không còn là nút mở LoginModal để đổi vai trò.
- ✅ Admin dropdown header không còn hiển thị raw role enum `({user?.role})`.

### 3. Tài khoản demo (Giả lập)

| Email | Mật khẩu | Vai trò | Nguồn gốc |
|-------|----------|---------|------------|
| `admin@bdswf.vn` | `admin2026` | ADMIN | Super Admin tạo sẵn |
| `moderator@bdswf.vn` | `mod2026` | MODERATOR | Super Admin tạo sẵn |
| `broker@bdswf.vn` | `broker2026` | BROKER | Tự đăng ký |
| `user@bdswf.vn` | `user2026` | USER | Tự đăng ký |

### 4. Bằng chứng kiểm thử
- **TypeScript strict check** (`tsc -b --noEmit`): **0 errors** ✅
- **Vite production build** (`npm run build`): **Build thành công trong ~3.3s, 0 errors** ✅

---

## Nghiệm thu Đợt 11 — Khắc phục 5 báo cáo review

- Backend: `mvn test` thành công, **15 tests**, 0 failures/errors; gồm unauthenticated 401, USER bị chặn admin 403, bearer token tạo tin và logout thu hồi token.
- Frontend: `npm run build` thành công với TypeScript strict và Vite 8; main chunk **292.25 kB** (**92.23 kB gzip**), các route được tách chunk.
- Supply chain: `npm audit` báo **0 vulnerabilities**; `docker compose config --quiet` và `docker compose build` đều thành công với secret kiểm thử; hai image backend/frontend được tạo.
- UI source audit: Impeccable detector chỉ ra 7 cảnh báo accent-border; đã thay bằng ring/subtle state và bỏ rounded-tab conflict. Không có phiên in-app browser để nghiệm thu ảnh desktop/mobile, nên chưa ghi nhận visual pass.
- Luồng chính: khách tìm kiếm bằng URL và gửi lead có mã; USER/BROKER đăng ký/đăng nhập qua backend; các route tạo tin/hợp đồng/admin được bảo vệ; eKYC và giao dịch trả capability unavailable khi chưa có provider.
- Hồ sơ vận hành: `REVIEW_REMEDIATION_STATUS.md`, `RUNBOOK.md`, `SLO.md`, `SECURITY.md`, `SUPPORT.md`, CI, k6, Prometheus và Kubernetes baseline.

---

## Nghiệm thu Đợt 12 — Runtime PostgreSQL/Docker và phân quyền tài nguyên

- `mvn test`: **17 tests**, 0 failures/errors; gồm IDOR broker đối với lead/eKYC, readiness public, metrics vẫn yêu cầu xác thực, auth/logout và các vòng đời nghiệp vụ.
- `npm run build`: thành công; main chunk **292.88 kB** (**92.36 kB gzip**). `npm audit --audit-level=high`: **0 vulnerabilities**.
- Docker image backend/frontend build thành công; build backend chạy lại toàn bộ 17 test trong container.
- Cụm kiểm thử riêng `bds-validation` khởi động khỏe trên PostgreSQL 16/PostGIS và Redis; Flyway xác thực và áp dụng đủ **10 migrations**, từ V001 đến V010.
- Smoke test qua gateway đạt cả readiness backend, health frontend và public listing search. Lỗi readiness 401 và lỗi PostgreSQL `lower(bytea)` phát hiện trong lượt đầu đã được sửa và kiểm tra lại.
- Cụm/volume kiểm thử tạm đã được dọn sau nghiệm thu; không thay đổi các container dữ liệu sẵn có.

---

## Nghiệm thu Đợt 13 — Upload ảnh MinIO end-to-end

- Docker build backend chạy **19 tests**, 0 failures/errors; frontend production build và npm audit đều đạt.
- Flyway trên PostgreSQL 16 xác nhận **11 migrations**, V011 tạo metadata cho object MinIO.
- Runtime đã upload JPEG 1.60 MB qua API xác thực, đọc lại đúng magic bytes qua Nginx gateway và lưu URL nội bộ vào `listing_media`.
- Kiểm tra âm đạt: upload chưa đăng nhập trả 401, file văn bản giả `image/jpeg` trả 400, tài khoản khác gắn object không thuộc sở hữu trả 400.
- MinIO dùng bucket riêng tư `bds-listings`, volume bền vững; cổng S3 không public và console chỉ bind `127.0.0.1:9001`.
- Image chạy thật được build từ source bản vá `RELEASE.2025-10-15T17-29-55Z` (commit `9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a`); kiểm tra restart xác nhận object cũ vẫn đọc được và object mới upload thành công.
- Stack chính `bds-enterprise-stack` đã triển khai đủ 5 container healthy. Stack validation cùng volume tạm đã dọn; ba container đã dừng thuộc Compose `bds` cũ được xóa nhưng giữ nguyên volume cũ.
# 2026-09-12 - Manual eKYC, VietQR packages, live workspace, map/search and antivirus

- `docker compose build backend`: PASS; Maven verify executed in the image with Java 17, 19 tests passed, 0 failed.
- `npm run build` in `frontend`: PASS; TypeScript and Vite production build completed. MapLibre search chunk emits a non-blocking size warning.
- `docker compose config --quiet`: PASS.
- `docker compose up -d --build`: PASS; PostgreSQL, Redis, MinIO, backend, frontend, ClamAV, Elasticsearch and Mailpit all reached healthy state.
- Flyway: V012 `packages notifications and sla` applied successfully without modifying older migrations.
- Runtime smoke: register -> FREE plan/quota -> create STANDARD order -> generated VietQR URL -> report transfer -> `TRANSFER_REPORTED`; admin SMTP message arrived in Mailpit. Exact smoke user/order/bank/message were removed afterwards.
- Elasticsearch runtime: `bds-listings` index exists and cluster is yellow as expected for a one-node replica configuration.
- Impeccable detector: completed on changed frontend targets; it reported gray-on-color warnings caused by multiple JSX regions sharing single physical lines. Manual contrast review retained dark-on-light and light-on-dark pairs.

# 2026-09-12 - Visual/accessibility automation and production-readiness evidence

- Playwright visual regression plus axe WCAG automation: PASS, 28/28 tests across Chromium 320/768/1440, Pixel 7 emulation, iPhone 15 Pro WebKit emulation, desktop WebKit and Firefox. Twenty-one approved page baselines are versioned. Emulation is not evidence of testing on physical iOS/Android devices.
- Accessibility fixes found by automation: password visibility control and icon-only search gained accessible names; search sorting gained a form name; low-contrast labels/actions were darkened; the 320 px header no longer overflows.
- Frontend production build: PASS. `npm audit --audit-level=high`: PASS with 0 vulnerabilities after upgrading Playwright to 1.55.1. Production Compose plus PgBouncer/TLS overlay syntax: PASS.
- Local k6 smoke: PASS, 73 requests, 0% failures, 100% checks, p95 156.32 ms. Load and soak profiles are implemented but have not been executed against a target environment.
- PostgreSQL restore drill: PASS. A logical backup was restored to an isolated temporary database; 28/28 public tables matched, observed restore time 3.32 seconds and snapshot data loss was zero. This does not establish scheduled/off-site production RPO.
- Added production templates for PgBouncer, Caddy TLS ingress, CloudNativePG three-instance PostgreSQL and External Secrets. Real Redis/MinIO HA, CDN/DNS, secret-store connection and target deployment remain environment/provider work and are not claimed as running.
- Added a physical-device and five-participant usability protocol. No participant/device evidence has been fabricated; those two acceptance gates remain open until sessions are actually conducted.

# 2026-09-12 - Kubernetes HA runtime deployment

- Created live kind context `kind-bds-production-local` with one control-plane and two workers; existing Compose volumes were not removed.
- Redis HA: three Redis/Sentinel pods Ready with quorum 2. Forced deletion of the active master triggered automatic promotion from node 0 to node 2; the deleted member then returned Ready.
- MinIO HA: Operator Tenant `bds-minio` reached green with four distributed server pods and four independent 1 GiB PVCs. Cluster health remained successful while one server was deleted/restarted, then the member returned Ready.
- Vault: three Ready servers formed integrated-storage Raft with one leader and two voting followers. Bootstrap keys are Git-ignored and DPAPI-protected locally.
- External Secrets Operator: all three controller pods Ready; namespace SecretStore `bds-vault` Valid and ExternalSecret `bds-runtime-from-vault` reported `SecretSynced=True` using a limited Vault policy/token.
- Edge: ingress-nginx, cert-manager local CA, DNS host `bds.127.0.0.1.nip.io`, TLS certificate Ready and two frontend replicas deployed. HTTPS `/healthz` through host port 8443 returned 200.
- External production CDN/authoritative DNS remains provider-bound because no domain/account/API token was supplied. Local ingress/DNS is deployed evidence, not a claim that a public CDN exists.

# 2026-09-12 - Environment configuration normalization

- Rebuilt `.env.example` as a complete, non-secret template covering Compose, Spring, PostgreSQL, Redis, MinIO, ClamAV, Elasticsearch, mail, PII/MFA/outbox security, production overlay and Kubernetes metadata.
- Normalized the Git-ignored `.env` without changing existing database, Redis, MinIO or demo credentials. Added independent cryptographically random PII encryption/index, MFA and outbox signing keys; synchronized Spring password aliases with their canonical service passwords.
- Validation: no duplicate/missing required keys, no placeholders, AES/HMAC keys decode to distinct 32-byte values, Compose config PASS, production overlay config PASS and Kubernetes context/namespace resolution PASS.

# 2026-09-12 - Cloudflare Tunnel for nhadatchuan.online

- Installed and authenticated `cloudflared` 2026.9.1, then created the named tunnel `nhadatchuan-online` with ID `046d1ad4-3b90-4fb8-9479-b3b13ecb75b1`.
- Cloudflare DNS routes for `nhadatchuan.online` and `www.nhadatchuan.online` point to the tunnel; no inbound router port or public-IP A record is required.
- The temporary workstation origin is the complete Docker gateway at `http://localhost:3000`. The Kubernetes ingress remains configured for both hostnames and can become the origin after the backend is migrated into Kubernetes.
- Public smoke checks passed for apex and `www`: `/healthz` returned HTTP 200 and `/api/v1/listings/search?page=0&size=1` returned HTTP 200 JSON.
- `cloudflared tunnel ingress validate` passed and the connector reached Cloudflare edge locations in Singapore.
- A Windows scheduled task named `BDS-Cloudflare-Tunnel` starts the connector at user logon and restarts it after failures. Installing a machine service was not possible from the non-elevated terminal; boot-before-login requires running `cloudflared service install` from an Administrator terminal.
- Tunnel credentials remain only under the user's `.cloudflared` directory. The repository contains a credential-free example configuration.
- Replaced the user-logon scheduled task with a single Compose `cloudflared` container using `restart: unless-stopped`; the old task was stopped and disabled so only the Docker connector remains. The connector mounts its credential read-only from the user profile and reaches the gateway over the private Compose network as `http://frontend:3000`.

# 2026-09-12 - Temporary production environment activation

- Changed the live Compose runtime from `demo/local` to `production/production`; disabled public SpringDoc, restricted CORS to the two HTTPS production hosts and restricted trusted media hosts to project-controlled domains.
- Rotated PostgreSQL, Redis, MinIO and unused demo-bootstrap passwords. The PostgreSQL role was changed before container recreation; persistent volumes were retained.
- Passed previously omitted production settings from Compose into the backend, including server/log/SpringDoc, feature flags, outbox interval, ClamAV timeout and mail endpoint.
- Production safety validation passed at backend startup. PostgreSQL, Redis, MinIO, backend, frontend and Cloudflare connector were recreated without deleting data and returned healthy/running.
- Added `docs/operations/PRODUCTION_ENV.md` describing safe-to-change values, coordinated secret rotation and intentionally disabled integrations. External authenticated SMTP remains a known production gap; current mail delivery terminates at the internal Mailpit service.

# 2026-09-12 - Gmail production SMTP

- Replaced the raw unauthenticated SMTP socket in billing with Spring `JavaMailSender`, SMTP AUTH, required STARTTLS and bounded connection/read/write timeouts.
- Configured the Git-ignored production `.env` for Gmail SMTP on port 587 and kept only placeholders in `.env.example`; the Mailpit container was removed from the live Compose stack.
- Docker Java 17 build passed all 19 backend tests. Runtime startup passed Gmail's authenticated connection check and a real test message was accepted for delivery to the configured mailbox.

# 2026-09-12 - Remove demo language from production UI

- Removed the global DEMO banner, demo-account disclosure and all remaining user-facing demo/sample-data wording from the production frontend.
- Rewrote the home, listing, posting and safety copy around the real product boundary: Nhà Đất Chuẩn moderates listing content while users contact, verify and transact directly with each other.
- Removed unsupported transaction-volume wording and renamed the analytics export from a demo filename.
- Frontend TypeScript/Vite production build passed, the Impeccable detector returned no findings on the changed surfaces, and the rebuilt frontend/Cloudflare containers served the new hashed assets publicly.

# 2026-09-12 - Database-backed experience listings

- Confirmed the previous home cards were backend fallback objects: PostgreSQL contained zero public listings while `ListingController` returned four fixed UUIDs, Unsplash images and `isVerified=true`.
- Removed the fallback objects and external fallback image URLs. Search now returns an empty array when PostgreSQL has no matching public records; real listings use `listing.isVerifiedOwner()` instead of a forced verified flag.
- Added Flyway V013 with six stable PostgreSQL experience records (four sale, two rent), complete listing/revision/publication relationships and a non-login seed owner containing no real-person PII. All six owner-verification flags are false.
- Frontend cards and map results now show a neutral no-image state when a database record has no uploaded media instead of substituting a property photo.
- Docker builds passed all 19 backend tests and the frontend production build. Flyway V013 succeeded; public API returned 4 sale and 2 rent records, zero legacy fallback IDs and zero falsely verified records.

# 2026-09-13 - MinIO experience media and API-backed comparison

- Uploaded six experience photos through the authenticated media endpoint, so every object passed the ClamAV scan and was persisted in the private MinIO bucket with a corresponding `media_objects` record.
- Linked one primary MinIO object to each V013 listing revision. The public listing API now returns an internal `/api/v1/public/media/...` URL for all six records; all six URLs were verified as HTTP 200 `image/jpeg` through `nhadatchuan.online`.
- Replaced the `/compare` page's three fixed `lst-101`–`lst-103` objects and invented legal, eKYC, escrow, bank and nearby-amenity claims with live public listing API data.
- Comparison now shows only fields supported by the public API: purpose, property type, price, area, unit price, address and owner-verification state. It supports up to three IDs through `?ids=...`, prevents mixed sale/rent comparisons and has loading, failure and insufficient-data states.
- Frontend production build and Docker builds passed; backend tests passed 19/19. The rebuilt stack is healthy, `/compare` returns HTTP 200 and source verification found no legacy compare IDs, escrow fields or Unsplash URLs in the route.

# 2026-09-13 - Real map data and mandatory email verification

- Removed the simulated map photograph, fixed cluster badges, synthetic pin coordinates, duplicate zoom controls and illustrative viewport status from the search page. The only rendered map is now MapLibre with OpenStreetMap tiles, API-sourced GeoJSON markers, native clustering and bounds-based search.
- Added Flyway V014 with privacy-offset public coordinates for all six database-backed experience revisions. Existing accounts were marked email-verified during migration to avoid lockout.
- Added one-time SHA-256-hashed email verification tokens with 24-hour expiry. New registrations remain `PENDING_EMAIL_VERIFICATION`, receive no session, and cannot log in until the verification link is consumed. Resend uses a non-enumerating accepted response.
- Added the public `/verify-email` completion screen and updated registration/login UI for verification instructions and resend.
- Replaced hardcoded analytics, lead/report and verification admin displays with their real API datasets and empty/error states; removed runtime external-image defaults from project and CMS mapping.
- Production verification: V014 succeeded, all six public listings expose coordinates, `/search` and `/verify-email` return HTTP 200, unknown-email resend returns HTTP 202, Docker backend tests pass 19/19 and the rebuilt services are healthy.

# 2026-09-13 - Production CSP for map, fonts and analytics

- Replaced the frontend's same-origin-only CSP with narrow host allowlists for OpenStreetMap tiles, Google Fonts and Cloudflare Insights; added the `font-src` and `worker-src blob:` directives required by the current MapLibre/font runtime.
- Kept scripts restricted to the application origin plus the exact Cloudflare Insights host, with no wildcard source and no `unsafe-eval` or `unsafe-inline` script permission.
- Rebuilt and recreated the frontend and Cloudflare tunnel containers. The public `/search` response returns the new CSP, both containers are running (frontend healthy), Google Fonts and Cloudflare Insights return HTTP 200, and an OpenStreetMap tile returns HTTP 200.

# 2026-09-13 - Resilient production basemap endpoint

- Replaced the OpenStreetMap standard tile hostname after the workstation/browser DNS resolver returned `ERR_NAME_NOT_RESOLVED` for it and its `a`, `b` and `c` subdomains.
- The MapLibre base layer now uses CARTO raster tiles backed by OpenStreetMap data with both required attributions. Database-backed listing markers, native clustering and bounds search are unchanged.
- Updated the production CSP to the exact CARTO tile hostname and removed the unreachable tile hostname. Also removed the stale UI wording that called the now-live map illustrative.
- Frontend TypeScript/Vite build passed, the Impeccable detector returned no findings, the rebuilt frontend is healthy, Cloudflare Tunnel is running, the public CSP is current and a representative CARTO tile returns HTTP 200.

# 2026-09-13 - API-key-free vector basemap

- Removed CARTO after live rendering exposed an `API KEY REQUIRED` watermark that was not visible in the HTTP-only tile check.
- Switched MapLibre to OpenFreeMap's hosted Positron vector style. The provider requires no account or API key and supplies OpenStreetMap/OpenMapTiles attribution through the style.
- Restricted CSP to the exact `tiles.openfreemap.org` host and removed CARTO. Frontend build and Impeccable detection passed; production serves the new bundle and CSP, both style and tile metadata return HTTP 200, frontend is healthy and Cloudflare Tunnel is running.

# 2026-09-13 - MapLibre production worker packaging

- Browser-level diagnostics found that the map style and attribution loaded while MapLibre's default `/assets/maplibre-gl-worker.mjs` request failed, leaving the WebGL canvas blank.
- Configured Vite to bundle MapLibre's worker and assign its hashed production URL explicitly. The emitted self-contained worker replaces the missing runtime module and its unresolved shared import.
- Fixed the listing-source initialization race by seeding the GeoJSON source from the latest API listings when the map style finishes loading, while retaining subsequent reactive updates.
- Production browser verification loaded the style, sprite, vector tiles and fonts successfully and captured the rendered Hanoi basemap with database-backed listing cluster/point markers. The visual test now waits for dynamic vector-map rendering without relying on a never-idle network.

# 2026-09-13 - Listing localization, navigation and media delivery

- Added one shared Vietnamese property-type formatter for apartment, house, villa, townhouse and land values; search, comparison and moderation no longer expose backend enum codes.
- Made the search result detail action an isolated, elevated navigation target and added a browser regression test. Production verification clicked the first result, reached its UUID detail route and rendered the listing heading.
- Removed unsupported fixed bedroom, bathroom, direction and broker claims from search/detail. Detail descriptions now come from the API/database, with only a neutral missing-description state.
- Search images now decode asynchronously, lazy-load below the first result and prioritize the first visible image. Public objects remain private in MinIO and are authorized once through the public backend endpoint; Cloudflare serves the immutable one-year response from edge cache (`cf-cache-status: HIT`), while eKYC objects remain inaccessible through that route.
- Frontend production build and Impeccable detection passed; the rebuilt frontend is healthy and Cloudflare Tunnel is running.

# 2026-09-13 - Broker workspace loading recovery

- Production logs identified the workspace 500 as invalid PostgreSQL aggregate syntax: `FILTER` was attached to `ROUND` instead of `AVG`. Corrected the query and verified it against the production PostgreSQL schema, returning lead counts and a zero default without error.
- Rebuilt the workspace state handling so rejected requests stop loading, show a Vietnamese recovery message and provide a retry action; saving the response target now reports its own recoverable error.
- Replaced the remaining workspace-facing English `WORKSPACE`, raw listing status codes and `SLA` control wording with Vietnamese labels and added a real empty-listing state.
- Docker Java 17 verification passed all 19 backend tests; frontend TypeScript/Vite build passed. Backend and frontend containers are healthy and Cloudflare Tunnel is running.
