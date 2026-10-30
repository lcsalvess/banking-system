-- ============================================================
-- Transaction amount
-- ============================================================

-- Enforce the same upper bound used by the application and account balances.
-- The existing ck_transactions_amount_positive constraint remains in place.
ALTER TABLE public.transactions
    ADD CONSTRAINT ck_transactions_amount_max
        CHECK (amount <= 9999999999999.99);


-- ============================================================
-- Transfer code integrity
-- ============================================================

-- Historical transfers created before V3 may have a NULL transfer_code.
-- However, non-transfer transactions must never contain transfer metadata.
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
-- NOT VALID preserves historical transfers without a code while enforcing
-- the constraint on all new inserts and updates.
ALTER TABLE public.transactions
    ADD CONSTRAINT ck_transactions_transfer_code
        CHECK (
            (
                transaction_type IN ('TRANSFER_SENT', 'TRANSFER_RECEIVED')
                    AND transfer_code IS NOT NULL
                )
                OR
            (
                transaction_type NOT IN ('TRANSFER_SENT', 'TRANSFER_RECEIVED')
                    AND transfer_code IS NULL
                )
            ) NOT VALID;


-- Fail with an explicit message if historical transfer codes are duplicated
-- for the same transaction direction.
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


-- A transfer code can identify at most one transaction of each direction.
-- Legacy transfers without a code are excluded from this index.
CREATE UNIQUE INDEX uk_transactions_transfer_code_type
    ON public.transactions (transfer_code, transaction_type)
    WHERE transfer_code IS NOT NULL
        AND transaction_type IN ('TRANSFER_SENT', 'TRANSFER_RECEIVED');


-- ============================================================
-- Historical system-generated transactions
-- ============================================================

-- Verify that historical YIELD transactions still attributed to a user
-- belong to the technical migration user before normalizing them.
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
-- All other transaction types must have a human actor and must not be
-- marked as system-generated.
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
