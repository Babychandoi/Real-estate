# Diễn tập khôi phục — môi trường `synthetic` — 2026-09-28

- **Kết quả:** PASS
- **Bắt đầu:** 2026-09-28T06:05:54Z (UTC); công cụ: `scripts/restore-drill.sh`, image `bds-s5-backup:test`
- **Nơi khôi phục:** Compose project `bds-drill` cô lập (`infra/drill/compose.drill.yaml`: postgis/postgis:16-3.4, MinIO cùng bản production), không publish cổng, xóa sau diễn tập
- **Máy chạy:** Darwin arm64, Docker 29.8.0
- **Nguồn dữ liệu:** chỉ đọc `BACKUP_DIR` (không kết nối tới database/bucket nguồn); khóa age giải mã chỉ mount read-only vào container diễn tập

## Bản sao lưu được khôi phục

| Loại | Set | Tạo lúc (UTC) | Tuổi khi diễn tập | Dung lượng mã hóa | Nội dung | Công cụ |
|---|---|---|---:|---:|---|---|
| PostgreSQL | `20260928T060528Z` | 2026-09-28T06:05:29Z | 25s | 236896 B | 30 bảng, 5173 dòng | pgDump pg_dump (PostgreSQL) 16.15, age 1.2.1 |
| Object (MinIO) | `20260928T060534Z` | 2026-09-28T06:05:38Z | 16s | 15339520 B gốc | 120 object | mc mcli version RELEASE.2025-05-21T01-59-54Z, age 1.2.1 |

Checksum SHA-256 của mọi file mã hóa được kiểm tra với manifest trước khi giải mã.

## Thời gian

| Bước | Thời gian |
|---|---:|
| Khởi động PostgreSQL + MinIO cô lập (đến healthy) | 7 s |
| Giải mã + `pg_restore` vào database mới | 4.3 s |
| Giải mã + nạp lại object | 5.1 s |
| Đối soát (đếm dòng từng bảng, sha256 từng object) | 3.1 s |
| **RTO đo được cho dữ liệu** (hạ tầng + DB + object, chưa gồm deploy ứng dụng và đổi DNS/tunnel) | **16.5 s** |
| Toàn bộ diễn tập | 27 s |

**RPO:** tại thời điểm diễn tập, bản DB mới nhất đã 25 giây tuổi và bản object 16 giây tuổi. Với lịch mặc định của
`infra/compose.backup.yaml` (DB mỗi giờ, object mỗi ngày) RPO tối đa là 1 giờ cho dữ liệu DB và 24 giờ cho ảnh; bật
`infra/compose.pitr.yaml` + `infra/compose.pitr-backup.yaml` để giảm RPO DB xuống khoảng 5–10 phút.

## Đối soát PostgreSQL

| Bảng | Nguồn (manifest) | Sau khôi phục | Khớp |
|---|---:|---:|---|
| `public.api_idempotency_keys` | 0 | 0 | có |
| `public.audit_events` | 1 | 1 | có |
| `public.auth_sessions` | 1 | 1 | có |
| `public.bank_settings` | 0 | 0 | có |
| `public.broker_sla_settings` | 0 | 0 | có |
| `public.cms_article_revisions` | 0 | 0 | có |
| `public.cms_articles` | 0 | 0 | có |
| `public.deposit_contracts` | 0 | 0 | có |
| `public.email_verification_tokens` | 0 | 0 | có |
| `public.escrow_transactions` | 0 | 0 | có |
| `public.flyway_schema_history` | 26 | 26 | có |
| `public.geocode_cache` | 0 | 0 | có |
| `public.invoices` | 0 | 0 | có |
| `public.kyc_document_access_grants` | 0 | 0 | có |
| `public.leads` | 0 | 0 | có |
| `public.listing_media` | 0 | 0 | có |
| `public.listing_reports` | 0 | 0 | có |
| `public.listing_revisions` | 6 | 6 | có |
| `public.listing_verifications` | 0 | 0 | có |
| `public.listings` | 6 | 6 | có |
| `public.media_objects` | 120 | 120 | có |
| `public.outbox_events` | 0 | 0 | có |
| `public.package_orders` | 0 | 0 | có |
| `public.password_reset_tokens` | 0 | 0 | có |
| `public.projects` | 0 | 0 | có |
| `public.service_plans` | 3 | 3 | có |
| `public.user_kyc_profiles` | 0 | 0 | có |
| `public.user_notifications` | 5000 | 5000 | có |
| `public.user_roles` | 5 | 5 | có |
| `public.users` | 5 | 5 | có |

Tổng: 30 bảng trong manifest, 30 bảng sau khôi phục; 5173 dòng trong manifest, 5173 dòng sau khôi phục.

Kết quả: **PASS**

## Đối soát object

| Chỉ số | Nguồn (index) | Sau khôi phục |
|---|---:|---:|
| Số object | 120 | 120 |
| Tổng byte | 15339520 | 15339520 |
| Thiếu | 0 | 0 |
| Sai kích thước hoặc sha256 | 0 | 0 |

Kết quả: **PASS**

## Nhất quán DB ↔ object

| Chỉ số | Giá trị |
|---|---:|
| Dòng `media_objects` (tham chiếu tới object) | 120 |
| Có object tương ứng sau khôi phục | 120 |
| Tham chiếu không có object | 0 |
| Object không có dòng `media_objects` | 0 |

Mọi dòng `media_objects` đều có object tương ứng sau khôi phục.

## Ghi chú của người chạy

- Dữ liệu TỔNG HỢP, không phải production: database nguồn là database tạm s5_verify trên hạ tầng test dùng chung; bucket nguồn s5-drill-src chứa 120 object byte ngẫu nhiên.
- Diễn tập chạy lại sau Review 2 độc lập (đọc mã, không có promtool/container): sửa lỗi MAJOR (Caddy X-Forwarded-For hardening + test tự động scripts/verify-caddy-forwarding.sh) và MINOR (quét .partial cũ chủ động khi job khởi động, không chỉ chờ prune hằng ngày) không ảnh hưởng tới quy trình sao lưu/khôi phục PostgreSQL và MinIO.
