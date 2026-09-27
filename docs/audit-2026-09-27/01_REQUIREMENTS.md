# Ma trận yêu cầu — Audit 27/09/2026

Nguồn: `Real-estate_Audit_2026-09-27.md` (commit kiểm tra `831a010`). Mỗi dòng là một yêu cầu có thể nghiệm thu.
Cột **Luồng** trỏ tới luồng triển khai trong `00_PLAN.md`. Cột **Trạng thái**: `TODO` → `DONE` (có bằng chứng) /
`PARTIAL` (còn thiếu, ghi rõ) / `EXTERNAL` (cần con người hoặc hệ thống bên ngoài; đã chuẩn bị tài liệu/công cụ).
Bằng chứng (tên test, lệnh, báo cáo) được ghi trong `streams/<luồng>.md` và tổng hợp ở `04_EVIDENCE.md`.

## A. Phát hiện kỹ thuật (mục 6)

| ID | Yêu cầu | Tiêu chí nghiệm thu | Luồng | Trạng thái |
|---|---|---|---|---|
| F01.1 | E2E theo route/slug thật; selector đúng nhãn hiện tại | Không còn selector `/Chi tiết/`, URL kiểm tra slug canonical | S0-FE | TODO |
| F01.2 | Fixture dữ liệu xác định; chờ trạng thái thay cho sleep | Không còn `waitForTimeout` cố định trong spec; dữ liệu seed cố định đồng hồ | S0-FE, S0-BE | TODO |
| F01.3 | Tách visual, a11y, navigation thành kiểm tra độc lập | Screenshot fail không chặn axe/overflow | S0-FE | TODO |
| F01.4 | Upload report, diff, trace khi fail | CI upload `playwright-report`, `test-results` khi fail | S0-FE | TODO |
| F01.5 | Baseline chỉ cập nhật sau review ảnh | Quy trình ghi trong tài liệu; baseline mới kèm ảnh đã xem | S11-UX | TODO |
| F01.6 | Luồng buyer và broker xác thực dùng fixture | Spec đăng nhập buyer/broker chạy trong CI | S0-FE, S11-UX | TODO |
| F01.7 | ESLint riêng, không đếm typecheck là lint | `npm run lint` = ESLint; `npm run typecheck` = tsc | S0-FE | TODO |
| F01.8 | Suite xanh, không skip để làm xanh | CI xanh trên commit phát hành | S11-UX | TODO |
| F01.9 | Required checks + review trên main | Lệnh/tài liệu bật branch protection; cần quyền admin repo | S5-SEC | EXTERNAL |
| F02.1 | List 20–24 tin/lần, envelope `items/pageInfo(hasNext,nextCursor)` | API v2 trả envelope; UI “Xem thêm”/cuộn tải tiếp | S2-SEARCH | TODO |
| F02.2 | `total` tùy chọn, ghi rõ exact/estimated | `total.relation` = `eq`/`gte`; UI không lấy độ dài trang làm tổng | S2-SEARCH | TODO |
| F02.3 | Bản đồ dùng endpoint aggregation theo bbox/zoom cùng filter | `/api/v2/listings/map` trả points/clusters; không tải toàn bộ tin | S2-SEARCH | TODO |
| F02.4 | Offset nông cho phân trang thường, cursor cho duyệt sâu, thứ tự ổn định | Test fixture >250 tin: đi hết trang không trùng/mất | S2-SEARCH | TODO |
| F03.1 | Một schema filter duy nhất parse/serialize | Module schema dùng chung FE; backend validate cùng tham số | S2-SEARCH | TODO |
| F03.2 | Server chịu trách nhiệm mọi filter (kể cả xác thực) | Lọc xác thực ngoài trang đầu vẫn đúng | S2-SEARCH | TODO |
| F03.3 | Đổi filter reset cursor; URL giữ location/bbox, purpose, đơn vị giá, sort, view | Copy URL/reload/back/forward khôi phục cùng tiêu chí (E2E) | S2-SEARCH | TODO |
| F03.4 | push history cho thay đổi chủ ý, replace khi kéo bản đồ | E2E back/forward | S2-SEARCH | TODO |
| F04.1 | Money contract amount/currency/period; phí quản lý/đặt cọc tách riêng | API trả `price{amount,currency,period}`, `rentTerms` | S0-BE, S2-SEARCH, S3a-SUPPLY | TODO |
| F04.2 | Bộ lọc thuê theo tháng, preset theo phân khúc; không dùng ngưỡng giá bán | Chip thuê khác chip bán; đổi SALE↔RENT reset filter giá | S2-SEARCH | TODO |
| F04.3 | Card/detail/compare/search cùng đơn vị; ranh giới không chồng lấn | Test formatter + E2E hiển thị `/tháng` | S2-SEARCH | TODO |
| F05.1 | Sự kiện thay đổi tin phát cùng transaction; consumer checkpoint riêng | Trigger/queue `search-index` riêng, không dùng cờ processed chung | S0-BE, S2-SEARCH | TODO |
| F05.2 | Bulk indexing, version chống sai thứ tự, delete khi ẩn | Test phát lại không hạ version; ẩn tin → xóa doc | S2-SEARCH | TODO |
| F05.3 | Backfill theo batch/keyset; rebuild index mới rồi đổi alias | Test rebuild trong lúc có thay đổi không mất update/delete | S2-SEARCH | TODO |
| F05.4 | Retry/backoff, DLQ, cảnh báo lag | Metric lag + DLQ; alert rule | S2-SEARCH, S5-SEC | TODO |
| F05.5 | Không full scan mỗi thay đổi; lag p95 ≤10 s | Đo lag trong test tích hợp; báo cáo S10 | S2-SEARCH, S10-PERF | TODO |
| F06.1 | 400 cho enum/range/bbox sai; sort whitelist + tuple | Test validation | S2-SEARCH | TODO |
| F06.2 | `search_after` (ES) và keyset (DB); cursor ký, có filter hash/sort/version/expiry, không dùng chéo engine | Test cursor giả mạo/hết hạn/đổi engine | S2-SEARCH | TODO |
| F06.3 | Circuit breaker có timeout budget, metrics; fallback có hợp đồng công khai | Test ES timeout → DB, `degraded` trong response | S2-SEARCH | TODO |
| F06.4 | Parity ES–DB: tiếng Việt, tie-breaker, ẩn tin, query sai, deep browse | Bộ test chạy cùng dữ liệu trên hai engine | S2-SEARCH | TODO |
| F07.1 | Summary projection chỉ JOIN public revision, thumbnail, seller | Không tải mọi revision cho card | S2-SEARCH | TODO |
| F07.2 | Detail lấy public revision + media; owner/editor lấy draft riêng | Endpoint public không trả draft/private | S2-SEARCH, S3a-SUPPLY | TODO |
| F07.3 | Không nuốt LazyInitializationException | Mapper không còn catch nuốt lỗi | S3a-SUPPLY | TODO |
| F07.4 | Query count giới hạn (≤4) cho trang 24 tin có 1 hoặc 10 revision | Test đếm query bằng datasource-proxy | S2-SEARCH | TODO |
| F08.1 | Owner listings phân trang | `/my-listings` server paging | S3a-SUPPLY | TODO |
| F08.2 | Moderation pending phân trang | Queue server paging | S4-ADMIN | TODO |
| F08.3 | Lead broker JOIN theo owner_id, không tải toàn bộ tin | Test owner 10.000 tin mở trang lead không tải hết | S3b-LEADS | TODO |
| F08.4 | Billing mine/queue phân trang | API paging + UI | S4-ADMIN | TODO |
| F08.5 | Seller public >60 tin xem tiếp được | API seller listings có cursor/page | S2-SEARCH | TODO |
| F08.6 | Mọi query có sort ổn định và giới hạn size | Rà soát + test | S2/S3a/S3b/S4 | TODO |
| F09.1 | Dynamic predicates; ít mẫu ORDER BY cố định; read model | Không còn `OR :param IS NULL` ở đường public | S2-SEARCH | TODO |
| F09.2 | Cột không gian + GiST cho bbox; FTS/trgm cho keyword | Index tồn tại và được EXPLAIN dùng | S2-SEARCH, S10-PERF | TODO |
| F09.3 | EXPLAIN (ANALYZE, BUFFERS) ở 100k và 1M tin | Báo cáo kế hoạch truy vấn | S10-PERF | TODO |
| F10.1 | Cache detail/search có invalidation khi approve/hide/đổi giá, version hóa key | Test invalidation | S2-SEARCH | TODO |
| F10.2 | Không cache KYC/lead/admin ở shared cache | Header `no-store` cho endpoint nhạy cảm (test) | S5-SEC | TODO |
| F10.3 | Geocode cache có hạn, normalized key, negative cache | Test expiry | S2-SEARCH | TODO |
| F10.4 | Chống stampede, TTL jitter, Redis lỗi vẫn giới hạn tải DB | Test Redis down | S2-SEARCH | TODO |
| F11.1 | Header bảo mật có ở mọi location | Include chung; script kiểm tra HTML/asset/API/lỗi/SSE | S5-SEC | TODO |
| F11.2 | Kiểm tra response cuối qua CDN/proxy | Kết quả chạy script với domain thật | S5-SEC | TODO |
| F12.1 | DB giữ lịch sử + cursor; Redis Pub/Sub fanout giữa node | Test 2 instance: ghi ở A, SSE ở B nhận | S6-ENGAGE | TODO |
| F12.2 | Heartbeat, reconnect cả EOF, backoff+jitter, dedupe event ID, dọn client | Test reconnect không mất/không trùng | S6-ENGAGE | TODO |
| F13.1 | Chỉ tin proxy khai báo; IP thật qua chuỗi proxy | Hai người qua proxy không dùng chung quota | S5-SEC | TODO |
| F13.2 | Limit theo IP + account + endpoint; counter atomic; không dùng hashCode | Test | S5-SEC | TODO |
| F13.3 | Fallback bị chặn bộ nhớ; policy theo route; Retry-After; dashboard 429 | Test Redis down không tăng bộ nhớ vô hạn | S5-SEC | TODO |
| F14.1 | Gallery/lightbox đủ ảnh, keyboard, đếm ảnh, alt | E2E 20 ảnh xem được | S2-SEARCH | TODO |
| F14.2 | Upload tạo WebP (AVIF nếu có) 320/640/960/1600, EXIF, orientation, placeholder | Test pipeline | S1-MEDIA | TODO |
| F14.3 | srcset/sizes; không tải ảnh gốc khi mở detail; LCP đúng kích thước | E2E kiểm tra request ảnh | S1-MEDIA, S2-SEARCH | TODO |
| F14.4 | Tin ẩn xử lý asset theo chính sách có chủ đích | Chính sách + test | S1-MEDIA | TODO |
| F15.1 | Dynamic import Map chỉ khi cần | List mobile không tải chunk map | S2-SEARCH | TODO |
| F15.2 | Budget theo route | Script kiểm tra kích thước chunk trong CI | S0-FE | TODO |
| F15.3 | Đo LCP/INP/CLS | RUM web-vitals về pipeline analytics | S8-ANALYTICS | TODO |
| F16.1 | Prerender/SSR cho home, listing, project, area, bài viết | HTML ban đầu có title/canonical/nội dung chính | S7-SEO | TODO |
| F16.2 | Metadata thống nhất theo route, không rò rỉ khi đổi route | Hook meta reset (test) | S0-FE, S7-SEO | TODO |
| F16.3 | Status 404/410 đúng; redirect slug | Test HTTP status | S7-SEO | TODO |
| F16.4 | Sitemap index nhiều phần từ projection, cache snapshot | >10.000 tin không hydrate domain (test) | S7-SEO | TODO |
| F16.5 | Không index mọi tổ hợp filter | robots/noindex cho trang filter | S7-SEO | TODO |
| F16.6 | Kiểm tra Search Console | Cần quyền Search Console | S7-SEO | EXTERNAL |
| F17.1 | Quota atomic (không count-then-insert) | 20 request song song không vượt quota (PostgreSQL) | S3b-LEADS | TODO |
| F17.2 | Idempotency scoped actor + route + payload hash, có hạn lưu | Khác actor không nhận replay; retry nhận đúng leadId | S3b-LEADS | TODO |
| F17.3 | Chính sách pause listing cạnh tranh với tạo lead | Test + tài liệu | S3b-LEADS | TODO |
| F17.4 | Đo tỷ lệ bỏ cuộc trước/sau KYC | Event funnel lead_form_opened→kyc_required→lead_submitted | S3b-LEADS, S8 | TODO |
| F17.5 | Quyết định thay đổi yêu cầu KYC | Product/security chốt — giữ nguyên KYC | — | EXTERNAL |
| F18.1 | Email billing qua outbox bền vững | SMTP lỗi không giữ transaction; retry | S0-BE, S4-ADMIN | TODO |
| F18.2 | Order create idempotency; không cộng quyền nhiều lần | Test song song | S4-ADMIN | TODO |
| F18.3 | Compare-and-set bank settings | Hai admin sửa → một bên nhận 409 | S4-ADMIN | TODO |
| F18.4 | Audit đầy đủ, trạng thái đối soát bền vững | Lịch sử sự kiện đơn | S4-ADMIN | TODO |
| F19.1 | Event versioned: search, impression, detail, favorite, lead submitted/qualified, appointment confirmed/completed | Catalog + ingestion | S0-BE, S8 | TODO |
| F19.2 | Dedupe event ID; loại bot/internal; consent và retention | Test | S8-ANALYTICS | TODO |
| F19.3 | Dashboard phân biệt “chưa đo” và 0; theo nguồn/khu vực/thiết bị/thời gian; định nghĩa + độ trễ | Fixture 1 hành trình → funnel đúng một lần | S8-ANALYTICS | TODO |
| F20.1 | ADR mô hình session nhất quán | `docs/adr/0001-session-model.md` | S5-SEC | TODO |
| F20.2 | Test logout/revoke/expiry | Test tích hợp | S5-SEC | TODO |
| F20.3 | MFA admin | TOTP + recovery codes; test | S5-SEC | TODO |
| F20.4 | Least privilege | Rà soát ma trận quyền + test | S5-SEC | TODO |
| F21.1 | Phân loại backup trong Git LFS | Tài liệu phân loại | S5-SEC | TODO |
| F21.2 | Backup mã hóa, ACL, retention, restore drill | Dịch vụ backup + script + biên bản drill | S5-SEC, S10-PERF | TODO |
| F21.3 | Kế hoạch xử lý lịch sử Git/rotation (không tự xóa) | Kế hoạch; thực thi cần chủ repo quyết | S5-SEC | EXTERNAL |
| F21.4 | Tách production khỏi máy dev; đánh giá managed DB/object storage | Tài liệu topology; thực thi là việc hạ tầng | S5-SEC | EXTERNAL |
| F21.5 | RPO/RTO đo được; rollback deploy + migration tương thích | Biên bản drill + runbook rollback | S10-PERF | TODO |
| F22.1 | Format tự động; tách query/transport/mapping/retry | Spotless/Prettier; file dài được tách | S9-QUALITY | TODO |
| F22.2 | Log có cấu trúc + requestId, không log PII | MDC filter + masking + test | S9-QUALITY | TODO |
| F22.3 | DTO validation + Problem Details nhất quán | Test lỗi chuẩn | S9-QUALITY | TODO |
| F22.4 | OpenAPI snapshot/diff, generate type/client | CI kiểm tra snapshot; FE dùng type sinh ra | S9-QUALITY | TODO |
| F22.5 | ArchUnit cho boundary | Test ArchUnit | S9-QUALITY | TODO |

## B. Hành trình và giao diện (mục 5)

| ID | Route | Yêu cầu | Luồng | Trạng thái |
|---|---|---|---|---|
| UI-01 | `/` | Tìm kiếm và nguồn cung thật lên sớm; không số liệu/demo copy thiếu căn cứ | S7-SEO | TODO |
| UI-02 | `/search` | Phân trang, filter server, URL, đơn vị thuê, map/list (split desktop), zero-result gợi ý | S2-SEARCH | TODO |
| UI-03 | `/listings/:id` | Gallery đủ ảnh, giá thuê, trust panel, CTA, metadata | S2-SEARCH | TODO |
| UI-04 | `/nguoi-dang/:id` | Inventory phân trang; tách xác minh danh tính và xác minh từng tin | S2-SEARCH | TODO |
| UI-05 | `/compare` | Khác biệt, giá thuê đúng chu kỳ, phản ánh tin không còn hoạt động | S2-SEARCH | TODO |
| UI-06 | `/listings/new` | 4 bước cơ bản–vị trí–ảnh–xem trước; tự lưu draft; lỗi tại trường; vai trò chủ nhà rõ | S3a-SUPPLY | TODO |
| UI-07 | `/my-listings` | Phân trang, lọc trạng thái, bản công khai và bản sửa tách biệt | S3a-SUPPLY | TODO |
| UI-08 | `/broker/workspace` | SLA thực, việc cần làm hôm nay, phân công, lịch sử tiếp nhận lead | S3b-LEADS | TODO |
| UI-09 | `/my-leads` | JOIN owner, filter server, lịch sử trạng thái, chống cập nhật đè | S3b-LEADS | TODO |
| UI-10 | `/my-inquiries` | Lịch hẹn/đổi lịch, trạng thái phản hồi thực, rút yêu cầu | S3b-LEADS | TODO |
| UI-11 | `/billing` | Trạng thái đơn/đối soát rõ, snapshot, phân trang lịch sử, chống tạo lặp | S4-ADMIN | TODO |
| UI-12 | `/kyc` | Phạm vi, lý do cần giấy tờ, trạng thái/lý do từ chối, gửi lại | S4-ADMIN | TODO |
| UI-13 | `/account` | Đồng bộ cache sau sửa, ảnh vỡ, quản lý thông báo/quyền riêng tư | S6-ENGAGE | TODO |
| UI-14 | `/verify-email`, `/forgot-password`, `/reset-password` | Token hết hạn/đã dùng/sai, throttling gửi lại, thông báo trung tính, quay lại mục đích ban đầu | S5-SEC | TODO |
| UI-15 | `/about`, `/terms`, `/privacy`, `/contact` | Thông tin chủ thể/vận hành thực (cấu hình), liên kết CMS đã duyệt | S7-SEO | TODO |
| UI-16 | `*` | Status 404 thật ở tầng render/edge | S7-SEO | TODO |
| UI-17 | Admin login | MFA, kiểm soát phiên, audit | S5-SEC | TODO |
| UI-18 | Admin moderation | Phân trang, claim/assignment, SLA, bulk action có phạm vi và lý do | S4-ADMIN | TODO |
| UI-19 | Admin listings | Phân trang server, lọc/truy vết bản sửa, khóa/ẩn có lý do, preview draft không công khai | S4-ADMIN | TODO |
| UI-20 | Admin users | Least privilege, lịch sử khóa/mở và đổi vai trò, hạn chế dữ liệu nhạy cảm | S4-ADMIN | TODO |
| UI-21 | Admin leads-and-reports | Tách hàng đợi lead và báo xấu, SLA, audit | S4-ADMIN | TODO |
| UI-22 | Admin verification | Đối chiếu bằng chứng và quyết định; KYC không vào cache public | S4-ADMIN | TODO |
| UI-23 | Admin billing | Đối soát theo trạng thái, kiểm soát cạnh tranh, ngoại lệ/chênh tiền, lịch sử người duyệt | S4-ADMIN | TODO |
| UI-24 | Admin analytics | “Chưa đo” khác 0; cohort/khung thời gian | S8-ANALYTICS | TODO |
| UI-25 | Admin projects/cms | Hoàn tất hành trình public dự án/bài viết | S7-SEO | TODO |
| UI-26 | Alias admin | Regression test redirect alias, không đường cụt/không lộ quyền | S11-UX | TODO |
| UI-27 | `_account.contracts.tsx` | Gỡ tệp chết/không tính là tính năng | S0-FE | TODO |

## C. Khoảng trống sản phẩm (mục 4.1, 4.2)

| ID | Nhóm | Phần phải triển khai | Luồng | Trạng thái |
|---|---|---|---|---|
| P-01 | Tìm kiếm | Phòng ngủ, pháp lý, nội thất/chi phí thuê; địa danh có dấu/không dấu, bí danh; URL hoàn chỉnh; kết quả ngoài trang đầu | S2-SEARCH | TODO |
| P-02 | Giữ chân | Favorite tài khoản, saved search, cảnh báo tin mới/giảm giá/còn hàng, tần suất; shortlist chia sẻ có quyền và ngừng thông báo | S6-ENGAGE | TODO |
| P-03 | Hẹn xem | Slot, xác nhận hai bên, nhắc lịch, đổi/hủy, ghi nhận no-show | S3b-LEADS | TODO |
| P-04 | Trust | Phân loại identity/listing/ownership; bằng chứng còn hiệu lực; báo hết hàng; lịch sử xử lý khiếu nại | S4-ADMIN, S2 | TODO |
| P-05 | Dữ liệu BĐS | Tài sản thực vs nhiều tin; chống trùng; nguồn, freshness, lịch sử giá | S4-ADMIN (tài sản, chống trùng), S2 (lịch sử giá), S3a (nguồn, freshness) | TODO |
| P-06 | Khu vực/dự án | Trang public có inventory, tiện ích có nguồn, phương pháp thống kê, SEO | S7-SEO | TODO |
| P-07 | Nội dung | CMS đã duyệt lên public, revision, preview, lịch xuất bản, tác giả/nguồn | S7-SEO | TODO |
| P-08 | Seller/broker | Import có kiểm tra, chất lượng tin, SLA phản hồi, báo cáo lead đủ điều kiện và ROI | S3a (import, chất lượng), S3b (SLA, ROI) | TODO |
| P-09 | Chủ nhà | Persona chủ nhà (vai trò OWNER) có capability đăng tin, nhãn rõ | S0-BE, S0-FE, S3a-SUPPLY | TODO |
| P-10 | Analytics | Event pipeline, cohort, nguồn traffic/attribution có giới hạn, chất lượng lead | S8-ANALYTICS | TODO |
| P-11 | Thanh toán | Idempotency, audit, exception queue; tách dịch vụ đăng tin khỏi tiền cọc BĐS | S4-ADMIN | TODO |
| P-12 | AI/3D/chat | Không giả lập; giữ 501 rõ ràng; tiêu chí mở khi có dữ liệu | S7-SEO (tài liệu) | TODO |
| P-13 | Chỉ số trung tâm | Lịch hẹn hai bên xác nhận / người tìm đủ điều kiện / tuần + zero-result, search→detail, detail→lead đủ ĐK, thời gian phản hồi, lead→hẹn, hẹn diễn ra, tỷ lệ tin hết hàng, quay lại; broker: chi phí/lead đủ ĐK, gia hạn | S8-ANALYTICS | TODO |
| P-14 | Vận hành nguồn cung | Chuẩn hóa ảnh/địa chỉ/giá/xác nhận còn hàng; thu hồi tin không cập nhật; kiểm tra ngẫu nhiên; phản hồi báo cáo | S3a-SUPPLY, S4-ADMIN | TODO |
| P-15 | Pilot/phỏng vấn/tuyển nguồn cung | Giả thuyết sản phẩm phải thử với người thật | — | EXTERNAL |

## D. Dữ liệu và hiệu suất (mục 7)

| ID | Yêu cầu | Luồng | Trạng thái |
|---|---|---|---|
| D-01 | PostgreSQL là nguồn chuẩn; read model `listing_public_read`; ES là chỉ mục dựng lại được, không quyết định quyền | S2-SEARCH | TODO |
| D-02 | Luồng xuất bản: transaction + queue → read model/index → invalidate cache → notification; tin khóa/ẩn chặn ngay ở lớp đọc | S2-SEARCH | TODO |
| D-03 | Search API envelope (items, pageInfo, total, queryVersion, dataAsOf); `verified` là object/enum rõ nghĩa | S2-SEARCH | TODO |
| D-04 | Map cluster/count theo bbox+zoom, quota, giới hạn độ rộng vùng | S2-SEARCH | TODO |
| D-05 | Index theo EXPLAIN (owner/status, feed ACTIVE, read model giá, GiST vị trí); không tạo index cảm tính | S2, S10 | TODO |
| D-06 | Keyset tuple khớp sort; chính sách null/tie/đổi giá giữa hai trang | S2-SEARCH | TODO |
| D-07 | Bbox bằng geometry `&&`/`ST_Intersects`; bán kính bằng geography; không công khai tọa độ chính xác | S2-SEARCH | TODO |
| D-08 | Keyword: FTS/pg_trgm, normalization tiếng Việt có version + bộ test; ES mapping explicit, analyzer tiếng Việt, alias địa danh | S2-SEARCH | TODO |
| D-09 | Chính sách cache theo bảng 7.3 (assets, thumbnail, detail, search trang đầu, cluster, dự án/CMS, geocode, dữ liệu nhạy cảm, danh mục) | S2, S1, S7, S5 | TODO |
| D-10 | Chống trùng: exact hash → candidate theo khu vực/diện tích/giá → similarity trên candidate (không O(n²)) | S4-ADMIN | TODO |
| D-11 | Gợi ý top-k rule-based (tin tương tự, zero-result) | S2-SEARCH | TODO |
| D-12 | Nhắc lịch bằng job bền vững có `due_at` index, lease/idempotency | S0-BE, S3b-LEADS | TODO |
| D-13 | Benchmark: tải chuẩn 100 RPS đọc + 10 RPS ghi (constant-arrival-rate), cold/warm, 1M tin, burst/soak, ES/Redis hỏng, ghi cạnh tranh, rebuild index, khôi phục | S10-PERF | TODO |
| D-14 | Observability tối thiểu (route RED, DB pool/slow/locks, ES lag, outbox/queue lag/DLQ, Redis hit/eviction, storage, 429, notification, CWV) | S5-SEC, S8 | TODO |
| D-15 | Topology production nhỏ; 2 instance sau khi sửa SSE/scheduler | S5-SEC (tài liệu), S0-BE (scheduler lock) | TODO |

## E. Thiết kế (mục 8)

| ID | Yêu cầu | Luồng | Trạng thái |
|---|---|---|---|
| DS-01 | Một nguồn token (màu, chữ, spacing, radius, shadow, icon, control) cho Tailwind/CSS + catalog component | S0-FE | TODO |
| DS-02 | Be Vietnam Pro tự host, chỉ weight cần | S0-FE | TODO |
| DS-03 | Không dùng 10px cho thông tin quan trọng; control 44–48px; icon Lucide thống nhất, bỏ emoji | S0-FE, S11-UX | TODO |
| DS-04 | WCAG 2.2 AA: contrast, focus, keyboard, reflow; kiểm tra trên cặp màu thực tế | S11-UX | TODO |
| DS-05 | Layout theo trang (bảng 8.3) desktop/mobile | S2, S3a, S3b, S4, S7, S11 | TODO |
| DS-06 | Kiểm tra 360/390/768/1024/1440 và zoom 200% | S11-UX | TODO |
| DS-07 | Không dùng thuật ngữ nội bộ trong hành trình người tìm nhà | S11-UX | TODO |
| DS-08 | Component tối thiểu và đủ trạng thái (SearchBox, FilterBar/Sheet, ListingCard, TrustBadge/Panel, Gallery, ContactPanel, Compare, FormField, DataTable/Queue, Toast/InlineFeedback) | S0-FE (+ luồng dùng) | TODO |
| DS-09 | Tách anchor khỏi nút favorite/compare/link người đăng (không lồng tương tác) | S2-SEARCH | TODO |
| DS-10 | Nhãn xác thực trả lời “xác thực cái gì” | S2-SEARCH | TODO |
| DS-11 | UX flow người tìm nhà (khám phá trước đăng nhập, quay về mục đích sau xác minh) | S2, S3b, S5 | TODO |
| DS-12 | UX flow người đăng (draft tự lưu → preview → gửi duyệt → lý do từ chối → quản lý bản công khai/bản sửa → lead → xác nhận còn hàng; tin hết hiệu lực được nhắc) | S3a-SUPPLY | TODO |
| DS-13 | UX flow admin (ưu tiên → claim → đối chiếu → quyết định có lý do → audit; không dùng màu làm tín hiệu duy nhất; nút chấp nhận/từ chối không sát nhau) | S4-ADMIN | TODO |
| DS-14 | Thử tác vụ với 5–8 người mỗi nhóm | Chuẩn bị protocol; thực hiện cần người dùng thật | EXTERNAL |
| DS-15 | CWV p75 LCP ≤2,5 s, INP ≤200 ms, CLS ≤0,1 bằng RUM; a11y axe + keyboard/screen reader cho dialog, filter sheet, map, gallery | S8, S11 | TODO |

## F. Điều kiện phát hành (mục 10)

| ID | Điều kiện | Luồng | Trạng thái |
|---|---|---|---|
| R-1 | CI bắt buộc xanh; E2E theo route/slug thật; có artifact | S0-FE, S11 | TODO |
| R-2 | Search/filter/map/pagination thống nhất; không còn giới hạn 100; đơn vị thuê đúng | S2 | TODO |
| R-3 | Không lộ draft/private media qua API/cache; kiểm tra quyền owner/admin/inactive; badge đúng phạm vi | S2, S1, S4 | TODO |
| R-4 | Lead, approve gói, sửa revision, retry không double effect/lost update (test PostgreSQL cạnh tranh) | S3a (revision), S3b (lead), S4 (gói) | TODO |
| R-5 | Query count/p95/p99, index lag, outbox backlog, error rate được đo; fallback có kiểm soát | S10 | TODO |
| R-6 | Header/IP chain/session kiểm chứng từ response cuối; backup phân loại, restore có bằng chứng | S5, S10 | TODO |
| R-7 | Core flows mobile/desktop, keyboard, screen reader nghiệm thu; đủ trạng thái giá/empty/error/loading/offline | S11 | TODO |
| R-8 | Người sở hữu SLA kiểm duyệt/lead/còn hàng/khiếu nại; dashboard phân biệt không đo với 0 | S8 (dashboard) + tổ chức | PARTIAL/EXTERNAL |
