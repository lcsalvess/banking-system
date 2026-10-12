-- Add mandatory user attribution to transactions.
-- Legacy transactions are attributed to system_migration because
-- their original actor cannot be reliably determined.

-- Create the technical user if it does not already exist.
INSERT INTO users (username,
                   email,
                   password,
                   role,
                   active,
                   created_at,
                   updated_at)
VALUES ('system_migration',
        'system-migration@bankingsystem.com',
        '{noop}DISABLED_SYSTEM_MIGRATION_ACCOUNT',
        'EMPLOYEE',
        FALSE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;

-- Validate the reserved technical user.
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1
                       FROM users
                       WHERE username = 'system_migration'
                         AND email = 'system-migration@bankingsystem.com'
                         AND active = FALSE) THEN
            RAISE EXCEPTION
                'The reserved system_migration user already exists with unexpected data.';
        END IF;
    END
$$;

-- Add the column as nullable to support existing transactions.
ALTER TABLE transactions
    ADD COLUMN performed_by_user_id BIGINT;

-- Attribute legacy transactions to the technical migration user.
UPDATE transactions
SET performed_by_user_id = (SELECT id
                            FROM users
                            WHERE username = 'system_migration')
WHERE performed_by_user_id IS NULL;

-- Enforce mandatory attribution.
ALTER TABLE transactions
    ALTER COLUMN performed_by_user_id SET NOT NULL;

-- Enforce referential integrity.
ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_performed_by_user
        FOREIGN KEY (performed_by_user_id)
            REFERENCES users (id);