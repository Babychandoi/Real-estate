# Đánh giá triển khai, hiệu năng và khả năng mở rộng

Snapshot: [`b8d66bf`](https://github.com/Babychandoi/Real-estate/commit/b8d66bf251c117b2775a6b586a55ea8262ccaaff) — 2026-09-19

## Kết luận

Docker Compose là nền tảng demo/tích hợp khá tốt: Postgres, Redis, Mailpit, MinIO, ClamAV, Elasticsearch, backend và frontend được CI dựng thành công. Repo cũng có HPA/PDB backend, CloudNativePG template, ExternalSecret và backup skeleton.

Nhưng bộ manifest production **chưa tạo thành một release deployable nhất quán**. Tìm kiếm quét toàn bộ bảng và PUT từng document mỗi phút; SSE lưu connection trong RAM từng pod; media đi qua backend và đọc cả file vào heap; nhiều API dùng offset/list không total. Không có benchmark hoặc capacity model chứng minh quy mô lớn. Vì vậy, tuyên bố “hàng triệu người dùng đồng thời” hiện không có cơ sở.

## Trạng thái CI/CD

Tại [GitHub Actions run 57](https://github.com/Babychandoi/Real-estate/actions/runs/35048152964):

| Bước | Kết quả | Ghi chú |
|---|---|---|
| Backend Maven verify | Đạt | 21 tests, 0 failure/error |
| Frontend install/audit/lint/build | Đạt | 0 high audit finding; build thành công |
| Compose integration stack | Đạt | Containers lên và health đạt |
| Playwright | Không đạt | 35 tests: 8 pass, 27 fail |
| Trivy filesystem | Bị skip | Nằm sau E2E trong cùng job |

CI đỏ chủ yếu do locator/snapshot chưa cập nhật, nhưng đây vẫn là release gate thất bại. Một lỗi responsive 320 px là thật. Security scan không được phép phụ thuộc E2E.

Bằng chứng: [`.github/workflows/ci.yml` dòng 11–50](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/.github/workflows/ci.yml#L11-L50).

### Hành động CI/CD

- Tách `backend`, `frontend`, `integration-e2e`, `security`, `image-build` thành jobs độc lập với artifact/cache rõ ràng.
- `security` chạy ngay cả khi visual fail; thêm secret scan, SBOM, container image scan và license policy.
- Bảo vệ `main`, required checks và review; build image một lần, promote cùng digest qua môi trường.
- Ký image/provenance; migration compatibility check; canary/rollback và post-deploy smoke.

## Kubernetes/production manifests

### Điểm tốt

- Backend có 2 replicas, readiness/liveness, resource requests/limits, PDB và HPA 2–10.
- CloudNativePG template có 3 instances và object-store backup/retention.
- ExternalSecret và TLS ingress skeleton có sẵn.
- Local-edge frontend có 2 replicas và topology spread.

### Khoảng trống chặn production

1. `app.yaml` tạo backend/service `bds-backend` không namespace; image vẫn là placeholder.
2. `production-platform.yaml` Ingress trỏ `bds-frontend`, nhưng không có production frontend Deployment/Service trong cùng bộ manifest.
3. Frontend Deployment duy nhất nằm trong `local-edge.yaml`, namespace `bds`, dùng image `:latest` và `imagePullPolicy: Never` — chỉ phù hợp local cluster.
4. Local-edge tạo service `backend` không selector, trong khi app tạo `bds-backend`; namespace/name không khớp.
5. Redis/MinIO HA được ghi là dependency do platform team cài, chưa có wiring/endpoint/readiness/credentials hoàn chỉnh trong release.
6. Thiếu NetworkPolicy, pod/container `securityContext`, seccomp, read-only root filesystem, runAsNonRoot và service account tối thiểu.
7. Backend thiếu anti-affinity/topology spread; frontend production thiếu HPA/PDB.
8. Chưa có migration Job, init policy, disruption/upgrade runbook và tested restore path.

Bằng chứng:

- [`infra/k8s/app.yaml`](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/infra/k8s/app.yaml)
- [`infra/k8s/production-platform.yaml`](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/infra/k8s/production-platform.yaml)
- [`infra/k8s/local-edge.yaml`](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/infra/k8s/local-edge.yaml)

Kết luận: không thể `kubectl apply` một bộ production thống nhất rồi kỳ vọng hệ thống hoạt động end-to-end.

## Database và connection budget

Backend Hikari được cấu hình khoảng 15 connection/pod; HPA tối đa 10 pod có thể yêu cầu khoảng 150 connection, chưa tính job/management. Repo có PgBouncer trong production Compose overlay (`max_client_conn` 500, pool 40), nhưng K8s app không chứng minh traffic đi qua PgBouncer hoặc connection budget phù hợp CNPG.

Yêu cầu:

- Mô hình hóa `pods × maxPoolSize + jobs + admin < DB max minus reserve`.
- K8s service bắt buộc trỏ qua pooler; transaction pooling compatibility test.
- Slow query log, `pg_stat_statements`, query budgets và index regression tests.
- Read replicas chỉ khi consistency model rõ; không dùng replica để che query kém.

## Tìm kiếm và Elasticsearch

### Vấn đề hiện tại

Mỗi 60 giây, job SELECT **toàn bộ** listing ACTIVE rồi PUT từng document tuần tự qua HTTP. Không dùng delta/outbox, bulk API hoặc delete tombstone. Khi tin pause/lock, document cũ có thể còn trong index. Search hydrate lại từ DB và lọc ACTIVE nên giảm nguy cơ lộ, nhưng trang có thể thiếu item và pagination không ổn định. Query dùng deep offset `from = page × size`; khi Elasticsearch lỗi, hệ thống fallback relational LIKE.

Bằng chứng: [`ElasticsearchListingIndex.java` dòng 17–28](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/search/ElasticsearchListingIndex.java#L17-L28).

Tác động ở hàng triệu tin:

- Full-table scan và hàng triệu HTTP PUT mỗi phút là không khả thi.
- Nhiều backend replicas sẽ cùng chạy scheduler nếu không có leader lock, nhân tải lên.
- Deep offset tốn bộ nhớ/CPU; fallback SQL có thể đè database đúng lúc ES sự cố.

Thiết kế đề xuất:

1. Transactional outbox ghi `listing.changed/deleted` cùng transaction nghiệp vụ.
2. Consumer partitioned, idempotent, dùng `_bulk`, retry/DLQ và lag metric.
3. Version/external sequence để tránh event cũ ghi đè mới; delete/tombstone rõ ràng.
4. Cursor/search-after với deterministic tiebreaker; result DTO có cursor/total ước lượng.
5. Circuit breaker: khi ES lỗi, giới hạn fallback/cached popular search thay vì full SQL search không giới hạn.

## Realtime notifications/SSE

`RealtimeNotificationService` giữ `SseEmitter` trong `ConcurrentHashMap` của từng JVM. Notification được lưu DB, nhưng event trực tiếp chỉ tới connection trên đúng pod xử lý transaction. Với 2–10 replicas và load balancer, client có thể ở pod A trong khi update chạy ở pod B.

Bằng chứng: [`RealtimeNotificationService.java` dòng 16–59](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/notification/RealtimeNotificationService.java#L16-L59).

Sửa: Redis Streams/PubSub, Kafka/NATS hoặc gateway realtime; event id/cursor và replay; client reconnect luôn fetch unread/current state; connection limits/backpressure/heartbeat metrics. Sticky session chỉ là giảm triệu chứng, không bảo đảm delivery.

## Media path

Upload tối đa 10 MB được đọc thành `byte[]`, quét rồi lại stream lên MinIO. Nhiều upload đồng thời tạo heap pressure. Public image được backend proxy stream; chưa có CDN, presigned/direct delivery, resize/WebP variants hoặc range/caching tại object edge.

Bằng chứng: [`MediaStorageService.java` dòng 63–88](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/media/MediaStorageService.java#L63-L88).

Sửa:

- streaming upload/quarantine scan hoặc direct multipart upload với finalize token;
- asynchronous image processing + size variants;
- CDN/object origin cho public media, signed short URL cho private KYC;
- global/user concurrency and byte quotas; memory/GC load test.

## API và pagination

- Public search trả list trang, chưa có total/cursor/load-more ở UI.
- “My listings” tải toàn bộ listing cùng revisions.
- Nhiều admin list dùng offset; deep pages giảm hiệu năng.
- Sitemap loop tối đa 100 × 100 = 10.000 listing và build đồng bộ mỗi request.
- Slug dùng check-exists rồi insert; hai request cùng title có race nếu không retry unique conflict.

Sitemap bằng chứng: [`ListingSeoController.java` dòng 27–43](https://github.com/Babychandoi/Real-estate/blob/b8d66bf251c117b2775a6b586a55ea8262ccaaff/backend/src/main/java/com/company/bds/listing/api/ListingSeoController.java#L27-L43).

Sửa: keyset/cursor pagination; projections thay aggregate đầy đủ; sitemap index + pre-generated shards/cache; unique constraint + retry deterministic cho slug.

## Frontend delivery

Build thành công và route được lazy-load. Kích thước quan sát tại snapshot:

- entry JS khoảng 306 KB minified / 95 KB gzip;
- MapLibre chunk khoảng 1.037 KB minified / 279 KB gzip;
- MapLibre worker khoảng 507 KB;
- CSS app khoảng 98 KB, MapLibre CSS khoảng 83 KB.

Map được route-split nên không chặn mọi trang, nhưng search/map/create có chi phí lớn. Cần đo LCP/INP trên thiết bị thấp, chỉ tải map khi tab/viewport yêu cầu, preconnect hợp lý và giới hạn marker/cluster server-side.

## Load testing và “million concurrent”

K6 hiện chỉ ở mức smoke/load/soak nhỏ, chủ yếu search (xấp xỉ 10–25 virtual users). Tài liệu SLO nhắc mục tiêu ban đầu khoảng 100 read RPS/10 write RPS nhưng không phải benchmark kết quả. Không có bằng chứng:

- peak concurrency, RPS và traffic mix;
- p50/p95/p99 end-to-end theo hành trình;
- saturation point của DB/Redis/ES/MinIO/SSE;
- queue lag, error budget, autoscale time;
- failover, AZ loss, backup restore và RPO/RTO thực nghiệm.

Để đánh giá scale lớn, cần một capacity plan định lượng. “Một triệu tài khoản” khác hoàn toàn “một triệu concurrent”; yêu cầu thứ hai có thể cần hàng chục/hàng trăm nghìn RPS và số connection realtime rất lớn, phụ thuộc hành vi.

### Kế hoạch kiểm thử đề xuất

| Giai đoạn | Mục tiêu |
|---|---|
| Baseline | 50–100 RPS, xác lập p95 và resource/query profile |
| Component | Search index, listing read/write, lead, media, auth, SSE riêng |
| Journey mix | 70% browse, 15% search, 8% detail, 3% lead, 2% auth, 2% write — điều chỉnh theo telemetry |
| Step/ramp | Tăng đến saturation; xác định giới hạn an toàn và autoscale lag |
| Soak | 8–24 giờ để phát hiện leak, pool exhaustion, index lag |
| Resilience | Kill pod/node, Redis/ES degradation, DB failover, object-store latency |
| DR | Restore backup vào môi trường sạch và đo RPO/RTO |

## Lộ trình kiến trúc

### Trước beta

- Làm xanh CI và required checks.
- Một K8s overlay production deployable; image digests; security contexts/network policy.
- Fix ES scheduler duplicate/full scan; ít nhất leader lock + incremental timestamp, ưu tiên outbox.
- Client SSE reconnect fetch state; alert/metrics cơ bản.

### Trước production

- Outbox + bulk search indexer; distributed realtime.
- DB pooler/connection budget, migration job và restore drill.
- CDN/media variants, cursor APIs, sitemap shards.
- Observability theo RED/USE, trace id và SLO/error budget.

### Trước scale rất lớn

- Benchmark production-like với dữ liệu cardinality thực.
- Multi-AZ cho stateful dependencies, tested failover.
- Partitioning/caching/read model theo kết quả đo, không tối ưu theo phỏng đoán.
- Capacity forecast và cost model; game day định kỳ.
