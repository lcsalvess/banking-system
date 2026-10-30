package com.lcsalvess.bankingsystem.integration.transaction;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("Transaction migration compatibility")
class TransactionMigrationCompatibilityTests {

    private static final String ADMIN_URL =
            System.getenv("MIGRATION_TEST_DB_ADMIN_URL");

    private static final String ADMIN_USERNAME =
            System.getenv("MIGRATION_TEST_DB_ADMIN_USERNAME");

    private static final String ADMIN_PASSWORD =
            System.getenv("MIGRATION_TEST_DB_ADMIN_PASSWORD");

    private static final String SYSTEM_MIGRATION_USERNAME =
            "system_migration";

    private static final String UNEXPECTED_ACTOR_MESSAGE =
            "Unexpected user attribution found in historical YIELD transactions.";

    private static final String INCOMPLETE_TRANSFER_MESSAGE =
            "Historical transfer codes must identify exactly one consistent transfer pair.";

    private static final String INVALID_YIELD_ACCOUNT_MESSAGE =
            "Historical YIELD transactions must belong to a savings account.";

    @Test
    @DisplayName("Should preserve historical data when upgrading from V24 to V25")
    void shouldPreserveHistoricalDataWhenUpgradingToV25()
            throws SQLException {

        assumeDatabaseAdminConfiguration();

        String databaseName = createTemporaryDatabase();

        try {
            String databaseUrl = databaseUrl(databaseName);

            migrateTo(databaseUrl, "24");

            LegacyFixtures fixtures;

            try (Connection connection = connect(databaseUrl)) {
                fixtures = insertLegacyFixtures(
                        connection,
                        true
                );

                assertLegacyState(connection, fixtures);
            }

            migrateTo(databaseUrl, "25");

            try (Connection connection = connect(databaseUrl)) {
                assertUpgradedState(connection, fixtures);
            }

        } finally {
            dropTemporaryDatabase(databaseName);
        }
    }

    @Test
    @DisplayName("Should abort the upgrade when a historical transfer pair is incomplete")
    void shouldAbortUpgradeWhenTransferPairIsIncomplete()
            throws SQLException {

        assumeDatabaseAdminConfiguration();

        String databaseName = createTemporaryDatabase();

        try {
            String databaseUrl = databaseUrl(databaseName);

            migrateTo(databaseUrl, "24");

            LegacyFixtures fixtures;
            UUID incompleteTransferCode = UUID.randomUUID();

            try (Connection connection = connect(databaseUrl)) {
                fixtures = insertLegacyFixtures(
                        connection,
                        true
                );

                // A transfer code with only one transaction direction
                // must make the V25 migration fail.
                insertHistoricalTransaction(
                        connection,
                        "TRANSFER_SENT",
                        new BigDecimal("50.00"),
                        fixtures.checkingAccountId(),
                        new BigDecimal("350.00"),
                        incompleteTransferCode,
                        null,
                        fixtures.humanUserId()
                );
            }

            FlywayException exception = assertThrows(
                    FlywayException.class,
                    () -> migrateTo(databaseUrl, "25")
            );

            assertMigrationFailure(
                    exception,
                    INCOMPLETE_TRANSFER_MESSAGE
            );

            try (Connection connection = connect(databaseUrl)) {
                assertUnchangedAfterFailedUpgrade(
                        connection,
                        fixtures
                );

                assertEquals(
                        1L,
                        queryLong(
                                connection,
                                """
                                SELECT COUNT(*)
                                FROM public.transactions
                                WHERE transfer_code = ?
                                """,
                                incompleteTransferCode
                        ),
                        "The historical incomplete transfer must remain untouched."
                );
            }

        } finally {
            dropTemporaryDatabase(databaseName);
        }
    }

    @Test
    @DisplayName("Should abort the upgrade when a historical yield belongs to a checking account")
    void shouldAbortUpgradeWhenYieldBelongsToCheckingAccount()
            throws SQLException {

        assumeDatabaseAdminConfiguration();

        String databaseName = createTemporaryDatabase();

        try {
            String databaseUrl = databaseUrl(databaseName);

            migrateTo(databaseUrl, "24");

            LegacyFixtures fixtures;

            try (Connection connection = connect(databaseUrl)) {
                fixtures = insertLegacyFixtures(
                        connection,
                        true
                );

                // This record has the expected legacy system_migration
                // actor, but its account is not a savings account.
                insertHistoricalTransaction(
                        connection,
                        "YIELD",
                        new BigDecimal("5.00"),
                        fixtures.checkingAccountId(),
                        new BigDecimal("355.00"),
                        null,
                        LocalDate.now().minusDays(2),
                        fixtures.systemMigrationUserId()
                );
            }

            FlywayException exception = assertThrows(
                    FlywayException.class,
                    () -> migrateTo(databaseUrl, "25")
            );

            assertMigrationFailure(
                    exception,
                    INVALID_YIELD_ACCOUNT_MESSAGE
            );

            try (Connection connection = connect(databaseUrl)) {
                assertUnchangedAfterFailedUpgrade(
                        connection,
                        fixtures
                );

                // The invalid checking-account yield must also remain
                // unchanged because the migration is transactional.
                assertEquals(
                        1L,
                        queryLong(
                                connection,
                                """
                                SELECT COUNT(*)
                                FROM public.transactions
                                WHERE transaction_type = 'YIELD'
                                  AND account_id = ?
                                  AND performed_by_system = FALSE
                                  AND performed_by_user_id = ?
                                """,
                                fixtures.checkingAccountId(),
                                fixtures.systemMigrationUserId()
                        )
                );
            }

        } finally {
            dropTemporaryDatabase(databaseName);
        }
    }

    @Test
    @DisplayName("Should abort the upgrade when a historical yield has an unexpected human actor")
    void shouldAbortUpgradeWhenYieldHasUnexpectedHumanActor()
            throws SQLException {

        assumeDatabaseAdminConfiguration();

        String databaseName = createTemporaryDatabase();

        try {
            String databaseUrl = databaseUrl(databaseName);

            migrateTo(databaseUrl, "24");

            LegacyFixtures fixtures;

            try (Connection connection = connect(databaseUrl)) {
                fixtures = insertLegacyFixtures(
                        connection,
                        false
                );
            }

            FlywayException exception = assertThrows(
                    FlywayException.class,
                    () -> migrateTo(databaseUrl, "25")
            );

            assertMigrationFailure(
                    exception,
                    UNEXPECTED_ACTOR_MESSAGE
            );

            try (Connection connection = connect(databaseUrl)) {
                assertUnchangedAfterFailedUpgrade(
                        connection,
                        fixtures
                );
            }

        } finally {
            dropTemporaryDatabase(databaseName);
        }
    }

    // ============================================================
    // Database configuration
    // ============================================================

    private static void assumeDatabaseAdminConfiguration() {
        assumeTrue(
                ADMIN_URL != null
                        && ADMIN_USERNAME != null
                        && ADMIN_PASSWORD != null,
                "Migration compatibility tests require "
                        + "MIGRATION_TEST_DB_ADMIN_* environment variables."
        );
    }

    private static Connection connect(String databaseUrl)
            throws SQLException {

        return DriverManager.getConnection(
                databaseUrl,
                ADMIN_USERNAME,
                ADMIN_PASSWORD
        );
    }

    private static String createTemporaryDatabase()
            throws SQLException {

        String databaseName = "banking_upgrade_"
                + UUID.randomUUID().toString().replace("-", "");

        try (Connection connection = connect(ADMIN_URL);
             Statement statement = connection.createStatement()) {

            statement.execute(
                    "CREATE DATABASE \"" + databaseName + "\""
            );
        }

        return databaseName;
    }

    private static void dropTemporaryDatabase(String databaseName)
            throws SQLException {

        try (Connection connection = connect(ADMIN_URL);
             Statement statement = connection.createStatement()) {

            statement.execute(
                    "DROP DATABASE IF EXISTS \""
                            + databaseName
                            + "\" WITH (FORCE)"
            );
        }
    }

    private static String databaseUrl(String databaseName) {
        int queryIndex = ADMIN_URL.indexOf('?');

        String baseUrl = queryIndex >= 0
                ? ADMIN_URL.substring(0, queryIndex)
                : ADMIN_URL;

        String query = queryIndex >= 0
                ? ADMIN_URL.substring(queryIndex)
                : "";

        int lastSlash = baseUrl.lastIndexOf('/');

        if (lastSlash < "jdbc:postgresql://".length()) {
            throw new IllegalStateException(
                    "MIGRATION_TEST_DB_ADMIN_URL must include a database name."
            );
        }

        return baseUrl.substring(0, lastSlash + 1)
                + databaseName
                + query;
    }

    // ============================================================
    // Flyway
    // ============================================================

    private static void migrateTo(
            String databaseUrl,
            String targetVersion
    ) {
        Flyway.configure()
                .dataSource(
                        databaseUrl,
                        ADMIN_USERNAME,
                        ADMIN_PASSWORD
                )
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(targetVersion))
                .load()
                .migrate();
    }

    // ============================================================
    // Historical fixtures
    // ============================================================

    /**
     * Creates a V24 database state containing:
     *
     * - a human-attributed deposit;
     * - a legacy transfer pair without transfer_code;
     * - a complete transfer pair with transfer_code;
     * - a historical yield attributed to a user.
     *
     * When yieldAttributedToMigrationUser is false, the historical yield
     * is attributed to a real user, which must make V25 abort.
     */
    private static LegacyFixtures insertLegacyFixtures(
            Connection connection,
            boolean yieldAttributedToMigrationUser
    ) throws SQLException {

        Long humanUserId = createUser(connection);

        long systemMigrationUserId = queryLong(
                connection,
                """
                SELECT id
                FROM public.users
                WHERE username = ?
                """,
                SYSTEM_MIGRATION_USERNAME
        );

        Long clientId = createClient(connection);

        Long checkingAccountId = createAccount(
                connection,
                clientId,
                "91001",
                "CHECKING",
                new BigDecimal("400.00")
        );

        Long savingsAccountId = createAccount(
                connection,
                clientId,
                "91002",
                "SAVINGS",
                new BigDecimal("101.00")
        );

        // Historical deposit.
        insertHistoricalTransaction(
                connection,
                "DEPOSIT",
                new BigDecimal("500.00"),
                checkingAccountId,
                new BigDecimal("500.00"),
                null,
                null,
                humanUserId
        );

        // Historical transfer pair without a transfer code.
        // V3 did not backfill transfer_code for these records.
        insertHistoricalTransaction(
                connection,
                "TRANSFER_SENT",
                new BigDecimal("100.00"),
                checkingAccountId,
                new BigDecimal("400.00"),
                null,
                null,
                humanUserId
        );

        insertHistoricalTransaction(
                connection,
                "TRANSFER_RECEIVED",
                new BigDecimal("100.00"),
                savingsAccountId,
                new BigDecimal("100.00"),
                null,
                null,
                humanUserId
        );

        // A complete historical pair with a transfer code.
        // V25 must preserve this valid pair.
        UUID codedTransferCode = UUID.randomUUID();

        insertHistoricalTransaction(
                connection,
                "TRANSFER_SENT",
                new BigDecimal("25.00"),
                checkingAccountId,
                new BigDecimal("375.00"),
                codedTransferCode,
                null,
                humanUserId
        );

        insertHistoricalTransaction(
                connection,
                "TRANSFER_RECEIVED",
                new BigDecimal("25.00"),
                savingsAccountId,
                new BigDecimal("125.00"),
                codedTransferCode,
                null,
                humanUserId
        );

        Long yieldActorId = yieldAttributedToMigrationUser
                ? systemMigrationUserId
                : humanUserId;

        // Historical yield. V13 allowed a YIELD to have a human actor.
        insertHistoricalTransaction(
                connection,
                "YIELD",
                new BigDecimal("1.00"),
                savingsAccountId,
                new BigDecimal("101.00"),
                null,
                LocalDate.now().minusDays(1),
                yieldActorId
        );

        return new LegacyFixtures(
                humanUserId,
                systemMigrationUserId,
                checkingAccountId,
                savingsAccountId,
                yieldActorId,
                codedTransferCode
        );
    }

    private static Long createUser(Connection connection)
            throws SQLException {

        return insertReturningId(
                connection,
                """
                INSERT INTO public.users (
                    username,
                    email,
                    password,
                    role,
                    active,
                    created_at,
                    updated_at
                )
                VALUES (
                    'upgrade_actor',
                    ?,
                    'not-a-real-hash',
                    'EMPLOYEE',
                    TRUE,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                RETURNING id
                """,
                "upgrade-actor-" + UUID.randomUUID() + "@example.invalid"
        );
    }

    private static Long createClient(Connection connection)
            throws SQLException {

        Long addressId = insertReturningId(
                connection,
                """
                INSERT INTO public.addresses (
                    city,
                    complement,
                    neighborhood,
                    postal_code,
                    state,
                    street_name,
                    street_number
                )
                VALUES (
                    'Mogi das Cruzes',
                    NULL,
                    'Centro',
                    '08700000',
                    'SP',
                    'Rua de Teste',
                    '100'
                )
                RETURNING id
                """
        );

        return insertReturningId(
                connection,
                """
                INSERT INTO public.clients (
                    cpf,
                    email,
                    name,
                    phone_number,
                    address_id
                )
                VALUES (
                    '12345678901',
                    'upgrade-client@example.invalid',
                    'Upgrade Test Client',
                    '11999990000',
                    ?
                )
                RETURNING id
                """,
                addressId
        );
    }

    private static Long createAccount(
            Connection connection,
            Long clientId,
            String accountNumber,
            String accountType,
            BigDecimal balance
    ) throws SQLException {

        Long accountId = insertReturningId(
                connection,
                """
                INSERT INTO public.accounts (
                    account_number,
                    balance,
                    status,
                    account_type,
                    client_id,
                    account_digit
                )
                VALUES (?, ?, 'ACTIVE', ?, ?, '0')
                RETURNING id
                """,
                accountNumber,
                balance,
                accountType,
                clientId
        );

        if ("CHECKING".equals(accountType)) {
            executeUpdate(
                    connection,
                    """
                    INSERT INTO public.checking_accounts (id)
                    VALUES (?)
                    """,
                    accountId
            );
        } else if ("SAVINGS".equals(accountType)) {
            executeUpdate(
                    connection,
                    """
                    INSERT INTO public.savings_accounts (
                        id,
                        last_yield_date,
                        last_yield_processed_date
                    )
                    VALUES (
                        ?,
                        CURRENT_DATE - 2,
                        CURRENT_DATE - 2
                    )
                    """,
                    accountId
            );
        } else {
            throw new IllegalArgumentException(
                    "Unsupported account type: " + accountType
            );
        }

        return accountId;
    }

    private static void insertHistoricalTransaction(
            Connection connection,
            String type,
            BigDecimal amount,
            Long accountId,
            BigDecimal balanceAfter,
            UUID transferCode,
            LocalDate yieldDate,
            Long actorId
    ) throws SQLException {

        String sql = """
                INSERT INTO public.transactions (
                    amount,
                    created_at,
                    transaction_type,
                    account_id,
                    transaction_code,
                    transfer_code,
                    balance_after,
                    yield_date,
                    performed_by_user_id,
                    performed_by_system
                )
                VALUES (
                    ?,
                    CURRENT_TIMESTAMP,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    FALSE
                )
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setBigDecimal(1, amount);
            statement.setString(2, type);
            statement.setLong(3, accountId);
            statement.setObject(4, UUID.randomUUID());

            if (transferCode == null) {
                statement.setNull(5, Types.OTHER);
            } else {
                statement.setObject(5, transferCode);
            }

            statement.setBigDecimal(6, balanceAfter);

            if (yieldDate == null) {
                statement.setNull(7, Types.DATE);
            } else {
                statement.setObject(7, yieldDate);
            }

            statement.setLong(8, actorId);

            statement.executeUpdate();
        }
    }

    // ============================================================
    // Assertions: V24 state
    // ============================================================

    private static void assertLegacyState(
            Connection connection,
            LegacyFixtures fixtures
    ) throws SQLException {

        // Two historical transfer transactions must still have no code.
        assertEquals(
                2L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM public.transactions
                        WHERE transaction_type IN (
                            'TRANSFER_SENT',
                            'TRANSFER_RECEIVED'
                        )
                          AND transfer_code IS NULL
                        """
                )
        );

        // The complete coded transfer pair must exist.
        assertEquals(
                2L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM public.transactions
                        WHERE transfer_code = ?
                        """,
                        fixtures.codedTransferCode()
                )
        );

        // The legacy yield must still have its original attribution.
        assertEquals(
                1L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM public.transactions
                        WHERE transaction_type = 'YIELD'
                          AND performed_by_system = FALSE
                          AND performed_by_user_id = ?
                        """,
                        fixtures.yieldActorId()
                )
        );
    }

    // ============================================================
    // Assertions: successful V25 upgrade
    // ============================================================

    private static void assertUpgradedState(
            Connection connection,
            LegacyFixtures fixtures
    ) throws SQLException {

        // Both historical transfer legs without a code must remain intact.
        assertEquals(
                2L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM public.transactions
                        WHERE transaction_type IN (
                            'TRANSFER_SENT',
                            'TRANSFER_RECEIVED'
                        )
                          AND transfer_code IS NULL
                        """
                )
        );

        // The historical coded transfer pair must remain intact.
        assertEquals(
                2L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM public.transactions
                        WHERE transfer_code = ?
                        """,
                        fixtures.codedTransferCode()
                )
        );

        // The historical yield must now be attributed to the system.
        assertEquals(
                1L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM public.transactions
                        WHERE transaction_type = 'YIELD'
                          AND performed_by_system = TRUE
                          AND performed_by_user_id IS NULL
                        """
                )
        );

        // The ordinary deposit must retain its original human actor.
        assertEquals(
                1L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM public.transactions
                        WHERE transaction_type = 'DEPOSIT'
                          AND performed_by_system = FALSE
                          AND performed_by_user_id = ?
                        """,
                        fixtures.humanUserId()
                )
        );

        // The transfer code constraint must remain NOT VALID.
        assertEquals(
                Boolean.FALSE,
                queryBoolean(
                        connection,
                        """
                        SELECT convalidated
                        FROM pg_constraint
                        WHERE conname = 'ck_transactions_transfer_code'
                        """
                )
        );

        // The strict actor constraint must be validated.
        assertEquals(
                Boolean.TRUE,
                queryBoolean(
                        connection,
                        """
                        SELECT convalidated
                        FROM pg_constraint
                        WHERE conname = 'ck_transactions_actor'
                        """
                )
        );

        // The unique index must exist.
        assertEquals(
                1L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname = 'uk_transactions_transfer_code_type'
                        """
                )
        );

        // The new integrity triggers must be installed.
        assertEquals(
                4L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM pg_trigger
                        WHERE NOT tgisinternal
                          AND tgname IN (
                              'ct_transactions_transfer_pair',
                              'tr_transactions_yield_savings_account',
                              'tr_savings_accounts_protect_yield_transactions',
                              'tr_accounts_protect_yield_transactions'
                          )
                        """
                )
        );

        // Flyway must record V25 as successfully applied.
        assertEquals(
                1L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM public.flyway_schema_history
                        WHERE version = '25'
                          AND success = TRUE
                        """
                )
        );
    }

    // ============================================================
    // Assertions: failed V25 upgrade
    // ============================================================

    private static void assertUnchangedAfterFailedUpgrade(
            Connection connection,
            LegacyFixtures fixtures
    ) throws SQLException {

        // The historical yield must retain its original attribution.
        assertEquals(
                1L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM public.transactions
                        WHERE transaction_type = 'YIELD'
                          AND performed_by_system = FALSE
                          AND performed_by_user_id = ?
                        """,
                        fixtures.yieldActorId()
                )
        );

        // The historical transfer records without a code must remain intact.
        assertEquals(
                2L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM public.transactions
                        WHERE transaction_type IN (
                            'TRANSFER_SENT',
                            'TRANSFER_RECEIVED'
                        )
                          AND transfer_code IS NULL
                        """
                )
        );

        // The complete coded transfer pair must remain intact.
        assertEquals(
                2L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM public.transactions
                        WHERE transfer_code = ?
                        """,
                        fixtures.codedTransferCode()
                )
        );

        // The transactional migration must not leave partial constraints.
        assertEquals(
                0L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM pg_constraint
                        WHERE conname IN (
                            'ck_transactions_amount_max',
                            'ck_transactions_transfer_code'
                        )
                        """
                )
        );

        // The unique index must not remain after a failed migration.
        assertEquals(
                0L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname = 'uk_transactions_transfer_code_type'
                        """
                )
        );

        // The new triggers must not remain after a failed migration.
        assertEquals(
                0L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM pg_trigger
                        WHERE NOT tgisinternal
                          AND tgname IN (
                              'ct_transactions_transfer_pair',
                              'tr_transactions_yield_savings_account',
                              'tr_savings_accounts_protect_yield_transactions',
                              'tr_accounts_protect_yield_transactions'
                          )
                        """
                )
        );

        // V25 must not be recorded as successfully applied.
        assertEquals(
                0L,
                queryLong(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM public.flyway_schema_history
                        WHERE version = '25'
                          AND success = TRUE
                        """
                )
        );
    }

    private static void assertMigrationFailure(
            FlywayException exception,
            String expectedMessage
    ) {
        String messages = exceptionMessages(exception);

        assertTrue(
                messages.contains(expectedMessage),
                () -> "The migration failed for an unexpected reason. "
                        + "Expected message: "
                        + expectedMessage
                        + System.lineSeparator()
                        + "Actual messages:"
                        + System.lineSeparator()
                        + messages
        );
    }

    // ============================================================
    // JDBC helpers
    // ============================================================

    private static Long insertReturningId(
            Connection connection,
            String sql,
            Object... parameters
    ) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            setParameters(statement, parameters);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(
                            "The INSERT statement did not return an ID."
                    );
                }

                return resultSet.getLong(1);
            }
        }
    }

    private static void executeUpdate(
            Connection connection,
            String sql,
            Object... parameters
    ) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            setParameters(statement, parameters);
            statement.executeUpdate();
        }
    }

    private static long queryLong(
            Connection connection,
            String sql,
            Object... parameters
    ) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            setParameters(statement, parameters);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(
                            "The query did not return a result."
                    );
                }

                return resultSet.getLong(1);
            }
        }
    }

    private static Boolean queryBoolean(
            Connection connection,
            String sql,
            Object... parameters
    ) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            setParameters(statement, parameters);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(
                            "The query did not return a result."
                    );
                }

                return resultSet.getObject(1, Boolean.class);
            }
        }
    }

    private static void setParameters(
            PreparedStatement statement,
            Object... parameters
    ) throws SQLException {

        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
    }

    private static String exceptionMessages(Throwable exception) {
        StringBuilder messages = new StringBuilder();

        for (Throwable cause = exception;
             cause != null;
             cause = cause.getCause()) {

            messages.append(cause.getMessage())
                    .append(System.lineSeparator());
        }

        return messages.toString();
    }

    private record LegacyFixtures(
            Long humanUserId,
            Long systemMigrationUserId,
            Long checkingAccountId,
            Long savingsAccountId,
            Long yieldActorId,
            UUID codedTransferCode
    ) {
    }
}
