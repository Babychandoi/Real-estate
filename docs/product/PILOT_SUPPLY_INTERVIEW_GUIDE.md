# Hướng dẫn phỏng vấn nguồn cung và thiết kế pilot

Yêu cầu audit P-15 (EXTERNAL: giả thuyết sản phẩm phải thử với người thật). Căn cứ: audit §4.1–4.2 (giả thuyết "Tìm nhà
còn thật, thông tin rõ và hẹn xem có người phản hồi"; chọn một thành phố, một phân khúc; tuyển nhóm môi giới/chủ nhà
nòng cốt), §8.5 (luồng người đăng) và §9 giai đoạn C (pilot với nhóm nguồn cung thực). **Chưa có phỏng vấn hay pilot
nào được thực hiện**; mọi ngưỡng thành công do product đặt từ baseline, không lấy từ tài liệu này.

## 1. Giả thuyết cần kiểm chứng

| # | Giả thuyết | Bằng chứng ủng hộ | Bằng chứng bác bỏ |
|---|---|---|---|
| H1 | Môi giới/chủ nhà chịu xác nhận "còn hàng" định kỳ nếu đổi lại được lead có chất lượng | Xác nhận còn hàng đúng hạn ở `/my-listings`; nói rõ lý do sẵn sàng | Tin quá hạn không xác nhận; "không có thời gian" |
| H2 | Họ chấp nhận xác minh danh tính (KYC) để có nhãn xác minh; ma sát này không làm họ bỏ đi | Nộp KYC trong tuần đầu; không phản đối khi phỏng vấn | Bỏ dở ở bước KYC; lo ngại về giấy tờ |
| H3 | Họ phản hồi lead trên nền tảng trong thời gian chấp nhận được với người tìm nhà | Sự kiện `lead_first_response` (phút) | Lead không được xử lý; chuyển sang gọi ngoài nền tảng ngay |
| H4 | Lead từ nền tảng có tỷ lệ "đủ điều kiện" và hẹn xem thực cao hơn kênh họ đang dùng | `lead_qualified`, `appointment_confirmed`/`appointment_completed`; so sánh tự khai về kênh khác | Phần lớn lead bị đánh dấu không đủ điều kiện |
| H5 | Chủ nhà muốn tự đăng mà không bị coi là môi giới | Dùng `/become-owner`; phản hồi về nhãn vai trò | Chủ nhà nhờ môi giới đăng hộ |
| H6 | Đăng tin (4 bước, hoặc nhập CSV ở `/my-listings`) đủ nhanh so với công cụ hiện tại | Thời gian tự khai/quan sát; dùng nhập hàng loạt | Phàn nàn về số trường/ảnh bắt buộc |
| H7 | Có mức giá gói đăng tin họ sẵn sàng trả sau pilot | Gia hạn/mua gói ở `/billing` sau pilot; mức giá nêu ra | Chỉ dùng khi miễn phí |

## 2. Ai cần tuyển

| Nhóm | Phỏng vấn | Pilot | Tiêu chí |
|---|---|---|---|
| Môi giới cá nhân / đội nhỏ | 8–12 | 10–20 | Đang hoạt động trong khu vực pilot; ≥ 3 tin đang rao; trộn mua bán và cho thuê |
| Chủ nhà tự đăng | 5–8 | 5–10 (tùy chọn) | Đang bán/cho thuê ≥ 1 bất động sản trong khu vực pilot |
| Chủ đầu tư / đại lý phân phối dự án | 2–3 | Không bắt buộc | Có dự án trong khu vực; quan tâm trang `/du-an` |

Khu vực pilot: 1–2 quận của Hà Nội cùng một phân khúc (ví dụ căn hộ mua/thuê ở Cầu Giấy và Nam Từ Liêm — chủ dự án
chọn). Kênh tuyển: mạng lưới cá nhân, sàn/văn phòng môi giới trong khu vực, nhóm cộng đồng môi giới, người đăng tin trên
các nền tảng khác (liên hệ cá nhân, không thu thập dữ liệu hàng loạt). Loại: nhân viên/người thân của đội dự án.

**Screener** (5 phút): vai trò; khu vực hoạt động; số tin đang rao, tỷ lệ mua/thuê; nền tảng/công cụ đang dùng (ghi tên);
có điện thoại thông minh và dùng web hằng ngày; sẵn sàng 30–45 phút phỏng vấn (và 4 tuần pilot nếu chọn).

## 3. Kịch bản phỏng vấn bán cấu trúc (30–45 phút)

Nguyên tắc: hỏi về việc **đã làm** gần đây, không hỏi "anh/chị có muốn…"; xin ví dụ cụ thể; không giới thiệu sản phẩm
trước phần 6.

| Phần | Thời gian | Câu hỏi chính (gợi ý đào sâu) |
|---|---|---|
| 1. Mở đầu | 3' | Mục đích, ghi âm (xin đồng ý), quyền dừng, cách dùng dữ liệu |
| 2. Công việc hiện tại | 7' | Kể lại tin gần nhất anh/chị đăng: từ lúc nhận nhà tới lúc có khách. Mỗi tháng bao nhiêu tin mới, bao nhiêu giao dịch? Làm việc một mình hay theo đội? |
| 3. Công cụ | 5' | Đăng ở đâu (kể tên)? Vì sao chọn? Chi bao nhiêu/tháng cho đăng tin, đẩy tin? Quản lý khách bằng gì (Zalo, sổ, Excel, CRM)? |
| 4. Điểm đau | 8' | Lần gần nhất mất khách vì đâu? Tin hết hàng xử lý thế nào, bao lâu mới gỡ? Khách "ảo"/không nghiêm túc chiếm bao nhiêu? Bị cạnh tranh tin trùng, tin giả giá ra sao? |
| 5. Niềm tin và xác minh | 7' | Người mua tin môi giới dựa vào gì? Nếu nền tảng yêu cầu CCCD + ảnh chân dung để có nhãn xác minh, anh/chị sẽ làm không? Điều gì làm anh/chị ngần ngại? Có chứng chỉ hành nghề không, có sẵn sàng cung cấp? Đánh giá cách xác minh bằng OTP số điện thoại thay giấy tờ |
| 6. Phản hồi nhanh về sản phẩm (tùy chọn) | 7' | Cho xem trang tin, trang đăng tin, hộp thư khách quan tâm trên môi trường demo: điều gì thiếu để anh/chị dùng hằng ngày? |
| 7. Giá trị và chi trả | 5' | Hiện trả bao nhiêu cho một khách thật sự đi xem nhà? Nếu nền tảng đem lại X lead đủ điều kiện/tháng, mức nào là hợp lý? Thích trả theo gói, theo tin hay theo lead? |
| 8. Kết thúc | 3' | Có muốn tham gia pilot 4 tuần? Giới thiệu thêm ai? |

Ghi chép theo mẫu ở mục 7 trong vòng 24 giờ sau buổi.

## 4. Thiết kế pilot (4 tuần)

**Điều kiện trước khi mời người thật dùng production**
- Pilot chạy trên production với dữ liệu thật: hoàn tất sao lưu có diễn tập (`scripts/restore-drill.sh`) và cân nhắc tách
  production khỏi máy dev trước (`docs/operations/PRODUCTION_SEPARATION_PLAN.md`).
- Chốt chính sách KYC và câu thông báo thời hạn lưu (`VITE_KYC_RETENTION_NOTICE`) theo
  `docs/product/KYC_POLICY_DECISION_MEMO.md`; hiện người đăng phải KYC trước khi tạo tin.
- Gỡ nội dung UAT khỏi site công khai (`docs/operations/SEARCH_CONSOLE_STEPS.md` mục 0).
- Có người trực SLA kiểm duyệt (tin, KYC) trong giờ làm việc: tin chờ duyệt lâu sẽ làm hỏng pilot.

**Lịch**

| Tuần | Việc |
|---|---|
| 0 | Onboarding 30 phút/người (đăng ký, KYC, đăng hoặc nhập CSV tin đầu tiên); thu baseline tự khai: số tin, số lead/tuần, thời gian phản hồi ở kênh hiện tại |
| 1–4 | Dùng thật; check-in 10 phút mỗi tuần (điện thoại/Zalo); nhóm hỗ trợ trả lời trong giờ làm việc |
| 4 | Phỏng vấn kết thúc 20 phút: giữ lại hay không, vì sao, mức giá chấp nhận |

**Chỉ số** (nguồn: bảng `analytics_events`, dashboard admin `/2026/nhadatchuan/admin/analytics`; sự kiện định nghĩa ở
`backend/src/main/java/com/company/bds/analytics/application/EventCatalog.java`)

| Chỉ số | Định nghĩa | Nguồn | Ngưỡng (product đặt) |
|---|---|---|---|
| Tin được đăng | Số tin publish / người / tuần | `listing_published` | |
| Độ tươi nguồn cung | % tin được xác nhận còn hàng trước khi hết hạn (tin công khai hiệu lực 45 ngày từ lần xác nhận cuối, nhắc trước 7 và 2 ngày); số tin hết hạn; số báo "đã bán" chưa xác nhận trong 48 giờ | `listings.expires_at`, `listing_expiry_reminders`, `listings.sold_check_due_at` (chính sách ở `V045__listing_freshness_and_sold_check.sql`) | |
| Lead nhận được | Lead / tin / tuần | `lead_submitted` | |
| Thời gian phản hồi | Trung vị và p90 phút tới phản hồi đầu tiên | `lead_first_response` (`minutes`) | |
| Chất lượng lead | % lead `QUALIFIED` | `lead_qualified` | |
| Hẹn xem | Lịch được hai bên xác nhận / diễn ra / no-show | `appointment_confirmed`, `appointment_completed`, `appointment_no_show` | |
| Ma sát KYC | Người đăng ký nhưng chưa KYC sau 7 ngày | Bảng `user_kyc_profiles` (truy vấn mẫu ở KYC memo §4) | |
| Giữ chân | % người còn đăng/xác nhận tin trong tuần 4; % mua/gia hạn gói | Hoạt động theo tuần; `/billing` | |
| Phía người tìm nhà | Lịch hẹn xem xác nhận / người tìm nhà đủ điều kiện / tuần (chỉ số trung tâm audit §4.2) | `appointment_confirmed`, `search_performed` | |

Sự kiện web chỉ có `session_id`/`anonymous_id` khi người dùng đồng ý analytics; traffic nội bộ/bot bị loại khỏi dashboard.
Traffic nội bộ được nhận theo IP: đặt `APP_ANALYTICS_INTERNAL_NETWORKS` (dải IP văn phòng của đội) trước pilot để thao tác
hỗ trợ của đội không làm lệch số liệu.

## 5. Đồng ý và dữ liệu
- Phỏng vấn: đồng ý ghi âm bằng lời ở đầu buổi (ghi lại trong bản ghi) hoặc phiếu ký; lưu bản ghi ở thư mục có kiểm soát
  truy cập, không đưa vào Git; mã hóa người tham gia (`M01…` môi giới, `O01…` chủ nhà, `D01…` chủ đầu tư); thời hạn
  lưu bản ghi gốc do chủ dự án chốt.
- Pilot: thỏa thuận tham gia nêu rõ thời gian, hỗ trợ, ưu đãi (nếu có), dữ liệu được dùng để đánh giá sản phẩm, quyền rút
  khỏi pilot và yêu cầu xóa dữ liệu; điều khoản sử dụng và chính sách quyền riêng tư hiện hành vẫn áp dụng.
- Không xin bản chụp giấy tờ ngoài luồng KYC của sản phẩm; không nhận danh sách khách hàng của môi giới.

## 6. Tổng hợp

1. Mỗi buổi: một phiếu ghi chép (mục 7).
2. Sau 5–6 buổi mỗi nhóm: gom trích dẫn thành cụm (affinity) theo chủ đề: đăng tin, nguồn cung, lead, niềm tin/KYC, giá.
3. Chấm từng giả thuyết:

| Giả thuyết | Ủng hộ (số người, mã) | Bác bỏ (số người, mã) | Chưa rõ | Kết luận: giữ / sửa / bỏ | Việc tiếp theo |
|---|---|---|---|---|---|
| H1 | | | | | |
| … | | | | | |

4. Sau pilot: bảng chỉ số tuần 1–4 theo người tham gia + so với baseline tự khai; 3 quyết định sản phẩm rút ra (ví dụ
   giữ/đổi KYC, nhắc xác nhận còn hàng, mức giá gói). Không suy rộng kết quả 10–20 người thành số liệu thị trường.

## 7. Mẫu phiếu ghi chép

| Trường | Nội dung |
|---|---|
| Mã người tham gia, vai trò, khu vực, ngày | |
| Số tin đang rao / tin mới mỗi tháng / mua–thuê | |
| Công cụ đang dùng và chi phí/tháng | |
| Quy trình hiện tại (tóm tắt theo ví dụ cụ thể) | |
| 3 điểm đau chính (trích dẫn nguyên văn) | |
| Thái độ với KYC / OTP / chứng chỉ (trích dẫn) | |
| Tín hiệu tin cậy họ dùng hoặc mong muốn | |
| Mức giá/hình thức chi trả chấp nhận được | |
| Giả thuyết liên quan: H1–H7 ủng hộ / bác bỏ | |
| Đồng ý tham gia pilot / giới thiệu thêm | |
| Ghi chú của người phỏng vấn (tách khỏi lời người tham gia) | |
