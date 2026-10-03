-- W6-PERF (cold restart, O5): pg_prewarm lets the backend's start-up warm-up (SearchWarmup) load the read model's indexes
-- into shared buffers and its heap into the OS page cache before the instance reports ready. The extension ships with
-- PostgreSQL's contrib package (present in postgis/postgis); where it cannot be created the warm-up simply skips the
-- database step, so a failure here must not stop the migration.
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_prewarm;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pg_prewarm not available (%), the start-up warm-up skips the database step', SQLERRM;
END $$;
