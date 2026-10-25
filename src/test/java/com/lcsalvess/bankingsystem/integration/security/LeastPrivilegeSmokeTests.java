package com.lcsalvess.bankingsystem.integration.security;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova, contra um PostgreSQL real, que o schema migrado como
 * {@code banking_migrator} (com o afterMigrate de privilégios) entrega ao
 * {@code banking_app} exatamente o que o runtime precisa e nada além.
 *
 * <p>Os testes comuns rodam com um usuário dono de tudo, então um grant
 * faltando (como o UPDATE em checking_accounts) passaria despercebido no CI.
 *
 * <p>Não usa o contexto do Spring. É ignorado quando as variáveis abaixo não
 * estão definidas; o job {@code least-privilege} do CI as define.
 * Requer um banco vazio com as roles criadas por
 * {@code scripts/db/bootstrap-roles.sql}.
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
                .load()
                .migrate();
    }

    @Test
    @DisplayName("Runtime role can lock joined account tables with FOR UPDATE")
    void shouldLockJoinedAccountTables() {
        assertDoesNotThrow(() -> executeAsApp(
                "SELECT a.id FROM checking_accounts c "
                        + "JOIN accounts a ON a.id = c.id FOR UPDATE"
        ));
        assertDoesNotThrow(() -> executeAsApp(
                "SELECT a.id FROM savings_accounts s "
                        + "JOIN accounts a ON a.id = s.id FOR UPDATE"
        ));
    }

    @Test
    @DisplayName("Runtime role can do the work it needs")
    void shouldAllowRuntimeOperations() {
        List<String> allowed = List.of(
                "SELECT 1 FROM users LIMIT 1",
                "UPDATE users SET id = id WHERE false",
                "UPDATE accounts SET id = id WHERE false",
                "UPDATE savings_accounts SET id = id WHERE false",
                "SELECT 1 FROM transactions LIMIT 1",
                "SELECT 1 FROM audit_events LIMIT 1",
                "UPDATE transaction_idempotency_requests "
                        + "SET created_at = created_at WHERE false",
                "DELETE FROM transaction_idempotency_requests WHERE false",
                "SELECT nextval('account_number_seq')"
        );

        for (String sql : allowed) {
            assertDoesNotThrow(
                    () -> executeAsApp(sql),
                    () -> "Expected to be allowed: " + sql
            );
        }
    }

    @Test
    @DisplayName("Runtime role is denied everything beyond its policy")
    void shouldDenyOperationsOutsidePolicy() {
        List<String> denied = List.of(
                "DELETE FROM transactions WHERE false",
                "UPDATE transactions SET amount = amount WHERE false",
                "DELETE FROM audit_events WHERE false",
                "UPDATE audit_events SET outcome = outcome WHERE false",
                "DELETE FROM accounts WHERE false",
                "DELETE FROM users WHERE false",
                "DELETE FROM clients WHERE false",
                "DELETE FROM checking_accounts WHERE false",
                "SELECT 1 FROM flyway_schema_history LIMIT 1",
                "CREATE TABLE privilege_smoke_probe (id integer)"
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
                url, migratorUser, migratorPassword);
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
                () -> executeAsApp("DELETE FROM transactions WHERE false")
        );

        assertEquals(PERMISSION_DENIED, exception.getSQLState());
    }

    private static void executeAsApp(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                url, appUser, appPassword);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
