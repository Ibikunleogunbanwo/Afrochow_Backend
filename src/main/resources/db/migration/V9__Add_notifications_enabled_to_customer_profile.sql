-- Add notification opt-out toggle to customer_profile
ALTER TABLE customer_profile
    ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;
