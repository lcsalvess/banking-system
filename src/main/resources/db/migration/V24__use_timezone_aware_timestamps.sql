-- Convert application timestamps to timezone-aware instants.
--
-- Existing application timestamps represent America/Sao_Paulo local time.
-- Exception: system_migration.created_at was inserted directly by V6
-- using CURRENT_TIMESTAMP into a TIMESTAMP WITHOUT TIME ZONE column.
-- Converting it as UTC assumes the PostgreSQL session used by Flyway
-- during V6 was configured for UTC. The current database timezone does
-- not, by itself, prove the historical session timezone.
--
-- This is an explicit legacy-data conversion. Do not change historical
-- migrations: Flyway has already recorded and applied them.

-- Remove legacy defaults that produce local timestamps without a timezone.
ALTER TABLE audit_events
    ALTER COLUMN created_at DROP DEFAULT;

ALTER TABLE transaction_idempotency_requests
    ALTER COLUMN created_at DROP DEFAULT;

-- Convert user timestamps. The technical user's created_at was populated
-- directly by V6; its updated_at is managed by the application callback.
ALTER TABLE users
    ALTER COLUMN created_at
        TYPE TIMESTAMP(6) WITH TIME ZONE
        USING CASE
                  WHEN username = 'system_migration'
                      THEN created_at AT TIME ZONE 'UTC'
                  ELSE created_at AT TIME ZONE 'America/Sao_Paulo'
        END,
    ALTER COLUMN updated_at
        TYPE TIMESTAMP(6) WITH TIME ZONE
        USING updated_at AT TIME ZONE 'America/Sao_Paulo';

-- Transaction timestamps are created by the application using its
-- America/Sao_Paulo clock before conversion to Instant.
ALTER TABLE transactions
    ALTER COLUMN created_at
        TYPE TIMESTAMP(6) WITH TIME ZONE
        USING created_at AT TIME ZONE 'America/Sao_Paulo';

-- Audit timestamps historically use the local Sao Paulo representation.
ALTER TABLE audit_events
    ALTER COLUMN created_at
        TYPE TIMESTAMP(6) WITH TIME ZONE
        USING created_at AT TIME ZONE 'America/Sao_Paulo';

-- Idempotency timestamps use the same explicit local-time convention.
ALTER TABLE transaction_idempotency_requests
    ALTER COLUMN created_at
        TYPE TIMESTAMP(6) WITH TIME ZONE
        USING created_at AT TIME ZONE 'America/Sao_Paulo';

-- New records now store the current instant directly.
ALTER TABLE audit_events
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE transaction_idempotency_requests
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;