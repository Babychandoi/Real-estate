# Stream S9-QUALITY — architecture gate, OpenAPI contract, structured logs, Problem Details, formatting (wave W5)

Branch `audit/s9-quality` (from `audit-2026-09-27` @ `40323a2`). Flyway V100–V104 reserved, none used (no schema
change in this stream). Backend port 18124, Vite 5324.

## 1. How to verify

```sh
export JAVA_HOME=$HOME/.local/opt/jdk17 PATH=$HOME/.local/opt/jdk17/bin:$PATH MAVEN_OPTS=-Xmx1g
eval "$(scripts/test-infra.sh env)"
docker compose -f infra/test/compose.yaml up -d --wait elasticsearch minio mailpit
cd backend && sh mvnw -B -ntp verify
cd ../frontend && npm ci
npm run lint && npx tsc -b && npm run format:check && npm run check:api \
  && npx vitest run --testTimeout=30000 && npm run build && npm run check:bundle
```

Results on the final commit:
- Backend `sh mvnw -B -ntp verify`: **438 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** (PostgreSQL/PostGIS +
  Redis + Elasticsearch + MinIO + Mailpit test infrastructure; ArchUnit, the OpenAPI snapshot diff and Spotless's
  `check` goal all run as part of `verify`, not as separate opt-in steps).
- Frontend: `lint` 0 warnings, `tsc -b` 0 errors, `format:check` clean, `check:api` (generated types vs. the OpenAPI
  snapshot) clean, Vitest **215/215** (31 files), `build` OK, `check:bundle` all routes within budget.

## 2. Scope done — requirement → evidence

| ID | Delivered | Evidence | Status |
|---|---|---|---|
| F22.5 | `ArchitectureTests` (ArchUnit): per module `domain` free of Spring/JPA/servlet/Jackson and of `api`/`application`/`infrastructure`; `application` free of `api`, `infrastructure`, servlet, `org.springframework.web`/`http`; `api` never depends on `infrastructure`; every `@Entity` and Spring Data `Repository` lives in `infrastructure`; a `@RestController` method never returns or accepts a JPA entity; `@RestController`s of a layered module live in its `api` package; no field injection (`GeneralCodingRules`); no module depends on another module's `infrastructure` or `api`; the `shared` kernel never depends on a business module. Every current violation is listed one pair at a time in `ArchitectureTests.ALLOWED` with the reason it is tolerated, plus `everyAllowanceStillMatchesARealDependency` — an allowance whose violation is gone fails the build until it is deleted, so the list can only shrink and a new violation can never hide behind an old one | `ArchitectureTests` (10 tests) | DONE |
| F22.5 (fixes, not allowances) | `catalog`/`transaction` output ports moved from `infrastructure.persistence.port` to `application.port.out` (they were application-layer contracts that happened to sit in the infrastructure package); application services (`engagement`, `iam`, `lead`, `cms`, `catalog`) use new `ApiException` factories (`unauthorized`/`gone`/`preconditionRequired`/`tooManyRequests`) instead of importing `org.springframework.http.HttpStatus` directly; CMS category labels moved onto the `ArticleCategory` domain enum so `seo.application` no longer imports `cms.api`; `GeocodingController` moved from the bare `search` package into `search.api`; `ProductionSafetyValidator`'s media-signing-secret field now comes through the constructor instead of `@Value` field injection | same ArchUnit run turning red→green before/after; `GeocodeCacheTests` still green after the controller move | DONE |
| F22.4 | OpenAPI is a versioned, diffed contract: `OpenApiSnapshotTests` renders `/v3/api-docs` through springdoc's own `OpenApiWebMvcResource` (the path is not publicly reachable — `SecurityConfig` denies unlisted routes — so the test calls springdoc directly rather than asserting a 401) and byte-for-byte diffs it, with stable key ordering, against the committed `backend/src/test/resources/openapi/openapi.json`; a failure prints exactly which operations and schemas changed; `-Dopenapi.snapshot.update=true` regenerates it. `springdoc.use-fqn=true` plus `OpenApiSchemaNames`/`SchemaRefRewriter` (rewriting `$ref`s on the live object graph, never through a JSON round trip — see gap below) give every schema a unique, stable, human name even though many DTOs across modules share a simple class name (`TeamMember`, `Freshness`, `Item`, …) | `OpenApiSnapshotTests` (2), `OpenApiSchemaNamesTests` (4) | DONE |
| F22.4 (frontend) | `npm run gen:api` runs `openapi-typescript` against the same snapshot into `app/shared/api/generated/openapi.ts` (excluded from lint/Prettier, never hand-edited); `npm run check:api` fails on drift and is a new CI step (`frontend-checks` job, after Prettier); `app/shared/api/schema.ts` is the entry point (`Schemas`, `Present<T>`); `app/shared/api/contract.ts` compile-time-checks ~55 hand-written response view types against their matching generated schema (a backend field rename now fails `tsc`, not a runtime `undefined`) | `npx tsc -b` includes `contract.ts`; `npm run check:api` | DONE, partial (see gap) |
| F22.2 | `RequestIdFilter` (highest precedence, before Spring Security) accepts a safe inbound `X-Request-Id` (validated against an allow-list pattern; header-injection attempts are replaced) or generates one, reuses a W3C `traceparent` trace id when present, echoes `X-Request-Id` on the response, and puts `requestId`/`traceId` into the logging MDC for the whole request (including async/error dispatch), clearing it afterwards. Console logs use Spring Boot structured (Logstash JSON) logging so every line is one JSON object with the MDC fields. `PiiMaskingLogCustomizer` passes every logged string (message, MDC values, stack trace) through `PiiLogMasker`, which redacts e-mail addresses, Vietnamese phone numbers, bearer tokens and `password=/token=/secret=` pairs, leaving ids/timestamps/amounts/status codes untouched. Nginx now sets `X-Request-Id` to its own `$request_id` on every proxied request | `StructuredLoggingTests` (captures real log output, asserts JSON shape + MDC + masking + no leaked PII across the whole captured output, and that the request id round-trips through a real controller and a `traceparent`), `PiiLogMaskerTests` (10 parameterised cases + a "leaves ids/amounts/status alone" case) | DONE |
| F22.3 | One Problem Details standard for every error path: `ProblemDetails.of` builds every body (`type` derived from `code`, generic `title`, Vietnamese `detail`, `instance` = request path, `code`, `traceId` = the request's correlation id, `errors[]` for field errors), always `application/problem+json`, never cached. Used by `GlobalExceptionHandler` (rewritten), the Spring Security entry point/access-denied handler, `RateLimitResponses` (429/413), the `search`/`engagement`/`analytics` module advices, and a new `ProblemErrorController` (`/error`) for failures that never reach a controller advice (a filter threw, the container rejected the request before dispatch). Framework 4xx keep their real status instead of falling through to 500: 400 (bind/constraint/method-validation/missing header or parameter/mistyped parameter/unreadable body), 405 (with `Allow`), 406, 413, 415, 404 (no handler and no resource, and every controller that used to return an empty body now throws a coded `ApiException`: `LISTING_NOT_FOUND`, `PROFILE_NOT_FOUND`, `PROJECT_NOT_FOUND`, `KYC_NOT_FOUND`, `MEDIA_NOT_FOUND`). The generic 500 handler never exposes the exception's message or type. Rejected values (a malformed UUID, an unparseable query parameter) are never echoed back into the response, since the input may itself be sensitive | `ProblemDetailsContractTests` (7, through the full stack: filters → Spring Security → MVC → advice, on real endpoints), `GlobalExceptionHandlerTests` (5, standalone MockMvc for sources no public endpoint triggers on demand: constraint violations, missing header/parameter, upload limit, `ResponseStatusException`, `ApiException` 410/429, an unexpected exception's message not leaking, the `/error` dispatch) | DONE |
| F22.1 | Backend: Spotless (`spotless-maven-plugin`, bound to `check` in the default `verify` lifecycle) — deliberately conservative steps (trailing whitespace, final newline, unused imports, tab width) rather than a full re-flow that would touch nearly every line of the tree; Flyway migrations are excluded (checksums cover every byte of an applied migration). Frontend: whole-tree `npx prettier --write .`, one file changed (`app/routes/_public.listings.new.tsx`, flagged unformatted in S2/S5B/S6/S7's reports and deliberately left untouched by each of them) | `sh mvnw verify` (Spotless `check` runs and passes), `npm run format:check` clean; both isolated in the single `style: whole-tree formatting (F22.1)` commit for easy review | DONE |
| F22.1 (CI) | `frontend-checks` gained an "API contract" step (`npm run check:api`) after Prettier; Spotless's `check` goal already runs inside the existing `sh mvnw --batch-mode verify` CI step, so no separate CI job was needed for the backend formatting gate | `.github/workflows/ci.yml` diff | DONE |

## 3. Contract deviations / design notes

- **springdoc schema names.** Before this stream, two DTOs with the same simple class name in different modules (e.g.
  two `TeamMember`s, several `Freshness`es) silently collapsed into one springdoc schema, which would have made the
  generated frontend types wrong for one of them without any error. `springdoc.use-fqn=true` plus
  `OpenApiSchemaNames`/`SchemaRefRewriter` fix this at the source instead of only detecting it: schemas are named by
  fully-qualified class first, then shortened back to the simple name when unique, else
  `<Module><Simple>`/`<Module><Outer><Simple>` (e.g. `LeadTeamMember`), with a final deterministic tie-break for the
  rare case where even that still collides (a generic wrapper's mangled springdoc name vs. the plain type). This is
  additive tooling around springdoc, not a backend API contract change — the wire format (paths, response bodies) is
  unchanged, only the internal `#/components/schemas/*` names.
- **`$ref` rewriting on the live model, not through JSON.** The first implementation serialised the `OpenAPI` object
  to a JSON string, string-replaced `$ref`s, and deserialised back into `OpenAPI`. That silently corrupted every enum
  query parameter (`type: string` became the base `Schema` class's default `type: object` on the round trip) because
  swagger-core's `Schema` deserializer cannot always recover which subtype a plain-JSON schema was. `SchemaRefRewriter`
  instead walks the live object graph (components, path parameters, request bodies, responses, headers, callbacks,
  and every nested `properties`/`items`/`additionalProperties`/`allOf`/`oneOf`/`anyOf`/`not`) and only ever calls
  `schema.set$ref(...)` on schemas that already had one, so nothing else is touched.
- **`ProblemDetails.errors` never echoes the rejected value.** A mistyped path variable, an invalid enum, a malformed
  UUID all answer with the field name and a generic message, not the value the client sent — audit F22.3 pairs with
  PROJECT_CODE_RULES §12 (never log or return PII), and the value itself might be anything the client typed, including
  PII.
- **`AnalyticsLayeringTests`** (S0-BE's stopgap ArchUnit rule for the analytics module, superseded by `ArchitectureTests`)
  was left in place rather than deleted: it still passes and is redundant, not wrong, and removing another stream's
  test file wasn't asked for in this stream's scope. `ArchitectureTests` covers the same and every other module.

## 4. Known gaps (honest)

1. **`ArchitectureTests.everyOperationIsDocumentedAndErrorsUseProblemDetails`** in `OpenApiSnapshotTests` only checks
   operation ids are unique and that `ProblemDetails` exists as a schema; it does not check that *every* operation's
   error responses are documented as `ProblemDetails` in the OpenAPI document itself (springdoc does not reliably
   document exception-handler response schemas per-operation without per-controller `@ApiResponse` annotations across
   ~150 endpoints, which was out of scope for this stream). The runtime guarantee (every actual error response is
   Problem Details) is covered by `ProblemDetailsContractTests`/`GlobalExceptionHandlerTests`; the OpenAPI *document*
   itself is not fully annotated with error response schemas.
2. **`app/shared/api/contract.ts` covers ~55 of the ~90 hand-written response view types**, not all of them. Four are
   deliberately left out (documented in the file's header) because the automatic name-based match picked the wrong
   backend schema of the same simple name (`KycDocumentAccess`, `LeadItem`, `TeamMember`, `ListingLocation` — the FE
   type is either composed client-side from more than one endpoint, or a same-named-but-unrelated backend DTO exists).
   A handful of other hand-written types (`Money`/`RentTerms`/`UnitPrice` in `shared/format/money.ts`, `InquiryItem`)
   no longer have an exact-name match at all once schema names were disambiguated by module, so they are not checked
   either. The file is maintained by hand (documented in its header) rather than regenerated by a script, so adding a
   new checked type is a manual, reviewable addition — same effort as any other contract test.
3. **Backend formatting is conservative on purpose.** Spotless is configured for whitespace/imports only, not a full
   re-flow (google-java-format/Palantir), so the codebase's existing brace/line-length style (many long lines noted
   in the audit) is untouched. A full re-flow is a larger, separate decision (it would touch nearly every backend file
   and make this stream's diff unreviewable) — F22.1's "tách file dài" (split long files) part of the requirement is
   therefore only partially addressed: the *tooling* to keep new code formatted is in place; the *pass of splitting
   existing long/dense controllers and services* was not attempted in this stream (out of the P2 budget and orthogonal
   to correctness; several already-split examples exist from earlier streams, e.g. S3a's `GlobalExceptionHandler`
   handlers).
4. **No dedicated ArchUnit rule enforces "one output port per interface, one adapter"** or checks for God classes/line
   counts; F22.5's boundary rules are structural (package dependency direction), not a complexity/size gate.
5. Flyway V100–V104 were reserved for this stream but unused — no schema change was needed for any F22 item.

## 5. Manual / production steps

None. No environment variables, secrets or migrations were added. `logging.structured.format.console: logstash` and
`logging.structured.json.customizer` are `application.yml` defaults, not env-gated; `APP_LOG_LEVEL` is unchanged.
Nginx's `proxy-backend.conf` gained one line (`X-Request-Id $request_id`) that takes effect on the next Nginx
container build — no other operator action needed.

## 6. Follow-ups for other streams

- **S10-PERF / S11-UX:** the OpenAPI snapshot (`backend/src/test/resources/openapi/openapi.json`) and generated
  frontend types (`frontend/app/shared/api/generated/openapi.ts`) must be regenerated
  (`sh mvnw -Dtest=OpenApiSnapshotTests -Dopenapi.snapshot.update=true test && cd ../frontend && npm run gen:api`) in
  the same commit as any endpoint/DTO change, and the diff reviewed — this is now enforced by `verify` and
  `check:api`/CI, so a merge that skips it simply fails.
- **Whoever merges into `audit-2026-09-27`:** the whole-tree formatting commit (`style: whole-tree formatting
  (F22.1)`) touches six backend files (all one-line whitespace) and one frontend file
  (`app/routes/_public.listings.new.tsx`); it is a separate commit from the substantive S9 changes precisely so it can
  be reviewed (or, if truly noisy, skipped/dropped) independently.
- **Any stream adding a new module or a new `application`/`domain`/`api`/`infrastructure` package:** `ArchitectureTests`
  now enforces the boundary automatically; a genuinely necessary cross-module or cross-layer dependency needs an
  entry in `ArchitectureTests.ALLOWED` with a one-line reason, reviewed like any other exception to the architecture.
- **Any stream adding a hand-written response view type that mirrors a backend DTO 1:1:** consider adding a row to
  `frontend/app/shared/api/contract.ts` (see its header comment) so a future backend rename is caught at compile time.
