# Protocol thử nghiệm khả dụng (usability test) có điều phối

Yêu cầu audit DS-14 (EXTERNAL: cần người dùng thật) và audit §8.6: "vòng đầu 5–8 người mỗi nhóm để phát hiện vấn đề định
tính, không suy diễn thành thống kê toàn thị trường". Tác vụ dựa trên route thật (`frontend/app/routes.tsx`) và các hành
trình đã có kiểm thử E2E (`frontend/tests/e2e/search.spec.ts`, `supply.spec.ts`, `journeys.spec.ts`, `engagement.spec.ts`,
`admin.spec.ts`). **Chưa buổi nào được thực hiện.**

## 1. Mục tiêu và phạm vi
- Tìm điểm khiến người dùng thất bại, chậm hoặc hiểu sai trong các hành trình chính; ưu tiên sửa theo mức nghiêm trọng.
- Kiểm tra riêng: ngưỡng giá thuê/mua, ý nghĩa nhãn "đã xác minh", cổng KYC khi liên hệ (dữ liệu cho
  `docs/product/KYC_POLICY_DECISION_MEMO.md`), đăng tin 4 bước, sửa tin bị từ chối.
- Không đo: tốc độ tải trang (dùng RUM/Lighthouse), mức độ hài lòng thị trường.

## 2. Nhóm tham gia và tuyển chọn

| Nhóm | Số người | Tài khoản dùng trong buổi |
|---|---|---|
| A. Người tìm mua/thuê | 5–8 | `USER` tổng hợp: một tài khoản đã KYC, một tài khoản chưa KYC |
| B. Môi giới / chủ nhà đăng tin | 5–8 (trộn ≥ 2 chủ nhà) | `BROKER` và `OWNER` tổng hợp đã KYC, có sẵn một tin bị từ chối và một lead mới |
| C. Kiểm duyệt viên / admin (tùy chọn) | 3–5 | `MODERATOR` / `ADMIN` trên môi trường thử (MFA do người điều phối hỗ trợ) |

**Screener** (gọi điện hoặc form 5 phút; loại nếu là nhân viên/người thân của đội dự án, người làm UX/IT chuyên nghiệp):

| Câu hỏi | Nhóm A đạt khi | Nhóm B đạt khi |
|---|---|---|
| Dự định mua/thuê nhà trong 12 tháng tới ở Hà Nội? | Có | — |
| Đã dùng trang/ứng dụng nhà đất trong 3 tháng qua? | Có (ghi tên) | Có (ghi tên) |
| Đang đăng bán/cho thuê bao nhiêu bất động sản/tháng? | — | Môi giới ≥ 3; chủ nhà ≥ 1 đang rao |
| Thiết bị thường dùng để tìm/đăng tin | Ghi lại (cân bằng mobile/desktop) | Ghi lại |
| Độ tuổi, quận sinh sống/hoạt động | Trải đều, không quá 3 người cùng nhóm tuổi | Trải đều |
| Đồng ý ghi màn hình + giọng nói | Có | Có |

Thù lao: chủ dự án quyết (ghi mức trong biên bản); trả như nhau dù hoàn thành tác vụ hay không.

## 3. Môi trường, thiết bị, dữ liệu
- **Không dùng production.** Dùng stack demo/staging có dữ liệu tổng hợp: `scripts/e2e-local.sh --serve` (máy điều phối,
  desktop) hoặc stack demo theo `.env.demo.example` có hostname truy cập được từ điện thoại. Seed bằng
  `--app.uat-seed.mode=seed --app.uat-seed.password=<≥12 ký tự>` (tài khoản `uat.<tên>@example.invalid`; mật khẩu này bị
  từ chối khi `APP_MODE=production`). Reset dữ liệu giữa các buổi (purge + seed) để mọi người thấy cùng trạng thái.
- Thiết bị: điện thoại Android/iOS hiển thị rộng ~360 px (ưu tiên điện thoại của chính người tham gia, kết nối wifi khách)
  và laptop 1366–1440 px. Mỗi nhóm: ít nhất một nửa số người làm trên mobile.
- Ghi: màn hình + giọng nói (OBS/QuickTime trên desktop, ghi màn hình hệ điều hành trên mobile); khuôn mặt không bắt buộc.
- **Không bao giờ dùng CCCD thật.** Cho tác vụ KYC, phát sẵn bộ ảnh mẫu do đội tạo (CCCD giả có chữ "MẪU" và ảnh chân dung
  của người mẫu đã đồng ý) trong thư mục trên thiết bị thử; dặn trước người tham gia không chụp giấy tờ của mình.

## 4. Đồng ý tham gia và xử lý dữ liệu cá nhân
- Phiếu đồng ý (ký trước buổi): mục đích; ghi gì (màn hình, giọng nói); ai xem (đội sản phẩm); lưu ở đâu (thư mục
  có kiểm soát truy cập, không đưa vào Git); thời hạn lưu bản ghi gốc (đề xuất 90 ngày — chủ dự án chốt); quyền dừng
  bất cứ lúc nào vẫn nhận thù lao; quyền yêu cầu xóa.
- Mã hóa người tham gia `A01…`, `B01…`, `C01…`; bảng tên ↔ mã lưu tách riêng. Ghi chú và log phát hiện chỉ dùng mã.
- Trích dẫn trong báo cáo: bỏ tên, số điện thoại, địa chỉ, tên công ty môi giới.

## 5. Kịch bản buổi (60 phút nhóm A/B, 45 phút nhóm C)

| Phần | Thời gian | Nội dung |
|---|---|---|
| Mở đầu | 5' | "Chúng tôi thử sản phẩm, không thử anh/chị. Không có câu trả lời sai. Hãy nói to điều anh/chị đang nghĩ." Xác nhận đồng ý ghi hình, bắt đầu ghi |
| Khởi động | 5' | Lần gần nhất tìm/đăng nhà làm thế nào? Dùng công cụ gì? |
| Tác vụ | 35' | Đọc từng tác vụ (mục 6) trên thẻ giấy; không gợi ý. Nếu bị kẹt > 3 phút hoặc người tham gia muốn bỏ: ghi "thất bại", chuyển tác vụ. Sau mỗi tác vụ hỏi SEQ |
| Bảng hỏi | 5' | SUS (mục 7) |
| Trao đổi | 10' | Điều gì khó nhất? Điều gì làm anh/chị tin hoặc không tin một tin đăng? Có sẵn sàng xác minh danh tính để liên hệ không, vì sao? |

Câu nhắc trung lập được phép: "Anh/chị đang tìm gì?", "Anh/chị mong điều gì xảy ra?", "Anh/chị sẽ làm gì tiếp?".
Không được: chỉ vào nút, đọc nhãn hộ, giải thích thuật ngữ.

## 6. Tác vụ và tiêu chí thành công

**Nhóm A — người tìm mua/thuê** (bắt đầu chưa đăng nhập ở `/`)

| # | Tác vụ (đọc cho người tham gia) | Thành công khi |
|---|---|---|
| A1 | "Tìm căn hộ để mua ở Cầu Giấy, giá tối đa 4 tỷ." | Danh sách `/search` có bộ lọc mua + khu vực + giá đúng; mở được ≥ 1 tin phù hợp |
| A2 | "Tìm nhà cho thuê dưới 15 triệu/tháng." | Bộ lọc thuê với đơn vị triệu/tháng; người tham gia đọc đúng giá thuê trên thẻ tin |
| A3 | "Chỉ xem tin đã được xác minh. Nhãn xác minh trên tin này nghĩa là gì?" | Bật đúng bộ lọc; giải thích đúng phạm vi (danh tính người đăng / sở hữu), không cho rằng "đảm bảo pháp lý" |
| A4 | "So sánh hai căn bạn thích nhất." | Mở `/compare` với 2 tin; nêu được một điểm khác nhau |
| A5 | "Lưu một tin và lưu cả tìm kiếm này để nhận thông báo hàng tuần." | Tin và tìm kiếm xuất hiện ở `/saved` với tần suất đã chọn |
| A6 | (tài khoản đã KYC) "Hẹn đi xem căn này vào cuối tuần." | Gửi yêu cầu hẹn xem, thấy "Yêu cầu đã được ghi nhận"; tìm lại được ở `/my-inquiries` |
| A7 | (tài khoản chưa KYC) "Liên hệ người đăng tin này." | Hiểu vì sao bị yêu cầu xác minh, mở `/kyc`, nói được cần chuẩn bị gì và mất bao lâu; dùng ảnh mẫu nếu đi tiếp |
| A8 | "Môi giới đã đề xuất giờ xem nhà. Hãy chọn một khung giờ." (người điều phối đề xuất trước bằng tài khoản môi giới) | Xác nhận khung giờ trong `/my-inquiries` |

**Nhóm B — môi giới / chủ nhà** (đã đăng nhập)

| # | Tác vụ | Thành công khi |
|---|---|---|
| B1 | "Đăng tin bán căn hộ 72,5 m², giá 3,95 tỷ, sổ hồng, ở Cầu Giấy, kèm ảnh." | Qua 4 bước `/listings/new`, xem trước đúng giá "3,95 tỷ", gửi duyệt; tin ở tab "Chờ duyệt" |
| B2 | "Đóng trình duyệt giữa chừng rồi quay lại làm tiếp." | Tìm lại bản nháp tự lưu, dữ liệu còn nguyên |
| B3 | "Một tin của bạn bị từ chối. Tìm lý do, sửa và gửi lại." | Đọc đúng lý do, sửa đúng trường, gửi lại thành công |
| B4 | "Có khách mới quan tâm. Hãy đề xuất lịch xem nhà." | Từ `/my-leads` (hoặc `/broker/workspace` với môi giới) gửi được đề xuất lịch |
| B5 | "Xác nhận tin X vẫn còn bán; tạm dừng tin Y." | Trạng thái hai tin đổi đúng ở `/my-listings` |
| B6 | "Mua gói đăng tin và báo đã chuyển khoản." (tiền giả lập trên môi trường thử) | Tạo đơn ở `/billing`, thấy trạng thái chờ đối soát |
| B7 | (chủ nhà, tài khoản `USER`) "Bạn muốn tự đăng bán nhà của mình." | Tìm ra `/become-owner` và hiểu khác biệt chủ nhà / môi giới |

**Nhóm C — kiểm duyệt viên / admin** (`/2026/nhadatchuan/admin`)

| # | Tác vụ | Thành công khi |
|---|---|---|
| C1 | "Nhận một tin chờ duyệt và phê duyệt nó." | Nhận xử lý, đối chiếu, phê duyệt có lý do ở `moderation` |
| C2 | "Từ chối một tin sao cho người đăng biết phải sửa gì." | Lý do cụ thể, đúng trường |
| C3 | "Duyệt một hồ sơ xác minh danh tính." | Mở giấy tờ (nhập lại mật khẩu + lý do) ở `verification`, ra quyết định có mã lý do |
| C4 | "Xử lý một báo cáo vi phạm." | Đóng báo cáo với ghi chú ở `leads-and-reports`/`reports` |
| C5 | "Một đơn chuyển khoản lệch số tiền. Xử lý." | Lưu quyết định ngoại lệ có ghi chú ở `billing` |

## 7. Đo lường
- **Hoàn thành** mỗi tác vụ: thành công / thành công có trợ giúp / thất bại.
- **Thời gian** từ lúc đọc xong tác vụ tới lúc người tham gia nói "xong" (ghi từ bản ghi).
- **Lỗi**: thao tác sai dẫn tới đường vòng hoặc kết quả sai; **hiểu sai**: câu nói cho thấy hiểu sai nhãn/giá/trạng thái.
- **SEQ** sau mỗi tác vụ: "Nhìn chung, tác vụ này khó hay dễ?" 1 (rất khó) – 7 (rất dễ).
- **SUS** cuối buổi, thang 1 (hoàn toàn không đồng ý) – 5 (hoàn toàn đồng ý):
  1. Tôi nghĩ tôi sẽ muốn dùng trang web này thường xuyên.
  2. Tôi thấy trang web phức tạp một cách không cần thiết.
  3. Tôi thấy trang web dễ sử dụng.
  4. Tôi nghĩ tôi cần người kỹ thuật hỗ trợ mới dùng được trang web này.
  5. Tôi thấy các chức năng của trang web được kết hợp tốt với nhau.
  6. Tôi thấy trang web có quá nhiều điểm không nhất quán.
  7. Tôi nghĩ hầu hết mọi người sẽ học cách dùng trang web này rất nhanh.
  8. Tôi thấy trang web rất rườm rà, khó thao tác.
  9. Tôi cảm thấy tự tin khi dùng trang web này.
  10. Tôi cần học nhiều thứ trước khi có thể dùng được trang web này.

  Điểm SUS = (Σ(câu lẻ − 1) + Σ(5 − câu chẵn)) × 2,5. Với 5–8 người, báo cáo từng điểm và trung vị; không suy ra phân vị
  thị trường.

## 8. Mức nghiêm trọng của phát hiện

| Mức | Định nghĩa | Xử lý |
|---|---|---|
| 4 — Chặn | Người dùng không hoàn thành được tác vụ chính, hoặc hiểu sai gây thiệt hại (giá, phạm vi xác minh, dữ liệu cá nhân) | Sửa trước khi mở rộng/pilot |
| 3 — Nghiêm trọng | Hoàn thành nhưng mất nhiều thời gian/cần trợ giúp; ≥ 2 người gặp | Sửa trong sprint kế tiếp |
| 2 — Vừa | Gây chậm hoặc bối rối nhưng tự vượt qua | Đưa vào backlog có ưu tiên |
| 1 — Nhẹ | Thẩm mỹ, chữ, nhất quán | Gom sửa |
| 0 — Không phải vấn đề | Ý kiến cá nhân không ảnh hưởng tác vụ | Ghi lại, không xử lý |

## 9. Mẫu log phát hiện

| ID | Nhóm/tác vụ | Người tham gia (mã) | Thiết bị | Mô tả quan sát (hành vi + trích dẫn) | Route | Mức | Số người gặp | Đề xuất | Issue |
|---|---|---|---|---|---|---|---|---|---|
| F-01 | A2 | A03, A05 | mobile 360 | "15 triệu là giá cả căn à?" — đọc giá thuê như giá bán | `/search` | 4 | 2/6 | Hiện đơn vị "/tháng" cạnh giá | |

Tổng hợp sau mỗi nhóm: bảng tác vụ × người tham gia (thành công/trợ giúp/thất bại, thời gian, SEQ), điểm SUS từng người,
5 phát hiện nghiêm trọng nhất. Sửa xong mức 3–4 thì chạy lại vòng ngắn (3–5 người) với đúng các tác vụ đó.

## 10. Hậu cần
- Người điều phối (một người hỏi) + người ghi chép (ghi giờ, hành vi) — không quá 2 người quan sát trong phòng.
- Chạy thử toàn bộ kịch bản với một người nội bộ trước buổi đầu; kiểm tra tài khoản, dữ liệu seed, ảnh mẫu KYC, thiết bị ghi.
- Trước mỗi buổi: reset dữ liệu, đăng xuất, xóa lịch sử trình duyệt, tắt thông báo trên thiết bị.
- Sau buổi: lưu bản ghi vào thư mục có kiểm soát, cập nhật log trong 24 giờ, xóa ảnh mẫu/dữ liệu khỏi thiết bị dùng chung.
