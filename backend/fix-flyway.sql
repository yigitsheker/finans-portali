-- Fix Flyway migration history
-- Remove failed V2 migration record
DELETE FROM flyway_schema_history WHERE version = '2' AND success = false;
