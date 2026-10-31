-- Append-only ledger.
--
-- The runtime role (banking_app) already has no UPDATE or DELETE privilege on
-- transactions, but privileges do not stop the table owner or a database
-- administrator from rewriting financial history by accident. These triggers
-- enforce the rule for every role, the same way V19 does for audit_events.
--
-- A migration that must correct ledger rows has to say so explicitly, inside
-- the migration, and restore the protection before it ends:
--
--   ALTER TABLE transactions DISABLE TRIGGER trg_transactions_no_update_delete;
--   -- corrective UPDATE / DELETE
--   ALTER TABLE transactions ENABLE TRIGGER trg_transactions_no_update_delete;
--
-- Flyway runs each migration in a transaction, so a failure rolls the
-- DISABLE back as well.

CREATE FUNCTION transactions_reject_change()
    RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'transactions is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END
$$;

CREATE TRIGGER trg_transactions_no_update_delete
    BEFORE UPDATE OR DELETE
    ON transactions
    FOR EACH ROW
EXECUTE FUNCTION transactions_reject_change();

CREATE TRIGGER trg_transactions_no_truncate
    BEFORE TRUNCATE
    ON transactions
    FOR EACH STATEMENT
EXECUTE FUNCTION transactions_reject_change();
