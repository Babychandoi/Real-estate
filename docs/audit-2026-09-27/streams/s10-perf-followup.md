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


## Accepted small-fixture baseline

GitHub run [36686479978](https://github.com/Babychandoi/Real-estate/actions/runs/36686479978),
artifact `mixed-load-36686479978-1` (11084400418), passed at PR head `4b7f106` / merge commit `0db634d`.
Steady constant arrival rate was 100 read + 10 draft-create starts/s for one minute, warmed SALE first page,
small deterministic UAT fixture, shared GitHub-hosted application/load generator, rate-limit multiplier 50.

| Endpoint | p50 ms | p95 ms | p99 ms | HTTP errors |
|---|---:|---:|---:|---:|
| Reads | 2.74 | 17.58 | 229.06 | 0% |
| Draft creates | 21.04 | 310.70 | 553.99 | 0% |

Dropped iterations: 0. k6 exit: 0. PostgreSQL independently verified **601 distinct persisted drafts**
against the successful-create counter. These numbers certify that specific diagnostic run only.

## Cache/fault/recovery extension

The runner now executes six ordered phases: warm baseline; search-cache cold start; ES unavailable;
ES recovered; Redis unavailable; Redis recovered. Only the warm phase uses the chosen steady/burst/soak
profile; each remaining phase runs steady 100/10 for one minute. Soak explicitly lasts thirty minutes
(the earlier runner unintentionally passed a five-minute DURATION override to soak; no completed soak result existed).
Each phase has its own write marker, k6 summary and PostgreSQL count verification. A failed phase makes the job fail,
even if later phases pass; missing or inconsistent metrics are not acceptance.

Search-key eviction is limited to the runner's Redis `bds:search:v1:*` namespace and preserves session/rate-limit
keys. It is **one-time application-cache eviction**, not cold database/OS buffers or an uncached sustained load.
ES outage first evicts search keys and waits for database fallback so a warm cached response cannot hide the failure.
Measured outage traffic begins after that readiness probe; failover transition latency is not measured here.
Every ES-outage read must be degraded, every other phase read must be non-degraded. Redis limiter availability
must be 0 in its outage phase and 1 after recovery; auth is performed before the outage, preserving fail-closed login.

Before/after snapshots include private-container Prometheus search/cache/index-lag, durable-job queue/backlog,
rate-limit fallback, Hikari/JVM/process metrics and pg_stat_database aggregate activity. Database activity is
not a per-endpoint SQL query count. Draft-related index jobs do not prove public publication throughput.
Artifacts contain no fixture token files. Integrated six-phase acceptance is pending; D-13/R-5 remain PARTIAL.

## Fault-phase findings (CI 36692698150, 2026-10-01)

**Redis phases — fixed in code.** Redis unavailable measured reads p95 4041 ms, writes p95 2014 ms, 370 dropped
starts; the recovery phase still stalled for its first ~13 s. Cause: 2 s command timeout with Lettuce buffering
commands while disconnected (each Redis touch waited the full timeout, a search touches Redis twice), back-offs that
released every concurrent request at once, and Lettuce's reconnect back-off of up to 30 s. Now commands fail at once
while disconnected, the timeout is 250 ms, one shared request-path breaker skips Redis between 5 s probes and the
reconnect back-off is ≤ 2 s (`shared/redis/*`). A local reproduction (not this harness) went from p95 4484/2395 ms
(reads/writes) to 33/94 ms with Redis stopped and from 3007/2572 ms to 69/155 ms while it restarted, with no dropped
starts. The harness's `bds_ratelimit_redis_available` checks keep their meaning (0 during the outage, 1 after recovery).

**ES phase — the measured outage includes the breaker transition.** The section above states that failover transition
latency is not measured; the evidence says otherwise: `bds_search_breaker_state` was 0 (closed) in the phase's
before-snapshot because `wait_search_state 1` makes a single request (one failure, below `min-calls`). All reads of
the phase's first ~1.5 s therefore waited on the stopped engine while the breaker was still closed: p99 1178 ms,
max 1842 ms, 32 dropped starts, while the phase p95 stayed 5.75 ms. The max is about twice the 800 ms budget because a request that waited for an identical
in-flight first page gets nothing reusable (a degraded page is deliberately not cached) and then makes its own engine
call. Collapsing those waiters onto the first answer removes the second wait but starves the count-based breaker (one
failure per 800 ms instead of a burst), stretching the transition to several seconds; no change was made because both
options trade one cost for another. If the phase is meant to measure the steady outage, its readiness step should wait
for `bds_search_breaker_state == 1`; if it is meant to include the transition, `p(99)<1000` with `dropped_iterations==0`
cannot hold at 100 reads/s with an 800 ms budget (≥ ~80 requests are already in flight when the first failure is
known). The thresholds were left unchanged; the decision belongs to the owner.
