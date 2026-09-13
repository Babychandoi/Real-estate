CREATE TABLE media_objects (
    object_key VARCHAR(80) PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    content_type VARCHAR(40) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_media_objects_owner_created ON media_objects(owner_id, created_at DESC);
