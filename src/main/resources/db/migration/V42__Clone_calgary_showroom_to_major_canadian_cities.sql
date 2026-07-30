-- ===========================================================================
-- Afrochow Database Migration V42
-- Description: Reserved bridge migration for major-city showroom cloning.
--
-- The first V42 attempt was too strict for production Calgary source data and
-- failed before any major-city data could be created. Keep this version as a
-- harmless bridge so Flyway can advance cleanly; V43 performs the live Calgary
-- showroom clone for Toronto, Vancouver, Winnipeg, Saskatoon, Halifax, Moncton,
-- Charlottetown, and St Johns.
-- ===========================================================================

SELECT 1;

-- ===========================================================================
-- End of V42 migration
-- ===========================================================================
