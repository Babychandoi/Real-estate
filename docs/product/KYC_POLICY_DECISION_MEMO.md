# Memo quyết định: yêu cầu KYC (xác minh danh tính) trên Nhà Đất Chuẩn

Yêu cầu audit F17.5 (EXTERNAL: product + security + pháp lý chốt). Audit F17: "cân nhắc phone verification + consent/risk
controls cho liên hệ thông thường, giữ KYC ở bước có rủi ro cao. Quyết định thay yêu cầu KYC phải do product/security chốt
sau đánh giá rủi ro; không tự bỏ bảo vệ." **Cho tới khi có quyết định ở mục 6, hệ thống giữ nguyên hiện trạng (phương án A).**
Memo này không đưa kết luận pháp lý; mọi điểm về Nghị định 13/2023/NĐ-CP và văn bản liên quan cần luật sư xác nhận.

## 1. Hiện trạng (đối chiếu mã nguồn ngày 03/10/2026)

| Khía cạnh | Thực tế trong repo | Nguồn |
|---|---|---|
| Cơ chế | KYC thủ công: người dùng nộp số CCCD (12 số), họ tên, ngày sinh, địa chỉ, ảnh CCCD mặt trước/sau và ảnh chân dung; nhân sự duyệt/từ chối/thu hồi có mã lý do | `verification/api/KycController.java`, `SubmitKycRequest.java`; route `/kyc`, admin `/2026/nhadatchuan/admin/verification` |
| Cờ production | `FEATURE_REAL_KYC=false`; production từ chối khởi động nếu bật vì chưa có adapter nhà cung cấp eKYC được nghiệm thu | `docs/operations/PRODUCTION_ENV.md`, `shared/config/ProductionSafetyValidator.java` |
| Ai bị yêu cầu | (1) Người gửi yêu cầu liên hệ **và** người đăng tin nhận liên hệ đều phải VERIFIED còn hạn; (2) tạo/sửa nháp và gửi duyệt tin cần chủ tin VERIFIED (`app.listing.require-verified-kyc`, mặc định `true` trong mã) | `lead/application/LeadApplicationService.java` (`KYC_REQUIRED`, `OWNER_KYC_REQUIRED`), `listing/application/service/ListingApplicationService.java` |
| Hiệu lực | 24 tháng từ ngày duyệt; nhắc trước 30 ngày; hết hạn tự động | `TrustDecisionService.IDENTITY_VALIDITY`, `TrustExpiryTask` |
| Lưu trữ | Số CCCD mã hóa AES-GCM + blind index; ảnh ở bucket MinIO chung `bds-listings` với `visibility = 'KYC_PRIVATE'`, chỉ phục vụ qua `/api/v1/media/kyc/…`, không qua pipeline ảnh công khai | `V004__create_verification_tables.sql`, `media/MediaStorageService.java` |
| Nhân sự xem giấy tờ | Nhập lại mật khẩu + lý do ≥ 5 ký tự; quyền xem 10 phút (`kyc_document_access_grants`); mỗi lần mở ghi `kyc_access_log` | `KycDocumentAccessService.java`, `AuthService.KYC_DOCUMENT_ACCESS_TTL` |
| Thời hạn lưu ảnh | **Chưa có** chính sách và chưa có job xóa; UI hiện "chưa có dữ liệu chính sách" khi `VITE_KYC_RETENTION_NOTICE` trống | `features/kyc/KycScopePanel.tsx`, `.env.production.example` |
| Sự cố liên quan | Bản sao lưu volume MinIO (gồm ảnh KYC) từng nằm trong Git LFS | `docs/ops/BACKUP_CLASSIFICATION.md`, `docs/ops/GIT_HISTORY_REMEDIATION_PLAN.md` |
| Đo lường | Sự kiện `kyc_required_shown` (context `lead_form`), funnel "Form liên hệ → yêu cầu xác minh → gửi" | `analytics/application/EventCatalog.java`, `lead/api/FunnelAnalyticsController.java` |
| Chưa có | Xác minh số điện thoại bằng OTP cho tài khoản; kiểm tra chứng chỉ hành nghề môi giới; sự kiện `kyc_submitted`/`kyc_page_viewed` | grep backend/frontend |

Hệ quả: mọi người tìm nhà muốn liên hệ đều phải nộp CCCD + chân dung và chờ duyệt thủ công — điểm ma sát lớn nhất mà
audit nêu.

## 2. Phương án

| | A. Giữ nguyên | B. KYC chỉ cho bên đăng tin | C. OTP điện thoại + kiểm tra môi giới | D. Tích hợp nhà cung cấp eKYC |
|---|---|---|---|---|
| Mô tả | Như mục 1 | Chủ nhà/môi giới vẫn KYC; người tìm nhà liên hệ bằng email đã xác minh + giới hạn tần suất hiện có; KYC người tìm nhà chỉ ở bước rủi ro cao (nếu sau này có đặt cọc) | Người tìm nhà: OTP số điện thoại; chủ nhà: OTP + KYC; môi giới: OTP + KYC + đối chiếu chứng chỉ hành nghề | Đọc chip CCCD/NFC, liveness, so khớp khuôn mặt tự động qua nhà cung cấp (ví dụ VNPT, FPT.AI, Viettel — cần báo giá) |
| Ma sát người dùng | Cao nhất: nộp giấy tờ + chờ duyệt trước khi liên hệ | Thấp cho người tìm nhà; không đổi cho bên đăng | Thấp–trung bình (OTP vài chục giây); bên đăng như A | Trung bình: vẫn chụp giấy tờ nhưng có kết quả trong vài phút, không chờ người duyệt |
| Rủi ro dữ liệu cá nhân (cần luật sư) | Thu và giữ ảnh giấy tờ + chân dung của **mọi** người liên hệ — lượng dữ liệu nhạy cảm lớn nhất; chưa có thời hạn lưu | Giảm mạnh số người bị thu giấy tờ (nguyên tắc tối thiểu hóa) | Thêm số điện thoại (đã có, mã hóa) + chứng chỉ; giảm ảnh giấy tờ của người tìm nhà | Thêm bên xử lý dữ liệu (hợp đồng xử lý, có thể chuyển dữ liệu ra ngoài); dữ liệu sinh trắc học |
| Rủi ro lừa đảo/spam | Thấp nhất ở phía người liên hệ | Người liên hệ chỉ ràng buộc bằng email; cần giới hạn tần suất và báo cáo lạm dụng chặt | Số điện thoại thật làm khóa chống spam; bên đăng như A | Như A, nhanh hơn |
| Chi phí vận hành | Người duyệt cho mọi hồ sơ; nhắc gia hạn 24 tháng | Ít hồ sơ hơn (chỉ bên đăng) | Phí SMS mỗi OTP + đăng ký brandname (cần báo giá); duyệt bên đăng như A | Phí mỗi lượt eKYC (cần báo giá); ít người duyệt; vẫn cần xử lý ngoại lệ |
| Công sức kỹ thuật (ước lượng thô) | 0 | Nhỏ: 1–3 người-ngày | Trung bình: 5–10 người-ngày (OTP, nhà cung cấp SMS, giới hạn tần suất, UI) | Lớn: 10–20 người-ngày + nghiệm thu nhà cung cấp |
| Thay đổi mã/cờ | — | Bỏ `requireVerifiedKyc(requesterId…)` trong `LeadApplicationService` sau một cờ mới (ví dụ `app.lead.require-requester-kyc`); khi cờ tắt, bỏ cổng `kycGate` ở `routes/_public.listings.$listingId.tsx` (backend không còn trả `KYC_REQUIRED` nên nhánh xử lý trong `LeadConsultationModal.tsx` không kích hoạt); giữ `OWNER_KYC_REQUIRED` | Module OTP mới + adapter SMS; cột `phone_verified_at`; điều kiện liên hệ đổi sang "phone verified"; trường chứng chỉ trên hồ sơ môi giới + hàng đợi duyệt | Adapter nhà cung cấp; cho phép `FEATURE_REAL_KYC=true` trong `ProductionSafetyValidator` sau nghiệm thu; webhook kết quả; lưu tối thiểu kết quả thay vì ảnh |

Có thể kết hợp: ví dụ B ngay (rẻ, giảm ma sát lớn nhất) rồi C hoặc D khi có số liệu. Mọi phương án đều cần một **thời hạn
lưu ảnh giấy tờ** và job xóa tương ứng — hiện chưa có.

## 3. Câu hỏi cần luật sư trả lời (không tự kết luận)
1. Ảnh CCCD, ảnh chân dung, kết quả so khớp khuôn mặt thuộc loại dữ liệu cá nhân nào theo Nghị định 13/2023/NĐ-CP và
   Luật Bảo vệ dữ liệu cá nhân 2025 (theo thông tin công khai có hiệu lực từ 01/01/2026 — cần xác nhận văn bản hiện hành)?
   Căn cứ xử lý (đồng ý hay căn cứ khác) và nội dung thông báo bắt buộc?
2. Mục đích "chống lừa đảo trên nền tảng tin đăng" có đủ để yêu cầu KYC với **người tìm nhà**, hay chỉ với bên đăng tin?
3. Thời hạn lưu tối đa ảnh giấy tờ và dữ liệu sau khi tài khoản bị xóa/từ chối/hết hạn.
4. Với phương án D: hồ sơ đánh giá tác động, hợp đồng với bên xử lý, chuyển dữ liệu ra nước ngoài (nếu máy chủ nhà cung
   cấp ở nước ngoài).
5. Với môi giới: điều kiện hành nghề theo Luật Kinh doanh bất động sản hiện hành và nền tảng có phải kiểm tra không.
6. Nghĩa vụ phát sinh từ việc ảnh KYC từng nằm trong bản sao lưu trên Git LFS (xem `docs/ops/BACKUP_CLASSIFICATION.md` §4).

## 4. Dữ liệu cần để quyết định

Chạy truy vấn trên bản khôi phục (`scripts/restore-drill.sh --keep`, rồi kết nối project `bds-drill`) hoặc bằng role chỉ
đọc; loại dữ liệu tổng hợp UAT (id bắt đầu `ee5eed`, email `@example.invalid`).

| Dữ liệu | Nguồn | Trạng thái |
|---|---|---|
| Tỷ lệ bỏ cuộc tại bước KYC khi liên hệ | Admin `/2026/nhadatchuan/admin/analytics` → funnel "Form liên hệ → yêu cầu xác minh → gửi"; API `GET /api/v1/analytics/lead-funnel?days=30` (tối đa 92 ngày) | Có; chỉ đếm sự kiện ghi nhận được, không gồm traffic nội bộ/bot |
| Số hồ sơ theo trạng thái | Truy vấn Q1 | Có (bảng) |
| Tỷ lệ và lý do từ chối/thu hồi | Truy vấn Q2 | Có (bảng) |
| Thời gian duyệt (p50/p90) | Truy vấn Q3 | Có (bảng); chưa có dashboard |
| Khối lượng mở giấy tờ của nhân sự | `SELECT date_trunc('month', created_at), count(*) FROM kyc_access_log GROUP BY 1 ORDER BY 1;` | Có (bảng) |
| Báo cáo lừa đảo/vi phạm và trạng thái KYC của người đăng | Truy vấn Q4 | Có (bảng); phân loại "lừa đảo" phụ thuộc `category` người báo chọn |
| Người mở trang `/kyc` nhưng không nộp | — | **Chưa có** sự kiện; cần thêm `kyc_page_viewed`/`kyc_submitted` vào `EventCatalog` nếu muốn đo |
| Chất lượng lead (guardrail) | Sự kiện `lead_qualified`; dashboard admin | Có khi môi giới đánh dấu lead |
| Thời hạn lưu được pháp lý duyệt | Luật sư | **Chưa có** |
| Ý kiến người dùng | `docs/product/USABILITY_TEST_PROTOCOL.md`, `docs/product/PILOT_SUPPLY_INTERVIEW_GUIDE.md` | Chưa thực hiện |

```sql
-- Q1: hồ sơ KYC theo trạng thái
SELECT status, count(*) FROM user_kyc_profiles
WHERE id::text NOT LIKE 'ee5eed%' GROUP BY status;

-- Q2: quyết định KYC theo tháng, loại và mã lý do
SELECT date_trunc('month', created_at) AS month, decision, reason_code, count(*)
FROM trust_decisions WHERE subject_type = 'KYC' AND id::text NOT LIKE 'ee5eed%'
GROUP BY 1, 2, 3 ORDER BY 1, 2, 4 DESC;

-- Q3: thời gian từ lần nộp gần nhất tới lúc duyệt (giờ)
SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM verified_at - created_at) / 3600) AS p50_h,
       percentile_cont(0.9) WITHIN GROUP (ORDER BY extract(epoch FROM verified_at - created_at) / 3600) AS p90_h,
       count(*)
FROM user_kyc_profiles WHERE status = 'VERIFIED' AND verified_at IS NOT NULL AND id::text NOT LIKE 'ee5eed%';

-- Q4: báo cáo vi phạm theo loại, và người đăng có KYC VERIFIED hay không
SELECT r.category, (k.status = 'VERIFIED') AS owner_verified, count(*)
FROM listing_reports r JOIN listings l ON l.id = r.listing_id
LEFT JOIN user_kyc_profiles k ON k.user_id = l.owner_id
WHERE r.id::text NOT LIKE 'ee5eed%' GROUP BY 1, 2 ORDER BY 3 DESC;
```

## 5. Tiêu chí gợi ý (product đặt ngưỡng từ baseline, không lấy từ memo này)
- Chuyển sang B/C nếu funnel cho thấy phần lớn người được yêu cầu KYC không gửi được liên hệ **và** báo cáo lừa đảo từ phía
  người liên hệ thấp.
- Giữ A hoặc chọn D nếu báo cáo lừa đảo/quấy rối từ phía người liên hệ đáng kể.
- Sau khi đổi: theo dõi 4 tuần tỷ lệ form → lead, tỷ lệ `lead_qualified`, báo cáo lạm dụng; có ngưỡng quay lại phương án cũ.

## 6. Bảng quyết định (điền khi chốt)

| Mục | Giá trị |
|---|---|
| Người quyết định (product) | |
| Người duyệt (security) | |
| Ý kiến pháp lý (người, ngày, số văn bản) | |
| Ngày quyết định | |
| Phương án chọn (A/B/C/D/kết hợp) | |
| Phạm vi áp dụng (người tìm nhà / chủ nhà / môi giới) | |
| Thời hạn lưu ảnh giấy tờ (đã duyệt) | |
| Thời hạn lưu sau khi tài khoản bị xóa/từ chối | |
| Nội dung `VITE_KYC_RETENTION_NOTICE` (nguyên văn đã duyệt) | |
| Chỉ số theo dõi sau thay đổi và ngưỡng quay lại | |
| Ngày xem xét lại | |
