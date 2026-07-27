-- The `notification` table predates/bypasses Flyway management in this schema
-- (no earlier migration references it), so its `message` column was left at
-- whatever Hibernate's ddl-auto defaulted an unannotated String field to:
-- VARCHAR(255). The admin broadcast UI allows messages up to 500 characters,
-- so any broadcast over ~255 chars threw DataIntegrityViolationException
-- ("Data too long for column 'message'") deep in an @Async background thread
-- — after the HTTP request had already returned "Broadcast sent successfully"
-- to the admin. Widen to TEXT to match BroadcastLog.message, which already
-- uses TEXT for the same content.
ALTER TABLE notification MODIFY COLUMN message TEXT NULL;
