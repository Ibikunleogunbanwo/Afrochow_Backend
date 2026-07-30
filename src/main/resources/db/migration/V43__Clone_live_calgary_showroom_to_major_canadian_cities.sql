-- ===========================================================================
-- Afrochow Database Migration V43
-- Description: Reserved bridge migration for live Calgary showroom cloning.
--
-- A production attempt of V43 failed after V42 was repaired. Keep this version
-- as a harmless bridge so Flyway can advance cleanly; V44 performs the live
-- Calgary showroom clone for the additional Canadian cities using durable
-- staging tables that are dropped at the end of the migration.
-- ===========================================================================

SELECT 1;

-- ===========================================================================
-- End of V43 migration
-- ===========================================================================
