-- Stream S2-SEARCH, Review 2 fix: the search corpus (listing_public_read.search_text/search_tsv and the Elasticsearch
-- search_text field built from it) must not contain contact details. V033 built it from the raw title, address and
-- description, so a phone number or e-mail typed as a keyword matched the listing ("does listing X contain phone Y").
-- bds_redact_contact mirrors com.company.bds.shared.security.ContactInfoGuard (parity-tested); the refresh function now
-- redacts before normalising. Existing rows are refreshed here (PostgreSQL immediately) and re-enqueued for the index.
-- Rollback: redeploy the previous image; this function version stays (it only removes matches), no data is lost.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

-- Replaces links, e-mails and Vietnamese phone numbers (in that order, like ContactInfoGuard.redact) by p_replacement.
CREATE OR REPLACE FUNCTION bds_redact_contact(p_text text, p_replacement text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
AS $$
SELECT regexp_replace(
         regexp_replace(
           regexp_replace(p_text,
             '(?:https?://\S+|www\.\S+|\y(?:zalo\.me|facebook\.com|fb\.com|fb\.me|m\.me|t\.me|wa\.me|messenger\.com)/?\S*)',
             p_replacement, 'gi'),
           '[A-Za-z0-9._%+-]+\s*(?:@|\(at\)|\[at\])\s*[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}',
           p_replacement, 'gi'),
         '(?<![0-9.,])(?:\+?84[[:space:].-]?|0)(?:[35789](?:[[:space:].-]?[0-9]){8}|2(?:[[:space:].-]?[0-9]){9})(?![0-9])',
         p_replacement, 'g')
$$;

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
        -- free text is redacted exactly like the public display (ContactInfoGuard) before it becomes searchable
        SELECT coalesce(bds_search_normalize(concat_ws(' ', bds_redact_contact(r.title, ' '),
                   bds_redact_contact(r.address_summary, ' '), loc.name, array_to_string(loc.aliases, ' '),
                   loc.province_name, p.name, bds_redact_contact(r.description, ' '))), '') AS text
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

-- Re-derive the search text of every public row now (versions increase), then let the search-index job rewrite the
-- Elasticsearch documents (coalesced per listing; it refreshes again, which is harmless).
SELECT count(*) FROM listings l, LATERAL bds_refresh_listing_public_read(l.id) f WHERE l.status = 'ACTIVE';
SELECT count(*) FROM (SELECT bds_enqueue_listing_index(listing_id) FROM listing_public_read) q;
