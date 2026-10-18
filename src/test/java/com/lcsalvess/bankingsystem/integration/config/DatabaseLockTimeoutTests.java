package com.lcsalvess.bankingsystem.integration.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest
@DisplayName("Database configuration")
class DatabaseLockTimeoutTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Nested
    @DisplayName("Lock timeout configuration")
    class LockTimeoutTests {

        @Test
        @DisplayName("Should apply a 3-second lock timeout to pooled connections")
        void shouldApplyLockTimeoutToPooledConnections() {
            String lockTimeout = jdbcTemplate.queryForObject(
                    "SHOW lock_timeout",
                    String.class
            );

            assertEquals("3s", lockTimeout);
        }

        @Test
        @DisplayName("Should use the default lock timeout for Flyway connections")
        void shouldUseDefaultLockTimeoutForFlywayConnections() {
            JdbcTemplate flywayJdbcTemplate = new JdbcTemplate(
                    flyway.getConfiguration().getDataSource()
            );

            String lockTimeout = flywayJdbcTemplate.queryForObject(
                    "SHOW lock_timeout",
                    String.class
            );

            assertEquals("0", lockTimeout);
        }
    }

    @Nested
    @DisplayName("Database constraints")
    class DatabaseConstraintsTests {

        @Test
        @DisplayName("Should enforce the maximum account balance constraint")
        void shouldDeclareBalanceUpperBoundConstraint() {
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*)
                    FROM pg_constraint
                    WHERE conname = 'ck_accounts_balance_max'
                    """,
                    Integer.class
            );

            assertEquals(1, count);
        }
    }
}