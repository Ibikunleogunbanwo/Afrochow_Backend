-- ===========================================================================
-- Afrochow Database Migration V5
-- Description: Create review and notification tables
-- Author: Afrochow Development Team
-- Date: 2025-11-27
-- ===========================================================================

-- Review Table
CREATE TABLE IF NOT EXISTS review (
    review_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT,
    vendor_id BIGINT,
    rating INT NOT NULL,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES product(product_id) ON DELETE CASCADE,
    CONSTRAINT fk_review_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_profile(vendor_profile_id) ON DELETE CASCADE,
    CONSTRAINT chk_rating_range CHECK (rating >= 1 AND rating <= 5),
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id),
    INDEX idx_vendor_id (vendor_id),
    INDEX idx_rating (rating),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Product and vendor reviews table';

-- Notification Table
CREATE TABLE IF NOT EXISTS notification (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type ENUM('ORDER_UPDATE', 'DELIVERY_UPDATE', 'PAYMENT_SUCCESS', 'PAYMENT_FAILED', 'PROMO', 'SYSTEM_ALERT', 'REVIEW_RECEIVED', 'NEW_ORDER') NOT NULL,
    related_entity_type ENUM('ORDER', 'PAYMENT', 'PRODUCT', 'VENDOR', 'REVIEW', 'USER'),
    related_entity_id VARCHAR(100) COMMENT 'Public ID of related entity',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_type (type),
    INDEX idx_is_read (is_read),
    INDEX idx_created_at (created_at),
    INDEX idx_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='User notifications table';

-- ===========================================================================
-- End of V5 migration
-- ===========================================================================
