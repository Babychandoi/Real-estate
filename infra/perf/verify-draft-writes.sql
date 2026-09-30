\set ON_ERROR_STOP on
-- psql -v run_id=lowercasemarker -v expected_count=<drafts_created from k6 summary> -f ...
BEGIN READ ONLY;
SELECT current_database() LIKE 'bds_perf_%' AS isolated \gset
\if :isolated
\else
  \echo Refusing a database outside bds_perf_*
  \quit 2
\endif
SELECT set_config('perf.run_id', :'run_id', true), set_config('perf.expected_count', :'expected_count', true);
DO $$
DECLARE actual bigint; expected bigint;
BEGIN
  IF current_setting('perf.run_id') !~ '^[a-z]{4,32}$' THEN RAISE EXCEPTION 'Invalid run marker'; END IF;
  expected := current_setting('perf.expected_count')::bigint;
  IF expected <= 0 THEN RAISE EXCEPTION 'Expected a positive successful-write count'; END IF;
  SELECT count(DISTINCT l.id) INTO actual FROM listings l JOIN listing_revisions r ON r.listing_id = l.id
  WHERE r.title LIKE 'Perf draft ' || current_setting('perf.run_id') || ' %' AND l.status = 'DRAFT' AND r.status = 'DRAFT';
  IF actual <> expected THEN RAISE EXCEPTION 'Draft effect mismatch: actual %, expected %', actual, expected; END IF;
  RAISE NOTICE 'Verified % persisted drafts', actual;
END $$;
ROLLBACK;
