-- Source of truth for the runtime role (banking_app) privileges.
--
-- Flyway runs this callback after every successful migrate, so the policy is
-- re-applied on each startup and does not depend on the one-shot, conditional
-- V20/V21 migrations (which Flyway never retries once recorded as applied).
--
-- It is registered through spring.flyway.locations in the local and prod
-- profiles only. The test profile runs Flyway as the fixture owner and does
-- not load it. If Flyway runs as any other user, startup fails instead of
-- silently leaving the runtime role without (or with excess) privileges.
--
-- The whole policy is one DO block, so it is applied atomically: there is no
-- window in which banking_app has lost its grants but not yet received them.

DO
$$
    BEGIN
        IF current_user <> 'banking_migrator' THEN
            RAISE EXCEPTION
                'Runtime privileges must be applied by banking_migrator, but Flyway is running as %. Check DB_MIGRATOR_USERNAME and DB_MIGRATOR_PASSWORD.',
                current_user;
        END IF;

        -- Start from a clean slate so the result is deterministic on existing databases.
        REVOKE ALL PRIVILEGES ON TABLE
            public.accounts,
            public.addresses,
            public.audit_events,
            public.checking_accounts,
            public.clients,
            public.flyway_schema_history,
            public.savings_accounts,
            public.transaction_idempotency_requests,
            public.transactions,
            public.users
            FROM banking_app;

        -- Core business data: read and create.
        -- Updates are restricted to the specific columns required by the application.
        GRANT SELECT, INSERT ON TABLE
            public.accounts,
            public.addresses,
            public.clients,
            public.savings_accounts,
            public.users
            TO banking_app;

        -- Accounts: balance and status are the only mutable business attributes.
        GRANT UPDATE (balance, status)
            ON TABLE public.accounts TO banking_app;

        -- Clients: only contact and identification-display data can be updated.
        GRANT UPDATE (name, email, phone_number)
            ON TABLE public.clients TO banking_app;

        -- Addresses: only address information can be updated.
        GRANT UPDATE (
                      city,
                      complement,
                      neighborhood,
                      postal_code,
                      state,
                      street_name,
                      street_number
            )
            ON TABLE public.addresses TO banking_app;

        -- Savings accounts: only yield processing dates can be updated.
        GRANT UPDATE (last_yield_date, last_yield_processed_date)
            ON TABLE public.savings_accounts TO banking_app;

        -- Users: only authentication status and password-related fields can be updated.
        GRANT UPDATE (password, active, token_version, updated_at)
            ON TABLE public.users TO banking_app;

        -- Joined-inheritance child table.
        -- The application only needs to read and create checking accounts.
        -- Pessimistic locking is now performed exclusively on the accounts table.
        GRANT SELECT, INSERT ON TABLE public.checking_accounts TO banking_app;

        -- Financial history is append-only for the runtime role.
        GRANT SELECT, INSERT ON TABLE public.transactions TO banking_app;

        -- Audit history can only be read and appended.
        GRANT SELECT, INSERT ON TABLE public.audit_events TO banking_app;

        -- Idempotency records are updated and deleted by the scheduled cleanup.
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON TABLE public.transaction_idempotency_requests TO banking_app;

        -- Sequences: only what the runtime needs. flyway_schema_history stays reserved.
        REVOKE ALL PRIVILEGES ON SEQUENCE
            public.account_number_seq,
            public.accounts_id_seq,
            public.addresses_id_seq,
            public.audit_events_id_seq,
            public.clients_id_seq,
            public.transaction_idempotency_requests_id_seq,
            public.transactions_id_seq,
            public.users_id_seq
            FROM banking_app;

        GRANT USAGE, SELECT ON SEQUENCE
            public.account_number_seq,
            public.accounts_id_seq,
            public.addresses_id_seq,
            public.audit_events_id_seq,
            public.clients_id_seq,
            public.transaction_idempotency_requests_id_seq,
            public.transactions_id_seq,
            public.users_id_seq
            TO banking_app;

        -- Future objects get no default privileges for banking_app, so a new
        -- table or sequence is closed to the runtime role until it is granted
        -- explicitly above. These REVOKEs also clear any default ACL left by
        -- earlier versions of this callback, which granted SELECT and INSERT.
        ALTER DEFAULT PRIVILEGES FOR ROLE banking_migrator IN SCHEMA public
            REVOKE ALL PRIVILEGES ON TABLES FROM banking_app;
        ALTER DEFAULT PRIVILEGES FOR ROLE banking_migrator IN SCHEMA public
            REVOKE ALL PRIVILEGES ON SEQUENCES FROM banking_app;
    END
$$;
