package com.lcsalvess.bankingsystem.integration.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest
class DatabaseLockTimeoutTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Pooled connections should run with a 3 second lock_timeout")
    void shouldApplyLockTimeoutToPooledConnections() {
        String lockTimeout = jdbcTemplate.queryForObject("SHOW lock_timeout", String.class);

        assertEquals("3s", lockTimeout);
    }

    @Test
    @DisplayName("Account balance should be limited by ck_accounts_balance_max")
    void shouldDeclareBalanceUpperBoundConstraint() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_accounts_balance_max'",
                Integer.class
        );

        assertEquals(1, count);
    }
}