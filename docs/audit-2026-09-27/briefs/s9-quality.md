# Brief S9-QUALITY (W5) — architecture gate, OpenAPI contract, structured logs, Problem Details, formatting

Branch `audit/s9-quality`; Flyway V100–V104 (none expected); backend 18124; Vite 5324; Redis/ES prefix `s9`.
Parallel in W5: one other stream (S10-PERF or S11-UX). S9 touches cross-cutting files (`GlobalExceptionHandler`,
`application.yml` logging, `pom.xml`, `ci.yml`, the Prettier pass): keep each change in its own commit so a merge
conflict is easy to resolve.

Read first: `00_PLAN.md` (reassigned Flyway ranges), `01_REQUIREMENTS.md`, `02_CONTRACTS.md`, `03_AGENT_RULES.md`,
`briefs/wave-rules.md`, every `streams/*.md` (S9 follow-ups), `PROJECT_CODE_RULES_BDS.md` §4.3, §8.4, §13, §16.
Audit: F22 (P2).

Requirement IDs: F22.1, F22.2, F22.3, F22.4, F22.5.

Follow-ups inherited from earlier streams:
- S0-BE: framework 4xx (e.g. `HttpMediaTypeNotSupportedException`) map to 500 → proper Problem Details.
- S3a/S4/S5B: fold `ListingDomainException`, `ApiException` (incl. 410/429) into one Problem Details standard.
- S5-SEC-A: `RateLimitResponses`, `SensitiveResponseCacheFilter` and security entry points must use the same shape.
- S7: ArchUnit allowance for `catalog`/`seo` using `search.application.port.ResponseCachePort` (or move it to `shared`).
- S0-BE: `AnalyticsLayeringTests` is superseded by the ArchUnit suite.
- S6/S7/S5B: `SearchPanels.tsx`, `_public.listings.new.tsx` are not Prettier-formatted.
- S4/S7/S8: the OpenAPI snapshot includes admin, CMS/SEO, dashboard and consent endpoints.

## Backend
1. **F22.5 ArchUnit** (`archunit-junit5`, test scope): per module `api → application → domain`,
   `infrastructure → application/domain`; `domain` free of Spring web/JPA/HTTP; `application` free of `api`,
   `infrastructure`, servlet/HTTP types; no module imports another module's `infrastructure`; controllers live in
   `api` and never return `@Entity` types; no field injection. Existing violations: fix the cheap ones, record the
   rest in a frozen violation store (ArchUnit `FreezingArchRule`, committed, one line per violation) so new
   violations fail and fixed ones are removed — never a package-wide ignore.
2. **F22.4 OpenAPI snapshot**: integration test renders `/v3/api-docs` with a deterministic order, compares it with
   the committed `backend/src/test/resources/openapi/openapi.json` and prints what changed;
   `-Dopenapi.snapshot.update=true` rewrites it. CI fails on drift through the normal `verify` run.
3. **F22.2 Structured logs**: `RequestIdFilter` (first in chain) accepts a safe inbound `X-Request-Id` or makes one,
   echoes it, puts `requestId`/`traceId` into MDC and clears it; JSON console log carries MDC; a PII masking step
   removes e-mails, phone numbers and bearer tokens from every logged message; test with captured output.
4. **F22.3 Problem Details**: one factory for every error (`GlobalExceptionHandler`, security entry point/denied
   handler, rate-limit and filter responses): `application/problem+json`, `type/title/status/detail/instance/code`,
   `traceId` = request id, `errors[]` for field errors; framework 4xx (415, 405, 406, missing/mistyped parameter,
   constraint violation, method validation, no resource, payload too large) get their real status, never 500. A test
   drives each case over MockMvc and checks the shape.

## Frontend
5. **F22.4 generated types**: `openapi-typescript` generates `app/shared/api/generated/openapi.ts` from the snapshot
   (`npm run gen:api`); `npm run check:api` fails on drift (CI step). Replace hand-written DTO types with aliases of
   the generated schemas where the shapes match.
6. **F22.1 formatting**: Prettier over the whole frontend tree in one `style:` commit (+ `.git-blame-ignore-revs`);
   backend Spotless (whitespace/newline/unused imports only — no re-flow that would rewrite every line) checked in
   `verify`.

## Tests / gates
`sh mvnw -B -ntp verify` (ArchUnit, OpenAPI snapshot, logging, Problem Details tests), frontend `lint`, `tsc -b`,
`vitest`, `build`, `check:bundle`, `format:check`, `check:api`.

Deliverables per `03_AGENT_RULES.md`: commits, `streams/s9-quality.md`, final message.
