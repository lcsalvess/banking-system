-- Transfer existing application database objects to the Flyway migration role.
-- Run as a PostgreSQL administrator against the existing application database.
-- Preconditions: V19 is successfully applied and V20 has not been applied yet.
-- Do not run this script against banking_system_test or a new, empty database.
-- It changes ownership only; it does not modify application data or migration files.

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

        IF NOT has_schema_privilege('banking_migrator', 'public', 'CREATE') THEN
            RAISE EXCEPTION
                'banking_migrator lacks CREATE on schema public; run scripts/db/bootstrap-roles.sql first';
        END IF;

        IF to_regclass('public.flyway_schema_history') IS NULL THEN
            RAISE EXCEPTION
                'flyway_schema_history was not found; this script is only for an existing database migrated through V19';
        END IF;

        IF NOT EXISTS (SELECT 1
                       FROM public.flyway_schema_history
                       WHERE version = '19'
                         AND success) THEN
            RAISE EXCEPTION
                'Flyway migration V19 is not recorded as successful; this script requires an existing schema at V19';
        END IF;

        IF EXISTS (SELECT 1
                   FROM public.flyway_schema_history
                   WHERE version = '20'
                     AND success) THEN
            RAISE EXCEPTION
                'V20 is already recorded as successful; this ownership-transfer script is intended to run before V20';
        END IF;
    END
$$;

-- Transfer table ownership first. Associated indexes and table constraints remain
-- attached to their tables; ownership of owned identity sequences may follow the table.
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

-- Explicitly align every sequence used by this schema, including account numbers.
ALTER SEQUENCE public.account_number_seq OWNER TO banking_migrator;
ALTER SEQUENCE public.accounts_id_seq OWNER TO banking_migrator;
ALTER SEQUENCE public.addresses_id_seq OWNER TO banking_migrator;
ALTER SEQUENCE public.audit_events_id_seq OWNER TO banking_migrator;
ALTER SEQUENCE public.clients_id_seq OWNER TO banking_migrator;
ALTER SEQUENCE public.transaction_idempotency_requests_id_seq OWNER TO banking_migrator;
ALTER SEQUENCE public.transactions_id_seq OWNER TO banking_migrator;
ALTER SEQUENCE public.users_id_seq OWNER TO banking_migrator;

-- The audit triggers call this function; keep its ownership aligned with Flyway.
    ALTER FUNCTION public.audit_events_reject_change() OWNER TO banking_migrator;

COMMIT;
