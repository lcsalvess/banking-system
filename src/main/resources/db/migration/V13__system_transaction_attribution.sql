
-- Allow automated transactions to have no human actor.
ALTER TABLE transactions
    ALTER COLUMN performed_by_user_id DROP NOT NULL;

-- Explicitly identify transactions performed by the system.
ALTER TABLE transactions
    ADD COLUMN performed_by_system BOOLEAN NOT NULL DEFAULT FALSE;

-- A system transaction must be a YIELD with no human actor.
-- Human-attributed transactions must always reference a user.
ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_actor
        CHECK (
            (
                performed_by_system = TRUE
                    AND performed_by_user_id IS NULL
                    AND transaction_type = 'YIELD'
                )
                OR
            (
                performed_by_system = FALSE
                    AND performed_by_user_id IS NOT NULL
                )
            );
