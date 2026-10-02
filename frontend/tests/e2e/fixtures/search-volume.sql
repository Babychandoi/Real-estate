-- R-2 acceptance fixture (search-consistency.spec.ts): the UAT seed has 46 public SALE listings, fewer than the
-- 100 that the old search capped at. This adds 3 copies of every ACTIVE listing of the synthetic UAT sellers
-- (owners @example.invalid; never the demo.* accounts other specs count), so SALE has 142 public results and RENT 44.
-- Copies get their own id, slug ("-vol-N"), title suffix and price, and are dated 60+ days before their original,
-- so the newest-first pages that the visual baselines show keep the seeded listings. The listing_public_read rows
-- are built by the existing triggers (V033) when public_revision_id is set. Idempotent: a second run adds nothing.
BEGIN;
-- Copies every column except generated ones (price_period, search vectors …), whatever the schema version adds.
CREATE FUNCTION pg_temp.vol_copy(target text, source text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE cols text;
BEGIN
  SELECT string_agg(quote_ident(column_name), ',' ORDER BY ordinal_position) INTO cols
  FROM information_schema.columns
  WHERE table_schema = 'public' AND table_name = target AND is_generated = 'NEVER';
  EXECUTE format('INSERT INTO %I (%s) SELECT %s FROM %I', target, cols, cols, source);
END $$;
CREATE TEMP TABLE vol_map ON COMMIT DROP AS
SELECT l.id AS old_id, l.public_revision_id AS old_rev, gen_random_uuid() AS new_id, gen_random_uuid() AS new_rev,
       g AS copy
FROM listings l
JOIN users u ON u.id = l.owner_id
CROSS JOIN generate_series(1, 3) g
WHERE l.status = 'ACTIVE' AND l.public_revision_id IS NOT NULL AND u.email LIKE '%@example.invalid'
  AND l.slug NOT LIKE '%-vol-%'
  AND NOT EXISTS (SELECT 1 FROM listings v WHERE v.slug LIKE '%-vol-%');

CREATE TEMP TABLE vol_l ON COMMIT DROP AS
SELECT m.new_id AS m_new, m.copy AS m_copy, l.* FROM listings l JOIN vol_map m ON m.old_id = l.id;
UPDATE vol_l SET id = m_new, slug = left(slug, 170) || '-vol-' || m_copy, public_revision_id = NULL, version = 0,
    created_at = created_at - make_interval(days => 60 + m_copy),
    updated_at = updated_at - make_interval(days => 60 + m_copy),
    availability_confirmed_at = availability_confirmed_at - make_interval(days => 60 + m_copy);
ALTER TABLE vol_l DROP COLUMN m_new, DROP COLUMN m_copy;
SELECT pg_temp.vol_copy('listings', 'vol_l');

CREATE TEMP TABLE vol_r ON COMMIT DROP AS
SELECT m.new_rev AS m_rev, m.new_id AS m_listing, m.copy AS m_copy, r.*
FROM listing_revisions r JOIN vol_map m ON m.old_rev = r.id;
UPDATE vol_r SET id = m_rev, listing_id = m_listing, revision_number = 1,
    title = left(title, 230) || ' (mẫu ' || m_copy || ')',
    price_vnd = price_vnd + m_copy * CASE WHEN purpose = 'RENT' THEN 100000 ELSE 10000000 END,
    created_at = created_at - make_interval(days => 60 + m_copy),
    submitted_at = submitted_at - make_interval(days => 60 + m_copy),
    moderated_at = moderated_at - make_interval(days => 60 + m_copy);
ALTER TABLE vol_r DROP COLUMN m_rev, DROP COLUMN m_listing, DROP COLUMN m_copy;
SELECT pg_temp.vol_copy('listing_revisions', 'vol_r');

UPDATE listings l SET public_revision_id = m.new_rev FROM vol_map m WHERE l.id = m.new_id;
COMMIT;
