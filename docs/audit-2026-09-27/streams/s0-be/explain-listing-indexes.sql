-- Reproduces the contract §2.7 index decision of stream S0-BE (see ../s0-be.md) on synthetic data.
-- Run it on a scratch database migrated to V029 of the shared test server, never on a real database:
--   docker exec bds-test-postgres-1 psql -U bds_test -d bds_test_admin -c "CREATE DATABASE s0be_explain"
--   cat backend/src/main/resources/db/migration/V0*.sql | docker exec -i bds-test-postgres-1 psql -q -U bds_test -d s0be_explain
--   docker exec -i bds-test-postgres-1 psql -U bds_test -d s0be_explain \
--     < docs/audit-2026-09-27/streams/s0-be/explain-listing-indexes.sql > docs/audit-2026-09-27/streams/s0-be/explain-listing-indexes.out.txt
--   docker exec bds-test-postgres-1 psql -U bds_test -d bds_test_admin -c "DROP DATABASE s0be_explain WITH (FORCE)"
-- Ids and timestamps are derived from the row number, so the data set is identical on every run (plans and buffer counts
-- are stable; execution times depend on the machine).
\set ON_ERROR_STOP on
\pset pager off

-- 2,000 owners with about 100 listings each (200,000 listings, 60 % ACTIVE) plus one owner with 12,000 listings.
INSERT INTO users(id, phone_lookup_hash, phone_encrypted, full_name, email, status)
SELECT md5('owner-' || g)::uuid, md5('phone-' || g) || md5('phone2-' || g), 'x', 'Owner ' || g, 'owner' || g || '@example.test', 'ACTIVE'
FROM generate_series(1, 2001) g;

INSERT INTO listings(id, owner_id, status, slug, created_at, updated_at)
SELECT md5('listing-' || g)::uuid, md5('owner-' || (1 + g % 2000))::uuid,
       CASE WHEN g % 20 < 12 THEN 'ACTIVE' WHEN g % 20 < 14 THEN 'DRAFT' WHEN g % 20 < 16 THEN 'PENDING_REVIEW'
            WHEN g % 20 < 18 THEN 'EXPIRED' WHEN g % 20 = 18 THEN 'PAUSED' ELSE 'REJECTED' END,
       'explain-' || g,
       TIMESTAMPTZ '2026-09-01 00:00:00+00' - (g % 730) * INTERVAL '1 day' - (g % 1440) * INTERVAL '1 minute',
       TIMESTAMPTZ '2026-09-01 00:00:00+00' - (g % 365) * INTERVAL '1 day'
FROM generate_series(1, 200000) g;

INSERT INTO listings(id, owner_id, status, slug, created_at, updated_at)
SELECT md5('big-listing-' || g)::uuid, md5('owner-2001')::uuid,
       CASE WHEN g % 10 < 7 THEN 'ACTIVE' WHEN g % 10 < 8 THEN 'DRAFT' ELSE 'EXPIRED' END,
       'explain-big-' || g,
       TIMESTAMPTZ '2026-09-01 00:00:00+00' - (g % 900) * INTERVAL '1 day' - (g % 1440) * INTERVAL '1 minute',
       TIMESTAMPTZ '2026-09-01 00:00:00+00'
FROM generate_series(1, 12000) g;

SELECT status, count(*) FROM listings WHERE slug LIKE 'explain-%' GROUP BY status ORDER BY 1;

-- ---------------------------------------------------------------------------------------------------------- BEFORE
DROP INDEX IF EXISTS idx_listings_owner_status_created;
DROP INDEX IF EXISTS idx_listings_active_created;
ANALYZE users;
ANALYZE listings;
\echo '### BEFORE: only the V001 indexes idx_listings_owner (owner_id) and idx_listings_status (status)'
\echo '--- C. public ACTIVE feed, page 1'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY ON)
SELECT id FROM listings WHERE status = 'ACTIVE' ORDER BY created_at DESC, id DESC LIMIT 24;
\echo '--- D. public ACTIVE feed, offset 2400'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY ON)
SELECT id FROM listings WHERE status = 'ACTIVE' ORDER BY created_at DESC, id DESC LIMIT 24 OFFSET 2400;
\echo '--- B. owner with ~100 listings, ACTIVE, newest 60 (public profile)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY ON)
SELECT id FROM listings WHERE owner_id = md5('owner-42')::uuid AND status = 'ACTIVE' ORDER BY created_at DESC, id DESC LIMIT 60;
\echo '--- B-big. owner with 12,000 listings, ACTIVE, newest 60'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY ON)
SELECT id FROM listings WHERE owner_id = md5('owner-2001')::uuid AND status = 'ACTIVE' ORDER BY created_at DESC, id DESC LIMIT 60;
\echo '--- A-big. owner with 12,000 listings, all statuses, newest 20 (my-listings without a filter)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY ON)
SELECT id FROM listings WHERE owner_id = md5('owner-2001')::uuid ORDER BY created_at DESC, id DESC LIMIT 20;

-- ----------------------------------------------------------------------------------------------------------- AFTER
CREATE INDEX idx_listings_owner_status_created ON listings (owner_id, status, created_at DESC, id DESC);
CREATE INDEX idx_listings_active_created ON listings (created_at DESC, id DESC) WHERE status = 'ACTIVE';
ANALYZE listings;
\echo '### AFTER: V027 section 2.7 indexes'
\echo '--- C. public ACTIVE feed, page 1'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY ON)
SELECT id FROM listings WHERE status = 'ACTIVE' ORDER BY created_at DESC, id DESC LIMIT 24;
\echo '--- D. public ACTIVE feed, offset 2400'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY ON)
SELECT id FROM listings WHERE status = 'ACTIVE' ORDER BY created_at DESC, id DESC LIMIT 24 OFFSET 2400;
\echo '--- B. owner with ~100 listings, ACTIVE, newest 60 (public profile)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY ON)
SELECT id FROM listings WHERE owner_id = md5('owner-42')::uuid AND status = 'ACTIVE' ORDER BY created_at DESC, id DESC LIMIT 60;
\echo '--- B-big. owner with 12,000 listings, ACTIVE, newest 60'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY ON)
SELECT id FROM listings WHERE owner_id = md5('owner-2001')::uuid AND status = 'ACTIVE' ORDER BY created_at DESC, id DESC LIMIT 60;
\echo '--- A-big. owner with 12,000 listings, all statuses, newest 20 (my-listings without a filter)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF, SUMMARY ON)
SELECT id FROM listings WHERE owner_id = md5('owner-2001')::uuid ORDER BY created_at DESC, id DESC LIMIT 20;
SELECT pg_size_pretty(pg_relation_size('idx_listings_owner_status_created')) AS owner_status_index,
       pg_size_pretty(pg_relation_size('idx_listings_active_created')) AS active_feed_index,
       pg_size_pretty(pg_relation_size('listings')) AS listings_heap;
