-- Transfer existing application database objects to the Flyway migration role.
--
-- Run as a PostgreSQL administrator against the existing application database.
--
-- Preconditions:
-- - V19 is successfully applied.
-- - The banking_migrator role exists.
-- - banking_migrator has CREATE on schema public.
--
-- This script can run before V20 or after later migrations.
-- Objects introduced by later migrations are transferred only when they exist.
--
-- It is idempotent: objects already owned by banking_migrator remain owned
-- by that role.
--
-- Do not run this script against banking_system_test or a new, empty database.
-- It changes ownership only; it does not modify application data or migrations.

BEGIN;

DO
$$
    BEGIN
        IF current_database() = 'banking_system_test' THEN
            RAISE EXCEPTION
                'Refusing to transfer application ownership in the test database banking_system_test';
        END IF;

        IF NOT EXISTS (SELECT 1
                       FROM pg_roles
                       WHERE rolname = 'banking_migrator') THEN
            RAISE EXCEPTION
                'Role banking_migrator does not exist; run scripts/db/bootstrap-roles.sql first';
        END IF;

        IF NOT has_schema_privilege(
                'banking_migrator',
                'public',
                'CREATE'
               ) THEN
            RAISE EXCEPTION
                'banking_migrator lacks CREATE on schema public; run scripts/db/bootstrap-roles.sql first';
        END IF;

        IF to_regclass('public.flyway_schema_history') IS NULL THEN
            RAISE EXCEPTION
                'flyway_schema_history was not found; this script requires an existing database migrated through at least V19';
        END IF;

        IF NOT EXISTS (SELECT 1
                       FROM public.flyway_schema_history
                       WHERE version = '19'
                         AND success) THEN
            RAISE EXCEPTION
                'Flyway migration V19 is not recorded as successful; this script requires an existing schema at V19 or later';
        END IF;
    END
$$;

-- ============================================================
-- Existing tables (V19 and earlier)
-- ============================================================

ALTER TABLE public.accounts
    OWNER TO banking_migrator;

ALTER TABLE public.addresses
    OWNER TO banking_migrator;

ALTER TABLE public.audit_events
    OWNER TO banking_migrator;

ALTER TABLE public.checking_accounts
    OWNER TO banking_migrator;

ALTER TABLE public.clients
    OWNER TO banking_migrator;

ALTER TABLE public.flyway_schema_history
    OWNER TO banking_migrator;

ALTER TABLE public.savings_accounts
    OWNER TO banking_migrator;

ALTER TABLE public.transaction_idempotency_requests
    OWNER TO banking_migrator;

ALTER TABLE public.transactions
    OWNER TO banking_migrator;

ALTER TABLE public.users
    OWNER TO banking_migrator;


-- ============================================================
-- Existing sequences (V19 and earlier)
-- ============================================================

ALTER SEQUENCE public.account_number_seq
    OWNER TO banking_migrator;

ALTER SEQUENCE public.accounts_id_seq
    OWNER TO banking_migrator;

ALTER SEQUENCE public.addresses_id_seq
    OWNER TO banking_migrator;

ALTER SEQUENCE public.audit_events_id_seq
    OWNER TO banking_migrator;

ALTER SEQUENCE public.clients_id_seq
    OWNER TO banking_migrator;

ALTER SEQUENCE public.transaction_idempotency_requests_id_seq
    OWNER TO banking_migrator;

ALTER SEQUENCE public.transactions_id_seq
    OWNER TO banking_migrator;

ALTER SEQUENCE public.users_id_seq
    OWNER TO banking_migrator;


-- ============================================================
-- Existing audit trigger function (V19)
-- ============================================================

ALTER FUNCTION public.audit_events_reject_change()
    OWNER TO banking_migrator;


-- ============================================================
-- Objects introduced by later migrations
-- ============================================================

DO
$$
    BEGIN
        -- --------------------------------------------------------
        -- V27: append-only transaction ledger
        -- --------------------------------------------------------

        IF to_regprocedure(
                'public.transactions_reject_change()'
           ) IS NOT NULL THEN
            EXECUTE
                'ALTER FUNCTION public.transactions_reject_change() OWNER TO banking_migrator';
        END IF;


        -- --------------------------------------------------------
        -- V29: audit query snapshot tables
        -- --------------------------------------------------------

        IF to_regclass(
                'public.audit_event_query_snapshots'
           ) IS NOT NULL THEN
            EXECUTE
                'ALTER TABLE public.audit_event_query_snapshots OWNER TO banking_migrator';
        END IF;

        IF to_regclass(
                'public.audit_event_query_snapshot_items'
           ) IS NOT NULL THEN
            EXECUTE
                'ALTER TABLE public.audit_event_query_snapshot_items OWNER TO banking_migrator';
        END IF;


        -- --------------------------------------------------------
        -- V33: employee identity sequence
        -- --------------------------------------------------------

        IF to_regclass(
                'public.employee_id_seq'
           ) IS NOT NULL THEN
            EXECUTE
                'ALTER SEQUENCE public.employee_id_seq OWNER TO banking_migrator';
        END IF;


        -- --------------------------------------------------------
        -- V33: employee ID generator
        -- --------------------------------------------------------

        IF to_regprocedure(
                'public.next_employee_id()'
           ) IS NOT NULL THEN
            EXECUTE
                'ALTER FUNCTION public.next_employee_id() OWNER TO banking_migrator';
        END IF;


        -- --------------------------------------------------------
        -- V33: user identity validation trigger
        -- --------------------------------------------------------

        IF to_regprocedure(
                'public.validate_user_identity_before_insert()'
           ) IS NOT NULL THEN
            EXECUTE
                'ALTER FUNCTION public.validate_user_identity_before_insert() OWNER TO banking_migrator';
        END IF;


        -- --------------------------------------------------------
        -- V33: user identity immutability trigger
        -- --------------------------------------------------------

        IF to_regprocedure(
                'public.prevent_user_identity_update()'
           ) IS NOT NULL THEN
            EXECUTE
                'ALTER FUNCTION public.prevent_user_identity_update() OWNER TO banking_migrator';
        END IF;
    END
$$;

COMMIT;
