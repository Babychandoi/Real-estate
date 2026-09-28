-- Stream S1-MEDIA (contract §10): image pipeline state, WebP variants and placeholders.
-- Contract range V030–V032 cannot be used: Flyway runs in order and V061 is already applied (see streams/s1-media.md).
-- Additive only. Existing rows become LEGACY (served as before until the backfill processes them).
-- Rollback = redeploy the previous image: it ignores the new columns/table; new uploads it makes are LEGACY by default.

SET LOCAL lock_timeout = '5s';

ALTER TABLE media_objects
    ADD COLUMN IF NOT EXISTS processing_state VARCHAR(16) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS width INT,
    ADD COLUMN IF NOT EXISTS height INT,
    ADD COLUMN IF NOT EXISTS dominant_color VARCHAR(7),
    ADD COLUMN IF NOT EXISTS lqip VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS processing_error VARCHAR(300);

ALTER TABLE media_objects DROP CONSTRAINT IF EXISTS chk_media_objects_processing_state;
ALTER TABLE media_objects ADD CONSTRAINT chk_media_objects_processing_state
    CHECK (processing_state IN ('LEGACY', 'PENDING', 'READY', 'FAILED'));
ALTER TABLE media_objects DROP CONSTRAINT IF EXISTS chk_media_objects_dimensions;
ALTER TABLE media_objects ADD CONSTRAINT chk_media_objects_dimensions
    CHECK ((width IS NULL AND height IS NULL) OR (width > 0 AND height > 0));
ALTER TABLE media_objects DROP CONSTRAINT IF EXISTS chk_media_objects_dominant_color;
ALTER TABLE media_objects ADD CONSTRAINT chk_media_objects_dominant_color
    CHECK (dominant_color IS NULL OR dominant_color ~ '^#[0-9a-f]{6}$');

-- Backfill scans LEGACY public objects in creation order.
CREATE INDEX IF NOT EXISTS idx_media_objects_legacy_created
    ON media_objects (created_at, object_key) WHERE processing_state = 'LEGACY';

CREATE TABLE IF NOT EXISTS media_variants (
    variant_key VARCHAR(100) PRIMARY KEY,
    object_key  VARCHAR(80)  NOT NULL REFERENCES media_objects (object_key) ON DELETE CASCADE,
    width       INT          NOT NULL CHECK (width > 0),
    height      INT          NOT NULL CHECK (height > 0),
    format      VARCHAR(10)  NOT NULL CHECK (format IN ('webp')),
    size_bytes  BIGINT       NOT NULL CHECK (size_bytes > 0),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_media_variants_object_width UNIQUE (object_key, format, width)
);

COMMENT ON TABLE media_variants IS
    'Resized, metadata-free WebP renditions of media_objects (S1 pipeline). Served under /api/v1/public/media/<variant_key> with the same visibility as the parent.';

-- Visibility checks and the delete/orphan checks look listing media up by URL.
CREATE INDEX IF NOT EXISTS idx_listing_media_url ON listing_media (media_url);
CREATE INDEX IF NOT EXISTS idx_users_avatar_media_url ON users (avatar_media_url) WHERE avatar_media_url IS NOT NULL;
