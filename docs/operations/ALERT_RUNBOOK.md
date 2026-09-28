# Runbook cảnh báo

Mỗi cảnh báo trong `infra/observability/rules/bds-alerts.yml` trỏ tới một mục dưới đây (PROJECT_CODE_RULES_BDS.md §13).
Dashboard: Grafana `http://127.0.0.1:3001`, thư mục "BDS". Lệnh giả định Compose project `bds-production` chạy từ thư
mục gốc repository.

**Người chịu trách nhiệm:** chưa được chỉ định — chủ dự án phải gán người trực và kênh nhận cảnh báo
(`infra/observability/alertmanager.yml` hiện chưa có receiver). Đây là việc EXTERNAL trong `00_PLAN.md`.

Lệnh chẩn đoán chung:
```bash
docker compose -p bds-production ps
docker compose -p bds-production logs --since 15m --tail 200 backend
curl -fsS http://127.0.0.1:3000/backend-health; echo
```

## BdsBackendDown
- **Ý nghĩa:** Prometheus không đọc được `/actuator/prometheus` trong 2 phút: backend chết, đang khởi động lại hoặc mạng Compose lỗi.
- **Chẩn đoán:** `docker compose -p bds-production ps backend`; log backend (OOM, lỗi kết nối PostgreSQL, Flyway); `docker stats --no-stream`.
- **Giảm thiểu:** `docker compose -p bds-production up -d backend`; nếu do bản deploy mới, rollback theo `docs/ops/PRODUCTION_TOPOLOGY.md` mục 8; nếu thiếu bộ nhớ, dừng các stack không phải production trên cùng máy.
- **Rollback:** image `bds-production-backend:rollback-<STAMP>` gần nhất.

## BdsHigh5xxRate
- **Ý nghĩa:** hơn 1 % request API trả 5xx trong 5 phút (SLO).
- **Chẩn đoán:** dashboard "BDS — API" → panel "5xx theo route và status"; log backend theo `traceId` trong body lỗi; nếu 502/503/504 từ Nginx (`SERVICE_UNAVAILABLE`) thì backend không trả lời kịp.
- **Giảm thiểu:** tìm route lỗi; nếu do thay đổi mới, rollback; nếu do PostgreSQL/ES chậm, xem các cảnh báo tương ứng.
- **Rollback:** như BdsBackendDown.

## BdsHighLatencyP95
- **Ý nghĩa:** p95 độ trễ (không tính SSE và actuator) vượt 500 ms trong 10 phút.
- **Chẩn đoán:** "BDS — API" → "p95 theo route" và "10 route chậm nhất"; "BDS — PostgreSQL" → truy vấn chậm, khóa, transaction lâu; "BDS — JVM" → GC; Hikari pending.
- **Giảm thiểu:** xử lý truy vấn/route gây chậm; tăng tài nguyên nếu CPU/RAM máy chủ cạn; tạm tắt tác vụ nền nặng.
- **Rollback:** nếu bắt đầu sau một deploy, rollback ứng dụng.

## BdsDbPoolSaturated
Áp dụng cho BdsDbPoolSaturated và BdsDbConnectionWaits.
- **Ý nghĩa:** pool Hikari dùng trên 80 % hoặc có request phải chờ kết nối trong 5 phút.
- **Chẩn đoán:** "BDS — PostgreSQL" → "Kết nối pool", "Thời gian giữ kết nối"; trong PostgreSQL: `SELECT pid, state, now() - xact_start AS age, left(query, 80) FROM pg_stat_activity WHERE datname = current_database() ORDER BY age DESC NULLS LAST LIMIT 20;`
- **Giảm thiểu:** tìm truy vấn/transaction giữ kết nối lâu; chỉ tăng `maximum-pool-size` khi PostgreSQL còn dư `max_connections`.
- **Rollback:** không áp dụng.

## BdsJobQueueLagHigh
- **Ý nghĩa:** job đến hạn cũ nhất của một hàng đợi (`background_jobs`, ví dụ `search-index`, `email`) chờ quá 60 giây.
- **Chẩn đoán:** "BDS — Hàng đợi job" → độ trễ/tồn đọng theo hàng đợi, kết quả xử lý; log backend của worker; handler có đang lỗi lặp lại không.
- **Giảm thiểu:** sửa nguyên nhân (ES/SMTP lỗi, handler lỗi); worker xử lý tiếp tự động khi nguồn lỗi hết.
- **Rollback:** nếu do deploy mới làm handler lỗi, rollback ứng dụng.

## BdsJobDeadLetters
- **Ý nghĩa:** có job đã dùng hết số lần thử và bị đưa vào dead letter.
- **Chẩn đoán:** `SELECT queue, dedupe_key, attempts, last_error, dead_lettered_at FROM background_jobs WHERE dead_lettered_at IS NOT NULL ORDER BY dead_lettered_at DESC LIMIT 20;`
- **Giảm thiểu:** sửa nguyên nhân rồi đưa job chạy lại theo quy trình của luồng S0-BE (đặt lại `dead_lettered_at`, `attempts`, `run_at`); với email, kiểm tra người nhận có cần gửi lại không.
- **Rollback:** không áp dụng.

## BdsRateLimitRedisFallback
- **Ý nghĩa:** rate limiter không dùng được Redis trong 5 phút và đang đếm trong bảng cục bộ của từng instance.
- **Chẩn đoán:** cảnh báo BdsRedisDown; `docker compose -p bds-production logs --since 15m redis`; log backend `rate_limit_redis_unavailable`.
- **Giảm thiểu:** khôi phục Redis (`docker compose -p bds-production up -d redis`); limiter tự quay lại Redis sau tối đa 5 giây.
- **Rollback:** không áp dụng.

## BdsRateLimitFailClosed
- **Ý nghĩa:** Redis lỗi **và** bảng cục bộ của các endpoint xác thực (đăng nhập, đăng ký, quên mật khẩu, xác minh email, xác nhận mật khẩu KYC) đã đầy: người dùng mới nhận 429. Thường là tấn công phân tán trong lúc Redis hỏng.
- **Chẩn đoán:** "BDS — Rate limit" → "Từ chối theo policy", "Kích thước bảng cục bộ"; trạng thái Redis.
- **Giảm thiểu:** ưu tiên khôi phục Redis; bật chế độ chống tấn công của Cloudflare (Under Attack/WAF rate limiting) cho các đường `/api/v1/auth/*`.
- **Rollback:** không áp dụng. Tăng `app.security.rate-limit.local-strict-max-entries` chỉ là biện pháp tạm.

## BdsRateLimitRejectionsSpike
- **Ý nghĩa (info):** một policy từ chối hơn 1 request/giây trong 10 phút.
- **Chẩn đoán:** "BDS — Rate limit" → theo policy và theo chiều (`ip`, `account`, `email`); một IP gây ra hay nhiều IP cùng nhắm một email.
- **Giảm thiểu:** nếu là tấn công, chặn ở Cloudflare; nếu là người dùng thật bị chặn (ví dụ nhiều người sau một NAT của nhà mạng), cân nhắc nới giới hạn của policy đó qua `app.security.rate-limit.policies.<policy>.<chiều>.limit`.
- **Rollback:** không áp dụng.

## BdsPostgresDown
- **Ý nghĩa:** postgres-exporter không kết nối được PostgreSQL trong 2 phút.
- **Chẩn đoán:** `docker compose -p bds-production ps postgres`; `docker compose -p bds-production logs --since 15m postgres`; dung lượng đĩa (PostgreSQL dừng khi hết chỗ).
- **Giảm thiểu:** khởi động lại container; nếu dữ liệu hỏng, khôi phục vào database mới theo `docs/ops/PRODUCTION_TOPOLOGY.md` mục 6 — không ghi đè volume đang có.
- **Rollback:** khôi phục từ bản sao lưu/PITR.

## BdsPostgresConnectionsHigh
- **Ý nghĩa:** số kết nối vượt 80 % `max_connections` trong 10 phút.
- **Chẩn đoán:** "BDS — PostgreSQL" → "Kết nối theo trạng thái"; tìm kết nối `idle in transaction` và client lạ (exporter, công cụ quản trị).
- **Giảm thiểu:** đóng phiên treo (`SELECT pg_terminate_backend(pid)` sau khi xác định rõ); giảm pool nếu có nhiều instance.
- **Rollback:** không áp dụng.

## BdsPostgresLongTransaction
Áp dụng cho BdsPostgresLongTransaction và BdsPostgresDeadlocks.
- **Ý nghĩa:** có transaction mở quá 5 phút (giữ khóa, chặn VACUUM), hoặc PostgreSQL phát hiện deadlock.
- **Chẩn đoán:** `SELECT pid, usename, state, now() - xact_start AS age, wait_event_type, left(query, 120) FROM pg_stat_activity WHERE xact_start < now() - interval '5 minutes';` và `SELECT * FROM pg_locks WHERE NOT granted;`; deadlock có chi tiết trong log PostgreSQL (bật bởi `infra/postgres/conf.d/observability.conf`).
- **Giảm thiểu:** kết thúc transaction treo sau khi xác định chủ của nó; sửa thứ tự khóa trong code nếu deadlock lặp lại.
- **Rollback:** không áp dụng.

## BdsRedisDown
Áp dụng cho BdsRedisDown và BdsRedisEvictions.
- **Ý nghĩa:** Redis không phản hồi, hoặc Redis đang xóa key vì hết bộ nhớ (counter rate limit mất sớm).
- **Chẩn đoán:** `docker compose -p bds-production logs --since 15m redis`; "BDS — Redis" → bộ nhớ, eviction, số key.
- **Giảm thiểu:** khởi động lại Redis; tìm key lớn (`redis-cli --bigkeys`); tăng giới hạn bộ nhớ của container nếu cần. Ứng dụng vẫn chạy khi Redis lỗi.
- **Rollback:** không áp dụng.

## BdsElasticsearchDown
Áp dụng cho BdsElasticsearchDown và BdsElasticsearchRed.
- **Ý nghĩa:** không đọc được trạng thái cluster Elasticsearch, hoặc cluster ở trạng thái red. Tìm kiếm tự chuyển sang PostgreSQL.
- **Chẩn đoán:** `curl -s http://elasticsearch:9200/_cluster/health` từ một container trong mạng; log elasticsearch; heap ES trên dashboard "BDS — Tìm kiếm".
- **Giảm thiểu:** khởi động lại ES; nếu chỉ mục hỏng, dựng lại từ PostgreSQL (quy trình rebuild của luồng S2).
- **Rollback:** chỉ mục là dữ liệu dựng lại được, không cần khôi phục từ sao lưu.

## BdsDiskSpaceLow
Áp dụng cho BdsDiskSpaceLow và BdsDiskSpaceCritical.
- **Ý nghĩa:** một filesystem còn dưới 20 % (warning) hoặc 10 % (critical).
- **Chẩn đoán:** `docker system df`; `docker compose -p bds-production exec postgres du -sh /var/lib/postgresql/data /var/lib/postgresql/wal-archive 2>/dev/null`; dung lượng `BACKUP_DIR`.
- **Giảm thiểu:** xóa image/build cache không dùng (`docker image prune`, `docker builder prune`), giảm thời gian giữ WAL/backup, chuyển `BACKUP_DIR` sang đĩa khác. Không xóa file trong thư mục dữ liệu PostgreSQL.
- **Rollback:** không áp dụng.

## BdsBackupStale
Áp dụng cho BdsDatabaseBackupStale, BdsMediaBackupStale, BdsBackupFailed và BdsBackupNeverReported.
- **Ý nghĩa:** không có bản sao lưu DB thành công trong 2 giờ, object trong 2 ngày, lần chạy cuối thất bại, hoặc chưa từng có metric sao lưu.
- **Chẩn đoán:** `docker compose -p bds-production -f docker-compose.yml -f infra/compose.backup.yaml --profile ops logs --since 3h backup` (tìm `backup_failed`); quyền ghi và dung lượng của `BACKUP_DIR`; `BACKUP_METRICS_DIR` của node-exporter có trỏ tới `$BACKUP_DIR/metrics` không.
- **Giảm thiểu:** sửa nguyên nhân rồi chạy tay: `docker compose -p bds-production -f docker-compose.yml -f infra/compose.backup.yaml --profile ops run --rm backup db`.
- **Rollback:** không áp dụng. Sau sự cố kéo dài, chạy `scripts/restore-drill.sh` để xác nhận bản sao lưu mới khôi phục được.
