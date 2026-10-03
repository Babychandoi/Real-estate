-- W6-PERF (F09.3/D-05): every public detail request resolves its slug on the read model
-- (JdbcListingReadModelAdapter.findVersion: `SELECT ... FROM listing_public_read WHERE slug = ?`). V033 indexed
-- listing_public_read by listing_id, purpose orders, owner, location and search_tsv, but not by slug, so that lookup
-- was a sequential scan of the whole read model on each detail page (see docs/audit-2026-09-27/streams/w6-perf.md for
-- the EXPLAIN before/after at 100k and 1M listings).
-- Not UNIQUE: slugs are unique in listings (uq_listings_slug); the read-model copy is refreshed row by row, so a
-- uniqueness check here could only add a failure mode to the refresh without protecting anything.
--
-- Locking: plain CREATE INDEX (Flyway runs this file in a transaction) holds a SHARE lock on listing_public_read for the
-- whole build, which blocks read-model refreshes (every listing write that touches the read model waits). Measured on
-- the CI runner: about 5.5-8 s for 1M rows. On a large production table build the index beforehand, outside Flyway:
--     CREATE INDEX CONCURRENTLY idx_lpr_slug ON listing_public_read (slug);
-- and check that it is valid before deploying (a failed CONCURRENTLY build leaves an INVALID index behind, which the
-- IF NOT EXISTS below would then silently keep):
--     SELECT indisvalid FROM pg_index WHERE indexrelid = 'idx_lpr_slug'::regclass;   -- must be true
-- If it is false, DROP INDEX CONCURRENTLY idx_lpr_slug and build it again. This statement then does nothing.
SET LOCAL lock_timeout = '5s';

CREATE INDEX IF NOT EXISTS idx_lpr_slug ON listing_public_read (slug);
