ALTER TABLE listing_revisions ADD COLUMN IF NOT EXISTS bedrooms INTEGER;
ALTER TABLE listing_revisions ADD COLUMN IF NOT EXISTS bathrooms INTEGER;
ALTER TABLE listing_revisions ADD COLUMN IF NOT EXISTS floors INTEGER;
ALTER TABLE listing_revisions ADD COLUMN IF NOT EXISTS frontage_m NUMERIC(10, 2);
ALTER TABLE listing_revisions ADD COLUMN IF NOT EXISTS road_width_m NUMERIC(10, 2);
ALTER TABLE listing_revisions ADD COLUMN IF NOT EXISTS direction VARCHAR(30);
ALTER TABLE listing_revisions ADD COLUMN IF NOT EXISTS legal_status VARCHAR(100);

ALTER TABLE listing_revisions
    ADD CONSTRAINT chk_listing_revisions_bedrooms_non_negative CHECK (bedrooms IS NULL OR bedrooms >= 0),
    ADD CONSTRAINT chk_listing_revisions_bathrooms_non_negative CHECK (bathrooms IS NULL OR bathrooms >= 0),
    ADD CONSTRAINT chk_listing_revisions_floors_non_negative CHECK (floors IS NULL OR floors >= 0),
    ADD CONSTRAINT chk_listing_revisions_frontage_non_negative CHECK (frontage_m IS NULL OR frontage_m >= 0),
    ADD CONSTRAINT chk_listing_revisions_road_width_non_negative CHECK (road_width_m IS NULL OR road_width_m >= 0);
