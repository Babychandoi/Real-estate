# W6-PERF — query plans at 100k/1M, soak/burst/cold/fault load evidence, publication lag

Branch `audit/w6-perf` (from `main` @ `13e41a2`), draft PR [#23](https://github.com/Babychandoi/Real-estate/pull/23).
Flyway: **V100–V101** (reserved range V100–V102; V102 unused). Every number below was measured on disposable GitHub-hosted runners
(workflow `.github/workflows/performance.yml`), never on the production machine. Rows F09.3, D-05, F09.2, D-13, R-5,
F05.5.

## 1. Row status

| Row | Status | Evidence (run, environment) | Left |
|---|---|---|---|
| F09.3 | **DONE** | EXPLAIN (ANALYZE, BUFFERS, SETTINGS) of 40 application query shapes at **100k and 1M** public listings, **warm and cold**, before/after V100: runs [37067923955](https://github.com/Babychandoi/Real-estate/actions/runs/37067923955) and [37069904979](https://github.com/Babychandoi/Real-estate/actions/runs/37069904979), artifacts `query-plans-<run>-1` (§3). | Repeat on an anonymised production snapshot before tuning further (synthetic skew, §3.1). |
| D-05 | **DONE** | The EXPLAINs show the intended indexes on their shapes (owner/status `idx_listings_owner_status_created`, `idx_listings_owner_created`; feed `idx_lpr_newest`; price/area `idx_lpr_price`/`idx_lpr_area`; GiST `idx_lpr_location`; GIN `idx_lpr_search_tsv`; seller `idx_lpr_owner_newest`). One missing index found by EXPLAIN — the public detail slug lookup was a sequential scan — added in **V100** with before/after: 1M warm 223 ms → 0.099 ms, cold 3858 ms → 1.0 ms (run 37069904979: 201 ms → 0.102 ms, 4964 ms → 1.1 ms) (§3.2). No other index added: the remaining slow shapes are not index problems (§3.3). | Map clusters / similar listings / capped counts need query or design changes (§3.3, §6). |
| F09.2 | **DONE** | GiST `idx_lpr_location` used for bbox (`map.count.zoom15`, `map.points.zoom15` 2.9 ms at 1M, `map.clusters.zoom13` BitmapAnd), GIN `idx_lpr_search_tsv` for selective keywords (`search.keyword.rare.newest`: 4.9 ms at 1M). Keyword search is FTS (`plainto_tsquery('simple')`); trigram is used only by duplicate detection, not by public search. Common keywords correctly walk `idx_lpr_newest` (0.75 ms at 1M). | — |
| D-13 | **PARTIAL** | Constant arrival 100 reads/s + 10 writes/s on **100k** listings: 10-min soak green four times (runs 37063450468, 37065843904, 37067923955, 37069904979), cold cache, burst, ES and Redis outage/recovery, index rebuild (14.7–16.9 s for 100,060 documents) all measured (§4). | **Burst 3× now passes** after O2 (§9); latest strict repeat has zero drops but fails cold latency; **1M** load was attempted and fails from shared-memory exhaustion (§10). Restore/rollback drill remains outside this stream. |
| R-5 | **PARTIAL** | Measured per phase: p50/p95/p99 per request type, API error rate (0 % in every phase), index lag (bds.search.index.lag deltas), job backlog (5-s samples), outbox backlog (0 — outbox disabled in this stack), SQL statements per request type (pg_stat_statements), top statements by database time (§4, §5). **Controlled fallback**: the degraded phase now passes (p95 137 / 119 / 119 ms in three runs) after a fix in `ListingSearchService` (§4.4). | O2 burst and steady ES stop/hang fallback pass (§9). Latest cold restart has zero drops but fails latency; 1M mixed-load attempts fail before a complete soak/burst (§10). |
| F05.5 | **DONE** | Approval → visible in Elasticsearch search under the 100/10 soak: **p95 1.68–1.85 s** (301 approvals per run, 100 % visible), four runs (37063450468 p95 1676 ms, 37065843904 1678 ms, 37067923955 1846 ms, 37069904979 1685 ms); server-side `bds.search.index.lag` 98.2–98.6 % ≤ 1 s, 100 % ≤ 5 s, max 1.03 s; job backlog ≤ 12 (§4.2). | Under the 3× burst the index lag is 22–23 s mean (only 30 % ≤ 10 s) — a burst gap, not the F05.5 load. |

## 2. Harness (what runs where)

`.github/workflows/performance.yml` (pull requests touching the harness/migrations/search code, and `workflow_dispatch`
with `suites` = all|plans|load, `listings`, `soak`, plus weekly schedule). PRs run only a gated **10k smoke**; full
evidence runs only on dispatch/schedule. The full workflow has three parallel jobs on `ubuntu-latest` (4 vCPU, 16 GB; CPU model
varies per runner and is recorded — AMD EPYC 7763, AMD EPYC 9V74 or Intel Xeon 8573C were all seen):

- **query-plans** (`scripts/ci-query-plans.sh`, ~11 min): fresh `postgis/postgis:16-3.4` container with the same
  default settings as `docker-compose.yml` (shared_buffers 128 MB, work_mem 4 MB, effective_cache_size 4 GB,
  random_page_cost 4, 2 parallel workers per gather), real Flyway migrations (`flyway/flyway:11.20.3-alpine`) through
  V095 ("before"), deterministic production-shaped fixture (`infra/perf/seed-listings.sql`), then
  `scripts/query-plans.py` runs every block of `infra/perf/query-catalog.sql`. The column lists and the owner-page SELECT
  are read from the Java adapters at run time, so the plans describe the SQL the application sends. **warm** = third
  execution; **cold** = PostgreSQL restarted and the kernel page cache dropped (`echo 3 > drop_caches`) before every
  statement. 100k → extended to 1M → `flyway migrate` (V100, 7 s on 1M rows) → ANALYZE → "after".
- **mixed-load steady / faults** (`scripts/ci-mixed-load.sh`, ~20 min each): the full `docker-compose.yml` stack
  (`.env.demo.example`, plus `infra/perf/compose.perf.yaml` which only preloads pg_stat_statements), UAT accounts,
  the same fixture with **100,000 public + 20,000 non-public listings, 5,000 sellers**, then a real index rebuild through
  `POST /api/v2/admin/search/index/rebuild` (timed) and `cleanup`. k6 1.7.0 (`infra/k6/mixed-search-drafts.js`, now
  `READ_MIX=realistic`): constant-arrival-rate **100 reads/s** (25 % cached first page, 25 % filtered search, 10 %
  keyword, 10 % map at zoom 11/13/15, 25 % detail by slug over all 100k listings, 5 % seller page) + **10 draft
  creates/s**; the soak adds the publication flow (one create → submit → moderator approve every 2 s, then a
  bbox+keyword search that bypasses the first-page cache is polled every 200 ms until the listing appears). Every phase
  records k6 per-request-type latency, a 2/10/30-s timeline, Prometheus deltas, pg_stat_statements top statements, a 5-s
  job/outbox backlog sample and the persisted-draft verification. Rate limiting stays ON; multiplier 100 for the single
  CI IP (search-v2 300/min × 100 = 500/s, needed for the 3× burst); every 429 counts as a failure (none occurred).
- Fault order (faults suite): warm-up (reported only) → warm → search-cache eviction → **cold restart** (backend+frontend
  stopped, PostgreSQL and Elasticsearch restarted, Redis search keys evicted, kernel page cache dropped, backend started
  and warm-up/health waits completed before the next phase's load) → **ES transition** (20 s, non-blocking, ES stopped and search keys evicted just before)
  → wait until `bds_search_breaker_state == 1` → **ES unavailable** (60 s, gated, every search must be degraded) → SQL
  statements per request type while ES is down → ES recovered → ES hanging (paused, transition + gated fallback)
  → ES unpaused → Redis unavailable → Redis recovered.

## 3. Query plans (F09.3, D-05, F09.2)

Run [37067923955](https://github.com/Babychandoi/Real-estate/actions/runs/37067923955) (commit `d26e7eb`), runner AMD
EPYC 7763, 4 vCPU, 16 GB. Full plans (JSON + text trees per query and mode) in artifact `query-plans-37067923955-1`
(`<label>/<query>.<mode>.json`, `<label>/summary.md`, `report.md`). Repeated in run
[37069904979](https://github.com/Babychandoi/Real-estate/actions/runs/37069904979) (commit `4b91387`, AMD EPYC 9V74):
same plans and warm times within ±15 %; cold times are 1.5–3× higher there (slower runner disk, e.g. map clusters
zoom 13 cold 39 s vs 15.7 s), so cold numbers are only comparable within one run. That run also fixes the map points
probe: in run 37067923955 it used a box with 7k matches that the application would answer with clusters (it asks for
points only when the count is ≤ 400), and the planner flipped between two equally costed plans (28 ms / 167 ms);
with a realistic ≤ 400-match box it is 2.9 ms on the GiST index at 1M.

### 3.1 Dataset

| | 100k | 1M |
|---|---:|---:|
| `listing_public_read` rows (SALE / RENT) | 100,006 (72,218 / 27,788) | 1,000,006 (720,595 / 279,411) |
| `listings` / `listing_revisions` | 120,006 / 120,006 | 1,200,006 / 1,200,006 |
| sellers (users) / leads | 50,001 / 7,870 | 50,001 / 80,074 |
| `listing_public_read` size (total / heap) | 205 MB / 156 MB | 1,981 MB / 1,563 MB |
| largest seller | 434 listings | 4,510 listings |
| seed time incl. VACUUM ANALYZE | 35 s | +302 s |

Distribution (deterministic hash of n): 72 % SALE; 40 % apartment, 25 % house, 15 % townhouse, 15 % land, 5 % villa;
30 Hà Nội districts with a power-law share (Cầu Giấy 21 %, the rarest < 0.1 %); points clustered round district centres;
log-uniform prices; published dates skewed to the last months; 60 % identity-verified, 20 % ownership-verified;
seller share ∝ rank² (a few large agencies). Synthetic, so real skew can differ.

### 3.2 Before/after V100 (`idx_lpr_slug`)

`JdbcListingReadModelAdapter.findVersion` runs on **every public detail request** (`GET /api/v2/listings/{slug}`:
ETag and cache key) with `WHERE slug = ?`; V033 had no index on the read model's slug.

| detail.version.slug | 100k warm | 100k cold | 1M warm | 1M cold |
|---|---:|---:|---:|---:|
| before (V095): Seq Scan on listing_public_read | 38.6 ms | 185.5 ms (20,004 blocks read) | 223.2 ms | 3,858 ms (200,005 blocks read) |
| after (V100): Index Scan `idx_lpr_slug` | — | — | **0.099 ms** | **1.04 ms** (6 blocks read) |

### 3.3 Execution time per shape (ms)

Warm / cold, run 37067923955 unless stated. V100 changes no plan other than the slug lookup (the map points flip is
explained above); the full table with every shape and the "after" columns is in the artifact `report.md`.

| Shape (application method) | 100k warm | 100k cold | 1M warm | 1M cold | Index used (1M) |
|---|---:|---:|---:|---:|---|
| search newest SALE p1 (`page`, DB engine) | 0.79 | 13.8 | 1.08 | 12.3 | idx_lpr_newest + users_pkey |
| search newest SALE p2 (keyset) | 0.79 | 11.7 | 1.17 | 11.7 | idx_lpr_newest |
| search PRICE_ASC / PRICE_DESC / AREA_DESC | 0.72–0.83 | 10.3–13.6 | 1.06–1.14 | 12.0–13.9 | idx_lpr_price / idx_lpr_area |
| search district+type+price, NEWEST | 2.78 | 164 | 3.88 | 172 | idx_lpr_newest |
| search area+beds, PRICE_ASC | 1.01 | 21.7 | 1.41 | 21.9 | idx_lpr_price |
| search rarest district, NEWEST | 4.11 | 239 | 4.61 | 218 | idx_lpr_newest |
| search rare district+VILLA RENT, PRICE_DESC | 3.29 | 151 | **80.1** | **2,423** | idx_lpr_price (17k rows filtered) |
| search keyword common, NEWEST | 0.87 | 40.4 | 0.75 | 53.4 | idx_lpr_newest |
| search keyword rare | 0.18 | 1.59 | 4.90 | 11.6 | **idx_lpr_search_tsv (GIN)** |
| search keyword+district, PRICE_ASC | 4.84 | 283 | 4.27 | 374 | idx_lpr_price |
| capped count default (`countCapped`, 10,001) | 34.3 | 217 | 32.7 | 197 | seq scan (stops at 10,001) |
| capped count district+type+price | 40.9 | 269 | **328** | **4,711** | idx_lpr_price |
| capped count keyword | 37.9 | 935 | **148** | **1,756** | GIN + idx_lpr_district |
| ES hydrate 24 ids (`findByIds`) | 0.46 | 11.3 | 0.38 | 17.3 | pkey |
| map points zoom 15, ≤ 400 matches (run 37069904979) | 0.65 | 13.3 | 2.88 | 144 | **idx_lpr_location (GiST)** |
| map count zoom 15 / zoom 11 | 20.8 / 41.2 | 174 / 74 | 34.8 / 40.0 | 1,477 / 205 | **idx_lpr_location (GiST)** / seq |
| map clusters zoom 11 (city) | 129 | 244 | **1,004** | **3,882** | seq scan, 1.56 GB heap |
| map clusters zoom 13 (district) | 49.2 | 1,553 | **793** | **15,669** | GiST ∧ idx_lpr_district |
| map clusters zoom 11 + type/price | 48.8 | 330 | **555** | **7,541** | GiST ∧ idx_lpr_price |
| detail by id / row / gone / price history | 0.08–0.14 | 1.1–1.9 | 0.08–0.12 | 1.0–1.9 | pkeys, uq_listings_slug |
| similar listings (`similar`) | 22.8 | 366 | **363** | **5,511** | idx_lpr_price (123k rows → heap) |
| seller page / count / profile (largest seller) | 0.24 / 0.27 / 2.56 | 4.7 / 1.8 / 75 | 0.18 / 2.0 / 17.1 | 4.5 / 9.0 / 817 | idx_lpr_owner_newest, idx_leads_… |
| owner my-listings counts / page 1 / ACTIVE / DRAFT | 0.19 / 0.69 / 0.64 / 0.61 | 1.6–15 | 1.13 / 0.57 / 0.71 / 0.61 | 11.5–19.3 | idx_listings_owner_status_created / _owner_created |
| owner my-listings page 21 (OFFSET 400) | 6.84 | 215 | 6.90 | 314 | idx_listings_owner_created |
| index rebuild batch (`batchAfter`, 500) | 1.78 | 70.3 | 1.08 | 70.5 | pkey |

Planning time: 1–2 ms for plain shapes, 9–10 ms for PostGIS shapes; cold planning (empty catalog cache after restart)
10–25 ms. No sort or hash spilled to disk in any plan (`temp_written = 0`; sorts are in-memory top-N or quicksort).
Warm at 1M still reads from the OS page cache, not shared buffers: the 2 GB read model does not fit the default 128 MB
shared_buffers that `docker-compose.yml` uses.

**Not index problems** (no index was added for them, D-05 "no gut-feeling indexes"):

- *Map clusters* aggregate every listing inside the viewport on PostgreSQL (`ListingReadService.map` is database-only):
  O(rows in the bbox), reading the wide read-model rows (≈1.6 KB/row). 1 s warm / 3.9 s cold at city zoom on 1M rows.
  A GiST index on `geometry` cannot serve an index-only scan (lossy boxes), so no covering index helps. Fix =
  design: Elasticsearch `geotile_grid` aggregation (the index already has `location` as `geo_point`) or a slim
  pre-aggregated grid; until then the map is the scaling limit (§4.3).
- *Capped counts* scan up to 10,001 matching rows plus the seller-ACTIVE semi-join for every row (a hash of all users
  or 10,001 index probes); they only run on the database engine (fallback) and for the map total.
- *Similar listings* fetch every listing of the same purpose in a ±30 % price band (123k index rows at 1M) to sort by
  "same district first, nearest price"; a rewrite into two index-ordered probes is needed, not another index.
- The planner choices themselves are sound: every list page stops after 25 index rows, the GIN index is used when the
  keyword is selective, and the newest index when it is not.

## 4. Load results (D-13, R-5, F05.5)

Numbers are k6 client-side latency (whole HTTP round trip through the frontend Nginx), runner as stated, 100k fixture.
Gate per phase = the existing k6 thresholds, unchanged: reads p95 < 500 ms and p99 < 1000 ms, writes p95 < 1000 ms
and p99 < 2000 ms, HTTP errors < 1 %, checks > 99 %, **0 dropped iterations**.

### 4.1 Steady suite

| Run (CPU) | Phase | Reads p50 / p95 / p99 ms | Writes p50 / p95 / p99 ms | Dropped | Gate |
|---|---|---|---|---:|---|
| 37061342752 (Xeon 8573C) | soak 10 min | 3.4 / 81 / 385 | 7.2 / 154 / 771 | 1 | fail (1 dropped start; max 3.7 s) |
| 37063450468 (EPYC 7763) | soak 10 min | 4.2 / 31 / 244 | 7.0 / 15.8 / 25.7 | 0 | **pass** |
| 37065843904 (EPYC 9V74) | soak 10 min | 4.0 / 30.6 / 256 | 7.0 / 15.3 / 25.5 | 0 | **pass** |
| 37067923955 (EPYC 7763) | soak 10 min | 4.0 / 28.2 / 237 | 6.5 / 13.8 / 22.0 | 0 | **pass** |
| 37069904979 (EPYC 7763) | soak 10 min | 4.4 / 33.8 / 253 | 7.1 / 15.2 / 22.9 | 0 | **pass** |
| 37063450468 (EPYC 7763) | burst 3× (300 + 30/s for 60 s) | 2263 / 3387 / 4218 | 3102 / 4377 / 5047 | 4264 | **fail** |
| 37065843904 (EPYC 9V74) | burst 3× | 2222 / 3315 / 4196 | 3067 / 4360 / 4812 | 4215 | **fail** |
| 37067923955 (EPYC 7763) | burst 3× | 2194 / 3218 / 4080 | 2998 / 4222 / 4643 | 3829 | **fail** |
| 37069904979 (EPYC 7763) | burst 3× | 2256 / 3407 / 4264 | 3119 / 4441 / 4959 | 4299 | **fail** |

Soak, run 37065843904, per request type (p50 / p95 / p99 / max ms): cached first page 3.7 / 8.0 / 13.0 / 85;
filtered search 6.8 / 20.4 / 153 / 423; keyword 3.5 / 15.6 / 29.9 / 116; map 17.6 / 286 / 377 / 734; detail 2.8 /
6.9 / 11.2 / 172; seller page 3.8 / 9.1 / 15.0 / 70; draft create 7.0 / 15.3 / 25.5 / 96. 50,256 API requests,
**0 5xx, 0 429**; Hikari: 0 connection timeouts; the 30-s timeline is flat for all 10 minutes (p95 19–42 ms per
bucket, no drift). Reads p99 ≈ 250 ms comes from the map (p99 377 ms).

### 4.2 Publication and index lag (F05.5)

| Run | Approvals | Visible within 30 s | Lag p50 / p95 / p99 / max (approval response → in search) | bds.search.index.lag (job created → index write) | Job backlog |
|---|---:|---:|---|---|---|
| 37063450468 soak | 301 | 100 % | 1042 / **1676** / 1859 / 2065 ms | 6,314 writes, mean 0.508 s, 98.56 % ≤ 1 s, 100 % ≤ 5 s, max 1.02 s | max 12 pending, oldest 1.0 s |
| 37065843904 soak | 301 | 100 % | 1043 / **1678** / 1889 / 2078 ms | 6,314 writes, mean 0.510 s, 98.21 % ≤ 1 s, 100 % ≤ 5 s, max 1.02 s | max 12 pending, oldest 1.0 s |
| 37067923955 soak | 301 | 100 % | 1038 / **1846** / 1878 / 1899 ms | 6,308 writes, mean 0.511 s, 98.21 % ≤ 1 s, 100 % ≤ 5 s, max 1.03 s | max 11 pending, oldest 1.0 s |
| 37069904979 soak | 301 | 100 % | 1040 / **1685** / 1880 / 2107 ms | 6,311 writes, mean 0.509 s, 98.29 % ≤ 1 s, 100 % ≤ 5 s | — |
| 37065843904 burst | — | — | — | 2,205 writes, mean 23.3 s, 29.9 % ≤ 10 s, 63.4 % ≤ 30 s, max 54 s | max 1,478 pending, oldest 54 s |

The client lag includes up to 200 ms of polling granularity; the job worker polls every 1 s (APP_JOBS_POLL_MS) and
Elasticsearch refreshes every second, which is what the ~1 s median is made of.

### 4.3 Burst: where the capacity goes

During the 3× burst (run 37065843904) PostgreSQL spent **634 s** in map-cluster aggregations (1,399 calls, mean 454 ms)
and **261 s** in map counts (2,009 calls) of 1,040 s of statement time (top 25 statements) in 100 s — about 10 busy
cores' worth on a 4-vCPU runner — while a search page costs 0.4 ms (35,959 calls in the soak). The map is 10 % of the
reads but **86 %** of database time in the burst and **83 %** in the soak (312 s clusters + 133 s map counts of 537 s). The timeline shows the system healthy
at 100/s, saturating within 10 s of the ramp (p50 0.7 s → 2.3–2.7 s) and recovering 10 s after the load drops back.
The burst target is therefore not reachable with database-side map clusters on this hardware; see §6.

### 4.4 Faults suite

| Phase | Run 37063450468 (Xeon 8573C, before the fix) | Run 37065843904 (EPYC 7763, with the fix) | Run 37067923955 (EPYC 7763, with the fix) |
|---|---|---|---|
| warm (60 s) | reads 3.3 / 26 / 219, writes 8.7 / 16.7 / 25.3, 0 dropped — pass | 4.1 / 27.8 / 243, 8.3 / 17.9 / 27.5, 0 — pass | 4.5 / 34.4 / 258, 8.8 / 20.0 / 30.8, 0 — pass |
| search cache evicted | 3.0 / 25 / 216, 0 dropped — pass | 3.7 / 28.1 / 237, 0 — pass | 3.9 / 23.6 / 241, 0 — pass |
| cold restart (gated) | 7.4 / 432 / 1317, writes p99 2236, 2 dropped — **fail** | 15.5 / 2305 / 3358, writes p99 5359, 70 dropped, 1,737 of 3,520 searches degraded — **fail** | 17.2 / 2084 / 3759, writes p99 5184, 42 dropped — **fail** |
| ES transition (20 s, non-blocking) | 23 / 347 / 416, max 804, 0 dropped | 8.2 / 420 / 1507, max 2595, 0 dropped | 5.5 / 94 / 302, max 474, 0 dropped |
| ES unavailable (gated, breaker open) | 69 / **565** / 997, writes p95 286 — **fail** | 6.1 / **137** / 347, writes 11.1 / 23.9 / 40.9, 0 dropped — **pass** | 5.9 / **119** / 315, writes 10.7 / 24.0 / 33.9, 0 — **pass** |
| ES recovered | 3.4 / 37.6 / 225 — pass | 4.4 / 52.1 / 262 — pass | 4.8 / 52.0 / 287 — pass |
| Redis unavailable | 5.0 / 41.8 / 225 — pass | 6.7 / 49.9 / 294, limiter on local fallback (gauge 0), Redis breaker open — pass | 6.4 / 38.5 / 257 — pass |
| Redis recovered | 3.0 / 29.5 / 215 — pass | 3.9 / 31.7 / 242, limiter back on Redis (gauge 1) — pass | 4.1 / 31.6 / 254 — pass |

Run 37069904979 (EPYC 7763, with the fix) repeated the same picture: warm p95 28.8, cache evicted 22.9, cold restart
p95 2,450 / 99 dropped (fail), transition p95 141 / p99 739 / 0 dropped, ES unavailable **p95 119 / p99 310 (pass)**,
ES recovered 71.9, Redis unavailable 42.9, Redis recovered 27.6 (ms, reads).

(Faults job of run 37061342752, EPYC 9V74, also before the fix: ES unavailable reads p95 3,049 ms with 103 dropped starts —
the database fallback saturated. CPU models above describe each job, not the entire run; steady and faults have different runners.)

API error rate is **0 %** in every phase of every run (the only 5xx are 3 `GET /actuator/health` 503s while Redis is
down — the container health check, not user traffic). 429s: none.

**ES outage — transition and steady phase measured separately** (owner decision): the transition phase is 20 s,
starts with Elasticsearch already stopped and the search keys evicted, and never gates. Its 2-s timeline (run
37065843904): the breaker is open after the first second (41 degraded searches in the first 2 s), the worst 2-s bucket
is p95 1625 / max 2595 ms (seconds 2–4, database fallback absorbing the uncached first pages), then p95 83–255 ms for
the rest of the 20 s. In run 37067923955 (same CPU class, with the fix) the whole transition stayed at p95 43–138 ms
per 2-s bucket, max 474 ms, 0 dropped. Breaker state read from `bds_search_breaker_state` = 1.0 before the gated degraded phase started.

**Fix for the degraded phase** (`ListingSearchService`, test `SearchDegradedFirstPageCacheTests`, fails without the
change): with the breaker open, the first-page cache key already is the database engine's key, yet a degraded page was
never cached, so every repeated first page and its capped count ran on PostgreSQL. In run 37063450468 the default first
page's count alone cost 102 s of database time per minute. The page is now cached under the database key (a fallback
computed under the engine's key is still never cached; closing the breaker switches back to the engine's key). Measured before → after: ES-unavailable reads p95 3,049 ms (EPYC 9V74) → 137 ms and 119 ms (EPYC 7763), first-page
cache hits 0 → 2,407 per minute. The machines differ, so these runs alone do not isolate the improvement due to the cache;
the reviewer tests separately pin the cache behavior.

**Cold restart, original implementation**: Elasticsearch, PostgreSQL, the JVM and the page cache all start cold before
the full 100/10 load. The first ~20–25 s are slow (10-s buckets: p95 3.1 s, 3.1 s, 0.4 s, 0.14 s, then 42–48 ms);
steady state is back after ~30–40 s. Degraded searches are observed, but these measurements do not distinguish index
readiness from breaker opening, nor isolate JVM, ES or DB cold-start costs. The earlier claim that the first cold ES
calls opened the breaker was a hypothesis. Startup warm-up and the O2 map change are evaluated separately in §9.

### 4.5 Other measured facts

- Index rebuild (D-13 "rebuild index"): 100,060 documents in **14.7–16.9 s** (5,900–6,800 docs/s) through the admin
  API with the alias swap; dual writes continued.
- Draft writes: every phase's successful creates matched the persisted-draft count in PostgreSQL (verify-draft-writes).
- Index lag after the ES outage: jobs created during the outage are written after recovery (es-recovered: 1,011
  writes, mean 39 s, 59 % ≤ 1 s); no job was dead-lettered (`bds_jobs_dead` 0); retried jobs from the outage were still
  draining during Redis-unavailable (max 240 pending, oldest 244 s) and the backlog was back to ≤ 8 in Redis-recovered.
- Outbox: `outbox_events` unprocessed = 0 in every sample (OUTBOX_ENABLED=false in this stack; nothing writes it on these
  paths). The durable job queue (`background_jobs`) is the backlog that matters and is reported above.
- Approvals write duplicate-candidate rows for every near-identical listing (`listing_duplicate_candidates`: 40,100
  inserts for 301 approvals of the deliberately identical test listings) — linear in the number of near-duplicates.

## 5. SQL statements per request type (R-5 "query count")

`scripts/query-counts.py`: pg_stat_statements reset → 6 s idle window (statements the workers run on their own are
recorded and excluded) → 30 sequential requests of one type → calls / 30. Run 37065843904 (steady suite, warm):

| Request type | Statements / request | Which |
|---|---:|---|
| search first page, cached | 1.0 | hydrate ids (`findByIds`) |
| search filtered, uncached (Elasticsearch) | 1.0 | hydrate ids |
| search keyword, uncached (Elasticsearch) | 1.07 | hydrate ids (+ suggestion counts on an empty page) |
| search filtered / keyword while ES is down (faults run) | 2.0 / 2.0 | page + capped count |
| detail by slug, uncached / cached | 2.0 / 1.0 | version by slug + row / version only |
| map clusters zoom 11, map points zoom 15 | 2.0 | capped count + clusters/points |
| seller page | 2.0 | profile + page |
| draft create | 12.0 | session, KYC, slug check, 2 inserts + reads, audit hash chain (2), trigger refresh/enqueue |
| create + submit + approve (publication) | 74.0 per flow | incl. 31 duplicate-candidate upserts at that point of the run |

## 6. What is not done and why

- **Burst 3×** (D-13/R-5): original DB-only version fails (§4); O2 Elasticsearch aggregation now passes on 100k (§9).
  The remaining validation is a repeat on the final PR head and representative production skew.
- **Cold restart gate**: original version fails for ~25 s (§4.4); warm-up/O2 brings read p95 to 72 ms, but 2 dropped
  iterations still fail the first gate (§9); the strict repeat has zero drops but fails latency (§10). No gate has been weakened.
- **1M listings under load**: run 37103504199 attempted the full mix, but shared-memory exhaustion prevents a
  complete passing soak/burst/fault result (§10). Distinct uncached DB fallback viewports remain a scaling risk.
- **Restore / migration rollback drill** (D-13 "khôi phục") and **concurrent writes to the same listing**: not run here.
- **Production configuration observations** (not changed, production stack is out of scope): default shared_buffers
  128 MB is ~6 % of a 1M-listing read model; Docker's default 64 MB `/dev/shm` makes a manual `VACUUM` with parallel
  index workers and large parallel hash joins fail with "could not resize shared memory segment" (hit by the fixture;
  Compose now allows 256 MB tmpfs (§10); production is unchanged until PostgreSQL is recreated).

## 7. Checks

| Check | Result |
|---|---|
| CI `backend-tests` on the PR head with the search change (run 37065843900) | **452 tests, 0 failures, 0 errors**, BUILD SUCCESS |
| `SearchDegradedFirstPageCacheTests` locally | 2/2 pass; **2/2 fail** with the `ListingSearchService` change reverted |
| Local `mvnw verify` (shared test infra) | 452 tests, 0 failures, 12 errors — MediaPipelineIntegrationTests 7, OpenApiSnapshotTests 2, SchemaMigrationTests 2, SitemapTests 1, all from the shared test PostgreSQL crashing mid-run ("I/O error while sending to the backend", "not yet accepting connections", "recovery mode"; the amd64-emulated container restarted repeatedly this session, also before my runs). The same commit is green on CI (row above). |
| `python3 scripts/perf_summary_test.py` | 7 tests OK |
| Frontend | not touched |

## 8. Reproduce

```sh
gh workflow run performance.yml --ref audit/w6-perf -f suites=all -f listings=100000 -f soak=10m
gh run download <run-id>   # query-plans-*, mixed-load-steady-*, mixed-load-faults-* artifacts (30 days)
```

Runs used as evidence (all on PR #23, workflow file `.github/workflows/performance.yml`): 37061342752 (first harness run, before
the search fix), 37063450468, 37065843904 (search fix), 37067923955 (first complete 1M plans), 37069904979 (plans
repeat with the corrected map-points probe, fourth load sample). CI (`ci.yml`) on the same commits: 37061342623,
37063450279, 37065843900 (with the search change: 452 backend tests green).


## 9. Review round 2 and O2 evidence (2026-10-03)

The O1 measurements (§4) show map counts/clusters taking 83–86 % of DB time; removing the separate count alone does
not remove the aggregation cost. O2 therefore moves clusters to Elasticsearch `geotile_grid` with a bounded 2,000-cell
answer, centroid and bounds. Zoomed-in points use one SQL probe limited to 401 rows, returning points only at ≤400.
There is no capped map count. `MapEngineSelectionTests` covers points, dense viewports, engine clusters, exact DB mode
and shared outage fallback; `SearchElasticsearchEngineTests` exercises the aggregation against the real engine.

Elasticsearch clusters follow the index refresh lag (roughly 1 s), unlike SQL points. During an ES outage, identical
filters and **exact bbox** share database clusters for 60 s. Cached answers retain their actual computation
`dataAsOf`, rather than claiming the hit time. Panning changes the cache key; clusters/counts exclude points outside
the requested bbox. Redis unavailable uses request collapsing without shared caching. This bounds repeated identical
work, not arbitrary distinct viewports; 1M fallback capacity requires the final integrated run.

Startup warm-up (`SearchWarmup`, off by default, enabled by Compose) prewarms DB indexes/heap when `pg_prewarm` exists
(V101), calls representative engine searches/aggregations directly without tripping the breaker and hydrates hits.
Warm-up is best effort, with a 60 s default deadline: failures/timeouts are reported in health/logs and release startup
to the existing fallback. It does not guarantee a latency target for every query or warm the listing-write JVM path.
The health contributor gates Compose startup. Readiness explicitly includes `readinessState,searchWarmup,db`; liveness
excludes dependencies. A regression check holds readiness at 503 while warm-up or DB is unavailable, preserves
liveness 200, then observes readiness 200 after recovery. The contributor is necessary because Boot publishes its
normal ACCEPTING_TRAFFIC event after ApplicationReadyEvent; see [Spring health groups](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html).

[Run 37101809547](https://github.com/Babychandoi/Real-estate/actions/runs/37101809547), commit `2d7ab74`, **100k**,
full realistic read mix, normal unchanged thresholds; artifacts `mixed-load-steady-37101809547-1` and
`mixed-load-faults-37101809547-1`:

| Job/CPU | Phase | Read p50/p95/p99 ms | Write p50/p95/p99 ms | Dropped | Gate |
|---|---|---|---|---:|---|
| steady / EPYC 7763 | 10-minute soak | 3.78 / 10.33 / 21.84 | 6.27 / 10.20 / 15.61 | 0 | PASS |
| steady / EPYC 7763 | burst 3× | 5.21 / 20.34 / 49.44 | 6.64 / 20.93 / 42.43 | 0 | PASS |
| faults / EPYC 9V45 | cold restart | 4.21 / 72.19 / 386.40 | 9.23 / 72.00 / 1691.69 | 2 | FAIL (dropped arrivals) |
| faults / EPYC 9V45 | ES unavailable | 2.67 / 60.21 / 90.51 | 6.32 / 10.79 / 18.14 | 0 | PASS |
| faults / EPYC 9V45 | ES hanging | 2.66 / 61.00 / 92.83 | 5.38 / 10.03 / 15.22 | 0 | PASS |
| faults / EPYC 9V45 | Redis unavailable | 3.96 / 8.10 / 17.15 | 4.87 / 7.18 / 9.95 | 0 | PASS |

Warm, cache eviction, ES recovered and Redis recovered also pass. Cold restart has **0 degraded searches, 0 5xx,
0 429**; all latency/error thresholds pass, but the first ten seconds drop two writes while k6 grows from 20 to 22
write VUs (maximum write latency 2,264 ms, 10 arrivals/s). Its zero-drop gate remains failed. The generator now
preallocates 30 write VUs, enough for that observed latency with headroom; rate, maximum VUs, latency/error/drop gates
stay unchanged. A final-head CI repeat is required before claiming the cold gate passes.

Soak publication: **300/300** approvals visible, lag p50/p95/p99/max **1039 / 1840 / 1860 / 1890 ms**. Query plans at
100k/1M also pass on this run. These are synthetic CI results, not production measurements. D-13 and R-5 remain PARTIAL
for final-head cold verification, 1M mixed load, concurrent same-listing writes and restore/rollback coverage.

Reviewer cache tests now assert the formerly missing behavior: the DB first page/count remain cached during an
in-flight half-open probe, and a bounded 10-second local ID-only cache serves degraded first pages when Redis is also
unavailable. Hydration always rechecks public visibility. Healthy engine results never use that local fallback cache.

Local focused check (2026-10-03): `sh mvnw -B -ntp
-Dtest=SearchWarmupTests,MapEngineSelectionTests,SearchDegradedCacheReviewTests,SearchDegradedFirstPageCacheTests,SearchCircuitBreakerTests
test` — **19 tests, 0 failures/errors** before adding the DB variant of the readiness test; the final variant is verified
in CI. `python3 -B scripts/perf_summary_test.py` — **7 pass**; `bash -n scripts/ci-mixed-load.sh scripts/ci-query-plans.sh`
and `git diff --check` pass. No local load, full stack, production command or production data was used.


## 10. Strict repeat, 1M attempt and fixes awaiting integrated measurement (2026-10-03)

[100k run 37103471806](https://github.com/Babychandoi/Real-estate/actions/runs/37103471806), measured commit `d6344ae`:
steady job on Xeon 8573C passes the 10-minute soak (read p95/p99 **8.89/17.26 ms**, write **9.12/13.48 ms**) and
3× burst (read **13.01/31.33 ms**, write **14.23/22.72 ms**), with zero dropped arrivals. Publication **301/301**,
lag p50/p95/p99/max **1034/1654/1681/1850 ms**. Faults on EPYC 7763 still **FAIL cold restart**:
read p50/p95/p99 **7.58/583.93/1159.64 ms**, write **12.45/679.71/2312.05 ms**, with zero drops, degraded searches,
5xx or 429. The first ten seconds dominate (read p95 1308 ms, write p95 2664 ms); subsequent read p95 falls to 68 ms,
then 21/14/13 ms. SQL/hydration are short (hydrate mean 0.58 ms, map-points mean 4.72 ms), without Hikari timeouts.
Direct engine/DB warm-up therefore does not establish readiness of the actual HTTP stack. All other gated fault
phases pass; the non-gated ES-hang transition has p99 about 1488 ms and is retained in the report.

[1M run 37103504199](https://github.com/Babychandoi/Real-estate/actions/runs/37103504199), commit `3128409`:
steady and faults jobs both **FAIL**. Rebuild reaches **1,000,060 documents** in 93.8 s on Xeon 8573C and 111.1 s
on EPYC 7763. During warm-up/fallback, PostgreSQL reports `could not resize shared memory segment: No space left
on device`; Docker's default `/dev/shm` is 64 MB. Steady never completes the soak/burst; faults cannot produce a
passing full fault result. These are attempted capacity measurements, not passing evidence.

The next code adds read-only loopback HTTP requests against the actual application web-server port before readiness,
covering search, maps and sampled existing public detail/seller routes. It skips non-web/MockMvc contexts, never follows
redirects, never sends writes/credentials, and uses per-request timeouts within the shared `APP_WARMUP_MAX_DURATION`
(default `PT60S`). Warm-up remains best effort; the unchanged cold thresholds determine whether this works.
Compose and standalone query-plan PostgreSQL now allow **256 MB `/dev/shm`**: an on-demand tmpfs allowance, not 256 MB
preallocated RSS. Apply production Compose configuration by recreating **only PostgreSQL**, preserving its named volume
and waiting for health before app startup; application deployment with `--no-deps` does not apply it. No production
container was changed by this stream. Environment examples expose warm-up enablement and its deadline.

The outage first-page cache admission is synchronized so concurrent unique requests cannot exceed its bound.
A concurrency regression passes and fails after temporarily removing the mutex (source restored). Map regressions
assert exact bbox edge exclusion, reuse only for identical viewports, and preservation of cached `dataAsOf`.
The query catalog follows the bounded 401-row points probe and removes the two obsolete map count shapes; historical
40-shape results above describe their original commits, while the current catalog has 38 shapes.

Focused local check before the final bbox change: **26 tests, 0 failures/errors** (`LoopbackHttpWarmupTests`,
`SearchWarmupTests`, `LocalPageCacheTests`, `MapEngineSelectionTests`, degraded cache and circuit-breaker tests),
19.542 s. Disposable HTTP tests exercise real GETs, a stalled handler/shared deadline, redirects and no-web skip.
After the exact-bbox change, a second focused check passes **16 tests, 0 failures/errors** in 20.021 s
(warm-up, local cache, map). `perf_summary_test.py` passes 7 checks; Compose configuration, shell syntax and
`git diff --check` pass. Latest full core CI on `3128409` (run 37103506976) passes backend/frontend/security/e2e. New-head CI and strict
The independent review caught a JDK 17 gap: request timeout can stop at headers while the body stalls. The helper now
uses cancellable `sendAsync` plus a deadline-bound wait for the **complete response body**; a 200-header/partial-body
regression passes. Loopback/map recheck: **11 tests, 0 failures/errors**, 11.081 s. Query-plan constant extraction also
joins concatenated Java string literals (the backend expiry guard), with 3 Python checks passing; it rejects unsupported
expressions rather than emitting incomplete SQL. Backend integration must also update the catalog seller/gone blocks
for their newly added expiry checks. Strict 100k/1M benchmarks must run after integration of backend expiry guards/schema 2; **D-13/R-5 remain PARTIAL** until
those artifacts pass. No thresholds or request mix have been relaxed.
