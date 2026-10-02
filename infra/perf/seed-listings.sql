\set ON_ERROR_STOP on
-- W6-PERF: production-shaped synthetic listings for query plans and load tests. Disposable bds_perf_* databases only.
--   psql -v start=1 -v finish=100000 -v owners=5000 -f infra/perf/seed-listings.sql
-- Public listings n = start..finish become ACTIVE listings with an APPROVED revision and a listing_public_read row
-- (slug perf-<n>). For every five public listings one non-public listing is added (DRAFT, PENDING_REVIEW, PAUSED,
-- EXPIRED, REJECTED; slug perf-x-<n>) so owner pages see every status. Calling it again with the next range extends
-- the dataset (100k -> 1M) without touching earlier rows. Every attribute is a deterministic hash of n, so two runs
-- produce the same data. Owners: `owners` ACTIVE users (70 % BROKER) with a skewed share of listings (the top owner
-- holds about 1/sqrt(owners) of them, like a large agency).
-- Triggers and FK checks are skipped while loading (session_replication_role = replica): the read-model rows are written
-- here directly, consistent with the listing/revision rows, instead of one bds_refresh_listing_public_read call per row.
-- Fixture listings have no media (thumbnail NULL); descriptions are short generated Vietnamese text.
BEGIN;
DO $$
BEGIN
    IF current_database() !~ '^bds_perf_' THEN
        RAISE EXCEPTION 'seed-listings requires an isolated bds_perf_* database, got %', current_database();
    END IF;
END
$$;
SET LOCAL session_replication_role = replica;
SET LOCAL statement_timeout = '60min';
SET LOCAL work_mem = '256MB';
SET LOCAL maintenance_work_mem = '512MB';
SET LOCAL synchronous_commit = off;

-- Deterministic uniform [0, 1) from (n, k).
CREATE FUNCTION pg_temp.u(n bigint, k int) RETURNS float8 LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT (hashint8extended(n, k) & 2147483647)::float8 / 2147483648 $$;

-- Hà Nội search areas by listing popularity (index 0 = most listings) with an approximate centre.
CREATE TEMP TABLE perf_districts (idx int PRIMARY KEY, code varchar(10), lng float8, lat float8) ON COMMIT DROP;
INSERT INTO perf_districts VALUES
    (0, '005', 105.7900, 21.0310), (1, '019', 105.7650, 21.0130), (2, '268', 105.7750, 20.9600),
    (3, '006', 105.8270, 21.0130), (4, '009', 105.8100, 20.9940), (5, '008', 105.8600, 20.9750),
    (6, '004', 105.8950, 21.0480), (7, '021', 105.7650, 21.0700), (8, '001', 105.8195, 21.0358),
    (9, '007', 105.8570, 21.0060), (10, '003', 105.8180, 21.0700), (11, '002', 105.8522, 21.0287),
    (12, '017', 105.8480, 21.1370), (13, '018', 105.9400, 21.0280), (14, '020', 105.8450, 20.9400),
    (15, '274', 105.7000, 21.0300), (16, '250', 105.7200, 21.1800), (17, '016', 105.8480, 21.2570),
    (18, '273', 105.6700, 21.0900), (19, '277', 105.6600, 20.9000), (20, '278', 105.7700, 20.8600),
    (21, '279', 105.8600, 20.8700), (22, '276', 105.5600, 21.0300), (23, '275', 105.6400, 20.9900),
    (24, '272', 105.5700, 21.1100), (25, '269', 105.5050, 21.1380), (26, '280', 105.9000, 20.7400),
    (27, '281', 105.7800, 20.7200), (28, '282', 105.7300, 20.6800), (29, '271', 105.4200, 21.2000);

INSERT INTO users (id, phone_lookup_hash, phone_encrypted, full_name, email, status, created_at, updated_at,
                   email_verified_at, plan_code, listing_quota_remaining)
SELECT md5('perf-owner:' || i)::uuid, md5('perf-phone:' || i), 'perf-fixture', 'Người bán ' || i,
       'perf.owner.' || i || '@example.invalid', 'ACTIVE',
       TIMESTAMPTZ '2024-01-01 00:00:00+00' + (i % 600) * interval '1 day',
       TIMESTAMPTZ '2024-01-01 00:00:00+00' + (i % 600) * interval '1 day',
       TIMESTAMPTZ '2024-01-01 00:00:00+00' + (i % 600) * interval '1 day', 'FREE', 0
FROM generate_series(0, :owners - 1) AS i
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (user_id, role)
SELECT md5('perf-owner:' || i)::uuid, CASE WHEN i % 10 < 7 THEN 'BROKER' ELSE 'OWNER' END
FROM generate_series(0, :owners - 1) AS i
ON CONFLICT DO NOTHING;

-- One row per public listing with every derived attribute.
CREATE TEMP TABLE perf_src ON COMMIT DROP AS
WITH base AS (
    SELECT n,
           CASE WHEN pg_temp.u(n, 1) < 0.72 THEN 'SALE' ELSE 'RENT' END AS purpose,
           CASE WHEN pg_temp.u(n, 2) < 0.40 THEN 'APARTMENT' WHEN pg_temp.u(n, 2) < 0.65 THEN 'HOUSE'
                WHEN pg_temp.u(n, 2) < 0.80 THEN 'TOWNHOUSE' WHEN pg_temp.u(n, 2) < 0.95 THEN 'LAND' ELSE 'VILLA' END AS ptype,
           least(29, floor(30 * power(pg_temp.u(n, 3), 2.2)))::int AS didx,
           floor(:owners * power(pg_temp.u(n, 10), 2))::int AS owner_idx,
           pg_temp.u(n, 6) AS u_price, pg_temp.u(n, 7) AS u_area, pg_temp.u(n, 8) AS u_beds,
           TIMESTAMPTZ '2026-10-01 00:00:00+00' - power(pg_temp.u(n, 9), 2) * interval '365 days' AS published_at
    FROM generate_series(:start::bigint, :finish::bigint) AS n
), shaped AS (
    SELECT b.*, d.code AS district_code, d.lng + (pg_temp.u(n, 4) - 0.5) * 0.04 AS lng,
           d.lat + (pg_temp.u(n, 5) - 0.5) * 0.03 AS lat,
           (ARRAY['Trần Duy Hưng', 'Nguyễn Trãi', 'Láng Hạ', 'Xuân Thủy', 'Phạm Văn Đồng', 'Giải Phóng', 'Kim Mã',
                  'Lê Văn Lương', 'Nguyễn Văn Cừ', 'Tố Hữu', 'Minh Khai', 'Âu Cơ'])[1 + (hashint8extended(n, 11) & 2147483647) % 12] AS street,
           (ARRAY['view hồ thoáng mát', 'gần trường học', 'sổ hồng chính chủ', 'full nội thất', 'mặt tiền kinh doanh',
                  'ngõ ô tô', 'giá tốt', 'tầng cao'])[1 + (hashint8extended(n, 12) & 2147483647) % 8] AS adj,
           CASE ptype WHEN 'APARTMENT' THEN 'căn hộ chung cư' WHEN 'HOUSE' THEN 'nhà riêng' WHEN 'TOWNHOUSE' THEN 'nhà phố'
                      WHEN 'LAND' THEN 'đất nền' ELSE 'biệt thự' END AS noun,
           round((CASE ptype WHEN 'APARTMENT' THEN 30 + 120 * power(u_area, 1.5) WHEN 'HOUSE' THEN 30 + 90 * power(u_area, 1.5)
                             WHEN 'TOWNHOUSE' THEN 50 + 100 * power(u_area, 1.5) WHEN 'LAND' THEN 50 + 450 * power(u_area, 1.5)
                             ELSE 150 + 450 * power(u_area, 1.5) END)::numeric, 1) AS area_m2,
           CASE WHEN ptype = 'LAND' THEN NULL ELSE 1 + floor(u_beds * 5)::int END AS bedrooms,
           CASE WHEN purpose = 'SALE' THEN round(800000000 * exp(u_price * 3.5) / 1000000) * 1000000
                ELSE round(3000000 * exp(u_price * 3.0) / 100000) * 100000 END::bigint AS price_vnd,
           (ARRAY['PINK_BOOK', 'PINK_BOOK', 'RED_BOOK', 'RED_BOOK', 'SALE_CONTRACT', 'PENDING_CERTIFICATE', 'OTHER'])
               [1 + (hashint8extended(n, 13) & 2147483647) % 7] AS legal_code,
           (ARRAY['NONE', 'BASIC', 'FULL'])[1 + (hashint8extended(n, 14) & 2147483647) % 3] AS furnishing
    FROM base b JOIN perf_districts d ON d.idx = b.didx
)
SELECT s.*, loc.name AS district_name, loc.aliases, loc.province_name,
       md5('perf-listing:' || n)::uuid AS listing_id, md5('perf-revision:' || n)::uuid AS revision_id,
       md5('perf-owner:' || owner_idx)::uuid AS owner_id, 'perf-' || n AS slug,
       format('%s %s %s, %s, %s %s m2', CASE WHEN purpose = 'SALE' THEN 'Bán' ELSE 'Cho thuê' END, noun, adj, street,
              loc.name, area_m2) || CASE WHEN n % 25000 = 7 THEN ' landmarkrare' ELSE '' END AS title,
       format('%s %s diện tích %s m2 tại đường %s, %s. Pháp lý %s, nội thất %s. Liên hệ để xem nhà.',
              CASE WHEN purpose = 'SALE' THEN 'Cần bán' ELSE 'Cho thuê' END, noun, area_m2, street, loc.name,
              legal_code, furnishing) AS description,
       street || ', ' || loc.name || ', Hà Nội' AS address_summary,
       CASE WHEN owner_idx % 10 < 7 THEN 'BROKER' ELSE 'OWNER' END AS seller_role,
       pg_temp.u(n, 15) < 0.6 AS identity_verified, pg_temp.u(n, 16) < 0.2 AS ownership_verified,
       pg_temp.u(n, 17) < 0.95 AS has_location
FROM shaped s JOIN search_locations loc ON loc.province_code = '01' AND loc.district_code = s.district_code;

INSERT INTO listings (id, owner_id, public_revision_id, status, created_at, updated_at, slug, version,
                      availability_confirmed_at, expires_at, source)
SELECT listing_id, owner_id, revision_id, 'ACTIVE', published_at - interval '1 day', published_at, slug, 1,
       published_at, now() + (30 + floor(pg_temp.u(n, 18) * 60)) * interval '1 day', 'DIRECT'
FROM perf_src;

INSERT INTO listing_revisions (id, listing_id, revision_number, status, title, purpose, property_type, price_vnd, area_m2,
                               description, province_code, district_code, address_summary, public_latitude,
                               public_longitude, created_at, submitted_at, moderated_at, bedrooms, bathrooms, floors,
                               direction, legal_status, legal_status_code, furnishing, monthly_service_fee_vnd, deposit_vnd)
SELECT revision_id, listing_id, 1, 'APPROVED', title, purpose, ptype, price_vnd, area_m2, description, '01',
       district_code, address_summary, CASE WHEN has_location THEN lat END, CASE WHEN has_location THEN lng END,
       published_at - interval '1 day', published_at - interval '12 hours', published_at, bedrooms,
       CASE WHEN bedrooms IS NULL THEN NULL ELSE greatest(1, bedrooms - 1) END,
       CASE WHEN ptype IN ('HOUSE', 'TOWNHOUSE', 'VILLA') THEN 2 + n % 4 END,
       (ARRAY['Đông', 'Tây', 'Nam', 'Bắc', 'Đông Nam', 'Tây Bắc'])[1 + n % 6], legal_code, legal_code, furnishing,
       CASE WHEN purpose = 'RENT' THEN 200000 * (n % 5) END, CASE WHEN purpose = 'RENT' THEN price_vnd * 2 END
FROM perf_src;

INSERT INTO listing_public_read (
    listing_id, slug, owner_id, public_revision_id, revision_number, title, description, description_excerpt, purpose,
    property_type, price_vnd, price_period, unit_price_vnd, area_m2, bedrooms, bathrooms, floors, frontage_m,
    road_width_m, direction, legal_status_code, legal_status_text, furnishing, monthly_service_fee_vnd, deposit_vnd,
    province_code, district_code, district_name, ward_code, ward_name, address_summary, public_location, lat, lng,
    project_id, project_slug, project_name, thumbnail_url, image_count, media_urls, seller_name, seller_avatar_url,
    seller_role, identity_status, identity_checked_at, identity_expires_at, ownership_status, ownership_checked_at,
    ownership_expires_at, ownership_document_type, listing_checked_at, trust_expires_at, published_at, updated_at,
    availability_confirmed_at, previous_price_vnd, price_changed_at, search_text, search_tsv, row_version, refreshed_at)
SELECT s.listing_id, s.slug, s.owner_id, s.revision_id, 1, s.title, s.description, left(s.description, 300), s.purpose,
       s.ptype, s.price_vnd, CASE WHEN s.purpose = 'RENT' THEN 'MONTH' END,
       CASE WHEN s.purpose = 'SALE' THEN round(s.price_vnd / s.area_m2)::bigint END, s.area_m2, s.bedrooms,
       CASE WHEN s.bedrooms IS NULL THEN NULL ELSE greatest(1, s.bedrooms - 1) END,
       CASE WHEN s.ptype IN ('HOUSE', 'TOWNHOUSE', 'VILLA') THEN 2 + s.n % 4 END, NULL, NULL,
       (ARRAY['Đông', 'Tây', 'Nam', 'Bắc', 'Đông Nam', 'Tây Bắc'])[1 + s.n % 6], s.legal_code, s.legal_code, s.furnishing,
       CASE WHEN s.purpose = 'RENT' THEN 200000 * (s.n % 5) END, CASE WHEN s.purpose = 'RENT' THEN s.price_vnd * 2 END,
       '01', s.district_code, s.district_name, NULL, NULL, s.address_summary,
       CASE WHEN s.has_location THEN ST_SetSRID(ST_MakePoint(s.lng, s.lat), 4326) END,
       CASE WHEN s.has_location THEN s.lat END, CASE WHEN s.has_location THEN s.lng END,
       NULL, NULL, NULL, NULL, 0, '{}', 'Người bán ' || s.owner_idx, NULL, s.seller_role,
       CASE WHEN s.identity_verified THEN 'VERIFIED' ELSE 'NOT_SUBMITTED' END,
       CASE WHEN s.identity_verified THEN s.published_at - interval '30 days' END,
       CASE WHEN s.identity_verified THEN TIMESTAMPTZ '2027-06-01 00:00:00+00' END,
       CASE WHEN s.ownership_verified THEN 'VERIFIED' ELSE 'NOT_SUBMITTED' END,
       CASE WHEN s.ownership_verified THEN s.published_at END,
       CASE WHEN s.ownership_verified THEN TIMESTAMPTZ '2027-03-01 00:00:00+00' END,
       CASE WHEN s.ownership_verified THEN 'PINK_BOOK' END, s.published_at,
       CASE WHEN s.ownership_verified THEN TIMESTAMPTZ '2027-03-01 00:00:00+00'
            WHEN s.identity_verified THEN TIMESTAMPTZ '2027-06-01 00:00:00+00' END,
       s.published_at, s.published_at, s.published_at, NULL, NULL, doc.text, to_tsvector('simple', doc.text),
       nextval('listing_public_read_version_seq'), now()
FROM perf_src s
CROSS JOIN LATERAL (
    SELECT coalesce(bds_search_normalize(concat_ws(' ', s.title, s.address_summary, s.district_name,
               array_to_string(s.aliases, ' '), s.province_name, s.description)), '') AS text
) doc;

-- Non-public listings: one per five public ones, same owners, every non-public status.
INSERT INTO listings (id, owner_id, public_revision_id, status, created_at, updated_at, slug, version, source)
SELECT md5('perf-x-listing:' || n)::uuid, md5('perf-owner:' || floor(:owners * power(pg_temp.u(n, 10), 2))::int)::uuid,
       CASE WHEN st IN ('PAUSED', 'EXPIRED') THEN md5('perf-x-revision:' || n)::uuid END, st,
       TIMESTAMPTZ '2026-10-01 00:00:00+00' - power(pg_temp.u(n, 9), 2) * interval '365 days',
       TIMESTAMPTZ '2026-10-01 00:00:00+00' - power(pg_temp.u(n, 9), 2) * interval '360 days', 'perf-x-' || n, 1, 'DIRECT'
FROM (SELECT n, (ARRAY['DRAFT', 'DRAFT', 'DRAFT', 'PENDING_REVIEW', 'PENDING_REVIEW', 'PAUSED', 'EXPIRED', 'EXPIRED',
                       'REJECTED', 'DRAFT'])[1 + n % 10] AS st
      FROM generate_series(:start::bigint, :finish::bigint) AS n WHERE n % 5 = 0) x;
INSERT INTO listing_revisions (id, listing_id, revision_number, status, title, purpose, property_type, price_vnd, area_m2,
                               description, province_code, district_code, created_at, submitted_at, moderated_at)
SELECT md5('perf-x-revision:' || n)::uuid, md5('perf-x-listing:' || n)::uuid, 1,
       CASE l.status WHEN 'DRAFT' THEN 'DRAFT' WHEN 'PENDING_REVIEW' THEN 'SUBMITTED' WHEN 'REJECTED' THEN 'REJECTED'
                     ELSE 'APPROVED' END,
       'Tin nháp kiểm thử ' || n, 'SALE', 'APARTMENT', 1500000000, 65, 'Bản nháp chưa công khai', '01', '005',
       l.created_at, CASE WHEN l.status <> 'DRAFT' THEN l.created_at END,
       CASE WHEN l.status IN ('PAUSED', 'EXPIRED', 'REJECTED') THEN l.updated_at END
FROM generate_series(:start::bigint, :finish::bigint) AS n
JOIN listings l ON l.id = md5('perf-x-listing:' || n)::uuid
WHERE n % 5 = 0;

-- Leads on 8 % of public listings (lead counts on owner pages, seller response statistics).
INSERT INTO leads (id, listing_id, full_name, phone_encrypted, phone_lookup_hash, note, status, created_at, updated_at,
                   first_response_at)
SELECT md5('perf-lead:' || n)::uuid, listing_id, 'Khách ' || n, 'perf-fixture', md5('perf-lead-phone:' || n), NULL,
       CASE WHEN n % 3 = 0 THEN 'NEW' ELSE 'CONTACTED' END, published_at + interval '2 days',
       published_at + interval '2 days', CASE WHEN n % 3 <> 0 THEN published_at + interval '2 days 3 hours' END
FROM perf_src WHERE pg_temp.u(n, 19) < 0.08;

SELECT count(*) AS public_rows_added FROM perf_src;
COMMIT;
