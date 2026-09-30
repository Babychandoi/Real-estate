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

## Mixed workload implementation (2026-09-30)

`infra/k6/mixed-search-drafts.js` now offers steady (100 read + 10 draft-create starts/s), burst
(200 + 20 peak) and soak profiles, independent endpoint latency/error thresholds, p95/p99, dropped iterations,
created-draft counts and degraded-read counts. It requires an explicitly isolated loopback HTTP stack,
a unique lowercase-letter `PERF_RUN_ID`, and dedicated verified actor tokens supplied through
`PERF_ACTOR_TOKENS_JSON`. Never use production tokens; summaries do not include tokens or response bodies.

```bash
# Configure BASE_URL, PERF_ACTOR_TOKENS_JSON, PERF_RUN_ID in the isolated runner environment.
BDS_PERF_ISOLATED=1 PROFILE=steady PERF_SUMMARY_PATH=.artifacts/perf/mixed.json k6 run infra/k6/mixed-search-drafts.js
```

Run `infra/perf/verify-draft-writes.sql` with psql variables `run_id` and `expected_count` (the k6
`drafts_created` count) against the same disposable `bds_perf_*` database. The read-only SQL rejects a
wrong database and mismatched persisted effects. Use a fresh marker for each run. After collecting evidence,
dispose of the fixture stack; do not delete individual application records through this harness.

This workload creates real private drafts (requires KYC, no publishing quota is consumed). It does **not**
claim published-write/index-lag performance. Index lag, outbox metrics, cold/warm conditions, faults and rebuild
still require a controlled run with the existing integration tests/observability tools. For an intentionally
unavailable search engine use `EXPECT_DEGRADED=1`; normal runs require non-degraded responses. Rate-limit
responses remain failures, never excluded from the measurement. Provision approved fixture capacity first.


## Isolated mixed-load workflow (2026-09-30)

`.github/workflows/performance.yml` runs an independent GitHub-hosted job for harness changes and offers
manual steady/burst/soak profiles. `scripts/ci-mixed-load.sh` creates a unique Compose project and fresh
`bds_perf_ci_*` database, seeds synthetic listings/KYC, waits for non-degraded search, obtains a throwaway
broker session, runs pinned `grafana/k6:1.7.0`, compares successful creates with persisted draft IDs, and
removes the project/volumes on exit. Credentials are kept in a private temporary directory outside uploaded evidence.
The artifact retains hardware/image metadata, p50/p95/p99, error rates, dropped starts, k6 threshold status
and PostgreSQL effect verification. Failures remain failures, including latency, 429, and count mismatches.

PRs run one minute at 100 read/10 create starts per second. Manual steady runs last five minutes, burst peaks
at 200/20, and soak lasts thirty minutes. Rate limiting stays enabled with multiplier 50 because every client
shares one CI IP; this is a diagnostic under the stated test policy. The read mix repeats the warmed SALE
first page against the small UAT dataset. It does not close the 1M/cold-cache/real distribution, publication
lag, ES/Redis outage, rebuild or restore acceptance requirements. Do not mark D-13/R-5 DONE from this workflow alone.
