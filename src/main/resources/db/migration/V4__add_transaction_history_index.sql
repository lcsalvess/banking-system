CREATE INDEX idx_transactions_account_created_id
    ON transactions (account_id, created_at DESC, id DESC);
