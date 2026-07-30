-- ===========================================================================
-- Afrochow Database Migration V40
-- Description: Replace the hand-authored Quebec showroom with Calgary clones.
--
-- V39 created a small standalone Quebec sample set. The Calgary production
-- showroom is the flow we trust, so this migration copies the existing Calgary
-- seed vendors/products/reviews from prod MySQL and changes only the Quebec
-- identity/location fields.
--
-- Safety:
--   - Deletes only seed rows with reserved Quebec prefixes:
--     VEN-QC%, CUS-QCR%, PROD-QC%, PROD-VEN-QC%, ADDR-QC-%.
--   - Does not touch real vendors/customers.
--   - Does not write Redis directly; the app rebuilds Redis GEO from MySQL.
-- ===========================================================================

-- ── 1. Cleanup the previous Quebec showroom rows ───────────────────────────

DROP TEMPORARY TABLE IF EXISTS qc_seed_vendors_to_remove;
DROP TEMPORARY TABLE IF EXISTS qc_seed_reviewers_to_remove;
DROP TEMPORARY TABLE IF EXISTS qc_seed_products_to_remove;
DROP TEMPORARY TABLE IF EXISTS qc_seed_addresses_to_remove;

CREATE TEMPORARY TABLE qc_seed_vendors_to_remove AS
SELECT vp.id AS vendor_profile_id, vp.user_id, vp.address_id
FROM vendor_profile vp
JOIN users u ON u.user_id = vp.user_id
WHERE u.is_seed_data = TRUE
  AND u.public_user_id COLLATE utf8mb4_unicode_ci LIKE 'VEN-QC%';

CREATE TEMPORARY TABLE qc_seed_reviewers_to_remove AS
SELECT u.user_id
FROM users u
WHERE u.is_seed_data = TRUE
  AND u.public_user_id COLLATE utf8mb4_unicode_ci LIKE 'CUS-QCR%';

CREATE TEMPORARY TABLE qc_seed_products_to_remove AS
SELECT p.product_id
FROM product p
LEFT JOIN qc_seed_vendors_to_remove qv ON qv.vendor_profile_id = p.vendor_profile_id
WHERE p.is_seed_data = TRUE
  AND (
       qv.vendor_profile_id IS NOT NULL
       OR p.public_product_id COLLATE utf8mb4_unicode_ci LIKE 'PROD-QC%'
       OR p.public_product_id COLLATE utf8mb4_unicode_ci LIKE 'PROD-VEN-QC%'
  );

CREATE TEMPORARY TABLE qc_seed_addresses_to_remove AS
SELECT a.address_id
FROM address a
WHERE a.is_seed_data = TRUE
  AND a.public_address_id COLLATE utf8mb4_unicode_ci LIKE 'ADDR-QC-%';

DELETE f
FROM favorite f
LEFT JOIN qc_seed_vendors_to_remove qv ON qv.vendor_profile_id = f.vendor_profile_id
LEFT JOIN qc_seed_products_to_remove qp ON qp.product_id = f.product_id
WHERE qv.vendor_profile_id IS NOT NULL OR qp.product_id IS NOT NULL;

DELETE r
FROM review r
LEFT JOIN qc_seed_vendors_to_remove qv ON qv.vendor_profile_id = r.vendor_profile_id
LEFT JOIN qc_seed_products_to_remove qp ON qp.product_id = r.product_id
LEFT JOIN qc_seed_reviewers_to_remove qr ON qr.user_id = r.user_id
WHERE qv.vendor_profile_id IS NOT NULL
   OR qp.product_id IS NOT NULL
   OR qr.user_id IS NOT NULL;

DELETE p FROM product p JOIN qc_seed_products_to_remove qp ON qp.product_id = p.product_id;
DELETE cp FROM customer_profile cp JOIN qc_seed_reviewers_to_remove qr ON qr.user_id = cp.user_id;
DELETE vp FROM vendor_profile vp JOIN qc_seed_vendors_to_remove qv ON qv.vendor_profile_id = vp.id;
DELETE a FROM address a JOIN qc_seed_addresses_to_remove qa ON qa.address_id = a.address_id;

DELETE u
FROM users u
LEFT JOIN qc_seed_vendors_to_remove qv ON qv.user_id = u.user_id
LEFT JOIN qc_seed_reviewers_to_remove qr ON qr.user_id = u.user_id
WHERE qv.user_id IS NOT NULL OR qr.user_id IS NOT NULL;

-- ── 2. Quebec target locations for the Calgary clone set ───────────────────

DROP TEMPORARY TABLE IF EXISTS qc_vendor_slots;

CREATE TEMPORARY TABLE qc_vendor_slots (
    slot_no INT PRIMARY KEY,
    public_user_id VARCHAR(16) NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    public_address_id VARCHAR(80) NOT NULL,
    address_line VARCHAR(200) NOT NULL,
    city VARCHAR(100) NOT NULL,
    province VARCHAR(50) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO qc_vendor_slots VALUES
(1,  'VEN-QCMTL01', 'qc_showroom_vendor_01', 'showroom.qc.vendor01@afrochow.ca', '5147770101', 'ADDR-QC-MTL-001', '5450 Avenue du Parc', 'Montreal', 'QC', 'H2V4G7', 45.5220, -73.5967),
(2,  'VEN-QCMTL02', 'qc_showroom_vendor_02', 'showroom.qc.vendor02@afrochow.ca', '5147770102', 'ADDR-QC-MTL-002', '3509 Boulevard Saint-Laurent', 'Montreal', 'QC', 'H2X2T6', 45.5146, -73.5747),
(3,  'VEN-QCMTL03', 'qc_showroom_vendor_03', 'showroom.qc.vendor03@afrochow.ca', '5147770103', 'ADDR-QC-MTL-003', '7070 Avenue Henri-Julien', 'Montreal', 'QC', 'H2S3S3', 45.5350, -73.6140),
(4,  'VEN-QCMTL04', 'qc_showroom_vendor_04', 'showroom.qc.vendor04@afrochow.ca', '5147770104', 'ADDR-QC-MTL-004', '1240 Rue Stanley', 'Montreal', 'QC', 'H3B2S7', 45.4995, -73.5719),
(5,  'VEN-QCMTL05', 'qc_showroom_vendor_05', 'showroom.qc.vendor05@afrochow.ca', '5147770105', 'ADDR-QC-MTL-005', '4634 Rue Wellington', 'Montreal', 'QC', 'H4G1W7', 45.4551, -73.5678),
(6,  'VEN-QCMTL06', 'qc_showroom_vendor_06', 'showroom.qc.vendor06@afrochow.ca', '5147770106', 'ADDR-QC-MTL-006', '6700 Chemin de la Cote-des-Neiges', 'Montreal', 'QC', 'H3S2B2', 45.5069, -73.6290),
(7,  'VEN-QCMTL07', 'qc_showroom_vendor_07', 'showroom.qc.vendor07@afrochow.ca', '5147770107', 'ADDR-QC-MTL-007', '2000 Rue Sainte-Catherine O', 'Montreal', 'QC', 'H3H2T2', 45.4935, -73.5801),
(8,  'VEN-QCMTL08', 'qc_showroom_vendor_08', 'showroom.qc.vendor08@afrochow.ca', '5147770108', 'ADDR-QC-MTL-008', '4101 Rue Sherbrooke O', 'Montreal', 'QC', 'H3Z1A7', 45.4776, -73.6046),
(9,  'VEN-QCMTL09', 'qc_showroom_vendor_09', 'showroom.qc.vendor09@afrochow.ca', '5147770109', 'ADDR-QC-MTL-009', '5333 Avenue Casgrain', 'Montreal', 'QC', 'H2T1X3', 45.5265, -73.5985),
(10, 'VEN-QCMTL10', 'qc_showroom_vendor_10', 'showroom.qc.vendor10@afrochow.ca', '5147770110', 'ADDR-QC-MTL-010', '5600 Rue Jean-Talon E', 'Montreal', 'QC', 'H1S1M2', 45.5844, -73.5846),
(11, 'VEN-QCLAV01', 'qc_showroom_vendor_11', 'showroom.qc.vendor11@afrochow.ca', '4507770101', 'ADDR-QC-LAV-001', '1600 Boulevard Le Corbusier', 'Laval', 'QC', 'H7S1Y9', 45.5632, -73.7310),
(12, 'VEN-QCLAV02', 'qc_showroom_vendor_12', 'showroom.qc.vendor12@afrochow.ca', '4507770102', 'ADDR-QC-LAV-002', '3035 Boulevard Le Carrefour', 'Laval', 'QC', 'H7T1C8', 45.5687, -73.7486),
(13, 'VEN-QCLAV03', 'qc_showroom_vendor_13', 'showroom.qc.vendor13@afrochow.ca', '4507770103', 'ADDR-QC-LAV-003', '1950 Rue Claude-Gagne', 'Laval', 'QC', 'H7N5H9', 45.5579, -73.7156),
(14, 'VEN-QCLAV04', 'qc_showroom_vendor_14', 'showroom.qc.vendor14@afrochow.ca', '4507770104', 'ADDR-QC-LAV-004', '255 Boulevard de la Concorde O', 'Laval', 'QC', 'H7N5T1', 45.5638, -73.6995),
(15, 'VEN-QCLAV05', 'qc_showroom_vendor_15', 'showroom.qc.vendor15@afrochow.ca', '4507770105', 'ADDR-QC-LAV-005', '3225 Boulevard Saint-Martin O', 'Laval', 'QC', 'H7T1S2', 45.5539, -73.7607),
(16, 'VEN-QCLAV06', 'qc_showroom_vendor_16', 'showroom.qc.vendor16@afrochow.ca', '4507770106', 'ADDR-QC-LAV-006', '3025 Avenue des Aristocrates', 'Laval', 'QC', 'H7E5H7', 45.6071, -73.6510),
(17, 'VEN-QCLAV07', 'qc_showroom_vendor_17', 'showroom.qc.vendor17@afrochow.ca', '4507770107', 'ADDR-QC-LAV-007', '4415 Boulevard Saint-Martin O', 'Laval', 'QC', 'H7T1C6', 45.5483, -73.7794),
(18, 'VEN-QCLAV08', 'qc_showroom_vendor_18', 'showroom.qc.vendor18@afrochow.ca', '4507770108', 'ADDR-QC-LAV-008', '1177 Autoroute 13', 'Laval', 'QC', 'H7X4C9', 45.5291, -73.8060),
(19, 'VEN-QCLAV09', 'qc_showroom_vendor_19', 'showroom.qc.vendor19@afrochow.ca', '4507770109', 'ADDR-QC-LAV-009', '1800 Boulevard des Laurentides', 'Laval', 'QC', 'H7M2P6', 45.5907, -73.7173),
(20, 'VEN-QCLAV10', 'qc_showroom_vendor_20', 'showroom.qc.vendor20@afrochow.ca', '4507770110', 'ADDR-QC-LAV-010', '4600 Boulevard Samson', 'Laval', 'QC', 'H7W2H3', 45.5277, -73.7895);

-- ── 3. Use prod Calgary seed vendors as the source of truth ────────────────

DROP TEMPORARY TABLE IF EXISTS qc_source_vendors;

CREATE TEMPORARY TABLE qc_source_vendors AS
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
    WHERE vp.is_seed_data = TRUE
      AND u.is_seed_data = TRUE
      AND u.role = 'VENDOR'
      AND vp.is_active = TRUE
      AND vp.is_verified = TRUE
      AND a.city COLLATE utf8mb4_unicode_ci = 'Calgary'
) ranked
WHERE ranked.slot_no <= 20;

-- ── 4. Clone users, addresses, vendors ─────────────────────────────────────

INSERT INTO users (
    username, public_user_id, email, profile_image_url, password, google_id,
    first_name, last_name, phone, role, auth_provider, is_active,
    scheduled_for_deletion_at, email_verified, accept_terms, is_seed_data,
    created_at, updated_at, last_login_at
)
SELECT
    qs.username,
    qs.public_user_id,
    qs.email,
    sv.profile_image_url,
    sv.password,
    NULL,
    sv.first_name,
    sv.last_name,
    qs.phone,
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
FROM qc_source_vendors sv
JOIN qc_vendor_slots qs ON qs.slot_no = sv.slot_no;

INSERT INTO address (
    public_address_id, address_line, city, province, postal_code, country,
    latitude, longitude, default_address, is_seed_data, customer_profile_id,
    created_at, updated_at
)
SELECT
    qs.public_address_id,
    qs.address_line,
    qs.city,
    qs.province,
    qs.postal_code,
    'Canada',
    qs.latitude,
    qs.longitude,
    FALSE,
    TRUE,
    NULL,
    NOW(),
    NOW()
FROM qc_source_vendors sv
JOIN qc_vendor_slots qs ON qs.slot_no = sv.slot_no;

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
    REPLACE(REPLACE(sv.restaurant_name, 'Calgary', qs.city), 'YYC', qs.city),
    sv.description,
    sv.cuisine_type,
    sv.logo_url,
    sv.banner_url,
    NULL,
    FALSE,
    TRUE,
    'VERIFIED',
    REPLACE(REPLACE(COALESCE(sv.business_license_url, ''), 'calgary', LOWER(qs.city)), 'Calgary', qs.city),
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
    'America/Montreal',
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
FROM qc_source_vendors sv
JOIN qc_vendor_slots qs ON qs.slot_no = sv.slot_no
JOIN users target_user
     ON target_user.public_user_id COLLATE utf8mb4_unicode_ci = qs.public_user_id COLLATE utf8mb4_unicode_ci
JOIN address target_address
     ON target_address.public_address_id COLLATE utf8mb4_unicode_ci = qs.public_address_id COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS qc_vendor_map;

CREATE TEMPORARY TABLE qc_vendor_map AS
SELECT
    sv.slot_no,
    sv.source_vendor_profile_id,
    target_vp.id AS target_vendor_profile_id,
    qs.public_user_id AS target_public_user_id
FROM qc_source_vendors sv
JOIN qc_vendor_slots qs ON qs.slot_no = sv.slot_no
JOIN users target_user
     ON target_user.public_user_id COLLATE utf8mb4_unicode_ci = qs.public_user_id COLLATE utf8mb4_unicode_ci
JOIN vendor_profile target_vp ON target_vp.user_id = target_user.user_id;

-- ── 5. Clone products into the matching Quebec vendors ─────────────────────

DROP TEMPORARY TABLE IF EXISTS qc_source_products;

CREATE TEMPORARY TABLE qc_source_products AS
SELECT
    product_ranked.*,
    CONCAT('PROD-', vm.target_public_user_id, '-', LPAD(product_ranked.product_slot, 2, '0')) AS target_public_product_id
FROM (
    SELECT
        ROW_NUMBER() OVER (PARTITION BY p.vendor_profile_id ORDER BY p.product_id) AS product_slot,
        p.product_id AS source_product_id,
        p.vendor_profile_id AS source_vendor_profile_id,
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
    JOIN qc_vendor_map source_map ON source_map.source_vendor_profile_id = p.vendor_profile_id
    WHERE p.is_seed_data = TRUE
) product_ranked
JOIN qc_vendor_map vm ON vm.source_vendor_profile_id = product_ranked.source_vendor_profile_id;

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
    sp.description,
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
    vm.target_vendor_profile_id,
    sp.category_id,
    NOW(),
    NOW()
FROM qc_source_products sp
JOIN qc_vendor_map vm ON vm.source_vendor_profile_id = sp.source_vendor_profile_id;

DROP TEMPORARY TABLE IF EXISTS qc_product_map;

CREATE TEMPORARY TABLE qc_product_map AS
SELECT sp.source_product_id, target_product.product_id AS target_product_id
FROM qc_source_products sp
JOIN product target_product
     ON target_product.public_product_id COLLATE utf8mb4_unicode_ci =
        sp.target_public_product_id COLLATE utf8mb4_unicode_ci;

-- ── 6. Clone seed reviews/reviewers for ratings consistency ────────────────

DROP TEMPORARY TABLE IF EXISTS qc_source_reviews;

CREATE TEMPORARY TABLE qc_source_reviews AS
SELECT
    DENSE_RANK() OVER (ORDER BY reviewer.user_id) AS reviewer_slot,
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
JOIN qc_vendor_map vm ON vm.source_vendor_profile_id = r.vendor_profile_id
LEFT JOIN qc_product_map pm ON pm.source_product_id = r.product_id
WHERE r.is_seed_data = TRUE;

INSERT INTO users (
    username, public_user_id, email, profile_image_url, password, google_id,
    first_name, last_name, phone, role, auth_provider, is_active,
    scheduled_for_deletion_at, email_verified, accept_terms, is_seed_data,
    created_at, updated_at, last_login_at
)
SELECT
    CONCAT('qc_showroom_reviewer_', LPAD(reviewer_slot, 3, '0')),
    CONCAT('CUS-QCR', LPAD(reviewer_slot, 3, '0')),
    CONCAT('showroom.qc.reviewer', LPAD(reviewer_slot, 3, '0'), '@afrochow.ca'),
    MAX(profile_image_url),
    MAX(password),
    NULL,
    COALESCE(MAX(first_name), 'Quebec'),
    COALESCE(MAX(last_name), 'Reviewer'),
    CONCAT('438777', LPAD(reviewer_slot, 4, '0')),
    'CUSTOMER',
    COALESCE(MAX(auth_provider), 'EMAIL'),
    TRUE,
    NULL,
    TRUE,
    TRUE,
    TRUE,
    NOW(),
    NOW(),
    NULL
FROM qc_source_reviews
GROUP BY reviewer_slot;

INSERT INTO customer_profile (
    user_id, default_delivery_instructions, payment_method, loyalty_points,
    notifications_enabled, is_seed_data, created_at, updated_at
)
SELECT
    u.user_id,
    'Quebec showroom reviewer cloned from Calgary seed flow',
    'CREDIT_CARD',
    100,
    TRUE,
    TRUE,
    NOW(),
    NOW()
FROM users u
WHERE u.public_user_id COLLATE utf8mb4_unicode_ci LIKE 'CUS-QCR%';

INSERT INTO review (
    user_id, vendor_profile_id, product_id, order_id, rating, comment,
    helpful_count, is_visible, is_seed_data, created_at, updated_at
)
SELECT
    target_reviewer.user_id,
    sr.target_vendor_profile_id,
    sr.target_product_id,
    NULL,
    sr.rating,
    sr.comment,
    sr.helpful_count,
    sr.is_visible,
    TRUE,
    NOW(),
    NOW()
FROM qc_source_reviews sr
JOIN users target_reviewer
     ON target_reviewer.public_user_id COLLATE utf8mb4_unicode_ci =
        CONCAT('CUS-QCR', LPAD(sr.reviewer_slot, 3, '0')) COLLATE utf8mb4_unicode_ci;

-- ── 7. Cleanup temp tables ─────────────────────────────────────────────────

DROP TEMPORARY TABLE IF EXISTS qc_source_reviews;
DROP TEMPORARY TABLE IF EXISTS qc_product_map;
DROP TEMPORARY TABLE IF EXISTS qc_source_products;
DROP TEMPORARY TABLE IF EXISTS qc_vendor_map;
DROP TEMPORARY TABLE IF EXISTS qc_source_vendors;
DROP TEMPORARY TABLE IF EXISTS qc_vendor_slots;
DROP TEMPORARY TABLE IF EXISTS qc_seed_addresses_to_remove;
DROP TEMPORARY TABLE IF EXISTS qc_seed_products_to_remove;
DROP TEMPORARY TABLE IF EXISTS qc_seed_reviewers_to_remove;
DROP TEMPORARY TABLE IF EXISTS qc_seed_vendors_to_remove;

-- ===========================================================================
-- End of V40 migration
-- ===========================================================================
