# S10-PERF — first measurable query-plan gate

Status: **PARTIAL**. The repository now has a reproducible, isolated 100k/1M-row query-plan harness. No numbers are
claimed until its outputs are captured on a provisioned test PostgreSQL/PostGIS instance. The 100 read/10 write RPS
constant-arrival-rate scenario, fault tests and restore/rollback drill remain open.

## F09.3 / D-05: query plans

1. Provision a disposable PostgreSQL/PostGIS database named `bds_perf_<run>`; apply the project's Flyway migrations.
   Never point this tool at `bds-production`, the demo stack or the shared test database.
2. Set `BDS_PERF_DATABASE_URL` to that database's connection string. Run:

   ```bash
   BDS_PERF_DATABASE_URL=postgresql://localhost:5432/bds_perf_run1 scripts/run-search-explain.sh 100000
   BDS_PERF_DATABASE_URL=postgresql://localhost:5432/bds_perf_run1 scripts/run-search-explain.sh 1000000
   ```

3. Archive both `.artifacts/perf/explain-search-*.txt` outputs with the database/image versions, CPU/RAM/storage
   limits, `shared_buffers` and warm/cold cache condition. Compare actual rows, planning/execution time and
   shared/temp blocks; note whether the intended B-tree/GiST/GIN index was used. The generator uses deterministic
   distribution and creates indexes matching the relevant V033 leading columns **on a temporary table**. All rows
   disappear when its transaction rolls back. The SQL refuses any database whose name does not start with
   `bds_perf_`. The bbox and keyword probes are deliberately selective so their GiST/GIN plans are meaningful;
   review the actual planner decision rather than assuming it uses either index. An unsuccessful run removes its
   partial report.

This synthetic distribution is useful for a reproducible query-plan regression, but does not represent actual market
data skew. Repeat EXPLAIN against an approved anonymized production-shaped test snapshot before approving index changes.

## Read-only 100 RPS baseline

`infra/k6/steady-search.js` uses k6's `constant-arrival-rate` executor (100 starts/s, not 100 VUs), reports
latency/errors/dropped iterations and checks the v2 response shape. It requires `BDS_PERF_ISOLATED=1` and a
loopback/host.docker.internal URL. Run it only after provisioning a separate seeded stack; for example:

```bash
BDS_PERF_ISOLATED=1 BASE_URL=http://localhost:3000 k6 run infra/k6/steady-search.js
```

The thresholds are a gate for a single run, not evidence of the achieved rate until a k6 summary is archived. A
10 RPS write workload needs an isolated fixture with quota-safe actors and separately verified DB effects.

## Remaining S10 acceptance

- **D-13 / R-5:** capture 100 RPS reads + 10 RPS writes with constant-arrival-rate, p50/p95/p99, query counts,
  index lag, outbox backlog and error rate under cold/warm cache, burst, soak and ES/Redis failure. The existing
  `infra/k6/workload.js` is VU-based and does not prove these rates; a write fixture must avoid quota/rate-limit
  distortion and must run on isolated infrastructure.
- **F21.5 / R-6:** measured RPO/RTO, encrypted-backup restore, compatible migration rollback and response headers
  through the final CDN/proxy. Do not operate the production stack from this harness.
- **F09.3:** pending outputs for both dataset sizes. Passing CI or having indexes present is not benchmark evidence.

## Automated CI capture (2026-09-30)

The integration E2E job now runs `scripts/ci-search-explain.sh` after browser suites. The runner requires
`GITHUB_ACTIONS=true` and `BDS_PERF_ISOLATED=1`, clones schema only into a separate `bds_perf_ci_*` database,
runs both sizes, and drops that database on exit. The `s10-query-plans-<attempt>` artifact retains completed
reports plus environment metadata for 30 days. SQL errors fail the step; partial reports are not uploaded as results.

This captures warm, synthetic TEMP-table plans after insertion/index creation/ANALYZE. Buffers for TEMP relations
are local buffers; do not interpret them as production shared-buffer/cache performance. CI shares hardware with
the integration services. Review the actual plans and archive accepted evidence before changing F09.3 status.
The earlier W1/S5A restore drill already passed; outstanding restore acceptance concerns measured RPO/RTO
and deploy/migration rollback, not an absence of any successful restore drill.
