-- W6-PERF query catalog: the SQL the application sends, copied from the adapters named in each block, with typical
-- parameter values bound as literals. scripts/query-plans.py runs EXPLAIN (ANALYZE, BUFFERS, SETTINGS) on each block.
-- @SUMMARY@ / @DETAIL@ / @OWNER_ACTIVE@ are JdbcListingReadModelAdapter.SUMMARY_COLUMNS / DETAIL_COLUMNS / OWNER_ACTIVE;
-- other @name@ values come from the params block (resolved once per dataset). Keep in sync with the adapters.

-- params
SELECT 'mid_slug', 'perf-' || (max(substring(slug FROM 6)::bigint) / 2) FROM listing_public_read WHERE slug ~ '^perf-[0-9]+$'
UNION ALL
SELECT 'gone_slug', min(slug) FROM listings WHERE slug LIKE 'perf-x-%' AND status = 'PAUSED'
UNION ALL
SELECT 'top_owner', (SELECT owner_id::text FROM listing_public_read GROUP BY owner_id ORDER BY count(*) DESC, owner_id LIMIT 1)
UNION ALL
SELECT 'small_owner', (SELECT owner_id::text FROM listing_public_read GROUP BY owner_id HAVING count(*) BETWEEN 3 AND 30
                        ORDER BY owner_id LIMIT 1)
UNION ALL
SELECT 'page_ids', (SELECT string_agg(quote_literal(listing_id::text), ',') FROM (
    SELECT listing_id FROM listing_public_read WHERE purpose = 'SALE'
    ORDER BY published_at DESC, listing_id DESC OFFSET 200 LIMIT 24) p)
UNION ALL
SELECT unnest(ARRAY['base_purpose', 'base_type', 'base_id', 'base_low', 'base_high', 'base_district', 'base_price']),
       unnest(ARRAY[purpose, property_type, listing_id::text, round(price_vnd * 0.7)::text, round(price_vnd * 1.3)::text,
                    district_code, price_vnd::text])
FROM (SELECT * FROM listing_public_read WHERE purpose = 'SALE' AND property_type = 'APARTMENT'
      ORDER BY listing_id LIMIT 1) b
UNION ALL
SELECT 'mid_id', md5('perf-listing:' || (max(substring(slug FROM 6)::bigint) / 2))
FROM listing_public_read WHERE slug ~ '^perf-[0-9]+$'
UNION ALL
SELECT 'cursor_published', (SELECT quote_literal(published_at::text) FROM (
    SELECT published_at FROM listing_public_read WHERE purpose = 'SALE'
    ORDER BY published_at DESC, listing_id DESC OFFSET 23 LIMIT 1) p)
UNION ALL
SELECT 'cursor_id', (SELECT quote_literal(listing_id::text) FROM (
    SELECT listing_id FROM listing_public_read WHERE purpose = 'SALE'
    ORDER BY published_at DESC, listing_id DESC OFFSET 23 LIMIT 1) p);

-- name: search.newest.sale
-- why: database search engine (Elasticsearch fallback/degraded), first page, default sort (NEWEST)
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
ORDER BY published_at DESC, listing_id DESC LIMIT 25;

-- name: search.newest.rent
-- why: database search engine, RENT first page
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'RENT'@OWNER_ACTIVE@
ORDER BY published_at DESC, listing_id DESC LIMIT 25;

-- name: search.newest.sale.page2
-- why: keyset second page (cursor), NEWEST
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND (published_at, listing_id) < (@cursor_published@::timestamptz, @cursor_id@::uuid)
ORDER BY published_at DESC, listing_id DESC LIMIT 25;

-- name: search.price_asc.sale
-- why: sort PRICE_ASC
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
ORDER BY price_vnd ASC, listing_id ASC LIMIT 25;

-- name: search.price_desc.rent
-- why: sort PRICE_DESC (backward scan of the ascending index)
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'RENT'@OWNER_ACTIVE@
ORDER BY price_vnd DESC, listing_id DESC LIMIT 25;

-- name: search.area_desc.sale
-- why: sort AREA_DESC
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
ORDER BY area_m2 DESC, listing_id DESC LIMIT 25;

-- name: search.filter.district_type_price.newest
-- why: typical filtered search (one popular district, apartment, 2-5 bn VND), NEWEST
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND property_type = ANY('{APARTMENT}'::text[]) AND price_vnd >= 2000000000 AND price_vnd <= 5000000000
 AND district_code = ANY('{005}'::text[])
ORDER BY published_at DESC, listing_id DESC LIMIT 25;

-- name: search.filter.area_beds.price_asc
-- why: area 50-100 m2, >= 2 bedrooms, PRICE_ASC
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND area_m2 >= 50 AND area_m2 <= 100 AND bedrooms >= 2
ORDER BY price_vnd ASC, listing_id ASC LIMIT 25;

-- name: search.filter.rare_district.newest
-- why: least popular district (sparse match on the NEWEST order)
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND district_code = ANY('{271}'::text[])
ORDER BY published_at DESC, listing_id DESC LIMIT 25;

-- name: search.filter.rare_district_type.price_desc
-- why: sparse district + type, PRICE_DESC
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'RENT'@OWNER_ACTIVE@
 AND property_type = ANY('{VILLA}'::text[]) AND district_code = ANY('{282,281}'::text[])
ORDER BY price_vnd DESC, listing_id DESC LIMIT 25;

-- name: search.filter.verified_identity.newest
-- why: verified=IDENTITY filter
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND identity_status = 'VERIFIED' AND (identity_expires_at IS NULL OR identity_expires_at > now())
ORDER BY published_at DESC, listing_id DESC LIMIT 25;

-- name: search.keyword.common.newest
-- why: keyword (FTS on search_tsv) matching many rows, RELEVANCE->NEWEST on the database engine
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND search_tsv @@ plainto_tsquery('simple', 'can ho cau giay')
ORDER BY published_at DESC, listing_id DESC LIMIT 25;

-- name: search.keyword.rare.newest
-- why: selective keyword (a few rows)
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND search_tsv @@ plainto_tsquery('simple', 'landmarkrare')
ORDER BY published_at DESC, listing_id DESC LIMIT 25;

-- name: search.keyword.district.price_asc
-- why: keyword + district + PRICE_ASC
SELECT @SUMMARY@ FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND district_code = ANY('{019}'::text[]) AND search_tsv @@ plainto_tsquery('simple', 'nha pho')
ORDER BY price_vnd ASC, listing_id ASC LIMIT 25;

-- name: search.count.default
-- why: total of a first page (countCapped, cap 10 000 + 1)
SELECT count(*) FROM (SELECT 1 FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@ LIMIT 10001) capped;

-- name: search.count.filtered
-- why: total of the typical filtered search
SELECT count(*) FROM (SELECT 1 FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND property_type = ANY('{APARTMENT}'::text[]) AND price_vnd >= 2000000000 AND price_vnd <= 5000000000
 AND district_code = ANY('{005}'::text[]) LIMIT 10001) capped;

-- name: search.count.keyword
-- why: total of a common keyword
SELECT count(*) FROM (SELECT 1 FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND search_tsv @@ plainto_tsquery('simple', 'can ho cau giay') LIMIT 10001) capped;

-- name: search.hydrate.ids
-- why: Elasticsearch path: hydrate a page of 24 ids (findByIds), also every cached first page hit
SELECT @SUMMARY@ FROM listing_public_read WHERE listing_id = ANY(ARRAY[@page_ids@]::uuid[])@OWNER_ACTIVE@;

-- name: map.points.zoom15
-- why: map points (the app asks for points only when the zoom >= 12 count is <= 400: a ~180 x 120 m box in the densest district)
SELECT listing_id, slug, lat, lng, price_vnd, price_period, property_type FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND public_location && ST_MakeEnvelope(105.7892, 21.0302, 105.7909, 21.0313, 4326)
 AND public_location IS NOT NULL ORDER BY published_at DESC, listing_id DESC LIMIT 400;

-- name: map.count.zoom15
-- why: map total at street zoom (decides points vs clusters)
SELECT count(*) FROM (SELECT 1 FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND public_location && ST_MakeEnvelope(105.7850, 21.0280, 105.7950, 21.0340, 4326) LIMIT 10001) capped;

-- name: map.count.zoom11
-- why: map total at city zoom
SELECT count(*) FROM (SELECT 1 FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND public_location && ST_MakeEnvelope(105.70, 20.95, 105.90, 21.10, 4326) LIMIT 10001) capped;

-- name: map.clusters.zoom11
-- why: map clusters at city zoom (cell = 360/2^11/4 degrees), every match is grouped
SELECT avg(lat), avg(lng), count(*), min(lng), min(lat), max(lng), max(lat)
FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND public_location && ST_MakeEnvelope(105.70, 20.95, 105.90, 21.10, 4326)
 AND public_location IS NOT NULL GROUP BY floor(lng / 0.0439453125), floor(lat / 0.0439453125) ORDER BY count(*) DESC LIMIT 2000;

-- name: map.clusters.zoom13
-- why: map clusters at district zoom
SELECT avg(lat), avg(lng), count(*), min(lng), min(lat), max(lng), max(lat)
FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND public_location && ST_MakeEnvelope(105.77, 21.02, 105.81, 21.045, 4326)
 AND public_location IS NOT NULL GROUP BY floor(lng / 0.010986328125), floor(lat / 0.010986328125) ORDER BY count(*) DESC LIMIT 2000;

-- name: map.clusters.zoom11.filtered
-- why: city clusters with type + price filter
SELECT avg(lat), avg(lng), count(*), min(lng), min(lat), max(lng), max(lat)
FROM listing_public_read WHERE  purpose = 'SALE'@OWNER_ACTIVE@
 AND property_type = ANY('{APARTMENT}'::text[]) AND price_vnd >= 2000000000 AND price_vnd <= 5000000000
 AND public_location && ST_MakeEnvelope(105.70, 20.95, 105.90, 21.10, 4326)
 AND public_location IS NOT NULL GROUP BY floor(lng / 0.0439453125), floor(lat / 0.0439453125) ORDER BY count(*) DESC LIMIT 2000;

-- name: detail.version.slug
-- why: every public detail request by slug (findVersion: ETag and cache key)
SELECT listing_id, row_version,
       (CASE WHEN identity_status = 'VERIFIED' AND identity_expires_at <= now() THEN 'i' ELSE '' END)
    || (CASE WHEN ownership_status = 'VERIFIED' AND ownership_expires_at <= now() THEN 'o' ELSE '' END)
FROM listing_public_read WHERE  slug = '@mid_slug@'@OWNER_ACTIVE@;

-- name: detail.version.id
-- why: public detail by id (findVersion)
SELECT listing_id, row_version,
       (CASE WHEN identity_status = 'VERIFIED' AND identity_expires_at <= now() THEN 'i' ELSE '' END)
    || (CASE WHEN ownership_status = 'VERIFIED' AND ownership_expires_at <= now() THEN 'o' ELSE '' END)
FROM listing_public_read WHERE  listing_id = '@mid_id@'@OWNER_ACTIVE@;

-- name: detail.row
-- why: detail cache miss (findDetail)
SELECT @DETAIL@ FROM listing_public_read WHERE listing_id = '@mid_id@'@OWNER_ACTIVE@;

-- name: detail.gone.slug
-- why: 404/410 path for a listing that is no longer public (findGone)
SELECT l.id, l.slug,
       CASE WHEN l.status <> 'LOCKED' AND u.status = 'ACTIVE' THEN r.title END AS title
FROM listings l
JOIN listing_revisions r ON r.id = l.public_revision_id AND r.status = 'APPROVED'
LEFT JOIN users u ON u.id = l.owner_id
WHERE l.slug = '@gone_slug@' AND NOT EXISTS (SELECT 1 FROM listing_public_read p WHERE p.listing_id = l.id AND u.status = 'ACTIVE');

-- name: detail.price_history
-- why: detail price history
SELECT r.revision_number, r.price_vnd, r.price_period, r.moderated_at
FROM listing_public_read p
JOIN listing_revisions r ON r.listing_id = p.listing_id AND r.status = 'APPROVED'
     AND r.revision_number <= p.revision_number AND r.purpose = p.purpose
WHERE p.listing_id = '@mid_id@'
ORDER BY r.revision_number
LIMIT 200;

-- name: detail.similar
-- why: similar listings (same purpose/type, price +-30 %, same district first)
SELECT @SUMMARY@
 FROM listing_public_read
WHERE purpose = '@base_purpose@' AND property_type = '@base_type@' AND listing_id <> '@base_id@'
  AND price_vnd BETWEEN @base_low@ AND @base_high@@OWNER_ACTIVE@
ORDER BY (district_code IS NOT DISTINCT FROM '@base_district@') DESC, abs(price_vnd - @base_price@) ASC, listing_id ASC
LIMIT 6;

-- name: seller.page.top
-- why: public seller page of the largest seller (sellerPage)
SELECT @SUMMARY@ FROM listing_public_read WHERE owner_id = '@top_owner@'@OWNER_ACTIVE@
ORDER BY published_at DESC, listing_id DESC LIMIT 25;

-- name: seller.count.top
-- why: seller page total (sellerCountCapped)
SELECT count(*) FROM (SELECT 1 FROM listing_public_read WHERE owner_id = '@top_owner@'@OWNER_ACTIVE@ LIMIT 10001) capped;

-- name: seller.profile.top
-- why: public seller profile (counts and lead response statistics)
SELECT u.id, u.full_name, u.avatar_media_url,
       COALESCE((SELECT ur.role FROM user_roles ur WHERE ur.user_id=u.id ORDER BY CASE ur.role WHEN 'ADMIN' THEN 1 WHEN 'MODERATOR' THEN 2 WHEN 'BROKER' THEN 3 WHEN 'OWNER' THEN 4 ELSE 5 END LIMIT 1), 'USER') AS role,
       u.created_at, k.status AS kyc_status, k.verified_at, k.expires_at,
       (SELECT count(*) FROM listing_public_read p WHERE p.owner_id = u.id) AS active_listings,
       (SELECT count(*) FROM listing_public_read p WHERE p.owner_id = u.id AND p.ownership_status = 'VERIFIED'
           AND (p.ownership_expires_at IS NULL OR p.ownership_expires_at > now())) AS ownership_verified,
       stats.samples, stats.median_minutes
FROM users u
LEFT JOIN user_kyc_profiles k ON k.user_id = u.id
LEFT JOIN LATERAL (
    SELECT count(*) AS samples,
           percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (ld.first_response_at - ld.created_at)) / 60.0)
               AS median_minutes
    FROM leads ld JOIN listings l ON l.id = ld.listing_id
    WHERE l.owner_id = u.id AND ld.first_response_at IS NOT NULL AND ld.first_response_at >= ld.created_at
) stats ON TRUE
WHERE u.id = '@top_owner@' AND u.status = 'ACTIVE';

-- name: owner.counts.top
-- why: my-listings status tabs of the largest owner (JdbcOwnerListingQuery)
SELECT status, COUNT(*) FROM listings WHERE owner_id = '@top_owner@' GROUP BY status;

-- name: owner.page.all.top
-- why: my-listings page 1 (all statuses) of the largest owner
@OWNER_PAGE@ WHERE l.owner_id = '@top_owner@'
ORDER BY l.created_at DESC, l.id DESC
LIMIT 20 OFFSET 0;

-- name: owner.page.active.top
-- why: my-listings ACTIVE tab of the largest owner
@OWNER_PAGE@ WHERE l.owner_id = '@top_owner@' AND l.status = 'ACTIVE'
ORDER BY l.created_at DESC, l.id DESC
LIMIT 20 OFFSET 0;

-- name: owner.page.draft.top
-- why: my-listings DRAFT tab of the largest owner
@OWNER_PAGE@ WHERE l.owner_id = '@top_owner@' AND l.status = 'DRAFT'
ORDER BY l.created_at DESC, l.id DESC
LIMIT 20 OFFSET 0;

-- name: owner.page.deep.top
-- why: my-listings page 21 (offset paging) of the largest owner
@OWNER_PAGE@ WHERE l.owner_id = '@top_owner@'
ORDER BY l.created_at DESC, l.id DESC
LIMIT 20 OFFSET 400;

-- name: owner.page.all.small
-- why: my-listings page 1 of a typical small owner
@OWNER_PAGE@ WHERE l.owner_id = '@small_owner@'
ORDER BY l.created_at DESC, l.id DESC
LIMIT 20 OFFSET 0;

-- name: index.backfill.batch
-- why: search index rebuild/backfill keyset batch (batchAfter, 500 rows)
SELECT @SUMMARY@ FROM listing_public_read WHERE listing_id > '@mid_id@'
ORDER BY listing_id LIMIT 500;
