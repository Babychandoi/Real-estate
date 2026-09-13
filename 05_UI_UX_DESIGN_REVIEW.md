# Đánh giá UI/UX dưới vai trò Senior Product Designer

> Repository: [Babychandoi/Real-estate](https://github.com/Babychandoi/Real-estate)  
> Commit: [4f52c76](https://github.com/Babychandoi/Real-estate/commit/4f52c76f99d982aae3d7fd37dc9b76d647599b80)  
> Ngày đánh giá: 2026-09-11  
> Phạm vi: đánh giá mã nguồn React/CSS, cấu trúc màn hình, responsive và luồng tương tác; frontend đã build thành công. Chưa có kiểm thử người dùng có giám sát, đo contrast tự động hoặc visual regression trên thiết bị thật.

## 1. Kết luận nhanh

**Điểm thẩm mỹ giao diện: 6,0/10. Điểm trải nghiệm end-to-end: 3,5/10.**

Sản phẩm có nền thị giác khá hiện đại: màu navy/xanh lá phù hợp lĩnh vực tài chính–BĐS, typography rõ và hệ thống card/status tương đối nhất quán. Nhìn ở mức demo, giao diện có khả năng tạo ấn tượng ban đầu. Tuy nhiên, nhiều nút chính chưa hoạt động, mobile thiếu điều hướng, bản đồ chỉ là hình minh họa và một form báo thành công kể cả khi API lỗi. Những điểm này làm niềm tin tụt nhanh hơn bất kỳ cải tiến trang trí nào có thể bù lại.

| Tiêu chí | Điểm | Nhận xét |
|---|---:|---|
| Nhận diện và thẩm mỹ | 6,5/10 | Màu sắc, font và card hợp phân khúc |
| Kiến trúc thông tin | 5,0/10 | Nhiều phân hệ nhưng ưu tiên điều hướng chưa rõ |
| Tìm kiếm và khám phá | 4,0/10 | Filter có hình thức tốt; CTA và bản đồ chưa hoàn chỉnh |
| Chuyển đổi lead | 3,0/10 | Hai luồng liên hệ không đồng nhất, có false-success |
| Đăng tin | 6,0/10 | Wizard 4 bước dễ hiểu; upload ảnh và recovery còn yếu |
| Mobile/responsive | 4,0/10 | Grid co giãn nhưng thiếu mobile navigation và map |
| Accessibility | 3,0/10 | Thiếu nhãn, thông báo trạng thái và chiến lược focus |
| Niềm tin thương hiệu | 2,5/10 | Claim “100%”, AI/eKYC/escrow vượt quá năng lực thật |

**Quyết định UX:** phù hợp trình diễn nội bộ sau khi sửa các CTA chết; chưa phù hợp chạy quảng cáo hoặc tiếp nhận khách hàng thật.

## 2. Những điểm làm tốt

- [Tailwind theme](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/frontend/tailwind.config.js) định nghĩa font Be Vietnam Pro và palette thương hiệu phù hợp ngữ cảnh Việt Nam.
- Hệ thống card, badge trạng thái, skeleton, empty state và button tạo cảm giác sản phẩm thống nhất hơn một prototype thông thường.
- Wizard đăng tin chia thành bốn bước, có preview, estimate và quality score; cách chia nhỏ này giảm tải nhận thức cho người bán.
- Trang chi tiết gom giá, thuộc tính, pháp lý và CTA liên hệ theo cấu trúc quen thuộc của sàn BĐS.
- Nhiều vùng dùng grid/breakpoint thay vì kích thước cố định, tạo nền tảng responsive tương đối tốt.
- Khu vực admin, broker và moderation đã phân biệt theo ngữ cảnh công việc thay vì dồn hết vào một dashboard.

## 3. Năm vấn đề UX nghiêm trọng nhất

### UX-001 — CTA tìm kiếm trên trang chủ không thực hiện hành động

Ô tìm kiếm, nút tìm, chip gợi ý và một số bộ lọc trong [trang chủ](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/frontend/app/routes/_public.home.tsx) không điều hướng hoặc áp dụng truy vấn. Đây là vùng tương tác quan trọng nhất của marketplace nhưng hiện tạo “dead end”.

**Giải pháp:** dùng một search state duy nhất; Enter, nút Tìm kiếm và suggestion đều điều hướng tới `/search` với URL parameters có thể chia sẻ. Giữ lại bộ lọc khi quay lại từ trang chi tiết.

### UX-002 — Thiếu điều hướng mobile chính

Trong [root layout](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/frontend/app/root.tsx), navigation desktop bị ẩn dưới breakpoint `lg` nhưng không có hamburger hoặc bottom navigation thay thế.

**Giải pháp:** thêm header mobile với menu drawer và ba tác vụ cố định: Tìm kiếm, Đăng tin, Tài khoản. Drawer phải khóa scroll nền, trap focus, đóng bằng Escape và trả focus về nút mở.

### UX-003 — Form liên hệ có thể báo thành công giả

[LeadConsultationModal](https://github.com/Babychandoi/Real-estate/blob/4f52c76f99d982aae3d7fd37dc9b76d647599b80/frontend/app/features/lead/ui/LeadConsultationModal.tsx) gọi endpoint/payload không khớp backend, nhưng nhánh lỗi vẫn đặt trạng thái thành công. Người dùng tin rằng môi giới đã nhận yêu cầu trong khi dữ liệu có thể chưa được lưu.

**Giải pháp:** chỉ hiện success sau response 2xx có `leadId`; lỗi mạng phải giữ dữ liệu form, hiển thị nguyên nhân dễ hiểu và cho phép retry. Hợp nhất tất cả form lead thành một component và một API contract.

### UX-004 — Bản đồ tạo kỳ vọng sai

Khu vực map ở trang tìm kiếm là minh họa tĩnh và bị ẩn trên màn hình nhỏ. Người dùng không thể pan, zoom, chọn marker hay đồng bộ bounds với danh sách.

**Giải pháp:** nếu chưa tích hợp map thật, gắn nhãn “Bản đồ đang phát triển” hoặc loại bỏ khỏi bản public. Khi triển khai, hỗ trợ cluster, trạng thái selected card/marker, tìm trong khu vực này và chế độ chuyển Danh sách/Bản đồ trên mobile.

### UX-005 — Thông điệp tin cậy không có bằng chứng tương xứng

Các claim như “100% xác minh”, AI định giá có độ chính xác cụ thể, eKYC và escrow dễ khiến khách hàng hiểu đây là cơ chế production. Trong mã nguồn, nhiều phần chỉ là rule, random score hoặc chuyển trạng thái trong cơ sở dữ liệu.

**Giải pháp:** thay claim tuyệt đối bằng mô tả kiểm chứng được; hiển thị nguồn dữ liệu, ngày cập nhật, phạm vi xác minh và disclaimer. Chỉ dùng nhãn “ký quỹ/escrow” khi có đối tác thanh toán, ledger, reconciliation và quy trình tranh chấp thật.

## 4. Responsive và khả năng đọc

- Nhiều nhãn dùng cỡ `10px`, `11px` hoặc `text-xs`; khó đọc trên điện thoại và với người thị lực yếu. Body text nên có baseline 16px, metadata tối thiểu 12–14px.
- Trang tìm kiếm dùng chiều cao theo viewport và vùng `overflow-hidden`; bàn phím ảo mobile có thể che kết quả hoặc CTA. Ưu tiên document flow, `min-height` và sticky có kiểm soát.
- Bản đồ bị ẩn dưới `xl`, làm tablet/mobile mất một chế độ khám phá mà không có lựa chọn thay thế.
- Bảng quản trị thiên về desktop và dựa vào horizontal scroll. Trên mobile nên đổi mỗi dòng thành summary card với progressive disclosure.
- CTA quan trọng cần vùng chạm tối thiểu khoảng 44×44 CSS px, khoảng cách đủ lớn và trạng thái pressed/focus rõ.
- Cần kiểm thử thực tế ít nhất tại 320, 360, 390, 768, 1024 và 1440 px; portrait/landscape; iOS Safari và Android Chrome.

## 5. Accessibility

Đích nên là **WCAG 2.2 AA**, theo [W3C WCAG 2.2 Quick Reference](https://www.w3.org/WAI/WCAG22/quickref/).

Các khoảng trống thấy được từ mã nguồn:

- Nút chỉ có icon chưa có accessible name/`aria-label` nhất quán.
- Chưa thấy skip link tới nội dung chính và landmarks/heading hierarchy chưa được kiểm soát toàn cục.
- Modal/drawer chưa chứng minh focus trap, restore focus và đóng bằng bàn phím.
- Toast, lỗi form và trạng thái loading/success chưa dùng `aria-live` nhất quán.
- Placeholder đang gánh vai trò hướng dẫn ở một số input; cần label thường trực và lỗi gắn bằng `aria-describedby`.
- Trạng thái không nên chỉ phân biệt bằng màu; badge cần text/icon bổ sung.
- Contrast của màu chữ nhỏ trên nền sáng và badge pastel cần được đo bằng công cụ, không suy đoán từ mã màu.

**Definition of Done accessibility:** keyboard-only hoàn thành được search, contact và create listing; không có lỗi nghiêm trọng qua axe; test screen reader cho ba luồng chính; zoom 200% không mất nội dung/chức năng.

## 6. Tính nhất quán của design system

Hiện dự án kết hợp DaisyUI, component tự viết và nhiều utility màu trực tiếp như slate, emerald, amber, rose. Kết quả vẫn khá đẹp nhưng dễ phân kỳ khi số màn hình tăng.

Nên chuẩn hóa:

| Nhóm token/component | Quy tắc đề xuất |
|---|---|
| Color | Semantic token: surface, text, primary, success, warning, danger |
| Typography | 6 cấp cố định; không thêm tùy ý `text-[10px]` |
| Spacing/radius/shadow | Một thang token dùng chung cho card, modal, form |
| Form | Label, hint, error, required, loading và disabled dùng một API |
| Feedback | Toast cho tác vụ nhẹ; inline error cho form; dialog cho hành động nguy hiểm |
| Status | Mỗi trạng thái có label, màu và icon; mapping tập trung |
| Content | Giọng văn tiếng Việt thống nhất; tránh trộn thuật ngữ Anh không cần thiết |

Nên bổ sung Storybook hoặc trang design-system nội bộ để review component theo breakpoint, theme và trạng thái lỗi/rỗng/loading.

## 7. Thiết kế lại ba hành trình tạo chuyển đổi

### Trang chủ → kết quả tìm kiếm

1. Một ô “Khu vực, dự án hoặc từ khóa” có autocomplete thật.
2. Hai lựa chọn rõ “Mua” và “Thuê”; giá/loại hình là bộ lọc phụ.
3. Submit chuyển tới URL có query; kết quả hiển thị ngay số lượng và filter đang áp dụng.
4. Cho lưu tìm kiếm, bật thông báo và quay lại không mất vị trí cuộn.

### Chi tiết tin → liên hệ

1. Phần đầu trang ưu tiên ảnh, giá, địa chỉ, ba thuộc tính quan trọng và trạng thái xác minh.
2. CTA desktop sticky sidebar; mobile sticky bottom bar “Gọi / Nhắn / Đặt lịch”.
3. Form chỉ hỏi dữ liệu tối thiểu, giải thích ai sẽ nhận thông tin và thời gian phản hồi.
4. Success phải có mã yêu cầu, bước tiếp theo và cách hủy/kiểm soát dữ liệu cá nhân.

### Đăng tin → xuất bản

1. Autosave server-side và cho tiếp tục trên thiết bị khác.
2. Upload kéo-thả/chụp ảnh, nén, sắp xếp, chọn ảnh bìa và báo tiến trình.
3. Validation tại trường, summary lỗi đầu trang và lưu draft khi API thất bại.
4. Preview theo desktop/mobile; sau submit hiển thị SLA kiểm duyệt và timeline trạng thái.

## 8. Lộ trình ưu tiên

### P0 — trước mọi public pilot

- Làm hoạt động toàn bộ CTA chính; xóa false-success của lead.
- Thêm navigation mobile và hoàn chỉnh keyboard/focus cho modal.
- Gắn nhãn demo, hạ các claim không có bằng chứng; không nhận PII/tiền thật.
- Hợp nhất form, API contract, loading/error/success state.
- Làm smoke test tự động cho ba hành trình search, contact, create listing.

### P1 — trước beta công khai

- Map thật hoặc bỏ hẳn placeholder; thêm saved search, favorite và lịch xem.
- Chuẩn hóa design token/component; tăng cỡ chữ nhỏ và vùng chạm.
- Accessibility audit WCAG 2.2 AA, screen-reader test và kiểm tra contrast.
- Lazy-load route/image, responsive images và tối ưu Core Web Vitals.
- User test với tối thiểu 5 người mua và 5 người đăng/môi giới cho mỗi vòng.

### P2 — tối ưu tăng trưởng

- A/B test thứ tự CTA, form ngắn và sticky action mobile.
- Cá nhân hóa gợi ý dựa trên hành vi có consent, kèm khả năng tắt.
- Bổ sung trust center, hồ sơ môi giới, lịch sử chỉnh sửa và cơ chế báo tin.
- Xây content design guideline và analytics event taxonomy.

## 9. Chỉ số nghiệm thu đề xuất

| Chỉ số | Mục tiêu beta |
|---|---:|
| Search submit thành công | ≥ 99,5% |
| Contact request lưu thành công | ≥ 99,9%, không false-success |
| Tỷ lệ hoàn thành đăng tin | ≥ 65% |
| Thời gian trung vị hoàn thành đăng tin | ≤ 8 phút |
| Mobile task success: tìm và liên hệ | ≥ 85% trong usability test |
| Accessibility | WCAG 2.2 AA cho hành trình chính; 0 lỗi axe nghiêm trọng |
| LCP p75 mobile | ≤ 2,5 giây |
| INP p75 | ≤ 200 ms |
| CLS p75 | ≤ 0,1 |

## 10. Nhận định cuối

Giao diện **có nền thẩm mỹ đủ tốt để phát triển tiếp**, nhưng sức hút thương mại không chỉ đến từ màu sắc và card đẹp. Với sàn BĐS, ba yếu tố quyết định là tìm đúng sản phẩm, liên hệ chắc chắn được ghi nhận và có bằng chứng tin cậy. Cần xử lý P0 trước khi polish thêm animation hoặc trang trí; sau đó kiểm chứng bằng test người dùng và dữ liệu chuyển đổi thay vì chỉ review cảm quan.
