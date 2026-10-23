-- Define least-privilege grants for the application's runtime database user.
-- In the normal application environment Flyway connects as banking_migrator.
-- The test profile deliberately runs Flyway as banking_test so integration tests
-- can create and clean fixtures. In that profile, skip runtime-role grants.
-- This conditional is intentional: all environments record the same migration,
-- but only the migration-role environment applies production runtime privileges.

DO
$$
    BEGIN
        IF current_user = 'banking_migrator' THEN
            -- Clear direct table grants so the policy is deterministic on existing databases.
            EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE
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
        FROM banking_app';

            -- Core business data can be read, created, and updated, but not physically deleted.
            EXECUTE 'GRANT SELECT, INSERT, UPDATE ON TABLE
            public.accounts,
            public.addresses,
            public.clients,
            public.savings_accounts,
            public.users
        TO banking_app';

            -- Financial transaction history is append-only for the runtime role.
            EXECUTE 'GRANT SELECT, INSERT ON TABLE public.transactions TO banking_app';

            -- Joined-inheritance child table used by checking accounts.
            EXECUTE 'GRANT SELECT, INSERT ON TABLE public.checking_accounts TO banking_app';

            -- Audit history can only be read and appended by the runtime role.
            EXECUTE 'GRANT SELECT, INSERT ON TABLE public.audit_events TO banking_app';

            -- Idempotency requests need updates and scheduled deletion of expired records.
            EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE
            ON TABLE public.transaction_idempotency_requests TO banking_app';

            -- Reserve Flyway metadata for the migration user and grant only needed sequence access.
            EXECUTE 'REVOKE ALL PRIVILEGES ON SEQUENCE
            public.account_number_seq,
            public.accounts_id_seq,
            public.addresses_id_seq,
            public.audit_events_id_seq,
            public.clients_id_seq,
            public.transaction_idempotency_requests_id_seq,
            public.transactions_id_seq,
            public.users_id_seq
        FROM banking_app';

            EXECUTE 'GRANT USAGE, SELECT ON SEQUENCE
            public.account_number_seq,
            public.accounts_id_seq,
            public.addresses_id_seq,
            public.audit_events_id_seq,
            public.clients_id_seq,
            public.transaction_idempotency_requests_id_seq,
            public.transactions_id_seq,
            public.users_id_seq
        TO banking_app';

            -- Restrict defaults for future objects created by banking_migrator in public.
            -- Schema-specific defaults are used to avoid changing defaults in other schemas.
            EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE banking_migrator IN SCHEMA public
            REVOKE ALL PRIVILEGES ON TABLES FROM banking_app';
            EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE banking_migrator IN SCHEMA public
            GRANT SELECT, INSERT ON TABLES TO banking_app';
            EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE banking_migrator IN SCHEMA public
            REVOKE ALL PRIVILEGES ON SEQUENCES FROM banking_app';
            EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE banking_migrator IN SCHEMA public
            GRANT USAGE, SELECT ON SEQUENCES TO banking_app';
        ELSE
            RAISE NOTICE 'Skipping banking_app grants because Flyway is running as %, not banking_migrator', current_user;
        END IF;
    END
$$;
