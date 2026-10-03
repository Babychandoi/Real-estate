# Google Search Console: các bước cho chủ dự án

Yêu cầu audit F16.6 (EXTERNAL: cần tài khoản Google của chủ dự án và quyền DNS Cloudflare). Tài liệu này rút gọn và làm
chặt `docs/audit-2026-09-27/streams/s7-seo.md` §6. **Chưa bước nào được thực hiện.** Không agent nào đăng nhập thay chủ
dự án.

## 0. Kiểm tra trước khi gửi sitemap

| # | Việc | Cách kiểm tra | Đạt khi |
|---|---|---|---|
| 1 | Bản deploy production có lớp prerender + sitemap | `scripts/verify-prerender.sh https://nhadatchuan.online` (giữ dưới 2 req/s: thêm `VERIFY_PACE_SECONDS=0.6`) | Script kết thúc không lỗi |
| 2 | Header bảo mật còn trên mọi location | `scripts/verify-headers.sh https://nhadatchuan.online` | Không dòng FAIL |
| 3 | `APP_PUBLIC_BASE_URL=https://nhadatchuan.online` | Mở `https://nhadatchuan.online/sitemap.xml`: mọi `<loc>` bắt đầu bằng `https://nhadatchuan.online/` | Không có `localhost`, IP hay http |
| 4 | `robots.txt` không chặn trang công khai | Mở `https://nhadatchuan.online/robots.txt` | Có dòng `Sitemap: https://nhadatchuan.online/sitemap.xml`; chỉ chặn `/api/`, `/render/`, `/tin-tuc/xem-truoc/`, `/shortlists/` và trang tài khoản, không `Disallow: /` |
| 5 | **Quyết định về nội dung UAT** (xem dưới) | Tìm `uat-` trong các file `https://nhadatchuan.online/sitemaps/*.xml` | Chủ dự án đã quyết và đã thực hiện |
| 6 | Trang thông tin pháp lý có dữ liệu thật | Mở `/about`, `/contact`, `/terms`, `/privacy` | Không còn "Chưa có dữ liệu" (biến `APP_OPERATOR_*` trong `.env`) |

**Nội dung UAT đang công khai.** Ngày 03/10/2026 site công khai có các trang slug bắt đầu bằng `uat-`, có thể index, ví dụ
`/du-an/uat-khu-do-thi-song-hong-xanh` và `/tin-tuc/uat-5-buoc-kiem-tra-phap-ly-truoc-khi-dat-coc-mua-nha`. Slug này do
`UatDataSeeder` (`backend/src/main/java/com/company/bds/shared/uat/UatDataSeeder.java`) tạo cho dự án và bài viết khi chạy
`--app.uat-seed.mode=seed`; cùng đợt seed còn có tài khoản `uat.*@example.invalid` và tin đăng tổng hợp. Gửi sitemap khi
còn nội dung này thì Google index dữ liệu giả dưới tên miền thật, và gỡ ra sau đó mất vài tuần (404/410 phải được crawl
lại). Chủ dự án chọn một:

| Lựa chọn | Làm gì | Ghi chú |
|---|---|---|
| A. Gỡ toàn bộ dữ liệu UAT trước khi gửi (khuyến nghị) | Sao lưu trước, rồi chạy backend một lần với `--app.uat-seed.mode=purge` trỏ vào DB production; seeder chỉ xóa đúng những hàng `seed` đã tạo (id bắt đầu `ee5eed`, khóa text bắt đầu `UAT`) | Việc trên production: chủ hệ thống làm, có bản sao lưu ngay trước (`docs/ops/PRODUCTION_TOPOLOGY.md` §8 "Trước khi deploy"); kiểm tra lại bằng bước 5 |
| B. Gỡ xuất bản từng mục trong admin CMS / Dự án | `/2026/nhadatchuan/admin/cms`, `/2026/nhadatchuan/admin/projects` | Tin đăng và tài khoản UAT vẫn còn |
| C. Giữ lại có chủ đích | Ghi lý do (ví dụ: demo bán hàng) | Không khuyến nghị: nội dung giả ảnh hưởng độ tin cậy và chất lượng index |

## 1. Thêm property kiểu Domain
1. https://search.google.com/search-console → Add property → **Domain** → nhập `nhadatchuan.online`.
2. Google đưa một bản ghi TXT `google-site-verification=…`. Cloudflare → `nhadatchuan.online` → DNS → Records → Add
   record: Type `TXT`, Name `@`, Content = chuỗi Google đưa, TTL Auto.
3. Quay lại Search Console → Verify (DNS có thể mất vài phút tới vài giờ). **Không xóa bản ghi TXT sau khi xác minh**:
   Google kiểm tra lại định kỳ.

Property Domain bao gồm cả http/https và mọi subdomain, nên không cần thêm property URL-prefix.

## 2. Gửi sitemap
Sitemaps → nhập `https://nhadatchuan.online/sitemap.xml` → Submit. Đây là **sitemap index**; Google tự tìm các phần
`/sitemaps/static.xml`, `/sitemaps/areas.xml`, `/sitemaps/projects.xml`, … Mong đợi: trạng thái "Success" và số URL
phát hiện xấp xỉ số tin công khai + dự án + khu vực + bài viết + các trang tĩnh. Số liệu thật lấy từ báo cáo, không ước tính.

## 3. Kiểm tra URL (URL Inspection → Test live URL)
Lấy URL mẫu thật từ chính các phần sitemap (không dùng slug `uat-` nếu đã chọn A/B):

| Loại | URL mẫu | Đạt khi |
|---|---|---|
| Trang chủ | `https://nhadatchuan.online/` | "URL is available to Google" |
| Tin đăng | `https://nhadatchuan.online/listings/<slug>` | Canonical người dùng khai báo = canonical Google chọn |
| Dự án | `https://nhadatchuan.online/du-an/<slug>` | "View tested page" → HTML có `<title>` và nội dung chính |
| Khu vực | `https://nhadatchuan.online/khu-vuc/<slug>` | "More info" không có tài nguyên bị chặn, trừ lời gọi `/api/` |
| Bài viết | `https://nhadatchuan.online/tin-tuc/<slug>` | Như trên |

Chỉ bấm "Request indexing" cho trang chủ và vài trang quan trọng; phần còn lại để sitemap lo.

## 4. Rich Results Test và Schema validator
JSON-LD do `backend/src/main/java/com/company/bds/seo/application/PrerenderService.java` sinh ra: tin đăng `Product` +
`Offer`, bài viết `Article`, dự án/khu vực `Place`, trang chủ `WebSite` + `SearchAction` + `Organization`, cùng
`BreadcrumbList`.

| Công cụ | Trang | Đạt khi |
|---|---|---|
| https://search.google.com/test/rich-results | 1 tin đăng (Product snippet), 1 bài viết (Article), 1 trang có breadcrumb | "Page is eligible for rich results"; chỉ có warning về trường không bắt buộc, không có error |
| https://validator.schema.org | Trang chủ, 1 dự án, 1 khu vực | 0 error |

`Place` không phải loại rich result của Google, và Google đã ngừng hiển thị Sitelinks search box từ cuối 2024, nên
Rich Results Test có thể báo "No items detected" cho trang chủ/dự án/khu vực — đó không phải lỗi; dùng validator.schema.org
cho các trang này. Lỗi structured data → ghi URL + ảnh và mở issue cho module `seo`.

## 5. Bằng chứng "đã làm" cần lưu (đính kèm hồ sơ F16.6)

| Bằng chứng | Dạng |
|---|---|
| Property Domain đã xác minh | Ảnh chụp Settings → Ownership verification (che email) |
| Sitemap "Success" + số URL phát hiện | Ảnh chụp trang Sitemaps |
| 5 URL Inspection (bảng mục 3) | Ảnh chụp từng kết quả "Test live URL" |
| 3 Rich Results Test | Ảnh chụp hoặc link kết quả (link hết hạn sau một thời gian, nên chụp ảnh) |
| Báo cáo Pages sau 2 tuần | Export CSV (Indexing → Pages → Export) |
| Quyết định nội dung UAT (mục 0) | Một dòng: lựa chọn, người quyết, ngày thực hiện |

## 6. Theo dõi 2–4 tuần đầu

| Báo cáo | Bình thường | Cần xử lý |
|---|---|---|
| Pages → "Excluded by 'noindex' tag" | URL tìm kiếm có bộ lọc, hồ sơ người đăng không còn tin (chủ đích) | Trang tin/dự án/bài viết công khai bị noindex |
| Pages → "Blocked by robots.txt" | Trang tài khoản/công cụ (`/kyc`, `/saved`, `/compare`, …), `/api/`, `/render/` | Trang công khai nằm trong danh sách này |
| Pages → "Not found (404)" / 410 | Tin đã gỡ, bài đã hủy xuất bản, dữ liệu UAT đã xóa | URL đang có trong sitemap mà trả 404 |
| "Soft 404" | Không có | Bất kỳ: thường là trang trống trả 200 |
| "Duplicate without user-selected canonical" | Không có | Kiểm tra canonical của URL đó bằng `curl -s <url> \| grep canonical` |
| "Crawled – currently not indexed" | Một phần trong tuần đầu | Tăng dần sau 4 tuần: xem chất lượng nội dung trang đó |
| Core Web Vitals | Chưa đủ dữ liệu trong tuần đầu | URL "Poor" (LCP > 4 s, INP > 500 ms, CLS > 0,25) |
| Sitemaps | "Success", ngày đọc gần đây | "Couldn't fetch" / "Has errors" |

Mỗi tuần một lần trong 4 tuần: ghi 3 số (Indexed, Not indexed, Sitemap discovered) vào hồ sơ F16.6. Tùy chọn: Bing
Webmaster Tools → Import from Google Search Console.
