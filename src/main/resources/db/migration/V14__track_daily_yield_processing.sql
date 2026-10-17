-- Track the last calendar date processed for each savings account.
ALTER TABLE savings_accounts
    ADD COLUMN last_yield_processed_date DATE;

-- Start recovery from the deployment date instead of granting
-- retroactive yields for the entire historical period.
UPDATE savings_accounts
SET last_yield_processed_date = GREATEST(
        last_yield_date,
        (CURRENT_TIMESTAMP AT TIME ZONE 'America/Sao_Paulo')::date - 1
                                )
WHERE last_yield_processed_date IS NULL;

ALTER TABLE savings_accounts
    ALTER COLUMN last_yield_processed_date SET NOT NULL;