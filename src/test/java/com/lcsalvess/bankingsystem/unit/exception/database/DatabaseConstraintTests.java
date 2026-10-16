package com.lcsalvess.bankingsystem.unit.exception.database;

import com.lcsalvess.bankingsystem.exception.database.DatabaseConstraint;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConstraintTests {

    private static final String UNKNOWN_CONSTRAINT = "uk_unknown_constraint";

    @Nested
    @DisplayName("Constraint declarations")
    class DeclarationTests {

        @ParameterizedTest(name = "{0}")
        @EnumSource(DatabaseConstraint.class)
        @DisplayName("Should follow the uk_ naming convention")
        void shouldFollowNamingConvention(DatabaseConstraint constraint) {
            assertTrue(
                    constraint.getConstraintName().matches("^(uk|ck)_[a-z0-9_]+$"),
                    constraint.getConstraintName()
            );
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(DatabaseConstraint.class)
        @DisplayName("Should be created by some Flyway migration")
        void shouldBeDeclaredInMigrations(DatabaseConstraint constraint) throws IOException {
            String migrations = activeDeclarations(readMigrations());

            Pattern declaration = Pattern.compile(
                    "(?i)\\b(CONSTRAINT|INDEX)\\s+(IF\\s+NOT\\s+EXISTS\\s+)?"
                            + Pattern.quote(constraint.getConstraintName())
                            + "\\b"
            );

            assertTrue(
                    declaration.matcher(migrations).find(),
                    "Constraint not created in any migration: " + constraint.getConstraintName()
            );
        }

        @Test
        @DisplayName("Should not declare duplicated names")
        void shouldNotDeclareDuplicatedNames() {
            Set<String> names = Arrays.stream(DatabaseConstraint.values())
                    .map(DatabaseConstraint::getConstraintName)
                    .collect(Collectors.toSet());

            assertEquals(DatabaseConstraint.values().length, names.size());
        }

        @Test
        @DisplayName("Should expose the exact database name")
        void shouldExposeTheExactName() {
            assertEquals("uk_users_username", DatabaseConstraint.USER_USERNAME_UNIQUE.getConstraintName());
            assertEquals("uk_users_email", DatabaseConstraint.USER_EMAIL_UNIQUE.getConstraintName());
            assertEquals("ck_users_system_migration_inactive", DatabaseConstraint.SYSTEM_MIGRATION_USER_INACTIVE.getConstraintName());
            assertEquals("uk_clients_cpf", DatabaseConstraint.CLIENT_CPF_UNIQUE.getConstraintName());
            assertEquals("uk_clients_email", DatabaseConstraint.CLIENT_EMAIL_UNIQUE.getConstraintName());
            assertEquals(
                    "uk_accounts_client_type_active",
                    DatabaseConstraint.ACCOUNT_CLIENT_TYPE_ACTIVE_UNIQUE.getConstraintName()
            );
            assertEquals(
                    "ck_accounts_balance_max",
                    DatabaseConstraint.ACCOUNT_BALANCE_MAX.getConstraintName()
            );
            assertEquals(
                    "uk_transactions_daily_yield",
                    DatabaseConstraint.TRANSACTION_DAILY_YIELD_UNIQUE.getConstraintName()
            );
        }
    }

    @Nested
    @DisplayName("Violation detection")
    class DetectionTests {

        @ParameterizedTest(name = "{0}")
        @EnumSource(DatabaseConstraint.class)
        @DisplayName("Should detect its own violation wrapped by Spring")
        void shouldDetectOwnViolation(DatabaseConstraint constraint) {
            DataIntegrityViolationException exception =
                    integrityViolation(constraint.getConstraintName());

            assertTrue(constraint.isViolatedBy(exception));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(DatabaseConstraint.class)
        @DisplayName("Should not detect the violation of any other constraint")
        void shouldNotDetectOtherConstraints(DatabaseConstraint constraint) {
            Arrays.stream(DatabaseConstraint.values())
                    .filter(other -> other != constraint)
                    .forEach(other -> assertFalse(
                            constraint.isViolatedBy(integrityViolation(other.getConstraintName())),
                            constraint + " detected " + other
                    ));
        }

        @Test
        @DisplayName("Should detect the Hibernate exception passed directly")
        void shouldDetectHibernateExceptionDirectly() {
            ConstraintViolationException exception =
                    hibernateViolation(DatabaseConstraint.USER_EMAIL_UNIQUE.getConstraintName());

            assertTrue(DatabaseConstraint.USER_EMAIL_UNIQUE.isViolatedBy(exception));
        }

        @Test
        @DisplayName("Should detect the violation deep in the cause chain")
        void shouldDetectViolationDeepInTheCauseChain() {
            RuntimeException exception = new RuntimeException(
                    "outer",
                    new IllegalStateException(
                            "middle",
                            integrityViolation(DatabaseConstraint.CLIENT_CPF_UNIQUE.getConstraintName())
                    )
            );

            assertTrue(DatabaseConstraint.CLIENT_CPF_UNIQUE.isViolatedBy(exception));
        }

        @Test
        @DisplayName("Should not detect an unknown constraint name")
        void shouldNotDetectUnknownName() {
            DataIntegrityViolationException exception = integrityViolation(UNKNOWN_CONSTRAINT);

            for (DatabaseConstraint constraint : DatabaseConstraint.values()) {
                assertFalse(constraint.isViolatedBy(exception), constraint.name());
            }
        }

        @Test
        @DisplayName("Should not detect a violation without constraint name")
        void shouldNotDetectViolationWithoutName() {
            DataIntegrityViolationException exception = integrityViolation(null);

            for (DatabaseConstraint constraint : DatabaseConstraint.values()) {
                assertFalse(constraint.isViolatedBy(exception), constraint.name());
            }
        }

        @Test
        @DisplayName("Should compare names ignoring nothing (case-sensitive)")
        void shouldBeCaseSensitive() {
            DataIntegrityViolationException exception = integrityViolation(
                    DatabaseConstraint.USER_USERNAME_UNIQUE.getConstraintName().toUpperCase()
            );

            assertFalse(DatabaseConstraint.USER_USERNAME_UNIQUE.isViolatedBy(exception));
        }

        @Test
        @DisplayName("Should not detect when there is no Hibernate exception in the chain")
        void shouldNotDetectWithoutHibernateException() {
            RuntimeException exception = new RuntimeException(
                    "outer",
                    new SQLException("duplicate key value violates unique constraint")
            );

            for (DatabaseConstraint constraint : DatabaseConstraint.values()) {
                assertFalse(constraint.isViolatedBy(exception), constraint.name());
            }
        }

        @Test
        @DisplayName("Should not detect when the exception has no cause")
        void shouldNotDetectWithoutCause() {
            for (DatabaseConstraint constraint : DatabaseConstraint.values()) {
                assertFalse(constraint.isViolatedBy(new DataIntegrityViolationException("boom")));
            }
        }

        @Test
        @DisplayName("Should return false for a null throwable")
        void shouldReturnFalseForNullThrowable() {
            for (DatabaseConstraint constraint : DatabaseConstraint.values()) {
                assertFalse(constraint.isViolatedBy(null), constraint.name());
            }
        }

        @Test
        @DisplayName("Should decide by the first Hibernate exception found (documented behavior)")
        void shouldDecideByTheFirstHibernateException() throws Exception {
            String matching = DatabaseConstraint.USER_EMAIL_UNIQUE.getConstraintName();
            String other = DatabaseConstraint.CLIENT_EMAIL_UNIQUE.getConstraintName();

            // outer violation = other, inner violation = matching -> the outer one wins
            SQLException rootWithMatching = new SQLException("root", hibernateViolation(matching));
            ConstraintViolationException outerOther =
                    new ConstraintViolationException("outer", rootWithMatching, other);

            assertFalse(DatabaseConstraint.USER_EMAIL_UNIQUE.isViolatedBy(outerOther));

            // outer violation = matching, inner violation = other -> the outer one wins
            SQLException rootWithOther = new SQLException("root", hibernateViolation(other));
            ConstraintViolationException outerMatching =
                    new ConstraintViolationException("outer", rootWithOther, matching);

            assertTrue(DatabaseConstraint.USER_EMAIL_UNIQUE.isViolatedBy(outerMatching));
        }
    }

    // ---------- helpers ----------

    private static DataIntegrityViolationException integrityViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                hibernateViolation(constraintName)
        );
    }

    private static ConstraintViolationException hibernateViolation(String constraintName) {
        return new ConstraintViolationException(
                "duplicate key value violates unique constraint",
                new SQLException("duplicate key"),
                constraintName
        );
    }

    private static String readMigrations() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/*.sql");

        assertTrue(resources.length > 0, "No Flyway migration found in the classpath");

        StringBuilder migrations = new StringBuilder();

        for (Resource resource : resources) {
            migrations.append(resource.getContentAsString(StandardCharsets.UTF_8)).append('\n');
        }

        return migrations.toString();
    }

    /**
     * Removes SQL comments and DROP statements, so a name that was only dropped
     * (and never recreated) is not considered declared.
     */
    private static String activeDeclarations(String sql) {
        return sql
                .replaceAll("(?m)--.*$", "")
                .replaceAll("(?i)DROP\\s+(CONSTRAINT|INDEX)\\s+(IF\\s+EXISTS\\s+)?[a-z0-9_]+", "");
    }
}