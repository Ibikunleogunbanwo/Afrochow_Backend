-- ===========================================================================
-- Afrochow Database Migration V39
-- Description: Add production-safe Quebec showroom data for Montreal and Laval.
--
-- Why SQL instead of CompleteFinalSeeder?
--   CompleteFinalSeeder is intentionally disabled in prod (@Profile("!prod")).
--   Production showroom data should be deliberate, versioned, and reversible by
--   normal database backup/restore practice. Every row here is marked
--   is_seed_data = TRUE so demo data remains distinguishable from real vendors.
--
-- Redis GEO note:
--   No Redis writes are needed here. VendorGeoIndexService rebuilds the Redis
--   vendor geo index from active, verified, geocoded vendors on app startup and
--   on its scheduled refresh.
-- ===========================================================================

-- ── 1. Ensure public categories exist ───────────────────────────────────────

INSERT INTO category (name, description, icon_url, display_order, is_active, created_at, updated_at)
SELECT 'African Kitchen',
       'Authentic African meals including jollof rice, pounded yam, egusi soup, and traditional dishes',
       '🍲', 1, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'African Kitchen');

INSERT INTO category (name, description, icon_url, display_order, is_active, created_at, updated_at)
SELECT 'Cakes',
       'Custom celebration cakes, birthday cakes, wedding cakes, and traditional African cakes',
       '🎂', 4, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'Cakes');

INSERT INTO category (name, description, icon_url, display_order, is_active, created_at, updated_at)
SELECT 'African Groceries',
       'Authentic African ingredients, spices, palm oil, garri, egusi seeds, and specialty items',
       '🛒', 6, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = 'African Groceries');

-- ── 2. Stage deterministic Quebec showroom vendors ─────────────────────────

CREATE TEMPORARY TABLE afrochow_qc_showroom_vendors (
    public_user_id VARCHAR(16) NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    public_address_id VARCHAR(80) NOT NULL,
    address_line VARCHAR(200) NOT NULL,
    city VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    restaurant_name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    store_category VARCHAR(50) NOT NULL,
    logo_url VARCHAR(500) NOT NULL,
    banner_url VARCHAR(500) NOT NULL,
    business_license_url VARCHAR(500) NOT NULL,
    tax_id VARCHAR(50) NOT NULL,
    offers_delivery BOOLEAN NOT NULL,
    offers_pickup BOOLEAN NOT NULL,
    delivery_fee DECIMAL(10,2) NOT NULL,
    minimum_order_amount DECIMAL(10,2) NOT NULL,
    estimated_delivery_minutes INT NOT NULL,
    max_delivery_distance_km DECIMAL(5,1) NOT NULL,
    preparation_time INT NOT NULL,
    total_orders_completed INT NOT NULL,
    total_revenue DECIMAL(10,2) NOT NULL
);

INSERT INTO afrochow_qc_showroom_vendors VALUES
('VEN-QCMTL01', 'qc_mama_kemi', 'showroom.mama.kemi.mtl@afrochow.ca', 'Mama', 'Kemi', '5145550101',
 'ADDR-QC-MTL-001', '5450 Avenue du Parc', 'Montreal', 'H2V4G7', 45.5220, -73.5967,
 'Mama Kemi Montreal Kitchen',
 'Home-style Nigerian and West African meals prepared for the Montreal showroom market.',
 'African Kitchen',
 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/Amala_jlxqmn.jpg',
 'https://picsum.photos/seed/afrochow-mtl-kitchen/1200/400',
 'https://storage.cloud.example.com/business-licenses/mama-kemi-montreal-kitchen.pdf',
 '987654321RC0101', TRUE, TRUE, 3.99, 18.00, 35, 25.0, 30, 142, 4820.00),

('VEN-QCMTL02', 'qc_afro_marche_jt', 'showroom.afro.marche.jt@afrochow.ca', 'Afro', 'Marche', '5145550102',
 'ADDR-QC-MTL-002', '7070 Avenue Henri-Julien', 'Montreal', 'H2S3S3', 45.5350, -73.6140,
 'Afro Marche Jean-Talon',
 'African pantry staples, spices, garri, palm oil, and fresh produce near Jean-Talon.',
 'African Groceries',
 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/ASORTED_Food_tg6kzh.jpg',
 'https://picsum.photos/seed/afrochow-mtl-groceries/1200/400',
 'https://storage.cloud.example.com/business-licenses/afro-marche-jean-talon.pdf',
 '987654321RC0102', TRUE, TRUE, 2.99, 20.00, 30, 20.0, 20, 118, 3925.00),

('VEN-QCMTL03', 'qc_plateau_jollof', 'showroom.plateau.jollof@afrochow.ca', 'Plateau', 'Jollof', '5145550103',
 'ADDR-QC-MTL-003', '3509 Boulevard Saint-Laurent', 'Montreal', 'H2X2T6', 45.5146, -73.5747,
 'Plateau Jollof House',
 'Party jollof, grilled meats, soups, and ready-to-order African comfort food on the Plateau.',
 'African Kitchen',
 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/vk-bro-al9eh9QkdPA-unsplash_hgb5fp.jpg',
 'https://picsum.photos/seed/afrochow-plateau-jollof/1200/400',
 'https://storage.cloud.example.com/business-licenses/plateau-jollof-house.pdf',
 '987654321RC0103', TRUE, TRUE, 4.49, 22.00, 40, 25.0, 35, 166, 5575.00),

('VEN-QCLAV01', 'qc_laval_suya', 'showroom.laval.suya@afrochow.ca', 'Laval', 'Suya', '4505550101',
 'ADDR-QC-LAV-001', '1600 Boulevard Le Corbusier', 'Laval', 'H7S1Y9', 45.5632, -73.7310,
 'Laval Suya Grill',
 'Smoky suya platters, jollof rice, fried rice, and grilled African street food in Laval.',
 'African Kitchen',
 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919513/omotayo-tajudeen-ME416b6sp2I-unsplash_jxh2qx.jpg',
 'https://picsum.photos/seed/afrochow-laval-suya/1200/400',
 'https://storage.cloud.example.com/business-licenses/laval-suya-grill.pdf',
 '987654321RC0201', TRUE, TRUE, 3.49, 18.00, 32, 22.0, 28, 127, 4140.00),

('VEN-QCLAV02', 'qc_sweet_lagos_laval', 'showroom.sweet.lagos.laval@afrochow.ca', 'Sweet', 'Lagos', '4505550102',
 'ADDR-QC-LAV-002', '3035 Boulevard Le Carrefour', 'Laval', 'H7T1C8', 45.5687, -73.7486,
 'Sweet Lagos Cakes Laval',
 'Custom celebration cakes and African-inspired baked treats for the Laval showroom market.',
 'Cakes',
 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/nathan-dumlao-1lAIRAsv3C4-unsplash_gmm3t6.jpg',
 'https://picsum.photos/seed/afrochow-laval-cakes/1200/400',
 'https://storage.cloud.example.com/business-licenses/sweet-lagos-cakes-laval.pdf',
 '987654321RC0202', TRUE, TRUE, 4.99, 35.00, 45, 25.0, 45, 94, 6380.00),

('VEN-QCLAV03', 'qc_marche_afro_laval', 'showroom.marche.afro.laval@afrochow.ca', 'Marche', 'Afro', '4505550103',
 'ADDR-QC-LAV-003', '1950 Rue Claude-Gagne', 'Laval', 'H7N5H9', 45.5579, -73.7156,
 'Marche Afro Laval',
 'Groceries, spices, plantains, yam, dried fish, and African staples for Laval households.',
 'African Groceries',
 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/nAIJA_FOOD_n7daze.jpg',
 'https://picsum.photos/seed/afrochow-laval-market/1200/400',
 'https://storage.cloud.example.com/business-licenses/marche-afro-laval.pdf',
 '987654321RC0203', TRUE, TRUE, 2.99, 20.00, 30, 20.0, 20, 103, 3490.00);

-- Showroom accounts are not meant for public login. Use a BCrypt hash generated
-- from an unknown random value so the non-null password column is satisfied
-- without creating a reusable demo credential.
INSERT INTO users (
    username, public_user_id, email, profile_image_url, password, google_id,
    first_name, last_name, phone, role, auth_provider, is_active,
    scheduled_for_deletion_at, email_verified, accept_terms, is_seed_data,
    created_at, updated_at, last_login_at
)
SELECT v.username, v.public_user_id, v.email, v.logo_url,
       '$2y$12$bKwxOudYuGkM20On.C5k0uJkGXkEbHPg6rLfZV0qg7iAo2G2uTtrW', NULL,
       v.first_name, v.last_name, v.phone, 'VENDOR', 'EMAIL', TRUE,
       NULL, TRUE, TRUE, TRUE, NOW(), NOW(), NULL
FROM afrochow_qc_showroom_vendors v
WHERE NOT EXISTS (
    SELECT 1 FROM users u
    WHERE u.email = v.email OR u.public_user_id = v.public_user_id
);

INSERT INTO address (
    public_address_id, address_line, city, province, postal_code, country,
    latitude, longitude, default_address, is_seed_data, customer_profile_id,
    created_at, updated_at
)
SELECT v.public_address_id, v.address_line, v.city, 'QC', v.postal_code, 'Canada',
       v.latitude, v.longitude, FALSE, TRUE, NULL, NOW(), NOW()
FROM afrochow_qc_showroom_vendors v
WHERE NOT EXISTS (
    SELECT 1 FROM address a WHERE a.public_address_id = v.public_address_id
);

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
SELECT u.user_id, v.restaurant_name, v.description, v.store_category, v.logo_url, v.banner_url,
       NULL, FALSE, TRUE, 'VERIFIED',
       v.business_license_url, v.tax_id, TRUE, TRUE, NOW(),
       NULL, NULL, NULL, NULL, NULL, NULL,
       'America/Montreal',
       '{"monday":{"isOpen":true,"openTime":"09:00","closeTime":"21:00"},"tuesday":{"isOpen":true,"openTime":"09:00","closeTime":"21:00"},"wednesday":{"isOpen":true,"openTime":"09:00","closeTime":"21:00"},"thursday":{"isOpen":true,"openTime":"09:00","closeTime":"21:00"},"friday":{"isOpen":true,"openTime":"09:00","closeTime":"22:00"},"saturday":{"isOpen":true,"openTime":"10:00","closeTime":"22:00"},"sunday":{"isOpen":true,"openTime":"11:00","closeTime":"19:00"}}',
       v.offers_delivery, v.offers_pickup, v.preparation_time,
       v.delivery_fee, v.minimum_order_amount, v.estimated_delivery_minutes,
       v.max_delivery_distance_km, a.address_id, v.total_orders_completed, v.total_revenue,
       NOW(), NOW()
FROM afrochow_qc_showroom_vendors v
JOIN users u ON u.public_user_id = v.public_user_id
JOIN address a ON a.public_address_id = v.public_address_id
WHERE NOT EXISTS (
    SELECT 1 FROM vendor_profile existing WHERE existing.user_id = u.user_id
);

-- ── 3. Stage and insert products ───────────────────────────────────────────

CREATE TEMPORARY TABLE afrochow_qc_showroom_products (
    public_product_id VARCHAR(100) NOT NULL,
    vendor_public_user_id VARCHAR(16) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    preparation_time_minutes INT NOT NULL,
    schedule_type VARCHAR(20) NOT NULL,
    advance_notice_hours INT NULL,
    calories INT NULL,
    is_vegetarian BOOLEAN NULL,
    is_vegan BOOLEAN NULL,
    is_gluten_free BOOLEAN NULL,
    is_spicy BOOLEAN NULL,
    is_featured BOOLEAN NOT NULL
);

INSERT INTO afrochow_qc_showroom_products VALUES
('PROD-QCMTL01-JOLLOF', 'VEN-QCMTL01', 'African Kitchen', 'Jollof Rice', 'Party jollof rice with roasted chicken and plantain.', 25.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/vk-bro-al9eh9QkdPA-unsplash_hgb5fp.jpg', 30, 'SAME_DAY', NULL, 720, FALSE, FALSE, TRUE, TRUE, TRUE),
('PROD-QCMTL01-EGUSI', 'VEN-QCMTL01', 'African Kitchen', 'Egusi Soup with Fufu', 'Melon seed soup with assorted meat and fresh fufu.', 28.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/Amala_jlxqmn.jpg', 35, 'SAME_DAY', NULL, 880, FALSE, FALSE, TRUE, TRUE, FALSE),
('PROD-QCMTL01-SUYA', 'VEN-QCMTL01', 'African Kitchen', 'Suya Platter', 'Spiced beef skewers with onions, tomato, and pepper sauce.', 22.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919513/omotayo-tajudeen-ME416b6sp2I-unsplash_jxh2qx.jpg', 25, 'SAME_DAY', NULL, 640, FALSE, FALSE, TRUE, TRUE, FALSE),

('PROD-QCMTL02-PALMOIL', 'VEN-QCMTL02', 'African Groceries', 'Palm Oil (1L)', 'Pure red palm oil for soups, stews, and sauces.', 15.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/ASORTED_Food_tg6kzh.jpg', 10, 'SAME_DAY', NULL, NULL, TRUE, TRUE, TRUE, FALSE, TRUE),
('PROD-QCMTL02-GARRI', 'VEN-QCMTL02', 'African Groceries', 'Garri (2 lbs)', 'Cassava flakes for eba and soaking garri.', 8.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/nAIJA_FOOD_n7daze.jpg', 10, 'SAME_DAY', NULL, NULL, TRUE, TRUE, TRUE, FALSE, FALSE),
('PROD-QCMTL02-PLANTAIN', 'VEN-QCMTL02', 'African Groceries', 'Plantains (6 pcs)', 'Fresh green or ripe plantains for frying or boiling.', 6.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919508/nico-smit-9ZJOs9hmuKs-unsplash_n3fwbt.jpg', 10, 'SAME_DAY', NULL, NULL, TRUE, TRUE, TRUE, FALSE, FALSE),

('PROD-QCMTL03-JOLLOF', 'VEN-QCMTL03', 'African Kitchen', 'Smoky Party Jollof', 'Smoky Nigerian jollof with chicken and coleslaw.', 26.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/vk-bro-al9eh9QkdPA-unsplash_hgb5fp.jpg', 30, 'SAME_DAY', NULL, 760, FALSE, FALSE, TRUE, TRUE, TRUE),
('PROD-QCMTL03-FRIEDRICE', 'VEN-QCMTL03', 'African Kitchen', 'Nigerian Fried Rice', 'Nigerian fried rice with vegetables and grilled chicken.', 24.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/ASORTED_Food_tg6kzh.jpg', 30, 'SAME_DAY', NULL, 700, FALSE, FALSE, TRUE, FALSE, FALSE),
('PROD-QCMTL03-PEPPERSOUP', 'VEN-QCMTL03', 'African Kitchen', 'Goat Meat Pepper Soup', 'Spicy goat meat pepper soup with herbs and yam.', 29.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919513/victoria-shes-UC0HZdUitWY-unsplash_wa1zr0.jpg', 35, 'SAME_DAY', NULL, 680, FALSE, FALSE, TRUE, TRUE, FALSE),

('PROD-QCLAV01-SUYA', 'VEN-QCLAV01', 'African Kitchen', 'Beef Suya Plate', 'Grilled beef suya with onions, tomatoes, and yaji spice.', 23.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919513/omotayo-tajudeen-ME416b6sp2I-unsplash_jxh2qx.jpg', 25, 'SAME_DAY', NULL, 640, FALSE, FALSE, TRUE, TRUE, TRUE),
('PROD-QCLAV01-JOLLOF', 'VEN-QCLAV01', 'African Kitchen', 'Jollof and Suya Combo', 'Jollof rice served with suya beef and fried plantain.', 31.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/vk-bro-al9eh9QkdPA-unsplash_hgb5fp.jpg', 35, 'SAME_DAY', NULL, 920, FALSE, FALSE, TRUE, TRUE, FALSE),
('PROD-QCLAV01-FRIEDRICE', 'VEN-QCLAV01', 'African Kitchen', 'Fried Rice', 'Nigerian-style fried rice with mixed vegetables.', 23.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/ASORTED_Food_tg6kzh.jpg', 30, 'SAME_DAY', NULL, 690, FALSE, FALSE, TRUE, FALSE, FALSE),

('PROD-QCLAV02-CHOC', 'VEN-QCLAV02', 'Cakes', 'Chocolate Celebration Cake', 'Rich chocolate celebration cake with African-inspired decoration.', 45.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/nathan-dumlao-1lAIRAsv3C4-unsplash_gmm3t6.jpg', 60, 'ADVANCE_ORDER', 24, 2400, TRUE, FALSE, FALSE, FALSE, TRUE),
('PROD-QCLAV02-VANILLA', 'VEN-QCLAV02', 'Cakes', 'Vanilla Wedding Cake', 'Elegant vanilla cake for weddings and special celebrations.', 75.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919513/victoria-shes-UC0HZdUitWY-unsplash_wa1zr0.jpg', 90, 'ADVANCE_ORDER', 48, 3200, TRUE, FALSE, FALSE, FALSE, FALSE),
('PROD-QCLAV02-REDVELVET', 'VEN-QCLAV02', 'Cakes', 'Red Velvet Cake', 'Moist red velvet cake with cream cheese frosting.', 42.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/nathan-dumlao-1lAIRAsv3C4-unsplash_gmm3t6.jpg', 60, 'ADVANCE_ORDER', 24, 2300, TRUE, FALSE, FALSE, FALSE, FALSE),

('PROD-QCLAV03-YAM', 'VEN-QCLAV03', 'African Groceries', 'Fresh Yam', 'Premium African yam sold by weight.', 4.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919508/nico-smit-9ZJOs9hmuKs-unsplash_n3fwbt.jpg', 10, 'SAME_DAY', NULL, NULL, TRUE, TRUE, TRUE, FALSE, TRUE),
('PROD-QCLAV03-EGUSI', 'VEN-QCLAV03', 'African Groceries', 'Egusi Seeds (500g)', 'Ground melon seeds for egusi soup.', 12.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/Amala_jlxqmn.jpg', 10, 'SAME_DAY', NULL, NULL, TRUE, TRUE, TRUE, FALSE, FALSE),
('PROD-QCLAV03-DRIEDFISH', 'VEN-QCLAV03', 'African Groceries', 'Dried Fish', 'Assorted dried fish for soups and stews.', 18.99, 'https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/nAIJA_FOOD_n7daze.jpg', 10, 'SAME_DAY', NULL, NULL, FALSE, FALSE, TRUE, FALSE, FALSE);

INSERT INTO product (
    version, public_product_id, name, description, price, image_url,
    available, admin_visible, is_seed_data, preparation_time_minutes,
    schedule_type, advance_notice_hours, calories, is_vegetarian, is_vegan,
    is_gluten_free, is_spicy, is_featured, featured_at, vendor_profile_id,
    category_id, created_at, updated_at
)
SELECT 0, p.public_product_id, p.name, p.description, p.price, p.image_url,
       TRUE, TRUE, TRUE, p.preparation_time_minutes,
       p.schedule_type, p.advance_notice_hours, p.calories, p.is_vegetarian, p.is_vegan,
       p.is_gluten_free, p.is_spicy, p.is_featured,
       CASE WHEN p.is_featured THEN NOW() ELSE NULL END,
       vp.id, c.category_id, NOW(), NOW()
FROM afrochow_qc_showroom_products p
JOIN users u ON u.public_user_id = p.vendor_public_user_id
JOIN vendor_profile vp ON vp.user_id = u.user_id
LEFT JOIN category c ON c.name = p.category_name
WHERE NOT EXISTS (
    SELECT 1 FROM product existing WHERE existing.public_product_id = p.public_product_id
);

-- ── 4. Add a small reusable Quebec reviewer pool and demo reviews ──────────

CREATE TEMPORARY TABLE afrochow_qc_showroom_reviewers (
    public_user_id VARCHAR(16) NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(32) NOT NULL
);

INSERT INTO afrochow_qc_showroom_reviewers VALUES
('CUS-QCREV01', 'qc_showroom_ada', 'showroom.qc.ada@afrochow.ca', 'Ada', 'Quebec', '5145550201'),
('CUS-QCREV02', 'qc_showroom_kofi', 'showroom.qc.kofi@afrochow.ca', 'Kofi', 'Quebec', '5145550202'),
('CUS-QCREV03', 'qc_showroom_amina', 'showroom.qc.amina@afrochow.ca', 'Amina', 'Quebec', '4505550203');

INSERT INTO users (
    username, public_user_id, email, profile_image_url, password, google_id,
    first_name, last_name, phone, role, auth_provider, is_active,
    scheduled_for_deletion_at, email_verified, accept_terms, is_seed_data,
    created_at, updated_at, last_login_at
)
SELECT r.username, r.public_user_id, r.email, NULL,
       '$2y$12$bKwxOudYuGkM20On.C5k0uJkGXkEbHPg6rLfZV0qg7iAo2G2uTtrW', NULL,
       r.first_name, r.last_name, r.phone, 'CUSTOMER', 'EMAIL', TRUE,
       NULL, TRUE, TRUE, TRUE, NOW(), NOW(), NULL
FROM afrochow_qc_showroom_reviewers r
WHERE NOT EXISTS (
    SELECT 1 FROM users u
    WHERE u.email = r.email OR u.public_user_id = r.public_user_id
);

INSERT INTO customer_profile (
    user_id, default_delivery_instructions, payment_method, loyalty_points,
    notifications_enabled, is_seed_data, created_at, updated_at
)
SELECT u.user_id, 'Quebec showroom reviewer', 'CREDIT_CARD', 100,
       TRUE, TRUE, NOW(), NOW()
FROM afrochow_qc_showroom_reviewers r
JOIN users u ON u.public_user_id = r.public_user_id
WHERE NOT EXISTS (
    SELECT 1 FROM customer_profile cp WHERE cp.user_id = u.user_id
);

CREATE TEMPORARY TABLE afrochow_qc_showroom_reviews (
    reviewer_public_user_id VARCHAR(16) NOT NULL,
    vendor_public_user_id VARCHAR(16) NOT NULL,
    product_public_id VARCHAR(100) NULL,
    rating INT NOT NULL,
    comment VARCHAR(1000) NOT NULL,
    helpful_count INT NOT NULL
);

INSERT INTO afrochow_qc_showroom_reviews VALUES
('CUS-QCREV01', 'VEN-QCMTL01', 'PROD-QCMTL01-JOLLOF', 5, 'Tastes like proper party jollof and the pickup experience was smooth.', 8),
('CUS-QCREV02', 'VEN-QCMTL02', 'PROD-QCMTL02-GARRI', 5, 'Great grocery selection for African pantry staples in Montreal.', 6),
('CUS-QCREV03', 'VEN-QCMTL03', 'PROD-QCMTL03-PEPPERSOUP', 4, 'Pepper soup had real heat and generous portions.', 5),
('CUS-QCREV01', 'VEN-QCLAV01', 'PROD-QCLAV01-SUYA', 5, 'The suya spice was excellent and the jollof combo travels well.', 7),
('CUS-QCREV02', 'VEN-QCLAV02', 'PROD-QCLAV02-CHOC', 5, 'Beautiful cake option for celebrations in Laval.', 4),
('CUS-QCREV03', 'VEN-QCLAV03', 'PROD-QCLAV03-YAM', 4, 'Fresh yam and staples made the showroom selection feel realistic.', 3);

INSERT INTO review (
    user_id, vendor_profile_id, product_id, order_id, rating, comment,
    helpful_count, is_visible, is_seed_data, created_at, updated_at
)
SELECT reviewer.user_id, vp.id, p.product_id, NULL, r.rating, r.comment,
       r.helpful_count, TRUE, TRUE, NOW(), NOW()
FROM afrochow_qc_showroom_reviews r
JOIN users reviewer ON reviewer.public_user_id = r.reviewer_public_user_id
JOIN users vendor_user ON vendor_user.public_user_id = r.vendor_public_user_id
JOIN vendor_profile vp ON vp.user_id = vendor_user.user_id
LEFT JOIN product p ON p.public_product_id = r.product_public_id
WHERE NOT EXISTS (
    SELECT 1
    FROM review existing
    WHERE existing.user_id = reviewer.user_id
      AND existing.vendor_profile_id = vp.id
      AND existing.comment = r.comment
);

DROP TEMPORARY TABLE IF EXISTS afrochow_qc_showroom_reviews;
DROP TEMPORARY TABLE IF EXISTS afrochow_qc_showroom_reviewers;
DROP TEMPORARY TABLE IF EXISTS afrochow_qc_showroom_products;
DROP TEMPORARY TABLE IF EXISTS afrochow_qc_showroom_vendors;

-- ===========================================================================
-- End of V39 migration
-- ===========================================================================
