-- ===========================================================================
-- Afrochow Database Migration V32
-- Description: Convert notification.type from a hardcoded MySQL ENUM to
--              VARCHAR — same fix, same reason, as V30 (payment.status).
--
-- Context: notifyVendorNewOrder() writes NotificationType.NEW_ORDER (see the
-- "Fix 1: uses NEW_ORDER type instead of ORDER_UPDATE" comment in
-- NotificationService.java — a prior session already corrected the Java side).
-- The DB's notification.type column, however, is a native MySQL ENUM whose
-- fixed value list was never updated to include 'NEW_ORDER', so every new
-- order silently failed to notify the vendor with "Data truncated for column
-- 'type'" — discovered live while testing the payment retry flow. There is no
-- Flyway migration in this repo that created the notification table at all
-- (it predates Flyway adoption here, baselined under version 16), so its
-- exact original ENUM list is unknown — VARCHAR removes this entire class of
-- bug going forward, same as V30 already did for payment.status.
-- ===========================================================================

ALTER TABLE notification
    MODIFY type VARCHAR(30) NOT NULL;

-- ===========================================================================
-- End of V32 migration
-- ===========================================================================
