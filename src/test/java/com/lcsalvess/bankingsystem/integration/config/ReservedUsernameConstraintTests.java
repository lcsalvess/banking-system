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
 * Insere usuários direto no PostgreSQL, sem passar pelo Bean Validation, para
 * provar que a regra do username reservado também vale no banco. Cada teste
 * roda em uma transação que sofre rollback, então nada é persistido.
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
                INSERT INTO users (username, email, password, role, active, created_at, updated_at)
                VALUES (?, ?, 'not-a-real-hash', 'EMPLOYEE', TRUE, now(), now())
                """,
                username,
                "reserved-username-test@example.invalid"
        );
    }
}