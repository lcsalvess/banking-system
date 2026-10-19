package com.lcsalvess.bankingsystem.integration.transaction;

import com.lcsalvess.bankingsystem.entity.user.Role;
import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.repository.UserRepository;
import com.lcsalvess.bankingsystem.repository.idempotency.TransactionIdempotencyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest
@DisplayName("Transaction idempotency cleanup integration")
class TransactionIdempotencyCleanupIntegrationTests {

    @Autowired
    private TransactionIdempotencyRepository idempotencyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        clearIdempotencyRequests();

        user = userRepository.saveAndFlush(
                new User(
                        "idem-cleanup-user",
                        "idem-cleanup-user@test.com",
                        "test-password",
                        Role.EMPLOYEE
                )
        );
    }

    @AfterEach
    void tearDown() {
        clearIdempotencyRequests();
        userRepository.delete(user);
    }

    @Test
    @DisplayName("Should delete only requests older than the retention period")
    void shouldDeleteOnlyExpiredRequests() {
        insertRequest(user.getId(), 10);
        insertRequest(user.getId(), 8);
        insertRequest(user.getId(), 1);

        int deleted = idempotencyRepository.deleteOlderThanDays(7);

        assertEquals(2, deleted);
        assertEquals(1L, countRequests());
    }

    private void insertRequest(long userId, int ageInDays) {
        jdbcTemplate.update(
                """
                INSERT INTO transaction_idempotency_requests (
                    user_id,
                    idempotency_key,
                    operation_type,
                    request_hash,
                    created_at
                )
                VALUES (
                    ?,
                    gen_random_uuid(),
                    'DEPOSIT',
                    repeat('0', 64),
                    (CURRENT_TIMESTAMP AT TIME ZONE 'America/Sao_Paulo')
                        - make_interval(days => ?)
                )
                """,
                userId,
                ageInDays
        );
    }

    private long countRequests() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_idempotency_requests",
                Long.class
        );
    }

    private void clearIdempotencyRequests() {
        jdbcTemplate.update("DELETE FROM transaction_idempotency_requests");
    }
}