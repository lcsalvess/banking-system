-- Provision the runtime and migration roles for the currently connected application database.
-- Run as a PostgreSQL administrator after creating the application database.
-- Automated tests should use their dedicated test database role and test setup.
-- Passwords are intentionally not stored in this script.

DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM pg_roles
            WHERE rolname = 'banking_app'
        ) THEN
            CREATE ROLE banking_app LOGIN;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_roles
            WHERE rolname = 'banking_migrator'
        ) THEN
            CREATE ROLE banking_migrator LOGIN;
        END IF;
    END
$$;

-- Enforce that neither account is a superuser or can administer the cluster.
ALTER ROLE banking_app
    WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

ALTER ROLE banking_migrator
    WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

-- Grant database connection privileges for the currently connected database.
DO $$
    BEGIN
        EXECUTE format(
                'GRANT CONNECT ON DATABASE %I TO banking_app, banking_migrator',
                current_database()
                );
    END
$$;

-- Runtime can use objects but cannot create or replace schema objects.
GRANT USAGE ON SCHEMA public TO banking_app;
REVOKE CREATE ON SCHEMA public FROM banking_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- Flyway needs schema access to create and alter its database objects.
GRANT USAGE, CREATE ON SCHEMA public TO banking_migrator;
