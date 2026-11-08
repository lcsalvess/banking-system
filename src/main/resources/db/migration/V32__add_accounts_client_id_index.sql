-- Index that supports the listing of the accounts of a client
-- (GET /api/v1/clients/{clientId}/accounts).
--
-- uk_accounts_client_type_active already starts with client_id, but it is
-- partial (status = 'ACTIVE'), so PostgreSQL can only use it when the query is
-- restricted to active accounts. This index covers every status, including the
-- cancelled accounts that remain part of a client's history.
--
-- Plain CREATE INDEX (not CONCURRENTLY) keeps this migration transactional.
-- It blocks writes to accounts while the index is built, which is negligible
-- on a small table; on a large existing table, create the index CONCURRENTLY
-- out of band before deploying.

CREATE INDEX idx_accounts_client_id
    ON accounts (client_id);
