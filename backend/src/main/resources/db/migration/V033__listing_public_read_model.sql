-- Stream S2-SEARCH (contract §9): public read model for listing search, cards and detail.
-- listing_public_read holds one denormalised row per publicly visible listing (status ACTIVE, APPROVED public revision,
-- seller account ACTIVE). PostgreSQL stays the source of truth; Elasticsearch is fed from this table (V034 triggers +
-- the search-index job) and can always be rebuilt from it.
-- Additive only: no existing table or column changes. Rollback = redeploy the previous image (tables stay unused).

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Vietnamese search normalisation, version 1 (mirrored by Java VietnameseNormalizer.normalize, parity-tested):
-- NFC, đ→d, strip diacritics, lower case, every run of non [a-z0-9] characters becomes one space, trimmed; '' → NULL.
CREATE OR REPLACE FUNCTION bds_search_normalize(p_text text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
AS $$
SELECT NULLIF(btrim(regexp_replace(
        lower(public.unaccent('public.unaccent'::regdictionary, translate(normalize(coalesce(p_text, ''), NFC), 'đĐ', 'dD'))),
        '[^a-z0-9]+', ' ', 'g')), '')
$$;

-- Search areas. Vietnam abolished the district level on 2025-07-01, but listings (and the people searching them) still
-- use the pre-2025 district codes and names, so they are kept as search areas, not as current administrative units.
CREATE TABLE IF NOT EXISTS search_locations (
    province_code VARCHAR(10)  NOT NULL,
    district_code VARCHAR(10)  NOT NULL,
    province_name VARCHAR(100) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    aliases       TEXT[]       NOT NULL DEFAULT '{}',
    kind          VARCHAR(30)  NOT NULL DEFAULT 'DISTRICT_PRE_2025',
    PRIMARY KEY (province_code, district_code),
    CONSTRAINT chk_search_locations_kind CHECK (kind IN ('DISTRICT_PRE_2025'))
);

COMMENT ON TABLE search_locations IS
    'Search areas keyed by the pre-2025 district codes listings carry (district level abolished 2025-07-01); aliases are unaccented search synonyms.';

-- Hà Nội (province 01), General Statistics Office codes valid until 2025-06-30.
INSERT INTO search_locations (province_code, district_code, province_name, name, aliases) VALUES
    ('01', '001', 'Hà Nội', 'Ba Đình',       ARRAY['q ba dinh', 'quan ba dinh']),
    ('01', '002', 'Hà Nội', 'Hoàn Kiếm',     ARRAY['q hoan kiem', 'quan hoan kiem', 'pho co']),
    ('01', '003', 'Hà Nội', 'Tây Hồ',        ARRAY['q tay ho', 'quan tay ho', 'ho tay']),
    ('01', '004', 'Hà Nội', 'Long Biên',     ARRAY['q long bien', 'quan long bien']),
    ('01', '005', 'Hà Nội', 'Cầu Giấy',      ARRAY['q cau giay', 'quan cau giay']),
    ('01', '006', 'Hà Nội', 'Đống Đa',       ARRAY['q dong da', 'quan dong da']),
    ('01', '007', 'Hà Nội', 'Hai Bà Trưng',  ARRAY['hbt', 'q hai ba trung', 'quan hai ba trung']),
    ('01', '008', 'Hà Nội', 'Hoàng Mai',     ARRAY['q hoang mai', 'quan hoang mai']),
    ('01', '009', 'Hà Nội', 'Thanh Xuân',    ARRAY['q thanh xuan', 'quan thanh xuan']),
    ('01', '016', 'Hà Nội', 'Sóc Sơn',       ARRAY['huyen soc son']),
    ('01', '017', 'Hà Nội', 'Đông Anh',      ARRAY['huyen dong anh']),
    ('01', '018', 'Hà Nội', 'Gia Lâm',       ARRAY['huyen gia lam']),
    ('01', '019', 'Hà Nội', 'Nam Từ Liêm',   ARRAY['ntl', 'tu liem', 'q nam tu liem', 'quan nam tu liem']),
    ('01', '020', 'Hà Nội', 'Thanh Trì',     ARRAY['huyen thanh tri']),
    ('01', '021', 'Hà Nội', 'Bắc Từ Liêm',   ARRAY['btl', 'tu liem', 'q bac tu liem', 'quan bac tu liem']),
    ('01', '250', 'Hà Nội', 'Mê Linh',       ARRAY['huyen me linh']),
    ('01', '268', 'Hà Nội', 'Hà Đông',       ARRAY['q ha dong', 'quan ha dong']),
    ('01', '269', 'Hà Nội', 'Sơn Tây',       ARRAY['thi xa son tay']),
    ('01', '271', 'Hà Nội', 'Ba Vì',         ARRAY['huyen ba vi']),
    ('01', '272', 'Hà Nội', 'Phúc Thọ',      ARRAY['huyen phuc tho']),
    ('01', '273', 'Hà Nội', 'Đan Phượng',    ARRAY['huyen dan phuong']),
    ('01', '274', 'Hà Nội', 'Hoài Đức',      ARRAY['huyen hoai duc']),
    ('01', '275', 'Hà Nội', 'Quốc Oai',      ARRAY['huyen quoc oai']),
    ('01', '276', 'Hà Nội', 'Thạch Thất',    ARRAY['huyen thach that']),
    ('01', '277', 'Hà Nội', 'Chương Mỹ',     ARRAY['huyen chuong my']),
    ('01', '278', 'Hà Nội', 'Thanh Oai',     ARRAY['huyen thanh oai']),
    ('01', '279', 'Hà Nội', 'Thường Tín',    ARRAY['huyen thuong tin']),
    ('01', '280', 'Hà Nội', 'Phú Xuyên',     ARRAY['huyen phu xuyen']),
    ('01', '281', 'Hà Nội', 'Ứng Hòa',       ARRAY['huyen ung hoa']),
    ('01', '282', 'Hà Nội', 'Mỹ Đức',        ARRAY['huyen my duc'])
ON CONFLICT (province_code, district_code) DO NOTHING;

-- Strictly increasing per refresh; also the Elasticsearch external version (a replay can never lower a document).
CREATE SEQUENCE IF NOT EXISTS listing_public_read_version_seq;

CREATE TABLE IF NOT EXISTS listing_public_read (
    listing_id                UUID PRIMARY KEY REFERENCES listings (id) ON DELETE CASCADE,
    slug                      VARCHAR(180)  NOT NULL,
    owner_id                  UUID          NOT NULL,
    public_revision_id        UUID          NOT NULL,
    revision_number           INT           NOT NULL,
    title                     VARCHAR(255)  NOT NULL,
    description               TEXT,
    description_excerpt       VARCHAR(300),
    purpose                   VARCHAR(20)   NOT NULL,
    property_type             VARCHAR(30)   NOT NULL,
    price_vnd                 BIGINT        NOT NULL,
    price_period              VARCHAR(10),
    unit_price_vnd            BIGINT,
    area_m2                   NUMERIC(10, 2) NOT NULL,
    bedrooms                  INT,
    bathrooms                 INT,
    floors                    INT,
    frontage_m                NUMERIC(10, 2),
    road_width_m              NUMERIC(10, 2),
    direction                 VARCHAR(30),
    legal_status_code         VARCHAR(30),
    legal_status_text         VARCHAR(100),
    furnishing                VARCHAR(20),
    monthly_service_fee_vnd   BIGINT,
    deposit_vnd               BIGINT,
    province_code             VARCHAR(50),
    district_code             VARCHAR(50),
    district_name             VARCHAR(100),
    ward_code                 VARCHAR(50),
    ward_name                 VARCHAR(100),
    address_summary           VARCHAR(255),
    public_location           geometry(Point, 4326),
    lat                       DOUBLE PRECISION,
    lng                       DOUBLE PRECISION,
    project_id                UUID,
    project_slug              VARCHAR(200),
    project_name              VARCHAR(200),
    thumbnail_url             TEXT,
    image_count               INT           NOT NULL DEFAULT 0,
    media_urls                TEXT[]        NOT NULL DEFAULT '{}',
    seller_name               VARCHAR(150),
    seller_avatar_url         VARCHAR(1000),
    seller_role               VARCHAR(20)   NOT NULL,
    identity_status           VARCHAR(20)   NOT NULL,
    identity_checked_at       TIMESTAMPTZ,
    identity_expires_at       TIMESTAMPTZ,
    ownership_status          VARCHAR(20)   NOT NULL,
    ownership_checked_at      TIMESTAMPTZ,
    ownership_expires_at      TIMESTAMPTZ,
    ownership_document_type   VARCHAR(50),
    listing_checked_at        TIMESTAMPTZ,
    -- Earliest future expiry of a trust fact shown on the row: the daily task refreshes the row once it has passed.
    trust_expires_at          TIMESTAMPTZ,
    published_at              TIMESTAMPTZ   NOT NULL,
    updated_at                TIMESTAMPTZ   NOT NULL,
    availability_confirmed_at TIMESTAMPTZ,
    previous_price_vnd        BIGINT,
    price_changed_at          TIMESTAMPTZ,
    search_text               TEXT          NOT NULL,
    search_tsv                TSVECTOR      NOT NULL,
    row_version               BIGINT        NOT NULL,
    refreshed_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_lpr_identity_status CHECK (identity_status IN ('VERIFIED', 'PENDING', 'REJECTED', 'EXPIRED', 'NOT_SUBMITTED')),
    CONSTRAINT chk_lpr_ownership_status CHECK (ownership_status IN ('VERIFIED', 'PENDING', 'REJECTED', 'REVOKED', 'EXPIRED', 'NOT_SUBMITTED'))
);

-- Keyset orders of contract §8 (purpose is always part of a public query: default SALE). PRICE_DESC/AREA_DESC use a
-- backward scan of the ascending indexes; the seller page uses its own owner index.
CREATE INDEX IF NOT EXISTS idx_lpr_newest ON listing_public_read (purpose, published_at DESC, listing_id DESC);
CREATE INDEX IF NOT EXISTS idx_lpr_price ON listing_public_read (purpose, price_vnd, listing_id);
CREATE INDEX IF NOT EXISTS idx_lpr_area ON listing_public_read (purpose, area_m2, listing_id);
CREATE INDEX IF NOT EXISTS idx_lpr_owner_newest ON listing_public_read (owner_id, published_at DESC, listing_id DESC);
CREATE INDEX IF NOT EXISTS idx_lpr_location ON listing_public_read USING GIST (public_location);
CREATE INDEX IF NOT EXISTS idx_lpr_search_tsv ON listing_public_read USING GIN (search_tsv);
CREATE INDEX IF NOT EXISTS idx_lpr_trust_expiry ON listing_public_read (trust_expires_at) WHERE trust_expires_at IS NOT NULL;

-- Upserts (visible) or deletes (not visible) the read-model row of one listing. Serialised per listing by an advisory
-- transaction lock, so row_version order equals commit order for the same listing. Returns the version assigned to
-- this refresh, also for a delete (the Elasticsearch delete must carry a version above every earlier index call).
CREATE OR REPLACE FUNCTION bds_refresh_listing_public_read(p_listing uuid)
    RETURNS TABLE (visible boolean, row_version bigint)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_version bigint;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('listing_public_read:' || p_listing::text, 0));
    v_version := nextval('listing_public_read_version_seq');

    INSERT INTO listing_public_read AS t (
        listing_id, slug, owner_id, public_revision_id, revision_number, title, description, description_excerpt,
        purpose, property_type, price_vnd, price_period, unit_price_vnd, area_m2, bedrooms, bathrooms, floors,
        frontage_m, road_width_m, direction, legal_status_code, legal_status_text, furnishing,
        monthly_service_fee_vnd, deposit_vnd, province_code, district_code, district_name, ward_code, ward_name,
        address_summary, public_location, lat, lng, project_id, project_slug, project_name,
        thumbnail_url, image_count, media_urls, seller_name, seller_avatar_url, seller_role,
        identity_status, identity_checked_at, identity_expires_at,
        ownership_status, ownership_checked_at, ownership_expires_at, ownership_document_type,
        listing_checked_at, trust_expires_at, published_at, updated_at, availability_confirmed_at,
        previous_price_vnd, price_changed_at, search_text, search_tsv, row_version, refreshed_at)
    SELECT l.id, l.slug, l.owner_id, r.id, r.revision_number, r.title, r.description,
           left(regexp_replace(coalesce(r.description, ''), '\s+', ' ', 'g'), 300),
           r.purpose, r.property_type, r.price_vnd, r.price_period,
           CASE WHEN r.purpose = 'SALE' AND r.area_m2 > 0 THEN round(r.price_vnd / r.area_m2)::bigint END,
           r.area_m2, r.bedrooms, r.bathrooms, r.floors, r.frontage_m, r.road_width_m, r.direction,
           r.legal_status_code, r.legal_status, r.furnishing,
           CASE WHEN r.purpose = 'RENT' THEN r.monthly_service_fee_vnd END,
           CASE WHEN r.purpose = 'RENT' THEN r.deposit_vnd END,
           r.province_code, r.district_code, loc.name, r.ward_code, NULL,
           r.address_summary,
           CASE WHEN r.public_latitude IS NOT NULL AND r.public_longitude IS NOT NULL
                THEN ST_SetSRID(ST_MakePoint(r.public_longitude, r.public_latitude), 4326) END,
           r.public_latitude, r.public_longitude,
           p.id, p.slug, p.name,
           media.urls[1], coalesce(cardinality(media.urls), 0), coalesce(media.urls, '{}'),
           u.full_name, u.avatar_media_url,
           COALESCE((SELECT ur.role FROM user_roles ur WHERE ur.user_id = l.owner_id
                     ORDER BY CASE ur.role WHEN 'ADMIN' THEN 1 WHEN 'MODERATOR' THEN 2 WHEN 'BROKER' THEN 3
                                           WHEN 'OWNER' THEN 4 ELSE 5 END LIMIT 1), 'USER'),
           CASE WHEN k.id IS NULL THEN 'NOT_SUBMITTED'
                WHEN k.status = 'VERIFIED' AND (k.expires_at IS NULL OR k.expires_at > now()) THEN 'VERIFIED'
                WHEN k.status = 'VERIFIED' THEN 'EXPIRED'
                WHEN k.status = 'REJECTED' THEN 'REJECTED'
                ELSE 'PENDING' END,
           CASE WHEN k.status IN ('VERIFIED', 'REJECTED') THEN k.verified_at END,
           CASE WHEN k.status = 'VERIFIED' THEN k.expires_at END,
           coalesce(own.status, 'NOT_SUBMITTED'), own.checked_at, own.expires_at, own.document_type,
           r.moderated_at,
           NULLIF(LEAST(CASE WHEN k.status = 'VERIFIED' AND k.expires_at > now() THEN k.expires_at ELSE 'infinity'::timestamptz END,
                        CASE WHEN own.status = 'VERIFIED' AND own.expires_at > now() THEN own.expires_at ELSE 'infinity'::timestamptz END),
                  'infinity'::timestamptz),
           coalesce(pub.first_published_at, l.created_at),
           GREATEST(l.updated_at, coalesce(r.moderated_at, l.updated_at)),
           l.availability_confirmed_at,
           CASE WHEN prev.price_vnd IS DISTINCT FROM r.price_vnd THEN prev.price_vnd END,
           CASE WHEN prev.price_vnd IS DISTINCT FROM r.price_vnd AND prev.price_vnd IS NOT NULL THEN r.moderated_at END,
           doc.text, to_tsvector('simple', doc.text), v_version, now()
    FROM listings l
    JOIN listing_revisions r ON r.id = l.public_revision_id AND r.listing_id = l.id AND r.status = 'APPROVED'
    JOIN users u ON u.id = l.owner_id AND u.status = 'ACTIVE'
    LEFT JOIN search_locations loc ON loc.province_code = r.province_code AND loc.district_code = r.district_code
    LEFT JOIN projects p ON p.id = r.project_id AND p.status <> 'LOCKED'
    LEFT JOIN user_kyc_profiles k ON k.user_id = l.owner_id
    LEFT JOIN LATERAL (
        SELECT array_agg(m.media_url ORDER BY m.sort_order, m.created_at, m.id) AS urls
        FROM listing_media m WHERE m.revision_id = r.id AND btrim(m.media_url) <> ''
    ) media ON TRUE
    LEFT JOIN LATERAL (
        SELECT CASE WHEN v.status = 'REVOKED' OR v.revoked_at IS NOT NULL THEN 'REVOKED'
                    WHEN v.status = 'VERIFIED_OWNER' AND (v.expires_at IS NULL OR v.expires_at > now()) THEN 'VERIFIED'
                    WHEN v.status = 'VERIFIED_OWNER' THEN 'EXPIRED'
                    WHEN v.status = 'REJECTED' THEN 'REJECTED'
                    ELSE 'PENDING' END AS status,
               CASE WHEN v.status = 'VERIFIED_OWNER' AND v.revoked_at IS NULL THEN v.verified_at END AS checked_at,
               CASE WHEN v.status = 'VERIFIED_OWNER' AND v.revoked_at IS NULL THEN v.expires_at END AS expires_at,
               v.verification_type AS document_type
        FROM listing_verifications v
        WHERE v.listing_id = l.id
        -- a valid ownership check wins over later pending/rejected submissions; otherwise the newest submission
        ORDER BY (v.status = 'VERIFIED_OWNER' AND v.revoked_at IS NULL AND (v.expires_at IS NULL OR v.expires_at > now())) DESC,
                 v.created_at DESC, v.id DESC
        LIMIT 1
    ) own ON TRUE
    LEFT JOIN LATERAL (
        SELECT min(x.moderated_at) AS first_published_at
        FROM listing_revisions x WHERE x.listing_id = l.id AND x.status = 'APPROVED'
    ) pub ON TRUE
    LEFT JOIN LATERAL (
        SELECT x.price_vnd FROM listing_revisions x
        WHERE x.listing_id = l.id AND x.status = 'APPROVED' AND x.revision_number < r.revision_number
          AND x.purpose = r.purpose
        ORDER BY x.revision_number DESC LIMIT 1
    ) prev ON TRUE
    CROSS JOIN LATERAL (
        SELECT coalesce(bds_search_normalize(concat_ws(' ', r.title, r.address_summary, loc.name,
                   array_to_string(loc.aliases, ' '), loc.province_name, p.name, r.description)), '') AS text
    ) doc
    WHERE l.id = p_listing AND l.status = 'ACTIVE'
    ON CONFLICT (listing_id) DO UPDATE SET
        slug = EXCLUDED.slug, owner_id = EXCLUDED.owner_id, public_revision_id = EXCLUDED.public_revision_id,
        revision_number = EXCLUDED.revision_number, title = EXCLUDED.title, description = EXCLUDED.description,
        description_excerpt = EXCLUDED.description_excerpt, purpose = EXCLUDED.purpose,
        property_type = EXCLUDED.property_type, price_vnd = EXCLUDED.price_vnd, price_period = EXCLUDED.price_period,
        unit_price_vnd = EXCLUDED.unit_price_vnd, area_m2 = EXCLUDED.area_m2, bedrooms = EXCLUDED.bedrooms,
        bathrooms = EXCLUDED.bathrooms, floors = EXCLUDED.floors, frontage_m = EXCLUDED.frontage_m,
        road_width_m = EXCLUDED.road_width_m, direction = EXCLUDED.direction,
        legal_status_code = EXCLUDED.legal_status_code, legal_status_text = EXCLUDED.legal_status_text,
        furnishing = EXCLUDED.furnishing, monthly_service_fee_vnd = EXCLUDED.monthly_service_fee_vnd,
        deposit_vnd = EXCLUDED.deposit_vnd, province_code = EXCLUDED.province_code,
        district_code = EXCLUDED.district_code, district_name = EXCLUDED.district_name, ward_code = EXCLUDED.ward_code,
        ward_name = EXCLUDED.ward_name, address_summary = EXCLUDED.address_summary,
        public_location = EXCLUDED.public_location, lat = EXCLUDED.lat, lng = EXCLUDED.lng,
        project_id = EXCLUDED.project_id, project_slug = EXCLUDED.project_slug, project_name = EXCLUDED.project_name,
        thumbnail_url = EXCLUDED.thumbnail_url, image_count = EXCLUDED.image_count, media_urls = EXCLUDED.media_urls,
        seller_name = EXCLUDED.seller_name, seller_avatar_url = EXCLUDED.seller_avatar_url,
        seller_role = EXCLUDED.seller_role, identity_status = EXCLUDED.identity_status,
        identity_checked_at = EXCLUDED.identity_checked_at, identity_expires_at = EXCLUDED.identity_expires_at,
        ownership_status = EXCLUDED.ownership_status, ownership_checked_at = EXCLUDED.ownership_checked_at,
        ownership_expires_at = EXCLUDED.ownership_expires_at, ownership_document_type = EXCLUDED.ownership_document_type,
        listing_checked_at = EXCLUDED.listing_checked_at, trust_expires_at = EXCLUDED.trust_expires_at,
        published_at = EXCLUDED.published_at, updated_at = EXCLUDED.updated_at,
        availability_confirmed_at = EXCLUDED.availability_confirmed_at,
        previous_price_vnd = EXCLUDED.previous_price_vnd, price_changed_at = EXCLUDED.price_changed_at,
        search_text = EXCLUDED.search_text, search_tsv = EXCLUDED.search_tsv,
        row_version = EXCLUDED.row_version, refreshed_at = EXCLUDED.refreshed_at
    WHERE t.row_version < EXCLUDED.row_version;

    IF FOUND THEN
        RETURN QUERY SELECT TRUE, v_version;
        RETURN;
    END IF;
    DELETE FROM listing_public_read d WHERE d.listing_id = p_listing;
    RETURN QUERY SELECT FALSE, v_version;
END;
$$;

-- Initial fill for the listings that are public today (the search index is backfilled from here on first start).
SELECT count(*) FROM listings l, LATERAL bds_refresh_listing_public_read(l.id) f WHERE l.status = 'ACTIVE';
