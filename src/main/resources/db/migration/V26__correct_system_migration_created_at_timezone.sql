-- Correct the historical timezone interpretation of the
-- system_migration user's creation timestamp, when present.
--
-- V6 inserted CURRENT_TIMESTAMP into a TIMESTAMP WITHOUT TIME ZONE
-- column while the Flyway session used America/Sao_Paulo.
--
-- V24 incorrectly interpreted this particular value as UTC.
-- This migration restores its original Sao Paulo interpretation.
--
-- The technical user may be absent in test databases because
-- integration-test fixtures clean up the users table.

UPDATE users
SET created_at =
        (created_at AT TIME ZONE 'UTC')
            AT TIME ZONE 'America/Sao_Paulo'
WHERE username = 'system_migration';
