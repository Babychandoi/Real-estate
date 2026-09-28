-- S3a-SUPPLY: CSV import of listing drafts (P-08). One committed batch per owner and file content (idempotent).
CREATE TABLE IF NOT EXISTS listing_import_batches (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_sha256 CHAR(64) NOT NULL,
    row_count INTEGER NOT NULL CHECK (row_count >= 0),
    created_count INTEGER NOT NULL CHECK (created_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_listing_import_batches_file UNIQUE (owner_id, file_sha256)
);

ALTER TABLE listings ADD COLUMN IF NOT EXISTS import_batch_id UUID REFERENCES listing_import_batches (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_listings_import_batch ON listings (import_batch_id) WHERE import_batch_id IS NOT NULL;
