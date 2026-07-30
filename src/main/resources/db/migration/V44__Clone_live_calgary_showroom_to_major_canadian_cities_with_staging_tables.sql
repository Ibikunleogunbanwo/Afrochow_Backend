-- ===========================================================================
-- Afrochow Database Migration V44
-- Description: Clone the live Calgary showroom flow to major Canadian cities.
--
-- This migration copies the existing production Calgary active vendors, available products,
-- and reviews, changing only deterministic public IDs and location metadata.
--
-- Target cities:
--   Toronto ON, Vancouver BC, Winnipeg MB, Saskatoon SK,
--   Halifax NS, Moncton NB, Charlottetown PE, St Johns NL.
--
-- Safety:
--   - Deletes/replaces only seed rows using reserved showroom prefixes for the
--     target markets.
--   - Does not touch Calgary, Quebec, or real vendor/customer rows.
--   - Does not write Redis directly; the app rebuilds Redis GEO from MySQL.
-- ===========================================================================

-- ── 1. Target markets ──────────────────────────────────────────────────────

DROP TABLE IF EXISTS major_market_specs;

CREATE TABLE major_market_specs (
    market_code VARCHAR(12) PRIMARY KEY,
    city VARCHAR(100) NOT NULL,
    province VARCHAR(50) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    phone_prefix VARCHAR(6) NOT NULL,
    address_stub VARCHAR(160) NOT NULL,
    base_lat DOUBLE NOT NULL,
    base_lng DOUBLE NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO major_market_specs VALUES
('ONTOR', 'Toronto',      'ON', 'America/Toronto',     'M5V2T6', '416880', 'Showroom Toronto',      43.6532, -79.3832),
('BCVAN', 'Vancouver',    'BC', 'America/Vancouver',   'V6B1A1', '604880', 'Showroom Vancouver',    49.2827, -123.1207),
('MBWPG', 'Winnipeg',     'MB', 'America/Winnipeg',    'R3C0A1', '204880', 'Showroom Winnipeg',     49.8951, -97.1384),
('SKSSK', 'Saskatoon',    'SK', 'America/Regina',      'S7K1J5', '306880', 'Showroom Saskatoon',    52.1579, -106.6702),
('NSHAL', 'Halifax',      'NS', 'America/Halifax',     'B3J1S9', '902880', 'Showroom Halifax',      44.6488, -63.5752),
('NBMON', 'Moncton',      'NB', 'America/Moncton',     'E1C1G2', '506880', 'Showroom Moncton',      46.0878, -64.7782),
('PECHA', 'Charlottetown','PE', 'America/Halifax',     'C1A4P3', '782880', 'Showroom Charlottetown',46.2382, -63.1311),
('NLSTJ', 'St Johns',     'NL', 'America/St_Johns',    'A1C1A1', '709880', 'Showroom St Johns',     47.5615, -52.7126);

-- ── 2. Cleanup previous rows for these reserved showroom markets ───────────

DROP TABLE IF EXISTS major_seed_vendors_to_remove;
DROP TABLE IF EXISTS major_seed_vendor_users_to_remove;
DROP TABLE IF EXISTS major_seed_reviewers_to_remove;
DROP TABLE IF EXISTS major_seed_products_to_remove;
DROP TABLE IF EXISTS major_seed_addresses_to_remove;

CREATE TABLE major_seed_vendor_users_to_remove AS
SELECT u.user_id
FROM users u
JOIN major_market_specs ms
  ON u.public_user_id COLLATE utf8mb4_unicode_ci LIKE CONCAT('VEN-', ms.market_code, '%') COLLATE utf8mb4_unicode_ci
WHERE u.is_seed_data = TRUE;

CREATE TABLE major_seed_vendors_to_remove AS
SELECT vp.id AS vendor_profile_id, vp.user_id, vp.address_id
FROM vendor_profile vp
JOIN major_seed_vendor_users_to_remove mu ON mu.user_id = vp.user_id;

CREATE TABLE major_seed_reviewers_to_remove AS
SELECT u.user_id
FROM users u
JOIN major_market_specs ms
  ON u.public_user_id COLLATE utf8mb4_unicode_ci LIKE CONCAT('CUS-', ms.market_code, 'R%') COLLATE utf8mb4_unicode_ci
WHERE u.is_seed_data = TRUE;

CREATE TABLE major_seed_products_to_remove AS
SELECT p.product_id
FROM product p
LEFT JOIN major_seed_vendors_to_remove mv ON mv.vendor_profile_id = p.vendor_profile_id
JOIN major_market_specs ms
  ON p.public_product_id COLLATE utf8mb4_unicode_ci LIKE CONCAT('PROD-VEN-', ms.market_code, '%') COLLATE utf8mb4_unicode_ci
WHERE p.is_seed_data = TRUE
  AND mv.vendor_profile_id IS NOT NULL;

CREATE TABLE major_seed_addresses_to_remove AS
SELECT a.address_id
FROM address a
JOIN major_market_specs ms
  ON a.public_address_id COLLATE utf8mb4_unicode_ci LIKE CONCAT('ADDR-', ms.market_code, '-%') COLLATE utf8mb4_unicode_ci
WHERE a.is_seed_data = TRUE;

DELETE f
FROM favorite f
LEFT JOIN major_seed_vendors_to_remove mv ON mv.vendor_profile_id = f.vendor_profile_id
LEFT JOIN major_seed_products_to_remove mp ON mp.product_id = f.product_id
WHERE mv.vendor_profile_id IS NOT NULL OR mp.product_id IS NOT NULL;

DELETE r
FROM review r
LEFT JOIN major_seed_vendors_to_remove mv ON mv.vendor_profile_id = r.vendor_profile_id
LEFT JOIN major_seed_products_to_remove mp ON mp.product_id = r.product_id
LEFT JOIN major_seed_reviewers_to_remove mr ON mr.user_id = r.user_id
WHERE mv.vendor_profile_id IS NOT NULL
   OR mp.product_id IS NOT NULL
   OR mr.user_id IS NOT NULL;

DELETE p FROM product p JOIN major_seed_products_to_remove mp ON mp.product_id = p.product_id;
DELETE cp FROM customer_profile cp JOIN major_seed_reviewers_to_remove mr ON mr.user_id = cp.user_id;
DELETE vp FROM vendor_profile vp JOIN major_seed_vendors_to_remove mv ON mv.vendor_profile_id = vp.id;
DELETE a FROM address a JOIN major_seed_addresses_to_remove ma ON ma.address_id = a.address_id;

DELETE u
FROM users u
LEFT JOIN major_seed_vendor_users_to_remove mu ON mu.user_id = u.user_id
LEFT JOIN major_seed_vendors_to_remove mv ON mv.user_id = u.user_id
LEFT JOIN major_seed_reviewers_to_remove mr ON mr.user_id = u.user_id
WHERE mu.user_id IS NOT NULL OR mv.user_id IS NOT NULL OR mr.user_id IS NOT NULL;

-- ── 3. Calgary source vendors ──────────────────────────────────────────────

DROP TABLE IF EXISTS major_source_vendors;

CREATE TABLE major_source_vendors AS
SELECT ranked.*
FROM (
    SELECT
        ROW_NUMBER() OVER (ORDER BY COALESCE(c.display_order, 999), vp.id) AS slot_no,
        u.user_id AS source_user_id,
        vp.id AS source_vendor_profile_id,
        u.profile_image_url,
        u.password,
        u.first_name,
        u.last_name,
        u.auth_provider,
        vp.restaurant_name,
        vp.description,
        vp.cuisine_type,
        vp.logo_url,
        vp.banner_url,
        vp.business_license_url,
        vp.tax_id,
        vp.food_handling_cert_url,
        vp.food_handling_cert_number,
        vp.food_handling_cert_issuing_body,
        vp.food_handling_cert_expiry,
        vp.operating_hours_json,
        vp.offers_delivery,
        vp.offers_pickup,
        vp.preparation_time,
        vp.delivery_fee,
        vp.minimum_order_amount,
        vp.estimated_delivery_minutes,
        vp.max_delivery_distance_km,
        vp.total_orders_completed,
        vp.total_revenue
    FROM vendor_profile vp
    JOIN users u ON u.user_id = vp.user_id
    JOIN address a ON a.address_id = vp.address_id
    LEFT JOIN category c
           ON c.name COLLATE utf8mb4_unicode_ci = vp.cuisine_type COLLATE utf8mb4_unicode_ci
    WHERE u.role = 'VENDOR'
      AND vp.is_active = TRUE
      AND vp.is_verified = TRUE
      AND LOWER(a.city COLLATE utf8mb4_unicode_ci) = 'calgary'
) ranked
WHERE ranked.slot_no <= 20;

DROP TABLE IF EXISTS major_vendor_slots;

CREATE TABLE major_vendor_slots AS
SELECT
    ms.market_code,
    ms.city,
    ms.province,
    ms.timezone,
    sv.slot_no,
    CONCAT('VEN-', ms.market_code, LPAD(sv.slot_no, 2, '0')) AS public_user_id,
    CONCAT(LOWER(ms.market_code), '_showroom_vendor_', LPAD(sv.slot_no, 2, '0')) AS username,
    CONCAT('showroom.', LOWER(ms.market_code), '.vendor', LPAD(sv.slot_no, 2, '0'), '@afrochow.ca') AS email,
    CONCAT(ms.phone_prefix, LPAD(sv.slot_no, 4, '0')) AS phone,
    CONCAT('ADDR-', ms.market_code, '-', LPAD(sv.slot_no, 3, '0')) AS public_address_id,
    CONCAT((100 + sv.slot_no), ' ', ms.address_stub, ' ', LPAD(sv.slot_no, 2, '0')) AS address_line,
    ms.postal_code,
    ms.base_lat + (((CAST(sv.slot_no AS SIGNED) - 1) MOD 5) - 2) * 0.0100 AS latitude,
    ms.base_lng + (FLOOR((CAST(sv.slot_no AS SIGNED) - 1) / 5) - 1.5) * 0.0120 AS longitude
FROM major_market_specs ms
CROSS JOIN major_source_vendors sv;

-- ── 4. Clone users, addresses, vendors ─────────────────────────────────────

INSERT INTO users (
    username, public_user_id, email, profile_image_url, password, google_id,
    first_name, last_name, phone, role, auth_provider, is_active,
    scheduled_for_deletion_at, email_verified, accept_terms, is_seed_data,
    created_at, updated_at, last_login_at
)
SELECT
    vs.username,
    vs.public_user_id,
    vs.email,
    sv.profile_image_url,
    sv.password,
    NULL,
    sv.first_name,
    sv.last_name,
    vs.phone,
    'VENDOR',
    COALESCE(sv.auth_provider, 'EMAIL'),
    TRUE,
    NULL,
    TRUE,
    TRUE,
    TRUE,
    NOW(),
    NOW(),
    NULL
FROM major_source_vendors sv
JOIN major_vendor_slots vs ON vs.slot_no = sv.slot_no;

INSERT INTO address (
    public_address_id, address_line, city, province, postal_code, country,
    latitude, longitude, default_address, is_seed_data, customer_profile_id,
    created_at, updated_at
)
SELECT
    vs.public_address_id,
    vs.address_line,
    vs.city,
    vs.province,
    vs.postal_code,
    'Canada',
    vs.latitude,
    vs.longitude,
    FALSE,
    TRUE,
    NULL,
    NOW(),
    NOW()
FROM major_vendor_slots vs;

INSERT INTO vendor_profile (
    user_id, restaurant_name, description, cuisine_type, logo_url, banner_url,
    stripe_account_id, stripe_onboarding_complete, is_seed_data, vendor_status,
    business_license_url, tax_id, is_verified, is_active, verified_at,
    food_handling_cert_url, food_handling_cert_number, food_handling_cert_issuing_body,
    food_handling_cert_expiry, cert_verified_at, cert_verified_by_admin_id,
    timezone, operating_hours_json, offers_delivery, offers_pickup, preparation_time,
    delivery_fee, minimum_order_amount, estimated_delivery_minutes,
    max_delivery_distance_km, address_id, total_orders_completed, total_revenue,
    created_at, updated_at
)
SELECT
    target_user.user_id,
    REPLACE(REPLACE(sv.restaurant_name, 'Calgary', vs.city), 'YYC', vs.city),
    REPLACE(REPLACE(COALESCE(sv.description, ''), 'Calgary', vs.city), 'YYC', vs.city),
    sv.cuisine_type,
    sv.logo_url,
    sv.banner_url,
    NULL,
    FALSE,
    TRUE,
    'VERIFIED',
    REPLACE(REPLACE(COALESCE(sv.business_license_url, ''), 'calgary', LOWER(vs.city)), 'Calgary', vs.city),
    sv.tax_id,
    TRUE,
    TRUE,
    NOW(),
    sv.food_handling_cert_url,
    sv.food_handling_cert_number,
    sv.food_handling_cert_issuing_body,
    sv.food_handling_cert_expiry,
    NOW(),
    NULL,
    vs.timezone,
    sv.operating_hours_json,
    sv.offers_delivery,
    sv.offers_pickup,
    sv.preparation_time,
    sv.delivery_fee,
    sv.minimum_order_amount,
    sv.estimated_delivery_minutes,
    sv.max_delivery_distance_km,
    target_address.address_id,
    sv.total_orders_completed,
    sv.total_revenue,
    NOW(),
    NOW()
FROM major_source_vendors sv
JOIN major_vendor_slots vs ON vs.slot_no = sv.slot_no
JOIN users target_user
     ON target_user.public_user_id COLLATE utf8mb4_unicode_ci = vs.public_user_id COLLATE utf8mb4_unicode_ci
JOIN address target_address
     ON target_address.public_address_id COLLATE utf8mb4_unicode_ci = vs.public_address_id COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS major_vendor_map;

CREATE TABLE major_vendor_map AS
SELECT
    vs.market_code,
    vs.slot_no,
    sv.source_vendor_profile_id,
    target_vp.id AS target_vendor_profile_id,
    vs.public_user_id AS target_public_user_id,
    vs.city
FROM major_source_vendors sv
JOIN major_vendor_slots vs ON vs.slot_no = sv.slot_no
JOIN users target_user
     ON target_user.public_user_id COLLATE utf8mb4_unicode_ci = vs.public_user_id COLLATE utf8mb4_unicode_ci
JOIN vendor_profile target_vp ON target_vp.user_id = target_user.user_id;

-- ── 5. Clone products into the matching city vendors ───────────────────────

DROP TABLE IF EXISTS major_source_products;

CREATE TABLE major_source_products AS
SELECT
    product_ranked.*,
    CONCAT('PROD-', product_ranked.target_public_user_id, '-', LPAD(product_ranked.product_slot, 2, '0')) AS target_public_product_id
FROM (
    SELECT
        ROW_NUMBER() OVER (PARTITION BY vm.market_code, p.vendor_profile_id ORDER BY p.product_id) AS product_slot,
        p.product_id AS source_product_id,
        p.vendor_profile_id AS source_vendor_profile_id,
        vm.market_code,
        vm.city,
        vm.target_vendor_profile_id,
        vm.target_public_user_id,
        p.category_id,
        p.name,
        p.description,
        p.price,
        p.image_url,
        p.available,
        p.admin_visible,
        p.preparation_time_minutes,
        p.schedule_type,
        p.advance_notice_hours,
        p.calories,
        p.is_vegetarian,
        p.is_vegan,
        p.is_gluten_free,
        p.is_spicy,
        p.is_featured
    FROM product p
    JOIN major_vendor_map vm ON vm.source_vendor_profile_id = p.vendor_profile_id
    WHERE p.available = TRUE
      AND p.admin_visible = TRUE
) product_ranked;

INSERT INTO product (
    version, public_product_id, name, description, price, image_url,
    available, admin_visible, is_seed_data, preparation_time_minutes,
    schedule_type, advance_notice_hours, calories, is_vegetarian, is_vegan,
    is_gluten_free, is_spicy, is_featured, featured_at, vendor_profile_id,
    category_id, created_at, updated_at
)
SELECT
    0,
    sp.target_public_product_id,
    sp.name,
    REPLACE(REPLACE(COALESCE(sp.description, ''), 'Calgary', sp.city), 'YYC', sp.city),
    sp.price,
    sp.image_url,
    sp.available,
    sp.admin_visible,
    TRUE,
    sp.preparation_time_minutes,
    sp.schedule_type,
    sp.advance_notice_hours,
    sp.calories,
    sp.is_vegetarian,
    sp.is_vegan,
    sp.is_gluten_free,
    sp.is_spicy,
    sp.is_featured,
    CASE WHEN sp.is_featured THEN NOW() ELSE NULL END,
    sp.target_vendor_profile_id,
    sp.category_id,
    NOW(),
    NOW()
FROM major_source_products sp;

DROP TABLE IF EXISTS major_product_map;

CREATE TABLE major_product_map AS
SELECT
    sp.market_code,
    sp.source_product_id,
    target_product.product_id AS target_product_id
FROM major_source_products sp
JOIN product target_product
     ON target_product.public_product_id COLLATE utf8mb4_unicode_ci =
        sp.target_public_product_id COLLATE utf8mb4_unicode_ci;

-- ── 6. Clone seed reviews/reviewers for ratings consistency ────────────────

DROP TABLE IF EXISTS major_source_reviews;

CREATE TABLE major_source_reviews AS
SELECT
    sr_ranked.*
FROM (
    SELECT
        vm.market_code,
        vm.city,
        DENSE_RANK() OVER (PARTITION BY vm.market_code ORDER BY reviewer.user_id) AS reviewer_slot,
        r.review_id AS source_review_id,
        reviewer.first_name,
        reviewer.last_name,
        reviewer.profile_image_url,
        reviewer.password,
        reviewer.auth_provider,
        vm.target_vendor_profile_id,
        pm.target_product_id,
        r.rating,
        r.comment,
        r.helpful_count,
        r.is_visible
    FROM review r
    JOIN users reviewer ON reviewer.user_id = r.user_id
    JOIN major_vendor_map vm ON vm.source_vendor_profile_id = r.vendor_profile_id
    LEFT JOIN major_product_map pm
           ON pm.market_code = vm.market_code
          AND pm.source_product_id = r.product_id
    WHERE r.is_visible = TRUE
) sr_ranked;

INSERT INTO users (
    username, public_user_id, email, profile_image_url, password, google_id,
    first_name, last_name, phone, role, auth_provider, is_active,
    scheduled_for_deletion_at, email_verified, accept_terms, is_seed_data,
    created_at, updated_at, last_login_at
)
SELECT
    CONCAT(LOWER(msr.market_code), '_showroom_reviewer_', LPAD(msr.reviewer_slot, 3, '0')),
    CONCAT('CUS-', msr.market_code, 'R', LPAD(msr.reviewer_slot, 3, '0')),
    CONCAT('showroom.', LOWER(msr.market_code), '.reviewer', LPAD(msr.reviewer_slot, 3, '0'), '@afrochow.ca'),
    MAX(msr.profile_image_url),
    MAX(msr.password),
    NULL,
    COALESCE(MAX(msr.first_name), msr.city),
    COALESCE(MAX(msr.last_name), 'Reviewer'),
    CONCAT(MAX(ms.phone_prefix), '9', LPAD(msr.reviewer_slot, 3, '0')),
    'CUSTOMER',
    COALESCE(MAX(msr.auth_provider), 'EMAIL'),
    TRUE,
    NULL,
    TRUE,
    TRUE,
    TRUE,
    NOW(),
    NOW(),
    NULL
FROM major_source_reviews msr
JOIN major_market_specs ms ON ms.market_code = msr.market_code
GROUP BY msr.market_code, msr.city, msr.reviewer_slot;

INSERT INTO customer_profile (
    user_id, default_delivery_instructions, payment_method, loyalty_points,
    notifications_enabled, is_seed_data, created_at, updated_at
)
SELECT
    u.user_id,
    CONCAT(ms.city, ' showroom reviewer cloned from Calgary seed flow'),
    'CREDIT_CARD',
    100,
    TRUE,
    TRUE,
    NOW(),
    NOW()
FROM users u
JOIN major_market_specs ms
  ON u.public_user_id COLLATE utf8mb4_unicode_ci LIKE CONCAT('CUS-', ms.market_code, 'R%') COLLATE utf8mb4_unicode_ci
WHERE u.is_seed_data = TRUE;

INSERT INTO review (
    user_id, vendor_profile_id, product_id, order_id, rating, comment,
    helpful_count, is_visible, is_seed_data, created_at, updated_at
)
SELECT
    target_reviewer.user_id,
    msr.target_vendor_profile_id,
    msr.target_product_id,
    NULL,
    msr.rating,
    REPLACE(REPLACE(COALESCE(msr.comment, ''), 'Calgary', msr.city), 'YYC', msr.city),
    msr.helpful_count,
    msr.is_visible,
    TRUE,
    NOW(),
    NOW()
FROM major_source_reviews msr
JOIN users target_reviewer
     ON target_reviewer.public_user_id COLLATE utf8mb4_unicode_ci =
        CONCAT('CUS-', msr.market_code, 'R', LPAD(msr.reviewer_slot, 3, '0')) COLLATE utf8mb4_unicode_ci;

-- ── 7. Cleanup temp tables ─────────────────────────────────────────────────

DROP TABLE IF EXISTS major_source_reviews;
DROP TABLE IF EXISTS major_product_map;
DROP TABLE IF EXISTS major_source_products;
DROP TABLE IF EXISTS major_vendor_map;
DROP TABLE IF EXISTS major_vendor_slots;
DROP TABLE IF EXISTS major_source_vendors;
DROP TABLE IF EXISTS major_seed_addresses_to_remove;
DROP TABLE IF EXISTS major_seed_products_to_remove;
DROP TABLE IF EXISTS major_seed_reviewers_to_remove;
DROP TABLE IF EXISTS major_seed_vendor_users_to_remove;
DROP TABLE IF EXISTS major_seed_vendors_to_remove;
DROP TABLE IF EXISTS major_market_specs;

-- ===========================================================================
-- End of V44 migration
-- ===========================================================================
