-- Add mandatory user attribution to transactions.
ALTER TABLE transactions
    ADD COLUMN performed_by_user_id BIGINT NOT NULL;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_performed_by_user
        FOREIGN KEY (performed_by_user_id)
            REFERENCES users (id);