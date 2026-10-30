-- ============================================================
-- Transaction amount integrity
-- ============================================================

-- Enforce the same upper bound used by the application and
-- account balances.
-- The existing ck_transactions_amount_positive constraint
-- remains in place.
ALTER TABLE public.transactions
    ADD CONSTRAINT ck_transactions_amount_max
        CHECK (amount <= 9999999999999.99);


-- ============================================================
-- Transfer code integrity
-- ============================================================

-- Historical transfers created before V3 may have a NULL
-- transfer_code.
--
-- Non-transfer transactions must never contain transfer metadata.
-- Fail rather than silently preserve unexpected historical data.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM public.transactions
                   WHERE transaction_type NOT IN (
                                                  'TRANSFER_SENT',
                                                  'TRANSFER_RECEIVED'
                       )
                     AND transfer_code IS NOT NULL) THEN
            RAISE EXCEPTION
                'Non-transfer transactions contain an unexpected transfer_code.';
        END IF;
    END
$$;


-- Transfer transactions must carry a transfer code.
-- Non-transfer transactions must not carry transfer metadata.
--
-- NOT VALID preserves historical transfers without a code while
-- enforcing the constraint on all new inserts and updates.
ALTER TABLE public.transactions
    ADD CONSTRAINT ck_transactions_transfer_code
        CHECK (
            (
                transaction_type IN (
                                     'TRANSFER_SENT',
                                     'TRANSFER_RECEIVED'
                    )
                    AND transfer_code IS NOT NULL
                )
                OR
            (
                transaction_type NOT IN (
                                         'TRANSFER_SENT',
                                         'TRANSFER_RECEIVED'
                    )
                    AND transfer_code IS NULL
                )
            ) NOT VALID;


-- Fail with an explicit message if historical transfer codes
-- are duplicated for the same transaction direction.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM public.transactions
                   WHERE transfer_code IS NOT NULL
                     AND transaction_type IN (
                                              'TRANSFER_SENT',
                                              'TRANSFER_RECEIVED'
                       )
                   GROUP BY transfer_code, transaction_type
                   HAVING COUNT(*) > 1) THEN
            RAISE EXCEPTION
                'Duplicate transfer_code found for the same transaction direction.';
        END IF;
    END
$$;


-- A transfer code can identify at most one transaction
-- of each direction.
--
-- Legacy transfers without a code are excluded from this index.
CREATE UNIQUE INDEX uk_transactions_transfer_code_type
    ON public.transactions (
                            transfer_code,
                            transaction_type
        )
    WHERE transfer_code IS NOT NULL
        AND transaction_type IN (
                                 'TRANSFER_SENT',
                                 'TRANSFER_RECEIVED'
            );


-- ============================================================
-- Historical transfer pair validation
-- ============================================================

-- Every historical transfer that already has a code must form
-- a complete and consistent pair.
--
-- Legacy transfers without a code remain untouched.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM public.transactions
                   WHERE transfer_code IS NOT NULL
                   GROUP BY transfer_code
                   HAVING COUNT(*) <> 2
                       OR COUNT(*) FILTER (
                       WHERE transaction_type = 'TRANSFER_SENT'
                       ) <> 1
                       OR COUNT(*) FILTER (
                       WHERE transaction_type = 'TRANSFER_RECEIVED'
                       ) <> 1
                       OR COUNT(DISTINCT amount) <> 1
                       OR COUNT(DISTINCT account_id) <> 2
                       OR COUNT(DISTINCT performed_by_user_id) <> 1) THEN
            RAISE EXCEPTION
                'Historical transfer codes must identify exactly one consistent transfer pair.';
        END IF;
    END
$$;


-- ============================================================
-- Transfer pair integrity
-- ============================================================

-- Validate the complete transfer pair.
--
-- The advisory transaction lock serializes integrity checks
-- involving the same transfer code.
CREATE OR REPLACE FUNCTION public.assert_transaction_transfer_pair(
    p_transfer_code UUID
)
    RETURNS VOID
    LANGUAGE plpgsql
AS
$$
DECLARE
    transfer_count BIGINT;
    sent_count     BIGINT;
    received_count BIGINT;
    amount_count   BIGINT;
    account_count  BIGINT;
    actor_count    BIGINT;
BEGIN
    IF p_transfer_code IS NULL THEN
        RETURN;
    END IF;

    PERFORM pg_advisory_xact_lock(
            hashtextextended(p_transfer_code::TEXT, 0)
            );

    SELECT COUNT(*),
           COUNT(*) FILTER (
               WHERE transaction_type = 'TRANSFER_SENT'
               ),
           COUNT(*) FILTER (
               WHERE transaction_type = 'TRANSFER_RECEIVED'
               ),
           COUNT(DISTINCT amount),
           COUNT(DISTINCT account_id),
           COUNT(DISTINCT performed_by_user_id)
    INTO
        transfer_count,
        sent_count,
        received_count,
        amount_count,
        account_count,
        actor_count
    FROM public.transactions
    WHERE transfer_code = p_transfer_code;

    IF transfer_count <> 2
        OR sent_count <> 1
        OR received_count <> 1
        OR amount_count <> 1
        OR account_count <> 2
        OR actor_count <> 1
    THEN
        RAISE EXCEPTION
            'Transfer code % must identify exactly one sent and one received transaction with the same amount and actor on different accounts.',
            p_transfer_code
            USING
                ERRCODE = '23514',
                CONSTRAINT = 'ck_transactions_transfer_pair';
    END IF;
END;
$$;


-- Trigger function responsible for validating affected transfer codes.
--
-- INSERT: validates the new transfer code.
-- UPDATE: validates the old code and, if changed, the new code.
-- DELETE: validates the old transfer code.
--
-- This prevents an already valid transfer pair from becoming
-- inconsistent after it has been created.
CREATE OR REPLACE FUNCTION public.enforce_transaction_transfer_pair()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM public.assert_transaction_transfer_pair(
                NEW.transfer_code
                );

    ELSIF TG_OP = 'DELETE' THEN
        PERFORM public.assert_transaction_transfer_pair(
                OLD.transfer_code
                );

    ELSE
        PERFORM public.assert_transaction_transfer_pair(
                OLD.transfer_code
                );

        IF NEW.transfer_code IS DISTINCT FROM OLD.transfer_code THEN
            PERFORM public.assert_transaction_transfer_pair(
                    NEW.transfer_code
                    );
        END IF;
    END IF;

    RETURN NULL;
END;
$$;


-- The complete pair is validated at transaction commit.
--
-- This is necessary because the application inserts the sent
-- and received transactions within the same @Transactional method.
CREATE CONSTRAINT TRIGGER ct_transactions_transfer_pair
    AFTER INSERT OR UPDATE OR DELETE
    ON public.transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION public.enforce_transaction_transfer_pair();


-- ============================================================
-- Historical system-generated transaction validation
-- ============================================================

-- Verify that historical YIELD transactions still attributed
-- to a user belong to the technical migration user.
--
-- Unexpected attribution must cause the migration to fail.
DO
$$
    DECLARE
        system_migration_user_id BIGINT;
    BEGIN
        SELECT id
        INTO STRICT system_migration_user_id
        FROM public.users
        WHERE username = 'system_migration';

        IF EXISTS (SELECT 1
                   FROM public.transactions
                   WHERE transaction_type = 'YIELD'
                     AND performed_by_system IS FALSE
                     AND performed_by_user_id IS DISTINCT FROM system_migration_user_id) THEN
            RAISE EXCEPTION
                'Unexpected user attribution found in historical YIELD transactions.';
        END IF;
    END
$$;


-- Normalize legacy YIELD transactions attributed to system_migration.
UPDATE public.transactions
SET performed_by_system  = TRUE,
    performed_by_user_id = NULL
WHERE transaction_type = 'YIELD'
  AND performed_by_system IS FALSE
  AND performed_by_user_id = (SELECT id
                              FROM public.users
                              WHERE username = 'system_migration');


-- ============================================================
-- Strict transaction actor attribution
-- ============================================================

-- YIELD transactions must be system-generated and have no human actor.
-- All other transaction types must have a human actor and must not
-- be marked as system-generated.
ALTER TABLE public.transactions
    DROP CONSTRAINT ck_transactions_actor;

ALTER TABLE public.transactions
    ADD CONSTRAINT ck_transactions_actor
        CHECK (
            (
                transaction_type = 'YIELD'
                    AND performed_by_system = TRUE
                    AND performed_by_user_id IS NULL
                )
                OR
            (
                transaction_type <> 'YIELD'
                    AND performed_by_system = FALSE
                    AND performed_by_user_id IS NOT NULL
                )
            );


-- ============================================================
-- Historical yield account validation
-- ============================================================

-- Every historical YIELD transaction must belong to an actual
-- savings account, including its corresponding inheritance row.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM public.transactions t
                   WHERE t.transaction_type = 'YIELD'
                     AND NOT EXISTS (SELECT 1
                                     FROM public.accounts a
                                              INNER JOIN public.savings_accounts sa
                                                         ON sa.id = a.id
                                     WHERE a.id = t.account_id
                                       AND a.account_type = 'SAVINGS')) THEN
            RAISE EXCEPTION
                'Historical YIELD transactions must belong to a savings account.';
        END IF;
    END
$$;


-- ============================================================
-- Yield account integrity
-- ============================================================

-- YIELD transactions must belong to an actual savings account.
--
-- FOR SHARE prevents the account type or savings subtype row
-- from being concurrently removed while the yield is registered.
CREATE OR REPLACE FUNCTION public.enforce_transaction_yield_savings_account()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF NEW.transaction_type = 'YIELD' THEN

        PERFORM 1
        FROM public.accounts a
                 INNER JOIN public.savings_accounts sa
                            ON sa.id = a.id
        WHERE a.id = NEW.account_id
          AND a.account_type = 'SAVINGS'
            FOR SHARE OF a, sa;

        IF NOT FOUND THEN
            RAISE EXCEPTION
                'YIELD transactions must belong to a savings account.'
                USING
                    ERRCODE = '23514',
                    CONSTRAINT = 'ck_transactions_yield_savings_account';
        END IF;

    END IF;

    RETURN NEW;
END;
$$;


CREATE TRIGGER tr_transactions_yield_savings_account
    BEFORE INSERT OR UPDATE
    ON public.transactions
    FOR EACH ROW
EXECUTE FUNCTION public.enforce_transaction_yield_savings_account();


-- ============================================================
-- Protect savings account subtype integrity
-- ============================================================

-- A savings account cannot lose its subtype row while it has
-- historical YIELD transactions.
CREATE OR REPLACE FUNCTION public.prevent_savings_account_removal_with_yield()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF EXISTS (SELECT 1
               FROM public.transactions
               WHERE transaction_type = 'YIELD'
                 AND account_id = OLD.id) THEN
        RAISE EXCEPTION
            'A savings account with YIELD transactions cannot lose its savings subtype.'
            USING
                ERRCODE = '23514',
                CONSTRAINT = 'ck_transactions_yield_savings_account';
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    RETURN NEW;
END;
$$;


CREATE TRIGGER tr_savings_accounts_protect_yield_transactions
    BEFORE DELETE OR UPDATE OF id
    ON public.savings_accounts
    FOR EACH ROW
EXECUTE FUNCTION public.prevent_savings_account_removal_with_yield();


-- An account with historical YIELD transactions cannot cease
-- to be classified as a savings account.
CREATE OR REPLACE FUNCTION public.prevent_account_type_change_with_yield()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF OLD.account_type IS DISTINCT FROM NEW.account_type
        AND EXISTS (SELECT 1
                    FROM public.transactions
                    WHERE transaction_type = 'YIELD'
                      AND account_id = OLD.id)
    THEN
        RAISE EXCEPTION
            'An account with YIELD transactions cannot change its account type.'
            USING
                ERRCODE = '23514',
                CONSTRAINT = 'ck_transactions_yield_savings_account';
    END IF;

    RETURN NEW;
END;
$$;


CREATE TRIGGER tr_accounts_protect_yield_transactions
    BEFORE UPDATE OF account_type
    ON public.accounts
    FOR EACH ROW
EXECUTE FUNCTION public.prevent_account_type_change_with_yield();
