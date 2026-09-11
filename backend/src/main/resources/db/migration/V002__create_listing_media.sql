-- ==============================================================================
-- Flyway Migration V002: Bổ sung bảng hình ảnh tin đăng và hỗ trợ Optimistic Locking
-- ==============================================================================

-- 1. Bổ sung cột version cho bảng listings để hỗ trợ Optimistic Locking
ALTER TABLE listings ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 2. Bảng hình ảnh đính kèm theo từng revision của tin đăng
CREATE TABLE IF NOT EXISTS listing_media (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    revision_id UUID NOT NULL REFERENCES listing_revisions(id) ON DELETE CASCADE,
    media_url TEXT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_listing_media_revision ON listing_media(revision_id);
