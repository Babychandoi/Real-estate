# Brief S2-SEARCH (W2) — search, listing read side, detail/compare/seller UI

Branch `audit/s2-search`; Flyway V033–V044; backend port 18113; Vite 5313; ES index prefix `s2`.
Parallel in W2: S3a-SUPPLY (owns listing JPA entity/domain/mapper, `/api/v1/listings/*` write endpoints, wizard,
my-listings) and S4-ADMIN (moderation, admin pages, trust decisions, billing, assets/dedupe).

Read first: `00_PLAN.md`, `01_REQUIREMENTS.md`, `02_CONTRACTS.md` (§4 money, §6 trust, §7 filter schema, §8 API v2,
§9 read model/index pipeline, §10 images, §12 frontend, §13 tests), `03_AGENT_RULES.md`, `briefs/wave-rules.md`.
Audit sections: F02, F03, F04 (search/display), F05, F06, F07 (public), F08.5, F09, F10 (listing caches + geocode),
F14.1/F14.3 (frontend), F15.1, §7.1–7.4, §8.3–8.4, §5 rows `/search`, `/listings/:id`, `/nguoi-dang/:id`, `/compare`.

Requirement IDs: F02.1–F02.4, F03.1–F03.4, F04.2, F04.3, F05.2, F05.3, F05.4 (metrics), F05.5 (lag test), F06.1–F06.4,
F07.1, F07.2 (public), F07.4, F08.5, F09.1, F09.2, F10.1, F10.3, F10.4, F14.1, F14.3 (frontend), F15.1, UI-02–UI-05,
P-01, P-04 (trust display), P-05 (price history), D-01–D-08, D-11, DS-09, DS-10, R-2, R-3 (public).

## Backend (first, commit in steps)
1. `listing_public_read` read model + `bds_refresh_listing_public_read(uuid)` + triggers (contract §9) enqueueing
   `search-index` via `bds_enqueue_job`. Location dictionary for the district codes used by listings (Hà Nội: names,
   unaccented, aliases) enriching `search_text`/`search_tsv` and giving `districtName`. Vietnam removed the district
   level in July 2025 but listings carry pre-2025 codes users still search by — keep them as search areas, document it.
   `priceChange` from the previous APPROVED revision; `GET /api/v2/listings/{id}/price-history`.
2. Replace the full-scan `ElasticsearchListingIndex.sync` with the batch `search-index` job handler: refresh rows, ES
   `_bulk` index/delete with `version_type=external`, `version=row_version`; explicit mapping with a Vietnamese folding
   analyzer; alias `bds-listings` → versioned index; migrate the existing concrete `bds-listings` index on first start;
   rebuild with dual-write (`search_index_state`) + keyset backfill + atomic alias swap + rollback; ADMIN endpoint to
   start/inspect rebuild; metrics (lag, bulk failures); daily locked task re-enqueueing listings whose trust expired.
   ES hits re-checked against PostgreSQL.
3. Search API v2 (contract §7–§8): strict validation (400 `INVALID_FILTER`), sort tuples, signed cursor
   (`SEARCH_CURSOR_SECRET`, required by ProductionSafetyValidator), ES `search_after`, DB engine on the read model with
   dynamic predicates, keyset, GiST bbox, FTS with the same normalization, totals (first page, cap 10 000, eq|gte),
   circuit breaker (800 ms budget, metrics), `engine/degraded/notices`, cursor bound to engine (409). Map endpoint.
   Detail v2 (404 vs 410, ETag/304, images via `PublicImageResolver`). Seller listings v2 (cursor, no 60 cap) + seller
   profile v2 (role, identity separate from listing ownership, response stats only when ≥5 samples). Similar listings
   and zero-result suggestions. v1 search = deprecated wrapper.
4. Caches (audit §7.3): detail in Redis keyed by listing + row_version, ETag, stampede lock, TTL jitter; first-page
   search cache only without bbox; Redis failure → bypass; geocode cache expiry 14 d, normalized key, negative 1 h.
5. Tests (PostgreSQL + ES prefix `s2`): >250 listings paged per sort on both engines without dup/miss; ES–DB parity
   (diacritics, aliases, ties, hidden, invalid, deep); cursor tamper/expiry/engine change; ES down → DB degraded;
   update/hide visible p95 ≤10 s via the worker (measure); replay never lowers row_version; rebuild during changes loses
   nothing; query count ≤4 for a 24-card page (1 or 10 revisions) and detail; no draft/private fields publicly; cache
   invalidation on approve/hide/price change; geocode expiry.

## Frontend
6. `app/features/search/filterSchema.ts` (parse/serialize/validate/canonical) + Vitest.
7. `/search` rewrite: server filters (purpose, type, purpose/type-aware price presets + custom, area, beds, legal,
   furnishing, verified scope, district, keyword/place with geocode → bbox+place), FilterBar desktop + Sheet mobile,
   URL as source of truth (push vs replace), "Xem thêm" with cursor + total label, zero-result suggestions, degraded
   notice, cursor-engine recovery, split view ≥1024 px with card↔pin highlight, mobile map toggle + marker sheet, map via
   dynamic `import()` only when shown, map endpoint clusters/points, scroll restoration from detail.
8. `ListingCard` v2 (ResponsiveImage, Money with period, area/beds, location, freshness, seller link, TrustBadges,
   compare/favorite as siblings of a stretched title link, inactive variant).
9. Detail: full gallery + lightbox (keyboard, counter, focus trap/return, alt), price + rent terms, facts, trust panel
   with scopes/dates, price history, contact panel states around the existing lead modal, sticky mobile CTA not covering
   content, similar listings, 410 page, `useDocumentMeta` (canonical slug, OG, JSON-LD); `track()` for
   listing_detail_viewed / search_performed / search_results_viewed.
10. Compare on v2 detail (rent units, differences-only, inactive column, refresh error); seller page paged, identity vs
    listing verification separated, role label.
11. E2E chromium via `scripts/e2e-local.sh`: URL roundtrip/back-forward, load more beyond page 1, rent "/tháng", list
    mode does not fetch the map chunk, gallery with many images, compare inactive item, seller paging. Bundle budget for
    `/search` list mode ≤130 kB gzip initial JS.

Deliverables per `03_AGENT_RULES.md`: commits, `streams/s2-search.md` (requirement → evidence incl. measured lag p95,
query counts, parity, bundle numbers), deviations, gaps, production notes (`SEARCH_CURSOR_SECRET`, index migration,
rebuild procedure), final message.
