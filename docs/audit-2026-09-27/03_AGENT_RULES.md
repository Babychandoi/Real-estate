# Rules for implementation agents

## Hard constraints (violating any of these is a failed task)
1. **Production is off limits.** Never run commands against the Compose project `bds-production`, its containers,
   volumes, network or the `.env` file; never read production data; never restart/stop anything you did not start.
   The demo project `bds-enterprise-stack` is also off limits. The shared test project `bds-test` may be *used*
   (connect, create your own databases/indices/buckets with your stream prefix) but never stopped, recreated or reset.
2. **Git:** work only inside your worktree. Create/rename your branch to `audit/<stream-id-lowercase>` (e.g.
   `audit/s2-search`). Commit in logical commits; every commit message ends with
   `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`. Never push, never merge, never rebase other
   branches, never touch the main worktree at `/Users/connecty/Real-estate`.
3. **Migrations:** only new files inside your Flyway range (`02_CONTRACTS.md` §1). Never edit V001–V026 or other ranges.
4. **Resources (the machine also serves production):** `export MAVEN_OPTS="-Xmx1g"`; run at most one backend JVM and
   one Vite dev server at a time and stop them before you finish; no `docker compose up` of full stacks; no load tests
   except S10 (and S10 caps CPU/memory as described in its brief). Use your stream ports/prefixes only.
5. **No fabricated data or claims** in product UI/docs: no invented statistics, company details, reviews or legal
   statements. Where real information is missing, render an explicit “chưa có dữ liệu”/configurable value.
6. **Security/privacy:** follow `PROJECT_CODE_RULES_BDS.md` (hexagonal modules, no JPA entity in API responses,
   immutable revisions, PII masking/encryption, Problem Details). Never log PII or secrets. Public endpoints must not
   expose drafts, private media, phone numbers or emails.

## Environment
- Java: `export JAVA_HOME=$HOME/.local/opt/jdk17 PATH=$HOME/.local/opt/jdk17/bin:$PATH`; run `cd backend && sh mvnw -B -ntp verify`.
- Test infra env: `eval "$(scripts/test-infra.sh env)"` (already running; check with `scripts/test-infra.sh status`).
- Frontend: Node 20 is installed (`node`, `npm`); `cd frontend && npm ci` once per worktree. Playwright Chromium is installed.
- Git LFS: `export PATH=$HOME/.local/bin:$PATH` if a git hook complains about git-lfs.
- Docs to read before coding: `docs/audit-2026-09-27/00_PLAN.md`, `01_REQUIREMENTS.md` (your IDs),
  `02_CONTRACTS.md`, relevant sections of `Real-estate_Audit_2026-09-27.md` and `PROJECT_CODE_RULES_BDS.md`.

## Engineering expectations
- Read the existing code before changing it; match its conventions (Vietnamese UI copy, English code identifiers).
- Every requirement you claim must have evidence: an automated test where behaviour can be tested, otherwise a
  script/report. Tests must test behaviour, not implementation trivia. Do not weaken or delete existing tests to pass.
- Keep API changes backward compatible unless the contract says otherwise; document breaking changes.
- UI: use the shared UI kit (after S0-FE lands) and tokens; accessible names, focus management, keyboard support,
  empty/loading/error states; mobile first; no emoji icons; no nested interactive elements.
- Performance: bounded queries (limit + stable sort), no N+1, no full scans on hot paths.
- Before finishing: backend `mvnw verify` green, frontend `npm run lint && npm run typecheck && npm run build` green
  (commands as they exist in your branch), relevant Playwright specs green, all background processes stopped,
  `git status` clean.

## Deliverables
1. Commits on `audit/<stream-id>`.
2. `docs/audit-2026-09-27/streams/<stream-id>.md` with: scope done (requirement IDs → evidence: test class/method,
   command + result summary), contract deviations, known gaps (honest), manual/production steps (migrations, env vars,
   deploy notes), follow-ups for other streams.
3. Final message to the orchestrator: branch name, commit list, test commands and results, requirement status table,
   risks. Do not edit `01_REQUIREMENTS.md`, `IMPLEMENTATION_PLANS_HISTORY.md` or `WALKTHROUGHS_HISTORY.md` (the
   orchestrator consolidates them to avoid conflicts).
