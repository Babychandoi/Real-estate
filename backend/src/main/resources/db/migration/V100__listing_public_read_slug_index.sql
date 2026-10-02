-- W6-PERF (F09.3/D-05): every public detail request resolves its slug on the read model
-- (JdbcListingReadModelAdapter.findVersion: `SELECT ... FROM listing_public_read WHERE slug = ?`). V033 indexed
-- listing_public_read by listing_id, purpose orders, owner, location and search_tsv, but not by slug, so that lookup
-- was a sequential scan of the whole read model on each detail page (see docs/audit-2026-09-27/streams/w6-perf.md for
-- the EXPLAIN before/after at 100k and 1M listings).
-- Not UNIQUE: slugs are unique in listings (uq_listings_slug); the read-model copy is refreshed row by row, so a
-- uniqueness check here could only add a failure mode to the refresh without protecting anything.
-- Plain CREATE INDEX (Flyway runs this file in a transaction). On a large production table build it beforehand with
-- CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lpr_slug ON listing_public_read (slug); this statement then does nothing.
SET LOCAL lock_timeout = '5s';

CREATE INDEX IF NOT EXISTS idx_lpr_slug ON listing_public_read (slug);
