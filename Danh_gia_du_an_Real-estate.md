# Đánh giá dự án `Babychandoi/Real-estate`

**Nguồn:** https://github.com/Babychandoi/Real-estate
**Ngày tổng hợp:** 14/09/2026, dựa trên các báo cáo audit có sẵn trong repo (snapshot commit `8afba2c`, đánh giá ngày 13/09/2026) + README.
**Lưu ý phạm vi:** đây là đánh giá qua đọc mã nguồn/tài liệu, không phải kết quả pentest hay kiểm thử tải thực tế trên hệ thống đang chạy.

> **Cập nhật trạng thái 14/09/2026:** phần đánh giá bên dưới mô tả snapshot cũ `8afba2c` và được giữ làm lịch sử phát hiện. Không dùng nguyên trạng phần này để kết luận về nhánh `main` hiện tại. Các remediation đã được đối chiếu lại tới commit `0b394fe` và thay đổi tiếp theo trong cùng đợt xử lý này.

## Trạng thái sau khắc phục

| Nhóm phát hiện cũ | Trạng thái hiện tại |
|---|---|
| Setup/env, Cloudflare và Mailpit | Đã tách mẫu demo/production, thống nhất `APP_ALLOWED_ORIGINS`, tunnel dùng cấu hình tương đối và Mailpit có service Compose. |
| SEC-01 cleanup KYC | Đã bảo vệ object đang được hồ sơ eKYC/xác minh tham chiếu; media được kiểm tra tồn tại, owner và trạng thái scan. |
| SEC-02 BOLA xác minh | Đã kiểm tra chủ sở hữu tin và trạng thái eKYC trước khi nộp/duyệt. |
| SEC-03 tin ẩn còn trong search | Kết quả Elasticsearch được hydrate lại qua PostgreSQL và chỉ trả tin `ACTIVE`; sort có khóa phụ ổn định. |
| Lead của chủ tin | USER/BROKER sở hữu tin xem được lead, reveal số điện thoại có kiểm tra quyền và consent, cập nhật trạng thái chăm sóc. |
| Badge/pháp lý/vị trí/AI giả | Đã bỏ fallback tọa độ, badge sai, kết luận pháp lý giả và bộ định giá/điểm AI-GIS hardcode. API chưa có mô hình thật trả HTTP 501 rõ ràng. |
| Dữ liệu mẫu | Được nhận diện là showcase, chặn gửi lead thật và gắn nhãn rõ; không giả làm giao dịch thật. |
| Project/CMS/Analytics | UI dùng DTO/API thật; hành động chưa có backend được bỏ khỏi giao diện thay vì hiện toast thành công. Analytics nói rõ hai tầng chưa được thu thập. |
| Ảnh chi tiết | Dùng `imageUrls` thật từ API và kho MinIO; không dùng URL ảnh fallback trực tiếp. |
| Báo cáo vi phạm | Người dùng gửi từ chi tiết tin; admin có thao tác tạm ẩn, giải quyết và bác bỏ qua API thật, bắt buộc ghi chú. |
| eKYC trong đăng tin | Đã bỏ toggle vô hiệu hóa và nhãn “đã gửi” giả; người dùng được dẫn tới hồ sơ eKYC riêng để gửi hai mặt CCCD và ảnh chân dung. |
| Sửa revision tin | Luồng sửa đầy đủ chưa có contract đọc revision riêng tư; nút dẫn sai sang tạo tin mới đã được ẩn và ghi rõ chưa mở. |

### Kết luận phát hành cập nhật

- Các lỗi P0 được nêu trong snapshot cũ không còn là căn cứ để tuyên bố `No-Go` cho bản hiện tại.
- Chưa được phép tuyên bố hệ thống đạt hàng triệu kết nối, SLA production hoặc HA đa host: vẫn cần load/soak trên hạ tầng đích, backup–restore drill có số liệu RPO/RTO, failover nhiều host và pentest độc lập.
- Website là nền tảng đăng tin và liên hệ; không cung cấp mua bán/đặt cọc bất động sản. Vì vậy màn Contracts cũ không được khôi phục như một yêu cầu sản phẩm.

---

## 0. Đánh giá tổng quan độ hoàn thiện

Đây **không phải** dự án chỉ có UI giả — có backend Spring Boot thật, DB PostgreSQL/PostGIS, auth thật, upload ảnh MinIO thật, và nhiều luồng nghiệp vụ đã nối API thật.

Tuy nhiên sản phẩm đang ở trạng thái **"pha trộn"**: một phần chạy thật, một phần chỉ đổi state cục bộ (giả), và một phần **hiển thị thông tin sai sự thật** (ví dụ hiện "đã xác thực" dù chưa xác thực).

> Rủi ro lớn nhất không phải "nút không đổi màu", mà là nút/tuyên bố khiến người dùng tin rằng dữ liệu đã được lưu, xác thực, làm mờ hoặc đối soát trong khi hệ thống chưa thực hiện việc đó.

---

## 1. Vai người dùng — trải nghiệm setup & mua bán sản phẩm

**Kết luận:** trải nghiệm đã vượt mức giao diện mô phỏng, nhưng chuỗi *tìm nhà → gửi liên hệ → người bán gọi lại* chưa hoàn chỉnh. Đủ làm bản beta có hỗ trợ vận hành, **chưa nên** mở rộng thu hút khách hàng thật hay thu phí đại trà.

### Setup không "nhanh gọn" như README hứa
- README bảo copy `.env.example` rồi chạy, nhưng file mẫu lại đặt sẵn `APP_MODE=production`, đòi khóa PII/MFA/SMTP thật, và `APP_PUBLIC_BASE_URL` vẫn là placeholder.
- Script preflight kiểm tra biến `APP_SECURITY_ALLOWED_ORIGINS`, trong khi Compose/Spring Boot thực tế dùng tên biến khác (`APP_ALLOWED_ORIGINS`) → **preflight production luôn từ chối chạy dù điền đúng giá trị ứng dụng.**
- Cloudflare Tunnel nằm trong stack mặc định nhưng cần file config/đường dẫn gắn với máy tác giả cụ thể — máy khác (Windows/Linux mới) sẽ không khởi động đầy đủ.
- SMTP fallback trỏ tới service `mailpit` nhưng Compose không hề khai báo service này; mẫu `.env` lại yêu cầu tài khoản Gmail thật.

→ Người dùng thường (không phải dev) **sẽ không tự "nhanh gọn" chạy được** bản demo chỉ bằng đổi vài mật khẩu.

### Vấn đề chặn chuyển đổi bán hàng nghiêm trọng nhất
Người mua gửi yêu cầu liên hệ thành công, nhưng **người bán (chủ tin) hiện không có cách nào lấy được số điện thoại thật** — API chỉ trả số đã che (`maskedPhone`), chưa có endpoint giải mã/reveal nào tồn tại trong source. Ngoài ra tài khoản USER (chủ tin thường) còn bị chặn ở tầng bảo mật (`/api/v1/leads/**` không cho role USER) trước khi hệ thống kịp kiểm tra quyền sở hữu tin — nên ngay cả khi có API, chủ tin thường vẫn không vào được khu vực quản lý lead của chính mình.

### Các vấn đề khác
- Tin đã bị ẩn/khóa vẫn có thể xuất hiện trên kết quả tìm kiếm (do Elasticsearch không xoá index khi đổi trạng thái).
- Sắp xếp (`sortBy`) được gửi lên nhưng cả Elasticsearch lẫn fallback JPA đều không dùng, luôn trả theo ngày tạo.
- Chống spam lead đếm toàn bộ lịch sử theo số điện thoại (không có cửa sổ thời gian) → khách hợp lệ có thể bị khóa vĩnh viễn sau 10 lần gửi.
- 6 tin "trải nghiệm" (demo) được chèn thẳng vào dữ liệu sản xuất qua migration Flyway, tài khoản seed không đăng nhập được nhưng khách vẫn có thể gửi lead vào các tin này.

---

## 2. Vai Senior Security

**Kết luận:** chưa đạt điều kiện mở production rộng rãi với hồ sơ cá nhân thật, dù nền tảng bảo mật cơ bản (BCrypt cost 12, AES-GCM cho PII, MFA, deny-by-default) đã tốt hơn hẳn bản trước.

### 3 lỗ hổng High/P0 — chặn phát hành
| ID | Vấn đề |
|---|---|
| SEC-01 | Tài liệu KYC đã nộp có thể bị dọn dẹp như ảnh mồ côi và **xóa khỏi MinIO sau 24h** vì cơ chế cleanup không loại trừ tài liệu KYC đang được tham chiếu |
| SEC-02 | Lỗi BOLA — một người dùng có thể **nộp hồ sơ xác minh cho tin đăng không thuộc sở hữu của mình** vì hệ thống chỉ kiểm tra listing tồn tại, không so khớp chủ sở hữu |
| SEC-03 | Tin đã bị **ẩn/khóa vẫn xuất hiện trong tìm kiếm công khai** vì đồng bộ Elasticsearch không xóa document khi tin đổi trạng thái |

### Các lỗ hổng P1 khác
- Rate-limit dễ bị né qua header `X-Forwarded-For` không được chuẩn hóa tại proxy tin cậy; endpoint báo cáo vi phạm công khai lại bị kiểm tra nhầm path.
- Toàn bộ admin/moderator **dùng chung một secret MFA** duy nhất — không cá nhân hóa, không chống replay trong cửa sổ timestep.
- Audit log ghi hash chain nhưng chưa thật sự chống sửa (không khóa chuỗi, hai request có thể chọn cùng predecessor).
- Backend được cấp quyền **root MinIO** thay vì quyền giới hạn theo bucket; chưa có key rotation cho mã hóa PII.
- KYC chỉ kiểm tra định dạng chuỗi URL, không xác minh object/owner/trạng thái scan tồn tại thật.

**Quyết định phát hành theo báo cáo gốc:** *No-Go* cho production có dữ liệu KYC thật cho tới khi các mục P0 được xử lý và kiểm chứng bằng test thật trên PostgreSQL + Elasticsearch + MinIO.

---

## 3. Vai Senior đóng gói sản phẩm — tính tương tác & hoàn thiện

Đây là phần trả lời trực tiếp câu hỏi của bạn về **hardcode và nút không hoạt động**. Báo cáo gốc quét toàn bộ 41 file TSX frontend, phát hiện 251 phần tử tương tác (125 button, 49 link, 67 input/select, 7 form).

### Nhóm 1 — Thông tin tạo niềm tin sai (nghiêm trọng nhất)
- Badge **"Đã xác thực người đăng"** có thể hiện sai vì trang chi tiết dùng logic khác trang tìm kiếm để tính cờ này — 6 tin seed thực tế đã bị gán sai trong dữ liệu.
- Toggle **"làm mờ vị trí 100m" không có tác dụng gì** — biến điều khiển không nằm trong payload gửi lên, tọa độ thật vẫn được truyền nguyên vẹn.
- Bàn kiểm duyệt tin **luôn hiển thị "Sổ đỏ chứng thực", "0 vi phạm", "không trùng tọa độ"** cho MỌI hồ sơ được chọn, mà không hề đọc dữ liệu thật từ API.
- **"AI/GIS định giá" thực chất là bảng giá hardcode** (VD: giá cố định theo m² ở cả frontend lẫn backend), hai bảng còn *mâu thuẫn với nhau*, gắn nhãn "độ tin cậy 94%" giả không dựa trên tính toán nào.
- Luồng xác minh Sổ hồng bị vô hiệu hóa (`requestVerification=false`) nhưng màn hình vẫn luôn báo "Đã gửi thẩm định" thành công.

### Nhóm 2 — Nút chỉ hiện toast, không làm gì thật (đúng như bạn quan sát)
- Toàn bộ khu vực **Quản lý dự án (Projects)**: Xuất báo cáo, Duyệt thay đổi, Đối chiếu Diff, Sửa hồ sơ, Lịch sử Revision, Quản lý tin đăng — **6 nhóm hành động chỉ hiện thông báo giả, không gọi API, không điều hướng**.
- **CMS**: nút Đối chiếu Diff, Xem trang công khai, Tạo Revision mới — chỉ toast cố định (VD: luôn nói "Revision 1 và Revision 2" bất kể chọn gì).
- Trang **Contracts** tồn tại trong code nhưng **không được đăng ký trong router** → là trang chết hoàn toàn, không ai truy cập được.
- CMS còn hiển thị timestamp "Hôm nay/Vừa xong" và reviewer "Admin Tổng biên tập" hardcode, bỏ qua dữ liệu thật từ response server.

### Nhóm 3 — API có nhưng UI chưa hoàn tất workflow
- Báo cáo vi phạm (report) không có form công khai và không có nút xử lý (ẩn/giải quyết/bỏ qua) trên bàn admin dù backend đã hỗ trợ đầy đủ.
- Bàn xác minh không hiển thị tài liệu/ảnh KYC để đối chiếu — chỉ hiện tên/số/loại giấy tờ, cho phép "duyệt mù".
- Form đăng tin có 8 trường (quận/huyện, phường/xã, phòng ngủ, phòng tắm, hướng nhà, pháp lý...) **bị loại khỏi payload khi gửi lên server dù người dùng đã nhập** — dữ liệu mất khi lưu/tải lại.
- Nút "Sửa tin" chỉ điều hướng tới trang tạo tin mới, không tải dữ liệu tin cũ — bấm sửa sẽ vô tình tạo tin mới.

### Nhóm 4 — Sai hợp đồng dữ liệu Frontend–Backend
- Trang **Project** và **Analytics** dùng tên trường hoàn toàn khác với DTO backend (VD: `code`/`investorName` ở FE vs `developerName`/`districtCode` ở BE) → chắc chắn lỗi 400 hoặc hiện `undefined` khi submit/tải dữ liệu.
- Trang chi tiết tin dùng field `primaryImageUrl` trong khi API trả về `imageUrls` → **ảnh thật từ DB không hiển thị được**.

**Kết luận đóng gói sản phẩm:** build production pass (`tsc -b && vite build` thành công), nhưng **chưa nên đưa các màn Project, Analytics, Verification/eKYC và các tuyên bố pháp lý ra production** cho tới khi xử lý các mục P0/P1 nêu trên.

---

## 4. Vai Senior triển khai — khả năng scale vài triệu người dùng đồng thời

**Kết luận thẳng thắn:** chưa có cơ sở để khẳng định hệ thống hiện phục vụ được vài triệu người dùng cùng lúc; thiết kế/cấu hình hiện tại còn những điểm chặn rõ ràng, thậm chí **chưa đo được đủ để cam kết ngay cả một mức thấp hơn như 10.000 concurrent** cho bản hiện tại.

### Điểm nghẽn cụ thể
| Vấn đề | Chi tiết |
|---|---|
| **Elasticsearch full sync** | Mỗi vòng đồng bộ (60s) query toàn bộ tin ACTIVE và gửi tuần tự từng HTTP PUT một — với 1 triệu tin sẽ là 1 triệu request/vòng, không có batch, không có checkpoint, không dùng sự kiện outbox |
| **Rate-limit & SSE (realtime) sống trong bộ nhớ từng pod** | Tăng số pod làm ngưỡng rate-limit tăng sai theo số pod; tin nhắn real-time không tới đúng người nếu họ không kết nối đúng pod đã phát sự kiện |
| **Geocoding** | Chạy `synchronized` toàn phương thức (một request chậm giữ khóa của cả pod); phụ thuộc Nominatim công khai giới hạn 1 request/giây **cho toàn ứng dụng**, không phải mỗi pod — không scale được bằng cách tăng replica |
| **Kết nối DB** | HikariCP tối đa 15 connection/pod; HPA scale tới 10 pod = 150 kết nối chỉ riêng API, chưa tính worker/migration — chưa có phép đo xem DB có chịu được không |
| **Metrics** | Endpoint Prometheus `/actuator/prometheus` bị chính cấu hình bảo mật (`SecurityConfig`) chặn truy cập → hiện tại **không thể theo dõi hiệu năng thực tế** để biết khi nào cần scale |
| **Media** | Backend đọc toàn bộ ảnh public qua chính JVM app thay vì CDN/object gateway riêng — băng thông ảnh sẽ cạnh tranh trực tiếp với API |

### Bằng chứng kiểm thử tải hiện có
Script k6 hiện tại chỉ chạy 25 virtual user (VU) gọi lặp lại trang tìm kiếm đầu tiên — đây là mức smoke test nhỏ, hoàn toàn không đại diện cho kịch bản hàng triệu người dùng, đăng nhập, upload, thanh toán hay nhiều kết nối SSE đồng thời.

Kiến trúc hiện tại (modular monolith, Docker Compose 1 host) phù hợp cho demo/môi trường nhỏ; có template Kubernetes (HPA, PDB) nhưng dùng placeholder image/host/storage, chưa phải cấu hình đã kiểm chứng trên cluster thật.

**Kết luận:** chưa nên nhận cam kết "vài triệu người dùng đồng thời". Cần đóng các lỗi tính đúng đắn ở mục 3 trước, sau đó đo tải thật theo từng bậc (baseline → mixed load → stress → soak) rồi mới mở rộng có số liệu.

---

## 5. Tổng kết & lộ trình ưu tiên

| Ưu tiên | Việc cần làm |
|---|---|
| **P0** | Sửa README/biến môi trường để setup demo chạy được trên máy sạch, tách rõ profile production/demo |
| **P0** | Vá 3 lỗ hổng bảo mật cao: cleanup KYC, BOLA xác minh tin đăng, tin ẩn vẫn lộ qua search |
| **P0** | Sửa/ẩn các badge và kết luận hardcode sai sự thật (xác thực chủ tin, kết luận pháp lý, làm mờ vị trí, "AI định giá") |
| **P0** | Hoàn thiện luồng chủ tin nhận & liên hệ được với lead — đây là điểm chặn cốt lõi của mô hình kinh doanh mua bán |
| **P1** | Nối các nút "chỉ toast" ở Project/CMS thành API thật, hoặc tạm ẩn khỏi UI cho tới khi sẵn sàng |
| **P1** | Sửa hợp đồng dữ liệu FE-BE (Project, Analytics, ảnh chi tiết tin) |
| **P2** | Đo benchmark tải thật (baseline → stress → soak) trước khi tuyên bố khả năng mở rộng |

---

*Tài liệu này tổng hợp lại từ các báo cáo có sẵn trong repo (`2026-09-13_01...05...md`) kết hợp phân tích của Claude. Khuyến nghị đối chiếu lại với trạng thái code mới nhất trước khi ra quyết định phát hành, vì các dòng số liệu trên gắn với một snapshot commit cụ thể.*
