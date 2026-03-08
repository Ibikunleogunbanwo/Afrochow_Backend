-- ===========================================================================
-- Afrochow Database Migration V3
-- Description: Create product and category tables
-- Author: Afrochow Development Team
-- Date: 2025-11-27
-- ===========================================================================

-- Category Table
CREATE TABLE IF NOT EXISTS category (
    category_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_category_id VARCHAR(36) NOT NULL UNIQUE COMMENT 'UUID for public API',
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_public_category_id (public_category_id),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Product category table';

-- Product Table
CREATE TABLE IF NOT EXISTS product (
    product_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_product_id VARCHAR(36) NOT NULL UNIQUE COMMENT 'UUID for public API',
    vendor_id BIGINT NOT NULL,
    category_id BIGINT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL COMMENT 'Product price',
    available BOOLEAN NOT NULL DEFAULT TRUE,
    image_url VARCHAR(500) COMMENT 'Product image URL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_product_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_profile(vendor_profile_id) ON DELETE CASCADE,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category(category_id) ON DELETE SET NULL,
    CONSTRAINT chk_price_positive CHECK (price >= 0),
    INDEX idx_public_product_id (public_product_id),
    INDEX idx_vendor_id (vendor_id),
    INDEX idx_category_id (category_id),
    INDEX idx_name (name),
    INDEX idx_available (available),
    INDEX idx_price (price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Product table';

-- ===========================================================================
-- End of V3 migration
-- ===========================================================================
