-- Track when an account was soft-deleted so it can be reactivated within 30 days
ALTER TABLE users
    ADD COLUMN scheduled_for_deletion_at DATETIME NULL DEFAULT NULL;
