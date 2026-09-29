\set ON_ERROR_STOP on
-- S10: run only in a disposable bds_perf_* database. All generated rows live in a TEMP table.
BEGIN;
DO $$
BEGIN
    IF current_database() !~ '^bds_perf_' THEN
        RAISE EXCEPTION 'S10 requires an isolated bds_perf_* database, got %', current_database();
    END IF;
END
$$;
SET LOCAL statement_timeout = '30min';
SET LOCAL work_mem = '64MB';

CREATE TEMP TABLE perf_lpr
    (LIKE listing_public_read INCLUDING DEFAULTS INCLUDING CONSTRAINTS)
    ON COMMIT DROP;

INSERT INTO perf_lpr
    (listing_id, slug, owner_id, public_revision_id, revision_number, title, purpose,
     property_type, price_vnd, area_m2, public_location, seller_role, identity_status,
     ownership_status, published_at, updated_at, search_text, search_tsv, row_version)
SELECT md5('listing:' || n)::uuid,
       'perf-' || n,
       md5('owner:' || n % 5000)::uuid,
       md5('revision:' || n)::uuid,
       1,
       CASE WHEN n % 10 = 0 THEN 'Căn hộ kiểm thử ' ELSE 'Nhà phố kiểm thử ' END || n,
       CASE WHEN n % 4 = 0 THEN 'RENT' ELSE 'SALE' END,
       CASE WHEN n % 10 = 0 THEN 'APARTMENT' ELSE 'HOUSE' END,
       (1000000000 + (n % 500) * 10000000)::bigint,
       (35 + n % 200)::numeric,
       ST_SetSRID(ST_MakePoint(105.7 + (n % 1000) / 10000.0, 20.8 + (n % 700) / 10000.0), 4326),
       'BROKER', 'NOT_SUBMITTED', 'NOT_SUBMITTED',
       TIMESTAMPTZ '2026-09-01 03:00:00+00' - (n % 365) * interval '1 day' - (n % 24) * interval '1 hour',
       TIMESTAMPTZ '2026-09-01 03:00:00+00' - (n % 365) * interval '1 day',
       CASE WHEN n % 10 = 0 THEN 'can ho cau giay' ELSE 'nha pho ha noi' END,
       to_tsvector('simple', CASE WHEN n % 10 = 0 THEN 'can ho cau giay' ELSE 'nha pho ha noi' END),
       n
FROM generate_series(1, :dataset_size) AS n;

-- Same leading columns and index types as V033, created after loading to keep setup bounded.
CREATE INDEX perf_lpr_newest ON perf_lpr (purpose, published_at DESC, listing_id DESC);
CREATE INDEX perf_lpr_price ON perf_lpr (purpose, price_vnd, listing_id);
CREATE INDEX perf_lpr_location ON perf_lpr USING GIST (public_location);
CREATE INDEX perf_lpr_search_tsv ON perf_lpr USING GIN (search_tsv);
ANALYZE perf_lpr;
\echo Dataset size (must equal the requested 100000 or 1000000)
SELECT count(*) AS generated_rows FROM perf_lpr;

\echo Newest public SALE page (keyset order)
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT listing_id FROM perf_lpr
WHERE purpose = 'SALE'
ORDER BY published_at DESC, listing_id DESC LIMIT 24;

\echo SALE price range, ascending keyset order
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT listing_id FROM perf_lpr
WHERE purpose = 'SALE' AND price_vnd BETWEEN 1500000000 AND 1900000000
ORDER BY price_vnd, listing_id LIMIT 24;

\echo Map bbox with the PostGIS GiST index
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT listing_id FROM perf_lpr
WHERE public_location && ST_MakeEnvelope(105.72, 20.82, 105.74, 20.84, 4326)
LIMIT 200;

\echo Keyword with the search_tsv GIN index
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT listing_id FROM perf_lpr
WHERE search_tsv @@ plainto_tsquery('simple', 'can ho')
LIMIT 24;

ROLLBACK;
