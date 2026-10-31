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
 * Prova, contra um PostgreSQL real, que o schema migrado como
 * {@code banking_migrator} (com o afterMigrate de privilégios) entrega ao
 * {@code banking_app} exatamente o que o runtime precisa e nada além.
 *
 * <p>Os testes comuns rodam com um usuário dono de tudo, então um grant
 * faltando (como o UPDATE em checking_accounts) passaria despercebido no CI.
 *
 * <p>Também confirma que os triggers append-only do ledger (V27) existem e
 * estão habilitados, porque privilégios não protegem o dono do schema.
 *
 * <p>Não usa o contexto do Spring. É ignorado quando as variáveis abaixo não
 * estão definidas; o job {@code least-privilege} do CI as define.
 * Requer um banco vazio com as roles criadas por
 * {@code scripts/db/bootstrap-roles.sql}.
 *
 * <p>Deve rodar antes de {@code RuntimePrivilegesEndToEndTests}, no mesmo
 * banco: ela verifica a migração a partir do zero (inclusive o timezone da
 * sessão do Flyway), o que deixa de valer em um banco já migrado. O job do CI
 * executa as duas classes em passos separados, nessa ordem.
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
                        + "updated_at = updated_at "
                        + "WHERE false",

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
    @DisplayName("Runtime role has INSERT privileges on required tables")
    void shouldHaveRequiredInsertPrivileges() throws SQLException {
        List<String> insertableTables = List.of(
                "public.accounts",
                "public.addresses",
                "public.audit_events",
                "public.checking_accounts",
                "public.clients",
                "public.savings_accounts",
                "public.transactions",
                "public.users",
                "public.transaction_idempotency_requests"
        );

        for (String table : insertableTables) {
            assertTrue(
                    appHasTablePrivilege(table, "INSERT"),
                    () -> "Expected INSERT privilege on: " + table
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
                "UPDATE public.users SET role = role WHERE false",
                "UPDATE public.users SET username = username WHERE false",
                "UPDATE public.users SET email = email WHERE false",
                "UPDATE public.users SET created_at = created_at WHERE false",
                "UPDATE public.checking_accounts SET id = id WHERE false",

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
