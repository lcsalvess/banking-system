package com.lcsalvess.bankingsystem.integration.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inserts users directly into PostgreSQL, bypassing Bean Validation, to
 * prove that the reserved username rule is also enforced by the database.
 * Each test runs in a transaction that is rolled back, so no user is persisted.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ReservedUsernameConstraintTests {

    private static final String CONSTRAINT = "ck_users_username_not_system";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"system", "SYSTEM", "System", " system "})
    @DisplayName("Database should reject the reserved username regardless of case and spaces")
    void shouldRejectReservedUsername(String username) {
        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> insertUser(username)
        );

        assertTrue(
                exception.getMessage().contains(CONSTRAINT),
                "The insert failed for another reason: " + exception.getMessage()
        );
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"systems", "system1", "system-admin", "my.system", "admin"})
    @DisplayName("Database should accept usernames that only contain the reserved name")
    void shouldAcceptUsernamesThatOnlyContainTheReservedName(String username) {
        assertDoesNotThrow(() -> insertUser(username));
    }

    private void insertUser(String username) {
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    employee_id,
                    full_name,
                    username,
                    email,
                    password,
                    role,
                    active,
                    created_at,
                    updated_at
                )
                VALUES (
                    public.next_employee_id(),
                    'Joe Doe',
                    ?,
                    ?,
                    'not-a-real-hash',
                    'EMPLOYEE',
                    TRUE,
                    now(),
                    now()
                )
                """,
                username,
                "reserved-username-test@example.invalid"
        );
    }
}
