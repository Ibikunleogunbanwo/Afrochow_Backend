-- ===========================================================================
-- Afrochow Database Migration V8
-- Description: Add FREE_DELIVERY to the promotion.type ENUM column.
--              Hibernate 6+ maps @Enumerated(EnumType.STRING) to MySQL ENUM,
--              so adding a new enum value requires an explicit ALTER TABLE.
-- Author: Afrochow Development Team
-- Date: 2026-03-23
-- ===========================================================================

ALTER TABLE promotion
    MODIFY COLUMN type ENUM('PERCENTAGE', 'FIXED_AMOUNT', 'FREE_DELIVERY') NOT NULL;

-- ===========================================================================
-- End of V8 migration
-- ===========================================================================
