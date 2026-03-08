-- ===========================================================================
-- Afrochow Database Migration V2
-- Description: Create profile tables (customer, vendor, admin) and address table
-- Author: Afrochow Development Team
-- Date: 2025-11-27
-- ===========================================================================

-- Address Table
CREATE TABLE IF NOT EXISTS address (
    address_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_address_id VARCHAR(36) NOT NULL UNIQUE COMMENT 'UUID for public API',
    street VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    province VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'Canada',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_public_address_id (public_address_id),
    INDEX idx_city (city),
    INDEX idx_postal_code (postal_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Address table for customers and vendors';

-- Customer Profile Table
CREATE TABLE IF NOT EXISTS customer_profile (
    customer_profile_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    preferences TEXT COMMENT 'Customer preferences (JSON or comma-separated)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_customer_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Customer profile table';

-- Vendor Profile Table
CREATE TABLE IF NOT EXISTS vendor_profile (
    vendor_profile_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    restaurant_name VARCHAR(255) NOT NULL,
    description TEXT,
    cuisine_type VARCHAR(100),
    address_id BIGINT,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_vendor_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_vendor_address FOREIGN KEY (address_id) REFERENCES address(address_id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_restaurant_name (restaurant_name),
    INDEX idx_is_verified (is_verified),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Vendor profile table';

-- Admin Profile Table
CREATE TABLE IF NOT EXISTS admin_profile (
    admin_profile_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    department VARCHAR(100),
    permissions TEXT COMMENT 'Admin permissions (JSON or comma-separated)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_admin_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_department (department)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Admin profile table';

-- Customer Address Junction Table
CREATE TABLE IF NOT EXISTS customer_address (
    customer_address_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_profile_id BIGINT NOT NULL,
    address_id BIGINT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    address_label VARCHAR(50) COMMENT 'e.g., Home, Work, Other',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_customer_address_customer FOREIGN KEY (customer_profile_id) REFERENCES customer_profile(customer_profile_id) ON DELETE CASCADE,
    CONSTRAINT fk_customer_address_address FOREIGN KEY (address_id) REFERENCES address(address_id) ON DELETE CASCADE,
    INDEX idx_customer_profile_id (customer_profile_id),
    INDEX idx_address_id (address_id),
    INDEX idx_is_default (is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Customer addresses junction table';

-- ===========================================================================
-- End of V2 migration
-- ===========================================================================
