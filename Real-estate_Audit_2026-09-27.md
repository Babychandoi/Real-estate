# NHÀ ĐẤT CHUẨN — Đánh giá sản phẩm, kỹ thuật và thiết kế

Ngày chốt mã nguồn: **27/09/2026**. Repository: **Babychandoi/Real-estate**. Nhánh: **main**. Commit: **831a01044a5694c1ad32d08fca2f59e73263222c**.

Đây là báo cáo đánh giá và kế hoạch triển khai. Chưa thay đổi hoặc đẩy mã nguồn lên repository. Những thông số ghi là “mục tiêu” hoặc “đề xuất” chưa phải kết quả đo production.

## 1. Kết luận điều hành

**Hệ thống có nền tảng của một sản phẩm bất động sản vận hành được nhiều nghiệp vụ, nhưng chưa có đủ bằng chứng để kết luận đã hoàn thiện, tối ưu hoặc sẵn sàng mở rộng mạnh.** Khoảng cách chính nằm ở tính đúng đắn của tìm kiếm, khả năng vận hành khi dữ liệu tăng, vòng đời khách hàng và chất lượng dữ liệu. Thêm thật nhiều tính năng sẽ không tự giải quyết các khoảng cách này.

Nên giữ React + Spring Boot + PostgreSQL và kiến trúc modular monolith hiện tại. Ưu tiên cải thiện bên trong trước khi cân nhắc microservice. Elasticsearch, Redis, Kubernetes chỉ có giá trị khi cách sử dụng và vận hành phù hợp; sự hiện diện của chúng không chứng minh hệ thống đã chịu được dữ liệu lớn.

| Góc nhìn | Kết luận tại commit kiểm tra | Quyết định nên làm |
|---|---|---|
| Product | Nhiều module đã có, nhưng hành trình tìm nhà–quay lại–liên hệ–hẹn xem còn thiếu hoặc có ma sát | Hoàn thiện một phân khúc và khu vực trước khi mở rộng |
| Software | Có phân quyền, revision, optimistic locking, outbox, migration; còn nợ ở hợp đồng API và đường đọc | Sửa tính đúng đắn, bổ sung kiểm thử tình huống nghiệp vụ |
| Performance | Có phân trang backend nhưng UI tìm kiếm dừng ở 100 tin; ES đồng bộ toàn bộ; nhiều đường đọc tải dư dữ liệu | Thiết kế read model, đồng bộ theo sự kiện và đo tải có dữ liệu thực tế |
| Operations | Có CI, tài liệu SLO, manifest, backup scripts; CI của commit đang thất bại | Khôi phục release gate, đo restore/failover, kiểm chứng cấu hình đang triển khai |
| UI/UX | Hướng xanh navy và typography hiện tại hợp ngành; chưa có bằng chứng vượt các nền tảng lớn về usability | Giữ thương hiệu, chuẩn hóa component và hoàn thiện hành trình |
| Tăng trưởng | Chưa thấy đo đủ đầu phễu và chất lượng lead | Đo lead đủ điều kiện, tỷ lệ hẹn xem và hiệu quả môi giới trước khi tăng chi phí thu hút |

**Thứ tự ưu tiên:** CI và lỗi hiển thị dữ liệu → tìm kiếm và hiệu suất → trải nghiệm tìm nhà → đo lường và giữ chân → mở rộng nguồn cung. Không có bộ tính năng hoặc thiết kế nào bảo đảm “chiếm lĩnh thị trường”; lợi thế phải đến từ nguồn cung hữu ích, tin đáng tin cậy và khả năng kết nối thành công.

## 2. Phạm vi và mức độ chắc chắn

Đã lấy cây repository đầy đủ: 678 mục, không bị cắt; đưa 349 tệp mã nguồn/cấu hình/văn bản trong phạm vi kiểm tra về môi trường đọc. Phạm vi gồm frontend, backend, migration, CI, hạ tầng và tài liệu thiết kế. Đã lập bản đồ toàn bộ route khai báo, rà soát các nhóm API và đọc sâu các luồng search, listing, revision, lead, KYC, billing, media, notification và outbox. Điều này không tương đương chứng minh mọi nhánh của mọi hàm đều đúng.

| Loại bằng chứng | Đã thực hiện / quan sát | Giới hạn |
|---|---|---|
| Mã nguồn | Đọc và đối chiếu tại SHA cố định; các liên kết chứng cứ bên dưới khóa theo SHA | Không đại diện cho commit mới hơn hoặc cấu hình ngoài repo |
| Frontend | `npm ci --ignore-scripts` và `npm run build` thành công trong môi trường audit | Build pass không chứng minh UX, API hay production hoạt động đúng |
| CI cùng SHA | Backend tests pass; frontend checks pass; integration stack khởi động; security-scan pass; bước browser test fail | Security-scan chỉ ở phạm vi cấu hình, có `ignore-unfixed: true` |
| Browser tests | CI ghi 35 trường hợp: 28 fail, 7 pass, từ 5 bài kiểm thử trên 7 cấu hình | Không phải 35 nghiệp vụ độc lập; không phải 28 lỗi sản phẩm |
| Backend tại môi trường audit | Đã thử Maven verify | Bị chặn ở phân giải DNS Maven Central; không kết luận backend lỗi vì việc này |
| UI trực quan | Đã xem ảnh baseline home lưu trong repo, đối chiếu với component hiện tại | Baseline có thể cũ; chưa xem được đầy đủ website live do công cụ trình duyệt timeout |
| Hiệu suất | Đọc SQL, cấu trúc tải dữ liệu, bundle build, k6/SLO | Chưa có EXPLAIN ANALYZE, query count, RUM, APM, load test hoặc DR drill production |
| Cạnh tranh | Nguồn chính thức của 7 nền tảng tiêu biểu trong và ngoài Việt Nam | Không tuyên bố khảo sát mọi website hoặc mọi chức năng trả phí |

Trong báo cáo: **xác nhận** = thấy trực tiếp trong mã nguồn/log; **rủi ro** = có cơ chế có thể gây lỗi nhưng chưa tái hiện ở runtime; **đề xuất** = thiết kế cần thực hiện và đo. Không sử dụng các báo cáo tự đánh giá trong repo làm bằng chứng nghiệm thu thay cho CI/mã nguồn.

## 3. Những nền tảng đã làm tốt

1. **Revision công khai và kiểm duyệt:** tách bản sửa khỏi bản được duyệt, public revision là nền tảng đúng cho tin đăng. Luồng hydrate public còn lọc ACTIVE và public revision, giảm nguy cơ trả tin đã ẩn từ chỉ mục cũ.
2. **Phân quyền theo vai trò và đối tượng:** có kiểm tra chủ sở hữu, vai trò admin/moderator; tài liệu riêng tư có đường truy cập bảo vệ. Đây là nền tảng cần giữ khi tối ưu DTO và cache.
3. **Bảo vệ dữ liệu nhạy cảm:** có mã hóa PII, blind index/HMAC phục vụ tra cứu, redaction thông tin liên hệ, rate limit, mật khẩu băm; không nên đánh đồng hệ thống với một CRUD demo không có bảo vệ.
4. **Outbox có cơ chế vận hành:** claim, retry/backoff, lease, dead-letter và xử lý cạnh tranh là hướng phù hợp. Dùng tiếp nền tảng này cho các tác vụ ngoài giao dịch, với checkpoint riêng cho từng consumer.
5. **Đường tìm kiếm đã có một phần tối ưu:** lấy ID trước, giữ thứ tự bằng Map khi hydrate; dữ liệu người đăng được lấy theo batch, không truy vấn từng seller riêng. Frontend có debounce geocoding và chặn response cũ ghi đè.
6. **Map và frontend:** đã dùng cluster GeoJSON, lazy route, lazy image, aspect ratio; có compare tối đa 3 tin cùng mục đích và đồng bộ localStorage giữa tab.
7. **Vận hành:** Flyway, CI, Docker, tài liệu runbook/SLO và cấu hình HA là tài sản tốt. Cần bổ sung bằng chứng rằng chúng vận hành đúng trong môi trường triển khai.
8. **Không giả lập AI thành tính năng thật:** endpoint định giá và quality score trả 501 khi chưa có nguồn dữ liệu/tiêu chí được duyệt; production validator chặn bật provider eKYC/giao dịch chưa nghiệm thu. Nên giữ sự rõ ràng này trên UI và tài liệu sản phẩm.

## 4. Đối chiếu với thị trường

Chọn các nền tảng theo vai trò: Batdongsan.com.vn và Nhà Tốt cho thị trường Việt Nam; OneHousing cho dữ liệu và định giá; Zillow, Redfin, Rightmove, idealista cho hành trình tìm nhà. Chỉ ghi các khả năng có nguồn chính thức; không coi mọi cách làm ở nước ngoài đều phù hợp Việt Nam.

| Nền tảng | Khả năng quan sát từ nguồn | Khoảng cách của Nhà Đất Chuẩn | Cách áp dụng |
|---|---|---|---|
| Batdongsan.com.vn | Tin xác thực, kiểm tra theo phạm vi chứng cứ; bộ lọc và ưu tiên tin xác thực [M1] | Có duyệt/xác minh nhưng UI dùng nhãn tổng quát; lọc xác thực chỉ ở frontend | Xác thực thành dữ liệu có loại, phạm vi, thời điểm; filter server-side; hiển thị bằng chứng và giới hạn |
| Nhà Tốt | Phân tách mua/thuê và loại hình; trang dự án, người đăng/môi giới; mục biểu đồ giá [M2] | Bộ lọc thuê còn dùng ngưỡng giá bán; dự án và CMS thiên về admin; thiếu vòng quay lại | Chuẩn hóa thuê, trang khu vực/dự án public có nội dung thật và nguồn giá rõ ràng |
| OneHousing | Công cụ định giá với thông tin dự án/tòa/căn và thuộc tính tài sản [M3] | Endpoint định giá hiện chưa triển khai | Làm dữ liệu so sánh và mức độ tin cậy trước; AVM chỉ sau khi đủ dữ liệu hợp lệ và có kiểm định |
| Zillow | Zestimate sử dụng nhiều nguồn dữ liệu và được giải thích là ước tính, không phải appraisal [M4] | Chưa có lịch sử giá, tập so sánh hoặc coverage để định giá có trách nhiệm | Hiển thị khoảng ước tính, nguồn/ngày cập nhật, độ phủ; không tạo một con số AI thiếu căn cứ |
| Redfin | Lưu tìm kiếm, thông báo; hỗ trợ chia sẻ saved search với search partner [M5] | Có compare cục bộ nhưng chưa thấy lưu tìm kiếm/cảnh báo và danh sách chia sẻ xuyên thiết bị | Saved search, favorite, shortlist chia sẻ có quyền truy cập và tùy chọn ngừng thông báo |
| Rightmove | Draw a Search và property alerts [M6] | Có bbox nhưng URL và map/list chưa đồng bộ đầy đủ; chưa có polygon | Ưu tiên bbox đúng + saved search; polygon sau khi nhu cầu được xác nhận |
| idealista | Tìm kiếm qua bản đồ, vẽ vùng trên trang map [M7] | Desktop hiện chuyển qua lại list hoặc map; chỉ cluster tập tối đa 100 tin | Split view desktop, map theo viewport, server aggregation; mobile list/map và bottom sheet |

Không nên sao chép mật độ quảng cáo, cách “đẩy tin” hay toàn bộ bộ lọc của đối thủ. Mỗi thành phần phải giúp người dùng trả lời: **nhà có phù hợp, tin còn hiệu lực, giá được hiểu đúng, người đăng có đáng tin và liên hệ có kết quả không?**

### 4.1. Những khoảng trống sản phẩm quan trọng

| Nhóm | Hiện trạng trong repo | Phần nên triển khai |
|---|---|---|
| Tìm kiếm | Giá, diện tích, loại hình, purpose, keyword, bbox, sort | Phòng ngủ, pháp lý, nội thất/chi phí thuê; địa danh có dấu/không dấu và bí danh; URL hoàn chỉnh; kết quả ngoài trang đầu |
| Giữ chân | Compare 3 tin, localStorage | Favorite tài khoản, saved search, cảnh báo tin mới/giảm giá/còn hàng, quản lý tần suất |
| Hẹn xem | Lead VIEWING/CONSULTATION, trạng thái có APPOINTED | Lịch có slot, xác nhận hai bên, nhắc lịch, đổi/hủy và ghi nhận no-show |
| Trust | KYC thủ công, kiểm duyệt và property verification | Phân loại identity/listing/ownership; bằng chứng còn hiệu lực; báo hết hàng, lịch sử xử lý khiếu nại |
| Dữ liệu bất động sản | Listing và revisions | Phân biệt tài sản thực với nhiều tin cùng tài sản; chống trùng; nguồn, freshness và giá thay đổi |
| Trang khu vực/dự án | Có catalog/admin | Trang public dự án/khu vực có inventory, tiện ích có nguồn, phương pháp thống kê và SEO |
| Content | Có CMS admin và trang information tĩnh | Nối nội dung đã duyệt với trang public, revision, preview, lịch xuất bản, tác giả/nguồn |
| Seller/broker | Profile, kho tin, lead inbox, gói dịch vụ | Import tin có kiểm tra, chất lượng tin, SLA phản hồi, báo cáo lead đủ điều kiện và ROI |
| Chủ nhà | Đăng tin hiện cho ADMIN/BROKER | Persona chủ nhà riêng hoặc cùng capability đăng tin với nhãn vai trò rõ; không bắt mọi chủ nhà tự nhận là môi giới |
| Analytics | Đếm lead và trạng thái; impressions/detailViews bằng 0 | Event pipeline đầu phễu, cohort, nguồn traffic và attribution có giới hạn, chỉ số chất lượng lead |
| Thanh toán | Gói tin, báo chuyển khoản, admin đối soát | Idempotency, audit và exception queue tốt hơn; tách rõ dịch vụ đăng tin với tiền đặt cọc bất động sản |
| AI/3D/chat | Một số khả năng chưa có hoặc bị chặn | Chỉ ưu tiên khi giải quyết một vấn đề đã đo; không phải điều kiện đầu tiên để ra thị trường |

### 4.2. Hướng cạnh tranh đề xuất

Giả thuyết sản phẩm nên thử: **“Tìm nhà còn thật, thông tin rõ và hẹn xem có người phản hồi.”** Chọn một thành phố và một phân khúc, ví dụ căn hộ mua/thuê trong một cụm khu vực có đội ngũ cung cấp tin tốt. Đây là giả thuyết để phỏng vấn và thử nghiệm, không phải kết luận thị trường đã được xác thực.

Trước khi mở rộng: tuyển nhóm môi giới/chủ nhà nòng cốt; chuẩn hóa ảnh, địa chỉ, giá và thời điểm xác nhận còn hàng; thu hồi tin không cập nhật; kiểm tra ngẫu nhiên và phản hồi báo cáo. Lợi thế dữ liệu hình thành từ vận hành này, không chỉ từ một dấu tick xanh.

Chỉ số trung tâm đề xuất: **số lịch hẹn xem được hai bên xác nhận trên mỗi người tìm nhà đủ điều kiện mỗi tuần**. Theo dõi thêm tỷ lệ zero-result, search→detail, detail→lead đủ điều kiện, thời gian phản hồi, lead→hẹn xem, hẹn xem diễn ra, tỷ lệ tin hết hàng và tỷ lệ khách quay lại. Với broker, đo chi phí trên lead đủ điều kiện và tỷ lệ gia hạn. Không đánh giá thành công bằng lượt xem hoặc số tài khoản đơn thuần.

## 5. Kiểm kê giao diện và hành trình toàn hệ thống

Nguồn đối chiếu route: [routes.tsx][C01]. “Có route” chỉ nghĩa là được khai báo, không chứng nhận mọi trạng thái đã qua E2E.

| Route / khu vực | Đánh giá và việc cần hoàn thiện |
|---|---|
| `/` | Baseline có bố cục rõ, màu hợp thương hiệu; hero chiếm nhiều vùng đầu. Đưa tìm kiếm và nguồn cung thật lên sớm; tránh số liệu/demo copy thiếu căn cứ |
| `/search` | Ưu tiên cao nhất: phân trang, filter server, URL, đơn vị thuê, map/list, zero-result gợi ý |
| `/listings/:listingId` | Đã hỗ trợ slug; hoàn thiện gallery đủ ảnh, giá thuê, trust panel, CTA và metadata |
| `/nguoi-dang/:sellerId` | Profile và inventory; API giới hạn 60 tin chưa có phân trang. Tách xác minh danh tính với xác minh từng tin |
| `/compare` | Có tối đa 3 tin cùng purpose; bổ sung chỉ hiển thị khác biệt, giá thuê đúng chu kỳ và phản ánh tin không còn hoạt động |
| `/listings/new` | Nên chia bước cơ bản–vị trí–ảnh–xem trước; giữ draft, lỗi tại trường; vai trò chủ nhà cần rõ |
| `/my-listings` | Giữ revision/state machine; phân trang, lọc trạng thái, hiển thị bản công khai và bản sửa tách biệt |
| `/broker/workspace` | Đo SLA thực, hành động cần làm hôm nay, phân công và lịch sử tiếp nhận lead |
| `/my-leads` | Tối ưu query owner bằng JOIN; bộ lọc server, lịch sử trạng thái và chống cập nhật đè |
| `/my-inquiries` | Hữu ích cho người tìm nhà; nên có lịch hẹn/đổi lịch, trạng thái phản hồi thực và quyền rút yêu cầu |
| `/billing` | Đơn mua gói và trạng thái đối soát rõ; giữ snapshot giá/gói; phân trang lịch sử, chống tạo lặp |
| `/kyc` | Đang là luồng hồ sơ xét duyệt; diễn đạt đúng phạm vi, lý do cần giấy tờ, trạng thái/rejection và cách gửi lại |
| `/account` | Avatar/thông tin tài khoản; kiểm tra đồng bộ cache sau sửa và hình vỡ; thêm quản lý thông báo/quyền riêng tư khi triển khai |
| `/verify-email`, `/forgot-password`, `/reset-password` | Cần đủ expired/used/invalid token, resend throttling, thông báo trung tính và không mất mục đích người dùng ban đầu |
| `/about`, `/terms`, `/privacy`, `/contact` | Nội dung hiện theo component tĩnh; cần thông tin chủ thể và vận hành thực, liên kết CMS đã duyệt nếu chọn CMS làm nguồn |
| Trang không tồn tại `*` | Có UI not found nhưng SPA fallback 200 tạo rủi ro soft 404; xử lý status ở tầng render/edge |
| `/2026/nhadatchuan/admin/login` | Đường dẫn kín không thay thế bảo mật; bổ sung MFA admin, kiểm soát phiên và audit nếu vận hành thật |
| Admin `/moderation` | Hàng đợi hiện có đường đọc không phân trang; cần claim/assignment, SLA, bulk action có phạm vi và lý do |
| Admin `/listings` | Phân trang server; lọc/truy vết bản sửa; hành động khóa/ẩn có lý do, preview không công khai draft |
| Admin `/users` | Least privilege, lịch sử khóa/mở và thay vai trò; hạn chế khả năng đọc dữ liệu nhạy cảm |
| Admin `/leads-and-reports` | Tách hàng đợi lead và báo xấu theo tác vụ; SLA và audit đầy đủ |
| Admin `/verification` | Hiển thị đối chiếu bằng chứng và quyết định; không đưa tài liệu KYC vào cache public |
| Admin `/billing` | Đối soát theo trạng thái, kiểm soát cạnh tranh; giải quyết ngoại lệ/chênh số tiền, lịch sử người duyệt |
| Admin `/analytics` | Ghi rõ dữ liệu chưa thu thập, không trình bày 0 như không có hoạt động; bổ sung cohort/khung thời gian |
| Admin `/projects`, `/cms` | Có quản trị nhưng chưa hoàn tất hành trình public dự án/bài viết |
| Alias `/admin/*`, `/2026/nhadatchua/admin/*` | Có redirect; đưa vào regression để không tạo đường cụt hoặc ảnh hưởng kiểm soát quyền |
| `_account.contracts.tsx` | Có tệp nhưng không được nối vào router; không tính là tính năng giao dịch người dùng hoàn chỉnh |

## 6. Các phát hiện kỹ thuật và cách sửa

Mức ưu tiên trong báo cáo: **P0 = phải xử lý để khôi phục độ tin cậy của phát hành; P1 = ảnh hưởng hành trình cốt lõi, bảo vệ dữ liệu hoặc mở rộng; P2 = cải thiện sau khi nền tảng ổn định.** Đây là mức ưu tiên dự án, không phải thang điểm CVSS.

### F01 — CI của commit đang đỏ; E2E chưa là cổng phát hành đáng tin cậy — P0

**Xác nhận:** [run 36314701789][C02] có 28 fail/7 pass. Bảy lần fail navigation dùng selector `/Chi tiết/` khác chữ hoa/thường so với `Xem chi tiết: ...`; URL test còn mong UUID trong khi route công khai dùng slug. Hai mươi mốt trường hợp còn lại là screenshot mismatch của home/search/compare. Cần xem ảnh diff trước khi coi đó là lỗi UI. Trong visual spec, screenshot được assert trước axe nên screenshot fail sẽ khiến kiểm tra axe/overflow phía sau không chạy. [C03]; [C04]; [C05]

**Sửa:** cập nhật test theo hành vi canonical slug; dùng fixture tin xác định; chờ trạng thái dữ liệu thay cho sleep; tách visual, a11y và navigation thành các kiểm tra độc lập. Upload report, diff và trace khi fail. Chỉ cập nhật baseline sau review ảnh. Bật required checks và review trên main: tại thời điểm đọc, branch trả `protected:false`, rulesets repo trả danh sách rỗng; chưa kiểm chứng các chính sách ngoài repo.

**Nghiệm thu:** suite hiện tại xanh, không disable/skip để làm xanh; có luồng buyer và broker xác thực dùng fixture; pull request không merge nếu gate đỏ; mọi fail có artifact để điều tra. `npm run lint` hiện chỉ là TypeScript check, cần ESLint riêng theo quy tắc repo, không đếm một kiểm tra thành hai loại bảo đảm.

### F02 — Tìm kiếm giới hạn ở 100 tin và số kết quả không phản ánh toàn bộ — P1

**Xác nhận:** frontend gửi `size:100`, không có state page/pager; backend clamp 100 và trả List. UI hiển thị `visibleListings.length`. [C06] (dòng 44–103, 388–437); [C07] (dòng 215–285)

**Tác động:** người mua không khám phá tin ngoài 100 kết quả đầu; đếm tin và bản đồ dễ bị hiểu là toàn bộ nguồn cung.

**Sửa:** list dùng 20–24 tin/lần và envelope `items`, `hasNext`, `nextCursor`; total chỉ khi cần và phải ghi rõ exact/estimated. Map gọi endpoint aggregation theo bbox/zoom cùng filter; không lấy toàn bộ tin để vẽ. Phân trang bình thường có thể dùng offset nông; deep browse và feed dùng cursor có thứ tự ổn định.

**Nghiệm thu:** fixture >250 tin, đi qua mọi trang không trùng/mất trong snapshot cố định; bản đồ và danh sách cùng bộ lọc; con số kết quả không lấy từ độ dài trang.

### F03 — Lọc “đã xác thực” phía client và URL không giữ đầy đủ trạng thái — P1

**Xác nhận:** `onlyVerified` lọc mảng đã tải; không có trong search criteria backend. `priceRange`, `sortBy`, `onlyVerified` khởi tạo mặc định thay vì đọc đầy đủ URL. Nhiều nút chỉ setState; địa điểm/bbox không được bảo toàn khi reload. [C06]; [C08]

**Sửa:** parse/serialize một schema filter duy nhất; server chịu trách nhiệm mọi filter quyết định tập kết quả. Thay filter reset cursor. Đồng bộ location ID hoặc bbox, purpose, đơn vị giá, sort và view. Dùng push history cho thay đổi có chủ ý, replace phù hợp với thao tác liên tục như di chuyển bản đồ.

**Nghiệm thu:** tin xác thực ở ngoài trang đầu vẫn xuất hiện đúng khi lọc; copy URL, reload, back/forward khôi phục cùng tiêu chí; không chỉ cùng ô nhập.

### F04 — Luồng cho thuê dùng ngưỡng giá mua và thiếu chu kỳ giá — P1

**Xác nhận:** chip `<3B`, `3–5B`, `>5B` dùng cho cả SALE/RENT; card hiển thị formatter tiền không có `/tháng`. [C06] (dòng 63–70, 328–360); [C05] (dòng 49–58)

**Sửa:** kiểu dữ liệu tiền gồm amount/currency/period; giá thuê có chu kỳ rõ, phí quản lý/đặt cọc tách riêng nếu có. Bộ lọc rent lấy min/max theo tháng, preset dựa trên phân khúc chứ không hardcode một giá chung cả nước. Không so sánh giá thuê tháng với giá bán.

**Nghiệm thu:** card/detail/compare/search cùng đơn vị; đổi SALE↔RENT reset hoặc chuyển filter hợp lệ; boundary không chồng lấn sai nhãn `<` và `>`.

### F05 — Elasticsearch quét toàn bộ dữ liệu và PUT từng document — P1

**Xác nhận:** hàm `sync()` mỗi fixed delay mặc định 60 giây tải toàn bộ ACTIVE bằng `queryForList`, lặp HTTP PUT từng document; không có bulk/incremental hoặc xóa document khi tin bị ẩn. Scheduler tồn tại trên mỗi instance. [C09] (dòng 21–28)

**Tác động:** CPU/RAM và lưu lượng tăng theo tổng kho tin ngay cả khi ít tin thay đổi; một HTTP chậm kéo dài vòng sync. 60 giây là delay sau vòng làm việc, không phải cam kết dữ liệu mới trong 60 giây. DB hydrate chặn tin inactive nhưng không tự sửa các filter giá/thuộc tính đã cũ trong ES; trang có thể thiếu dòng hoặc chứa tin không còn khớp tiêu chí mới.

**Sửa:** phát `ListingPublished/Updated/Hidden/VerificationChanged` cùng transaction; consumer có checkpoint riêng, bulk indexing, version bảo vệ sự kiện đến sai thứ tự, delete/tombstone khi ẩn. Backfill đọc theo batch/keyset; rebuild sang index mới rồi đổi alias. Retry có backoff, DLQ và cảnh báo lag. Không dùng một cờ processed chung để hai consumer tranh nhau nhận cùng sự kiện.

**Nghiệm thu:** update/ẩn hiện đúng trong ngưỡng lag đề xuất p95 ≤10 giây; phát lại sự kiện không hạ revision; worker chết/khởi động lại tiếp tục được; mỗi thay đổi không gây full scan.

### F06 — Deep pagination và fallback tìm kiếm chưa có hợp đồng đồng nhất — P1

**Xác nhận:** ES dùng `from=page*size`; bắt mọi exception và chuyển global `available=false`. Các sort giá/diện tích dùng tie-breaker khác SQL; ES tìm description nhưng SQL chỉ title/address, bbox thiếu thành phần có cách xử lý khác. Criteria nuốt enum sai và chưa kiểm tra đầy đủ min/max. [C08]; [C09]; [C10] (dòng 58–88)

**Sửa:** trả 400 cho enum/range/bbox sai; sort whitelist và tuple rõ ràng. Dùng `search_after`, PIT khi cần snapshot nhất quán; cursor ký hoặc xác thực, chứa filter hash/sort/version/expiry và không tái sử dụng sang engine khác. Elastic mặc định giới hạn from/size tới 10.000 hits; không tăng giới hạn để che thiết kế deep pagination [T1]. Có circuit breaker với timeout budget, metrics, và cơ chế fallback được kiểm thử. Có thể giới hạn chức năng fallback một cách công khai thay vì giả vờ kết quả tương đương.

**Nghiệm thu:** test cùng dữ liệu cho ES và SQL, bao gồm tiếng Việt, tie-breaker, ẩn tin, query không hợp lệ, ES timeout và deep browse; lỗi của một query không làm cả cụm âm thầm xuống chế độ chậm dài hạn.

### F07 — Hydrate toàn bộ revision/media cho trang danh sách — P1

**Xác nhận cấu trúc:** repository fetch `l.revisions`; mapper duyệt mọi revision và đọc collection media LAZY; mapper còn nuốt LazyInitializationException. [C10] (dòng 34–50); [C11] (dòng 20–58); [C12] (dòng 115–120)

**Rủi ro:** có thể N+1 khi persistence context còn mở, hoặc mất media khi context đóng; số query thực tế chưa đo. Dù không phát sinh N+1, tải tất cả revision để hiển thị một card vẫn dư dữ liệu.

**Sửa:** public summary projection chỉ JOIN `public_revision_id`, thumbnail và seller fields cần thiết. Detail lấy public revision và media; owner/editor lấy draft riêng. Không dùng `JOIN FETCH` collection trực tiếp cùng pagination để tạo vấn đề phân trang mới. Không nuốt lỗi lazy loading làm dữ liệu trống không có log.

**Nghiệm thu:** đếm query bằng instrumentation trong integration test; trang 24 tin có 1 hoặc 10 revision/tin đều giữ query count giới hạn đề xuất ≤4; response không chứa draft/private fields; profile query cải thiện ở dataset lớn.

### F08 — Một số danh sách không phân trang hoặc tải dư trước khi phân trang — P1

**Xác nhận:** owner listings, moderation pending tải toàn bộ; lead broker tải toàn bộ tin của owner rồi tạo `IN(ids)` dù lead đã phân trang; billing `mine`/`queue` không giới hạn; public seller giới hạn 60 nhưng không có trang tiếp. [C10] (dòng 37–56); [C13] (dòng 137–156); [C14] (dòng 64–65); [C15]

**Sửa:** pagination server mọi danh sách tăng theo thời gian; lead query JOIN listings với `owner_id`; projection theo màn hình; COUNT tách khỏi tải chi tiết. Không sửa tất cả endpoint theo một kiểu nếu dữ liệu là danh mục nhỏ có giới hạn tự nhiên.

**Nghiệm thu:** seller có >60 tin vẫn xem tiếp được; owner 10.000 tin không cần load toàn bộ để mở trang 20 lead; mọi query có sort ổn định và giới hạn size.

### F09 — SQL/GIS cần được tối ưu theo query plan — P1

**Xác nhận:** fallback dùng `OR :param IS NULL`, LIKE `%keyword%`, CASE sort và tọa độ dạng scalar. Có index trong migrations, nhưng chưa thấy cột không gian/GiST được dùng cho public search. Bật extension PostGIS không tự làm range lat/lng thành spatial index. [C10]; [C16]

**Sửa:** dynamic predicates theo filter thực, ít mẫu ORDER BY cố định; tạo public search read model khi cần. Xem thiết kế index và phương pháp đo tại mục 7. Không tạo hàng chục index theo cảm tính: có chi phí ghi, dung lượng và vacuum. EXPLAIN phải dùng cùng phân bố dữ liệu/selectivity với production [T2, T3].

### F10 — Cache hiện chưa đủ cho đường đọc nóng — P1/P2 theo tải

**Xác nhận:** có cache HTTP static/media, DB geocode cache và Redis rate limiting. Không thấy cache kết quả listing/detail có chiến lược invalidation tương ứng. Geocode cache truy vấn theo hash không kiểm tra thời hạn. [C17] (dòng 58–84); [C18]; [C19]

**Sửa:** áp dụng bảng chính sách ở mục 7; không cache response KYC/lead/admin public. Có invalidation khi approve/hide/change price và version hóa key. Cache là lớp tối ưu sau khi query đúng; ES là chỉ mục tìm kiếm, không thay thế chiến lược cache response.

### F11 — Header bảo mật Nginx có thể bị mất ở location con — P1

**Xác nhận cấu hình:** security headers ở server, nhưng `/assets/`, `/index.html`, `/` và stream SSE có `add_header` riêng. Theo quy tắc kế thừa mặc định của Nginx, child có add_header không kế thừa tập header từ parent [T4]. Dockerfile đang dùng Nginx 1.27, không thể dùng tùy tiện `add_header_inherit merge` của 1.29.3+.

**Sửa:** dùng include chung trong các location phù hợp, hoặc nâng phiên bản đã kiểm chứng rồi dùng cơ chế merge phù hợp. Kiểm tra response cuối qua CDN/proxy cho HTML, assets, API, lỗi, và SSE. Đây là phát hiện cấu hình, chưa phải bằng chứng đã khai thác XSS hoặc quan sát header thiếu ở production. [C19]

### F12 — SSE chỉ fanout trong một JVM — P1 trước khi chạy nhiều replica

**Xác nhận:** client emitters ở ConcurrentHashMap trong process; notification được lưu DB rồi publish sau commit là tốt, nhưng publish chỉ tới client cùng process. [C20]

**Sửa:** DB giữ lịch sử + cursor; Redis Pub/Sub có thể fanout thời gian thực giữa node, còn reconnect/replay đọc DB; nếu dùng Streams phải thiết kế broadcast tới các node, không nhầm consumer-group chia việc với fanout. Heartbeat, reconnect cả normal EOF, backoff+jitter, dedupe event ID và dọn client disconnect. Sticky session không xử lý triệt để producer ở node khác.

**Nghiệm thu:** hai app instance: ghi qua A, client SSE ở B vẫn nhận; reconnect không mất thông báo chưa đọc và không cập nhật UI trùng.

### F13 — Rate limit cần khớp chuỗi proxy và hành vi khi Redis lỗi — P1

**Xác nhận:** limiter dùng X-Real-IP, Nginx ghi đè bằng remote_addr; Redis INCR và EXPIRE là hai thao tác; fallback map chỉ xóa key cũ khi vượt 10.000, không hard cap key đang hoạt động. [C21]

**Rủi ro theo topology:** đi qua tunnel/reverse proxy có thể gom nhiều người vào IP của proxy nếu không cấu hình real IP tin cậy. Không kết luận có thể spoof qua Nginx hiện tại vì Nginx có ghi đè header.

**Sửa:** chỉ tin proxy đã khai báo; verify IP chain bằng staging; limit theo IP + account + endpoint; atomic Lua hoặc cơ chế tương đương cho counter/expiry; tránh hashCode 32-bit làm key định danh dễ va chạm; bounded fallback/circuit policy rõ cho từng route.

**Nghiệm thu:** hai người qua proxy không dùng chung quota sai; forged header không tăng quyền; Redis down không làm bộ nhớ tăng vô hạn; có Retry-After và dashboard 429.

### F14 — Gallery và image pipeline chưa theo quy mô marketplace — P1

**Xác nhận:** detail render ảnh đầu + `slice(1,6)` không có đường xem toàn bộ trong component, trong khi upload hỗ trợ nhiều hơn; card dùng ảnh nguyên bản, chưa thấy srcset/sizes hoặc thumbnail pipeline. [C22] (dòng 166–176); [C05]; [C18]

**Sửa:** gallery/lightbox có toàn bộ ảnh, điều khiển keyboard, đếm ảnh và alt phù hợp. Xử lý upload tạo WebP/AVIF và cỡ 320/640/960/1600, EXIF/privacy policy, orientation, placeholder, kiểm tra tệp và ảnh lỗi. CDN/object storage cho media công khai, đường private riêng cho hồ sơ.

**Nghiệm thu:** đủ 20 ảnh được xem; không tải 20 ảnh gốc khi mở detail; LCP image chọn đúng kích thước; tin ẩn xử lý asset theo chính sách có chủ đích.

### F15 — Map tăng chi phí tải trang tìm kiếm dù người dùng đang xem list — P1

**Xác nhận build:** chunk MapLibre khoảng **1.037,45 kB** chưa gzip, **279,11 kB** gzip; worker khoảng **506,72 kB** riêng; main JS **315,86 kB**, gzip **98,46 kB**. Search route import ListingMap trực tiếp. Đây là kích thước artifact build, không phải tổng bytes thực tế trên mỗi lượt xem hoặc số đo mạng production.

**Sửa:** dynamic import Map khi người dùng mở map hoặc khi desktop split-view thật sự cần; kiểm tra modulepreload; dùng budget theo route và đo network waterfall. Route lazy hiện có là điểm tốt, không cần thay framework chỉ để chia chunk. [C06]; [C23]

**Nghiệm thu:** chế độ list mobile không tải map chunk trước ý định dùng; đo LCP/INP/CLS và JS execution trên máy tầm trung.

### F16 — SEO hiện chủ yếu xử lý sau khi JavaScript chạy — P1

**Xác nhận:** React SPA với createBrowserRouter, HTML shell và metadata detail thay bằng effect. Cleanup chỉ xóa JSON-LD, có rủi ro canonical/title/meta detail lưu lại khi sang route khác không reset. Nginx fallback trả index cho URL lạ. Sitemap quét tối đa 100×100 tin, hydrate nhiều dữ liệu và tạo lại mỗi request. [C01]; [C22]; [C24]; [C19]

**Sửa:** SSR/prerender cho home, listing, project, area và bài viết; admin có thể giữ SPA. Metadata thống nhất ở route, canonical đúng, status 404/410 theo policy, redirect slug khi cần. Sitemap index + nhiều phần tạo từ projection, cache snapshot, invalidation khi xuất bản. Không index mọi tổ hợp filter vô hạn. Google có chạy JavaScript; vấn đề là khả năng crawl/render và các bot khác, không phải “SPA không thể SEO” [T5].

**Nghiệm thu:** HTML ban đầu của detail có title/canonical/nội dung chính; tin không tồn tại trả status đúng; sitemap bao phủ >10.000 tin mà không hydrate domain; kiểm tra Search Console khi có quyền.

### F17 — Lead/KYC tạo ma sát cao; cạnh tranh ghi cần test bằng PostgreSQL — P1

**Xác nhận:** gửi lead yêu cầu cả requester và owner đã VERIFIED KYC; giới hạn theo số điện thoại đếm trong 24 giờ; idempotency có sẵn. [C13] (dòng 65–105)

**Rủi ro cần test:** count-then-insert có thể vượt quota khi song song; pause listing cạnh tranh với tạo lead cần chính sách rõ; idempotency nên bind actor + route + payload. Không coi trường hợp này là lỗ hổng đã khai thác.

**Sửa:** thống nhất transaction/atomic quota; idempotency scoped actor, kiểm tra payload hash, hạn lưu key; test nhiều request đồng thời. Về UX, cân nhắc phone verification + consent/risk controls cho liên hệ thông thường, giữ KYC ở bước có rủi ro cao. Quyết định thay yêu cầu KYC phải do product/security chốt sau đánh giá rủi ro; không tự bỏ bảo vệ.

**Nghiệm thu:** 20 request đồng thời với cùng idempotency chỉ tạo một lead; khác actor không nhận replay của nhau; retry sau timeout nhận đúng leadId; đo tỷ lệ bỏ cuộc trước/sau KYC với lead quality làm guardrail.

### F18 — Billing còn tác vụ ngoài hệ thống trong transaction — P1

**Xác nhận:** báo chuyển khoản gọi gửi email đồng bộ trong transaction; lỗi mail được log nhưng không có durable retry cho lời gọi này. Phần approve có conditional update giúp chống cộng gói hai lần; snapshot giá/gói/thông tin chuyển khoản là tốt. Bank settings tăng version nhưng chưa dùng expectedVersion để chống lost update. [C14]

**Sửa:** email qua outbox, order create idempotency, compare-and-set bank settings, audit đầy đủ; giữ trạng thái đối soát bền vững. Không nối luồng này với escrow tiền mua nhà khi provider và mô hình vận hành chưa được nghiệm thu.

**Nghiệm thu:** SMTP lỗi không giữ transaction DB; retry không gửi/cộng quyền nhiều lần; hai admin sửa bank settings nhận conflict thay vì ghi đè âm thầm.

### F19 — Analytics chưa đủ để ra quyết định sản phẩm — P1

**Xác nhận:** funnel impressions/detailViews bằng 0 với nhãn chưa thu thập; overview có nhiều trường 0. Đếm trạng thái hiện tại chưa tạo được lịch sử funnel/cohort. [C25]

**Sửa:** event versioned cho search, result impression, detail, favorite, lead submitted, lead qualified, appointment confirmed/completed. Dedupe event ID; bot/internal traffic exclusion; consent và retention phù hợp. Backend phát sự kiện chuyển đổi đáng tin cậy; dashboard phân biệt “chưa đo” với “0”.

**Nghiệm thu:** fixture một hành trình cho ra đúng funnel một lần; xem theo nguồn/khu vực/thiết bị/thời gian; báo cáo có định nghĩa và độ trễ dữ liệu.

### F20 — Chính sách session chưa khớp tài liệu kiến trúc — P1/P2

**Xác nhận:** frontend lưu bearer trong sessionStorage; backend STATELESS với custom session lookup. Đây khác mô tả session HttpOnly trong baseline tài liệu. Tắt CSRF với bearer header không tự động là lỗi CSRF. SessionStorage vẫn chịu tác động nếu có XSS. [C26]; [C27]

**Sửa:** viết ADR chọn mô hình nhất quán; cân nhắc HttpOnly Secure SameSite cookie/BFF và bổ sung CSRF nếu chuyển sang cookie auth. Kiểm thử logout/revoke/expiry, MFA admin và least privilege. Không đổi cơ chế auth chỉ vì một khẩu hiệu “JWT/cookie luôn an toàn hơn”.

### F21 — Backup trong Git LFS và HA cần được xác minh vận hành — P1

**Xác nhận:** `backups/20260926-005833/README.md` mô tả raw volume PostgreSQL/MinIO/Redis trong Git LFS; chưa mở archive nên chưa xác định có dữ liệu thật/secret hay chỉ synthetic. Có K8s/HPA/PDB và template dịch vụ nhưng nhiều cấu hình mẫu, tài liệu có môi trường Docker Desktop/kind. [C28]; [C29]

**Sửa:** phân loại backup trước. Nếu có dữ liệu thật, chuyển quy trình sang backup mã hóa, ACL riêng, retention và restore drill; xử lý lịch sử Git/rotation theo bằng chứng và kế hoạch riêng. Không tự xóa lịch sử hoặc khẳng định đã lộ PII. Production nên tách khỏi máy dev; đánh giá managed DB/object storage theo ngân sách; không xem 2 pod trên cùng host là HA chống mất host.

**Nghiệm thu:** restore vào môi trường cô lập có biên bản dữ liệu đối soát; RPO/RTO được đo; failover app/DB có quan sát; một deploy lỗi có rollback và migration tương thích.

### F22 — Maintainability và hợp đồng API còn lệch với mức “hoàn thiện” — P2

**Xác nhận:** nhiều service/controller nén logic thành dòng rất dài, exception search bị nuốt; `lint` chỉ typecheck. Có springdoc nhưng chưa thấy một OpenAPI contract versioned/generated client và architecture gate tương ứng kỳ vọng tài liệu trong phần repo đã kiểm kê.

**Sửa:** format tự động, tách query/index transport/mapping/retry; log có cấu trúc kèm requestId, không log PII; DTO validation và Problem Details nhất quán; OpenAPI snapshot/diff hoặc contract-first, generate type/client, ArchUnit cho boundary. Bổ sung test khi khóa hành vi quan trọng, không tăng số test chỉ để làm đẹp con số.

## 7. Thiết kế dữ liệu và hiệu suất đề xuất

### 7.1. Giữ mô hình ghi, tách mô hình đọc

PostgreSQL vẫn là nguồn dữ liệu chuẩn. Listing + immutable revisions phục vụ nghiệp vụ; `listing_public_read` hoặc DTO query chuyên biệt phục vụ listing/search/detail. Elasticsearch là chỉ mục có thể dựng lại; không là nguồn quyết định quyền xem, thanh toán hay tài liệu riêng tư.

Luồng xuất bản: transaction lưu quyết định/public revision + outbox → index worker cập nhật read model/search index → invalidate cache → phát notification. Với tin bị khóa/ẩn, lớp đọc cuối cần chặn hiển thị ngay theo policy, không phụ thuộc hoàn toàn vào độ trễ cache/index. Nếu giữ kiểm tra DB ở search, dùng query hẹp và xử lý trang bị hụt có giới hạn; không hydrate mọi revision.

Search API đề xuất, chưa có trong hệ thống:

```json
{
  "items": [],
  "pageInfo": {"hasNext": true, "nextCursor": "opaque-cursor"},
  "total": {"value": 1200, "relation": "gte"},
  "queryVersion": "v2",
  "dataAsOf": "2026-09-27T00:00:00Z"
}
```

`total` là optional; không ép COUNT chính xác mỗi thao tác kéo map. Listing summary chỉ chứa dữ liệu card; `verified` phải là object/enum có nghĩa rõ thay cho một boolean dùng cho nhiều loại xác thực. Endpoint map trả cluster/count theo bbox+zoom với cùng filter schema, có quota và giới hạn độ rộng vùng.

### 7.2. SQL/index minh họa để benchmark

Ví dụ dưới là phương án thiết kế, **chưa chạy và không nên copy vào production mà thiếu migration, kiểm tra trùng index và EXPLAIN**. Khi dùng `CONCURRENTLY`, cần xử lý migration ngoài transaction theo công cụ đang dùng.

```sql
-- Đường quản lý tin theo chủ sở hữu và trạng thái.
CREATE INDEX CONCURRENTLY idx_listings_owner_status_created_id
ON listings (owner_id, status, created_at DESC, id DESC);

-- Đường feed công khai mới nhất. Kiểm tra index hiện hữu trước khi thêm.
CREATE INDEX CONCURRENTLY idx_listings_active_created_id
ON listings (created_at DESC, id DESC)
WHERE status = 'ACTIVE';

-- Khi triển khai bảng read model denormalized; đây là tên bảng đề xuất.
CREATE INDEX CONCURRENTLY idx_public_read_sale_price
ON listing_public_read (purpose, property_type, price_vnd ASC, id ASC);

-- Nếu read model có cột geometry(Point,4326) tên public_location.
CREATE INDEX CONCURRENTLY idx_public_read_location
ON listing_public_read USING GIST (public_location);
```

Với keyset giá tăng, sort và predicate phải khớp, ví dụ `(price_vnd, id) > (:last_price, :last_id)` + `ORDER BY price_vnd, id LIMIT :size_plus_one`; keyset mới nhất dùng tuple giảm tương ứng. Null, tie và cập nhật giá giữa hai trang phải có policy; dùng snapshot khi yêu cầu tính nhất quán của một phiên duyệt.

Bbox có thể dùng geometry và `ST_Intersects`/`&&` đúng hệ tọa độ; bán kính theo mét nên dùng geography và `ST_DWithin` phù hợp. Không dùng khoảng cách trên tọa độ độ như mét. Không công khai tọa độ riêng tư chính xác nếu sản phẩm chỉ được phép hiển thị vị trí gần đúng.

Keyword fallback: PostgreSQL FTS hoặc pg_trgm có thể phù hợp tùy truy vấn; normalization tiếng Việt có version và bộ test. Không gắn B-tree thông thường vào `LIKE '%...%'` rồi kết luận đã tối ưu. ES nên có mapping explicit cho keyword/numeric/date/geo, analyzer đã thử với tiếng Việt và alias địa danh; không dựa hoàn toàn dynamic mapping.

Đo `EXPLAIN (ANALYZE, BUFFERS)` trong staging với 100 nghìn rồi 1 triệu tin giả lập có phân bố hợp lý, nhiều revision/media, owner lớn, khu vực đông và ít. Theo dõi actual rows/loops, heap fetch, sort spill, buffers, p95/p99, pool wait và lock wait. Trên query ghi, ANALYZE thực sự thực thi câu lệnh; không dùng tùy tiện trên production.

### 7.3. Chính sách cache khởi điểm

Các TTL sau là giá trị thử nghiệm, phải điều chỉnh theo traffic, freshness và cache hit ratio.

| Dữ liệu | Lớp / TTL khởi điểm | Key và invalidation |
|---|---|---|
| JS/CSS tên có content hash | Browser/CDN, 1 năm immutable | Thay tên file khi build; giữ HTML revalidate phù hợp |
| Thumbnail public | CDN, dài hạn theo content hash | URL mới khi ảnh đổi; purge/takedown theo policy |
| Public listing detail | Redis/app 30–60 giây + ETag | listingId + publicRevisionId + verification version; invalidate publish/hide/price |
| Search trang đầu | Redis 15–30 giây nếu đo thấy hữu ích | Hash toàn bộ normalized filters+sort+page/cursor+index generation; không cache mọi bbox tự do vô hạn |
| Cluster bản đồ | Tile/zoom/filter key, 15–30 giây | Coarsen theo tile có kiểm soát; không gộp vùng khiến kết quả sai |
| Dự án/khu vực/CMS public | 5–15 phút | Version nội dung và sự kiện xuất bản |
| Geocode | 7–30 ngày theo chính sách provider | Query normalized + provider + ngôn ngữ + data version; expiry và refresh, negative cache ngắn |
| Hồ sơ KYC, lead, billing, admin | `private, no-store` theo độ nhạy cảm | Không dùng shared cache; kiểm tra quyền mỗi request |
| Danh mục loại hình | Cache nhỏ 1 giờ hoặc versioned | Evict khi quản trị thay đổi |

Cần chống stampede bằng single-flight/lock ngắn, TTL jitter; thiết kế Redis lỗi vẫn có giới hạn DB load. Không dùng stale-while-revalidate cho dữ liệu bị thu hồi quyền xem nếu chưa có cơ chế chặn an toàn.

### 7.4. Thuật toán và cấu trúc dữ liệu: đã có gì, nên thêm gì?

| Bài toán | Hiện tại | Cách phù hợp khi tăng quy mô |
|---|---|---|
| Tìm văn bản | ES inverted index; fallback LIKE | Analyzer/normalization, synonyms/alias, field boost và bộ đánh giá truy vấn; không cần tự viết search engine |
| Lấy trang sâu | Offset/from-size | Keyset B-tree hoặc search_after; chi phí không tăng theo số dòng bỏ qua như offset sâu, tùy index/filter |
| Ghép kết quả theo ID | Map giữ thứ tự hydrate, batch seller | Giữ lại, giảm tập fields; O(k) ghép theo số ID của trang thay vì tìm lặp tuyến tính toàn tập |
| Bản đồ | Cluster trong MapLibre trên dữ liệu đã tải | Spatial index + server grid/geotile aggregation; cluster client cho tập vừa phải, không tải hàng trăm nghìn điểm |
| Nhận biết tin trùng | Chưa thấy pipeline đầy đủ | Exact hash normalized trước; candidate theo project/địa chỉ/phone hash; similarity text/ảnh trên candidates, tránh so sánh mọi cặp O(n²) |
| Chọn gợi ý top-k | Chưa thấy recommender thực | Dùng search ranking rule-based trước; heap/top-k nếu xử lý candidate stream; chỉ ML khi có dữ liệu đánh giá |
| Nhắc lịch | Lead status hiện có | Durable scheduled jobs/queue với indexed due_at; claim có lease/idempotency, không quét toàn bảng liên tục |
| Rate limit | Counter Redis + map fallback | Atomic counter/sliding-window hoặc token bucket theo nhu cầu, bộ nhớ giới hạn |

Không có bằng chứng cần tự cài trie, graph database hay Kafka để hệ thống tốt hơn ngay. Tối ưu “Array.find trên 100 marker” có lợi nhỏ hơn rất nhiều so với loại bỏ full scan hàng triệu bản ghi. Partitioning chỉ đặt ra khi một bảng append-only lớn, retention hoặc query plan đã chứng minh nhu cầu; tránh chia nhỏ bảng listing sớm.

### 7.5. Kế hoạch benchmark và ngân sách vận hành

Repo đã đặt mục tiêu 100 RPS đọc, 10 RPS ghi, API search p95 <500 ms, availability 99,9%. Script k6 hiện dùng VU theo stages và chủ yếu gọi search page 0; **25 VU không đồng nghĩa 25 RPS hoặc chứng minh đạt 100 RPS**. Cần constant-arrival-rate và workload mix phù hợp [C30].

| Kịch bản | Cần kiểm tra | Điều kiện nghiệm thu đề xuất |
|---|---|---|
| Load chuẩn | Search nhiều filter, detail, map, lead; dataset 100k | 100 read RPS +10 write RPS, p95 read <500 ms, p99 <1s, error <1%; đây là mục tiêu, chưa đạt đo |
| Cold/warm | Cache lạnh, cache nóng, keyword phổ biến và hiếm | Báo riêng từng nhóm, không che cold miss bằng average |
| Dữ liệu lớn | 1 triệu tin, nhiều revision/ảnh, owner lớn | Query count trang cố định, không OOM; xác định ngưỡng capacity và chi phí |
| Burst/soak | Tải tăng đột biến, chạy dài | Không leak connection/heap/SSE; backlog quay về ngưỡng chấp nhận |
| ES/Redis hỏng | Timeout, restart, circuit breaker | DB không bị dồn tải không giới hạn; mất tính năng có kiểm soát, cảnh báo rõ |
| Ghi cạnh tranh | Lead idempotency, approve gói, revision/publication | Không double effect, không lộ draft, không mất update |
| Index rebuild | Backfill trong lúc có thay đổi | Không mất delete/update, alias switch không gián đoạn và có rollback |
| Khôi phục | Restore DB + object consistency | RPO/RTO do doanh nghiệp chốt; gợi ý thử RPO ≤15 phút, RTO ≤60 phút rồi đo thực tế |

Observability tối thiểu: request rate/error/latency theo route; DB pool/slow query/locks; ES lag và bulk failures; outbox lag/DLQ; Redis hit/eviction; queue age; storage growth; 429; notification delivery; Core Web Vitals theo thiết bị. Trace các luồng search→DB/ES và lead→outbox nhưng không đưa PII vào label/log.

Production nhỏ có thể bắt đầu bằng modular monolith, DB có backup/PITR, object storage+CDN và worker; mở rộng 2 app instance sau khi sửa SSE/scheduler. K8s chỉ nên giữ nếu đội có khả năng vận hành; không cần mua độ phức tạp trước khi biết tải và ngân sách.

## 8. Bộ tiêu chuẩn thiết kế có thể áp dụng

### 8.1. Nhận xét thẳng về UI hiện tại

Baseline home cho thấy bố cục sạch, blue/navy nhất quán, headline và card khá rõ. Đây là cơ sở tốt để nâng cấp. Tuy nhiên, chưa thể kết luận “đẹp hơn các trang khác” chỉ bằng một ảnh baseline; chưa có kiểm thử người dùng hoặc kiểm tra trực quan live đầy đủ. Điểm cần cải thiện cụ thể từ code: font label có chỗ 10px, chip nhỏ, icon emoji xen Lucide, đơn vị giá thuê, gallery không hết ảnh, nested interaction trong card, bộ lọc và URL lệch nhau. Những việc này ảnh hưởng chất lượng cảm nhận nhiều hơn thay màu hay thêm animation.

Hướng thiết kế: **giữ navy, nền sáng và Be Vietnam Pro; tăng độ rõ thông tin, giảm nhãn trang trí, để ảnh thật và giá dẫn mắt.** Tham khảo tác vụ: tìm kiếm/bản đồ từ Rightmove–idealista, lưu/chia sẻ từ Redfin, phạm vi xác thực từ Batdongsan.com.vn, cách giải thích dữ liệu giá từ Zillow/OneHousing. Đây là chọn pattern chức năng, không phải sao chép pixel hoặc xếp hạng thẩm mỹ các website.

### 8.2. Tokens

Kế thừa `design web desktop/modern_trust_real_estate/DESIGN.md`; chuẩn hóa thành một nguồn token dùng cho Tailwind/CSS và component catalog.

| Token | Giá trị đề xuất | Cách dùng |
|---|---|---|
| Brand primary | `#00355F` | CTA chính, điều hướng active, giá nổi bật |
| Brand hover/container | `#0F4C81` | Hover/selected tương ứng, tránh phủ quá nhiều vùng lớn |
| Success | `#006C4A` | Xác minh có căn cứ, trạng thái thành công; luôn kèm nhãn |
| Background | `#F8F9FF` | Nền tổng thể |
| Surface | `#FFFFFF` | Card/form/menu |
| Text primary | `#0B1C30` | Nội dung chính |
| Text secondary | `#42474F` | Metadata có đủ tương phản |
| Border | `#C2C7D1` | Input và phân cách; kiểm tra contrast theo chức năng |
| Error / warning | `#BA1A1A` / `#8A4B00` | Thông báo lỗi/cần chú ý, không dùng để làm mọi giá trông gấp |
| Font | Be Vietnam Pro, fallback system-ui | Vietnamese-first; chỉ tải weight cần, cân nhắc self-host |
| Text scale | 40/48, 32/40, 24/32, 18/26; body 16/26, 14/22; label 12/16 | Đơn vị px/line-height; mobile H1 28/36; không dùng 10px cho thông tin quan trọng |
| Spacing | 4, 8, 12, 16, 24, 32, 48, 64 | Dùng 8/16/24 thường xuyên; hạn chế giá trị tùy ý |
| Radius | 8 input, 12 card/dialog nhỏ, 16 panel; pill chỉ cho chip/badge | Không bo mọi thứ thành viên thuốc |
| Shadow | Nhẹ cho elevated popup/CTA panel | Card bình thường ưu tiên border + spacing |
| Icon | Lucide 16/20/24 | Một stroke/style; label cho icon-only button |
| Control | Input/button 44–48px trên touch | Đây là mục tiêu sản phẩm; không diễn giải thành quy định WCAG AA luôn bắt 44px |

WCAG 2.2 AA: contrast text thường ≥4,5:1, text lớn ≥3:1, focus rõ/không bị che, thao tác keyboard và reflow. Target Size Minimum là 24×24 CSS px với ngoại lệ; bộ sản phẩm chọn 44–48px để dễ chạm [T6]. Kiểm tra contrast trên cặp màu thực tế, không chứng nhận chỉ từ bảng tokens.

### 8.3. Layout theo trang

| Màn hình | Desktop | Mobile |
|---|---|---|
| Home | Container 1200–1280px, header gọn; hero tập trung search; listing thật xuất hiện sớm; khu vực phổ biến có dữ liệu | Padding 16px, một CTA tìm nhà rõ, bỏ khối trang trí lớn; giữ lịch sử tìm kiếm |
| Search | Filter bar + result summary; split list/map khoảng 55/45 khi đủ rộng; chọn card làm nổi pin tương ứng | List mặc định, filter bottom sheet và nút map; giữ vị trí cuộn khi đóng chi tiết; marker mở bottom sheet |
| Listing detail | Gallery; nội dung khoảng 2/3 + contact panel 1/3; facts, mô tả, trust, vị trí, người đăng | Giá + facts rõ; CTA liên hệ ở đáy không che nội dung/focus; gallery swipe có nút thay thế |
| Seller | Danh tính, vai trò, phạm vi xác minh, inventory paged; số phản hồi chỉ khi có mẫu đủ | Tóm tắt gọn, filter kho tin; tránh kéo qua bio dài để tới tin |
| Đăng tin | Stepper 4 bước; draft tự lưu có trạng thái; preview card và checklist chất lượng | Mỗi bước ngắn; bàn phím đúng loại; lỗi gần field và vẫn giữ dữ liệu |
| Broker workspace | Queue công việc + lead table có filter/column; lịch sử và next action | Card công việc, thao tác chính 1–2 nút; tránh bảng ngang 12 cột |
| Admin | Mật độ cao hơn public, filters server-side, drawer chi tiết và audit | Hỗ trợ tác vụ cốt lõi; review giấy tờ phức tạp có thể ưu tiên desktop rõ ràng |

Breakpoints là điểm layout đổi theo nội dung, thử tối thiểu 360/390/768/1024/1440px và zoom 200%; không chỉ kiểm tra thiết bị mẫu có sẵn. Không đưa thuật ngữ DB, module, outbox hoặc mã trạng thái nội bộ vào hành trình người tìm nhà.

### 8.4. Bộ component tối thiểu

| Component | Hợp đồng nội dung/hành vi | Trạng thái phải có |
|---|---|---|
| SearchBox | Gợi ý tách địa điểm/dự án/từ khóa; keyboard; hủy request cũ; lịch sử | Idle, loading, suggestions, no match, provider error |
| FilterBar/Sheet | Nhãn và đơn vị đúng purpose; applied count; clear; apply mobile | Default, active, invalid range, loading, restored from URL |
| ListingCard | Ảnh 16:10, giá+chu kỳ, title 2 dòng, area/bedrooms, location, freshness, người đăng | Missing/broken image, verified scopes, expired/hidden, favorite saving/error |
| TrustBadge/Panel | Identity, listing checked, ownership evidence tách loại; phạm vi/ngày kiểm tra | Pending, verified, expired, revoked, not checked |
| Gallery | Thumbnail/lightbox đủ ảnh, counter, full keyboard và focus return | Loading, error, single image, 20 images |
| ContactPanel | Một CTA chính; cho biết bước kế tiếp và dữ liệu cần; không hứa thời gian chưa đo | Guest, authenticated, verification required, submitting, accepted, duplicate, unavailable |
| Compare | Tối đa 3, cùng purpose; differences-only; giá/đơn vị và dữ liệu mới nhất | Empty, partial, full, expired item, refresh error |
| FormField | Label thật, hint, lỗi gắn aria-describedby; không chỉ placeholder | Pristine, editing, invalid, disabled, saving |
| DataTable/Queue | Page/filter/sort server; row selection phạm vi rõ; audit action | Empty, initial loading, refresh, partial error, permission denied, conflict |
| Toast/InlineFeedback | Thành công chỉ sau backend commit; không thay thế lỗi tại field | Success, retryable error, conflict, offline |

Tách anchor của title/card khỏi nút favorite/compare và link người đăng; không đặt phần tử tương tác lồng nhau rồi vá bằng event propagation. Nhãn “Đã xác thực” phải trả lời *xác thực cái gì*; “đã xác minh danh tính” không bảo đảm quyền sở hữu, nội dung tin hay pháp lý giao dịch.

### 8.5. UX flow đề xuất

Người tìm nhà: chọn mua/thuê → tiêu chí phù hợp → xem card/map → đọc detail và trust → lưu/so sánh → gửi yêu cầu → chọn hoặc xác nhận lịch → theo dõi. Cho phép khám phá trước khi đăng nhập. Nếu bước nào bắt buộc xác minh, giải thích tại đúng thời điểm và quay về mục đích ban đầu sau xác minh.

Người đăng: chọn vai trò đúng → draft tự lưu → kiểm tra tính đầy đủ/ảnh/địa chỉ → preview → gửi duyệt → biết lý do từ chối và sửa → quản lý công khai/bản sửa → tiếp nhận lead và xác nhận còn hàng. Tin hết hiệu lực cần nhắc và chuyển trạng thái có chủ đích; không tồn tại mãi như tin mới.

Admin: hàng đợi theo mức ưu tiên → claim → đối chiếu public/draft/chứng cứ → quyết định có lý do → audit và khả năng xử lý khiếu nại. Không dùng màu làm tín hiệu duy nhất hoặc nút chấp nhận/từ chối quá gần trên touch.

### 8.6. Tiêu chí đánh giá thiết kế

Tổ chức thử tác vụ với người mua/thuê và môi giới thuộc phân khúc chọn; vòng đầu 5–8 người mỗi nhóm để phát hiện vấn đề định tính, không suy diễn thành thống kê toàn thị trường. Tác vụ gồm tìm nhà theo ngân sách, chỉ xem tin xác thực đúng phạm vi, so sánh, lưu tìm kiếm, gửi/hẹn xem và sửa tin bị từ chối. Ghi task completion, lỗi, thời gian, câu hỏi hiểu sai và lý do bỏ cuộc; sửa rồi thử lại.

Mục tiêu hiệu suất trải nghiệm: LCP ≤2,5s, INP ≤200ms, CLS ≤0,1 ở p75, phân tách mobile/desktop [T7]. Dùng RUM để quyết định; Lighthouse là công cụ lab, không chứng minh p75 người dùng. A11y cần cả axe và kiểm tra keyboard/screen reader/focus, đặc biệt dialog, filter sheet, map và gallery.

## 9. Lộ trình và backlog có thể đưa vào sprint

Ước lượng dưới là sơ bộ theo người-ngày triển khai + kiểm thử thông thường, chưa bao gồm thu thập dữ liệu/provider, nghiên cứu pháp lý hoặc chờ bên ngoài. Giả định có backend, frontend, QA và một người chịu trách nhiệm sản phẩm/thiết kế. Không cộng máy móc các hàng thành ngày lịch; sau discovery cần sizing lại.

| Ưu tiên | Gói việc | Chủ trì | Ước lượng | Phụ thuộc / nghiệm thu chính |
|---|---|---|---|---|
| P0 | CI, selector/slug, tách visual/a11y, artifacts, required checks | QA + FE + DevOps | 2–4 | F01; suite xanh có diff review |
| P1 | Search API envelope, pagination, URL/filter server, verified | BE + FE | 5–8 | F02–03; >250 tin và URL roundtrip |
| P1 | Giá thuê/purpose/compare units | FE + BE | 1–3 | F04; một money contract thống nhất |
| P1 | Public summary/detail projection, query count | BE | 3–6 | F07; không tải revision không dùng |
| P1 | Owner/lead/moderation/billing/seller paging | BE + FE | 4–7 | F08; không unbounded queue |
| P1 | Search outbox consumer/bulk/backfill/delete/version | BE | 6–10 | F05; replay, order, lag và rebuild |
| P1 | Filter validation, engine parity, cursor/circuit | BE + QA | 4–7 | F06; stable sort và fallback có hợp đồng |
| P1 | Nginx headers/proxy/rate-limit/session ADR | BE + DevOps | 3–5 | F11/F13/F20; staging response và IP chain |
| P1 | Gallery đủ ảnh, responsive image, lazy map | FE + BE | 4–7 | F14–15; route budget và LCP |
| P1 | Lead/billing concurrency + mail outbox | BE + QA | 3–6 | F17–18; PostgreSQL integration |
| P1 | Multi-node SSE/replay | BE + FE | 3–5 | F12; 2 replica test |
| P1 | Metrics, dataset, load/failure/restore test | DevOps + BE + QA | 5–8 | F09/F21; báo cáo capacity và RPO/RTO |
| P1 | Analytics events và funnel thật | BE + FE + Product | 4–7 | F19; định nghĩa lead đủ điều kiện |
| P1 | Design system + public journey polishing | Design + FE | 6–10 | Mục 8; component states/a11y/task test |
| P2 | Saved listings/search, alerts, notification preferences | BE + FE | 6–10 | Search contract, events và consent |
| P2 | SSR/prerender, sitemap index, area/project/CMS public | FE + BE + Content | 8–14 | F16; content/source data và metadata |
| P2 | Hẹn xem thực, nhắc lịch, broker SLA/ROI | BE + FE + Product | 7–12 | Events, notification, lịch vận hành |
| P2 | Chuẩn hóa tài sản/chống trùng/freshness | BE + Data + Ops | 8–15 | Có nguồn cung và quy trình review |
| P2 | Contract/format/architecture quality gates | BE + FE | 3–5 | F22; tập trung module đang sửa |
| Sau kiểm chứng | AVM, 3D tour mở rộng, recommendation, mobile native | Product + Data | Chưa ước lượng | Chỉ khi có data, nhu cầu, provider và ROI rõ |

**Giai đoạn A — 0–2 tuần theo năng lực đội:** khôi phục release gate; sửa F02–04, header, gallery; đo baseline query/load. Nếu đội chỉ một người thì kéo dài phạm vi lịch, giữ nguyên thứ tự ưu tiên.

**Giai đoạn B — tuần 3–6:** read model và ES incremental, pagination các queue, proxy/SSE, image pipeline; analytics cơ bản và design components. Gate: dữ liệu đúng, kịch bản cạnh tranh xanh, tải chuẩn/restore có kết quả thực.

**Giai đoạn C — tuần 7–12:** saved search/alerts, SEO và trang khu vực/dự án, hẹn xem; pilot với nhóm nguồn cung thực. Gate: người dùng hoàn thành tác vụ, lead có chất lượng và nguồn cung cập nhật; điều chỉnh theo số đo thay vì cố giao toàn bộ backlog.

Sau pilot, mở rộng khu vực khi retention người tìm nhà, tỷ lệ hẹn xem thực và broker renewal đạt ngưỡng do product đặt từ baseline. Không tăng ads để bù cho nguồn cung sai, liên hệ không ai phản hồi hoặc quy trình xử lý báo xấu yếu.

## 10. Điều kiện để gọi là sẵn sàng phát hành và mở rộng

1. CI bắt buộc xanh trên commit phát hành; E2E phản ánh route/slug thật; có artifact điều tra.
2. Search/filter/map/pagination thống nhất; không còn giới hạn 100 tin bị che; đơn vị thuê đúng.
3. Không trả draft/private media qua public API/cache; xác thực quyền cả trường hợp owner/admin/inactive; UI badge đúng phạm vi.
4. Tạo lead, approve gói, sửa revision và retry không gây double effect/lost update trong kiểm thử PostgreSQL cạnh tranh.
5. Query count/p95/p99, index lag, outbox backlog và error rate được đo; fallback không tạo tải dây chuyền mất kiểm soát.
6. Production headers/IP chain/session được kiểm chứng từ response cuối; backup được phân loại và restore có bằng chứng.
7. Core flows mobile/desktop, keyboard và screen reader được nghiệm thu; price, empty/error/loading/offline states đủ.
8. Có người sở hữu SLA kiểm duyệt, phản hồi lead, cập nhật còn hàng và xử lý khiếu nại; dashboard phân biệt không đo với 0.

Đây là tiêu chí cho chất lượng sản phẩm và vận hành. Đánh giá pháp lý giao dịch, mô hình trung gian tiền và điều kiện nhà cung cấp cần một phạm vi chuyên môn riêng trước khi bật các luồng liên quan; báo cáo này không xác nhận các điều kiện đó đã được đáp ứng.

## 11. Chứng cứ mã nguồn và tài liệu đối chiếu

Các liên kết C khóa tại commit kiểm tra; cần quyền repository để mở. Các nguồn M/T là nguồn chính thức, truy cập trong đợt audit; nội dung có thể thay đổi sau ngày chốt.

[C01]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/frontend/app/routes.tsx
[C02]: https://github.com/Babychandoi/Real-estate/actions/runs/36314701789
[C03]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/frontend/tests/e2e/public-navigation.spec.ts
[C04]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/frontend/tests/e2e/public.visual-a11y.spec.ts
[C05]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/frontend/app/entities/listing/ui/ListingCard.tsx
[C06]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/frontend/app/routes/_public.search.tsx
[C07]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/listing/api/ListingController.java
[C08]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/listing/domain/model/ListingSearchCriteria.java
[C09]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/search/ElasticsearchListingIndex.java
[C10]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/listing/infrastructure/persistence/repository/ListingJpaRepository.java
[C11]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/listing/infrastructure/persistence/mapper/ListingEntityMapper.java
[C12]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/listing/infrastructure/persistence/adapter/ListingPersistenceAdapter.java
[C13]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/lead/application/LeadApplicationService.java
[C14]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/billing/BillingService.java
[C15]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/listing/api/PublicProfileController.java
[C16]: https://github.com/Babychandoi/Real-estate/tree/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/resources/db/migration
[C17]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/search/GeocodingController.java
[C18]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/media/MediaController.java
[C19]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/frontend/nginx.conf
[C20]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/notification/RealtimeNotificationService.java
[C21]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/shared/security/RequestRateLimitFilter.java
[C22]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/frontend/app/routes/_public.listings.%24listingId.tsx
[C23]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/frontend/app/shared/map/ListingMap.tsx
[C24]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/listing/api/ListingSeoController.java
[C25]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/lead/api/FunnelAnalyticsController.java
[C26]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/frontend/app/shared/auth/AuthContext.tsx
[C27]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backend/src/main/java/com/company/bds/shared/security/SecurityConfig.java
[C28]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/backups/20260926-005833/README.md
[C29]: https://github.com/Babychandoi/Real-estate/tree/831a01044a5694c1ad32d08fca2f59e73263222c/infra
[C30]: https://github.com/Babychandoi/Real-estate/blob/831a01044a5694c1ad32d08fca2f59e73263222c/infra/k6/workload.js

- M1 — [Batdongsan.com.vn: Giới thiệu về Tin xác thực](https://trogiup.batdongsan.com.vn/docs/gioi-thieu-ve-tin-dang-xac-thuc).
- M2 — [Nhà Tốt: trang chủ và các nhóm sản phẩm](https://www.nhatot.com/). Chỉ dùng để xác nhận khả năng hiện diện; không sử dụng số liệu quảng cáo/quy mô trên trang làm dữ liệu thị phần.
- M3 — [OneHousing: công cụ định giá](https://onehousing.vn/cong-cu/dinh-gia).
- M4 — [Zillow: What is a Zestimate?](https://www.zillow.com/zestimate/).
- M5 — [Redfin: How to Create a Saved Search](https://support.redfin.com/hc/en-us/articles/360001432532-How-to-Create-a-Saved-Search), [Shared Saved Searches](https://support.redfin.com/hc/en-us/articles/360007862251-Shared-Saved-Searches).
- M6 — [Rightmove: Draw a Search](https://faq.rightmove.co.uk/support/solutions/articles/7000048757-draw-a-search), [Property alerts](https://faq.rightmove.co.uk/support/solutions/articles/7000048758-how-to-register-for-property-alerts).
- M7 — [idealista: trang tìm nhà trên bản đồ](https://www.idealista.com/en/venta-viviendas/alicante-alacant-alicante/mapa).
- T1 — [Elastic: Paginate search results](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/paginate-search-results).
- T2 — [PostgreSQL 16: Using EXPLAIN](https://www.postgresql.org/docs/16/using-explain.html).
- T3 — [PostGIS: Spatial Indexing](https://postgis.net/workshops/postgis-intro/indexing.html).
- T4 — [Nginx: ngx_http_headers_module](https://nginx.org/en/docs/http/ngx_http_headers_module.html).
- T5 — [Google Search Central: JavaScript SEO basics](https://developers.google.com/search/docs/crawling-indexing/javascript/javascript-seo-basics).
- T6 — [W3C: WCAG 2.2](https://www.w3.org/TR/WCAG22/).
- T7 — [web.dev: Web Vitals](https://web.dev/articles/vitals).


[M1]: https://trogiup.batdongsan.com.vn/docs/gioi-thieu-ve-tin-dang-xac-thuc
[M2]: https://www.nhatot.com/
[M3]: https://onehousing.vn/cong-cu/dinh-gia
[M4]: https://www.zillow.com/zestimate/
[M5]: https://support.redfin.com/hc/en-us/articles/360001432532-How-to-Create-a-Saved-Search
[M6]: https://faq.rightmove.co.uk/support/solutions/articles/7000048757-draw-a-search
[M7]: https://www.idealista.com/en/venta-viviendas/alicante-alacant-alicante/mapa
[T1]: https://www.elastic.co/docs/reference/elasticsearch/rest-apis/paginate-search-results
[T2]: https://www.postgresql.org/docs/16/using-explain.html
[T3]: https://postgis.net/workshops/postgis-intro/indexing.html
[T4]: https://nginx.org/en/docs/http/ngx_http_headers_module.html
[T5]: https://developers.google.com/search/docs/crawling-indexing/javascript/javascript-seo-basics
[T6]: https://www.w3.org/TR/WCAG22/
[T7]: https://web.dev/articles/vitals
