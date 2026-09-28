-- S7-SEO (audit P-06): public project and area landing pages.

-- Projects: public description and where the facts come from. Amenities are only shown with a source and the date
-- someone checked them (no invented "near a school" claims).
ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS description     TEXT,
    ADD COLUMN IF NOT EXISTS website_url     VARCHAR(500),
    ADD COLUMN IF NOT EXISTS info_source     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS info_checked_at DATE;

CREATE TABLE IF NOT EXISTS project_amenities (
    id          UUID PRIMARY KEY,
    project_id  UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    category    VARCHAR(30)  NOT NULL,
    distance_m  INT,
    source_name VARCHAR(255) NOT NULL,
    source_url  VARCHAR(1000),
    checked_at  DATE         NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_project_amenity_category
        CHECK (category IN ('EDUCATION', 'HEALTH', 'TRANSPORT', 'SHOPPING', 'PARK', 'SPORT', 'OTHER')),
    CONSTRAINT chk_project_amenity_distance CHECK (distance_m IS NULL OR distance_m BETWEEN 0 AND 100000)
);
CREATE INDEX IF NOT EXISTS idx_project_amenities_project ON project_amenities (project_id, sort_order);

DROP TRIGGER IF EXISTS trg_projects_seo_generation ON projects;
CREATE TRIGGER trg_projects_seo_generation AFTER INSERT OR UPDATE OR DELETE ON projects
    FOR EACH STATEMENT EXECUTE FUNCTION bds_bump_seo_content_generation();
DROP TRIGGER IF EXISTS trg_project_amenities_seo_generation ON project_amenities;
CREATE TRIGGER trg_project_amenities_seo_generation AFTER INSERT OR UPDATE OR DELETE ON project_amenities
    FOR EACH STATEMENT EXECUTE FUNCTION bds_bump_seo_content_generation();

-- Areas: URL slugs for the search areas (pre-2025 districts, see V033). Unique across provinces; a later province whose
-- district name collides gets the province appended when it is inserted.
ALTER TABLE search_locations ADD COLUMN IF NOT EXISTS slug VARCHAR(120);
UPDATE search_locations SET slug = replace(bds_search_normalize(name), ' ', '-') WHERE slug IS NULL;
ALTER TABLE search_locations ALTER COLUMN slug SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_search_locations_slug ON search_locations (slug);

-- Inventory of an area or a project on the read model (landing pages, statistics, sitemap lastmod).
CREATE INDEX IF NOT EXISTS idx_lpr_district ON listing_public_read (province_code, district_code, purpose);
CREATE INDEX IF NOT EXISTS idx_lpr_project ON listing_public_read (project_id, purpose) WHERE project_id IS NOT NULL;
