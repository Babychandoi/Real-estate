CREATE EXTENSION IF NOT EXISTS unaccent;

ALTER TABLE listings ADD COLUMN IF NOT EXISTS slug VARCHAR(180);

WITH candidates AS (
    SELECT l.id,
           LEFT(TRIM(BOTH '-' FROM REGEXP_REPLACE(
               UNACCENT(LOWER(REPLACE(r.title, 'đ', 'd'))), '[^a-z0-9]+', '-', 'g'
           )), 160) AS base_slug
    FROM listings l
    JOIN listing_revisions r ON r.id = COALESCE(l.public_revision_id, (
        SELECT r2.id FROM listing_revisions r2 WHERE r2.listing_id = l.id
        ORDER BY r2.revision_number DESC LIMIT 1
    ))
), ranked AS (
    SELECT id, COALESCE(NULLIF(base_slug, ''), 'bat-dong-san') AS base_slug,
           ROW_NUMBER() OVER (PARTITION BY base_slug ORDER BY id) AS duplicate_number
    FROM candidates
)
UPDATE listings l SET slug = CASE WHEN ranked.duplicate_number = 1 THEN ranked.base_slug
    ELSE ranked.base_slug || '-' || ranked.duplicate_number END
FROM ranked WHERE ranked.id = l.id AND l.slug IS NULL;

ALTER TABLE listings ALTER COLUMN slug SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_listings_slug ON listings(slug);
