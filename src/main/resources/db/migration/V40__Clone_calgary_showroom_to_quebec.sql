-- ===========================================================================
-- Afrochow Database Migration V40
-- Description: Replace the hand-authored Quebec showroom with cloned Calgary
--              showroom data, changing only identifiers and location details.
--
-- Why this exists:
--   V39 created Quebec-specific sample vendors/products by hand. The product
--   goal is now different: keep the already-proven Calgary showroom flow as the
--   source of truth and replicate that same catalog/ratings/vendor setup into
--   Quebec so the search/order/payment experience behaves the same by city.
--
-- Safety:
--   This migration deletes only rows using reserved Quebec showroom prefixes:
--     VEN-QC*, PROD-QC*, CUS-QC*, ADDR-QC*
--   It does not touch real vendor/customer rows or the Calgary source rows.
--
-- Redis GEO note:
--   No Redis writes are needed here. VendorGeoIndexService rebuilds the Redis
--   vendor geo index from active, verified, geocoded vendors on app startup and
--   on its scheduled refresh.
-- ===========================================================================

-- ── 1. Remove V39 Quebec showroom rows by reserved public-id prefix ─────────

DROP TEMPORARY TABLE IF EXISTS qc_seed_vendors_to_remove;
DROP TEMPORARY TABLE IF EXISTS qc_seed_reviewers_to_remove;
DROP TEMPORARY TABLE IF EXISTS qc_seed_products_to_remove;
DROP TEMPORARY TABLE IF EXISTS qc_seed_addresses_to_remove;

CREATE TEMPORARY TABLE qc_seed_vendors_to_remove AS
SELECT vp.id AS vendor_profile_id, u.user_id, vp.address_id
FROM vendor_profile vp
JOIN users u ON u.user_id = vp.user_id
WHERE u.public_user_id LIKE 'VEN-QC%';

CREATE TEMPORARY TABLE qc_seed_reviewers_to_remove AS
SELECT cp.customer_profile_id, u.user_id
FROM customer_profile cp
JOIN users u ON u.user_id = cp.user_id
WHERE u.public_user_id LIKE 'CUS-QC%';

CREATE TEMPORARY TABLE qc_seed_products_to_remove AS
SELECT p.product_id
FROM product p
WHERE p.public_product_id LIKE 'PROD-QC%'
   OR p.public_product_id LIKE 'PROD-VEN-QC%';

CREATE TEMPORARY TABLE qc_seed_addresses_to_remove AS
SELECT a.address_id
FROM address a
WHERE a.public_address_id LIKE 'ADDR-QC-%';

DELETE f
FROM favorite f
LEFT JOIN qc_seed_reviewers_to_remove c ON c.customer_profile_id = f.customer_profile_id
LEFT JOIN qc_seed_vendors_to_remove v ON v.vendor_profile_id = f.vendor_profile_id
LEFT JOIN qc_seed_products_to_remove p ON p.product_id = f.product_id
WHERE c.customer_profile_id IS NOT NULL
   OR v.vendor_profile_id IS NOT NULL
   OR p.product_id IS NOT NULL;

DELETE r
FROM review r
LEFT JOIN qc_seed_reviewers_to_remove c ON c.user_id = r.user_id
LEFT JOIN qc_seed_vendors_to_remove v ON v.vendor_profile_id = r.vendor_profile_id
LEFT JOIN qc_seed_products_to_remove p ON p.product_id = r.product_id
WHERE c.user_id IS NOT NULL
   OR v.vendor_profile_id IS NOT NULL
   OR p.product_id IS NOT NULL;

DELETE p
FROM product p
JOIN qc_seed_products_to_remove old_p ON old_p.product_id = p.product_id;

DELETE cp
FROM customer_profile cp
JOIN qc_seed_reviewers_to_remove old_cp ON old_cp.customer_profile_id = cp.customer_profile_id;

DELETE vp
FROM vendor_profile vp
JOIN qc_seed_vendors_to_remove old_vp ON old_vp.vendor_profile_id = vp.id;

DELETE a
FROM address a
JOIN qc_seed_addresses_to_remove old_a ON old_a.address_id = a.address_id;

DELETE u
FROM users u
WHERE u.public_user_id LIKE 'VEN-QC%'
   OR u.public_user_id LIKE 'CUS-QC%';

-- ── 2. Build Quebec location slots ─────────────────────────────────────────

DROP TEMPORARY TABLE IF EXISTS qc_vendor_slots;

CREATE TEMPORARY TABLE qc_vendor_slots (
    slot_no INT NOT NULL PRIMARY KEY,
    new_public_user_id VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    city VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    province VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    timezone VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    new_public_address_id VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    address_line VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    postal_code VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    username_prefix VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    email_prefix VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    phone VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL
);

INSERT INTO qc_vendor_slots VALUES
(1,  'VEN-QCMTL01', 'Montreal', 'QC', 'America/Montreal', 'ADDR-QC-MTL-001', '5450 Avenue du Parc',             'H2V4G7', 45.5220, -73.5967, 'qc_mtl_01', 'showroom.qc.mtl.01', '5145550101'),
(2,  'VEN-QCMTL02', 'Montreal', 'QC', 'America/Montreal', 'ADDR-QC-MTL-002', '7070 Avenue Henri-Julien',        'H2S3S3', 45.5350, -73.6140, 'qc_mtl_02', 'showroom.qc.mtl.02', '5145550102'),
(3,  'VEN-QCMTL03', 'Montreal', 'QC', 'America/Montreal', 'ADDR-QC-MTL-003', '3509 Boulevard Saint-Laurent',     'H2X2T6', 45.5146, -73.5747, 'qc_mtl_03', 'showroom.qc.mtl.03', '5145550103'),
(4,  'VEN-QCMTL04', 'Montreal', 'QC', 'America/Montreal', 'ADDR-QC-MTL-004', '1450 Rue Peel',                    'H3A1T5', 45.5006, -73.5741, 'qc_mtl_04', 'showroom.qc.mtl.04', '5145550104'),
(5,  'VEN-QCMTL05', 'Montreal', 'QC', 'America/Montreal', 'ADDR-QC-MTL-005', '4300 Rue Wellington',              'H4G1W4', 45.4585, -73.5678, 'qc_mtl_05', 'showroom.qc.mtl.05', '5145550105'),
(6,  'VEN-QCMTL06', 'Montreal', 'QC', 'America/Montreal', 'ADDR-QC-MTL-006', '6700 Rue Saint-Hubert',            'H2S2M6', 45.5369, -73.6089, 'qc_mtl_06', 'showroom.qc.mtl.06', '5145550106'),
(7,  'VEN-QCMTL07', 'Montreal', 'QC', 'America/Montreal', 'ADDR-QC-MTL-007', '410 Rue Saint-Vincent',            'H2Y3A5', 45.5064, -73.5532, 'qc_mtl_07', 'showroom.qc.mtl.07', '5145550107'),
(8,  'VEN-QCMTL08', 'Montreal', 'QC', 'America/Montreal', 'ADDR-QC-MTL-008', '5401 Boulevard Decarie',           'H3W3C6', 45.4856, -73.6320, 'qc_mtl_08', 'showroom.qc.mtl.08', '5145550108'),
(9,  'VEN-QCMTL09', 'Montreal', 'QC', 'America/Montreal', 'ADDR-QC-MTL-009', '1221 Rue Sainte-Catherine Ouest',  'H3G1P5', 45.4983, -73.5732, 'qc_mtl_09', 'showroom.qc.mtl.09', '5145550109'),
(10, 'VEN-QCMTL10', 'Montreal', 'QC', 'America/Montreal', 'ADDR-QC-MTL-010', '280 Rue Jean-Talon Est',           'H2R1S7', 45.5389, -73.6151, 'qc_mtl_10', 'showroom.qc.mtl.10', '5145550110'),
(11, 'VEN-QCLAV01', 'Laval',    'QC', 'America/Montreal', 'ADDR-QC-LAV-001', '1600 Boulevard Le Corbusier',      'H7S1Y9', 45.5632, -73.7310, 'qc_lav_01', 'showroom.qc.lav.01', '4505550101'),
(12, 'VEN-QCLAV02', 'Laval',    'QC', 'America/Montreal', 'ADDR-QC-LAV-002', '3035 Boulevard Le Carrefour',      'H7T1C8', 45.5687, -73.7486, 'qc_lav_02', 'showroom.qc.lav.02', '4505550102'),
(13, 'VEN-QCLAV03', 'Laval',    'QC', 'America/Montreal', 'ADDR-QC-LAV-003', '1950 Rue Claude-Gagne',            'H7N5H9', 45.5579, -73.7156, 'qc_lav_03', 'showroom.qc.lav.03', '4505550103'),
(14, 'VEN-QCLAV04', 'Laval',    'QC', 'America/Montreal', 'ADDR-QC-LAV-004', '2600 Avenue Pierre-Peladeau',      'H7T2Z8', 45.5708, -73.7552, 'qc_lav_04', 'showroom.qc.lav.04', '4505550104'),
(15, 'VEN-QCLAV05', 'Laval',    'QC', 'America/Montreal', 'ADDR-QC-LAV-005', '3100 Boulevard de la Concorde Est', 'H7E2B8', 45.5880, -73.6663, 'qc_lav_05', 'showroom.qc.lav.05', '4505550105'),
(16, 'VEN-QCLAV06', 'Laval',    'QC', 'America/Montreal', 'ADDR-QC-LAV-006', '940 Boulevard Cure-Labelle',        'H7V2V5', 45.5520, -73.7587, 'qc_lav_06', 'showroom.qc.lav.06', '4505550106'),
(17, 'VEN-QCLAV07', 'Laval',    'QC', 'America/Montreal', 'ADDR-QC-LAV-007', '2475 Boulevard Saint-Martin Est',   'H7E4X6', 45.5862, -73.6924, 'qc_lav_07', 'showroom.qc.lav.07', '4505550107'),
(18, 'VEN-QCLAV08', 'Laval',    'QC', 'America/Montreal', 'ADDR-QC-LAV-008', '500 Autoroute Chomedey Ouest',      'H7X3S9', 45.5365, -73.7890, 'qc_lav_08', 'showroom.qc.lav.08', '4505550108'),
(19, 'VEN-QCLAV09', 'Laval',    'QC', 'America/Montreal', 'ADDR-QC-LAV-009', '1799 Avenue Pierre-Peladeau',      'H7T2Y5', 45.5714, -73.7510, 'qc_lav_09', 'showroom.qc.lav.09', '4505550109'),
(20, 'VEN-QCLAV10', 'Laval',    'QC', 'America/Montreal', 'ADDR-QC-LAV-010', '1200 Boulevard Chomedey',           'H7V3Z3', 45.5538, -73.7478, 'qc_lav_10', 'showroom.qc.lav.10', '4505550110');

-- ── 3. Select Calgary showroom vendors as the source of truth ──────────────

DROP TEMPORARY TABLE IF EXISTS qc_source_vendors;

CREATE TEMPORARY TABLE qc_source_vendors AS
SELECT *
FROM (
    SELECT
        ROW_NUMBER() OVER (ORDER BY COALESCE(c.display_order, 999), vp.id) AS slot_no,
        u.user_id AS source_user_id,
        u.username,
        u.email,
        u.profile_image_url,
        u.password,
        u.google_id,
        u.first_name,
        u.last_name,
        u.role,
        u.auth_provider,
        u.is_active AS user_is_active,
        u.scheduled_for_deletion_at,
        u.email_verified,
        u.accept_terms,
        vp.id AS source_vendor_profile_id,
        vp.restaurant_name,
        vp.description,
        vp.cuisine_type,
        vp.logo_url,
        vp.banner_url,
        vp.stripe_account_id,
        vp.stripe_onboarding_complete,
        vp.vendor_status,
        vp.business_license_url,
        vp.tax_id,
        vp.is_verified,
        vp.is_active AS vendor_is_active,
        vp.food_handling_cert_url,
        vp.food_handling_cert_number,
        vp.food_handling_cert_issuing_body,
        vp.food_handling_cert_expiry,
        vp.cert_verified_at,
        vp.cert_verified_by_admin_id,
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
    LEFT JOIN category c ON c.name = vp.cuisine_type
    WHERE vp.is_seed_data = TRUE
      AND u.is_seed_data = TRUE
      AND u.role = 'VENDOR'
      AND vp.is_active = TRUE
      AND vp.is_verified = TRUE
      AND LOWER(a.city) = 'calgary'
) ranked
WHERE ranked.slot_no <= 20;

INSERT INTO users (
    username, public_user_id, email, profile_image_url, password, google_id,
    first_name, last_name, phone, role, auth_provider, is_active,
    scheduled_for_deletion_at, email_verified, accept_terms, is_seed_data,
    created_at, updated_at, last_login_at
)
SELECT
    s.username_prefix,
    s.new_public_user_id,
    CONCAT(s.email_prefix, '@afrochow.ca'),
    COALESCE(src.profile_image_url, src.logo_url),
    src.password,
    NULL,
    src.first_name,
    src.last_name,
    s.phone,
    src.role,
    src.auth_provider,
    TRUE,
    NULL,
    TRUE,
    TRUE,
    TRUE,
    NOW(),
    NOW(),
    NULL
FROM qc_source_vendors src
JOIN qc_vendor_slots s ON s.slot_no = src.slot_no;

INSERT INTO address (
    public_address_id, address_line, city, province, postal_code, country,
    latitude, longitude, default_address, is_seed_data, customer_profile_id,
    created_at, updated_at
)
SELECT
    s.new_public_address_id,
    s.address_line,
    s.city,
    s.province,
    s.postal_code,
    'Canada',
    s.latitude,
    s.longitude,
    FALSE,
    TRUE,
    NULL,
    NOW(),
    NOW()
FROM qc_source_vendors src
JOIN qc_vendor_slots s ON s.slot_no = src.slot_no;

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
    new_u.user_id,
    src.restaurant_name,
    src.description,
    src.cuisine_type,
    src.logo_url,
    src.banner_url,
    src.stripe_account_id,
    src.stripe_onboarding_complete,
    TRUE,
    src.vendor_status,
    src.business_license_url,
    src.tax_id,
    src.is_verified,
    src.vendor_is_active,
    NOW(),
    src.food_handling_cert_url,
    src.food_handling_cert_number,
    src.food_handling_cert_issuing_body,
    src.food_handling_cert_expiry,
    src.cert_verified_at,
    src.cert_verified_by_admin_id,
    s.timezone,
    src.operating_hours_json,
    src.offers_delivery,
    src.offers_pickup,
    src.preparation_time,
    src.delivery_fee,
    src.minimum_order_amount,
    src.estimated_delivery_minutes,
    src.max_delivery_distance_km,
    new_a.address_id,
    src.total_orders_completed,
    src.total_revenue,
    NOW(),
    NOW()
FROM qc_source_vendors src
JOIN qc_vendor_slots s ON s.slot_no = src.slot_no
JOIN users new_u ON new_u.public_user_id = s.new_public_user_id
JOIN address new_a ON new_a.public_address_id = s.new_public_address_id;

DROP TEMPORARY TABLE IF EXISTS qc_vendor_map;

CREATE TEMPORARY TABLE qc_vendor_map AS
SELECT
    src.source_vendor_profile_id,
    new_vp.id AS new_vendor_profile_id,
    s.new_public_user_id
FROM qc_source_vendors src
JOIN qc_vendor_slots s ON s.slot_no = src.slot_no
JOIN users new_u ON new_u.public_user_id = s.new_public_user_id
JOIN vendor_profile new_vp ON new_vp.user_id = new_u.user_id;

-- ── 4. Clone Calgary products under the new Quebec vendors ─────────────────

DROP TEMPORARY TABLE IF EXISTS qc_source_products;

CREATE TEMPORARY TABLE qc_source_products AS
SELECT *
FROM (
    SELECT
        ROW_NUMBER() OVER (PARTITION BY p.vendor_profile_id ORDER BY p.is_featured DESC, p.product_id) AS product_slot_no,
        p.product_id AS source_product_id,
        p.vendor_profile_id AS source_vendor_profile_id,
        p.version,
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
        p.is_featured,
        p.category_id
    FROM product p
    JOIN qc_vendor_map vm ON vm.source_vendor_profile_id = p.vendor_profile_id
    WHERE p.is_seed_data = TRUE
) ranked_products;

INSERT INTO product (
    version, public_product_id, name, description, price, image_url,
    available, admin_visible, is_seed_data, preparation_time_minutes,
    schedule_type, advance_notice_hours, calories, is_vegetarian, is_vegan,
    is_gluten_free, is_spicy, is_featured, featured_at, vendor_profile_id,
    category_id, created_at, updated_at
)
SELECT
    0,
    CONCAT('PROD-', vm.new_public_user_id, '-', LPAD(sp.product_slot_no, 2, '0')),
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
    vm.new_vendor_profile_id,
    sp.category_id,
    NOW(),
    NOW()
FROM qc_source_products sp
JOIN qc_vendor_map vm ON vm.source_vendor_profile_id = sp.source_vendor_profile_id;

DROP TEMPORARY TABLE IF EXISTS qc_product_map;

CREATE TEMPORARY TABLE qc_product_map AS
SELECT
    sp.source_product_id,
    new_p.product_id AS new_product_id
FROM qc_source_products sp
JOIN qc_vendor_map vm ON vm.source_vendor_profile_id = sp.source_vendor_profile_id
JOIN product new_p
  ON new_p.vendor_profile_id = vm.new_vendor_profile_id
 AND new_p.public_product_id = CONCAT('PROD-', vm.new_public_user_id, '-', LPAD(sp.product_slot_no, 2, '0'));

-- ── 5. Clone seed reviewers/reviews used by the Calgary showroom ───────────

DROP TEMPORARY TABLE IF EXISTS qc_source_reviewers;

CREATE TEMPORARY TABLE qc_source_reviewers AS
SELECT *
FROM (
    SELECT
        ROW_NUMBER() OVER (ORDER BY u.user_id) AS reviewer_slot_no,
        u.user_id AS source_user_id,
        u.username,
        u.profile_image_url,
        u.password,
        u.google_id,
        u.first_name,
        u.last_name,
        u.phone,
        u.role,
        u.auth_provider,
        u.is_active,
        u.scheduled_for_deletion_at,
        u.email_verified,
        u.accept_terms,
        cp.default_delivery_instructions,
        cp.payment_method,
        cp.loyalty_points,
        cp.notifications_enabled
    FROM review r
    JOIN qc_vendor_map vm ON vm.source_vendor_profile_id = r.vendor_profile_id
    JOIN users u ON u.user_id = r.user_id
    JOIN customer_profile cp ON cp.user_id = u.user_id
    WHERE u.is_seed_data = TRUE
      AND cp.is_seed_data = TRUE
    GROUP BY
        u.user_id, u.username, u.profile_image_url, u.password, u.google_id,
        u.first_name, u.last_name, u.phone, u.role, u.auth_provider, u.is_active,
        u.scheduled_for_deletion_at, u.email_verified, u.accept_terms,
        cp.default_delivery_instructions, cp.payment_method, cp.loyalty_points,
        cp.notifications_enabled
) ranked_reviewers;

INSERT INTO users (
    username, public_user_id, email, profile_image_url, password, google_id,
    first_name, last_name, phone, role, auth_provider, is_active,
    scheduled_for_deletion_at, email_verified, accept_terms, is_seed_data,
    created_at, updated_at, last_login_at
)
SELECT
    CONCAT('qc_reviewer_', LPAD(reviewer_slot_no, 3, '0')),
    CONCAT('CUS-QCR', LPAD(reviewer_slot_no, 3, '0')),
    CONCAT('showroom.qc.reviewer.', LPAD(reviewer_slot_no, 3, '0'), '@afrochow.ca'),
    profile_image_url,
    password,
    NULL,
    first_name,
    last_name,
    CONCAT('438776', LPAD(reviewer_slot_no, 4, '0')),
    role,
    auth_provider,
    is_active,
    NULL,
    email_verified,
    accept_terms,
    TRUE,
    NOW(),
    NOW(),
    NULL
FROM qc_source_reviewers;

INSERT INTO customer_profile (
    user_id, default_delivery_instructions, payment_method, loyalty_points,
    notifications_enabled, is_seed_data, created_at, updated_at
)
SELECT
    new_u.user_id,
    COALESCE(src.default_delivery_instructions, 'Quebec showroom reviewer'),
    src.payment_method,
    src.loyalty_points,
    src.notifications_enabled,
    TRUE,
    NOW(),
    NOW()
FROM qc_source_reviewers src
JOIN users new_u ON new_u.public_user_id = CONCAT('CUS-QCR', LPAD(src.reviewer_slot_no, 3, '0'));

DROP TEMPORARY TABLE IF EXISTS qc_reviewer_map;

CREATE TEMPORARY TABLE qc_reviewer_map AS
SELECT
    src.source_user_id,
    new_u.user_id AS new_user_id
FROM qc_source_reviewers src
JOIN users new_u ON new_u.public_user_id = CONCAT('CUS-QCR', LPAD(src.reviewer_slot_no, 3, '0'));

INSERT INTO review (
    user_id, vendor_profile_id, product_id, order_id, rating, comment,
    helpful_count, is_visible, is_seed_data, created_at, updated_at
)
SELECT
    rm.new_user_id,
    vm.new_vendor_profile_id,
    pm.new_product_id,
    NULL,
    r.rating,
    r.comment,
    r.helpful_count,
    r.is_visible,
    TRUE,
    NOW(),
    NOW()
FROM review r
JOIN qc_vendor_map vm ON vm.source_vendor_profile_id = r.vendor_profile_id
JOIN qc_reviewer_map rm ON rm.source_user_id = r.user_id
LEFT JOIN qc_product_map pm ON pm.source_product_id = r.product_id
WHERE r.is_seed_data = TRUE;

-- ── 6. Cleanup temp tables ─────────────────────────────────────────────────

DROP TEMPORARY TABLE IF EXISTS qc_reviewer_map;
DROP TEMPORARY TABLE IF EXISTS qc_source_reviewers;
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
