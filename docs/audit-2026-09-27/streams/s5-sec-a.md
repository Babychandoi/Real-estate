# S5-SEC phase A — báo cáo luồng

Nhánh: `audit/s5-sec-a`. Cơ sở: `e77db38`. HEAD: `60da984`. Không có migration Flyway mới (V065 trong dải được cấp
không dùng tới — mọi thay đổi của pha A là mã ứng dụng và hạ tầng, không đổi schema).

## Danh sách commit

```
5af2dfe feat(security): rate limiter v2 keyed by trusted client IP, account and e-mail
1620a76 feat(security): never let shared caches store private API responses
7ca5462 feat(observability): expose open SSE streams as bds.sse.connections
a874b68 feat(edge): security headers on every response, real client IP from Cloudflare
6a07be8 docs(adr): record the bearer-token session model and fix docs that claimed cookies
f3db995 feat(ops): encrypted backups, PITR overlay and a scripted restore drill
caefba0 feat(observability): Prometheus, Alertmanager, Grafana and exporters overlay
6629e47 feat(ops): bounded WAL archive, compressed WAL shipping and base backups for PITR
02e011e docs(ops): backup classification, history remediation plan, topology, branch protection
9a7250e fix(security): close rate-limit bypasses via padded bodies, encoded paths and HEAD
6b5bd8e fix(security): address rate limiter review findings
fff81f1 fix(security): WAL gap detection, drill script set pinning, real-IP hardening at Caddy
60da984 docs(ops): re-run the synthetic restore drill for final verification
```
83 files changed, 11,580 insertions(+), 127 deletions(-) versus `e77db38`.

**Ghi chú quy trình:** `fff81f1` (WAL gap detection, set-id pinning trong `restore-drill.sh`, Caddy real-IP hardening)
được orchestrator commit thay tôi sau khi phiên làm việc trước đó bị cắt giữa chừng vì giới hạn tốc độ; nội dung đã
hoàn chỉnh và khớp với những gì tôi đang xây dựng, tôi chỉ xác minh lại (mục "Xác minh cuối" bên dưới), không chỉnh
sửa thêm.

## Về "review findings" (9a7250e, 6b5bd8e)

**Không có một lượt Review 2 độc lập nào do orchestrator điều phối như mô tả trong `00_PLAN.md`.** Cả hai commit đều
do chính tôi (agent của luồng này) tự tạo ra, không phải một agent reviewer riêng biệt do orchestrator dispatch:

- `9a7250e` ("Found in self-review…"): tôi tự đọc lại mã vừa viết và tìm ra 3 lỗ hổng (body đệm để né quota email,
  đường dẫn mã hóa `%6C` né policy, HEAD không tính vào quota GET). Đây là tự soát xét thủ công, ghi rõ trong commit
  message là "self-review".
- `6b5bd8e` ("address rate limiter review findings"): tôi gọi skill `code-review` (Skill tool, effort `high`) như một
  subagent tách nhánh (forked execution) để rà soát diff của chính luồng này. Đây là một công cụ tự động tôi chủ động
  gọi trong phiên làm việc của mình, chạy trên cùng agent/model, **không phải** một agent reviewer độc lập được
  orchestrator dispatch sau khi gộp nhánh (như bước "Review 2" trong `00_PLAN.md` mô tả: "agent reviewer độc lập kiểm
  tra từng yêu cầu của luồng"). Công cụ tìm ra 9 vấn đề (IP giả mạo qua Caddy, DB query chạy trước rate limit, thứ tự
  đếm rule, Retry-After sai, một số vấn đề trong `infra/backup/backup.sh` và `restore-drill.sh` mà `fff81f1` xử lý
  tiếp). Tôi đã sửa và viết test cho toàn bộ, nhưng việc này không thay thế cho một Review 2 thật của orchestrator/agent
  khác đọc mã với góc nhìn hoàn toàn tách biệt.

**Kết luận:** luồng này chưa qua Review 2 độc lập. Orchestrator/agent reviewer nên coi toàn bộ commit của luồng
`audit/s5-sec-a` là **chưa được review bởi bên thứ ba**, dù đã qua hai vòng tự soát xét (một thủ công, một bằng công
cụ tự động) có bằng chứng test đi kèm.

## Xác minh cuối (chạy lại tại HEAD `60da984` sau thông báo của orchestrator)

| Việc | Lệnh | Kết quả |
|---|---|---|
| Backend test suite đầy đủ | `sh mvnw -B -ntp verify` (JDK 17, hạ tầng test dùng chung, DB Redis 7) | **69/69 pass**, `BUILD SUCCESS` |
| Alert rules cú pháp | `promtool check rules infra/observability/rules/bds-alerts.yml` | `SUCCESS: 26 rules found` |
| Alert rules unit test | `promtool test rules infra/observability/tests/bds-alerts.test.yml` | `SUCCESS` (9 nhóm test, gồm 2 nhóm mới cho `BdsWalShippingStale`/`BdsBaseBackupStale`) |
| Header bảo mật, HTTP thường | `scripts/verify-headers.sh http://127.0.0.1:18136` (container `bds-s5-frontend` cô lập, backend stream qua forwarder `bds-s5-backend-forwarder`) | **105/105 checks, 0 failed, 0 warnings** |
| Header bảo mật, mô phỏng HTTPS + phiên đăng nhập thật | `VERIFY_BEARER_TOKEN=... scripts/verify-headers.sh http://127.0.0.1:18136 --simulate-https` | **106/106 checks, 0 failed, 0 warnings** (bao gồm SSE stream 200 thật) |
| Diễn tập khôi phục | `scripts/restore-drill.sh --env synthetic` chạy lại từ đầu: key age mới, bucket MinIO tổng hợp mới (120 object), database tạm `s5_verify` nạp lại từ Flyway + dữ liệu tổng hợp mới, hai bản backup mới, khôi phục vào project cô lập `bds-drill` | **PASS**; báo cáo `docs/ops/drills/2026-09-28-synthetic.md` (thay thế báo cáo ngày 27 — cùng kết quả, xác nhận các bản sửa lỗi tự soát xét không làm hỏng quy trình) |

Toàn bộ container/network/volume dùng để xác minh (`bds-s5-frontend`, `bds-s5-backend-forwarder`, mạng
`bds-s5-verify-net`, project `bds-drill`, database tạm `s5_verify`) đã bị xóa sau khi xác minh xong; `git status`
sạch.

## Yêu cầu → bằng chứng

| ID | Yêu cầu | Trạng thái | Bằng chứng |
|---|---|---|---|
| F01.9 | Bắt buộc CI xanh + 1 review trên `main` | EXTERNAL (cần quyền admin repo) | `docs/ops/BRANCH_PROTECTION.md` — lệnh `gh api` đầy đủ cho ruleset và classic protection; chưa chạy |
| F10.2 | Không cache KYC/lead/admin ở shared cache | DONE | `SensitiveResponseCacheFilter` (auth, KYC, media riêng tư, leads, billing trừ `/plans`, admin, moderation, verification, reports, transactions, broker, notifications, analytics, CMS/catalog admin, draft) ép `no-store` bất kể controller làm gì; test: `SensitiveResponseCacheTests` (3, tích hợp thật — login/me/logout, 9 route riêng tư theo vai trò, 401/403), `SensitiveResponseCacheFilterTests` (4, đơn vị — prefix match, path đã decode `%6B`, controller cố ý set `public`, media công khai vẫn cache 1 năm) |
| F11.1 | Header bảo mật có ở mọi location | DONE | `frontend/nginx/security-headers.conf` include ở server block và mọi location (assets, index.html, SPA, SSE, healthz, API, sitemap, backend-health, trang lỗi 503 mới); xác minh bằng `scripts/verify-headers.sh` — 105–106/106 checks trên container cô lập (xem bảng Xác minh cuối) |
| F11.2 | Kiểm tra response cuối qua CDN/proxy | PARTIAL (công cụ có, domain thật là EXTERNAL) | `scripts/verify-headers.sh` chạy được với `--simulate-https` và test cả CSP/HSTS/no-store; **chưa chạy với domain thật `https://nhadatchuan.online`** — theo brief, việc đó thuộc orchestrator |
| F13.1 | Chỉ tin proxy khai báo; IP thật qua chuỗi proxy | DONE | `ClientIpResolver`/`IpAddresses` (chỉ literal IP, không DNS lookup; `app.security.trusted-proxies` mặc định loopback + dải Docker); Nginx `realip` từ `CF-Connecting-IP` chỉ ở dải tin cậy; test: `ClientIpResolverTests` 10 case gồm peer không tin cậy, XFF ưu tiên hơn X-Real-IP khi cả hai có mặt (phát hiện qua self-review — Caddy pass-through), chuỗi toàn nội bộ, IPv4-mapped IPv6, IPv6 /64 quota; kiểm chứng thủ công qua Nginx container cô lập (script `ip-chain-check.sh`): 2 client CF khác nhau có quota riêng, X-Real-IP giả mạo từ peer không tin cậy bị bỏ qua |
| F13.2 | Limit theo IP + account + endpoint; counter atomic; không hashCode | DONE | `RateLimitPolicies` (11 policy: auth-login/admin-login/register/forgot/resend/reset/verify-email, kyc-document-access, public-leads/reports, geocoding, analytics-events, media-upload, api-default); khóa = SHA-256 128-bit (không `hashCode`); Redis: một script Lua INCR+PEXPIRE+PTTL nguyên tử, dừng ở rule đầu tiên vượt hạn mức; test: `RateLimiterTests` (8), `RequestRateLimitFilterTests` (10, tích hợp thật với Redis DB 7) |
| F13.3 | Fallback bị chặn bộ nhớ; policy theo route; Retry-After; dashboard 429 | DONE | `LocalRateLimitStore` (16 phân đoạn LRU có trần cứng, sweep định kỳ), `RateLimitFailureMode` (FAIL_CLOSED cho endpoint xác thực, EVICT cho còn lại); `RequestRateLimitRedisOutageTests` (3: Redis mất vẫn giới hạn, 100k client không vượt trần bộ nhớ, endpoint xác thực fail-closed khi bảng đầy); dashboard "BDS — Rate limit" (`bds-ratelimit.json`) hiển thị `bds_ratelimit_rejected/fallback/redis_available/local_entries` |
| F20.1 | ADR mô hình session nhất quán | DONE | `docs/adr/0001-session-model.md`; sửa `PROJECT_CODE_RULES_BDS.md` và `Ke_hoach_du_an_website_BDS_Waterfall.md` (đã mô tả sai Spring Session JDBC + cookie HttpOnly) khớp thực tế (bearer token opaque, hash SHA-256 trong `auth_sessions`) |
| F21.1 | Phân loại backup trong Git LFS | DONE | `docs/ops/BACKUP_CLASSIFICATION.md` — chỉ dựa vào README và schema, không mở archive; phân loại Mật – dữ liệu cá nhân |
| F21.2 | Backup mã hóa, ACL, retention, restore drill | DONE | `infra/backup/{backup.sh,restore.sh,lib.sh,Dockerfile}`, `infra/compose.backup.yaml`, `infra/compose.pitr*.yaml`, `infra/drill/compose.drill.yaml`, `scripts/restore-drill.sh`; diễn tập cuối `docs/ops/drills/2026-09-28-synthetic.md` PASS (dữ liệu tổng hợp) |
| F21.3 | Kế hoạch xử lý lịch sử Git (không tự xóa) | DONE (kế hoạch, EXTERNAL để thực thi) | `docs/ops/GIT_HISTORY_REMEDIATION_PLAN.md` — 4 phương án, quy trình 8 bước, checklist xoay bí mật; chưa thực thi bước nào |
| F21.4 | Tách production khỏi máy dev | DONE (tài liệu, EXTERNAL để thực thi) | `docs/ops/PRODUCTION_TOPOLOGY.md` §1–4 |
| D-14 | Observability tối thiểu (infra) | DONE | `infra/compose.observability.yaml` (Prometheus, Alertmanager, Grafana, exporter Postgres/Redis/ES/host); 8 dashboard, 26 alert rule + test; validated bằng promtool và chạy sống scrape backend thật |
| D-15 (tài liệu) | Topology production nhỏ | DONE | `docs/ops/PRODUCTION_TOPOLOGY.md` §3 (topology, HA matrix), §8 (rollback ứng dụng/migration), §9 (ghi chú deploy pha A) |

## Sai lệch so với hợp đồng (`02_CONTRACTS.md`)

- Không dùng migration nào trong dải V065 được cấp — mọi thay đổi là mã ứng dụng/hạ tầng, không đổi schema. Nếu pha B
  cần V065 cho mục đích khác, dải vẫn còn nguyên.
- `RequestRateLimitFilter` được tách thành 2 filter (`RequestRateLimitFilter` cho IP/email trước bearer lookup,
  `AccountRateLimitFilter` cho account sau bearer lookup) thay vì một filter duy nhất như thiết kế ban đầu — lý do: để
  tránh flood token ngẫu nhiên tốn một truy vấn DB mỗi request (phát hiện qua self-review). Tên lớp
  `RequestRateLimitFilter` giữ nguyên để không phá vỡ tên bean mà `SecurityConfig`/test tham chiếu.

## Khoảng trống còn lại (thành thật)

1. **Không có Review 2 độc lập** (xem mục trên) — chỉ có tự soát xét (thủ công + công cụ `code-review` tự gọi).
2. F11.2: chưa chạy `scripts/verify-headers.sh` với domain thật qua Cloudflare — cần orchestrator hoặc chủ dự án chạy
   sau khi deploy.
3. F01.9, F21.3, F21.4 thực thi: cần quyền admin GitHub / quyết định chủ dự án / hạ tầng mới — chỉ có tài liệu và công
   cụ.
4. Diễn tập khôi phục dùng dữ liệu TỔNG HỢP trên hạ tầng test dùng chung; chưa từng chạy với bản sao lưu production
   thật (đúng theo brief — production off-limits).
5. `infra/observability/alertmanager.yml` không có receiver thật (không có địa chỉ/kênh của người trực trong repo) —
   cảnh báo hiển thị trong UI Alertmanager/Grafana nhưng không báo cho ai; ghi rõ trong
   `docs/operations/ALERT_RUNBOOK.md` là việc EXTERNAL.
6. Test `RateLimiterTests`/`LocalRateLimitStoreTests` dùng cổng Redis đóng (127.0.0.1:1) để mô phỏng mất kết nối —
   nhanh và ổn định trong CI, nhưng không kiểm tra hành vi khi Redis timeout chậm (kết nối treo) thay vì bị từ chối
   ngay; rủi ro thấp vì Lettuce có `commandTimeout` riêng.

## Ghi chú triển khai / biến môi trường mới

| Thay đổi | Chi tiết |
|---|---|
| `docker-compose.yml`: `frontend` publish `127.0.0.1:3000:3000` thay vì `3000:3000` | **Phá vỡ khả năng truy cập LAN trực tiếp** — máy khác trong mạng nội bộ không còn mở được `http://<ip>:3000`; truy cập công khai qua Cloudflare Tunnel không đổi. Cần kiểm thử từ thiết bị khác qua domain thật trước khi coi là xong. |
| `APP_SECURITY_TRUSTED_PROXIES` (mới, mặc định `127.0.0.0/8,::1/128,172.16.0.0/12,192.168.0.0/16`) | Backend chỉ tin `X-Real-IP`/`X-Forwarded-For` từ các dải này. Nếu mạng Compose production dùng dải IP khác, phải đặt biến này trước khi deploy. |
| `APP_RATE_LIMIT_ENABLED` (mặc định `true`) | Công tắc tắt khẩn cấp toàn bộ rate limiter. |
| `RATE_LIMIT_KEY_PEPPER` (mới, khuyến nghị đặt ở production) | Trộn vào hash khóa Redis của rate limiter; để trống vẫn hoạt động (khóa vẫn SHA-256 128-bit, không phải hashCode). |
| `RATE_LIMIT_LIMIT_MULTIPLIER` → `APP_SECURITY_RATELIMIT_LIMITMULTIPLIER` (mặc định `1`; stack demo/E2E đặt `20`) | Nhân mọi giới hạn; **phải giữ `1` ở production** — giá trị `0` làm backend từ chối khởi động (đã kiểm chứng). |
| `BACKUP_DIR`, `BACKUP_AGE_RECIPIENTS`, `BACKUP_ENV` (mới, bắt buộc khi bật `infra/compose.backup.yaml`) | `BACKUP_DIR` phải là thư mục ngoài repository; `BACKUP_AGE_RECIPIENTS` chỉ chứa public key age. |
| `GRAFANA_ADMIN_PASSWORD` (mới, bắt buộc khi bật `infra/compose.observability.yaml`) | Mật khẩu admin Grafana; UI chỉ nghe `127.0.0.1`. |
| `.gitignore`: thêm `backups/`, `*.dump.age` | File cũ trong `backups/20260926-005833/` vẫn được track (không tự xóa lịch sử); chỉ chặn backup mới. |
| Không có migration Flyway mới | — |
| Overlay mới không tự bật: `infra/compose.backup.yaml`, `infra/compose.pitr.yaml`, `infra/compose.pitr-backup.yaml`, `infra/compose.observability.yaml` | Bật theo `docs/ops/PRODUCTION_TOPOLOGY.md` §6–7 khi chủ hệ thống quyết định; PITR/observability khởi động lại PostgreSQL một lần khi bật lần đầu. |

## Việc luồng khác cần biết

- **S9-QUALITY**: `RateLimitResponses`, `SensitiveResponseCacheFilter` dùng chung khuôn Problem Details với
  `GlobalExceptionHandler` — khi chuẩn hóa toàn cục Problem Details, kiểm tra hai chỗ này không bị lệch format.
- **S6-ENGAGE**: `bds.sse.connections` gauge đã có sẵn trên `RealtimeNotificationService`; khi chuyển fan-out sang
  Redis, giữ nguyên tên metric.
- **S2-SEARCH**: dashboard "BDS — Tìm kiếm" tham chiếu `bds_search_requests_total`, `bds_search_breaker_state`
  (chưa tồn tại — sẽ hiện "Chưa có dữ liệu" cho tới khi S2 thêm các metric này theo đúng tên trong `02_CONTRACTS.md` §8).
- **S0-BE**: dashboard "BDS — Hàng đợi job" tham chiếu `bds_jobs_lag_seconds`, `bds_jobs_pending`, `bds_jobs_dead`,
  `bds_jobs_processed_total` theo đúng tên trong `02_CONTRACTS.md` §3.1 — chưa tồn tại cho tới khi S0-BE hoàn tất job
  queue; alert `BdsJobQueueLagHigh`/`BdsJobDeadLetters` sẽ không bao giờ fire cho tới lúc đó (không phải false negative,
  chỉ là chưa có nguồn số liệu).
- **S5-SEC phase B**: ADR 0001 đã chốt giữ bearer token; phase B cần thêm TTL ngắn hơn cho staff, MFA, session
  list/revoke theo đúng phần "Quyết định" của ADR.
