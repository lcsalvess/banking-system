package com.lcsalvess.bankingsystem.integration.security;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies against a real PostgreSQL instance that the schema migrated as
 * {@code banking_migrator}, including the runtime privilege callback,
 * grants {@code banking_app} exactly what the application needs.
 *
 * <p>Regular tests run as the schema owner, so missing grants (such as UPDATE
 * on checking_accounts) would otherwise go unnoticed.
 *
 * <p>Also verifies that the ledger append-only triggers (V27) exist and are
 * enabled because privileges alone do not protect the schema owner.
 *
 * <p>Does not use the Spring application context. Tests are skipped when the
 * required environment variables are missing. The CI {@code least-privilege}
 * job configures them. Requires an empty database with roles provisioned by
 * {@code scripts/db/bootstrap-roles.sql}.
 *
 * <p>Must run before {@code RuntimePrivilegesEndToEndTests} against the same
 * database. This test verifies migrations from an empty database, including
 * the Flyway session timezone. The CI job runs both classes in separate steps,
 * in this order.
 */
class LeastPrivilegeSmokeTests {

    private static final String PERMISSION_DENIED = "42501";

    private static String url;
    private static String migratorUser;
    private static String migratorPassword;
    private static String appUser;
    private static String appPassword;

    @BeforeAll
    static void migrateAsMigrator() {
        url = System.getenv("PRIVILEGE_TEST_DB_URL");
        migratorUser = System.getenv("PRIVILEGE_TEST_MIGRATOR_USER");
        migratorPassword = System.getenv("PRIVILEGE_TEST_MIGRATOR_PASSWORD");
        appUser = System.getenv("PRIVILEGE_TEST_APP_USER");
        appPassword = System.getenv("PRIVILEGE_TEST_APP_PASSWORD");

        assumeTrue(
                url != null && migratorUser != null && migratorPassword != null
                        && appUser != null && appPassword != null,
                "PRIVILEGE_TEST_* environment variables are not set"
        );

        Flyway.configure()
                .dataSource(url, migratorUser, migratorPassword)
                .locations("classpath:db/migration", "classpath:db/callback")
                .callbacks(timezoneVerificationCallback())
                .load()
                .migrate();
    }

    @Test
    @DisplayName("Runtime role can lock the base account table with FOR UPDATE")
    void shouldLockAccountBaseTable() {
        assertDoesNotThrow(() -> executeAsApp(
                "SELECT id FROM public.accounts FOR UPDATE"
        ));
    }

    @Test
    @DisplayName("Runtime role can do the work it needs")
    void shouldAllowRuntimeOperations() {
        List<String> allowed = List.of(
                // SELECT privileges
                "SELECT 1 FROM public.accounts LIMIT 1",
                "SELECT 1 FROM public.addresses LIMIT 1",
                "SELECT 1 FROM public.audit_events LIMIT 1",
                "SELECT 1 FROM public.audit_event_query_snapshots LIMIT 1",
                "SELECT 1 FROM public.audit_event_query_snapshot_items LIMIT 1",
                "SELECT 1 FROM public.checking_accounts LIMIT 1",
                "SELECT 1 FROM public.clients LIMIT 1",
                "SELECT 1 FROM public.savings_accounts LIMIT 1",
                "SELECT 1 FROM public.transactions LIMIT 1",
                "SELECT 1 FROM public.users LIMIT 1",

                // Accounts: permitted UPDATE columns
                "UPDATE public.accounts "
                        + "SET balance = balance, status = status WHERE false",

                // Clients: permitted UPDATE columns
                "UPDATE public.clients "
                        + "SET name = name, email = email, phone_number = phone_number "
                        + "WHERE false",

                // Addresses: permitted UPDATE columns
                "UPDATE public.addresses SET "
                        + "city = city, "
                        + "complement = complement, "
                        + "neighborhood = neighborhood, "
                        + "postal_code = postal_code, "
                        + "state = state, "
                        + "street_name = street_name, "
                        + "street_number = street_number "
                        + "WHERE false",

                // Savings accounts: permitted UPDATE columns
                "UPDATE public.savings_accounts "
                        + "SET last_yield_date = last_yield_date, "
                        + "last_yield_processed_date = last_yield_processed_date "
                        + "WHERE false",

                // Users: permitted UPDATE columns
                "UPDATE public.users "
                        + "SET password = password, "
                        + "active = active, "
                        + "token_version = token_version, "
                        + "updated_at = updated_at, "
                        + "role = role, "
                        + "full_name = full_name "
                        + "WHERE false",

                // Employee ID generation: function execution is permitted.
                "SELECT public.next_employee_id()",

                // Audit query snapshots: permitted DELETE operations
                "DELETE FROM public.audit_event_query_snapshots WHERE false",
                "DELETE FROM public.audit_event_query_snapshot_items WHERE false",

                // Idempotency records
                "UPDATE public.transaction_idempotency_requests "
                        + "SET created_at = created_at WHERE false",
                "DELETE FROM public.transaction_idempotency_requests WHERE false",

                // Sequence access
                "SELECT nextval('public.account_number_seq')"
        );

        for (String sql : allowed) {
            assertDoesNotThrow(
                    () -> executeAsApp(sql),
                    () -> "Expected to be allowed: " + sql
            );
        }
    }

    @Test
    @DisplayName("Runtime role has INSERT privileges on required tables and user columns")
    void shouldHaveRequiredInsertPrivileges() throws SQLException {
        List<String> insertableTables = List.of(
                "public.accounts",
                "public.addresses",
                "public.audit_events",
                "public.audit_event_query_snapshots",
                "public.audit_event_query_snapshot_items",
                "public.checking_accounts",
                "public.clients",
                "public.savings_accounts",
                "public.transactions",
                "public.transaction_idempotency_requests"
        );

        for (String table : insertableTables) {
            assertTrue(
                    appHasTablePrivilege(table, "INSERT"),
                    () -> "Expected INSERT privilege on: " + table
            );
        }

        // Users: INSERT is granted only at the column level.
        List<String> insertableUserColumns = List.of(
                "employee_id",
                "full_name",
                "username",
                "email",
                "password",
                "role",
                "active",
                "token_version",
                "created_at",
                "updated_at"
        );

        for (String column : insertableUserColumns) {
            assertTrue(
                    appHasColumnPrivilege(
                            "public.users",
                            column,
                            "INSERT"
                    ),
                    () -> "Expected INSERT privilege on public.users."
                            + column
            );
        }

        assertFalse(
                appHasTablePrivilege("public.users", "INSERT"),
                "The runtime role must not have table-level INSERT on users."
        );

        assertFalse(
                appHasColumnPrivilege(
                        "public.users",
                        "user_type",
                        "INSERT"
                ),
                "The runtime role must not be allowed to insert user_type."
        );

        // The employee sequence must not be directly accessible.
        for (String privilege : List.of("USAGE", "SELECT", "UPDATE")) {
            assertFalse(
                    appHasSequencePrivilege(
                            "public.employee_id_seq",
                            privilege
                    ),
                    () -> "The runtime role must not have "
                            + privilege + " on employee_id_seq."
            );
        }
    }

    @Test
    @DisplayName("Runtime role is denied everything beyond its policy")
    void shouldDenyOperationsOutsidePolicy() {
        List<String> denied = List.of(
                // Financial and audit history are append-only
                "DELETE FROM public.transactions WHERE false",
                "UPDATE public.transactions SET amount = amount WHERE false",
                "DELETE FROM public.audit_events WHERE false",
                "UPDATE public.audit_events SET outcome = outcome WHERE false",

                // Audit query snapshots: UPDATE and TRUNCATE are not permitted.
                "UPDATE public.audit_event_query_snapshots "
                        + "SET expires_at = expires_at WHERE false",
                "UPDATE public.audit_event_query_snapshot_items "
                        + "SET position = position WHERE false",
                "TRUNCATE TABLE public.audit_event_query_snapshots, "
                        + "public.audit_event_query_snapshot_items",

                // Physical deletion is forbidden
                "DELETE FROM public.accounts WHERE false",
                "DELETE FROM public.users WHERE false",
                "DELETE FROM public.clients WHERE false",
                "DELETE FROM public.checking_accounts WHERE false",

                // TRUNCATE is a separate privilege from DELETE
                "TRUNCATE TABLE public.transactions",
                "TRUNCATE TABLE public.audit_events",
                "TRUNCATE TABLE public.accounts CASCADE",

                // Physical deletion of the remaining business tables
                "DELETE FROM public.addresses WHERE false",
                "DELETE FROM public.savings_accounts WHERE false",

                // Forbidden UPDATE columns
                "UPDATE public.accounts SET id = id WHERE false",
                "UPDATE public.accounts SET client_id = client_id WHERE false",
                "UPDATE public.accounts SET account_number = account_number WHERE false",
                "UPDATE public.accounts SET account_digit = account_digit WHERE false",
                "UPDATE public.accounts SET account_type = account_type WHERE false",
                "UPDATE public.clients SET cpf = cpf WHERE false",
                "UPDATE public.clients SET address_id = address_id WHERE false",
                "UPDATE public.addresses SET id = id WHERE false",
                "UPDATE public.savings_accounts SET id = id WHERE false",
                "UPDATE public.users SET username = username WHERE false",
                "UPDATE public.users SET email = email WHERE false",
                "UPDATE public.users SET created_at = created_at WHERE false",
                "UPDATE public.users SET employee_id = employee_id WHERE false",
                "UPDATE public.users SET user_type = user_type WHERE false",
                "UPDATE public.checking_accounts SET id = id WHERE false",

                // Explicit user_type insertion is forbidden.
                """
                INSERT INTO public.users (
                    employee_id,
                    full_name,
                    username,
                    email,
                    password,
                    user_type,
                    role,
                    active,
                    token_version,
                    created_at,
                    updated_at
                )
                VALUES (
                    'EMP-999999',
                    'Joe Doe',
                    'privilege-probe',
                    'privilege-probe@example.com',
                    'encoded-password',
                    'HUMAN',
                    'EMPLOYEE',
                    TRUE,
                    0,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """,

                // Direct employee sequence access is forbidden.
                "SELECT nextval('public.employee_id_seq')",

                // Migration metadata and schema modification
                "SELECT 1 FROM public.flyway_schema_history LIMIT 1",
                "CREATE TABLE public.privilege_smoke_probe (id integer)"
        );

        for (String sql : denied) {
            SQLException exception = assertThrows(
                    SQLException.class,
                    () -> executeAsApp(sql),
                    "Expected to be denied: " + sql
            );

            assertEquals(
                    PERMISSION_DENIED,
                    exception.getSQLState(),
                    "Expected permission denied (42501) for: " + sql
            );
        }
    }

    @Test
    @DisplayName("Privileges are reapplied after being tampered with")
    void shouldReapplyPrivilegesOnNextMigrate() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                url, migratorUser, migratorPassword
        );
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "GRANT DELETE ON TABLE public.transactions TO "
                            + appUser
            );
        }

        Flyway.configure()
                .dataSource(url, migratorUser, migratorPassword)
                .locations("classpath:db/migration", "classpath:db/callback")
                .load()
                .migrate();

        SQLException exception = assertThrows(
                SQLException.class,
                () -> executeAsApp("DELETE FROM public.transactions WHERE false")
        );

        assertEquals(PERMISSION_DENIED, exception.getSQLState());
    }

    @Test
    @DisplayName("Objects created later are closed to the runtime role until granted")
    void shouldNotGrantPrivilegesOnObjectsCreatedLater() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                url, migratorUser, migratorPassword
        );
             Statement statement = connection.createStatement()) {

            statement.execute("DROP TABLE IF EXISTS public.privilege_default_probe");
            statement.execute("DROP SEQUENCE IF EXISTS public.privilege_default_probe_seq");
            statement.execute("CREATE TABLE public.privilege_default_probe (id integer)");
            statement.execute("CREATE SEQUENCE public.privilege_default_probe_seq");

            try {
                for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
                    assertFalse(
                            appHasTablePrivilege(
                                    "public.privilege_default_probe",
                                    privilege
                            ),
                            () -> "A new table must not default to " + privilege
                    );
                }

                for (String privilege : List.of("USAGE", "SELECT", "UPDATE")) {
                    assertFalse(
                            appHasSequencePrivilege(
                                    "public.privilege_default_probe_seq",
                                    privilege
                            ),
                            () -> "A new sequence must not default to " + privilege
                    );
                }
            } finally {
                statement.execute("DROP TABLE IF EXISTS public.privilege_default_probe");
                statement.execute("DROP SEQUENCE IF EXISTS public.privilege_default_probe_seq");
            }
        }
    }

    @Test
    @DisplayName("Ledger append-only triggers exist and are enabled")
    void shouldKeepLedgerTriggersEnabled() throws SQLException {
        String sql = """
                SELECT tgname
                FROM pg_trigger
                WHERE tgrelid = 'public.transactions'::regclass
                  AND NOT tgisinternal
                  AND tgenabled = 'O'
                """;

        List<String> enabled = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(
                url, migratorUser, migratorPassword
        );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                enabled.add(resultSet.getString(1));
            }
        }

        assertTrue(
                enabled.containsAll(List.of(
                        "trg_transactions_no_update_delete",
                        "trg_transactions_no_truncate"
                )),
                () -> "Missing or disabled ledger triggers. Enabled: " + enabled
        );
    }

    private static boolean appHasSequencePrivilege(
            String sequence,
            String privilege
    ) throws SQLException {
        String sql = """
                SELECT has_sequence_privilege(current_user, ?, ?)
                """;

        try (Connection connection = DriverManager.getConnection(
                url, appUser, appPassword
        );
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, sequence);
            statement.setString(2, privilege);

            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getBoolean(1);
            }
        }
    }

    private static boolean appHasTablePrivilege(
            String table,
            String privilege
    ) throws SQLException {
        String sql = """
                SELECT has_table_privilege(current_user, ?, ?)
                """;

        try (Connection connection = DriverManager.getConnection(
                url, appUser, appPassword
        );
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, table);
            statement.setString(2, privilege);

            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getBoolean(1);
            }
        }
    }

    private static boolean appHasColumnPrivilege(
            String table,
            String column,
            String privilege
    ) throws SQLException {
        String sql = """
                SELECT has_column_privilege(current_user, ?, ?, ?)
                """;

        try (Connection connection = DriverManager.getConnection(
                url, appUser, appPassword
        );
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, table);
            statement.setString(2, column);
            statement.setString(3, privilege);

            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getBoolean(1);
            }
        }
    }

    private static void executeAsApp(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                url, appUser, appPassword
        );
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Callback timezoneVerificationCallback() {
        return new Callback() {
            @Override
            public boolean supports(Event event, Context context) {
                return event == Event.BEFORE_EACH_MIGRATE;
            }

            @Override
            public boolean canHandleInTransaction(Event event, Context context) {
                return true;
            }

            @Override
            public void handle(Event event, Context context) {
                try (Statement statement = context.getConnection().createStatement();
                     ResultSet resultSet = statement.executeQuery(
                             "SELECT current_setting('TimeZone')"
                     )) {

                    assertTrue(resultSet.next());

                    assertEquals(
                            "America/Sao_Paulo",
                            resultSet.getString(1),
                            "Flyway migration connection must use America/Sao_Paulo"
                    );

                } catch (SQLException exception) {
                    throw new AssertionError(
                            "Could not verify Flyway session timezone",
                            exception
                    );
                }
            }

            @Override
            public String getCallbackName() {
                return "TimezoneVerification";
            }
        };
    }
}
