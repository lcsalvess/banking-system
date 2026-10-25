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

        -- Core business data: read, create, and update; never physically deleted.
        GRANT SELECT, INSERT, UPDATE ON TABLE
            public.accounts,
            public.addresses,
            public.clients,
            public.savings_accounts,
            public.users
            TO banking_app;

        -- Financial history is append-only for the runtime role.
        GRANT SELECT, INSERT ON TABLE public.transactions TO banking_app;

        -- Audit history can only be read and appended.
        GRANT SELECT, INSERT ON TABLE public.audit_events TO banking_app;

        -- Joined-inheritance child table of accounts. Hibernate's pessimistic lock
        -- (SELECT ... FOR UPDATE over the joined tables) requires UPDATE on at least
        -- one column of every table it locks. checking_accounts only has the id
        -- column, so the grant is limited to it instead of the whole table.
        GRANT SELECT, INSERT ON TABLE public.checking_accounts TO banking_app;
        GRANT UPDATE (id) ON TABLE public.checking_accounts TO banking_app;

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

        -- Defaults for future objects created by banking_migrator in public.
        ALTER DEFAULT PRIVILEGES FOR ROLE banking_migrator IN SCHEMA public
            REVOKE ALL PRIVILEGES ON TABLES FROM banking_app;
        ALTER DEFAULT PRIVILEGES FOR ROLE banking_migrator IN SCHEMA public
            GRANT SELECT, INSERT ON TABLES TO banking_app;
        ALTER DEFAULT PRIVILEGES FOR ROLE banking_migrator IN SCHEMA public
            REVOKE ALL PRIVILEGES ON SEQUENCES FROM banking_app;
        ALTER DEFAULT PRIVILEGES FOR ROLE banking_migrator IN SCHEMA public
            GRANT USAGE, SELECT ON SEQUENCES TO banking_app;
    END
$$;
